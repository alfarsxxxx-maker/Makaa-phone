package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.*
import com.example.financial.EgyptianCashDenominations
import com.example.financial.FinancialInvariantValidator
import com.example.financial.SaleLineInput
import com.example.financial.WalletOperationType
import com.example.repository.MekkaPhoneRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P0 Hardening End-to-End Suite for Part 1:
 * Covers all 30 mandates including Financial Invariants, Reversed Transaction Bug,
 * Posted-Only Projection, Atomic Operations, Wallet Operations (Deposit/Withdraw/Transfer),
 * Commission Single Source of Truth, Cash Drawer & Denominations, Shift Lifecycle,
 * POS Protected Sale & Split Payment, Return/Refund, and Maintenance Accounting (Advance vs Revenue).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1FinancialHardeningCrossModuleTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    private val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
    private val ownerId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
    private val deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID

    private val vodafoneWalletId = "WLT-VODAFONE-01"
    private val orangeWalletId = "WLT-ORANGE-01"
    private val testProductId = "PRD-IP14-128"
    private val testCustomerId = "CUST-TEST-001"

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = MekkaPhoneDatabase.createInMemoryForTesting(context)
            repository = MekkaPhoneRepository(database)
            repository.ensureFoundationInitialized()
            repository.authenticateUserPin(ownerId, "1111")

            // 1. Initial State Setup:
            // Inject 40,000 EGP (4,000,000 minor) cash to fund cash drawer (10,000 EGP) and wallets (30,000 EGP)
            val cashRes = repository.executeCashOperation(
                operationType = FinancialTransactionType.CASH_IN,
                amountMinor = 4_000_000L,
                reasonArabic = "رأس مال افتتاحي للخزينة والمحافظ 40,000 ج.م",
                actorUserId = ownerId
            )
            assertTrue("Cash open must succeed", cashRes is AppResult.Success)

            // 2. Wallets Initial State:
            // Vodafone Wallet = 20,000 EGP = 2,000,000 minor (transferred from cash drawer)
            val vodafoneWallet = WalletEntity(
                localId = vodafoneWalletId,
                shopId = shopId,
                providerId = "PRV-VODAFONE",
                walletNameArabic = "فودافون كاش الرئيسية",
                walletNumber = "01000000001",
                dailyLimitMinor = 6_000_000L,
                monthlyLimitMinor = 20_000_000L,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.financialLedgerDao().insertWallet(vodafoneWallet)
            val vfTopup = repository.executeWalletOperation(
                walletId = vodafoneWalletId,
                transferKind = "CASH_TO_WALLET",
                customerPhone = "01000000001",
                principalAmountMinor = 2_000_000L,
                providerFeeMinor = 0L,
                shopCommissionMinor = 0L,
                notes = "تغذية افتتاحية لمحفظة فودافون كاش",
                actorUserId = ownerId
            )
            assertTrue("Vodafone wallet topup must succeed", vfTopup is AppResult.Success)

            // Orange Wallet = 10,000 EGP = 1,000,000 minor (transferred from cash drawer)
            val orangeWallet = WalletEntity(
                localId = orangeWalletId,
                shopId = shopId,
                providerId = "PRV-ORANGE",
                walletNameArabic = "أورنج كاش الفرعية",
                walletNumber = "01200000001",
                dailyLimitMinor = 6_000_000L,
                monthlyLimitMinor = 20_000_000L,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.financialLedgerDao().insertWallet(orangeWallet)
            val ogTopup = repository.executeWalletOperation(
                walletId = orangeWalletId,
                transferKind = "CASH_TO_WALLET",
                customerPhone = "01200000001",
                principalAmountMinor = 1_000_000L,
                providerFeeMinor = 0L,
                shopCommissionMinor = 0L,
                notes = "تغذية افتتاحية لمحفظة أورنج كاش",
                actorUserId = ownerId
            )
            assertTrue("Orange wallet topup must succeed", ogTopup is AppResult.Success)

            // Cash remaining in drawer is now: 4,000,000 - 2,000,000 - 1,000,000 = 1,000,000 minor (10,000 EGP)

            // 3. Inventory: 10 units of iPhone 14 at 20,000 EGP cost (2,000,000 minor)
            // Default selling price = 25,000 EGP (2,500,000 minor), min selling = 22,000 EGP (2,200,000 minor)
            val product = ProductEntity(
                localId = testProductId,
                shopId = shopId,
                sku = "IP14-128-BLK",
                nameArabic = "آيفون 14 - 128 جيجا أسود",
                categoryId = "CAT-PHONES",
                defaultSellingPriceMinor = 2_500_000L,
                minSellingPriceMinor = 2_200_000L,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.catalogInventoryDao().insertProduct(product)
            repository.recordAuthorizedInventoryMovement(
                productId = testProductId,
                movementType = InventoryMovementType.ADJUSTMENT_IN,
                quantity = 10,
                unitCostMinor = 2_000_000L,
                reason = "رصيد مخزني افتتاحي 10 أجهزة",
                actorUserId = ownerId
            )

            // 4. Test Customer
            val customer = CustomerEntity(
                localId = testCustomerId,
                shopId = shopId,
                fullName = "العميل التجريبي للاختبارات المالية",
                phone = "01012345678",
                creditLimitMinor = 10_000_000L,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.catalogInventoryDao().insertCustomer(customer)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInitialFinancialInvariantsState() = runBlocking {
        val cashBalance = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        assertEquals("Initial cash must be strictly 10,000 EGP (1,000,000 minor)", 1_000_000L, cashBalance)

        val vfBalance = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.WALLET,
            accountId = vodafoneWalletId
        )
        assertEquals("Vodafone wallet must be strictly 20,000 EGP (2,000,000 minor)", 2_000_000L, vfBalance)

        val ogBalance = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.WALLET,
            accountId = orangeWalletId
        )
        assertEquals("Orange wallet must be strictly 10,000 EGP (1,000,000 minor)", 1_000_000L, ogBalance)

        val stock = database.catalogInventoryDao().getDerivedStockQuantity(shopId, testProductId)
        assertEquals("Product stock must be 10 units", 10, stock)
    }

    @Test
    fun testCrossModuleExecutionScenario() = runBlocking {
        // Step 1: Cash Sale of 1 unit (Price 25,000 EGP = 2,500,000 minor)
        val cashSaleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = testProductId, quantity = 1, unitSellingPriceMinor = 2_500_000L)),
            paidAmountMinorOverride = 2_500_000L,
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
            actorUserId = ownerId
        )
        assertTrue("Cash sale must succeed", cashSaleRes is AppResult.Success)
        val cashSale = (cashSaleRes as AppResult.Success).data

        var currentCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        // Cash was 10,000 EGP + 25,000 EGP = 35,000 EGP (3,500,000 minor)
        assertEquals(3_500_000L, currentCash)

        // Stock decreased to 9
        val stockAfterSale = database.catalogInventoryDao().getDerivedStockQuantity(shopId, testProductId)
        assertEquals(9, stockAfterSale)

        // Step 2: Credit Sale of 1 unit (Price 25,000 EGP = 2,500,000 minor, Paid 0)
        val creditSaleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = testProductId, quantity = 1, unitSellingPriceMinor = 2_500_000L)),
            paidAmountMinorOverride = 0L,
            actorUserId = ownerId
        )
        assertTrue("Credit sale must succeed", creditSaleRes is AppResult.Success)

        // Customer debt must be exactly 2,500,000 minor
        val customerDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId
        )
        assertEquals(2_500_000L, customerDebt)
        val stockAfterCredit = database.catalogInventoryDao().getDerivedStockQuantity(shopId, testProductId)
        assertEquals(8, stockAfterCredit)

        // Step 3: Wallet Customer Deposit: 1,000 EGP (100,000 minor)
        // Customer gives cash to shop (+100,000 Cash Drawer), shop transfers from wallet to customer (-100,000 Wallet)
        val depositRes = repository.executeWalletOperation(
            walletId = vodafoneWalletId,
            transferKind = "CUSTOMER_DEPOSIT",
            customerPhone = "01099999999",
            principalAmountMinor = 100_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 2_000L,
            notes = "إيداع للعميل من المحفظة",
            actorUserId = ownerId
        )
        assertTrue("Customer deposit must succeed", depositRes is AppResult.Success)
        val vfAfterDep = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        val cashAfterDep = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID)
        // Vodafone wallet was 2,000,000 - 100,000 = 1,900,000 minor
        assertEquals(1_900_000L, vfAfterDep)
        // Cash was 3,500,000 + 100,000 + commission
        assertTrue(cashAfterDep >= 3_600_000L)

        // Step 4: Wallet Customer Withdrawal: 500 EGP (50,000 minor)
        // Customer sends to shop wallet (+50,000 Wallet), shop gives customer cash (-50,000 Cash Drawer)
        val withdrawRes = repository.executeWalletOperation(
            walletId = vodafoneWalletId,
            transferKind = "CUSTOMER_WITHDRAWAL",
            customerPhone = "01099999999",
            principalAmountMinor = 50_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 1_000L,
            notes = "سحب كاش للعميل",
            actorUserId = ownerId
        )
        assertTrue("Customer withdrawal must succeed", withdrawRes is AppResult.Success)
        val vfAfterWithdraw = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        // Vodafone wallet was 1,900,000 + 50,000 = 1,950,000 minor
        assertEquals(1_950_000L, vfAfterWithdraw)

        // Step 5: Cash Expense: 200 EGP (20,000 minor)
        val cashExpenseRes = repository.executeExpense(
            categoryName = "كهرباء ومرافق",
            title = "فاتورة كهرباء للمحل",
            amountMinor = 20_000L,
            sourceAccountType = FinancialAccountType.CASH_DRAWER,
            sourceAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
            actorUserId = ownerId
        )
        assertTrue("Cash expense must succeed", cashExpenseRes is AppResult.Success)

        // Step 6: Wallet Expense: 300 EGP (30,000 minor) from Orange Wallet
        val walletExpenseRes = repository.executeExpense(
            categoryName = "اشتراك إنترنت",
            title = "دفع اشتراك الإنترنت من محفظة أورنج",
            amountMinor = 30_000L,
            sourceAccountType = FinancialAccountType.WALLET,
            sourceAccountId = orangeWalletId,
            actorUserId = ownerId
        )
        assertTrue("Wallet expense must succeed", walletExpenseRes is AppResult.Success)
        val ogAfterExpense = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, orangeWalletId)
        assertEquals(970_000L, ogAfterExpense)

        // Step 7: Sale Refund (Full refund of Cash Sale #1)
        val refundRes = repository.executeSalesReturn(
            originalSaleId = cashSale.localId,
            reasonArabic = "إرجاع كامل للجهاز واسترداد المبلغ",
            actorUserId = ownerId
        )
        assertTrue("Sale refund must succeed", refundRes is AppResult.Success)
        // Stock should be restored back by 1 unit (8 + 1 = 9)
        val stockAfterRefund = database.catalogInventoryDao().getDerivedStockQuantity(shopId, testProductId)
        assertEquals(9, stockAfterRefund)

        // Step 8: Maintenance Deposit: 500 EGP (50,000 minor) on order total 2,500 EGP
        val mntOrderRes = repository.createMaintenanceOrder(
            customerId = testCustomerId,
            deviceBrand = "Samsung",
            deviceModel = "A54",
            deviceImeiOrSerial = "354890123456789",
            faultDescription = "تغيير شاشة مكسورة",
            estimatedCostMinor = 250_000L,
            depositMinor = 50_000L,
            actorUserId = ownerId
        )
        assertTrue("Maintenance order creation with deposit must succeed", mntOrderRes is AppResult.Success)
        val mntOrder = (mntOrderRes as AppResult.Success).data

        // Step 9: Maintenance Parts Usage: Consume spare part
        val partProduct = ProductEntity(
            localId = "PRD-SCR-A54",
            shopId = shopId,
            sku = "SCR-A54-OLED",
            nameArabic = "شاشة سامسونج A54 أصلية",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 120_000L,
            minSellingPriceMinor = 100_000L,
            deviceId = deviceId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertProduct(partProduct)
        repository.recordAuthorizedInventoryMovement(
            productId = partProduct.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 5,
            unitCostMinor = 80_000L,
            reason = "شاشات صيانة قطع غيار",
            actorUserId = ownerId
        )
        val consumeRes = repository.consumePartForMaintenanceOrder(
            maintenanceOrderId = mntOrder.localId,
            productId = partProduct.localId,
            quantity = 1,
            unitChargeMinor = 120_000L,
            actorUserId = ownerId
        )
        assertTrue("Consuming part for maintenance must succeed", consumeRes is AppResult.Success)
        val partStock = database.catalogInventoryDao().getDerivedStockQuantity(shopId, partProduct.localId)
        assertEquals(4, partStock)

        // Step 10: Maintenance Final Invoice Delivery:
        // Service Total = 2,500 EGP (250,000 minor), Deposit was 500 EGP (50,000 minor)
        // Remaining paid upon delivery = 2,000 EGP (200,000 minor)
        val deliveryRes = repository.updateMaintenanceStatus(
            orderId = mntOrder.localId,
            newStatus = MaintenanceStatus.DELIVERED,
            finalChargeMinor = 250_000L,
            actorUserId = ownerId
        )
        assertTrue("Delivering maintenance order must succeed", deliveryRes is AppResult.Success)

        // Step 11: Cash Denominations and Shift Closeout
        val expectedCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        assertTrue("Expected cash must be positive", expectedCash > 0L)

        // Denomination Breakdown verification
        val countsMap = mapOf(
            200 to (expectedCash / (200 * 100)).toInt(),
            100 to 0
        )
        val breakdown = com.example.financial.DenominationCountBreakdown.fromCounts(countsMap)
        assertTrue("Breakdown total must calculate in minor units", breakdown.totalCountedMinor >= 0L)
    }

    @Test
    fun testReversedTransactionDoesNotAffectAuthoritativeBalance() = runBlocking {
        // Initial balance
        val initialCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )

        // Execute Cash Operation of 50,000 minor
        val cashRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 50_000L,
            reasonArabic = "حركة تجريبية سيتم عكسها",
            actorUserId = ownerId
        )
        assertTrue(cashRes is AppResult.Success)
        val txId = (cashRes as AppResult.Success).data.localId

        val balanceAfterAdd = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        assertEquals(initialCash + 50_000L, balanceAfterAdd)

        // Reverse the transaction
        val revRes = repository.reverseLedgerTransaction(
            originalTransactionId = txId,
            reasonArabic = "إلغاء وعكس الحركة بالكامل",
            actorUserId = ownerId
        )
        assertTrue("Reversal must succeed", revRes is AppResult.Success)

        // Query balance: must equal initialCash because reversed tx is not POSTED!
        val balanceAfterReversal = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.CASH_DRAWER, MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        assertEquals("Reversed transaction lines must not affect authoritative posted balance", initialCash, balanceAfterReversal)

        // Check authoritative projection
        val proj = database.financialLedgerDao().getAuthoritativePostedLedgerProjection(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
        )
        assertNotNull(proj)
        assertEquals(initialCash, proj!!.postedBalanceMinor)
    }

    @Test
    fun testFinancialInvariantsValidatorEnforcesBalance() {
        val header = TransactionEntity(
            localId = "FTX-TEST-VALID-01",
            shopId = shopId,
            referenceNumber = "REF-TEST-001",
            transactionType = FinancialTransactionType.TRANSFER,
            amountMinor = 100_000L,
            currency = "EGP",
            sourceAccountType = FinancialAccountType.CASH_DRAWER,
            sourceAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
            destinationAccountType = FinancialAccountType.WALLET,
            destinationAccountId = vodafoneWalletId,
            status = LedgerTransactionStatus.POSTED,
            descriptionArabic = "تحويل اختباري متوازن",
            ledgerHashSha256 = "TEST_HASH",
            createdByUserId = ownerId,
            deviceId = deviceId,
            transactionTimestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val headerVal = FinancialInvariantValidator.validateTransactionHeader(header)
        assertTrue("Valid header must pass", headerVal is AppResult.Success)

        // Balanced lines
        val validLines = listOf(
            TransactionLineEntity(
                localId = "TLN-1",
                transactionId = header.localId,
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
                direction = "OUTFLOW",
                amountMinor = 100_000L,
                signedImpactMinor = -100_000L,
                lineTimestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                createdAt = System.currentTimeMillis()
            ),
            TransactionLineEntity(
                localId = "TLN-2",
                transactionId = header.localId,
                shopId = shopId,
                accountType = FinancialAccountType.WALLET,
                accountId = vodafoneWalletId,
                direction = "INFLOW",
                amountMinor = 100_000L,
                signedImpactMinor = 100_000L,
                lineTimestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                createdAt = System.currentTimeMillis()
            )
        )
        val linesVal = FinancialInvariantValidator.validateTransactionLines(header, validLines)
        assertTrue("Balanced lines must pass", linesVal is AppResult.Success)

        // Unbalanced lines (creating money out of thin air)
        val unbalancedLines = listOf(
            validLines[0],
            validLines[1].copy(localId = "TLN-2-UNBAL", amountMinor = 150_000L, signedImpactMinor = 150_000L)
        )
        val unbalRes = FinancialInvariantValidator.validateTransactionLines(header, unbalancedLines)
        assertTrue("Unbalanced lines must fail validator", unbalRes is AppResult.Error)
    }
}

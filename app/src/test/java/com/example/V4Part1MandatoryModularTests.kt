package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.*
import com.example.financial.*
import com.example.repository.MekkaPhoneRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P0 Mandatory 14 Test Suites covering all requirements:
 * 1. FinancialInvariantTest
 * 2. LedgerPostingTest
 * 3. WalletOperationTest
 * 4. WalletTreasuryTest
 * 5. CashDrawerTest
 * 6. ShiftCloseTest
 * 7. CommissionEngineTest
 * 8. ProtectedSaleTest
 * 9. SplitPaymentTest
 * 10. RefundTest
 * 11. MaintenanceAccountingTest
 * 12. MaintenancePartsCostTest
 * 13. IdempotencyTest
 * 14. ReversalTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1MandatoryModularTests {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    private val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
    private val ownerId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
    private val mainDrawerId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
    private val vodafoneWalletId = "WLT-VF-TEST"
    private val orangeWalletId = "WLT-OG-TEST"

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = MekkaPhoneDatabase.createInMemoryForTesting(context)
            repository = MekkaPhoneRepository(database)
            repository.ensureFoundationInitialized()
            repository.authenticateUserPin(ownerId, "1111")

            // Seed opening cash: 40,000 EGP = 4,000,000 minor (to fund drawer 10,000 EGP and wallets 30,000 EGP)
            val openCashRes = repository.executeCashOperation(
                operationType = FinancialTransactionType.CASH_IN,
                amountMinor = 4_000_000L,
                reasonArabic = "رصيد افتتاحي للخزينة والمحافظ"
            )
            assertTrue("Cash opening must succeed", openCashRes is AppResult.Success)

            // Seed Vodafone Wallet
            database.financialLedgerDao().insertWallet(
                WalletEntity(
                    localId = vodafoneWalletId,
                    shopId = shopId,
                    providerId = "PRV-VODAFONE",
                    walletNameArabic = "فودافون كاش الرئيسية",
                    walletNumber = "01000000001",
                    dailyLimitMinor = 10_000_000L,
                    monthlyLimitMinor = 30_000_000L,
                    deviceId = "DEV-01",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            // Top up Vodafone wallet: 20,000 EGP from cash
            val vfTopupRes = repository.executeWalletOperation(
                walletId = vodafoneWalletId,
                operationType = WalletOperationType.CASH_TO_WALLET,
                customerPhone = "01000000001",
                principalAmountMinor = 2_000_000L,
                providerFeeMinor = 0L,
                shopCommissionMinor = 0L
            )
            assertTrue("Vodafone wallet topup must succeed", vfTopupRes is AppResult.Success)

            // Seed Orange Wallet: 10,000 EGP from cash
            database.financialLedgerDao().insertWallet(
                WalletEntity(
                    localId = orangeWalletId,
                    shopId = shopId,
                    providerId = "PRV-ORANGE",
                    walletNameArabic = "أورنج كاش الفرعية",
                    walletNumber = "01200000001",
                    dailyLimitMinor = 10_000_000L,
                    monthlyLimitMinor = 30_000_000L,
                    deviceId = "DEV-01",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            val ogTopupRes = repository.executeWalletOperation(
                walletId = orangeWalletId,
                operationType = WalletOperationType.CASH_TO_WALLET,
                customerPhone = "01200000001",
                principalAmountMinor = 1_000_000L,
                providerFeeMinor = 0L,
                shopCommissionMinor = 0L
            )
            assertTrue("Orange wallet topup must succeed", ogTopupRes is AppResult.Success)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. FinancialInvariantTest
    @Test
    fun testFinancialInvariantValidator() {
        val validTx = TransactionEntity(
            localId = "TX-INV-01",
            shopId = shopId,
            referenceNumber = "REF-INV-01",
            transactionType = FinancialTransactionType.TRANSFER,
            amountMinor = 50_000L,
            currency = "EGP",
            sourceAccountType = FinancialAccountType.CASH_DRAWER,
            sourceAccountId = mainDrawerId,
            destinationAccountType = FinancialAccountType.WALLET,
            destinationAccountId = vodafoneWalletId,
            status = LedgerTransactionStatus.POSTED,
            descriptionArabic = "قيد اختباري",
            ledgerHashSha256 = "HASH",
            createdByUserId = ownerId,
            deviceId = "DEV-01",
            transactionTimestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        assertTrue(FinancialInvariantValidator.validateTransactionHeader(validTx) is AppResult.Success)

        // Zero or negative amount must fail
        val invalidTx = validTx.copy(amountMinor = 0L)
        assertTrue(FinancialInvariantValidator.validateTransactionHeader(invalidTx) is AppResult.Error)

        // Unbalanced lines must fail
        val lines = listOf(
            TransactionLineEntity(
                localId = "TLN-1",
                transactionId = validTx.localId,
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = mainDrawerId,
                direction = "OUTFLOW",
                amountMinor = 50_000L,
                signedImpactMinor = -50_000L,
                lineTimestamp = System.currentTimeMillis(),
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis()
            ),
            TransactionLineEntity(
                localId = "TLN-2",
                transactionId = validTx.localId,
                shopId = shopId,
                accountType = FinancialAccountType.WALLET,
                accountId = vodafoneWalletId,
                direction = "INFLOW",
                amountMinor = 60_000L, // Unbalanced by 10,000!
                signedImpactMinor = 60_000L,
                lineTimestamp = System.currentTimeMillis(),
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis()
            )
        )
        assertTrue(FinancialInvariantValidator.validateTransactionLines(validTx, lines) is AppResult.Error)
    }

    // 2. LedgerPostingTest
    @Test
    fun testLedgerPostingAndAuthoritativeProjection() = runBlocking {
        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val proj = database.financialLedgerDao().getAuthoritativePostedLedgerProjection(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertNotNull(proj)
        assertEquals(cashBefore, proj!!.postedBalanceMinor)
    }

    // 3. WalletOperationTest
    @Test
    fun testWalletOperationsDepositAndWithdrawal() = runBlocking {
        val vfBalBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        val cashBalBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Customer Deposit: 1,000 EGP (100,000 minor) + 10 EGP commission (1,000 minor)
        // Cash Drawer: +101,000, Wallet: -100,000
        val depRes = repository.executeWalletOperation(
            walletId = vodafoneWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 100_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 1_000L
        )
        assertTrue(depRes is AppResult.Success)

        val vfBalAfterDep = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        val cashBalAfterDep = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(vfBalBefore - 100_000L, vfBalAfterDep)
        assertEquals(cashBalBefore + 101_000L, cashBalAfterDep)

        // Customer Withdrawal: 500 EGP (50,000 minor), 10 EGP commission (1,000 minor)
        // Wallet: +50,000, Cash Drawer: -49,000
        val wthRes = repository.executeWalletOperation(
            walletId = vodafoneWalletId,
            operationType = WalletOperationType.CUSTOMER_WITHDRAWAL,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 1_000L
        )
        assertTrue(wthRes is AppResult.Success)

        val vfBalAfterWth = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        val cashBalAfterWth = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(vfBalAfterDep + 50_000L, vfBalAfterWth)
        assertEquals(cashBalAfterDep - 49_000L, cashBalAfterWth)
    }

    // 4. WalletTreasuryTest & Reconciliation
    @Test
    fun testWalletTreasuryReconciliation() = runBlocking {
        val currentVfBal = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)

        // Reconcile with 50 EGP shortage (5,000 minor)
        val actualCounted = currentVfBal - 5_000L
        val reconRes = repository.reconcileWallet(
            walletId = vodafoneWalletId,
            actualCountedBalanceMinor = actualCounted,
            reasonArabic = "عجز رصيد بمطابقة كشف فودافون كاش"
        )
        assertTrue(reconRes is AppResult.Success)
        val recon = (reconRes as AppResult.Success).data
        assertEquals(-5_000L, recon.varianceMinor)
        assertEquals(ReconciliationStatus.SHORTAGE, recon.status)

        // Derived balance must match counted balance after adjustment!
        val newVfBal = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)
        assertEquals(actualCounted, newVfBal)
    }

    // 5. CashDrawerTest & Denominations
    @Test
    fun testCashDrawerDenominationsCounting() {
        val counts = mapOf(
            1000 to 2, // 2,000 EGP
            500 to 4,  // 2,000 EGP
            200 to 10, // 2,000 EGP
            100 to 15, // 1,500 EGP
            50 to 10,  // 500 EGP
            20 to 10,  // 200 EGP
            10 to 10,  // 100 EGP
            5 to 10,   // 50 EGP
            1 to 50    // 50 EGP
        ) // Total = 8,400 EGP = 840,000 minor
        val breakdown = DenominationCountBreakdown.fromCounts(counts)
        assertEquals(840_000L, breakdown.totalCountedMinor)
    }

    // 6. ShiftCloseTest
    @Test
    fun testShiftCloseoutAndDiscrepancy() = runBlocking {
        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Close shift with counted cash = actual - 100 EGP (-10,000 minor)
        val countedCash = cashBefore - 10_000L
        val closeRes = repository.executeCashCloseout(
            countedCashMinor = countedCash,
            notes = "إغلاق وردية بوجود عجز 100 ج.م"
        )
        assertTrue(closeRes is AppResult.Success)
        val session = (closeRes as AppResult.Success).data
        assertEquals("CLOSED", session.status)
        assertEquals(-10_000L, session.discrepancyMinor)
    }

    // 7. CommissionEngineTest
    @Test
    fun testCommissionEngineSingleSourceOfTruth() {
        // Test tiered calculation for Vodafone Cash Deposit:
        // <= 500: 5 EGP fixed, cost 1 EGP
        val previewLow = CommissionEngine.calculateProfitPreview(
            providerCode = "VODAFONE_CASH",
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            principalAmountMinor = 300_00L
        )
        assertEquals(5_00L, previewLow.customerCommissionMinor)
        assertEquals(1_00L, previewLow.providerCostFeeMinor)
        assertEquals(4_00L, previewLow.expectedShopProfitMinor)

        // > 1000: 1.5% commission, 0.5% cost
        val previewHigh = CommissionEngine.calculateProfitPreview(
            providerCode = "VODAFONE_CASH",
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            principalAmountMinor = 2_000_00L
        )
        assertEquals(30_00L, previewHigh.customerCommissionMinor) // 1.5% of 2000 = 30 EGP
        assertEquals(10_00L, previewHigh.providerCostFeeMinor)    // 0.5% of 2000 = 10 EGP
        assertEquals(20_00L, previewHigh.expectedShopProfitMinor)
    }

    // 8. ProtectedSaleTest
    @Test
    fun testProtectedSalePolicyViolations() = runBlocking {
        val prod = (repository.createProduct(
            nameArabic = "شاحن سريع 65 واط",
            sku = "CHR-65W",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 500_00L,
            minSellingPriceMinor = 400_00L,
            maxDiscountMinor = 50_00L
        ) as AppResult.Success).data

        // Seed inventory stock
        repository.recordAuthorizedInventoryMovement(
            productId = prod.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 10,
            unitCostMinor = 300_00L,
            reason = "رصيد شواحن"
        )

        // Attempt sale below minimum selling price (350 < 400) without approval -> must fail
        val saleRes = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = prod.localId, quantity = 1, unitSellingPriceMinor = 350_00L))
        )
        assertTrue(saleRes is AppResult.Error)
        assertEquals("PRICE_POLICY_VIOLATION", (saleRes as AppResult.Error).code)
    }

    // 9. SplitPaymentTest
    @Test
    fun testAtomicSplitPaymentSale() = runBlocking {
        val prod = (repository.createProduct(
            nameArabic = "ساعة ذكية ألترا",
            sku = "WTCH-ULT",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 5_000_00L
        ) as AppResult.Success).data
        repository.recordAuthorizedInventoryMovement(
            productId = prod.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 5,
            unitCostMinor = 3_500_00L,
            reason = "رصيد ساعات"
        )

        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val vfBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)

        // Split sale: 5,000 EGP total = 2,000 Cash + 3,000 Vodafone Wallet
        val splits = listOf(
            SalePaymentSplit(FinancialAccountType.CASH_DRAWER, mainDrawerId, 2_000_00L),
            SalePaymentSplit(FinancialAccountType.WALLET, vodafoneWalletId, 3_000_00L)
        )
        val saleRes = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = prod.localId, quantity = 1, unitSellingPriceMinor = 5_000_00L)),
            paidAmountMinorOverride = 5_000_00L,
            splitPayments = splits
        )
        assertTrue(saleRes is AppResult.Success)

        val cashAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val vfAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, vodafoneWalletId)

        assertEquals(cashBefore + 2_000_00L, cashAfter)
        assertEquals(vfBefore + 3_000_00L, vfAfter)
        assertEquals(4, database.catalogInventoryDao().getDerivedStockQuantity(shopId, prod.localId))
    }

    // 10. RefundTest
    @Test
    fun testSaleRefundAndStockRestoration() = runBlocking {
        val prod = (repository.createProduct(
            nameArabic = "باور بانك 20000",
            sku = "PWR-20K",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 1_000_00L
        ) as AppResult.Success).data
        repository.recordAuthorizedInventoryMovement(
            productId = prod.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 3,
            unitCostMinor = 700_00L,
            reason = "رصيد باور بانك"
        )

        val saleRes = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = prod.localId, quantity = 1, unitSellingPriceMinor = 1_000_00L))
        )
        assertTrue(saleRes is AppResult.Success)
        val sale = (saleRes as AppResult.Success).data
        assertEquals(2, database.catalogInventoryDao().getDerivedStockQuantity(shopId, prod.localId))

        // Refund sale
        val refundRes = repository.executeSalesReturn(
            originalSaleId = sale.localId,
            reasonArabic = "استرجاع العميل للباور بانك"
        )
        assertTrue(refundRes is AppResult.Success)

        // Stock restored to 3
        assertEquals(3, database.catalogInventoryDao().getDerivedStockQuantity(shopId, prod.localId))

        // Duplicate refund attempt must fail
        val dupRefund = repository.executeSalesReturn(
            originalSaleId = sale.localId,
            reasonArabic = "محاولة استرجاع مكرر"
        )
        assertTrue(dupRefund is AppResult.Error)
    }

    // 11. MaintenanceAccountingTest
    @Test
    fun testMaintenanceAdvanceVsFinalRevenue() = runBlocking {
        val cust = (repository.createCustomer("عميل صيانة", "01188889999") as AppResult.Success).data
        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Step 1: Maintenance Deposit = 500 EGP (50,000 minor) on 2,500 EGP order (250,000 minor)
        val orderRes = repository.createMaintenanceOrder(
            customerId = cust.localId,
            deviceBrand = "Xiaomi",
            deviceModel = "Redmi Note 12",
            deviceImeiOrSerial = "869999000011111",
            faultDescription = "تغيير منفذ الشحن",
            estimatedCostMinor = 250_000L,
            depositMinor = 50_000L
        )
        assertTrue(orderRes is AppResult.Success)
        val order = (orderRes as AppResult.Success).data

        // Cash increases by deposit only (+50,000)
        val cashAfterDep = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashBefore + 50_000L, cashAfterDep)

        // Step 2: Final Invoice upon delivery: Total 2,500 EGP, Deposit was 500 EGP, remaining paid = 2,000 EGP
        val delRes = repository.updateMaintenanceStatus(
            orderId = order.localId,
            newStatus = MaintenanceStatus.DELIVERED,
            finalChargeMinor = 250_000L
        )
        assertTrue(delRes is AppResult.Success)

        val cashAfterDelivery = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        // Cash increased by remaining 2,000 EGP (+200,000) -> total cash received = 2,500 EGP!
        assertEquals(cashAfterDep + 200_000L, cashAfterDelivery)
    }

    // 12. MaintenancePartsCostTest
    @Test
    fun testMaintenancePartsUsageAndProfit() = runBlocking {
        val cust = (repository.createCustomer("عميل قطع صيانة", "01177778888") as AppResult.Success).data
        val part = (repository.createProduct(
            nameArabic = "سوكت شحن تايب سي",
            sku = "SKT-TYPE-C",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 150_00L
        ) as AppResult.Success).data
        repository.recordAuthorizedInventoryMovement(
            productId = part.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 10,
            unitCostMinor = 50_00L, // Part cost = 50 EGP
            reason = "رصيد قطع"
        )

        val order = (repository.createMaintenanceOrder(
            customerId = cust.localId,
            deviceBrand = "Realme",
            deviceModel = "C55",
            deviceImeiOrSerial = "867777000011111",
            faultDescription = "صيانة سوكت",
            estimatedCostMinor = 200_00L
        ) as AppResult.Success).data

        // Consume 1 part
        val consumeRes = repository.consumePartForMaintenanceOrder(
            maintenanceOrderId = order.localId,
            productId = part.localId,
            quantity = 1,
            unitChargeMinor = 150_00L
        )
        assertTrue(consumeRes is AppResult.Success)
        assertEquals(9, database.catalogInventoryDao().getDerivedStockQuantity(shopId, part.localId))

        val updatedOrder = database.operationsDao().getMaintenanceOrderById(order.localId)!!
        assertEquals(50_00L, updatedOrder.partsCostMinor) // Parts cost = 50 EGP
    }

    // 13. IdempotencyTest
    @Test
    fun testIdempotencyProtectionOnDoubleSubmit() = runBlocking {
        val key = "IDEM-TEST-KEY-001"
        val op1 = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100_000L,
            reasonArabic = "حركة أولى بمفتاح",
            idempotencyKey = key
        )
        assertTrue(op1 is AppResult.Success)

        // Retry with same idempotency key must fail
        val op2 = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100_000L,
            reasonArabic = "إعادة نفس الحركة",
            idempotencyKey = key
        )
        assertTrue(op2 is AppResult.Error)
        assertEquals("DUPLICATE_TRANSACTION_IDEMPOTENT", (op2 as AppResult.Error).code)
    }

    // 14. ReversalTest
    @Test
    fun testReversalDoesNotAffectPostedBalances() = runBlocking {
        val initialCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        val cashRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 300_000L,
            reasonArabic = "حركة سيتم عكسها"
        )
        assertTrue(cashRes is AppResult.Success)
        val txId = (cashRes as AppResult.Success).data.localId

        assertEquals(initialCash + 300_000L, database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId))

        // Reverse transaction
        val revRes = repository.reverseLedgerTransaction(
            originalTransactionId = txId,
            reasonArabic = "إلغاء وعكس الحركة"
        )
        assertTrue(revRes is AppResult.Success)

        // Cash balance returns strictly to initialCash
        val cashAfterReversal = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals("Reversed transaction must restore balance cleanly", initialCash, cashAfterReversal)
    }
}

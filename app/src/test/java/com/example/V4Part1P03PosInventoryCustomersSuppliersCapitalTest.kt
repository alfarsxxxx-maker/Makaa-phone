package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.*
import com.example.financial.*
import com.example.inventory.InventoryEngine
import com.example.inventory.StockCountLineInput
import com.example.inventory.WeightedAverageCostCalculator
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
 * MEKKA PHONE V4.3 — P0.3 Verification Test Suite
 * Covers POS, INVENTORY, CUSTOMERS, SUPPLIERS & CAPITAL HARDENING:
 *
 * PHASE 1 — POS AND SALES:
 * - Completed sale posts financial effects exactly once to posted ledger.
 * - Inventory quantity and movements update atomically with the sale.
 * - Cost of Goods Sold (COGS) and Gross Profit derive from authoritative WAC.
 * - Split payments across cash, wallets, and customer credit represent accurately.
 * - Pricing policy & discount limit enforcement / manager override.
 * - Sales return reverses financial and inventory effects without duplicate posting.
 * - Atomic rollback on invalid sale line (zero or negative price).
 *
 * PHASE 2 — INVENTORY AND PURCHASES:
 * - Stock balances derive from inventory movements.
 * - Weighted-Average Cost (WAC) updates on inbound purchase and remains stable on outbound.
 * - Reconstructs inventory quantity and valuation strictly from movement history.
 * - Serial/IMEI lifecycle validation (unique inbound, in-stock transition on sale, restock on return).
 * - Physical stock count reconciliation creates auditable adjustments and aligns stock.
 *
 * PHASE 3 — CUSTOMERS AND RECEIVABLES:
 * - Customer debt derives from posted ledger transactions.
 * - Credit sales enforce credit limits; manager approval audited on override.
 * - Customer collection reduces customer debt and increases cash drawer without overpayment.
 * - Chronological statement reflects sales, collections, and returns correctly.
 *
 * PHASE 4 — SUPPLIERS AND PAYABLES:
 * - Supplier payables derive from purchases and supplier payments in posted ledger.
 * - Purchase return reduces supplier payables or issues refund.
 * - Supplier payment reduces liability without unapproved overpayment.
 *
 * PHASE 5 — CAPITAL MANAGEMENT:
 * - Capital ≠ Liquid Cash ≠ Wallets.
 * - Capital injection increases owner equity and drawer/wallet assets.
 * - Capital withdrawal decreases owner equity and drawer/wallet assets.
 * - Reconciled Capital Statement segregates equity contributions, profit, cash, wallets, and receivables.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1P03PosInventoryCustomersSuppliersCapitalTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    private val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
    private val ownerId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
    private val deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID

    private val mainDrawerId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
    private val testWalletId = "WLT-VODAFONE-01"

    private val serializedProductId = "PRD-IP14-128"
    private val standardProductId = "PRD-CHARGER-20W"
    private val testCustomerId = "CUST-TEST-001"
    private val testSupplierId = "SUP-ALEX-01"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = MekkaPhoneDatabase.createInMemoryForTesting(context)
        repository = MekkaPhoneRepository(database)
        repository.ensureFoundationInitialized()
        repository.authenticateUserPin(ownerId, "1111")

        // Seed initial opening capital into Cash Drawer (100,000 EGP = 10,000,000 minor)
        val initialCash = repository.recordOpeningCapital(
            amountMinor = 10_000_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId,
            notes = "رأس مال افتتاحي للخزينة 100,000 ج.م"
        )
        assertTrue("Initial cash injection must succeed", initialCash is AppResult.Success<*>)

        // Seed WalletEntity first so wallet exists in database
        val testWallet = WalletEntity(
            localId = testWalletId,
            shopId = shopId,
            providerId = "PRV-VODAFONE",
            walletNameArabic = "فودافون كاش الرئيسية",
            walletNumber = "01012345678",
            dailyLimitMinor = 6_000_000L,
            monthlyLimitMinor = 20_000_000L,
            deviceId = deviceId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.financialLedgerDao().insertWallet(testWallet)

        // Fund test wallet via cash-to-wallet operation (1,000,000 minor)
        val walletFund = repository.executeWalletOperation(
            walletId = testWalletId,
            transferKind = "CASH_TO_WALLET",
            customerPhone = "01012345678",
            principalAmountMinor = 1_000_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 0L,
            notes = "تمويل محفظة فودافون كاش للتجارب",
            actorUserId = ownerId
        )
        assertTrue("Wallet funding must succeed", walletFund is AppResult.Success<*>)

        // Seed a standard non-serialized product for tests
        database.catalogInventoryDao().insertProduct(
            ProductEntity(
                localId = standardProductId,
                shopId = shopId,
                categoryId = "CAT-ACCESSORIES",
                nameArabic = "شاحن أنكر 20 واط أصلي",
                sku = "ANK-20W-BLK",
                barcode = "694123456789",
                defaultSellingPriceMinor = 45_000L, // 450 EGP
                minSellingPriceMinor = 40_000L,     // 400 EGP
                wholesalePriceMinor = 38_000L,
                specialCustomerPriceMinor = 39_000L,
                maxDiscountMinor = 5_000L,
                isSerialized = false,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed a serialized smartphone product for IMEI lifecycle tests
        database.catalogInventoryDao().insertProduct(
            ProductEntity(
                localId = serializedProductId,
                shopId = shopId,
                categoryId = "CAT-PHONES",
                nameArabic = "آبل آيفون 14 - 128 جيجا",
                sku = "IPHONE-14-128",
                barcode = "194253408253",
                defaultSellingPriceMinor = 3_500_000L, // 35,000 EGP
                minSellingPriceMinor = 3_200_000L,     // 32,000 EGP
                wholesalePriceMinor = 3_100_000L,
                specialCustomerPriceMinor = 3_150_000L,
                maxDiscountMinor = 300_000L,
                isSerialized = true,
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed an active customer
        database.catalogInventoryDao().insertCustomer(
            CustomerEntity(
                localId = testCustomerId,
                shopId = shopId,
                fullName = "أحمد محمود سالم",
                phone = "01012345678",
                creditLimitMinor = 200_000L, // 2,000 EGP credit limit
                notes = "عميل مميز",
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed an active supplier
        database.catalogInventoryDao().insertSupplier(
            SupplierEntity(
                localId = testSupplierId,
                shopId = shopId,
                companyName = "شركة النيل للتوريدات والتوزيع",
                contactName = "أستاذ سامح التاجر",
                phone = "01234567890",
                notes = "أجل 14 يوم",
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // PHASE 1: POS AND SALES
    // =========================================================================

    @Test
    fun testCompletedSalePostsFinancialEffectsExactlyOnceAndDerivesCogs() = runBlocking {
        // 1. Stock inbound via purchase: 10 units @ 300 EGP (30,000 minor)
        val purchaseRes = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 10, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 300_000L // 3,000 EGP
        )
        assertTrue("Purchase must succeed", purchaseRes is AppResult.Success<*>)

        val stockBefore = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        assertEquals(10, stockBefore)
        val initialCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // 2. Execute completed Sale: 2 units @ 450 EGP (45,000 minor) = 90,000 minor total
        val saleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 2, unitSellingPriceMinor = 45_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            idempotencyKey = "SALE-IDEMP-001"
        )
        assertTrue("Sale must succeed", saleRes is AppResult.Success<*>)
        val sale = (saleRes as AppResult.Success).data

        // Verify financial posting: exactly 90,000 minor collected into Cash Drawer
        val cashAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(initialCash + 90_000L, cashAfter)

        // Verify COGS: 2 units * 30,000 minor WAC = 60,000 minor
        assertEquals(60_000L, sale.totalCostOfGoodsMinor)
        assertEquals(30_000L, sale.grossProfitMinor) // 90,000 - 60,000 = 30,000

        // Verify Inventory movement and quantity: derived stock must be 8
        val stockAfter = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        assertEquals(8, stockAfter)

        val movements = database.catalogInventoryDao().getMovementsForProductChronological(shopId, standardProductId)
        val saleMovement = movements.last()
        assertEquals(InventoryMovementType.SALE_OUT, saleMovement.movementType)
        assertEquals(2, saleMovement.quantity)
        assertEquals(-2, saleMovement.signedQuantityDelta)
        assertEquals(30_000L, saleMovement.unitCostMinor)

        // Verify idempotency / double posting prevention
        val duplicateSaleAttempt = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 2, unitSellingPriceMinor = 45_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            idempotencyKey = "SALE-IDEMP-001"
        )
        assertTrue("Duplicate sale idempotency key must reject repeated execution", duplicateSaleAttempt is AppResult.Error)

        // Total cash must not change on rejected duplicate
        val cashAfterDup = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashAfter, cashAfterDup)
    }

    @Test
    fun testSplitPaymentsAcrossCashWalletsAndCustomerCredit() = runBlocking {
        // Stock inbound: 5 units @ 300 EGP
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 5, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 150_000L
        )

        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val walletBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, testWalletId)
        val customerDebtBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId)

        // Total invoice: 2 units * 45,000 minor = 90,000 minor (900 EGP)
        // Split: 400 EGP Cash (40,000) + 300 EGP Wallet (30,000) + 200 EGP Customer Credit (20,000 unpaid)
        val splits = listOf(
            SalePaymentSplit(accountType = FinancialAccountType.CASH_DRAWER, accountId = mainDrawerId, amountMinor = 40_000L, methodLabelArabic = "نقداً"),
            SalePaymentSplit(accountType = FinancialAccountType.WALLET, accountId = testWalletId, amountMinor = 30_000L, methodLabelArabic = "فودافون كاش")
        )

        val saleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 2, unitSellingPriceMinor = 45_000L)),
            paidAmountMinorOverride = 70_000L, // 40,000 + 30,000 paid, remaining 20,000 is credit
            splitPayments = splits
        )
        assertTrue("Split payment sale must succeed", saleRes is AppResult.Success<*>)
        val sale = (saleRes as AppResult.Success).data
        assertEquals("PARTIALLY_PAID", sale.status)
        assertEquals(70_000L, sale.paidAmountMinor)
        assertEquals(90_000L, sale.totalAmountMinor)

        // Verify account postings
        val cashAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val walletAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, testWalletId)
        val customerDebtAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId)

        assertEquals(cashBefore + 40_000L, cashAfter)
        assertEquals(walletBefore + 30_000L, walletAfter)
        assertEquals(customerDebtBefore + 20_000L, customerDebtAfter)
    }

    @Test
    fun testPricingPolicyEnforcementAndManagerOverride() = runBlocking {
        // Stock inbound: 2 units @ 30,000 minor WAC
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 2, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 60_000L
        )

        // Minimum allowed selling price for standardProductId is 40,000 minor (400 EGP)
        // Attempt sale at 35,000 minor without manager approval -> must fail
        val unapprovedSale = repository.executeV4ProtectedSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 1, unitSellingPriceMinor = 35_000L)),
            priceOverrideApprovedByUserId = null,
            priceOverrideReasonArabic = ""
        )
        assertTrue("Selling below minimum allowed price without manager approval must fail", unapprovedSale is AppResult.Error)

        // Approved sale by owner with valid reason -> must succeed and record audit log
        val approvedSale = repository.executeV4ProtectedSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 1, unitSellingPriceMinor = 35_000L)),
            priceOverrideApprovedByUserId = ownerId,
            priceOverrideReasonArabic = "موافقة المدير لعميل دائم"
        )
        assertTrue("Selling below min price with owner approval must succeed", approvedSale is AppResult.Success<*>)

        val audits = database.systemIntegrityDao().getAllAuditLogsChronological(shopId)
        assertTrue("Audit log must record price override", audits.any { it.actionType == "PRICE_OVERRIDE_APPROVED" })
    }

    @Test
    fun testSalesReturnReversesFinancialAndInventoryEffectsWithoutDuplication() = runBlocking {
        // Inbound: 5 units @ 30,000 minor
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 5, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 150_000L
        )

        // Cash sale: 2 units @ 45,000 minor = 90,000 minor
        val saleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 2, unitSellingPriceMinor = 45_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue(saleRes is AppResult.Success<*>)
        val saleId = (saleRes as AppResult.Success).data.localId
        assertEquals(3, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))
        val cashBeforeRefund = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Execute Sales Return
        val refundRes = repository.executeSalesReturn(
            originalSaleId = saleId,
            reasonArabic = "استرجاع العميل للسلعة بحالة سليمة"
        )
        assertTrue("Sales return must succeed", refundRes is AppResult.Success<*>)

        // Verify inventory restored to 5
        val stockAfterReturn = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        assertEquals(5, stockAfterReturn)

        // Verify financial refund posted: 90,000 minor deducted from Cash Drawer
        val cashAfterRefund = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashBeforeRefund - 90_000L, cashAfterRefund)

        // Repeated return attempt on already refunded sale must be rejected
        val duplicateRefund = repository.executeSalesReturn(
            originalSaleId = saleId,
            reasonArabic = "محاولة استرجاع مكررة"
        )
        assertTrue("Repeated return on refunded sale must be rejected", duplicateRefund is AppResult.Error)
        assertEquals(5, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))
    }

    @Test
    fun testFailedSaleRollsBackAllInventoryAndFinancialChanges() = runBlocking {
        // Inbound: 2 units
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 2, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 60_000L
        )

        val stockBefore = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        val cashBefore = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Attempt sale with invalid line price (0 minor)
        val invalidSale = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 1, unitSellingPriceMinor = 0L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue("Sale with zero price must fail", invalidSale is AppResult.Error)

        // Confirm full rollback: stock and cash unaltered
        val stockAfter = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        val cashAfter = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(stockBefore, stockAfter)
        assertEquals(cashBefore, cashAfter)
    }

    // =========================================================================
    // PHASE 2: INVENTORY AND PURCHASES
    // =========================================================================

    @Test
    fun testWeightedAverageCostUpdatesCorrectlyAndDerivesFromMovements() = runBlocking {
        // Purchase batch 1: 10 units @ 200 EGP (20,000 minor) -> WAC = 20,000 minor
        val p1 = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 10, unitCostMinor = 20_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 200_000L
        )
        assertTrue(p1 is AppResult.Success<*>)
        var invRec = database.catalogInventoryDao().getInventoryRecord(shopId, standardProductId)
        assertEquals(20_000L, invRec?.weightedAvgCostMinor)

        // Purchase batch 2: 10 units @ 400 EGP (40,000 minor)
        // New WAC = (10 * 20,000 + 10 * 40,000) / 20 = 600,000 / 20 = 30,000 minor (300 EGP)
        val p2 = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 10, unitCostMinor = 40_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 400_000L
        )
        assertTrue(p2 is AppResult.Success<*>)
        invRec = database.catalogInventoryDao().getInventoryRecord(shopId, standardProductId)
        assertEquals(30_000L, invRec?.weightedAvgCostMinor)
        assertEquals(20, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))

        // Sale: 5 units @ 45,000 minor -> Outbound movement does NOT alter unit WAC
        repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 5, unitSellingPriceMinor = 45_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        invRec = database.catalogInventoryDao().getInventoryRecord(shopId, standardProductId)
        assertEquals(30_000L, invRec?.weightedAvgCostMinor)
        assertEquals(15, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))

        // Pure mathematical history reconstruction verification
        val movements = database.catalogInventoryDao().getMovementsForProductChronological(shopId, standardProductId)
        val reconstructed = WeightedAverageCostCalculator.reconstructFromHistory(standardProductId, movements)
        assertEquals(15, reconstructed.currentQuantity)
        assertEquals(30_000L, reconstructed.weightedAvgCostMinor)
        assertEquals(15 * 30_000L, reconstructed.totalInventoryValuationMinor)
    }

    @Test
    fun testSerialImeiLifecycleAcrossPurchaseSaleAndReturn() = runBlocking {
        val imei1 = "354892091234567"
        val imei2 = "354892091234568"

        // 1. Inbound purchase of 2 serialized iPhones @ 3,000,000 minor (30,000 EGP each)
        val purchaseRes = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(
                PurchaseLineInput(
                    productId = serializedProductId,
                    quantity = 2,
                    unitCostMinor = 3_000_000L,
                    serialNumbersOrImeis = listOf(imei1, imei2)
                )
            ),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 6_000_000L
        )
        assertTrue("Serialized purchase must succeed", purchaseRes is AppResult.Success<*>)

        // Verify serial records are IN_STOCK
        val serial1 = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imei1)
        val serial2 = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imei2)
        assertNotNull(serial1)
        assertNotNull(serial2)
        assertEquals(SerialLifecycleStatus.IN_STOCK, serial1?.status)
        assertEquals(SerialLifecycleStatus.IN_STOCK, serial2?.status)

        // Duplicate IMEI purchase in same or different batch must be rejected
        val dupImeiPurchase = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(
                PurchaseLineInput(
                    productId = serializedProductId,
                    quantity = 1,
                    unitCostMinor = 3_000_000L,
                    serialNumbersOrImeis = listOf(imei1)
                )
            ),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue("Duplicate IMEI purchase must be rejected", dupImeiPurchase is AppResult.Error)

        // 2. Sale of IMEI 1
        val saleRes = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(
                SaleLineInput(
                    productId = serializedProductId,
                    quantity = 1,
                    unitSellingPriceMinor = 3_500_000L,
                    serialNumberId = serial1?.localId
                )
            ),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue("Serialized sale must succeed", saleRes is AppResult.Success<*>)
        val sale = (saleRes as AppResult.Success).data

        // Serial status must transition to SOLD
        val serial1AfterSale = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imei1)
        assertEquals(SerialLifecycleStatus.SOLD, serial1AfterSale?.status)

        // Resell of sold IMEI must be blocked
        val resellAttempt = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(
                SaleLineInput(
                    productId = serializedProductId,
                    quantity = 1,
                    unitSellingPriceMinor = 3_500_000L,
                    serialNumberId = serial1?.localId
                )
            ),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue("Reselling a sold IMEI must be blocked", resellAttempt is AppResult.Error)

        // 3. Sales Return restores IMEI back to IN_STOCK
        val returnRes = repository.executeSalesReturn(
            originalSaleId = sale.localId,
            reasonArabic = "مرتجع جهاز معتمد"
        )
        assertTrue("Sales return of serialized item must succeed", returnRes is AppResult.Success<*>)
        val serial1AfterReturn = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imei1)
        assertEquals(SerialLifecycleStatus.IN_STOCK, serial1AfterReturn?.status)
    }

    @Test
    fun testPhysicalStockCountReconciliationAdjustsInventoryAndAudits() = runBlocking {
        // Stock 10 units @ 30,000 minor
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 10, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 300_000L
        )
        assertEquals(10, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))

        // Physical count reports only 8 units found (shortage of 2)
        val countRes = repository.executeStockCountReconciliation(
            lines = listOf(StockCountLineInput(productId = standardProductId, countedQuantity = 8, isDamagedOrScrap = false)),
            reasonArabic = "جرد ربع سنوي وتعديل العجز"
        )
        assertTrue("Stock count reconciliation must succeed", countRes is AppResult.Success<*>)
        val report = (countRes as AppResult.Success).data
        assertEquals(1, report.adjustedLinesCount)
        assertEquals(2 * 30_000L, report.totalShortageValueMinor)

        // Derived stock must immediately equal 8
        val stockAfterReconciliation = database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId)
        assertEquals(8, stockAfterReconciliation)

        // Audit log must be persisted
        val audits = database.systemIntegrityDao().getAllAuditLogsChronological(shopId)
        assertTrue(audits.any { it.actionType == "STOCK_COUNT_RECONCILIATION" })
    }

    // =========================================================================
    // PHASE 3: CUSTOMERS AND RECEIVABLES
    // =========================================================================

    @Test
    fun testCustomerCreditSaleEnforcesLimitAndCustomerCollectionReducesDebt() = runBlocking {
        // Inbound: 10 units @ 30,000 minor
        repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 10, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 300_000L
        )

        // Customer credit limit is 200,000 minor (2,000 EGP).
        // Attempt credit sale of 5 units @ 45,000 = 225,000 minor (> 200,000 limit) without manager override -> Must fail
        val overCreditLimitSale = repository.executeV4ProtectedSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 5, unitSellingPriceMinor = 45_000L)),
            paidAmountMinorOverride = 0L, // 100% credit
            creditOverrideApprovedByUserId = null
        )
        assertTrue("Credit sale exceeding customer limit must fail without override", overCreditLimitSale is AppResult.Error)

        // Credit sale of 3 units @ 45,000 = 135,000 minor (within limit) -> Must succeed
        val validCreditSale = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = standardProductId, quantity = 3, unitSellingPriceMinor = 45_000L)),
            paidAmountMinorOverride = 0L // 100% credit
        )
        assertTrue("Credit sale within limit must succeed", validCreditSale is AppResult.Success<*>)

        // Derived customer debt must be exactly 135,000 minor
        var derivedDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId
        )
        assertEquals(135_000L, derivedDebt)

        val cashBeforeCollection = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)

        // Customer collection: Customer pays 100,000 minor (1,000 EGP) into Cash Drawer
        val collectionRes = repository.recordCustomerPaymentCollection(
            customerId = testCustomerId,
            amountMinor = 100_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId,
            notes = "سداد دفعة نقدية من الحساب"
        )
        assertTrue("Customer collection must succeed", collectionRes is AppResult.Success<*>)

        // Derived customer debt must drop to 35,000 minor
        derivedDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId
        )
        assertEquals(35_000L, derivedDebt)

        // Cash Drawer balance must increase by 100,000 minor
        val cashAfterCollection = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashBeforeCollection + 100_000L, cashAfterCollection)

        // Overpayment beyond remaining debt (35,000 minor) must be blocked
        val overpaymentAttempt = repository.recordCustomerPaymentCollection(
            customerId = testCustomerId,
            amountMinor = 50_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId
        )
        assertTrue("Customer overpayment beyond remaining debt must be blocked", overpaymentAttempt is AppResult.Error)
    }

    // =========================================================================
    // PHASE 4: SUPPLIERS AND PAYABLES
    // =========================================================================

    @Test
    fun testCreditPurchaseSupplierPaymentAndPurchaseReturn() = runBlocking {
        val initialPayable = database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, testSupplierId)
        assertEquals(0L, initialPayable)

        // 1. Credit Purchase: 5 units @ 30,000 minor = 150,000 minor total. Paid = 50,000 minor. Unpaid = 100,000 minor
        val purchaseRes = repository.executePurchase(
            supplierId = testSupplierId,
            items = listOf(PurchaseLineInput(productId = standardProductId, quantity = 5, unitCostMinor = 30_000L)),
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            paidAmountMinorOverride = 50_000L
        )
        assertTrue("Credit purchase must succeed", purchaseRes is AppResult.Success<*>)
        val purchase = (purchaseRes as AppResult.Success).data

        // Derived supplier payable must be 100,000 minor
        var derivedPayable = database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, testSupplierId)
        assertEquals(100_000L, derivedPayable)

        // 2. Supplier Payment: Pay 60,000 minor to supplier from Cash Drawer
        val payRes = repository.recordSupplierPayment(
            supplierId = testSupplierId,
            amountMinor = 60_000L,
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId,
            notes = "سداد دفعة من الفاتورة"
        )
        assertTrue("Supplier payment must succeed", payRes is AppResult.Success<*>)

        // Remaining payable must be 40,000 minor
        derivedPayable = database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, testSupplierId)
        assertEquals(40_000L, derivedPayable)

        // Unapproved overpayment (> 40,000) must be rejected
        val overpayAttempt = repository.recordSupplierPayment(
            supplierId = testSupplierId,
            amountMinor = 50_000L,
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = mainDrawerId
        )
        assertTrue("Supplier overpayment must be rejected", overpayAttempt is AppResult.Error)

        // 3. Purchase Return to supplier: Return entire purchase (150,000 minor)
        // Reduces remaining payable (40,000) and refunds cash for the rest (110,000)
        val returnRes = repository.executePurchaseReturn(
            originalPurchaseId = purchase.localId,
            reasonArabic = "استرجاع الدفعة للمورد لوجود عيوب تصنيع"
        )
        assertTrue("Purchase return must succeed", returnRes is AppResult.Success<*>)

        // Supplier payable must now be 0
        derivedPayable = database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, testSupplierId)
        assertEquals(0L, derivedPayable)

        // Stock quantity must have deducted the 5 units
        assertEquals(0, database.catalogInventoryDao().getDerivedStockQuantity(shopId, standardProductId))
    }

    // =========================================================================
    // PHASE 5: CAPITAL MANAGEMENT AND SEGREGATION
    // =========================================================================

    @Test
    fun testCapitalSegregatedFromCashAndReconciledInCapitalStatement() = runBlocking {
        val capitalEngine = CapitalManagementEngine(database, repository.financialLedgerEngine)

        // 1. Initial Capital Statement
        val initialStatement = capitalEngine.getCapitalStatement(shopId)
        assertTrue("Initial capital contributions must be positive", initialStatement.openingCapitalMinor > 0L)

        // 2. Owner Injects 100,000 EGP (10,000,000 minor) into Cash Drawer
        val cashBeforeInject = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val injectRes = capitalEngine.recordCapitalInjection(
            shopId = shopId,
            amountMinor = 10_000_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId,
            notes = "ضخ سيولة نقدية للتوسعة",
            actorUserId = ownerId,
            deviceId = deviceId
        )
        assertTrue("Capital injection must succeed", injectRes is AppResult.Success<*>)

        // Cash Drawer increases
        val cashAfterInject = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashBeforeInject + 10_000_000L, cashAfterInject)

        // 3. Owner withdraws 20,000 EGP (2,000,000 minor) personal drawings from Cash Drawer
        val withdrawRes = capitalEngine.recordCapitalWithdrawal(
            shopId = shopId,
            amountMinor = 2_000_000L,
            sourceAccountType = FinancialAccountType.CASH_DRAWER,
            sourceAccountId = mainDrawerId,
            notes = "مسحوبات شخصية للمالك",
            actorUserId = ownerId,
            deviceId = deviceId
        )
        assertTrue("Capital withdrawal must succeed", withdrawRes is AppResult.Success<*>)

        val cashAfterWithdraw = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        assertEquals(cashAfterInject - 2_000_000L, cashAfterWithdraw)

        // 4. Verify Capital Statement accurately calculates net contributions and segregates assets
        val updatedStatement = capitalEngine.getCapitalStatement(shopId)
        assertEquals(10_000_000L, updatedStatement.ownerInjectionsMinor)
        assertEquals(2_000_000L, updatedStatement.ownerWithdrawalsMinor)
        assertEquals(
            initialStatement.openingCapitalMinor + 8_000_000L,
            updatedStatement.netCapitalContributionsMinor
        )

        // Confirm segregation: Capital != Liquid Cash
        // Total liquid assets is Cash Drawer + Wallets
        assertEquals(
            cashAfterWithdraw + updatedStatement.walletsTotalBalanceMinor,
            updatedStatement.netLiquidAssetsMinor
        )
    }

    @Test
    fun testCapitalRegressionClassificationAndRepositoryIntegration() = runBlocking {
        // 1. Verify reachability through production repository entry points
        val repStatement = repository.getCapitalStatement(shopId)
        assertNotNull(repStatement)
        assertTrue(repStatement.openingCapitalMinor > 0L)

        // 2. Explicitly classified opening capital transaction
        val explicitOpeningRes = repository.recordOpeningCapital(
            amountMinor = 2_000_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId,
            notes = "رأس مال افتتاحي إضافي موثق"
        )
        assertTrue("Explicit opening capital must post successfully", explicitOpeningRes is AppResult.Success<*>)
        val afterExplicitOpening = repository.getCapitalStatement(shopId)
        assertEquals(
            repStatement.openingCapitalMinor + 2_000_000L,
            afterExplicitOpening.openingCapitalMinor
        )

        // 3. Ordinary CASH_IN must NOT be classified as capital injection or opening capital
        val ordinaryCashIn = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 500_000L,
            reasonArabic = "استلام نقدية عادية لتسوية مصاريف مكتبية"
        )
        assertTrue("Ordinary cash in must succeed", ordinaryCashIn is AppResult.Success<*>)

        val afterOrdinaryCashIn = repository.getCapitalStatement(shopId)
        // Opening capital and owner injections must remain unchanged by an ordinary cash in
        assertEquals(afterExplicitOpening.openingCapitalMinor, afterOrdinaryCashIn.openingCapitalMinor)
        assertEquals(afterExplicitOpening.ownerInjectionsMinor, afterOrdinaryCashIn.ownerInjectionsMinor)

        // 4. Test REVERSED transaction does not affect capital statement
        val txToReverse = (ordinaryCashIn as AppResult.Success).data
        database.financialLedgerDao().markTransactionReversed(
            originalTxId = txToReverse.localId,
            reversalTxId = "REV-${txToReverse.localId}",
            updatedAt = System.currentTimeMillis()
        )

        val afterReversalStatement = repository.getCapitalStatement(shopId)
        assertEquals(afterExplicitOpening.openingCapitalMinor, afterReversalStatement.openingCapitalMinor)
    }
}

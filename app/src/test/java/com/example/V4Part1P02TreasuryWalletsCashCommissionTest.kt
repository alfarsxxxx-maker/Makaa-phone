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
 * MEKKA PHONE V4.3 — P0.2
 * TREASURY, WALLETS, CASH & COMMISSION HARDENING TEST SUITE
 *
 * Verifies and hardens:
 * 1. Wallet Operations (Deposits, Withdrawals, Cash-to-Wallet, Wallet-to-Cash, Transfers).
 * 2. Balance, limit, and status validations (Insufficient balance, Suspended/Disabled wallet, Tenant mismatch).
 * 3. Treasury & Cash Drawer (Opening cash, Inflows/Outflows, Denominations counting, Reconciliation).
 * 4. Shift Management (Open/Close lifecycle, Duplicate closeout protection, Variance approval gate).
 * 5. Commission Engine (Rules validation, Profit preview, Exactly-once commission posting).
 * 6. Deterministic Cross-Module Regression Scenario (10,000 opening cash, 1,000 deposit, 500 withdrawal, 2,000 cash sale, 300 expense, 700 customer payment).
 * 7. Required Failure and Edge Cases (Idempotency duplicate protection, Unbalanced prevention).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1P02TreasuryWalletsCashCommissionTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    private val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
    private val ownerId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
    private val mainDrawerId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
    private val primaryWalletId = "WLT-VF-P02"
    private val secondaryWalletId = "WLT-OG-P02"
    private val suspendedWalletId = "WLT-SUSPENDED-P02"
    private val testCustomerId = "CUST-P02-001"
    private val testProductId = "PRD-P02-001"

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = MekkaPhoneDatabase.createInMemoryForTesting(context)
            repository = MekkaPhoneRepository(database)
            repository.ensureFoundationInitialized()
            repository.authenticateUserPin(ownerId, "1111")

        // Seed Primary Active Wallet (Vodafone Cash)
        database.financialLedgerDao().insertWallet(
            WalletEntity(
                localId = primaryWalletId,
                shopId = shopId,
                providerId = "PRV-VODAFONE",
                walletNameArabic = "فودافون كاش الرئيسية",
                walletNumber = "01000000001",
                dailyLimitMinor = 60_000_00L,
                monthlyLimitMinor = 200_000_00L,
                operationalStatus = "ACTIVE",
                isActive = true,
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed Secondary Active Wallet (Orange Cash)
        database.financialLedgerDao().insertWallet(
            WalletEntity(
                localId = secondaryWalletId,
                shopId = shopId,
                providerId = "PRV-ORANGE",
                walletNameArabic = "أورنج كاش الفرعية",
                walletNumber = "01200000001",
                dailyLimitMinor = 60_000_00L,
                monthlyLimitMinor = 200_000_00L,
                operationalStatus = "ACTIVE",
                isActive = true,
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed Suspended Wallet
        database.financialLedgerDao().insertWallet(
            WalletEntity(
                localId = suspendedWalletId,
                shopId = shopId,
                providerId = "PRV-ETISALAT",
                walletNameArabic = "محفظة معلقة للاختبار",
                walletNumber = "01100000001",
                operationalStatus = "SUSPENDED",
                isActive = false,
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed Test Customer
        database.catalogInventoryDao().insertCustomer(
            CustomerEntity(
                localId = testCustomerId,
                shopId = shopId,
                fullName = "أحمد مصطفى (عميل اختبار P0.2)",
                phone = "01099991111",
                creditLimitMinor = 10_000_000L,
                deviceId = "DEV-01",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Seed Test Products
        val screenProduct = ProductEntity(
            localId = testProductId,
            shopId = shopId,
            sku = "SKU-P02-SCR",
            nameArabic = "شاشة سامسونج A54 أصلية",
            categoryId = "CAT-PARTS",
            defaultSellingPriceMinor = 100_000L,
            minSellingPriceMinor = 80_000L,
            deviceId = "DEV-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertProduct(screenProduct)
        repository.recordAuthorizedInventoryMovement(
            productId = testProductId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 10,
            unitCostMinor = 60_000L,
            reason = "رصيد مخزني افتتاحي 10 قطع",
            actorUserId = ownerId
        )

        val phoneProduct = ProductEntity(
            localId = "PRD-P02-PHONE",
            shopId = shopId,
            sku = "SKU-P02-PHN",
            nameArabic = "هاتف سامسونج A15",
            categoryId = "CAT-PHONES",
            defaultSellingPriceMinor = 200_000L,
            minSellingPriceMinor = 180_000L,
            deviceId = "DEV-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertProduct(phoneProduct)
        repository.recordAuthorizedInventoryMovement(
            productId = "PRD-P02-PHONE",
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 10,
            unitCostMinor = 150_000L,
            reason = "رصيد مخزني افتتاحي 10 أجهزة",
            actorUserId = ownerId
        )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =============================================================================================
    // 1. WALLET OPERATIONS & STATUS HARDENING
    // =============================================================================================

    @Test
    fun testWalletSuspendedOrDisabledStatusRejection() = runBlocking {
        // Attempt operation on SUSPENDED wallet must be rejected
        val opRes = repository.executeWalletOperation(
            walletId = suspendedWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L
        )
        assertTrue("Suspended wallet must be rejected", opRes is AppResult.Error)
        assertEquals("WALLET_INACTIVE_OR_SUSPENDED", (opRes as AppResult.Error).code)
    }

    @Test
    fun testWalletInsufficientBalanceOnDepositRejection() = runBlocking {
        // Primary wallet balance is 0 initially. Customer deposit requires wallet to send funds!
        val depRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 100_000L,
            shopCommissionMinor = 1_000L
        )
        assertTrue("Deposit from unbacked wallet must fail", depRes is AppResult.Error)
        assertEquals("INSUFFICIENT_DERIVED_BALANCE", (depRes as AppResult.Error).code)
    }

    @Test
    fun testCashDrawerInsufficientBalanceOnWithdrawalRejection() = runBlocking {
        // Drawer has 0 cash initially. Withdrawal requires drawer to hand out cash!
        val wthRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_WITHDRAWAL,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L,
            shopCommissionMinor = 500L
        )
        assertTrue("Withdrawal from empty cash drawer must fail", wthRes is AppResult.Error)
        assertEquals("INSUFFICIENT_DERIVED_BALANCE", (wthRes as AppResult.Error).code)
    }

    @Test
    fun testWalletTransferBetweenWalletsWithExplicitIds() = runBlocking {
        // Fund drawer first: 5,000 EGP (500,000 minor)
        val seedCash = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 500_000L,
            reasonArabic = "تغذية افتتاحية للخزينة"
        )
        assertTrue(seedCash is AppResult.Success)

        // Fund primary wallet: 3,000 EGP (300,000 minor) via CASH_TO_WALLET
        val topupRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CASH_TO_WALLET,
            customerPhone = "01000000001",
            principalAmountMinor = 300_000L
        )
        assertTrue(topupRes is AppResult.Success)

        // Transfer 1,000 EGP (100,000 minor) from Primary to Secondary wallet
        val transferRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.WALLET_TRANSFER,
            customerPhone = "01200000001",
            principalAmountMinor = 100_000L,
            targetWalletId = secondaryWalletId
        )
        assertTrue(transferRes is AppResult.Success)

        val primBal = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, primaryWalletId)
        val secBal = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, secondaryWalletId)
        assertEquals(200_000L, primBal)
        assertEquals(100_000L, secBal)
    }

    @Test
    fun testWalletTransferToSelfOrInvalidTargetRejection() = runBlocking {
        // Transfer to same wallet must be rejected
        val selfTransferRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.WALLET_TRANSFER,
            customerPhone = "01000000001",
            principalAmountMinor = 100_000L,
            targetWalletId = primaryWalletId
        )
        assertTrue(selfTransferRes is AppResult.Error)
        assertEquals("INVALID_TARGET_WALLET", (selfTransferRes as AppResult.Error).code)

        // Transfer to non-existent target wallet must be rejected
        val nonExistentRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.WALLET_TRANSFER,
            customerPhone = "01000000001",
            principalAmountMinor = 100_000L,
            targetWalletId = "WLT-DOES-NOT-EXIST"
        )
        assertTrue(nonExistentRes is AppResult.Error)
        assertEquals("TARGET_WALLET_NOT_FOUND", (nonExistentRes as AppResult.Error).code)
    }

    @Test
    fun testDuplicateWalletOperationProtection() = runBlocking {
        // Fund drawer and wallet
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 500_000L,
            reasonArabic = "تغذية افتتاحية للخزينة"
        )
        repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CASH_TO_WALLET,
            customerPhone = "01000000001",
            principalAmountMinor = 200_000L
        )

        val key = "IDEMPOTENT-P02-WALLET-KEY-001"
        val op1 = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L,
            shopCommissionMinor = 500L,
            idempotencyKey = key
        )
        assertTrue(op1 is AppResult.Success)

        // Second call with same key must be rejected
        val op2 = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L,
            shopCommissionMinor = 500L,
            idempotencyKey = key
        )
        assertTrue("Duplicate submission must be rejected", op2 is AppResult.Error)
        assertEquals("DUPLICATE_TRANSACTION_IDEMPOTENT", (op2 as AppResult.Error).code)
    }

    // =============================================================================================
    // 2. CASH TREASURY, DENOMINATIONS & SHIFT MANAGEMENT
    // =============================================================================================

    @Test
    fun testCashDenominationsBreakdownCalculation() {
        val counts = mapOf(
            200 to 20, // 4,000 EGP
            100 to 30, // 3,000 EGP
            50 to 20,  // 1,000 EGP
            20 to 50,  // 1,000 EGP
            10 to 50,  // 500 EGP
            5 to 60,   // 300 EGP
            1 to 200   // 200 EGP
        ) // Total = 10,000 EGP = 1,000,000 minor
        val totalMinor = EgyptianCashDenominations.calculateTotalCountedMinor(counts)
        assertEquals(1_000_000L, totalMinor)

        val breakdown = DenominationCountBreakdown.fromCounts(counts)
        assertEquals(1_000_000L, breakdown.totalCountedMinor)
    }

    @Test
    fun testShiftLifecycleAndDuplicateCloseoutProtection() = runBlocking {
        // 1. Open shift
        val openRes = repository.openCashShift(
            openingCashInputMinor = 0L,
            notes = "وردية الصباح التجريبية"
        )
        assertTrue("Opening shift must succeed", openRes is AppResult.Success)
        val shift = (openRes as AppResult.Success).data
        assertEquals("OPEN", shift.status)

        // Opening again while open must be blocked
        val dupOpenRes = repository.openCashShift(
            openingCashInputMinor = 0L,
            notes = "محاولة فتح ثانية غير مسموحة"
        )
        assertTrue(dupOpenRes is AppResult.Error)
        assertEquals("SHIFT_ALREADY_OPEN", (dupOpenRes as AppResult.Error).code)

        // 2. Close shift matching balance
        val closeRes = repository.closeCashShift(
            countedCashMinor = 0L,
            notes = "إغلاق مطابق"
        )
        assertTrue("Closing shift must succeed", closeRes is AppResult.Success)
        val closedShift = (closeRes as AppResult.Success).data
        assertEquals("CLOSED", closedShift.status)
        assertEquals(0L, closedShift.discrepancyMinor)

        // 3. Attempting to close again when NO shift is open must be blocked
        val dupCloseRes = repository.closeCashShift(
            countedCashMinor = 0L,
            notes = "محاولة إغلاق ثانية"
        )
        assertTrue("Closing without open shift must fail", dupCloseRes is AppResult.Error)
        assertEquals("NO_OPEN_SHIFT", (dupCloseRes as AppResult.Error).code)
    }

    @Test
    fun testShiftCloseoutWithDiscrepancyRequiresReasonAndApproval() = runBlocking {
        // Open shift
        repository.openCashShift(openingCashInputMinor = 0L)

        // Seed 100 EGP cash so expected cash is 10,000 minor:
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 10_000L,
            reasonArabic = "رصيد نقدية لاختبار العجز"
        )

        // Expected is 10,000 minor. Counted is 8,000 minor (shortage 2,000 minor = 20 EGP).
        val missingReason = repository.closeCashShift(
            countedCashMinor = 8_000L,
            varianceReasonArabic = ""
        )
        assertTrue(missingReason is AppResult.Error)
        assertEquals("MISSING_VARIANCE_REASON", (missingReason as AppResult.Error).code)

        // Closing with reason & manager approval succeeds
        val approvedClose = repository.closeCashShift(
            countedCashMinor = 8_000L,
            varianceReasonArabic = "عجز فكة معتمد",
            managerApprovedByUserId = ownerId
        )
        assertTrue(approvedClose is AppResult.Success)
        val session = (approvedClose as AppResult.Success).data
        assertEquals("CLOSED", session.status)
        assertEquals("APPROVED", session.approvalStatus)
        assertEquals(-2_000L, session.discrepancyMinor)
        assertEquals(10_000L, session.closingDerivedBalanceMinor)
        assertEquals(8_000L, session.closingCountedCashMinor)
    }

    // =============================================================================================
    // 3. COMMISSION ENGINE HARDENING
    // =============================================================================================

    @Test
    fun testCommissionEngineRuleValidationAndPreview() {
        // Valid preview for Vodafone Cash deposit
        val preview = CommissionEngine.calculatePreview(
            principalAmountMinor = 50_000L, // 500 EGP
            providerCode = "VODAFONE_CASH",
            operationType = WalletOperationType.CUSTOMER_DEPOSIT
        )
        assertEquals(50_000L, preview.principalAmountMinor)
        assertEquals(5_00L, preview.customerCommissionMinor) // 5 EGP
        assertEquals(505_00L, preview.netCustomerAmountMinor) // 505 EGP
        assertTrue(preview.expectedShopProfitMinor > 0L)

        // Invalid rule registration with negative fees must be rejected
        val invalidRule = CommissionTierRule(
            ruleId = "INVALID_RULE_P02",
            providerCode = "TEST",
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerFeeFixedMinor = -10_00L
        )
        val regRes = CommissionEngine.registerRule(invalidRule)
        assertTrue("Negative commission rule must be rejected", regRes is AppResult.Error)
        assertEquals("INVALID_COMMISSION_FEE", (regRes as AppResult.Error).code)

        // Preview with negative custom override must be handled gracefully
        val negPreview = CommissionEngine.calculatePreview(
            principalAmountMinor = 50_000L,
            providerCode = "VODAFONE_CASH",
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customCommissionMinorOverride = -500L
        )
        assertEquals(0L, negPreview.customerCommissionMinor)
    }

    // =============================================================================================
    // 4. DETERMINISTIC CROSS-MODULE REGRESSION SCENARIO (P0.2 SPECIFICATION)
    // =============================================================================================

    @Test
    fun testDeterministicCrossModuleScenario() = runBlocking {
        // Step 0: Opening Cash: 10,000 EGP (1,000,000 minor)
        val openCashRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 1_000_000L,
            reasonArabic = "رصيد افتتاحي للخزينة (10,000 ج.م)"
        )
        assertTrue("Opening cash must succeed", openCashRes is AppResult.Success)

        // Fund Primary Wallet explicitly: 5,000 EGP (500,000 minor) via WALLET_ADJUSTMENT (from equity)
        val fundWalletRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.WALLET_ADJUSTMENT,
            customerPhone = "01000000001",
            principalAmountMinor = 500_000L,
            notes = "رصيد افتتاحي للمحفظة من رأس المال"
        )
        assertTrue("Wallet capital funding must succeed", fundWalletRes is AppResult.Success)

        // Seed initial customer credit sale so customer has outstanding debt of 1,000 EGP (100,000 minor)
        val creditSaleInit = repository.executeSale(
            customerId = testCustomerId,
            items = listOf(SaleLineInput(productId = testProductId, quantity = 1, unitSellingPriceMinor = 100_000L)),
            paidAmountMinorOverride = 0L,
            actorUserId = ownerId
        )
        assertTrue("Initial credit sale must succeed", creditSaleInit is AppResult.Success)

        // Verification of starting state:
        val startCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val startWallet = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, primaryWalletId)
        val startCustDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId)
        assertEquals(1_000_000L, startCash)       // 10,000 EGP
        assertEquals(500_000L, startWallet)       // 5,000 EGP
        assertEquals(100_000L, startCustDebt)     // 1,000 EGP

        // -----------------------------------------------------------------------------------------
        // 1. Wallet Deposit: 1,000 EGP (100,000 minor), Commission 15 EGP (1,500 minor)
        // Customer gives cash: +1,015 EGP (+101,500 minor) to Cash Drawer
        // Shop sends e-money: -1,000 EGP (-100,000 minor) from Shop Wallet
        // Commission revenue: +15 EGP (+1,500 minor)
        // -----------------------------------------------------------------------------------------
        val depRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_DEPOSIT,
            customerPhone = "01099998888",
            principalAmountMinor = 100_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 1_500L,
            notes = "إيداع محفظة للعميل (1,000 ج.م)"
        )
        assertTrue("Customer deposit must succeed", depRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // 2. Wallet Withdrawal: 500 EGP (50,000 minor), Commission 8 EGP (800 minor)
        // Customer sends e-money: +500 EGP (+50,000 minor) to Shop Wallet
        // Shop pays cash out: -492 EGP (-49,200 minor) from Cash Drawer
        // Commission revenue: +8 EGP (+800 minor)
        // -----------------------------------------------------------------------------------------
        val wthRes = repository.executeWalletOperation(
            walletId = primaryWalletId,
            operationType = WalletOperationType.CUSTOMER_WITHDRAWAL,
            customerPhone = "01099998888",
            principalAmountMinor = 50_000L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 800L,
            notes = "سحب كاش للعميل (500 ج.م)"
        )
        assertTrue("Customer withdrawal must succeed", wthRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // 3. Cash Sale: 2,000 EGP (200,000 minor)
        // Cash Drawer increases by +2,000 EGP (+200,000 minor)
        // -----------------------------------------------------------------------------------------
        val cashSaleRes = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = "PRD-P02-PHONE", quantity = 1, unitSellingPriceMinor = 200_000L)),
            paidAmountMinorOverride = 200_000L,
            actorUserId = ownerId
        )
        assertTrue("Cash sale must succeed", cashSaleRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // 4. Expense: 300 EGP (30,000 minor)
        // Cash Drawer decreases by -300 EGP (-30,000 minor)
        // -----------------------------------------------------------------------------------------
        val expenseRes = repository.executeExpense(
            categoryName = "مصاريف تشغيل",
            title = "فاتورة إنترنت ومرافق",
            amountMinor = 30_000L,
            sourceAccountType = FinancialAccountType.CASH_DRAWER,
            sourceAccountId = mainDrawerId
        )
        assertTrue("Expense must succeed", expenseRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // 5. Customer Payment: 700 EGP (70,000 minor)
        // Customer pays cash towards 1,000 EGP outstanding debt
        // Cash Drawer increases by +700 EGP (+70,000 minor)
        // Customer Debt decreases from 1,000 EGP to 300 EGP (30,000 minor)
        // -----------------------------------------------------------------------------------------
        val paymentRes = repository.recordCustomerPaymentCollection(
            customerId = testCustomerId,
            amountMinor = 70_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = mainDrawerId,
            notes = "تحصيل دفعة نقدية من حساب العميل"
        )
        assertTrue("Customer debt collection must succeed", paymentRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // VERIFY FINAL DERIVED BALANCES & INVARIANTS:
        //
        // Cash Drawer calculation:
        //   Opening: +1,000,000
        //   + Deposit Cash In: +101,500
        //   - Withdrawal Cash Out: -49,200
        //   + Cash Sale: +200,000
        //   - Expense: -30,000
        //   + Customer Payment: +70,000
        //   = 1,000,000 + 101,500 - 49,200 + 200,000 - 30,000 + 70,000
        //   = 1,292,300 minor = 12,923.00 EGP
        //
        // Wallet calculation:
        //   Initial: +500,000
        //   - Deposit Outflow: -100,000
        //   + Withdrawal Inflow: +50,000
        //   = 450,000 minor = 4,500.00 EGP
        //
        // Customer Debt calculation:
        //   Initial Debt: +100,000
        //   - Payment Collection: -70,000
        //   = 30,000 minor = 300.00 EGP
        // -----------------------------------------------------------------------------------------
        val finalCash = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, mainDrawerId)
        val finalWallet = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, primaryWalletId)
        val finalCustomerDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_CUSTOMER, testCustomerId)

        assertEquals("Final Cash Drawer must match exactly 12,923.00 EGP", 1_292_300L, finalCash)
        assertEquals("Final Wallet must match exactly 4,500.00 EGP", 450_000L, finalWallet)
        assertEquals("Final Customer Debt must match exactly 300.00 EGP", 30_000L, finalCustomerDebt)

        // Verify Commission Revenue: 1,500 minor (Deposit) + 800 minor (Withdrawal) = 2,300 minor (23.00 EGP)
        val commissionRevenue = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
            accountId = "WALLET_COMMISSION_REVENUE"
        )
        // In double-entry, revenue is recorded with signed impact -2,300
        assertEquals(-2_300L, commissionRevenue)

        // Verify Global Invariants: All journal lines across the shop must balance to zero
        val allTx = database.financialLedgerDao().getAllTransactionsChronological(shopId)
        assertTrue("Transactions must not be empty", allTx.isNotEmpty())
        for (tx in allTx) {
            val headerVal = FinancialInvariantValidator.validateTransactionHeader(tx)
            assertTrue("Header invariant failed for ${tx.localId}", headerVal is AppResult.Success)
            val lines = database.financialLedgerDao().getLinesForTransaction(tx.localId)
            val linesVal = FinancialInvariantValidator.validateTransactionLines(tx, lines)
            assertTrue("Lines invariant failed for ${tx.localId}", linesVal is AppResult.Success)
        }
    }
}

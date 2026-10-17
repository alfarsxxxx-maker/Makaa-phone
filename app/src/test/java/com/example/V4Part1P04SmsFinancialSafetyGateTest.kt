package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.ShopEntity
import com.example.database.entities.WalletEntity
import com.example.repository.MekkaPhoneRepository
import com.example.sms.SmsClassification
import com.example.sms.SmsConfidenceLevel
import com.example.sms.SmsProcessingState
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
 * MEKKA PHONE V4.3 — P0.4.1 SMS Financial Safety Gate Verification Suite
 *
 * Strict Financial Safety Rules:
 * 1. "SMS is evidence, NOT financial truth."
 * 2. Ingestion of ANY SMS (genuine, high-confidence, OTP, promotional, or malformed)
 *    must NEVER post financial transactions to the immutable ledger or change account balances.
 * 3. Non-financial messages (OTP, Promotional, Personal, Unsupported) must never create
 *    pending financial transactions (pendingTransaction == null).
 * 4. Genuine transaction SMS must always enter AWAITING_CONFIRMATION / PENDING_REVIEW.
 * 5. Financial posting requires explicit human user confirmation.
 * 6. Explicit confirmation must post exactly once and be strictly idempotent against duplicate posting.
 * 7. Rejection/Ignore must leave zero financial footprint and permanently prevent subsequent confirmation.
 * 8. Duplicate detection must strictly block duplicate message hashes and duplicate provider references.
 * 9. Multi-tenant shop isolation must be strictly enforced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1P04SmsFinancialSafetyGateTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    private val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
    private val ownerId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
    private val deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID
    private val mainDrawerId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = MekkaPhoneDatabase.createInMemoryForTesting(context)
            repository = MekkaPhoneRepository(database)
            repository.ensureFoundationInitialized()
            repository.authenticateUserPin(ownerId, "1111")

            // Seed initial opening cash into Cash Drawer
            repository.recordOpeningCapital(
                amountMinor = 10_000_000L, // 100,000.00 EGP
                destinationAccountType = FinancialAccountType.CASH_DRAWER,
                destinationAccountId = mainDrawerId,
                notes = "رأس مال افتتاحي للخزينة"
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testOtpMessageNeverEntersFinancialPipeline() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val initialLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        val initialWalletsBalance = repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor

        // OTP message with financial-looking numbers
        val otpBody = "كود التأكيد الخاص بك هو 7788 لتسجيل الدخول إلى محفظة فودافون كاش بمبلغ 500 جنيه. لا تشارك هذا الرمز مع أحد"
        val result = repository.ingestSmsMessage(
            senderAddress = "VodafoneCash",
            rawMessageBody = otpBody
        )

        assertTrue("OTP ingestion must be processed", result is AppResult.Success)
        val outcome = (result as AppResult.Success).data

        // Strict Safety Gate Assertions
        assertEquals("Must be classified as OTP", SmsClassification.OTP, outcome.classification)
        assertEquals("Processing state must be IGNORED", SmsProcessingState.IGNORED, outcome.processingState)
        assertNull("OTP must NEVER produce a pending financial transaction", outcome.pendingTransaction)
        assertNull("OTP must NEVER produce a parsing result", outcome.parsingResult)
        assertNull("OTP must NEVER auto-confirm", outcome.autoConfirmedTransaction)

        // Verify zero financial footprint
        val postLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("Ledger count must remain unchanged", initialLedgerCount, postLedgerCount)
        val postWalletsBalance = repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor
        assertEquals("Wallets balance must remain unchanged", initialWalletsBalance, postWalletsBalance)
    }

    @Test
    fun testPromotionalMessageNeverEntersFinancialPipeline() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val initialLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size

        val promoBody = "عروض الصيف من فودافون كاش! اشترك الآن بـ 50 جنيه واحصل على 500 دقيقة هدية ومبروك رصيد إضافي"
        val result = repository.ingestSmsMessage(
            senderAddress = "VF-Promo",
            rawMessageBody = promoBody
        )

        assertTrue("Promotional SMS ingestion must succeed", result is AppResult.Success)
        val outcome = (result as AppResult.Success).data

        assertEquals("Must be classified as PROMOTIONAL", SmsClassification.PROMOTIONAL, outcome.classification)
        assertEquals("Processing state must be IGNORED", SmsProcessingState.IGNORED, outcome.processingState)
        assertNull("Promotional SMS must NEVER produce a pending financial transaction", outcome.pendingTransaction)
        assertNull("Promotional SMS must NEVER produce a parsing result", outcome.parsingResult)

        val postLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("Zero financial ledger transactions created", initialLedgerCount, postLedgerCount)
    }

    @Test
    fun testPersonalAndDistortedMessageNeverEntersFinancialPipeline() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val initialLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size

        // 1. Personal non-financial message
        val personalResult = repository.ingestSmsMessage(
            senderAddress = "01012345678",
            rawMessageBody = "السلام عليكم ورحمة الله، كيف حالك يا صديقي؟ أراك غداً إن شاء الله"
        )
        assertTrue(personalResult is AppResult.Success)
        val personalOutcome = (personalResult as AppResult.Success).data
        assertEquals(SmsClassification.PERSONAL, personalOutcome.classification)
        assertNull(personalOutcome.pendingTransaction)

        // 2. Distorted message with financial words but missing amount
        val distortedResult = repository.ingestSmsMessage(
            senderAddress = "VF-Cash",
            rawMessageBody = "تم استلام تحويل كاش في محفظتك بنجاح"
        )
        assertTrue(distortedResult is AppResult.Success)
        val distortedOutcome = (distortedResult as AppResult.Success).data
        assertEquals(SmsClassification.UNSUPPORTED, distortedOutcome.classification)
        assertNull(distortedOutcome.pendingTransaction)

        val postLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("Zero financial ledger transactions created for personal/distorted messages", initialLedgerCount, postLedgerCount)
    }

    @Test
    fun testGenuineFinancialSmsIngestionNeverAutoPostsWithoutExplicitConfirmation() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val initialLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        val initialWalletsBalance = repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor

        // Ingest genuine high-confidence SMS with autoConfirmOnHighConfidenceMatch = true (testing bypass prevention)
        val smsBody = "تم استلام مبلغ 2500.00 ج.م من رقم 01011223344 عبر فودافون كاش رقم العملية #VF112233"
        val result = repository.ingestSmsMessage(
            senderAddress = "VF-Cash",
            rawMessageBody = smsBody,
            autoConfirmOnHighConfidenceMatch = true // Should still be blocked by safety gate!
        )

        assertTrue(result is AppResult.Success)
        val outcome = (result as AppResult.Success).data

        assertEquals("Must be classified as TRANSACTION_SMS", SmsClassification.TRANSACTION_SMS, outcome.classification)
        assertEquals("Must enter PENDING_REVIEW", SmsProcessingState.PENDING_REVIEW, outcome.processingState)
        assertNotNull("Pending transaction must be created", outcome.pendingTransaction)
        assertEquals("Status must be AWAITING_CONFIRMATION", "AWAITING_CONFIRMATION", outcome.pendingTransaction?.status)
        assertNull("Must NEVER auto-confirm into financial ledger", outcome.autoConfirmedTransaction)

        // CRITICAL SAFETY ASSERTION: Zero financial ledger transactions created upon ingestion!
        val postLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("Ledger count must remain exactly as before ingestion", initialLedgerCount, postLedgerCount)

        // CRITICAL SAFETY ASSERTION: Wallet balance must remain 0!
        val postWalletsBalance = repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor
        assertEquals("Wallet balance must NOT change upon ingestion", initialWalletsBalance, postWalletsBalance)
    }

    @Test
    fun testExplicitUserConfirmationPostsExactlyOnce() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val opsDao = database.operationsDao()

        // 1. Create target wallet
        val walletRes = repository.createWallet(
            providerId = "PRV-VODAFONE",
            walletNameArabic = "محفظة فودافون الرئيسية",
            walletNumber = "01000112233"
        )
        assertTrue(walletRes is AppResult.Success)
        val wallet = (walletRes as AppResult.Success).data

        val preConfirmLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size

        // 2. Ingest genuine SMS
        val smsBody = "تم استلام مبلغ 3000.00 ج.م من رقم 01022334455 عبر فودافون كاش رقم العملية #VF300099"
        val ingestRes = repository.ingestSmsMessage(senderAddress = "VF-Cash", rawMessageBody = smsBody)
        assertTrue(ingestRes is AppResult.Success)
        val pendingId = (ingestRes as AppResult.Success).data.pendingTransaction!!.localId

        // 3. Explicit user confirmation
        val confirmRes = repository.confirmPendingSms(
            pendingId = pendingId,
            explicitWalletId = wallet.localId,
            actorUserId = ownerId
        )
        assertTrue("Explicit confirmation must succeed", confirmRes is AppResult.Success)
        val ledgerTx = (confirmRes as AppResult.Success).data

        // 4. Verify financial ledger transaction
        assertEquals(FinancialTransactionType.DEPOSIT, ledgerTx.transactionType)
        assertEquals(300_000L, ledgerTx.amountMinor) // 3,000.00 EGP
        assertEquals(FinancialAccountType.WALLET, ledgerTx.destinationAccountType)
        assertEquals(wallet.localId, ledgerTx.destinationAccountId)

        val postConfirmLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("Exactly one ledger entry must be created", preConfirmLedgerCount + 1, postConfirmLedgerCount)

        // 5. Verify authoritative wallet balance derived from ledger increased
        val currentBalance = ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, wallet.localId)
        assertEquals(300_000L, currentBalance)

        // 6. Verify pending transaction status
        val updatedPending = opsDao.getPendingTransactionById(pendingId)
        assertNotNull(updatedPending)
        assertEquals("CONFIRMED", updatedPending?.status)
        assertEquals(ledgerTx.localId, updatedPending?.confirmedLedgerTransactionId)
    }

    @Test
    fun testDoubleConfirmationBlockedIdempotentlyWithoutDuplicateFinancialImpact() = runBlocking {
        val ledgerDao = database.financialLedgerDao()

        // 1. Create wallet
        val walletRes = repository.createWallet(
            providerId = "PRV-VODAFONE",
            walletNameArabic = "محفظة فودافون كاش",
            walletNumber = "01099887766"
        )
        val wallet = (walletRes as AppResult.Success).data

        // 2. Ingest and confirm first time
        val smsBody = "تم استلام مبلغ 1200.00 ج.م من رقم 01033445566 عبر فودافون كاش رقم العملية #VF120011"
        val ingestRes = repository.ingestSmsMessage(senderAddress = "VF-Cash", rawMessageBody = smsBody)
        val pendingId = (ingestRes as AppResult.Success).data.pendingTransaction!!.localId

        val firstConfirm = repository.confirmPendingSms(pendingId = pendingId, explicitWalletId = wallet.localId)
        assertTrue("First confirmation must succeed", firstConfirm is AppResult.Success)

        val postFirstConfirmLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        val postFirstConfirmBalance = ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, wallet.localId)
        assertEquals(120_000L, postFirstConfirmBalance)

        // 3. Attempt SECOND confirmation on the same pending transaction
        val secondConfirm = repository.confirmPendingSms(pendingId = pendingId, explicitWalletId = wallet.localId)
        assertTrue("Second confirmation MUST fail idempotently", secondConfirm is AppResult.Error)
        assertEquals("ALREADY_CONFIRMED_IDEMPOTENT", (secondConfirm as AppResult.Error).code)

        // 4. Verify ZERO additional financial effect
        val postSecondConfirmLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size
        assertEquals("No duplicate ledger row created", postFirstConfirmLedgerCount, postSecondConfirmLedgerCount)

        val postSecondConfirmBalance = ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, wallet.localId)
        assertEquals("Balance must NOT be doubled", 120_000L, postSecondConfirmBalance)
    }

    @Test
    fun testRejectionAndIgnoreWorkflowLeavesZeroFinancialFootprint() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val opsDao = database.operationsDao()
        val initialLedgerCount = ledgerDao.getAllTransactionsChronological(shopId).size

        // 1. Ingest SMS
        val smsBody = "تم استلام مبلغ 1800.00 ج.م عبر انستا باي إلى 01155443322 مرجع IPN998877"
        val ingestRes = repository.ingestSmsMessage(senderAddress = "InstaPay", rawMessageBody = smsBody)
        assertTrue(ingestRes is AppResult.Success)
        val pendingId = (ingestRes as AppResult.Success).data.pendingTransaction!!.localId

        // 2. Reject pending transaction
        val rejectRes = repository.rejectOrIgnorePendingSms(pendingId = pendingId, decisionStatus = "REJECTED")
        assertTrue(rejectRes is AppResult.Success)

        val rejectedPending = opsDao.getPendingTransactionById(pendingId)
        assertEquals("REJECTED", rejectedPending?.status)

        // Verify zero financial ledger transactions created
        assertEquals(initialLedgerCount, ledgerDao.getAllTransactionsChronological(shopId).size)

        // 3. Attempt to confirm rejected SMS -> MUST be blocked!
        val confirmAttempt = repository.confirmPendingSms(pendingId = pendingId)
        assertTrue("Confirming a rejected SMS must fail", confirmAttempt is AppResult.Error)
        assertEquals("INVALID_PENDING_STATUS", (confirmAttempt as AppResult.Error).code)

        // Verify still zero financial impact
        assertEquals(initialLedgerCount, ledgerDao.getAllTransactionsChronological(shopId).size)
    }

    @Test
    fun testDuplicateSmsHashAndDuplicateReferenceStrictlyBlocked() = runBlocking {
        val smsBody = "تم استلام مبلغ 900.00 ج.م في محفظة أورنج كاش من 0122334455 رقم مرجع ORG991122"

        // 1. First ingestion succeeds
        val firstIngest = repository.ingestSmsMessage(senderAddress = "OrangeCash", rawMessageBody = smsBody)
        assertTrue("First ingestion must succeed", firstIngest is AppResult.Success)

        // 2. Duplicate exact message (same hash) must be blocked
        val duplicateHashIngest = repository.ingestSmsMessage(senderAddress = "OrangeCash", rawMessageBody = smsBody)
        assertTrue("Duplicate message hash must fail", duplicateHashIngest is AppResult.Error)
        assertEquals("DUPLICATE_SMS_HASH", (duplicateHashIngest as AppResult.Error).code)

        // 3. Different message text but duplicate provider + reference must be blocked
        val dupRefBody = "تم استلام 900 جنيه محفظة أورنج كاش من رقم 0122334455 رقم مرجع ORG991122"
        val duplicateRefIngest = repository.ingestSmsMessage(senderAddress = "OrangeCash", rawMessageBody = dupRefBody)
        assertTrue("Duplicate reference must fail", duplicateRefIngest is AppResult.Error)
        assertEquals("DUPLICATE_SMS_REFERENCE", (duplicateRefIngest as AppResult.Error).code)
    }

    @Test
    fun testShopIsolationEnforcedOnSmsConfirmation() = runBlocking {
        val coreDao = database.coreShopAuthDao()
        val ledgerDao = database.financialLedgerDao()

        // 1. Create a second shop and wallet belonging to it
        val secondShopId = "SHOP-BRANCH-02"
        coreDao.insertShop(
            ShopEntity(
                localId = secondShopId,
                code = "SHP-02",
                nameArabic = "فرع مكة فون الإسكندرية",
                nameEnglish = "Mekka Phone Alexandria",
                defaultCurrency = "EGP",
                address = "الإسكندرية",
                phone = "01099887766",
                deviceId = deviceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        val foreignWallet = WalletEntity(
            localId = "WLT-FOREIGN-01",
            shopId = secondShopId,
            providerId = "PRV-VODAFONE",
            walletNameArabic = "محفظة فرع الإسكندرية",
            walletNumber = "01055443322",
            deviceId = deviceId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ledgerDao.insertWallet(foreignWallet)

        // 2. Ingest SMS in primary shop
        val smsBody = "تم استلام مبلغ 1500.00 ج.م عبر انستا باي إلى 01100112233 مرجع IPN332211"
        val ingestRes = repository.ingestSmsMessage(senderAddress = "InstaPay", rawMessageBody = smsBody)
        val pendingId = (ingestRes as AppResult.Success).data.pendingTransaction!!.localId

        // 3. Try to confirm with foreign wallet belonging to second shop -> MUST fail!
        val crossShopConfirm = repository.confirmPendingSms(
            pendingId = pendingId,
            explicitWalletId = foreignWallet.localId,
            actorUserId = ownerId
        )
        assertTrue("Cross-shop wallet confirmation must fail", crossShopConfirm is AppResult.Error)
        assertEquals("WALLET_SHOP_ISOLATION_VIOLATION", (crossShopConfirm as AppResult.Error).code)
    }

    @Test
    fun testExpectedOperationMatchingIncreasesConfidenceWithoutAutoPosting() = runBlocking {
        val ledgerDao = database.financialLedgerDao()
        val walletRes = repository.createWallet(
            providerId = "PRV-VODAFONE",
            walletNameArabic = "محفظة فودافون العمليات",
            walletNumber = "01044332211"
        )
        val wallet = (walletRes as AppResult.Success).data

        // 1. Register expected operation before SMS arrives
        val expectedRes = repository.registerExpectedWalletOperation(
            targetWalletId = wallet.localId,
            expectedType = FinancialTransactionType.DEPOSIT,
            expectedAmountMinor = 750_00L, // 750.00 EGP
            counterpartyPhone = "01077665544"
        )
        assertTrue(expectedRes is AppResult.Success)

        // 2. Ingest matching SMS
        val smsBody = "تم استلام مبلغ 750.00 ج.م من رقم 01077665544 عبر فودافون كاش رقم العملية #VF75001"
        val ingestRes = repository.ingestSmsMessage(senderAddress = "VF-Cash", rawMessageBody = smsBody)
        assertTrue(ingestRes is AppResult.Success)
        val outcome = (ingestRes as AppResult.Success).data

        // Expected matching should link and boost confidence
        assertTrue("Must match existing expected operation", outcome.matchedExistingPendingOperation)
        assertEquals("Confidence must be HIGH", SmsConfidenceLevel.HIGH, outcome.confidenceLevel)
        assertEquals("Target wallet must be pre-linked", wallet.localId, outcome.pendingTransaction?.targetWalletId)

        // CRITICAL: Even with expected operation match and HIGH confidence, NO auto-posting occurs!
        assertNull("Must NOT auto-post into financial ledger", outcome.autoConfirmedTransaction)
        assertEquals("AWAITING_CONFIRMATION", outcome.pendingTransaction?.status)
        val currentBalance = ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, wallet.localId)
        assertEquals("Wallet balance must still be 0", 0L, currentBalance)
    }
}

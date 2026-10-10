package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.ProductEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.SupplierEntity
import com.example.repository.MekkaPhoneRepository
import com.example.transaction.IntelligentTransactionParser
import com.example.transaction.TransactionDraft
import com.example.transaction.TransactionInputModality
import com.example.transaction.TransactionValidationEngine
import com.example.transaction.UnifiedTransactionExecutionCoordinator
import com.example.transaction.UnifiedTransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Robolectric & Room Verification Suite for MEKKA PHONE V4.1:
 * PART 1 — INTELLIGENT TRANSACTION ENGINE + UNIFIED TRANSACTION PIPELINE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part1IntelligentTransactionEngineTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository
    private lateinit var parser: IntelligentTransactionParser
    private lateinit var validationEngine: TransactionValidationEngine
    private lateinit var coordinator: UnifiedTransactionExecutionCoordinator

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = MekkaPhoneDatabase.createInMemoryForTesting(context)
        repository = MekkaPhoneRepository(database)
        repository.ensureFoundationInitialized()
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        repository.executeCashOperation(
            operationType = com.example.database.entities.FinancialTransactionType.CASH_IN,
            amountMinor = 10000000L,
            reasonArabic = "رصيد افتتاحي للخزينة للاختبارات",
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )

        parser = repository.transactionParser
        validationEngine = repository.transactionValidationEngine
        coordinator = repository.transactionCoordinator

        // Insert test product with stock
        val testProduct = ProductEntity(
            localId = "PRD-TEST-IP13",
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            sku = "IP13-128",
            nameArabic = "آيفون 13 سعة 128",
            categoryId = "CAT-PHONES",
            defaultSellingPriceMinor = 1800000L,
            minSellingPriceMinor = 1500000L,
            deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertProduct(testProduct)
        repository.recordAuthorizedInventoryMovement(
            productId = "PRD-TEST-IP13",
            movementType = com.example.database.entities.InventoryMovementType.ADJUSTMENT_IN,
            quantity = 10,
            unitCostMinor = 1400000L,
            reason = "رصيد افتتاحي للاختبار",
            actorUserId = "USR-ADMIN"
        )

        // Insert test customer
        val testCustomer = CustomerEntity(
            localId = "CUST-MOHAMED",
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            fullName = "محمد أحمد",
            phone = "01011112222",
            creditLimitMinor = 5000000L,
            deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertCustomer(testCustomer)

        // Insert test supplier
        val testSupplier = SupplierEntity(
            localId = "SUP-AHMED",
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            companyName = "شركة أحمد للتوريدات",
            contactName = "أحمد",
            phone = "01099998888",
            deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.catalogInventoryDao().insertSupplier(testSupplier)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTextToSaleTransactionDraftExtraction() = runBlocking {
        val input = "بعت آيفون 13 لمحمد بـ18 ألف، دفع 10 والباقي عليه"
        val draft = repository.parseTransactionIntent(input, TransactionInputModality.TEXT)

        assertEquals(UnifiedTransactionType.SALE, draft.transactionType)
        assertEquals(TransactionInputModality.TEXT, draft.inputModality)
        assertEquals(1800000L, draft.totalAmountMinor)
        assertEquals(1000000L, draft.paidAmountMinor)
        assertEquals(800000L, draft.remainingDebtMinor)
        assertEquals("PARTIAL", draft.paymentMode)
        assertNotNull(draft.counterpartyId)
        assertTrue(draft.counterpartyName?.contains("محمد") == true)
        assertTrue(draft.items.isNotEmpty())
    }

    @Test
    fun testTextToExpenseTransactionDraftExtraction() = runBlocking {
        val input = "مصروف كهرباء 500 جنيه"
        val draft = repository.parseTransactionIntent(input, TransactionInputModality.TEXT)

        assertEquals(UnifiedTransactionType.EXPENSE, draft.transactionType)
        assertEquals(50000L, draft.totalAmountMinor)
        assertEquals(50000L, draft.paidAmountMinor)
        assertEquals("مرافق وفواتير", draft.expenseCategoryName)
    }

    @Test
    fun testVoiceToTransactionDraftExtraction() = runBlocking {
        val voiceInput = "سحبت 2000 من فودافون كاش"
        val draft = repository.parseTransactionIntent(voiceInput, TransactionInputModality.VOICE)

        assertEquals(UnifiedTransactionType.WALLET_WITHDRAW, draft.transactionType)
        assertEquals(TransactionInputModality.VOICE, draft.inputModality)
        assertEquals(200000L, draft.totalAmountMinor)
        assertEquals("WALLET", draft.paymentMode)
    }

    @Test
    fun testNeverExecuteWithoutExplicitUserConfirmation() = runBlocking {
        val input = "مصروف كهرباء 500 جنيه"
        val draft = repository.parseTransactionIntent(input, TransactionInputModality.TEXT)

        // Attempting to execute with userConfirmed = false MUST fail immediately
        val result = coordinator.executeConfirmedDraft(
            draft = draft,
            userConfirmed = false,
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )

        assertTrue(result is AppResult.Error)
        assertEquals("CONFIRMATION_REQUIRED", (result as AppResult.Error).code)
    }

    @Test
    fun testSmartValidationBlocksInsufficientStock() = runBlocking {
        val saleDraft = TransactionDraft(
            transactionType = UnifiedTransactionType.SALE,
            inputModality = TransactionInputModality.MANUAL,
            counterpartyId = "CUST-MOHAMED",
            items = listOf(
                com.example.transaction.TransactionDraftItem(
                    productId = "PRD-TEST-IP13",
                    productNameArabic = "آيفون 13 سعة 128",
                    sku = "IP13-128",
                    quantity = 50, // Available stock is only 10
                    unitPriceMinor = 1800000L
                )
            ),
            totalAmountMinor = 50 * 1800000L,
            paidAmountMinor = 50 * 1800000L
        )

        val validation = validationEngine.validateDraft(
            draft = saleDraft,
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
        )

        assertFalse(validation.isValid)
        assertFalse(validation.canProceedToConfirmation)
        assertTrue(validation.stockShortageItems.isNotEmpty())
    }

    @Test
    fun testSmartValidationBlocksCreditSaleWithoutCustomer() = runBlocking {
        val creditSaleWithoutCustomer = TransactionDraft(
            transactionType = UnifiedTransactionType.SALE,
            inputModality = TransactionInputModality.MANUAL,
            counterpartyId = null, // Missing customer for credit sale
            items = listOf(
                com.example.transaction.TransactionDraftItem(
                    productId = "PRD-TEST-IP13",
                    productNameArabic = "آيفون 13 سعة 128",
                    sku = "IP13-128",
                    quantity = 1,
                    unitPriceMinor = 1800000L
                )
            ),
            totalAmountMinor = 1800000L,
            paidAmountMinor = 500000L // 13000 debt remaining
        )

        val validation = validationEngine.validateDraft(
            draft = creditSaleWithoutCustomer,
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
        )

        assertFalse(validation.canProceedToConfirmation)
        assertTrue(validation.errorMessagesArabic.any { it.contains("عميل") })
    }

    @Test
    fun testConfirmedExecutionUpdatesLedgerAndAudit() = runBlocking {
        val input = "مصروف صيانة 300 جنيه"
        val draft = repository.parseTransactionIntent(input, TransactionInputModality.TEXT)

        val execResult = repository.executeConfirmedTransactionDraft(
            draft = draft,
            userConfirmed = true,
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )

        assertTrue("Execution should succeed: $execResult", execResult is AppResult.Success)
        val successData = (execResult as AppResult.Success).data
        assertTrue(successData.isSuccess)
        assertNotNull(successData.executedTransactionId)

        // Verify audit log entry
        val auditLogs = database.systemIntegrityDao().getAllAuditLogsChronological(MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(auditLogs.any { it.actionType == "UNIFIED_TRANSACTION_EXECUTION" })
    }
}

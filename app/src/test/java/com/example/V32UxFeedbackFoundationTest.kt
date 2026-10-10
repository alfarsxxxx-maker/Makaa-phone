package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.InventoryMovementType
import com.example.financial.SaleLineInput
import com.example.repository.MekkaPhoneRepository
import com.example.ui.components.ConfirmationRiskLevel
import com.example.ui.components.LocalizedFormValidator
import com.example.ui.components.MekkaSemanticStyleCatalog
import com.example.ui.components.OperationIdempotencyGuard
import com.example.ui.components.UxErrorSanitizer
import com.example.ui.components.UxFeedbackStatus
import com.example.ui.components.UxOperationState
import com.example.ui.components.UxSuccessMessageBuilder
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
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V32UxFeedbackFoundationTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = MekkaPhoneDatabase.createInMemoryForTesting(context)
        repository = MekkaPhoneRepository(database)
        repository.ensureFoundationInitialized()
        OperationIdempotencyGuard.clearAllForTesting()
    }

    @After
    fun tearDown() {
        OperationIdempotencyGuard.clearAllForTesting()
        database.close()
    }

    @Test
    fun section3_and_4_standardStatusModel_and_semanticVisualSystem_neverRelyOnColorAlone() {
        val expectedStatuses = setOf(
            UxFeedbackStatus.IDLE,
            UxFeedbackStatus.LOADING,
            UxFeedbackStatus.PROCESSING,
            UxFeedbackStatus.SUCCESS,
            UxFeedbackStatus.WARNING,
            UxFeedbackStatus.ERROR,
            UxFeedbackStatus.PENDING,
            UxFeedbackStatus.OFFLINE,
            UxFeedbackStatus.SYNCING,
            UxFeedbackStatus.SYNCED,
            UxFeedbackStatus.SYNC_FAILED,
            UxFeedbackStatus.CANCELLED
        )
        assertEquals(12, expectedStatuses.size)

        expectedStatuses.forEach { status ->
            val style = MekkaSemanticStyleCatalog.styleFor(status)
            assertEquals(status, style.status)
            assertTrue("Status ${status.name} must have non-blank Arabic label", style.labelArabic.isNotBlank())
            assertNotNull("Status ${status.name} must have an icon (never rely on color alone)", style.icon)
        }
    }

    @Test
    fun section8_and_9_errorSanitizer_hidesTechnicalExceptions_and_providesActionableArabicGuidance() {
        // 1. Technical exceptions must be stripped from user-facing messages
        val rawTechnicalError = "android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed: products.sku (code 2067)\n\tat org.sqlite.database.sqlite.SQLiteConnection.nativeExecuteForChangedRowCount(Native Method)"
        val sanitized = UxErrorSanitizer.sanitizeForUser(rawTechnicalError)
        assertFalse("Must never show SQLiteConstraintException to user", sanitized.contains("SQLiteConstraintException"))
        assertFalse("Must never show stack trace to user", sanitized.contains("at org.sqlite"))
        assertTrue("Must explain problem in Arabic", sanitized.contains("تعذر"))

        val npeSanitized = UxErrorSanitizer.sanitizeForUser("java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference")
        assertFalse("Must never show NullPointerException to user", npeSanitized.contains("NullPointerException"))
        assertTrue(npeSanitized.contains("تعذر حفظ العملية"))
        assertTrue(npeSanitized.contains("لم يتم تغيير البيانات"))

        val http500Sanitized = UxErrorSanitizer.sanitizeForUser("HTTP 500 Internal Server Error at https://api.example.com/sync")
        assertFalse("Must never show raw HTTP 500 to user", http500Sanitized.contains("HTTP 500"))

        // 2. Actionable error for stock shortage (Problem + Reason + Action: «مراجعة الكمية»)
        val stockError = UxErrorSanitizer.toActionableError(
            rawMessage = "INSUFFICIENT_STOCK: الكمية المطلوبة غير متوفرة في المخزون",
            operationNameArabic = "حفظ البيع"
        )
        assertTrue(stockError.problemArabic.contains("تعذر حفظ البيع لأن الكمية المطلوبة غير متوفرة"))
        assertEquals("مراجعة الكمية", stockError.actionLabelArabic)
        assertTrue("Technical diagnostic details must be preserved for Diagnostics", stockError.technicalDiagnosticDetails.contains("INSUFFICIENT_STOCK"))

        // 3. Actionable error for offline/sync failure (only when local save succeeded)
        val syncError = UxErrorSanitizer.toActionableError(
            rawMessage = "Unable to resolve host: Network timeout",
            operationNameArabic = "المزامنة",
            localSaveSucceeded = true
        )
        assertTrue(syncError.problemArabic.contains("تعذر الاتصال بالخادم"))
        assertTrue(syncError.persistenceNoteArabic.contains("تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا"))
        assertEquals("إعادة المحاولة", syncError.actionLabelArabic)
    }

    @Test
    fun section6_and_7_successMessageSystem_and_realStateSyncRequirement() {
        val saleSuccessLocalOnly = UxSuccessMessageBuilder.saleCompleted(
            invoiceNumber = "INV-1001",
            totalFormattedArabic = "2,500.00 ج.م",
            isRemoteSynced = false
        )
        assertTrue(saleSuccessLocalOnly.titleArabic.contains("تم تسجيل البيع بنجاح"))
        assertTrue(
            "When remote backend has not acknowledged sync, must state local persistence awaiting sync",
            saleSuccessLocalOnly.persistenceAndSyncNoteArabic.contains("تم حفظ العملية على الجهاز")
        )
        assertFalse(
            "Must NOT claim remote sync succeeded before server ACK",
            saleSuccessLocalOnly.persistenceAndSyncNoteArabic.contains("وتمت مزامنتها مع الخادم السحابي")
        )

        val purchaseSuccess = UxSuccessMessageBuilder.purchaseCompleted(
            productsCount = 3,
            invoiceNumber = "PUR-2001",
            isRemoteSynced = false
        )
        assertTrue(purchaseSuccess.titleArabic.contains("تم إضافة 3 منتجات للمخزون"))

        val customerPaymentSuccess = UxSuccessMessageBuilder.customerPaymentRecorded(
            amountFormattedArabic = "1,500 جنيه",
            customerNameArabic = "محمد علي"
        )
        assertTrue(customerPaymentSuccess.titleArabic.contains("تم تسجيل دفعة العميل بقيمة 1,500 جنيه"))

        val backupSuccess = UxSuccessMessageBuilder.backupCreated(
            fileName = "makkaphone-backup-v1.json",
            recordsCount = 42
        )
        assertEquals("تم إنشاء النسخة الاحتياطية بنجاح", backupSuccess.titleArabic)
    }

    @Test
    fun section12_doubleTapProtection_and_domainIdempotencyGuard() {
        val opKey = "SALE_EXECUTION"
        val fingerprint = "CUST1|PROD1:1:150000|150000"

        // First tap acquires lock
        val firstAcquire = OperationIdempotencyGuard.tryAcquireOperation(opKey, fingerprint, cooldownWindowMs = 2_000L)
        assertTrue("First execution must be allowed", firstAcquire)
        assertTrue("Operation must be marked in-flight", OperationIdempotencyGuard.isOperationInFlight(opKey))

        // Immediate second tap while in-flight must be blocked
        val duplicateWhileInFlight = OperationIdempotencyGuard.tryAcquireOperation(opKey, fingerprint, cooldownWindowMs = 2_000L)
        assertFalse("Duplicate tap while operation is in-flight must be blocked", duplicateWhileInFlight)

        // Release with cooldown: immediate re-tap with identical payload within cooldown window must also be blocked
        OperationIdempotencyGuard.releaseOperation(opKey, recordCompletionTime = true)
        assertFalse("Operation must no longer be in-flight after release", OperationIdempotencyGuard.isOperationInFlight(opKey))

        val rapidDuplicateAfterCompletion = OperationIdempotencyGuard.tryAcquireOperation(opKey, fingerprint, cooldownWindowMs = 5_000L)
        assertFalse("Rapid duplicate tap with identical fingerprint within cooldown window must be blocked", rapidDuplicateAfterCompletion)

        // Different payload fingerprint is allowed immediately
        val differentPayloadAcquire = OperationIdempotencyGuard.tryAcquireOperation(opKey, "CUST2|PROD2:1:200000|200000", cooldownWindowMs = 5_000L)
        assertTrue("Different operation payload must be allowed after previous completed", differentPayloadAcquire)
        OperationIdempotencyGuard.releaseOperation(opKey, recordCompletionTime = false)
    }

    @Test
    fun section14_and_19_confirmationLevels_and_localizedFormValidation() {
        // Section 14: 3-tier confirmation levels
        assertEquals(3, ConfirmationRiskLevel.entries.size)
        assertTrue(ConfirmationRiskLevel.LEVEL_1_SAFE.arabicLabel.isNotBlank())
        assertTrue(ConfirmationRiskLevel.LEVEL_2_MODERATE.arabicLabel.isNotBlank())
        assertTrue(ConfirmationRiskLevel.LEVEL_3_HIGH_RISK.arabicLabel.isNotBlank())

        // Section 19: Localized Egyptian phone validation («أدخل رقم هاتف صحيح.»)
        val invalidPhone = LocalizedFormValidator.validateEgyptianPhone("12345", required = true)
        assertFalse(invalidPhone.isValid)
        assertEquals("أدخل رقم هاتف صحيح.", invalidPhone.errorMessageArabic)

        val validPhone = LocalizedFormValidator.validateEgyptianPhone("01012345678", required = true)
        assertTrue(validPhone.isValid)

        val validArabicDigitsPhone = LocalizedFormValidator.validateEgyptianPhone("٠١١٢٣٤٥٦٧٨٩", required = true)
        assertTrue("Arabic digits must be normalized and accepted", validArabicDigitsPhone.isValid)
    }

    @Test
    fun section1_and_7_realCommitBeforeSuccess_saleAndBackupOperations() = runBlocking {
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")

        // Create product and stock
        val prodRes = repository.createProduct(
            nameArabic = "شاحن أصلي سامسونج 25W",
            sku = "SAM-CHG-25W",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-SAMSUNG",
            isSerialized = false,
            defaultSellingPriceMinor = 650_00L
        )
        assertTrue(prodRes is AppResult.Success)
        val product = (prodRes as AppResult.Success).data

        val stockRes = repository.recordAuthorizedInventoryMovement(
            productId = product.localId,
            movementType = InventoryMovementType.ADJUSTMENT_IN,
            quantity = 5,
            unitCostMinor = 450_00L,
            reason = "رصيد افتتاحي للاختبار"
        )
        assertTrue(stockRes is AppResult.Success)

        // Attempt sale with insufficient stock (10 > 5) -> must fail and NOT commit
        val failedSale = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = product.localId,
                    serialNumberId = null,
                    quantity = 10,
                    unitSellingPriceMinor = 650_00L
                )
            )
        )
        assertTrue("Sale exceeding stock must fail", failedSale is AppResult.Error)
        val actionableErr = UxErrorSanitizer.toActionableError(
            rawMessage = (failedSale as AppResult.Error).message,
            operationNameArabic = "حفظ البيع"
        )
        assertEquals("مراجعة الكمية", actionableErr.actionLabelArabic)

        // Valid sale (2 units) -> commits atomically
        val validSale = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = product.localId,
                    serialNumberId = null,
                    quantity = 2,
                    unitSellingPriceMinor = 650_00L
                )
            )
        )
        assertTrue("Valid sale must succeed", validSale is AppResult.Success)

        // Verified backup -> only reports success after real creation and validation
        val backupRes = repository.createVerifiedBackup()
        assertTrue("Verified backup must succeed", backupRes is AppResult.Success)
        val createdBackup = (backupRes as AppResult.Success).data
        val validationRes = repository.validateBackupJson(createdBackup.payloadJson)
        assertTrue("Created backup must pass SHA-256 and schema validation", validationRes.isValid)
        assertTrue("Created backup checksum must match", validationRes.isChecksumMatched)
    }
}

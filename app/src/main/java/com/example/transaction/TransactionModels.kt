package com.example.transaction

import com.example.auth.PermissionCode
import com.example.core.IdGenerator
import com.example.database.entities.FinancialTransactionType
import com.example.financial.Money

/**
 * Supported Unified Transaction Types in Mekka Phone V4.1 (Sections 2 & 5).
 * Fully mapped to the existing Atomic Double-Entry Ledger and Inventory Core.
 */
enum class UnifiedTransactionType(
    val titleArabic: String,
    val financialType: FinancialTransactionType,
    val requiredPermission: PermissionCode,
    val isDirectionInflow: Boolean,
    val affectsInventory: Boolean
) {
    SALE(
        titleArabic = "فاتورة بيع",
        financialType = FinancialTransactionType.SALE,
        requiredPermission = PermissionCode.SALE_CREATE,
        isDirectionInflow = true,
        affectsInventory = true
    ),
    PURCHASE(
        titleArabic = "فاتورة شراء بضاعة",
        financialType = FinancialTransactionType.PURCHASE,
        requiredPermission = PermissionCode.PURCHASE_CREATE,
        isDirectionInflow = false,
        affectsInventory = true
    ),
    EXPENSE(
        titleArabic = "مصروف تشغيلي",
        financialType = FinancialTransactionType.EXPENSE,
        requiredPermission = PermissionCode.EXPENSE_CREATE,
        isDirectionInflow = false,
        affectsInventory = false
    ),
    CASH_IN(
        titleArabic = "توريد نقدية للخزينة",
        financialType = FinancialTransactionType.CASH_IN,
        requiredPermission = PermissionCode.CASH_OPERATE,
        isDirectionInflow = true,
        affectsInventory = false
    ),
    CASH_OUT(
        titleArabic = "صرف نقدية من الخزينة",
        financialType = FinancialTransactionType.CASH_OUT,
        requiredPermission = PermissionCode.CASH_OPERATE,
        isDirectionInflow = false,
        affectsInventory = false
    ),
    WALLET_DEPOSIT(
        titleArabic = "إيداع محفظة إلكترونية",
        financialType = FinancialTransactionType.DEPOSIT,
        requiredPermission = PermissionCode.WALLET_OPERATE,
        isDirectionInflow = true,
        affectsInventory = false
    ),
    WALLET_WITHDRAW(
        titleArabic = "سحب من محفظة إلكترونية",
        financialType = FinancialTransactionType.WITHDRAW,
        requiredPermission = PermissionCode.WALLET_OPERATE,
        isDirectionInflow = false,
        affectsInventory = false
    ),
    WALLET_TRANSFER(
        titleArabic = "تحويل بين محافظ",
        financialType = FinancialTransactionType.TRANSFER,
        requiredPermission = PermissionCode.WALLET_OPERATE,
        isDirectionInflow = false,
        affectsInventory = false
    ),
    CUSTOMER_PAYMENT(
        titleArabic = "تحصيل مستحقات عميل (سداد آجل)",
        financialType = FinancialTransactionType.CASH_IN,
        requiredPermission = PermissionCode.SALE_CREATE,
        isDirectionInflow = true,
        affectsInventory = false
    ),
    SUPPLIER_PAYMENT(
        titleArabic = "سداد مديونية مورد",
        financialType = FinancialTransactionType.CASH_OUT,
        requiredPermission = PermissionCode.PURCHASE_CREATE,
        isDirectionInflow = false,
        affectsInventory = false
    ),
    MAINTENANCE_PAYMENT(
        titleArabic = "تحصيل صيانة وأتعاب فحص",
        financialType = FinancialTransactionType.CASH_IN,
        requiredPermission = PermissionCode.MAINTENANCE_MANAGE,
        isDirectionInflow = true,
        affectsInventory = false
    ),
    REFUND(
        titleArabic = "مرتجع مالي / رد مبيعات",
        financialType = FinancialTransactionType.REFUND,
        requiredPermission = PermissionCode.REFUND_APPROVE,
        isDirectionInflow = false,
        affectsInventory = true
    ),
    INVENTORY_ADJUSTMENT(
        titleArabic = "تسوية جرد مخزني",
        financialType = FinancialTransactionType.ADJUSTMENT,
        requiredPermission = PermissionCode.INVENTORY_ADJUST,
        isDirectionInflow = false,
        affectsInventory = true
    )
}

/**
 * Multi-Modal Input Source (Section 1).
 */
enum class TransactionInputModality(val titleArabic: String, val iconName: String) {
    MANUAL("إدخال يدوي", "Edit"),
    TEXT("أمر نصي ذكي", "Chat"),
    VOICE("أمر صوتي", "Mic"),
    SMS("رسالة بنكية / محفظة", "Sms"),
    CAMERA_OCR("قراءة كاميرا / إيصال", "Camera"),
    BARCODE("مسح باركود", "QrCode"),
    IMEI_SERIAL("مسح سيريال IMEI", "PhonelinkRing")
}

/**
 * Line item in a Transaction Draft.
 */
data class TransactionDraftItem(
    val lineId: String = IdGenerator.newPrefixedId("LINE"),
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val barcode: String? = null,
    val quantity: Int = 1,
    val unitPriceMinor: Long,
    val availableStockQty: Int = 0,
    val serialNumberId: String? = null,
    val serialOrImei: String? = null,
    val isSerialized: Boolean = false
) {
    val totalLineMinor: Long
        get() = quantity.toLong() * unitPriceMinor
}

/**
 * Ambiguity Option presented to user when an input matches multiple items (Section 8).
 */
data class AmbiguityOption(
    val optionId: String,
    val labelArabic: String,
    val secondaryHintArabic: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents ambiguous or missing data requiring user disambiguation.
 */
data class TransactionAmbiguity(
    val ambiguityKey: String,
    val questionArabic: String,
    val candidateOptions: List<AmbiguityOption>
)

/**
 * Central Transaction Draft generated by the Intelligent Pipeline (Section 2 & 3).
 * STRICTLY MUST be validated, reviewed, and confirmed before hitting the ledger!
 */
data class TransactionDraft(
    val draftId: String = IdGenerator.newPrefixedId("DRFT"),
    val transactionType: UnifiedTransactionType,
    val inputModality: TransactionInputModality,
    val rawPromptOrInput: String? = null,
    val counterpartyId: String? = null,
    val counterpartyName: String? = null,
    val counterpartyPhone: String? = null,
    val items: List<TransactionDraftItem> = emptyList(),
    val totalAmountMinor: Long = 0L,
    val paidAmountMinor: Long = 0L,
    val invoiceDiscountMinor: Long = 0L,
    val paymentMode: String = "CASH", // CASH, WALLET, CREDIT, PARTIAL
    val targetAccountId: String? = null, // Drawer ID or Wallet ID
    val targetAccountNameArabic: String? = null,
    val expenseCategoryName: String? = null,
    val notes: String = "",
    val confidencePercent: Int = 100, // 0..100
    val ambiguities: List<TransactionAmbiguity> = emptyList(),
    val missingRequirementsArabic: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val remainingDebtMinor: Long
        get() = (totalAmountMinor - paidAmountMinor).coerceAtLeast(0L)

    val isBalancedAndReadyForReview: Boolean
        get() = confidencePercent >= 70 && ambiguities.isEmpty() && missingRequirementsArabic.isEmpty() && totalAmountMinor > 0L
}

/**
 * Comprehensive Smart Validation Outcome (Section 11).
 */
data class TransactionDraftValidation(
    val isValid: Boolean,
    val canProceedToConfirmation: Boolean,
    val errorMessagesArabic: List<String> = emptyList(),
    val warningMessagesArabic: List<String> = emptyList(),
    val missingRolePermission: PermissionCode? = null,
    val stockShortageItems: List<String> = emptyList()
)

/**
 * Real Transaction Execution Result (Section 2 & 3).
 */
data class TransactionExecutionResult(
    val isSuccess: Boolean,
    val executedTransactionId: String? = null,
    val referenceInvoiceNumber: String? = null,
    val summaryArabic: String,
    val auditLogId: String? = null,
    val errorArabic: String? = null
)

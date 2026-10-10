package com.example.financial

/**
 * P0 Requirement 10: Authoritative Unified Wallet Domain Operation Type.
 * Eliminates arbitrary String constants and enforces exact operational semantics across
 * UI, ViewModel, Domain, Repository, Ledger, Reports, and Tests:
 *
 * - CUSTOMER_DEPOSIT: Customer pays cash to shop drawer, shop transfers e-money to customer's phone.
 *   Posting: Cash Drawer +Amount (+Commission), Wallet -Amount (-ProviderCost).
 *
 * - CUSTOMER_WITHDRAWAL: Customer transfers e-money to shop wallet, shop gives physical cash to customer.
 *   Posting: Wallet +Amount, Cash Drawer -Amount (+Commission).
 *
 * - WALLET_TRANSFER: Internal transfer between two shop wallets (Source -Amount, Target +Amount).
 *
 * - CASH_TO_WALLET: Internal funding of shop wallet from cash drawer (Cash Drawer -Amount, Wallet +Amount).
 *
 * - WALLET_TO_CASH: Internal liquidation of shop wallet into cash drawer (Wallet -Amount, Cash Drawer +Amount).
 *
 * - WALLET_ADJUSTMENT: Discrepancy reconciliation between system ledger and provider statement.
 */
enum class WalletOperationType(
    val arabicLabel: String,
    val arabicDescription: String,
    val isCustomerFacing: Boolean
) {
    CUSTOMER_DEPOSIT(
        arabicLabel = "إيداع للعميل (تحويل صادر)",
        arabicDescription = "استلام نقدية من العميل وتغذية محفظة العميل (الخزينة تزيد، المحفظة تنقص)",
        isCustomerFacing = true
    ),
    CUSTOMER_WITHDRAWAL(
        arabicLabel = "سحب للعميل (استلام وارد)",
        arabicDescription = "استلام رصيد بمحفظة المحل وصرف نقدية للعميل (المحفظة تزيد، الخزينة تنقص)",
        isCustomerFacing = true
    ),
    WALLET_TRANSFER(
        arabicLabel = "تحويل بين المحافظ",
        arabicDescription = "تحويل رصيد داخلي من محفظة إلى أخرى (المحفظة المصدر تنقص، المحفظة المستلمة تزيد)",
        isCustomerFacing = false
    ),
    CASH_TO_WALLET(
        arabicLabel = "تغذية محفظة من الخزينة",
        arabicDescription = "إيداع نقدي من خزينة المحل لتغذية رصيد المحفظة (الخزينة تنقص، المحفظة تزيد)",
        isCustomerFacing = false
    ),
    WALLET_TO_CASH(
        arabicLabel = "تسييل محفظة إلى الخزينة",
        arabicDescription = "سحب رصيد من المحفظة وتوريده للخزينة (المحفظة تنقص، الخزينة تزيد)",
        isCustomerFacing = false
    ),
    WALLET_ADJUSTMENT(
        arabicLabel = "تسوية رصيد محفظة",
        arabicDescription = "قيد تسوية محاسبي مطابق لجرد المحفظة الفعلي عبر الأستاذ",
        isCustomerFacing = false
    );

    companion object {
        fun fromCode(code: String): WalletOperationType = when (code.uppercase().trim()) {
            "CUSTOMER_DEPOSIT", "WALLET_SEND", "SEND", "DEPOSIT" -> CUSTOMER_DEPOSIT
            "CUSTOMER_WITHDRAWAL", "WALLET_WITHDRAW", "WALLET_RECEIVE", "RECEIVE", "WITHDRAW" -> CUSTOMER_WITHDRAWAL
            "WALLET_TRANSFER", "TRANSFER" -> WALLET_TRANSFER
            "CASH_TO_WALLET", "WALLET_TOPUP", "TOPUP" -> CASH_TO_WALLET
            "WALLET_TO_CASH" -> WALLET_TO_CASH
            "WALLET_ADJUSTMENT", "ADJUSTMENT" -> WALLET_ADJUSTMENT
            else -> throw IllegalArgumentException("نوع عملية المحفظة غير معروف: $code")
        }

        fun fromCodeOrNull(code: String?): WalletOperationType? = if (code.isNullOrBlank()) null else try {
            fromCode(code)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Backward compatibility alias for existing code.
 */
typealias WalletDomainOperationType = WalletOperationType

val WalletOperationType.Companion.WALLET_DEPOSIT: WalletOperationType get() = WalletOperationType.CUSTOMER_DEPOSIT
val WalletOperationType.Companion.WALLET_WITHDRAW: WalletOperationType get() = WalletOperationType.CUSTOMER_WITHDRAWAL

/**
 * Operational Status for Electronic Wallets (P0-C).
 * Wallets in SUSPENDED or DISABLED state cannot execute transactions.
 */
enum class WalletOperationalStatus(val arabicLabel: String, val canTransact: Boolean) {
    ACTIVE("نشطة ومتاحة للعمليات", true),
    SUSPENDED("معلقة مؤقتاً", false),
    DISABLED("معطلة نهائياً", false)
}

/**
 * Reconciliation Classification for Cash and Wallets (P0-B & P0-C).
 */
enum class ReconciliationStatus(val arabicLabel: String) {
    MATCHED("مطابق تماماً"),
    SHORTAGE("عجز في الرصيد الفعلي"),
    OVERAGE("زيادة / فائض في الرصيد الفعلي");

    companion object {
        fun of(varianceMinor: Long): ReconciliationStatus = when {
            varianceMinor < 0L -> SHORTAGE
            varianceMinor > 0L -> OVERAGE
            else -> MATCHED
        }
    }
}

data class WalletReconciliationResult(
    val walletId: String,
    val walletNameArabic: String,
    val providerCode: String,
    val systemDerivedBalanceMinor: Long,
    val actualCountedBalanceMinor: Long,
    val varianceMinor: Long,
    val status: ReconciliationStatus,
    val adjustmentTransactionId: String? = null,
    val auditLogId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

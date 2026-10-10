package com.example.financial

import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.SupplierBalanceProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.abs

/**
 * Normal balance side for Double-Entry Accounting accounts.
 */
enum class NormalBalanceSide {
    DEBIT,
    CREDIT
}

/**
 * Standard financial account classification in the Chart of Accounts.
 */
enum class AccountClassification(val normalSide: NormalBalanceSide, val arabicTitle: String) {
    ASSET(NormalBalanceSide.DEBIT, "أصول (Assets)"),
    LIABILITY(NormalBalanceSide.CREDIT, "التزامات / خصوم (Liabilities)"),
    EQUITY(NormalBalanceSide.CREDIT, "حقوق الملكية (Equity)"),
    REVENUE(NormalBalanceSide.CREDIT, "إيرادات (Revenue)"),
    CONTRA_REVENUE(NormalBalanceSide.DEBIT, "مردودات ومسموحات مبيعات (Contra-Revenue)"),
    COGS(NormalBalanceSide.DEBIT, "تكلفة البضاعة المباعة (COGS)"),
    EXPENSE(NormalBalanceSide.DEBIT, "مصروفات تشغيلية ورسوم (Expenses)")
}

/**
 * Account definition in the extensible Chart of Accounts (Requirement 19).
 */
data class ChartOfAccountItem(
    val accountCode: String,
    val nameArabic: String,
    val nameEnglish: String,
    val classification: AccountClassification,
    val isSystemAccount: Boolean = true
)

/**
 * Individual Debit or Credit posting line within a Double-Entry Journal Entry (Requirement 18 & 20).
 */
data class JournalLine(
    val lineId: String,
    val accountCode: String,
    val accountNameArabic: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val memoArabic: String = "",
    val subLedgerPartyId: String? = null
)

/**
 * Immutable Double-Entry Journal Entry header and lines (Requirements 18, 20, 21, 22, 23, 24, 25).
 * Domain rule: `totalDebitMinor == totalCreditMinor` and `totalDebitMinor > 0`.
 */
data class JournalEntry(
    val journalEntryId: String,
    val shopId: String,
    val ledgerTransactionId: String,
    val referenceNumber: String,
    val transactionType: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val isBalanced: Boolean,
    val lines: List<JournalLine>,
    val createdByUserId: String,
    val deviceId: String,
    val timestamp: Long
)

/**
 * Trial Balance line derived from Double-Entry Journal postings.
 */
data class TrialBalanceAccountSummary(
    val accountCode: String,
    val accountNameArabic: String,
    val classification: AccountClassification,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val netBalanceMinor: Long
)

/**
 * Complete Trial Balance report verifying `totalDebitsMinor == totalCreditsMinor` across the shop.
 */
data class TrialBalanceReport(
    val shopId: String,
    val journalEntriesCount: Int,
    val totalDebitsMinor: Long,
    val totalCreditsMinor: Long,
    val isBalanced: Boolean,
    val accountSummaries: List<TrialBalanceAccountSummary>
)

/**
 * Extensible Chart of Accounts & Double-Entry Accounting Engine (Requirements 18–25).
 * Enforces `Total Debits == Total Credits` in the domain layer before any financial transaction is committed.
 */
object AccountingEngine {

    const val ACC_CASH_DRAWER = "1000"
    const val ACC_VODAFONE_CASH = "1010"
    const val ACC_ORANGE_CASH = "1020"
    const val ACC_ETISALAT_MONEY = "1030"
    const val ACC_INSTAPAY = "1040"
    const val ACC_AXIS_PAY = "1050"
    const val ACC_ACCOUNTS_RECEIVABLE = "1100"
    const val ACC_INVENTORY = "1200"
    const val ACC_ACCOUNTS_PAYABLE = "2000"
    const val ACC_OWNER_EQUITY = "3000"
    const val ACC_SALES_REVENUE = "4000"
    const val ACC_SALES_RETURNS = "4100"
    const val ACC_WALLET_COMMISSIONS_REVENUE = "4200"
    const val ACC_COST_OF_GOODS_SOLD = "5000"
    const val ACC_OPERATING_EXPENSES = "6000"
    const val ACC_WALLET_FEES_EXPENSE = "7000"

    val standardChartOfAccounts: List<ChartOfAccountItem> = listOf(
        ChartOfAccountItem(ACC_CASH_DRAWER, "النقدية بالخزينة", "Cash Drawer", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_VODAFONE_CASH, "محفظة فودافون كاش", "Vodafone Cash", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_ORANGE_CASH, "محفظة أورنج كاش", "Orange Cash", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_ETISALAT_MONEY, "محفظة اتصالات كاش (e& money)", "e& money", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_INSTAPAY, "حساب إنستا باي (InstaPay)", "InstaPay", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_AXIS_PAY, "محفظة أكسس باي (Axis Pay)", "Axis Pay", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_ACCOUNTS_RECEIVABLE, "العملاء — الذمم المدينة", "Accounts Receivable", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_INVENTORY, "مخزون البضاعة", "Merchandise Inventory", AccountClassification.ASSET),
        ChartOfAccountItem(ACC_ACCOUNTS_PAYABLE, "الموردون — الذمم الدائنة", "Accounts Payable", AccountClassification.LIABILITY),
        ChartOfAccountItem(ACC_OWNER_EQUITY, "رأس المال وجاري المالك", "Owner's Equity & Capital", AccountClassification.EQUITY),
        ChartOfAccountItem(ACC_SALES_REVENUE, "إيرادات المبيعات والصيانة", "Sales & Service Revenue", AccountClassification.REVENUE),
        ChartOfAccountItem(ACC_SALES_RETURNS, "مردودات ومسموحات المبيعات", "Sales Returns & Allowances", AccountClassification.CONTRA_REVENUE),
        ChartOfAccountItem(ACC_WALLET_COMMISSIONS_REVENUE, "إيرادات عمولات المحافظ", "Wallet Commissions Revenue", AccountClassification.REVENUE),
        ChartOfAccountItem(ACC_COST_OF_GOODS_SOLD, "تكلفة البضاعة المباعة (COGS)", "Cost of Goods Sold", AccountClassification.COGS),
        ChartOfAccountItem(ACC_OPERATING_EXPENSES, "المصروفات التشغيلية", "Operating Expenses", AccountClassification.EXPENSE),
        ChartOfAccountItem(ACC_WALLET_FEES_EXPENSE, "رسوم ومصاريف المحافظ", "Wallet Fees & Charges", AccountClassification.EXPENSE)
    )

    private val accountByCode: Map<String, ChartOfAccountItem> =
        standardChartOfAccounts.associateBy { it.accountCode }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val journalAdapter = moshi.adapter(JournalEntry::class.java)

    fun getAccount(code: String): ChartOfAccountItem =
        accountByCode[code] ?: ChartOfAccountItem(code, "حساب محاسبي ($code)", "Account $code", AccountClassification.ASSET)

    fun resolveWalletAccountCode(providerCodeOrHint: String?): String {
        val hint = providerCodeOrHint?.uppercase().orEmpty()
        return when {
            hint.contains("ORANGE") -> ACC_ORANGE_CASH
            hint.contains("ETISALAT") || hint.contains("E&") -> ACC_ETISALAT_MONEY
            hint.contains("INSTAPAY") || hint.contains("IPN") -> ACC_INSTAPAY
            hint.contains("AXIS") -> ACC_AXIS_PAY
            else -> ACC_VODAFONE_CASH
        }
    }

    /**
     * Validates that a list of JournalLines satisfies the strict Double-Entry Accounting Rule:
     * 1. At least 2 lines (at least 1 debit and 1 credit).
     * 2. No line has negative amounts or both debit and credit > 0 simultaneously.
     * 3. `totalDebitMinor == totalCreditMinor` and `totalDebitMinor > 0`.
     */
    fun validateAndBuildJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        transactionType: String,
        lines: List<JournalLine>,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<JournalEntry> {
        if (lines.size < 2) {
            return AppResult.Error(
                "القيد المحاسبي المزدوج يتطلب طرفين على الأقل (مدين ودائن)",
                "JOURNAL_MIN_LINES_VIOLATION"
            )
        }
        for (line in lines) {
            if (line.debitMinor < 0L || line.creditMinor < 0L) {
                return AppResult.Error(
                    "لا يُسمح بقيم سالبة في سطور القيد المحاسبي المزدوج",
                    "JOURNAL_NEGATIVE_LINE_VIOLATION"
                )
            }
            if ((line.debitMinor == 0L && line.creditMinor == 0L) ||
                (line.debitMinor > 0L && line.creditMinor > 0L)
            ) {
                return AppResult.Error(
                    "كل سطر في القيد المحاسبي يجب أن يكون إما مديناً أو دائناً بقيمة أكبر من الصفر",
                    "JOURNAL_INVALID_LINE_SIDE"
                )
            }
        }

        val totalDebit = lines.sumOf { it.debitMinor }
        val totalCredit = lines.sumOf { it.creditMinor }
        if (totalDebit <= 0L || totalDebit != totalCredit) {
            return AppResult.Error(
                "رفض القيد المحاسبي: عدم توازن القيد المزدوج (إجمالي المدين $totalDebit != إجمالي الدائن $totalCredit)",
                "UNBALANCED_DOUBLE_ENTRY_JOURNAL"
            )
        }

        return AppResult.Success(
            JournalEntry(
                journalEntryId = IdGenerator.newPrefixedId("JRN"),
                shopId = shopId,
                ledgerTransactionId = ledgerTransactionId,
                referenceNumber = referenceNumber,
                transactionType = transactionType,
                totalDebitMinor = totalDebit,
                totalCreditMinor = totalCredit,
                isBalanced = true,
                lines = lines,
                createdByUserId = createdByUserId,
                deviceId = deviceId,
                timestamp = timestamp
            )
        )
    }

    /**
     * Builds a balanced Double-Entry JournalEntry for a Sale (Cash, Partial, or Credit) + COGS/Inventory (Requirement 21).
     */
    fun buildSaleJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        totalSaleAmountMinor: Long,
        paidCashAmountMinor: Long,
        totalCogsMinor: Long,
        customerId: String?,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long
    ): AppResult<JournalEntry> {
        val lines = mutableListOf<JournalLine>()
        val actualPaid = paidCashAmountMinor.coerceIn(0L, totalSaleAmountMinor)
        val receivableMinor = (totalSaleAmountMinor - actualPaid).coerceAtLeast(0L)

        // 1. Revenue recognition: DR Cash and/or DR Accounts Receivable -> CR Sales Revenue
        if (actualPaid > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_CASH_DRAWER,
                    debitMinor = actualPaid,
                    creditMinor = 0L,
                    memo = "تحصيل نقدي مبيعات ($referenceNumber)"
                )
            )
        }
        if (receivableMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_ACCOUNTS_RECEIVABLE,
                    debitMinor = receivableMinor,
                    creditMinor = 0L,
                    memo = "ذمم مدينة مبيعات آجلة ($referenceNumber)",
                    partyId = customerId
                )
            )
        }
        lines.add(
            makeLine(
                accountCode = ACC_SALES_REVENUE,
                debitMinor = 0L,
                creditMinor = totalSaleAmountMinor,
                memo = "إيراد مبيعات ($referenceNumber)",
                partyId = customerId
            )
        )

        // 2. Perpetual Inventory COGS recognition: DR COGS (5000) -> CR Inventory (1200)
        if (totalCogsMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_COST_OF_GOODS_SOLD,
                    debitMinor = totalCogsMinor,
                    creditMinor = 0L,
                    memo = "تكلفة البضاعة المباعة WAC ($referenceNumber)"
                )
            )
            lines.add(
                makeLine(
                    accountCode = ACC_INVENTORY,
                    debitMinor = 0L,
                    creditMinor = totalCogsMinor,
                    memo = "صرف مخزون بضاعة مباعة ($referenceNumber)"
                )
            )
        }

        return validateAndBuildJournalEntry(
            shopId = shopId,
            ledgerTransactionId = ledgerTransactionId,
            referenceNumber = referenceNumber,
            transactionType = "SALE",
            lines = lines,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Builds a balanced Double-Entry JournalEntry for a Purchase (Cash, Partial, or Credit) (Requirement 22).
     */
    fun buildPurchaseJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        totalPurchaseAmountMinor: Long,
        paidCashAmountMinor: Long,
        supplierId: String,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long
    ): AppResult<JournalEntry> {
        val lines = mutableListOf<JournalLine>()
        val actualPaid = paidCashAmountMinor.coerceIn(0L, totalPurchaseAmountMinor)
        val payableMinor = (totalPurchaseAmountMinor - actualPaid).coerceAtLeast(0L)

        // DR Inventory (1200)
        lines.add(
            makeLine(
                accountCode = ACC_INVENTORY,
                debitMinor = totalPurchaseAmountMinor,
                creditMinor = 0L,
                memo = "توريد بضاعة للمخزن ($referenceNumber)",
                partyId = supplierId
            )
        )
        // CR Cash (1000) and/or CR Accounts Payable (2000)
        if (actualPaid > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_CASH_DRAWER,
                    debitMinor = 0L,
                    creditMinor = actualPaid,
                    memo = "سداد نقدي لمشتريات ($referenceNumber)",
                    partyId = supplierId
                )
            )
        }
        if (payableMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_ACCOUNTS_PAYABLE,
                    debitMinor = 0L,
                    creditMinor = payableMinor,
                    memo = "استحقاق مورد آجل ($referenceNumber)",
                    partyId = supplierId
                )
            )
        }

        return validateAndBuildJournalEntry(
            shopId = shopId,
            ledgerTransactionId = ledgerTransactionId,
            referenceNumber = referenceNumber,
            transactionType = "PURCHASE",
            lines = lines,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Builds a balanced Double-Entry JournalEntry for a Sales Return / Refund + COGS/Inventory Reversal (Requirement 24).
     */
    fun buildRefundJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        refundAmountMinor: Long,
        restoredCogsMinor: Long,
        customerId: String?,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long,
        cashRefundAmountMinor: Long = refundAmountMinor,
        receivableReductionMinor: Long = 0L
    ): AppResult<JournalEntry> {
        val lines = mutableListOf<JournalLine>()
        // 1. Revenue reversal: DR Sales Returns (4100) -> CR Cash (1000) and/or CR Accounts Receivable (1100)
        lines.add(
            makeLine(
                accountCode = ACC_SALES_RETURNS,
                debitMinor = refundAmountMinor,
                creditMinor = 0L,
                memo = "مردودات مبيعات ($referenceNumber)",
                partyId = customerId
            )
        )
        if (cashRefundAmountMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_CASH_DRAWER,
                    debitMinor = 0L,
                    creditMinor = cashRefundAmountMinor,
                    memo = "رد نقدية مرتجع مبيعات ($referenceNumber)",
                    partyId = customerId
                )
            )
        }
        if (receivableReductionMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_ACCOUNTS_RECEIVABLE,
                    debitMinor = 0L,
                    creditMinor = receivableReductionMinor,
                    memo = "تخفيض ذمم مدينة مقابل مرتجع مبيعات ($referenceNumber)",
                    partyId = customerId
                )
            )
        }

        // 2. Inventory & COGS reversal: DR Inventory (1200) -> CR COGS (5000)
        if (restoredCogsMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_INVENTORY,
                    debitMinor = restoredCogsMinor,
                    creditMinor = 0L,
                    memo = "إعادة بضاعة مرتجعة للمخزن ($referenceNumber)"
                )
            )
            lines.add(
                makeLine(
                    accountCode = ACC_COST_OF_GOODS_SOLD,
                    debitMinor = 0L,
                    creditMinor = restoredCogsMinor,
                    memo = "عكس تكلفة البضاعة المباعة للمرتجع ($referenceNumber)"
                )
            )
        }

        return validateAndBuildJournalEntry(
            shopId = shopId,
            ledgerTransactionId = ledgerTransactionId,
            referenceNumber = referenceNumber,
            transactionType = "REFUND",
            lines = lines,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Builds a balanced Double-Entry JournalEntry for a Purchase Return to Supplier:
     * DR Accounts Payable (2000) and/or DR Cash Drawer (1000) -> CR Inventory (1200).
     */
    fun buildPurchaseReturnJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        returnTotalMinor: Long,
        payableReductionMinor: Long,
        cashRefundInMinor: Long,
        supplierId: String,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long
    ): AppResult<JournalEntry> {
        val lines = mutableListOf<JournalLine>()
        if (payableReductionMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_ACCOUNTS_PAYABLE,
                    debitMinor = payableReductionMinor,
                    creditMinor = 0L,
                    memo = "تخفيض استحقاق مورد مقابل مرتجع مشتريات ($referenceNumber)",
                    partyId = supplierId
                )
            )
        }
        if (cashRefundInMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_CASH_DRAWER,
                    debitMinor = cashRefundInMinor,
                    creditMinor = 0L,
                    memo = "استرداد نقدي من مورد مقابل مرتجع مشتريات ($referenceNumber)",
                    partyId = supplierId
                )
            )
        }
        lines.add(
            makeLine(
                accountCode = ACC_INVENTORY,
                debitMinor = 0L,
                creditMinor = returnTotalMinor,
                memo = "صرف بضاعة مرتجعة للمورد ($referenceNumber)",
                partyId = supplierId
            )
        )

        return validateAndBuildJournalEntry(
            shopId = shopId,
            ledgerTransactionId = ledgerTransactionId,
            referenceNumber = referenceNumber,
            transactionType = "PURCHASE_RETURN",
            lines = lines,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Builds a balanced Double-Entry JournalEntry for any general ledger transaction
     * (Expense, Wallet Deposit/Withdraw/Transfer, Customer Collection, Supplier Payment, Cash In/Out, Reversal).
     */
    fun buildGeneralLedgerJournalEntry(
        shopId: String,
        ledgerTransactionId: String,
        referenceNumber: String,
        transactionType: FinancialTransactionType,
        amountMinor: Long,
        feeMinor: Long,
        commissionMinor: Long,
        sourceAccountType: FinancialAccountType,
        sourceAccountId: String,
        destinationAccountType: FinancialAccountType,
        destinationAccountId: String,
        walletProviderHint: String? = null,
        customerId: String? = null,
        supplierId: String? = null,
        createdByUserId: String,
        deviceId: String,
        timestamp: Long
    ): AppResult<JournalEntry> {
        val srcCode = mapFinancialAccountToChartCode(
            accountType = sourceAccountType,
            accountId = sourceAccountId,
            transactionType = transactionType,
            isSource = true,
            walletProviderHint = walletProviderHint
        )
        val dstCode = mapFinancialAccountToChartCode(
            accountType = destinationAccountType,
            accountId = destinationAccountId,
            transactionType = transactionType,
            isSource = false,
            walletProviderHint = walletProviderHint
        )

        val lines = mutableListOf<JournalLine>()
        val partyId = customerId ?: supplierId

        // Main transfer/movement: DR Destination Account, CR Source Account
        lines.add(
            makeLine(
                accountCode = dstCode,
                debitMinor = amountMinor,
                creditMinor = 0L,
                memo = "مدين: ${transactionType.arabicLabel} ($referenceNumber)",
                partyId = partyId
            )
        )
        lines.add(
            makeLine(
                accountCode = srcCode,
                debitMinor = 0L,
                creditMinor = amountMinor,
                memo = "دائن: ${transactionType.arabicLabel} ($referenceNumber)",
                partyId = partyId
            )
        )

        // Wallet / Transfer Fee: DR Wallet Fees (7000) -> CR Source Account
        if (feeMinor > 0L) {
            lines.add(
                makeLine(
                    accountCode = ACC_WALLET_FEES_EXPENSE,
                    debitMinor = feeMinor,
                    creditMinor = 0L,
                    memo = "رسوم عملية ($referenceNumber)"
                )
            )
            lines.add(
                makeLine(
                    accountCode = srcCode,
                    debitMinor = 0L,
                    creditMinor = feeMinor,
                    memo = "خصم رسوم العملية من الحساب المصدر ($referenceNumber)"
                )
            )
        }

        // Commission earned: DR Cash Drawer (1000) for inflows/sends, or DR Wallet (dstCode) for WITHDRAW where net cash left drawer
        if (commissionMinor > 0L) {
            val commissionAssetAccountCode = if (
                transactionType == FinancialTransactionType.WITHDRAW &&
                destinationAccountType == FinancialAccountType.WALLET
            ) {
                dstCode
            } else {
                ACC_CASH_DRAWER
            }
            lines.add(
                makeLine(
                    accountCode = commissionAssetAccountCode,
                    debitMinor = commissionMinor,
                    creditMinor = 0L,
                    memo = "تحصيل عمولة محفظة ($referenceNumber)"
                )
            )
            lines.add(
                makeLine(
                    accountCode = ACC_WALLET_COMMISSIONS_REVENUE,
                    debitMinor = 0L,
                    creditMinor = commissionMinor,
                    memo = "إيراد عمولة محفظة ($referenceNumber)"
                )
            )
        }

        return validateAndBuildJournalEntry(
            shopId = shopId,
            ledgerTransactionId = ledgerTransactionId,
            referenceNumber = referenceNumber,
            transactionType = transactionType.name,
            lines = lines,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    fun encodeJournalEntryToJson(entry: JournalEntry): String =
        journalAdapter.toJson(entry)

    fun decodeJournalEntryFromJson(jsonStr: String): JournalEntry? =
        runCatching {
            if (jsonStr.isBlank() || !jsonStr.contains("journalEntryId")) null
            else journalAdapter.fromJson(jsonStr)
        }.getOrNull()

    /**
     * Computes a complete Trial Balance Report across all posted transactions in the shop,
     * reconstructing any legacy transactions if needed and verifying `totalDebits == totalCredits`.
     */
    fun computeTrialBalance(
        shopId: String,
        transactions: List<TransactionEntity>
    ): TrialBalanceReport {
        val activeTransactions = transactions.filter {
            it.shopId == shopId && it.status == LedgerTransactionStatus.POSTED
        }

        val debitTotals = mutableMapOf<String, Long>()
        val creditTotals = mutableMapOf<String, Long>()
        var entriesCount = 0

        for (tx in activeTransactions) {
            val journal = decodeJournalEntryFromJson(tx.metadataJson)
                ?: (buildGeneralLedgerJournalEntry(
                    shopId = tx.shopId,
                    ledgerTransactionId = tx.localId,
                    referenceNumber = tx.referenceNumber,
                    transactionType = tx.transactionType,
                    amountMinor = tx.amountMinor,
                    feeMinor = tx.feeMinor,
                    commissionMinor = tx.commissionMinor,
                    sourceAccountType = tx.sourceAccountType,
                    sourceAccountId = tx.sourceAccountId,
                    destinationAccountType = tx.destinationAccountType,
                    destinationAccountId = tx.destinationAccountId,
                    customerId = tx.customerId,
                    supplierId = tx.supplierId,
                    createdByUserId = tx.createdByUserId,
                    deviceId = tx.deviceId,
                    timestamp = tx.transactionTimestamp
                ) as? AppResult.Success)?.data

            if (journal != null) {
                entriesCount++
                for (line in journal.lines) {
                    debitTotals[line.accountCode] = (debitTotals[line.accountCode] ?: 0L) + line.debitMinor
                    creditTotals[line.accountCode] = (creditTotals[line.accountCode] ?: 0L) + line.creditMinor
                }
            }
        }

        val allCodes = (standardChartOfAccounts.map { it.accountCode } + debitTotals.keys + creditTotals.keys).distinct().sorted()
        val summaries = allCodes.mapNotNull { code ->
            val dr = debitTotals[code] ?: 0L
            val cr = creditTotals[code] ?: 0L
            if (dr == 0L && cr == 0L) return@mapNotNull null
            val account = getAccount(code)
            val net = if (account.classification.normalSide == NormalBalanceSide.DEBIT) {
                dr - cr
            } else {
                cr - dr
            }
            TrialBalanceAccountSummary(
                accountCode = code,
                accountNameArabic = account.nameArabic,
                classification = account.classification,
                totalDebitMinor = dr,
                totalCreditMinor = cr,
                netBalanceMinor = net
            )
        }

        val grandDebit = summaries.sumOf { it.totalDebitMinor }
        val grandCredit = summaries.sumOf { it.totalCreditMinor }

        return TrialBalanceReport(
            shopId = shopId,
            journalEntriesCount = entriesCount,
            totalDebitsMinor = grandDebit,
            totalCreditsMinor = grandCredit,
            isBalanced = grandDebit == grandCredit,
            accountSummaries = summaries
        )
    }

    private fun mapFinancialAccountToChartCode(
        accountType: FinancialAccountType,
        accountId: String,
        transactionType: FinancialTransactionType,
        isSource: Boolean,
        walletProviderHint: String?
    ): String = when (accountType) {
        FinancialAccountType.CASH_DRAWER -> ACC_CASH_DRAWER
        FinancialAccountType.WALLET -> resolveWalletAccountCode(walletProviderHint ?: accountId)
        FinancialAccountType.EXTERNAL_CUSTOMER -> when (transactionType) {
            FinancialTransactionType.SALE -> if (isSource) ACC_SALES_REVENUE else ACC_ACCOUNTS_RECEIVABLE
            FinancialTransactionType.REFUND -> if (isSource) ACC_ACCOUNTS_RECEIVABLE else ACC_SALES_RETURNS
            else -> ACC_ACCOUNTS_RECEIVABLE
        }
        FinancialAccountType.EXTERNAL_SUPPLIER -> when (transactionType) {
            FinancialTransactionType.PURCHASE -> if (isSource) ACC_ACCOUNTS_PAYABLE else ACC_INVENTORY
            else -> ACC_ACCOUNTS_PAYABLE
        }
        FinancialAccountType.OPERATING_EXPENSE -> ACC_OPERATING_EXPENSES
        FinancialAccountType.REVENUE_COMMISSION -> ACC_WALLET_COMMISSIONS_REVENUE
        FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT -> when (accountId) {
            "MAINTENANCE_SERVICE_REVENUE" -> ACC_SALES_REVENUE
            else -> ACC_OWNER_EQUITY
        }
    }

    private fun makeLine(
        accountCode: String,
        debitMinor: Long,
        creditMinor: Long,
        memo: String,
        partyId: String? = null
    ): JournalLine {
        val acc = getAccount(accountCode)
        return JournalLine(
            lineId = IdGenerator.newPrefixedId("JRL"),
            accountCode = accountCode,
            accountNameArabic = acc.nameArabic,
            debitMinor = debitMinor,
            creditMinor = creditMinor,
            memoArabic = memo,
            subLedgerPartyId = partyId
        )
    }
}

// ============================================================================
// MEKKA PHONE V3 PART 2 — DOMAIN BUSINESS RULES & FINANCIAL PROJECTIONS
// ============================================================================

/**
 * User-friendly wallet operation types (Rule 12: never expose WALLET_TOPUP/WALLET_SEND/WALLET_RECEIVE in normal UI).
 */
enum class WalletUserOperationType(
    val arabicLabel: String,
    val arabicDescription: String,
    val internalTransferKind: String,
    val requiresWalletOutflow: Boolean,
    val requiresCashDrawerOutflow: Boolean
) {
    DEPOSIT(
        arabicLabel = "إيداع",
        arabicDescription = "إيداع رصيد لمحفظة العميل واستلام النقدية بالدرج",
        internalTransferKind = "SEND",
        requiresWalletOutflow = true,
        requiresCashDrawerOutflow = false
    ),
    WITHDRAWAL(
        arabicLabel = "سحب",
        arabicDescription = "سحب رصيد من محفظة العميل وصرف النقدية من الدرج",
        internalTransferKind = "RECEIVE",
        requiresWalletOutflow = false,
        requiresCashDrawerOutflow = true
    ),
    TRANSFER(
        arabicLabel = "تحويل",
        arabicDescription = "تحويل رصيد لمحفظة أخرى مع احتساب الرسوم والعمولة",
        internalTransferKind = "SEND",
        requiresWalletOutflow = true,
        requiresCashDrawerOutflow = false
    )
}

/**
 * Configurable Wallet Fee Calculation Result (Rule 14).
 */
data class WalletFeeBreakdown(
    val operationType: WalletUserOperationType,
    val amountMinor: Long,
    val networkFeeMinor: Long,
    val shopFeeMinor: Long,
    val customerTotalMinor: Long,
    val shopNetMinor: Long
)

data class WalletOperationValidation(
    val isValid: Boolean,
    val errorArabic: String? = null
)

/**
 * Pure domain engine for Wallet fee calculation and pre-execution validation (Rules 12, 13, 14, 15).
 */
object WalletDomainPolicyEngine {

    /**
     * Computes network fee, shop fee, customer total, and shop net strictly in the domain layer
     * based on the shop's business configuration and provider rules.
     */
    fun calculateFees(
        operationType: WalletUserOperationType,
        amountMinor: Long,
        providerCode: String,
        configuredShopCommissionBps: Int,
        customNetworkFeeOverrideMinor: Long? = null,
        customShopFeeOverrideMinor: Long? = null
    ): WalletFeeBreakdown {
        val safeAmount = amountMinor.coerceAtLeast(0L)
        if (safeAmount == 0L) {
            return WalletFeeBreakdown(
                operationType = operationType,
                amountMinor = 0L,
                networkFeeMinor = 0L,
                shopFeeMinor = 0L,
                customerTotalMinor = 0L,
                shopNetMinor = 0L
            )
        }

        // Configurable network fee rule by provider & operation type
        val defaultNetworkFeeBps = when {
            providerCode.equals("INSTAPAY", ignoreCase = true) -> 0
            operationType == WalletUserOperationType.WITHDRAWAL -> 0
            else -> 10 // 0.1% network transfer fee (capped/floored)
        }
        val calculatedNetworkFee = if (defaultNetworkFeeBps == 0) {
            0L
        } else {
            ((safeAmount * defaultNetworkFeeBps) / 10_000L).coerceIn(1_00L, 15_00L)
        }
        val networkFeeMinor = (customNetworkFeeOverrideMinor ?: calculatedNetworkFee).coerceAtLeast(0L)

        // Configurable shop fee (commission) rule from shop configuration (default 100 bps = 1%)
        val effectiveShopBps = configuredShopCommissionBps.coerceIn(0, 2000)
        val calculatedShopFee = if (effectiveShopBps == 0) {
            0L
        } else {
            ((safeAmount * effectiveShopBps) / 10_000L).coerceAtLeast(5_00L)
        }
        val shopFeeMinor = (customShopFeeOverrideMinor ?: calculatedShopFee).coerceAtLeast(0L)

        // Customer total:
        // - For DEPOSIT / TRANSFER: customer pays Amount + Network Fee + Shop Fee
        // - For WITHDRAWAL: customer receives Amount - Shop Fee (or pays Amount + Shop Fee from wallet)
        val customerTotalMinor = when (operationType) {
            WalletUserOperationType.DEPOSIT, WalletUserOperationType.TRANSFER ->
                safeAmount + networkFeeMinor + shopFeeMinor
            WalletUserOperationType.WITHDRAWAL ->
                (safeAmount - shopFeeMinor).coerceAtLeast(0L)
        }

        // Shop net profit/effect on this operation = Shop Fee - Network Fee (or net cash + wallet change)
        val shopNetMinor = shopFeeMinor - networkFeeMinor

        return WalletFeeBreakdown(
            operationType = operationType,
            amountMinor = safeAmount,
            networkFeeMinor = networkFeeMinor,
            shopFeeMinor = shopFeeMinor,
            customerTotalMinor = customerTotalMinor,
            shopNetMinor = shopNetMinor
        )
    }

    /**
     * Validates a wallet operation before execution (Rule 15).
     */
    fun validateOperation(
        walletId: String?,
        providerId: String?,
        operationType: WalletUserOperationType,
        amountMinor: Long?,
        customerPhoneOrAccount: String,
        walletBalanceMinor: Long,
        walletDailyRemainingMinor: Long,
        walletMonthlyRemainingMinor: Long,
        cashDrawerBalanceMinor: Long,
        feeBreakdown: WalletFeeBreakdown,
        roleCode: String
    ): WalletOperationValidation {
        if (walletId.isNullOrBlank() || providerId.isNullOrBlank()) {
            return WalletOperationValidation(false, "يرجى اختيار المحفظة / المزود أولاً")
        }
        if (amountMinor == null || amountMinor <= 0L) {
            return WalletOperationValidation(false, "يرجى إدخال مبلغ عملية صحيح أكبر من الصفر")
        }
        val normalizedPhone = Money.normalizeDigits(customerPhoneOrAccount).trim()
        if (normalizedPhone.length < 8) {
            return WalletOperationValidation(false, "يرجى إدخال رقم هاتف أو حساب عميل صحيح")
        }
        if (feeBreakdown.networkFeeMinor < 0L || feeBreakdown.shopFeeMinor < 0L) {
            return WalletOperationValidation(false, "حساب الرسوم غير صالح (لا تقبل القيم السالبة)")
        }
        val allowedRoles = setOf("OWNER", "MANAGER", "ACCOUNTANT", "CASHIER")
        if (roleCode.uppercase() !in allowedRoles) {
            return WalletOperationValidation(false, "الدور الوظيفي الحالي ($roleCode) غير مصرح له بتنفيذ عمليات المحافظ")
        }
        if (operationType.requiresWalletOutflow) {
            val totalRequiredFromWallet = amountMinor + feeBreakdown.networkFeeMinor
            if (walletBalanceMinor < totalRequiredFromWallet) {
                return WalletOperationValidation(
                    false,
                    "رصيد المحفظة غير كافٍ: المتاح ${Money.ofMinor(walletBalanceMinor).formatArabic()} والمطلوب ${Money.ofMinor(totalRequiredFromWallet).formatArabic()}"
                )
            }
            if (totalRequiredFromWallet > walletDailyRemainingMinor) {
                return WalletOperationValidation(
                    false,
                    "المبلغ يتجاوز الحد اليومي المتبقي للمحفظة (${Money.ofMinor(walletDailyRemainingMinor).formatArabic()})"
                )
            }
            if (totalRequiredFromWallet > walletMonthlyRemainingMinor) {
                return WalletOperationValidation(
                    false,
                    "المبلغ يتجاوز الحد الشهري المتبقي للمحفظة (${Money.ofMinor(walletMonthlyRemainingMinor).formatArabic()})"
                )
            }
        }
        if (operationType.requiresCashDrawerOutflow) {
            val cashOutRequired = (amountMinor - feeBreakdown.shopFeeMinor).coerceAtLeast(0L)
            if (cashDrawerBalanceMinor < cashOutRequired) {
                return WalletOperationValidation(
                    false,
                    "رصيد النقدية بالخزينة غير كافٍ لصرف مبلغ السحب (${Money.ofMinor(cashOutRequired).formatArabic()})"
                )
            }
        }
        return WalletOperationValidation(true, null)
    }

    fun friendlyOperationLabel(transferKind: String, notes: String = ""): String {
        val kind = transferKind.uppercase()
        return when {
            notes.contains("تحويل") -> "تحويل"
            notes.contains("إيداع") -> "إيداع"
            notes.contains("سحب") -> "سحب"
            kind.contains("RECEIVE") || kind.contains("CASH_OUT") -> "سحب"
            kind.contains("SEND") || kind.contains("TOPUP") || kind.contains("CASH_IN") -> "إيداع"
            else -> "تحويل"
        }
    }
}

/**
 * Maps internal technical enums to user-friendly Arabic terminology (Rules 9, 12, 16).
 */
object FriendlyTerminologyMapper {

    fun friendlyInventoryMovementCategory(movementType: InventoryMovementType, reason: String = ""): String {
        val r = reason.lowercase()
        return when {
            movementType == InventoryMovementType.DAMAGE || r.contains("تالف") || r.contains("damaged") -> "تالف"
            movementType == InventoryMovementType.TRANSFER_IN ||
                movementType == InventoryMovementType.TRANSFER_OUT ||
                r.contains("نقل") || r.contains("تحويل مخزني") || r.contains("transfer") -> "نقل"
            movementType == InventoryMovementType.PURCHASE_IN ||
                movementType == InventoryMovementType.RETURN_IN -> "دخول للمخزون"
            movementType == InventoryMovementType.SALE_OUT ||
                movementType == InventoryMovementType.MAINTENANCE_USAGE ||
                movementType == InventoryMovementType.RETURN_OUT -> "خروج من المخزون"
            movementType == InventoryMovementType.ADJUSTMENT_IN ||
                movementType == InventoryMovementType.ADJUSTMENT_OUT -> {
                if (r.contains("دخول للمخزون")) "دخول للمخزون"
                else if (r.contains("خروج من المخزون")) "خروج من المخزون"
                else "تسوية"
            }
            else -> if (movementType.isInbound) "دخول للمخزون" else "خروج من المخزون"
        }
    }

    fun friendlyCashTransactionLabel(tx: TransactionEntity): String {
        val desc = tx.descriptionArabic
        return when (tx.transactionType) {
            FinancialTransactionType.CASH_IN -> "إيداع نقدية"
            FinancialTransactionType.CASH_OUT -> {
                if (tx.relatedEntityType == "SUPPLIER_PAYMENT") "سداد لمورد" else "صرف نقدية"
            }
            FinancialTransactionType.ADJUSTMENT -> "تسوية فرق"
            FinancialTransactionType.SALE -> {
                if (tx.relatedEntityType == "MAINTENANCE") "تحصيل صيانة" else "مبيعات"
            }
            FinancialTransactionType.PURCHASE -> "سداد مشتريات"
            FinancialTransactionType.REFUND -> "رد نقدية مرتجع"
            FinancialTransactionType.EXPENSE -> "مصروف تشغيلي"
            FinancialTransactionType.DEPOSIT -> {
                when {
                    tx.relatedEntityType == "MAINTENANCE_DEPOSIT" -> "عربون صيانة"
                    tx.relatedEntityType == "CUSTOMER_COLLECTION" -> "تحصيل من عميل"
                    desc.contains("رصيد افتتاحي") -> "رصيد افتتاحي"
                    else -> "إيداع محفظة"
                }
            }
            FinancialTransactionType.WITHDRAW -> "سحب محفظة"
            FinancialTransactionType.TRANSFER -> {
                if (desc.contains("تحويل")) "تحويل محفظة" else "إيداع محفظة"
            }
            FinancialTransactionType.REVERSAL -> "قيد تصحيحي عكسي"
        }
    }

    fun friendlyProviderName(providerCode: String?): String {
        return when (providerCode?.uppercase()) {
            "VODAFONE_CASH" -> "فودافون كاش"
            "ORANGE_CASH" -> "أورنج كاش"
            "ETISALAT_CASH" -> "اتصالات كاش"
            "WE_PAY" -> "WE Pay"
            "INSTAPAY" -> "إنستا باي (InstaPay)"
            "FAWRY" -> "فوري (Fawry)"
            "AXIS_PAY" -> "أكسس باي (Axis Pay)"
            null, "", "UNKNOWN" -> "غير معروف"
            else -> providerCode
        }
    }

    fun friendlyAccountTypeLabel(accountType: FinancialAccountType): String = when (accountType) {
        FinancialAccountType.CASH_DRAWER -> "الخزينة النقدية"
        FinancialAccountType.WALLET -> "المحفظة الإلكترونية"
        FinancialAccountType.EXTERNAL_CUSTOMER -> "حساب العميل"
        FinancialAccountType.EXTERNAL_SUPPLIER -> "حساب المورد"
        FinancialAccountType.OPERATING_EXPENSE -> "مركز المصروفات التشغيلية"
        FinancialAccountType.REVENUE_COMMISSION -> "إيرادات وعمولات المتجر"
        FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT -> "حساب رأس المال والتسويات"
    }

    fun toFriendlyAccountArabic(accountType: FinancialAccountType, accountId: String? = null): String =
        friendlyAccountTypeLabel(accountType)

    fun toFriendlyAccountArabic(accountTypeOrCode: String): String = when (accountTypeOrCode.uppercase()) {
        "CASH_DRAWER", "1000" -> "الخزينة النقدية"
        "WALLET", "1010", "1020", "1030", "1040", "1050" -> "المحفظة الإلكترونية"
        "EXTERNAL_CUSTOMER", "1100" -> "حساب العميل"
        "EXTERNAL_SUPPLIER", "2000" -> "حساب المورد"
        "OPERATING_EXPENSE", "EXPENSE_CENTER", "6000" -> "مركز المصروفات التشغيلية"
        "REVENUE_COMMISSION", "4100", "4200" -> "إيرادات وعمولات المتجر"
        "EQUITY_OPENING_OR_ADJUSTMENT", "SYSTEM_ADJUSTMENT", "3000" -> "حساب رأس المال والتسويات"
        else -> accountTypeOrCode
    }

    fun friendlyLedgerStatusLabel(status: LedgerTransactionStatus, isReversalEntry: Boolean = false): String = when {
        status == LedgerTransactionStatus.REVERSED -> "معكوس بعملية تصحيح"
        isReversalEntry -> "قيد تصحيحي معتمد"
        status == LedgerTransactionStatus.POSTED -> "معتمد"
        else -> "معتمد"
    }

    fun toFriendlyStatusArabic(status: LedgerTransactionStatus, isReversalEntry: Boolean = false): String =
        friendlyLedgerStatusLabel(status, isReversalEntry)

    fun toFriendlyStatusArabic(status: String): String = when (status.uppercase()) {
        "POSTED", "COMPLETED", "PAID" -> "معتمد"
        "REVERSED" -> "معكوس بعملية تصحيح"
        "REFUNDED", "RETURNED" -> "مرتجع"
        "PARTIALLY_PAID" -> "دفع جزئي"
        "CREDIT", "UNPAID" -> "آجل"
        "OPEN" -> "مفتوحة"
        "CLOSED" -> "مغلقة"
        else -> status
    }

    fun toFriendlyOperationArabic(tx: TransactionEntity): String = friendlyCashTransactionLabel(tx)

    fun toFriendlyOperationArabic(
        opType: FinancialTransactionType,
        descriptionArabic: String? = null
    ): String {
        val desc = descriptionArabic.orEmpty()
        return when {
            opType == FinancialTransactionType.REVERSAL -> "قيد تصحيحي عكسي"
            desc.contains("تحصيل") -> "تحصيل من عميل"
            desc.contains("سداد") && desc.contains("مورد") -> "سداد لمورد"
            else -> opType.arabicLabel
        }
    }

    fun toFriendlyOperationArabic(opType: String): String = when (opType.uppercase()) {
        "SALE", "SALE_CREATE", "SALE_POST", "LEDGER_POST" -> "عملية بيع"
        "PURCHASE", "PURCHASE_CREATE" -> "توريد مشتريات"
        "DEPOSIT" -> "إيداع محفظة"
        "WITHDRAW" -> "سحب محفظة"
        "TRANSFER" -> "تحويل محفظة"
        "EXPENSE" -> "مصروف تشغيلي"
        "REFUND" -> "مرتجع مبيعات"
        "ADJUSTMENT" -> "قيد تسوية"
        "CASH_IN" -> "توريد نقدي للخزينة"
        "CASH_OUT" -> "صرف نقدي من الخزينة"
        "REVERSAL" -> "قيد تصحيحي عكسي"
        "INSERT" -> "إضافة سجل"
        "UPDATE" -> "تحديث سجل"
        "SOFT_DELETE" -> "أرشفة سجل"
        else -> opType
    }

    fun friendlyRoleLabel(roleCode: String): String = when (roleCode.uppercase()) {
        "OWNER" -> "مالك المتجر"
        "MANAGER" -> "مدير الفرع"
        "CASHIER" -> "كاشير ومبيعات"
        "TECHNICIAN" -> "مهندس صيانة"
        "INVENTORY" -> "مسؤول المخزون"
        "STAFF" -> "موظف"
        else -> roleCode
    }

    fun toFriendlyRoleArabic(roleCode: String): String = friendlyRoleLabel(roleCode)

    fun friendlyAuditActionLabel(actionType: String): String = when (actionType.uppercase()) {
        "SALE_CREATE", "SALE_POST", "SALE" -> "إصدار فاتورة مبيعات"
        "SALE_RETURN", "REFUND_CREATE", "REFUND" -> "اعتماد مرتجع مبيعات"
        "PURCHASE_CREATE", "PURCHASE_POST", "PURCHASE" -> "توريد فاتورة مشتريات"
        "INVENTORY_ADJUST" -> "تسوية وحركة مخزنية"
        "WALLET_TRANSFER", "WALLET_OPERATE", "WALLET_OP" -> "عملية محفظة إلكترونية"
        "CASH_OPERATION", "CASH_IN", "CASH_OUT", "CASH_ADJUST" -> "حركة خزينة نقدية"
        "CASH_CLOSEOUT" -> "إغلاق ومطابقة الوردية"
        "EXPENSE_CREATE", "EXPENSE" -> "صرف مصروف تشغيلي"
        "LEDGER_REVERSAL", "REVERSAL" -> "إصدار قيد تصحيحي عكسي"
        "BACKUP_CREATE" -> "إنشاء نسخة احتياطية مشفرة"
        "BACKUP_RESTORE" -> "استعادة نسخة احتياطية"
        "CONFLICT_RESOLUTION" -> "حسم تعارض مزامنة"
        "SHOP_SETTINGS_UPDATE" -> "تحديث إعدادات وهوية المتجر"
        "INITIAL_ONBOARDING_SETUP", "FIRST_LAUNCH_ONBOARDING" -> "إعداد وتأسيس مكة فون"
        "USER_CREATE", "USER_UPDATE", "PIN_CHANGE", "PERMISSION_CHANGE" -> "إدارة حساب وصلاحيات مستخدم"
        "SMS_CONFIRM" -> "اعتماد رسالة مالية"
        "SMS_REJECT" -> "رفض رسالة مالية"
        else -> "عملية موثقة بالنظام"
    }

    fun toFriendlyAuditActionArabic(actionType: String): String = friendlyAuditActionLabel(actionType)

    fun friendlyEntityTypeLabel(entityType: String): String = when (entityType) {
        "Sale", "SALE" -> "فاتورة مبيعات"
        "Purchase", "PURCHASE" -> "فاتورة مشتريات"
        "Refund", "REFUND" -> "مرتجع مبيعات"
        "InventoryMovement", "Product" -> "المخزون والأصناف"
        "Wallet", "Transfer", "WALLET_TRANSFER" -> "المحافظ الإلكترونية"
        "CashSession", "CashDrawer" -> "الخزينة والورديات"
        "MaintenanceOrder" -> "تذكرة صيانة"
        "Customer", "CUSTOMER_COLLECTION" -> "حساب العميل"
        "Supplier", "SUPPLIER_PAYMENT" -> "حساب المورد"
        "BackupMetadata" -> "النسخ الاحتياطي"
        "Shop", "AppSetting" -> "إعدادات المتجر"
        "User", "Role" -> "المستخدمون والأمان"
        "FinancialTransaction", "Transaction" -> "دفتر الأستاذ"
        "PendingTransaction", "SmsMessage" -> "الرسائل المالية"
        else -> entityType
    }
}

data class PartyStatementLine(
    val id: String,
    val timestamp: Long,
    val typeLabelArabic: String,
    val referenceNumber: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val runningBalanceMinor: Long,
    val descriptionArabic: String
)

data class CustomerFinancialProfile(
    val customerId: String,
    val currentBalanceMinor: Long,
    val totalPurchasesMinor: Long,
    val totalPaymentsMinor: Long,
    val totalRefundsMinor: Long,
    val outstandingAmountMinor: Long,
    val statementLines: List<PartyStatementLine>
)

data class CustomerCollectionPreview(
    val previousBalanceMinor: Long,
    val amountPaidMinor: Long,
    val newBalanceMinor: Long,
    val isValid: Boolean,
    val validationErrorArabic: String? = null
)

data class SupplierFinancialProfile(
    val supplierId: String,
    val currentBalanceMinor: Long,
    val totalPurchasesMinor: Long,
    val totalPaymentsMinor: Long,
    val outstandingAmountMinor: Long,
    val statementLines: List<PartyStatementLine>
)

data class SupplierPaymentPreview(
    val previousBalanceMinor: Long,
    val amountPaidMinor: Long,
    val newBalanceMinor: Long,
    val isValid: Boolean,
    val validationErrorArabic: String? = null
)

/**
 * Pure domain engine for Customer & Supplier Financial Statements and Collection/Payment validation (Rules 2, 3, 4, 29).
 */
object PartyAccountStatementEngine {

    fun buildCustomerProfile(
        customerId: String,
        derivedBalanceMinor: Long,
        sales: List<SaleEntity>,
        refunds: List<RefundEntity>,
        ledgerTransactions: List<TransactionEntity>
    ): CustomerFinancialProfile {
        val customerSales = sales.filter { it.customerId == customerId }
        val activeSales = customerSales.filter { it.status != "REFUNDED" }
        val saleIds = customerSales.map { it.localId }.toSet()
        val customerRefunds = refunds.filter { it.originalSaleId in saleIds }

        val totalPurchasesMinor = activeSales.sumOf { it.totalAmountMinor }
        val initialPaidOnSalesMinor = activeSales.sumOf { it.paidAmountMinor }
        val collectionTransactions = ledgerTransactions.filter {
            it.customerId == customerId &&
                it.status == LedgerTransactionStatus.POSTED &&
                it.relatedEntityType == "CUSTOMER_COLLECTION"
        }
        val collectionsMinor = collectionTransactions.sumOf { it.amountMinor }
        val totalPaymentsMinor = initialPaidOnSalesMinor + collectionsMinor
        val totalRefundsMinor = customerRefunds.sumOf { it.refundAmountMinor }
        val outstandingMinor = derivedBalanceMinor.coerceAtLeast(0L)

        // Build chronological statement events
        data class RawEvent(
            val id: String,
            val timestamp: Long,
            val label: String,
            val ref: String,
            val debit: Long,
            val credit: Long,
            val desc: String
        )

        val events = mutableListOf<RawEvent>()
        for (sale in customerSales) {
            events.add(
                RawEvent(
                    id = "SALE-${sale.localId}",
                    timestamp = sale.saleTimestamp,
                    label = "فاتورة مبيعات",
                    ref = sale.invoiceNumber,
                    debit = sale.totalAmountMinor,
                    credit = sale.paidAmountMinor,
                    desc = "إجمالي الفاتورة ${Money.ofMinor(sale.totalAmountMinor).formatArabic()} — المدفوع ${Money.ofMinor(sale.paidAmountMinor).formatArabic()}"
                )
            )
        }
        for (col in collectionTransactions) {
            events.add(
                RawEvent(
                    id = "COL-${col.localId}",
                    timestamp = col.transactionTimestamp,
                    label = "تحصيل دفعة",
                    ref = col.referenceNumber,
                    debit = 0L,
                    credit = col.amountMinor,
                    desc = col.descriptionArabic
                )
            )
        }
        for (ref in customerRefunds) {
            events.add(
                RawEvent(
                    id = "REF-${ref.localId}",
                    timestamp = ref.refundTimestamp,
                    label = "مرتجع مبيعات",
                    ref = ref.refundNumber,
                    debit = 0L,
                    credit = ref.refundAmountMinor,
                    desc = ref.reason
                )
            )
        }

        val sorted = events.sortedBy { it.timestamp }
        var running = 0L
        val statementLines = sorted.map { ev ->
            running += (ev.debit - ev.credit)
            PartyStatementLine(
                id = ev.id,
                timestamp = ev.timestamp,
                typeLabelArabic = ev.label,
                referenceNumber = ev.ref,
                debitMinor = ev.debit,
                creditMinor = ev.credit,
                runningBalanceMinor = running.coerceAtLeast(0L),
                descriptionArabic = ev.desc
            )
        }.reversed()

        return CustomerFinancialProfile(
            customerId = customerId,
            currentBalanceMinor = derivedBalanceMinor,
            totalPurchasesMinor = totalPurchasesMinor,
            totalPaymentsMinor = totalPaymentsMinor,
            totalRefundsMinor = totalRefundsMinor,
            outstandingAmountMinor = outstandingMinor,
            statementLines = statementLines
        )
    }

    /**
     * Validates Customer Collection and prevents overpayment unless explicitly supported (Rule 3).
     */
    fun evaluateCustomerCollection(
        currentBalanceMinor: Long,
        amountPaidMinor: Long?,
        allowOverpayment: Boolean = false
    ): CustomerCollectionPreview {
        val prev = currentBalanceMinor.coerceAtLeast(0L)
        if (amountPaidMinor == null || amountPaidMinor <= 0L) {
            return CustomerCollectionPreview(
                previousBalanceMinor = prev,
                amountPaidMinor = 0L,
                newBalanceMinor = prev,
                isValid = false,
                validationErrorArabic = "يرجى إدخال مبلغ تحصيل صحيح أكبر من الصفر"
            )
        }
        if (prev <= 0L && !allowOverpayment) {
            return CustomerCollectionPreview(
                previousBalanceMinor = prev,
                amountPaidMinor = amountPaidMinor,
                newBalanceMinor = prev - amountPaidMinor,
                isValid = false,
                validationErrorArabic = "لا توجد مستحقات قائمة على هذا العميل للتحصيل"
            )
        }
        if (amountPaidMinor > prev && !allowOverpayment) {
            return CustomerCollectionPreview(
                previousBalanceMinor = prev,
                amountPaidMinor = amountPaidMinor,
                newBalanceMinor = prev - amountPaidMinor,
                isValid = false,
                validationErrorArabic = "المبلغ المدخل (${Money.ofMinor(amountPaidMinor).formatArabic()}) يتجاوز المستحق الحالي على العميل (${Money.ofMinor(prev).formatArabic()})"
            )
        }
        return CustomerCollectionPreview(
            previousBalanceMinor = prev,
            amountPaidMinor = amountPaidMinor,
            newBalanceMinor = prev - amountPaidMinor,
            isValid = true,
            validationErrorArabic = null
        )
    }

    fun buildSupplierProfile(
        supplierId: String,
        derivedBalanceMinor: Long,
        purchases: List<PurchaseEntity>,
        ledgerTransactions: List<TransactionEntity>
    ): SupplierFinancialProfile {
        val supplierPurchases = purchases.filter { it.supplierId == supplierId }
        val totalPurchasesMinor = supplierPurchases.sumOf { it.totalAmountMinor }
        val initialPaidOnPurchasesMinor = supplierPurchases.sumOf { it.paidAmountMinor }
        val paymentTransactions = ledgerTransactions.filter {
            it.supplierId == supplierId &&
                it.status == LedgerTransactionStatus.POSTED &&
                it.relatedEntityType == "SUPPLIER_PAYMENT"
        }
        val purchaseReturnTransactions = ledgerTransactions.filter {
            it.supplierId == supplierId &&
                it.status == LedgerTransactionStatus.POSTED &&
                it.relatedEntityType == "PURCHASE_RETURN"
        }
        val extraPaymentsMinor = paymentTransactions.sumOf { it.amountMinor }
        val purchaseReturnsMinor = purchaseReturnTransactions.sumOf { it.amountMinor }
        val totalPaymentsMinor = initialPaidOnPurchasesMinor + extraPaymentsMinor
        val outstandingMinor = if (derivedBalanceMinor > 0L) {
            derivedBalanceMinor
        } else if (derivedBalanceMinor < 0L) {
            abs(derivedBalanceMinor)
        } else {
            (totalPurchasesMinor - totalPaymentsMinor - purchaseReturnsMinor).coerceAtLeast(0L)
        }

        data class RawEvent(
            val id: String,
            val timestamp: Long,
            val label: String,
            val ref: String,
            val debit: Long,
            val credit: Long,
            val desc: String
        )

        val events = mutableListOf<RawEvent>()
        for (pur in supplierPurchases) {
            events.add(
                RawEvent(
                    id = "PUR-${pur.localId}",
                    timestamp = pur.purchaseTimestamp,
                    label = "فاتورة توريد / شراء",
                    ref = pur.invoiceNumber,
                    debit = pur.paidAmountMinor,
                    credit = pur.totalAmountMinor,
                    desc = "إجمالي الفاتورة ${Money.ofMinor(pur.totalAmountMinor).formatArabic()} — المسدد ${Money.ofMinor(pur.paidAmountMinor).formatArabic()}"
                )
            )
        }
        for (pay in paymentTransactions) {
            events.add(
                RawEvent(
                    id = "PAY-${pay.localId}",
                    timestamp = pay.transactionTimestamp,
                    label = "سداد لمورد",
                    ref = pay.referenceNumber,
                    debit = pay.amountMinor,
                    credit = 0L,
                    desc = pay.descriptionArabic
                )
            )
        }
        for (ret in purchaseReturnTransactions) {
            events.add(
                RawEvent(
                    id = "PRET-${ret.localId}",
                    timestamp = ret.transactionTimestamp,
                    label = "مرتجع مشتريات للمورد",
                    ref = ret.referenceNumber,
                    debit = ret.amountMinor,
                    credit = 0L,
                    desc = ret.descriptionArabic
                )
            )
        }

        val sorted = events.sortedBy { it.timestamp }
        var runningPayable = 0L
        val statementLines = sorted.map { ev ->
            runningPayable += (ev.credit - ev.debit)
            PartyStatementLine(
                id = ev.id,
                timestamp = ev.timestamp,
                typeLabelArabic = ev.label,
                referenceNumber = ev.ref,
                debitMinor = ev.debit,
                creditMinor = ev.credit,
                runningBalanceMinor = runningPayable.coerceAtLeast(0L),
                descriptionArabic = ev.desc
            )
        }.reversed()

        return SupplierFinancialProfile(
            supplierId = supplierId,
            currentBalanceMinor = outstandingMinor,
            totalPurchasesMinor = totalPurchasesMinor,
            totalPaymentsMinor = totalPaymentsMinor,
            outstandingAmountMinor = outstandingMinor,
            statementLines = statementLines
        )
    }

    fun evaluateSupplierPayment(
        currentOutstandingMinor: Long,
        amountPaidMinor: Long?,
        allowOverpayment: Boolean = false
    ): SupplierPaymentPreview {
        val prev = currentOutstandingMinor.coerceAtLeast(0L)
        if (amountPaidMinor == null || amountPaidMinor <= 0L) {
            return SupplierPaymentPreview(
                previousBalanceMinor = prev,
                amountPaidMinor = 0L,
                newBalanceMinor = prev,
                isValid = false,
                validationErrorArabic = "يرجى إدخال مبلغ سداد صحيح أكبر من الصفر"
            )
        }
        if (amountPaidMinor > prev && !allowOverpayment && prev > 0L) {
            return SupplierPaymentPreview(
                previousBalanceMinor = prev,
                amountPaidMinor = amountPaidMinor,
                newBalanceMinor = (prev - amountPaidMinor).coerceAtLeast(0L),
                isValid = false,
                validationErrorArabic = "المبلغ المدخل (${Money.ofMinor(amountPaidMinor).formatArabic()}) يتجاوز المستحق للمورد (${Money.ofMinor(prev).formatArabic()})"
            )
        }
        return SupplierPaymentPreview(
            previousBalanceMinor = prev,
            amountPaidMinor = amountPaidMinor,
            newBalanceMinor = (prev - amountPaidMinor).coerceAtLeast(0L),
            isValid = true,
            validationErrorArabic = null
        )
    }
}

/**
 * Cash Closeout Evaluation & Authorization Policy Engine (Rule 17).
 */
data class CashCloseoutEvaluation(
    val expectedBalanceMinor: Long,
    val countedCashMinor: Long,
    val discrepancyMinor: Long,
    val hasDiscrepancy: Boolean,
    val isLargeDiscrepancy: Boolean,
    val requiresManagerAuthorization: Boolean,
    val isRoleAuthorized: Boolean,
    val isValidToSubmit: Boolean,
    val validationErrorArabic: String? = null
)

object CashCloseoutPolicyEngine {
    const val LARGE_DISCREPANCY_THRESHOLD_MINOR = 100_00L // 100.00 EGP

    fun evaluateCloseout(
        expectedBalanceMinor: Long,
        countedCashMinor: Long?,
        discrepancyReasonArabic: String,
        actorRoleCode: String,
        managerConfirmed: Boolean,
        largeDiscrepancyThresholdMinor: Long = LARGE_DISCREPANCY_THRESHOLD_MINOR
    ): CashCloseoutEvaluation {
        if (countedCashMinor == null || countedCashMinor < 0L) {
            return CashCloseoutEvaluation(
                expectedBalanceMinor = expectedBalanceMinor,
                countedCashMinor = 0L,
                discrepancyMinor = -expectedBalanceMinor,
                hasDiscrepancy = true,
                isLargeDiscrepancy = false,
                requiresManagerAuthorization = false,
                isRoleAuthorized = true,
                isValidToSubmit = false,
                validationErrorArabic = "يرجى إدخال النقدية الفعلية المعدودة بالخزينة"
            )
        }

        val discrepancy = countedCashMinor - expectedBalanceMinor
        val hasDiff = discrepancy != 0L
        val isLarge = abs(discrepancy) >= largeDiscrepancyThresholdMinor
        val isManagerOrOwner = actorRoleCode.uppercase() in setOf("OWNER", "MANAGER", "ACCOUNTANT")
        val authorized = if (!isLarge) true else (isManagerOrOwner || managerConfirmed)

        val error = when {
            hasDiff && discrepancyReasonArabic.isBlank() ->
                "يوجد فرق في الخزينة (${Money.ofMinor(discrepancy).formatArabic()}) — يجب تحديد سبب الفرق قبل الإغلاق"
            isLarge && !authorized ->
                "الفرق يتجاوز حد الصلاحية (${Money.ofMinor(largeDiscrepancyThresholdMinor).formatArabic()}) ويتطلب اعتماد المدير أو المالك"
            else -> null
        }

        return CashCloseoutEvaluation(
            expectedBalanceMinor = expectedBalanceMinor,
            countedCashMinor = countedCashMinor,
            discrepancyMinor = discrepancy,
            hasDiscrepancy = hasDiff,
            isLargeDiscrepancy = isLarge,
            requiresManagerAuthorization = isLarge,
            isRoleAuthorized = authorized,
            isValidToSubmit = error == null,
            validationErrorArabic = error
        )
    }
}

/**
 * Purchase Invoice Calculation Summary (Rules 10, 11, 29).
 */
data class PurchaseCalculationSummary(
    val subtotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val paidMinor: Long,
    val remainingPayableMinor: Long,
    val isValid: Boolean,
    val validationErrorArabic: String? = null
)

object PurchaseCalculationEngine {
    fun calculatePurchaseSummary(
        supplierId: String,
        lineSubtotalsMinor: List<Long>,
        discountMinor: Long,
        paidMinorOverride: Long?
    ): PurchaseCalculationSummary {
        val subtotal = lineSubtotalsMinor.sum()
        val safeDiscount = discountMinor.coerceIn(0L, subtotal)
        val netTotal = (subtotal - safeDiscount).coerceAtLeast(0L)
        val paid = (paidMinorOverride ?: netTotal).coerceIn(0L, netTotal)
        val remaining = (netTotal - paid).coerceAtLeast(0L)

        val error = when {
            supplierId.isBlank() -> "يرجى اختيار المورد بشكل صريح أولاً"
            lineSubtotalsMinor.isEmpty() || subtotal <= 0L -> "يرجى إضافة منتج واحد على الأقل بكمية وتكلفة صحيحة"
            discountMinor < 0L || discountMinor > subtotal -> "قيمة الخصم غير صحيحة"
            paidMinorOverride != null && (paidMinorOverride < 0L || paidMinorOverride > netTotal) ->
                "المبلغ المسدد لا يمكن أن يتجاوز صافي الفاتورة (${Money.ofMinor(netTotal).formatArabic()})"
            else -> null
        }

        return PurchaseCalculationSummary(
            subtotalMinor = subtotal,
            discountMinor = safeDiscount,
            netTotalMinor = netTotal,
            paidMinor = paid,
            remainingPayableMinor = remaining,
            isValid = error == null,
            validationErrorArabic = error
        )
    }
}

/**
 * Maintenance Ticket Financial Summary (Rules 18, 19, 20, 29).
 */
data class MaintenanceFinancialSummary(
    val orderId: String,
    val estimatedCostMinor: Long,
    val partsCostMinor: Long,
    val partsCustomerChargeMinor: Long,
    val effectiveTotalChargeMinor: Long,
    val paidAmountMinor: Long,
    val remainingAmountMinor: Long,
    val isFullyPaid: Boolean
)

object MaintenanceFinancialEngine {
    fun computeTicketFinancials(
        order: MaintenanceOrderEntity,
        parts: List<MaintenancePartEntity>,
        ledgerTransactions: List<TransactionEntity>
    ): MaintenanceFinancialSummary {
        val orderParts = parts.filter { it.maintenanceOrderId == order.localId }
        val partsCharge = orderParts.sumOf { it.unitChargeMinor * it.quantity.toLong() }
        val baseTotal = if (order.finalTotalMinor > 0L) order.finalTotalMinor else order.estimatedCostMinor
        val effectiveTotal = maxOf(baseTotal, order.estimatedCostMinor + partsCharge)

        val paidFromLedger = ledgerTransactions
            .filter {
                it.status == LedgerTransactionStatus.POSTED &&
                    it.relatedEntityId == order.localId &&
                    (it.relatedEntityType == "MAINTENANCE_DEPOSIT" || it.relatedEntityType == "MAINTENANCE")
            }
            .sumOf { it.amountMinor }

        val remaining = (effectiveTotal - paidFromLedger).coerceAtLeast(0L)
        return MaintenanceFinancialSummary(
            orderId = order.localId,
            estimatedCostMinor = order.estimatedCostMinor,
            partsCostMinor = order.partsCostMinor,
            partsCustomerChargeMinor = partsCharge,
            effectiveTotalChargeMinor = effectiveTotal,
            paidAmountMinor = paidFromLedger,
            remainingAmountMinor = remaining,
            isFullyPaid = remaining == 0L
        )
    }
}

/**
 * MEKKA PHONE V3 — PART 3: Authoritative Reports Center Domain Engine (Rules 1, 2, 3).
 * Never calculates financial truth in Compose UI; all 7 report sections are computed here
 * from the Accounting Engine, Ledger, and Inventory projections.
 */
enum class ReportCenterCategory(val arabicTitle: String) {
    SALES("المبيعات"),
    PROFITS("الأرباح"),
    CUSTOMERS("العملاء"),
    SUPPLIERS("الموردون"),
    INVENTORY("المخزون"),
    WALLETS("المحافظ"),
    MAINTENANCE("الصيانة")
}

enum class ReportPeriodFilter(val arabicLabel: String) {
    TODAY("اليوم"),
    LAST_7_DAYS("آخر 7 أيام"),
    THIS_MONTH("هذا الشهر"),
    ALL_TIME("كل الفترات")
}

typealias ReportPeriodPreset = ReportPeriodFilter

data class ReportsCenterFilter(
    val periodPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    val selectedProductId: String? = null,
    val selectedCustomerId: String? = null,
    val selectedSupplierId: String? = null,
    val selectedProviderCode: String? = null
)

data class DailySalesBarPoint(
    val dayLabel: String,
    val grossSalesMinor: Long,
    val invoiceCount: Int
)

data class TopProductReportItem(
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val unitsSold: Int,
    val revenueMinor: Long,
    val cogsMinor: Long,
    val grossProfitMinor: Long
) {
    val quantitySold: Int get() = unitsSold
    val totalRevenueMinor: Long get() = revenueMinor
}

data class ReportDrillDownItem(
    val id: String,
    val referenceNumber: String,
    val titleArabic: String,
    val subtitleArabic: String,
    val amountMinor: Long,
    val timestampMillis: Long,
    val statusArabic: String,
    val linkedCustomerId: String? = null,
    val linkedCustomerName: String? = null,
    val linkedProductId: String? = null,
    val linkedProductName: String? = null,
    val linkedWalletId: String? = null,
    val linkedWalletName: String? = null
)

data class SalesReportSection(
    val salesTodayMinor: Long = 0L,
    val salesInPeriodMinor: Long = 0L,
    val salesInvoicesCount: Int = 0,
    val returnsInPeriodMinor: Long = 0L,
    val returnsCount: Int = 0,
    val discountsInPeriodMinor: Long = 0L,
    val salesByDay: List<DailySalesBarPoint> = emptyList(),
    val topProducts: List<TopProductReportItem> = emptyList(),
    val filteredSales: List<SaleEntity> = emptyList(),
    val drillDownSales: List<ReportDrillDownItem> = emptyList()
) {
    val todaySalesMinor: Long get() = salesTodayMinor
    val periodSalesMinor: Long get() = salesInPeriodMinor
    val periodInvoiceCount: Int get() = salesInvoicesCount
    val periodReturnsMinor: Long get() = returnsInPeriodMinor
    val periodReturnCount: Int get() = returnsCount
    val periodDiscountsMinor: Long get() = discountsInPeriodMinor
    val netSalesMinor: Long get() = (salesInPeriodMinor - returnsInPeriodMinor).coerceAtLeast(0L)
}

data class ProfitsReportSection(
    val grossSalesRevenueMinor: Long = 0L,
    val returnsMinor: Long = 0L,
    val netSalesRevenueMinor: Long = 0L,
    val cogsMinor: Long = 0L,
    val walletCommissionsMinor: Long = 0L,
    val maintenanceRevenueMinor: Long = 0L,
    val grossProfitMinor: Long = 0L,
    val expensesMinor: Long = 0L,
    val netResultMinor: Long = 0L
) {
    val revenueMinor: Long get() = netSalesRevenueMinor
    val maintenanceNetRevenueMinor: Long get() = maintenanceRevenueMinor
}

data class CustomersReportSection(
    val totalReceivablesMinor: Long = 0L,
    val collectionsInPeriodMinor: Long = 0L,
    val customersWithDebtCount: Int = 0,
    val customerBalances: List<CustomerBalanceProjection> = emptyList(),
    val collectionTransactions: List<ReportDrillDownItem> = emptyList()
) {
    val periodCollectionsMinor: Long get() = collectionsInPeriodMinor
    val outstandingBalances: List<CustomerBalanceProjection> get() = customerBalances
}

data class SuppliersReportSection(
    val totalPayablesMinor: Long = 0L,
    val purchasesInPeriodMinor: Long = 0L,
    val paymentsInPeriodMinor: Long = 0L,
    val suppliersWithPayableCount: Int = 0,
    val supplierBalances: List<SupplierBalanceProjection> = emptyList()
) {
    val periodPurchasesMinor: Long get() = purchasesInPeriodMinor
    val periodPaymentsMinor: Long get() = paymentsInPeriodMinor
    val supplierRows: List<SupplierBalanceProjection> get() = supplierBalances
}

data class InventoryReportSection(
    val totalStockValueWacMinor: Long = 0L,
    val totalUnitsInStock: Int = 0,
    val lowStockProducts: List<ProductInventoryProjection> = emptyList(),
    val outOfStockProducts: List<ProductInventoryProjection> = emptyList(),
    val deadStockProducts: List<ProductInventoryProjection> = emptyList(),
    val topSellers: List<TopProductReportItem> = emptyList(),
    val allProductsValuation: List<ProductInventoryProjection> = emptyList()
) {
    val totalStockValueMinor: Long get() = totalStockValueWacMinor
    val lowStockItems: List<ProductInventoryProjection> get() = lowStockProducts
    val outOfStockItems: List<ProductInventoryProjection> get() = outOfStockProducts
    val deadStockItems: List<ProductInventoryProjection> get() = deadStockProducts
    val wacValuationList: List<ProductInventoryProjection> get() = allProductsValuation
}

data class WalletsReportSection(
    val depositsInPeriodMinor: Long = 0L,
    val withdrawalsInPeriodMinor: Long = 0L,
    val feesInPeriodMinor: Long = 0L,
    val commissionsInPeriodMinor: Long = 0L,
    val totalProviderBalancesMinor: Long = 0L,
    val providerBalances: List<WalletDerivedProjection> = emptyList(),
    val walletTransactions: List<ReportDrillDownItem> = emptyList()
) {
    val totalDepositsMinor: Long get() = depositsInPeriodMinor
    val totalWithdrawalsMinor: Long get() = withdrawalsInPeriodMinor
    val totalFeesMinor: Long get() = feesInPeriodMinor
    val totalCommissionsMinor: Long get() = commissionsInPeriodMinor
}

data class MaintenanceReportSection(
    val openTicketsCount: Int = 0,
    val readyTicketsCount: Int = 0,
    val completedTicketsCount: Int = 0,
    val maintenanceRevenueMinor: Long = 0L,
    val partsUsageUnits: Int = 0,
    val partsCostMinor: Long = 0L,
    val partsCustomerChargeMinor: Long = 0L,
    val ticketsInPeriod: List<MaintenanceOrderEntity> = emptyList()
) {
    val totalMaintenanceRevenueMinor: Long get() = maintenanceRevenueMinor
    val totalPartsUsageCostMinor: Long get() = partsCostMinor
    val totalPartsUsedQty: Int get() = partsUsageUnits
}

data class ComprehensiveReportsSnapshot(
    val filter: ReportsCenterFilter = ReportsCenterFilter(),
    val periodFilter: ReportPeriodFilter = filter.periodPreset,
    val salesSection: SalesReportSection = SalesReportSection(),
    val profitsSection: ProfitsReportSection = ProfitsReportSection(),
    val customersSection: CustomersReportSection = CustomersReportSection(),
    val suppliersSection: SuppliersReportSection = SuppliersReportSection(),
    val inventorySection: InventoryReportSection = InventoryReportSection(),
    val walletsSection: WalletsReportSection = WalletsReportSection(),
    val maintenanceSection: MaintenanceReportSection = MaintenanceReportSection()
)

typealias ReportsCenterBundle = ComprehensiveReportsSnapshot

object ReportsCenterEngine {

    fun computePeriodBounds(
        period: ReportPeriodFilter,
        todayStartMillis: Long,
        todayEndMillis: Long,
        monthStartMillis: Long,
        monthEndMillis: Long
    ): Pair<Long, Long> = when (period) {
        ReportPeriodFilter.TODAY -> todayStartMillis to todayEndMillis
        ReportPeriodFilter.LAST_7_DAYS -> (todayStartMillis - 6L * 24L * 60L * 60L * 1000L) to todayEndMillis
        ReportPeriodFilter.THIS_MONTH -> monthStartMillis to monthEndMillis
        ReportPeriodFilter.ALL_TIME -> 0L to Long.MAX_VALUE
    }

    fun buildReportsCenterBundle(
        filter: ReportsCenterFilter,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        refunds: List<RefundEntity>,
        purchases: List<PurchaseEntity>,
        expenses: List<ExpenseEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        maintenanceParts: List<MaintenancePartEntity>,
        transactions: List<TransactionEntity>,
        products: List<ProductInventoryProjection>,
        customers: List<CustomerBalanceProjection>,
        suppliers: List<SupplierBalanceProjection>,
        wallets: List<WalletDerivedProjection>,
        nowMillis: Long = System.currentTimeMillis()
    ): ReportsCenterBundle {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val todayEnd = cal.timeInMillis

        val monthCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthStart = monthCal.timeInMillis
        monthCal.set(
            java.util.Calendar.DAY_OF_MONTH,
            monthCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        )
        monthCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        monthCal.set(java.util.Calendar.MINUTE, 59)
        monthCal.set(java.util.Calendar.SECOND, 59)
        monthCal.set(java.util.Calendar.MILLISECOND, 999)
        val monthEnd = monthCal.timeInMillis

        return buildComprehensiveSnapshot(
            period = filter.periodPreset,
            todayBounds = todayStart to todayEnd,
            monthBounds = monthStart to monthEnd,
            providerFilterId = filter.selectedProviderCode,
            productFilterId = filter.selectedProductId,
            customerFilterId = filter.selectedCustomerId,
            sales = sales,
            saleItems = saleItems,
            refunds = refunds,
            purchases = purchases,
            expenses = expenses,
            ledgerTransactions = transactions,
            products = products,
            customerBalances = customers,
            supplierBalances = suppliers,
            wallets = wallets,
            maintenanceOrders = maintenanceOrders,
            maintenanceParts = maintenanceParts
        ).copy(filter = filter)
    }

    fun buildComprehensiveSnapshot(
        period: ReportPeriodFilter,
        todayBounds: Pair<Long, Long>,
        monthBounds: Pair<Long, Long>,
        providerFilterId: String?,
        productFilterId: String?,
        customerFilterId: String?,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        refunds: List<RefundEntity>,
        purchases: List<PurchaseEntity>,
        expenses: List<ExpenseEntity>,
        ledgerTransactions: List<TransactionEntity>,
        products: List<ProductInventoryProjection>,
        customerBalances: List<CustomerBalanceProjection>,
        supplierBalances: List<SupplierBalanceProjection>,
        wallets: List<WalletDerivedProjection>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        maintenanceParts: List<MaintenancePartEntity>
    ): ComprehensiveReportsSnapshot {
        val (startMillis, endMillis) = computePeriodBounds(
            period = period,
            todayStartMillis = todayBounds.first,
            todayEndMillis = todayBounds.second,
            monthStartMillis = monthBounds.first,
            monthEndMillis = monthBounds.second
        )

        val productMap = products.associateBy { it.productId }
        val customerMap = customerBalances.associateBy { it.customerId }
        val walletMap = wallets.associateBy { it.walletId }

        val salesTodayMinor = sales
            .filter { it.saleTimestamp in todayBounds.first..todayBounds.second && it.status != "RETURNED" && it.status != "REFUNDED" }
            .sumOf { it.totalAmountMinor }

        val filteredSales = sales.filter { sale ->
            sale.saleTimestamp in startMillis..endMillis &&
                sale.status != "RETURNED" &&
                sale.status != "REFUNDED" &&
                (customerFilterId.isNullOrBlank() || sale.customerId == customerFilterId)
        }
        val filteredSaleIds = filteredSales.map { it.localId }.toSet()

        val filteredSaleItems = saleItems.filter { item ->
            item.saleId in filteredSaleIds &&
                (productFilterId.isNullOrBlank() || item.productId == productFilterId)
        }

        val salesInPeriodMinor = if (!productFilterId.isNullOrBlank()) {
            filteredSaleItems.sumOf { it.lineTotalMinor }
        } else {
            filteredSales.sumOf { it.totalAmountMinor }
        }

        val discountsInPeriodMinor = filteredSales.sumOf { it.discountMinor }
        val cogsInPeriodMinor = if (!productFilterId.isNullOrBlank()) {
            filteredSaleItems.sumOf { it.unitCostAtSaleMinor * it.quantity.toLong() }
        } else {
            filteredSales.sumOf { it.totalCostOfGoodsMinor }
        }

        val filteredRefunds = refunds.filter { ref ->
            ref.refundTimestamp in startMillis..endMillis
        }
        val returnsInPeriodMinor = filteredRefunds.sumOf { it.refundAmountMinor }

        val dayFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val salesByDay = filteredSales
            .groupBy { dayFormatter.format(java.util.Date(it.saleTimestamp)) }
            .entries
            .sortedByDescending { it.key }
            .take(14)
            .map { (dayKey, daySales) ->
                DailySalesBarPoint(
                    dayLabel = dayKey,
                    grossSalesMinor = daySales.sumOf { it.totalAmountMinor },
                    invoiceCount = daySales.size
                )
            }

        // Top products aggregation
        val topProducts = filteredSaleItems
            .groupBy { it.productId }
            .map { (prodId, items) ->
                val proj = productMap[prodId]
                val units = items.sumOf { it.quantity }
                val rev = items.sumOf { it.lineTotalMinor }
                val cogs = items.sumOf { it.unitCostAtSaleMinor * it.quantity.toLong() }
                TopProductReportItem(
                    productId = prodId,
                    productNameArabic = proj?.nameArabic ?: prodId,
                    sku = proj?.sku ?: "-",
                    unitsSold = units,
                    revenueMinor = rev,
                    cogsMinor = cogs,
                    grossProfitMinor = rev - cogs
                )
            }
            .sortedByDescending { it.revenueMinor }

        val drillDownSales = filteredSales.map { s ->
            val custName = s.customerId?.let { customerMap[it]?.fullName } ?: "عميل نقدي مباشر"
            val firstItem = saleItems.firstOrNull { it.saleId == s.localId }
            val firstProdName = firstItem?.productId?.let { productMap[it]?.nameArabic }
            ReportDrillDownItem(
                id = s.localId,
                referenceNumber = s.invoiceNumber,
                titleArabic = "فاتورة بيع #${s.invoiceNumber}",
                subtitleArabic = "العميل: $custName • الأصناف: ${saleItems.count { it.saleId == s.localId }}",
                amountMinor = s.totalAmountMinor,
                timestampMillis = s.saleTimestamp,
                statusArabic = when (s.status) {
                    "COMPLETED", "PAID" -> "مدفوعة بالكامل"
                    "PARTIALLY_PAID" -> "دفع جزئي"
                    "CREDIT" -> "آجل"
                    else -> s.status
                },
                linkedCustomerId = s.customerId,
                linkedCustomerName = custName,
                linkedProductId = firstItem?.productId,
                linkedProductName = firstProdName
            )
        }

        // Ledger transactions in period
        val postedPeriodTx = ledgerTransactions.filter {
            it.status == LedgerTransactionStatus.POSTED &&
                it.transactionTimestamp in startMillis..endMillis
        }

        // Wallet section
        val filteredWallets = wallets.filter {
            providerFilterId.isNullOrBlank() ||
                it.providerId == providerFilterId ||
                it.walletId == providerFilterId ||
                it.providerNameArabic.contains(providerFilterId, ignoreCase = true)
        }
        val allowedWalletIds = filteredWallets.map { it.walletId }.toSet()

        val walletPeriodTx = postedPeriodTx.filter { tx ->
            val isWalletTx = tx.sourceAccountType == FinancialAccountType.WALLET ||
                tx.destinationAccountType == FinancialAccountType.WALLET
            val matchesFilter = providerFilterId.isNullOrBlank() ||
                tx.sourceAccountId in allowedWalletIds ||
                tx.destinationAccountId in allowedWalletIds
            isWalletTx && matchesFilter
        }

        val walletDepositsMinor = walletPeriodTx
            .filter { it.destinationAccountType == FinancialAccountType.WALLET }
            .sumOf { it.amountMinor }
        val walletWithdrawalsMinor = walletPeriodTx
            .filter { it.sourceAccountType == FinancialAccountType.WALLET }
            .sumOf { it.amountMinor }
        val walletFeesMinor = walletPeriodTx.sumOf { it.feeMinor }
        val walletCommissionsMinor = walletPeriodTx.sumOf { it.commissionMinor }
        val totalProviderBalancesMinor = filteredWallets.sumOf { it.derivedBalanceMinor }

        val walletDrillDown = walletPeriodTx.map { tx ->
            val wId = if (tx.sourceAccountType == FinancialAccountType.WALLET) tx.sourceAccountId else tx.destinationAccountId
            val wProj = walletMap[wId]
            ReportDrillDownItem(
                id = tx.localId,
                referenceNumber = tx.referenceNumber,
                titleArabic = FriendlyTerminologyMapper.friendlyCashTransactionLabel(tx),
                subtitleArabic = "${wProj?.walletNameArabic ?: "محفظة إلكترونية"} • ${tx.descriptionArabic}",
                amountMinor = tx.amountMinor,
                timestampMillis = tx.transactionTimestamp,
                statusArabic = FriendlyTerminologyMapper.friendlyLedgerStatusLabel(tx.status),
                linkedWalletId = wId,
                linkedWalletName = wProj?.walletNameArabic
            )
        }

        // Maintenance section
        val maintRevenueMinor = postedPeriodTx
            .filter { it.relatedEntityType == "MAINTENANCE" || it.relatedEntityType == "MAINTENANCE_DEPOSIT" }
            .sumOf { it.amountMinor }

        val openMaintCount = maintenanceOrders.count {
            it.status != MaintenanceStatus.READY_FOR_PICKUP &&
                it.status != MaintenanceStatus.CUSTOMER_NOTIFIED &&
                it.status != MaintenanceStatus.PAYMENT_COLLECTED &&
                it.status != MaintenanceStatus.DELIVERED &&
                it.status != MaintenanceStatus.CANCELLED
        }
        val readyMaintCount = maintenanceOrders.count {
            it.status == MaintenanceStatus.READY_FOR_PICKUP ||
                it.status == MaintenanceStatus.CUSTOMER_NOTIFIED ||
                it.status == MaintenanceStatus.PAYMENT_COLLECTED
        }
        val completedMaintCount = maintenanceOrders.count { it.status == MaintenanceStatus.DELIVERED }
        val periodMaintParts = maintenanceParts.filter { it.createdAt in startMillis..endMillis }

        // Expenses & Net Result
        val expensesInPeriodMinor = expenses
            .filter { it.expenseTimestamp in startMillis..endMillis }
            .sumOf { it.amountMinor }

        val netSalesRevenue = (salesInPeriodMinor - returnsInPeriodMinor).coerceAtLeast(0L)
        val grossProfitMinor = (netSalesRevenue - cogsInPeriodMinor) + walletCommissionsMinor + maintRevenueMinor
        val netResultMinor = grossProfitMinor - expensesInPeriodMinor

        // Customers section
        val filteredCustBalances = customerBalances.filter {
            customerFilterId.isNullOrBlank() || it.customerId == customerFilterId
        }
        val totalReceivablesMinor = filteredCustBalances.sumOf { it.outstandingBalanceMinor.coerceAtLeast(0L) }
        val collectionsTx = postedPeriodTx.filter {
            it.relatedEntityType == "CUSTOMER_COLLECTION" &&
                (customerFilterId.isNullOrBlank() || it.customerId == customerFilterId)
        }
        val collectionsInPeriodMinor = collectionsTx.sumOf { it.amountMinor }
        val collectionDrillDown = collectionsTx.map { tx ->
            val cName = tx.customerId?.let { customerMap[it]?.fullName } ?: "عميل"
            ReportDrillDownItem(
                id = tx.localId,
                referenceNumber = tx.referenceNumber,
                titleArabic = "تحصيل دفعة من $cName",
                subtitleArabic = tx.descriptionArabic,
                amountMinor = tx.amountMinor,
                timestampMillis = tx.transactionTimestamp,
                statusArabic = "معتمد",
                linkedCustomerId = tx.customerId,
                linkedCustomerName = cName
            )
        }

        // Suppliers section
        val totalPayablesMinor = supplierBalances.sumOf { it.outstandingPayableMinor.coerceAtLeast(0L) }
        val purchasesInPeriodMinor = purchases
            .filter { it.purchaseTimestamp in startMillis..endMillis }
            .sumOf { it.totalAmountMinor }
        val supplierPaymentsMinor = purchases
            .filter { it.purchaseTimestamp in startMillis..endMillis }
            .sumOf { it.paidAmountMinor } +
            postedPeriodTx.filter { it.relatedEntityType == "SUPPLIER_PAYMENT" }.sumOf { it.amountMinor }

        // Inventory section
        val filteredProducts = products.filter {
            productFilterId.isNullOrBlank() || it.productId == productFilterId
        }
        val soldProductIdsInPeriod = filteredSaleItems.map { it.productId }.toSet()
        val deadStock = filteredProducts.filter { it.derivedStockQty > 0 && it.productId !in soldProductIdsInPeriod }

        return ComprehensiveReportsSnapshot(
            filter = ReportsCenterFilter(
                periodPreset = period,
                selectedProductId = productFilterId,
                selectedCustomerId = customerFilterId,
                selectedProviderCode = providerFilterId
            ),
            periodFilter = period,
            salesSection = SalesReportSection(
                salesTodayMinor = salesTodayMinor,
                salesInPeriodMinor = salesInPeriodMinor,
                salesInvoicesCount = filteredSales.size,
                returnsInPeriodMinor = returnsInPeriodMinor,
                returnsCount = filteredRefunds.size,
                discountsInPeriodMinor = discountsInPeriodMinor,
                salesByDay = salesByDay,
                topProducts = topProducts,
                filteredSales = filteredSales.sortedByDescending { it.saleTimestamp },
                drillDownSales = drillDownSales
            ),
            profitsSection = ProfitsReportSection(
                grossSalesRevenueMinor = salesInPeriodMinor,
                returnsMinor = returnsInPeriodMinor,
                netSalesRevenueMinor = netSalesRevenue,
                cogsMinor = cogsInPeriodMinor,
                walletCommissionsMinor = walletCommissionsMinor,
                maintenanceRevenueMinor = maintRevenueMinor,
                grossProfitMinor = grossProfitMinor,
                expensesMinor = expensesInPeriodMinor,
                netResultMinor = netResultMinor
            ),
            customersSection = CustomersReportSection(
                totalReceivablesMinor = totalReceivablesMinor,
                collectionsInPeriodMinor = collectionsInPeriodMinor,
                customersWithDebtCount = filteredCustBalances.count { it.outstandingBalanceMinor > 0L },
                customerBalances = filteredCustBalances.sortedByDescending { it.outstandingBalanceMinor },
                collectionTransactions = collectionDrillDown
            ),
            suppliersSection = SuppliersReportSection(
                totalPayablesMinor = totalPayablesMinor,
                purchasesInPeriodMinor = purchasesInPeriodMinor,
                paymentsInPeriodMinor = supplierPaymentsMinor,
                suppliersWithPayableCount = supplierBalances.count { it.outstandingPayableMinor > 0L },
                supplierBalances = supplierBalances.sortedByDescending { it.outstandingPayableMinor }
            ),
            inventorySection = InventoryReportSection(
                totalStockValueWacMinor = filteredProducts.sumOf { it.stockValueMinor },
                totalUnitsInStock = filteredProducts.sumOf { it.derivedStockQty.coerceAtLeast(0) },
                lowStockProducts = filteredProducts.filter { it.derivedStockQty in 1..it.reorderThresholdQty },
                outOfStockProducts = filteredProducts.filter { it.derivedStockQty <= 0 },
                deadStockProducts = deadStock,
                topSellers = topProducts,
                allProductsValuation = filteredProducts.sortedByDescending { it.stockValueMinor }
            ),
            walletsSection = WalletsReportSection(
                depositsInPeriodMinor = walletDepositsMinor,
                withdrawalsInPeriodMinor = walletWithdrawalsMinor,
                feesInPeriodMinor = walletFeesMinor,
                commissionsInPeriodMinor = walletCommissionsMinor,
                totalProviderBalancesMinor = totalProviderBalancesMinor,
                providerBalances = filteredWallets,
                walletTransactions = walletDrillDown
            ),
            maintenanceSection = MaintenanceReportSection(
                openTicketsCount = openMaintCount,
                readyTicketsCount = readyMaintCount,
                completedTicketsCount = completedMaintCount,
                maintenanceRevenueMinor = maintRevenueMinor,
                partsUsageUnits = periodMaintParts.sumOf { it.quantity },
                partsCostMinor = periodMaintParts.sumOf { it.unitCostMinor * it.quantity.toLong() },
                partsCustomerChargeMinor = periodMaintParts.sumOf { it.unitChargeMinor * it.quantity.toLong() },
                ticketsInPeriod = maintenanceOrders.filter { it.receivedAt in startMillis..endMillis }
            )
        )
    }
}

// =================================================================================================
// MEKKA PHONE V4 — PART 1: ADVANCED DOMAIN, FINANCIAL, SHIFT, CREDIT, RETURNS & Z-REPORT ENGINES
// =================================================================================================

enum class ShiftVarianceStatus(val arabicLabel: String) {
    BALANCED("مطابق تماماً"),
    SHORTAGE("عجز خزينة"),
    SURPLUS("فائض / زيادة خزينة"),
    MATCHED("مطابق تماماً"),
    OVERAGE("فائض / زيادة خزينة");

    companion object {
        fun of(varianceMinor: Long): ShiftVarianceStatus = when {
            varianceMinor < 0L -> SHORTAGE
            varianceMinor > 0L -> OVERAGE
            else -> MATCHED
        }
    }
}

data class ShiftCashFlowBreakdown(
    val shiftId: String,
    val openedByUserId: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingCashMinor: Long,
    val cashSalesInMinor: Long,
    val customerCollectionsInMinor: Long,
    val walletCashInMinor: Long,
    val manualCashInMinor: Long,
    val totalCashInMinor: Long,
    val walletCashOutMinor: Long,
    val expensesCashOutMinor: Long,
    val supplierPaymentsOutMinor: Long,
    val refundsCashOutMinor: Long,
    val manualCashOutMinor: Long,
    val totalCashOutMinor: Long,
    val expectedClosingCashMinor: Long,
    val countedClosingCashMinor: Long? = null,
    val varianceMinor: Long = 0L,
    val varianceStatus: ShiftVarianceStatus = ShiftVarianceStatus.BALANCED,
    val walletClosingBalances: Map<String, Long> = emptyMap()
)

object ShiftManagementEngine {

    fun computeShiftBreakdown(
        session: com.example.database.entities.CashSessionEntity,
        shiftTransactions: List<TransactionEntity>,
        currentWallets: List<WalletDerivedProjection> = emptyList(),
        countedCashMinor: Long? = session.closingCountedCashMinor
    ): ShiftCashFlowBreakdown {
        val postedShiftTxs = shiftTransactions.filter {
            it.status == LedgerTransactionStatus.POSTED &&
                (it.shiftId == session.localId ||
                    (it.shiftId == null && it.transactionTimestamp >= session.openedAt &&
                        (session.closedAt == null || it.transactionTimestamp <= session.closedAt))) &&
                it.localId != session.explicitAdjustmentTxId
        }

        val drawerId = session.cashDrawerId
        var cashSalesIn = 0L
        var collectionsIn = 0L
        var walletCashIn = 0L
        var manualCashIn = 0L

        var walletCashOut = 0L
        var expensesOut = 0L
        var supplierPaymentsOut = 0L
        var refundsOut = 0L
        var manualCashOut = 0L

        for (tx in postedShiftTxs) {
            val isDrawerInflow = tx.destinationAccountType == FinancialAccountType.CASH_DRAWER && tx.destinationAccountId == drawerId
            val isDrawerOutflow = tx.sourceAccountType == FinancialAccountType.CASH_DRAWER && tx.sourceAccountId == drawerId

            if (isDrawerInflow) {
                when {
                    tx.transactionType == FinancialTransactionType.SALE || tx.relatedEntityType == "SALE" || tx.relatedEntityType == "MAINTENANCE" || tx.relatedEntityType == "MAINTENANCE_DEPOSIT" -> {
                        cashSalesIn += tx.amountMinor
                    }
                    tx.relatedEntityType == "CUSTOMER_COLLECTION" -> {
                        collectionsIn += tx.amountMinor
                    }
                    tx.transactionType == FinancialTransactionType.DEPOSIT || tx.transactionType == FinancialTransactionType.TRANSFER || tx.relatedEntityType == "TRANSFER" -> {
                        walletCashIn += tx.amountMinor
                    }
                    else -> {
                        // Exclude opening balance seeding if it happened prior to or at session opening as initial seed
                        if (!tx.descriptionArabic.contains("رصيد افتتاحي")) {
                            manualCashIn += tx.amountMinor
                        }
                    }
                }
            }

            if (isDrawerOutflow) {
                val outflow = tx.amountMinor + tx.feeMinor
                when {
                    tx.transactionType == FinancialTransactionType.WITHDRAW || (tx.relatedEntityType == "TRANSFER" && tx.sourceAccountType == FinancialAccountType.CASH_DRAWER) -> {
                        walletCashOut += outflow
                    }
                    tx.transactionType == FinancialTransactionType.EXPENSE || tx.relatedEntityType == "EXPENSE" -> {
                        expensesOut += outflow
                    }
                    tx.transactionType == FinancialTransactionType.PURCHASE || tx.relatedEntityType == "PURCHASE" || tx.relatedEntityType == "SUPPLIER_PAYMENT" -> {
                        supplierPaymentsOut += outflow
                    }
                    tx.transactionType == FinancialTransactionType.REFUND || tx.relatedEntityType == "REFUND" -> {
                        refundsOut += outflow
                    }
                    else -> {
                        manualCashOut += outflow
                    }
                }
            }
        }

        val totalIn = cashSalesIn + collectionsIn + walletCashIn + manualCashIn
        val totalOut = walletCashOut + expensesOut + supplierPaymentsOut + refundsOut + manualCashOut
        val expectedClosing = session.openingDerivedBalanceMinor + totalIn - totalOut
        val effectiveCounted = countedCashMinor ?: expectedClosing
        val variance = effectiveCounted - expectedClosing
        val status = when {
            variance < 0L -> ShiftVarianceStatus.SHORTAGE
            variance > 0L -> ShiftVarianceStatus.SURPLUS
            else -> ShiftVarianceStatus.BALANCED
        }

        return ShiftCashFlowBreakdown(
            shiftId = session.localId,
            openedByUserId = session.openedByUserId,
            openedAt = session.openedAt,
            closedAt = session.closedAt,
            openingCashMinor = session.openingDerivedBalanceMinor,
            cashSalesInMinor = cashSalesIn,
            customerCollectionsInMinor = collectionsIn,
            walletCashInMinor = walletCashIn,
            manualCashInMinor = manualCashIn,
            totalCashInMinor = totalIn,
            walletCashOutMinor = walletCashOut,
            expensesCashOutMinor = expensesOut,
            supplierPaymentsOutMinor = supplierPaymentsOut,
            refundsCashOutMinor = refundsOut,
            manualCashOutMinor = manualCashOut,
            totalCashOutMinor = totalOut,
            expectedClosingCashMinor = expectedClosing,
            countedClosingCashMinor = countedCashMinor,
            varianceMinor = variance,
            varianceStatus = status,
            walletClosingBalances = currentWallets.associate { it.walletId to it.derivedBalanceMinor }
        )
    }
}

/**
 * V4 Section 3: True Profitability Engine (محرك الربحية الحقيقية).
 * Distinguishes strictly between Gross Revenue, Net Sales, COGS, Maintenance Net Profit,
 * Wallet Net Profit, Operating Expenses, Shrinkage, and Cash Variances.
 */
data class CategoryProfitBreakdownItem(
    val categoryId: String,
    val categoryNameArabic: String,
    val unitsSold: Int,
    val netRevenueMinor: Long,
    val cogsMinor: Long,
    val grossProfitMinor: Long
)

data class BrandProfitBreakdownItem(
    val brandId: String,
    val brandName: String,
    val unitsSold: Int,
    val netRevenueMinor: Long,
    val cogsMinor: Long,
    val grossProfitMinor: Long
)

data class UserSalesProfitBreakdownItem(
    val userId: String,
    val invoicesCount: Int,
    val netSalesRevenueMinor: Long,
    val cogsMinor: Long,
    val grossProfitMinor: Long
)

data class TechnicianProfitBreakdownItem(
    val technicianId: String,
    val completedTicketsCount: Int,
    val totalRevenueMinor: Long,
    val partsCostMinor: Long,
    val netProfitMinor: Long
)

data class TrueProfitabilityReport(
    val grossSalesBeforeDiscountMinor: Long,
    val salesDiscountsMinor: Long,
    val salesReturnsMinor: Long,
    val netSalesRevenueMinor: Long,
    val grossCogsMinor: Long,
    val returnedCogsMinor: Long,
    val netCogsMinor: Long,
    val salesGrossProfitMinor: Long,
    val maintenanceRevenueMinor: Long,
    val maintenancePartsCostMinor: Long,
    val maintenanceNetProfitMinor: Long,
    val walletCommissionsMinor: Long,
    val walletNetworkFeesMinor: Long,
    val walletNetProfitMinor: Long,
    val totalGrossProfitMinor: Long,
    val operatingExpensesMinor: Long,
    val inventoryShrinkageLossMinor: Long,
    val inventorySurplusGainMinor: Long,
    val cashShortageLossMinor: Long,
    val cashSurplusGainMinor: Long,
    val trueNetProfitMinor: Long,
    val profitByProduct: List<TopProductReportItem> = emptyList(),
    val profitByCategory: List<CategoryProfitBreakdownItem> = emptyList(),
    val profitByBrand: List<BrandProfitBreakdownItem> = emptyList(),
    val profitByCashier: List<UserSalesProfitBreakdownItem> = emptyList(),
    val profitByTechnician: List<TechnicianProfitBreakdownItem> = emptyList()
)

object TrueProfitabilityEngine {

    fun computeTrueProfitability(
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        refunds: List<RefundEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        maintenanceParts: List<MaintenancePartEntity>,
        expenses: List<ExpenseEntity>,
        inventoryMovements: List<com.example.database.entities.InventoryMovementEntity>,
        ledgerTransactions: List<TransactionEntity>,
        products: List<ProductInventoryProjection> = emptyList(),
        categories: Map<String, String> = emptyMap(),
        brands: Map<String, String> = emptyMap()
    ): TrueProfitabilityReport {
        val periodSales = sales.filter { it.saleTimestamp in startMillis..endMillis }
        val nonCancelledSales = periodSales.filter { it.status != "CANCELLED" }

        val grossSalesBeforeDiscount = nonCancelledSales.sumOf { it.subtotalMinor }
        val salesDiscounts = nonCancelledSales.sumOf { it.discountMinor }
        val grossInvoiceRevenue = nonCancelledSales.sumOf { it.totalAmountMinor }

        val periodRefunds = refunds.filter { it.refundTimestamp in startMillis..endMillis }
        val salesReturns = periodRefunds.sumOf { it.refundAmountMinor }
        val netSalesRevenue = (grossInvoiceRevenue - salesReturns).coerceAtLeast(0L)

        val grossCogs = nonCancelledSales.sumOf { it.totalCostOfGoodsMinor }
        val periodMovements = inventoryMovements.filter { it.timestamp in startMillis..endMillis }
        val returnedCogs = periodMovements
            .filter { it.movementType == InventoryMovementType.RETURN_IN }
            .sumOf { it.totalCostMinor }
        val netCogs = (grossCogs - returnedCogs).coerceAtLeast(0L)
        val salesGrossProfit = netSalesRevenue - netCogs

        val postedTx = ledgerTransactions.filter {
            it.status == LedgerTransactionStatus.POSTED && it.transactionTimestamp in startMillis..endMillis
        }

        val maintRevenue = postedTx
            .filter { it.relatedEntityType == "MAINTENANCE" }
            .sumOf { it.amountMinor }
        val periodMaintParts = maintenanceParts.filter { it.createdAt in startMillis..endMillis }
        val maintPartsCost = periodMaintParts.sumOf { it.unitCostMinor * it.quantity.toLong() }
        val maintNetProfit = maintRevenue - maintPartsCost

        val walletTx = postedTx.filter {
            it.sourceAccountType == FinancialAccountType.WALLET || it.destinationAccountType == FinancialAccountType.WALLET
        }
        val walletCommissions = walletTx.sumOf { it.commissionMinor }
        val walletFees = walletTx.sumOf { it.feeMinor }
        val walletNetProfit = walletCommissions - walletFees

        val totalGrossProfit = salesGrossProfit + maintNetProfit + walletNetProfit

        val operatingExpenses = expenses
            .filter { it.expenseTimestamp in startMillis..endMillis }
            .sumOf { it.amountMinor }

        val shrinkageLoss = periodMovements
            .filter {
                it.movementType == InventoryMovementType.DAMAGE ||
                    (it.movementType == InventoryMovementType.ADJUSTMENT_OUT && it.referenceType != "INITIAL_SEED")
            }
            .sumOf { it.totalCostMinor }

        val surplusGain = periodMovements
            .filter {
                it.movementType == InventoryMovementType.ADJUSTMENT_IN &&
                    (it.referenceType == "STOCK_COUNT" || it.reason.contains("جرد"))
            }
            .sumOf { it.totalCostMinor }

        val cashAdjustments = postedTx.filter {
            it.transactionType == FinancialTransactionType.ADJUSTMENT &&
                (it.relatedEntityType == "CASH_CLOSEOUT" || it.relatedEntityType == "CASH_SESSION" || it.relatedEntityType == "CASH_DRAWER")
        }
        val cashShortageLoss = cashAdjustments
            .filter { it.sourceAccountType == FinancialAccountType.CASH_DRAWER }
            .sumOf { it.amountMinor }
        val cashSurplusGain = cashAdjustments
            .filter { it.destinationAccountType == FinancialAccountType.CASH_DRAWER }
            .sumOf { it.amountMinor }

        val trueNetProfit = totalGrossProfit - operatingExpenses - shrinkageLoss + surplusGain - cashShortageLoss + cashSurplusGain

        // Breakdowns
        val productMap = products.associateBy { it.productId }
        val activeSaleIds = nonCancelledSales.filter { it.status != "REFUNDED" && it.status != "RETURNED" }.map { it.localId }.toSet()
        val activeSaleItems = saleItems.filter { it.saleId in activeSaleIds }

        val byProduct = activeSaleItems.groupBy { it.productId }.map { (prodId, items) ->
            val proj = productMap[prodId]
            val effectiveItems = items.map { item ->
                val netQty = (item.quantity - item.returnedQuantity).coerceAtLeast(0)
                val unitNetRev = if (item.quantity > 0) item.lineTotalMinor / item.quantity else item.unitSellingPriceMinor
                Triple(netQty, unitNetRev * netQty, item.unitCostAtSaleMinor * netQty)
            }
            val units = effectiveItems.sumOf { it.first }
            val rev = effectiveItems.sumOf { it.second }
            val cogs = effectiveItems.sumOf { it.third }
            TopProductReportItem(
                productId = prodId,
                productNameArabic = proj?.nameArabic ?: prodId,
                sku = proj?.sku ?: "-",
                unitsSold = units,
                revenueMinor = rev,
                cogsMinor = cogs,
                grossProfitMinor = rev - cogs
            )
        }.sortedByDescending { it.grossProfitMinor }

        val byCategory = byProduct.groupBy { item ->
            productMap[item.productId]?.categoryId ?: "UNCATEGORIZED"
        }.map { (catId, prodItems) ->
            val rev = prodItems.sumOf { it.revenueMinor }
            val cogs = prodItems.sumOf { it.cogsMinor }
            CategoryProfitBreakdownItem(
                categoryId = catId,
                categoryNameArabic = categories[catId] ?: catId,
                unitsSold = prodItems.sumOf { it.unitsSold },
                netRevenueMinor = rev,
                cogsMinor = cogs,
                grossProfitMinor = rev - cogs
            )
        }.sortedByDescending { it.grossProfitMinor }

        val byBrand = byProduct.groupBy { item ->
            productMap[item.productId]?.brandId ?: "UNBRANDED"
        }.map { (bId, prodItems) ->
            val rev = prodItems.sumOf { it.revenueMinor }
            val cogs = prodItems.sumOf { it.cogsMinor }
            BrandProfitBreakdownItem(
                brandId = bId,
                brandName = brands[bId] ?: bId,
                unitsSold = prodItems.sumOf { it.unitsSold },
                netRevenueMinor = rev,
                cogsMinor = cogs,
                grossProfitMinor = rev - cogs
            )
        }.sortedByDescending { it.grossProfitMinor }

        val byCashier = nonCancelledSales.filter { it.status != "REFUNDED" }.groupBy { it.createdByUserId }.map { (uId, userSales) ->
            val rev = userSales.sumOf { it.totalAmountMinor }
            val cogs = userSales.sumOf { it.totalCostOfGoodsMinor }
            UserSalesProfitBreakdownItem(
                userId = uId,
                invoicesCount = userSales.size,
                netSalesRevenueMinor = rev,
                cogsMinor = cogs,
                grossProfitMinor = rev - cogs
            )
        }.sortedByDescending { it.grossProfitMinor }

        val byTechnician = maintenanceOrders
            .filter { it.receivedAt in startMillis..endMillis && it.status != MaintenanceStatus.CANCELLED }
            .groupBy { it.assignedTechnicianId ?: it.receivedByUserId }
            .map { (techId, orders) ->
                val orderIds = orders.map { it.localId }.toSet()
                val rev = postedTx.filter {
                    it.relatedEntityId in orderIds &&
                        (it.relatedEntityType == "MAINTENANCE" || it.relatedEntityType == "MAINTENANCE_DEPOSIT")
                }.sumOf { it.amountMinor }
                val partsCost = maintenanceParts.filter { it.maintenanceOrderId in orderIds }
                    .sumOf { it.unitCostMinor * it.quantity.toLong() }
                TechnicianProfitBreakdownItem(
                    technicianId = techId,
                    completedTicketsCount = orders.count { it.status == MaintenanceStatus.DELIVERED || it.status == MaintenanceStatus.CLOSED },
                    totalRevenueMinor = rev,
                    partsCostMinor = partsCost,
                    netProfitMinor = rev - partsCost
                )
            }.sortedByDescending { it.netProfitMinor }

        return TrueProfitabilityReport(
            grossSalesBeforeDiscountMinor = grossSalesBeforeDiscount,
            salesDiscountsMinor = salesDiscounts,
            salesReturnsMinor = salesReturns,
            netSalesRevenueMinor = netSalesRevenue,
            grossCogsMinor = grossCogs,
            returnedCogsMinor = returnedCogs,
            netCogsMinor = netCogs,
            salesGrossProfitMinor = salesGrossProfit,
            maintenanceRevenueMinor = maintRevenue,
            maintenancePartsCostMinor = maintPartsCost,
            maintenanceNetProfitMinor = maintNetProfit,
            walletCommissionsMinor = walletCommissions,
            walletNetworkFeesMinor = walletFees,
            walletNetProfitMinor = walletNetProfit,
            totalGrossProfitMinor = totalGrossProfit,
            operatingExpensesMinor = operatingExpenses,
            inventoryShrinkageLossMinor = shrinkageLoss,
            inventorySurplusGainMinor = surplusGain,
            cashShortageLossMinor = cashShortageLoss,
            cashSurplusGainMinor = cashSurplusGain,
            trueNetProfitMinor = trueNetProfit,
            profitByProduct = byProduct,
            profitByCategory = byCategory,
            profitByBrand = byBrand,
            profitByCashier = byCashier,
            profitByTechnician = byTechnician
        )
    }
}

/**
 * V4 Section 5: Customer Credit Control & Debt Aging Engine (الائتمان وأعمار الديون).
 */
data class DebtAgingBuckets(
    val bucket0To7DaysMinor: Long = 0L,
    val bucket8To15DaysMinor: Long = 0L,
    val bucket16To30DaysMinor: Long = 0L,
    val bucket31To60DaysMinor: Long = 0L,
    val bucketOver60DaysMinor: Long = 0L,
    val totalOutstandingMinor: Long = 0L,
    val overdueAmountMinor: Long = 0L,
    val oldestInvoiceDays: Int = 0,
    val maxOverdueDays: Int = 0
)

data class CustomerCreditStanding(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val creditLimitMinor: Long,
    val defaultCreditDays: Int,
    val isCreditActive: Boolean,
    val currentDebtMinor: Long,
    val availableCreditMinor: Long,
    val isExceedingCreditLimit: Boolean,
    val hasOverdueInvoices: Boolean,
    val agingBuckets: DebtAgingBuckets
)

data class CreditSaleEligibility(
    val isAllowedWithoutOverride: Boolean,
    val requiresManagerOverride: Boolean,
    val isHardBlocked: Boolean,
    val currentDebtMinor: Long,
    val newCreditAmountMinor: Long,
    val projectedNewDebtMinor: Long,
    val creditLimitMinor: Long,
    val reasonsArabic: List<String>
)

object CustomerCreditAgingEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun computeDebtAgingBuckets(
        customerSales: List<SaleEntity>,
        derivedBalanceMinor: Long,
        defaultCreditDays: Int = 30,
        nowMillis: Long = System.currentTimeMillis()
    ): DebtAgingBuckets {
        val totalDebt = derivedBalanceMinor.coerceAtLeast(0L)
        if (totalDebt == 0L) {
            return DebtAgingBuckets()
        }

        // Allocate remaining debt across unpaid/partially paid invoices from newest to oldest (FIFO payment allocation)
        val creditSalesNewestFirst = customerSales
            .filter { it.status != "REFUNDED" && it.status != "CANCELLED" && it.totalAmountMinor > it.paidAmountMinor }
            .sortedByDescending { it.saleTimestamp }

        var unallocatedDebt = totalDebt
        var b0To7 = 0L
        var b8To15 = 0L
        var b16To30 = 0L
        var b31To60 = 0L
        var bOver60 = 0L
        var overdueAmt = 0L
        var oldestDays = 0
        var maxOverdue = 0

        for (sale in creditSalesNewestFirst) {
            if (unallocatedDebt <= 0L) break
            val invoiceUnpaid = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
            val allocated = minOf(unallocatedDebt, invoiceUnpaid)
            if (allocated <= 0L) continue
            unallocatedDebt -= allocated

            val ageDays = ((nowMillis - sale.saleTimestamp).coerceAtLeast(0L) / DAY_MILLIS).toInt()
            if (ageDays > oldestDays) oldestDays = ageDays

            when {
                ageDays <= 7 -> b0To7 += allocated
                ageDays <= 15 -> b8To15 += allocated
                ageDays <= 30 -> b16To30 += allocated
                ageDays <= 60 -> b31To60 += allocated
                else -> bOver60 += allocated
            }

            val dueMillis = sale.dueDateTimestamp ?: (sale.saleTimestamp + defaultCreditDays.toLong() * DAY_MILLIS)
            if (nowMillis > dueMillis) {
                overdueAmt += allocated
                val overdueDays = ((nowMillis - dueMillis) / DAY_MILLIS).toInt().coerceAtLeast(1)
                if (overdueDays > maxOverdue) maxOverdue = overdueDays
            }
        }

        if (unallocatedDebt > 0L) {
            b0To7 += unallocatedDebt
        }

        return DebtAgingBuckets(
            bucket0To7DaysMinor = b0To7,
            bucket8To15DaysMinor = b8To15,
            bucket16To30DaysMinor = b16To30,
            bucket31To60DaysMinor = b31To60,
            bucketOver60DaysMinor = bOver60,
            totalOutstandingMinor = totalDebt,
            overdueAmountMinor = overdueAmt,
            oldestInvoiceDays = oldestDays,
            maxOverdueDays = maxOverdue
        )
    }

    fun computeCustomerCreditStanding(
        customer: com.example.database.entities.CustomerEntity,
        derivedBalanceMinor: Long,
        customerSales: List<SaleEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerCreditStanding {
        val currentDebt = derivedBalanceMinor.coerceAtLeast(0L)
        val aging = computeDebtAgingBuckets(
            customerSales = customerSales,
            derivedBalanceMinor = currentDebt,
            defaultCreditDays = customer.defaultCreditDays,
            nowMillis = nowMillis
        )
        val limit = customer.creditLimitMinor.coerceAtLeast(0L)
        val available = if (limit > 0L) (limit - currentDebt).coerceAtLeast(0L) else Long.MAX_VALUE
        val exceedsLimit = limit > 0L && currentDebt > limit

        return CustomerCreditStanding(
            customerId = customer.localId,
            customerName = customer.fullName,
            phone = customer.phone,
            creditLimitMinor = limit,
            defaultCreditDays = customer.defaultCreditDays,
            isCreditActive = customer.isActive && !customer.isDeleted,
            currentDebtMinor = currentDebt,
            availableCreditMinor = available,
            isExceedingCreditLimit = exceedsLimit,
            hasOverdueInvoices = aging.overdueAmountMinor > 0L,
            agingBuckets = aging
        )
    }

    fun evaluateCreditSaleEligibility(
        customer: com.example.database.entities.CustomerEntity,
        currentDebtMinor: Long,
        newCreditAmountMinor: Long,
        customerSales: List<SaleEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): CreditSaleEligibility {
        val safeCurrent = currentDebtMinor.coerceAtLeast(0L)
        val safeNew = newCreditAmountMinor.coerceAtLeast(0L)
        val projected = safeCurrent + safeNew
        if (safeNew == 0L) {
            return CreditSaleEligibility(
                isAllowedWithoutOverride = true,
                requiresManagerOverride = false,
                isHardBlocked = false,
                currentDebtMinor = safeCurrent,
                newCreditAmountMinor = 0L,
                projectedNewDebtMinor = projected,
                creditLimitMinor = customer.creditLimitMinor,
                reasonsArabic = emptyList()
            )
        }

        val reasons = mutableListOf<String>()
        if (!customer.isActive || customer.isDeleted) {
            reasons += "حساب العميل (${customer.fullName}) موقوف أو غير نشط للائتمان"
        }

        val limit = customer.creditLimitMinor
        if (limit > 0L && projected > limit) {
            reasons += "المديونية المتوقعة (${Money.ofMinor(projected).formatArabic()}) تتجاوز الحد الائتماني المسموح للعميل (${Money.ofMinor(limit).formatArabic()})"
        }

        val aging = computeDebtAgingBuckets(
            customerSales = customerSales,
            derivedBalanceMinor = safeCurrent,
            defaultCreditDays = customer.defaultCreditDays,
            nowMillis = nowMillis
        )
        if (aging.overdueAmountMinor > 0L) {
            reasons += "يوجد مستحقات متأخرة السداد على العميل بقيمة (${Money.ofMinor(aging.overdueAmountMinor).formatArabic()}) منذ (${aging.maxOverdueDays}) يوم"
        }

        val requiresOverride = reasons.isNotEmpty()
        return CreditSaleEligibility(
            isAllowedWithoutOverride = !requiresOverride,
            requiresManagerOverride = requiresOverride,
            isHardBlocked = !customer.isActive || customer.isDeleted,
            currentDebtMinor = safeCurrent,
            newCreditAmountMinor = safeNew,
            projectedNewDebtMinor = projected,
            creditLimitMinor = limit,
            reasonsArabic = reasons
        )
    }
}

/**
 * V4 Section 6: Supplier Payables & Due Date Schedule Engine (التزامات الموردين وجدولة الاستحقاق).
 */
enum class PayableDueStatus(val arabicLabel: String) {
    OVERDUE("متأخرة السداد"),
    DUE_TODAY("تستحق اليوم"),
    UPCOMING_7_DAYS("تستحق خلال 7 أيام"),
    SCHEDULED("مجدولة")
}

data class SupplierPayableScheduleItem(
    val purchaseId: String,
    val invoiceNumber: String,
    val supplierId: String,
    val supplierNameArabic: String,
    val purchaseTimestamp: Long,
    val dueDateTimestamp: Long,
    val totalAmountMinor: Long,
    val paidAmountMinor: Long,
    val remainingPayableMinor: Long,
    val daysUntilDue: Int,
    val dueStatus: PayableDueStatus
)

data class SupplierPayablesSummary(
    val totalPayablesMinor: Long,
    val overduePayablesMinor: Long,
    val dueTodayPayablesMinor: Long,
    val upcoming7DaysPayablesMinor: Long,
    val scheduleItems: List<SupplierPayableScheduleItem>
)

object SupplierPayablesScheduleEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun buildPayablesSchedule(
        purchases: List<PurchaseEntity>,
        supplierBalances: List<SupplierBalanceProjection>,
        defaultSupplierCreditDays: Int = 14,
        nowMillis: Long = System.currentTimeMillis()
    ): SupplierPayablesSummary {
        val supplierMap = supplierBalances.associateBy { it.supplierId }
        val items = mutableListOf<SupplierPayableScheduleItem>()

        for (sup in supplierBalances) {
            var remainingToAllocate = sup.outstandingPayableMinor.coerceAtLeast(0L)
            if (remainingToAllocate <= 0L) continue

            val supPurchasesNewestFirst = purchases
                .filter { it.supplierId == sup.supplierId && it.totalAmountMinor > it.paidAmountMinor }
                .sortedByDescending { it.purchaseTimestamp }

            for (pur in supPurchasesNewestFirst) {
                if (remainingToAllocate <= 0L) break
                val unpaidOnInvoice = (pur.totalAmountMinor - pur.paidAmountMinor).coerceAtLeast(0L)
                val allocated = minOf(remainingToAllocate, unpaidOnInvoice)
                if (allocated <= 0L) continue
                remainingToAllocate -= allocated

                val dueTs = pur.purchaseTimestamp + defaultSupplierCreditDays.toLong() * DAY_MILLIS
                val diffDays = ((dueTs - nowMillis) / DAY_MILLIS).toInt()
                val status = when {
                    dueTs < nowMillis - DAY_MILLIS -> PayableDueStatus.OVERDUE
                    diffDays == 0 -> PayableDueStatus.DUE_TODAY
                    diffDays in 1..7 -> PayableDueStatus.UPCOMING_7_DAYS
                    else -> PayableDueStatus.SCHEDULED
                }
                items += SupplierPayableScheduleItem(
                    purchaseId = pur.localId,
                    invoiceNumber = pur.invoiceNumber,
                    supplierId = sup.supplierId,
                    supplierNameArabic = sup.companyName,
                    purchaseTimestamp = pur.purchaseTimestamp,
                    dueDateTimestamp = dueTs,
                    totalAmountMinor = pur.totalAmountMinor,
                    paidAmountMinor = pur.totalAmountMinor - allocated,
                    remainingPayableMinor = allocated,
                    daysUntilDue = diffDays,
                    dueStatus = status
                )
            }
        }

        val sortedItems = items.sortedBy { it.dueDateTimestamp }
        val totalPayables = supplierBalances.sumOf { it.outstandingPayableMinor.coerceAtLeast(0L) }
        val overdue = sortedItems.filter { it.dueStatus == PayableDueStatus.OVERDUE }.sumOf { it.remainingPayableMinor }
        val dueToday = sortedItems.filter { it.dueStatus == PayableDueStatus.DUE_TODAY }.sumOf { it.remainingPayableMinor }
        val upcoming7 = sortedItems.filter { it.dueStatus == PayableDueStatus.UPCOMING_7_DAYS }.sumOf { it.remainingPayableMinor }

        return SupplierPayablesSummary(
            totalPayablesMinor = totalPayables,
            overduePayablesMinor = overdue,
            dueTodayPayablesMinor = dueToday,
            upcoming7DaysPayablesMinor = upcoming7,
            scheduleItems = sortedItems
        )
    }
}

/**
 * V4 Section 7: Advanced Returns & Exchanges Policy Engine (المرتجعات والاستبدال المتقدم).
 */
enum class ReturnConditionType(val code: String, val arabicLabel: String) {
    RESTOCKABLE("RESTOCKABLE", "سليم — يعاد للمخزون القابل للبيع"),
    DAMAGED_SCRAP("DAMAGED_SCRAP", "معيب / تالف — يحول للتالف ولا يعاد للبيع")
}

enum class ReturnSettlementMode(val code: String, val arabicLabel: String) {
    CASH_REFUND("CASH_REFUND", "رد نقدي من الخزينة"),
    WALLET_REFUND("WALLET_REFUND", "رد عبر محفظة إلكترونية"),
    CUSTOMER_CREDIT("CUSTOMER_CREDIT", "خصم من مديونية العميل / رصيد دائن"),
    EXCHANGE_OFFSET("EXCHANGE_OFFSET", "مقاصة استبدال بصنف بديل")
}

data class AdvancedReturnLineInput(
    val saleItemId: String,
    val returnQuantity: Int,
    val condition: ReturnConditionType = ReturnConditionType.RESTOCKABLE
)

data class EvaluatedReturnLine(
    val saleItem: SaleItemEntity,
    val returnQuantity: Int,
    val remainingReturnableBefore: Int,
    val condition: ReturnConditionType,
    val refundLineAmountMinor: Long,
    val returnedCogsMinor: Long
)

data class AdvancedReturnEvaluation(
    val isValid: Boolean,
    val validationErrorArabic: String? = null,
    val isFullInvoiceReturn: Boolean = false,
    val evaluatedLines: List<EvaluatedReturnLine> = emptyList(),
    val totalRefundAmountMinor: Long = 0L,
    val totalReturnedCogsMinor: Long = 0L,
    val replacementTotalMinor: Long = 0L,
    val netPriceDifferenceMinor: Long = 0L // > 0 means customer owes extra; < 0 means shop owes customer refund
)

object AdvancedReturnsPolicyEngine {

    fun evaluateReturnOrExchange(
        originalSale: SaleEntity,
        originalSaleItems: List<SaleItemEntity>,
        requestedLines: List<AdvancedReturnLineInput>,
        replacementTotalMinor: Long = 0L
    ): AdvancedReturnEvaluation {
        if (originalSale.status == "REFUNDED") {
            return AdvancedReturnEvaluation(
                isValid = false,
                validationErrorArabic = "هذه الفاتورة (#${originalSale.invoiceNumber}) مستردة بالكامل مسبقاً ولا يمكن إرجاعها مرة أخرى!"
            )
        }
        if (requestedLines.isEmpty()) {
            return AdvancedReturnEvaluation(
                isValid = false,
                validationErrorArabic = "يرجى تحديد صنف واحد على الأقل وكمية صحيحة للإرجاع"
            )
        }

        val saleItemsMap = originalSaleItems.associateBy { it.localId }
        val evaluated = mutableListOf<EvaluatedReturnLine>()
        val cumulativeInBatchByItem = mutableMapOf<String, Int>()

        val grossItemsTotal = originalSaleItems.sumOf { it.lineTotalMinor }.coerceAtLeast(1L)

        for (req in requestedLines) {
            if (req.returnQuantity <= 0) {
                return AdvancedReturnEvaluation(
                    isValid = false,
                    validationErrorArabic = "كمية المرتجع يجب أن تكون أكبر من الصفر"
                )
            }
            val item = saleItemsMap[req.saleItemId]
                ?: return AdvancedReturnEvaluation(
                    isValid = false,
                    validationErrorArabic = "بند الفاتورة المطلوب إرجاعه غير موجود في الفاتورة الأصلية"
                )
            val alreadyInBatch = cumulativeInBatchByItem[req.saleItemId] ?: 0
            val remainingReturnable = (item.quantity - item.returnedQuantity - alreadyInBatch).coerceAtLeast(0)
            if (req.returnQuantity > remainingReturnable) {
                return AdvancedReturnEvaluation(
                    isValid = false,
                    validationErrorArabic = "الكمية المطلوب إرجاعها (${req.returnQuantity}) تتجاوز الكمية القابلة للإرجاع ($remainingReturnable) من أصل (${item.quantity})!"
                )
            }
            cumulativeInBatchByItem[req.saleItemId] = alreadyInBatch + req.returnQuantity

            // Pro-rata net refund amount for the returned units (accounting for invoice-level discount/tax)
            val lineNetAfterInvoiceAdj = (item.lineTotalMinor * originalSale.totalAmountMinor) / grossItemsTotal
            val refundLineMinor = if (req.returnQuantity == item.quantity && originalSaleItems.size == 1) {
                originalSale.totalAmountMinor
            } else {
                (lineNetAfterInvoiceAdj * req.returnQuantity.toLong()) / item.quantity.toLong()
            }
            val returnedCogs = item.unitCostAtSaleMinor * req.returnQuantity.toLong()

            evaluated += EvaluatedReturnLine(
                saleItem = item,
                returnQuantity = req.returnQuantity,
                remainingReturnableBefore = remainingReturnable,
                condition = req.condition,
                refundLineAmountMinor = refundLineMinor,
                returnedCogsMinor = returnedCogs
            )
        }

        val isFullReturn = originalSaleItems.all { item ->
            val returningNow = evaluated.filter { it.saleItem.localId == item.localId }.sumOf { it.returnQuantity }
            (item.returnedQuantity + returningNow) >= item.quantity
        }

        val totalRefund = if (isFullReturn && originalSaleItems.all { it.returnedQuantity == 0 }) {
            originalSale.totalAmountMinor
        } else {
            evaluated.sumOf { it.refundLineAmountMinor }
        }
        val totalReturnedCogs = evaluated.sumOf { it.returnedCogsMinor }
        val netDiff = replacementTotalMinor - totalRefund

        return AdvancedReturnEvaluation(
            isValid = true,
            validationErrorArabic = null,
            isFullInvoiceReturn = isFullReturn,
            evaluatedLines = evaluated,
            totalRefundAmountMinor = totalRefund,
            totalReturnedCogsMinor = totalReturnedCogs,
            replacementTotalMinor = replacementTotalMinor,
            netPriceDifferenceMinor = netDiff
        )
    }
}

/**
 * V4 Section 13: Daily Operational Z-Report Engine (التقرير التشغيلي اليومي الشامل).
 */
data class DailyOperationalZReport(
    val reportDateLabel: String,
    val openingCashMinor: Long,
    val cashSalesInMinor: Long,
    val creditSalesMinor: Long,
    val customerCollectionsInMinor: Long,
    val supplierPaymentsOutMinor: Long,
    val expensesOutMinor: Long,
    val returnsMinor: Long,
    val discountsMinor: Long,
    val walletInMinor: Long,
    val walletOutMinor: Long,
    val walletNetCommissionMinor: Long,
    val maintenanceRevenueMinor: Long,
    val maintenanceDeliveredCount: Int,
    val expectedClosingCashMinor: Long,
    val actualCountedCashMinor: Long?,
    val cashVarianceMinor: Long,
    val grossSalesProfitMinor: Long,
    val trueNetDailyProfitMinor: Long,
    val invoicesCount: Int,
    val returnsCount: Int,
    val shiftsClosedCount: Int
)

object DailyOperationalZReportEngine {

    fun buildDailyZReport(
        dayStartMillis: Long,
        dayEndMillis: Long,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        refunds: List<RefundEntity>,
        purchases: List<PurchaseEntity>,
        expenses: List<ExpenseEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        maintenanceParts: List<MaintenancePartEntity>,
        inventoryMovements: List<com.example.database.entities.InventoryMovementEntity>,
        ledgerTransactions: List<TransactionEntity>,
        cashSessions: List<com.example.database.entities.CashSessionEntity>,
        currentDrawerBalanceMinor: Long
    ): DailyOperationalZReport {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateLabel = dateFormat.format(java.util.Date(dayStartMillis))

        val daySales = sales.filter {
            it.saleTimestamp in dayStartMillis..dayEndMillis && it.status != "CANCELLED"
        }
        val cashSalesIn = daySales.sumOf { it.paidAmountMinor }
        val creditSales = daySales.sumOf { (it.totalAmountMinor - it.paidAmountMinor).coerceAtLeast(0L) }
        val discounts = daySales.sumOf { it.discountMinor }

        val dayRefunds = refunds.filter { it.refundTimestamp in dayStartMillis..dayEndMillis }
        val returnsMinor = dayRefunds.sumOf { it.refundAmountMinor }

        val dayTx = ledgerTransactions.filter {
            it.status == LedgerTransactionStatus.POSTED &&
                it.transactionTimestamp in dayStartMillis..dayEndMillis
        }

        val collectionsIn = dayTx
            .filter { it.relatedEntityType == "CUSTOMER_COLLECTION" }
            .sumOf { it.amountMinor }

        val supplierPaymentsOut = purchases
            .filter { it.purchaseTimestamp in dayStartMillis..dayEndMillis }
            .sumOf { it.paidAmountMinor } +
            dayTx.filter { it.relatedEntityType == "SUPPLIER_PAYMENT" }.sumOf { it.amountMinor }

        val expensesOut = expenses
            .filter { it.expenseTimestamp in dayStartMillis..dayEndMillis }
            .sumOf { it.amountMinor }

        val walletIn = dayTx
            .filter { it.destinationAccountType == FinancialAccountType.WALLET }
            .sumOf { it.amountMinor }
        val walletOut = dayTx
            .filter { it.sourceAccountType == FinancialAccountType.WALLET }
            .sumOf { it.amountMinor }
        val walletNetComm = dayTx
            .filter { it.sourceAccountType == FinancialAccountType.WALLET || it.destinationAccountType == FinancialAccountType.WALLET }
            .sumOf { it.commissionMinor - it.feeMinor }

        val maintRevenue = dayTx
            .filter { it.relatedEntityType == "MAINTENANCE" || it.relatedEntityType == "MAINTENANCE_DEPOSIT" }
            .sumOf { it.amountMinor }
        val maintDelivered = maintenanceOrders.count {
            (it.deliveredAt ?: it.updatedAt) in dayStartMillis..dayEndMillis &&
                (it.status == MaintenanceStatus.DELIVERED || it.status == MaintenanceStatus.CLOSED)
        }

        val daySessions = cashSessions.filter {
            it.openedAt in dayStartMillis..dayEndMillis || (it.closedAt != null && it.closedAt in dayStartMillis..dayEndMillis)
        }
        val openingCash = daySessions.minByOrNull { it.openedAt }?.openingDerivedBalanceMinor ?: 0L
        val latestClosed = daySessions.filter { it.status == "CLOSED" }.maxByOrNull { it.closedAt ?: 0L }
        val actualCounted = latestClosed?.closingCountedCashMinor
        val cashVariance = daySessions.sumOf { it.discrepancyMinor ?: 0L }

        val profitability = TrueProfitabilityEngine.computeTrueProfitability(
            startMillis = dayStartMillis,
            endMillis = dayEndMillis,
            sales = sales,
            saleItems = saleItems,
            refunds = refunds,
            maintenanceOrders = maintenanceOrders,
            maintenanceParts = maintenanceParts,
            expenses = expenses,
            inventoryMovements = inventoryMovements,
            ledgerTransactions = ledgerTransactions
        )

        return DailyOperationalZReport(
            reportDateLabel = dateLabel,
            openingCashMinor = openingCash,
            cashSalesInMinor = cashSalesIn,
            creditSalesMinor = creditSales,
            customerCollectionsInMinor = collectionsIn,
            supplierPaymentsOutMinor = supplierPaymentsOut,
            expensesOutMinor = expensesOut,
            returnsMinor = returnsMinor,
            discountsMinor = discounts,
            walletInMinor = walletIn,
            walletOutMinor = walletOut,
            walletNetCommissionMinor = walletNetComm,
            maintenanceRevenueMinor = maintRevenue,
            maintenanceDeliveredCount = maintDelivered,
            expectedClosingCashMinor = currentDrawerBalanceMinor,
            actualCountedCashMinor = actualCounted,
            cashVarianceMinor = cashVariance,
            grossSalesProfitMinor = profitability.salesGrossProfitMinor,
            trueNetDailyProfitMinor = profitability.trueNetProfitMinor,
            invoicesCount = daySales.size,
            returnsCount = dayRefunds.size,
            shiftsClosedCount = daySessions.count { it.status == "CLOSED" }
        )
    }
}


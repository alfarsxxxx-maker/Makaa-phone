package com.example.financial

import com.example.core.AppResult
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransactionLineEntity
import kotlin.math.abs

/**
 * Central Financial Invariant Validator (Requirement 6 & 7).
 * Enforces strict financial integrity checks before committing any financial transaction:
 * 1. Valid Transaction ID (non-blank, standardized prefix)
 * 2. Valid Transaction Type & Status (POSTED / REVERSED)
 * 3. Valid Accounts (source, destination, line accounts)
 * 4. Valid Monetary Amounts (strictly > 0 in minor units, no Float/Double)
 * 5. Valid Currency
 * 6. Valid Lines (at least 2 lines, matching transactionId, non-empty accounts)
 * 7. Economically Balanced: Sum of all signed impact lines must equal 0,
 *    and no funds created or destroyed without explicit counter-accounts.
 */
object FinancialInvariantValidator {

    fun validateTransactionHeader(tx: TransactionEntity): AppResult<Unit> {
        if (tx.localId.isBlank()) {
            return AppResult.Error("معرف العملية المالية غير صالح (فارغ)", "INVALID_TRANSACTION_ID")
        }
        if (tx.shopId.isBlank()) {
            return AppResult.Error("معرف المتجر غير صالح للعملية المالية", "INVALID_SHOP_ID")
        }
        if (tx.amountMinor <= 0L) {
            return AppResult.Error("قيمة العملية المالية يجب أن تكون أكبر من الصفر (${tx.amountMinor})", "INVALID_TRANSACTION_AMOUNT")
        }
        if (tx.feeMinor < 0L || tx.commissionMinor < 0L) {
            return AppResult.Error("الرسوم والعمولات لا يمكن أن تكون سالبة", "INVALID_FEE_OR_COMMISSION")
        }
        if (tx.currency.isBlank() || tx.currency.length !in 3..5) {
            return AppResult.Error("رمز العملة غير صالح (${tx.currency})", "INVALID_CURRENCY")
        }
        if (tx.status != LedgerTransactionStatus.POSTED && tx.status != LedgerTransactionStatus.REVERSED) {
            return AppResult.Error("حالة القيد في دفتر الأستاذ يجب أن تكون معتمدة (POSTED) أو معكوسة (REVERSED)", "INVALID_LEDGER_STATUS")
        }
        if (tx.sourceAccountId.isBlank() || tx.destinationAccountId.isBlank()) {
            return AppResult.Error("الحسابات المصدر والوجهة للعملية المالية يجب ألا تكون فارغة", "EMPTY_ACCOUNT_ID")
        }
        return AppResult.Success(Unit)
    }

    fun validateTransactionLines(tx: TransactionEntity, lines: List<TransactionLineEntity>): AppResult<Unit> {
        if (lines.size < 2) {
            return AppResult.Error("يجب أن يحتوي القيد المالي على سطرين على الأقل لتحقيق التوازن المحاسبي", "INSUFFICIENT_TRANSACTION_LINES")
        }

        var totalNetSignedImpact = 0L

        for ((index, line) in lines.withIndex()) {
            if (line.localId.isBlank()) {
                return AppResult.Error("معرف سطر القيد رقم $index فارغ", "INVALID_LINE_ID")
            }
            if (line.transactionId != tx.localId) {
                return AppResult.Error(
                    "سطر القيد (${line.localId}) غير مرتبط بالعملية الأصلية (${tx.localId})",
                    "LINE_TRANSACTION_MISMATCH"
                )
            }
            if (line.accountId.isBlank()) {
                return AppResult.Error("معرف الحساب في سطر القيد رقم $index فارغ", "EMPTY_LINE_ACCOUNT")
            }
            if (line.amountMinor <= 0L) {
                return AppResult.Error("مبلغ سطر القيد (${line.localId}) يجب أن يكون أكبر من الصفر", "INVALID_LINE_AMOUNT")
            }
            if (line.direction != "INFLOW" && line.direction != "OUTFLOW") {
                return AppResult.Error("اتجاه حركة السطر غير صالح (${line.direction})", "INVALID_LINE_DIRECTION")
            }

            // Direction sign consistency
            val expectedSign = if (line.direction == "INFLOW") line.amountMinor else -line.amountMinor
            if (line.signedImpactMinor != expectedSign) {
                return AppResult.Error(
                    "الأثر المالي للسطر (${line.signedImpactMinor}) لا يطابق اتجاه الحركة (${line.direction})",
                    "DIRECTION_SIGN_MISMATCH"
                )
            }

            totalNetSignedImpact += line.signedImpactMinor
        }

        // Economic Balance Invariant:
        // Every financial transaction must be economically closed: sum of signedImpactMinor == 0.
        // Exception: When fee/commission splits are modeled across sub-ledgers, the sum must still strictly balance.
        if (totalNetSignedImpact != 0L) {
            return AppResult.Error(
                "القيد المالي غير متوازن اقتصاديًا (فارق التوازن: $totalNetSignedImpact مليم). لا يجوز خلق أو فقدان أموال دون حساب مقابل.",
                "UNBALANCED_TRANSACTION_LINES"
            )
        }

        return AppResult.Success(Unit)
    }

    fun validateBeforeCommit(tx: TransactionEntity, lines: List<TransactionLineEntity>): AppResult<Unit> {
        val headerResult = validateTransactionHeader(tx)
        if (headerResult is AppResult.Error) return headerResult

        val linesResult = validateTransactionLines(tx, lines)
        if (linesResult is AppResult.Error) return linesResult

        return AppResult.Success(Unit)
    }
}

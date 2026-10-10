package com.example.transaction

import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.financial.Money
import com.example.financial.PurchaseLineInput
import com.example.financial.SaleLineInput
import com.example.repository.MekkaPhoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified Transaction Execution Coordinator (Sections 2, 3, 9, 10).
 * Strictly guarantees that no AI or raw input mutates the database without
 * validation, user review, confirmation, and role permission checks.
 */
class UnifiedTransactionExecutionCoordinator(
    private val repository: MekkaPhoneRepository,
    private val validationEngine: TransactionValidationEngine
) {

    suspend fun executeConfirmedDraft(
        draft: TransactionDraft,
        userConfirmed: Boolean,
        actorUserId: String,
        shopId: String = MekkaPhoneRepository.DEFAULT_SHOP_ID,
        deviceId: String = MekkaPhoneRepository.DEFAULT_DEVICE_ID
    ): AppResult<TransactionExecutionResult> = withContext(Dispatchers.IO) {
        // 1. Mandatory Confirmation Gate (Section 3 & 10)
        if (!userConfirmed) {
            return@withContext AppResult.Error(
                "تم إيقاف العملية: تتطلب العمليات المالية والمخزنية تأكيد المستخدم الصريح بعد المراجعة",
                "CONFIRMATION_REQUIRED"
            )
        }

        // 2. Pre-execution Smart Validation (Section 11)
        val validation = validationEngine.validateDraft(draft, actorUserId, shopId)
        if (!validation.canProceedToConfirmation) {
            val combinedErrors = validation.errorMessagesArabic.joinToString(" • ")
            return@withContext AppResult.Error(
                "فشل التحقق من صحة مسودة العملية: $combinedErrors",
                "VALIDATION_FAILED"
            )
        }

        val idempotencyKey = "UNIFIED:${draft.draftId}:${draft.transactionType.name}"

        // 3. Dispatch to Real Atomic Transaction Core
        val executionResult: AppResult<TransactionExecutionResult> = when (draft.transactionType) {
            UnifiedTransactionType.SALE -> {
                val saleLines = draft.items.map { item ->
                    SaleLineInput(
                        productId = item.productId,
                        serialNumberId = item.serialNumberId,
                        quantity = item.quantity,
                        unitSellingPriceMinor = item.unitPriceMinor
                    )
                }
                val paidOverride = if (draft.paymentMode == "CREDIT") 0L else draft.paidAmountMinor
                val paymentAccType = if (draft.paymentMode == "WALLET") FinancialAccountType.WALLET else FinancialAccountType.CASH_DRAWER
                val paymentAccId = draft.targetAccountId ?: if (draft.paymentMode == "WALLET") "WLT-01" else MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID

                val res = repository.executeSale(
                    customerId = draft.counterpartyId,
                    items = saleLines,
                    invoiceDiscountMinor = draft.invoiceDiscountMinor,
                    paidAmountMinorOverride = paidOverride,
                    paymentAccountType = paymentAccType,
                    paymentAccountId = paymentAccId,
                    notes = draft.notes.ifBlank { "عملية بيع ذكية (${draft.inputModality.titleArabic})" },
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.invoiceNumber,
                            summaryArabic = "تم تنفيذ فاتورة البيع #${res.data.invoiceNumber} بقيمة ${Money.ofMinor(res.data.totalAmountMinor).formatArabic()} وتحديث المخزون ودفتر الأستاذ"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.PURCHASE -> {
                val purchaseLines = draft.items.map { item ->
                    PurchaseLineInput(
                        productId = item.productId,
                        quantity = item.quantity,
                        unitCostMinor = item.unitPriceMinor,
                        serialNumbersOrImeis = item.serialOrImei?.let { listOf(it) } ?: emptyList()
                    )
                }
                val paidOverride = if (draft.paymentMode == "CREDIT") 0L else draft.paidAmountMinor

                val res = repository.executePurchase(
                    supplierId = draft.counterpartyId ?: "SUP-GENERIC",
                    items = purchaseLines,
                    discountMinor = draft.invoiceDiscountMinor,
                    paidAmountMinorOverride = paidOverride,
                    notes = draft.notes.ifBlank { "فاتورة شراء (${draft.inputModality.titleArabic})" },
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.invoiceNumber,
                            summaryArabic = "تم تسجيل فاتورة الشراء #${res.data.invoiceNumber} بقيمة ${Money.ofMinor(res.data.totalAmountMinor).formatArabic()} وتحديث المتوسط المرجح WAC"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.EXPENSE -> {
                val accType = if (draft.paymentMode == "WALLET") FinancialAccountType.WALLET else FinancialAccountType.CASH_DRAWER
                val accId = draft.targetAccountId ?: MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID

                val res = repository.executeExpense(
                    categoryName = draft.expenseCategoryName ?: "مصروفات عامة",
                    title = draft.notes.ifBlank { "مصروف تشغيلي" },
                    amountMinor = draft.totalAmountMinor,
                    sourceAccountType = accType,
                    sourceAccountId = accId,
                    notes = draft.notes,
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.ledgerTransactionId,
                            summaryArabic = "تم صرف المصروف بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} وقيد السند في دفتر الأستاذ"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.CASH_IN -> {
                val res = repository.executeCashOperation(
                    operationType = FinancialTransactionType.CASH_IN,
                    amountMinor = draft.totalAmountMinor,
                    reasonArabic = draft.notes.ifBlank { "توريد نقدية للخزينة" },
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.referenceNumber,
                            summaryArabic = "تم توريد مبلغ ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} إلى الخزينة النقدية الرئيسية"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.CASH_OUT -> {
                val res = repository.executeCashOperation(
                    operationType = FinancialTransactionType.CASH_OUT,
                    amountMinor = draft.totalAmountMinor,
                    reasonArabic = draft.notes.ifBlank { "صرف نقدية من الخزينة" },
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.referenceNumber,
                            summaryArabic = "تم صرف مبلغ ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} من الخزينة النقدية وتوثيق القيد"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.WALLET_DEPOSIT,
            UnifiedTransactionType.WALLET_WITHDRAW -> {
                val kind = if (draft.transactionType == UnifiedTransactionType.WALLET_DEPOSIT) "DEPOSIT" else "WITHDRAW"
                val walletId = draft.targetAccountId ?: "WLT-01"

                val res = repository.executeWalletOperation(
                    walletId = walletId,
                    transferKind = kind,
                    customerPhone = draft.counterpartyPhone ?: "",
                    principalAmountMinor = draft.totalAmountMinor,
                    providerFeeMinor = 0L,
                    shopCommissionMinor = 0L,
                    notes = draft.notes,
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.transferReference,
                            summaryArabic = "تم تنفيذ معاملة المحفظة ($kind) بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} وتحديث أرصدة السيولة"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.CUSTOMER_PAYMENT -> {
                val custId = draft.counterpartyId
                    ?: return@withContext AppResult.Error("يرجى تحديد العميل لتحصيل الدفعة", "MISSING_CUSTOMER")

                val res = repository.recordCustomerPaymentCollection(
                    customerId = custId,
                    amountMinor = draft.totalAmountMinor,
                    destinationAccountType = FinancialAccountType.CASH_DRAWER,
                    destinationAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
                    notes = draft.notes,
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.referenceNumber,
                            summaryArabic = "تم تحصيل دفعة العميل بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} وتخفيض مديونيته القائمة"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            UnifiedTransactionType.SUPPLIER_PAYMENT -> {
                val supId = draft.counterpartyId
                    ?: return@withContext AppResult.Error("يرجى تحديد المورد لسداد الدفعة", "MISSING_SUPPLIER")

                val res = repository.recordSupplierPayment(
                    supplierId = supId,
                    amountMinor = draft.totalAmountMinor,
                    notes = draft.notes,
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.referenceNumber,
                            summaryArabic = "تم سداد دفعة المورد بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()} وتخفيض الالتزام المحاسبي"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }

            else -> {
                // Fallback for remaining operational types
                val res = repository.executeCashOperation(
                    operationType = FinancialTransactionType.CASH_IN,
                    amountMinor = draft.totalAmountMinor,
                    reasonArabic = draft.notes.ifBlank { draft.transactionType.titleArabic },
                    actorUserId = actorUserId,
                    idempotencyKey = idempotencyKey
                )
                when (res) {
                    is AppResult.Success -> AppResult.Success(
                        TransactionExecutionResult(
                            isSuccess = true,
                            executedTransactionId = res.data.localId,
                            referenceInvoiceNumber = res.data.referenceNumber,
                            summaryArabic = "تم تسجيل العملية (${draft.transactionType.titleArabic}) بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()}"
                        )
                    )
                    is AppResult.Error -> AppResult.Error(res.message, res.code, res.cause)
                }
            }
        }

        // 4. Audit Log Recording (Requirement 2 & 3)
        if (executionResult is AppResult.Success) {
            val now = System.currentTimeMillis()
            val audit = AuditLogEntity(
                localId = IdGenerator.newPrefixedId("AUD"),
                shopId = shopId,
                actorUserId = actorUserId,
                actorRoleCode = "CONFIRMED_USER",
                actionType = "UNIFIED_TRANSACTION_EXECUTION",
                entityType = draft.transactionType.name,
                entityId = executionResult.data.executedTransactionId ?: draft.draftId,
                summaryArabic = "تنفيذ عملية ذكية (${draft.transactionType.titleArabic}) مدخلة عبر (${draft.inputModality.titleArabic}) بقيمة ${Money.ofMinor(draft.totalAmountMinor).formatArabic()}",
                beforeStateJson = null,
                afterStateJson = """{"draftId":"${draft.draftId}","modality":"${draft.inputModality.name}","amountMinor":${draft.totalAmountMinor},"confirmed":true}""",
                deviceId = deviceId,
                timestamp = now
            )
            repository.database.systemIntegrityDao().insertAuditLog(audit)
        }

        executionResult
    }
}

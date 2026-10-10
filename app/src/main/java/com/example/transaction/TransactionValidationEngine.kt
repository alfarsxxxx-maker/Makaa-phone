package com.example.transaction

import com.example.auth.AuthSecurityManager
import com.example.auth.RolePermissionMatrix
import com.example.database.MekkaPhoneDatabase
import com.example.financial.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Smart Transaction Validation Engine (Sections 10 & 11).
 * Validates product catalog presence, stock availability, valid prices,
 * customer credit limits, user RBAC permissions, and duplicate guards before confirmation.
 */
class TransactionValidationEngine(
    private val database: MekkaPhoneDatabase,
    private val authSecurityManager: AuthSecurityManager,
    private val businessLimitsManager: BusinessLimitsManager = BusinessLimitsManager(database)
) {
    private val catalogDao = database.catalogInventoryDao()

    suspend fun validateDraft(
        draft: TransactionDraft,
        actorUserId: String,
        shopId: String
    ): TransactionDraftValidation = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val shortageItems = mutableListOf<String>()

        // 1. RBAC Permission Check
        val user = database.coreShopAuthDao().getUserById(actorUserId)
        val roleCode = user?.roleCode ?: "CASHIER"
        val requiredPermission = draft.transactionType.requiredPermission

        val hasPermission = RolePermissionMatrix.hasPermission(roleCode, requiredPermission)
        if (!hasPermission) {
            errors.add("حسابك الحالي ($roleCode) غير مصرح له بتنفيذ (${draft.transactionType.titleArabic}) — يلزم صلاحية (${requiredPermission.name})")
        }

        // 2. Amount and Balance Check
        if (draft.totalAmountMinor <= 0L) {
            errors.add("قيمة العملية الإجمالية يجب أن تكون أكبر من صفر")
        }
        if (draft.paidAmountMinor < 0L || draft.paidAmountMinor > draft.totalAmountMinor) {
            errors.add("المبلغ المدفوع يجب أن يكون بين صفر وإجمالي العملية")
        }

        // 3. Ambiguities & Missing Requirements Check
        if (draft.ambiguities.isNotEmpty()) {
            errors.add("يرجى حسم الخيارات المتعارضة أولاً قبل المتابعة")
        }
        if (draft.missingRequirementsArabic.isNotEmpty()) {
            errors.addAll(draft.missingRequirementsArabic.map { "بيان ناقص: $it" })
        }

        // 4. Business Limits Controls (Section 28)
        when (draft.transactionType) {
            UnifiedTransactionType.SALE -> {
                val discountCheck = businessLimitsManager.checkSaleDiscount(
                    totalBeforeDiscountMinor = draft.totalAmountMinor + draft.invoiceDiscountMinor,
                    discountMinor = draft.invoiceDiscountMinor,
                    actorRoleCode = roleCode
                )
                if (!discountCheck.isAllowed && discountCheck.blockReasonArabic != null) {
                    errors.add(discountCheck.blockReasonArabic)
                } else if (discountCheck.warningMessageArabic != null) {
                    warnings.add(discountCheck.warningMessageArabic)
                }
            }
            UnifiedTransactionType.EXPENSE -> {
                val expenseCheck = businessLimitsManager.checkExpenseLimit(
                    amountMinor = draft.totalAmountMinor,
                    actorRoleCode = roleCode
                )
                if (!expenseCheck.isAllowed && expenseCheck.blockReasonArabic != null) {
                    errors.add(expenseCheck.blockReasonArabic)
                } else if (expenseCheck.warningMessageArabic != null) {
                    warnings.add(expenseCheck.warningMessageArabic)
                }
            }
            UnifiedTransactionType.REFUND -> {
                val refundCheck = businessLimitsManager.checkRefundLimit(
                    amountMinor = draft.totalAmountMinor,
                    actorRoleCode = roleCode
                )
                if (!refundCheck.isAllowed && refundCheck.blockReasonArabic != null) {
                    errors.add(refundCheck.blockReasonArabic)
                } else if (refundCheck.warningMessageArabic != null) {
                    warnings.add(refundCheck.warningMessageArabic)
                }
            }
            else -> {}
        }

        // 4. Product & Stock Checks (for Sales, Purchases, Refunds, Adjustments)
        if (draft.transactionType == UnifiedTransactionType.SALE) {
            if (draft.items.isEmpty()) {
                errors.add("يجب إضافة صنف واحد على الأقل لفاتورة البيع")
            }
            val loadedProducts = catalogDao.observeProductInventoryProjections(shopId).first().associateBy { it.productId }

            for (item in draft.items) {
                val dbProduct = loadedProducts[item.productId]
                if (dbProduct == null) {
                    errors.add("المنتج (${item.productNameArabic}) غير مسجل في كتالوج المتجر")
                    continue
                }
                if (item.quantity <= 0) {
                    errors.add("الكمية للصنف (${item.productNameArabic}) يجب أن تكون 1 أو أكثر")
                }
                if (item.unitPriceMinor <= 0L) {
                    errors.add("سعر بيع (${item.productNameArabic}) غير صالح")
                }
                if (item.unitPriceMinor < dbProduct.minSellingPriceMinor) {
                    warnings.add("سعر البيع (${Money.ofMinor(item.unitPriceMinor).formatArabic()}) أقل من الحد الأدنى المعتمد (${Money.ofMinor(dbProduct.minSellingPriceMinor).formatArabic()})")
                }
                if (dbProduct.derivedStockQty < item.quantity) {
                    errors.add("الرصيد غير كافٍ للصنف (${item.productNameArabic}): المتاح (${dbProduct.derivedStockQty}) قطعة والمطلوب (${item.quantity})")
                    shortageItems.add(item.productNameArabic)
                }
                if (item.isSerialized && item.serialNumberId.isNullOrBlank() && item.serialOrImei.isNullOrBlank()) {
                    warnings.add("الصنف (${item.productNameArabic}) خاضع لتتبع السيريال/IMEI — يفضل مسح السيريال قبل التأكيد")
                }
            }

            // Customer Credit Check
            if (draft.remainingDebtMinor > 0L) {
                if (draft.counterpartyId.isNullOrBlank()) {
                    errors.add("البيع الآجل أو الجزئي يتطلب اختيار عميل مسجل لربط المديونية بحسابه")
                } else {
                    val customer = catalogDao.getCustomerById(draft.counterpartyId)
                    if (customer == null) {
                        errors.add("العميل المحدد غير موجود في قاعدة البيانات")
                    } else if (!customer.isActive || customer.isDeleted) {
                        errors.add("حساب العميل (${customer.fullName}) معطل أو غير نشط")
                    }
                }
            }
        }

        if (draft.transactionType == UnifiedTransactionType.PURCHASE) {
            if (draft.items.isEmpty()) {
                errors.add("يجب إضافة صنف واحد على الأقل لفاتورة الشراء")
            }
            if (draft.counterpartyId.isNullOrBlank()) {
                errors.add("فاتورة الشراء تتطلب تحديد المورد")
            }
        }

        val canProceed = errors.isEmpty() && hasPermission

        TransactionDraftValidation(
            isValid = errors.isEmpty(),
            canProceedToConfirmation = canProceed,
            errorMessagesArabic = errors,
            warningMessagesArabic = warnings,
            missingRolePermission = if (!hasPermission) requiredPermission else null,
            stockShortageItems = shortageItems
        )
    }
}

package com.example.transaction

import com.example.auth.PermissionCode
import com.example.auth.RolePermissionMatrix
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.AppSettingEntity
import com.example.financial.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Business Controls and Financial Operational Limits (V4.1 Part 2 - Section 28).
 * Instead of personal budgeting, enforces strict business governance:
 * - Maximum Discount Percentage / Minor limit
 * - Expense Approval Limit
 * - Refund Approval Limit
 * - Cash Variance and Adjustment Approval Limit
 */
data class BusinessLimitsConfig(
    val maxDiscountPercent: Int = 10, // Max 10% discount on sales without manager approval
    val maxDiscountMinor: Long = 50_000L, // Max 500 EGP discount
    val expenseApprovalThresholdMinor: Long = 200_000L, // Expenses > 2,000 EGP require approval
    val refundApprovalThresholdMinor: Long = 100_000L, // Refunds > 1,000 EGP require approval
    val cashAdjustmentThresholdMinor: Long = 50_000L // Cash adjustments > 500 EGP require audit
)

data class BusinessLimitCheckResult(
    val isAllowed: Boolean,
    val requiresManagerApproval: Boolean,
    val warningMessageArabic: String? = null,
    val blockReasonArabic: String? = null
)

class BusinessLimitsManager(
    private val database: MekkaPhoneDatabase
) {
    companion object {
        const val SETTING_KEY_BUSINESS_LIMITS = "BUSINESS_LIMITS_CONTROLS_CONFIG"
    }

    private var currentConfig = BusinessLimitsConfig()
    private var isLoaded = false

    suspend fun getLimits(): BusinessLimitsConfig = withContext(Dispatchers.IO) {
        if (!isLoaded) {
            try {
                val setting = database.coreShopAuthDao().getSetting("shop_mekka_main", SETTING_KEY_BUSINESS_LIMITS)
                if (setting != null && setting.settingValue.isNotBlank()) {
                    val json = JSONObject(setting.settingValue)
                    currentConfig = BusinessLimitsConfig(
                        maxDiscountPercent = json.optInt("maxDiscountPercent", 10),
                        maxDiscountMinor = json.optLong("maxDiscountMinor", 50_000L),
                        expenseApprovalThresholdMinor = json.optLong("expenseApprovalThresholdMinor", 200_000L),
                        refundApprovalThresholdMinor = json.optLong("refundApprovalThresholdMinor", 100_000L),
                        cashAdjustmentThresholdMinor = json.optLong("cashAdjustmentThresholdMinor", 50_000L)
                    )
                }
            } catch (_: Exception) {}
            isLoaded = true
        }
        currentConfig
    }

    suspend fun updateLimits(config: BusinessLimitsConfig, actorUserId: String): Unit = withContext(Dispatchers.IO) {
        currentConfig = config
        isLoaded = true
        val json = JSONObject().apply {
            put("maxDiscountPercent", config.maxDiscountPercent)
            put("maxDiscountMinor", config.maxDiscountMinor)
            put("expenseApprovalThresholdMinor", config.expenseApprovalThresholdMinor)
            put("refundApprovalThresholdMinor", config.refundApprovalThresholdMinor)
            put("cashAdjustmentThresholdMinor", config.cashAdjustmentThresholdMinor)
        }
        val now = System.currentTimeMillis()
        val entity = AppSettingEntity(
            localId = "SETTING_$SETTING_KEY_BUSINESS_LIMITS",
            shopId = "shop_mekka_main",
            settingKey = SETTING_KEY_BUSINESS_LIMITS,
            settingValue = json.toString(),
            deviceId = "DEVICE_PRIMARY",
            updatedByUserId = actorUserId,
            createdAt = now,
            updatedAt = now
        )
        database.coreShopAuthDao().upsertSetting(entity)
    }

    /**
     * Checks if a sale discount complies with business policy.
     */
    suspend fun checkSaleDiscount(
        totalBeforeDiscountMinor: Long,
        discountMinor: Long,
        actorRoleCode: String
    ): BusinessLimitCheckResult = withContext(Dispatchers.IO) {
        val limits = getLimits()
        if (discountMinor <= 0L) {
            return@withContext BusinessLimitCheckResult(isAllowed = true, requiresManagerApproval = false)
        }

        val discountPercent = if (totalBeforeDiscountMinor > 0L) {
            ((discountMinor * 100.0) / totalBeforeDiscountMinor).toInt()
        } else 0

        val exceedsPercent = discountPercent > limits.maxDiscountPercent
        val exceedsMinor = discountMinor > limits.maxDiscountMinor

        if (exceedsPercent || exceedsMinor) {
            val hasManagerPermission = RolePermissionMatrix.hasPermission(actorRoleCode, PermissionCode.PRICE_OVERRIDE_APPROVE)
            val msg = "قيمة الخصم (${Money.ofMinor(discountMinor).formatArabic()} بنسبة $discountPercent%) تتجاوز سقف الخصم المعتمد (${limits.maxDiscountPercent}% أو بحد أقصى ${Money.ofMinor(limits.maxDiscountMinor).formatArabic()})"
            if (hasManagerPermission) {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = true,
                    requiresManagerApproval = true,
                    warningMessageArabic = "$msg — تم الاعتماد بصلاحية الإدارة ($actorRoleCode)"
                )
            } else {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = false,
                    requiresManagerApproval = true,
                    blockReasonArabic = "$msg — يلزم موافقة مدير المتجر"
                )
            }
        }

        BusinessLimitCheckResult(isAllowed = true, requiresManagerApproval = false)
    }

    /**
     * Checks if an expense exceeds the single-expense approval threshold.
     */
    suspend fun checkExpenseLimit(
        amountMinor: Long,
        actorRoleCode: String
    ): BusinessLimitCheckResult = withContext(Dispatchers.IO) {
        val limits = getLimits()
        if (amountMinor > limits.expenseApprovalThresholdMinor) {
            val isOwnerOrManager = actorRoleCode in listOf("OWNER", "MANAGER")
            val msg = "المصروف (${Money.ofMinor(amountMinor).formatArabic()}) يتجاوز سقف الصرف المباشر (${Money.ofMinor(limits.expenseApprovalThresholdMinor).formatArabic()})"
            if (isOwnerOrManager) {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = true,
                    requiresManagerApproval = false,
                    warningMessageArabic = "$msg — اعتماد مالي موثق للمدير"
                )
            } else {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = false,
                    requiresManagerApproval = true,
                    blockReasonArabic = "$msg — يتطلب اعتماد مالي مسبق من الإدارة"
                )
            }
        }
        BusinessLimitCheckResult(isAllowed = true, requiresManagerApproval = false)
    }

    /**
     * Checks if a refund exceeds refund approval threshold.
     */
    suspend fun checkRefundLimit(
        amountMinor: Long,
        actorRoleCode: String
    ): BusinessLimitCheckResult = withContext(Dispatchers.IO) {
        val limits = getLimits()
        if (amountMinor > limits.refundApprovalThresholdMinor) {
            val hasRefundPermission = RolePermissionMatrix.hasPermission(actorRoleCode, PermissionCode.REFUND_APPROVE)
            val msg = "مبلغ المرتجع (${Money.ofMinor(amountMinor).formatArabic()}) يتجاوز سقف الرد المباشر (${Money.ofMinor(limits.refundApprovalThresholdMinor).formatArabic()})"
            if (hasRefundPermission) {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = true,
                    requiresManagerApproval = true,
                    warningMessageArabic = "$msg — تم الاعتماد بصلاحية اعتماد المرتجعات"
                )
            } else {
                return@withContext BusinessLimitCheckResult(
                    isAllowed = false,
                    requiresManagerApproval = true,
                    blockReasonArabic = "$msg — يتطلب اعتماد مرتجع مالي من المدير"
                )
            }
        }
        BusinessLimitCheckResult(isAllowed = true, requiresManagerApproval = false)
    }
}

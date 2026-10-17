package com.example.financial

import com.example.core.AppResult

/**
 * P0-D — Domain Commission Engine.
 * Supports:
 * - Fixed Commission
 * - Percentage Commission
 * - Provider-Specific Rules (Vodafone Cash, Orange Cash, e& money, InstaPay, Axis Pay, etc.)
 * - Operation-Specific Rules (DEPOSIT, WITHDRAW, TRANSFER)
 * - Tiered Amount Ranges (e.g., 0-500, 500-1000, 1000+)
 * - Profit Preview: Amount, Commission, Cost, Expected Profit, Net Customer Amount
 */
data class CommissionTierRule(
    val ruleId: String,
    val providerCode: String, // VODAFONE_CASH, ORANGE_CASH, ETISALAT_CASH, INSTAPAY, AXIS_PAY, ALL
    val operationType: WalletDomainOperationType,
    val minAmountMinor: Long = 0L,
    val maxAmountMinor: Long = Long.MAX_VALUE,
    val customerFeePercentageBps: Int = 0, // e.g. 150 bps = 1.5%
    val customerFeeFixedMinor: Long = 0L,   // e.g. 5_00L = 5.00 EGP
    val providerCostPercentageBps: Int = 0, // e.g. 50 bps = 0.5%
    val providerCostFixedMinor: Long = 0L,
    val descriptionArabic: String = ""
)

data class CommissionProfitPreview(
    val principalAmountMinor: Long,
    val customerCommissionMinor: Long, // Gross commission charged to customer
    val providerCostFeeMinor: Long,     // Network fee / cost deducted by provider
    val expectedShopProfitMinor: Long,  // Net profit retained by shop = (Commission - Cost)
    val netCustomerAmountMinor: Long,   // Total collected from or paid to customer
    val appliedRuleDescriptionArabic: String
)

object CommissionEngine {

    private val defaultRules: MutableList<CommissionTierRule> = mutableListOf(
        // Vodafone Cash - Deposit (0 to 500: 5 EGP, 500 to 1000: 10 EGP, 1000+: 1.5%)
        CommissionTierRule(
            ruleId = "VF_DEP_LOW",
            providerCode = "VODAFONE_CASH",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 0L,
            maxAmountMinor = 500_00L,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 0,
            providerCostFixedMinor = 1_00L,
            descriptionArabic = "فودافون كاش - إيداع حتى 500 ج.م (عمولة ثابتة 5 ج.م)"
        ),
        CommissionTierRule(
            ruleId = "VF_DEP_MID",
            providerCode = "VODAFONE_CASH",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 500_01L,
            maxAmountMinor = 1_000_00L,
            customerFeeFixedMinor = 10_00L,
            customerFeePercentageBps = 0,
            providerCostFixedMinor = 2_00L,
            descriptionArabic = "فودافون كاش - إيداع 500 إلى 1000 ج.م (عمولة ثابتة 10 ج.م)"
        ),
        CommissionTierRule(
            ruleId = "VF_DEP_HIGH",
            providerCode = "VODAFONE_CASH",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 1_000_01L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 0L,
            customerFeePercentageBps = 150, // 1.5%
            providerCostPercentageBps = 50, // 0.5%
            descriptionArabic = "فودافون كاش - إيداع فوق 1000 ج.م (عمولة 1.5%)"
        ),

        // Vodafone Cash - Withdraw (1% standard, min 3 EGP)
        CommissionTierRule(
            ruleId = "VF_WTH_STD",
            providerCode = "VODAFONE_CASH",
            operationType = WalletDomainOperationType.WALLET_WITHDRAW,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 3_00L,
            customerFeePercentageBps = 100, // 1%
            providerCostPercentageBps = 50, // 0.5%
            descriptionArabic = "فودافون كاش - سحب (1% + 3 ج.م رسوم خدمة)"
        ),

        // Orange Cash
        CommissionTierRule(
            ruleId = "ORANGE_DEP_STD",
            providerCode = "ORANGE_CASH",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 100, // 1%
            providerCostPercentageBps = 40,
            descriptionArabic = "أورنج كاش - إيداع (1% + 5 ج.م)"
        ),
        CommissionTierRule(
            ruleId = "ORANGE_WTH_STD",
            providerCode = "ORANGE_CASH",
            operationType = WalletDomainOperationType.WALLET_WITHDRAW,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 3_00L,
            customerFeePercentageBps = 100,
            providerCostPercentageBps = 40,
            descriptionArabic = "أورنج كاش - سحب (1% + 3 ج.م)"
        ),

        // e& money (Etisalat Cash)
        CommissionTierRule(
            ruleId = "ETISALAT_DEP_STD",
            providerCode = "ETISALAT_CASH",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 100,
            providerCostPercentageBps = 50,
            descriptionArabic = "e& money - إيداع (1% + 5 ج.م)"
        ),
        CommissionTierRule(
            ruleId = "ETISALAT_WTH_STD",
            providerCode = "ETISALAT_CASH",
            operationType = WalletDomainOperationType.WALLET_WITHDRAW,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 3_00L,
            customerFeePercentageBps = 100,
            providerCostPercentageBps = 50,
            descriptionArabic = "e& money - سحب (1% + 3 ج.م)"
        ),

        // InstaPay (Nominal service fee)
        CommissionTierRule(
            ruleId = "INSTAPAY_STD",
            providerCode = "INSTAPAY",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 50, // 0.5%
            providerCostFixedMinor = 0L,
            descriptionArabic = "إنستا باي - تحويل لحظي (0.5% + 5 ج.م)"
        ),
        CommissionTierRule(
            ruleId = "INSTAPAY_WTH",
            providerCode = "INSTAPAY",
            operationType = WalletDomainOperationType.WALLET_WITHDRAW,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 50,
            providerCostFixedMinor = 0L,
            descriptionArabic = "إنستا باي - استلام نقدية (0.5% + 5 ج.م)"
        ),

        // Generic / Fallback Rule
        CommissionTierRule(
            ruleId = "GENERIC_FALLBACK",
            providerCode = "ALL",
            operationType = WalletDomainOperationType.WALLET_DEPOSIT,
            minAmountMinor = 0L,
            maxAmountMinor = Long.MAX_VALUE,
            customerFeeFixedMinor = 5_00L,
            customerFeePercentageBps = 100, // 1%
            providerCostFixedMinor = 1_00L,
            descriptionArabic = "التعريفة القياسية للخدمات المالية (1% + 5 ج.م)"
        )
    )

    fun validateRule(rule: CommissionTierRule): AppResult<Unit> {
        if (rule.ruleId.isBlank()) {
            return AppResult.Error("معرف قاعدة العمولة لا يمكن أن يكون فارغاً", "INVALID_RULE_ID")
        }
        if (rule.minAmountMinor < 0L || rule.maxAmountMinor < rule.minAmountMinor) {
            return AppResult.Error("نطاق المبلغ غير صالح للقاعدة (${rule.ruleId})", "INVALID_RULE_AMOUNT_RANGE")
        }
        if (rule.customerFeePercentageBps < 0 || rule.customerFeeFixedMinor < 0L) {
            return AppResult.Error("عمولة العميل لا يمكن أن تكون سالبة (${rule.ruleId})", "INVALID_COMMISSION_FEE")
        }
        if (rule.providerCostPercentageBps < 0 || rule.providerCostFixedMinor < 0L) {
            return AppResult.Error("تكلفة مزود الخدمة لا يمكن أن تكون سالبة (${rule.ruleId})", "INVALID_PROVIDER_COST")
        }
        return AppResult.Success(Unit)
    }

    fun registerRule(rule: CommissionTierRule): AppResult<Unit> {
        val validation = validateRule(rule)
        if (validation is AppResult.Error) return validation
        defaultRules.removeAll { it.ruleId == rule.ruleId }
        defaultRules.add(0, rule)
        return AppResult.Success(Unit)
    }

    fun getAllRules(): List<CommissionTierRule> = defaultRules.toList()

    fun calculateProfitPreview(
        providerCode: String,
        operationType: WalletOperationType,
        principalAmountMinor: Long,
        customCommissionMinorOverride: Long? = null,
        customCostFeeMinorOverride: Long? = null
    ): CommissionProfitPreview = calculatePreview(
        principalAmountMinor = principalAmountMinor,
        providerCode = providerCode,
        operationType = operationType,
        customCommissionMinorOverride = customCommissionMinorOverride,
        customCostFeeMinorOverride = customCostFeeMinorOverride
    )

    /**
     * Calculates the exact commission, network cost, expected profit, and net amounts
     * based on active business rules for the provider, operation type, and amount tier.
     */
    fun calculatePreview(
        principalAmountMinor: Long,
        providerCode: String,
        operationType: WalletDomainOperationType,
        customCommissionMinorOverride: Long? = null,
        customCostFeeMinorOverride: Long? = null
    ): CommissionProfitPreview {
        if (principalAmountMinor <= 0L) {
            return CommissionProfitPreview(
                principalAmountMinor = 0L,
                customerCommissionMinor = 0L,
                providerCostFeeMinor = 0L,
                expectedShopProfitMinor = 0L,
                netCustomerAmountMinor = 0L,
                appliedRuleDescriptionArabic = "يرجى إدخال مبلغ صالح"
            )
        }

        if (customCommissionMinorOverride != null && customCommissionMinorOverride < 0L) {
            return CommissionProfitPreview(
                principalAmountMinor = principalAmountMinor,
                customerCommissionMinor = 0L,
                providerCostFeeMinor = 0L,
                expectedShopProfitMinor = 0L,
                netCustomerAmountMinor = principalAmountMinor,
                appliedRuleDescriptionArabic = "قيمة العمولة المحددة غير صالحة"
            )
        }
        if (customCostFeeMinorOverride != null && customCostFeeMinorOverride < 0L) {
            return CommissionProfitPreview(
                principalAmountMinor = principalAmountMinor,
                customerCommissionMinor = 0L,
                providerCostFeeMinor = 0L,
                expectedShopProfitMinor = 0L,
                netCustomerAmountMinor = principalAmountMinor,
                appliedRuleDescriptionArabic = "تكلفة الخدمة المحددة غير صالحة"
            )
        }

        val normProvider = providerCode.uppercase().trim()
        val matchingRule = defaultRules.firstOrNull { rule ->
            (rule.providerCode == normProvider || rule.providerCode == "ALL") &&
                (rule.operationType == operationType) &&
                principalAmountMinor >= rule.minAmountMinor &&
                principalAmountMinor <= rule.maxAmountMinor
        } ?: defaultRules.last()

        val calculatedCustomerCommissionMinor = customCommissionMinorOverride ?: run {
            val percentagePart = (principalAmountMinor * matchingRule.customerFeePercentageBps) / 10_000L
            percentagePart + matchingRule.customerFeeFixedMinor
        }

        val calculatedProviderCostMinor = customCostFeeMinorOverride ?: run {
            val percentagePart = (principalAmountMinor * matchingRule.providerCostPercentageBps) / 10_000L
            percentagePart + matchingRule.providerCostFixedMinor
        }

        val expectedProfitMinor = (calculatedCustomerCommissionMinor - calculatedProviderCostMinor).coerceAtLeast(0L)

        // Customer Total:
        // For CUSTOMER_DEPOSIT: Customer gives principal + commission
        // For CUSTOMER_WITHDRAWAL: Customer receives principal - commission (or pays commission separately)
        val netCustomerAmountMinor = when (operationType) {
            WalletOperationType.CUSTOMER_DEPOSIT -> principalAmountMinor + calculatedCustomerCommissionMinor
            WalletOperationType.CUSTOMER_WITHDRAWAL -> (principalAmountMinor - calculatedCustomerCommissionMinor).coerceAtLeast(0L)
            else -> principalAmountMinor + calculatedCustomerCommissionMinor
        }

        return CommissionProfitPreview(
            principalAmountMinor = principalAmountMinor,
            customerCommissionMinor = calculatedCustomerCommissionMinor,
            providerCostFeeMinor = calculatedProviderCostMinor,
            expectedShopProfitMinor = expectedProfitMinor,
            netCustomerAmountMinor = netCustomerAmountMinor,
            appliedRuleDescriptionArabic = matchingRule.descriptionArabic
        )
    }
}

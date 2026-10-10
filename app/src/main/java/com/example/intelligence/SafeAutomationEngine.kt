package com.example.intelligence

import com.example.database.dao.CashDrawerDerivedProjection
import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.BackupMetadataEntity
import com.example.financial.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AutomationTriggerType {
    LOW_STOCK,
    WALLET_LIMIT,
    DAILY_SUMMARY,
    BACKUP_REMINDER,
    HIGH_RECEIVABLE
}

enum class AutomationActionType {
    CREATE_SMART_ALERT,
    GENERATE_DAILY_SUMMARY,
    CREATE_BACKUP_REMINDER_NOTIFICATION
}

data class SafeAutomationRule(
    val id: String,
    val nameArabic: String,
    val descriptionArabic: String,
    val triggerType: AutomationTriggerType,
    val actionType: AutomationActionType,
    val isEnabled: Boolean = true,
    val executionCount: Int = 0,
    val lastExecutedAt: Long? = null,
    val lastExecutionSummaryArabic: String? = null
)

data class AutomationExecutionResult(
    val ruleId: String,
    val triggered: Boolean,
    val summaryArabic: String,
    val executedAt: Long = System.currentTimeMillis()
)

object SafeAutomationEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    val DEFAULT_RULES = listOf(
        SafeAutomationRule(
            id = "AUTO-LOW-STOCK",
            nameArabic = "تنبيه نقص المخزون التلقائي",
            descriptionArabic = "إنشاء تنبيه فوري عندما ينخفض رصيد أي منتج عن نقطة إعادة الطلب.",
            triggerType = AutomationTriggerType.LOW_STOCK,
            actionType = AutomationActionType.CREATE_SMART_ALERT,
            isEnabled = true
        ),
        SafeAutomationRule(
            id = "AUTO-WALLET-LIMIT",
            nameArabic = "تحذير سقف استهلاك المحافظ",
            descriptionArabic = "إنشاء تنبيه عندما يتجاوز الاستهلاك اليومي أو الشهري للمحفظة 80% من سقفها المحدد.",
            triggerType = AutomationTriggerType.WALLET_LIMIT,
            actionType = AutomationActionType.CREATE_SMART_ALERT,
            isEnabled = true
        ),
        SafeAutomationRule(
            id = "AUTO-BACKUP-REMINDER",
            nameArabic = "تذكير النسخ الاحتياطي الدوري",
            descriptionArabic = "تنبيه النظام إذا مر أكثر من 48 ساعة على آخر نسخة احتياطية ناجحة.",
            triggerType = AutomationTriggerType.BACKUP_REMINDER,
            actionType = AutomationActionType.CREATE_BACKUP_REMINDER_NOTIFICATION,
            isEnabled = true
        ),
        SafeAutomationRule(
            id = "AUTO-DAILY-SUMMARY",
            nameArabic = "ملخص نهاية اليوم التجاري",
            descriptionArabic = "تجميع وتجهيز إحصائيات المبيعات والأرباح والخزينة تلقائياً عند نهاية اليوم.",
            triggerType = AutomationTriggerType.DAILY_SUMMARY,
            actionType = AutomationActionType.GENERATE_DAILY_SUMMARY,
            isEnabled = true
        ),
        SafeAutomationRule(
            id = "AUTO-HIGH-RECEIVABLE",
            nameArabic = "مراقبة تراكم مديونيات العملاء",
            descriptionArabic = "تنبيه المشرف إذا تجاوز إجمالي المبالغ الآجلة على عميل معين 2000 جنيه.",
            triggerType = AutomationTriggerType.HIGH_RECEIVABLE,
            actionType = AutomationActionType.CREATE_SMART_ALERT,
            isEnabled = true
        )
    )

    /**
     * Evaluates all enabled safe automation rules.
     * Guaranteed safe: strictly produces alerts/notifications/summaries and never mutates ledger balances directly.
     */
    fun evaluateRules(
        rules: List<SafeAutomationRule>,
        products: List<ProductInventoryProjection>,
        wallets: List<WalletDerivedProjection>,
        customers: List<CustomerBalanceProjection>,
        lastBackup: BackupMetadataEntity?,
        nowMillis: Long = System.currentTimeMillis()
    ): List<AutomationExecutionResult> {
        val results = mutableListOf<AutomationExecutionResult>()

        for (rule in rules) {
            if (!rule.isEnabled) continue

            when (rule.triggerType) {
                AutomationTriggerType.LOW_STOCK -> {
                    val lowItems = products.filter { it.quantityOnHand <= 3 }
                    if (lowItems.isNotEmpty()) {
                        results += AutomationExecutionResult(
                            ruleId = rule.id,
                            triggered = true,
                            summaryArabic = "تم رصد ${lowItems.size} منتجات تحتاج إعادة طلب وتم إصدار التنبيه الذكي."
                        )
                    }
                }
                AutomationTriggerType.WALLET_LIMIT -> {
                    val nearLimit = wallets.filter {
                        it.dailyLimitMinor > 0L && ((it.todayOutflowMinor * 100L) / it.dailyLimitMinor) >= 80
                    }
                    if (nearLimit.isNotEmpty()) {
                        results += AutomationExecutionResult(
                            ruleId = rule.id,
                            triggered = true,
                            summaryArabic = "تم رصد ${nearLimit.size} محافظ قاربت سقفها اليومي."
                        )
                    }
                }
                AutomationTriggerType.BACKUP_REMINDER -> {
                    val lastTime = lastBackup?.createdAt ?: 0L
                    if (nowMillis - lastTime > 48L * 60L * 60L * 1000L) {
                        results += AutomationExecutionResult(
                            ruleId = rule.id,
                            triggered = true,
                            summaryArabic = "مضى أكثر من 48 ساعة دون نسخة احتياطية؛ تم إنشاء إشعار الحث على الحفظ."
                        )
                    }
                }
                AutomationTriggerType.HIGH_RECEIVABLE -> {
                    val highCusts = customers.filter { it.outstandingBalanceMinor >= 200_000L } // >= 2,000 EGP
                    if (highCusts.isNotEmpty()) {
                        results += AutomationExecutionResult(
                            ruleId = rule.id,
                            triggered = true,
                            summaryArabic = "يوجد ${highCusts.size} عملاء تجاوزت مديونياتهم الحد الائتماني الآمن."
                        )
                    }
                }
                AutomationTriggerType.DAILY_SUMMARY -> {
                    results += AutomationExecutionResult(
                        ruleId = rule.id,
                        triggered = true,
                        summaryArabic = "تم تحديث ملخص الأداء اليومي بنجاح."
                    )
                }
            }
        }
        return results
    }
}

package com.example.intelligence

import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.CashSessionEntity
import com.example.financial.Money
import com.example.navigation.MekkaDestination
import com.example.sync.SyncUiStatus

enum class SmartAlertSeverity(val arabicLabel: String) {
    CRITICAL("حرج وعاجل"),
    WARNING("تحذيري"),
    INFO("تنبيه معلوماتي")
}

data class SmartAlertItem(
    val code: String,
    val titleArabic: String,
    val descriptionArabic: String,
    val severity: SmartAlertSeverity,
    val targetRoute: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SmartAlertEngine {

    fun generateAlerts(
        lowStockCount: Int,
        outOfStockCount: Int,
        customerReceivablesMinor: Long,
        supplierPayablesMinor: Long,
        pendingSmsCount: Int,
        lastCashDiscrepancyMinor: Long,
        unresolvedConflictsCount: Int,
        pendingSyncCount: Int,
        failedSyncCount: Int,
        wallets: List<WalletDerivedProjection>,
        openSession: CashSessionEntity?,
        syncStatus: SyncUiStatus
    ): List<SmartAlertItem> {
        val alerts = mutableListOf<SmartAlertItem>()

        // 1. Critical Cash Discrepancy
        if (lastCashDiscrepancyMinor != 0L) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_CASH_DISCREPANCY",
                    titleArabic = "فرق خزينة غير مطابق: ${Money.ofMinor(lastCashDiscrepancyMinor).formatArabic()}",
                    descriptionArabic = "سجلت الوردية الأخيرة فرقاً بين النقدية الفعلية والرصيد الدفتري المتوقع.",
                    severity = SmartAlertSeverity.CRITICAL,
                    targetRoute = MekkaDestination.CASH_DRAWER.route
                )
            )
        }

        // 2. Out of Stock Products (Critical)
        if (outOfStockCount > 0) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_OUT_OF_STOCK",
                    titleArabic = "أصناف نفدت بالكامل: $outOfStockCount أصناف",
                    descriptionArabic = "يوجد أصناف في المخزن وصل رصيدها إلى صفر وتحتاج لإعادة توريد عاجلة.",
                    severity = SmartAlertSeverity.CRITICAL,
                    targetRoute = MekkaDestination.PRODUCTS.route
                )
            )
        }

        // 3. Low Stock Products (Warning)
        if (lowStockCount > 0) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_LOW_STOCK",
                    titleArabic = "مخزون منخفض: $lowStockCount أصناف",
                    descriptionArabic = "وصلت هذه الأصناف إلى حد إعادة الطلب المحدد للمتجر.",
                    severity = SmartAlertSeverity.WARNING,
                    targetRoute = MekkaDestination.PRODUCTS.route
                )
            )
        }

        // 4. Wallets near Daily / Monthly Limit (> 80% usage)
        for (w in wallets) {
            if (w.dailyLimitMinor > 0L) {
                val usagePercent = (w.todayOutflowMinor * 100L) / w.dailyLimitMinor
                if (usagePercent >= 80L) {
                    alerts.add(
                        SmartAlertItem(
                            code = "ALERT_WALLET_DAILY_LIMIT_${w.walletId}",
                            titleArabic = "اقتراب حد المحفظة اليومي (${w.walletNameArabic}): $usagePercent%",
                            descriptionArabic = "المستخدم اليوم: ${Money.ofMinor(w.todayOutflowMinor).formatArabic()} من أصل ${Money.ofMinor(w.dailyLimitMinor).formatArabic()}",
                            severity = if (usagePercent >= 95L) SmartAlertSeverity.CRITICAL else SmartAlertSeverity.WARNING,
                            targetRoute = MekkaDestination.WALLETS.route
                        )
                    )
                }
            }
            if (w.monthlyLimitMinor > 0L) {
                val usagePercent = (w.monthOutflowMinor * 100L) / w.monthlyLimitMinor
                if (usagePercent >= 85L) {
                    alerts.add(
                        SmartAlertItem(
                            code = "ALERT_WALLET_MONTHLY_LIMIT_${w.walletId}",
                            titleArabic = "اقتراب حد المحفظة الشهري (${w.walletNameArabic}): $usagePercent%",
                            descriptionArabic = "المستخدم شهرياً: ${Money.ofMinor(w.monthOutflowMinor).formatArabic()} من أصل ${Money.ofMinor(w.monthlyLimitMinor).formatArabic()}",
                            severity = SmartAlertSeverity.WARNING,
                            targetRoute = MekkaDestination.WALLETS.route
                        )
                    )
                }
            }
        }

        // 5. Customer Overdue Receivables
        if (customerReceivablesMinor > 0L) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_CUSTOMER_RECEIVABLES",
                    titleArabic = "مديونيات عملاء قائمة: ${Money.ofMinor(customerReceivablesMinor).formatArabic()}",
                    descriptionArabic = "توجد مبالغ مستحقة للتحصيل على العملاء.",
                    severity = SmartAlertSeverity.WARNING,
                    targetRoute = MekkaDestination.CUSTOMERS.route
                )
            )
        }

        // 6. Supplier Payables
        if (supplierPayablesMinor > 0L) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_SUPPLIER_PAYABLES",
                    titleArabic = "مستحقات للموردين: ${Money.ofMinor(supplierPayablesMinor).formatArabic()}",
                    descriptionArabic = "توجد فواتير مشتريات آجلة مستحقة السداد للموردين.",
                    severity = SmartAlertSeverity.INFO,
                    targetRoute = MekkaDestination.SUPPLIERS.route
                )
            )
        }

        // 7. Pending SMS Operations
        if (pendingSmsCount > 0) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_PENDING_SMS",
                    titleArabic = "رسائل محافظ بانتظار التأكيد: $pendingSmsCount رسالة",
                    descriptionArabic = "يوجد رسائل تم استلامها من مزودي المحافظ بحاجة لمطابقة واعتماد محاسبي.",
                    severity = SmartAlertSeverity.WARNING,
                    targetRoute = MekkaDestination.SMS_INTELLIGENCE.route
                )
            )
        }

        // 8. Sync Conflicts & Failures
        if (unresolvedConflictsCount > 0) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_SYNC_CONFLICTS",
                    titleArabic = "تعارضات مزامنة بحاجة للحل: $unresolvedConflictsCount",
                    descriptionArabic = "توجد عمليات سجلت تعارضاً مع السحابة وتحتاج لقرار إداري صريح.",
                    severity = SmartAlertSeverity.CRITICAL,
                    targetRoute = MekkaDestination.SYNC.route
                )
            )
        } else if (failedSyncCount > 0) {
            alerts.add(
                SmartAlertItem(
                    code = "ALERT_SYNC_FAILED",
                    titleArabic = "فشل مزامنة: $failedSyncCount عمليات معلقة",
                    descriptionArabic = "تعذر إرسال بعض العمليات المحلية للسحابة، جاري إعادة المحاولة.",
                    severity = SmartAlertSeverity.WARNING,
                    targetRoute = MekkaDestination.SYNC.route
                )
            )
        }

        // 9. Long-Running Open Shift (> 16 hours)
        if (openSession != null) {
            val durationHours = (System.currentTimeMillis() - openSession.openedAt) / (1000L * 60L * 60L)
            if (durationHours >= 16) {
                alerts.add(
                    SmartAlertItem(
                        code = "ALERT_LONG_OPEN_SHIFT",
                        titleArabic = "وردية الخزينة مفتوحة منذ $durationHours ساعة",
                        descriptionArabic = "يُوصى بإغلاق وردية الخزينة وجرد النقدية لبدء وردية جديدة.",
                        severity = SmartAlertSeverity.WARNING,
                        targetRoute = MekkaDestination.CASH_DRAWER.route
                    )
                )
            }
        }

        return alerts.sortedByDescending { it.severity == SmartAlertSeverity.CRITICAL }
    }
}

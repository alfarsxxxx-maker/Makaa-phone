package com.example.intelligence

import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.financial.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class SmartBusinessInsightItem(
    val id: String,
    val domain: String, // SALES, INVENTORY, EXPENSE, WALLET, PROFIT
    val titleArabic: String,
    val descriptionArabic: String,
    val metricFormatted: String,
    val isPositive: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val targetRoute: String
)

data class AnomalyDetectionAlert(
    val id: String,
    val anomalyType: String, // UNUSUAL_EXPENSE, UNUSUAL_DISCOUNT, UNUSUAL_REFUND, CASH_VARIANCE
    val titleArabic: String,
    val descriptionArabic: String,
    val detectedValueFormatted: String,
    val baselineFormatted: String,
    val timestamp: Long = System.currentTimeMillis(),
    val targetRoute: String
)

object SmartBusinessInsightsEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun generateInsights(
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        products: List<ProductInventoryProjection>,
        expenses: List<ExpenseEntity>,
        wallets: List<WalletDerivedProjection>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<SmartBusinessInsightItem> {
        val insights = mutableListOf<SmartBusinessInsightItem>()

        val sevenDaysAgo = nowMillis - 7L * DAY_MILLIS
        val fourteenDaysAgo = nowMillis - 14L * DAY_MILLIS
        val thirtyDaysAgo = nowMillis - 30L * DAY_MILLIS

        val validSales = sales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }

        // 1. Sales Growth Insight (Last 7 days vs previous 7 days)
        val current7DaysSales = validSales.filter { it.saleTimestamp >= sevenDaysAgo }
        val prev7DaysSales = validSales.filter { it.saleTimestamp in fourteenDaysAgo until sevenDaysAgo }
        val current7Rev = current7DaysSales.sumOf { it.totalAmountMinor }
        val prev7Rev = prev7DaysSales.sumOf { it.totalAmountMinor }

        if (prev7Rev > 0L) {
            val changePercent = (((current7Rev - prev7Rev).toDouble() / prev7Rev.toDouble()) * 100.0).roundToInt()
            val isUp = changePercent >= 0
            insights += SmartBusinessInsightItem(
                id = "INSIGHT-SALES-TREND",
                domain = "SALES",
                titleArabic = if (isUp) "نمو في المبيعات الأسبوعية" else "تراجع في وتيرة المبيعات",
                descriptionArabic = if (isUp) {
                    "المبيعات ارتفعت $changePercent% مقارنة بالأسبوع السابق (${Money.ofMinor(current7Rev).formatArabic()} مقابل ${Money.ofMinor(prev7Rev).formatArabic()})."
                } else {
                    "المبيعات انخفضت ${abs(changePercent)}% مقارنة بالأسبوع السابق (${Money.ofMinor(current7Rev).formatArabic()} مقابل ${Money.ofMinor(prev7Rev).formatArabic()})."
                },
                metricFormatted = "${if (isUp) "+" else ""}$changePercent%",
                isPositive = isUp,
                targetRoute = "reports"
            )
        }

        // 2. Top-Selling Inventory Item (Last 7 days)
        val current7SaleIds = current7DaysSales.map { it.localId }.toSet()
        val recentSaleItems = saleItems.filter { it.saleId in current7SaleIds }
        val unitsSoldByProd = recentSaleItems.groupBy { it.productId }
            .mapValues { (_, items) -> items.sumOf { it.quantity - it.returnedQuantity } }
        val topProdEntry = unitsSoldByProd.maxByOrNull { it.value }

        if (topProdEntry != null && topProdEntry.value > 0) {
            val prod = products.firstOrNull { it.productId == topProdEntry.key }
            if (prod != null) {
                insights += SmartBusinessInsightItem(
                    id = "INSIGHT-TOP-PRODUCT",
                    domain = "INVENTORY",
                    titleArabic = "المنتج الأعلى طلباً هذا الأسبوع",
                    descriptionArabic = "الصنف «${prod.nameArabic}» حقق أعلى مبيعات بواقع ${topProdEntry.value} وحدة خلال الـ 7 أيام الماضية.",
                    metricFormatted = "${topProdEntry.value} وحدة",
                    isPositive = true,
                    targetRoute = "products"
                )
            }
        }

        // 3. Expense vs 30-Day Average
        val last30DaysExpenses = expenses.filter { it.expenseTimestamp >= thirtyDaysAgo }
        val todayStart = nowMillis - (nowMillis % DAY_MILLIS)
        val todayExpenses = expenses.filter { it.expenseTimestamp >= todayStart }.sumOf { it.amountMinor }
        val avgDailyExpense = if (last30DaysExpenses.isNotEmpty()) {
            last30DaysExpenses.sumOf { it.amountMinor } / 30L
        } else 0L

        if (todayExpenses > 0L && avgDailyExpense > 0L && todayExpenses > avgDailyExpense * 2L) {
            insights += SmartBusinessInsightItem(
                id = "INSIGHT-EXPENSE-SPIKE",
                domain = "EXPENSE",
                titleArabic = "مصروفات اليوم أعلى من المعتاد",
                descriptionArabic = "بلغت مصروفات اليوم ${Money.ofMinor(todayExpenses).formatArabic()} متجاوزة متوسط المصروف اليومي (${Money.ofMinor(avgDailyExpense).formatArabic()}).",
                metricFormatted = Money.ofMinor(todayExpenses).formatArabic(),
                isPositive = false,
                targetRoute = "expenses"
            )
        }

        // 4. Wallet Near Limit (> 80% usage)
        for (w in wallets) {
            if (w.dailyLimitMinor > 0L) {
                val usagePercent = ((w.todayOutflowMinor * 100L) / w.dailyLimitMinor).toInt()
                if (usagePercent >= 80) {
                    insights += SmartBusinessInsightItem(
                        id = "INSIGHT-WALLET-LIMIT-${w.walletId}",
                        domain = "WALLET",
                        titleArabic = "اقتراب الحد اليومي للمحفظة",
                        descriptionArabic = "محفظة «${w.walletNameArabic}» استهلكت $usagePercent% من حدها اليومي (${Money.ofMinor(w.todayOutflowMinor).formatArabic()} من ${Money.ofMinor(w.dailyLimitMinor).formatArabic()}).",
                        metricFormatted = "$usagePercent%",
                        isPositive = false,
                        targetRoute = "wallets"
                    )
                    break // Report top critical one
                }
            }
        }

        // 5. Overall Gross Profit Margin
        val totalRev = current7DaysSales.sumOf { it.totalAmountMinor }
        val totalCogs = recentSaleItems.sumOf { it.unitCostAtSaleMinor * (it.quantity - it.returnedQuantity) }
        val grossProfit = totalRev - totalCogs
        if (totalRev > 0L) {
            val marginPercent = ((grossProfit.toDouble() / totalRev.toDouble()) * 100.0).roundToInt()
            insights += SmartBusinessInsightItem(
                id = "INSIGHT-MARGIN-HEALTH",
                domain = "PROFIT",
                titleArabic = "هامش الربح الإجمالي للأسبوع",
                descriptionArabic = "حقق المحل هامش ربح إجمالي بمعدل $marginPercent% على مبيعات الأسبوع (${Money.ofMinor(grossProfit).formatArabic()}).",
                metricFormatted = "$marginPercent%",
                isPositive = marginPercent >= 15,
                targetRoute = "reports"
            )
        }

        return insights.take(5)
    }

    fun detectAnomalies(
        expenses: List<ExpenseEntity>,
        sales: List<SaleEntity>,
        refunds: List<RefundEntity>,
        cashSessions: List<CashSessionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<AnomalyDetectionAlert> {
        val anomalies = mutableListOf<AnomalyDetectionAlert>()
        val thirtyDaysAgo = nowMillis - 30L * DAY_MILLIS

        // 1. Unusual Expense (> 2.5x 30-day average and > 500 EGP)
        val validExpenses = expenses.filter { it.expenseTimestamp >= thirtyDaysAgo }
        if (validExpenses.size >= 5) {
            val avgExpense = validExpenses.map { it.amountMinor }.average().toLong()
            val recentExpenses = expenses.filter { it.expenseTimestamp >= nowMillis - DAY_MILLIS }
            for (exp in recentExpenses) {
                if (exp.amountMinor > avgExpense * 25L / 10L && exp.amountMinor >= 50_000L) { // 500 EGP
                    anomalies += AnomalyDetectionAlert(
                        id = "ANOMALY-EXP-${exp.localId}",
                        anomalyType = "UNUSUAL_EXPENSE",
                        titleArabic = "مصروف تشغيلي غير معتاد",
                        descriptionArabic = "عملية غير معتادة مقارنة بالنمط السابق: تم تسجيل مصروف «${exp.title}» بقيمة غير معتادة.",
                        detectedValueFormatted = Money.ofMinor(exp.amountMinor).formatArabic(),
                        baselineFormatted = "متوسط المصروف: ${Money.ofMinor(avgExpense).formatArabic()}",
                        timestamp = exp.expenseTimestamp,
                        targetRoute = "expenses"
                    )
                }
            }
        }

        // 2. Unusual High Discount (> 25% of sale or > 200 EGP discount)
        val recentSales = sales.filter { it.saleTimestamp >= nowMillis - DAY_MILLIS && it.discountMinor > 0L }
        for (sale in recentSales) {
            val totalBeforeDiscount = sale.totalAmountMinor + sale.discountMinor
            val discountPercent = if (totalBeforeDiscount > 0L) ((sale.discountMinor * 100L) / totalBeforeDiscount).toInt() else 0
            if (discountPercent >= 25 || sale.discountMinor >= 20_000L) { // > 25% or > 200 EGP
                anomalies += AnomalyDetectionAlert(
                    id = "ANOMALY-DISCOUNT-${sale.localId}",
                    anomalyType = "UNUSUAL_DISCOUNT",
                    titleArabic = "خصم مبيعات استثنائي",
                    descriptionArabic = "عملية غير معتادة مقارنة بالنمط السابق: تم تطبيق خصم $discountPercent% على الفاتورة #${sale.invoiceNumber}.",
                    detectedValueFormatted = Money.ofMinor(sale.discountMinor).formatArabic(),
                    baselineFormatted = "الفاتورة: ${Money.ofMinor(sale.totalAmountMinor).formatArabic()}",
                    timestamp = sale.saleTimestamp,
                    targetRoute = "sales"
                )
            }
        }

        // 3. Unusual Cash Variance
        val openOrRecentSessions = cashSessions.filter { (it.discrepancyMinor ?: 0L) != 0L }
        for (session in openOrRecentSessions.take(2)) {
            val discrepancy = session.discrepancyMinor ?: 0L
            val isShortage = discrepancy < 0L
            anomalies += AnomalyDetectionAlert(
                id = "ANOMALY-CASH-VAR-${session.localId}",
                anomalyType = "CASH_VARIANCE",
                titleArabic = if (isShortage) "عجز نقدي في الوردية" else "زيادة نقدية في الوردية",
                descriptionArabic = "عملية غير معتادة مقارنة بالنمط السابق: تم تسجيل فرق نقدية في الوردية (${if (isShortage) "عجز" else "زيادة"}: ${Money.ofMinor(abs(discrepancy)).formatArabic()}).",
                detectedValueFormatted = Money.ofMinor(discrepancy).formatArabic(),
                baselineFormatted = "المتوقع: ${Money.ofMinor(session.openingDerivedBalanceMinor).formatArabic()}",
                timestamp = session.closedAt ?: session.openedAt,
                targetRoute = "cash_drawer"
            )
        }

        // 4. Unusual Refund (> 1000 EGP)
        val recentRefunds = refunds.filter { it.refundTimestamp >= nowMillis - DAY_MILLIS && it.refundAmountMinor >= 100_000L }
        for (ref in recentRefunds) {
            anomalies += AnomalyDetectionAlert(
                id = "ANOMALY-REFUND-${ref.localId}",
                anomalyType = "UNUSUAL_REFUND",
                titleArabic = "مرتجع مالي ذو قيمة مرتفعة",
                descriptionArabic = "عملية غير معتادة مقارنة بالنمط السابق: استرداد مبلغ كبير على المرتجع #${ref.refundNumber}.",
                detectedValueFormatted = Money.ofMinor(ref.refundAmountMinor).formatArabic(),
                baselineFormatted = "السبب: ${ref.reason}",
                timestamp = ref.refundTimestamp,
                targetRoute = "sales"
            )
        }

        return anomalies
    }
}

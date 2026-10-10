package com.example.intelligence

import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.TransferEntity
import com.example.financial.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Metric item for comparing business periods (Requirement P2-B & Reports Tab 7).
 */
data class PeriodComparisonMetric(
    val labelArabic: String,
    val isPositiveTrend: Boolean,
    val percentDiff: Int?,
    val absoluteDiff: Long
)

/**
 * Group of metrics comparing two adjacent time frames.
 */
data class PeriodComparisonGroup(
    val periodLabelArabic: String,
    val hasSufficientData: Boolean,
    val summaryArabic: String,
    val sales: PeriodComparisonMetric,
    val grossProfit: PeriodComparisonMetric,
    val expenses: PeriodComparisonMetric,
    val transactionCount: PeriodComparisonMetric
)

/**
 * Complete period comparisons bundle (Today vs Yesterday, This Week vs Last Week, This Month vs Last Month).
 */
data class PeriodComparisonsBundle(
    val todayVsYesterday: PeriodComparisonGroup,
    val thisWeekVsLastWeek: PeriodComparisonGroup,
    val thisMonthVsLastMonth: PeriodComparisonGroup
)

/**
 * Individual assessment pillar in the Business Health Scorecard (Requirement P2-O & Reports Tab 8).
 */
data class ScorecardPillarItem(
    val key: String,
    val nameArabic: String,
    val statusArabic: String,
    val score: Int,
    val colorCode: String, // "GREEN", "AMBER", "RED"
    val explanationArabic: String,
    val keyMetric: String
)

/**
 * Comprehensive Business Health Scorecard.
 */
data class BusinessScorecardBundle(
    val overallScore: Int,
    val overallRatingArabic: String,
    val pillars: List<ScorecardPillarItem>
)

/**
 * Conservative forward-looking financial forecast bundle (Requirement P2-B & Reports Tab 9).
 */
data class BusinessForecastingBundle(
    val estimatedWeeklySalesMinor: Long,
    val estimatedWeeklyExpensesMinor: Long,
    val estimatedWeeklyNetCashRequirementMinor: Long,
    val estimatedWeeklyWalletVolumeMinor: Long,
    val salesForecastConfidenceArabic: String,
    val sourcePeriodArabic: String,
    val calculationMethodArabic: String
)

/**
 * Daily business snapshot bundle (Requirement P2-M & Reports Tab 10).
 */
data class DailyBusinessSummaryBundle(
    val dateFormattedArabic: String,
    val salesMinor: Long,
    val grossProfitMinor: Long,
    val cashOnHandMinor: Long,
    val walletBalancesTotalMinor: Long,
    val expensesMinor: Long,
    val transactionsCount: Int,
    val activeAlertsCount: Int
)

object P2ForecastingEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun computePeriodComparisons(
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        expenses: List<ExpenseEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): PeriodComparisonsBundle {
        val validSales = sales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }
        val saleCostMap = saleItems.groupBy { it.saleId }
            .mapValues { (_, items) -> items.sumOf { (it.unitCostAtSaleMinor * (it.quantity - it.returnedQuantity)).coerceAtLeast(0L) } }

        fun aggregateForRange(from: Long, to: Long): Quadruple<Long, Long, Long, Int> {
            val sList = validSales.filter { it.saleTimestamp in from until to }
            val totalSales = sList.sumOf { it.totalAmountMinor }
            val totalCogs = sList.sumOf { saleCostMap[it.localId] ?: 0L }
            val grossProfit = totalSales - totalCogs
            val totalExp = expenses.filter { it.expenseTimestamp in from until to }.sumOf { it.amountMinor }
            return Quadruple(totalSales, grossProfit, totalExp, sList.size)
        }

        fun makeMetric(label: String, curr: Long, prev: Long, higherIsBetter: Boolean = true): PeriodComparisonMetric {
            val diff = curr - prev
            val pct = if (prev > 0L) {
                ((diff.toDouble() / prev.toDouble()) * 100.0).roundToInt()
            } else if (curr > 0L) {
                100
            } else {
                0
            }
            val isPositive = if (higherIsBetter) diff >= 0L else diff <= 0L
            return PeriodComparisonMetric(
                labelArabic = label,
                isPositiveTrend = isPositive,
                percentDiff = pct,
                absoluteDiff = abs(diff)
            )
        }

        // Today vs Yesterday
        val startOfToday = nowMillis - (nowMillis % DAY_MILLIS)
        val startOfYesterday = startOfToday - DAY_MILLIS
        val todayAgg = aggregateForRange(startOfToday, nowMillis + 1000L)
        val yestAgg = aggregateForRange(startOfYesterday, startOfToday)

        val todayVsYest = PeriodComparisonGroup(
            periodLabelArabic = "اليوم مقارنة بالأمس",
            hasSufficientData = todayAgg.fourth > 0 || yestAgg.fourth > 0,
            summaryArabic = if (todayAgg.first >= yestAgg.first) "أداء المبيعات اليوم يتفوق على الأمس" else "انخفاض طفيف في مبيعات اليوم عن الأمس",
            sales = makeMetric("المبيعات", todayAgg.first, yestAgg.first),
            grossProfit = makeMetric("مجمل الربح", todayAgg.second, yestAgg.second),
            expenses = makeMetric("المصروفات", todayAgg.third, yestAgg.third, higherIsBetter = false),
            transactionCount = makeMetric("عدد الفواتير", todayAgg.fourth.toLong(), yestAgg.fourth.toLong())
        )

        // This Week vs Last Week
        val startOfThisWeek = startOfToday - (6L * DAY_MILLIS)
        val startOfLastWeek = startOfThisWeek - (7L * DAY_MILLIS)
        val thisWeekAgg = aggregateForRange(startOfThisWeek, nowMillis + 1000L)
        val lastWeekAgg = aggregateForRange(startOfLastWeek, startOfThisWeek)

        val weekVsLast = PeriodComparisonGroup(
            periodLabelArabic = "هذا الأسبوع مقارنة بالأسبوع السابق",
            hasSufficientData = thisWeekAgg.fourth > 0 || lastWeekAgg.fourth > 0,
            summaryArabic = if (thisWeekAgg.first >= lastWeekAgg.first) "نمو إيجابي في وتيرة النشاط الأسبوعي" else "استقرار نسبي في الأداء الأسبوعي",
            sales = makeMetric("المبيعات", thisWeekAgg.first, lastWeekAgg.first),
            grossProfit = makeMetric("مجمل الربح", thisWeekAgg.second, lastWeekAgg.second),
            expenses = makeMetric("المصروفات", thisWeekAgg.third, lastWeekAgg.third, higherIsBetter = false),
            transactionCount = makeMetric("عدد الفواتير", thisWeekAgg.fourth.toLong(), lastWeekAgg.fourth.toLong())
        )

        // This Month vs Last Month
        val startOfThisMonth = startOfToday - (29L * DAY_MILLIS)
        val startOfLastMonth = startOfThisMonth - (30L * DAY_MILLIS)
        val thisMonthAgg = aggregateForRange(startOfThisMonth, nowMillis + 1000L)
        val lastMonthAgg = aggregateForRange(startOfLastMonth, startOfThisMonth)

        val monthVsLast = PeriodComparisonGroup(
            periodLabelArabic = "آخر 30 يوماً مقارنة بالفترة السابقة",
            hasSufficientData = thisMonthAgg.fourth > 0 || lastMonthAgg.fourth > 0,
            summaryArabic = if (thisMonthAgg.first >= lastMonthAgg.first) "تحسن في الحجم الإجمالي للمبيعات الشهرية" else "نشاط متوازن خلال الشهر",
            sales = makeMetric("المبيعات", thisMonthAgg.first, lastMonthAgg.first),
            grossProfit = makeMetric("مجمل الربح", thisMonthAgg.second, lastMonthAgg.second),
            expenses = makeMetric("المصروفات", thisMonthAgg.third, lastMonthAgg.third, higherIsBetter = false),
            transactionCount = makeMetric("عدد الفواتير", thisMonthAgg.fourth.toLong(), lastMonthAgg.fourth.toLong())
        )

        return PeriodComparisonsBundle(
            todayVsYesterday = todayVsYest,
            thisWeekVsLastWeek = weekVsLast,
            thisMonthVsLastMonth = monthVsLast
        )
    }

    fun computeBusinessScorecard(
        products: List<ProductInventoryProjection>,
        sales: List<SaleEntity>,
        expenses: List<ExpenseEntity>,
        cashBalanceMinor: Long,
        totalCustomerReceivablesMinor: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): BusinessScorecardBundle {
        val pillars = mutableListOf<ScorecardPillarItem>()

        // 1. Inventory Health
        val totalProducts = products.size.coerceAtLeast(1)
        val outOfStock = products.count { it.derivedStockQty <= 0 }
        val lowStock = products.count { it.derivedStockQty in 1..it.reorderThresholdQty }
        val inventoryScore = ((1.0 - ((outOfStock * 1.5 + lowStock * 0.5) / totalProducts)) * 100.0).roundToInt().coerceIn(30, 100)
        pillars += ScorecardPillarItem(
            key = "INVENTORY_HEALTH",
            nameArabic = "سلامة وتوفر المخزون",
            statusArabic = if (inventoryScore >= 80) "ممتاز" else if (inventoryScore >= 60) "مستقر" else "يحتاج تعزيز",
            score = inventoryScore,
            colorCode = if (inventoryScore >= 80) "GREEN" else if (inventoryScore >= 60) "AMBER" else "RED",
            explanationArabic = "يتوفر $totalProducts صنف، منها $outOfStock نفد مخزونها و$lowStock تقترب من حد الطلب.",
            keyMetric = "$outOfStock أصناف نافدة / $lowStock منخفضة"
        )

        // 2. Liquidity & Cash Flow
        val cashScore = when {
            cashBalanceMinor >= 5_000_000L -> 95 // >= 50k EGP
            cashBalanceMinor >= 1_000_000L -> 80 // >= 10k EGP
            cashBalanceMinor >= 200_000L -> 65   // >= 2k EGP
            else -> 40
        }
        pillars += ScorecardPillarItem(
            key = "LIQUIDITY",
            nameArabic = "السيولة النقدية بالخزينة",
            statusArabic = if (cashScore >= 80) "سيولة قوية" else if (cashScore >= 60) "سيولة مقبولة" else "سيولة حرجة",
            score = cashScore,
            colorCode = if (cashScore >= 80) "GREEN" else if (cashScore >= 60) "AMBER" else "RED",
            explanationArabic = "الرصيد الفعلي المتوفر بالدرج يبلغ ${Money.ofMinor(cashBalanceMinor).formatArabic()}.",
            keyMetric = Money.ofMinor(cashBalanceMinor).formatArabic()
        )

        // 3. Receivables & Debt Risk
        val debtScore = when {
            totalCustomerReceivablesMinor <= 1_000_000L -> 90
            totalCustomerReceivablesMinor <= 5_000_000L -> 75
            else -> 55
        }
        pillars += ScorecardPillarItem(
            key = "RECEIVABLES",
            nameArabic = "تحصيل المديونيات ومخاطر الائتمان",
            statusArabic = if (debtScore >= 80) "مديونية منخفضة" else "تحتاج متابعة",
            score = debtScore,
            colorCode = if (debtScore >= 80) "GREEN" else "AMBER",
            explanationArabic = "إجمالي ديون العملاء الآجلة يبلغ ${Money.ofMinor(totalCustomerReceivablesMinor).formatArabic()}.",
            keyMetric = Money.ofMinor(totalCustomerReceivablesMinor).formatArabic()
        )

        // 4. Operational Activity
        val validSales = sales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }
        val recentSalesCount = validSales.count { it.saleTimestamp >= (nowMillis - 7L * DAY_MILLIS) }
        val activityScore = (recentSalesCount * 10).coerceIn(40, 95)
        pillars += ScorecardPillarItem(
            key = "ACTIVITY",
            nameArabic = "وتيرة المبيعات والنشاط التجاري",
            statusArabic = if (activityScore >= 75) "نشاط قوي" else "نشاط معتدل",
            score = activityScore,
            colorCode = if (activityScore >= 75) "GREEN" else "AMBER",
            explanationArabic = "تم تسجيل $recentSalesCount فاتورة بيع خلال الأيام السبعة الماضية.",
            keyMetric = "$recentSalesCount فاتورة / أسبوع"
        )

        val overall = (pillars.sumOf { it.score } / pillars.size).coerceIn(0, 100)
        val overallRating = when {
            overall >= 85 -> "صحة أعمال ممتازة ومستقرة للغاية"
            overall >= 70 -> "أداء تجاري جيد ومستقر"
            overall >= 55 -> "أداء متوسط مع فرص تحسين"
            else -> "يحتاج تدخلاً عاجلاً لضبط المخزون والسيولة"
        }

        return BusinessScorecardBundle(
            overallScore = overall,
            overallRatingArabic = overallRating,
            pillars = pillars
        )
    }

    fun computeBusinessForecasting(
        sales: List<SaleEntity>,
        expenses: List<ExpenseEntity>,
        walletTransfers: List<TransferEntity>,
        cashBalanceMinor: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): BusinessForecastingBundle {
        val validSales = sales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }
        val sevenDaysAgo = nowMillis - (7L * DAY_MILLIS)
        val recentSales = validSales.filter { it.saleTimestamp >= sevenDaysAgo }
        val recentSalesTotal = recentSales.sumOf { it.totalAmountMinor }

        val recentExpenses = expenses.filter { it.expenseTimestamp >= sevenDaysAgo }
        val recentExpensesTotal = recentExpenses.sumOf { it.amountMinor }

        val recentTransfers = walletTransfers.filter { it.transferTimestamp >= sevenDaysAgo }
        val recentWalletVolume = recentTransfers.sumOf { it.principalAmountMinor }

        // Conservative baseline estimate for the coming week
        val estWeeklySales = if (recentSalesTotal > 0L) recentSalesTotal else (validSales.sumOf { it.totalAmountMinor } / 2).coerceAtLeast(0L)
        val estWeeklyExpenses = if (recentExpensesTotal > 0L) recentExpensesTotal else (expenses.sumOf { it.amountMinor } / 2).coerceAtLeast(0L)
        val netCashReq = (estWeeklyExpenses - (estWeeklySales / 4L)).coerceAtLeast(0L)

        val confidence = when {
            recentSales.size >= 10 -> "عالية (مبنية على نشاط أسبوعي فعلي)"
            recentSales.isNotEmpty() -> "متوسطة (عينة عمليات محدودة)"
            else -> "أولية (تقدير مبدئي)"
        }

        return BusinessForecastingBundle(
            estimatedWeeklySalesMinor = estWeeklySales,
            estimatedWeeklyExpensesMinor = estWeeklyExpenses,
            estimatedWeeklyNetCashRequirementMinor = netCashReq,
            estimatedWeeklyWalletVolumeMinor = recentWalletVolume,
            salesForecastConfidenceArabic = confidence,
            sourcePeriodArabic = "آخر 7 أيام تشغيلية",
            calculationMethodArabic = "المتوسط المتحرك الأسبوعي المحافظ بدون مضاعفات عشوائية"
        )
    }

    fun computeDailyBusinessSummary(
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        expenses: List<ExpenseEntity>,
        cashBalanceMinor: Long,
        walletBalancesMinor: Long,
        activeAlertsCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyBusinessSummaryBundle {
        val startOfToday = nowMillis - (nowMillis % DAY_MILLIS)
        val todaySales = sales.filter { it.saleTimestamp >= startOfToday && it.status != "CANCELLED" && it.status != "REFUNDED" }
        val salesMinor = todaySales.sumOf { it.totalAmountMinor }

        val todaySaleIds = todaySales.map { it.localId }.toSet()
        val todayItems = saleItems.filter { it.saleId in todaySaleIds }
        val cogsMinor = todayItems.sumOf { (it.unitCostAtSaleMinor * (it.quantity - it.returnedQuantity)).coerceAtLeast(0L) }
        val grossProfitMinor = salesMinor - cogsMinor

        val todayExpenses = expenses.filter { it.expenseTimestamp >= startOfToday }
        val expensesMinor = todayExpenses.sumOf { it.amountMinor }

        val dateFmt = SimpleDateFormat("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar-EG"))

        return DailyBusinessSummaryBundle(
            dateFormattedArabic = dateFmt.format(Date(nowMillis)),
            salesMinor = salesMinor,
            grossProfitMinor = grossProfitMinor,
            cashOnHandMinor = cashBalanceMinor,
            walletBalancesTotalMinor = walletBalancesMinor,
            expensesMinor = expensesMinor,
            transactionsCount = todaySales.size + todayExpenses.size,
            activeAlertsCount = activeAlertsCount
        )
    }

    fun computeDemandForecasts(
        products: List<ProductInventoryProjection>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        leadTimeDays: Int = 3,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ProductDemandForecastItem> {
        val thirtyDaysAgo = nowMillis - (30L * DAY_MILLIS)
        val activeSaleIds = sales.filter { it.saleTimestamp >= thirtyDaysAgo && it.status != "CANCELLED" && it.status != "REFUNDED" }
            .map { it.localId }.toSet()

        val itemsByProduct = saleItems.filter { it.saleId in activeSaleIds }
            .groupBy { it.productId }

        return products.map { prod ->
            val soldQty = itemsByProduct[prod.productId]?.sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) } ?: 0
            val dailyVelocity = (soldQty.toDouble() / 30.0)
            val currentStock = prod.derivedStockQty.coerceAtLeast(0)

            val daysRemaining: Int? = if (dailyVelocity > 0.0) {
                (currentStock / dailyVelocity).roundToInt()
            } else if (currentStock > 0) {
                null // Indefinite / slow moving
            } else {
                0
            }

            val minStock = prod.reorderThresholdQty.coerceAtLeast(1)
            val leadTimeDemand = (dailyVelocity * leadTimeDays).roundToInt()
            val suggestedReorder = when {
                currentStock <= 0 -> maxOf(minStock * 2, 5)
                currentStock <= minStock -> (minStock * 2 - currentStock).coerceAtLeast(1)
                dailyVelocity > 0.0 && currentStock < (leadTimeDemand + minStock) -> (leadTimeDemand + minStock - currentStock).coerceAtLeast(1)
                else -> 0
            }

            val confidence = if (soldQty >= 5) "عالية" else if (soldQty > 0) "متوسطة" else "أولية"
            val explanation = when {
                currentStock <= 0 -> "الرصيد نافد بالكامل. يُنصح بإعادة الطلب فوراً لتجنب فقدان المبيعات."
                suggestedReorder > 0 -> "المخزون الحالي ($currentStock) يقترب من النفاد بناءً على معدل السحب (${"%.1f".format(dailyVelocity)}/يوم)."
                else -> "المخزون مستقر وكافٍ لتغطية فترات التوريد القادمة."
            }

            ProductDemandForecastItem(
                productId = prod.productId,
                productNameArabic = prod.nameArabic,
                currentStockQty = currentStock,
                avgDailyVelocity = dailyVelocity,
                estimatedDaysRemaining = daysRemaining,
                suggestedReorderQty = suggestedReorder,
                confidenceLabelArabic = confidence,
                explanationArabic = explanation
            )
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

package com.example.intelligence

import com.example.database.entities.CustomerEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.TransactionEntity
import com.example.financial.CustomerCreditAgingEngine
import com.example.financial.DebtAgingBuckets
import com.example.financial.Money

/**
 * Objective, explainable Customer Segments (Requirement P1-D).
 */
enum class CustomerSegment(val labelArabic: String, val descriptionArabic: String) {
    HIGH_VALUE("عميل مميز (VIP)", "إجمالي مشتريات تتجاوز 5,000 ج.م"),
    FREQUENT("عميل متكرر", "أجرى 5 فواتير شراء أو أكثر"),
    OUTSTANDING("عليه مستحقات", "توجد مبالغ آجلة مستحقة السداد"),
    NEW("عميل جديد", "انضم للمتجر خلال آخر 14 يوماً"),
    INACTIVE("غير نشط", "لم يسجل أي تعامل منذ أكثر من 60 يوماً")
}

/**
 * Customer 360 Insights (Requirement P1-D: Simple, factual, verifiable).
 */
data class CustomerFactInsight(
    val titleArabic: String,
    val descriptionArabic: String,
    val badgeLabelArabic: String,
    val isPositive: Boolean
)

data class ReceivableAgingSummary(
    val currentMinor: Long,
    val days1To7Minor: Long,
    val days8To30Minor: Long,
    val days31To60Minor: Long,
    val days60PlusMinor: Long,
    val totalOutstandingMinor: Long,
    val totalCustomersWithDebt: Int
)

data class CustomerIntelligenceProfile(
    val customerId: String,
    val customerNameArabic: String,
    val segment: CustomerSegment,
    val totalPurchasesMinor: Long,
    val totalPaymentsMinor: Long,
    val currentOutstandingMinor: Long,
    val salesCount: Int,
    val lastTransactionTimestamp: Long?,
    val agingBuckets: DebtAgingBuckets,
    val factualInsights: List<CustomerFactInsight>
)

object CustomerIntelligenceEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun classifyCustomer(
        customer: CustomerEntity,
        derivedBalanceMinor: Long,
        customerSales: List<SaleEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerSegment {
        val totalPurchases = customerSales
            .filter { it.status != "CANCELLED" && it.status != "REFUNDED" }
            .sumOf { it.totalAmountMinor }
        val count = customerSales.count { it.status != "CANCELLED" && it.status != "REFUNDED" }
        val lastSaleTs = customerSales.maxOfOrNull { it.saleTimestamp }

        val ageDaysSinceCreation = ((nowMillis - customer.createdAt).coerceAtLeast(0L) / DAY_MILLIS).toInt()
        val daysSinceLastSale = lastSaleTs?.let { ((nowMillis - it).coerceAtLeast(0L) / DAY_MILLIS).toInt() }

        return when {
            derivedBalanceMinor > 0L -> CustomerSegment.OUTSTANDING
            totalPurchases >= 500_000L -> CustomerSegment.HIGH_VALUE // 5,000+ EGP
            count >= 5 -> CustomerSegment.FREQUENT
            ageDaysSinceCreation <= 14 && count <= 2 -> CustomerSegment.NEW
            daysSinceLastSale != null && daysSinceLastSale >= 60 -> CustomerSegment.INACTIVE
            else -> CustomerSegment.NEW
        }
    }

    fun generateFactualInsights(
        customer: CustomerEntity,
        derivedBalanceMinor: Long,
        customerSales: List<SaleEntity>,
        customerPayments: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomerFactInsight> {
        val insights = mutableListOf<CustomerFactInsight>()
        val thirtyDaysAgo = nowMillis - 30L * DAY_MILLIS

        val validSales = customerSales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }
        val last30DaysSales = validSales.filter { it.saleTimestamp >= thirtyDaysAgo }
        val last30DaysRevenue = last30DaysSales.sumOf { it.totalAmountMinor }

        if (last30DaysSales.isNotEmpty()) {
            insights += CustomerFactInsight(
                titleArabic = "نشاط الـ 30 يوماً الأخيرة",
                descriptionArabic = "أجرى هذا العميل ${last30DaysSales.size} عمليات شراء بإجمالي ${Money.ofMinor(last30DaysRevenue).formatArabic()}.",
                badgeLabelArabic = "نشط مؤخراً",
                isPositive = true
            )
        }

        if (derivedBalanceMinor > 0L) {
            insights += CustomerFactInsight(
                titleArabic = "مديونية قائمة",
                descriptionArabic = "عليه رصيد مستحق للتحصيل قدره ${Money.ofMinor(derivedBalanceMinor).formatArabic()}.",
                badgeLabelArabic = "مطلوب تحصيل",
                isPositive = false
            )
        } else if (derivedBalanceMinor == 0L && validSales.isNotEmpty()) {
            insights += CustomerFactInsight(
                titleArabic = "حساب مسدد بالكامل",
                descriptionArabic = "لا توجد أي مديونيات متأخرة أو معلقة بذمة العميل.",
                badgeLabelArabic = "حساب منتظم",
                isPositive = true
            )
        }

        val totalPaid = customerPayments.filter { it.status.name == "POSTED" }.sumOf { it.amountMinor }
        val totalPurchases = validSales.sumOf { it.totalAmountMinor }

        if (totalPurchases >= 500_000L) {
            insights += CustomerFactInsight(
                titleArabic = "عميل ذو حجم تعامل كبير",
                descriptionArabic = "إجمالي مشتريات العميل التراكمية بلغ ${Money.ofMinor(totalPurchases).formatArabic()} مع سداد ${Money.ofMinor(totalPaid).formatArabic()}.",
                badgeLabelArabic = "حجم تداول مرتفع",
                isPositive = true
            )
        }

        return insights
    }

    fun computeGlobalReceivableAging(
        customers: List<CustomerEntity>,
        balancesMap: Map<String, Long>,
        allSales: List<SaleEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): ReceivableAgingSummary {
        val salesByCustomer = allSales.groupBy { it.customerId }
        var currentSum = 0L
        var d1To7Sum = 0L
        var d8To30Sum = 0L
        var d31To60Sum = 0L
        var d60PlusSum = 0L
        var customersWithDebt = 0

        for (cust in customers) {
            val bal = (balancesMap[cust.localId] ?: 0L).coerceAtLeast(0L)
            if (bal > 0L) {
                customersWithDebt++
                val custSales = salesByCustomer[cust.localId] ?: emptyList()
                val buckets = CustomerCreditAgingEngine.computeDebtAgingBuckets(
                    customerSales = custSales,
                    derivedBalanceMinor = bal,
                    defaultCreditDays = cust.defaultCreditDays,
                    nowMillis = nowMillis
                )
                d1To7Sum += buckets.bucket0To7DaysMinor
                d8To30Sum += (buckets.bucket8To15DaysMinor + buckets.bucket16To30DaysMinor)
                d31To60Sum += buckets.bucket31To60DaysMinor
                d60PlusSum += buckets.bucketOver60DaysMinor
            }
        }

        val total = d1To7Sum + d8To30Sum + d31To60Sum + d60PlusSum
        return ReceivableAgingSummary(
            currentMinor = d1To7Sum,
            days1To7Minor = d1To7Sum,
            days8To30Minor = d8To30Sum,
            days31To60Minor = d31To60Sum,
            days60PlusMinor = d60PlusSum,
            totalOutstandingMinor = total,
            totalCustomersWithDebt = customersWithDebt
        )
    }
}

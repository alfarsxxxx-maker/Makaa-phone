package com.example.ai

import com.example.BuildConfig
import com.example.auth.AuthSecurityManager
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.SaleItemEntity
import com.example.financial.Money
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production Grounded AI Assistant Engine (Section 6 & 7).
 * Strictly enforces:
 * User Question -> Intent -> Structured Query -> Database -> Verified Result -> AI Explanation.
 * NEVER hallucinates financial numbers, prices, stock, or debt.
 */
class GroundedAiAssistantEngine(
    private val database: MekkaPhoneDatabase,
    private val authSecurityManager: AuthSecurityManager
) {
    private val catalogDao = database.catalogInventoryDao()
    private val opsDao = database.operationsDao()
    private val ledgerDao = database.financialLedgerDao()
    private val coreDao = database.coreShopAuthDao()

    private fun getTodayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    private fun getCurrentWeekBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        return start to end
    }

    private fun getCurrentMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    /**
     * Identifies the user's intent deterministically from their natural language question.
     */
    fun parseIntent(question: String): Pair<AiBusinessIntent, Map<String, String>> {
        val q = Money.normalizeDigits(question).trim().lowercase()

        // Action detection first (Section 5 & 7)
        if (q.contains("سجل مصروف") || q.contains("تسجيل مصروف") || q.contains("صرف مصروف") || q.contains("سجل مصاريف") || q.contains("اضف مصروف") || q.contains("أضف مصروف")) {
            val amtMinor = extractAmountMinor(q)
            val title = extractExpenseTitle(q)
            return AiBusinessIntent.ACTION_PROPOSAL to mapOf(
                "action" to "CREATE_EXPENSE",
                "amountMinor" to (amtMinor ?: 0L).toString(),
                "title" to title
            )
        }

        // Section 3: Specialized Shop Questions
        if (q.contains("الفرق بين مبيعات") || q.contains("مقارنة مبيعات") || q.contains("الاسبوع ده والاسبوع اللي فات") || q.contains("الأسبوع ده والأسبوع اللي فات") || q.contains("الاسبوعين")) {
            return AiBusinessIntent.WEEK_OVER_WEEK_COMPARISON to emptyMap()
        }

        if (q.contains("مبيعات هذا الشهر") || q.contains("مبيعات الشهر") || q.contains("اجمالي مبيعات الشهر") || q.contains("إجمالي مبيعات الشهر") || q.contains("كام بعنا في الشهر")) {
            return AiBusinessIntent.SALES_MONTHLY to emptyMap()
        }

        if (q.contains("اكبر مبلغ") || q.contains("أكبر مبلغ") || q.contains("مين عليه اكبر مبلغ") || q.contains("اعلى مديونية") || q.contains("أعلى مديونية")) {
            return AiBusinessIntent.HIGHEST_DEBTOR to emptyMap()
        }

        if (q.contains("اعلى مصروف") || q.contains("أعلى مصروف") || q.contains("اكبر مصروف") || q.contains("أكبر مصروف")) {
            return AiBusinessIntent.HIGHEST_EXPENSE to emptyMap()
        }

        if (q.contains("مبيعات الاسبوع") || q.contains("مبيعات الأسبوع") || q.contains("مبيعات هذا الاسبوع") || q.contains("مبيعات هذا الأسبوع") || q.contains("كام بعنا في الاسبوع") || q.contains("ايراد الاسبوع")) {
            return AiBusinessIntent.SALES_WEEKLY to emptyMap()
        }

        if (q.contains("بيع النهارده") || q.contains("مبيعات النهارده") || q.contains("مبيعات اليوم") || q.contains("كام بعنا") || q.contains("ايراد اليوم") || q.contains("بعت كام النهارده") || q.contains("بعت كام")) {
            return AiBusinessIntent.SALES_TODAY to emptyMap()
        }

        if (q.contains("مين عليه فلوس") || q.contains("ديون") || q.contains("مديونيات") || q.contains("اجل") || q.contains("مستحقات العملاء") || q.contains("مين مديون") || q.contains("مين ليه فلوس")) {
            return AiBusinessIntent.RECEIVABLES_WHO_OWES to emptyMap()
        }

        if (q.contains("اكتر عميل") || q.contains("أكتر عميل") || q.contains("اكثر عميل") || q.contains("مين اكتر عميل اشترى") || q.contains("مين أكثر عميل اشترى") || q.contains("افضل عميل") || q.contains("أفضل عميل")) {
            return AiBusinessIntent.TOP_CUSTOMER to emptyMap()
        }

        if (q.contains("اكتر منتج ربحني") || q.contains("أكتر منتج ربحني") || q.contains("اكثر منتج ربح") || q.contains("اكتر منتج مربح") || q.contains("اعلى ربح") || q.contains("ارباح المنتجات") || q.contains("أرباح المنتجات")) {
            return AiBusinessIntent.TOP_PROFITABLE_PRODUCT to emptyMap()
        }

        if (q.contains("قربت تخلص") || q.contains("نواقص") || q.contains("ناقص") || q.contains("اعادة طلب") || q.contains("المخزون قليل") || q.contains("المنتجات اللي قربت تخلص")) {
            return AiBusinessIntent.LOW_STOCK to emptyMap()
        }

        if (q.contains("اعلى المنتجات") || q.contains("أعلى المنتجات") || q.contains("اكثر مبيعا") || q.contains("الأكثر مبيعاً") || q.contains("اكتر صنف بيتباع") || q.contains("اكتر منتج اتباع") || q.contains("أكتر منتج اتباع")) {
            return AiBusinessIntent.TOP_SELLING to emptyMap()
        }

        if (q.contains("محتاجة مراجعة") || q.contains("محتاجه مراجعه") || q.contains("عمليات المراجعة") || q.contains("رسائل معلقة") || q.contains("العمليات المعلقة")) {
            return AiBusinessIntent.PENDING_REVIEW_OPERATIONS to emptyMap()
        }

        if (q.contains("مصاريف الشهر") || q.contains("مصروفات الشهر") || q.contains("مصاريف كام") || q.contains("كم صرفنا الشهر") || q.contains("كام مصروفات الشهر")) {
            return AiBusinessIntent.MONTHLY_EXPENSES to emptyMap()
        }

        if (q.contains("صيانة متاخرة") || q.contains("صيانة متأخرة") || q.contains("اجهزة الصيانة") || q.contains("صيانة جاهزة")) {
            return AiBusinessIntent.OVERDUE_MAINTENANCE to emptyMap()
        }

        if (q.contains("فلوس الخزنة") || q.contains("رصيد الخزينة") || q.contains("المحافظ") || q.contains("فودافون كاش") || q.contains("انستا باي") || q.contains("كام فلوس موجودة في المحافظ")) {
            return AiBusinessIntent.CASH_AND_WALLETS to emptyMap()
        }

        if (q.contains("صنف") || q.contains("سعر") || q.contains("مخزون") || q.contains("جراب") || q.contains("شاحن") || q.contains("ايفون") || q.contains("سامسونج")) {
            return AiBusinessIntent.PRODUCT_LOOKUP to mapOf("query" to question.trim())
        }

        return AiBusinessIntent.GENERAL_QUERY to emptyMap()
    }

    private fun extractAmountMinor(text: String): Long? {
        val regex = Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:ج|جنيه|ج\.م|egp)?""")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 100).toLong() }
    }

    private fun extractExpenseTitle(text: String): String {
        return text
            .replace(Regex("""(?:سجل|تسجيل|صرف|عملية)\s*مصروف(?:\s*بقيمة|\s*بمبلغ)?"""), "")
            .replace(Regex("""[0-9]+(?:\.[0-9]{1,2})?\s*(?:ج|جنيه|ج\.م|egp)?"""), "")
            .trim()
            .ifBlank { "مصروف تشغيلي عام" }
    }

    /**
     * Executes the structured query against Room database and builds verified FactSheet.
     */
    suspend fun queryGroundedDatabaseFacts(
        intent: AiBusinessIntent,
        parameters: Map<String, String>,
        shopId: String
    ): GroundedFactSheet = withContext(Dispatchers.IO) {
        val (startToday, endToday) = getTodayBounds()
        val (startMonth, endMonth) = getCurrentMonthBounds()

        when (intent) {
            AiBusinessIntent.SALES_TODAY -> {
                val todaySales = opsDao.getAllSales(shopId).filter { it.saleTimestamp in startToday..endToday }
                if (todaySales.isEmpty()) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد مبيعات مسجلة اليوم حتى الآن.",
                        structuredMetrics = mapOf("إجمالي مبيعات اليوم" to "0 جنيه", "عدد الفواتير" to "0"),
                        itemsSummary = emptyList()
                    )
                } else {
                    val totalSalesMinor = todaySales.sumOf { it.totalAmountMinor }
                    val paidMinor = todaySales.sumOf { it.paidAmountMinor }
                    val creditMinor = totalSalesMinor - paidMinor

                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "إجمالي مبيعات اليوم: ${Money.ofMinor(totalSalesMinor).formatArabic()} بعدد ${todaySales.size} فواتير (المدفوع نقدًا/محافظ: ${Money.ofMinor(paidMinor).formatArabic()}، والمتبقي آجل: ${Money.ofMinor(creditMinor).formatArabic()})",
                        structuredMetrics = mapOf(
                            "إجمالي المبيعات" to Money.ofMinor(totalSalesMinor).formatArabic(),
                            "عدد الفواتير" to "${todaySales.size}",
                            "المحصل فعليًا" to Money.ofMinor(paidMinor).formatArabic(),
                            "الآجل الجديد" to Money.ofMinor(creditMinor).formatArabic()
                        ),
                        itemsSummary = todaySales.take(5).map { "فاتورة #${it.invoiceNumber}: ${Money.ofMinor(it.totalAmountMinor).formatArabic()}" }
                    )
                }
            }

            AiBusinessIntent.SALES_WEEKLY -> {
                val (startWeek, endWeek) = getCurrentWeekBounds()
                val weekSales = opsDao.getAllSales(shopId).filter { it.saleTimestamp in startWeek..endWeek }
                if (weekSales.isEmpty()) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد مبيعات مسجلة خلال الأسبوع الأخير.",
                        structuredMetrics = mapOf("مبيعات الأسبوع" to "0 جنيه", "عدد الفواتير" to "0"),
                        itemsSummary = emptyList()
                    )
                } else {
                    val totalSalesMinor = weekSales.sumOf { it.totalAmountMinor }
                    val paidMinor = weekSales.sumOf { it.paidAmountMinor }
                    val creditMinor = totalSalesMinor - paidMinor

                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "إجمالي مبيعات الأسبوع: ${Money.ofMinor(totalSalesMinor).formatArabic()} بعدد ${weekSales.size} فواتير (المدفوع: ${Money.ofMinor(paidMinor).formatArabic()}، والمتبقي آجل: ${Money.ofMinor(creditMinor).formatArabic()}).",
                        structuredMetrics = mapOf(
                            "إجمالي مبيعات الأسبوع" to Money.ofMinor(totalSalesMinor).formatArabic(),
                            "عدد الفواتير" to "${weekSales.size}",
                            "المحصل" to Money.ofMinor(paidMinor).formatArabic(),
                            "الآجل" to Money.ofMinor(creditMinor).formatArabic()
                        ),
                        itemsSummary = weekSales.take(5).map { "فاتورة #${it.invoiceNumber}: ${Money.ofMinor(it.totalAmountMinor).formatArabic()}" }
                    )
                }
            }

            AiBusinessIntent.TOP_CUSTOMER -> {
                val allSales = opsDao.getAllSales(shopId).filter { !it.customerId.isNullOrBlank() }
                if (allSales.isEmpty()) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد بيانات كافية للإجابة (لم تُسجل مبيعات مرتبطة بعملاء بعد).",
                        itemsSummary = emptyList()
                    )
                } else {
                    val customers = catalogDao.observeCustomerBalanceProjections(shopId).first().associateBy { it.customerId }
                    val topCustomers = allSales.groupBy { it.customerId!! }
                        .map { (cid, sales) ->
                            val c = customers[cid]
                            val name = c?.fullName ?: "عميل رقم $cid"
                            val totalSpent = sales.sumOf { it.totalAmountMinor }
                            val invoicesCount = sales.size
                            val currentDebt = c?.outstandingBalanceMinor ?: 0L
                            Triple(name, totalSpent, invoicesCount) to currentDebt
                        }
                        .sortedByDescending { it.first.second }
                        .take(5)

                    val top = topCustomers.firstOrNull()
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = if (top != null) {
                            "أكثر عميل شراءً هو (${top.first.first}) بإجمالي مشتريات ${Money.ofMinor(top.first.second).formatArabic()} عبر ${top.first.third} فواتير (مديونيته الحالية: ${Money.ofMinor(top.second).formatArabic()})."
                        } else {
                            "لا توجد بيانات كافية للإجابة."
                        },
                        structuredMetrics = mapOf(
                            "العميل الأول" to (top?.first?.first ?: "لا يوجد"),
                            "إجمالي مشترياته" to (top?.let { Money.ofMinor(it.first.second).formatArabic() } ?: "0"),
                            "عدد الفواتير" to "${top?.first?.third ?: 0}"
                        ),
                        itemsSummary = topCustomers.map {
                            "${it.first.first}: مشتريات ${Money.ofMinor(it.first.second).formatArabic()} (${it.first.third} فواتير)"
                        }
                    )
                }
            }

            AiBusinessIntent.TOP_PROFITABLE_PRODUCT -> {
                val saleItems: List<SaleItemEntity> = opsDao.getAllSaleItems(shopId)
                if (saleItems.isEmpty()) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد بيانات كافية للإجابة (لا توجد مبيعات مسجلة لحساب الأرباح).",
                        itemsSummary = emptyList()
                    )
                } else {
                    val products = catalogDao.observeProductInventoryProjections(shopId).first().associateBy { it.productId }
                    val profitableProducts = saleItems.groupBy { it.productId }
                        .mapNotNull { (pid, items) ->
                            val p = products[pid] ?: return@mapNotNull null
                            val totalQty = items.sumOf { it.quantity }
                            val totalRevenue = items.sumOf { it.lineTotalMinor }
                            val totalCost = totalQty * p.weightedAvgCostMinor
                            val grossProfit = totalRevenue - totalCost
                            val marginPercent = if (totalRevenue > 0) ((grossProfit.toDouble() / totalRevenue) * 100).toInt() else 0
                            Triple(p.nameArabic, grossProfit, Pair(totalRevenue, Pair(totalQty, marginPercent)))
                        }
                        .sortedByDescending { it.second }
                        .take(5)

                    val top = profitableProducts.firstOrNull()
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = if (top != null) {
                            "أعلى صنف تحقيقًا للأرباح هو (${top.first}) بإجمالي ربح ${Money.ofMinor(top.second).formatArabic()} (هامش ربح ${top.third.second.second}% من مبيعات ${top.third.second.first} قطعة)."
                        } else {
                            "لا توجد بيانات كافية للإجابة."
                        },
                        structuredMetrics = mapOf(
                            "الأعلى ربحًا" to (top?.first ?: "لا يوجد"),
                            "إجمالي الربح" to (top?.let { Money.ofMinor(it.second).formatArabic() } ?: "0"),
                            "هامش الربح" to "${top?.third?.second?.second ?: 0}%"
                        ),
                        itemsSummary = profitableProducts.map {
                            "${it.first}: ربح ${Money.ofMinor(it.second).formatArabic()} (مبيعات ${Money.ofMinor(it.third.first).formatArabic()})"
                        }
                    )
                }
            }

            AiBusinessIntent.PENDING_REVIEW_OPERATIONS -> {
                val pendingTransactions = opsDao.getAllPendingTransactions(shopId).filter { it.status == "AWAITING_CONFIRMATION" }
                val orders = opsDao.getAllMaintenanceOrders(shopId).filter { it.status == MaintenanceStatus.RECEIVED || it.status == MaintenanceStatus.DIAGNOSING }
                val totalPending = pendingTransactions.size + orders.size

                if (totalPending == 0) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "جميع العمليات مكتملة ومؤكدة! لا توجد معاملات معلقة أو أجهزة بانتظار الفحص حالياً.",
                        structuredMetrics = mapOf("العمليات المعلقة" to "0"),
                        itemsSummary = emptyList()
                    )
                } else {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "يوجد إجمالي $totalPending عملية تحتاج لمراجعتك وتأكيدك (${pendingTransactions.size} عمليات معلقة، و${orders.size} أجهزة صيانة بانتظار الفحص).",
                        structuredMetrics = mapOf(
                            "إجمالي المعلق" to "$totalPending",
                            "معاملات للمراجعة" to "${pendingTransactions.size}",
                            "أجهزة صيانة جديدة" to "${orders.size}"
                        ),
                        itemsSummary = pendingTransactions.take(5).map {
                            "عملية ${it.proposedType}: ${Money.ofMinor(it.proposedAmountMinor).formatArabic()} (بانتظار التأكيد)"
                        } + orders.take(3).map {
                            "صيانة #${it.ticketNumber}: ${it.deviceBrand} ${it.deviceModel}"
                        }
                    )
                }
            }

            AiBusinessIntent.RECEIVABLES_WHO_OWES -> {
                val customersWithDebt = catalogDao.observeCustomerBalanceProjections(shopId).first()
                    .filter { it.outstandingBalanceMinor > 0L }
                    .sortedByDescending { it.outstandingBalanceMinor }

                val totalDebtMinor = customersWithDebt.sumOf { it.outstandingBalanceMinor }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "إجمالي مديونيات العملاء القائمة: ${Money.ofMinor(totalDebtMinor).formatArabic()} موزعة على ${customersWithDebt.size} عملاء.",
                    structuredMetrics = mapOf(
                        "إجمالي المديونيات" to Money.ofMinor(totalDebtMinor).formatArabic(),
                        "عدد العملاء المدينين" to "${customersWithDebt.size}"
                    ),
                    itemsSummary = customersWithDebt.take(8).map {
                        "${it.fullName} (هاتف: ${it.phone}): ${Money.ofMinor(it.outstandingBalanceMinor).formatArabic()}"
                    }
                )
            }

            AiBusinessIntent.LOW_STOCK -> {
                val lowStockProducts = catalogDao.observeProductInventoryProjections(shopId).first()
                    .filter { it.derivedStockQty <= it.reorderThresholdQty }
                    .sortedBy { it.derivedStockQty }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "يوجد ${lowStockProducts.size} أصناف وصلت إلى حد إعادة الطلب أو نفدت من المخزن.",
                    structuredMetrics = mapOf(
                        "عدد النواقص" to "${lowStockProducts.size}",
                        "أصناف رصيدها صفر" to "${lowStockProducts.count { it.derivedStockQty <= 0 }}"
                    ),
                    itemsSummary = lowStockProducts.take(8).map {
                        "${it.nameArabic} (كود: ${it.sku}): المتبقي ${it.derivedStockQty} قطعة فقط (حد الطلب: ${it.reorderThresholdQty})"
                    }
                )
            }

            AiBusinessIntent.TOP_SELLING -> {
                val saleItems: List<SaleItemEntity> = opsDao.getAllSaleItems(shopId)
                val products = catalogDao.observeProductInventoryProjections(shopId).first().associateBy { it.productId }
                val topProducts = saleItems.groupBy { it.productId }
                    .map { (pid, items) ->
                        val p = products[pid]
                        val name = p?.nameArabic ?: "صنف غير معروف"
                        val totalQty = items.sumOf { it.quantity }
                        val totalRevenue = items.sumOf { it.lineTotalMinor }
                        Triple(name, totalQty, totalRevenue)
                    }
                    .sortedByDescending { it.second }
                    .take(5)

                val firstProduct = topProducts.firstOrNull()
                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = if (firstProduct != null) "المنتج الأكثر مبيعًا هو (${firstProduct.first}) بإجمالي ${firstProduct.second} قطعة." else "لا توجد مبيعات مسجلة حتى الآن.",
                    structuredMetrics = mapOf(
                        "الأول مبيعًا" to (firstProduct?.first ?: "لا يوجد"),
                        "كمية الأول" to "${firstProduct?.second ?: 0} قطعة"
                    ),
                    itemsSummary = topProducts.map {
                        "${it.first}: بيع ${it.second} قطعة بإجمالي ${Money.ofMinor(it.third).formatArabic()}"
                    }
                )
            }

            AiBusinessIntent.MONTHLY_EXPENSES -> {
                val expenses = opsDao.getAllExpenses(shopId).filter { it.expenseTimestamp in startMonth..endMonth }
                val totalMinor = expenses.sumOf { it.amountMinor }
                val byCategory = expenses.groupBy { it.categoryName }
                    .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "إجمالي مصروفات الشهر الحالي: ${Money.ofMinor(totalMinor).formatArabic()} موزعة على ${expenses.size} سندات صرف.",
                    structuredMetrics = mapOf(
                        "إجمالي مصروفات الشهر" to Money.ofMinor(totalMinor).formatArabic(),
                        "عدد السندات" to "${expenses.size}"
                    ),
                    itemsSummary = byCategory.entries.map {
                        "${it.key}: ${Money.ofMinor(it.value).formatArabic()}"
                    }
                )
            }

            AiBusinessIntent.OVERDUE_MAINTENANCE -> {
                val orders = opsDao.getAllMaintenanceOrders(shopId)
                val overdue = orders.filter {
                    it.status.isPendingWork && it.expectedDeliveryAt != null && it.expectedDeliveryAt < startToday
                }
                val ready = orders.filter { it.status == MaintenanceStatus.READY_FOR_PICKUP }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "يوجد ${overdue.size} أجهزة صيانة متأخرة عن موعد التسليم، و${ready.size} أجهزة جاهزة للاستلام.",
                    structuredMetrics = mapOf(
                        "صيانة متأخرة" to "${overdue.size}",
                        "جاهز للتسليم" to "${ready.size}"
                    ),
                    itemsSummary = overdue.take(5).map {
                        "تذكرة #${it.ticketNumber} (${it.deviceBrand} ${it.deviceModel}) للعميل ${it.customerNameSnapshot}"
                    }
                )
            }

            AiBusinessIntent.CASH_AND_WALLETS -> {
                val drawers = ledgerDao.observeCashDrawerDerivedProjections(shopId, startToday, endToday).first()
                val wallets = ledgerDao.observeWalletDerivedProjections(shopId, startToday, endToday, startMonth, endMonth).first()

                val cashMinor = drawers.firstOrNull()?.derivedBalanceMinor ?: 0L
                val walletsMinor = wallets.sumOf { it.derivedBalanceMinor }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "رصيد الخزينة النقدية: ${Money.ofMinor(cashMinor).formatArabic()} • إجمالي المحافظ الإلكترونية: ${Money.ofMinor(walletsMinor).formatArabic()} (إجمالي السيولة: ${Money.ofMinor(cashMinor + walletsMinor).formatArabic()})",
                    structuredMetrics = mapOf(
                        "رصيد الخزينة" to Money.ofMinor(cashMinor).formatArabic(),
                        "رصيد المحافظ" to Money.ofMinor(walletsMinor).formatArabic(),
                        "إجمالي السيولة" to Money.ofMinor(cashMinor + walletsMinor).formatArabic()
                    ),
                    itemsSummary = wallets.map {
                        "${it.walletNameArabic} (${it.walletNumber}): ${Money.ofMinor(it.derivedBalanceMinor).formatArabic()}"
                    }
                )
            }

            AiBusinessIntent.PRODUCT_LOOKUP -> {
                val q = parameters["query"].orEmpty().lowercase()
                val matches = catalogDao.observeProductInventoryProjections(shopId).first()
                    .filter { it.nameArabic.lowercase().contains(q) || it.sku.lowercase().contains(q) }
                    .take(3)

                if (matches.isNotEmpty()) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "تم العثور على ${matches.size} أصناف مطابقة للبحث في قاعدة البيانات.",
                        itemsSummary = matches.map {
                            "${it.nameArabic} | الرصيد: ${it.derivedStockQty} قطعة | السعر: ${Money.ofMinor(it.effectiveRetailPriceMinor).formatArabic()} | WAC: ${Money.ofMinor(it.weightedAvgCostMinor).formatArabic()}"
                        }
                    )
                } else {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لم يتم العثور على صنف مطابق لهذا الوصف في المخزن.",
                        itemsSummary = emptyList()
                    )
                }
            }

            AiBusinessIntent.ACTION_PROPOSAL -> {
                val title = parameters["title"] ?: "مصروف عام"
                val amountMinor = parameters["amountMinor"]?.toLongOrNull() ?: 0L
                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "تم تجهيز مقترح عملية: تسجيل مصروف بقيمة ${Money.ofMinor(amountMinor).formatArabic()} تحت عنوان ($title) — بانتظار مراجعتك واعتمادك.",
                    structuredMetrics = mapOf(
                        "العملية" to "تسجيل مصروف",
                        "المبلغ" to Money.ofMinor(amountMinor).formatArabic(),
                        "البند" to title
                    )
                )
            }

            AiBusinessIntent.SALES_MONTHLY -> {
                val monthSales = opsDao.getAllSales(shopId).filter { it.saleTimestamp in startMonth..endMonth && it.status != "CANCELLED" }
                val totalSalesMinor = monthSales.sumOf { it.totalAmountMinor }
                val paidMinor = monthSales.sumOf { it.paidAmountMinor }
                val profitMinor = monthSales.sumOf { it.grossProfitMinor }
                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "إجمالي مبيعات الشهر الحالي: ${Money.ofMinor(totalSalesMinor).formatArabic()} عبر ${monthSales.size} فواتير (المدفوع: ${Money.ofMinor(paidMinor).formatArabic()}، صافي الربح الإجمالي: ${Money.ofMinor(profitMinor).formatArabic()}).",
                    structuredMetrics = mapOf(
                        "إجمالي مبيعات الشهر" to Money.ofMinor(totalSalesMinor).formatArabic(),
                        "المدفوع نقداً" to Money.ofMinor(paidMinor).formatArabic(),
                        "الأرباح الإجمالية" to Money.ofMinor(profitMinor).formatArabic(),
                        "عدد الفواتير" to "${monthSales.size}"
                    ),
                    itemsSummary = monthSales.take(5).map { "فاتورة #${it.invoiceNumber}: ${Money.ofMinor(it.totalAmountMinor).formatArabic()}" },
                    citationSourceArabic = "المصدر: ${monthSales.size} فواتير بيع مسجلة خلال الشهر الحالي"
                )
            }

            AiBusinessIntent.HIGHEST_DEBTOR -> {
                val debtors = catalogDao.observeCustomerBalanceProjections(shopId).first()
                    .filter { it.outstandingBalanceMinor > 0L }
                    .sortedByDescending { it.outstandingBalanceMinor }
                val topDebtor = debtors.firstOrNull()
                if (topDebtor != null) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "أكبر مديونية مستحقة حالياً هي على العميل (${topDebtor.fullName}) بمبلغ ${Money.ofMinor(topDebtor.outstandingBalanceMinor).formatArabic()} (هاتف: ${topDebtor.phone}).",
                        structuredMetrics = mapOf(
                            "العميل" to topDebtor.fullName,
                            "المبلغ المستحق" to Money.ofMinor(topDebtor.outstandingBalanceMinor).formatArabic(),
                            "رقم الهاتف" to topDebtor.phone
                        ),
                        itemsSummary = debtors.take(5).map { "${it.fullName}: ${Money.ofMinor(it.outstandingBalanceMinor).formatArabic()}" },
                        citationSourceArabic = "المصدر: سجل الدفاتر المعتمد لمديونيات العملاء"
                    )
                } else {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد أي مديونيات مسجلة على أي عميل حالياً (جميع الحسابات مسددة بالكامل).",
                        structuredMetrics = mapOf("أعلى مديونية" to "0 جنيه"),
                        citationSourceArabic = "المصدر: سجل حسابات العملاء"
                    )
                }
            }

            AiBusinessIntent.HIGHEST_EXPENSE -> {
                val expenses = opsDao.getAllExpenses(shopId).sortedByDescending { it.amountMinor }
                val topExp = expenses.firstOrNull()
                if (topExp != null) {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "أعلى مصروف مسجل هو (${topExp.title}) بقيمة ${Money.ofMinor(topExp.amountMinor).formatArabic()} في تصنيف (${topExp.categoryName}).",
                        structuredMetrics = mapOf(
                            "البند" to topExp.title,
                            "المبلغ" to Money.ofMinor(topExp.amountMinor).formatArabic(),
                            "التصنيف" to topExp.categoryName
                        ),
                        itemsSummary = expenses.take(5).map { "${it.title}: ${Money.ofMinor(it.amountMinor).formatArabic()}" },
                        citationSourceArabic = "المصدر: قيود دفتر المصروفات التشغيلية"
                    )
                } else {
                    GroundedFactSheet(
                        intent = intent,
                        summaryDataArabic = "لا توجد مصروفات مسجلة بالدفاتر حتى الآن.",
                        citationSourceArabic = "المصدر: دفتر المصروفات"
                    )
                }
            }

            AiBusinessIntent.WEEK_OVER_WEEK_COMPARISON -> {
                val now = System.currentTimeMillis()
                val weekStart = now - (7L * 24L * 3600L * 1000L)
                val prevWeekStart = now - (14L * 24L * 3600L * 1000L)
                val allSales = opsDao.getAllSales(shopId).filter { it.status != "CANCELLED" }
                val thisWeekSales = allSales.filter { it.saleTimestamp in weekStart..now }
                val lastWeekSales = allSales.filter { it.saleTimestamp in prevWeekStart until weekStart }
                val thisWeekTotal = thisWeekSales.sumOf { it.totalAmountMinor }
                val lastWeekTotal = lastWeekSales.sumOf { it.totalAmountMinor }
                val diff = thisWeekTotal - lastWeekTotal
                val pct = if (lastWeekTotal > 0L) (diff.toDouble() / lastWeekTotal.toDouble()) * 100.0 else null

                val growthLabel = when {
                    lastWeekTotal == 0L -> "لا توجد مبيعات في الأسبوع السابق للمقارنة"
                    diff > 0L -> "نمو بنسبة +${String.format("%.1f%%", pct)} (+${Money.ofMinor(diff).formatArabic()})"
                    diff < 0L -> "انخفاض بنسبة ${String.format("%.1f%%", pct)} (${Money.ofMinor(diff).formatArabic()})"
                    else -> "المبيعات متطابقة تماماً"
                }

                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "مبيعات الأسبوع الحالي: ${Money.ofMinor(thisWeekTotal).formatArabic()} (${thisWeekSales.size} فواتير) • الأسبوع السابق: ${Money.ofMinor(lastWeekTotal).formatArabic()} (${lastWeekSales.size} فواتير). الفارق: $growthLabel.",
                    structuredMetrics = mapOf(
                        "الأسبوع الحالي" to Money.ofMinor(thisWeekTotal).formatArabic(),
                        "الأسبوع السابق" to Money.ofMinor(lastWeekTotal).formatArabic(),
                        "الفارق" to growthLabel
                    ),
                    citationSourceArabic = "المصدر: مقارنة ${thisWeekSales.size} فاتورة حالية مع ${lastWeekSales.size} فاتورة سابقة"
                )
            }

            AiBusinessIntent.GENERAL_QUERY -> {
                val shop = coreDao.getShopById(shopId)
                val name = shop?.nameArabic ?: "مكة فون"
                GroundedFactSheet(
                    intent = intent,
                    summaryDataArabic = "مرحبًا بك في مساعد $name الذكي. يمكنك سؤالي عن: مبيعات اليوم، مديونيات العملاء، نواقص البضاعة، مصاريف الشهر، تذاكر الصيانة، وأرصدة الخزينة والمحافظ."
                )
            }
        }
    }

    /**
     * Synthesizes final response via Gemini API using verified grounded facts,
     * or gracefully falls back to deterministic Arabic synthesis if offline or key is unset.
     */
    suspend fun processUserInquiry(
        userQuestion: String,
        shopId: String
    ): AiAssistantResponse = withContext(Dispatchers.IO) {
        val (intent, params) = parseIntent(userQuestion)
        val facts = queryGroundedDatabaseFacts(intent, params, shopId)

        var proposedAction: AiActionProposal? = null
        if (intent == AiBusinessIntent.ACTION_PROPOSAL) {
            val amountMinor = params["amountMinor"]?.toLongOrNull() ?: 0L
            val title = params["title"] ?: "مصروف تشغيلي"
            proposedAction = AiActionProposal(
                actionType = AiProposedActionType.CREATE_EXPENSE,
                titleArabic = "تسجيل مصروف: $title",
                descriptionArabic = "صرف مبلغ ${Money.ofMinor(amountMinor).formatArabic()} من الخزينة النقدية الرئيسية للبند ($title)",
                parameters = mapOf(
                    "category" to "مصروفات عامة",
                    "title" to title,
                    "amountMinor" to amountMinor.toString()
                )
            )
        } else if (intent == AiBusinessIntent.LOW_STOCK && (facts.structuredMetrics["عدد النواقص"]?.toIntOrNull() ?: 0) > 0) {
            proposedAction = AiActionProposal(
                actionType = AiProposedActionType.CREATE_PURCHASE_DRAFT,
                titleArabic = "إنشاء مسودة شراء للأصناف الناقصة",
                descriptionArabic = "تجهيز مسودة شراء مجمعة للأصناف التي وصلت لحد الطلب لمراجعتها واعتمادها",
                parameters = mapOf("reason" to "LOW_STOCK_REORDER")
            )
        }

        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val isKeyConfigured = geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY"

        var synthesizedText = facts.summaryDataArabic
        var isLlmUsed = false

        if (isKeyConfigured) {
            val prompt = buildGroundedPrompt(userQuestion, facts)
            val llmResult = callGeminiApiRest(geminiApiKey, prompt)
            if (llmResult is AppResult.Success) {
                synthesizedText = llmResult.data
                isLlmUsed = true
            }
        }

        // If not using LLM, format nicely with items and source citation
        if (!isLlmUsed) {
            val sb = StringBuilder()
            sb.append(facts.summaryDataArabic)
            if (facts.itemsSummary.isNotEmpty()) {
                sb.append("\n\nالتفاصيل:\n• ").append(facts.itemsSummary.joinToString("\n• "))
            }
            sb.append("\n\n📌 ").append(facts.citationSourceArabic)
            synthesizedText = sb.toString()
        } else {
            synthesizedText = "$synthesizedText\n\n📌 ${facts.citationSourceArabic}"
        }

        AiAssistantResponse(
            userQuestion = userQuestion,
            detectedIntent = intent,
            factualResultArabic = synthesizedText,
            groundedFacts = facts,
            proposedAction = proposedAction,
            isLlmSynthesized = isLlmUsed
        )
    }

    private fun buildGroundedPrompt(question: String, facts: GroundedFactSheet): String {
        return """
            أنت المساعد المالي والتشغيلي الذكي لنظام إدارة متجر "مكة فون".
            القواعد الصارمة:
            1. استخدم فقط الأرقام والحقائق المذكورة أدناه والمستخرجة مباشرة من قاعدة البيانات.
            2. ممنوع اختراع أو تخمين أي أسعار أو أرقام أو كميات أو مديونيات من عندك.
            3. أجب باللغة العربية بأسلوب احترافي وودود وواضح لصاحب المتجر.
            
            بيانات قاعدة البيانات الموثقة:
            - الهدف: ${facts.intent.titleArabic}
            - الملخص: ${facts.summaryDataArabic}
            - العناصر: ${facts.itemsSummary.joinToString(" | ")}
            
            سؤال المستخدم: "$question"
        """.trimIndent()
    }

    private fun callGeminiApiRest(apiKey: String, prompt: String): AppResult<String> {
        var connection: HttpURLConnection? = null
        return try {
            val model = "gemini-3.5-flash"
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(endpoint)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val partObj = JSONObject().apply { put("text", prompt) }
                    val contentObj = JSONObject().apply { put("parts", JSONArray().put(partObj)) }
                    put(contentObj)
                }
                put("contents", contents)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            if (connection.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(responseText)
                val candidates = root.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    AppResult.Success(text.trim())
                } else {
                    AppResult.Error("رد فارغ من نموذج Gemini", "EMPTY_GEMINI_RESPONSE")
                }
            } else {
                AppResult.Error("فشل طلب Gemini (HTTP ${connection.responseCode})", "GEMINI_HTTP_ERROR")
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر الاتصال بـ Gemini API: ${e.message}", "GEMINI_CONNECTION_ERROR", e)
        } finally {
            connection?.disconnect()
        }
    }
}

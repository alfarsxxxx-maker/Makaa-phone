package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Score
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaBlueInfo
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.theme.MekkaGoldPrimary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.entities.SaleEntity
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.financial.ReportPeriodPreset
import com.example.financial.ReportsCenterBundle
import com.example.navigation.MekkaDestination
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaGoldPrimary
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional Reports Center (`مركز التقارير`, Part 3 Rules 1, 2, 3).
 *
 * Organized into 7 authoritative sections:
 * 0. المبيعات (Sales)
 * 1. الأرباح (Profits)
 * 2. العملاء (Customers)
 * 3. الموردون (Suppliers)
 * 4. المخزون (Inventory)
 * 5. المحافظ (Wallets)
 * 6. الصيانة (Maintenance)
 *
 * Consumes 100% domain-calculated `ReportsCenterBundle` from `ReportsCenterEngine`
 * without performing financial truth calculations inside Compose.
 * Supports date/entity filters, visual bar charts, export/share, and drill-down:
 * Report -> Summary -> Transaction -> Customer/Product/Wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToDestination: (MekkaDestination) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val bundle by viewModel.reportsCenterBundle.collectAsStateWithLifecycle()
    val filter by viewModel.reportsFilter.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val providers by viewModel.walletProviders.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val periodComparisons by viewModel.periodComparisons.collectAsStateWithLifecycle()
    val businessScorecard by viewModel.businessScorecard.collectAsStateWithLifecycle()
    val businessForecasting by viewModel.businessForecasting.collectAsStateWithLifecycle()
    val dailyBusinessSummary by viewModel.dailyBusinessSummary.collectAsStateWithLifecycle()
    val demandForecasts by viewModel.demandForecasts.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showEntityFilterSheet by remember { mutableStateOf(false) }
    var drillDownSale by remember { mutableStateOf<SaleEntity?>(null) }

    val reportTabs = listOf(
        "المبيعات" to Icons.Default.PointOfSale,
        "الأرباح" to Icons.AutoMirrored.Filled.TrendingUp,
        "العملاء" to Icons.Default.Groups,
        "الموردون" to Icons.Default.LocalShipping,
        "المخزون" to Icons.Default.Inventory2,
        "المحافظ" to Icons.Default.AccountBalanceWallet,
        "الصيانة" to Icons.Default.Build,
        "المقارنات الزمنية" to Icons.Default.CompareArrows,
        "صحة الأعمال" to Icons.Default.Score,
        "التوقعات الذكية" to Icons.Default.QueryStats,
        "الملخص اليومي" to Icons.Default.Today
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reports_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Column {
                        Text(
                            text = "مركز التقارير",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${shopConfig.shopNameArabic} • مصدر موحد من المحرك المحاسبي والمخزني",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showEntityFilterSheet = true },
                        modifier = Modifier.testTag("reports_entity_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "تصفية متقدمة"
                        )
                    }
                    IconButton(
                        onClick = {
                            val shareText = buildShareableReportSummary(bundle, shopConfig.shopNameArabic)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "تقرير مكة فون المالي والتشغيلي")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "مشاركة وتصدير التقرير")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            viewModel.showFeedbackMessage("تم تجهيز ملخص التقرير للمشاركة والتصدير")
                        },
                        modifier = Modifier.testTag("reports_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة وتصدير التقرير"
                        )
                    }
                }
            }
        }

        // Date Filter Bar (Rule 2)
        item {
            V4ReportingEngineSection(viewModel = viewModel)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportPeriodPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = filter.periodPreset == preset,
                        onClick = {
                            viewModel.updateReportsFilter { it.copy(periodPreset = preset) }
                        },
                        label = { Text(preset.arabicLabel) },
                        modifier = Modifier.testTag("report_period_${preset.name.lowercase()}")
                    )
                }
                if (filter.selectedProductId != null || filter.selectedCustomerId != null || filter.selectedProviderCode != null) {
                    TextButton(
                        onClick = {
                            viewModel.updateReportsFilter {
                                it.copy(
                                    selectedProductId = null,
                                    selectedCustomerId = null,
                                    selectedSupplierId = null,
                                    selectedProviderCode = null
                                )
                            }
                        }
                    ) {
                        Text("مسح الفلاتر الفرعية")
                    }
                }
            }
        }

        // 7 Report Sections Tab Row (Rule 1)
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                reportTabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.testTag("reports_tab_$index")
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // 1. المبيعات (Sales)
                val salesSec = bundle.salesSection
                item {
                    ReportSectionHeader(
                        title = "تقرير المبيعات (${filter.periodPreset.arabicLabel})",
                        subtitle = "إجمالي المبيعات، المرتجعات، الخصومات، وأعلى الأصناف مبيعاً"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "مبيعات اليوم",
                            value = Money.ofMinor(salesSec.todaySalesMinor).formatArabic(),
                            subtitle = "إجمالي فواتير اليوم",
                            accentColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "مبيعات الفترة",
                            value = Money.ofMinor(salesSec.periodSalesMinor).formatArabic(),
                            subtitle = "${salesSec.periodInvoiceCount} فاتورة",
                            accentColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "المرتجعات",
                            value = Money.ofMinor(salesSec.periodReturnsMinor).formatArabic(),
                            subtitle = "${salesSec.periodReturnCount} عملية مرتجع",
                            accentColor = FinancialNegative,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "الخصومات الممنوحة",
                            value = Money.ofMinor(salesSec.periodDiscountsMinor).formatArabic(),
                            subtitle = "صافي المبيعات: ${Money.ofMinor(salesSec.netSalesMinor).formatArabic()}",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Sales by Day visual chart
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "المبيعات حسب اليوم",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (salesSec.salesByDay.isEmpty()) {
                                Text(
                                    text = "لا توجد فواتير مبيعات مسجلة في هذه الفترة.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val maxDaySales = salesSec.salesByDay.maxOfOrNull { it.grossSalesMinor }?.coerceAtLeast(1L) ?: 1L
                                salesSec.salesByDay.forEach { dayRow ->
                                    val fraction = (dayRow.grossSalesMinor.toFloat() / maxDaySales.toFloat()).coerceIn(0.04f, 1f)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${dayRow.dayLabel} (${dayRow.invoiceCount} فاتورة)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = Money.ofMinor(dayRow.grossSalesMinor).formatArabic(),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = FinancialPositive
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = FinancialPositive
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Products
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الأصناف الأكثر مبيعاً",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onNavigateToDestination(MekkaDestination.PRODUCTS) }) {
                                    Text("فتح المخزون")
                                }
                            }
                            if (salesSec.topProducts.isEmpty()) {
                                Text(
                                    text = "لم يتم بيع أصناف خلال الفترة المحددة.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                salesSec.topProducts.forEach { prod ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.requestSelectProductDetail(prod.productId)
                                                onNavigateToDestination(MekkaDestination.PRODUCTS)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = prod.productNameArabic,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "SKU: ${prod.sku} • الكمية المباعة: ${prod.quantitySold}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = Money.ofMinor(prod.totalRevenueMinor).formatArabic(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FinancialPositive
                                                )
                                                Text(
                                                    text = "ربح: ${Money.ofMinor(prod.grossProfitMinor).formatArabic()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = FinancialInfo
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Drill-down Sales Transactions (Report -> Summary -> Transaction -> Customer/Product)
                item {
                    Text(
                        text = "فواتير المبيعات في الفترة (اضغط للتفاصيل)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(salesSec.filteredSales.take(25), key = { it.localId }) { sale ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { drillDownSale = sale },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "فاتورة بيع #${sale.invoiceNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()} • الحاله: ${FriendlyTerminologyMapper.toFriendlyStatusArabic(sale.status)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = Money.ofMinor(sale.totalAmountMinor).formatArabic(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = FinancialPositive
                            )
                        }
                    }
                }
            }

            1 -> {
                // 2. الأرباح (Profits)
                val profSec = bundle.profitsSection
                item {
                    ReportSectionHeader(
                        title = "تقرير الأرباح والنتيجة المالية (${filter.periodPreset.arabicLabel})",
                        subtitle = "الإيرادات، تكلفة البضاعة المباعة (COGS)، مجمل الربح، المصروفات، وصافي النتيجة"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "الإيرادات (Revenue)",
                            value = Money.ofMinor(profSec.revenueMinor).formatArabic(),
                            subtitle = "صافي إيرادات المبيعات",
                            accentColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "تكلفة المبيعات (COGS)",
                            value = Money.ofMinor(profSec.cogsMinor).formatArabic(),
                            subtitle = "محسوبة بالمتوسط المرجح WAC",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "مجمل الربح (Gross Profit)",
                            value = Money.ofMinor(profSec.grossProfitMinor).formatArabic(),
                            subtitle = "إيراد المبيعات ناقص التكلفة",
                            accentColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "المصروفات التشغيلية",
                            value = Money.ofMinor(profSec.expensesMinor).formatArabic(),
                            subtitle = "إيجار، كهرباء، رواتب، نثريات",
                            accentColor = FinancialNegative,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (profSec.netResultMinor >= 0L) {
                                FinancialPositive.copy(alpha = 0.10f)
                            } else {
                                FinancialNegative.copy(alpha = 0.10f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "صافي النتيجة المالية (Net Result)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = Money.ofMinor(profSec.netResultMinor).formatArabic(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (profSec.netResultMinor >= 0L) FinancialPositive else FinancialNegative
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "عمولات المحافظ: ${Money.ofMinor(profSec.walletCommissionsMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "صافي إيراد الصيانة: ${Money.ofMinor(profSec.maintenanceNetRevenueMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // 3. العملاء (Customers)
                val custSec = bundle.customersSection
                item {
                    ReportSectionHeader(
                        title = "تقرير ذمم وتحصيلات العملاء",
                        subtitle = "المستحقات، التحصيلات خلال الفترة، والأرصدة المفتوحة لكل عميل"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "إجمالي المستحقات",
                            value = Money.ofMinor(custSec.totalReceivablesMinor).formatArabic(),
                            subtitle = "${custSec.customersWithDebtCount} عميل عليه رصيد",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "التحصيلات في الفترة",
                            value = Money.ofMinor(custSec.periodCollectionsMinor).formatArabic(),
                            subtitle = "إجمالي المسدد للخزينة والمحافظ",
                            accentColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                items(custSec.outstandingBalances, key = { it.customerId }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestSelectCustomerProfile(row.customerId)
                                onNavigateToDestination(MekkaDestination.CUSTOMERS)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.fullName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "هاتف: ${row.phone} • مبيعات: ${Money.ofMinor(row.totalSalesMinor).formatArabic()} • مسدد: ${Money.ofMinor(row.totalPaymentsMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.ofMinor(row.outstandingBalanceMinor).formatArabic(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.outstandingBalanceMinor > 0L) FinancialNegative else FinancialPositive
                                )
                                Text(
                                    text = if (row.outstandingBalanceMinor > 0L) "عليه مستحقات" else "خالص",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                // 4. الموردون (Suppliers)
                val supSec = bundle.suppliersSection
                item {
                    ReportSectionHeader(
                        title = "تقرير الموردين والمشتريات",
                        subtitle = "مستحقات الموردين، فواتير الشراء، والمدفوعات المسددة"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "مستحقات الموردين",
                            value = Money.ofMinor(supSec.totalPayablesMinor).formatArabic(),
                            subtitle = "إجمالي الذمم الدائنة للموردين",
                            accentColor = FinancialNegative,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "مشتريات الفترة",
                            value = Money.ofMinor(supSec.periodPurchasesMinor).formatArabic(),
                            subtitle = "المدفوعات: ${Money.ofMinor(supSec.periodPaymentsMinor).formatArabic()}",
                            accentColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                items(supSec.supplierRows, key = { it.supplierId }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestSelectSupplierProfile(row.supplierId)
                                onNavigateToDestination(MekkaDestination.SUPPLIERS)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.companyName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "هاتف: ${row.phone} • المشتريات: ${Money.ofMinor(row.totalPurchasesMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.ofMinor(row.outstandingPayableMinor).formatArabic(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.outstandingPayableMinor > 0L) FinancialWarning else FinancialPositive
                                )
                                Text(
                                    text = if (row.outstandingPayableMinor > 0L) "مستحق للمورد" else "مسدد بالكامل",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                // 5. المخزون (Inventory)
                val invSec = bundle.inventorySection
                item {
                    ReportSectionHeader(
                        title = "تقرير المخزون وتقييم متوسط التكلفة المرجح (WAC)",
                        subtitle = "قيمة المخزون، الأصناف المنخفضة، الأصناف الراكدة، والأكثر مبيعاً"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "قيمة المخزون (WAC)",
                            value = Money.ofMinor(invSec.totalStockValueMinor).formatArabic(),
                            subtitle = "تقييم فعلي من حركات المخزن",
                            accentColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "نواقص المخزون",
                            value = "${invSec.lowStockItems.size} صنف",
                            subtitle = "نافد: ${invSec.outOfStockItems.size} • راكد: ${invSec.deadStockItems.size}",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Text(
                        text = "جدول تقييم الأصناف والمتوسط المرجح للتكلفة (WAC)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(invSec.wacValuationList, key = { it.productId }) { prod ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestSelectProductDetail(prod.productId)
                                onNavigateToDestination(MekkaDestination.PRODUCTS)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prod.nameArabic,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SKU: ${prod.sku} • الرصيد: ${prod.derivedStockQty} • WAC: ${Money.ofMinor(prod.weightedAvgCostMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.ofMinor(prod.stockValueMinor).formatArabic(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FinancialInfo
                                )
                                val statusText = when {
                                    prod.derivedStockQty <= 0 -> "نافد"
                                    prod.derivedStockQty <= prod.reorderThresholdQty -> "مخزون منخفض"
                                    else -> "متوفر"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (prod.derivedStockQty <= prod.reorderThresholdQty) FinancialWarning else FinancialPositive
                                )
                            }
                        }
                    }
                }
            }

            5 -> {
                // 6. المحافظ (Wallets)
                val walSec = bundle.walletsSection
                item {
                    ReportSectionHeader(
                        title = "تقرير المحافظ الإلكترونية والعمولات",
                        subtitle = "الإيداعات، السحوبات، رسوم المزودين، عمولات المتجر، وأرصدة المحافظ"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "الإيداعات / الوارد",
                            value = Money.ofMinor(walSec.totalDepositsMinor).formatArabic(),
                            subtitle = "إجمالي التدفقات الواردة",
                            accentColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "السحوبات / الصادر",
                            value = Money.ofMinor(walSec.totalWithdrawalsMinor).formatArabic(),
                            subtitle = "إجمالي التدفقات الصادرة",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "رسوم المزودين (Fees)",
                            value = Money.ofMinor(walSec.totalFeesMinor).formatArabic(),
                            subtitle = "رسوم التحويل المخصومة",
                            accentColor = FinancialNegative,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "عمولات المتجر",
                            value = Money.ofMinor(walSec.totalCommissionsMinor).formatArabic(),
                            subtitle = "أرباح خدمات المحافظ",
                            accentColor = MekkaGoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                items(walSec.providerBalances, key = { it.walletId }) { wallet ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDestination(MekkaDestination.WALLETS) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${wallet.walletNameArabic} (${wallet.providerNameArabic})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "رقم المحفظة: ${wallet.walletNumber} • رسوم مسجلة: ${Money.ofMinor(wallet.totalFeesMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = Money.ofMinor(wallet.derivedBalanceMinor).formatArabic(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = FinancialInfo
                            )
                        }
                    }
                }
            }

            6 -> {
                // 7. الصيانة (Maintenance)
                val maintSec = bundle.maintenanceSection
                item {
                    ReportSectionHeader(
                        title = "تقرير مركز الصيانة وقطع الغيار",
                        subtitle = "التذاكر المفتوحة، الجاهزة للتسليم، المكتملة، الإيرادات، واستهلاك قطع الغيار"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "تذاكر مفتوحة",
                            value = "${maintSec.openTicketsCount}",
                            subtitle = "جاهزة للتسليم: ${maintSec.readyTicketsCount}",
                            accentColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "تذاكر مكتملة",
                            value = "${maintSec.completedTicketsCount}",
                            subtitle = "تم التسليم والتحصيل",
                            accentColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryCard(
                            title = "إيرادات الصيانة",
                            value = Money.ofMinor(maintSec.totalMaintenanceRevenueMinor).formatArabic(),
                            subtitle = "الإيراد المحصل للتذاكر",
                            accentColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                        ReportSummaryCard(
                            title = "استهلاك قطع الغيار",
                            value = Money.ofMinor(maintSec.totalPartsUsageCostMinor).formatArabic(),
                            subtitle = "عدد القطع: ${maintSec.totalPartsUsedQty} قطعة",
                            accentColor = MekkaGoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onNavigateToDestination(MekkaDestination.MAINTENANCE) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فتح مركز الصيانة ومراجعة التذاكر")
                    }
                }
            }

            7 -> { // 8. المقارنات الزمنية الذكية (Requirement P2-N)
                item {
                    ReportSectionHeader(
                        title = "المقارنات الزمنية وتحليل الفروقات (Period Comparisons)",
                        subtitle = "مقارنة اليوم بالأمس، الأسبوع الحالي بالسابق، وهذا الشهر بالشهر السابق"
                    )
                }

                val comparisonGroups = listOf(
                    periodComparisons.todayVsYesterday,
                    periodComparisons.thisWeekVsLastWeek,
                    periodComparisons.thisMonthVsLastMonth
                )

                items(comparisonGroups) { grp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = grp.periodLabelArabic,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                MekkaStatusBadge(
                                    text = if (grp.hasSufficientData) "مكتمل البيانات" else "بيانات غير كافية",
                                    color = if (grp.hasSufficientData) MekkaEmeraldGreen else MekkaAmberWarning
                                )
                            }
                            Text(
                                text = grp.summaryArabic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            val metrics = listOf(grp.sales, grp.grossProfit, grp.expenses, grp.transactionCount)
                            metrics.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = m.labelArabic, style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (m.isPositiveTrend) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = if (m.isPositiveTrend) MekkaEmeraldGreen else MekkaCrimsonError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        val pct = m.percentDiff
                                        val pctText = if (pct != null) (if (pct >= 0) "+$pct%" else "$pct%") else "غير متاح"
                                        Text(
                                            text = pctText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (m.isPositiveTrend) MekkaEmeraldGreen else MekkaCrimsonError
                                        )
                                        Text(
                                            text = "(${Money.ofMinor(m.absoluteDiff).formatArabic()})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            8 -> { // 9. بطاقة صحة الأعمال (Requirement P2-O)
                item {
                    ReportSectionHeader(
                        title = "بطاقة صحة الأعمال الداخلية (Business Scorecard)",
                        subtitle = "مؤشرات تحليلية مفسرة لقياس أداء واستقرار المحل (درجة إجمالية: ${businessScorecard.overallScore}/100)"
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("التقييم التشغيلي والمالي الشامل", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${businessScorecard.overallScore} / 100",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (businessScorecard.overallScore >= 80) MekkaEmeraldGreen else if (businessScorecard.overallScore >= 60) MekkaGoldAccent else MekkaAmberWarning
                            )
                            MekkaStatusBadge(
                                text = businessScorecard.overallRatingArabic,
                                color = if (businessScorecard.overallScore >= 80) MekkaEmeraldGreen else MekkaAmberWarning
                            )
                        }
                    }
                }

                items(businessScorecard.pillars, key = { it.key }) { pillar ->
                    val pColor = when (pillar.colorCode) {
                        "GREEN" -> MekkaEmeraldGreen
                        "AMBER" -> MekkaAmberWarning
                        else -> MekkaCrimsonError
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, pColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = pillar.nameArabic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                MekkaStatusBadge(text = "${pillar.statusArabic} (${pillar.score}/100)", color = pColor)
                            }
                            Text(text = pillar.explanationArabic, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "المؤشر القياسي: ${pillar.keyMetric}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            9 -> { // 10. التوقعات الذكية وحركة الطلب (Requirements P2-B & P2-C)
                item {
                    ReportSectionHeader(
                        title = "مركز التوقعات الذكية المحافظة (Forecasting)",
                        subtitle = "توقعات أسبوعية تقديرية مبنية على المتوسطات الحقيقية دون تخمين عشوائي"
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("التوقعات المالية التقديرية للأسبوع القادم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                MekkaStatusBadge(text = "تقديري • Estimated", color = MekkaGoldAccent)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportSummaryCard(
                                    title = "المبيعات المتوقعة",
                                    value = Money.ofMinor(businessForecasting.estimatedWeeklySalesMinor).formatArabic(),
                                    subtitle = "بناءً على متوسط الأسبوع",
                                    accentColor = MekkaEmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportSummaryCard(
                                    title = "المصروفات المتوقعة",
                                    value = Money.ofMinor(businessForecasting.estimatedWeeklyExpensesMinor).formatArabic(),
                                    subtitle = "التشغيل والرواتب التقديرية",
                                    accentColor = MekkaCrimsonError,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportSummaryCard(
                                    title = "احتياج النقدية بالخزينة",
                                    value = Money.ofMinor(businessForecasting.estimatedWeeklyNetCashRequirementMinor).formatArabic(),
                                    subtitle = "لتغطية الفارق التشغيلي",
                                    accentColor = MekkaBlueInfo,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportSummaryCard(
                                    title = "حجم تداول المحافظ",
                                    value = Money.ofMinor(businessForecasting.estimatedWeeklyWalletVolumeMinor).formatArabic(),
                                    subtitle = "حركات الإيداع والسحب",
                                    accentColor = MekkaGoldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "مستوى دقة التوقع: ${businessForecasting.salesForecastConfidenceArabic} • المصدر: ${businessForecasting.sourcePeriodArabic}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "طريقة الحساب: ${businessForecasting.calculationMethodArabic}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "توقعات الطلب والمخزون المتبقي للأصناف (${demandForecasts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(demandForecasts.take(15), key = { it.productId }) { df ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(df.productNameArabic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                 val daysRemainingStr = if (df.estimatedDaysRemaining != null) "≈ ${df.estimatedDaysRemaining} يوم" else "غير متاح"
                                Text(
                                    text = "الرصيد: ${df.currentStockQty} • معدل البيع: ${"%.1f".format(df.avgDailyVelocity)}/يوم • يكفي: $daysRemainingStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (df.suggestedReorderQty > 0) {
                                MekkaStatusBadge(text = "طلب ${df.suggestedReorderQty} قطعة", color = MekkaAmberWarning)
                            }
                        }
                    }
                }
            }

            10 -> { // 11. الملخص اليومي للأعمال (Requirement P2-M)
                item {
                    ReportSectionHeader(
                        title = "ملخص اليوم الشامل (Daily Business Summary)",
                        subtitle = "عرض سريع وموثق لجميع أرقام ونشاط اليوم التشغيلي"
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("reports_daily_business_summary_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MekkaGoldAccent.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MEKKA PHONE — DAILY SUMMARY",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaGoldAccent
                                )
                                Text(
                                    text = dailyBusinessSummary.dateFormattedArabic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportSummaryCard(
                                    title = "إجمالي المبيعات",
                                    value = Money.ofMinor(dailyBusinessSummary.salesMinor).formatArabic(),
                                    subtitle = "نقداً وآجل ومحافظ",
                                    accentColor = MekkaEmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportSummaryCard(
                                    title = "مجمل الربح",
                                    value = Money.ofMinor(dailyBusinessSummary.grossProfitMinor).formatArabic(),
                                    subtitle = "فارق البيع عن التكلفة",
                                    accentColor = MekkaGoldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportSummaryCard(
                                    title = "نقدية الخزينة الحالية",
                                    value = Money.ofMinor(dailyBusinessSummary.cashOnHandMinor).formatArabic(),
                                    subtitle = "الرصيد الفعلي بالدرج",
                                    accentColor = MekkaEmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportSummaryCard(
                                    title = "أرصدة المحافظ",
                                    value = Money.ofMinor(dailyBusinessSummary.walletBalancesTotalMinor).formatArabic(),
                                    subtitle = "فودافون، أورنج، إلخ",
                                    accentColor = MekkaBlueInfo,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportSummaryCard(
                                    title = "إجمالي المصروفات",
                                    value = Money.ofMinor(dailyBusinessSummary.expensesMinor).formatArabic(),
                                    subtitle = "المصاريف التشغيلية",
                                    accentColor = MekkaCrimsonError,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportSummaryCard(
                                    title = "العمليات والتنبيهات",
                                    value = "${dailyBusinessSummary.transactionsCount} عملية",
                                    subtitle = "${dailyBusinessSummary.activeAlertsCount} تنبيه نشط",
                                    accentColor = MekkaGoldPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedButton(
                                onClick = { onNavigateToDestination(MekkaDestination.CONTROL_CENTER) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("الانتقال لمركز التحكم الرئيسي")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    // Entity Filter Bottom Sheet (Rule 2: Product, Customer, Provider filters)
    if (showEntityFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEntityFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تصفية التقارير حسب العميل أو الصنف أو مزود المحفظة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text("تصفية حسب مزود المحفظة:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter.selectedProviderCode == null,
                        onClick = { viewModel.updateReportsFilter { it.copy(selectedProviderCode = null) } },
                        label = { Text("الكل") }
                    )
                    providers.forEach { prv ->
                        FilterChip(
                            selected = filter.selectedProviderCode == prv.providerCode,
                            onClick = {
                                viewModel.updateReportsFilter { it.copy(selectedProviderCode = prv.providerCode) }
                            },
                            label = { Text(prv.nameArabic) }
                        )
                    }
                }

                Text("تصفية حسب الصنف:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter.selectedProductId == null,
                        onClick = { viewModel.updateReportsFilter { it.copy(selectedProductId = null) } },
                        label = { Text("كل الأصناف") }
                    )
                    products.take(15).forEach { prod ->
                        FilterChip(
                            selected = filter.selectedProductId == prod.productId,
                            onClick = {
                                viewModel.updateReportsFilter { it.copy(selectedProductId = prod.productId) }
                            },
                            label = { Text(prod.nameArabic, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Text("تصفية حسب العميل:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter.selectedCustomerId == null,
                        onClick = { viewModel.updateReportsFilter { it.copy(selectedCustomerId = null) } },
                        label = { Text("كل العملاء") }
                    )
                    customers.take(15).forEach { cust ->
                        FilterChip(
                            selected = filter.selectedCustomerId == cust.localId,
                            onClick = {
                                viewModel.updateReportsFilter { it.copy(selectedCustomerId = cust.localId) }
                            },
                            label = { Text(cust.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Button(
                    onClick = { showEntityFilterSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تطبيق الفلاتر")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Drill-down sheet for a Sale Transaction (Report -> Summary -> Transaction -> Customer/Product)
    drillDownSale?.let { sale ->
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.forLanguageTag("ar-EG")) }
        ModalBottomSheet(
            onDismissRequest = { drillDownSale = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تفاصيل فاتورة البيع #${sale.invoiceNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "التوقيت: ${dateFormat.format(Date(sale.saleTimestamp))} • الحالة: ${FriendlyTerminologyMapper.toFriendlyStatusArabic(sale.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("إجمالي الفاتورة:")
                    Text(Money.ofMinor(sale.totalAmountMinor).formatArabic(), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("الخصم الممنوح:")
                    Text(Money.ofMinor(sale.discountAmountMinor).formatArabic())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("المبلغ المحصل:")
                    Text(Money.ofMinor(sale.paidAmountMinor).formatArabic(), color = FinancialPositive)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تكلفة المبيعات (COGS):")
                    Text(Money.ofMinor(sale.totalCogsMinor).formatArabic())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("مجمل ربح الفاتورة:")
                    Text(
                        Money.ofMinor(sale.grossProfitMinor).formatArabic(),
                        fontWeight = FontWeight.Bold,
                        color = FinancialInfo
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sale.customerId != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestSelectCustomerProfile(sale.customerId)
                                drillDownSale = null
                                onNavigateToDestination(MekkaDestination.CUSTOMERS)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ملف العميل")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            drillDownSale = null
                            onNavigateToDestination(MekkaDestination.LEDGER)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("القيد بدفتر الأستاذ")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ReportSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildShareableReportSummary(
    bundle: ReportsCenterBundle,
    shopNameArabic: String
): String {
    val s = bundle.salesSection
    val p = bundle.profitsSection
    val c = bundle.customersSection
    val sup = bundle.suppliersSection
    val inv = bundle.inventorySection
    val w = bundle.walletsSection
    val m = bundle.maintenanceSection
    return buildString {
        appendLine("تقرير $shopNameArabic — (${bundle.filter.periodPreset.arabicLabel})")
        appendLine("==============================")
        appendLine("المبيعات: ${Money.ofMinor(s.periodSalesMinor).formatArabic()} (${s.periodInvoiceCount} فاتورة)")
        appendLine("المرتجعات: ${Money.ofMinor(s.periodReturnsMinor).formatArabic()} | الخصومات: ${Money.ofMinor(s.periodDiscountsMinor).formatArabic()}")
        appendLine("مجمل الربح: ${Money.ofMinor(p.grossProfitMinor).formatArabic()} | المصروفات: ${Money.ofMinor(p.expensesMinor).formatArabic()}")
        appendLine("صافي النتيجة: ${Money.ofMinor(p.netResultMinor).formatArabic()}")
        appendLine("مستحقات العملاء: ${Money.ofMinor(c.totalReceivablesMinor).formatArabic()} | مستحقات الموردين: ${Money.ofMinor(sup.totalPayablesMinor).formatArabic()}")
        appendLine("قيمة المخزون (WAC): ${Money.ofMinor(inv.totalStockValueMinor).formatArabic()} | أرصدة المحافظ: ${Money.ofMinor(w.totalProviderBalancesMinor).formatArabic()}")
        appendLine("تذاكر الصيانة المفتوحة: ${m.openTicketsCount} | إيراد الصيانة: ${Money.ofMinor(m.totalMaintenanceRevenueMinor).formatArabic()}")
    }
}

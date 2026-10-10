package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.entities.FinancialTransactionType
import com.example.financial.Money
import com.example.navigation.MekkaDestination
import com.example.repository.SystemAlertItem
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaBlueInfo
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldDark
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaEmeraldLight
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.viewmodel.MekkaPhoneViewModel

/**
 * MEKKA PHONE V3 — Control Center Dashboard (مركز تحكم مكة فون)
 * Primary purpose: «ماذا أحتاج أن أفعل الآن؟»
 * - Compact operational header + unified 9-domain global search
 * - Primary CTA: بيع + Secondary quick actions: إيداع، سحب، محفظة، صيانة، جرد
 * - Actionable centralized alerts section (every alert navigates directly to the target action)
 * - Concise 6-metric Daily Summary (مبيعات اليوم، صافي النقدية، أرصدة المحافظ، المبالغ الآجلة، المصروفات، مؤشر الربح اليومي)
 * - Zero decorative hero banner clutter; 100% live Room Flows.
 */
@Composable
fun ControlCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit
) {
    val kpis by viewModel.dashboardKpis.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val alerts by viewModel.systemAlerts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.globalSearchResults.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val activeUserId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val pendingOps by viewModel.pendingOperationsBundle.collectAsStateWithLifecycle()
    val offlineCenter by viewModel.offlineControlCenterBundle.collectAsStateWithLifecycle()
    val monthlyReport by viewModel.monthlyReport.collectAsStateWithLifecycle()
    val cashSessions by viewModel.cashSessions.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val periodComparisons by viewModel.periodComparisons.collectAsStateWithLifecycle()
    val businessForecasting by viewModel.businessForecasting.collectAsStateWithLifecycle()
    val businessScorecard by viewModel.businessScorecard.collectAsStateWithLifecycle()
    val integrityIssues by viewModel.integrityIssues.collectAsStateWithLifecycle()
    val reportsBundle by viewModel.reportsCenterBundle.collectAsStateWithLifecycle()

    val currentUser = users.firstOrNull { it.localId == activeUserId }
    val dayStartMillis = remember {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val todayPurchasesMinor = remember(purchases, dayStartMillis) {
        purchases.filter { it.purchaseTimestamp >= dayStartMillis }
            .sumOf { it.totalAmountMinor }
    }
    val todayCollectionsMinor = remember(ledgerTransactions, dayStartMillis) {
        ledgerTransactions.filter {
            it.transactionTimestamp >= dayStartMillis &&
                it.status == com.example.database.entities.LedgerTransactionStatus.POSTED &&
                it.relatedEntityType == "CUSTOMER_COLLECTION"
        }.sumOf { it.amountMinor }
    }
    val todayOperationsCount = remember(ledgerTransactions, dayStartMillis) {
        ledgerTransactions.count {
            it.transactionTimestamp >= dayStartMillis &&
                it.status == com.example.database.entities.LedgerTransactionStatus.POSTED
        }
    }
    val activeShift = remember(cashSessions) {
        cashSessions.firstOrNull { it.status == "OPEN" }
    }

    // Quick Cash Deposit / Withdrawal Dialog state (for 1-tap إيداع / سحب from Dashboard)
    var quickCashDialogMode by rememberSaveable { mutableStateOf<String?>(null) } // "DEPOSIT" or "WITHDRAWAL"
    var quickCashAmount by rememberSaveable { mutableStateOf("") }
    var quickCashReason by rememberSaveable { mutableStateOf("") }
    var quickCashError by rememberSaveable { mutableStateOf<String?>(null) }

    if (quickCashDialogMode != null) {
        val isDeposit = quickCashDialogMode == "DEPOSIT"
        AlertDialog(
            onDismissRequest = {
                quickCashDialogMode = null
                quickCashError = null
            },
            title = {
                Text(
                    text = if (isDeposit) "إيداع نقدي سريع بالخزينة" else "سحب نقدي سريع من الخزينة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الرصيد النقدي الحالي: ${Money.ofMinor(kpis.cashPositionMinor).formatArabic()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (quickCashError != null) {
                        Surface(
                            color = MekkaCrimsonError.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = quickCashError.orEmpty(),
                                color = MekkaCrimsonError,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = quickCashAmount,
                        onValueChange = {
                            quickCashAmount = it
                            quickCashError = null
                        },
                        label = { Text("المبلغ (ج.م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_quick_cash_amount")
                    )
                    OutlinedTextField(
                        value = quickCashReason,
                        onValueChange = {
                            quickCashReason = it
                            quickCashError = null
                        },
                        label = { Text("البيان / السبب") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_quick_cash_reason")
                    )
                    TextButton(
                        onClick = {
                            quickCashDialogMode = null
                            onNavigateToRoute(MekkaDestination.CASH_DRAWER.route)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("فتح شاشة الخزينة الكاملة")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedMinor = runCatching { Money.parseMajorString(quickCashAmount).amountMinor }.getOrNull()
                        if (parsedMinor == null || parsedMinor <= 0L) {
                            quickCashError = "أدخل مبلغًا صحيحًا"
                            return@Button
                        }
                        if (!isDeposit && parsedMinor > kpis.cashPositionMinor) {
                            quickCashError = "الرصيد غير كافٍ في الخزينة"
                            return@Button
                        }
                        if (quickCashReason.isBlank()) {
                            quickCashError = "يرجى كتابة سبب العملية"
                            return@Button
                        }
                        viewModel.executeCashOperation(
                            operationType = if (isDeposit) FinancialTransactionType.CASH_IN else FinancialTransactionType.CASH_OUT,
                            amountMajorStr = quickCashAmount,
                            reasonArabic = quickCashReason.trim()
                        )
                        quickCashAmount = ""
                        quickCashReason = ""
                        quickCashError = null
                        quickCashDialogMode = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeposit) MekkaEmeraldGreen else MekkaCrimsonError
                    ),
                    modifier = Modifier.testTag("dashboard_quick_cash_confirm")
                ) {
                    Text(if (isDeposit) "تأكيد الإيداع" else "تأكيد السحب")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        quickCashDialogMode = null
                        quickCashError = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("control_center_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Operational Status Header & Global Search (Rule 8 & Rule 12)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shopConfig.shopNameArabic.ifBlank { "مركز تحكم مكة فون" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ماذا أحتاج أن أفعل الآن؟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MekkaEmeraldGreen.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "المستخدم الحالي",
                                    tint = MekkaEmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = currentUser?.fullNameArabic ?: "مدير النظام",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }

                    // Global Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateGlobalSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_search_input"),
                        placeholder = {
                            Text(
                                text = "بحث شامل: عميل، مورد، منتج، SKU، IMEI، فاتورة، صيانة، قيد...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث شامل",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateGlobalSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "مسح البحث"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    if (searchQuery.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (searchResults.isEmpty()) {
                            Text(
                                text = "لا توجد نتائج مطابقة لـ «$searchQuery»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Text(
                                text = "نتائج البحث (${searchResults.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            searchResults.take(12).forEach { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.openGlobalSearchResult(item)
                                            viewModel.updateGlobalSearchQuery("")
                                            onNavigateToRoute(item.routeTarget)
                                        }
                                        .testTag("global_search_result_${item.referenceCode}"),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            MekkaStatusBadge(
                                                text = item.categoryArabic,
                                                color = MekkaEmeraldGreen
                                            )
                                        }
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "المرجع: ${item.referenceCode}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "الحالة: ${item.statusArabic.ifBlank { "نشط" }} • التاريخ: ${item.dateFormatted.ifBlank { "-" }}",
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
            }
        }

        // 2. Dashboard Quick Actions (Rule 9: Primary بيع + Secondary إيداع، سحب، محفظة، صيانة، جرد)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "الإجراءات السريعة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // MEKKA PHONE V4.1: Intelligent Multi-Modal Transaction Composer CTA
                    Button(
                        onClick = { viewModel.openTransactionComposer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("quick_action_composer_launch"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCartCheckout,
                                    contentDescription = "تنفيذ عملية ذكية",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "تنفيذ عملية ذكية (Quick Transaction)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "تحدث 🎙 • اكتب نصاً 💬 • مسح باركود ▣ • إدخال ومراجعة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "فتح مشغل العمليات",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // MEKKA PHONE V4.1 Part 3: Mekka Intelligence AI Assistant CTA
                    Button(
                        onClick = { viewModel.openAiAssistant() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("quick_action_ai_assistant_launch"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "مساعد مكة فون",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "مساعد مكة فون الذكي (AI Assistant)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "اسأل عن المبيعات والأرباح والمديونيات ونواقص المخزون",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "فتح المساعد الذكي",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Primary Action: بيع (Full-width high-visibility Emerald CTA)
                    Button(
                        onClick = { onNavigateToRoute(MekkaDestination.SALES.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("quick_action_sale"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MekkaEmeraldGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = "بيع",
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "بيع جديد (نقطة البيع POS)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "بحث سريع • باركود / IMEI • نقدي أو آجل",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "فتح نقطة البيع"
                            )
                        }
                    }

                    // Secondary Quick Actions Row: إيداع • سحب • محفظة • صيانة • جرد
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardQuickActionChip(
                            title = "إيداع",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            tint = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_deposit",
                            onClick = {
                                viewModel.requestWalletQuickOperation(com.example.financial.WalletUserOperationType.DEPOSIT)
                                onNavigateToRoute(MekkaDestination.WALLETS.route)
                            }
                        )
                        DashboardQuickActionChip(
                            title = "سحب",
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            tint = MekkaCrimsonError,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_withdraw",
                            onClick = {
                                viewModel.requestWalletQuickOperation(com.example.financial.WalletUserOperationType.WITHDRAWAL)
                                onNavigateToRoute(MekkaDestination.WALLETS.route)
                            }
                        )
                        DashboardQuickActionChip(
                            title = "محفظة",
                            icon = Icons.Default.AccountBalanceWallet,
                            tint = MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_wallet",
                            onClick = {
                                viewModel.requestWalletQuickOperation(com.example.financial.WalletUserOperationType.TRANSFER)
                                onNavigateToRoute(MekkaDestination.WALLETS.route)
                            }
                        )
                        DashboardQuickActionChip(
                            title = "صيانة",
                            icon = Icons.Default.Build,
                            tint = MekkaGoldAccent,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_maintenance",
                            onClick = { onNavigateToRoute(MekkaDestination.MAINTENANCE.route) }
                        )
                        DashboardQuickActionChip(
                            title = "جرد",
                            icon = Icons.Default.Inventory2,
                            tint = MekkaEmeraldDark,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_inventory",
                            onClick = {
                                viewModel.requestInventoryCountingMode()
                                onNavigateToRoute(MekkaDestination.PRODUCTS.route)
                            }
                        )
                    }

                    // Row 2 of Quick Actions (شراء ومسودة • تحصيل عميل • مصروف • تسوية جرد • أتمتة)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardQuickActionChip(
                            title = "شراء / مسودة",
                            icon = Icons.Default.ShoppingCartCheckout,
                            tint = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_purchase",
                            onClick = { onNavigateToRoute(MekkaDestination.PURCHASES.route) }
                        )
                        DashboardQuickActionChip(
                            title = "تحصيل عميل",
                            icon = Icons.Default.People,
                            tint = MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_customer_payment",
                            onClick = { onNavigateToRoute(MekkaDestination.CUSTOMERS.route) }
                        )
                        DashboardQuickActionChip(
                            title = "تسجيل مصروف",
                            icon = Icons.Default.RequestQuote,
                            tint = MekkaCrimsonError,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_expense",
                            onClick = { onNavigateToRoute(MekkaDestination.EXPENSES.route) }
                        )
                        DashboardQuickActionChip(
                            title = "تسوية جرد",
                            icon = Icons.Default.Inventory2,
                            tint = MekkaGoldAccent,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_stock_adjustment",
                            onClick = {
                                viewModel.requestInventoryCountingMode()
                                onNavigateToRoute(MekkaDestination.PRODUCTS.route)
                            }
                        )
                        DashboardQuickActionChip(
                            title = "الأتمتة الآمنة",
                            icon = Icons.Default.SmartToy,
                            tint = MekkaGoldAccent,
                            modifier = Modifier.weight(1f),
                            testTag = "quick_action_safe_automation",
                            onClick = { onNavigateToRoute(MekkaDestination.SAFE_AUTOMATION.route) }
                        )
                    }
                }
            }
        }

        // 3. Centralized Actionable Alerts Section (Rule 11: ماذا أحتاج أن أفعل الآن؟)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_alerts_section"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (alerts.isEmpty()) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                                contentDescription = "التنبيهات",
                                tint = if (alerts.isEmpty()) MekkaEmeraldGreen else MekkaAmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "التنبيهات والمهام العاجلة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        MekkaStatusBadge(
                            text = if (alerts.isEmpty()) "لا توجد مهام معلقة" else "${alerts.size} تنبيه نشط",
                            color = if (alerts.isEmpty()) MekkaEmeraldGreen else MekkaAmberWarning
                        )
                    }

                    if (alerts.isEmpty()) {
                        Text(
                            text = "جميع المؤشرات التشغيلية والمخزنية والمالية في حالة منتظمة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        alerts.forEach { alert ->
                            DashboardActionableAlertRow(
                                alert = alert,
                                onClick = { onNavigateToRoute(alert.routeTarget) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Concise Daily Summary (Rule 10: مبيعات اليوم، صافي النقدية، أرصدة المحافظ، المبالغ الآجلة، المصروفات، مؤشر الربح)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_daily_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ملخص اليوم",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { onNavigateToRoute(MekkaDestination.REPORTS.route) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("dashboard_open_reports_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("التقارير التفصيلية", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Row 1: مبيعات اليوم + صافي النقدية
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "مبيعات اليوم",
                            value = Money.ofMinor(kpis.todaySalesMinor).formatArabic(),
                            accentColor = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.SALES.route) }
                        )
                        DailySummaryMetricTile(
                            title = "صافي النقدية",
                            value = Money.ofMinor(kpis.cashPositionMinor).formatArabic(),
                            accentColor = MekkaEmeraldDark,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.CASH_DRAWER.route) }
                        )
                    }

                    // Row 2: أرصدة المحافظ + المبالغ الآجلة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "أرصدة المحافظ",
                            value = Money.ofMinor(kpis.totalWalletsBalanceMinor).formatArabic(),
                            accentColor = MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.WALLETS.route) }
                        )
                        DailySummaryMetricTile(
                            title = "المبالغ الآجلة",
                            value = Money.ofMinor(kpis.customerReceivablesMinor).formatArabic(),
                            accentColor = MekkaAmberWarning,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.CUSTOMERS.route) }
                        )
                    }

                    // Row 3: المصروفات + مؤشر صافي الربح اليومي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "المصروفات",
                            value = Money.ofMinor(kpis.todayExpensesMinor).formatArabic(),
                            accentColor = MekkaCrimsonError,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.EXPENSES.route) }
                        )
                        DailySummaryMetricTile(
                            title = "مؤشر ربح اليوم",
                            value = Money.ofMinor(kpis.todayNetProfitIndicatorMinor).formatArabic(),
                            accentColor = if (kpis.todayNetProfitIndicatorMinor >= 0L) MekkaGoldAccent else MekkaCrimsonError,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.REPORTS.route) }
                        )
                    }

                    // Row 4: مشتريات اليوم + التحصيلات + عدد العمليات (V4 Part 2 Today Completion)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "مشتريات اليوم",
                            value = Money.ofMinor(todayPurchasesMinor).formatArabic(),
                            accentColor = MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.PURCHASES.route) }
                        )
                        DailySummaryMetricTile(
                            title = "تحصيلات اليوم",
                            value = Money.ofMinor(todayCollectionsMinor).formatArabic(),
                            accentColor = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.CUSTOMERS.route) }
                        )
                        DailySummaryMetricTile(
                            title = "عمليات اليوم",
                            value = "$todayOperationsCount عملية",
                            accentColor = MekkaGoldAccent,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.OPERATIONS.route) }
                        )
                    }
                }
            }
        }

        // 5. V4 Part 2: Business & Operations Control Sections
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_business_operations_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مؤشرات الأعمال والعمليات الحية (Business & Operations)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        MekkaStatusBadge(
                            text = offlineCenter.lifecycleState.badgeText,
                            color = if (offlineCenter.lifecycleState == com.example.intelligence.ConnectivitySyncLifecycleState.OFFLINE) MekkaAmberWarning else MekkaEmeraldGreen
                        )
                    }

                    // Business Row 1: Receivables, Payables, Inventory Value (WAC)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "مستحقات الموردين",
                            value = Money.ofMinor(kpis.supplierPayablesMinor).formatArabic(),
                            accentColor = MekkaCrimsonError,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.SUPPLIERS.route) }
                        )
                        DailySummaryMetricTile(
                            title = "قيمة المخزون (WAC)",
                            value = Money.ofMinor(monthlyReport.totalInventoryValuationMinor).formatArabic(),
                            accentColor = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.PRODUCTS.route) }
                        )
                    }

                    // Business Row 2: Low Stock, Maintenance Pending, Pending Operations Center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "نواقص المخزون",
                            value = "${kpis.lowStockProductsCount} صنف",
                            accentColor = if (kpis.lowStockProductsCount > 0) MekkaAmberWarning else MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.PRODUCTS.route) }
                        )
                        DailySummaryMetricTile(
                            title = "صيانة قيد العمل",
                            value = "${kpis.pendingRepairsCount + kpis.readyRepairsCount} تذكرة",
                            accentColor = MekkaGoldAccent,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.MAINTENANCE.route) }
                        )
                        DailySummaryMetricTile(
                            title = "العمليات المعلقة",
                            value = "${pendingOps.totalCount} عنصر",
                            accentColor = if (pendingOps.totalCount > 0) MekkaCrimsonError else MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.PENDING_OPERATIONS.route) }
                        )
                    }

                    // Operations Status Row: Shift Status, Sync Problems, SMS Reviews
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "حالة الوردية",
                            value = if (activeShift != null) "مفتوحة (#${activeShift.localId.takeLast(4)})" else "لا توجد وردية مفتوحة",
                            accentColor = if (activeShift != null) MekkaEmeraldGreen else MekkaAmberWarning,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.CASH_DRAWER.route) }
                        )
                        DailySummaryMetricTile(
                            title = "مشكلات المزامنة",
                            value = "${kpis.failedSyncCount + kpis.unresolvedConflictsCount}",
                            accentColor = if (kpis.failedSyncCount + kpis.unresolvedConflictsCount > 0) MekkaCrimsonError else MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.SYNC.route) }
                        )
                        DailySummaryMetricTile(
                            title = "مراجعات SMS",
                            value = "${kpis.pendingSmsCount} رسالة",
                            accentColor = if (kpis.pendingSmsCount > 0) MekkaAmberWarning else MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.SMS_INTELLIGENCE.route) }
                        )
                    }
                }
            }
        }

        // 6. P2-A: PERFORMANCE — الأداء والاتجاهات
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_performance_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MekkaEmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "مؤشرات الأداء والاتجاهات (Performance Trends)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        MekkaStatusBadge(
                            text = "صحة الأعمال: ${businessScorecard.overallScore}/100",
                            color = if (businessScorecard.overallScore >= 80) MekkaEmeraldGreen else MekkaAmberWarning
                        )
                    }

                    // Sales Trend & Profit Trend Tiles
                    val salesTrend = periodComparisons.thisWeekVsLastWeek.sales
                    val profitTrend = periodComparisons.thisWeekVsLastWeek.grossProfit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToRoute(MekkaDestination.REPORTS.route) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("اتجاه مبيعات الأسبوع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (salesTrend.isPositiveTrend) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = if (salesTrend.isPositiveTrend) MekkaEmeraldGreen else MekkaCrimsonError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${salesTrend.percentDiff?.let { if (it >= 0) "+$it%" else "$it%" } ?: "بيانات أولية"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (salesTrend.isPositiveTrend) MekkaEmeraldGreen else MekkaCrimsonError
                                    )
                                }
                                Text(
                                    text = "فارق: ${Money.ofMinor(salesTrend.absoluteDiff).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToRoute(MekkaDestination.REPORTS.route) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("اتجاه مجمل الربح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (profitTrend.isPositiveTrend) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = if (profitTrend.isPositiveTrend) MekkaGoldAccent else MekkaCrimsonError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${profitTrend.percentDiff?.let { if (it >= 0) "+$it%" else "$it%" } ?: "مستقر"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profitTrend.isPositiveTrend) MekkaGoldAccent else MekkaCrimsonError
                                    )
                                }
                                Text(
                                    text = "فارق: ${Money.ofMinor(profitTrend.absoluteDiff).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Top Products Glance
                    val topProds = reportsBundle.salesSection.topProducts
                    if (topProds.isNotEmpty()) {
                        Text(
                            text = "الأصناف الأكثر طلباً هذا الشهر:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        for ((idx, p) in topProds.take(3).withIndex()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${p.productNameArabic}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${p.quantitySold} قطعة • ${Money.ofMinor(p.totalRevenueMinor).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. P2-B: BUSINESS FORECASTING & SCORECARD GLANCE
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_forecasting_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MekkaGoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "التوقعات الذكية المحافظة (Forecasting)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        MekkaStatusBadge(
                            text = "تقديري • Estimated",
                            color = MekkaGoldAccent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailySummaryMetricTile(
                            title = "توقع مبيعات الأسبوع",
                            value = Money.ofMinor(businessForecasting.estimatedWeeklySalesMinor).formatArabic(),
                            accentColor = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.REPORTS.route) }
                        )
                        DailySummaryMetricTile(
                            title = "احتياج النقدية المتوقع",
                            value = Money.ofMinor(businessForecasting.estimatedWeeklyNetCashRequirementMinor).formatArabic(),
                            accentColor = MekkaBlueInfo,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToRoute(MekkaDestination.CASH_DRAWER.route) }
                        )
                    }

                    Text(
                        text = "مستوى الجودة: ${businessForecasting.salesForecastConfidenceArabic} • المصدر: ${businessForecasting.sourcePeriodArabic}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickActionChip(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardActionableAlertRow(
    alert: SystemAlertItem,
    onClick: () -> Unit
) {
    val color = when (alert.severity) {
        "CRITICAL" -> MekkaCrimsonError
        "WARNING" -> MekkaAmberWarning
        else -> MekkaBlueInfo
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("alert_item_${alert.code}"),
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = alert.titleArabic,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "فتح الإجراء",
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DailySummaryMetricTile(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

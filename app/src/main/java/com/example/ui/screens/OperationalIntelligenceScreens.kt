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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financial.Money
import com.example.intelligence.AuditDiagnosticsDashboardBundle
import com.example.intelligence.CashAndWalletControlCenterBundle
import com.example.intelligence.ConnectivitySyncLifecycleState
import com.example.intelligence.Customer360EventType
import com.example.intelligence.Customer360Snapshot
import com.example.intelligence.Customer360TimelineEvent
import com.example.intelligence.DiagnosticSeverity
import com.example.intelligence.InventoryAnalyticsDashboardBundle
import com.example.intelligence.MaintenanceManagementCenterBundle
import com.example.intelligence.OfflineControlCenterBundle
import com.example.intelligence.PendingCenterActionType
import com.example.intelligence.PendingOperationCenterItem
import com.example.intelligence.PendingOperationType
import com.example.intelligence.PendingOperationsCenterBundle
import com.example.intelligence.ReportExportFormat
import com.example.intelligence.SmartNotificationCenterBundle
import com.example.intelligence.SmartNotificationDomain
import com.example.intelligence.SmartNotificationPriority
import com.example.intelligence.Supplier360EventType
import com.example.intelligence.Supplier360Snapshot
import com.example.intelligence.Supplier360TimelineEvent
import com.example.intelligence.V4ReportType
import com.example.intelligence.AppHealthDimension
import com.example.intelligence.AppHealthSummary
import com.example.intelligence.DataIntegrityAndRepairEngine
import com.example.intelligence.IntegrityIssueReport
import com.example.intelligence.IntegrityRepairProposal
import com.example.intelligence.IntegritySeverity
import com.example.intelligence.SafeAutomationRule
import com.example.navigation.MekkaDestination
import com.example.ui.components.ContextualGuidanceBanner
import com.example.ui.components.MekkaEmptyStateCard
import com.example.ui.components.MekkaSectionHeader
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaGoldPrimary
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MekkaInfoBlue: Color
    get() = FinancialInfo

private fun formatOperationalTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "غير متوفر"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}

// =================================================================================================
// SECTION 1 — PENDING OPERATIONS CENTER SCREEN
// =================================================================================================

@Composable
fun PendingOperationsCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val bundle by viewModel.pendingOperationsBundle.collectAsStateWithLifecycle()
    val offlineBundle by viewModel.offlineControlCenterBundle.collectAsStateWithLifecycle()
    val isGuidanceVisible by viewModel.showGuidanceBanners.collectAsStateWithLifecycle()

    var selectedTypeFilter by rememberSaveable { mutableStateOf<PendingOperationType?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredItems = remember(bundle.items, selectedTypeFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        bundle.items.filter { item ->
            val matchesType = selectedTypeFilter == null || item.type == selectedTypeFilter
            val matchesQuery = q.isEmpty() ||
                item.reference.lowercase().contains(q) ||
                item.reasonArabic.lowercase().contains(q) ||
                item.requiredActionArabic.lowercase().contains(q) ||
                item.userNameArabic.lowercase().contains(q) ||
                item.type.arabicLabel.lowercase().contains(q)
            matchesType && matchesQuery
        }
    }

    val criticalOrSyncProblemsCount = remember(bundle.countsByType) {
        (bundle.countsByType[PendingOperationType.SYNC_FAILED] ?: 0) +
            (bundle.countsByType[PendingOperationType.SYNC_CONFLICTS] ?: 0) +
            (bundle.countsByType[PendingOperationType.BACKUP_PROBLEMS] ?: 0) +
            (bundle.countsByType[PendingOperationType.FAILED_OPERATIONS] ?: 0)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pending_operations_center_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ContextualGuidanceBanner(
                destination = MekkaDestination.PENDING_OPERATIONS,
                visible = isGuidanceVisible,
                onDismissBanner = { viewModel.dismissGuidanceBanners() }
            )
        }

        // Top Bar & Summary KPIs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("pending_ops_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع"
                                )
                            }
                            Column {
                                Text(
                                    text = "مركز العمليات المعلقة والمتابعة",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "رؤية موحدة للمزامنة، رسائل المحافظ، الاعتمادات، الصيانة، والمديونيات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MekkaStatusBadge(
                            text = "${bundle.totalCount} عنصر",
                            color = if (criticalOrSyncProblemsCount > 0) MekkaCrimsonError else if (bundle.totalCount > 0) MekkaAmberWarning else MekkaEmeraldGreen
                        )
                    }

                    // Offline & Sync Quick Control Row inside Pending Operations Center
                    OfflineAndMultiDeviceCenterCard(
                        bundle = offlineBundle,
                        onToggleOnlineMode = { viewModel.setNetworkOnlineMode(it) },
                        onTriggerSync = { viewModel.triggerSyncCycle() }
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_ops_search_input"),
                        placeholder = { Text("بحث بالمرجع، السبب، المستخدم، أو الإجراء المطلوب...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Category Filter Chips across all 11 PendingOperationType values
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTypeFilter == null,
                            onClick = { selectedTypeFilter = null },
                            label = { Text("الكل (${bundle.totalCount})") },
                            modifier = Modifier.testTag("pending_filter_ALL")
                        )
                        PendingOperationType.entries.forEach { opType ->
                            val count = bundle.countsByType[opType] ?: 0
                            FilterChip(
                                selected = selectedTypeFilter == opType,
                                onClick = {
                                    selectedTypeFilter = if (selectedTypeFilter == opType) null else opType
                                },
                                label = { Text("${opType.arabicLabel} ($count)") },
                                modifier = Modifier.testTag("pending_filter_${opType.code}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MekkaGoldPrimary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                MekkaEmptyStateCard(
                    title = "لا توجد عمليات معلقة تحتاج إلى متابعة",
                    description = "جميع عمليات المزامنة، رسائل المحافظ، الاعتمادات، والنسخ الاحتياطي تعمل بصورة منتظمة.",
                    icon = Icons.Default.CheckCircle
                )
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                PendingOperationItemCard(
                    item = item,
                    onExecuteAction = { action ->
                        viewModel.executePendingCenterAction(
                            item = item,
                            action = action,
                            onNavigate = onNavigateToRoute
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PendingOperationItemCard(
    item: PendingOperationCenterItem,
    onExecuteAction: (PendingCenterActionType) -> Unit
) {
    val accentColor = when (item.type) {
        PendingOperationType.SYNC_FAILED,
        PendingOperationType.SYNC_CONFLICTS,
        PendingOperationType.FAILED_OPERATIONS,
        PendingOperationType.BACKUP_PROBLEMS -> MekkaCrimsonError
        PendingOperationType.SMS_REVIEW,
        PendingOperationType.PENDING_APPROVALS,
        PendingOperationType.RESTORE_WARNINGS,
        PendingOperationType.INVENTORY_ALERTS -> MekkaAmberWarning
        PendingOperationType.PENDING_SYNC,
        PendingOperationType.PENDING_MAINTENANCE,
        PendingOperationType.PENDING_CUSTOMER_PAYMENTS -> MekkaInfoBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_op_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Type + Status + Created At
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MekkaStatusBadge(
                        text = item.type.arabicLabel,
                        color = accentColor
                    )
                    MekkaStatusBadge(
                        text = item.statusArabic,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = formatOperationalTimestamp(item.createdAt),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reference & User
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المرجع (Reference): ${item.reference}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LedgerMonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "المستخدم (User): ${item.userNameArabic}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reason & Required Action
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "السبب (Reason): ${item.reasonArabic}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الإجراء المطلوب (Required Action): ${item.requiredActionArabic}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }

            // Actions Bar: Review, Retry, Resolve, Approve, Reject, Open Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.availableActions.forEach { action ->
                    val isPrimary = action == PendingCenterActionType.APPROVE ||
                        action == PendingCenterActionType.RESOLVE ||
                        action == PendingCenterActionType.RETRY
                    if (isPrimary) {
                        Button(
                            onClick = { onExecuteAction(action) },
                            modifier = Modifier.testTag("pending_op_action_${item.id}_${action.code}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(action.arabicLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onExecuteAction(action) },
                            modifier = Modifier.testTag("pending_op_action_${item.id}_${action.code}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(action.arabicLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 2 — SMART NOTIFICATION CENTER PANEL (Inventory, Financial, Maintenance, System)
// =================================================================================================

@Composable
fun SmartNotificationCenterSection(
    bundle: SmartNotificationCenterBundle,
    onOpenSource: (routeTarget: String, entityId: String?, referenceCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    SmartNotificationCenterContent(
        bundle = bundle,
        onOpenAlertSource = onOpenSource,
        modifier = modifier
    )
}

@Composable
fun SmartNotificationCenterContent(
    bundle: SmartNotificationCenterBundle,
    onOpenAlertSource: (routeTarget: String, entityId: String?, referenceCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDomain by rememberSaveable { mutableStateOf<SmartNotificationDomain?>(null) }
    var selectedPriority by rememberSaveable { mutableStateOf<SmartNotificationPriority?>(null) }

    val filteredAlerts = remember(bundle.allItems, selectedDomain, selectedPriority) {
        bundle.allItems.filter { alert ->
            val matchesDomain = selectedDomain == null || alert.domain == selectedDomain
            val matchesPriority = selectedPriority == null || alert.priority == selectedPriority
            matchesDomain && matchesPriority
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_notification_center_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Priority Summary Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedDomain == null && selectedPriority == null,
                onClick = {
                    selectedDomain = null
                    selectedPriority = null
                },
                label = { Text("الكل (${bundle.totalCount})") },
                modifier = Modifier.testTag("smart_alert_filter_all")
            )
            SmartNotificationDomain.entries.forEach { dom ->
                val count = bundle.itemsByDomain[dom]?.size ?: 0
                FilterChip(
                    selected = selectedDomain == dom,
                    onClick = { selectedDomain = if (selectedDomain == dom) null else dom },
                    label = { Text("${dom.arabicLabel} ($count)") },
                    modifier = Modifier.testTag("smart_alert_cat_${dom.code}")
                )
            }
        }

        // Priority Chips (CRITICAL, ACTION_REQUIRED, WARNING, INFO)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmartNotificationPriority.entries.sortedByDescending { it.rank }.forEach { prio ->
                val count = when (prio) {
                    SmartNotificationPriority.CRITICAL -> bundle.criticalCount
                    SmartNotificationPriority.ACTION_REQUIRED -> bundle.actionRequiredCount
                    SmartNotificationPriority.WARNING -> bundle.warningCount
                    SmartNotificationPriority.INFO -> bundle.infoCount
                }
                FilterChip(
                    selected = selectedPriority == prio,
                    onClick = { selectedPriority = if (selectedPriority == prio) null else prio },
                    label = { Text("${prio.arabicLabel} ($count)") },
                    modifier = Modifier.testTag("smart_alert_prio_${prio.name}")
                )
            }
        }

        if (filteredAlerts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Text(
                    text = "لا توجد تنبيهات ذكية نشطة في هذه الفئة حالياً.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAlerts, key = { it.id }) { alert ->
                    val prioColor = when (alert.priority) {
                        SmartNotificationPriority.CRITICAL -> MekkaCrimsonError
                        SmartNotificationPriority.ACTION_REQUIRED -> MekkaAmberWarning
                        SmartNotificationPriority.WARNING -> MekkaGoldPrimary
                        SmartNotificationPriority.INFO -> MekkaInfoBlue
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenAlertSource(alert.routeTarget, alert.entityId, alert.referenceCode)
                            }
                            .testTag("smart_alert_item_${alert.id}"),
                        shape = RoundedCornerShape(12.dp),
                        color = prioColor.copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, prioColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MekkaStatusBadge(text = alert.priority.arabicLabel, color = prioColor)
                                Text(
                                    text = "${alert.domain.arabicLabel} • ${alert.subType.arabicLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = alert.titleArabic,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alert.messageArabic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مرجع: ${alert.referenceCode} • ${formatOperationalTimestamp(alert.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = alert.actionLabelArabic,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = prioColor
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "فتح المصدر",
                                        tint = prioColor,
                                        modifier = Modifier.size(14.dp)
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

// =================================================================================================
// SECTION 4 & 5 — CUSTOMER 360 & SUPPLIER 360 TIMELINE COMPONENTS
// =================================================================================================

@Composable
fun Timeline360InteractiveCard(
    event: Customer360TimelineEvent,
    onOpenEventSource: (Customer360TimelineEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (event.eventType) {
        Customer360EventType.SALE -> MekkaEmeraldGreen
        Customer360EventType.PAYMENT -> MekkaInfoBlue
        Customer360EventType.REFUND -> MekkaCrimsonError
        Customer360EventType.MAINTENANCE -> MekkaGoldPrimary
        Customer360EventType.WALLET_OPERATION -> MekkaAmberWarning
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenEventSource(event) }
            .testTag("timeline_360_event_${event.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = event.dateFormatted,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    MekkaStatusBadge(
                        text = event.statusArabic,
                        color = accentColor
                    )
                }
                Text(
                    text = event.titleArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${event.subtitleArabic} • مرجع: ${event.referenceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                event.amountMinor?.let { amt ->
                    Text(
                        text = Money.ofMinor(amt).formatArabic(),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = LedgerMonoFontFamily),
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "فتح",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "فتح التفاصيل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SupplierTimeline360InteractiveCard(
    event: Supplier360TimelineEvent,
    onOpenEventSource: (Supplier360TimelineEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (event.eventType) {
        Supplier360EventType.PURCHASE -> MekkaEmeraldGreen
        Supplier360EventType.PAYMENT -> MekkaInfoBlue
        Supplier360EventType.RETURN -> MekkaCrimsonError
        Supplier360EventType.PRICE_CHANGE -> MekkaAmberWarning
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenEventSource(event) }
            .testTag("supplier_timeline_360_event_${event.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = event.dateFormatted,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    MekkaStatusBadge(
                        text = event.statusArabic,
                        color = accentColor
                    )
                }
                Text(
                    text = event.titleArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${event.subtitleArabic} • مرجع: ${event.referenceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                event.amountMinor?.let { amt ->
                    Text(
                        text = Money.ofMinor(amt).formatArabic(),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = LedgerMonoFontFamily),
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "فتح",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "فتح التفاصيل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Customer360SummaryBanner(
    snapshot: Customer360Snapshot,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("customer_360_summary_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                Text(
                    text = "ملف العميل الشامل (Customer 360)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                MekkaStatusBadge(
                    text = if (snapshot.creditStanding.isCreditActive) "ائتمان مفعل (${snapshot.creditStanding.defaultCreditDays} يوم)" else "نقدي فقط",
                    color = if (snapshot.creditStanding.isCreditActive) MekkaEmeraldGreen else MekkaAmberWarning
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "حد الائتمان: ${Money.ofMinor(snapshot.creditStanding.creditLimitMinor).formatArabic()}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "المتاح للائتمان: ${if (snapshot.creditStanding.creditLimitMinor > 0L) Money.ofMinor(snapshot.creditStanding.availableCreditMinor).formatArabic() else "مفتوح"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MekkaEmeraldGreen
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "تذاكر الصيانة: ${snapshot.maintenanceTickets.size} • عمليات المحفظة: ${snapshot.walletOperations.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الخط الزمني: ${snapshot.timeline.size} حدث",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (snapshot.notes.isNotBlank()) {
                Text(
                    text = "ملاحظات العميل: ${snapshot.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Supplier360SummaryBanner(
    snapshot: Supplier360Snapshot,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("supplier_360_summary_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                Text(
                    text = "ملف المورد الشامل (Supplier 360)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                MekkaStatusBadge(
                    text = "${snapshot.suppliedProducts.size} صنف مورد",
                    color = MekkaInfoBlue
                )
            }
            val totalReturnsMinor = snapshot.returns.sumOf { it.amountMinor }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مرتجعات المشتريات: ${Money.ofMinor(totalReturnsMinor).formatArabic()} (${snapshot.returns.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "سجل أسعار التوريد: ${snapshot.priceHistory.size} حركة",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// =================================================================================================
// SECTION 7 & 8 — V4 REPORTING ENGINE (16 REPORTS) & REAL FILE EXPORT (CSV / PDF / XLSX)
// =================================================================================================

@Composable
fun V4ReportingEngineSection(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val v4Filter by viewModel.v4ReportFilter.collectAsStateWithLifecycle()
    val v4Document by viewModel.v4TabularReportDocument.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExportedReport.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val walletProviders by viewModel.walletProviders.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (v4Document == null) {
            viewModel.refreshV4TabularReport()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("v4_reporting_engine_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MekkaGoldPrimary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "محرك التقارير المتقدم والتصدير الفعلي (V4 Reporting & Export)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "16 تقريراً متكاملاً من دفتر الأستاذ والمخزون مع تصدير ملفات حقيقية (CSV / PDF / XLSX)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshV4TabularReport() },
                    modifier = Modifier.testTag("v4_refresh_report_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث التقرير")
                }
            }

            // 16 Report Types Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                V4ReportType.entries.forEach { rType ->
                    FilterChip(
                        selected = v4Filter.reportType == rType,
                        onClick = {
                            viewModel.updateV4ReportFilter(v4Filter.copy(reportType = rType))
                        },
                        label = { Text(rType.arabicTitle) },
                        modifier = Modifier.testTag("v4_report_type_${rType.code}")
                    )
                }
            }

            // Advanced Filters Row: User, Category, Payment Method, Provider, Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Filter
                FilterChip(
                    selected = v4Filter.userId != null,
                    onClick = {
                        val nextUser = if (v4Filter.userId == null) users.firstOrNull()?.localId else null
                        viewModel.updateV4ReportFilter(v4Filter.copy(userId = nextUser))
                    },
                    label = {
                        val uName = users.firstOrNull { it.localId == v4Filter.userId }?.roleCode
                        Text(if (uName != null) "المستخدم: $uName" else "كل المستخدمين")
                    },
                    modifier = Modifier.testTag("v4_filter_user_chip")
                )

                // Category Filter
                FilterChip(
                    selected = v4Filter.categoryId != null,
                    onClick = {
                        val nextCat = if (v4Filter.categoryId == null) categories.firstOrNull()?.localId else null
                        viewModel.updateV4ReportFilter(v4Filter.copy(categoryId = nextCat))
                    },
                    label = {
                        val cName = categories.firstOrNull { it.localId == v4Filter.categoryId }?.nameArabic
                        Text(if (cName != null) "التصنيف: $cName" else "كل التصنيفات")
                    },
                    modifier = Modifier.testTag("v4_filter_category_chip")
                )

                // Payment Method Filter
                val methods = listOf(null to "كل طرق الدفع", "CASH" to "نقدي", "WALLET" to "محفظة", "CREDIT" to "آجل")
                val currMethodIdx = methods.indexOfFirst { it.first == v4Filter.paymentMethod }.coerceAtLeast(0)
                FilterChip(
                    selected = v4Filter.paymentMethod != null,
                    onClick = {
                        val nextMethod = methods[(currMethodIdx + 1) % methods.size].first
                        viewModel.updateV4ReportFilter(v4Filter.copy(paymentMethod = nextMethod))
                    },
                    label = { Text(methods[currMethodIdx].second) },
                    modifier = Modifier.testTag("v4_filter_payment_method_chip")
                )

                // Provider Filter
                FilterChip(
                    selected = v4Filter.providerCode != null,
                    onClick = {
                        val nextProv = if (v4Filter.providerCode == null) {
                            walletProviders.firstOrNull()?.providerCode ?: "VODAFONE_CASH"
                        } else null
                        viewModel.updateV4ReportFilter(v4Filter.copy(providerCode = nextProv))
                    },
                    label = {
                        Text(if (v4Filter.providerCode != null) "المزود: ${v4Filter.providerCode}" else "كل المزودين")
                    },
                    modifier = Modifier.testTag("v4_filter_provider_chip")
                )

                // Status Filter
                val statuses = listOf(null to "كل الحالات", "POSTED" to "مرحل POSTED", "COMPLETED" to "مكتمل", "PENDING" to "معلق")
                val currStatusIdx = statuses.indexOfFirst { it.first == v4Filter.status }.coerceAtLeast(0)
                FilterChip(
                    selected = v4Filter.status != null,
                    onClick = {
                        val nextSt = statuses[(currStatusIdx + 1) % statuses.size].first
                        viewModel.updateV4ReportFilter(v4Filter.copy(status = nextSt))
                    },
                    label = { Text(statuses[currStatusIdx].second) },
                    modifier = Modifier.testTag("v4_filter_status_chip")
                )
            }

            // Export Actions Bar: CSV, PDF, Excel (XLSX)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val exportDir = remember(context) {
                    File(context.filesDir, "mekka_exports").apply { mkdirs() }
                }
                ReportExportFormat.entries.forEach { format ->
                    OutlinedButton(
                        onClick = { viewModel.exportCurrentV4Report(format, exportDir) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("v4_export_btn_${format.name}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (format) {
                                ReportExportFormat.CSV -> "تصدير CSV"
                                ReportExportFormat.PDF -> "تصدير PDF"
                                ReportExportFormat.XLSX -> "تصدير XLSX"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Export Verification Result Banner
            lastExport?.let { exp ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("v4_export_result_banner"),
                    shape = RoundedCornerShape(10.dp),
                    color = MekkaEmeraldGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MekkaEmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تم إنشاء ملف حقيقي: ${exp.fileName} (${exp.byteSize} بايت • ${exp.rowCount} سطر)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MekkaEmeraldGreen
                            )
                            Text(
                                text = "البصمة SHA-256: ${exp.sha256Checksum.take(16)}... • المسار: ${exp.filePath}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, exp.fileName)
                                    putExtra(Intent.EXTRA_TEXT, "تقرير مكة فون المصدر: ${exp.fileName}\nالمسار: ${exp.filePath}\nSHA-256: ${exp.sha256Checksum}")
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(sendIntent, "مشاركة ملف التقرير")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة الملف")
                        }
                    }
                }
            }

            // Tabular Report Preview
            v4Document?.let { doc ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.titleArabic,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${doc.rows.size} سجل",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Summary Totals Chips
                        if (doc.summaryMetrics.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                doc.summaryMetrics.forEach { (k, v) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "$k: $v",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Column Headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            doc.headers.take(4).forEach { hdr ->
                                Text(
                                    text = hdr,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (doc.rows.isEmpty()) {
                            Text(
                                text = "لا توجد سجلات مطابقة للفلاتر المحددة في هذه الفترة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            doc.rows.take(15).forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = row.routeTarget != null) {
                                            row.routeTarget?.let(onNavigateToRoute)
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    row.cells.take(4).forEach { cell ->
                                        Text(
                                            text = cell,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 9 & 10 — PROCUREMENT INTELLIGENCE & INVENTORY ANALYTICS
// =================================================================================================

@Composable
fun ProcurementAndInventoryAnalyticsSection(
    viewModel: MekkaPhoneViewModel,
    modifier: Modifier = Modifier
) {
    val bundle by viewModel.inventoryAnalyticsBundle.collectAsStateWithLifecycle()
    ProcurementAndInventoryAnalyticsSection(
        bundle = bundle,
        onOrderProduct = { productId -> viewModel.requestPurchaseProduct(productId) },
        onOpenProductDetail = { sku -> viewModel.requestSelectProductDetail(sku) },
        modifier = modifier
    )
}

@Composable
fun ProcurementAndInventoryAnalyticsSection(
    bundle: InventoryAnalyticsDashboardBundle,
    onOrderProduct: (String) -> Unit,
    onOpenProductDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0 = Reorder Suggestions, 1 = Aging & Turnover

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("procurement_inventory_analytics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ذكاء المشتريات وتحليلات المخزون (Procurement & Analytics)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "مقترحات إعادة الطلب من المبيعات الفعلية • تقييم WAC • الدوران والتقادم",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MekkaStatusBadge(
                    text = "${bundle.reorderSuggestions.size} مقترح شراء",
                    color = if (bundle.reorderSuggestions.isNotEmpty()) MekkaAmberWarning else MekkaEmeraldGreen
                )
            }

            // Top KPI Strip: Inventory Value (WAC), Potential Margin, Turnover, Velocity counts
            val marginPercent = if (bundle.totalPotentialRevenueMinor > 0L) {
                (bundle.totalPotentialMarginMinor.toDouble() * 100.0) / bundle.totalPotentialRevenueMinor.toDouble()
            } else 0.0
            val turnoverRatio = bundle.averageTurnoverBasisPoints / 10000.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMiniTile(
                    label = "قيمة المخزون (WAC)",
                    value = Money.ofMinor(bundle.totalInventoryValueWacMinor).formatArabic(),
                    accent = MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "الهامش المحتمل (${String.format(Locale.US, "%.1f%%", marginPercent)})",
                    value = Money.ofMinor(bundle.totalPotentialMarginMinor).formatArabic(),
                    accent = MekkaGoldPrimary,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "معدل الدوران",
                    value = String.format(Locale.US, "%.2fx", turnoverRatio),
                    accent = MekkaInfoBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMiniTile(
                    label = "سريع الحركة (Fast)",
                    value = "${bundle.fastMovingItems.size} صنف",
                    accent = MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "بطيء الحركة (Slow)",
                    value = "${bundle.slowMovingItems.size} صنف",
                    accent = MekkaAmberWarning,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "مخزون راكد (Dead)",
                    value = "${bundle.deadStockItems.size} صنف",
                    accent = if (bundle.deadStockItems.isNotEmpty()) MekkaCrimsonError else MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("مقترحات الشراء (${bundle.reorderSuggestions.size})") },
                    modifier = Modifier.testTag("procurement_tab_reorder")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("تقادم ودوران الأصناف (${bundle.allItemsAnalytics.size})") },
                    modifier = Modifier.testTag("procurement_tab_analytics")
                )
            }

            if (activeTab == 0) {
                if (bundle.reorderSuggestions.isEmpty()) {
                    Text(
                        text = "جميع الأصناف فوق حد إعادة الطلب حالياً وفقاً للمبيعات الفعلية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    bundle.reorderSuggestions.take(10).forEach { sug ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reorder_suggestion_${sug.productId}"),
                            shape = RoundedCornerShape(12.dp),
                            color = MekkaAmberWarning.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MekkaAmberWarning.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${sug.productNameArabic} (${sug.sku})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "الرصيد الحالي: ${sug.currentStockQty} • الحد الأدنى: ${sug.minimumStockQty} • الكمية المقترحة: ${sug.suggestedReorderQuantity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "السبب: ${sug.reasonArabic}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekkaAmberWarning
                                    )
                                }
                                Button(
                                    onClick = { onOrderProduct(sug.productId) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reorder_btn_${sug.productId}")
                                ) {
                                    Text("شراء (${sug.suggestedReorderQuantity})", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                bundle.allItemsAnalytics.take(12).forEach { item ->
                    val velColor = when (item.velocityClass) {
                        "FAST_MOVING" -> MekkaEmeraldGreen
                        "NORMAL_MOVING" -> MekkaInfoBlue
                        "SLOW_MOVING" -> MekkaAmberWarning
                        else -> MekkaCrimsonError
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProductDetail(item.sku) }
                            .testTag("inventory_analytics_row_${item.productId}"),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.nameArabic} (${item.sku})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "العمر: ${item.ageDaysSinceLastInbound} يوم (${item.agingBucket.arabicLabel}) • اتجاه التكلفة: ${item.costTrend.arabicLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MekkaStatusBadge(text = item.velocityArabic, color = velColor)
                                Text(
                                    text = "WAC: ${Money.ofMinor(item.unitWacMinor).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 11 — MAINTENANCE MANAGEMENT CENTER CARD
// =================================================================================================

@Composable
fun MaintenanceManagementCenterCard(
    viewModel: MekkaPhoneViewModel,
    modifier: Modifier = Modifier
) {
    val bundle by viewModel.maintenanceManagementBundle.collectAsStateWithLifecycle()
    MaintenanceManagementCenterCard(
        bundle = bundle,
        modifier = modifier
    )
}

@Composable
fun MaintenanceManagementCenterCard(
    bundle: MaintenanceManagementCenterBundle,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("maintenance_management_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MekkaGoldPrimary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مركز إدارة الصيانة المتقدم (Maintenance Management Center)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "متابعة المراحل • ضغط عمل المهندسين • متوسط زمن الإصلاح • الربح الصافي الحقيقي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MekkaStatusBadge(
                    text = String.format(Locale.US, "متوسط الإصلاح: %.1f ساعة", bundle.averageRepairDurationHours),
                    color = MekkaInfoBlue
                )
            }

            // Stage Counts Grid: Received, Under Inspection, Waiting Approval, In Repair, Testing, Ready, Overdue, Delivered
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StageCountPill("مستلم", bundle.receivedCount, MekkaInfoBlue)
                StageCountPill("تحت الفحص", bundle.underInspectionCount, MekkaInfoBlue)
                StageCountPill("بانتظار الموافقة", bundle.waitingApprovalCount, MekkaAmberWarning)
                StageCountPill("قيد الإصلاح", bundle.inRepairCount, MekkaGoldPrimary)
                StageCountPill("تحت الاختبار", bundle.testingCount, MekkaInfoBlue)
                StageCountPill("جاهز للتسليم", bundle.readyCount, MekkaEmeraldGreen)
                StageCountPill("متأخر Overdue", bundle.overdueCount, MekkaCrimsonError)
                StageCountPill("تم التسليم", bundle.deliveredCount, MekkaEmeraldGreen)
            }

            // True Maintenance Financial Breakdown (Revenue - Parts Cost WAC - Related Expenses)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMiniTile(
                    label = "إيراد الصيانة المحصل",
                    value = Money.ofMinor(bundle.totalMaintenanceRevenueMinor).formatArabic(),
                    accent = MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "تكلفة القطع والمصروفات",
                    value = Money.ofMinor(bundle.totalPartsCostWacMinor + bundle.totalRelatedExpensesMinor).formatArabic(),
                    accent = MekkaAmberWarning,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "صافي ربح الصيانة",
                    value = Money.ofMinor(bundle.trueMaintenanceNetProfitMinor).formatArabic(),
                    accent = if (bundle.trueMaintenanceNetProfitMinor >= 0L) MekkaEmeraldGreen else MekkaCrimsonError,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "مدفوعات متبقية",
                    value = Money.ofMinor(bundle.totalOutstandingPaymentsMinor).formatArabic(),
                    accent = if (bundle.totalOutstandingPaymentsMinor > 0L) MekkaCrimsonError else MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            // Technician Workload Breakdown
            if (bundle.technicianWorkloads.isNotEmpty()) {
                Text(
                    text = "توزيع عبء العمل على مهندسي الصيانة (Technician Workload):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                bundle.technicianWorkloads.forEach { tech ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${tech.technicianNameArabic} • نشط: ${tech.activeTicketsCount} • مكتمل: ${tech.completedTicketsCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "متأخر: ${tech.overdueTicketsCount} • إيراد: ${Money.ofMinor(tech.generatedRevenueMinor).formatArabic()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = if (tech.overdueTicketsCount > 0) MekkaCrimsonError else MekkaEmeraldGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageCountPill(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = LedgerMonoFontFamily),
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// =================================================================================================
// SECTION 12 — CASH & WALLET CONTROL CENTER CARD
// =================================================================================================

@Composable
fun CashAndWalletControlCenterCard(
    viewModel: MekkaPhoneViewModel,
    modifier: Modifier = Modifier
) {
    val bundle by viewModel.cashAndWalletControlBundle.collectAsStateWithLifecycle()
    CashAndWalletControlCenterCard(
        bundle = bundle,
        modifier = modifier
    )
}

@Composable
fun CashAndWalletControlCenterCard(
    bundle: CashAndWalletControlCenterBundle,
    modifier: Modifier = Modifier
) {
    val allChannels = remember(bundle.cashDrawerRow, bundle.walletRows) {
        listOf(bundle.cashDrawerRow) + bundle.walletRows
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cash_wallet_control_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MekkaEmeraldGreen.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مركز التحكم الموحد للخزينة والمحافظ (Cash & Wallet Control)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "المصدر الوحيد للحقيقة المالية هو دفتر الأستاذ (Ledger) • الإجمالي المتوقع: ${Money.ofMinor(bundle.totalExpectedLiquidityMinor).formatArabic()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MekkaStatusBadge(
                    text = "${allChannels.size} قناة مالية",
                    color = MekkaEmeraldGreen
                )
            }

            allChannels.forEach { channel ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("control_channel_${channel.accountId}"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${channel.accountNameArabic} (${channel.providerCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "المتوقع: ${Money.ofMinor(channel.expectedBalanceMinor).formatArabic()}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LedgerMonoFontFamily),
                                fontWeight = FontWeight.ExtraBold,
                                color = MekkaEmeraldGreen
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "افتتاحي: ${Money.ofMinor(channel.openingBalanceMinor).formatArabic()} • وارد (In): ${Money.ofMinor(channel.inflowMinor).formatArabic()} • صادر (Out): ${Money.ofMinor(channel.outflowMinor).formatArabic()}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الرسوم: ${Money.ofMinor(channel.feesMinor).formatArabic()} • العمليات: ${channel.transactionsCount} • المعلقة: ${channel.pendingOperationsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (channel.varianceMinor != 0L) {
                                MekkaStatusBadge(
                                    text = "فرق الجرد: ${Money.ofMinor(channel.varianceMinor).formatArabic()}",
                                    color = MekkaCrimsonError
                                )
                            } else {
                                MekkaStatusBadge(
                                    text = "مطابق",
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 13 & 14 — OFFLINE CONTROL CENTER & MULTI-DEVICE VISIBILITY
// =================================================================================================

@Composable
fun OfflineAndMultiDeviceCenterCard(
    bundle: OfflineControlCenterBundle,
    onToggleOnlineMode: (Boolean) -> Unit,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = when (bundle.lifecycleState) {
        ConnectivitySyncLifecycleState.OFFLINE -> MekkaAmberWarning
        ConnectivitySyncLifecycleState.SYNCING -> MekkaInfoBlue
        ConnectivitySyncLifecycleState.SYNCED_VERIFIED -> MekkaEmeraldGreen
        ConnectivitySyncLifecycleState.ONLINE_PENDING -> MekkaGoldPrimary
        ConnectivitySyncLifecycleState.SYNC_FAILED -> MekkaCrimsonError
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_control_center_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = stateColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.45f))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (bundle.lifecycleState == ConnectivitySyncLifecycleState.OFFLINE) {
                            Icons.Default.CloudOff
                        } else if (bundle.lifecycleState == ConnectivitySyncLifecycleState.SYNCING) {
                            Icons.Default.CloudSync
                        } else {
                            Icons.Default.CloudDone
                        },
                        contentDescription = null,
                        tint = stateColor
                    )
                    Column {
                        Text(
                            text = "${bundle.lifecycleState.badgeText} — ${bundle.lifecycleState.arabicLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = stateColor
                        )
                        Text(
                            text = if (!bundle.isRemoteServerConfigured) {
                                "العمليات محفوظة محلياً • لا يتم عرض Synced إلا بعد مصادقة خادم حقيقية (Remote ACK)"
                            } else {
                                "آخر مصادقة خادم: ${formatOperationalTimestamp(bundle.lastVerifiedSyncTimestamp)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (bundle.isNetworkOnline) "متصل" else "Offline",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Switch(
                        checked = bundle.isNetworkOnline,
                        onCheckedChange = onToggleOnlineMode,
                        modifier = Modifier.testTag("offline_mode_toggle_switch")
                    )
                }
            }

            // Counts Strip: Pending Local Operations, Pending Sync, Failed Sync, Conflicts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMiniTile(
                    label = "عمليات محلية معلقة",
                    value = "${bundle.pendingLocalOperationsCount}",
                    accent = MekkaInfoBlue,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "بانتظار المزامنة",
                    value = "${bundle.pendingSyncCount}",
                    accent = MekkaGoldPrimary,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "فشل المزامنة",
                    value = "${bundle.failedSyncCount}",
                    accent = if (bundle.failedSyncCount > 0) MekkaCrimsonError else MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniTile(
                    label = "تعارضات",
                    value = "${bundle.unresolvedConflictsCount}",
                    accent = if (bundle.unresolvedConflictsCount > 0) MekkaCrimsonError else MekkaEmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            if (bundle.multiDeviceOperations.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "رؤية تعدد الأجهزة (Multi-Device Visibility — بدون Last-Write-Wins للمال/المخزون):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onTriggerSync,
                        modifier = Modifier.testTag("offline_center_sync_now_btn")
                    ) {
                        Text("مزامنة الآن", style = MaterialTheme.typography.labelSmall)
                    }
                }
                bundle.multiDeviceOperations.take(4).forEach { rec ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${rec.summaryArabic} (#${rec.referenceNumber})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${rec.syncStatusArabic} • ${rec.conflictStatusArabic}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rec.hasUnresolvedConflict) MekkaCrimsonError else MekkaEmeraldGreen
                                )
                            }
                            Text(
                                text = "الجهاز: ${rec.deviceId} • المنفذ: ${rec.operatorNameArabic} • Remote ID: ${rec.remoteCloudId ?: "محلي فقط"} • ${formatOperationalTimestamp(rec.timestamp)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 15 — AUDIT & DIAGNOSTICS 12-PILLAR DASHBOARD
// =================================================================================================

@Composable
fun AuditDiagnosticsCenterCard(
    bundle: AuditDiagnosticsDashboardBundle,
    onRunHealthCheck: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audit_diagnostics_12_pillar_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مركز المراقبة والتشخيص الشامل (12-Pillar Audit & Diagnostics)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "مراقبة حية لقاعدة البيانات، المزامنة، النسخ الاحتياطي، التوازن المحاسبي، والصلاحيات",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRunHealthCheck,
                    modifier = Modifier.testTag("diagnostics_refresh_12_pillars_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "فحص الآن")
                }
            }

            // 12 Pillars Grid
            val pillars = listOf(
                "1. صحة قاعدة البيانات" to bundle.databaseHealthStatusArabic,
                "2. إصدار Migration" to "v${bundle.migrationSchemaVersion}",
                "3. مساحة التخزين" to bundle.storageStatusArabic,
                "4. طابور المزامنة" to "${bundle.syncQueuePendingCount} بالانتظار",
                "5. فشل المزامنة" to "${bundle.syncFailedCount} عملية",
                "6. عدد التعارضات" to "${bundle.conflictCount} تعارض",
                "7. حالة النسخ الاحتياطي" to bundle.backupStatusArabic,
                "8. آخر نسخة احتياطية" to formatOperationalTimestamp(bundle.lastBackupTimestamp),
                "9. آخر مزامنة ناجحة" to formatOperationalTimestamp(bundle.lastSuccessfulSyncTimestamp),
                "10. سلامة التوازن المالي" to bundle.integrityStatusArabic,
                "11. أنبوب رسائل SMS" to bundle.smsPipelineStatusArabic,
                "12. حالة الصلاحيات" to "${bundle.permissionStatusArabic} (${bundle.activeRoleCode})"
            )

            pillars.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (title, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Issues with Description, Severity, Recommended Action
            if (bundle.issuesWithRecommendations.isNotEmpty()) {
                Text(
                    text = "المشكلات المكتشفة والإجراءات الموصى بها (${bundle.issuesWithRecommendations.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MekkaAmberWarning
                )
                bundle.issuesWithRecommendations.forEach { issue ->
                    val sevColor = when (issue.severity) {
                        DiagnosticSeverity.CRITICAL -> MekkaCrimsonError
                        DiagnosticSeverity.WARNING -> MekkaAmberWarning
                        DiagnosticSeverity.HEALTHY -> MekkaInfoBlue
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToRoute(issue.targetRoute) }
                            .testTag("diagnostic_issue_${issue.code}"),
                        shape = RoundedCornerShape(10.dp),
                        color = sevColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, sevColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = issue.subsystemArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                MekkaStatusBadge(text = issue.severity.arabicLabel, color = sevColor)
                            }
                            Text(
                                text = "الوصف (Description): ${issue.descriptionArabic}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "الإجراء الموصى به (Recommended Action): ${issue.recommendedActionArabic}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sevColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMiniTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily),
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =================================================================================================
// SECTION 8 — SAFE AUTOMATION CENTER SCREEN (Requirement P2-L)
// =================================================================================================

@Composable
fun SafeAutomationCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val rules by viewModel.automationRules.collectAsStateWithLifecycle()
    val activeRulesCount = remember(rules) { rules.count { it.isEnabled } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("safe_automation_center_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("safe_automation_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع"
                                )
                            }
                            Column {
                                Text(
                                    text = "محرك الأتمتة والرقابة الآمنة",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "تنبيهات واستجابات آلية خالية من أي أثر مالي مباشر (P2-L)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MekkaStatusBadge(
                            text = "$activeRulesCount من ${rules.size} نشطة",
                            color = if (activeRulesCount > 0) MekkaEmeraldGreen else MekkaAmberWarning
                        )
                    }

                    // Operational Safety Principle Notice
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MekkaEmeraldGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MekkaEmeraldGreen.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MekkaEmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "مبدأ الأمان غير القابل للتفاوض: محرك الأتمتة يُصدر إشعارات وتنبيهات فقط، ولا يقوم بأي تحويل أو سحب أو إيداع أو صرف نقدي تلقائياً دون موافقة واعتماد صريح من المشرف.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("automation_rule_card_${rule.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (rule.isEnabled) MekkaGoldPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
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
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (rule.isEnabled) MekkaEmeraldGreen else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = rule.nameArabic,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleAutomationRule(rule.id) },
                            modifier = Modifier.testTag("rule_toggle_${rule.id}")
                        )
                    }

                    Text(
                        text = rule.descriptionArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "المشغل: ${rule.triggerType.name}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "الإجراء: ${rule.actionType.name}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (rule.lastExecutionSummaryArabic != null) {
                        Text(
                            text = "آخر تنفيذ: ${rule.lastExecutionSummaryArabic}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MekkaEmeraldGreen
                        )
                    }
                }
            }
        }
    }
}

// =================================================================================================
// SECTION 9 — DATA INTEGRITY & REPAIR CENTER SCREEN (Requirement P2-X)
// =================================================================================================

@Composable
fun DataIntegrityAndRepairCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val issues by viewModel.integrityIssues.collectAsStateWithLifecycle()
    val healthSummary by viewModel.appHealthSummary.collectAsStateWithLifecycle()

    var pendingProposal by remember { mutableStateOf<IntegrityRepairProposal?>(null) }

    if (pendingProposal != null) {
        val prop = pendingProposal!!
        AlertDialog(
            onDismissRequest = { pendingProposal = null },
            title = {
                Text(
                    text = "تأكيد تنفيذ مقترح الإصلاح المعتمد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = prop.titleArabic,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "البيان: ${prop.affectedEntityDescription}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الحالة الحالية: ${prop.beforeStateArabic}", style = MaterialTheme.typography.labelSmall, color = MekkaCrimsonError)
                            Text("الحالة المقترحة: ${prop.proposedStateArabic}", style = MaterialTheme.typography.labelSmall, color = MekkaEmeraldGreen)
                        }
                    }
                    Text(
                        text = "يتطلب هذا الإجراء صلاحيات إشرافية ويتم توثيقه فوراً في سجل التدقيق المالي.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = prop
                        pendingProposal = null
                        viewModel.executeAuthorizedRepair(p)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("confirm_repair_btn")
                ) {
                    Text("تنفيذ الإصلاح الآن")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingProposal = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("data_integrity_center_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("integrity_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع"
                                )
                            }
                            Column {
                                Text(
                                    text = "مركز سلامة وصيانة البيانات",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "فحص الاتساق المحاسبي والمخزني وسلامة الدفاتر",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MekkaStatusBadge(
                            text = "${healthSummary.healthScore}% سلامة",
                            color = if (healthSummary.isOverallHealthy) MekkaEmeraldGreen else MekkaAmberWarning
                        )
                    }

                    // Health Dimensions Grid
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        healthSummary.dimensions.forEach { dim ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (dim.isHealthy) MekkaEmeraldGreen.copy(alpha = 0.07f) else MekkaCrimsonError.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    1.dp,
                                    if (dim.isHealthy) MekkaEmeraldGreen.copy(alpha = 0.25f) else MekkaCrimsonError.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dim.isHealthy) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = if (dim.isHealthy) MekkaEmeraldGreen else MekkaCrimsonError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = dim.titleArabic,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = dim.statusLabelArabic,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dim.isHealthy) MekkaEmeraldGreen else MekkaCrimsonError,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MekkaSectionHeader(
                title = "المشكلات المرصودة ومقترحات المعالجة (${issues.size})"
            )
        }

        if (issues.isEmpty()) {
            item {
                MekkaEmptyStateCard(
                    title = "البيانات متسقة تماماً وخالية من التعارض",
                    description = "تم فحص قيود دفتر الأستاذ وحركات المخزون وجلسات الخزينة وسقوف المحافظ بنجاح دون رصد أي خلل.",
                    icon = Icons.Default.VerifiedUser
                )
            }
        } else {
            items(issues, key = { it.issueId }) { issue ->
                val badgeColor = when (issue.severity) {
                    IntegritySeverity.CRITICAL -> MekkaCrimsonError
                    IntegritySeverity.WARNING -> MekkaAmberWarning
                    IntegritySeverity.INFO -> FinancialInfo
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("integrity_issue_${issue.issueId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
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
                            Text(
                                text = issue.titleArabic,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            MekkaStatusBadge(
                                text = when (issue.severity) {
                                    IntegritySeverity.CRITICAL -> "حرج"
                                    IntegritySeverity.WARNING -> "تنبيه"
                                    IntegritySeverity.INFO -> "معلومات"
                                },
                                color = badgeColor
                            )
                        }

                        Text(
                            text = issue.descriptionArabic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "الإجراء المقترح: ${issue.suggestedRepairActionArabic}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        if (issue.repairCode.isNotBlank()) {
                            Button(
                                onClick = {
                                    pendingProposal = IntegrityRepairProposal(
                                        issueId = issue.issueId,
                                        repairCode = issue.repairCode,
                                        titleArabic = issue.titleArabic,
                                        affectedEntityDescription = issue.descriptionArabic,
                                        beforeStateArabic = "بيانات غير متطابقة / متعثرة",
                                        proposedStateArabic = issue.suggestedRepairActionArabic,
                                        requiresSupervisorAuthorization = true
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apply_repair_${issue.issueId}")
                            ) {
                                Text("مراجعة واعتماد مقترح الإصلاح")
                            }
                        }
                    }
                }
            }
        }
    }
}

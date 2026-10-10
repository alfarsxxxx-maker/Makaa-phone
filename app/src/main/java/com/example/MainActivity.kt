package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.MekkaPhoneDatabase
import com.example.navigation.MekkaDestination
import com.example.repository.MekkaPhoneRepository
import com.example.ui.components.ContextualGuidanceBanner
import com.example.ui.components.MekkaCentralUxFeedbackHost
import com.example.ui.components.NotificationCenterDialog
import com.example.ui.components.OperationFeedbackBanner
import com.example.ui.components.QuickActionLauncherDialog
import com.example.ui.components.QuickActionShortcutItem
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.UxOperationState
import com.example.financial.FriendlyTerminologyMapper
import com.example.ui.screens.BackupAndRestoreCenterScreen
import com.example.ui.screens.CashDrawerScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.FirstLaunchOnboardingDialog
import com.example.ui.screens.MaintenanceScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.OperationsLedgerScreen
import com.example.ui.screens.PendingOperationsCenterScreen
import com.example.ui.screens.PosSalesScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.ReorganizedSettingsScreen
import com.example.ui.screens.ReportsCenterScreen
import com.example.ui.screens.SafeAutomationCenterScreen
import com.example.ui.screens.DataIntegrityAndRepairCenterScreen
import com.example.ui.screens.SmsIntelligenceScreen
import com.example.ui.screens.SuppliersScreen
import com.example.ui.screens.UsersAndPermissionsScreen
import com.example.ui.screens.WalletsScreen
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaGoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MekkaPhoneViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val repository = remember(context) {
                MekkaPhoneRepository(MekkaPhoneDatabase.getInstance(context))
            }
            val viewModel: MekkaPhoneViewModel = viewModel(
                factory = MekkaPhoneViewModel.Factory(repository)
            )
            val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
            MyApplicationTheme(
                themeMode = shopConfig.themeMode,
                accentPalette = shopConfig.accentPalette,
                uiDensity = shopConfig.uiDensity
            ) {
                MekkaPhoneAppShell(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MekkaPhoneAppShell(
    viewModel: MekkaPhoneViewModel
) {
    var currentDestination by rememberSaveable { mutableStateOf(MekkaDestination.CONTROL_CENTER) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val kpis by viewModel.dashboardKpis.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val monthlyReport by viewModel.monthlyReport.collectAsStateWithLifecycle()
    val activeUserId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val isSessionLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerBalances by viewModel.customerBalances.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplierBalances by viewModel.supplierBalances.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val serialNumbers by viewModel.serialNumbers.collectAsStateWithLifecycle()
    val movements by viewModel.inventoryMovements.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val refunds by viewModel.refunds.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val maintenanceOrders by viewModel.maintenanceOrders.collectAsStateWithLifecycle()
    val walletProviders by viewModel.walletProviders.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val smsReviewItems by viewModel.smsReviewItems.collectAsStateWithLifecycle()
    val cashDrawers by viewModel.cashDrawers.collectAsStateWithLifecycle()
    val cashSessions by viewModel.cashSessions.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val syncQueue by viewModel.syncQueue.collectAsStateWithLifecycle()
    val syncConflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val lastBackupValidation by viewModel.lastBackupValidation.collectAsStateWithLifecycle()
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val searchQuery by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.globalSearchResults.collectAsStateWithLifecycle()
    val systemAlerts by viewModel.systemAlerts.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val uxOperationState by viewModel.uxOperationState.collectAsStateWithLifecycle()
    val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
    val showGuidanceBanners by viewModel.showGuidanceBanners.collectAsStateWithLifecycle()
    val truthfulSyncStatus by viewModel.truthfulSyncStatus.collectAsStateWithLifecycle()
    val pendingOperationsBundle by viewModel.pendingOperationsBundle.collectAsStateWithLifecycle()
    val smartNotificationBundle by viewModel.smartNotificationCenterBundle.collectAsStateWithLifecycle()
    val showComposerDialog by viewModel.showComposerDialog.collectAsStateWithLifecycle()
    val composerInitialModality by viewModel.composerInitialModality.collectAsStateWithLifecycle()

    var showNotificationCenterDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickActionDialog by rememberSaveable { mutableStateOf(false) }
    var firstLaunchDismissed by rememberSaveable { mutableStateOf(false) }

    val activeRoleCode = users.firstOrNull { it.localId == activeUserId }?.roleCode ?: "OWNER"
    val activeRoleArabic = FriendlyTerminologyMapper.toFriendlyRoleArabic(activeRoleCode)

    fun badgeCountForDestination(destination: MekkaDestination): Int = when (destination) {
        MekkaDestination.CONTROL_CENTER -> systemAlerts.size
        MekkaDestination.PRODUCTS -> kpis.lowStockProductsCount
        MekkaDestination.SMS_INTELLIGENCE -> kpis.pendingSmsCount
        MekkaDestination.MAINTENANCE -> kpis.readyRepairsCount
        MekkaDestination.OPERATIONS -> kpis.unresolvedConflictsCount + kpis.failedSyncCount
        MekkaDestination.PENDING_OPERATIONS -> pendingOperationsBundle.totalCount
        else -> 0
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("main_navigation_drawer")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${shopConfig.shopNameArabic} — ${shopConfig.shopNameEnglish}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "نظام تشغيل المتجر والمحاسبة الذرية (${shopConfig.defaultCurrency})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPillBadge(
                            text = "${truthfulSyncStatus.arabicLabel} • $activeRoleArabic",
                            color = FinancialPositive
                        )
                        val isDarkActive = shopConfig.themeMode.equals("DARK", ignoreCase = true)
                        FilterChip(
                            selected = isDarkActive,
                            onClick = {
                                val nextMode = if (isDarkActive) "LIGHT" else "DARK"
                                viewModel.updateAppSetting(
                                    MekkaPhoneRepository.KEY_THEME_MODE,
                                    nextMode,
                                    "تبديل سريع للوضع البصري"
                                )
                            },
                            label = {
                                Text(if (isDarkActive) "الوضع الليلي" else "الوضع النهاري")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isDarkActive) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.testTag("drawer_quick_theme_toggle")
                        )
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                MekkaDestination.entries.forEach { destination ->
                    val destBadgeCount = badgeCountForDestination(destination)
                    NavigationDrawerItem(
                        label = { Text(destination.titleArabic) },
                        selected = currentDestination == destination,
                        onClick = {
                            if (isSessionLocked) {
                                viewModel.showFeedbackMessage("الجلسة مقفلة — أدخل رمز PIN أولاً", isError = true)
                            } else if (!viewModel.isRouteAuthorizedForActiveUser(destination.route)) {
                                viewModel.showFeedbackMessage(
                                    "عذراً، دورك الوظيفي ($activeRoleArabic) غير مخول بالوصول إلى (${destination.titleArabic})",
                                    isError = true
                                )
                            } else {
                                currentDestination = destination
                            }
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.titleArabic
                            )
                        },
                        badge = if (destBadgeCount > 0) {
                            {
                                StatusPillBadge(
                                    text = destBadgeCount.toString(),
                                    color = if (destination == MekkaDestination.OPERATIONS) FinancialNegative else FinancialWarning
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_${destination.route}")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentDestination.titleArabic,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("top_bar_title")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "فتح القائمة الرئيسية",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        if (currentDestination != MekkaDestination.CONTROL_CENTER) {
                            IconButton(
                                onClick = { currentDestination = MekkaDestination.CONTROL_CENTER },
                                modifier = Modifier.testTag("back_to_control_center_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "العودة لمركز التحكم"
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleGuidanceBanners() },
                            modifier = Modifier.testTag("toggle_guidance_banners_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "إظهار أو إخفاء أشرطة التوضيح المحاسبي",
                                tint = if (showGuidanceBanners) MekkaGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showNotificationCenterDialog = true },
                            modifier = Modifier.testTag("notification_center_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    val alertTotal = systemAlerts.size
                                    if (alertTotal > 0) {
                                        Badge(
                                            containerColor = FinancialNegative,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text(alertTotal.toString())
                                        }
                                    } else if (notificationHistory.isNotEmpty()) {
                                        Badge(containerColor = MekkaGoldPrimary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "مركز الإشعارات والتنبيهات",
                                    tint = if (systemAlerts.isNotEmpty()) FinancialWarning else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { currentDestination = MekkaDestination.SMS_INTELLIGENCE },
                            modifier = Modifier.testTag("quick_sms_intelligence_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (kpis.pendingSmsCount > 0) {
                                        Badge(containerColor = MekkaGoldPrimary) {
                                            Text(kpis.pendingSmsCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "ذكاء رسائل SMS",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { currentDestination = MekkaDestination.CASH_DRAWER },
                            modifier = Modifier.testTag("quick_cash_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "الخزينة",
                                tint = MekkaGoldPrimary
                            )
                        }
                        IconButton(
                            onClick = { currentDestination = MekkaDestination.SETTINGS },
                            modifier = Modifier.testTag("quick_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.testTag("main_bottom_navigation_bar")
                ) {
                    MekkaDestination.primaryBottomTabs.forEach { tab ->
                        val tabBadgeCount = badgeCountForDestination(tab)
                        val isTabSelected = currentDestination == tab ||
                            (tab == MekkaDestination.MORE && currentDestination !in MekkaDestination.primaryBottomTabs)
                        NavigationBarItem(
                            selected = isTabSelected,
                            onClick = { currentDestination = tab },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tabBadgeCount > 0) {
                                            Badge(
                                                containerColor = if (tab == MekkaDestination.OPERATIONS) FinancialNegative else FinancialWarning
                                            ) {
                                                Text(tabBadgeCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.shortTitleArabic
                                    )
                                }
                            },
                            label = { Text(tab.shortTitleArabic) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("bottom_tab_${tab.route}")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentDestination == MekkaDestination.CONTROL_CENTER) {
                    ExtendedFloatingActionButton(
                        onClick = { showQuickActionDialog = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "مشغل العمليات السريعة"
                            )
                        },
                        text = { Text("إجراء سريع") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("quick_action_fab")
                    )
                }
            }
        ) { innerPadding ->
            val returnToControlCenter = {
                if (!isSessionLocked) {
                    currentDestination = MekkaDestination.CONTROL_CENTER
                }
            }
            val navigateToRoute: (String) -> Unit = { route ->
                val targetDest = MekkaDestination.entries.firstOrNull { it.route == route }
                if (targetDest != null) {
                    if (isSessionLocked) {
                        viewModel.showFeedbackMessage("الجلسة مقفلة — أدخل رمز PIN أولاً", isError = true)
                    } else if (!viewModel.isRouteAuthorizedForActiveUser(targetDest.route)) {
                        viewModel.showFeedbackMessage(
                            "عذراً، دورك الوظيفي ($activeRoleArabic) غير مخول بالوصول إلى (${targetDest.titleArabic})",
                            isError = true
                        )
                    } else {
                        currentDestination = targetDest
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 900.dp)
                ) {
                    if (uxOperationState is UxOperationState.Loading ||
                        uxOperationState is UxOperationState.Processing ||
                        uxOperationState is UxOperationState.Progress ||
                        uxOperationState is UxOperationState.Syncing
                    ) {
                        MekkaCentralUxFeedbackHost(
                            operationState = uxOperationState,
                            onDismiss = { viewModel.clearFeedback() }
                        )
                    }

                    val errorActionLabel = (uxOperationState as? UxOperationState.Error)?.actionLabelArabic
                    val errorActionCallback = (uxOperationState as? UxOperationState.Error)?.onAction ?: { viewModel.clearFeedback() }

                    OperationFeedbackBanner(
                        message = feedback?.first,
                        isError = feedback?.second ?: false,
                        onDismiss = { viewModel.clearFeedback() },
                        actionLabelArabic = if (feedback?.second == true) errorActionLabel else null,
                        onActionClick = if (feedback?.second == true) errorActionCallback else null
                    )

                    ContextualGuidanceBanner(
                        destination = currentDestination,
                        visible = showGuidanceBanners,
                        onDismissBanner = { viewModel.dismissGuidanceBanners() }
                    )

                    if (isSessionLocked) {
                        BackHandler { /* Prevent bypassing lock screen via back button */ }
                        SessionLockGateScreen(
                            users = users,
                            activeUserId = activeUserId,
                            onAuthenticate = { userId, pin ->
                                viewModel.authenticateWithPin(userId = userId, rawPin = pin)
                            }
                        )
                    } else if (!viewModel.isRouteAuthorizedForActiveUser(currentDestination.route)) {
                        BackHandler { returnToControlCenter() }
                        UnauthorizedRouteScreen(
                            destinationTitle = currentDestination.titleArabic,
                            activeRoleArabic = activeRoleArabic,
                            onReturnHome = returnToControlCenter
                        )
                    } else {
                        if (currentDestination != MekkaDestination.CONTROL_CENTER) {
                            BackHandler { returnToControlCenter() }
                        }

                        when (currentDestination) {
                        MekkaDestination.CONTROL_CENTER -> ControlCenterScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.MORE -> MoreScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.CUSTOMERS -> CustomersScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.SUPPLIERS -> SuppliersScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.PRODUCTS -> ProductsScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.SALES -> PosSalesScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.PURCHASES -> PurchasesScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.MAINTENANCE -> MaintenanceScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.WALLETS -> WalletsScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.SMS_INTELLIGENCE -> SmsIntelligenceScreen(
                            viewModel = viewModel,
                            onNavigateToDiagnostics = { currentDestination = MekkaDestination.DIAGNOSTICS }
                        )
                        MekkaDestination.CASH_DRAWER -> CashDrawerScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.EXPENSES -> ExpensesScreen(
                            expenses = expenses,
                            onExecuteExpense = viewModel::executeExpense,
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.OPERATIONS, MekkaDestination.LEDGER -> OperationsLedgerScreen(
                            transactions = ledgerTransactions,
                            auditLogs = auditLogs,
                            syncQueue = syncQueue,
                            conflicts = syncConflicts,
                            onReverseTransaction = viewModel::reverseLedgerTransaction,
                            onRunDeltaSync = { viewModel.runDeltaSync(isNetworkAvailable = true) },
                            onResolveConflict = viewModel::resolveSyncConflict,
                            onBack = returnToControlCenter,
                            initialTab = 0
                        )
                        MekkaDestination.AUDIT -> OperationsLedgerScreen(
                            transactions = ledgerTransactions,
                            auditLogs = auditLogs,
                            syncQueue = syncQueue,
                            conflicts = syncConflicts,
                            onReverseTransaction = viewModel::reverseLedgerTransaction,
                            onRunDeltaSync = { viewModel.runDeltaSync(isNetworkAvailable = true) },
                            onResolveConflict = viewModel::resolveSyncConflict,
                            onBack = returnToControlCenter,
                            initialTab = 1
                        )
                        MekkaDestination.SYNC -> OperationsLedgerScreen(
                            transactions = ledgerTransactions,
                            auditLogs = auditLogs,
                            syncQueue = syncQueue,
                            conflicts = syncConflicts,
                            onReverseTransaction = viewModel::reverseLedgerTransaction,
                            onRunDeltaSync = { viewModel.runDeltaSync(isNetworkAvailable = true) },
                            onResolveConflict = viewModel::resolveSyncConflict,
                            onBack = returnToControlCenter,
                            initialTab = 2
                        )
                        MekkaDestination.REPORTS -> ReportsCenterScreen(
                            viewModel = viewModel,
                            onNavigateToDestination = { dest -> navigateToRoute(dest.route) },
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.SETTINGS -> ReorganizedSettingsScreen(
                            viewModel = viewModel,
                            onNavigateToDestination = { dest -> navigateToRoute(dest.route) },
                            onLockSession = { viewModel.lockSession() },
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.BACKUP -> BackupAndRestoreCenterScreen(
                            viewModel = viewModel,
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.USERS_PERMISSIONS -> UsersAndPermissionsScreen(
                            viewModel = viewModel
                        )
                        MekkaDestination.DIAGNOSTICS -> DiagnosticsScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute
                        )
                        MekkaDestination.PENDING_OPERATIONS -> PendingOperationsCenterScreen(
                            viewModel = viewModel,
                            onNavigateToRoute = navigateToRoute,
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.SAFE_AUTOMATION -> SafeAutomationCenterScreen(
                            viewModel = viewModel,
                            onBack = returnToControlCenter
                        )
                        MekkaDestination.DATA_INTEGRITY -> DataIntegrityAndRepairCenterScreen(
                            viewModel = viewModel,
                            onBack = returnToControlCenter
                        )
                        }
                    }
                }

                if (!shopConfig.onboardingCompleted) {
                    FirstLaunchOnboardingDialog(
                        initialShopName = shopConfig.shopNameArabic,
                        initialPhone = shopConfig.shopPhone,
                        onDismiss = { /* Mandatory: Cannot bypass initial security credentials setup */ },
                        onSubmit = { ownerName, shopName, phone, pin, confirmPin ->
                            viewModel.completeFirstLaunchSetup(
                                ownerFullNameArabic = ownerName,
                                shopNameArabic = shopName,
                                phoneOrAccount = phone,
                                rawPin = pin,
                                confirmPin = confirmPin,
                                onSuccess = { /* Setup complete */ }
                            )
                        }
                    )
                }

                if (showNotificationCenterDialog) {
                    NotificationCenterDialog(
                        systemAlerts = systemAlerts,
                        notificationHistory = notificationHistory,
                        showGuidanceBanners = showGuidanceBanners,
                        onToggleGuidanceBanners = { viewModel.toggleGuidanceBanners() },
                        onClearHistory = { viewModel.clearNotificationHistory() },
                        onNavigateToRoute = { route ->
                            navigateToRoute(route)
                        },
                        onDismiss = { showNotificationCenterDialog = false },
                        smartNotificationBundle = smartNotificationBundle,
                        onOpenSmartAlertSource = { route, entityId, reference ->
                            if (route == MekkaDestination.CUSTOMERS.route && entityId.isNotBlank()) {
                                viewModel.navigateToCustomerDetail(entityId)
                            } else if (route == MekkaDestination.SUPPLIERS.route && entityId.isNotBlank()) {
                                viewModel.navigateToSupplierDetail(entityId)
                            } else if (route == MekkaDestination.PRODUCTS.route && reference.isNotBlank()) {
                                viewModel.navigateToProductDetail(reference)
                            } else if (route == MekkaDestination.MAINTENANCE.route && reference.isNotBlank()) {
                                viewModel.navigateToMaintenanceTicket(reference)
                            }
                            navigateToRoute(route)
                        }
                    )
                }

                if (showQuickActionDialog) {
                    val shortcuts = remember(kpis) {
                        listOf(
                            QuickActionShortcutItem(
                                titleArabic = "تنفيذ عملية ذكية موحدة (V4.1)",
                                subtitleArabic = "أمر صوتي • نص طبيعي • قراءة إيصال • مسح باركود • إدخال يدوي ومراجعة فورية",
                                badgeArabic = "ذكاء موحد",
                                routeTarget = "QUICK_TRANSACTION_COMPOSER",
                                accentColor = FinancialPositive
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "فاتورة بيع جديدة (POS)",
                                subtitleArabic = "بيع نقدي أو آجل أو سلة متعددة الأصناف مع خصم المخزن وحساب WAC",
                                badgeArabic = "نقطة البيع",
                                routeTarget = MekkaDestination.SALES.route,
                                accentColor = FinancialPositive
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "عملية محفظة إلكترونية سريعة",
                                subtitleArabic = "تحويل، سحب، أو تغذية مع حساب العمولة التلقائي ومراقبة حد الـ 80%",
                                badgeArabic = "5 مزودين",
                                routeTarget = MekkaDestination.WALLETS.route,
                                accentColor = FinancialInfo
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "استلام جهاز صيانة جديد",
                                subtitleArabic = "تذكرة إصلاح بالباركود والإملاء الصوتي وصورة الجهاز وإشعار واتساب",
                                badgeArabic = "${kpis.readyRepairsCount} جاهز",
                                routeTarget = MekkaDestination.MAINTENANCE.route,
                                accentColor = MekkaGoldPrimary
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "تحصيل دفعة عميل / كشف حساب واتساب",
                                subtitleArabic = "تسجيل سداد مديونية أو إرسال مطالبة مالية فورية عبر واتساب",
                                badgeArabic = "العملاء",
                                routeTarget = MekkaDestination.CUSTOMERS.route,
                                accentColor = FinancialPositive
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "مراجعة رسائل SMS المالية الواردة",
                                subtitleArabic = "تأكيد رسائل فودافون كاش وإنستا باي وترحيلها لدفتر الأستاذ",
                                badgeArabic = "${kpis.pendingSmsCount} بانتظار",
                                routeTarget = MekkaDestination.SMS_INTELLIGENCE.route,
                                accentColor = FinancialWarning
                            ),
                            QuickActionShortcutItem(
                                titleArabic = "توريد / صرف نقدي وتقفيل الوردية",
                                subtitleArabic = "إدارة الخزينة النقدية اليومية ومطابقة الرصيد الفعلي بالمشتق",
                                badgeArabic = "الخزينة",
                                routeTarget = MekkaDestination.CASH_DRAWER.route,
                                accentColor = MekkaGoldPrimary
                            )
                        )
                    }
                    QuickActionLauncherDialog(
                        shortcuts = shortcuts,
                        onSelectRoute = { route ->
                            showQuickActionDialog = false
                            if (route == "QUICK_TRANSACTION_COMPOSER") {
                                viewModel.openTransactionComposer()
                            } else {
                                navigateToRoute(route)
                            }
                        },
                        onDismiss = { showQuickActionDialog = false }
                    )
                }

                if (showComposerDialog) {
                    com.example.transaction.UniversalTransactionComposerDialog(
                        viewModel = viewModel,
                        initialModality = composerInitialModality,
                        onDismiss = { viewModel.closeTransactionComposer() }
                    )
                }

                val showAiAssistantDialog by viewModel.showAiAssistantDialog.collectAsStateWithLifecycle()
                if (showAiAssistantDialog) {
                    com.example.ui.screens.MekkaAiAssistantDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeAiAssistant() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionLockGateScreen(
    users: List<com.example.database.entities.UserEntity>,
    activeUserId: String,
    onAuthenticate: (String, String) -> Unit
) {
    var selectedUserId by rememberSaveable(activeUserId, users.size) {
        mutableStateOf(users.firstOrNull { it.localId == activeUserId }?.localId ?: users.firstOrNull()?.localId.orEmpty())
    }
    var pinInput by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("session_lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "بوابة الأمان — الجلسة مقفلة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "تم قفل الجلسة لحماية البيانات المالية والمخزنية. اختر الحساب وأدخل رمز PIN السري للمتابعة:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                users.filter { it.isActive }.forEach { u ->
                    FilterChip(
                        selected = selectedUserId == u.localId,
                        onClick = { selectedUserId = u.localId },
                        label = { Text("${u.fullNameArabic} (${u.roleCode})") },
                        modifier = Modifier.testTag("lock_gate_user_chip_${u.localId}")
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("رمز PIN السري") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_gate_pin_input"),
                    singleLine = true
                )
                androidx.compose.material3.Button(
                    onClick = {
                        onAuthenticate(selectedUserId, pinInput)
                        pinInput = ""
                    },
                    enabled = selectedUserId.isNotBlank() && pinInput.length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_gate_unlock_button")
                ) {
                    Text("فتح الجلسة والدخول")
                }
            }
        }
    }
}

@Composable
private fun UnauthorizedRouteScreen(
    destinationTitle: String,
    activeRoleArabic: String,
    onReturnHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("unauthorized_route_screen"),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "وصول مقيد حسب الصلاحيات (RBAC)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "دورك الحالي ($activeRoleArabic) لا يملك صلاحية الوصول إلى شاشة ($destinationTitle).",
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.Button(
                    onClick = onReturnHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unauthorized_return_home_button")
                ) {
                    Text("العودة إلى مركز التحكم")
                }
            }
        }
    }
}

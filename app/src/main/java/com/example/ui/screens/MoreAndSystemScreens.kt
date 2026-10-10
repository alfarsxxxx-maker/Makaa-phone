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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diagnostics.HealthCheckLevel
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.ui.components.ExpandableTechnicalDetails
import com.example.navigation.MekkaDestination
import com.example.ui.components.MekkaSectionHeader
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaBlueInfo
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaEmeraldLight
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.viewmodel.MekkaPhoneViewModel

private data class MoreMenuItem(
    val titleArabic: String,
    val subtitleArabic: String,
    val route: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badgeText: String? = null,
    val testTag: String
)

/**
 * MEKKA PHONE V3 — More Screen (المزيد)
 * Strictly organized into:
 * 1. العمليات اليومية (العملاء، الموردين، المشتريات، الصيانة، الخزينة، المصروفات)
 * 2. الإدارة (التقارير، دفتر الأستاذ، التدقيق، المزامنة، النسخ الاحتياطي)
 * 3. النظام (الإعدادات، المستخدمون والصلاحيات، Diagnostics)
 */
@Composable
fun MoreScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit
) {
    val kpis by viewModel.dashboardKpis.collectAsStateWithLifecycle()
    val pendingOps by viewModel.pendingOperationsBundle.collectAsStateWithLifecycle()

    val dailyOperationsItems = listOf(
        MoreMenuItem(
            titleArabic = "العملاء",
            subtitleArabic = if (kpis.customerReceivablesMinor > 0L) {
                "مبالغ آجلة: ${Money.ofMinor(kpis.customerReceivablesMinor).formatArabic()}"
            } else {
                "إدارة حسابات العملاء والمديونيات والتحصيل"
            },
            route = MekkaDestination.CUSTOMERS.route,
            icon = Icons.Default.People,
            accentColor = MekkaEmeraldGreen,
            badgeText = if (kpis.customerReceivablesMinor > 0L) "مستحق" else null,
            testTag = "more_item_customers"
        ),
        MoreMenuItem(
            titleArabic = "الموردين",
            subtitleArabic = if (kpis.supplierPayablesMinor > 0L) {
                "مستحقات موردين: ${Money.ofMinor(kpis.supplierPayablesMinor).formatArabic()}"
            } else {
                "إدارة الموردين وكشوف الحساب وسداد الدفعات"
            },
            route = MekkaDestination.SUPPLIERS.route,
            icon = Icons.Default.LocalShipping,
            accentColor = MekkaBlueInfo,
            badgeText = if (kpis.supplierPayablesMinor > 0L) "مستحق" else null,
            testTag = "more_item_suppliers"
        ),
        MoreMenuItem(
            titleArabic = "المشتريات",
            subtitleArabic = "تسجيل فواتير الشراء وتوريد البضاعة للمخزون",
            route = MekkaDestination.PURCHASES.route,
            icon = Icons.Default.ShoppingCartCheckout,
            accentColor = MekkaEmeraldLight,
            testTag = "more_item_purchases"
        ),
        MoreMenuItem(
            titleArabic = "الصيانة",
            subtitleArabic = if (kpis.readyRepairsCount > 0) {
                "${kpis.readyRepairsCount} أجهزة جاهزة للتسليم • ${kpis.pendingRepairsCount} قيد الإصلاح"
            } else {
                "استلام أجهزة الصيانة ومتابعة الإصلاح والتسليم"
            },
            route = MekkaDestination.MAINTENANCE.route,
            icon = Icons.Default.Build,
            accentColor = MekkaGoldAccent,
            badgeText = if (kpis.readyRepairsCount > 0) "${kpis.readyRepairsCount} جاهز" else null,
            testTag = "more_item_maintenance"
        ),
        MoreMenuItem(
            titleArabic = "الخزينة",
            subtitleArabic = "الرصيد الحالي: ${Money.ofMinor(kpis.cashPositionMinor).formatArabic()}",
            route = MekkaDestination.CASH_DRAWER.route,
            icon = Icons.Default.AccountBalance,
            accentColor = MekkaEmeraldGreen,
            testTag = "more_item_cash_drawer"
        ),
        MoreMenuItem(
            titleArabic = "المصروفات",
            subtitleArabic = "مصروفات اليوم: ${Money.ofMinor(kpis.todayExpensesMinor).formatArabic()}",
            route = MekkaDestination.EXPENSES.route,
            icon = Icons.Default.RequestQuote,
            accentColor = MekkaCrimsonError,
            testTag = "more_item_expenses"
        )
    )

    val managementItems = listOf(
        MoreMenuItem(
            titleArabic = "مركز العمليات المعلقة والمتابعة",
            subtitleArabic = if (pendingOps.totalCount > 0) {
                "${pendingOps.totalCount} عملية تتطلب المراجعة أو الاعتماد أو المعالجة"
            } else {
                "جميع عمليات المزامنة والرسائل والاعتمادات مكتملة"
            },
            route = MekkaDestination.PENDING_OPERATIONS.route,
            icon = Icons.Default.FactCheck,
            accentColor = MekkaAmberWarning,
            badgeText = if (pendingOps.totalCount > 0) "${pendingOps.totalCount}" else null,
            testTag = "more_item_pending_operations"
        ),
        MoreMenuItem(
            titleArabic = "التقارير",
            subtitleArabic = "ملخص الأرباح والمبيعات وحركة المخزون والمحافظ",
            route = MekkaDestination.REPORTS.route,
            icon = Icons.Default.Assessment,
            accentColor = MekkaEmeraldGreen,
            testTag = "more_item_reports"
        ),
        MoreMenuItem(
            titleArabic = "دفتر الأستاذ",
            subtitleArabic = "السجل المالي الكامل للعمليات والقيود المحاسبية",
            route = MekkaDestination.LEDGER.route,
            icon = Icons.Default.ReceiptLong,
            accentColor = MekkaBlueInfo,
            testTag = "more_item_ledger"
        ),
        MoreMenuItem(
            titleArabic = "التدقيق",
            subtitleArabic = "سجل الرقابة والتعديلات والعمليات الحساسة",
            route = MekkaDestination.AUDIT.route,
            icon = Icons.Default.FactCheck,
            accentColor = MekkaGoldAccent,
            testTag = "more_item_audit"
        ),
        MoreMenuItem(
            titleArabic = "المزامنة",
            subtitleArabic = if (kpis.pendingSyncCount > 0) {
                "${kpis.pendingSyncCount} حركة بانتظار المزامنة"
            } else {
                "حالة المزامنة وتحديث البيانات"
            },
            route = MekkaDestination.SYNC.route,
            icon = Icons.Default.CloudSync,
            accentColor = MekkaBlueInfo,
            badgeText = if (kpis.pendingSyncCount > 0) "${kpis.pendingSyncCount}" else null,
            testTag = "more_item_sync"
        ),
        MoreMenuItem(
            titleArabic = "النسخ الاحتياطي",
            subtitleArabic = "حفظ واستعادة نسخة مشفرة وآمنة لبيانات المتجر",
            route = MekkaDestination.BACKUP.route,
            icon = Icons.Default.Backup,
            accentColor = MekkaEmeraldLight,
            badgeText = if (kpis.backupRequired) "مطلوب" else null,
            testTag = "more_item_backup"
        ),
        MoreMenuItem(
            titleArabic = "محرك الأتمتة الآمنة",
            subtitleArabic = "قواعد التنبيه التلقائي للمخزون وسقف المحافظ والملخص اليومي",
            route = MekkaDestination.SAFE_AUTOMATION.route,
            icon = Icons.Default.SmartToy,
            accentColor = MekkaGoldAccent,
            testTag = "more_item_safe_automation"
        ),
        MoreMenuItem(
            titleArabic = "سلامة وصيانة البيانات",
            subtitleArabic = "فحص الفروق النقدية والمخزنية وطابور المزامنة ومقترحات الإصلاح",
            route = MekkaDestination.DATA_INTEGRITY.route,
            icon = Icons.Default.VerifiedUser,
            accentColor = MekkaEmeraldGreen,
            testTag = "more_item_data_integrity"
        )
    )

    val systemItems = listOf(
        MoreMenuItem(
            titleArabic = "الإعدادات",
            subtitleArabic = "بيانات المحل والفواتير والضمان والتفضيلات",
            route = MekkaDestination.SETTINGS.route,
            icon = Icons.Default.Settings,
            accentColor = MekkaEmeraldGreen,
            testTag = "more_item_settings"
        ),
        MoreMenuItem(
            titleArabic = "المستخدمون والصلاحيات",
            subtitleArabic = "إدارة حسابات الموظفين والأدوار والصلاحيات",
            route = MekkaDestination.USERS_PERMISSIONS.route,
            icon = Icons.Default.AdminPanelSettings,
            accentColor = MekkaBlueInfo,
            testTag = "more_item_users_permissions"
        ),
        MoreMenuItem(
            titleArabic = "Diagnostics",
            subtitleArabic = "فحص سلامة البيانات وأدوات التشخيص المتقدمة",
            route = MekkaDestination.DIAGNOSTICS.route,
            icon = Icons.Default.BugReport,
            accentColor = MekkaAmberWarning,
            testTag = "more_item_diagnostics"
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("more_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MoreCategoryCard(
                sectionTitleArabic = "العمليات اليومية",
                sectionSubtitleArabic = "إدارة العملاء والموردين والمشتريات والصيانة والخزينة",
                items = dailyOperationsItems,
                onNavigateToRoute = onNavigateToRoute
            )
        }

        item {
            MoreCategoryCard(
                sectionTitleArabic = "الإدارة",
                sectionSubtitleArabic = "التقارير المالية وسجلات المحاسبة والنسخ الاحتياطي",
                items = managementItems,
                onNavigateToRoute = onNavigateToRoute
            )
        }

        item {
            MoreCategoryCard(
                sectionTitleArabic = "النظام",
                sectionSubtitleArabic = "إعدادات المتجر وصلاحيات المستخدمين والتشخيص",
                items = systemItems,
                onNavigateToRoute = onNavigateToRoute
            )
        }
    }
}

@Composable
private fun MoreCategoryCard(
    sectionTitleArabic: String,
    sectionSubtitleArabic: String,
    items: List<MoreMenuItem>,
    onNavigateToRoute: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = sectionTitleArabic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = sectionSubtitleArabic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToRoute(item.route) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag(item.testTag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(item.accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.titleArabic,
                                tint = item.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.titleArabic,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.subtitleArabic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.badgeText != null) {
                            MekkaStatusBadge(text = item.badgeText, color = item.accentColor)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "انتقال",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Users and Permissions Management Screen (المستخدمون والصلاحيات)
 * Shows active shop staff accounts and role matrix without exposing developer PINs or test switchers.
 */
@Composable
fun UsersAndPermissionsScreen(viewModel: MekkaPhoneViewModel) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val activeUserId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val isSessionLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()

    var showOnboardingDialog by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var targetUserForPinLogin by remember { mutableStateOf<com.example.database.entities.UserEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("users_permissions_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MekkaSectionHeader(
                title = "المستخدمون والصلاحيات والأمان",
                subtitle = "إدارة الموظفين، إعداد مكة فون، ومستويات الصلاحيات المعتمدة (RBAC)"
            )
        }

        // First-launch / Owner Setup card (Rule 8: إعداد مكة فون)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MekkaEmeraldGreen.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "إعداد مكة فون وحساب المالك",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        MekkaStatusBadge(
                            text = if (shopConfig.onboardingCompleted) "مُفعل برمز PIN خاص" else "متاح للإعداد",
                            color = MekkaEmeraldGreen
                        )
                    }
                    Text(
                        text = "ضبط اسم المالك، اسم المتجر، رقم الهاتف/الحساب، وتعيين رمز PIN السري المشفر (PBKDF2-HMAC-SHA256) دون الاعتماد على أي رموز افتراضية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showOnboardingDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_first_launch_setup_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                    ) {
                        Text("إعداد مكة فون وتعيين رمز PIN للمالك")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddStaffDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_add_staff_dialog_button")
                        ) {
                            Text("إضافة حساب موظف")
                        }
                        OutlinedButton(
                            onClick = { viewModel.lockSession() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("logout_lock_session_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MekkaCrimsonError)
                        ) {
                            Text(if (isSessionLocked) "الجلسة مقفلة" else "قفل الجلسة / خروج")
                        }
                    }
                }
            }
        }

        items(users, key = { it.localId }) { user ->
            val isCurrent = user.localId == activeUserId && !isSessionLocked
            val roleArabic = FriendlyTerminologyMapper.toFriendlyRoleArabic(user.roleCode)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MekkaEmeraldGreen.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = user.fullNameArabic,
                                    tint = MekkaEmeraldGreen
                                )
                            }
                            Column {
                                Text(
                                    text = user.fullNameArabic,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الدور الوظيفي: $roleArabic • الحالة: ${if (user.isActive) "نشط" else "موقوف"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MekkaStatusBadge(
                            text = if (isCurrent) "الجلسة الحالية" else if (user.isActive) "نشط" else "موقوف",
                            color = if (isCurrent) MekkaEmeraldGreen else if (user.isActive) MekkaBlueInfo else MekkaCrimsonError
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { targetUserForPinLogin = user },
                            enabled = user.isActive,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("login_with_pin_btn_${user.localId}")
                        ) {
                            Text(if (isCurrent) "إعادة المصادقة برمز PIN" else "دخول / تبديل برمز PIN")
                        }
                        if (user.localId != com.example.repository.MekkaPhoneRepository.DEFAULT_OWNER_USER_ID) {
                            OutlinedButton(
                                onClick = { viewModel.toggleUserActiveStatus(user.localId, !user.isActive) },
                                modifier = Modifier.testTag("toggle_user_status_btn_${user.localId}")
                            ) {
                                Text(if (user.isActive) "إيقاف" else "تفعيل")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            MekkaSectionHeader(
                title = "مستويات الصلاحيات المعتمدة (RBAC)",
                subtitle = "تُفرض الصلاحيات برمجياً في طبقة الأعمال والـ Repository وليس فقط بإخفاء الواجهة"
            )
        }

        items(roles, key = { it.localId }) { role ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = role.nameArabic,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        MekkaStatusBadge(text = role.roleCode, color = MekkaEmeraldLight)
                    }
                    Text(
                        text = role.descriptionArabic.ifBlank {
                            when (role.roleCode) {
                                "OWNER" -> "صلاحية كاملة على المبيعات والمشتريات والمحافظ والجرد والتقارير والنسخ الاحتياطي والقيود العكسية."
                                "CASHIER" -> "صلاحية تنفيذ المبيعات وتحصيل الدفعات وعمليات المحافظ اليومية واستلام الصيانة."
                                "TECHNICIAN" -> "صلاحية إدارة تذاكر الصيانة وصرف قطع الغيار وتحديث حالة الإصلاح."
                                "INVENTORY" -> "صلاحية إدارة فواتير المشتريات، تعريف الأصناف، جرد المخزن، وتتبع سيريالات IMEI."
                                else -> "صلاحية مخصصة"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showOnboardingDialog) {
        FirstLaunchOnboardingDialog(
            initialShopName = shopConfig.shopNameArabic,
            initialPhone = shopConfig.shopPhone,
            onDismiss = { showOnboardingDialog = false },
            onSubmit = { ownerName, shopName, phone, pin, confirmPin ->
                viewModel.completeFirstLaunchSetup(
                    ownerFullNameArabic = ownerName,
                    shopNameArabic = shopName,
                    phoneOrAccount = phone,
                    rawPin = pin,
                    confirmPin = confirmPin,
                    onSuccess = { showOnboardingDialog = false }
                )
            }
        )
    }

    if (showAddStaffDialog) {
        AddStaffUserDialog(
            onDismiss = { showAddStaffDialog = false },
            onSubmit = { fullName, username, phone, roleCode, pin ->
                viewModel.createStaffUser(
                    fullNameArabic = fullName,
                    username = username,
                    phone = phone,
                    roleCode = roleCode,
                    rawPin = pin,
                    onSuccess = { showAddStaffDialog = false }
                )
            }
        )
    }

    targetUserForPinLogin?.let { targetUser ->
        PinAuthenticationDialog(
            user = targetUser,
            onDismiss = { targetUserForPinLogin = null },
            onSubmitPin = { rawPin ->
                viewModel.authenticateWithPin(
                    userId = targetUser.localId,
                    rawPin = rawPin,
                    onSuccess = { targetUserForPinLogin = null }
                )
            }
        )
    }
}

@Composable
fun PinAuthenticationDialog(
    user: com.example.database.entities.UserEntity,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "مصادقة الجلسة برمز PIN",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أدخل رمز PIN السري لحساب (${user.fullNameArabic} — ${FriendlyTerminologyMapper.toFriendlyRoleArabic(user.roleCode)}):",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("رمز PIN السري") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_auth_dialog_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPin(pin) },
                enabled = pin.length >= 4,
                modifier = Modifier.testTag("pin_auth_dialog_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("تحقق ودخول")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun AddStaffUserDialog(
    onDismiss: () -> Unit,
    onSubmit: (fullName: String, username: String, phone: String, roleCode: String, pin: String) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("CASHIER") }
    var pin by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حساب موظف جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("الاسم الكامل للموظف") },
                    modifier = Modifier.fillMaxWidth().testTag("staff_fullname_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم (Username)") },
                    modifier = Modifier.fillMaxWidth().testTag("staff_username_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    modifier = Modifier.fillMaxWidth().testTag("staff_phone_input"),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASHIER" to "كاشير", "TECHNICIAN" to "فني صيانة", "INVENTORY" to "أمين مخزن").forEach { (code, label) ->
                        FilterChip(
                            selected = selectedRole == code,
                            onClick = { selectedRole = code },
                            label = { Text(label) },
                            modifier = Modifier.testTag("staff_role_chip_$code")
                        )
                    }
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("رمز PIN السري (4 أرقام على الأقل)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("staff_pin_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fullName, username, phone, selectedRole, pin) },
                modifier = Modifier.testTag("staff_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("إنشاء الحساب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * First-Launch Onboarding Setup Dialog (`إعداد مكة فون`, Part 3 Rule 8).
 * Fields:
 * - Owner name
 * - Shop name
 * - Phone/account
 * - PIN
 * - Confirm PIN
 */
@Composable
fun FirstLaunchOnboardingDialog(
    initialShopName: String = "مكة فون",
    initialPhone: String = "",
    onDismiss: () -> Unit,
    onSubmit: (ownerName: String, shopName: String, phone: String, pin: String, confirmPin: String) -> Unit
) {
    var ownerName by rememberSaveable { mutableStateOf("") }
    var shopName by rememberSaveable { mutableStateOf(initialShopName) }
    var phoneOrAccount by rememberSaveable { mutableStateOf(initialPhone) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إعداد مكة فون",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أدخل بيانات المالك والمتجر وعين رمز PIN السري الخاص بك لتأمين الجلسات:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("اسم المالك (Owner Name)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_owner_name_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("اسم المتجر (Shop Name)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_shop_name_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phoneOrAccount,
                    onValueChange = { phoneOrAccount = it },
                    label = { Text("رقم الهاتف / الحساب") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_phone_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("رمز PIN السري (4 أرقام على الأقل)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_pin_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("تأكيد رمز PIN السري") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_confirm_pin_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(ownerName, shopName, phoneOrAccount, pin, confirmPin) },
                modifier = Modifier.testTag("onboarding_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("حفظ وتفعيل الجلسة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * Isolated Diagnostics & Developer Tools Screen (Rule 4: Settings → Diagnostics / Developer Tools).
 * Houses:
 * - Data Integrity Health Check
 * - Developer Role Switcher (isolated from production screens)
 * - SMS Parser Diagnostic Tool (isolated from production Wallet/SMS review screen)
 * - Sync Queue Transient Failure Diagnostic Tool
 */
@Composable
fun DiagnosticsScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit = {}
) {
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val activeUserId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val diagnosticErrorLogs by viewModel.diagnosticErrorLogs.collectAsStateWithLifecycle()
    val auditDiagBundle by viewModel.auditDiagnosticsBundle.collectAsStateWithLifecycle()
    val offlineBundle by viewModel.offlineControlCenterBundle.collectAsStateWithLifecycle()

    var testSmsSender by rememberSaveable { mutableStateOf("") }
    var testSmsBody by rememberSaveable { mutableStateOf("") }
    var diagPinTargetUser by remember { mutableStateOf<com.example.database.entities.UserEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MekkaAmberWarning.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostics",
                        tint = MekkaAmberWarning
                    )
                    Column {
                        Text(
                            text = "أدوات التشخيص والمطورين (Diagnostics / Developer Tools)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "هذه الشاشة مخصصة لفحص سلامة قاعدة البيانات واختبار الصلاحيات ومعالج الرسائل في بيئة معزولة عن شاشات الكاشير اليومية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 0. V4 Part 2: Offline Control Center, Multi-Device Visibility & 12-Pillar Audit/Diagnostics Dashboard
        item {
            OfflineAndMultiDeviceCenterCard(
                bundle = offlineBundle,
                onToggleOnlineMode = { online -> viewModel.setNetworkOnlineMode(online) },
                onTriggerSync = { viewModel.triggerSyncCycle() }
            )
        }

        item {
            AuditDiagnosticsCenterCard(
                bundle = auditDiagBundle,
                onNavigateToRoute = onNavigateToRoute
            )
        }

        // 1. Data Integrity & Ledger Health Check
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Text(
                            text = "فحص سلامة البيانات وتوازن القيود",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.runDataIntegrityCheck() },
                            modifier = Modifier.testTag("run_integrity_check_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                        ) {
                            Text("تشغيل الفحص الآن")
                        }
                    }
                    integrityReport?.let { rep ->
                        val passCount = rep.items.count { it.status == HealthCheckLevel.PASS }
                        MekkaStatusBadge(
                            text = "الحالة العامة: ${rep.overallStatus.arabicLabel} ($passCount/${rep.items.size})",
                            color = if (rep.overallStatus == HealthCheckLevel.PASS) MekkaEmeraldGreen else MekkaAmberWarning
                        )
                        rep.items.forEach { chk ->
                            val isPass = chk.status == HealthCheckLevel.PASS
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chk.titleArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                MekkaStatusBadge(
                                    text = if (isPass) "سليم" else "تحذير",
                                    color = if (isPass) MekkaEmeraldGreen else MekkaCrimsonError
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. PIN-Authenticated RBAC Role Verification (never switches roles without PIN authentication)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MekkaBlueInfo)
                        Text(
                            text = "فحص صلاحيات الأدوار (RBAC Authenticated Inspector)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "لتبديل الجلسة وفحص صلاحيات دور آخر يلزم إدخال رمز PIN السري الخاص بالحساب (يمنع التبديل غير المصرح به):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        users.forEach { u ->
                            val selected = u.localId == activeUserId
                            OutlinedButton(
                                onClick = { diagPinTargetUser = u },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("diag_switch_user_${u.roleCode.lowercase()}"),
                                colors = if (selected) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MekkaEmeraldGreen.copy(alpha = 0.14f),
                                        contentColor = MekkaEmeraldGreen
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text(
                                    text = u.roleCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Isolated SMS Parser & Ingestion Diagnostic Tool (Rule 25: Sample/Test SMS tools live only in Settings -> Diagnostics)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "أداة فحص واختبار محلل رسائل المحافظ (SMS Parser Diagnostics)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اختر رسالة نموذجية للاختبار أو أدخل نص رسالة يدوياً لفحص محرك التحليل والمطابقة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                testSmsSender = "VF-Cash"
                                testSmsBody = "تم استلام مبلغ 1250.00 ج.م من رقم 01012345678 عبر فودافون كاش رقم العملية #VF${System.currentTimeMillis() % 100000}"
                            },
                            modifier = Modifier.weight(1f).testTag("diag_preset_vf_cash")
                        ) {
                            Text("عينة فودافون", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                testSmsSender = "InstaPay"
                                testSmsBody = "تم تحويل مبلغ 2500.00 ج.م عبر انستا باي إلى 01122334455 مرجع IPN${System.currentTimeMillis() % 100000}"
                            },
                            modifier = Modifier.weight(1f).testTag("diag_preset_instapay")
                        ) {
                            Text("عينة إنستا باي", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedTextField(
                        value = testSmsSender,
                        onValueChange = { testSmsSender = it },
                        label = { Text("اسم المرسل (مثال: VF-Cash أو InstaPay)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diag_sms_sender_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = testSmsBody,
                        onValueChange = { testSmsBody = it },
                        label = { Text("نص رسالة الـ SMS للفحص") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diag_sms_body_input"),
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            viewModel.ingestSmsMessage(
                                senderAddress = testSmsSender.trim(),
                                rawMessageBody = testSmsBody.trim(),
                                autoConfirm = false
                            )
                            testSmsBody = ""
                        },
                        enabled = testSmsSender.isNotBlank() && testSmsBody.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diag_submit_sms_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaBlueInfo)
                    ) {
                        Text("إرسال الرسالة لمحرك الفحص والمطابقة")
                    }
                }
            }
        }

        // 4. Centralized Error Logs (Rule 16: Technical details remain available through Diagnostics)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سجل التشخيص الفني للأخطاء (${diagnosticErrorLogs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (diagnosticErrorLogs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearDiagnosticErrorLogs() }) {
                                Text("مسح السجل")
                            }
                        }
                    }
                    if (diagnosticErrorLogs.isEmpty()) {
                        Text(
                            text = "لا توجد أخطاء فنية مسجلة في الجلسة الحالية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        diagnosticErrorLogs.forEach { err ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = err.userMessageArabic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    ExpandableTechnicalDetails(titleArabic = "التفاصيل الفنية للخطأ") {
                                        Text("الكود الفني: ${err.technicalCode}", style = MaterialTheme.typography.bodySmall)
                                        Text("نوع الاستثناء: ${err.technicalExceptionClass}", style = MaterialTheme.typography.bodySmall)
                                        Text("النص الفني: ${err.technicalDetails}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    diagPinTargetUser?.let { targetUser ->
        PinAuthenticationDialog(
            user = targetUser,
            onDismiss = { diagPinTargetUser = null },
            onSubmitPin = { rawPin ->
                viewModel.authenticateWithPin(
                    userId = targetUser.localId,
                    rawPin = rawPin,
                    onSuccess = { diagPinTargetUser = null }
                )
            }
        )
    }
}

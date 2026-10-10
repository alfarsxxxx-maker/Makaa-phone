package com.example.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * MEKKA PHONE V3 Navigation Architecture.
 * Bottom Navigation strictly contains the 5 cashier-focused tabs in exact order:
 * 1. الرئيسية (CONTROL_CENTER)
 * 2. بيع (SALES)
 * 3. المحافظ (WALLETS)
 * 4. المخزون (PRODUCTS)
 * 5. المزيد (MORE)
 *
 * All daily operations, management, and system/diagnostics stations are organized under المزيد (MORE).
 */
enum class MekkaDestination(
    val route: String,
    val titleArabic: String,
    val shortTitleArabic: String,
    val icon: ImageVector,
    val isPrimaryBottomTab: Boolean = false
) {
    CONTROL_CENTER(
        route = "control_center",
        titleArabic = "مركز تحكم مكة فون",
        shortTitleArabic = "الرئيسية",
        icon = Icons.Default.Dashboard,
        isPrimaryBottomTab = true
    ),
    SALES(
        route = "sales",
        titleArabic = "نقطة البيع (POS)",
        shortTitleArabic = "بيع",
        icon = Icons.Default.PointOfSale,
        isPrimaryBottomTab = true
    ),
    WALLETS(
        route = "wallets",
        titleArabic = "المحافظ الإلكترونية",
        shortTitleArabic = "المحافظ",
        icon = Icons.Default.AccountBalanceWallet,
        isPrimaryBottomTab = true
    ),
    PRODUCTS(
        route = "products",
        titleArabic = "المخزون والبضاعة",
        shortTitleArabic = "المخزون",
        icon = Icons.Default.Inventory2,
        isPrimaryBottomTab = true
    ),
    MORE(
        route = "more",
        titleArabic = "المزيد",
        shortTitleArabic = "المزيد",
        icon = Icons.Default.GridView,
        isPrimaryBottomTab = true
    ),
    CUSTOMERS(
        route = "customers",
        titleArabic = "العملاء",
        shortTitleArabic = "العملاء",
        icon = Icons.Default.People,
        isPrimaryBottomTab = false
    ),
    SUPPLIERS(
        route = "suppliers",
        titleArabic = "الموردين",
        shortTitleArabic = "الموردين",
        icon = Icons.Default.LocalShipping,
        isPrimaryBottomTab = false
    ),
    PURCHASES(
        route = "purchases",
        titleArabic = "المشتريات",
        shortTitleArabic = "المشتريات",
        icon = Icons.Default.ShoppingCartCheckout,
        isPrimaryBottomTab = false
    ),
    MAINTENANCE(
        route = "maintenance",
        titleArabic = "الصيانة",
        shortTitleArabic = "الصيانة",
        icon = Icons.Default.Build,
        isPrimaryBottomTab = false
    ),
    CASH_DRAWER(
        route = "cash_drawer",
        titleArabic = "الخزينة",
        shortTitleArabic = "الخزينة",
        icon = Icons.Default.AccountBalance,
        isPrimaryBottomTab = false
    ),
    EXPENSES(
        route = "expenses",
        titleArabic = "المصروفات",
        shortTitleArabic = "المصروفات",
        icon = Icons.Default.RequestQuote,
        isPrimaryBottomTab = false
    ),
    SMS_INTELLIGENCE(
        route = "sms_intelligence",
        titleArabic = "مراجعة رسائل المحافظ",
        shortTitleArabic = "رسائل المحافظ",
        icon = Icons.Default.Sms,
        isPrimaryBottomTab = false
    ),
    REPORTS(
        route = "reports",
        titleArabic = "التقارير",
        shortTitleArabic = "التقارير",
        icon = Icons.Default.Assessment,
        isPrimaryBottomTab = false
    ),
    LEDGER(
        route = "ledger",
        titleArabic = "دفتر الأستاذ",
        shortTitleArabic = "دفتر الأستاذ",
        icon = Icons.Default.ReceiptLong,
        isPrimaryBottomTab = false
    ),
    AUDIT(
        route = "audit",
        titleArabic = "التدقيق",
        shortTitleArabic = "التدقيق",
        icon = Icons.Default.ReceiptLong,
        isPrimaryBottomTab = false
    ),
    SYNC(
        route = "sync",
        titleArabic = "المزامنة",
        shortTitleArabic = "المزامنة",
        icon = Icons.Default.Backup,
        isPrimaryBottomTab = false
    ),
    OPERATIONS(
        route = "operations",
        titleArabic = "دفتر الأستاذ والتدقيق والمزامنة",
        shortTitleArabic = "دفتر الأستاذ",
        icon = Icons.Default.ReceiptLong,
        isPrimaryBottomTab = false
    ),
    BACKUP(
        route = "backup",
        titleArabic = "النسخ الاحتياطي والاستعادة",
        shortTitleArabic = "النسخ الاحتياطي",
        icon = Icons.Default.Backup,
        isPrimaryBottomTab = false
    ),
    SETTINGS(
        route = "settings",
        titleArabic = "الإعدادات",
        shortTitleArabic = "الإعدادات",
        icon = Icons.Default.Settings,
        isPrimaryBottomTab = false
    ),
    USERS_PERMISSIONS(
        route = "users_permissions",
        titleArabic = "المستخدمون والصلاحيات",
        shortTitleArabic = "المستخدمون والصلاحيات",
        icon = Icons.Default.AdminPanelSettings,
        isPrimaryBottomTab = false
    ),
    DIAGNOSTICS(
        route = "diagnostics",
        titleArabic = "التشخيص وأدوات المطورين (Diagnostics)",
        shortTitleArabic = "Diagnostics",
        icon = Icons.Default.BugReport,
        isPrimaryBottomTab = false
    ),
    PENDING_OPERATIONS(
        route = "pending_operations",
        titleArabic = "مركز العمليات المعلقة والمتابعة",
        shortTitleArabic = "العمليات المعلقة",
        icon = Icons.Default.ReceiptLong,
        isPrimaryBottomTab = false
    ),
    SAFE_AUTOMATION(
        route = "safe_automation",
        titleArabic = "محرك الأتمتة الآمنة",
        shortTitleArabic = "الأتمتة الآمنة",
        icon = Icons.Default.SmartToy,
        isPrimaryBottomTab = false
    ),
    DATA_INTEGRITY(
        route = "data_integrity",
        titleArabic = "مركز سلامة وصيانة البيانات",
        shortTitleArabic = "سلامة البيانات",
        icon = Icons.Default.VerifiedUser,
        isPrimaryBottomTab = false
    );

    companion object {
        val primaryBottomTabs: List<MekkaDestination> = listOf(
            CONTROL_CENTER,
            SALES,
            WALLETS,
            PRODUCTS,
            MORE
        )

        val dailyOperationsDestinations: List<MekkaDestination> = listOf(
            PENDING_OPERATIONS,
            CUSTOMERS,
            SUPPLIERS,
            PURCHASES,
            MAINTENANCE,
            CASH_DRAWER,
            EXPENSES
        )

        val managementDestinations: List<MekkaDestination> = listOf(
            REPORTS,
            LEDGER,
            AUDIT,
            SYNC,
            BACKUP,
            SAFE_AUTOMATION,
            DATA_INTEGRITY
        )

        val systemDestinations: List<MekkaDestination> = listOf(
            SETTINGS,
            USERS_PERMISSIONS,
            DIAGNOSTICS
        )
    }
}

object MekkaRoute {
    const val LOGIN_PIN = "login_pin"
    const val DASHBOARD = "dashboard"
    const val SALES_POS = "sales_pos"
    const val PURCHASES = "purchases"
    const val INVENTORY = "inventory"
    const val WALLETS = "wallets"
    const val CASH_DRAWER = "cash_drawer"
    const val MAINTENANCE = "maintenance"
    const val EXPENSES = "expenses"
    const val CUSTOMERS = "customers"
    const val SUPPLIERS = "suppliers"
    const val REPORTS = "reports"
    const val SMS_REVIEW = "sms_review"
    const val LEDGER = "ledger"
    const val AUDIT_LOGS = "audit_logs"
    const val CONFLICTS = "conflicts"
    const val BACKUP = "backup"
    const val DIAGNOSTICS = "diagnostics"
    const val USERS_AND_ROLES = "users_and_roles"
    const val SHOP_SETTINGS = "shop_settings"
    const val FIRST_LAUNCH_ONBOARDING = "first_launch_onboarding"
    const val MORE = "more"
    const val PENDING_OPERATIONS = "pending_operations"
    const val SAFE_AUTOMATION = "safe_automation"
    const val DATA_INTEGRITY = "data_integrity"
}


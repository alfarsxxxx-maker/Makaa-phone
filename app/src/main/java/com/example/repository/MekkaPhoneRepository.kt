package com.example.repository

import androidx.room.withTransaction
import com.example.auth.AuthSecurityManager
import com.example.auth.PermissionCode
import com.example.backup.BackupEngine
import com.example.backup.BackupValidationReport
import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.core.SyncState
import com.example.database.MekkaPhoneDatabase
import com.example.database.dao.CashDrawerDerivedProjection
import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.SmsReviewItemProjection
import com.example.database.dao.SupplierBalanceProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.AppSettingEntity
import com.example.database.entities.AuditLogEntity
import com.example.database.migrations.DatabaseMigrations
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.BrandEntity
import com.example.database.entities.CashDrawerEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.DeviceEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.PermissionEntity
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.ProductEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.RoleEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SerialLifecycleStatus
import com.example.database.entities.SerialNumberEntity
import com.example.database.entities.ShopEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.database.entities.UserEntity
import com.example.database.entities.UserRoleType
import com.example.database.entities.WalletEntity
import com.example.database.entities.WalletProviderEntity
import com.example.diagnostics.DataIntegrityChecker
import com.example.diagnostics.SystemIntegrityReport
import com.example.financial.AccountingEngine
import com.example.financial.AdvancedReturnEvaluation
import com.example.financial.AdvancedReturnLineInput
import com.example.financial.AdvancedReturnsPolicyEngine
import com.example.financial.CreditSaleEligibility
import com.example.financial.CustomerCreditAgingEngine
import com.example.financial.CustomerCreditStanding
import com.example.financial.DailyOperationalZReport
import com.example.financial.DailyOperationalZReportEngine
import com.example.financial.FinancialLedgerEngine
import com.example.financial.Money
import com.example.financial.PurchaseLineInput
import com.example.financial.ReportsCenterBundle
import com.example.financial.ReportsCenterEngine
import com.example.financial.ReportsCenterFilter
import com.example.financial.ReturnConditionType
import com.example.financial.ReturnSettlementMode
import com.example.financial.SaleLineInput
import com.example.financial.WalletOperationType
import com.example.financial.SalePaymentSplit
import com.example.financial.ShiftCashFlowBreakdown
import com.example.financial.ShiftHandoverRecord
import com.example.financial.ShiftManagementEngine
import com.example.financial.SupplierPayablesScheduleEngine
import com.example.financial.SupplierPayablesSummary
import com.example.financial.TrialBalanceReport
import com.example.financial.TrueProfitabilityEngine
import com.example.financial.TrueProfitabilityReport
import com.example.inventory.ImeiLifecycleEvent
import com.example.inventory.ImeiRegistryProjection
import com.example.inventory.ImeiTraceabilityTimeline
import com.example.inventory.InventoryEngine
import com.example.inventory.PricingPolicyEngine
import com.example.inventory.PricingTier
import com.example.inventory.ProductReorderAndVelocityReport
import com.example.inventory.ProductVelocityClass
import com.example.inventory.StockCountLineInput
import com.example.inventory.StockCountLineVariance
import com.example.inventory.StockCountReconciliationReport
import com.example.intelligence.AuditDiagnosticsDashboardBundle
import com.example.intelligence.CashAndWalletControlCenterBundle
import com.example.intelligence.Customer360Snapshot
import com.example.intelligence.InventoryAnalyticsDashboardBundle
import com.example.intelligence.MaintenanceManagementCenterBundle
import com.example.intelligence.OfflineControlCenterBundle
import com.example.intelligence.OperationalAnalyticsEngine
import com.example.intelligence.OperationalCentersEngine
import com.example.intelligence.PendingOperationsCenterBundle
import com.example.intelligence.RealReportExportEngine
import com.example.intelligence.ReportExportFormat
import com.example.intelligence.ReportExportResult
import com.example.intelligence.SmartNotificationCenterBundle
import com.example.intelligence.Supplier360Snapshot
import com.example.intelligence.V4ReportFilter
import com.example.intelligence.V4ReportingEngine
import com.example.intelligence.V4TabularReportDocument
import com.example.navigation.MekkaRoute
import com.example.sms.SmsIntelligenceEngine
import com.example.sms.SmsPipelineOutcome
import com.example.sync.SyncBatchExecutionReport
import com.example.sync.SyncCoordinator
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Live Dashboard KPIs derived 100% from Room operational & ledger queries (Requirements 25 & 30).
 */
data class DashboardLiveKpis(
    val todaySalesMinor: Long = 0L,
    val todayExpensesMinor: Long = 0L,
    val cashPositionMinor: Long = 0L,
    val totalWalletsBalanceMinor: Long = 0L,
    val customerReceivablesMinor: Long = 0L,
    val supplierPayablesMinor: Long = 0L,
    val lowStockProductsCount: Int = 0,
    val pendingRepairsCount: Int = 0,
    val readyRepairsCount: Int = 0,
    val pendingSmsCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val failedSyncCount: Int = 0,
    val unresolvedConflictsCount: Int = 0,
    val todayGrossProfitMinor: Long = 0L,
    val todayNetProfitIndicatorMinor: Long = 0L,
    val lastCashDiscrepancyMinor: Long = 0L,
    val backupRequired: Boolean = false
)

/**
 * Comprehensive Financial & Inventory Report (Requirement 29).
 */
data class MonthlyFinancialReport(
    val todaySalesMinor: Long = 0L,
    val todayPurchasesMinor: Long = 0L,
    val todayExpensesMinor: Long = 0L,
    val monthSalesMinor: Long = 0L,
    val monthPurchasesMinor: Long = 0L,
    val monthExpensesMinor: Long = 0L,
    val monthCommissionsMinor: Long = 0L,
    val totalInventoryValuationMinor: Long = 0L
)

/**
 * Unified Global Search result item (Requirement 32).
 */
data class GlobalSearchResultItem(
    val categoryArabic: String,
    val title: String,
    val subtitle: String,
    val referenceCode: String,
    val routeTarget: String,
    val statusArabic: String = "نشط",
    val timestamp: Long = 0L,
    val dateFormatted: String = "",
    val entityId: String = ""
)

/**
 * Internal Alert item (Requirement 33).
 */
data class SystemAlertItem(
    val code: String,
    val titleArabic: String,
    val severity: String, // INFO, WARNING, CRITICAL
    val routeTarget: String
)

/**
 * Comprehensive Shop & Application Configuration Profile (persisted in `shops` and `app_settings`).
 */
data class ShopConfigurationProfile(
    val shopNameArabic: String = "مكة فون",
    val shopNameEnglish: String = "MEKKA PHONE",
    val shopCode: String = "MK-001",
    val shopPhone: String = "01000000000",
    val shopAddress: String = "الفرع الرئيسي",
    val taxRegistrationNumber: String = "",
    val defaultCurrency: String = "EGP",
    val themeMode: String = "LIGHT",           // LIGHT, DARK, SYSTEM
    val accentPalette: String = "EMERALD",     // EMERALD, ROYAL_BLUE, GOLD, SLATE
    val uiDensity: String = "COMFORTABLE",     // COMPACT, COMFORTABLE, LARGE
    val defaultTaxPercent: String = "0",       // e.g. 0, 14, 15
    val receiptHeaderNote: String = "أهلاً بكم في مكة فون لخدمات الهواتف والدفع الإلكتروني",
    val receiptFooterNote: String = "شكراً لتعاملكم معنا — يرجى الاحتفاظ بالفاتورة للضمان",
    val defaultWarrantyDays: String = "14",
    val defaultWarrantyMonths: Int = 12,
    val allowCreditSales: Boolean = true,
    val requireCustomerForCredit: Boolean = true,
    val showProfitAnalysisOnPos: Boolean = true,
    val defaultCustomerCreditLimitMajor: String = "10000",
    val defaultCustomerCreditLimitMinor: Long = 1_000_000L, // 10,000.00 EGP
    val defaultLowStockThreshold: Int = 2,
    val defaultWalletDailyLimitMajor: String = "60000",
    val defaultWalletMonthlyLimitMajor: String = "200000",
    val defaultWalletCommissionBps: Int = 100, // 1.00%
    val autoCaptureSmsEnabled: Boolean = true,
    val smsAutoCaptureEnabled: Boolean = true,
    val smsHighConfidenceAutoConfirm: Boolean = false,
    val smsHighConfidenceThresholdBps: Int = 8500,
    val autoSyncOnFinancialPost: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val autoDailyBackupEnabled: Boolean = true,
    val printerType: String = "THERMAL_80MM",
    val paperSize: String = "80mm",
    val fiscalYearStartMonth: String = "1",
    val cloudSyncEndpointUrl: String = "",
    val cloudSyncWifiOnly: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val requireSerialOnDevices: Boolean = true
)

class MekkaPhoneRepository(
    val database: MekkaPhoneDatabase
) {
    val authSecurityManager = AuthSecurityManager(database.coreShopAuthDao())
    val inventoryEngine = InventoryEngine(database.catalogInventoryDao())
    val syncCoordinator = SyncCoordinator(database.systemIntegrityDao())
    val backupEngine = BackupEngine(
        database = database,
        authSecurityManager = authSecurityManager
    )
    val financialLedgerEngine = FinancialLedgerEngine(
        database = database,
        inventoryEngine = inventoryEngine,
        authSecurityManager = authSecurityManager,
        syncCoordinator = syncCoordinator
    )
    val smsIntelligenceEngine = SmsIntelligenceEngine(
        database = database,
        financialLedgerEngine = financialLedgerEngine
    )
    val capitalManagementEngine = com.example.financial.CapitalManagementEngine(
        database = database,
        financialLedgerEngine = financialLedgerEngine
    )
    val dataIntegrityChecker = DataIntegrityChecker(database)
    val smartCategorizationEngine = com.example.transaction.SmartCategorizationEngine(database)
    val businessLimitsManager = com.example.transaction.BusinessLimitsManager(database)
    val receiptOcrEngine = com.example.transaction.ReceiptOcrEngine(database)
    val transactionParser = com.example.transaction.IntelligentTransactionParser(database, smartCategorizationEngine)
    val transactionValidationEngine = com.example.transaction.TransactionValidationEngine(database, authSecurityManager, businessLimitsManager)
    val transactionCoordinator by lazy {
        com.example.transaction.UnifiedTransactionExecutionCoordinator(this, transactionValidationEngine)
    }
    val groundedAiAssistantEngine by lazy {
        com.example.ai.GroundedAiAssistantEngine(database, authSecurityManager)
    }

    suspend fun queryMekkaAiAssistant(
        question: String,
        shopId: String = DEFAULT_SHOP_ID
    ): com.example.ai.AiAssistantResponse =
        groundedAiAssistantEngine.processUserInquiry(question, shopId)

    companion object {
        const val DEFAULT_SHOP_ID = "SHOP-MEKKA-MAIN"
        const val DEFAULT_DEVICE_ID = "DEV-ANDROID-PRIMARY"
        const val DEFAULT_MAIN_DRAWER_ID = "DRAWER-MAIN-EGP"
        const val DEFAULT_OWNER_USER_ID = "USR-OWNER-PRIMARY"
        const val DEFAULT_CASHIER_USER_ID = "USR-CASHIER-01"
        const val DEFAULT_TECH_USER_ID = "USR-TECH-01"
        const val DEFAULT_INVENTORY_USER_ID = "USR-INVENTORY-01"

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_PALETTE = "accent_palette"
        const val KEY_UI_DENSITY = "ui_density"
        const val KEY_DEFAULT_TAX_PERCENT = "default_tax_percent"
        const val KEY_DEFAULT_WARRANTY_DAYS = "default_warranty_days"
        const val KEY_DEFAULT_CUSTOMER_CREDIT_LIMIT = "default_customer_credit_limit_major"
        const val KEY_RECEIPT_HEADER_NOTE = "receipt_header_note"
        const val KEY_RECEIPT_FOOTER_NOTE = "receipt_footer_note"
        const val KEY_ALLOW_CREDIT_SALES = "allow_credit_sales"
        const val KEY_REQUIRE_CUSTOMER_FOR_CREDIT = "require_customer_for_credit"
        const val KEY_SHOW_PROFIT_ON_POS = "show_profit_on_pos"
        const val KEY_LOW_STOCK_THRESHOLD = "default_low_stock_threshold"
        const val KEY_WALLET_DAILY_LIMIT = "wallet_daily_limit_major"
        const val KEY_WALLET_MONTHLY_LIMIT = "wallet_monthly_limit_major"
        const val KEY_AUTO_CAPTURE_SMS = "sms_auto_capture_enabled"
        const val KEY_SMS_HIGH_CONFIDENCE_AUTO = "sms_high_confidence_auto_confirm"
        const val KEY_AUTO_SYNC_ON_POST = "auto_sync_enabled"
        const val KEY_PRINTER_TYPE = "printer_type"
        const val KEY_PAPER_SIZE = "paper_size"
        const val KEY_FISCAL_YEAR_START_MONTH = "fiscal_year_start_month"
        const val KEY_CLOUD_SYNC_ENDPOINT_URL = "cloud_sync_endpoint_url"
        const val KEY_CLOUD_SYNC_WIFI_ONLY = "cloud_sync_wifi_only"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_REQUIRE_SERIAL_ON_DEVICES = "require_serial_on_devices"
    }

    val activeShopId: String
        get() = authSecurityManager.activeSession.value?.shopId ?: DEFAULT_SHOP_ID

    private var _overrideActiveUserId: String? = null

    val activeUserId: String
        get() = authSecurityManager.activeSession.value?.userId ?: _overrideActiveUserId ?: DEFAULT_OWNER_USER_ID

    val activeDeviceId: String
        get() = authSecurityManager.activeSession.value?.installationId ?: DEFAULT_DEVICE_ID

    val isSessionLocked: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = authSecurityManager.isSessionLocked

    val activeSession: kotlinx.coroutines.flow.StateFlow<com.example.auth.AuthenticatedSession?>
        get() = authSecurityManager.activeSession

    fun switchActiveUser(userId: String) {
        if (authSecurityManager.isSessionLocked.value) return
        val currentSession = authSecurityManager.activeSession.value
        if (currentSession != null && currentSession.userId != userId) return
        if (activeUserId != DEFAULT_OWNER_USER_ID && userId != activeUserId) return
        _overrideActiveUserId = userId
    }

    suspend fun authenticateUserPin(
        userId: String,
        rawPin: String,
        shopId: String = DEFAULT_SHOP_ID
    ): AppResult<com.example.auth.AuthenticatedSession> {
        val res = authSecurityManager.authenticateWithPin(
            userId = userId,
            rawPin = rawPin,
            expectedShopId = shopId,
            installationId = activeDeviceId
        )
        if (res is AppResult.Success) {
            _overrideActiveUserId = res.data.userId
        }
        return res
    }

    fun lockSession() {
        authSecurityManager.signOut()
    }

    /**
     * Initializes the shop tenant, RBAC roles/permissions, PBKDF2 user PIN credentials, installation identity,
     * structural accounts (Main Cash Drawer, 5 Wallet Providers), and base catalog categories WITHOUT any fake financial balances.
     */
    suspend fun ensureFoundationInitialized(timestamp: Long = System.currentTimeMillis()) {
        val coreDao = database.coreShopAuthDao()
        val existingShop = coreDao.getShopById(DEFAULT_SHOP_ID)
        if (existingShop != null) {
            // Ensure Axis Pay provider is also present if upgrading from Part 1
            val axisProvider = database.financialLedgerDao().getWalletProviderByCode(DEFAULT_SHOP_ID, "AXIS_PAY")
            if (axisProvider == null) {
                database.financialLedgerDao().insertWalletProviders(
                    listOf(
                        WalletProviderEntity(
                            localId = "PRV-AXISPAY",
                            shopId = DEFAULT_SHOP_ID,
                            providerCode = "AXIS_PAY",
                            nameArabic = "أكسس باي (Axis Pay)",
                            defaultFeeBasisPoints = 50,
                            defaultCommissionBasisPoints = 100,
                            smsSenderFilter = "AxisPay",
                            deviceId = DEFAULT_DEVICE_ID,
                            createdAt = timestamp,
                            updatedAt = timestamp
                        )
                    )
                )
            }
            val inst = authSecurityManager.ensureInstallationIdentity(
                shopId = DEFAULT_SHOP_ID,
                ownerUserId = DEFAULT_OWNER_USER_ID,
                timestamp = timestamp
            )
            if (authSecurityManager.activeSession.value == null) {
                val isOnboardingDone = coreDao.getSetting(DEFAULT_SHOP_ID, KEY_ONBOARDING_COMPLETED)?.settingValue == "true"
                if (!isOnboardingDone) {
                    authSecurityManager.establishSessionForUser(
                        userId = DEFAULT_OWNER_USER_ID,
                        expectedShopId = DEFAULT_SHOP_ID,
                        installationId = inst.installationId,
                        timestamp = timestamp
                    )
                } else {
                    // Production Gate (Requirement 1): No automatic owner session bypass after onboarding.
                    // Real authentication is required to unlock the session.
                    authSecurityManager.signOut()
                }
            }
            return
        }

        database.withTransaction {
            val shop = ShopEntity(
                localId = DEFAULT_SHOP_ID,
                code = "MK-001",
                nameArabic = "مكة فون للهواتف والخدمات الإلكترونية",
                nameEnglish = "Mekka Phone Flagship",
                defaultCurrency = "EGP",
                phone = "01000000000",
                address = "الفرع الرئيسي",
                deviceId = DEFAULT_DEVICE_ID,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            coreDao.insertShop(shop)

            val rolesMap = authSecurityManager.ensureStandardRolesAndPermissions(
                shopId = DEFAULT_SHOP_ID,
                deviceId = DEFAULT_DEVICE_ID,
                timestamp = timestamp
            )

            val ownerRole = rolesMap.getValue(UserRoleType.OWNER)
            val cashierRole = rolesMap.getValue(UserRoleType.CASHIER)
            val techRole = rolesMap.getValue(UserRoleType.TECHNICIAN)
            val inventoryRole = rolesMap.getValue(UserRoleType.INVENTORY)

            coreDao.insertUser(
                UserEntity(
                    localId = DEFAULT_OWNER_USER_ID,
                    shopId = DEFAULT_SHOP_ID,
                    roleId = ownerRole.localId,
                    roleCode = UserRoleType.OWNER.name,
                    username = "owner",
                    fullNameArabic = "مدير النظام (المالك)",
                    pinHashSha256 = authSecurityManager.hashOperationalPin(
                        shopId = DEFAULT_SHOP_ID,
                        userId = DEFAULT_OWNER_USER_ID,
                        rawPin = "1111"
                    ),
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            coreDao.insertUser(
                UserEntity(
                    localId = DEFAULT_CASHIER_USER_ID,
                    shopId = DEFAULT_SHOP_ID,
                    roleId = cashierRole.localId,
                    roleCode = UserRoleType.CASHIER.name,
                    username = "cashier",
                    fullNameArabic = "مسؤول الخزينة والمبيعات",
                    pinHashSha256 = authSecurityManager.hashOperationalPin(
                        shopId = DEFAULT_SHOP_ID,
                        userId = DEFAULT_CASHIER_USER_ID,
                        rawPin = "2222"
                    ),
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            coreDao.insertUser(
                UserEntity(
                    localId = DEFAULT_TECH_USER_ID,
                    shopId = DEFAULT_SHOP_ID,
                    roleId = techRole.localId,
                    roleCode = UserRoleType.TECHNICIAN.name,
                    username = "technician",
                    fullNameArabic = "مهندس الصيانة",
                    pinHashSha256 = authSecurityManager.hashOperationalPin(
                        shopId = DEFAULT_SHOP_ID,
                        userId = DEFAULT_TECH_USER_ID,
                        rawPin = "3333"
                    ),
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            coreDao.insertUser(
                UserEntity(
                    localId = DEFAULT_INVENTORY_USER_ID,
                    shopId = DEFAULT_SHOP_ID,
                    roleId = inventoryRole.localId,
                    roleCode = UserRoleType.INVENTORY.name,
                    username = "inventory",
                    fullNameArabic = "مسؤول المخزن والمشتريات",
                    pinHashSha256 = authSecurityManager.hashOperationalPin(
                        shopId = DEFAULT_SHOP_ID,
                        userId = DEFAULT_INVENTORY_USER_ID,
                        rawPin = "4444"
                    ),
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )

            val inst = authSecurityManager.ensureInstallationIdentity(
                shopId = DEFAULT_SHOP_ID,
                ownerUserId = DEFAULT_OWNER_USER_ID,
                timestamp = timestamp
            )

            coreDao.insertDevice(
                DeviceEntity(
                    localId = DEFAULT_DEVICE_ID,
                    shopId = DEFAULT_SHOP_ID,
                    deviceName = "جهاز نقطة البيع الرئيسي",
                    hardwareFingerprint = inst.installationId,
                    osVersion = "Android",
                    appVersion = "2.0.0-production",
                    isCurrentDevice = true,
                    isAuthorized = true,
                    lastActiveAt = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )

            authSecurityManager.establishSessionForUser(
                userId = DEFAULT_OWNER_USER_ID,
                expectedShopId = DEFAULT_SHOP_ID,
                installationId = inst.installationId,
                timestamp = timestamp
            )

            database.financialLedgerDao().insertCashDrawer(
                CashDrawerEntity(
                    localId = DEFAULT_MAIN_DRAWER_ID,
                    shopId = DEFAULT_SHOP_ID,
                    drawerCode = "DRW-01",
                    nameArabic = "الخزينة النقدية الرئيسية",
                    currency = "EGP",
                    isMainDrawer = true,
                    isActive = true,
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )

            // All 5 required Electronic Wallet Providers (Vodafone Cash, Orange Cash, e& money, InstaPay, Axis Pay)
            val providers = listOf(
                WalletProviderEntity(
                    localId = "PRV-VODAFONE",
                    shopId = DEFAULT_SHOP_ID,
                    providerCode = "VODAFONE_CASH",
                    nameArabic = "فودافون كاش (Vodafone Cash)",
                    defaultFeeBasisPoints = 100,
                    defaultCommissionBasisPoints = 100,
                    smsSenderFilter = "VF-Cash",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                ),
                WalletProviderEntity(
                    localId = "PRV-ORANGE",
                    shopId = DEFAULT_SHOP_ID,
                    providerCode = "ORANGE_CASH",
                    nameArabic = "أورنج كاش (Orange Cash)",
                    defaultFeeBasisPoints = 100,
                    defaultCommissionBasisPoints = 100,
                    smsSenderFilter = "OrangeCash",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                ),
                WalletProviderEntity(
                    localId = "PRV-ETISALAT",
                    shopId = DEFAULT_SHOP_ID,
                    providerCode = "ETISALAT_CASH",
                    nameArabic = "اتصالات كاش (e& money)",
                    defaultFeeBasisPoints = 100,
                    defaultCommissionBasisPoints = 100,
                    smsSenderFilter = "e&money",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                ),
                WalletProviderEntity(
                    localId = "PRV-INSTAPAY",
                    shopId = DEFAULT_SHOP_ID,
                    providerCode = "INSTAPAY",
                    nameArabic = "إنستا باي (InstaPay)",
                    defaultFeeBasisPoints = 0,
                    defaultCommissionBasisPoints = 50,
                    smsSenderFilter = "InstaPay",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                ),
                WalletProviderEntity(
                    localId = "PRV-AXISPAY",
                    shopId = DEFAULT_SHOP_ID,
                    providerCode = "AXIS_PAY",
                    nameArabic = "أكسس باي (Axis Pay)",
                    defaultFeeBasisPoints = 50,
                    defaultCommissionBasisPoints = 100,
                    smsSenderFilter = "AxisPay",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            database.financialLedgerDao().insertWalletProviders(providers)

            val catalogDao = database.catalogInventoryDao()
            catalogDao.insertCategory(
                CategoryEntity(
                    localId = "CAT-PHONES",
                    shopId = DEFAULT_SHOP_ID,
                    nameArabic = "هواتف ذكية وأجهزة لوحية",
                    sortOrder = 1,
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            catalogDao.insertCategory(
                CategoryEntity(
                    localId = "CAT-ACCESSORIES",
                    shopId = DEFAULT_SHOP_ID,
                    nameArabic = "إكسسوارات وشواحن وكابلات",
                    sortOrder = 2,
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            catalogDao.insertCategory(
                CategoryEntity(
                    localId = "CAT-PARTS",
                    shopId = DEFAULT_SHOP_ID,
                    nameArabic = "قطع غيار صيانة وشاشات",
                    sortOrder = 3,
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )

            catalogDao.insertBrand(
                BrandEntity(
                    localId = "BRD-SAMSUNG",
                    shopId = DEFAULT_SHOP_ID,
                    name = "Samsung",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            catalogDao.insertBrand(
                BrandEntity(
                    localId = "BRD-APPLE",
                    shopId = DEFAULT_SHOP_ID,
                    name = "Apple",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            catalogDao.insertBrand(
                BrandEntity(
                    localId = "BRD-ANKER",
                    shopId = DEFAULT_SHOP_ID,
                    name = "Anker",
                    deviceId = DEFAULT_DEVICE_ID,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
        }
    }

    fun observeDashboardLiveKpis(shopId: String = DEFAULT_SHOP_ID): Flow<DashboardLiveKpis> {
        val (startOfDay, endOfDay) = getTodayBoundsMillis()
        val ledgerDao = database.financialLedgerDao()
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val sysDao = database.systemIntegrityDao()

        val financialFlow = combine(
            ledgerDao.observeSalesTotalForRange(shopId, startOfDay, endOfDay),
            ledgerDao.observeExpensesTotalForRange(shopId, startOfDay, endOfDay),
            ledgerDao.observeTotalCashPositionMinor(shopId),
            ledgerDao.observeTotalWalletsBalanceMinor(shopId)
        ) { sales, expenses, cash, wallets ->
            longArrayOf(sales, expenses, cash, wallets)
        }

        val balancesFlow = combine(
            catalogDao.observeCustomerBalanceProjections(shopId),
            catalogDao.observeSupplierBalanceProjections(shopId)
        ) { custList, supList ->
            val custRec = custList.sumOf { it.outstandingBalanceMinor.coerceAtLeast(0L) }
            val supPay = supList.sumOf { it.outstandingPayableMinor.coerceAtLeast(0L) }
            custRec to supPay
        }

        val operationalFlow1 = combine(
            catalogDao.observeLowStockProductCount(shopId),
            opsDao.observePendingRepairsCount(shopId),
            opsDao.observeReadyRepairsCount(shopId),
            opsDao.observeAwaitingSmsCount(shopId)
        ) { lowStock, repairs, readyRepairs, pendingSms ->
            intArrayOf(lowStock, repairs, readyRepairs, pendingSms)
        }

        val operationalFlow2 = combine(
            sysDao.observePendingSyncCount(shopId),
            sysDao.observeFailedSyncCount(shopId),
            sysDao.observeUnresolvedConflictCount(shopId)
        ) { pendingSync, failedSync, conflicts ->
            intArrayOf(pendingSync, failedSync, conflicts)
        }

        val profitAndAlertsFlow = combine(
            opsDao.observeSales(shopId),
            ledgerDao.observeCommissionsTotalForRange(shopId, startOfDay, endOfDay),
            ledgerDao.observeCashSessions(shopId),
            sysDao.observeBackups(shopId)
        ) { sales, todayCommissions, cashSessions, backups ->
            val todaySalesGross = sales
                .asSequence()
                .filter { it.saleTimestamp in startOfDay..endOfDay && it.status != "RETURNED" }
                .sumOf { it.grossProfitMinor }
            val totalTodayGrossProfit = todaySalesGross + todayCommissions
            val latestDiscrepancy = cashSessions.firstOrNull()?.discrepancyMinor ?: 0L
            val latestBackupAt = backups.maxOfOrNull { it.createdAt } ?: 0L
            val isBackupRequired = backups.isEmpty() || (System.currentTimeMillis() - latestBackupAt) > 24L * 60L * 60L * 1000L
            longArrayOf(totalTodayGrossProfit, latestDiscrepancy, if (isBackupRequired) 1L else 0L)
        }

        return combine(
            financialFlow,
            balancesFlow,
            operationalFlow1,
            operationalFlow2,
            profitAndAlertsFlow
        ) { fin, bal, ops1, ops2, profAlerts ->
            val todayGross = profAlerts[0]
            val todayExpenses = fin[1]
            DashboardLiveKpis(
                todaySalesMinor = fin[0],
                todayExpensesMinor = todayExpenses,
                cashPositionMinor = fin[2],
                totalWalletsBalanceMinor = fin[3],
                customerReceivablesMinor = bal.first,
                supplierPayablesMinor = bal.second,
                lowStockProductsCount = ops1[0],
                pendingRepairsCount = ops1[1],
                readyRepairsCount = ops1[2],
                pendingSmsCount = ops1[3],
                pendingSyncCount = ops2[0],
                failedSyncCount = ops2[1],
                unresolvedConflictsCount = ops2[2],
                todayGrossProfitMinor = todayGross,
                todayNetProfitIndicatorMinor = todayGross - todayExpenses,
                lastCashDiscrepancyMinor = profAlerts[1],
                backupRequired = profAlerts[2] == 1L
            )
        }
    }

    fun observeMonthlyFinancialReport(shopId: String = DEFAULT_SHOP_ID): Flow<MonthlyFinancialReport> {
        val (startOfDay, endOfDay) = getTodayBoundsMillis()
        val (startOfMonth, endOfMonth) = getCurrentMonthBoundsMillis()
        val ledgerDao = database.financialLedgerDao()
        val catalogDao = database.catalogInventoryDao()

        val dailyFlow = combine(
            ledgerDao.observeSalesTotalForRange(shopId, startOfDay, endOfDay),
            ledgerDao.observePurchasesTotalForRange(shopId, startOfDay, endOfDay),
            ledgerDao.observeExpensesTotalForRange(shopId, startOfDay, endOfDay)
        ) { dSales, dPurchases, dExpenses ->
            longArrayOf(dSales, dPurchases, dExpenses)
        }

        val monthlyFlow = combine(
            ledgerDao.observeSalesTotalForRange(shopId, startOfMonth, endOfMonth),
            ledgerDao.observePurchasesTotalForRange(shopId, startOfMonth, endOfMonth),
            ledgerDao.observeExpensesTotalForRange(shopId, startOfMonth, endOfMonth),
            ledgerDao.observeCommissionsTotalForRange(shopId, startOfMonth, endOfMonth)
        ) { mSales, mPurchases, mExpenses, mComm ->
            longArrayOf(mSales, mPurchases, mExpenses, mComm)
        }

        return combine(
            dailyFlow,
            monthlyFlow,
            catalogDao.observeProductInventoryProjections(shopId)
        ) { daily, monthly, projections ->
            val totalInventoryValue = projections.sumOf { proj ->
                proj.derivedStockQty.toLong().coerceAtLeast(0L) * proj.weightedAvgCostMinor
            }
            MonthlyFinancialReport(
                todaySalesMinor = daily[0],
                todayPurchasesMinor = daily[1],
                todayExpensesMinor = daily[2],
                monthSalesMinor = monthly[0],
                monthPurchasesMinor = monthly[1],
                monthExpensesMinor = monthly[2],
                monthCommissionsMinor = monthly[3],
                totalInventoryValuationMinor = totalInventoryValue
            )
        }
    }

    fun observeShop(shopId: String = DEFAULT_SHOP_ID): Flow<ShopEntity?> =
        database.coreShopAuthDao().observeShopById(shopId)

    fun observeAppSettings(shopId: String = DEFAULT_SHOP_ID): Flow<List<AppSettingEntity>> =
        database.coreShopAuthDao().observeSettings(shopId)

    fun observeShopConfiguration(shopId: String = DEFAULT_SHOP_ID): Flow<ShopConfigurationProfile> {
        return combine(
            database.coreShopAuthDao().observeShopById(shopId),
            database.coreShopAuthDao().observeSettings(shopId)
        ) { shop, settingsList ->
            val map = settingsList.associate { it.settingKey to it.settingValue }
            val autoSms = (map[KEY_AUTO_CAPTURE_SMS] ?: "true").equals("true", ignoreCase = true)
            val autoSync = (map[KEY_AUTO_SYNC_ON_POST] ?: "true").equals("true", ignoreCase = true)
            ShopConfigurationProfile(
                shopNameArabic = shop?.nameArabic ?: "مكة فون",
                shopNameEnglish = shop?.nameEnglish ?: "MEKKA PHONE",
                shopCode = shop?.code ?: "MK-001",
                shopPhone = shop?.phone?.ifBlank { "01000000000" } ?: "01000000000",
                shopAddress = shop?.address?.ifBlank { "الفرع الرئيسي" } ?: "الفرع الرئيسي",
                taxRegistrationNumber = shop?.taxRegistrationNumber.orEmpty(),
                defaultCurrency = shop?.defaultCurrency ?: "EGP",
                themeMode = map[KEY_THEME_MODE] ?: "LIGHT",
                accentPalette = map[KEY_ACCENT_PALETTE] ?: "EMERALD",
                uiDensity = map[KEY_UI_DENSITY] ?: "COMFORTABLE",
                defaultTaxPercent = map[KEY_DEFAULT_TAX_PERCENT] ?: "0",
                receiptHeaderNote = map[KEY_RECEIPT_HEADER_NOTE] ?: "أهلاً بكم في مكة فون لخدمات الهواتف والدفع الإلكتروني",
                receiptFooterNote = map[KEY_RECEIPT_FOOTER_NOTE] ?: "شكراً لتعاملكم معنا — يرجى الاحتفاظ بالفاتورة للضمان",
                defaultWarrantyDays = map[KEY_DEFAULT_WARRANTY_DAYS] ?: "14",
                defaultWarrantyMonths = map["default_warranty_months"]?.toIntOrNull() ?: 12,
                allowCreditSales = (map[KEY_ALLOW_CREDIT_SALES] ?: "true").equals("true", ignoreCase = true),
                requireCustomerForCredit = (map[KEY_REQUIRE_CUSTOMER_FOR_CREDIT] ?: "true").equals("true", ignoreCase = true),
                showProfitAnalysisOnPos = (map[KEY_SHOW_PROFIT_ON_POS] ?: "true").equals("true", ignoreCase = true),
                defaultCustomerCreditLimitMajor = map[KEY_DEFAULT_CUSTOMER_CREDIT_LIMIT] ?: "10000",
                defaultCustomerCreditLimitMinor = (map[KEY_DEFAULT_CUSTOMER_CREDIT_LIMIT]?.toLongOrNull() ?: 10000L) * 100L,
                defaultLowStockThreshold = map[KEY_LOW_STOCK_THRESHOLD]?.toIntOrNull() ?: 2,
                defaultWalletDailyLimitMajor = map[KEY_WALLET_DAILY_LIMIT] ?: "60000",
                defaultWalletMonthlyLimitMajor = map[KEY_WALLET_MONTHLY_LIMIT] ?: "200000",
                defaultWalletCommissionBps = map["default_wallet_commission_bps"]?.toIntOrNull() ?: 100,
                autoCaptureSmsEnabled = autoSms,
                smsAutoCaptureEnabled = autoSms,
                smsHighConfidenceAutoConfirm = (map[KEY_SMS_HIGH_CONFIDENCE_AUTO] ?: "false").equals("true", ignoreCase = true),
                smsHighConfidenceThresholdBps = map["sms_high_confidence_bps"]?.toIntOrNull() ?: 8500,
                autoSyncOnFinancialPost = autoSync,
                autoSyncEnabled = autoSync,
                autoDailyBackupEnabled = (map["auto_daily_backup_enabled"] ?: "true").equals("true", ignoreCase = true),
                printerType = map[KEY_PRINTER_TYPE] ?: "THERMAL_80MM",
                paperSize = map[KEY_PAPER_SIZE] ?: "80mm",
                fiscalYearStartMonth = map[KEY_FISCAL_YEAR_START_MONTH] ?: "1",
                cloudSyncEndpointUrl = map[KEY_CLOUD_SYNC_ENDPOINT_URL].orEmpty(),
                cloudSyncWifiOnly = (map[KEY_CLOUD_SYNC_WIFI_ONLY] ?: "true").equals("true", ignoreCase = true),
                onboardingCompleted = (map[KEY_ONBOARDING_COMPLETED] ?: "false").equals("true", ignoreCase = true),
                requireSerialOnDevices = (map[KEY_REQUIRE_SERIAL_ON_DEVICES] ?: "true").equals("true", ignoreCase = true)
            )
        }
    }

    /**
     * Completes First-Launch Setup (`إعداد مكة فون`, Part 3 Rule 8 & 22).
     * Replaces any initial bootstrap PIN on the primary Owner account with the user's real PBKDF2-HMAC-SHA256 PIN,
     * configures the Shop identity, marks onboarding as completed, and establishes an authenticated session.
     */
    suspend fun completeFirstLaunchSetup(
        ownerFullNameArabic: String,
        shopNameArabic: String,
        phoneOrAccount: String,
        rawPin: String,
        confirmPin: String,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<com.example.auth.AuthenticatedSession> {
        val cleanOwner = ownerFullNameArabic.trim()
        val cleanShop = shopNameArabic.trim()
        val cleanPhone = phoneOrAccount.trim()
        val cleanPin = rawPin.trim()
        val cleanConfirm = confirmPin.trim()

        if (cleanOwner.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المالك بالكامل", "MISSING_OWNER_NAME")
        }
        if (cleanShop.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المتجر", "MISSING_SHOP_NAME")
        }
        if (cleanPhone.isBlank()) {
            return AppResult.Error("يرجى إدخال رقم الهاتف أو الحساب المرتبط بالمتجر", "MISSING_SHOP_PHONE")
        }
        if (cleanPin.length < 4 || !cleanPin.all { it.isDigit() }) {
            return AppResult.Error("يجب أن يتكون رمز PIN السري من 4 أرقام على الأقل", "WEAK_PIN")
        }
        if (cleanPin != cleanConfirm) {
            return AppResult.Error("رمز PIN وتأكيد الرمز غير متطابقين", "PIN_MISMATCH")
        }

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val coreDao = database.coreShopAuthDao()

                val existingShop = coreDao.getShopById(shopId)
                if (existingShop != null) {
                    coreDao.updateShop(
                        existingShop.copy(
                            nameArabic = cleanShop,
                            phone = cleanPhone,
                            updatedAt = now,
                            version = existingShop.version + 1L,
                            syncState = SyncState.PENDING
                        )
                    )
                }

                val existingOwner = coreDao.getUserById(DEFAULT_OWNER_USER_ID)
                val targetOwnerId = existingOwner?.localId ?: DEFAULT_OWNER_USER_ID
                val newHash = authSecurityManager.hashOperationalPin(
                    shopId = shopId,
                    userId = targetOwnerId,
                    rawPin = cleanPin
                )

                if (existingOwner != null) {
                    coreDao.updateUser(
                        existingOwner.copy(
                            fullNameArabic = cleanOwner,
                            phone = cleanPhone,
                            pinHashSha256 = newHash,
                            isActive = true,
                            updatedAt = now,
                            version = existingOwner.version + 1L,
                            syncState = SyncState.PENDING
                        )
                    )
                }

                val existingOnboardSetting = coreDao.getSetting(shopId, KEY_ONBOARDING_COMPLETED)
                val onboardEntity = if (existingOnboardSetting != null) {
                    existingOnboardSetting.copy(
                        settingValue = "true",
                        valueType = "BOOLEAN",
                        updatedByUserId = DEFAULT_OWNER_USER_ID,
                        updatedAt = now,
                        version = existingOnboardSetting.version + 1L,
                        syncState = SyncState.PENDING
                    )
                } else {
                    AppSettingEntity(
                        localId = IdGenerator.newPrefixedId("SET"),
                        shopId = shopId,
                        settingKey = KEY_ONBOARDING_COMPLETED,
                        settingValue = "true",
                        valueType = "BOOLEAN",
                        updatedByUserId = DEFAULT_OWNER_USER_ID,
                        deviceId = deviceId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                coreDao.upsertSetting(onboardEntity)

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = DEFAULT_OWNER_USER_ID,
                        actorRoleCode = "OWNER",
                        actionType = "FIRST_LAUNCH_ONBOARDING",
                        entityType = "Shop",
                        entityId = shopId,
                        summaryArabic = "إتمام إعداد مكة فون الأولي — المتجر: $cleanShop | المالك: $cleanOwner",
                        beforeStateJson = null,
                        afterStateJson = """{"shop":"$cleanShop","owner":"$cleanOwner","phone":"$cleanPhone"}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
            }

            authSecurityManager.authenticateWithPin(
                userId = DEFAULT_OWNER_USER_ID,
                rawPin = cleanPin,
                expectedShopId = shopId,
                installationId = activeDeviceId
            )
        } catch (e: Exception) {
            AppResult.Error("تعذر إتمام الإعداد الأولي: ${e.message}", "ONBOARDING_ERROR", e)
        }
    }

    suspend fun updateShopProfile(
        nameArabic: String,
        nameEnglish: String,
        phone: String,
        address: String,
        taxRegistrationNumber: String,
        defaultCurrency: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<ShopEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        if (nameArabic.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المتجر باللغة العربية", "INVALID_SHOP_NAME")
        }
        return try {
            database.withTransaction {
                val coreDao = database.coreShopAuthDao()
                val existing = coreDao.getShopById(shopId)
                    ?: return@withTransaction AppResult.Error("المتجر غير موجود", "SHOP_NOT_FOUND")
                val now = System.currentTimeMillis()
                val updated = existing.copy(
                    nameArabic = nameArabic.trim(),
                    nameEnglish = nameEnglish.trim().ifBlank { "MEKKA PHONE" },
                    phone = phone.trim(),
                    address = address.trim(),
                    taxRegistrationNumber = taxRegistrationNumber.trim().takeIf { it.isNotEmpty() },
                    defaultCurrency = defaultCurrency.trim().uppercase().ifBlank { "EGP" },
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                coreDao.updateShop(updated)
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "SHOP_SETTINGS_UPDATE",
                        entityType = "Shop",
                        entityId = shopId,
                        summaryArabic = "تحديث هوية وبيانات المتجر (${updated.nameArabic} — ${updated.defaultCurrency})",
                        beforeStateJson = """{"name":"${existing.nameArabic}","currency":"${existing.defaultCurrency}"}""",
                        afterStateJson = """{"name":"${updated.nameArabic}","currency":"${updated.defaultCurrency}"}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث بيانات المتجر: ${e.message}", "SHOP_UPDATE_ERROR", e)
        }
    }

    suspend fun updateAppSetting(
        settingKey: String,
        settingValue: String,
        valueType: String = "STRING",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<AppSettingEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        return try {
            database.withTransaction {
                val coreDao = database.coreShopAuthDao()
                val existing = coreDao.getSetting(shopId, settingKey)
                val now = System.currentTimeMillis()
                val entity = if (existing != null) {
                    existing.copy(
                        settingValue = settingValue,
                        valueType = valueType,
                        updatedByUserId = actorUserId,
                        updatedAt = now,
                        version = existing.version + 1L,
                        syncState = SyncState.PENDING
                    )
                } else {
                    AppSettingEntity(
                        localId = IdGenerator.newPrefixedId("SET"),
                        shopId = shopId,
                        settingKey = settingKey,
                        settingValue = settingValue,
                        valueType = valueType,
                        updatedByUserId = actorUserId,
                        deviceId = deviceId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                coreDao.upsertSetting(entity)
                AppResult.Success(entity)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر حفظ الإعداد ($settingKey): ${e.message}", "SETTING_UPDATE_ERROR", e)
        }
    }

    suspend fun resetAppSettingsToDefaults(
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<Unit> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        return try {
            val defaults = mapOf(
                "theme_mode" to "LIGHT",
                "accent_palette" to "EMERALD",
                "ui_density" to "COMFORTABLE",
                "default_tax_percent" to "0",
                "receipt_header_note" to "أهلاً بكم في مكة فون لخدمات الهواتف والدفع الإلكتروني",
                "receipt_footer_note" to "شكراً لتعاملكم معنا — يرجى الاحتفاظ بالفاتورة للضمان",
                "default_warranty_months" to "12",
                "allow_credit_sales" to "true",
                "default_customer_credit_limit_minor" to "1000000",
                "default_low_stock_threshold" to "2",
                "default_wallet_commission_bps" to "100",
                "sms_auto_capture_enabled" to "true",
                "sms_high_confidence_bps" to "8500",
                "auto_sync_enabled" to "true",
                "auto_daily_backup_enabled" to "true"
            )
            for ((k, v) in defaults) {
                updateAppSetting(k, v, actorUserId = actorUserId, shopId = shopId, deviceId = deviceId)
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error("تعذر استعادة الإعدادات الافتراضية: ${e.message}", "SETTINGS_RESET_ERROR", e)
        }
    }

    fun observeUsers(shopId: String = DEFAULT_SHOP_ID): Flow<List<UserEntity>> =
        database.coreShopAuthDao().observeUsers(shopId)

    fun observeRoles(shopId: String = DEFAULT_SHOP_ID): Flow<List<RoleEntity>> =
        database.coreShopAuthDao().observeRoles(shopId)

    fun observePermissionsForRole(roleId: String): Flow<List<PermissionEntity>> =
        database.coreShopAuthDao().observePermissionsForRole(roleId)

    fun observeCustomers(shopId: String = DEFAULT_SHOP_ID): Flow<List<CustomerEntity>> =
        database.catalogInventoryDao().observeCustomers(shopId)

    fun observeCustomerBalanceProjections(shopId: String = DEFAULT_SHOP_ID): Flow<List<CustomerBalanceProjection>> =
        database.catalogInventoryDao().observeCustomerBalanceProjections(shopId)

    fun observeSuppliers(shopId: String = DEFAULT_SHOP_ID): Flow<List<SupplierEntity>> =
        database.catalogInventoryDao().observeSuppliers(shopId)

    fun observeSupplierBalanceProjections(shopId: String = DEFAULT_SHOP_ID): Flow<List<SupplierBalanceProjection>> =
        database.catalogInventoryDao().observeSupplierBalanceProjections(shopId)

    fun observeCategories(shopId: String = DEFAULT_SHOP_ID): Flow<List<CategoryEntity>> =
        database.catalogInventoryDao().observeCategories(shopId)

    fun observeBrands(shopId: String = DEFAULT_SHOP_ID): Flow<List<BrandEntity>> =
        database.catalogInventoryDao().observeBrands(shopId)

    fun observeProductInventoryProjections(shopId: String = DEFAULT_SHOP_ID): Flow<List<ProductInventoryProjection>> =
        database.catalogInventoryDao().observeProductInventoryProjections(shopId)

    fun observeSerialNumbers(shopId: String = DEFAULT_SHOP_ID): Flow<List<SerialNumberEntity>> =
        database.catalogInventoryDao().observeSerialNumbers(shopId)

    fun observeAllInventoryMovements(shopId: String = DEFAULT_SHOP_ID): Flow<List<InventoryMovementEntity>> =
        database.catalogInventoryDao().observeAllInventoryMovements(shopId)

    fun observeSales(shopId: String = DEFAULT_SHOP_ID): Flow<List<SaleEntity>> =
        database.operationsDao().observeSales(shopId)

    fun observeAllSaleItems(shopId: String = DEFAULT_SHOP_ID) =
        database.operationsDao().observeAllSaleItems(shopId)

    fun observeRefunds(shopId: String = DEFAULT_SHOP_ID): Flow<List<RefundEntity>> =
        database.operationsDao().observeRefunds(shopId)

    fun observePurchases(shopId: String = DEFAULT_SHOP_ID): Flow<List<PurchaseEntity>> =
        database.operationsDao().observePurchases(shopId)

    fun observeAllPurchaseItems(shopId: String = DEFAULT_SHOP_ID) =
        database.operationsDao().observeAllPurchaseItems(shopId)

    fun observeMaintenanceOrders(shopId: String = DEFAULT_SHOP_ID): Flow<List<MaintenanceOrderEntity>> =
        database.operationsDao().observeMaintenanceOrders(shopId)

    fun observeAllMaintenanceParts(shopId: String = DEFAULT_SHOP_ID) =
        database.operationsDao().observeAllMaintenanceParts(shopId)

    fun observeWalletProviders(shopId: String = DEFAULT_SHOP_ID): Flow<List<WalletProviderEntity>> =
        database.financialLedgerDao().observeWalletProviders(shopId)

    fun observeWalletDerivedProjections(shopId: String = DEFAULT_SHOP_ID): Flow<List<WalletDerivedProjection>> {
        val (startOfDay, endOfDay) = getTodayBoundsMillis()
        val (startOfMonth, endOfMonth) = getCurrentMonthBoundsMillis()
        return database.financialLedgerDao().observeWalletDerivedProjections(
            shopId = shopId,
            startOfDayMillis = startOfDay,
            endOfDayMillis = endOfDay,
            startOfMonthMillis = startOfMonth,
            endOfMonthMillis = endOfMonth
        )
    }

    fun observeCashDrawerDerivedProjections(shopId: String = DEFAULT_SHOP_ID): Flow<List<CashDrawerDerivedProjection>> {
        val (startOfDay, endOfDay) = getTodayBoundsMillis()
        return database.financialLedgerDao().observeCashDrawerDerivedProjections(
            shopId = shopId,
            startOfDayMillis = startOfDay,
            endOfDayMillis = endOfDay
        )
    }

    fun observeCashSessions(shopId: String = DEFAULT_SHOP_ID): Flow<List<CashSessionEntity>> =
        database.financialLedgerDao().observeCashSessions(shopId)

    fun observeExpenses(shopId: String = DEFAULT_SHOP_ID): Flow<List<ExpenseEntity>> =
        database.operationsDao().observeExpenses(shopId)

    fun observeTransfers(shopId: String = DEFAULT_SHOP_ID): Flow<List<TransferEntity>> =
        database.operationsDao().observeTransfers(shopId)

    fun observeSmsReviewItems(shopId: String = DEFAULT_SHOP_ID): Flow<List<SmsReviewItemProjection>> =
        database.operationsDao().observeSmsReviewItems(shopId)

    fun observeLedgerTransactions(shopId: String = DEFAULT_SHOP_ID): Flow<List<TransactionEntity>> =
        database.financialLedgerDao().observeLedgerTransactions(shopId)

    fun observeAuditLogs(shopId: String = DEFAULT_SHOP_ID): Flow<List<AuditLogEntity>> =
        database.systemIntegrityDao().observeAuditLogs(shopId)

    fun observeSyncQueue(shopId: String = DEFAULT_SHOP_ID): Flow<List<SyncQueueEntity>> =
        database.systemIntegrityDao().observeSyncQueue(shopId)

    fun observeSyncConflicts(shopId: String = DEFAULT_SHOP_ID): Flow<List<SyncConflictEntity>> =
        database.systemIntegrityDao().observeSyncConflicts(shopId)

    fun observeBackups(shopId: String = DEFAULT_SHOP_ID): Flow<List<BackupMetadataEntity>> =
        database.systemIntegrityDao().observeBackups(shopId)

    /**
     * Creates a new staff User account with PBKDF2-HMAC-SHA256 PIN hashing, enforcing USER_PERMISSION_MANAGE RBAC.
     */
    suspend fun createStaffUser(
        username: String,
        fullNameArabic: String,
        roleType: UserRoleType,
        rawPin: String,
        phone: String = "",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<UserEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        if (username.isBlank() || fullNameArabic.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المستخدم والاسم الكامل", "INVALID_USER_INPUT")
        }
        val cleanPin = rawPin.trim()
        if (cleanPin.length < 4 || !cleanPin.all { it.isDigit() }) {
            return AppResult.Error("يجب أن يتكون رمز PIN من 4 أرقام على الأقل", "WEAK_PIN")
        }
        return try {
            database.withTransaction {
                val coreDao = database.coreShopAuthDao()
                val roleEntity = coreDao.getRoleByCode(shopId, roleType.name)
                    ?: return@withTransaction AppResult.Error("الدور المطلوب غير موجود", "ROLE_NOT_FOUND")
                val now = System.currentTimeMillis()
                val userId = IdGenerator.newPrefixedId("USR")
                val pinHash = authSecurityManager.hashOperationalPin(
                    shopId = shopId,
                    userId = userId,
                    rawPin = cleanPin
                )
                val user = UserEntity(
                    localId = userId,
                    shopId = shopId,
                    roleId = roleEntity.localId,
                    roleCode = roleType.name,
                    username = username.trim().lowercase(),
                    fullNameArabic = fullNameArabic.trim(),
                    phone = phone.trim(),
                    pinHashSha256 = pinHash,
                    isActive = true,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                coreDao.insertUser(user)
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "USER_CREATE",
                        entityType = "User",
                        entityId = user.localId,
                        summaryArabic = "إنشاء حساب موظف جديد (${user.fullNameArabic}) بدور (${roleType.arabicTitle})",
                        beforeStateJson = null,
                        afterStateJson = """{"userId":"${user.localId}","role":"${roleType.name}"}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(user)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر إنشاء حساب الموظف: ${e.message}", "USER_CREATE_ERROR", e)
        }
    }

    /**
     * Overload of [createStaffUser] accepting a string roleCode.
     */
    suspend fun createStaffUser(
        fullNameArabic: String,
        username: String,
        phone: String = "",
        roleCode: String,
        rawPin: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<UserEntity> {
        val parsedRole = UserRoleType.entries.firstOrNull { it.name.equals(roleCode.trim(), ignoreCase = true) }
            ?: return AppResult.Error("الدور الوظيفي غير صالح: $roleCode", "INVALID_ROLE_CODE")
        return createStaffUser(
            username = username,
            fullNameArabic = fullNameArabic,
            roleType = parsedRole,
            rawPin = rawPin,
            phone = phone,
            actorUserId = actorUserId,
            shopId = shopId,
            deviceId = deviceId
        )
    }

    /**
     * Toggles a User account active status, enforcing USER_PERMISSION_MANAGE RBAC and preventing disabling the primary Owner.
     */
    suspend fun setUserActiveStatus(
        targetUserId: String,
        isActive: Boolean,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<UserEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        if (targetUserId == DEFAULT_OWNER_USER_ID && !isActive) {
            return AppResult.Error("لا يمكن إيقاف حساب المالك الرئيسي للنظام", "CANNOT_DISABLE_PRIMARY_OWNER")
        }
        return try {
            database.withTransaction {
                val coreDao = database.coreShopAuthDao()
                val existing = coreDao.getUserById(targetUserId)
                    ?: return@withTransaction AppResult.Error("المستخدم غير موجود", "USER_NOT_FOUND")
                val now = System.currentTimeMillis()
                val updated = existing.copy(
                    isActive = isActive,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                coreDao.updateUser(updated)
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = if (isActive) "USER_ACTIVATE" else "USER_DEACTIVATE",
                        entityType = "User",
                        entityId = updated.localId,
                        summaryArabic = "${if (isActive) "تفعيل" else "إيقاف"} حساب الموظف (${updated.fullNameArabic})",
                        beforeStateJson = """{"isActive":${existing.isActive}}""",
                        afterStateJson = """{"isActive":$isActive}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث حالة حساب المستخدم: ${e.message}", "USER_STATUS_ERROR", e)
        }
    }

    suspend fun toggleUserActiveStatus(
        targetUserId: String,
        isActive: Boolean,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<UserEntity> = setUserActiveStatus(
        targetUserId = targetUserId,
        isActive = isActive,
        actorUserId = actorUserId,
        shopId = shopId,
        deviceId = deviceId
    )

    suspend fun changeUserPin(
        targetUserId: String,
        newPin: String,
        confirmPin: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<UserEntity> {
        if (actorUserId != targetUserId) {
            val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
            if (auth is AppResult.Error) return auth
        } else {
            val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.SALE_CREATE, shopId)
            if (auth is AppResult.Error && actorUserId == DEFAULT_OWNER_USER_ID) {
                val ownerAuth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.USER_PERMISSION_MANAGE, shopId)
                if (ownerAuth is AppResult.Error) return ownerAuth
            }
        }
        val cleanPin = newPin.trim()
        if (cleanPin.length < 4 || !cleanPin.all { it.isDigit() }) {
            return AppResult.Error("يجب أن يتكون رمز PIN الجديد من 4 أرقام على الأقل", "WEAK_PIN")
        }
        if (cleanPin != confirmPin.trim()) {
            return AppResult.Error("رمز PIN الجديد وتأكيد الرمز غير متطابقين", "PIN_MISMATCH")
        }
        return try {
            database.withTransaction {
                val coreDao = database.coreShopAuthDao()
                val existing = coreDao.getUserById(targetUserId)
                    ?: return@withTransaction AppResult.Error("المستخدم غير موجود", "USER_NOT_FOUND")
                val now = System.currentTimeMillis()
                val newHash = authSecurityManager.hashOperationalPin(
                    shopId = shopId,
                    userId = existing.localId,
                    rawPin = cleanPin
                )
                val updated = existing.copy(
                    pinHashSha256 = newHash,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                coreDao.updateUser(updated)
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = existing.roleCode,
                        actionType = "USER_PIN_CHANGE",
                        entityType = "User",
                        entityId = updated.localId,
                        summaryArabic = "تحديث رمز PIN السري لحساب (${updated.fullNameArabic})",
                        beforeStateJson = null,
                        afterStateJson = """{"userId":"${updated.localId}","pinUpdated":true}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث رمز PIN: ${e.message}", "PIN_CHANGE_ERROR", e)
        }
    }

    /**
     * Creates a new Customer with local-first sync queue & RBAC authorization.
     */
    suspend fun createCustomer(
        fullName: String,
        phone: String,
        nationalId: String? = null,
        address: String = "",
        notes: String = "",
        creditLimitMinor: Long = 0L,
        defaultCreditDays: Int = 30,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CustomerEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.SALE_CREATE, shopId)
        if (auth is AppResult.Error) return auth
        if (fullName.isBlank() || phone.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم العميل ورقم الهاتف", "INVALID_CUSTOMER")
        }
        val normalizedPhone = Money.normalizeDigits(phone).trim()
        if (normalizedPhone.length < 8 || !normalizedPhone.all { it.isDigit() || it == '+' }) {
            return AppResult.Error("يرجى إدخال رقم هاتف عميل صحيح (8 أرقام على الأقل)", "INVALID_PHONE")
        }
        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val entity = CustomerEntity(
                    localId = IdGenerator.newPrefixedId("CUS"),
                    shopId = shopId,
                    fullName = fullName.trim(),
                    phone = normalizedPhone,
                    nationalId = nationalId?.trim()?.takeIf { it.isNotEmpty() },
                    address = address.trim(),
                    creditLimitMinor = creditLimitMinor.coerceAtLeast(0L),
                    defaultCreditDays = defaultCreditDays.coerceAtLeast(1),
                    notes = notes.trim(),
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                database.catalogInventoryDao().insertCustomer(entity)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Customer",
                    entityLocalId = entity.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"id":"${entity.localId}","name":"${entity.fullName}","phone":"${entity.phone}"}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(entity)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر حفظ العميل (تأكد من عدم تكرار رقم الهاتف): ${e.message}", "CUSTOMER_CREATE_ERROR", e)
        }
    }

    /**
     * Archives (soft-deletes) a Customer without destroying historical sales or ledger links (Requirement 2 & 66).
     */
    suspend fun archiveCustomer(
        customerId: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CustomerEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.SALE_CREATE, shopId)
        if (auth is AppResult.Error) return auth
        return try {
            database.withTransaction {
                val existing = database.catalogInventoryDao().getCustomerById(customerId)
                    ?: return@withTransaction AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND")
                val now = System.currentTimeMillis()
                val archived = existing.copy(
                    isActive = false,
                    isDeleted = true,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                database.catalogInventoryDao().updateCustomer(archived)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Customer",
                    entityLocalId = archived.localId,
                    operationType = "SOFT_DELETE",
                    entityVersion = archived.version,
                    payloadJson = """{"id":"${archived.localId}","archived":true}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(archived)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر أرشفة العميل: ${e.message}", "CUSTOMER_ARCHIVE_ERROR", e)
        }
    }

    /**
     * Creates a new Supplier with local-first sync queue & RBAC authorization.
     */
    suspend fun createSupplier(
        companyName: String,
        contactName: String,
        phone: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<SupplierEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PURCHASE_CREATE, shopId)
        if (auth is AppResult.Error) return auth
        if (companyName.isBlank() || phone.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المورد ورقم الهاتف", "INVALID_SUPPLIER")
        }
        val normalizedPhone = Money.normalizeDigits(phone).trim()
        if (normalizedPhone.length < 8 || !normalizedPhone.all { it.isDigit() || it == '+' }) {
            return AppResult.Error("يرجى إدخال رقم هاتف مورد صحيح (8 أرقام على الأقل)", "INVALID_PHONE")
        }
        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val supplier = SupplierEntity(
                    localId = IdGenerator.newPrefixedId("SUP"),
                    shopId = shopId,
                    companyName = companyName.trim(),
                    contactName = contactName.trim(),
                    phone = normalizedPhone,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                database.catalogInventoryDao().insertSupplier(supplier)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Supplier",
                    entityLocalId = supplier.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"id":"${supplier.localId}","company":"${supplier.companyName}"}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(supplier)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر إضافة المورد (تأكد من عدم تكرار رقم الهاتف): ${e.message}", "SUPPLIER_CREATE_ERROR", e)
        }
    }

    /**
     * Records a payment to a Supplier against their payable balance via the Financial Ledger (Requirement 3).
     */
    suspend fun recordSupplierPayment(
        supplierId: String,
        amountMinor: Long,
        notes: String = "",
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID,
        idempotencyKey: String? = null
    ): AppResult<TransactionEntity> {
        if (amountMinor <= 0L) {
            return AppResult.Error("مبلغ سداد المورد يجب أن يكون أكبر من الصفر", "INVALID_SUPPLIER_PAYMENT")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PURCHASE_CREATE)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        return try {
            database.withTransaction {
                val supplier = database.catalogInventoryDao().getSupplierById(supplierId)
                    ?: return@withTransaction AppResult.Error("المورد غير موجود", "SUPPLIER_NOT_FOUND")
                val currentPayableMinor = database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, supplierId)
                if (currentPayableMinor <= 0L) {
                    return@withTransaction AppResult.Error(
                        "لا توجد مديونية قائمة للمورد لسدادها",
                        "NO_OUTSTANDING_PAYABLE"
                    )
                }
                if (amountMinor > currentPayableMinor) {
                    return@withTransaction AppResult.Error(
                        "مبلغ السداد (${Money.ofMinor(amountMinor).formatArabic()}) يتجاوز المستحق للمورد (${Money.ofMinor(currentPayableMinor).formatArabic()})",
                        "SUPPLIER_OVERPAYMENT_NOT_ALLOWED"
                    )
                }
                val now = System.currentTimeMillis()
                val txRes = financialLedgerEngine.postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = FinancialTransactionType.CASH_OUT,
                    amountMinor = amountMinor,
                    sourceAccountType = paymentAccountType,
                    sourceAccountId = paymentAccountId,
                    destinationAccountType = FinancialAccountType.EXTERNAL_SUPPLIER,
                    destinationAccountId = supplierId,
                    supplierId = supplierId,
                    relatedEntityType = "SUPPLIER_PAYMENT",
                    relatedEntityId = supplierId,
                    descriptionArabic = "سداد دفعة لمورد (${supplier.companyName}) — $notes",
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = now,
                    idempotencyKey = idempotencyKey
                )
                if (txRes is AppResult.Error) return@withTransaction txRes
                val tx = (txRes as AppResult.Success).data
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "SUPPLIER_PAYMENT",
                        entityType = "FinancialTransaction",
                        entityId = tx.localId,
                        summaryArabic = "سداد مستحقات للمورد (${supplier.companyName}) بقيمة ${Money.ofMinor(amountMinor).formatArabic()}",
                        beforeStateJson = """{"payableBeforeMinor":$currentPayableMinor}""",
                        afterStateJson = """{"payableAfterMinor":${currentPayableMinor - amountMinor},"txId":"${tx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(tx)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تسجيل سداد المورد: ${e.message}", "SUPPLIER_PAYMENT_ERROR", e)
        }
    }

    /**
     * Creates a new Category explicitly.
     */
    suspend fun createCategory(
        nameArabic: String,
        description: String = "",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CategoryEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PRICE_CHANGE, shopId)
        if (auth is AppResult.Error) return auth
        if (nameArabic.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم الفئة", "INVALID_CATEGORY")
        }
        return try {
            val existing = database.catalogInventoryDao().getAllCategories(shopId)
                .firstOrNull { it.nameArabic.equals(nameArabic.trim(), ignoreCase = true) && !it.isDeleted }
            if (existing != null) {
                return AppResult.Success(existing)
            }
            val now = System.currentTimeMillis()
            val cat = CategoryEntity(
                localId = IdGenerator.newPrefixedId("CAT"),
                shopId = shopId,
                nameArabic = nameArabic.trim(),
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now
            )
            database.catalogInventoryDao().insertCategory(cat)
            AppResult.Success(cat)
        } catch (e: Exception) {
            AppResult.Error("تعذر إضافة الفئة: ${e.message}", "CATEGORY_CREATE_ERROR", e)
        }
    }

    /**
     * Creates a new Brand explicitly.
     */
    suspend fun createBrand(
        nameArabic: String,
        nameEnglish: String = "",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<BrandEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PRICE_CHANGE, shopId)
        if (auth is AppResult.Error) return auth
        if (nameArabic.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم العلامة التجارية", "INVALID_BRAND")
        }
        return try {
            val existing = database.catalogInventoryDao().getAllBrands(shopId)
                .firstOrNull { it.name.equals(nameArabic.trim(), ignoreCase = true) && !it.isDeleted }
            if (existing != null) {
                return AppResult.Success(existing)
            }
            val now = System.currentTimeMillis()
            val brand = BrandEntity(
                localId = IdGenerator.newPrefixedId("BRD"),
                shopId = shopId,
                name = nameArabic.trim(),
                countryOfOrigin = nameEnglish.trim(),
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now
            )
            database.catalogInventoryDao().insertBrand(brand)
            AppResult.Success(brand)
        } catch (e: Exception) {
            AppResult.Error("تعذر إضافة العلامة التجارية: ${e.message}", "BRAND_CREATE_ERROR", e)
        }
    }

    /**
     * Creates a Product in the catalog (stock starts at 0 and only enters via Inventory Movements).
     */
    suspend fun createProduct(
        nameArabic: String,
        sku: String,
        barcode: String? = null,
        categoryId: String,
        brandId: String? = null,
        isSerialized: Boolean = false,
        defaultSellingPriceMinor: Long,
        reorderThresholdQty: Int = 2,
        description: String = "",
        minSellingPriceMinor: Long = 0L,
        wholesalePriceMinor: Long = 0L,
        specialCustomerPriceMinor: Long = 0L,
        maxDiscountMinor: Long = 0L,
        promotionalPriceMinor: Long = 0L,
        isPromotionActive: Boolean = false,
        reorderPointQty: Int = 5,
        warrantyMonths: Int = 12,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<ProductEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PRICE_CHANGE, shopId)
        if (auth is AppResult.Error) return auth
        if (nameArabic.isBlank() || sku.isBlank() || defaultSellingPriceMinor <= 0L) {
            return AppResult.Error("يرجى إدخال اسم الصنف والكود (SKU) وسعر بيع صحيح", "INVALID_PRODUCT")
        }
        if (categoryId.isBlank()) {
            return AppResult.Error("يرجى اختيار الفئة بشكل صريح", "MISSING_CATEGORY")
        }
        val normalizedSku = sku.trim().uppercase()
        val normalizedBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            database.withTransaction {
                val existingBySku = database.catalogInventoryDao().getProductBySku(shopId, normalizedSku)
                if (existingBySku != null) {
                    return@withTransaction AppResult.Error(
                        "كود الصنف ($normalizedSku) مستخدم بالفعل لصنف آخر (${existingBySku.nameArabic})",
                        "DUPLICATE_SKU"
                    )
                }
                if (normalizedBarcode != null) {
                    val existingByBarcode = database.catalogInventoryDao().getProductByBarcode(shopId, normalizedBarcode)
                    if (existingByBarcode != null) {
                        return@withTransaction AppResult.Error(
                            "الباركود ($normalizedBarcode) مستخدم بالفعل لصنف آخر (${existingByBarcode.nameArabic})",
                            "DUPLICATE_BARCODE"
                        )
                    }
                }
                val now = System.currentTimeMillis()
                val product = ProductEntity(
                    localId = IdGenerator.newPrefixedId("PRD"),
                    shopId = shopId,
                    categoryId = categoryId,
                    brandId = brandId?.takeIf { it.isNotBlank() },
                    sku = normalizedSku,
                    barcode = normalizedBarcode,
                    nameArabic = nameArabic.trim(),
                    modelNumber = description.trim(),
                    isSerialized = isSerialized,
                    defaultSellingPriceMinor = defaultSellingPriceMinor,
                    minSellingPriceMinor = minSellingPriceMinor.coerceAtLeast(0L),
                    wholesalePriceMinor = wholesalePriceMinor.coerceAtLeast(0L),
                    specialCustomerPriceMinor = specialCustomerPriceMinor.coerceAtLeast(0L),
                    maxDiscountMinor = maxDiscountMinor.coerceAtLeast(0L),
                    promotionalPriceMinor = promotionalPriceMinor.coerceAtLeast(0L),
                    isPromotionActive = isPromotionActive,
                    reorderThresholdQty = reorderThresholdQty.coerceAtLeast(0),
                    reorderPointQty = reorderPointQty.coerceAtLeast(reorderThresholdQty.coerceAtLeast(0)),
                    warrantyMonths = warrantyMonths.coerceAtLeast(0),
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                database.catalogInventoryDao().insertProduct(product)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Product",
                    entityLocalId = product.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"id":"${product.localId}","sku":"${product.sku}","serialized":$isSerialized}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(product)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر حفظ الصنف (قد يكون كود SKU أو الباركود مستخدماً بالفعل): ${e.message}", "PRODUCT_CREATE_ERROR", e)
        }
    }

    /**
     * Records an explicit Inventory Adjustment / Stock Movement with RBAC authorization, WAC update, AuditLog, and SyncQueue.
     */
    suspend fun recordAuthorizedInventoryMovement(
        productId: String,
        movementType: InventoryMovementType,
        quantity: Int,
        unitCostMinor: Long,
        serialOrImei: String? = null,
        reason: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<InventoryMovementEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.INVENTORY_ADJUST)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                var serialEntityId: String? = null
                if (!serialOrImei.isNullOrBlank()) {
                    val cleanSerial = serialOrImei.trim()
                    if (movementType.isInbound) {
                        val existingReturnedOrSold = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, cleanSerial)
                        if (existingReturnedOrSold != null && movementType == InventoryMovementType.RETURN_IN) {
                            if (existingReturnedOrSold.productId != productId) {
                                return@withTransaction AppResult.Error(
                                    "السيريال / IMEI ($cleanSerial) مسجل على صنف آخر ولا يتبع هذا الصنف",
                                    "SERIAL_PRODUCT_MISMATCH"
                                )
                            }
                            serialEntityId = existingReturnedOrSold.localId
                        } else {
                            val serRes = inventoryEngine.registerInboundSerialInternal(
                                shopId = shopId,
                                productId = productId,
                                serialOrImei = cleanSerial,
                                acquisitionCostMinor = unitCostMinor,
                                deviceId = deviceId,
                                timestamp = now
                            )
                            if (serRes is AppResult.Error) {
                                return@withTransaction serRes
                            }
                            serialEntityId = (serRes as AppResult.Success).data.localId
                        }
                    } else {
                        val existingSerial = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, cleanSerial)
                            ?: database.catalogInventoryDao().getSerialById(cleanSerial)
                            ?: return@withTransaction AppResult.Error(
                                "السيريال / IMEI ($cleanSerial) غير موجود في سجل السيريالات",
                                "SERIAL_NOT_FOUND"
                            )
                        if (existingSerial.productId != productId) {
                            return@withTransaction AppResult.Error(
                                "السيريال / IMEI (${existingSerial.serialOrImei}) ينتمي لصنف آخر ولا يمكن صرفه تحت هذا الصنف",
                                "SERIAL_PRODUCT_MISMATCH"
                            )
                        }
                        serialEntityId = existingSerial.localId
                    }
                }

                val movRes = inventoryEngine.recordMovementInternal(
                    shopId = shopId,
                    productId = productId,
                    serialNumberId = serialEntityId,
                    movementType = movementType,
                    quantity = quantity,
                    inboundUnitCostMinor = unitCostMinor,
                    reason = reason,
                    referenceType = "INVENTORY_ADJUSTMENT",
                    referenceId = IdGenerator.newReferenceNumber("ADJ", now),
                    performedByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = now
                )
                if (movRes is AppResult.Error) {
                    return@withTransaction movRes
                }
                val mov = (movRes as AppResult.Success).data

                if (serialEntityId != null) {
                    val targetSerialStatus: SerialLifecycleStatus? = when (movementType) {
                        InventoryMovementType.RETURN_IN -> SerialLifecycleStatus.IN_STOCK
                        InventoryMovementType.SALE_OUT -> SerialLifecycleStatus.SOLD
                        InventoryMovementType.RETURN_OUT -> SerialLifecycleStatus.RETURNED_TO_SUPPLIER
                        InventoryMovementType.DAMAGE -> SerialLifecycleStatus.DAMAGED
                        InventoryMovementType.MAINTENANCE_USAGE -> SerialLifecycleStatus.RESERVED_MAINTENANCE
                        InventoryMovementType.ADJUSTMENT_OUT, InventoryMovementType.TRANSFER_OUT -> SerialLifecycleStatus.RETURNED_TO_SUPPLIER
                        else -> null
                    }
                    if (targetSerialStatus != null) {
                        val transRes = inventoryEngine.transitionSerialStatusInternal(
                            shopId = shopId,
                            serialId = serialEntityId,
                            newStatus = targetSerialStatus,
                            movementId = mov.localId,
                            expectedProductId = productId,
                            timestamp = now
                        )
                        if (transRes is AppResult.Error) {
                            return@withTransaction transRes
                        }
                    }
                }

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "INVENTORY_ADJUST",
                        entityType = "InventoryMovement",
                        entityId = mov.localId,
                        summaryArabic = "حركة مخزنية (${movementType.arabicLabel}) بكمية $quantity وتكلفة مرجحة بعد الحركة ${mov.weightedAvgCostAfterMinor / 100.0} ج.م",
                        beforeStateJson = """{"qtyBefore":${mov.quantityBefore},"wacBeforeMinor":${mov.weightedAvgCostBeforeMinor}}""",
                        afterStateJson = """{"qtyAfter":${mov.quantityAfter},"wacAfterMinor":${mov.weightedAvgCostAfterMinor}}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "InventoryMovement",
                    entityLocalId = mov.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"movId":"${mov.localId}","productId":"$productId","delta":${mov.signedQuantityDelta},"wacAfter":${mov.weightedAvgCostAfterMinor}}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )

                AppResult.Success(mov)
            }
        } catch (e: Exception) {
            AppResult.Error(e.message ?: "تعذر تسجيل الحركة المخزنية", "MOVEMENT_ERROR", e)
        }
    }

    /**
     * Registers a new Electronic Wallet (balance starts at 0.00 and is derived from ledger lines).
     */
    suspend fun createWallet(
        providerId: String,
        walletNameArabic: String,
        walletNumber: String,
        dailyLimitMinor: Long = 60_000_00L,
        monthlyLimitMinor: Long = 200_000_00L,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<WalletEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.WALLET_OPERATE, shopId)
        if (auth is AppResult.Error) return auth
        if (walletNameArabic.isBlank() || walletNumber.isBlank()) {
            return AppResult.Error("يرجى إدخال اسم المحفظة ورقم الهاتف/الحساب", "INVALID_WALLET")
        }
        val normalizedPhone = Money.normalizeDigits(walletNumber).trim()
        if (normalizedPhone.length < 8) {
            return AppResult.Error("يرجى إدخال رقم محفظة صحيح (8 أرقام على الأقل)", "INVALID_PHONE")
        }
        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val wallet = WalletEntity(
                    localId = IdGenerator.newPrefixedId("WLT"),
                    shopId = shopId,
                    providerId = providerId,
                    walletNameArabic = walletNameArabic.trim(),
                    walletNumber = normalizedPhone,
                    dailyLimitMinor = dailyLimitMinor,
                    monthlyLimitMinor = monthlyLimitMinor,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                database.financialLedgerDao().insertWallet(wallet)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Wallet",
                    entityLocalId = wallet.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"walletId":"${wallet.localId}","number":"${wallet.walletNumber}"}""",
                    isFinancialLedgerRecord = false,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(wallet)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر إضافة المحفظة (قد يكون الرقم مسجلاً مسبقاً): ${e.message}", "WALLET_CREATE_ERROR", e)
        }
    }

    /**
     * Creates a Maintenance / Repair Order, with optional deposit collection posted directly to the Ledger (Requirements 17, 18, 19).
     */
    suspend fun createMaintenanceOrder(
        customerId: String,
        deviceBrand: String,
        deviceModel: String,
        deviceImeiOrSerial: String,
        faultDescription: String,
        estimatedCostMinor: Long,
        depositMinor: Long = 0L,
        assignedTechnicianId: String? = null,
        customerNotes: String = "",
        photoAttachmentNotes: String = "",
        initialStatus: MaintenanceStatus = MaintenanceStatus.RECEIVED,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<MaintenanceOrderEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.MAINTENANCE_MANAGE)
        if (auth is AppResult.Error) return auth
        if (customerId.isBlank()) {
            return AppResult.Error("يرجى اختيار العميل بشكل صريح أولاً", "MISSING_CUSTOMER")
        }
        if (deviceBrand.isBlank() || deviceModel.isBlank() || faultDescription.isBlank()) {
            return AppResult.Error("يرجى إدخال بيانات الجهاز ووصف العطل", "INVALID_MAINTENANCE")
        }

        return try {
            database.withTransaction {
                val customer = database.catalogInventoryDao().getCustomerById(customerId)
                    ?: return@withTransaction AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND")
                val now = System.currentTimeMillis()
                val orderId = IdGenerator.newPrefixedId("MNT")
                val ticketNumber = IdGenerator.newReferenceNumber("MNT", now)

                if (depositMinor > 0L) {
                    val depRes = financialLedgerEngine.postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.DEPOSIT,
                        amountMinor = depositMinor,
                        sourceAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        sourceAccountId = customer.localId,
                        destinationAccountType = FinancialAccountType.CASH_DRAWER,
                        destinationAccountId = DEFAULT_MAIN_DRAWER_ID,
                        customerId = customer.localId,
                        relatedEntityType = "MAINTENANCE_DEPOSIT",
                        relatedEntityId = orderId,
                        descriptionArabic = "استلام عربون صيانة تذكرة رقم $ticketNumber (${deviceBrand.trim()} ${deviceModel.trim()}) - أمانة ودفعة مقدمة (Customer Advance)",
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = now
                    )
                    if (depRes is AppResult.Error) {
                        return@withTransaction depRes
                    }
                }

                val combinedNotes = buildList {
                    if (depositMinor > 0L) add("عربون مستلم: ${depositMinor / 100.0} ج.م")
                    if (customerNotes.isNotBlank()) add("ملاحظات العميل: ${customerNotes.trim()}")
                    if (photoAttachmentNotes.isNotBlank()) add("مرفقات الصور: ${photoAttachmentNotes.trim()}")
                }.joinToString(" | ")

                val order = MaintenanceOrderEntity(
                    localId = orderId,
                    shopId = shopId,
                    ticketNumber = ticketNumber,
                    customerId = customer.localId,
                    customerNameSnapshot = customer.fullName,
                    customerPhoneSnapshot = customer.phone,
                    deviceBrand = deviceBrand.trim(),
                    deviceModel = deviceModel.trim(),
                    deviceImeiOrSerial = deviceImeiOrSerial.trim(),
                    faultDescription = faultDescription.trim(),
                    diagnosticNotes = combinedNotes,
                    estimatedCostMinor = estimatedCostMinor.coerceAtLeast(0L),
                    depositPaidMinor = depositMinor.coerceAtLeast(0L),
                    status = initialStatus,
                    assignedTechnicianId = assignedTechnicianId?.takeIf { it.isNotBlank() },
                    receivedByUserId = actorUserId,
                    deviceId = deviceId,
                    receivedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
                database.operationsDao().insertMaintenanceOrder(order)
                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "MaintenanceOrder",
                    entityLocalId = order.localId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"orderId":"${order.localId}","ticket":"$ticketNumber","depositMinor":$depositMinor}""",
                    isFinancialLedgerRecord = depositMinor > 0L,
                    deviceId = deviceId,
                    timestamp = now
                )
                AppResult.Success(order)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تسجيل أمر الصيانة: ${e.message}", "MAINTENANCE_ERROR", e)
        }
    }

    /**
     * Collects payment for a Maintenance Order and posts it atomically to the Financial Ledger (Rule 20).
     */
    suspend fun collectMaintenanceOrderPayment(
        orderId: String,
        amountMinor: Long,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID,
        idempotencyKey: String? = null
    ): AppResult<TransactionEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.MAINTENANCE_MANAGE)
        if (auth is AppResult.Error) return auth
        if (amountMinor <= 0L) {
            return AppResult.Error("يرجى إدخال مبلغ تحصيل صحيح أكبر من الصفر", "INVALID_AMOUNT")
        }

        return try {
            database.withTransaction {
                val existing = database.operationsDao().getMaintenanceOrderById(orderId)
                    ?: return@withTransaction AppResult.Error("تذكرة الصيانة غير موجودة", "ORDER_NOT_FOUND")
                val now = System.currentTimeMillis()

                val txRes = financialLedgerEngine.postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = FinancialTransactionType.SALE,
                    amountMinor = amountMinor,
                    sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                    sourceAccountId = "MAINTENANCE_SERVICE_REVENUE",
                    destinationAccountType = paymentAccountType,
                    destinationAccountId = paymentAccountId,
                    customerId = existing.customerId,
                    relatedEntityType = "MAINTENANCE",
                    relatedEntityId = existing.localId,
                    descriptionArabic = "تحصيل دفعة صيانة تذكرة رقم ${existing.ticketNumber} (${existing.deviceBrand} ${existing.deviceModel}) ${notes.trim()}".trim(),
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = now,
                    idempotencyKey = idempotencyKey
                )
                if (txRes is AppResult.Error) {
                    return@withTransaction txRes
                }
                val tx = (txRes as AppResult.Success).data

                val nextStatus = if (existing.status == MaintenanceStatus.READY_FOR_PICKUP ||
                    existing.status == MaintenanceStatus.CUSTOMER_NOTIFIED
                ) {
                    MaintenanceStatus.PAYMENT_COLLECTED
                } else {
                    existing.status
                }

                val updated = existing.copy(
                    status = nextStatus,
                    completedLedgerTransactionId = tx.localId,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                database.operationsDao().updateMaintenanceOrder(updated)
                AppResult.Success(tx)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحصيل دفعة الصيانة: ${e.message}", "MAINTENANCE_PAYMENT_ERROR", e)
        }
    }

    /**
     * Consumes a spare part from Inventory for a Repair Ticket (`MAINTENANCE_USAGE`, Requirement 19).
     */
    suspend fun consumePartForMaintenanceOrder(
        maintenanceOrderId: String,
        productId: String,
        quantity: Int,
        unitChargeMinor: Long,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<MaintenancePartEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.MAINTENANCE_MANAGE)
        if (auth is AppResult.Error) return auth

        return try {
            database.withTransaction {
                val order = database.operationsDao().getMaintenanceOrderById(maintenanceOrderId)
                    ?: return@withTransaction AppResult.Error("تذكرة الصيانة غير موجودة", "ORDER_NOT_FOUND")
                val now = System.currentTimeMillis()

                val movRes = inventoryEngine.recordMovementInternal(
                    shopId = shopId,
                    productId = productId,
                    movementType = InventoryMovementType.MAINTENANCE_USAGE,
                    quantity = quantity,
                    reason = "صرف قطعة غيار لتذكرة صيانة #${order.ticketNumber}",
                    referenceType = "MAINTENANCE",
                    referenceId = order.localId,
                    performedByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = now
                )
                if (movRes is AppResult.Error) {
                    return@withTransaction movRes
                }
                val mov = (movRes as AppResult.Success).data

                val partEntity = MaintenancePartEntity(
                    localId = IdGenerator.newPrefixedId("MPT"),
                    shopId = shopId,
                    maintenanceOrderId = order.localId,
                    productId = productId,
                    quantity = quantity,
                    unitCostMinor = mov.unitCostMinor,
                    unitChargeMinor = unitChargeMinor,
                    inventoryMovementId = mov.localId,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                database.operationsDao().insertMaintenancePart(partEntity)

                val newPartsCost = order.partsCostMinor + mov.totalCostMinor
                database.operationsDao().updateMaintenanceOrder(
                    order.copy(
                        partsCostMinor = newPartsCost,
                        updatedAt = now,
                        version = order.version + 1L
                    )
                )

                AppResult.Success(partEntity)
            }
        } catch (e: Exception) {
            AppResult.Error(e.message ?: "تعذر صرف قطعة الغيار للصيانة", "MAINTENANCE_PART_ERROR", e)
        }
    }

    /**
     * Updates a Maintenance Order status, and if DELIVERED with a fee > 0, atomically posts a SALE/Service inflow to the ledger!
     */
    suspend fun updateMaintenanceStatus(
        orderId: String,
        newStatus: MaintenanceStatus,
        finalChargeMinor: Long = 0L,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<MaintenanceOrderEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.MAINTENANCE_MANAGE)
        if (auth is AppResult.Error) return auth

        return try {
            database.withTransaction {
                val existing = database.operationsDao().getMaintenanceOrderById(orderId)
                    ?: return@withTransaction AppResult.Error("أمر الصيانة غير موجود", "ORDER_NOT_FOUND")
                val now = System.currentTimeMillis()
                var ledgerTxId = existing.completedLedgerTransactionId

                if (newStatus == MaintenanceStatus.DELIVERED && finalChargeMinor > 0L && ledgerTxId == null) {
                    val priorDepositMinor = database.financialLedgerDao().getAllTransactionsChronological(shopId)
                        .filter { it.relatedEntityId == existing.localId && it.relatedEntityType == "MAINTENANCE_DEPOSIT" && it.status == LedgerTransactionStatus.POSTED }
                        .sumOf { it.amountMinor }

                    val (totalServiceRevenue, cashCollectedAtDelivery) = when {
                        // Caller passed remaining amount directly:
                        finalChargeMinor == (existing.estimatedCostMinor - priorDepositMinor) -> {
                            existing.estimatedCostMinor to finalChargeMinor
                        }
                        // Caller passed total amount including deposit:
                        finalChargeMinor >= priorDepositMinor -> {
                            finalChargeMinor to (finalChargeMinor - priorDepositMinor)
                        }
                        else -> {
                            finalChargeMinor to finalChargeMinor
                        }
                    }

                    val additionalLines = if (priorDepositMinor > 0L) {
                        listOf(
                            Triple(FinancialAccountType.EXTERNAL_CUSTOMER, existing.customerId, priorDepositMinor),
                            Triple(FinancialAccountType.CASH_DRAWER, paymentAccountId, -(totalServiceRevenue - cashCollectedAtDelivery))
                        )
                    } else emptyList()

                    val txRes = financialLedgerEngine.postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.SALE,
                        amountMinor = totalServiceRevenue,
                        sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        sourceAccountId = "MAINTENANCE_SERVICE_REVENUE",
                        destinationAccountType = FinancialAccountType.CASH_DRAWER,
                        destinationAccountId = paymentAccountId,
                        customerId = existing.customerId,
                        relatedEntityType = "MAINTENANCE",
                        relatedEntityId = existing.localId,
                        descriptionArabic = "اعتماد فاتورة صيانة نهائية تذكرة رقم ${existing.ticketNumber} (${existing.deviceBrand} ${existing.deviceModel})",
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = now,
                        additionalLines = additionalLines
                    )
                    if (txRes is AppResult.Error) {
                        return@withTransaction txRes
                    }
                    ledgerTxId = (txRes as AppResult.Success).data.localId
                }

                val finalTotalCalculated = if (finalChargeMinor > 0L) {
                    if (finalChargeMinor >= existing.estimatedCostMinor) finalChargeMinor else existing.estimatedCostMinor
                } else existing.estimatedCostMinor

                val updated = existing.copy(
                    status = newStatus,
                    finalTotalMinor = finalTotalCalculated,
                    depositPaidMinor = if (existing.depositPaidMinor > 0L) existing.depositPaidMinor else 0L,
                    completedLedgerTransactionId = ledgerTxId,
                    deliveredAt = if (newStatus == MaintenanceStatus.DELIVERED) now else existing.deliveredAt,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                database.operationsDao().updateMaintenanceOrder(updated)
                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث حالة أمر الصيانة: ${e.message}", "MAINTENANCE_UPDATE_ERROR", e)
        }
    }

    suspend fun executeSale(
        customerId: String?,
        items: List<SaleLineInput>,
        invoiceDiscountMinor: Long = 0L,
        taxBasisPoints: Int = 0,
        paidAmountMinorOverride: Long? = null,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        splitPayments: List<SalePaymentSplit> = emptyList(),
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = executeV4ProtectedSale(
        customerId = customerId,
        items = items,
        invoiceDiscountMinor = invoiceDiscountMinor,
        taxBasisPoints = taxBasisPoints,
        paidAmountMinorOverride = paidAmountMinorOverride,
        paymentAccountType = paymentAccountType,
        paymentAccountId = paymentAccountId,
        splitPayments = splitPayments,
        notes = notes,
        actorUserId = actorUserId,
        idempotencyKey = idempotencyKey
    )

    suspend fun recordCustomerPaymentCollection(
        customerId: String,
        amountMinor: Long,
        destinationAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        destinationAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeCustomerPaymentCollection(
        shopId = DEFAULT_SHOP_ID,
        customerId = customerId,
        amountMinor = amountMinor,
        destinationAccountType = destinationAccountType,
        destinationAccountId = destinationAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun executeSalesReturn(
        originalSaleId: String,
        reasonArabic: String,
        refundAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        refundAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicSalesReturn(
        shopId = DEFAULT_SHOP_ID,
        originalSaleId = originalSaleId,
        reasonArabic = reasonArabic,
        refundAccountType = refundAccountType,
        refundAccountId = refundAccountId,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun executePurchase(
        supplierId: String,
        items: List<PurchaseLineInput>,
        discountMinor: Long = 0L,
        paidAmountMinorOverride: Long? = null,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicPurchase(
        shopId = DEFAULT_SHOP_ID,
        supplierId = supplierId,
        items = items,
        discountMinor = discountMinor,
        paidAmountMinorOverride = paidAmountMinorOverride,
        paymentAccountType = paymentAccountType,
        paymentAccountId = paymentAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun executePurchaseReturn(
        originalPurchaseId: String,
        reasonArabic: String = "مرتجع مشتريات للمورد",
        refundAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        refundAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicPurchaseReturn(
        shopId = DEFAULT_SHOP_ID,
        originalPurchaseId = originalPurchaseId,
        reasonArabic = reasonArabic,
        refundAccountType = refundAccountType,
        refundAccountId = refundAccountId,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun executeWalletOperation(
        walletId: String,
        operationType: WalletOperationType,
        customerPhone: String,
        principalAmountMinor: Long,
        providerFeeMinor: Long = 0L,
        shopCommissionMinor: Long = 0L,
        targetWalletId: String? = null,
        cashDrawerId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicWalletTransfer(
        shopId = DEFAULT_SHOP_ID,
        walletId = walletId,
        cashDrawerId = cashDrawerId,
        targetWalletId = targetWalletId,
        transferKind = operationType.name,
        customerPhone = customerPhone,
        principalAmountMinor = principalAmountMinor,
        providerFeeMinor = providerFeeMinor,
        shopCommissionMinor = shopCommissionMinor,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun executeWalletOperation(
        walletId: String,
        transferKind: String,
        customerPhone: String,
        principalAmountMinor: Long,
        providerFeeMinor: Long = 0L,
        shopCommissionMinor: Long = 0L,
        targetWalletId: String? = null,
        cashDrawerId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicWalletTransfer(
        shopId = DEFAULT_SHOP_ID,
        walletId = walletId,
        cashDrawerId = cashDrawerId,
        targetWalletId = targetWalletId,
        transferKind = transferKind,
        customerPhone = customerPhone,
        principalAmountMinor = principalAmountMinor,
        providerFeeMinor = providerFeeMinor,
        shopCommissionMinor = shopCommissionMinor,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun reconcileWallet(
        walletId: String,
        actualCountedBalanceMinor: Long,
        reasonArabic: String,
        actorUserId: String = activeUserId,
        deviceId: String = DEFAULT_DEVICE_ID
    ) = financialLedgerEngine.reconcileWallet(
        shopId = DEFAULT_SHOP_ID,
        walletId = walletId,
        actualCountedBalanceMinor = actualCountedBalanceMinor,
        reasonArabic = reasonArabic,
        actorUserId = actorUserId,
        deviceId = deviceId
    )

    suspend fun executeCashOperation(
        operationType: FinancialTransactionType,
        amountMinor: Long,
        reasonArabic: String,
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicCashOperation(
        shopId = DEFAULT_SHOP_ID,
        cashDrawerId = DEFAULT_MAIN_DRAWER_ID,
        operationType = operationType,
        amountMinor = amountMinor,
        reasonArabic = reasonArabic,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun recordCapitalInjection(
        amountMinor: Long,
        destinationAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        destinationAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId
    ) = capitalManagementEngine.recordCapitalInjection(
        shopId = DEFAULT_SHOP_ID,
        amountMinor = amountMinor,
        destinationAccountType = destinationAccountType,
        destinationAccountId = destinationAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun recordCapitalWithdrawal(
        amountMinor: Long,
        sourceAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        sourceAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId
    ) = capitalManagementEngine.recordCapitalWithdrawal(
        shopId = DEFAULT_SHOP_ID,
        amountMinor = amountMinor,
        sourceAccountType = sourceAccountType,
        sourceAccountId = sourceAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun recordOpeningCapital(
        amountMinor: Long,
        destinationAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        destinationAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "رأس مال افتتاحي",
        actorUserId: String = activeUserId
    ) = capitalManagementEngine.recordOpeningCapital(
        shopId = DEFAULT_SHOP_ID,
        amountMinor = amountMinor,
        destinationAccountType = destinationAccountType,
        destinationAccountId = destinationAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun getCapitalStatement(shopId: String = DEFAULT_SHOP_ID) =
        capitalManagementEngine.getCapitalStatement(shopId)

    suspend fun executeCashCloseout(
        countedCashMinor: Long,
        notes: String,
        actorUserId: String = activeUserId
    ) = financialLedgerEngine.executeCashDrawerCloseout(
        shopId = DEFAULT_SHOP_ID,
        cashDrawerId = DEFAULT_MAIN_DRAWER_ID,
        countedCashMinor = countedCashMinor,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun executeExpense(
        categoryName: String,
        title: String,
        amountMinor: Long,
        sourceAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        sourceAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        notes: String = "",
        actorUserId: String = activeUserId,
        idempotencyKey: String? = null
    ) = financialLedgerEngine.executeAtomicExpense(
        shopId = DEFAULT_SHOP_ID,
        categoryName = categoryName,
        title = title,
        amountMinor = amountMinor,
        sourceAccountType = sourceAccountType,
        sourceAccountId = sourceAccountId,
        notes = notes,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID,
        idempotencyKey = idempotencyKey
    )

    suspend fun reverseLedgerTransaction(
        originalTransactionId: String,
        reasonArabic: String,
        actorUserId: String = activeUserId
    ) = financialLedgerEngine.reverseTransaction(
        shopId = DEFAULT_SHOP_ID,
        originalTransactionId = originalTransactionId,
        reasonArabic = reasonArabic,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    // --- MEKKA PHONE V4.1: Unified Intelligent Transaction Pipeline ---
    suspend fun parseTransactionIntent(
        rawText: String,
        modality: com.example.transaction.TransactionInputModality = com.example.transaction.TransactionInputModality.TEXT,
        shopId: String = DEFAULT_SHOP_ID
    ): com.example.transaction.TransactionDraft =
        transactionParser.parseNaturalInput(rawText, modality, shopId)

    suspend fun validateTransactionDraft(
        draft: com.example.transaction.TransactionDraft,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID
    ): com.example.transaction.TransactionDraftValidation =
        transactionValidationEngine.validateDraft(draft, actorUserId, shopId)

    suspend fun executeConfirmedTransactionDraft(
        draft: com.example.transaction.TransactionDraft,
        userConfirmed: Boolean = true,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID
    ): AppResult<com.example.transaction.TransactionExecutionResult> =
        transactionCoordinator.executeConfirmedDraft(draft, userConfirmed, actorUserId, shopId)

    // --- SMS Intelligence Delegation ---
    suspend fun ingestSmsMessage(
        senderAddress: String,
        rawMessageBody: String,
        autoConfirmOnHighConfidenceMatch: Boolean = false,
        actorUserId: String = activeUserId
    ): AppResult<SmsPipelineOutcome> = smsIntelligenceEngine.ingestSmsEvidence(
        shopId = activeShopId,
        senderAddress = senderAddress,
        rawMessageBody = rawMessageBody,
        deviceId = DEFAULT_DEVICE_ID,
        autoConfirmOnHighConfidenceMatch = autoConfirmOnHighConfidenceMatch,
        actorUserId = actorUserId
    )

    suspend fun registerExpectedWalletOperation(
        targetWalletId: String,
        expectedType: FinancialTransactionType,
        expectedAmountMinor: Long,
        expectedFeeMinor: Long = 0L,
        counterpartyPhone: String = ""
    ) = smsIntelligenceEngine.registerExpectedOperation(
        shopId = activeShopId,
        targetWalletId = targetWalletId,
        expectedType = expectedType,
        expectedAmountMinor = expectedAmountMinor,
        expectedFeeMinor = expectedFeeMinor,
        counterpartyPhone = counterpartyPhone,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun confirmPendingSms(
        pendingId: String,
        explicitWalletId: String? = null,
        actorUserId: String = activeUserId
    ) = smsIntelligenceEngine.confirmPendingSmsToLedger(
        shopId = activeShopId,
        pendingId = pendingId,
        explicitWalletId = explicitWalletId,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    suspend fun rejectOrIgnorePendingSms(
        pendingId: String,
        decisionStatus: String, // REJECTED or IGNORED
        actorUserId: String = activeUserId
    ) = smsIntelligenceEngine.updatePendingSmsReviewDecision(
        shopId = activeShopId,
        pendingId = pendingId,
        decisionStatus = decisionStatus,
        actorUserId = actorUserId,
        deviceId = DEFAULT_DEVICE_ID
    )

    // --- Sync, Backup, Restore & Diagnostics ---
    suspend fun runDeltaSyncCycle(
        isNetworkAvailable: Boolean = true,
        simulateTransientFailure: Boolean = false
    ): AppResult<SyncBatchExecutionReport> = syncCoordinator.executeDeltaSyncCycle(
        expectedShopId = activeShopId,
        isNetworkAvailable = isNetworkAvailable,
        simulateTransientFailure = simulateTransientFailure
    )

    suspend fun createVerifiedBackup(
        actorUserId: String = activeUserId
    ): AppResult<BackupMetadataEntity> {
        return backupEngine.createVerifiedBackup(
            shopId = DEFAULT_SHOP_ID,
            createdByUserId = actorUserId,
            deviceId = DEFAULT_DEVICE_ID,
            schemaVersion = DatabaseMigrations.CURRENT_SCHEMA_VERSION
        )
    }

    fun validateBackupJson(rawJson: String): BackupValidationReport =
        backupEngine.validateBackupJson(
            rawEnvelopeJson = rawJson,
            currentSchemaVersion = DatabaseMigrations.CURRENT_SCHEMA_VERSION
        )

    suspend fun executeSafeBackupRestore(
        rawEnvelopeJson: String,
        userConfirmed: Boolean,
        actorUserId: String = activeUserId,
        restoreMode: com.example.backup.RestoreMode = com.example.backup.RestoreMode.REPLACE
    ): AppResult<BackupValidationReport> {
        return backupEngine.executeSafeRestore(
            shopId = DEFAULT_SHOP_ID,
            rawEnvelopeJson = rawEnvelopeJson,
            userConfirmed = userConfirmed,
            actorUserId = actorUserId,
            deviceId = DEFAULT_DEVICE_ID,
            currentSchemaVersion = DatabaseMigrations.CURRENT_SCHEMA_VERSION,
            restoreMode = restoreMode
        )
    }

    suspend fun getAuthorizedReportsCenterBundle(
        filter: ReportsCenterFilter,
        actorUserId: String = activeUserId,
        shopId: String = activeShopId
    ): AppResult<ReportsCenterBundle> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.AUDIT_VIEW, shopId)
        if (auth is AppResult.Error) return auth
        return AppResult.Success(computeReportsCenterBundle(filter, shopId))
    }

    suspend fun runDataIntegrityHealthCheck(shopId: String = activeShopId): SystemIntegrityReport =
        dataIntegrityChecker.runFullHealthCheck(shopId)

    suspend fun computeTrialBalanceReport(shopId: String = activeShopId): TrialBalanceReport {
        val allTransactions = database.financialLedgerDao().getAllTransactionsChronological(shopId)
        return AccountingEngine.computeTrialBalance(shopId, allTransactions)
    }

    /**
     * Authorizes and returns sensitive financial reports (Trial Balance + Reports Center Bundle).
     * Enforces [PermissionCode.REPORTS_SENSITIVE_VIEW].
     */
    suspend fun getAuthorizedSensitiveReports(
        filter: ReportsCenterFilter,
        actorUserId: String = activeUserId,
        shopId: String = activeShopId
    ): AppResult<ReportsCenterBundle> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.REPORTS_SENSITIVE_VIEW, shopId)
        if (auth is AppResult.Error) return auth
        return AppResult.Success(computeReportsCenterBundle(filter, shopId))
    }

    /**
     * Builds the authoritative Reports Center Bundle (`مركز التقارير`, Part 3 Rules 1, 2, 3).
     * Never computes financial truth inside Compose; all figures come from Ledger & Inventory Engines.
     */
    suspend fun computeReportsCenterBundle(
        filter: ReportsCenterFilter,
        shopId: String = activeShopId
    ): ReportsCenterBundle {
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()
        val (startDay, endDay) = getTodayBoundsMillis()
        val (startMonth, endMonth) = getCurrentMonthBoundsMillis()

        val sales = opsDao.getAllSales(shopId)
        val saleItems = opsDao.getAllSaleItems(shopId)
        val refunds = opsDao.getAllRefunds(shopId)
        val purchases = opsDao.getAllPurchases(shopId)
        val expenses = opsDao.getAllExpenses(shopId)
        val maintenanceOrders = opsDao.getAllMaintenanceOrders(shopId)
        val maintenanceParts = opsDao.getAllMaintenanceParts(shopId)
        val transactions = ledgerDao.getAllTransactionsChronological(shopId)
        val products = catalogDao.observeProductInventoryProjections(shopId).first()
        val customers = catalogDao.observeCustomerBalanceProjections(shopId).first()
        val suppliers = catalogDao.observeSupplierBalanceProjections(shopId).first()
        val wallets = ledgerDao.observeWalletDerivedProjections(
            shopId = shopId,
            startOfDayMillis = startDay,
            endOfDayMillis = endDay,
            startOfMonthMillis = startMonth,
            endOfMonthMillis = endMonth
        ).first()

        return ReportsCenterEngine.buildReportsCenterBundle(
            filter = filter,
            sales = sales,
            saleItems = saleItems,
            refunds = refunds,
            purchases = purchases,
            expenses = expenses,
            maintenanceOrders = maintenanceOrders,
            maintenanceParts = maintenanceParts,
            transactions = transactions,
            products = products,
            customers = customers,
            suppliers = suppliers,
            wallets = wallets
        )
    }

    fun getTodayBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
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
        return start to cal.timeInMillis
    }

    fun getCurrentMonthBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
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
        return start to cal.timeInMillis
    }

    // =============================================================================================
    // MEKKA PHONE V4 — PART 1: SHIFT, PRICING, CREDIT, RETURNS/EXCHANGES, STOCK COUNT, IMEI & Z-REPORT
    // =============================================================================================

    /**
     * V4 Section 1 & 2: Opens a formal Cashier Shift (`CashSessionEntity` with status `OPEN`),
     * recording opening cash, opening wallet balances, cashier user, and audit log.
     */
    suspend fun openCashShift(
        openingCashInputMinor: Long? = null,
        notes: String = "",
        drawerId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CashSessionEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.CASH_OPERATE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val ledgerDao = database.financialLedgerDao()
                val existingOpen = ledgerDao.getOpenCashSession(shopId, drawerId)
                if (existingOpen != null) {
                    return@withTransaction AppResult.Error(
                        "توجد وردية خزينة مفتوحة بالفعل (${existingOpen.localId}) — يرجى إغلاق الوردية الحالية أولاً",
                        "SHIFT_ALREADY_OPEN"
                    )
                }

                val now = System.currentTimeMillis()
                val currentDerivedCash = ledgerDao.getDerivedAccountBalanceMinor(
                    shopId = shopId,
                    accountType = FinancialAccountType.CASH_DRAWER,
                    accountId = drawerId
                )
                val (startDay, endDay) = getTodayBoundsMillis(now)
                val (startMonth, endMonth) = getCurrentMonthBoundsMillis(now)
                val wallets = ledgerDao.observeWalletDerivedProjections(shopId, startDay, endDay, startMonth, endMonth).first()
                val walletJson = wallets.joinToString(prefix = "{", postfix = "}") { w ->
                    "\"${w.walletId}\":${w.derivedBalanceMinor}"
                }

                val session = CashSessionEntity(
                    localId = IdGenerator.newPrefixedId("CSH"),
                    shopId = shopId,
                    cashDrawerId = drawerId,
                    openedByUserId = actorUserId,
                    openedAt = now,
                    openingDerivedBalanceMinor = currentDerivedCash,
                    openingCashInputMinor = openingCashInputMinor ?: currentDerivedCash,
                    openingWalletBalancesJson = walletJson,
                    status = "OPEN",
                    notes = notes.trim(),
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
                ledgerDao.insertCashSession(session)

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "SHIFT_OPEN",
                        entityType = "CashSession",
                        entityId = session.localId,
                        summaryArabic = "فتح وردية خزينة جديدة (${session.localId}) برصيد افتتاحي ${Money.ofMinor(currentDerivedCash).formatArabic()}",
                        beforeStateJson = null,
                        afterStateJson = """{"shiftId":"${session.localId}","openingCashMinor":$currentDerivedCash}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(session)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر فتح وردية الخزينة: ${e.message}", "SHIFT_OPEN_ERROR", e)
        }
    }

    /**
     * Computes live cash flow breakdown for the active shift (or most recent shift).
     */
    suspend fun getShiftCashFlowBreakdown(
        shiftId: String? = null,
        countedCashMinor: Long? = null,
        drawerId: String = DEFAULT_MAIN_DRAWER_ID,
        shopId: String = DEFAULT_SHOP_ID
    ): ShiftCashFlowBreakdown? {
        val ledgerDao = database.financialLedgerDao()
        val session = if (!shiftId.isNullOrBlank()) {
            ledgerDao.getCashSessionById(shiftId)
        } else {
            ledgerDao.getOpenCashSession(shopId, drawerId)
                ?: ledgerDao.observeCashSessions(shopId).first().firstOrNull()
        } ?: return null

        val allTx = ledgerDao.getAllTransactionsChronological(shopId)
        val (startDay, endDay) = getTodayBoundsMillis()
        val (startMonth, endMonth) = getCurrentMonthBoundsMillis()
        val wallets = ledgerDao.observeWalletDerivedProjections(shopId, startDay, endDay, startMonth, endMonth).first()

        return ShiftManagementEngine.computeShiftBreakdown(
            session = session,
            shiftTransactions = allTx,
            currentWallets = wallets,
            countedCashMinor = countedCashMinor ?: session.closingCountedCashMinor
        )
    }

    /**
     * V4 Section 1 & 2: Closes the active shift with mandatory variance reason and Manager Approval (`SHIFT_VARIANCE_APPROVE`)
     * whenever `countedCashMinor != expectedBalanceMinor`.
     */
    suspend fun closeCashShift(
        countedCashMinor: Long,
        varianceReasonArabic: String = "",
        managerApprovedByUserId: String? = null,
        notes: String = "",
        drawerId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CashSessionEntity> {
        if (countedCashMinor < 0L) {
            return AppResult.Error("النقدية الفعلية المعدودة لا يمكن أن تكون سالبة", "INVALID_COUNTED_CASH")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.CASH_OPERATE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        val ledgerDao = database.financialLedgerDao()
        val openSession = ledgerDao.getOpenCashSession(shopId, drawerId)
            ?: return AppResult.Error(
                "لا توجد وردية مفتوحة حالياً لهذا الدرج ($drawerId) لإغلاقها",
                "NO_OPEN_SHIFT"
            )

        val expectedCash = ledgerDao.getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = drawerId
        )
        val variance = countedCashMinor - expectedCash
        val effectiveReason = varianceReasonArabic.trim().ifBlank { notes.trim() }

        if (variance != 0L) {
            if (effectiveReason.isBlank()) {
                return AppResult.Error(
                    "يوجد فرق في الخزينة (${Money.ofMinor(variance).formatArabic()}) — يجب إدخال سبب الفرق قبل إغلاق الوردية",
                    "MISSING_VARIANCE_REASON"
                )
            }
            val approverId = managerApprovedByUserId?.takeIf { it.isNotBlank() } ?: actorUserId
            val varianceAuth = authSecurityManager.authorizeAction(approverId, PermissionCode.SHIFT_VARIANCE_APPROVE, shopId)
            if (varianceAuth is AppResult.Error) {
                return AppResult.Error(
                    "إغلاق الوردية مع وجود فرق (${Money.ofMinor(variance).formatArabic()}) يتطلب اعتماد المدير أو المالك بصلاحية (SHIFT_VARIANCE_APPROVE)",
                    "SHIFT_VARIANCE_APPROVAL_REQUIRED"
                )
            }
        }

        val closeRes = financialLedgerEngine.executeCashDrawerCloseout(
            shopId = shopId,
            cashDrawerId = drawerId,
            countedCashMinor = countedCashMinor,
            notes = effectiveReason.ifBlank { "إغلاق وردية الخزينة مطابق" },
            actorUserId = actorUserId,
            deviceId = deviceId
        )
        if (closeRes is AppResult.Error) return closeRes
        val closedSession = (closeRes as AppResult.Success).data

        val (startDay, endDay) = getTodayBoundsMillis()
        val (startMonth, endMonth) = getCurrentMonthBoundsMillis()
        val wallets = ledgerDao.observeWalletDerivedProjections(shopId, startDay, endDay, startMonth, endMonth).first()
        val walletJson = wallets.joinToString(prefix = "{", postfix = "}") { w ->
            "\"${w.walletId}\":${w.derivedBalanceMinor}"
        }
        val approverId = if (variance != 0L) (managerApprovedByUserId?.takeIf { it.isNotBlank() } ?: actorUserId) else null
        val enrichedSession = closedSession.copy(
            closingWalletBalancesJson = walletJson,
            varianceReasonArabic = if (variance != 0L) effectiveReason else "",
            approvedByUserId = approverId,
            approvalStatus = if (variance == 0L) "NOT_REQUIRED" else "APPROVED"
        )
        ledgerDao.updateCashSession(enrichedSession)

        if (variance != 0L) {
            database.systemIntegrityDao().insertAuditLog(
                AuditLogEntity(
                    localId = IdGenerator.newPrefixedId("AUD"),
                    shopId = shopId,
                    actorUserId = approverId ?: actorUserId,
                    actorRoleCode = actor.roleCode,
                    actionType = "SHIFT_VARIANCE_APPROVED",
                    entityType = "CashSession",
                    entityId = enrichedSession.localId,
                    summaryArabic = "اعتماد فرق إغلاق وردية الخزينة (${Money.ofMinor(variance).formatArabic()}) — السبب: $effectiveReason",
                    beforeStateJson = """{"expectedMinor":$expectedCash}""",
                    afterStateJson = """{"countedMinor":$countedCashMinor,"varianceMinor":$variance,"approvedBy":"${approverId ?: actorUserId}"}""",
                    deviceId = deviceId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        return AppResult.Success(enrichedSession)
    }

    /**
     * Requirement P1-H: Shift Handover.
     * Reconciles and closes the active shift with variance verification,
     * records the handover audit trail, and immediately opens the next shift for the incoming cashier.
     */
    suspend fun executeShiftHandover(
        fromUserId: String,
        toUserId: String,
        countedCashMinor: Long,
        handoverReasonArabic: String,
        notes: String = "",
        drawerId: String = DEFAULT_MAIN_DRAWER_ID,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<ShiftHandoverRecord> {
        val ledgerDao = database.financialLedgerDao()
        val openShift = ledgerDao.getOpenCashSession(shopId, drawerId)
            ?: return AppResult.Error("لا توجد وردية مفتوحة حالياً لتسليمها", "NO_OPEN_SHIFT")

        val breakdown = getShiftCashFlowBreakdown(shiftId = openShift.localId, drawerId = drawerId, shopId = shopId)
            ?: return AppResult.Error("تعذر استخراج بيان تدفقات الوردية الحالية", "BREAKDOWN_ERROR")

        val expectedCash = breakdown.expectedClosingCashMinor
        val variance = countedCashMinor - expectedCash

        // Close the previous shift
        val closeRes = closeCashShift(
            countedCashMinor = countedCashMinor,
            varianceReasonArabic = if (variance != 0L) handoverReasonArabic else "",
            managerApprovedByUserId = fromUserId,
            notes = "تسليم وردية من $fromUserId إلى $toUserId: $notes".trim(),
            drawerId = drawerId,
            actorUserId = fromUserId,
            shopId = shopId,
            deviceId = deviceId
        )
        if (closeRes is AppResult.Error) return closeRes
        val closedSession = (closeRes as AppResult.Success).data

        // Open the new shift for the incoming user
        val openRes = openCashShift(
            openingCashInputMinor = countedCashMinor,
            notes = "استلام وردية جديدة من $fromUserId".trim(),
            drawerId = drawerId,
            actorUserId = toUserId,
            shopId = shopId,
            deviceId = deviceId
        )
        if (openRes is AppResult.Error) return openRes
        val newSession = (openRes as AppResult.Success).data

        val users = database.coreShopAuthDao().getAllUsers(shopId).associateBy { it.localId }
        val fromName = users[fromUserId]?.fullNameArabic ?: fromUserId
        val toName = users[toUserId]?.fullNameArabic ?: toUserId

        val now = System.currentTimeMillis()
        val handoverId = IdGenerator.newPrefixedId("HND")

        database.systemIntegrityDao().insertAuditLog(
            AuditLogEntity(
                localId = IdGenerator.newPrefixedId("AUD"),
                shopId = shopId,
                actorUserId = fromUserId,
                actorRoleCode = users[fromUserId]?.roleCode ?: "CASHIER",
                actionType = "SHIFT_HANDOVER",
                entityType = "ShiftHandover",
                entityId = handoverId,
                summaryArabic = "تسليم وردية نقدية من ($fromName) إلى ($toName) بمبلغ ${Money.ofMinor(countedCashMinor).formatArabic()} (فرق: ${Money.ofMinor(variance).formatArabic()})",
                beforeStateJson = """{"previousShiftId":"${closedSession.localId}","expectedMinor":$expectedCash}""",
                afterStateJson = """{"newShiftId":"${newSession.localId}","countedMinor":$countedCashMinor,"toUserId":"$toUserId"}""",
                deviceId = deviceId,
                timestamp = now
            )
        )

        val record = ShiftHandoverRecord(
            handoverId = handoverId,
            fromUserId = fromUserId,
            fromUserNameArabic = fromName,
            toUserId = toUserId,
            toUserNameArabic = toName,
            previousShiftId = closedSession.localId,
            newShiftId = newSession.localId,
            countedCashMinor = countedCashMinor,
            expectedCashMinor = expectedCash,
            varianceMinor = variance,
            handoverReasonArabic = handoverReasonArabic,
            timestamp = now,
            breakdown = breakdown
        )
        return AppResult.Success(record)
    }

    /**
     * V4 Section 4: Updates a Product's multi-tier pricing policy and records immutable [PriceHistoryEntity] rows
     * for every changed price field with User, Role, Reason, Old Price, and New Price.
     */
    suspend fun updateProductPricingPolicy(
        productId: String,
        defaultSellingPriceMinor: Long,
        minSellingPriceMinor: Long = 0L,
        wholesalePriceMinor: Long = 0L,
        specialCustomerPriceMinor: Long = 0L,
        maxDiscountMinor: Long = 0L,
        promotionalPriceMinor: Long = 0L,
        isPromotionActive: Boolean = false,
        reorderThresholdQty: Int? = null,
        reorderPointQty: Int? = null,
        warrantyMonths: Int? = null,
        reasonArabic: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<ProductEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PRICE_CHANGE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        if (defaultSellingPriceMinor <= 0L) {
            return AppResult.Error("سعر البيع الافتراضي (القطاعي) يجب أن يكون أكبر من الصفر", "INVALID_PRICE")
        }
        if (minSellingPriceMinor > defaultSellingPriceMinor) {
            return AppResult.Error("الحد الأدنى لسعر البيع لا يمكن أن يتجاوز سعر البيع القطاعي", "INVALID_MIN_PRICE")
        }
        val cleanReason = reasonArabic.trim().ifBlank { "تحديث سياسة تسعير الصنف" }

        return try {
            database.withTransaction {
                val catalogDao = database.catalogInventoryDao()
                val existing = catalogDao.getProductById(productId)
                    ?: return@withTransaction AppResult.Error("الصنف غير موجود", "PRODUCT_NOT_FOUND")
                val now = System.currentTimeMillis()

                val priceChanges = mutableListOf<Triple<String, Long, Long>>()
                if (existing.defaultSellingPriceMinor != defaultSellingPriceMinor) {
                    priceChanges += Triple("RETAIL", existing.defaultSellingPriceMinor, defaultSellingPriceMinor)
                }
                if (existing.minSellingPriceMinor != minSellingPriceMinor) {
                    priceChanges += Triple("MIN_ALLOWED", existing.minSellingPriceMinor, minSellingPriceMinor)
                }
                if (existing.wholesalePriceMinor != wholesalePriceMinor) {
                    priceChanges += Triple("WHOLESALE", existing.wholesalePriceMinor, wholesalePriceMinor)
                }
                if (existing.specialCustomerPriceMinor != specialCustomerPriceMinor) {
                    priceChanges += Triple("SPECIAL_CUSTOMER", existing.specialCustomerPriceMinor, specialCustomerPriceMinor)
                }
                if (existing.maxDiscountMinor != maxDiscountMinor) {
                    priceChanges += Triple("MAX_DISCOUNT", existing.maxDiscountMinor, maxDiscountMinor)
                }
                if (existing.promotionalPriceMinor != promotionalPriceMinor) {
                    priceChanges += Triple("PROMOTIONAL", existing.promotionalPriceMinor, promotionalPriceMinor)
                }

                val updated = existing.copy(
                    defaultSellingPriceMinor = defaultSellingPriceMinor,
                    minSellingPriceMinor = minSellingPriceMinor.coerceAtLeast(0L),
                    wholesalePriceMinor = wholesalePriceMinor.coerceAtLeast(0L),
                    specialCustomerPriceMinor = specialCustomerPriceMinor.coerceAtLeast(0L),
                    maxDiscountMinor = maxDiscountMinor.coerceAtLeast(0L),
                    promotionalPriceMinor = promotionalPriceMinor.coerceAtLeast(0L),
                    isPromotionActive = isPromotionActive,
                    reorderThresholdQty = reorderThresholdQty ?: existing.reorderThresholdQty,
                    reorderPointQty = reorderPointQty ?: existing.reorderPointQty,
                    warrantyMonths = warrantyMonths ?: existing.warrantyMonths,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                catalogDao.updateProduct(updated)

                for ((priceType, oldVal, newVal) in priceChanges) {
                    val hist = PriceHistoryEntity(
                        localId = IdGenerator.newPrefixedId("PRH"),
                        shopId = shopId,
                        productId = productId,
                        priceType = priceType,
                        oldPriceMinor = oldVal,
                        newPriceMinor = newVal,
                        changedByUserId = actorUserId,
                        changedByRoleCode = actor.roleCode,
                        reasonArabic = cleanReason,
                        deviceId = deviceId,
                        changedAt = now
                    )
                    catalogDao.insertPriceHistory(hist)
                }

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "PRICE_POLICY_UPDATE",
                        entityType = "Product",
                        entityId = productId,
                        summaryArabic = "تحديث أسعار وسياسات الصنف (${existing.nameArabic}) — عدد التعديلات السعرية: ${priceChanges.size} — السبب: $cleanReason",
                        beforeStateJson = """{"retail":${existing.defaultSellingPriceMinor},"min":${existing.minSellingPriceMinor},"wholesale":${existing.wholesalePriceMinor}}""",
                        afterStateJson = """{"retail":$defaultSellingPriceMinor,"min":$minSellingPriceMinor,"wholesale":$wholesalePriceMinor}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )

                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث سياسة تسعير الصنف: ${e.message}", "PRICE_POLICY_UPDATE_ERROR", e)
        }
    }

    fun observePriceHistoryForProduct(
        productId: String,
        shopId: String = DEFAULT_SHOP_ID
    ): Flow<List<PriceHistoryEntity>> =
        database.catalogInventoryDao().observePriceHistoryForProduct(shopId, productId)

    suspend fun getPriceHistoryForProduct(
        productId: String,
        shopId: String = DEFAULT_SHOP_ID
    ): List<PriceHistoryEntity> =
        database.catalogInventoryDao().getPriceHistoryForProduct(shopId, productId)

    fun observeRecentPriceHistory(
        shopId: String = DEFAULT_SHOP_ID,
        limit: Int = 200
    ): Flow<List<PriceHistoryEntity>> =
        database.catalogInventoryDao().observeRecentPriceHistory(shopId, limit)

    /**
     * V4 Section 5: Updates Customer Credit Limit, Default Credit Days, and Active Credit Status.
     */
    suspend fun updateCustomerCreditPolicy(
        customerId: String,
        creditLimitMinor: Long,
        defaultCreditDays: Int = 30,
        isCreditActive: Boolean = true,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<CustomerEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.CREDIT_LIMIT_OVERRIDE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val catalogDao = database.catalogInventoryDao()
                val existing = catalogDao.getCustomerById(customerId)
                    ?: return@withTransaction AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND")
                val now = System.currentTimeMillis()
                val updated = existing.copy(
                    creditLimitMinor = creditLimitMinor.coerceAtLeast(0L),
                    defaultCreditDays = defaultCreditDays.coerceAtLeast(1),
                    isActive = isCreditActive,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                catalogDao.updateCustomer(updated)
                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "CUSTOMER_CREDIT_POLICY_UPDATE",
                        entityType = "Customer",
                        entityId = customerId,
                        summaryArabic = "تحديث سياسة ائتمان العميل (${existing.fullName}): الحد الائتماني ${Money.ofMinor(creditLimitMinor).formatArabic()} — مهلة السداد $defaultCreditDays يوم",
                        beforeStateJson = """{"creditLimitMinor":${existing.creditLimitMinor},"creditDays":${existing.defaultCreditDays},"isActive":${existing.isActive}}""",
                        afterStateJson = """{"creditLimitMinor":$creditLimitMinor,"creditDays":$defaultCreditDays,"isActive":$isCreditActive}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )
                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث سياسة ائتمان العميل: ${e.message}", "CREDIT_POLICY_ERROR", e)
        }
    }

    /**
     * V4 Section 5: Computes full Customer Credit Standing & Debt Aging (0-7, 8-15, 16-30, 31-60, 60+ days).
     */
    suspend fun computeCustomerCreditStanding(
        customerId: String,
        nowMillis: Long = System.currentTimeMillis(),
        shopId: String = DEFAULT_SHOP_ID
    ): CustomerCreditStanding? {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val customer = catalogDao.getCustomerById(customerId) ?: return null
        val derivedDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
            accountId = customerId
        )
        val sales = opsDao.getSalesForCustomer(shopId, customerId)
        return CustomerCreditAgingEngine.computeCustomerCreditStanding(
            customer = customer,
            derivedBalanceMinor = derivedDebt,
            customerSales = sales,
            nowMillis = nowMillis
        )
    }

    /**
     * V4 Section 4 & 5: Executes a POS Sale with strict enforcement of:
     * - Product Price Floor (`minSellingPriceMinor`), Max Discount (`maxDiscountMinor`), and Below-WAC-Cost protection
     *   (requiring `PRICE_OVERRIDE_APPROVE` manager approval + reason if violated)
     * - Customer Credit Limit (`creditLimitMinor`) and Overdue Debt Aging protection
     *   (requiring `CREDIT_LIMIT_OVERRIDE` manager approval + reason if violated)
     */
    suspend fun executeV4ProtectedSale(
        customerId: String?,
        items: List<SaleLineInput>,
        invoiceDiscountMinor: Long = 0L,
        taxBasisPoints: Int = 0,
        paidAmountMinorOverride: Long? = null,
        pricingTier: PricingTier = PricingTier.RETAIL,
        priceOverrideApprovedByUserId: String? = null,
        priceOverrideReasonArabic: String = "",
        creditOverrideApprovedByUserId: String? = null,
        creditOverrideReasonArabic: String = "",
        dueDateTimestamp: Long? = null,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        splitPayments: List<SalePaymentSplit> = emptyList(),
        notes: String = "",
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID,
        idempotencyKey: String? = null
    ): AppResult<SaleEntity> {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val grossSubtotal = items.sumOf { (it.unitSellingPriceMinor * it.quantity.toLong()) - it.lineDiscountMinor }.coerceAtLeast(0L)
        val safeInvDisc = invoiceDiscountMinor.coerceIn(0L, grossSubtotal)
        val netBeforeTax = (grossSubtotal - safeInvDisc).coerceAtLeast(0L)
        val taxMinor = (netBeforeTax * taxBasisPoints.toLong()) / 10_000L
        val finalTotalMinor = netBeforeTax + taxMinor
        val effectivePaid = paidAmountMinorOverride ?: finalTotalMinor
        val newCreditMinor = (finalTotalMinor - effectivePaid).coerceAtLeast(0L)

        // 1. Evaluate Line Pricing Policy (Below Cost, Below Min Allowed Price, Exceeding Max Discount)
        val priceViolations = mutableListOf<String>()
        for (line in items) {
            val product = catalogDao.getProductById(line.productId)
                ?: return AppResult.Error("الصنف المطلوب غير موجود (${line.productId})", "PRODUCT_NOT_FOUND")
            val invRecord = catalogDao.getInventoryRecord(shopId, line.productId)
            val wacMinor = invRecord?.weightedAvgCostMinor ?: 0L
            val allocatedInvDisc = if (grossSubtotal > 0L) {
                val lineNet = (line.unitSellingPriceMinor * line.quantity.toLong()) - line.lineDiscountMinor
                (safeInvDisc * lineNet) / grossSubtotal
            } else {
                0L
            }
            val eval = PricingPolicyEngine.evaluateLinePricing(
                productId = product.localId,
                productNameArabic = product.nameArabic,
                quantity = line.quantity,
                defaultSellingPriceMinor = product.defaultSellingPriceMinor,
                minSellingPriceMinor = product.minSellingPriceMinor,
                wholesalePriceMinor = product.wholesalePriceMinor,
                specialCustomerPriceMinor = product.specialCustomerPriceMinor,
                maxDiscountMinor = product.maxDiscountMinor,
                promotionalPriceMinor = product.promotionalPriceMinor,
                isPromotionActive = product.isPromotionActive,
                unitWacCostMinor = wacMinor,
                unitSellingPriceMinor = line.unitSellingPriceMinor,
                lineDiscountMinor = line.lineDiscountMinor,
                allocatedInvoiceDiscountMinor = allocatedInvDisc,
                tier = pricingTier
            )
            if (eval.requiresManagerApproval) {
                priceViolations += eval.warningsArabic
            }
        }

        if (priceViolations.isNotEmpty()) {
            val approverId = priceOverrideApprovedByUserId?.takeIf { it.isNotBlank() }
            if (approverId == null || priceOverrideReasonArabic.isBlank()) {
                return AppResult.Error(
                    priceViolations.joinToString(" | ") + " — يتطلب اعتماد المدير (PRICE_OVERRIDE_APPROVE) مع ذكر سبب التجاوز",
                    "PRICE_POLICY_VIOLATION"
                )
            }
            val overrideAuth = authSecurityManager.authorizeAction(approverId, PermissionCode.PRICE_OVERRIDE_APPROVE, shopId)
            if (overrideAuth is AppResult.Error) {
                return AppResult.Error(
                    "المستخدم المحدد لاعتماد تجاوز السعر لا يملك صلاحية (PRICE_OVERRIDE_APPROVE)",
                    "UNAUTHORIZED_PRICE_OVERRIDE"
                )
            }
        }

        // 2. Evaluate Customer Credit Limit & Debt Aging Policy if sale has credit component
        if (newCreditMinor > 0L && !customerId.isNullOrBlank()) {
            val customer = catalogDao.getCustomerById(customerId)
                ?: return AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND")
            val currentDebt = database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                accountId = customerId
            )
            val customerSales = opsDao.getSalesForCustomer(shopId, customerId)
            val creditEval = CustomerCreditAgingEngine.evaluateCreditSaleEligibility(
                customer = customer,
                currentDebtMinor = currentDebt,
                newCreditAmountMinor = newCreditMinor,
                customerSales = customerSales
            )
            if (creditEval.isHardBlocked) {
                return AppResult.Error(
                    creditEval.reasonsArabic.joinToString(" | "),
                    "CUSTOMER_CREDIT_BLOCKED"
                )
            }
            if (creditEval.requiresManagerOverride) {
                val creditApproverId = creditOverrideApprovedByUserId?.takeIf { it.isNotBlank() }
                if (creditApproverId == null || creditOverrideReasonArabic.isBlank()) {
                    return AppResult.Error(
                        creditEval.reasonsArabic.joinToString(" | ") + " — يتطلب اعتماد المدير (CREDIT_LIMIT_OVERRIDE) مع ذكر سبب التجاوز",
                        "CREDIT_LIMIT_EXCEEDED"
                    )
                }
                val creditAuth = authSecurityManager.authorizeAction(creditApproverId, PermissionCode.CREDIT_LIMIT_OVERRIDE, shopId)
                if (creditAuth is AppResult.Error) {
                    return AppResult.Error(
                        "المستخدم المحدد لاعتماد تجاوز الائتمان لا يملك صلاحية (CREDIT_LIMIT_OVERRIDE)",
                        "UNAUTHORIZED_CREDIT_OVERRIDE"
                    )
                }
            }
        }

        val saleRes = financialLedgerEngine.executeAtomicSale(
            shopId = shopId,
            customerId = customerId,
            items = items,
            invoiceDiscountMinor = invoiceDiscountMinor,
            taxBasisPoints = taxBasisPoints,
            paidAmountMinorOverride = paidAmountMinorOverride,
            paymentAccountType = paymentAccountType,
            paymentAccountId = paymentAccountId,
            splitPayments = splitPayments,
            notes = notes,
            actorUserId = actorUserId,
            deviceId = deviceId,
            idempotencyKey = idempotencyKey,
            allowCreditLimitOverride = !creditOverrideApprovedByUserId.isNullOrBlank()
        )
        if (saleRes is AppResult.Error) return saleRes
        val createdSale = (saleRes as AppResult.Success).data

        val now = System.currentTimeMillis()
        val openSession = database.financialLedgerDao().getAnyOpenCashSession(shopId)
        val enrichedSale = createdSale.copy(
            dueDateTimestamp = dueDateTimestamp ?: createdSale.dueDateTimestamp,
            shiftId = openSession?.localId,
            pricingTierUsed = pricingTier.code
        )
        opsDao.updateSale(enrichedSale)

        if (priceViolations.isNotEmpty() && !priceOverrideApprovedByUserId.isNullOrBlank()) {
            database.systemIntegrityDao().insertAuditLog(
                AuditLogEntity(
                    localId = IdGenerator.newPrefixedId("AUD"),
                    shopId = shopId,
                    actorUserId = priceOverrideApprovedByUserId,
                    actorRoleCode = "MANAGER_OR_OWNER",
                    actionType = "PRICE_OVERRIDE_APPROVED",
                    entityType = "Sale",
                    entityId = enrichedSale.localId,
                    summaryArabic = "اعتماد تجاوز الحد الأدنى للسعر / الخصم في فاتورة (#${enrichedSale.invoiceNumber}) — السبب: $priceOverrideReasonArabic",
                    beforeStateJson = null,
                    afterStateJson = """{"saleId":"${enrichedSale.localId}","reason":"$priceOverrideReasonArabic"}""",
                    deviceId = deviceId,
                    timestamp = now
                )
            )
        }

        if (newCreditMinor > 0L && !creditOverrideApprovedByUserId.isNullOrBlank()) {
            database.systemIntegrityDao().insertAuditLog(
                AuditLogEntity(
                    localId = IdGenerator.newPrefixedId("AUD"),
                    shopId = shopId,
                    actorUserId = creditOverrideApprovedByUserId,
                    actorRoleCode = "MANAGER_OR_OWNER",
                    actionType = "CREDIT_LIMIT_OVERRIDE_APPROVED",
                    entityType = "Sale",
                    entityId = enrichedSale.localId,
                    summaryArabic = "اعتماد تجاوز الحد الائتماني للعميل في فاتورة (#${enrichedSale.invoiceNumber}) — السبب: $creditOverrideReasonArabic",
                    beforeStateJson = null,
                    afterStateJson = """{"saleId":"${enrichedSale.localId}","newCreditMinor":$newCreditMinor,"reason":"$creditOverrideReasonArabic"}""",
                    deviceId = deviceId,
                    timestamp = now
                )
            )
        }

        return AppResult.Success(enrichedSale)
    }

    /**
     * V4 Section 6: Computes Supplier Payables & Due Date Schedule.
     */
    suspend fun computeSupplierPayablesSchedule(
        defaultSupplierCreditDays: Int = 14,
        nowMillis: Long = System.currentTimeMillis(),
        shopId: String = DEFAULT_SHOP_ID
    ): SupplierPayablesSummary {
        val purchases = database.operationsDao().getAllPurchases(shopId)
        val supplierBalances = database.catalogInventoryDao().observeSupplierBalanceProjections(shopId).first()
        return SupplierPayablesScheduleEngine.buildPayablesSchedule(
            purchases = purchases,
            supplierBalances = supplierBalances,
            defaultSupplierCreditDays = defaultSupplierCreditDays,
            nowMillis = nowMillis
        )
    }

    /**
     * V4 Section 7: Executes an Atomic Partial or Full Sales Return with item-level quantity tracking,
     * condition handling (`RESTOCKABLE` vs `DAMAGED_SCRAP`), and flexible settlement (`CASH_REFUND`, `WALLET_REFUND`, `CUSTOMER_CREDIT`).
     */
    suspend fun executePartialSalesReturn(
        originalSaleId: String,
        returnLines: List<AdvancedReturnLineInput>,
        reasonArabic: String,
        settlementMode: ReturnSettlementMode = ReturnSettlementMode.CASH_REFUND,
        refundAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        refundAccountId: String = DEFAULT_MAIN_DRAWER_ID,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<RefundEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.REFUND_APPROVE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        if (reasonArabic.isBlank()) {
            return AppResult.Error("يرجى تحديد سبب المرتجع", "MISSING_RETURN_REASON")
        }

        return try {
            database.withTransaction {
                val opsDao = database.operationsDao()
                val catalogDao = database.catalogInventoryDao()
                val originalSale = opsDao.getSaleById(originalSaleId)
                    ?: return@withTransaction AppResult.Error("الفاتورة الأصلية غير موجودة", "SALE_NOT_FOUND")
                val saleItems = opsDao.getSaleItems(originalSaleId)

                val eval = AdvancedReturnsPolicyEngine.evaluateReturnOrExchange(
                    originalSale = originalSale,
                    originalSaleItems = saleItems,
                    requestedLines = returnLines
                )
                if (!eval.isValid) {
                    return@withTransaction AppResult.Error(
                        eval.validationErrorArabic ?: "بيانات المرتجع غير صالحة",
                        "INVALID_PARTIAL_RETURN"
                    )
                }

                val now = System.currentTimeMillis()
                val refundId = IdGenerator.newPrefixedId("RFD")
                val refundNumber = IdGenerator.newReferenceNumber("RFD", now)
                val cumulativeReturnedByItem = mutableMapOf<String, Int>()

                for (evLine in eval.evaluatedLines) {
                    val item = evLine.saleItem
                    val baseReturned = cumulativeReturnedByItem[item.localId] ?: item.returnedQuantity
                    val newReturnedQty = baseReturned + evLine.returnQuantity
                    cumulativeReturnedByItem[item.localId] = newReturnedQty
                    opsDao.updateSaleItem(
                        item.copy(returnedQuantity = newReturnedQty)
                    )

                    // 1. Return item into inventory at original sale WAC
                    val returnMovRes = inventoryEngine.recordMovementInternal(
                        shopId = shopId,
                        productId = item.productId,
                        variantId = item.variantId,
                        serialNumberId = item.serialNumberId,
                        movementType = InventoryMovementType.RETURN_IN,
                        quantity = evLine.returnQuantity,
                        inboundUnitCostMinor = item.unitCostAtSaleMinor,
                        reason = "مرتجع مبيعات ($refundNumber) من فاتورة #${originalSale.invoiceNumber}: $reasonArabic",
                        referenceType = "REFUND",
                        referenceId = refundId,
                        performedByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = now
                    )
                    if (returnMovRes is AppResult.Error) return@withTransaction returnMovRes
                    val returnMov = (returnMovRes as AppResult.Success).data

                    // 2. If item is DAMAGED_SCRAP, immediately move out of sellable stock via DAMAGE movement
                    if (evLine.condition == ReturnConditionType.DAMAGED_SCRAP) {
                        val dmgMovRes = inventoryEngine.recordMovementInternal(
                            shopId = shopId,
                            productId = item.productId,
                            variantId = item.variantId,
                            serialNumberId = item.serialNumberId,
                            movementType = InventoryMovementType.DAMAGE,
                            quantity = evLine.returnQuantity,
                            inboundUnitCostMinor = item.unitCostAtSaleMinor,
                            reason = "تحويل مرتجع معيب/تالف ($refundNumber) من فاتورة #${originalSale.invoiceNumber}",
                            referenceType = "REFUND_DAMAGED",
                            referenceId = refundId,
                            performedByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = now + 1L
                        )
                        if (dmgMovRes is AppResult.Error) return@withTransaction dmgMovRes
                        val dmgMov = (dmgMovRes as AppResult.Success).data

                        if (item.serialNumberId != null) {
                            val serRes = inventoryEngine.transitionSerialStatusInternal(
                                shopId = shopId,
                                serialId = item.serialNumberId,
                                newStatus = SerialLifecycleStatus.DAMAGED,
                                movementId = dmgMov.localId,
                                customerId = null,
                                expectedProductId = item.productId,
                                timestamp = now + 1L
                            )
                            if (serRes is AppResult.Error) return@withTransaction serRes
                        }
                    } else if (item.serialNumberId != null) {
                        val serRes = inventoryEngine.transitionSerialStatusInternal(
                            shopId = shopId,
                            serialId = item.serialNumberId,
                            newStatus = SerialLifecycleStatus.IN_STOCK,
                            movementId = returnMov.localId,
                            customerId = null,
                            expectedProductId = item.productId,
                            timestamp = now
                        )
                        if (serRes is AppResult.Error) return@withTransaction serRes
                    }
                }

                // 3. Post Financial Settlement to Ledger
                val refundMinor = eval.totalRefundAmountMinor.coerceAtLeast(1L)
                val resolvedSourceType: FinancialAccountType
                val resolvedSourceId: String
                val resolvedDestType: FinancialAccountType
                val resolvedDestId: String

                when (settlementMode) {
                    ReturnSettlementMode.CUSTOMER_CREDIT -> {
                        val custId = originalSale.customerId
                            ?: return@withTransaction AppResult.Error(
                                "رد المبلغ لرصيد العميل يتطلب أن تكون الفاتورة مرتبطة بعميل مسجل",
                                "MISSING_CUSTOMER_FOR_CREDIT_REFUND"
                            )
                        resolvedSourceType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT
                        resolvedSourceId = "SALES_RETURN_OFFSET"
                        resolvedDestType = FinancialAccountType.EXTERNAL_CUSTOMER
                        resolvedDestId = custId
                    }
                    ReturnSettlementMode.WALLET_REFUND -> {
                        resolvedSourceType = FinancialAccountType.WALLET
                        resolvedSourceId = refundAccountId
                        resolvedDestType = FinancialAccountType.EXTERNAL_CUSTOMER
                        resolvedDestId = originalSale.customerId ?: "WALK_IN_CUSTOMER"
                    }
                    else -> {
                        resolvedSourceType = refundAccountType
                        resolvedSourceId = refundAccountId
                        resolvedDestType = FinancialAccountType.EXTERNAL_CUSTOMER
                        resolvedDestId = originalSale.customerId ?: "WALK_IN_CUSTOMER"
                    }
                }

                val ledgerRes = financialLedgerEngine.postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = FinancialTransactionType.REFUND,
                    amountMinor = refundMinor,
                    sourceAccountType = resolvedSourceType,
                    sourceAccountId = resolvedSourceId,
                    destinationAccountType = resolvedDestType,
                    destinationAccountId = resolvedDestId,
                    customerId = originalSale.customerId,
                    relatedEntityType = "REFUND",
                    relatedEntityId = refundId,
                    descriptionArabic = "مرتجع مبيعات ($refundNumber) من فاتورة #${originalSale.invoiceNumber} — $reasonArabic",
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = now
                )
                if (ledgerRes is AppResult.Error) return@withTransaction ledgerRes
                val ledgerTx = (ledgerRes as AppResult.Success).data

                val newSaleStatus = if (eval.isFullInvoiceReturn) "REFUNDED" else "PARTIALLY_REFUNDED"
                opsDao.updateSale(
                    originalSale.copy(
                        status = newSaleStatus,
                        updatedAt = now,
                        version = originalSale.version + 1L,
                        syncState = SyncState.PENDING
                    )
                )

                val conditionsSummary = eval.evaluatedLines.map { it.condition }.distinct()
                val condCode = if (conditionsSummary.size == 1) conditionsSummary.first().code else "MIXED"
                val snapshotJson = eval.evaluatedLines.joinToString(prefix = "[", postfix = "]") { l ->
                    """{"saleItemId":"${l.saleItem.localId}","productId":"${l.saleItem.productId}","qty":${l.returnQuantity},"condition":"${l.condition.code}","refundMinor":${l.refundLineAmountMinor}}"""
                }

                val refundEntity = RefundEntity(
                    localId = refundId,
                    shopId = shopId,
                    originalSaleId = originalSaleId,
                    refundNumber = refundNumber,
                    refundAmountMinor = refundMinor,
                    reason = reasonArabic.trim(),
                    returnType = if (eval.isFullInvoiceReturn) "FULL_RETURN" else "PARTIAL_RETURN",
                    settlementMode = settlementMode.code,
                    productCondition = condCode,
                    returnedItemsSnapshotJson = snapshotJson,
                    refundAccountType = resolvedSourceType.name,
                    refundAccountId = resolvedSourceId,
                    ledgerTransactionId = ledgerTx.localId,
                    approvedByUserId = actorUserId,
                    deviceId = deviceId,
                    refundTimestamp = now,
                    createdAt = now,
                    updatedAt = now
                )
                opsDao.insertRefund(refundEntity)

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "SALE_PARTIAL_OR_FULL_RETURN",
                        entityType = "Refund",
                        entityId = refundEntity.localId,
                        summaryArabic = "اعتماد مرتجع (${refundEntity.returnType}) رقم $refundNumber للفاتورة #${originalSale.invoiceNumber} بقيمة ${Money.ofMinor(refundMinor).formatArabic()} (${settlementMode.arabicLabel})",
                        beforeStateJson = """{"saleStatus":"${originalSale.status}"}""",
                        afterStateJson = """{"saleStatus":"$newSaleStatus","refundMinor":$refundMinor,"condition":"$condCode"}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )

                AppResult.Success(refundEntity)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تنفيذ مرتجع المبيعات: ${e.message}", "PARTIAL_RETURN_ERROR", e)
        }
    }

    /**
     * V4 Section 7: Executes an Atomic Product Exchange (استبدال صنف بصنف آخر مع حساب فرق السعر تلقائياً).
     */
    suspend fun executeSalesExchange(
        originalSaleId: String,
        returnLines: List<AdvancedReturnLineInput>,
        replacementItems: List<SaleLineInput>,
        reasonArabic: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<Pair<RefundEntity, SaleEntity>> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.EXCHANGE_EXECUTE, shopId)
        if (auth is AppResult.Error) return auth
        if (replacementItems.isEmpty()) {
            return AppResult.Error("يرجى اختيار الصنف البديل لإتمام عملية الاستبدال", "MISSING_REPLACEMENT_ITEMS")
        }

        val originalSale = database.operationsDao().getSaleById(originalSaleId)
            ?: return AppResult.Error("الفاتورة الأصلية غير موجودة", "SALE_NOT_FOUND")

        val returnRes = executePartialSalesReturn(
            originalSaleId = originalSaleId,
            returnLines = returnLines,
            reasonArabic = reasonArabic.ifBlank { "استبدال أصناف من فاتورة #${originalSale.invoiceNumber}" },
            settlementMode = if (originalSale.customerId != null) ReturnSettlementMode.CUSTOMER_CREDIT else ReturnSettlementMode.CASH_REFUND,
            actorUserId = actorUserId,
            shopId = shopId,
            deviceId = deviceId
        )
        if (returnRes is AppResult.Error) return returnRes
        val refund = (returnRes as AppResult.Success).data

        val newSaleRes = executeSale(
            customerId = originalSale.customerId,
            items = replacementItems,
            notes = "فاتورة بديلة لاستبدال مرتجع #${refund.refundNumber}",
            actorUserId = actorUserId
        )
        if (newSaleRes is AppResult.Error) return newSaleRes
        val replacementSale = (newSaleRes as AppResult.Success).data

        val priceDiff = replacementSale.totalAmountMinor - refund.refundAmountMinor
        val linkedRefund = refund.copy(
            returnType = "EXCHANGE",
            settlementMode = ReturnSettlementMode.EXCHANGE_OFFSET.code,
            replacementSaleId = replacementSale.localId,
            priceDifferenceMinor = priceDiff,
            updatedAt = System.currentTimeMillis()
        )
        database.operationsDao().upsertRefunds(listOf(linkedRefund))

        return AppResult.Success(linkedRefund to replacementSale)
    }

    /**
     * V4 Section 9: Executes Physical Stock Count Reconciliation (الجرد الفعلي للمخزون وتسوية الفروقات).
     */
    suspend fun executeStockCountReconciliation(
        lines: List<StockCountLineInput>,
        reasonArabic: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<StockCountReconciliationReport> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.INVENTORY_ADJUST, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data
        if (lines.isEmpty()) {
            return AppResult.Error("يرجى إدخال صنف واحد على الأقل للجرد الفعلي", "EMPTY_STOCK_COUNT")
        }
        val cleanReason = reasonArabic.trim().ifBlank { "مطابقة وتسوية الجرد الفعلي للمخزون" }

        return try {
            database.withTransaction {
                val catalogDao = database.catalogInventoryDao()
                val now = System.currentTimeMillis()
                val sessionId = IdGenerator.newReferenceNumber("CNT", now)
                val varianceLines = mutableListOf<StockCountLineVariance>()
                var surplusMinor = 0L
                var shortageMinor = 0L
                var adjustedCount = 0

                for (input in lines) {
                    if (input.countedQuantity < 0) {
                        return@withTransaction AppResult.Error(
                            "الكمية المعدودة للصنف لا يمكن أن تكون سالبة",
                            "NEGATIVE_COUNTED_QTY"
                        )
                    }
                    val product = catalogDao.getProductById(input.productId)
                        ?: return@withTransaction AppResult.Error("الصنف غير موجود (${input.productId})", "PRODUCT_NOT_FOUND")
                    val systemQty = catalogDao.getDerivedStockQuantity(shopId, input.productId)
                    val invRec = catalogDao.getInventoryRecord(shopId, input.productId)
                    val wacMinor = invRec?.weightedAvgCostMinor ?: 0L
                    val varianceQty = input.countedQuantity - systemQty
                    val varianceVal = varianceQty.toLong() * wacMinor
                    var movId: String? = null

                    if (varianceQty != 0) {
                        val movType = when {
                            varianceQty > 0 -> InventoryMovementType.ADJUSTMENT_IN
                            input.isDamagedOrScrap -> InventoryMovementType.DAMAGE
                            else -> InventoryMovementType.ADJUSTMENT_OUT
                        }
                        val movRes = inventoryEngine.recordMovementInternal(
                            shopId = shopId,
                            productId = input.productId,
                            movementType = movType,
                            quantity = kotlin.math.abs(varianceQty),
                            inboundUnitCostMinor = wacMinor.coerceAtLeast(100L),
                            reason = "تسوية جرد فعلي ($sessionId): $cleanReason",
                            referenceType = "STOCK_COUNT",
                            referenceId = sessionId,
                            performedByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = now
                        )
                        if (movRes is AppResult.Error) return@withTransaction movRes
                        movId = (movRes as AppResult.Success).data.localId
                        adjustedCount++
                        if (varianceVal > 0L) {
                            surplusMinor += varianceVal
                        } else {
                            shortageMinor += kotlin.math.abs(varianceVal)
                        }
                    }

                    varianceLines += StockCountLineVariance(
                        productId = product.localId,
                        productNameArabic = product.nameArabic,
                        sku = product.sku,
                        systemQuantity = systemQty,
                        countedQuantity = input.countedQuantity,
                        varianceQuantity = varianceQty,
                        unitWacMinor = wacMinor,
                        varianceValueMinor = varianceVal,
                        movementId = movId
                    )
                }

                val report = StockCountReconciliationReport(
                    sessionId = sessionId,
                    executedAt = now,
                    executedByUserId = actorUserId,
                    reasonArabic = cleanReason,
                    lines = varianceLines,
                    adjustedLinesCount = adjustedCount,
                    totalSurplusValueMinor = surplusMinor,
                    totalShortageValueMinor = shortageMinor,
                    netVarianceValueMinor = surplusMinor - shortageMinor
                )

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "STOCK_COUNT_RECONCILIATION",
                        entityType = "InventoryMovement",
                        entityId = sessionId,
                        summaryArabic = "إتمام مطابقة الجرد الفعلي ($sessionId): تم فحص ${lines.size} صنف وتسوية $adjustedCount صنف",
                        beforeStateJson = null,
                        afterStateJson = """{"sessionId":"$sessionId","adjusted":$adjustedCount,"surplusMinor":$surplusMinor,"shortageMinor":$shortageMinor}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )

                AppResult.Success(report)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر إتمام تسوية الجرد الفعلي: ${e.message}", "STOCK_COUNT_ERROR", e)
        }
    }

    /**
     * V4 Section 9: Computes Product Velocity Classification (`FAST_MOVING`, `NORMAL_MOVING`, `SLOW_MOVING`)
     * and Reorder Point alerts across all catalog products.
     */
    suspend fun computeProductVelocityAndReorderReports(
        periodDays: Int = 30,
        nowMillis: Long = System.currentTimeMillis(),
        shopId: String = DEFAULT_SHOP_ID
    ): List<ProductReorderAndVelocityReport> {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val products = catalogDao.observeProductInventoryProjections(shopId).first()
        val periodStart = nowMillis - (periodDays.coerceAtLeast(1).toLong() * 24L * 60L * 60L * 1000L)
        val sales = opsDao.getAllSales(shopId).filter {
            it.saleTimestamp >= periodStart && it.status != "REFUNDED" && it.status != "CANCELLED"
        }
        val saleMap = sales.associateBy { it.localId }
        val saleItems = opsDao.getAllSaleItems(shopId).filter { it.saleId in saleMap.keys }

        return products.map { proj ->
            val itemsForProd = saleItems.filter { it.productId == proj.productId }
            val soldUnits = itemsForProd.sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) }
            val revenue = itemsForProd.sumOf { it.lineTotalMinor }
            val cogs = itemsForProd.sumOf { it.unitCostAtSaleMinor * it.quantity.toLong() }
            val lastSaleTs = itemsForProd.mapNotNull { saleMap[it.saleId]?.saleTimestamp }.maxOrNull()

            val velocity = when {
                soldUnits >= 5 -> ProductVelocityClass.FAST_MOVING
                soldUnits >= 1 -> ProductVelocityClass.NORMAL_MOVING
                else -> ProductVelocityClass.SLOW_MOVING
            }
            val isOut = proj.derivedStockQty <= 0
            val isLow = proj.derivedStockQty in 1..proj.reorderThresholdQty
            val effectiveReorderPoint = maxOf(proj.reorderPointQty, proj.reorderThresholdQty)
            val isReorderReached = proj.derivedStockQty <= effectiveReorderPoint
            val recommendedOrderQty = if (isReorderReached) {
                maxOf(effectiveReorderPoint * 2 - proj.derivedStockQty, soldUnits.coerceAtLeast(3))
            } else {
                0
            }
            val alertLabel = when {
                isOut -> "نفد من المخزون (Out of Stock)"
                isLow -> "مخزون حرج (تحت الحد الأدنى)"
                isReorderReached -> "وصل لنقطة إعادة الطلب"
                else -> "رصيد آمن"
            }

            ProductReorderAndVelocityReport(
                productId = proj.productId,
                sku = proj.sku,
                nameArabic = proj.nameArabic,
                currentStockQty = proj.derivedStockQty,
                reorderThresholdQty = proj.reorderThresholdQty,
                reorderPointQty = effectiveReorderPoint,
                soldUnitsInPeriod = soldUnits,
                revenueInPeriodMinor = revenue,
                profitInPeriodMinor = revenue - cogs,
                lastSaleTimestamp = lastSaleTs,
                velocityClass = velocity,
                isOutOfStock = isOut,
                isLowStockCritical = isLow,
                isReorderPointReached = isReorderReached,
                recommendedOrderQty = recommendedOrderQty,
                alertLabelArabic = alertLabel
            )
        }
    }

    /**
     * V4 Section 10: Builds the complete IMEI / Serial Traceability Timeline & Warranty Status
     * from Purchase -> Sale -> Customer -> Warranty -> Returns -> Maintenance Tickets.
     */
    suspend fun buildImeiTraceabilityTimeline(
        serialOrImei: String,
        nowMillis: Long = System.currentTimeMillis(),
        shopId: String = DEFAULT_SHOP_ID
    ): AppResult<ImeiTraceabilityTimeline> {
        val clean = serialOrImei.trim()
        if (clean.isBlank()) {
            return AppResult.Error("يرجى إدخال رقم السيريال / IMEI للبحث", "EMPTY_IMEI")
        }
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()

        val serial = catalogDao.getSerialByImeiAnyStatus(shopId, clean)
            ?: catalogDao.getSerialById(clean)
            ?: return AppResult.Error("لم يتم العثور على جهاز مسجل برقم IMEI / السيريال ($clean)", "SERIAL_NOT_FOUND")

        val product = catalogDao.getProductById(serial.productId)
        val movements = catalogDao.getMovementsForSerialChronological(shopId, serial.localId)
        val purchaseItems = opsDao.getAllPurchaseItems(shopId).filter {
            it.serialNumberId == serial.localId || it.serialOrImei == serial.serialOrImei
        }
        val purchasesMap = opsDao.getAllPurchases(shopId).associateBy { it.localId }
        val suppliersMap = catalogDao.getAllSuppliers(shopId).associateBy { it.localId }

        val saleItems = opsDao.getAllSaleItems(shopId).filter {
            it.serialNumberId == serial.localId || it.serialOrImei == serial.serialOrImei
        }
        val salesMap = opsDao.getAllSales(shopId).associateBy { it.localId }
        val customersMap = catalogDao.getAllCustomers(shopId).associateBy { it.localId }
        val refunds = opsDao.getAllRefunds(shopId)
        val maintOrders = opsDao.getAllMaintenanceOrders(shopId).filter {
            it.deviceImeiOrSerial.equals(serial.serialOrImei, ignoreCase = true) ||
                it.deviceSerial.equals(serial.serialOrImei, ignoreCase = true)
        }

        val latestPurchaseItem = purchaseItems.maxByOrNull { it.createdAt }
        val latestPurchase = latestPurchaseItem?.let { purchasesMap[it.purchaseId] }
        val supplier = latestPurchase?.let { suppliersMap[it.supplierId] }

        val latestSaleItem = saleItems.maxByOrNull { it.createdAt }
        val latestSale = latestSaleItem?.let { salesMap[it.saleId] }
        val customer = (serial.currentCustomerId ?: latestSale?.customerId)?.let { customersMap[it] }

        val warrantyMonths = product?.warrantyMonths ?: 12
        val saleTs = latestSale?.saleTimestamp
        val warrantyExpiryTs = if (saleTs != null && warrantyMonths > 0) {
            saleTs + (warrantyMonths.toLong() * 30L * 24L * 60L * 60L * 1000L)
        } else {
            null
        }
        val isWarrantyActive = warrantyExpiryTs != null && nowMillis <= warrantyExpiryTs && serial.status == SerialLifecycleStatus.SOLD
        val remainingDays = if (warrantyExpiryTs != null && nowMillis <= warrantyExpiryTs) {
            ((warrantyExpiryTs - nowMillis) / (24L * 60L * 60L * 1000L)).toInt()
        } else {
            0
        }

        val matchingRefunds = refunds.filter { ref ->
            saleItems.any { it.saleId == ref.originalSaleId } || ref.returnedItemsSnapshotJson.contains(serial.localId)
        }

        val events = mutableListOf<ImeiLifecycleEvent>()
        for (mov in movements) {
            events += ImeiLifecycleEvent(
                timestamp = mov.timestamp,
                eventTypeCode = mov.movementType.name,
                titleArabic = mov.movementType.arabicLabel,
                detailsArabic = mov.reason,
                referenceNumber = mov.referenceId,
                amountMinor = mov.unitCostMinor
            )
        }
        if (latestPurchase != null && events.none { it.eventTypeCode == "PURCHASE_IN" }) {
            events += ImeiLifecycleEvent(
                timestamp = latestPurchase.purchaseTimestamp,
                eventTypeCode = "PURCHASE_IN",
                titleArabic = "توريد وشراء الجهاز",
                detailsArabic = "فاتورة شراء #${latestPurchase.invoiceNumber} من المورد (${supplier?.companyName ?: "-"})",
                referenceNumber = latestPurchase.invoiceNumber,
                counterpartyNameArabic = supplier?.companyName,
                amountMinor = latestPurchaseItem?.unitCostMinor ?: serial.acquisitionCostMinor
            )
        }
        if (latestSale != null) {
            events += ImeiLifecycleEvent(
                timestamp = latestSale.saleTimestamp,
                eventTypeCode = "SALE_OUT",
                titleArabic = "بيع الجهاز للعميل",
                detailsArabic = "فاتورة بيع #${latestSale.invoiceNumber} للعميل (${customer?.fullName ?: "عميل نقدي"})",
                referenceNumber = latestSale.invoiceNumber,
                counterpartyNameArabic = customer?.fullName,
                amountMinor = latestSaleItem?.unitSellingPriceMinor
            )
        }
        for (ref in matchingRefunds) {
            events += ImeiLifecycleEvent(
                timestamp = ref.refundTimestamp,
                eventTypeCode = ref.returnType,
                titleArabic = "مرتجع / استبدال مبيعات (#${ref.refundNumber})",
                detailsArabic = "السبب: ${ref.reason} — الحالة: ${ref.productCondition}",
                referenceNumber = ref.refundNumber,
                amountMinor = ref.refundAmountMinor
            )
        }
        for (mnt in maintOrders) {
            events += ImeiLifecycleEvent(
                timestamp = mnt.receivedAt,
                eventTypeCode = "MAINTENANCE",
                titleArabic = "تذكرة صيانة (#${mnt.ticketNumber}) — ${mnt.status.arabicLabel}",
                detailsArabic = "العطل: ${mnt.faultDescription} | الإجراء: ${mnt.repairActionsArabic.ifBlank { mnt.diagnosticNotes }}",
                referenceNumber = mnt.ticketNumber,
                counterpartyNameArabic = mnt.customerNameSnapshot,
                amountMinor = mnt.finalTotalMinor.takeIf { it > 0L } ?: mnt.estimatedCostMinor
            )
        }

        val projection = ImeiRegistryProjection(
            serialEntity = serial,
            productNameArabic = product?.nameArabic ?: serial.productId,
            productSku = product?.sku ?: "-",
            saleStatusArabic = serial.status.arabicLabel,
            purchaseStatusArabic = if (latestPurchase != null) "مشتراة بفاتورة #${latestPurchase.invoiceNumber}" else "مسجل بالمخزون",
            customerId = customer?.localId,
            customerNameArabic = customer?.fullName,
            customerPhone = customer?.phone,
            saleInvoiceNumber = latestSale?.invoiceNumber,
            purchaseInvoiceNumber = latestPurchase?.invoiceNumber,
            supplierId = supplier?.localId,
            supplierNameArabic = supplier?.companyName,
            purchaseTimestamp = latestPurchase?.purchaseTimestamp ?: serial.createdAt,
            saleTimestamp = saleTs,
            warrantyMonths = warrantyMonths,
            warrantyExpiryTimestamp = warrantyExpiryTs,
            isWarrantyActive = isWarrantyActive,
            remainingWarrantyDays = remainingDays,
            returnRecordsCount = matchingRefunds.size,
            maintenanceTicketsCount = maintOrders.size
        )

        val warrantyLabel = when {
            saleTs == null -> "الضمان يبدأ عند البيع ($warrantyMonths شهر)"
            isWarrantyActive -> "ساري الضمان (متبقي $remainingDays يوم)"
            else -> "انتهى الضمان"
        }

        return AppResult.Success(
            ImeiTraceabilityTimeline(
                projection = projection,
                movements = movements,
                timelineEvents = events.sortedBy { it.timestamp },
                warrantyStatusArabic = warrantyLabel
            )
        )
    }

    /**
     * V4 Section 8: Advances a Maintenance Order through the 14-stage professional repair lifecycle:
     * Diagnosis & Estimate -> Customer Approval -> Repair & Parts -> Testing -> Ready & Notification -> Payment & Delivery.
     */
    suspend fun updateMaintenanceWorkflowStage(
        orderId: String,
        targetStatus: MaintenanceStatus,
        diagnosticNotes: String? = null,
        repairActionsArabic: String? = null,
        warrantyNotesArabic: String? = null,
        estimatedCostMinor: Long? = null,
        laborFeeMinor: Long? = null,
        isCustomerApproved: Boolean? = null,
        isTestingPassed: Boolean? = null,
        isCustomerNotified: Boolean? = null,
        assignedTechnicianId: String? = null,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        deviceId: String = DEFAULT_DEVICE_ID
    ): AppResult<MaintenanceOrderEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.MAINTENANCE_MANAGE, shopId)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val opsDao = database.operationsDao()
                val existing = opsDao.getMaintenanceOrderById(orderId)
                    ?: return@withTransaction AppResult.Error("تذكرة الصيانة غير موجودة", "MAINTENANCE_NOT_FOUND")
                val now = System.currentTimeMillis()

                val newEstimated = estimatedCostMinor ?: existing.estimatedCostMinor
                val newLabor = laborFeeMinor ?: existing.laborFeeMinor
                val customerApproved = isCustomerApproved ?: (existing.isCustomerApproved || targetStatus.stageOrder >= MaintenanceStatus.IN_REPAIR.stageOrder)
                val testingPassed = isTestingPassed ?: (existing.isTestingPassed || targetStatus.stageOrder >= MaintenanceStatus.READY_FOR_PICKUP.stageOrder)
                val customerNotified = isCustomerNotified ?: (existing.isCustomerNotified || targetStatus == MaintenanceStatus.CUSTOMER_NOTIFIED)

                if (targetStatus == MaintenanceStatus.IN_REPAIR && !customerApproved) {
                    return@withTransaction AppResult.Error(
                        "لا يمكن بدء الإصلاح قبل تسجيل موافقة العميل على التكلفة التقديرية",
                        "CUSTOMER_APPROVAL_REQUIRED"
                    )
                }

                val updated = existing.copy(
                    status = targetStatus,
                    diagnosticNotes = diagnosticNotes?.trim() ?: existing.diagnosticNotes,
                    repairActionsArabic = repairActionsArabic?.trim() ?: existing.repairActionsArabic,
                    warrantyNotesArabic = warrantyNotesArabic?.trim() ?: existing.warrantyNotesArabic,
                    estimatedCostMinor = newEstimated,
                    laborFeeMinor = newLabor,
                    assignedTechnicianId = assignedTechnicianId ?: existing.assignedTechnicianId,
                    isCustomerApproved = customerApproved,
                    customerApprovedAt = if (customerApproved && existing.customerApprovedAt == null) now else existing.customerApprovedAt,
                    isTestingPassed = testingPassed,
                    isCustomerNotified = customerNotified,
                    customerNotifiedAt = if (customerNotified && existing.customerNotifiedAt == null) now else existing.customerNotifiedAt,
                    deliveredAt = if (targetStatus == MaintenanceStatus.DELIVERED && existing.deliveredAt == null) now else existing.deliveredAt,
                    closedAt = if (targetStatus == MaintenanceStatus.CLOSED && existing.closedAt == null) now else existing.closedAt,
                    updatedAt = now,
                    version = existing.version + 1L,
                    syncState = SyncState.PENDING
                )
                opsDao.updateMaintenanceOrder(updated)

                database.systemIntegrityDao().insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "MAINTENANCE_STAGE_TRANSITION",
                        entityType = "MaintenanceOrder",
                        entityId = updated.localId,
                        summaryArabic = "انتقال تذكرة الصيانة (#${updated.ticketNumber}) من (${existing.status.arabicLabel}) إلى (${targetStatus.arabicLabel})",
                        beforeStateJson = """{"status":"${existing.status.name}"}""",
                        afterStateJson = """{"status":"${targetStatus.name}","approved":$customerApproved,"tested":$testingPassed}""",
                        deviceId = deviceId,
                        timestamp = now
                    )
                )

                AppResult.Success(updated)
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تحديث مرحلة تذكرة الصيانة: ${e.message}", "MAINTENANCE_STAGE_ERROR", e)
        }
    }

    /**
     * V4 Section 3: Computes the authoritative True Profitability Report for any period.
     */
    suspend fun computeTrueProfitabilityReport(
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE,
        shopId: String = DEFAULT_SHOP_ID
    ): TrueProfitabilityReport {
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()

        val sales = opsDao.getAllSales(shopId)
        val saleItems = opsDao.getAllSaleItems(shopId)
        val refunds = opsDao.getAllRefunds(shopId)
        val maintOrders = opsDao.getAllMaintenanceOrders(shopId)
        val maintParts = opsDao.getAllMaintenanceParts(shopId)
        val expenses = opsDao.getAllExpenses(shopId)
        val movements = catalogDao.getAllMovementsChronological(shopId)
        val transactions = ledgerDao.getAllTransactionsChronological(shopId)
        val products = catalogDao.observeProductInventoryProjections(shopId).first()
        val categories = catalogDao.getAllCategories(shopId).associate { it.localId to it.nameArabic }
        val brands = catalogDao.getAllBrands(shopId).associate { it.localId to it.name }

        return TrueProfitabilityEngine.computeTrueProfitability(
            startMillis = startMillis,
            endMillis = endMillis,
            sales = sales,
            saleItems = saleItems,
            refunds = refunds,
            maintenanceOrders = maintOrders,
            maintenanceParts = maintParts,
            expenses = expenses,
            inventoryMovements = movements,
            ledgerTransactions = transactions,
            products = products,
            categories = categories,
            brands = brands
        )
    }

    /**
     * V4 Section 13: Computes the Daily Operational Z-Report (التقرير التشغيلي اليومي الشامل).
     */
    suspend fun computeDailyOperationalZReport(
        nowMillis: Long = System.currentTimeMillis(),
        drawerId: String = DEFAULT_MAIN_DRAWER_ID,
        shopId: String = DEFAULT_SHOP_ID
    ): DailyOperationalZReport {
        val (dayStart, dayEnd) = getTodayBoundsMillis(nowMillis)
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()

        val sales = opsDao.getAllSales(shopId)
        val saleItems = opsDao.getAllSaleItems(shopId)
        val refunds = opsDao.getAllRefunds(shopId)
        val purchases = opsDao.getAllPurchases(shopId)
        val expenses = opsDao.getAllExpenses(shopId)
        val maintOrders = opsDao.getAllMaintenanceOrders(shopId)
        val maintParts = opsDao.getAllMaintenanceParts(shopId)
        val movements = catalogDao.getAllMovementsChronological(shopId)
        val transactions = ledgerDao.getAllTransactionsChronological(shopId)
        val cashSessions = ledgerDao.observeCashSessions(shopId).first()
        val currentDrawerBalance = ledgerDao.getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = drawerId
        )

        return DailyOperationalZReportEngine.buildDailyZReport(
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            sales = sales,
            saleItems = saleItems,
            refunds = refunds,
            purchases = purchases,
            expenses = expenses,
            maintenanceOrders = maintOrders,
            maintenanceParts = maintParts,
            inventoryMovements = movements,
            ledgerTransactions = transactions,
            cashSessions = cashSessions,
            currentDrawerBalanceMinor = currentDrawerBalance
        )
    }

    // =============================================================================================
    // MEKKA PHONE V4 — PART 2: OPERATIONAL INTELLIGENCE & MANAGEMENT CENTER
    // =============================================================================================

    suspend fun getPendingOperationsCenterBundle(
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingOperationsCenterBundle {
        val sysDao = database.systemIntegrityDao()
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()
        val coreDao = database.coreShopAuthDao()

        val syncQueue = sysDao.observeSyncQueue(shopId).first()
        val syncConflicts = sysDao.observeSyncConflicts(shopId).first()
        val pendingSms = opsDao.getAllPendingTransactions(shopId)
        val cashSessions = ledgerDao.observeCashSessions(shopId).first()
        val maintenanceOrders = opsDao.getAllMaintenanceOrders(shopId)
        val customerBalances = catalogDao.observeCustomerBalanceProjections(shopId).first()
        val backups = sysDao.observeBackups(shopId).first()
        val auditLogs = sysDao.observeAuditLogs(shopId).first()
        val inventoryItems = catalogDao.observeProductInventoryProjections(shopId).first()
        val usersMap = coreDao.observeUsers(shopId).first().associateBy { it.localId }

        return OperationalCentersEngine.buildPendingOperationsCenter(
            syncQueue = syncQueue,
            syncConflicts = syncConflicts,
            pendingSmsTransactions = pendingSms,
            cashSessions = cashSessions,
            maintenanceOrders = maintenanceOrders,
            customerBalances = customerBalances,
            backups = backups,
            auditLogs = auditLogs,
            inventoryItems = inventoryItems,
            usersMap = usersMap,
            nowMillis = nowMillis
        )
    }

    suspend fun getInventoryAnalyticsDashboardBundle(
        analysisWindowDays: Int = 30,
        configuredLeadTimeDays: Int = 3,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): InventoryAnalyticsDashboardBundle {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()

        val products = catalogDao.observeProductInventoryProjections(shopId).first()
        val categories = catalogDao.getAllCategories(shopId)
        val movements = catalogDao.getAllMovementsChronological(shopId)
        val sales = opsDao.getAllSales(shopId)
        val saleItems = opsDao.getAllSaleItems(shopId)

        return OperationalAnalyticsEngine.buildInventoryAnalyticsAndProcurement(
            products = products,
            categories = categories,
            movements = movements,
            sales = sales,
            saleItems = saleItems,
            analysisWindowDays = analysisWindowDays,
            configuredLeadTimeDays = configuredLeadTimeDays,
            nowMillis = nowMillis
        )
    }

    suspend fun getSmartNotificationCenterBundle(
        storageFreeBytes: Long = 500L * 1024L * 1024L,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartNotificationCenterBundle {
        val sysDao = database.systemIntegrityDao()
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()

        val inventoryAnalytics = getInventoryAnalyticsDashboardBundle(shopId = shopId, nowMillis = nowMillis)
        val inventoryItems = catalogDao.observeProductInventoryProjections(shopId).first()
        val cashSessions = ledgerDao.observeCashSessions(shopId).first()
        val customerBalances = catalogDao.observeCustomerBalanceProjections(shopId).first()
        val supplierBalances = catalogDao.observeSupplierBalanceProjections(shopId).first()
        val expenses = opsDao.getAllExpenses(shopId)
        val pendingSms = opsDao.getAllPendingTransactions(shopId)
        val maintenanceOrders = opsDao.getAllMaintenanceOrders(shopId)
        val syncQueue = sysDao.observeSyncQueue(shopId).first()
        val syncConflicts = sysDao.observeSyncConflicts(shopId).first()
        val backups = sysDao.observeBackups(shopId).first()
        val auditLogs = sysDao.observeAuditLogs(shopId).first()

        return OperationalCentersEngine.buildSmartNotificationCenter(
            inventoryAnalytics = inventoryAnalytics,
            inventoryItems = inventoryItems,
            cashSessions = cashSessions,
            customerBalances = customerBalances,
            supplierBalances = supplierBalances,
            expenses = expenses,
            pendingSmsTransactions = pendingSms,
            maintenanceOrders = maintenanceOrders,
            syncQueue = syncQueue,
            syncConflicts = syncConflicts,
            backups = backups,
            auditLogs = auditLogs,
            storageFreeBytes = storageFreeBytes,
            nowMillis = nowMillis
        )
    }

    suspend fun getCustomer360Snapshot(
        customerId: String,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): AppResult<Customer360Snapshot> {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val ledgerDao = database.financialLedgerDao()
        val sysDao = database.systemIntegrityDao()
        val coreDao = database.coreShopAuthDao()

        val customer = catalogDao.getCustomerById(customerId)
            ?: return AppResult.Error("العميل غير موجود في قاعدة البيانات", "CUSTOMER_NOT_FOUND")
        val derivedBal = ledgerDao.getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
            accountId = customerId
        )
        val actor = coreDao.getUserById(actorUserId)
        val roleCode = actor?.roleCode ?: "OWNER"

        val snapshot = OperationalCentersEngine.buildCustomer360Snapshot(
            customer = customer,
            derivedBalanceMinor = derivedBal,
            allSales = opsDao.getAllSales(shopId),
            allRefunds = opsDao.getAllRefunds(shopId),
            allMaintenance = opsDao.getAllMaintenanceOrders(shopId),
            allTransfers = opsDao.getAllTransfers(shopId),
            allLedgerTransactions = ledgerDao.getAllTransactionsChronological(shopId),
            allAuditLogs = sysDao.observeAuditLogs(shopId).first(),
            activeRoleCode = roleCode,
            nowMillis = nowMillis
        )
        return AppResult.Success(snapshot)
    }

    suspend fun getSupplier360Snapshot(
        supplierId: String,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): AppResult<Supplier360Snapshot> {
        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val ledgerDao = database.financialLedgerDao()

        val supplier = catalogDao.getSupplierById(supplierId)
            ?: return AppResult.Error("المورد غير موجود في قاعدة البيانات", "SUPPLIER_NOT_FOUND")
        val proj = catalogDao.observeSupplierBalanceProjections(shopId).first()
            .firstOrNull { it.supplierId == supplierId }

        val snapshot = OperationalCentersEngine.buildSupplier360Snapshot(
            supplier = supplier,
            supplierBalanceProjection = proj,
            allPurchases = opsDao.getAllPurchases(shopId),
            allPurchaseItems = opsDao.getAllPurchaseItems(shopId),
            allProducts = catalogDao.observeProductInventoryProjections(shopId).first(),
            allLedgerTransactions = ledgerDao.getAllTransactionsChronological(shopId),
            nowMillis = nowMillis
        )
        return AppResult.Success(snapshot)
    }

    suspend fun getMaintenanceManagementCenterBundle(
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): MaintenanceManagementCenterBundle {
        val opsDao = database.operationsDao()
        val ledgerDao = database.financialLedgerDao()
        val coreDao = database.coreShopAuthDao()

        return OperationalAnalyticsEngine.buildMaintenanceManagementCenter(
            orders = opsDao.getAllMaintenanceOrders(shopId),
            parts = opsDao.getAllMaintenanceParts(shopId),
            expenses = opsDao.getAllExpenses(shopId),
            ledgerTransactions = ledgerDao.getAllTransactionsChronological(shopId),
            usersMap = coreDao.observeUsers(shopId).first().associateBy { it.localId },
            nowMillis = nowMillis
        )
    }

    suspend fun getCashAndWalletControlCenterBundle(
        shopId: String = DEFAULT_SHOP_ID
    ): CashAndWalletControlCenterBundle {
        val ledgerDao = database.financialLedgerDao()
        val opsDao = database.operationsDao()
        val (startDay, endDay) = getTodayBoundsMillis()
        val (startMonth, endMonth) = getCurrentMonthBoundsMillis()

        val drawers = ledgerDao.observeCashDrawerDerivedProjections(shopId, startDay, endDay).first()
        val wallets = ledgerDao.observeWalletDerivedProjections(shopId, startDay, endDay, startMonth, endMonth).first()
        val cashSessions = ledgerDao.observeCashSessions(shopId).first()
        val allTx = ledgerDao.getAllTransactionsChronological(shopId)
        val pendingSms = opsDao.getAllPendingTransactions(shopId)
        val shiftBreakdown = getShiftCashFlowBreakdown(shopId = shopId)

        return OperationalAnalyticsEngine.buildCashAndWalletControlCenter(
            cashDrawerProjection = drawers.firstOrNull(),
            wallets = wallets,
            cashSessions = cashSessions,
            ledgerTransactions = allTx,
            pendingSmsTransactions = pendingSms,
            activeShiftBreakdown = shiftBreakdown
        )
    }

    suspend fun getOfflineControlCenterBundle(
        isNetworkOnline: Boolean = true,
        isRemoteServerConfigured: Boolean = false,
        isSyncingNow: Boolean = false,
        shopId: String = DEFAULT_SHOP_ID
    ): OfflineControlCenterBundle {
        val sysDao = database.systemIntegrityDao()
        val ledgerDao = database.financialLedgerDao()
        val catalogDao = database.catalogInventoryDao()
        val coreDao = database.coreShopAuthDao()

        return OperationalAnalyticsEngine.buildOfflineAndMultiDeviceControlCenter(
            isNetworkOnline = isNetworkOnline,
            isRemoteServerConfigured = isRemoteServerConfigured,
            isSyncingNow = isSyncingNow,
            syncQueue = sysDao.observeSyncQueue(shopId).first(),
            syncConflicts = sysDao.observeSyncConflicts(shopId).first(),
            ledgerTransactions = ledgerDao.getAllTransactionsChronological(shopId),
            inventoryMovements = catalogDao.getAllMovementsChronological(shopId),
            auditLogs = sysDao.observeAuditLogs(shopId).first(),
            usersMap = coreDao.observeUsers(shopId).first().associateBy { it.localId }
        )
    }

    suspend fun getAuditDiagnosticsDashboardBundle(
        storageUsedBytes: Long = 512L * 1024L,
        storageFreeBytes: Long = 500L * 1024L * 1024L,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): AuditDiagnosticsDashboardBundle {
        val integrity = runDataIntegrityHealthCheck(shopId)
        val sysDao = database.systemIntegrityDao()
        val opsDao = database.operationsDao()
        val coreDao = database.coreShopAuthDao()
        val roleCode = coreDao.getUserById(actorUserId)?.roleCode ?: "OWNER"

        return OperationalAnalyticsEngine.buildAuditDiagnosticsDashboard(
            integrityReport = integrity,
            migrationSchemaVersion = DatabaseMigrations.CURRENT_SCHEMA_VERSION,
            storageUsedBytes = storageUsedBytes,
            storageFreeBytes = storageFreeBytes,
            syncQueue = sysDao.observeSyncQueue(shopId).first(),
            syncConflicts = sysDao.observeSyncConflicts(shopId).first(),
            backups = sysDao.observeBackups(shopId).first(),
            pendingSmsTransactions = opsDao.getAllPendingTransactions(shopId),
            activeRoleCode = roleCode,
            nowMillis = nowMillis
        )
    }

    suspend fun generateV4TabularReport(
        filter: V4ReportFilter,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): V4TabularReportDocument {
        val coreDao = database.coreShopAuthDao()
        val opsDao = database.operationsDao()
        val catalogDao = database.catalogInventoryDao()
        val ledgerDao = database.financialLedgerDao()
        val sysDao = database.systemIntegrityDao()
        val (startDay, endDay) = getTodayBoundsMillis(nowMillis)
        val (startMonth, endMonth) = getCurrentMonthBoundsMillis(nowMillis)

        val shop = coreDao.getShopById(shopId)
        val categoriesMap = catalogDao.getAllCategories(shopId).associate { it.localId to it.nameArabic }

        return V4ReportingEngine.generateTabularReport(
            filter = filter,
            shopNameArabic = shop?.nameArabic ?: "مكة فون",
            generatedByUserId = actorUserId,
            sales = opsDao.getAllSales(shopId),
            saleItems = opsDao.getAllSaleItems(shopId),
            purchases = opsDao.getAllPurchases(shopId),
            refunds = opsDao.getAllRefunds(shopId),
            expenses = opsDao.getAllExpenses(shopId),
            transfers = opsDao.getAllTransfers(shopId),
            maintenanceOrders = opsDao.getAllMaintenanceOrders(shopId),
            maintenanceParts = opsDao.getAllMaintenanceParts(shopId),
            cashSessions = ledgerDao.observeCashSessions(shopId).first(),
            ledgerTransactions = ledgerDao.getAllTransactionsChronological(shopId),
            inventoryItems = catalogDao.observeProductInventoryProjections(shopId).first(),
            inventoryMovements = catalogDao.getAllMovementsChronological(shopId),
            customerBalances = catalogDao.observeCustomerBalanceProjections(shopId).first(),
            supplierBalances = catalogDao.observeSupplierBalanceProjections(shopId).first(),
            wallets = ledgerDao.observeWalletDerivedProjections(shopId, startDay, endDay, startMonth, endMonth).first(),
            auditLogs = sysDao.observeAuditLogs(shopId).first(),
            categoriesMap = categoriesMap,
            nowMillis = nowMillis
        )
    }

    suspend fun exportV4Report(
        filter: V4ReportFilter,
        format: ReportExportFormat,
        outputDirectory: java.io.File,
        actorUserId: String = activeUserId,
        shopId: String = DEFAULT_SHOP_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): AppResult<ReportExportResult> {
        return try {
            val doc = generateV4TabularReport(
                filter = filter,
                actorUserId = actorUserId,
                shopId = shopId,
                nowMillis = nowMillis
            )
            val exported = RealReportExportEngine.exportReportToFile(
                document = doc,
                format = format,
                outputDirectory = outputDirectory
            )
            AppResult.Success(exported)
        } catch (e: Exception) {
            AppResult.Error("تعذر تصدير التقرير (${format.extension}): ${e.message}", "REPORT_EXPORT_ERROR", e)
        }
    }

    suspend fun searchGlobalEntities(
        rawQuery: String,
        shopId: String = DEFAULT_SHOP_ID
    ): List<GlobalSearchResultItem> {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val results = mutableListOf<GlobalSearchResultItem>()

        val catalogDao = database.catalogInventoryDao()
        val opsDao = database.operationsDao()
        val ledgerDao = database.financialLedgerDao()

        // 1. Products (Product, SKU, Barcode)
        catalogDao.observeProductInventoryProjections(shopId).first().filter {
            it.nameArabic.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                (it.barcode?.lowercase()?.contains(q) == true)
        }.take(6).forEach { p ->
            results += GlobalSearchResultItem(
                categoryArabic = "صنف بالمخزن",
                title = p.nameArabic,
                subtitle = "SKU: ${p.sku} • الرصيد: ${p.derivedStockQty} • السعر: ${Money.ofMinor(p.effectiveRetailPriceMinor).formatArabic()}",
                referenceCode = p.sku,
                routeTarget = MekkaRoute.INVENTORY,
                statusArabic = p.stockStatusArabic,
                timestamp = System.currentTimeMillis(),
                dateFormatted = fmt.format(java.util.Date()),
                entityId = p.productId
            )
        }

        // 2. Serial / IMEI
        catalogDao.observeSerialNumbers(shopId).first().filter {
            it.serialOrImei.lowercase().contains(q) || (it.secondaryImei?.lowercase()?.contains(q) == true)
        }.take(6).forEach { s ->
            results += GlobalSearchResultItem(
                categoryArabic = "سيريال / IMEI",
                title = "IMEI: ${s.serialOrImei}",
                subtitle = "الحالة: ${s.status.arabicLabel} • التكلفة: ${Money.ofMinor(s.acquisitionCostMinor).formatArabic()}",
                referenceCode = s.serialOrImei,
                routeTarget = MekkaRoute.INVENTORY,
                statusArabic = s.status.arabicLabel,
                timestamp = s.updatedAt,
                dateFormatted = fmt.format(java.util.Date(s.updatedAt)),
                entityId = s.localId
            )
        }

        // 3. Customers (Customer Name, Phone)
        catalogDao.observeCustomerBalanceProjections(shopId).first().filter {
            it.fullName.lowercase().contains(q) || it.phone.lowercase().contains(q)
        }.take(6).forEach { c ->
            val st = if (c.outstandingBalanceMinor > 0L) "مديونية: ${Money.ofMinor(c.outstandingBalanceMinor).formatArabic()}" else "خالص الذمة"
            results += GlobalSearchResultItem(
                categoryArabic = "عميل",
                title = c.fullName,
                subtitle = "هاتف: ${c.phone} • $st",
                referenceCode = c.phone,
                routeTarget = MekkaRoute.CUSTOMERS,
                statusArabic = st,
                timestamp = System.currentTimeMillis(),
                dateFormatted = fmt.format(java.util.Date()),
                entityId = c.customerId
            )
        }

        // 4. Suppliers (Company Name, Contact, Phone)
        catalogDao.observeSupplierBalanceProjections(shopId).first().filter {
            it.companyName.lowercase().contains(q) || it.contactName.lowercase().contains(q) || it.phone.lowercase().contains(q)
        }.take(6).forEach { sup ->
            val st = if (sup.outstandingPayableMinor > 0L) "مستحق: ${Money.ofMinor(sup.outstandingPayableMinor).formatArabic()}" else "مسدد"
            results += GlobalSearchResultItem(
                categoryArabic = "مورد",
                title = sup.companyName,
                subtitle = "هاتف: ${sup.phone} • $st",
                referenceCode = sup.phone,
                routeTarget = MekkaRoute.SUPPLIERS,
                statusArabic = st,
                timestamp = System.currentTimeMillis(),
                dateFormatted = fmt.format(java.util.Date()),
                entityId = sup.supplierId
            )
        }

        // 5. Sales / Invoices
        opsDao.getAllSales(shopId).filter {
            it.invoiceNumber.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }.take(6).forEach { sale ->
            results += GlobalSearchResultItem(
                categoryArabic = "فاتورة بيع",
                title = "فاتورة #${sale.invoiceNumber}",
                subtitle = "الإجمالي: ${Money.ofMinor(sale.totalAmountMinor).formatArabic()} • ${fmt.format(java.util.Date(sale.saleTimestamp))}",
                referenceCode = sale.invoiceNumber,
                routeTarget = MekkaRoute.SALES_POS,
                statusArabic = sale.status,
                timestamp = sale.saleTimestamp,
                dateFormatted = fmt.format(java.util.Date(sale.saleTimestamp)),
                entityId = sale.localId
            )
        }

        // 6. Purchases
        opsDao.getAllPurchases(shopId).filter {
            it.invoiceNumber.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }.take(6).forEach { pur ->
            val st = if (pur.totalAmountMinor <= pur.paidAmountMinor) "مسددة" else "آجل"
            results += GlobalSearchResultItem(
                categoryArabic = "فاتورة شراء",
                title = "توريد #${pur.invoiceNumber}",
                subtitle = "الإجمالي: ${Money.ofMinor(pur.totalAmountMinor).formatArabic()} • ${fmt.format(java.util.Date(pur.purchaseTimestamp))}",
                referenceCode = pur.invoiceNumber,
                routeTarget = MekkaRoute.PURCHASES,
                statusArabic = st,
                timestamp = pur.purchaseTimestamp,
                dateFormatted = fmt.format(java.util.Date(pur.purchaseTimestamp)),
                entityId = pur.localId
            )
        }

        // 7. Maintenance Tickets
        opsDao.getAllMaintenanceOrders(shopId).filter {
            it.ticketNumber.lowercase().contains(q) ||
                it.customerNameSnapshot.lowercase().contains(q) ||
                it.customerPhoneSnapshot.lowercase().contains(q) ||
                it.deviceModel.lowercase().contains(q) ||
                it.deviceImeiOrSerial.lowercase().contains(q) ||
                it.deviceSerial.lowercase().contains(q)
        }.take(6).forEach { m ->
            results += GlobalSearchResultItem(
                categoryArabic = "تذكرة صيانة",
                title = "${m.ticketNumber} — ${m.deviceBrand} ${m.deviceModel}",
                subtitle = "العميل: ${m.customerNameSnapshot} (${m.customerPhoneSnapshot}) • ${m.status.arabicLabel}",
                referenceCode = m.ticketNumber,
                routeTarget = MekkaRoute.MAINTENANCE,
                statusArabic = m.status.arabicLabel,
                timestamp = m.receivedAt,
                dateFormatted = fmt.format(java.util.Date(m.receivedAt)),
                entityId = m.localId
            )
        }

        // 8. Transactions & Reference Numbers
        ledgerDao.getAllTransactionsChronological(shopId).filter {
            it.referenceNumber.lowercase().contains(q) || it.descriptionArabic.lowercase().contains(q)
        }.take(6).forEach { tx ->
            results += GlobalSearchResultItem(
                categoryArabic = "قيد مالي / مرجع",
                title = "قيد #${tx.referenceNumber} (${tx.transactionType.arabicLabel})",
                subtitle = "${Money.ofMinor(tx.amountMinor).formatArabic()} — ${tx.descriptionArabic}",
                referenceCode = tx.referenceNumber,
                routeTarget = MekkaRoute.LEDGER,
                statusArabic = tx.status.name,
                timestamp = tx.transactionTimestamp,
                dateFormatted = fmt.format(java.util.Date(tx.transactionTimestamp)),
                entityId = tx.localId
            )
        }

        return results
    }
}

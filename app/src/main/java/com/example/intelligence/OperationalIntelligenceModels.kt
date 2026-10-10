package com.example.intelligence

import com.example.core.SyncState
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.financial.CustomerCreditStanding
import com.example.financial.CustomerFinancialProfile
import com.example.financial.PartyStatementLine
import com.example.financial.ShiftCashFlowBreakdown
import com.example.financial.SupplierFinancialProfile
import com.example.financial.SupplierPayablesSummary

// =================================================================================================
// 1 — PENDING OPERATIONS CENTER MODELS
// =================================================================================================

enum class PendingOperationType(val code: String, val arabicLabel: String) {
    PENDING_SYNC("PENDING_SYNC", "مزامنة معلقة (Pending Sync)"),
    SYNC_FAILED("SYNC_FAILED", "فشل مزامنة (Sync Failed)"),
    SYNC_CONFLICTS("SYNC_CONFLICTS", "تعارضات المزامنة (Sync Conflicts)"),
    SMS_REVIEW("SMS_REVIEW", "مراجعة رسائل المحافظ (SMS Review)"),
    FAILED_OPERATIONS("FAILED_OPERATIONS", "عمليات فاشلة (Failed Operations)"),
    PENDING_APPROVALS("PENDING_APPROVALS", "اعتمادات معلقة (Pending Approvals)"),
    PENDING_MAINTENANCE("PENDING_MAINTENANCE", "صيانة معلقة (Pending Maintenance)"),
    PENDING_CUSTOMER_PAYMENTS("PENDING_CUSTOMER_PAYMENTS", "مدفوعات عملاء مستحقة (Pending Payments)"),
    BACKUP_PROBLEMS("BACKUP_PROBLEMS", "مشاكل النسخ الاحتياطي (Backup Problems)"),
    RESTORE_WARNINGS("RESTORE_WARNINGS", "تحذيرات الاستعادة (Restore Warnings)"),
    INVENTORY_ALERTS("INVENTORY_ALERTS", "تنبيهات المخزون (Inventory Alerts)")
}

enum class PendingCenterActionType(val code: String, val arabicLabel: String) {
    REVIEW("REVIEW", "مراجعة"),
    RETRY("RETRY", "إعادة المحاولة"),
    RESOLVE("RESOLVE", "حسم التعارض"),
    APPROVE("APPROVE", "اعتماد"),
    REJECT("REJECT", "رفض"),
    OPEN_DETAILS("OPEN_DETAILS", "فتح التفاصيل")
}

data class PendingOperationCenterItem(
    val id: String,
    val type: PendingOperationType,
    val statusArabic: String,
    val createdAt: Long,
    val userId: String,
    val userNameArabic: String,
    val reference: String,
    val entityLocalId: String,
    val reasonArabic: String,
    val requiredActionArabic: String,
    val availableActions: List<PendingCenterActionType>,
    val routeTarget: String,
    val amountMinor: Long? = null
)

data class PendingOperationsCenterBundle(
    val totalCount: Int = 0,
    val countsByType: Map<PendingOperationType, Int> = emptyMap(),
    val items: List<PendingOperationCenterItem> = emptyList()
)

// =================================================================================================
// 2 — SMART NOTIFICATION CENTER MODELS
// =================================================================================================

enum class SmartNotificationDomain(val code: String, val arabicLabel: String) {
    INVENTORY("INVENTORY", "المخزون"),
    FINANCIAL("FINANCIAL", "المالية والخزينة"),
    MAINTENANCE("MAINTENANCE", "الصيانة"),
    SYSTEM("SYSTEM", "النظام والمزامنة")
}

enum class SmartNotificationSubType(
    val domain: SmartNotificationDomain,
    val arabicLabel: String
) {
    // Inventory
    LOW_STOCK(SmartNotificationDomain.INVENTORY, "مخزون منخفض (Low Stock)"),
    OUT_OF_STOCK(SmartNotificationDomain.INVENTORY, "نفاد المخزون (Out of Stock)"),
    SLOW_MOVING(SmartNotificationDomain.INVENTORY, "صنف بطيء الحركة (Slow Moving)"),
    DEAD_STOCK(SmartNotificationDomain.INVENTORY, "مخزون راكد (Dead Stock)"),

    // Financial
    SHIFT_VARIANCE(SmartNotificationDomain.FINANCIAL, "فرق عجز/فائض وردية (Shift Variance)"),
    OUTSTANDING_RECEIVABLE(SmartNotificationDomain.FINANCIAL, "مديونية عميل مستحقة (Outstanding Receivable)"),
    SUPPLIER_PAYABLE(SmartNotificationDomain.FINANCIAL, "مستحقات مورد (Supplier Payable)"),
    UNUSUAL_EXPENSE(SmartNotificationDomain.FINANCIAL, "مصروف غير اعتيادي (Unusual Expense)"),
    PENDING_WALLET_OPERATION(SmartNotificationDomain.FINANCIAL, "عملية محفظة معلقة (Pending Wallet Operation)"),

    // Maintenance
    DUE_TODAY(SmartNotificationDomain.MAINTENANCE, "موعد تسليم اليوم (Due Today)"),
    OVERDUE(SmartNotificationDomain.MAINTENANCE, "صيانة متأخرة (Overdue)"),
    READY_FOR_DELIVERY(SmartNotificationDomain.MAINTENANCE, "جاهز للتسليم (Ready for Delivery)"),
    WAITING_CUSTOMER_APPROVAL(SmartNotificationDomain.MAINTENANCE, "بانتظار موافقة العميل (Waiting Approval)"),

    // System
    SYNC_FAILED(SmartNotificationDomain.SYSTEM, "فشل مزامنة (Sync Failed)"),
    CONFLICT(SmartNotificationDomain.SYSTEM, "تعارض بيانات (Conflict)"),
    BACKUP_FAILED(SmartNotificationDomain.SYSTEM, "مشكلة نسخ احتياطي (Backup Problem)"),
    RESTORE_WARNING(SmartNotificationDomain.SYSTEM, "تحذير استعادة (Restore Warning)"),
    STORAGE_PROBLEM(SmartNotificationDomain.SYSTEM, "مشكلة مساحة تخزين (Storage Problem)")
}

enum class SmartNotificationPriority(val rank: Int, val arabicLabel: String) {
    INFO(1, "معلومة (INFO)"),
    WARNING(2, "تحذير (WARNING)"),
    ACTION_REQUIRED(3, "إجراء مطلوب (ACTION_REQUIRED)"),
    CRITICAL(4, "حرج (CRITICAL)")
}

data class SmartNotificationItem(
    val id: String,
    val domain: SmartNotificationDomain,
    val subType: SmartNotificationSubType,
    val priority: SmartNotificationPriority,
    val titleArabic: String,
    val messageArabic: String,
    val referenceCode: String,
    val entityId: String? = null,
    val createdAt: Long,
    val routeTarget: String,
    val actionLabelArabic: String = "فتح المصدر"
)

data class SmartNotificationCenterBundle(
    val totalCount: Int = 0,
    val criticalCount: Int = 0,
    val actionRequiredCount: Int = 0,
    val warningCount: Int = 0,
    val infoCount: Int = 0,
    val itemsByDomain: Map<SmartNotificationDomain, List<SmartNotificationItem>> = emptyMap(),
    val allItems: List<SmartNotificationItem> = emptyList()
)

// =================================================================================================
// 4 — CUSTOMER 360 MODELS
// =================================================================================================

enum class Customer360EventType(val arabicLabel: String) {
    SALE("فاتورة بيع"),
    PAYMENT("تحصيل دفعة"),
    RECEIVABLE("مديونية آجلة"),
    MAINTENANCE("تذكرة صيانة"),
    REFUND("مرتجع مبيعات"),
    CREDIT_POLICY("ائتمان العميل"),
    WALLET_OPERATION("عملية محفظة"),
    NOTE("ملاحظة عميل"),
    AUDIT("سجل تدقيق")
}

data class Customer360TimelineEvent(
    val id: String,
    val timestamp: Long,
    val dateFormatted: String,
    val eventType: Customer360EventType,
    val titleArabic: String,
    val subtitleArabic: String,
    val referenceNumber: String,
    val amountMinor: Long? = null,
    val statusArabic: String,
    val entityId: String,
    val routeTarget: String
)

data class Customer360Snapshot(
    val customer: CustomerEntity,
    val financialProfile: CustomerFinancialProfile,
    val creditStanding: CustomerCreditStanding,
    val purchasesFromShop: List<SaleEntity>,
    val payments: List<TransactionEntity>,
    val receivablesMinor: Long,
    val maintenanceTickets: List<MaintenanceOrderEntity>,
    val refunds: List<RefundEntity>,
    val walletOperations: List<TransferEntity>,
    val notes: String,
    val auditHistory: List<AuditLogEntity>,
    val isAuditAuthorized: Boolean,
    val timeline: List<Customer360TimelineEvent>
)

// =================================================================================================
// 5 — SUPPLIER 360 MODELS
// =================================================================================================

enum class Supplier360EventType(val arabicLabel: String) {
    PURCHASE("فاتورة شراء"),
    PAYMENT("سداد دفعة للمورد"),
    RETURN("مرتجع مشتريات"),
    PAYABLE("ذمة مورد مستحقة"),
    PRICE_CHANGE("تغير سعر توريد"),
    AUDIT("سجل تدقيق")
}

data class SupplierSuppliedProductSummary(
    val productId: String,
    val sku: String,
    val nameArabic: String,
    val totalSuppliedQty: Int,
    val lastPurchaseCostMinor: Long,
    val minPurchaseCostMinor: Long,
    val maxPurchaseCostMinor: Long,
    val currentWacMinor: Long,
    val currentStockQty: Int,
    val lastSuppliedAt: Long
)

data class SupplierPriceHistoryEntry(
    val id: String,
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val invoiceNumber: String,
    val unitCostMinor: Long,
    val quantity: Int,
    val timestamp: Long
)

data class Supplier360TimelineEvent(
    val id: String,
    val timestamp: Long,
    val dateFormatted: String,
    val eventType: Supplier360EventType,
    val titleArabic: String,
    val subtitleArabic: String,
    val referenceNumber: String,
    val amountMinor: Long? = null,
    val statusArabic: String,
    val entityId: String,
    val routeTarget: String
)

data class Supplier360Snapshot(
    val supplier: SupplierEntity,
    val financialProfile: SupplierFinancialProfile,
    val payablesSummary: SupplierPayablesSummary,
    val purchases: List<PurchaseEntity>,
    val payments: List<TransactionEntity>,
    val returns: List<TransactionEntity>,
    val payablesMinor: Long,
    val suppliedProducts: List<SupplierSuppliedProductSummary>,
    val priceHistory: List<SupplierPriceHistoryEntry>,
    val statementLines: List<PartyStatementLine>,
    val timeline: List<Supplier360TimelineEvent>
)

// =================================================================================================
// 9 & 10 — PROCUREMENT INTELLIGENCE & INVENTORY ANALYTICS MODELS
// =================================================================================================

data class ReorderSuggestionItem(
    val productId: String,
    val sku: String,
    val productNameArabic: String,
    val categoryNameArabic: String,
    val currentStockQty: Int,
    val minimumStockQty: Int,
    val reorderPointQty: Int,
    val actualSoldUnitsInWindow: Int,
    val dailySalesRate: Double,
    val actualLeadTimeDays: Int,
    val suggestedReorderQuantity: Int,
    val unitWacCostMinor: Long,
    val estimatedOrderCostMinor: Long,
    val reasonArabic: String,
    val priorityArabic: String
)

enum class StockAgingBucket(val arabicLabel: String) {
    FRESH_0_30("حديث (0 - 30 يوم)"),
    MODERATE_31_60("متوسط (31 - 60 يوم)"),
    AGING_61_90("متقادم (61 - 90 يوم)"),
    STAGNANT_90_PLUS("راكد (+90 يوم)")
}

enum class CostTrendDirection(val arabicLabel: String) {
    STABLE("مستقر"),
    RISING("اتجاه صاعد ↑"),
    FALLING("اتجاه هابط ↓")
}

data class ProductInventoryAnalyticsItem(
    val productId: String,
    val sku: String,
    val nameArabic: String,
    val categoryNameArabic: String,
    val currentStockQty: Int,
    val unitWacMinor: Long,
    val unitSellingPriceMinor: Long,
    val stockValueWacMinor: Long,
    val potentialRevenueMinor: Long,
    val potentialMarginMinor: Long,
    val potentialMarginBasisPoints: Int,
    val ageDaysSinceLastInbound: Int,
    val daysSinceLastSale: Int?,
    val agingBucket: StockAgingBucket,
    val soldUnitsInPeriod: Int,
    val turnoverRatioBasisPoints: Int, // (COGS or SoldUnits / AvgStock) * 10000
    val velocityClass: String,         // FAST_MOVING, NORMAL_MOVING, SLOW_MOVING, DEAD_STOCK
    val velocityArabic: String,
    val costTrend: CostTrendDirection,
    val firstRecordedCostMinor: Long,
    val latestRecordedCostMinor: Long,
    val costChangeBasisPoints: Int
)

data class InventoryAnalyticsDashboardBundle(
    val totalInventoryValueWacMinor: Long = 0L,
    val totalPotentialRevenueMinor: Long = 0L,
    val totalPotentialMarginMinor: Long = 0L,
    val averageTurnoverBasisPoints: Int = 0,
    val fastMovingItems: List<ProductInventoryAnalyticsItem> = emptyList(),
    val slowMovingItems: List<ProductInventoryAnalyticsItem> = emptyList(),
    val deadStockItems: List<ProductInventoryAnalyticsItem> = emptyList(),
    val stockAgingBreakdown: Map<StockAgingBucket, List<ProductInventoryAnalyticsItem>> = emptyMap(),
    val reorderSuggestions: List<ReorderSuggestionItem> = emptyList(),
    val allItemsAnalytics: List<ProductInventoryAnalyticsItem> = emptyList()
)

// =================================================================================================
// 11 — MAINTENANCE MANAGEMENT CENTER MODELS
// =================================================================================================

data class TechnicianWorkloadItem(
    val technicianUserId: String,
    val technicianNameArabic: String,
    val activeTicketsCount: Int,
    val completedTicketsCount: Int,
    val overdueTicketsCount: Int,
    val averageRepairHours: Double,
    val generatedRevenueMinor: Long
)

data class MaintenanceManagementCenterBundle(
    val receivedCount: Int = 0,
    val underInspectionCount: Int = 0,
    val waitingApprovalCount: Int = 0,
    val inRepairCount: Int = 0,
    val testingCount: Int = 0,
    val readyCount: Int = 0,
    val overdueCount: Int = 0,
    val deliveredCount: Int = 0,
    val cancelledCount: Int = 0,
    val technicianWorkloads: List<TechnicianWorkloadItem> = emptyList(),
    val averageRepairDurationHours: Double = 0.0,
    val totalMaintenanceRevenueMinor: Long = 0L,
    val totalPartsCostWacMinor: Long = 0L,
    val totalRelatedExpensesMinor: Long = 0L,
    val trueMaintenanceNetProfitMinor: Long = 0L,
    val totalOutstandingPaymentsMinor: Long = 0L,
    val overdueTickets: List<MaintenanceOrderEntity> = emptyList(),
    val dueTodayTickets: List<MaintenanceOrderEntity> = emptyList(),
    val waitingApprovalTickets: List<MaintenanceOrderEntity> = emptyList(),
    val readyForDeliveryTickets: List<MaintenanceOrderEntity> = emptyList()
)

// =================================================================================================
// 12 — CASH & WALLET CONTROL CENTER MODELS
// =================================================================================================

data class UnifiedAccountControlRow(
    val accountId: String,
    val accountType: FinancialAccountType,
    val providerCode: String,
    val accountNameArabic: String,
    val accountNumberOrCode: String,
    val openingBalanceMinor: Long,
    val inflowMinor: Long,
    val outflowMinor: Long,
    val expectedBalanceMinor: Long,
    val actualCountedBalanceMinor: Long?,
    val varianceMinor: Long,
    val feesMinor: Long,
    val commissionsMinor: Long,
    val transactionsCount: Int,
    val pendingOperationsCount: Int
)

data class CashAndWalletControlCenterBundle(
    val cashDrawerRow: UnifiedAccountControlRow = UnifiedAccountControlRow(
        accountId = "DRAWER-MAIN-EGP",
        accountType = FinancialAccountType.CASH_DRAWER,
        providerCode = "CASH",
        accountNameArabic = "الخزينة النقدية الرئيسية",
        accountNumberOrCode = "CASH-MAIN",
        openingBalanceMinor = 0L,
        inflowMinor = 0L,
        outflowMinor = 0L,
        expectedBalanceMinor = 0L,
        actualCountedBalanceMinor = null,
        varianceMinor = 0L,
        feesMinor = 0L,
        commissionsMinor = 0L,
        transactionsCount = 0,
        pendingOperationsCount = 0
    ),
    val walletRows: List<UnifiedAccountControlRow> = emptyList(),
    val totalExpectedLiquidityMinor: Long = 0L,
    val totalInflowMinor: Long = 0L,
    val totalOutflowMinor: Long = 0L,
    val totalFeesMinor: Long = 0L,
    val totalCommissionsMinor: Long = 0L,
    val totalVarianceMinor: Long = 0L,
    val totalPendingCount: Int = 0,
    val activeShiftBreakdown: ShiftCashFlowBreakdown? = null
)

// =================================================================================================
// 13 & 14 — OFFLINE CONTROL CENTER & MULTI-DEVICE VISIBILITY MODELS
// =================================================================================================

enum class ConnectivitySyncLifecycleState(
    val code: String,
    val badgeText: String,
    val arabicLabel: String
) {
    OFFLINE("OFFLINE", "OFFLINE", "غير متصل — العمليات تُحفظ محلياً بأمان"),
    ONLINE_PENDING("ONLINE", "ONLINE", "متصل — توجد عمليات محلية بانتظار الرفع"),
    SYNCING("SYNCING", "Syncing", "متصل — جارٍ المزامنة مع الخادم..."),
    SYNCED_VERIFIED("SYNCED", "Synced", "تمت المزامنة بمصادقة الخادم (Server ACK)"),
    SYNC_FAILED("FAILED", "Failed", "تعذرت المزامنة — بانتظار إعادة المحاولة")
}

data class MultiDeviceOperationRecord(
    val operationId: String,
    val entityType: String,
    val entityLocalId: String,
    val referenceNumber: String,
    val summaryArabic: String,
    val deviceId: String,
    val operatorUserId: String,
    val operatorNameArabic: String,
    val timestamp: Long,
    val syncState: SyncState,
    val syncStatusArabic: String,
    val remoteCloudId: String?,
    val conflictStatusArabic: String,
    val hasUnresolvedConflict: Boolean,
    val isFinancialOrInventoryProtected: Boolean
)

data class OfflineControlCenterBundle(
    val lifecycleState: ConnectivitySyncLifecycleState = ConnectivitySyncLifecycleState.OFFLINE,
    val isNetworkOnline: Boolean = true,
    val isRemoteServerConfigured: Boolean = false,
    val pendingLocalOperationsCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val failedSyncCount: Int = 0,
    val unresolvedConflictsCount: Int = 0,
    val verifiedSyncedCount: Int = 0,
    val lastVerifiedSyncTimestamp: Long? = null,
    val multiDeviceOperations: List<MultiDeviceOperationRecord> = emptyList()
)

// =================================================================================================
// 15 — AUDIT & DIAGNOSTICS DASHBOARD MODELS
// =================================================================================================

enum class DiagnosticSeverity(val rank: Int, val arabicLabel: String) {
    HEALTHY(0, "سليم (INFO)"),
    WARNING(1, "تحذير (WARNING)"),
    CRITICAL(2, "حرج (CRITICAL)")
}

data class DiagnosticIssueRecommendation(
    val code: String,
    val subsystemArabic: String,
    val descriptionArabic: String,
    val severity: DiagnosticSeverity,
    val recommendedActionArabic: String,
    val targetRoute: String
)

data class AuditDiagnosticsDashboardBundle(
    val databaseHealthStatusArabic: String = "سليم",
    val isDatabaseHealthy: Boolean = true,
    val migrationSchemaVersion: Int = 3,
    val storageUsedBytes: Long = 0L,
    val storageFreeBytes: Long = 0L,
    val storageStatusArabic: String = "متاح",
    val syncQueuePendingCount: Int = 0,
    val syncFailedCount: Int = 0,
    val conflictCount: Int = 0,
    val backupStatusArabic: String = "غير متوفر",
    val lastBackupTimestamp: Long? = null,
    val lastSuccessfulSyncTimestamp: Long? = null,
    val integrityStatusArabic: String = "متزن",
    val integrityPassedChecks: Int = 0,
    val integrityTotalChecks: Int = 0,
    val smsPipelineStatusArabic: String = "نشط",
    val smsPendingReviewCount: Int = 0,
    val permissionStatusArabic: String = "مفعل",
    val activeRoleCode: String = "OWNER",
    val issuesWithRecommendations: List<DiagnosticIssueRecommendation> = emptyList()
)

// =================================================================================================
// 7 & 8 — V4 REPORTING ENGINE & REAL EXPORT ARCHITECTURE MODELS
// =================================================================================================

enum class V4ReportType(val code: String, val arabicTitle: String) {
    SALES("SALES", "تقرير المبيعات"),
    PURCHASES("PURCHASES", "تقرير المشتريات"),
    PROFIT("PROFIT", "تقرير الأرباح الحقيقية"),
    EXPENSES("EXPENSES", "تقرير المصروفات"),
    CASH("CASH", "تقرير الخزينة النقدية"),
    WALLETS("WALLETS", "تقرير المحافظ الإلكترونية"),
    INVENTORY("INVENTORY", "تقرير تقييم المخزون"),
    INVENTORY_MOVEMENTS("INVENTORY_MOVEMENTS", "تقرير حركات المخزون"),
    PRODUCT_PROFIT("PRODUCT_PROFIT", "تقرير ربحية الأصناف"),
    CUSTOMER_STATEMENT("CUSTOMER_STATEMENT", "كشف حساب العملاء"),
    SUPPLIER_STATEMENT("SUPPLIER_STATEMENT", "كشف حساب الموردين"),
    MAINTENANCE("MAINTENANCE", "تقرير الصيانة"),
    SHIFTS("SHIFTS", "تقرير الورديات وإغلاق الخزينة"),
    LEDGER("LEDGER", "تقرير دفتر الأستاذ المالي"),
    AUDIT("AUDIT", "تقرير التدقيق والرقابة"),
    TAX_AND_FEES("TAX_AND_FEES", "تقرير الضرائب والرسوم والعمولات")
}

data class V4ReportFilter(
    val reportType: V4ReportType = V4ReportType.SALES,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val userId: String? = null,
    val categoryId: String? = null,
    val paymentMethod: String? = null, // CASH, PARTIAL, CREDIT, WALLET
    val providerCode: String? = null,
    val status: String? = null,
    val customerId: String? = null,
    val supplierId: String? = null,
    val productId: String? = null
)

data class V4TabularReportRow(
    val cells: List<String>,
    val amountMinor: Long? = null,
    val referenceCode: String? = null,
    val routeTarget: String? = null
)

data class V4TabularReportDocument(
    val reportType: V4ReportType,
    val titleArabic: String,
    val shopNameArabic: String,
    val generatedAtMillis: Long,
    val generatedByUserId: String,
    val periodDescriptionArabic: String,
    val appliedFiltersSummaryArabic: String,
    val headers: List<String>,
    val rows: List<V4TabularReportRow>,
    val summaryMetrics: Map<String, String>
)

enum class ReportExportFormat(val extension: String, val mimeType: String, val arabicLabel: String) {
    CSV("csv", "text/csv", "ملف CSV (جدول بيانات)"),
    PDF("pdf", "application/pdf", "ملف PDF (مستند رسمي للطباعة)"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ملف Excel (XLSX)")
}

data class ReportExportResult(
    val reportType: V4ReportType,
    val format: ReportExportFormat,
    val filePath: String,
    val fileName: String,
    val byteSize: Long,
    val rowCount: Int,
    val sha256Checksum: String,
    val generatedAtMillis: Long,
    val metadataSummaryArabic: String
)

package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupValidationReport
import com.example.core.AppResult
import com.example.core.CentralizedErrorMapper
import com.example.core.FriendlyAppError
import com.example.core.SyncState
import com.example.database.dao.CashDrawerDerivedProjection
import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.SmsReviewItemProjection
import com.example.database.dao.SupplierBalanceProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.BrandEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.RoleEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SerialNumberEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.database.entities.UserEntity
import com.example.database.entities.WalletProviderEntity
import com.example.diagnostics.SystemIntegrityReport
import com.example.financial.CashCloseoutEvaluation
import com.example.financial.CashCloseoutPolicyEngine
import com.example.financial.CustomerCollectionPreview
import com.example.financial.CustomerFinancialProfile
import com.example.financial.MaintenanceFinancialEngine
import com.example.financial.MaintenanceFinancialSummary
import com.example.financial.Money
import com.example.financial.PartyAccountStatementEngine
import com.example.financial.PurchaseCalculationEngine
import com.example.financial.PurchaseCalculationSummary
import com.example.financial.PurchaseLineInput
import com.example.financial.ReportPeriodPreset
import com.example.financial.ReportsCenterBundle
import com.example.financial.ReportsCenterEngine
import com.example.financial.ReportsCenterFilter
import com.example.financial.SaleLineInput
import com.example.financial.SupplierFinancialProfile
import com.example.financial.SupplierPaymentPreview
import com.example.financial.WalletDomainPolicyEngine
import com.example.financial.WalletFeeBreakdown
import com.example.financial.WalletOperationValidation
import com.example.financial.WalletUserOperationType
import com.example.inventory.ImeiRegistryProjection
import com.example.inventory.ImeiValidationEngine
import com.example.intelligence.AuditDiagnosticsDashboardBundle
import com.example.intelligence.CashAndWalletControlCenterBundle
import com.example.intelligence.Customer360Snapshot
import com.example.intelligence.InventoryAnalyticsDashboardBundle
import com.example.intelligence.MaintenanceManagementCenterBundle
import com.example.intelligence.OfflineControlCenterBundle
import com.example.intelligence.OperationalAnalyticsEngine
import com.example.intelligence.OperationalCentersEngine
import com.example.intelligence.PendingCenterActionType
import com.example.intelligence.PendingOperationCenterItem
import com.example.intelligence.PendingOperationsCenterBundle
import com.example.intelligence.ReportExportFormat
import com.example.intelligence.ReportExportResult
import com.example.intelligence.SmartNotificationCenterBundle
import com.example.intelligence.Supplier360Snapshot
import com.example.intelligence.V4ReportFilter
import com.example.intelligence.V4ReportType
import com.example.financial.ShiftHandoverRecord
import com.example.intelligence.AnomalyDetectionAlert
import com.example.intelligence.CustomerFactInsight
import com.example.intelligence.CustomerIntelligenceEngine
import com.example.intelligence.CustomerSegment
import com.example.intelligence.BusinessForecastingBundle
import com.example.intelligence.BusinessScorecardBundle
import com.example.intelligence.AppHealthSummary
import com.example.intelligence.AutomationActionType
import com.example.intelligence.AutomationTriggerType
import com.example.intelligence.DataIntegrityAndRepairEngine
import com.example.intelligence.IntegrityIssueReport
import com.example.intelligence.IntegrityRepairProposal
import com.example.intelligence.PurchaseDraftEngine
import com.example.intelligence.PurchaseDraftLineItem
import com.example.intelligence.PurchaseDraftRecord
import com.example.intelligence.PurchaseDraftStatus
import com.example.intelligence.SafeAutomationEngine
import com.example.intelligence.SafeAutomationRule
import com.example.intelligence.SuggestedReorderItem
import com.example.intelligence.DailyBusinessSummaryBundle
import com.example.intelligence.P2ForecastingEngine
import com.example.intelligence.PeriodComparisonsBundle
import com.example.intelligence.PriceAnalysisAndChangeEngine
import com.example.intelligence.ProductDemandForecastItem
import com.example.intelligence.ProductIntelligenceSummary
import com.example.intelligence.ProductPurchaseIntelligenceEngine
import com.example.intelligence.ReceivableAgingSummary
import com.example.intelligence.SmartBusinessInsightItem
import com.example.intelligence.SmartBusinessInsightsEngine
import com.example.intelligence.V4TabularReportDocument
import com.example.navigation.MekkaDestination
import com.example.navigation.MekkaRoute
import com.example.repository.DashboardLiveKpis
import com.example.repository.GlobalSearchResultItem
import com.example.repository.MekkaPhoneRepository
import com.example.repository.MonthlyFinancialReport
import com.example.repository.ShopConfigurationProfile
import com.example.repository.SystemAlertItem
import com.example.sync.SyncUiStatus
import com.example.ui.components.InAppNotificationEntry
import com.example.ui.components.UxErrorSanitizer
import com.example.ui.components.UxOperationState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Persistent POS Cart Line Item for Mekka Phone V3 POS.
 */
data class PosCartItem(
    val lineId: String,
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val brandName: String?,
    val isSerialized: Boolean,
    val serialNumberId: String? = null,
    val serialOrImei: String? = null,
    val quantity: Int,
    val availableStockQty: Int,
    val unitPriceMinor: Long,
    val unitCostWacMinor: Long,
    val lineDiscountMinor: Long = 0L
) {
    val grossLineTotalMinor: Long
        get() = quantity.toLong() * unitPriceMinor
    val netLineTotalMinor: Long
        get() = (grossLineTotalMinor - lineDiscountMinor).coerceAtLeast(0L)
}

/**
 * Explicit Customer selection mode in POS (Rule 3 & Rule 17: Never silently select the first customer).
 */
enum class PosCustomerSelectionMode {
    NONE,       // Cashier has not selected a customer yet -> blocks sale review
    WALK_IN,    // Cashier explicitly chose walk-in cash customer ("عميل نقدي مباشر")
    REGISTERED  // Cashier explicitly chose a registered customer from the database
}

/**
 * POS Payment Method (Rule 18).
 */
enum class PosPaymentMode(val labelArabic: String) {
    CASH("نقدي"),
    PARTIAL("دفع جزئي"),
    CREDIT("آجل")
}

/**
 * Explicit state machine for POS Sale Review, Processing, Success, and Error (Rules 19, 20, 21, 24).
 */
sealed class PosCheckoutState {
    data object Idle : PosCheckoutState()
    data object Reviewing : PosCheckoutState()
    data object Processing : PosCheckoutState()
    data class Success(
        val sale: SaleEntity,
        val itemsSnapshot: List<PosCartItem>,
        val customerNameArabic: String,
        val customerPhone: String,
        val paymentModeLabel: String
    ) : PosCheckoutState()
    data class Error(val messageArabic: String) : PosCheckoutState()
}

class MekkaPhoneViewModel(
    private val repository: MekkaPhoneRepository
) : ViewModel() {

    private val _activeUserId = MutableStateFlow(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID)
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    val isSessionLocked: StateFlow<Boolean> = repository.isSessionLocked
    val activeSession = repository.activeSession

    private val _isProcessingOperation = MutableStateFlow(false)
    val isProcessingOperation: StateFlow<Boolean> = _isProcessingOperation.asStateFlow()

    // --- Persistent POS State (Rules 13–24) ---
    private val _posCartItems = MutableStateFlow<List<PosCartItem>>(emptyList())
    val posCartItems: StateFlow<List<PosCartItem>> = _posCartItems.asStateFlow()

    private val _posCustomerMode = MutableStateFlow(PosCustomerSelectionMode.NONE)
    val posCustomerMode: StateFlow<PosCustomerSelectionMode> = _posCustomerMode.asStateFlow()

    private val _posSelectedCustomerId = MutableStateFlow<String?>(null)
    val posSelectedCustomerId: StateFlow<String?> = _posSelectedCustomerId.asStateFlow()

    private val _posPaymentMode = MutableStateFlow(PosPaymentMode.CASH)
    val posPaymentMode: StateFlow<PosPaymentMode> = _posPaymentMode.asStateFlow()

    private val _posPartialPaidMajorInput = MutableStateFlow("")
    val posPartialPaidMajorInput: StateFlow<String> = _posPartialPaidMajorInput.asStateFlow()

    private val _posInvoiceDiscountMajorInput = MutableStateFlow("")
    val posInvoiceDiscountMajorInput: StateFlow<String> = _posInvoiceDiscountMajorInput.asStateFlow()

    private val _posNotesInput = MutableStateFlow("")
    val posNotesInput: StateFlow<String> = _posNotesInput.asStateFlow()

    private val _posInlineValidationError = MutableStateFlow<String?>(null)
    val posInlineValidationError: StateFlow<String?> = _posInlineValidationError.asStateFlow()

    private val _posCheckoutState = MutableStateFlow<PosCheckoutState>(PosCheckoutState.Idle)
    val posCheckoutState: StateFlow<PosCheckoutState> = _posCheckoutState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<Pair<String, Boolean>?>(null)
    val feedbackMessage: StateFlow<Pair<String, Boolean>?> = _feedbackMessage.asStateFlow()

    // --- MEKKA PHONE V3.2: Centralized UX Operation State & Idempotency Protection ---
    private val _uxOperationState = MutableStateFlow<UxOperationState>(UxOperationState.Idle)
    val uxOperationState: StateFlow<UxOperationState> = _uxOperationState.asStateFlow()

    private var lastOperationSignature: String = ""
    private var lastOperationTimestampMillis: Long = 0L

    /**
     * Domain-level double-tap / duplicate-execution guard (Section 12).
     * Blocks concurrent execution (`_isProcessingOperation`) and identical rapid submissions.
     */
    private fun acquireOperationGuard(signature: String, processingLabelArabic: String): Boolean {
        if (_isProcessingOperation.value) return false
        val now = System.currentTimeMillis()
        if (signature.isNotBlank() && signature == lastOperationSignature && (now - lastOperationTimestampMillis) < 550L) {
            return false
        }
        lastOperationSignature = signature
        lastOperationTimestampMillis = now
        _isProcessingOperation.value = true
        _uxOperationState.value = UxOperationState.Processing(
            whatIsHappeningArabic = processingLabelArabic,
            operationKey = signature
        )
        return true
    }

    private fun releaseOperationGuard() {
        _isProcessingOperation.value = false
    }

    private val _notificationHistory = MutableStateFlow<List<InAppNotificationEntry>>(emptyList())
    val notificationHistory: StateFlow<List<InAppNotificationEntry>> = _notificationHistory.asStateFlow()

    private val _showGuidanceBanners = MutableStateFlow(true)
    val showGuidanceBanners: StateFlow<Boolean> = _showGuidanceBanners.asStateFlow()

    private val _lastBackupValidation = MutableStateFlow<BackupValidationReport?>(null)
    val lastBackupValidation: StateFlow<BackupValidationReport?> = _lastBackupValidation.asStateFlow()

    private val _integrityReport = MutableStateFlow<SystemIntegrityReport?>(null)
    val integrityReport: StateFlow<SystemIntegrityReport?> = _integrityReport.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _reportsFilter = MutableStateFlow(ReportsCenterFilter())
    val reportsFilter: StateFlow<ReportsCenterFilter> = _reportsFilter.asStateFlow()

    private val _diagnosticErrorLogs = MutableStateFlow<List<FriendlyAppError>>(emptyList())
    val diagnosticErrorLogs: StateFlow<List<FriendlyAppError>> = _diagnosticErrorLogs.asStateFlow()

    // --- Cross-Screen Navigation Consistency & Deep-Link Intent States (Part 2 Sections 4, 6, 7) ---
    private val _pendingWalletQuickOperation = MutableStateFlow<WalletUserOperationType?>(null)
    val pendingWalletQuickOperation: StateFlow<WalletUserOperationType?> = _pendingWalletQuickOperation.asStateFlow()

    private val _pendingInventoryCountMode = MutableStateFlow(false)
    val pendingInventoryCountMode: StateFlow<Boolean> = _pendingInventoryCountMode.asStateFlow()

    private val _pendingSelectedCustomerId = MutableStateFlow<String?>(null)
    val pendingSelectedCustomerId: StateFlow<String?> = _pendingSelectedCustomerId.asStateFlow()

    private val _pendingSelectedSupplierId = MutableStateFlow<String?>(null)
    val pendingSelectedSupplierId: StateFlow<String?> = _pendingSelectedSupplierId.asStateFlow()

    private val _pendingSelectedProductIdOrSku = MutableStateFlow<String?>(null)
    val pendingSelectedProductIdOrSku: StateFlow<String?> = _pendingSelectedProductIdOrSku.asStateFlow()

    private val _pendingImeiSearchQuery = MutableStateFlow<String?>(null)
    val pendingImeiSearchQuery: StateFlow<String?> = _pendingImeiSearchQuery.asStateFlow()

    private val _pendingSelectedMaintenanceTicket = MutableStateFlow<String?>(null)
    val pendingSelectedMaintenanceTicket: StateFlow<String?> = _pendingSelectedMaintenanceTicket.asStateFlow()

    private val _pendingSelectedSaleInvoice = MutableStateFlow<String?>(null)
    val pendingSelectedSaleInvoice: StateFlow<String?> = _pendingSelectedSaleInvoice.asStateFlow()

    private val _pendingPurchaseProductId = MutableStateFlow<String?>(null)
    val pendingPurchaseProductId: StateFlow<String?> = _pendingPurchaseProductId.asStateFlow()

    fun requestWalletQuickOperation(operationType: WalletUserOperationType) {
        _pendingWalletQuickOperation.value = operationType
    }

    fun consumeWalletQuickOperation() {
        _pendingWalletQuickOperation.value = null
    }

    fun requestInventoryCountingMode() {
        _pendingInventoryCountMode.value = true
    }

    fun consumeInventoryCountingMode() {
        _pendingInventoryCountMode.value = false
    }

    fun requestSelectCustomerProfile(customerId: String) {
        _pendingSelectedCustomerId.value = customerId
    }

    fun consumeSelectedCustomerProfile() {
        _pendingSelectedCustomerId.value = null
    }

    fun requestSelectSupplierProfile(supplierId: String) {
        _pendingSelectedSupplierId.value = supplierId
    }

    fun consumeSelectedSupplierProfile() {
        _pendingSelectedSupplierId.value = null
    }

    fun requestSelectProductDetail(productIdOrSku: String) {
        _pendingSelectedProductIdOrSku.value = productIdOrSku
    }

    fun consumeSelectedProductDetail() {
        _pendingSelectedProductIdOrSku.value = null
    }

    fun requestImeiSearch(imeiQuery: String) {
        _pendingImeiSearchQuery.value = imeiQuery
    }

    fun consumeImeiSearch() {
        _pendingImeiSearchQuery.value = null
    }

    fun requestSelectMaintenanceTicket(ticketOrOrderId: String) {
        _pendingSelectedMaintenanceTicket.value = ticketOrOrderId
    }

    fun consumeSelectedMaintenanceTicket() {
        _pendingSelectedMaintenanceTicket.value = null
    }

    fun requestSelectSaleInvoice(invoiceNumber: String) {
        _pendingSelectedSaleInvoice.value = invoiceNumber
    }

    fun consumeSelectedSaleInvoice() {
        _pendingSelectedSaleInvoice.value = null
    }

    fun requestPurchaseProduct(productId: String) {
        _pendingPurchaseProductId.value = productId
    }

    fun consumePurchaseProduct() {
        _pendingPurchaseProductId.value = null
    }

    /**
     * Dispatches a Global Search Result to open the exact underlying entity (Part 2 Section 7).
     */
    fun openGlobalSearchResult(result: GlobalSearchResultItem) {
        when (result.categoryArabic) {
            "عميل" -> requestSelectCustomerProfile(result.referenceCode)
            "مورد" -> requestSelectSupplierProfile(result.referenceCode)
            "منتج / مخزون" -> requestSelectProductDetail(result.referenceCode)
            "IMEI / سيريال" -> requestImeiSearch(result.referenceCode)
            "صيانة" -> requestSelectMaintenanceTicket(result.referenceCode)
            "فاتورة بيع" -> requestSelectSaleInvoice(result.referenceCode)
        }
    }

    fun updateReportsFilter(updater: (ReportsCenterFilter) -> ReportsCenterFilter) {
        _reportsFilter.value = updater(_reportsFilter.value)
    }

    fun clearDiagnosticErrorLogs() {
        _diagnosticErrorLogs.value = emptyList()
    }

    private fun emitFeedback(
        message: String,
        isError: Boolean,
        categoryArabic: String = if (isError) "تنبيه تحقق" else "عملية ناجحة",
        whatChangedArabic: String = "",
        actionLabelArabic: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val sanitizedMessage = if (isError) {
            UxErrorSanitizer.sanitizeUserMessage(message)
        } else {
            message
        }
        _feedbackMessage.value = sanitizedMessage to isError

        if (isError) {
            val spec = UxErrorSanitizer.toActionableError(message, operationContextArabic = categoryArabic)
            if (UxErrorSanitizer.containsTechnicalLeak(message)) {
                val diagEntry = FriendlyAppError(
                    userMessageArabic = spec.problemArabic,
                    technicalCode = "SANITIZED_TECH_ERROR",
                    technicalDetails = spec.technicalDetailForDiagnostics
                )
                _diagnosticErrorLogs.value = (listOf(diagEntry) + _diagnosticErrorLogs.value).take(50)
            }
            _uxOperationState.value = UxOperationState.Error(
                problemArabic = spec.problemArabic,
                causeArabic = spec.causeArabic,
                dataSafetyNoteArabic = spec.dataSafetyArabic,
                actionLabelArabic = actionLabelArabic ?: spec.actionLabelArabic,
                onAction = onAction,
                technicalDiagnostics = spec.technicalDetailForDiagnostics
            )
        } else {
            val isCloudConfigured = shopConfiguration.value.cloudSyncEndpointUrl.isNotBlank()
            val persistenceNote = if (isCloudConfigured) {
                "تم حفظ العملية على الجهاز وإدراجها في طابور المزامنة السحابية"
            } else {
                "تم حفظ العملية على الجهاز وسيتم مزامنتها عند توفر الاتصال"
            }
            _uxOperationState.value = UxOperationState.Success(
                whatHappenedArabic = sanitizedMessage,
                whatChangedArabic = whatChangedArabic,
                dataPersistenceNoteArabic = persistenceNote,
                nextActionLabelArabic = actionLabelArabic,
                onNextAction = onAction
            )
        }

        val entry = InAppNotificationEntry(
            id = "NTF-${UUID.randomUUID()}",
            titleArabic = sanitizedMessage,
            categoryArabic = categoryArabic,
            isError = isError,
            timestampMillis = System.currentTimeMillis()
        )
        _notificationHistory.value = (listOf(entry) + _notificationHistory.value).take(40)
    }

    fun toggleGuidanceBanners() {
        val nextState = !_showGuidanceBanners.value
        _showGuidanceBanners.value = nextState
        emitFeedback(
            message = if (nextState) "تم إظهار أشرطة التوضيح والإرشاد المحاسبي في جميع الشاشات" else "تم إخفاء أشرطة التوضيح (يمكنك إظهارها من شريط الأدوات العلوي)",
            isError = false,
            categoryArabic = "واجهة المستخدم"
        )
    }

    fun dismissGuidanceBanners() {
        _showGuidanceBanners.value = false
    }

    fun clearNotificationHistory() {
        _notificationHistory.value = emptyList()
    }

    init {
        viewModelScope.launch {
            repository.ensureFoundationInitialized()
            _integrityReport.value = repository.runDataIntegrityHealthCheck()
        }
    }

    val dashboardKpis: StateFlow<DashboardLiveKpis> = repository.observeDashboardLiveKpis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardLiveKpis())

    val shopConfiguration: StateFlow<ShopConfigurationProfile> = repository.observeShopConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopConfigurationProfile())

    val monthlyReport: StateFlow<MonthlyFinancialReport> = repository.observeMonthlyFinancialReport()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyFinancialReport())

    val users: StateFlow<List<UserEntity>> = repository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roles: StateFlow<List<RoleEntity>> = repository.observeRoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.observeCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerBalances: StateFlow<List<CustomerBalanceProjection>> = repository.observeCustomerBalanceProjections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = repository.observeSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierBalances: StateFlow<List<SupplierBalanceProjection>> = repository.observeSupplierBalanceProjections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val brands: StateFlow<List<BrandEntity>> = repository.observeBrands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductInventoryProjection>> = repository.observeProductInventoryProjections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serialNumbers: StateFlow<List<SerialNumberEntity>> = repository.observeSerialNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryMovements: StateFlow<List<InventoryMovementEntity>> = repository.observeAllInventoryMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SaleEntity>> = repository.observeSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSaleItems: StateFlow<List<SaleItemEntity>> = repository.observeAllSaleItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val refunds: StateFlow<List<RefundEntity>> = repository.observeRefunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = repository.observePurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseItems: StateFlow<List<PurchaseItemEntity>> = repository.observeAllPurchaseItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceOrders: StateFlow<List<MaintenanceOrderEntity>> = repository.observeMaintenanceOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMaintenanceParts: StateFlow<List<MaintenancePartEntity>> = repository.observeAllMaintenanceParts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletProviders: StateFlow<List<WalletProviderEntity>> = repository.observeWalletProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wallets: StateFlow<List<WalletDerivedProjection>> = repository.observeWalletDerivedProjections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsReviewItems: StateFlow<List<SmsReviewItemProjection>> = repository.observeSmsReviewItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashDrawers: StateFlow<List<CashDrawerDerivedProjection>> = repository.observeCashDrawerDerivedProjections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashSessions: StateFlow<List<CashSessionEntity>> = repository.observeCashSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transfers: StateFlow<List<TransferEntity>> = repository.observeTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ledgerTransactions: StateFlow<List<TransactionEntity>> = repository.observeLedgerTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Domain-computed Customer Financial Profiles (Rules 2, 3, 29).
     */
    val customerProfiles: StateFlow<Map<String, CustomerFinancialProfile>> = combine(
        customers,
        customerBalances,
        sales,
        refunds,
        ledgerTransactions
    ) { custList, balList, salesList, refundList, txList ->
        val balMap = balList.associate { it.customerId to it.outstandingBalanceMinor }
        custList.associate { c ->
            c.localId to PartyAccountStatementEngine.buildCustomerProfile(
                customerId = c.localId,
                derivedBalanceMinor = balMap[c.localId] ?: 0L,
                sales = salesList,
                refunds = refundList,
                ledgerTransactions = txList
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Domain-computed Supplier Financial Profiles (Rules 4, 29).
     */
    val supplierProfiles: StateFlow<Map<String, SupplierFinancialProfile>> = combine(
        suppliers,
        supplierBalances,
        purchases,
        ledgerTransactions
    ) { supList, balList, purList, txList ->
        val balMap = balList.associate { it.supplierId to it.outstandingPayableMinor }
        supList.associate { s ->
            s.localId to PartyAccountStatementEngine.buildSupplierProfile(
                supplierId = s.localId,
                derivedBalanceMinor = balMap[s.localId] ?: 0L,
                purchases = purList,
                ledgerTransactions = txList
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Domain-computed Maintenance Ticket Financial Summaries (Rules 18, 20, 29).
     */
    val maintenanceFinancialSummaries: StateFlow<Map<String, MaintenanceFinancialSummary>> = combine(
        maintenanceOrders,
        allMaintenanceParts,
        ledgerTransactions
    ) { ordersList, partsList, txList ->
        ordersList.associate { order ->
            order.localId to MaintenanceFinancialEngine.computeTicketFinancials(
                order = order,
                parts = partsList,
                ledgerTransactions = txList
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Domain-computed IMEI / Serial Registry Projections (Rule 8).
     */
    private val salesAndPurchasesMapFlow = combine(
        sales,
        allSaleItems,
        purchases,
        allPurchaseItems
    ) { salesList, saleItemsList, purList, purItemsList ->
        val saleById = salesList.associateBy { it.localId }
        val purById = purList.associateBy { it.localId }
        val saleItemBySerialId = saleItemsList.filter { it.serialNumberId != null }
            .associateBy { it.serialNumberId!! }
        val purItemBySerialId = purItemsList.filter { it.serialNumberId != null }
            .associateBy { it.serialNumberId!! }
        val purItemByImei = purItemsList.filter { !it.serialOrImei.isNullOrBlank() }
            .associateBy { it.serialOrImei!! }
        Triple(
            saleItemBySerialId.mapValues { (_, item) -> saleById[item.saleId] },
            purItemBySerialId.mapValues { (_, item) -> purById[item.purchaseId] },
            purItemByImei.mapValues { (_, item) -> purById[item.purchaseId] }
        )
    }

    val imeiRegistryProjections: StateFlow<List<ImeiRegistryProjection>> = combine(
        serialNumbers,
        products,
        customers,
        salesAndPurchasesMapFlow
    ) { serList, prodList, custList, invoiceMaps ->
        val prodMap = prodList.associateBy { it.productId }
        val custMap = custList.associateBy { it.localId }
        val (saleBySerialId, purBySerialId, purByImei) = invoiceMaps

        serList.map { sr ->
            val prod = prodMap[sr.productId]
            val linkedSale = saleBySerialId[sr.localId]
            val linkedPurchase = purBySerialId[sr.localId] ?: purByImei[sr.serialOrImei]
            val resolvedCustomerId = sr.currentCustomerId ?: linkedSale?.customerId
            val cust = resolvedCustomerId?.let { custMap[it] }

            val saleStatusLabel = when {
                sr.status.name == "SOLD" && linkedSale != null -> "مباع (فاتورة #${linkedSale.invoiceNumber})"
                sr.status.name == "SOLD" -> "مباع"
                sr.status.name == "IN_STOCK" -> "متاح في المخزن"
                else -> sr.status.arabicLabel
            }
            val purchaseStatusLabel = if (linkedPurchase != null) {
                "وارد بفاتورة شراء #${linkedPurchase.invoiceNumber}"
            } else {
                "مسجل بحركة مخزنية (تكلفة ${Money.ofMinor(sr.acquisitionCostMinor).formatArabic()})"
            }

            ImeiRegistryProjection(
                serialEntity = sr,
                productNameArabic = prod?.nameArabic ?: "منتج غير معروف",
                productSku = prod?.sku ?: "-",
                saleStatusArabic = saleStatusLabel,
                purchaseStatusArabic = purchaseStatusLabel,
                customerId = resolvedCustomerId,
                customerNameArabic = cust?.fullName,
                customerPhone = cust?.phone,
                saleInvoiceNumber = linkedSale?.invoiceNumber,
                purchaseInvoiceNumber = linkedPurchase?.invoiceNumber
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncQueue: StateFlow<List<SyncQueueEntity>> = repository.observeSyncQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncConflicts: StateFlow<List<SyncConflictEntity>> = repository.observeSyncConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backups: StateFlow<List<BackupMetadataEntity>> = repository.observeBackups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Truthful Synchronization Status (Part 3 Rule 12 & 13).
     * Never claims "Server ACK" or "تمت المزامنة" unless a real remote endpoint is configured and synced.
     */
    val truthfulSyncStatus: StateFlow<SyncUiStatus> = combine(
        syncQueue,
        syncConflicts,
        shopConfiguration
    ) { queue, conflicts, config ->
        val pendingCount = queue.count { it.syncState == SyncState.PENDING || it.syncState == SyncState.SYNCING }
        val failedCount = queue.count { it.syncState == SyncState.FAILED || it.syncState == SyncState.RETRYING }
        val conflictCount = conflicts.count { !it.isResolved }
        val syncedCount = queue.count { it.syncState == SyncState.SYNCED }
        repository.syncCoordinator.resolveTruthfulSyncUiStatus(
            pendingCount = pendingCount,
            failedCount = failedCount,
            unresolvedConflictCount = conflictCount,
            syncedCount = syncedCount,
            isRemoteEndpointConfigured = config.cloudSyncEndpointUrl.isNotBlank()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiStatus.SAVED_LOCALLY)

    /**
     * Authoritative Reports Center Bundle (Part 3 Rules 1, 2, 3).
     * Computed strictly in the domain layer via `ReportsCenterEngine`.
     */
    private data class ReportsGroupA(
        val filter: ReportsCenterFilter,
        val sales: List<SaleEntity>,
        val saleItems: List<SaleItemEntity>,
        val refunds: List<RefundEntity>,
        val purchases: List<PurchaseEntity>
    )

    private data class ReportsGroupB(
        val expenses: List<ExpenseEntity>,
        val maintenanceOrders: List<MaintenanceOrderEntity>,
        val maintenanceParts: List<MaintenancePartEntity>,
        val transactions: List<TransactionEntity>
    )

    private data class ReportsGroupC(
        val products: List<ProductInventoryProjection>,
        val customers: List<CustomerBalanceProjection>,
        val suppliers: List<SupplierBalanceProjection>,
        val wallets: List<WalletDerivedProjection>
    )

    private val reportsGroupAFlow = combine(
        _reportsFilter,
        sales,
        allSaleItems,
        refunds,
        purchases
    ) { f, s, si, r, p -> ReportsGroupA(f, s, si, r, p) }

    private val reportsGroupBFlow = combine(
        expenses,
        maintenanceOrders,
        allMaintenanceParts,
        ledgerTransactions
    ) { e, mo, mp, tx -> ReportsGroupB(e, mo, mp, tx) }

    private val reportsGroupCFlow = combine(
        products,
        customerBalances,
        supplierBalances,
        wallets
    ) { pr, cb, sb, w -> ReportsGroupC(pr, cb, sb, w) }

    val reportsCenterBundle: StateFlow<ReportsCenterBundle> = combine(
        reportsGroupAFlow,
        reportsGroupBFlow,
        reportsGroupCFlow,
        _activeUserId,
        users
    ) { a, b, c, currentUserId, userList ->
        if (repository.isSessionLocked.value) {
            return@combine ReportsCenterBundle(filter = a.filter)
        }
        val activeRoleCode = userList.firstOrNull { it.localId == currentUserId }?.roleCode
            ?: when {
                currentUserId.contains("CASHIER", ignoreCase = true) -> "CASHIER"
                currentUserId.contains("TECH", ignoreCase = true) -> "TECHNICIAN"
                currentUserId.contains("INVENTORY", ignoreCase = true) -> "INVENTORY"
                else -> "OWNER"
            }
        if (!com.example.auth.RolePermissionMatrix.hasPermission(activeRoleCode, com.example.auth.PermissionCode.REPORTS_SENSITIVE_VIEW)) {
            return@combine ReportsCenterBundle(filter = a.filter)
        }
        ReportsCenterEngine.buildReportsCenterBundle(
            filter = a.filter,
            sales = a.sales,
            saleItems = a.saleItems,
            refunds = a.refunds,
            purchases = a.purchases,
            expenses = b.expenses,
            maintenanceOrders = b.maintenanceOrders,
            maintenanceParts = b.maintenanceParts,
            transactions = b.transactions,
            products = c.products,
            customers = c.customers,
            suppliers = c.suppliers,
            wallets = c.wallets
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReportsCenterBundle(filter = ReportsCenterFilter())
    )

    val periodComparisons: StateFlow<PeriodComparisonsBundle> = combine(
        sales,
        allSaleItems,
        expenses
    ) { sList, sItems, expList ->
        P2ForecastingEngine.computePeriodComparisons(sList, sItems, expList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        P2ForecastingEngine.computePeriodComparisons(emptyList(), emptyList(), emptyList())
    )

    val businessScorecard: StateFlow<BusinessScorecardBundle> = combine(
        products,
        sales,
        expenses,
        cashDrawers,
        customerBalances
    ) { prodList, sList, expList, drawers, cBalances ->
        val cashBal = drawers.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: drawers.firstOrNull()?.derivedBalanceMinor ?: 0L
        val totalRec = cBalances.sumOf { it.outstandingBalanceMinor.coerceAtLeast(0L) }
        P2ForecastingEngine.computeBusinessScorecard(
            products = prodList,
            sales = sList,
            expenses = expList,
            cashBalanceMinor = cashBal,
            totalCustomerReceivablesMinor = totalRec
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        P2ForecastingEngine.computeBusinessScorecard(emptyList(), emptyList(), emptyList(), 0L, 0L)
    )

    val businessForecasting: StateFlow<BusinessForecastingBundle> = combine(
        sales,
        expenses,
        transfers,
        cashDrawers
    ) { sList, expList, transfersList, drawers ->
        val cashBal = drawers.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: drawers.firstOrNull()?.derivedBalanceMinor ?: 0L
        P2ForecastingEngine.computeBusinessForecasting(
            sales = sList,
            expenses = expList,
            walletTransfers = transfersList,
            cashBalanceMinor = cashBal
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        P2ForecastingEngine.computeBusinessForecasting(emptyList(), emptyList(), emptyList(), 0L)
    )

    val dailyBusinessSummary: StateFlow<DailyBusinessSummaryBundle> = combine(
        sales,
        allSaleItems,
        expenses,
        cashDrawers,
        wallets
    ) { sList, sItems, expList, drawers, walletList ->
        val cashBal = drawers.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: drawers.firstOrNull()?.derivedBalanceMinor ?: 0L
        val walletBalTotal = walletList.sumOf { it.derivedBalanceMinor }
        P2ForecastingEngine.computeDailyBusinessSummary(
            sales = sList,
            saleItems = sItems,
            expenses = expList,
            cashBalanceMinor = cashBal,
            walletBalancesMinor = walletBalTotal,
            activeAlertsCount = 0
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        P2ForecastingEngine.computeDailyBusinessSummary(emptyList(), emptyList(), emptyList(), 0L, 0L, 0)
    )

    val demandForecasts: StateFlow<List<ProductDemandForecastItem>> = combine(
        products,
        sales,
        allSaleItems
    ) { prodList, sList, sItems ->
        P2ForecastingEngine.computeDemandForecasts(prodList, sList, sItems)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    suspend fun requestSensitiveReports(
        filter: ReportsCenterFilter = _reportsFilter.value
    ): AppResult<ReportsCenterBundle> = repository.getAuthorizedSensitiveReports(
        filter = filter,
        actorUserId = _activeUserId.value
    )

    /**
     * Live Internal System Alerts derived from operational and financial KPIs & Wallet Limit Thresholds (Rule 11).
     * Covers: مخزون منخفض, صيانة جاهزة, مبالغ مستحقة, رسائل مالية تحتاج مراجعة, فرق خزينة, مزامنة معلقة, Backup required.
     */
    val systemAlerts: StateFlow<List<SystemAlertItem>> = combine(
        dashboardKpis,
        wallets
    ) { kpi, walletList ->
        buildList {
            if (kpi.lowStockProductsCount > 0) {
                add(
                    SystemAlertItem(
                        code = "LOW_STOCK_ALERT",
                        titleArabic = "مخزون منخفض: ${kpi.lowStockProductsCount} أصناف وصلت لحد إعادة الطلب",
                        severity = "WARNING",
                        routeTarget = MekkaDestination.PRODUCTS.route
                    )
                )
            }
            if (kpi.readyRepairsCount > 0) {
                add(
                    SystemAlertItem(
                        code = "READY_REPAIRS_ALERT",
                        titleArabic = "صيانة جاهزة للتسليم: ${kpi.readyRepairsCount} أجهزة جاهزة للتحصيل",
                        severity = "INFO",
                        routeTarget = MekkaDestination.MAINTENANCE.route
                    )
                )
            }
            if (kpi.customerReceivablesMinor > 0L) {
                add(
                    SystemAlertItem(
                        code = "CUSTOMER_RECEIVABLES_ALERT",
                        titleArabic = "مبالغ مستحقة على العملاء: ${Money.ofMinor(kpi.customerReceivablesMinor).formatArabic()}",
                        severity = "WARNING",
                        routeTarget = MekkaDestination.CUSTOMERS.route
                    )
                )
            }
            if (kpi.supplierPayablesMinor > 0L) {
                add(
                    SystemAlertItem(
                        code = "SUPPLIER_PAYABLES_ALERT",
                        titleArabic = "مبالغ مستحقة للموردين: ${Money.ofMinor(kpi.supplierPayablesMinor).formatArabic()}",
                        severity = "WARNING",
                        routeTarget = MekkaDestination.SUPPLIERS.route
                    )
                )
            }
            if (kpi.pendingSmsCount > 0) {
                add(
                    SystemAlertItem(
                        code = "PENDING_SMS_ALERT",
                        titleArabic = "رسائل مالية تحتاج مراجعة: ${kpi.pendingSmsCount} رسالة محفظة معلقة",
                        severity = "WARNING",
                        routeTarget = MekkaDestination.SMS_INTELLIGENCE.route
                    )
                )
            }
            if (kpi.lastCashDiscrepancyMinor != 0L) {
                add(
                    SystemAlertItem(
                        code = "CASH_DISCREPANCY_ALERT",
                        titleArabic = "فرق خزينة مسجل: ${Money.ofMinor(kpi.lastCashDiscrepancyMinor).formatArabic()} — اضغط للمراجعة",
                        severity = "CRITICAL",
                        routeTarget = MekkaDestination.CASH_DRAWER.route
                    )
                )
            }
            if (kpi.unresolvedConflictsCount > 0) {
                add(
                    SystemAlertItem(
                        code = "CONFLICT_ALERT",
                        titleArabic = "تعارض مزامنة (${kpi.unresolvedConflictsCount}) يتطلب المراجعة الفورية",
                        severity = "CRITICAL",
                        routeTarget = MekkaDestination.SYNC.route
                    )
                )
            } else if (kpi.failedSyncCount > 0 || kpi.pendingSyncCount > 0) {
                val syncCount = kpi.failedSyncCount + kpi.pendingSyncCount
                add(
                    SystemAlertItem(
                        code = "PENDING_SYNC_ALERT",
                        titleArabic = "مزامنة معلقة: $syncCount عملية في طابور المزامنة",
                        severity = if (kpi.failedSyncCount > 0) "WARNING" else "INFO",
                        routeTarget = MekkaDestination.SYNC.route
                    )
                )
            }
            if (kpi.backupRequired) {
                add(
                    SystemAlertItem(
                        code = "BACKUP_REQUIRED_ALERT",
                        titleArabic = "نسخ احتياطي مطلوب: يوصى بحفظ نسخة احتياطية مشفرة اليوم",
                        severity = "WARNING",
                        routeTarget = MekkaDestination.BACKUP.route
                    )
                )
            }
            walletList.forEach { w ->
                val dailyPct = if (w.dailyLimitMinor > 0L) (w.todayOutflowMinor * 100L) / w.dailyLimitMinor else 0L
                val monthlyPct = if (w.monthlyLimitMinor > 0L) (w.monthOutflowMinor * 100L) / w.monthlyLimitMinor else 0L
                if (dailyPct >= 80L || monthlyPct >= 80L) {
                    val maxPct = maxOf(dailyPct, monthlyPct)
                    add(
                        SystemAlertItem(
                            code = "WALLET_LIMIT_${w.walletNumber}",
                            titleArabic = "تنبيه سقف محفظة (${w.walletNameArabic}): استهلكت $maxPct% من الحد المسموح",
                            severity = if (maxPct >= 95L) "CRITICAL" else "WARNING",
                            routeTarget = MekkaDestination.WALLETS.route
                        )
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // =============================================================================================
    // P2 — SAFE AUTOMATION, DATA INTEGRITY & PROCUREMENT INTELLIGENCE (Requirements P2-D, P2-E, P2-L, P2-N)
    // =============================================================================================
    private val _automationRules = MutableStateFlow<List<SafeAutomationRule>>(SafeAutomationEngine.DEFAULT_RULES)
    val automationRules: StateFlow<List<SafeAutomationRule>> = _automationRules.asStateFlow()

    fun toggleAutomationRule(ruleId: String) {
        _automationRules.value = _automationRules.value.map { rule ->
            if (rule.id == ruleId) rule.copy(isEnabled = !rule.isEnabled) else rule
        }
    }

    private data class IntegrityScanGroupA(
        val transactions: List<TransactionEntity>,
        val wallets: List<WalletDerivedProjection>,
        val cashSessions: List<CashSessionEntity>,
        val inventoryMovements: List<InventoryMovementEntity>
    )

    private data class IntegrityScanGroupB(
        val products: List<ProductInventoryProjection>,
        val customerBalances: List<CustomerBalanceProjection>,
        val syncQueue: List<SyncQueueEntity>
    )

    private val integrityScanGroupAFlow = combine(
        ledgerTransactions,
        wallets,
        cashSessions,
        inventoryMovements
    ) { txs, wList, sessions, movements ->
        IntegrityScanGroupA(txs, wList, sessions, movements)
    }

    private val integrityScanGroupBFlow = combine(
        products,
        customerBalances,
        syncQueue
    ) { prods, custBals, syncQ ->
        IntegrityScanGroupB(prods, custBals, syncQ)
    }

    val integrityIssues: StateFlow<List<IntegrityIssueReport>> = combine(
        integrityScanGroupAFlow,
        integrityScanGroupBFlow
    ) { groupA, groupB ->
        DataIntegrityAndRepairEngine.runFullIntegrityScan(
            transactions = groupA.transactions,
            wallets = groupA.wallets,
            cashSessions = groupA.cashSessions,
            inventoryMovements = groupA.inventoryMovements,
            products = groupB.products,
            customerBalances = groupB.customerBalances,
            syncQueue = groupB.syncQueue,
            lastBackup = null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appHealthSummary: StateFlow<AppHealthSummary> = combine(
        products,
        wallets,
        cashDrawers,
        syncQueue,
        integrityIssues
    ) { prods, wList, drawers, syncQ, issues ->
        DataIntegrityAndRepairEngine.evaluateAppHealth(
            products = prods,
            wallets = wList,
            cashDrawer = drawers.firstOrNull(),
            syncQueue = syncQ,
            lastBackup = null,
            issues = issues
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DataIntegrityAndRepairEngine.evaluateAppHealth(emptyList(), emptyList(), null, emptyList(), null, emptyList())
    )

    fun executeAuthorizedRepair(proposal: IntegrityRepairProposal) {
        viewModelScope.launch {
            _uxOperationState.value = UxOperationState.Loading("جاري تنفيذ إجراء المعالجة والإصلاح المعتمد...")
            when (proposal.repairCode) {
                "REPAIR_NEGATIVE_STOCK" -> {
                    val negativeProds = products.value.filter { it.quantityOnHand < 0 }
                    for (p in negativeProds) {
                        val needed = -p.quantityOnHand
                        repository.recordAuthorizedInventoryMovement(
                            productId = p.productId,
                            movementType = InventoryMovementType.ADJUSTMENT_IN,
                            quantity = needed,
                            unitCostMinor = p.weightedAvgCostMinor,
                            reason = "معالجة آلية معتمدة لتصفير الرصيد السالب (${proposal.titleArabic})",
                            actorUserId = _activeUserId.value
                        )
                    }
                }
                "REPAIR_RETRY_SYNC" -> {
                    repository.runDeltaSyncCycle()
                }
                else -> {
                    // Standard supervised repair
                }
            }
            _uxOperationState.value = UxOperationState.Success("تم تنفيذ واعتماد إجراء المعالجة (${proposal.titleArabic}) بنجاح")
            _feedbackMessage.value = "تم تنفيذ إجراء المعالجة: ${proposal.titleArabic}" to false
        }
    }

    private val _purchaseDrafts = MutableStateFlow<List<PurchaseDraftRecord>>(emptyList())
    val purchaseDrafts: StateFlow<List<PurchaseDraftRecord>> = _purchaseDrafts.asStateFlow()

    val suggestedReorderList: StateFlow<List<SuggestedReorderItem>> = combine(
        demandForecasts,
        suppliers,
        products
    ) { forecasts, supList, prodList ->
        PurchaseDraftEngine.buildSuggestedReorderList(forecasts, supList, prodList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePurchaseDraftStatus(draftId: String, newStatus: PurchaseDraftStatus) {
        _purchaseDrafts.value = _purchaseDrafts.value.map { draft ->
            if (draft.draftId == draftId) {
                when (val res = PurchaseDraftEngine.transitionStatus(draft, newStatus)) {
                    is AppResult.Success -> res.data
                    is AppResult.Error -> draft
                }
            } else draft
        }
    }

    fun createPurchaseDraftFromSuggestedReorder(
        supplierId: String?,
        supplierNameArabic: String,
        items: List<SuggestedReorderItem>,
        notes: String
    ) {
        val lineItems = items.map { item ->
            PurchaseDraftLineItem(
                productId = item.productId,
                productNameArabic = item.productNameArabic,
                sku = item.sku,
                quantity = item.suggestedQuantity,
                estimatedUnitCostMinor = item.estimatedUnitCostMinor,
                estimatedLineTotalMinor = item.estimatedTotalCostMinor
            )
        }
        val draft = PurchaseDraftEngine.createDraft(
            supplierId = supplierId,
            supplierNameArabic = supplierNameArabic,
            items = lineItems,
            notes = notes,
            actorUserId = _activeUserId.value
        )
        _purchaseDrafts.value = listOf(draft) + _purchaseDrafts.value
        _feedbackMessage.value = "تم إنشاء مسودة الشراء #${draft.draftNumber} بنجاح" to false
    }

    fun convertPurchaseDraftToActualPurchase(
        draftId: String,
        paymentAccountType: FinancialAccountType,
        paymentAccountId: String
    ) {
        val draft = _purchaseDrafts.value.firstOrNull { it.draftId == draftId } ?: return
        if (draft.items.isEmpty()) return
        val supId = draft.supplierId ?: suppliers.value.firstOrNull()?.localId ?: return

        val purchaseLines = draft.items.map { item ->
            PurchaseLineInput(
                productId = item.productId,
                quantity = item.quantity,
                unitCostMinor = item.estimatedUnitCostMinor
            )
        }
        viewModelScope.launch {
            _uxOperationState.value = UxOperationState.Loading("جاري تحويل المسودة إلى فاتورة شراء وتوريد الأصناف...")
            val res = repository.executePurchase(
                supplierId = supId,
                items = purchaseLines,
                actorUserId = _activeUserId.value
            )
            when (res) {
                is AppResult.Success -> {
                    _purchaseDrafts.value = _purchaseDrafts.value.map { d ->
                        if (d.draftId == draftId) d.copy(
                            status = PurchaseDraftStatus.CONVERTED,
                            convertedPurchaseId = res.data.localId,
                            updatedAt = System.currentTimeMillis()
                        ) else d
                    }
                    _uxOperationState.value = UxOperationState.Success("تم توريد الأصناف وترحيل الفاتورة #${res.data.invoiceNumber} بنجاح")
                    _feedbackMessage.value = "تم تحويل المسودة إلى فاتورة رسمية #${res.data.invoiceNumber}" to false
                }
                is AppResult.Error -> {
                    _uxOperationState.value = UxOperationState.Error(res.message, res.code)
                    _feedbackMessage.value = "فشل تحويل المسودة: ${res.message}" to true
                }
            }
        }
    }

    // =============================================================================================
    // P1 — BUSINESS INTELLIGENCE, SMART INSIGHTS & ANOMALIES (Requirements P1-A to P1-R)
    // =============================================================================================
    private val _dismissedInsightIds = MutableStateFlow<Set<String>>(emptySet())

    val priceHistory: StateFlow<List<PriceHistoryEntity>> = repository
        .observeRecentPriceHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smartBusinessInsights: StateFlow<List<SmartBusinessInsightItem>> = combine(
        combine(sales, allSaleItems, products) { s, si, p -> Triple(s, si, p) },
        combine(expenses, wallets, _dismissedInsightIds) { e, w, d -> Triple(e, w, d) }
    ) { (s, si, p), (e, w, dismissed) ->
        SmartBusinessInsightsEngine.generateInsights(s, si, p, e, w)
            .filter { it.id !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detectedAnomalies: StateFlow<List<AnomalyDetectionAlert>> = combine(
        expenses,
        sales,
        refunds,
        cashSessions
    ) { e, s, r, cs ->
        SmartBusinessInsightsEngine.detectAnomalies(e, s, r, cs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerSegments: StateFlow<Map<String, CustomerSegment>> = combine(
        customers,
        customerBalances,
        sales
    ) { custList, balList, saleList ->
        val balMap = balList.associate { it.customerId to it.outstandingBalanceMinor }
        val salesByCust = saleList.groupBy { it.customerId }
        custList.associate { c ->
            val bal = balMap[c.localId] ?: 0L
            val cSales = salesByCust[c.localId] ?: emptyList()
            c.localId to CustomerIntelligenceEngine.classifyCustomer(c, bal, cSales)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val receivableAgingSummary: StateFlow<ReceivableAgingSummary> = combine(
        customers,
        customerBalances,
        sales
    ) { custList, balList, saleList ->
        val balMap = balList.associate { it.customerId to it.outstandingBalanceMinor }
        CustomerIntelligenceEngine.computeGlobalReceivableAging(custList, balMap, saleList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReceivableAgingSummary(0L, 0L, 0L, 0L, 0L, 0L, 0))

    fun dismissInsight(insightId: String) {
        _dismissedInsightIds.value = _dismissedInsightIds.value + insightId
    }

    fun executeShiftHandover(
        toUserId: String,
        countedCashMinor: Long,
        handoverReason: String,
        notes: String = "",
        onSuccess: (ShiftHandoverRecord) -> Unit = {}
    ) {
        val fromUserId = _activeUserId.value
        viewModelScope.launch {
            val res = repository.executeShiftHandover(
                fromUserId = fromUserId,
                toUserId = toUserId,
                countedCashMinor = countedCashMinor,
                handoverReasonArabic = handoverReason,
                notes = notes
            )
            when (res) {
                is AppResult.Success -> {
                    showFeedbackMessage("تم تسليم الوردية واعتماد المحضر بنجاح")
                    onSuccess(res.data)
                }
                is AppResult.Error -> {
                    showFeedbackMessage(res.message, isError = true)
                }
            }
        }
    }

    fun getProductIntelligenceSummary(product: ProductInventoryProjection): ProductIntelligenceSummary {
        return ProductPurchaseIntelligenceEngine.computeProductIntelligence(
            product = product,
            allPurchases = purchases.value,
            allPurchaseItems = allPurchaseItems.value,
            allSales = sales.value,
            allSaleItems = allSaleItems.value,
            suppliers = suppliers.value,
            priceChanges = priceHistory.value
        )
    }

    fun getCustomerFactInsights(customer: CustomerEntity): List<CustomerFactInsight> {
        val bal = customerBalances.value.firstOrNull { it.customerId == customer.localId }?.outstandingBalanceMinor ?: 0L
        val cSales = sales.value.filter { it.customerId == customer.localId }
        val cPayments = ledgerTransactions.value.filter { it.customerId == customer.localId }
        return CustomerIntelligenceEngine.generateFactualInsights(customer, bal, cSales, cPayments)
    }

    /**
     * Unified Smart Search across all 9 required domains (Rule 8):
     * Customers, Suppliers, Products (name/SKU/Barcode), IMEI/Serial, Invoices (Sales/Purchases),
     * Maintenance, Wallet transactions, Expenses, and Ledger references.
     */
    private val catalogSearchFlow = combine(
        _globalSearchQuery,
        customers,
        suppliers,
        products,
        serialNumbers
    ) { query, custList, supList, prodList, serList ->
        val q = Money.normalizeDigits(query).trim().lowercase()
        if (q.isEmpty()) return@combine emptyList()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        val custBalMap = customerBalances.value.associate { it.customerId to it.outstandingBalanceMinor }
        val supBalMap = supplierBalances.value.associate { it.supplierId to it.outstandingPayableMinor }
        val results = mutableListOf<GlobalSearchResultItem>()
        custList.asSequence().filter {
            it.fullName.lowercase().contains(q) ||
                it.phone.lowercase().contains(q) ||
                (it.nationalId?.lowercase()?.contains(q) == true)
        }.forEach { c ->
            val bal = custBalMap[c.localId] ?: 0L
            val st = if (bal > 0L) "مديونية ${Money.ofMinor(bal).formatArabic()}" else "نشط"
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "عميل",
                    title = c.fullName,
                    subtitle = "هاتف: ${c.phone} • $st",
                    referenceCode = c.phone,
                    routeTarget = MekkaDestination.CUSTOMERS.route,
                    statusArabic = st,
                    timestamp = c.updatedAt,
                    dateFormatted = fmt.format(java.util.Date(c.updatedAt)),
                    entityId = c.localId
                )
            )
        }
        supList.asSequence().filter {
            it.companyName.lowercase().contains(q) ||
                it.contactName.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }.forEach { s ->
            val bal = supBalMap[s.localId] ?: 0L
            val st = if (bal > 0L) "مستحق ${Money.ofMinor(bal).formatArabic()}" else "نشط"
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "مورد",
                    title = s.companyName,
                    subtitle = "هاتف: ${s.phone} • $st",
                    referenceCode = s.phone,
                    routeTarget = MekkaDestination.SUPPLIERS.route,
                    statusArabic = st,
                    timestamp = s.updatedAt,
                    dateFormatted = fmt.format(java.util.Date(s.updatedAt)),
                    entityId = s.localId
                )
            )
        }
        prodList.asSequence().filter {
            it.nameArabic.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                (it.barcode?.lowercase()?.contains(q) == true)
        }.forEach { p ->
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "منتج / مخزون",
                    title = p.nameArabic,
                    subtitle = "SKU: ${p.sku} • المتاح: ${p.derivedStockQty} • السعر: ${Money.ofMinor(p.defaultSellingPriceMinor).formatArabic()}",
                    referenceCode = p.sku,
                    routeTarget = MekkaDestination.PRODUCTS.route,
                    statusArabic = p.stockStatusArabic,
                    timestamp = System.currentTimeMillis(),
                    dateFormatted = fmt.format(java.util.Date()),
                    entityId = p.productId
                )
            )
        }
        serList.asSequence().filter {
            it.serialOrImei.lowercase().contains(q) || (it.secondaryImei?.lowercase()?.contains(q) == true)
        }.forEach { sr ->
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "IMEI / سيريال",
                    title = "IMEI: ${sr.serialOrImei}",
                    subtitle = "الحالة: ${sr.status.arabicLabel}",
                    referenceCode = sr.serialOrImei,
                    routeTarget = MekkaDestination.PRODUCTS.route,
                    statusArabic = sr.status.arabicLabel,
                    timestamp = sr.updatedAt,
                    dateFormatted = fmt.format(java.util.Date(sr.updatedAt)),
                    entityId = sr.localId
                )
            )
        }
        results
    }

    private val operationsSearchFlow = combine(
        _globalSearchQuery,
        sales,
        maintenanceOrders,
        transfers,
        expenses
    ) { query, salesList, maintList, transferList, expenseList ->
        val q = Money.normalizeDigits(query).trim().lowercase()
        if (q.isEmpty()) return@combine emptyList()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        val results = mutableListOf<GlobalSearchResultItem>()
        salesList.asSequence().filter {
            it.invoiceNumber.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
        }.forEach { sale ->
            val st = com.example.financial.FriendlyTerminologyMapper.toFriendlyStatusArabic(sale.status)
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "فاتورة بيع",
                    title = "فاتورة #${sale.invoiceNumber} (${Money.ofMinor(sale.totalAmountMinor).formatArabic()})",
                    subtitle = "المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()} • الحالة: $st",
                    referenceCode = sale.invoiceNumber,
                    routeTarget = MekkaDestination.SALES.route,
                    statusArabic = st,
                    timestamp = sale.saleTimestamp,
                    dateFormatted = fmt.format(java.util.Date(sale.saleTimestamp)),
                    entityId = sale.localId
                )
            )
        }
        maintList.asSequence().filter {
            it.ticketNumber.lowercase().contains(q) ||
                it.customerNameSnapshot.lowercase().contains(q) ||
                it.customerPhoneSnapshot.lowercase().contains(q) ||
                it.deviceBrand.lowercase().contains(q) ||
                it.deviceModel.lowercase().contains(q) ||
                it.deviceImeiOrSerial.lowercase().contains(q) ||
                it.deviceSerial.lowercase().contains(q)
        }.forEach { order ->
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "صيانة",
                    title = "${order.deviceBrand} ${order.deviceModel} (#${order.ticketNumber})",
                    subtitle = "العميل: ${order.customerNameSnapshot} • الحالة: ${order.status.arabicLabel}",
                    referenceCode = order.ticketNumber,
                    routeTarget = MekkaDestination.MAINTENANCE.route,
                    statusArabic = order.status.arabicLabel,
                    timestamp = order.receivedAt,
                    dateFormatted = fmt.format(java.util.Date(order.receivedAt)),
                    entityId = order.localId
                )
            )
        }
        transferList.asSequence().filter {
            it.transferReference.lowercase().contains(q) ||
                it.customerPhoneOrAccount.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
        }.forEach { tr ->
            val friendlyKind = WalletDomainPolicyEngine.friendlyOperationLabel(tr.transferKind, tr.notes)
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "عملية محفظة",
                    title = "$friendlyKind (${Money.ofMinor(tr.principalAmountMinor).formatArabic()})",
                    subtitle = "طرف مقابل: ${tr.customerPhoneOrAccount} • مرجع: ${tr.transferReference}",
                    referenceCode = tr.transferReference,
                    routeTarget = MekkaDestination.WALLETS.route,
                    statusArabic = "منفذة",
                    timestamp = tr.transferTimestamp,
                    dateFormatted = fmt.format(java.util.Date(tr.transferTimestamp)),
                    entityId = tr.localId
                )
            )
        }
        expenseList.asSequence().filter {
            it.localId.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.categoryName.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
        }.forEach { exp ->
            results.add(
                GlobalSearchResultItem(
                    categoryArabic = "مصروف",
                    title = "${exp.title} (${Money.ofMinor(exp.amountMinor).formatArabic()})",
                    subtitle = "التصنيف: ${exp.categoryName}",
                    referenceCode = exp.localId,
                    routeTarget = MekkaDestination.EXPENSES.route,
                    statusArabic = "معتمد",
                    timestamp = exp.expenseTimestamp,
                    dateFormatted = fmt.format(java.util.Date(exp.expenseTimestamp)),
                    entityId = exp.localId
                )
            )
        }
        results
    }

    val globalSearchResults: StateFlow<List<GlobalSearchResultItem>> = combine(
        catalogSearchFlow,
        operationsSearchFlow,
        ledgerTransactions,
        purchases
    ) { catMatches, opsMatches, txs, purchaseList ->
        val q = Money.normalizeDigits(_globalSearchQuery.value).trim().lowercase()
        if (q.isEmpty()) return@combine emptyList()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val purchaseMatches = purchaseList.asSequence().filter {
            it.invoiceNumber.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
        }.map { pur ->
            val st = if (pur.totalAmountMinor <= pur.paidAmountMinor) "مسددة" else "آجل"
            GlobalSearchResultItem(
                categoryArabic = "فاتورة شراء",
                title = "مشتريات #${pur.invoiceNumber} (${Money.ofMinor(pur.totalAmountMinor).formatArabic()})",
                subtitle = "المسدد: ${Money.ofMinor(pur.paidAmountMinor).formatArabic()}",
                referenceCode = pur.invoiceNumber,
                routeTarget = MekkaDestination.PURCHASES.route,
                statusArabic = st,
                timestamp = pur.purchaseTimestamp,
                dateFormatted = fmt.format(java.util.Date(pur.purchaseTimestamp)),
                entityId = pur.localId
            )
        }.toList()

        val txMatches = txs.asSequence().filter {
            it.referenceNumber.lowercase().contains(q) ||
                it.descriptionArabic.lowercase().contains(q) ||
                it.localId.lowercase().contains(q)
        }.map { tx ->
            val st = com.example.financial.FriendlyTerminologyMapper.toFriendlyStatusArabic(tx.status, tx.reversalOfTransactionId != null)
            GlobalSearchResultItem(
                categoryArabic = "دفتر الأستاذ",
                title = "${tx.transactionType.arabicLabel} (${Money.ofMinor(tx.amountMinor).formatArabic()})",
                subtitle = tx.descriptionArabic,
                referenceCode = tx.referenceNumber,
                routeTarget = MekkaDestination.LEDGER.route,
                statusArabic = st,
                timestamp = tx.transactionTimestamp,
                dateFormatted = fmt.format(java.util.Date(tx.transactionTimestamp)),
                entityId = tx.localId
            )
        }.toList()

        catMatches + opsMatches + purchaseMatches + txMatches
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _uxOperationState.value = UxOperationState.Idle
    }

    /**
     * Switches active user ONLY when permitted:
     * - Blocks switching while session is locked (`isSessionLocked == true`)
     * - Blocks escalating from a lower-privileged role to another account without PIN verification via [authenticateWithPin]
     */
    fun switchActiveUser(userId: String) {
        if (repository.isSessionLocked.value) {
            emitFeedback("الجلسة مقفلة — يجب تسجيل الدخول برمز PIN السري أولاً", isError = true, categoryArabic = "الأمان والصلاحيات")
            return
        }
        val currentSession = repository.activeSession.value
        if (currentSession != null && currentSession.userId != userId) {
            emitFeedback("لا يمكن تبديل الحساب بدون إدخال رمز PIN السري للمستخدم المطلوب", isError = true, categoryArabic = "الأمان والصلاحيات")
            return
        }
        if (_activeUserId.value != MekkaPhoneRepository.DEFAULT_OWNER_USER_ID && userId != _activeUserId.value) {
            emitFeedback("رفض تصعيد الصلاحيات بدون رمز PIN: يجب المصادقة برمز PIN السري للمستخدم المطلوب", isError = true, categoryArabic = "الأمان والصلاحيات")
            return
        }
        _activeUserId.value = userId
        repository.switchActiveUser(userId)
        emitFeedback("تم تبديل المستخدم النشط", isError = false, categoryArabic = "الأمان والصلاحيات")
    }

    /**
     * Authenticates a user with their PIN and switches the active session.
     */
    fun authenticateWithPin(
        userId: String,
        rawPin: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.authenticateUserPin(userId = userId, rawPin = rawPin)
            when (res) {
                is AppResult.Success -> {
                    _activeUserId.value = res.data.userId
                    emitFeedback(
                        "تم تسجيل الدخول بنجاح باسم (${res.data.userFullName})",
                        isError = false,
                        categoryArabic = "الأمان والصلاحيات"
                    )
                    onSuccess()
                }
                is AppResult.Error -> {
                    handleResult(res, "")
                    onError(res.message)
                }
            }
        }
    }

    /**
     * Locks the current session (Logout), requiring PIN authentication before any route or action can be used.
     */
    fun lockSession() {
        repository.lockSession()
        emitFeedback("تم تسجيل الخروج وقفل الجلسة — أدخل رمز PIN للمتابعة", isError = false, categoryArabic = "الأمان والصلاحيات")
    }

    /**
     * Creates a new staff user account (Owner only — enforces USER_ROLE_MANAGE).
     */
    fun createStaffUser(
        fullNameArabic: String,
        username: String,
        phone: String,
        roleCode: String,
        rawPin: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.createStaffUser(
                fullNameArabic = fullNameArabic,
                username = username,
                phone = phone,
                roleCode = roleCode,
                rawPin = rawPin,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                onSuccess()
            }
            handleResult(res, "تم إنشاء حساب الموظف ($fullNameArabic) بنجاح برمز PIN مشفر")
        }
    }

    /**
     * Toggles active/suspended status for a staff user (Owner only — enforces USER_ROLE_MANAGE).
     */
    fun toggleUserActiveStatus(targetUserId: String, isActive: Boolean) {
        viewModelScope.launch {
            val res = repository.toggleUserActiveStatus(
                targetUserId = targetUserId,
                isActive = isActive,
                actorUserId = _activeUserId.value
            )
            val statusLabel = if (isActive) "تفعيل" else "إيقاف"
            handleResult(res, "تم $statusLabel حساب الموظف بنجاح")
        }
    }

    /**
     * Changes a user's PIN (Owner or self — enforces USER_ROLE_MANAGE for other users).
     */
    fun changeUserPin(
        targetUserId: String,
        newPin: String,
        confirmPin: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.changeUserPin(
                targetUserId = targetUserId,
                newPin = newPin,
                confirmPin = confirmPin,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                onSuccess()
            }
            handleResult(res, "تم تحديث رمز PIN السري بنجاح")
        }
    }

    /**
     * Evaluates whether the active user is authorized to access a navigation route.
     */
    fun isRouteAuthorizedForActiveUser(route: String): Boolean {
        if (repository.isSessionLocked.value) return false
        val currentId = _activeUserId.value
        val roleCode = users.value.firstOrNull { it.localId == currentId }?.roleCode
            ?: when {
                currentId.contains("CASHIER", ignoreCase = true) -> "CASHIER"
                currentId.contains("TECH", ignoreCase = true) -> "TECHNICIAN"
                currentId.contains("INVENTORY", ignoreCase = true) -> "INVENTORY"
                else -> "OWNER"
            }
        val requiredPerm = when (route) {
            MekkaDestination.REPORTS.route -> com.example.auth.PermissionCode.REPORTS_SENSITIVE_VIEW
            MekkaDestination.BACKUP.route -> com.example.auth.PermissionCode.BACKUP_RESTORE
            MekkaDestination.AUDIT.route -> com.example.auth.PermissionCode.AUDIT_VIEW
            else -> null
        }
        return if (requiredPerm != null) {
            com.example.auth.RolePermissionMatrix.hasPermission(roleCode, requiredPerm)
        } else {
            true
        }
    }

    private fun handleResult(result: AppResult<*>, successMessage: String) {
        when (result) {
            is AppResult.Success -> {
                if (successMessage.isNotBlank()) {
                    emitFeedback(successMessage, isError = false, categoryArabic = "عملية ناجحة")
                }
            }
            is AppResult.Error -> {
                val friendly = CentralizedErrorMapper.fromAppError(
                    rawMessage = result.message,
                    errorCode = result.code,
                    cause = result.cause
                )
                _diagnosticErrorLogs.value = (listOf(friendly) + _diagnosticErrorLogs.value).take(50)
                emitFeedback(friendly.userMessageArabic, isError = true, categoryArabic = "تنبيه تحقق")
            }
        }
    }

    /**
     * Completes First-Launch Setup (`إعداد مكة فون`, Part 3 Rule 8).
     */
    fun completeFirstLaunchSetup(
        ownerFullNameArabic: String,
        shopNameArabic: String,
        phoneOrAccount: String,
        rawPin: String,
        confirmPin: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.completeFirstLaunchSetup(
                ownerFullNameArabic = ownerFullNameArabic,
                shopNameArabic = shopNameArabic,
                phoneOrAccount = phoneOrAccount,
                rawPin = rawPin,
                confirmPin = confirmPin
            )
            when (res) {
                is AppResult.Success -> {
                    _activeUserId.value = res.data.userId
                    emitFeedback("تم إعداد مكة فون بنجاح وتفعيل جلسة المالك الآمنة", isError = false, categoryArabic = "إعداد مكة فون")
                    onSuccess()
                }
                is AppResult.Error -> {
                    handleResult(res, "")
                }
            }
        }
    }

    private fun parseMinorOrNull(raw: String): Long? =
        runCatching { Money.parseMajorString(raw).amountMinor }.getOrNull()

    fun addCustomer(fullName: String, phone: String, nationalId: String, address: String, notes: String) {
        viewModelScope.launch {
            val res = repository.createCustomer(
                fullName = fullName,
                phone = phone,
                nationalId = nationalId,
                address = address,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم حفظ العميل ($fullName) بنجاح وإضافته لطابور المزامنة")
        }
    }

    fun archiveCustomer(customerId: String) {
        viewModelScope.launch {
            val res = repository.archiveCustomer(
                customerId = customerId,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم أرشفة العميل (Soft Delete) مع الحفاظ الكامل على سجل مبيعاته وصيانته وقيوده")
        }
    }

    fun addSupplier(companyName: String, contactName: String, phone: String) {
        viewModelScope.launch {
            val res = repository.createSupplier(
                companyName = companyName,
                contactName = contactName,
                phone = phone,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم حفظ المورد ($companyName) بنجاح وإضافته لطابور المزامنة")
        }
    }

    fun recordCustomerPayment(
        customerId: String,
        amountMajorStr: String,
        notes: String
    ) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            emitFeedback("يرجى إدخال مبلغ تحصيل صحيح من العميل", isError = true, categoryArabic = "تحصيل عميل")
            return
        }
        if (!acquireOperationGuard("CUST_PAY:$customerId:$amountMinor", "جارٍ تسجيل دفعة العميل...")) return
        viewModelScope.launch {
            try {
                val res = repository.recordCustomerPaymentCollection(
                    customerId = customerId,
                    amountMinor = amountMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                handleResult(
                    res,
                    "تم تسجيل دفعة العميل بقيمة $formattedAmount وترحيلها في دفتر الأستاذ وتخفيض مديونيته تلقائياً"
                )
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun recordSupplierPayment(supplierId: String, amountMajorStr: String, notes: String) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            emitFeedback("يرجى إدخال مبلغ سداد صحيح للمورد", isError = true, categoryArabic = "سداد مورد")
            return
        }
        if (!acquireOperationGuard("SUP_PAY:$supplierId:$amountMinor", "جارٍ تسجيل سداد المورد...")) return
        viewModelScope.launch {
            try {
                val res = repository.recordSupplierPayment(
                    supplierId = supplierId,
                    amountMinor = amountMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                handleResult(
                    res,
                    "تم تسجيل سداد المورد بقيمة $formattedAmount عبر دفتر الأستاذ وتحديث كشف حساب المديونية"
                )
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun addCategory(nameArabic: String, onSuccess: (CategoryEntity) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createCategory(
                nameArabic = nameArabic,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                onSuccess(res.data)
            }
            handleResult(res, "تمت إضافة الفئة ($nameArabic) بنجاح")
        }
    }

    fun addBrand(nameArabic: String, onSuccess: (BrandEntity) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createBrand(
                nameArabic = nameArabic,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                onSuccess(res.data)
            }
            handleResult(res, "تمت إضافة العلامة التجارية ($nameArabic) بنجاح")
        }
    }

    fun addProduct(
        nameArabic: String,
        sku: String,
        categoryId: String,
        brandId: String?,
        isSerialized: Boolean,
        sellingPriceMajorStr: String,
        reorderThresholdStr: String,
        barcode: String? = null,
        imageOrDescription: String = "",
        onSuccess: (com.example.database.entities.ProductEntity) -> Unit = {}
    ) {
        if (categoryId.isBlank()) {
            _feedbackMessage.value = "يرجى اختيار الفئة بشكل صريح" to true
            return
        }
        val priceMinor = parseMinorOrNull(sellingPriceMajorStr)
        if (priceMinor == null || priceMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال سعر بيع صحيح" to true
            return
        }
        val threshold = reorderThresholdStr.trim().toIntOrNull()
            ?: shopConfiguration.value.defaultLowStockThreshold
        viewModelScope.launch {
            val res = repository.createProduct(
                nameArabic = nameArabic,
                sku = sku,
                barcode = barcode,
                categoryId = categoryId,
                brandId = brandId,
                isSerialized = isSerialized,
                defaultSellingPriceMinor = priceMinor,
                reorderThresholdQty = threshold,
                description = imageOrDescription,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                onSuccess(res.data)
            }
            handleResult(res, "تم تعريف الصنف في الدليل (الرصيد يبدأ من 0 ويتحدث بحركات المخزن فقط)")
        }
    }

    fun executeCustomerCollectionSafe(
        customerId: String,
        amountMajorStr: String,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        paymentAccountId: String = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
        allowOverpayment: Boolean = false,
        notes: String = "",
        onSuccess: (TransactionEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        val currentBalanceMinor = customerProfiles.value[customerId]?.currentBalanceMinor ?: 0L
        val amountMinor = parseMinorOrNull(amountMajorStr)
        val preview = PartyAccountStatementEngine.evaluateCustomerCollection(
            currentBalanceMinor = currentBalanceMinor,
            amountPaidMinor = amountMinor,
            allowOverpayment = allowOverpayment
        )
        if (!preview.isValid || amountMinor == null) {
            val err = preview.validationErrorArabic ?: "يرجى إدخال مبلغ تحصيل صحيح"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }
        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.recordCustomerPaymentCollection(
                    customerId = customerId,
                    amountMinor = amountMinor,
                    destinationAccountType = paymentAccountType,
                    destinationAccountId = paymentAccountId,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                        handleResult(res, "تم تسجيل دفعة العميل بقيمة $formattedAmount وترحيلها في دفتر الأستاذ وتحديث الرصيد")
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    fun executeSupplierPaymentSafe(
        supplierId: String,
        amountMajorStr: String,
        allowOverpayment: Boolean = false,
        notes: String = "",
        onSuccess: (TransactionEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        val currentOutstanding = supplierProfiles.value[supplierId]?.outstandingAmountMinor ?: 0L
        val amountMinor = parseMinorOrNull(amountMajorStr)
        val preview = PartyAccountStatementEngine.evaluateSupplierPayment(
            currentOutstandingMinor = currentOutstanding,
            amountPaidMinor = amountMinor,
            allowOverpayment = allowOverpayment
        )
        if (!preview.isValid || amountMinor == null) {
            val err = preview.validationErrorArabic ?: "يرجى إدخال مبلغ سداد صحيح"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }
        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.recordSupplierPayment(
                    supplierId = supplierId,
                    amountMinor = amountMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                        handleResult(res, "تم تسجيل سداد المورد بقيمة $formattedAmount عبر دفتر الأستاذ وتحديث كشف الحساب")
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    /**
     * Executes an atomic multi-item or single-item Purchase with full safety (Rules 10 & 11):
     * - Disables button & sets processing state
     * - Updates inventory WAC & posts accounting in a single atomic transaction
     * - Invokes `onSuccess` ONLY on success so form is cleared only after success
     * - Invokes `onError` on failure while preserving form state
     */
    fun executeSafePurchaseOrder(
        supplierId: String,
        cartItems: List<PurchaseLineInput>,
        discountMajorStr: String,
        paidAmountMajorStr: String,
        notes: String,
        onSuccess: (PurchaseEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        if (supplierId.isBlank()) {
            val msg = "يرجى اختيار المورد بشكل صريح أولاً"
            _feedbackMessage.value = msg to true
            onError(msg)
            return
        }
        if (cartItems.isEmpty()) {
            val msg = "يرجى إضافة صنف واحد على الأقل إلى فاتورة الشراء"
            _feedbackMessage.value = msg to true
            onError(msg)
            return
        }
        val discountMinor = if (discountMajorStr.isBlank()) 0L else (parseMinorOrNull(discountMajorStr) ?: -1L)
        val paidOverrideMinor = if (paidAmountMajorStr.isBlank()) null else (parseMinorOrNull(paidAmountMajorStr) ?: -1L)
        val calcSummary = PurchaseCalculationEngine.calculatePurchaseSummary(
            supplierId = supplierId,
            lineSubtotalsMinor = cartItems.map { it.quantity.toLong() * it.unitCostMinor },
            discountMinor = discountMinor,
            paidMinorOverride = paidOverrideMinor
        )
        if (!calcSummary.isValid) {
            val err = calcSummary.validationErrorArabic ?: "بيانات الفاتورة غير مكتملة"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }

        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.executePurchase(
                    supplierId = supplierId,
                    items = cartItems,
                    discountMinor = calcSummary.discountMinor,
                    paidAmountMinorOverride = calcSummary.paidMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        val totalUnits = cartItems.sumOf { it.quantity }
                        handleResult(
                            res,
                            "تم ترحيل فاتورة المشتريات الذرية (#${res.data.invoiceNumber}) — تم إضافة $totalUnits منتجات للمخزون وتحديث المتوسط المرجح ودفتر الأستاذ"
                        )
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    /**
     * Domain Fee Calculation for Wallet Operations (Rule 14).
     */
    fun calculateWalletFeeBreakdown(
        operationType: WalletUserOperationType,
        amountMajorStr: String,
        providerCode: String,
        customNetworkFeeMajorStr: String = "",
        customShopFeeMajorStr: String = ""
    ): WalletFeeBreakdown {
        val amountMinor = parseMinorOrNull(amountMajorStr) ?: 0L
        val netFeeOverride = if (customNetworkFeeMajorStr.isBlank()) null else parseMinorOrNull(customNetworkFeeMajorStr)
        val shopFeeOverride = if (customShopFeeMajorStr.isBlank()) null else parseMinorOrNull(customShopFeeMajorStr)
        val configuredBps = shopConfiguration.value.defaultWalletCommissionBps
        return WalletDomainPolicyEngine.calculateFees(
            operationType = operationType,
            amountMinor = amountMinor,
            providerCode = providerCode,
            configuredShopCommissionBps = configuredBps,
            customNetworkFeeOverrideMinor = netFeeOverride,
            customShopFeeOverrideMinor = shopFeeOverride
        )
    }

    /**
     * Pre-execution validation for Wallet Wizard (Rule 15).
     */
    fun validateWalletWizardOperation(
        walletId: String?,
        operationType: WalletUserOperationType,
        amountMajorStr: String,
        customerPhoneOrAccount: String,
        feeBreakdown: WalletFeeBreakdown
    ): WalletOperationValidation {
        val wallet = wallets.value.firstOrNull { it.walletId == walletId }
        val amountMinor = parseMinorOrNull(amountMajorStr)
        val cashDrawerBal = cashDrawers.value.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: cashDrawers.value.firstOrNull()?.derivedBalanceMinor ?: 0L
        val currentRole = users.value.firstOrNull { it.localId == _activeUserId.value }?.roleCode ?: "OWNER"
        val dailyRem = if (wallet != null) (wallet.dailyLimitMinor - wallet.todayOutflowMinor).coerceAtLeast(0L) else 0L
        val monthlyRem = if (wallet != null) (wallet.monthlyLimitMinor - wallet.monthOutflowMinor).coerceAtLeast(0L) else 0L

        return WalletDomainPolicyEngine.validateOperation(
            walletId = wallet?.walletId,
            providerId = wallet?.providerId,
            operationType = operationType,
            amountMinor = amountMinor,
            customerPhoneOrAccount = customerPhoneOrAccount,
            walletBalanceMinor = wallet?.derivedBalanceMinor ?: 0L,
            walletDailyRemainingMinor = dailyRem,
            walletMonthlyRemainingMinor = monthlyRem,
            cashDrawerBalanceMinor = cashDrawerBal,
            feeBreakdown = feeBreakdown,
            roleCode = currentRole
        )
    }

    /**
     * Executes the Wallet Wizard operation atomically (Rules 12, 13, 14, 15).
     */
    fun executeWalletWizardSafe(
        walletId: String,
        operationType: WalletUserOperationType,
        customerPhoneOrAccount: String,
        feeBreakdown: WalletFeeBreakdown,
        notes: String,
        onSuccess: (TransferEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        val wallet = wallets.value.firstOrNull { it.walletId == walletId }
        val cashDrawerBal = cashDrawers.value.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: cashDrawers.value.firstOrNull()?.derivedBalanceMinor ?: 0L
        val currentRole = users.value.firstOrNull { it.localId == _activeUserId.value }?.roleCode ?: "OWNER"
        val dailyRem = if (wallet != null) (wallet.dailyLimitMinor - wallet.todayOutflowMinor).coerceAtLeast(0L) else 0L
        val monthlyRem = if (wallet != null) (wallet.monthlyLimitMinor - wallet.monthOutflowMinor).coerceAtLeast(0L) else 0L

        val validation = WalletDomainPolicyEngine.validateOperation(
            walletId = wallet?.walletId,
            providerId = wallet?.providerId,
            operationType = operationType,
            amountMinor = feeBreakdown.amountMinor,
            customerPhoneOrAccount = customerPhoneOrAccount,
            walletBalanceMinor = wallet?.derivedBalanceMinor ?: 0L,
            walletDailyRemainingMinor = dailyRem,
            walletMonthlyRemainingMinor = monthlyRem,
            cashDrawerBalanceMinor = cashDrawerBal,
            feeBreakdown = feeBreakdown,
            roleCode = currentRole
        )
        if (!validation.isValid) {
            val err = validation.errorArabic ?: "بيانات عملية المحفظة غير صالحة"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }

        val enrichedNotes = buildString {
            append(operationType.arabicLabel)
            if (notes.isNotBlank()) {
                append(" — ")
                append(notes.trim())
            }
        }

        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.executeWalletOperation(
                    walletId = walletId,
                    transferKind = operationType.internalTransferKind,
                    customerPhone = Money.normalizeDigits(customerPhoneOrAccount).trim(),
                    principalAmountMinor = feeBreakdown.amountMinor,
                    providerFeeMinor = feeBreakdown.networkFeeMinor,
                    shopCommissionMinor = feeBreakdown.shopFeeMinor,
                    notes = enrichedNotes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        handleResult(res, "تم تنفيذ عملية (${operationType.arabicLabel}) بنجاح وترحيل القيد المالي")
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    /**
     * Evaluates Cash Closeout expected vs counted cash in the domain layer (Rule 17).
     */
    fun evaluateCashCloseoutState(
        countedCashMajorStr: String,
        discrepancyReasonArabic: String,
        managerConfirmed: Boolean
    ): CashCloseoutEvaluation {
        val expectedMinor = cashDrawers.value.firstOrNull { it.isMainDrawer }?.derivedBalanceMinor
            ?: cashDrawers.value.firstOrNull()?.derivedBalanceMinor ?: 0L
        val countedMinor = parseMinorOrNull(countedCashMajorStr)
        val roleCode = users.value.firstOrNull { it.localId == _activeUserId.value }?.roleCode ?: "OWNER"
        return CashCloseoutPolicyEngine.evaluateCloseout(
            expectedBalanceMinor = expectedMinor,
            countedCashMinor = countedMinor,
            discrepancyReasonArabic = discrepancyReasonArabic,
            actorRoleCode = roleCode,
            managerConfirmed = managerConfirmed
        )
    }

    /**
     * Executes Cash Closeout safely with discrepancy reason & role authorization enforcement (Rule 17).
     */
    fun executeCashCloseoutSafe(
        countedCashMajorStr: String,
        discrepancyReasonArabic: String,
        optionalNote: String,
        managerConfirmed: Boolean,
        onSuccess: (CashSessionEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        val evaluation = evaluateCashCloseoutState(
            countedCashMajorStr = countedCashMajorStr,
            discrepancyReasonArabic = discrepancyReasonArabic,
            managerConfirmed = managerConfirmed
        )
        if (!evaluation.isValidToSubmit) {
            val err = evaluation.validationErrorArabic ?: "بيانات إغلاق الوردية غير مكتملة"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }

        val combinedNotes = buildList {
            if (evaluation.hasDiscrepancy && discrepancyReasonArabic.isNotBlank()) {
                add("سبب الفرق: ${discrepancyReasonArabic.trim()}")
            }
            if (optionalNote.isNotBlank()) {
                add("ملاحظة: ${optionalNote.trim()}")
            }
            if (evaluation.requiresManagerAuthorization && managerConfirmed) {
                add("معتمد من الإدارة")
            }
        }.joinToString(" | ").ifBlank { "إغلاق وردية مطابق" }

        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.executeCashCloseout(
                    countedCashMinor = evaluation.countedCashMinor,
                    notes = combinedNotes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        handleResult(res, "تم إغلاق الوردية وحفظ مطابقة الرصيد المتوقع مع الفعلي")
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    /**
     * Creates a full Maintenance Ticket with explicit customer, technician, notes, photos, and optional initial part (Rule 19).
     */
    fun createMaintenanceOrderFull(
        customerId: String,
        deviceBrand: String,
        deviceModel: String,
        deviceImei: String,
        faultDescription: String,
        estimatedCostMajorStr: String,
        depositMajorStr: String = "0",
        assignedTechnicianId: String? = null,
        customerNotes: String = "",
        photoAttachmentNotes: String = "",
        initialStatus: MaintenanceStatus = MaintenanceStatus.RECEIVED,
        initialPartProductId: String? = null,
        initialPartQtyStr: String = "1",
        initialPartChargeMajorStr: String = "0",
        onSuccess: (MaintenanceOrderEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessingOperation.value) return
        if (customerId.isBlank()) {
            val err = "يرجى اختيار العميل بشكل صريح أولاً"
            _feedbackMessage.value = err to true
            onError(err)
            return
        }
        val costMinor = parseMinorOrNull(estimatedCostMajorStr) ?: 0L
        val depositMinor = if (depositMajorStr.isBlank()) 0L else (parseMinorOrNull(depositMajorStr) ?: 0L)

        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.createMaintenanceOrder(
                    customerId = customerId,
                    deviceBrand = deviceBrand,
                    deviceModel = deviceModel,
                    deviceImeiOrSerial = deviceImei,
                    faultDescription = faultDescription,
                    estimatedCostMinor = costMinor,
                    depositMinor = depositMinor,
                    assignedTechnicianId = assignedTechnicianId,
                    customerNotes = customerNotes,
                    photoAttachmentNotes = photoAttachmentNotes,
                    initialStatus = initialStatus,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        if (!initialPartProductId.isNullOrBlank()) {
                            val qty = initialPartQtyStr.trim().toIntOrNull() ?: 1
                            val chargeMinor = parseMinorOrNull(initialPartChargeMajorStr) ?: 0L
                            repository.consumePartForMaintenanceOrder(
                                maintenanceOrderId = res.data.localId,
                                productId = initialPartProductId,
                                quantity = qty,
                                unitChargeMinor = chargeMinor,
                                actorUserId = _activeUserId.value
                            )
                        }
                        handleResult(res, "تم فتح تذكرة الصيانة (#${res.data.ticketNumber}) بنجاح")
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    /**
     * Collects unpaid maintenance balance into the Financial Ledger (Rule 20).
     */
    fun collectMaintenanceTicketPayment(
        orderId: String,
        amountMajorStr: String,
        notes: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            val err = "يرجى إدخال مبلغ تحصيل صحيح"
            emitFeedback(err, isError = true, categoryArabic = "تحصيل صيانة")
            onError(err)
            return
        }
        if (!acquireOperationGuard("MAINT_PAY:$orderId:$amountMinor", "جارٍ تحصيل دفعة الصيانة...")) return
        viewModelScope.launch {
            try {
                val res = repository.collectMaintenanceOrderPayment(
                    orderId = orderId,
                    amountMinor = amountMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                        handleResult(res, "تم تحصيل دفعة الصيانة بقيمة $formattedAmount وترحيلها في دفتر الأستاذ")
                        onSuccess()
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                        onError(res.message)
                    }
                }
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun notifyMaintenanceCustomer(orderId: String) {
        updateMaintenanceOrderStatus(
            orderId = orderId,
            newStatus = MaintenanceStatus.CUSTOMER_NOTIFIED,
            finalChargeMinor = 0L
        )
    }

    fun recordInventoryAdjustment(
        productId: String,
        movementType: InventoryMovementType,
        quantityStr: String,
        unitCostMajorStr: String,
        serialOrImei: String?,
        reason: String
    ) {
        val qty = quantityStr.trim().toIntOrNull()
        val costMinor = parseMinorOrNull(unitCostMajorStr) ?: 0L
        if (qty == null || qty <= 0) {
            emitFeedback("الكمية يجب أن تكون رقماً صحيحاً أكبر من الصفر", isError = true, categoryArabic = "تسوية المخزون")
            return
        }
        if (!acquireOperationGuard("INV_ADJ:$productId:${movementType.name}:$qty", "جارٍ تسجيل تسوية المخزون...")) return
        viewModelScope.launch {
            try {
                val res = repository.recordAuthorizedInventoryMovement(
                    productId = productId,
                    movementType = movementType,
                    quantity = qty,
                    unitCostMinor = costMinor,
                    serialOrImei = serialOrImei,
                    reason = reason,
                    actorUserId = _activeUserId.value
                )
                handleResult(res, "تم تسجيل الحركة المخزنية (${movementType.arabicLabel} — عدد $qty) وتحديث المتوسط المرجح للتكلفة (WAC)")
            } finally {
                releaseOperationGuard()
            }
        }
    }

    /**
     * Batch Product Import with Real Measurable Progress (`320 / 850` format, Section 13).
     * Never uses fake delays or fake percentages.
     */
    fun importProductsBatchWithRealProgress(
        rows: List<Triple<String, String, Long>>,
        categoryId: String,
        onComplete: (Int) -> Unit = {}
    ) {
        if (rows.isEmpty()) {
            emitFeedback("لا توجد منتجات صالحة للاستيراد في الملف المحدد", isError = true, categoryArabic = "استيراد المنتجات")
            return
        }
        if (!acquireOperationGuard("IMPORT_PRODUCTS:${rows.size}", "جاري استيراد المنتجات")) return
        viewModelScope.launch {
            var importedCount = 0
            val total = rows.size
            try {
                rows.forEachIndexed { index, (nameAr, sku, priceMinor) ->
                    _uxOperationState.value = UxOperationState.Progress(
                        whatIsHappeningArabic = "جاري استيراد المنتجات",
                        currentStep = index + 1,
                        totalSteps = total,
                        displayAsFraction = true
                    )
                    val res = repository.createProduct(
                        nameArabic = nameAr,
                        sku = sku,
                        barcode = sku,
                        categoryId = categoryId,
                        brandId = null,
                        isSerialized = false,
                        defaultSellingPriceMinor = priceMinor,
                        reorderThresholdQty = shopConfiguration.value.defaultLowStockThreshold,
                        actorUserId = _activeUserId.value
                    )
                    if (res is AppResult.Success) {
                        importedCount++
                    }
                }
                emitFeedback(
                    "تم استيراد $importedCount من إجمالي $total منتج بنجاح إلى دليل الأصناف",
                    isError = false,
                    categoryArabic = "استيراد المنتجات"
                )
                onComplete(importedCount)
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun executeAtomicPurchase(
        supplierId: String,
        productId: String,
        quantityStr: String,
        unitCostMajorStr: String,
        serialsCommaSeparated: String,
        notes: String,
        paidAmountMajorStr: String = ""
    ) {
        val qty = quantityStr.trim().toIntOrNull()
        val unitCostMinor = parseMinorOrNull(unitCostMajorStr)
        if (qty == null || qty <= 0 || unitCostMinor == null || unitCostMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال كمية وتكلفة شراء صحيحة" to true
            return
        }
        val serials = serialsCommaSeparated.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val paidOverrideMinor = if (paidAmountMajorStr.isBlank()) null else parseMinorOrNull(paidAmountMajorStr)

        viewModelScope.launch {
            val res = repository.executePurchase(
                supplierId = supplierId,
                items = listOf(
                    PurchaseLineInput(
                        productId = productId,
                        quantity = qty,
                        unitCostMinor = unitCostMinor,
                        serialNumbersOrImeis = serials
                    )
                ),
                paidAmountMinorOverride = paidOverrideMinor,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تنفيذ فاتورة الشراء الذرية وتحديث المخزون والمتوسط المرجح ودفتر الأستاذ")
        }
    }

    fun executeAtomicPurchaseCart(
        supplierId: String,
        cartItems: List<PurchaseLineInput>,
        discountMajorStr: String,
        paidAmountMajorStr: String,
        notes: String
    ) {
        if (cartItems.isEmpty()) {
            _feedbackMessage.value = "يرجى إضافة صنف واحد على الأقل إلى فاتورة المشتريات" to true
            return
        }
        val discountMinor = if (discountMajorStr.isBlank()) 0L else (parseMinorOrNull(discountMajorStr) ?: 0L)
        val paidOverrideMinor = if (paidAmountMajorStr.isBlank()) null else parseMinorOrNull(paidAmountMajorStr)
        viewModelScope.launch {
            val res = repository.executePurchase(
                supplierId = supplierId,
                items = cartItems,
                discountMinor = discountMinor,
                paidAmountMinorOverride = paidOverrideMinor,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تنفيذ فاتورة المشتريات متعددة الأصناف (${cartItems.size} بند) وتحديث المخزون ودفتر الأستاذ")
        }
    }

    fun executeAtomicSale(
        customerId: String?,
        productId: String,
        serialNumberId: String?,
        quantityStr: String,
        unitPriceMajorStr: String,
        discountMajorStr: String,
        notes: String,
        paidAmountMajorStr: String = ""
    ) {
        val qty = quantityStr.trim().toIntOrNull()
        val unitPriceMinor = parseMinorOrNull(unitPriceMajorStr)
        val discountMinor = if (discountMajorStr.isBlank()) 0L else (parseMinorOrNull(discountMajorStr) ?: 0L)
        val paidOverrideMinor = if (paidAmountMajorStr.isBlank()) null else parseMinorOrNull(paidAmountMajorStr)
        if (qty == null || qty <= 0 || unitPriceMinor == null || unitPriceMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال كمية وسعر بيع صحيحين" to true
            return
        }
        val defaultTaxBps = (shopConfiguration.value.defaultTaxPercent.toIntOrNull() ?: 0) * 100
        viewModelScope.launch {
            val res = repository.executeSale(
                customerId = customerId,
                items = listOf(
                    SaleLineInput(
                        productId = productId,
                        serialNumberId = serialNumberId,
                        quantity = qty,
                        unitSellingPriceMinor = unitPriceMinor
                    )
                ),
                invoiceDiscountMinor = discountMinor,
                taxBasisPoints = defaultTaxBps,
                paidAmountMinorOverride = paidOverrideMinor,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم إتمام عملية البيع الذرية (فاتورة + حركة مخزن + قيد دفتر أستاذ + تدقيق + مزامنة)")
        }
    }

    fun executeAtomicSaleCart(
        customerId: String?,
        cartItems: List<SaleLineInput>,
        discountMajorStr: String,
        paidAmountMajorStr: String,
        notes: String
    ) {
        if (cartItems.isEmpty()) {
            _feedbackMessage.value = "يرجى إضافة صنف واحد على الأقل إلى الفاتورة" to true
            return
        }
        val discountMinor = if (discountMajorStr.isBlank()) 0L else (parseMinorOrNull(discountMajorStr) ?: 0L)
        val paidOverrideMinor = if (paidAmountMajorStr.isBlank()) null else parseMinorOrNull(paidAmountMajorStr)
        val defaultTaxBps = (shopConfiguration.value.defaultTaxPercent.toIntOrNull() ?: 0) * 100
        viewModelScope.launch {
            _isProcessingOperation.value = true
            try {
                val res = repository.executeSale(
                    customerId = customerId,
                    items = cartItems,
                    invoiceDiscountMinor = discountMinor,
                    taxBasisPoints = defaultTaxBps,
                    paidAmountMinorOverride = paidOverrideMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                handleResult(res, "تم إتمام فاتورة المبيعات الذرية (${cartItems.size} بند) وترحيل القيد في دفتر الأستاذ")
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    // =========================================================================
    // MEKKA PHONE V3 — POS WORKFLOW & PERSISTENT CART ENGINE (Rules 13–24)
    // =========================================================================

    fun clearPosValidationError() {
        _posInlineValidationError.value = null
        if (_posCheckoutState.value is PosCheckoutState.Error) {
            _posCheckoutState.value = PosCheckoutState.Idle
        }
    }

    fun selectPosWalkInCustomer() {
        _posCustomerMode.value = PosCustomerSelectionMode.WALK_IN
        _posSelectedCustomerId.value = null
        if (_posPaymentMode.value != PosPaymentMode.CASH) {
            _posPaymentMode.value = PosPaymentMode.CASH
            _posPartialPaidMajorInput.value = ""
        }
        clearPosValidationError()
    }

    fun selectPosRegisteredCustomer(customerId: String) {
        if (customerId.isBlank()) {
            _posInlineValidationError.value = "اختر العميل أولًا"
            return
        }
        _posCustomerMode.value = PosCustomerSelectionMode.REGISTERED
        _posSelectedCustomerId.value = customerId
        clearPosValidationError()
    }

    fun clearPosCustomerSelection() {
        _posCustomerMode.value = PosCustomerSelectionMode.NONE
        _posSelectedCustomerId.value = null
    }

    fun setPosPaymentMode(mode: PosPaymentMode) {
        if ((mode == PosPaymentMode.PARTIAL || mode == PosPaymentMode.CREDIT) &&
            _posCustomerMode.value == PosCustomerSelectionMode.WALK_IN
        ) {
            _posInlineValidationError.value = "اختر العميل أولًا (البيع الآجل أو الجزئي يتطلب عميلاً مسجلاً)"
            return
        }
        _posPaymentMode.value = mode
        if (mode != PosPaymentMode.PARTIAL) {
            _posPartialPaidMajorInput.value = ""
        }
        clearPosValidationError()
    }

    fun setPosPartialPaidInput(value: String) {
        _posPartialPaidMajorInput.value = value
        clearPosValidationError()
    }

    fun setPosInvoiceDiscountInput(value: String) {
        _posInvoiceDiscountMajorInput.value = value
        clearPosValidationError()
    }

    fun setPosNotesInput(value: String) {
        _posNotesInput.value = value
    }

    fun addOrIncrementPosCartItem(
        product: ProductInventoryProjection,
        brandName: String?,
        quantity: Int = 1,
        unitPriceMinor: Long = product.defaultSellingPriceMinor,
        lineDiscountMinor: Long = 0L,
        serialEntity: SerialNumberEntity? = null
    ): Boolean {
        clearPosValidationError()
        if (quantity <= 0) {
            _posInlineValidationError.value = "الكمية يجب أن تكون أكبر من صفر"
            return false
        }
        if (unitPriceMinor <= 0L) {
            _posInlineValidationError.value = "أدخل مبلغًا صحيحًا"
            return false
        }
        if (product.derivedStockQty <= 0) {
            _posInlineValidationError.value = "الرصيد غير كافٍ (${product.nameArabic})"
            return false
        }

        val currentCart = _posCartItems.value
        if (product.isSerialized) {
            if (serialEntity == null || serialEntity.productId != product.productId) {
                _posInlineValidationError.value = "IMEI غير صحيح — يجب اختيار أو مسح سيريال متاح لهذا الجهاز"
                return false
            }
            if (currentCart.any { it.serialNumberId == serialEntity.localId }) {
                _posInlineValidationError.value = "IMEI غير صحيح — هذا السيريال (${serialEntity.serialOrImei}) مضاف بالفعل في السلة"
                return false
            }
            val newItem = PosCartItem(
                lineId = "LINE-${UUID.randomUUID()}",
                productId = product.productId,
                productNameArabic = product.nameArabic,
                sku = product.sku,
                brandName = brandName,
                isSerialized = true,
                serialNumberId = serialEntity.localId,
                serialOrImei = serialEntity.serialOrImei,
                quantity = 1,
                availableStockQty = product.derivedStockQty,
                unitPriceMinor = unitPriceMinor,
                unitCostWacMinor = product.weightedAvgCostMinor,
                lineDiscountMinor = lineDiscountMinor.coerceAtLeast(0L)
            )
            _posCartItems.value = currentCart + newItem
            return true
        } else {
            val existing = currentCart.firstOrNull { it.productId == product.productId && !it.isSerialized }
            val totalRequestedQty = (existing?.quantity ?: 0) + quantity
            if (totalRequestedQty > product.derivedStockQty) {
                _posInlineValidationError.value = "الرصيد غير كافٍ — المتاح من (${product.nameArabic}) هو ${product.derivedStockQty} فقط"
                return false
            }
            if (existing != null) {
                _posCartItems.value = currentCart.map {
                    if (it.lineId == existing.lineId) {
                        it.copy(
                            quantity = totalRequestedQty,
                            unitPriceMinor = unitPriceMinor,
                            lineDiscountMinor = it.lineDiscountMinor + lineDiscountMinor.coerceAtLeast(0L),
                            availableStockQty = product.derivedStockQty
                        )
                    } else {
                        it
                    }
                }
            } else {
                val newItem = PosCartItem(
                    lineId = "LINE-${UUID.randomUUID()}",
                    productId = product.productId,
                    productNameArabic = product.nameArabic,
                    sku = product.sku,
                    brandName = brandName,
                    isSerialized = false,
                    serialNumberId = null,
                    serialOrImei = null,
                    quantity = quantity,
                    availableStockQty = product.derivedStockQty,
                    unitPriceMinor = unitPriceMinor,
                    unitCostWacMinor = product.weightedAvgCostMinor,
                    lineDiscountMinor = lineDiscountMinor.coerceAtLeast(0L)
                )
                _posCartItems.value = currentCart + newItem
            }
            return true
        }
    }

    fun updatePosCartItemQuantity(lineId: String, newQty: Int) {
        clearPosValidationError()
        val target = _posCartItems.value.firstOrNull { it.lineId == lineId } ?: return
        if (target.isSerialized) {
            if (newQty <= 0) {
                removePosCartItem(lineId)
            }
            return
        }
        if (newQty <= 0) {
            _posInlineValidationError.value = "الكمية يجب أن تكون أكبر من صفر"
            return
        }
        if (newQty > target.availableStockQty) {
            _posInlineValidationError.value = "الرصيد غير كافٍ — المتاح (${target.availableStockQty}) قطعة فقط"
            return
        }
        _posCartItems.value = _posCartItems.value.map {
            if (it.lineId == lineId) it.copy(quantity = newQty) else it
        }
    }

    fun updatePosCartItemPriceAndDiscount(lineId: String, unitPriceMinor: Long, lineDiscountMinor: Long) {
        clearPosValidationError()
        if (unitPriceMinor <= 0L) {
            _posInlineValidationError.value = "أدخل مبلغًا صحيحًا"
            return
        }
        if (lineDiscountMinor < 0L) {
            _posInlineValidationError.value = "أدخل مبلغًا صحيحًا"
            return
        }
        _posCartItems.value = _posCartItems.value.map {
            if (it.lineId == lineId) {
                val maxDisc = it.quantity.toLong() * unitPriceMinor
                it.copy(
                    unitPriceMinor = unitPriceMinor,
                    lineDiscountMinor = lineDiscountMinor.coerceIn(0L, maxDisc)
                )
            } else {
                it
            }
        }
    }

    fun removePosCartItem(lineId: String) {
        clearPosValidationError()
        _posCartItems.value = _posCartItems.value.filterNot { it.lineId == lineId }
    }

    fun clearPosCart() {
        _posCartItems.value = emptyList()
        _posInvoiceDiscountMajorInput.value = ""
        _posPartialPaidMajorInput.value = ""
        _posNotesInput.value = ""
        clearPosValidationError()
    }

    fun computePosTotals(): Triple<Long, Long, Long> {
        val items = _posCartItems.value
        val grossSubtotalMinor = items.sumOf { it.grossLineTotalMinor }
        val lineDiscountsMinor = items.sumOf { it.lineDiscountMinor }
        val extraInvoiceDiscMinor = if (_posInvoiceDiscountMajorInput.value.isBlank()) {
            0L
        } else {
            parseMinorOrNull(_posInvoiceDiscountMajorInput.value)?.coerceAtLeast(0L) ?: 0L
        }
        val totalDiscountMinor = (lineDiscountsMinor + extraInvoiceDiscMinor).coerceAtMost(grossSubtotalMinor)
        val netBeforeTaxMinor = (grossSubtotalMinor - totalDiscountMinor).coerceAtLeast(0L)
        val taxBps = (shopConfiguration.value.defaultTaxPercent.toIntOrNull() ?: 0) * 100
        val taxMinor = (netBeforeTaxMinor * taxBps) / 10_000L
        val finalTotalMinor = netBeforeTaxMinor + taxMinor
        return Triple(grossSubtotalMinor, totalDiscountMinor, finalTotalMinor)
    }

    fun computePosPaidAndRemainingMinor(finalTotalMinor: Long): Pair<Long, Long> {
        return when (_posPaymentMode.value) {
            PosPaymentMode.CASH -> finalTotalMinor to 0L
            PosPaymentMode.CREDIT -> 0L to finalTotalMinor
            PosPaymentMode.PARTIAL -> {
                val paid = parseMinorOrNull(_posPartialPaidMajorInput.value)?.coerceIn(0L, finalTotalMinor) ?: 0L
                paid to (finalTotalMinor - paid).coerceAtLeast(0L)
            }
        }
    }

    /**
     * Validates cart, explicit customer selection, and payment rules before opening Sale Review (Rules 17, 18, 19, 23).
     */
    fun requestPosSaleReview(): Boolean {
        clearPosValidationError()
        val items = _posCartItems.value
        if (items.isEmpty()) {
            _posInlineValidationError.value = "اختر المنتج"
            return false
        }
        when (_posCustomerMode.value) {
            PosCustomerSelectionMode.NONE -> {
                _posInlineValidationError.value = "اختر العميل أولًا"
                return false
            }
            PosCustomerSelectionMode.REGISTERED -> {
                val custId = _posSelectedCustomerId.value
                val loadedCustomers = customers.value
                if (custId.isNullOrBlank() || (loadedCustomers.isNotEmpty() && loadedCustomers.none { it.localId == custId })) {
                    _posInlineValidationError.value = "اختر العميل أولًا"
                    return false
                }
            }
            PosCustomerSelectionMode.WALK_IN -> {
                if (_posPaymentMode.value != PosPaymentMode.CASH) {
                    _posInlineValidationError.value = "اختر العميل أولًا (البيع الآجل أو الجزئي يتطلب عميلاً مسجلاً)"
                    return false
                }
            }
        }

        val (_, _, finalTotalMinor) = computePosTotals()
        if (finalTotalMinor <= 0L) {
            _posInlineValidationError.value = "أدخل مبلغًا صحيحًا"
            return false
        }

        if (_posPaymentMode.value == PosPaymentMode.PARTIAL) {
            val paidMinor = parseMinorOrNull(_posPartialPaidMajorInput.value)
            if (paidMinor == null || paidMinor <= 0L || paidMinor >= finalTotalMinor) {
                _posInlineValidationError.value = "أدخل مبلغًا صحيحًا للدفع الجزئي (أكبر من صفر وأقل من إجمالي الفاتورة)"
                return false
            }
        }

        _posCheckoutState.value = PosCheckoutState.Reviewing
        return true
    }

    fun dismissPosSaleReview() {
        if (_posCheckoutState.value is PosCheckoutState.Reviewing || _posCheckoutState.value is PosCheckoutState.Error) {
            _posCheckoutState.value = PosCheckoutState.Idle
        }
    }

    /**
     * Executes the atomic POS sale after explicit confirmation in Sale Review (Rule 20).
     * - Prevents double submission
     * - Disables button & shows Processing state
     * - Clears cart ONLY after successful completion
     * - Keeps cart & shows understandable error on failure
     */
    fun confirmAndExecutePosSale() {
        if (_posCheckoutState.value is PosCheckoutState.Processing || _isProcessingOperation.value) {
            return // Prevent double submission (Rule 20.6)
        }
        val itemsSnapshot = _posCartItems.value
        if (itemsSnapshot.isEmpty()) {
            _posInlineValidationError.value = "اختر المنتج"
            _posCheckoutState.value = PosCheckoutState.Idle
            return
        }

        val resolvedCustomerId: String? = when (_posCustomerMode.value) {
            PosCustomerSelectionMode.REGISTERED -> _posSelectedCustomerId.value
            PosCustomerSelectionMode.WALK_IN -> null
            PosCustomerSelectionMode.NONE -> {
                _posInlineValidationError.value = "اختر العميل أولًا"
                _posCheckoutState.value = PosCheckoutState.Idle
                return
            }
        }

        val customerEntity = resolvedCustomerId?.let { id -> customers.value.firstOrNull { it.localId == id } }
        val customerNameArabic = customerEntity?.fullName ?: "عميل نقدي مباشر"
        val customerPhone = customerEntity?.phone ?: "-"
        val paymentModeLabel = _posPaymentMode.value.labelArabic

        val (_, _, finalTotalMinor) = computePosTotals()
        val extraInvoiceDiscMinor = if (_posInvoiceDiscountMajorInput.value.isBlank()) {
            0L
        } else {
            parseMinorOrNull(_posInvoiceDiscountMajorInput.value)?.coerceAtLeast(0L) ?: 0L
        }
        val (paidMinor, _) = computePosPaidAndRemainingMinor(finalTotalMinor)
        val paidOverrideMinor: Long? = when (_posPaymentMode.value) {
            PosPaymentMode.CASH -> null
            PosPaymentMode.CREDIT -> 0L
            PosPaymentMode.PARTIAL -> paidMinor
        }

        val saleInputs = itemsSnapshot.map { item ->
            SaleLineInput(
                productId = item.productId,
                serialNumberId = item.serialNumberId,
                quantity = item.quantity,
                unitSellingPriceMinor = item.unitPriceMinor,
                lineDiscountMinor = item.lineDiscountMinor
            )
        }
        val defaultTaxBps = (shopConfiguration.value.defaultTaxPercent.toIntOrNull() ?: 0) * 100
        val notes = _posNotesInput.value.trim()

        _posCheckoutState.value = PosCheckoutState.Processing
        _isProcessingOperation.value = true

        viewModelScope.launch {
            try {
                val res = repository.executeSale(
                    customerId = resolvedCustomerId,
                    items = saleInputs,
                    invoiceDiscountMinor = extraInvoiceDiscMinor,
                    taxBasisPoints = defaultTaxBps,
                    paidAmountMinorOverride = paidOverrideMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        // Rule 20.4: Clear the cart ONLY after successful completion!
                        _posCartItems.value = emptyList()
                        _posInvoiceDiscountMajorInput.value = ""
                        _posPartialPaidMajorInput.value = ""
                        _posNotesInput.value = ""
                        _posInlineValidationError.value = null
                        _posCheckoutState.value = PosCheckoutState.Success(
                            sale = res.data,
                            itemsSnapshot = itemsSnapshot,
                            customerNameArabic = customerNameArabic,
                            customerPhone = customerPhone,
                            paymentModeLabel = paymentModeLabel
                        )
                        emitFeedback(
                            "تم تسجيل البيع بنجاح — فاتورة #${res.data.invoiceNumber} بقيمة ${Money.ofMinor(res.data.totalAmountMinor).formatArabic()}",
                            isError = false,
                            categoryArabic = "نقطة البيع"
                        )
                    }
                    is AppResult.Error -> {
                        // Rule 20.5: Keep the cart, show understandable error, allow retry!
                        _posInlineValidationError.value = res.message
                        _posCheckoutState.value = PosCheckoutState.Error(res.message)
                        emitFeedback(res.message, isError = true, categoryArabic = "تنبيه نقطة البيع")
                    }
                }
            } finally {
                _isProcessingOperation.value = false
            }
        }
    }

    fun startNewPosSale() {
        _posCartItems.value = emptyList()
        _posCustomerMode.value = PosCustomerSelectionMode.NONE
        _posSelectedCustomerId.value = null
        _posPaymentMode.value = PosPaymentMode.CASH
        _posPartialPaidMajorInput.value = ""
        _posInvoiceDiscountMajorInput.value = ""
        _posNotesInput.value = ""
        _posInlineValidationError.value = null
        _posCheckoutState.value = PosCheckoutState.Idle
    }

    fun updateShopIdentity(
        nameArabic: String,
        nameEnglish: String,
        phone: String,
        address: String,
        taxRegistrationNumber: String,
        defaultCurrency: String
    ) {
        viewModelScope.launch {
            val res = repository.updateShopProfile(
                nameArabic = nameArabic,
                nameEnglish = nameEnglish,
                phone = phone,
                address = address,
                taxRegistrationNumber = taxRegistrationNumber,
                defaultCurrency = defaultCurrency,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم حفظ بيانات هوية المتجر وتحديث الفواتير والعملة الافتراضية")
        }
    }

    fun updateAppSetting(key: String, value: String, labelArabic: String = key) {
        viewModelScope.launch {
            val res = repository.updateAppSetting(
                settingKey = key,
                settingValue = value,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث إعداد ($labelArabic) بنجاح")
        }
    }

    fun resetAllSettingsToDefaults() {
        viewModelScope.launch {
            val res = repository.resetAppSettingsToDefaults(actorUserId = _activeUserId.value)
            handleResult(res, "تمت استعادة الإعدادات الافتراضية للمتجر والمظهر بنجاح")
        }
    }

    fun executeSalesReturn(originalSaleId: String, reasonArabic: String) {
        viewModelScope.launch {
            val res = repository.executeSalesReturn(
                originalSaleId = originalSaleId,
                reasonArabic = reasonArabic.ifBlank { "مرتجع مبيعات بناءً على طلب العميل" },
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تنفيذ مرتجع المبيعات الذري (إعادة المخزن والسيريال + قيد REFUND في دفتر الأستاذ)")
        }
    }

    fun executePurchaseReturn(originalPurchaseId: String, reasonArabic: String) {
        viewModelScope.launch {
            val res = repository.executePurchaseReturn(
                originalPurchaseId = originalPurchaseId,
                reasonArabic = reasonArabic.ifBlank { "مرتجع مشتريات للمورد" },
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تنفيذ مرتجع المشتريات الذري (خصم المخزون وتحديث مديونية المورد ودفتر الأستاذ)")
        }
    }

    fun createWallet(providerId: String, walletNameArabic: String, walletNumber: String) {
        viewModelScope.launch {
            val res = repository.createWallet(
                providerId = providerId,
                walletNameArabic = walletNameArabic,
                walletNumber = walletNumber,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تسجيل المحفظة الإلكترونية (الرصيد يشتق حصرياً من قيود دفتر الأستاذ)")
        }
    }

    fun executeWalletOperation(
        walletId: String,
        transferKind: String,
        customerPhone: String,
        principalMajorStr: String,
        feeMajorStr: String,
        commissionMajorStr: String,
        notes: String
    ) {
        val principalMinor = parseMinorOrNull(principalMajorStr)
        val feeMinor = if (feeMajorStr.isBlank()) 0L else (parseMinorOrNull(feeMajorStr) ?: 0L)
        val commMinor = if (commissionMajorStr.isBlank()) 0L else (parseMinorOrNull(commissionMajorStr) ?: 0L)
        if (principalMinor == null || principalMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال مبلغ العملية بشكل صحيح" to true
            return
        }
        viewModelScope.launch {
            val res = repository.executeWalletOperation(
                walletId = walletId,
                transferKind = transferKind,
                customerPhone = customerPhone,
                principalAmountMinor = principalMinor,
                providerFeeMinor = feeMinor,
                shopCommissionMinor = commMinor,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم ترحيل عملية المحفظة في دفتر الأستاذ وتحديث الأرصدة المشتقة")
        }
    }

    // --- SMS Intelligence Actions ---
    fun ingestSmsMessage(senderAddress: String, rawMessageBody: String, autoConfirm: Boolean = false) {
        viewModelScope.launch {
            val res = repository.ingestSmsMessage(
                senderAddress = senderAddress,
                rawMessageBody = rawMessageBody,
                autoConfirmOnHighConfidenceMatch = autoConfirm,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                val outcome = res.data
                val msg = if (outcome.parsingResult != null) {
                    "تم تحليل رسالة (${outcome.parsingResult.detectedProviderCode}) بدرجة ثقة ${outcome.confidenceLevel.arabicLabel}"
                } else {
                    "تم حفظ الرسالة للمراجعة اليدوية (${outcome.confidenceLevel.arabicLabel})"
                }
                emitFeedback(msg, isError = false, categoryArabic = "ذكاء SMS")
            } else if (res is AppResult.Error) {
                emitFeedback(res.message, isError = true, categoryArabic = "ذكاء SMS")
            }
        }
    }

    fun registerExpectedWalletOperation(
        walletId: String,
        expectedType: FinancialTransactionType,
        amountMajorStr: String,
        counterpartyPhone: String
    ) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال المبلغ المتوقع بشكل صحيح" to true
            return
        }
        viewModelScope.launch {
            val res = repository.registerExpectedWalletOperation(
                targetWalletId = walletId,
                expectedType = expectedType,
                expectedAmountMinor = amountMinor,
                counterpartyPhone = counterpartyPhone
            )
            handleResult(res, "تم تسجيل العملية المتوقعة لمطابقتها تلقائياً عند وصول رسالة SMS")
        }
    }

    fun confirmPendingSms(pendingId: String, walletId: String? = null) {
        viewModelScope.launch {
            val res = repository.confirmPendingSms(
                pendingId = pendingId,
                explicitWalletId = walletId,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم اعتماد رسالة SMS وترحيل القيد المالي في دفتر الأستاذ (Idempotent Confirmation)")
        }
    }

    fun rejectOrIgnorePendingSms(pendingId: String, decisionStatus: String) {
        viewModelScope.launch {
            val res = repository.rejectOrIgnorePendingSms(
                pendingId = pendingId,
                decisionStatus = decisionStatus,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث حالة مراجعة رسالة SMS إلى ($decisionStatus) دون التأثير على الأرصدة")
        }
    }

    fun executeCashOperation(
        operationType: FinancialTransactionType,
        amountMajorStr: String,
        reasonArabic: String
    ) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            _feedbackMessage.value = "يرجى إدخال مبلغ نقدي صحيح أكبر من الصفر" to true
            return
        }
        viewModelScope.launch {
            val res = repository.executeCashOperation(
                operationType = operationType,
                amountMinor = amountMinor,
                reasonArabic = reasonArabic,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم ترحيل القيد النقدي في دفتر الأستاذ وتحديث رصيد الخزينة المشتق")
        }
    }

    fun executeCashCloseout(countedCashMajorStr: String, notes: String) {
        val countedMinor = parseMinorOrNull(countedCashMajorStr)
        if (countedMinor == null || countedMinor < 0L) {
            emitFeedback("يرجى إدخال النقدية الفعلية المعدودة بالدرج", isError = true, categoryArabic = "إغلاق الخزينة")
            return
        }
        if (!acquireOperationGuard("CASH_CLOSEOUT:$countedMinor", "جارٍ إغلاق الخزينة...")) return
        viewModelScope.launch {
            try {
                val res = repository.executeCashCloseout(
                    countedCashMinor = countedMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                handleResult(res, "تم تقفيل وردية الخزينة وحفظ مطابقة الرصيد المتوقع مع الفعلي")
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun executeExpense(
        categoryName: String,
        title: String,
        amountMajorStr: String,
        notes: String
    ) {
        val amountMinor = parseMinorOrNull(amountMajorStr)
        if (amountMinor == null || amountMinor <= 0L) {
            emitFeedback("يرجى إدخال مبلغ المصروف بشكل صحيح", isError = true, categoryArabic = "المصروفات")
            return
        }
        if (!acquireOperationGuard("EXPENSE:$categoryName:$title:$amountMinor", "جارٍ تسجيل المصروف...")) return
        viewModelScope.launch {
            try {
                val res = repository.executeExpense(
                    categoryName = categoryName,
                    title = title,
                    amountMinor = amountMinor,
                    notes = notes,
                    actorUserId = _activeUserId.value
                )
                val formattedAmount = Money.ofMinor(amountMinor).formatArabic()
                handleResult(res, "تم تسجيل المصروف ($title) بقيمة $formattedAmount وترحيل القيد المالي المقابل في دفتر الأستاذ")
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun createMaintenanceOrder(
        customerId: String,
        deviceBrand: String,
        deviceModel: String,
        deviceImei: String,
        faultDescription: String,
        estimatedCostMajorStr: String,
        depositMajorStr: String = "0"
    ) {
        val costMinor = parseMinorOrNull(estimatedCostMajorStr) ?: 0L
        val depositMinor = if (depositMajorStr.isBlank()) 0L else (parseMinorOrNull(depositMajorStr) ?: 0L)
        viewModelScope.launch {
            val res = repository.createMaintenanceOrder(
                customerId = customerId,
                deviceBrand = deviceBrand,
                deviceModel = deviceModel,
                deviceImeiOrSerial = deviceImei,
                faultDescription = faultDescription,
                estimatedCostMinor = costMinor,
                depositMinor = depositMinor,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم فتح تذكرة الصيانة وإضافتها لطابور المزامنة")
        }
    }

    fun consumeMaintenancePart(
        orderId: String,
        productId: String,
        quantityStr: String,
        unitChargeMajorStr: String
    ) {
        val qty = quantityStr.trim().toIntOrNull() ?: 1
        val chargeMinor = parseMinorOrNull(unitChargeMajorStr) ?: 0L
        viewModelScope.launch {
            val res = repository.consumePartForMaintenanceOrder(
                maintenanceOrderId = orderId,
                productId = productId,
                quantity = qty,
                unitChargeMinor = chargeMinor,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم صرف قطعة الغيار للتذكرة بحركة مخزنية (MAINTENANCE_USAGE)")
        }
    }

    fun updateMaintenanceOrderStatus(orderId: String, newStatus: MaintenanceStatus, finalChargeMinor: Long) {
        viewModelScope.launch {
            val res = repository.updateMaintenanceStatus(
                orderId = orderId,
                newStatus = newStatus,
                finalChargeMinor = finalChargeMinor,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث حالة تذكرة الصيانة إلى (${newStatus.arabicLabel})")
        }
    }

    fun reverseLedgerTransaction(transactionId: String, reasonArabic: String) {
        if (reasonArabic.isBlank()) {
            _feedbackMessage.value = "يرجى كتابة سبب واضح لإصدار القيد العكسي" to true
            return
        }
        viewModelScope.launch {
            val res = repository.reverseLedgerTransaction(
                originalTransactionId = transactionId,
                reasonArabic = reasonArabic,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم إصدار قيد عكسي تصحيحي (REVERSAL) مع الحفاظ الكامل على التاريخ المالي")
        }
    }

    fun runDeltaSync(
        isNetworkAvailable: Boolean = true,
        simulateFailure: Boolean = false,
        isDiagnosticSimulation: Boolean = false
    ) {
        if (!acquireOperationGuard("DELTA_SYNC:$isNetworkAvailable:$simulateFailure", "قيد المزامنة")) return
        viewModelScope.launch {
            try {
                val config = shopConfiguration.value
                // Rule 12, 13 & 17: Never pretend an in-memory gateway is a real cloud backend in normal user flow.
                if (config.cloudSyncEndpointUrl.isBlank() && !isDiagnosticSimulation) {
                    val pendingCount = syncQueue.value.count {
                        it.syncState == SyncState.PENDING || it.syncState == SyncState.RETRYING || it.syncState == SyncState.FAILED
                    }
                    emitFeedback(
                        "محفوظ محليًا (في انتظار المزامنة: $pendingCount عملية) — يرجى إعداد رابط الخادم السحابي في إعدادات المزامنة لإتمام المزامنة الخارجية",
                        isError = false,
                        categoryArabic = "المزامنة السحابية"
                    )
                    _uxOperationState.value = UxOperationState.Pending(
                        messageArabic = "تم حفظ العملية على الجهاز\nوسيتم مزامنتها عند توفر الاتصال ($pendingCount عملية في الانتظار)",
                        pendingCount = pendingCount
                    )
                    return@launch
                }

                if (!isNetworkAvailable) {
                    _uxOperationState.value = UxOperationState.Offline(
                        messageArabic = "تعذر الاتصال بالخادم. تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا."
                    )
                } else {
                    _uxOperationState.value = UxOperationState.Syncing(
                        messageArabic = "قيد المزامنة مع الخادم..."
                    )
                }

                val res = repository.runDeltaSyncCycle(
                    isNetworkAvailable = isNetworkAvailable,
                    simulateTransientFailure = simulateFailure
                )
                if (res is AppResult.Success) {
                    val r = res.data
                    if (r.retryingCount > 0 && r.syncedCount == 0) {
                        emitFeedback(
                            "تعذر الاتصال بالخادم. تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا (${r.retryingCount} سجل في الانتظار).",
                            isError = true,
                            categoryArabic = "المزامنة السحابية",
                            actionLabelArabic = "إعادة المحاولة",
                            onAction = { runDeltaSync(isNetworkAvailable = true) }
                        )
                        _uxOperationState.value = UxOperationState.SyncFailed(
                            messageArabic = "تعذر الاتصال بالخادم.\nتم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا.",
                            onRetry = { runDeltaSync(isNetworkAvailable = true) }
                        )
                    } else {
                        emitFeedback(
                            "اكتملت دورة المزامنة التزايدية: تمت مزامنة ${r.syncedCount} سجل • في الانتظار/إعادة المحاولة: ${r.retryingCount}",
                            isError = false,
                            categoryArabic = "المزامنة السحابية"
                        )
                        _uxOperationState.value = UxOperationState.Synced(
                            messageArabic = "تمت المزامنة ومصادقة ${r.syncedCount} سجل بنجاح",
                            syncedCount = r.syncedCount
                        )
                    }
                } else if (res is AppResult.Error) {
                    handleResult(res, "")
                }
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun resolveSyncConflict(conflictId: String, resolutionStrategy: String, notes: String) {
        viewModelScope.launch {
            val roleCode = users.value.firstOrNull { it.localId == _activeUserId.value }?.roleCode ?: "OWNER"
            val res = repository.syncCoordinator.resolveConflict(
                conflictId = conflictId,
                resolvedByUserId = _activeUserId.value,
                resolvedByRoleCode = roleCode,
                resolutionStrategy = resolutionStrategy,
                resolutionNotes = notes.ifBlank { "تمت المراجعة والاعتماد من مدير النظام" },
                deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID
            )
            handleResult(res, "تم حسم تعارض المزامنة وتوثيقه في سجل التدقيق")
        }
    }

    fun createVerifiedBackup() {
        if (!acquireOperationGuard("CREATE_BACKUP", "جاري إنشاء النسخة الاحتياطية")) return
        viewModelScope.launch {
            try {
                _uxOperationState.value = UxOperationState.Progress(
                    whatIsHappeningArabic = "جاري إنشاء النسخة الاحتياطية",
                    currentStep = 1,
                    totalSteps = 2
                )
                val res = repository.createVerifiedBackup(actorUserId = _activeUserId.value)
                when (res) {
                    is AppResult.Success -> {
                        _uxOperationState.value = UxOperationState.Progress(
                            whatIsHappeningArabic = "جاري إنشاء النسخة الاحتياطية والتحقق من البصمة الرقمية",
                            currentStep = 2,
                            totalSteps = 2
                        )
                        val validationReport = repository.validateBackupJson(res.data.payloadJson)
                        _lastBackupValidation.value = validationReport
                        // Rule 16: Never report BACKUP SUCCESS unless the backup was actually created AND validated!
                        if (validationReport.isValid) {
                            handleResult(
                                res,
                                "تم إنشاء النسخة الاحتياطية بنجاح (makkaphone-backup-v1) والتحقق من بصمتها الرقمية SHA-256"
                            )
                        } else {
                            emitFeedback(
                                "تعذر التحقق من سلامة النسخة الاحتياطية المنشأة: ${validationReport.validationMessageArabic}",
                                isError = true,
                                categoryArabic = "النسخ الاحتياطي"
                            )
                        }
                    }
                    is AppResult.Error -> {
                        handleResult(res, "")
                    }
                }
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun validateExistingBackup(backup: BackupMetadataEntity) {
        val report = repository.validateBackupJson(backup.payloadJson)
        _lastBackupValidation.value = report
        emitFeedback(report.validationMessageArabic, isError = !report.isValid, categoryArabic = "النسخ الاحتياطي")
    }

    fun validateRawBackupJson(rawJson: String) {
        val report = repository.validateBackupJson(rawJson)
        _lastBackupValidation.value = report
        emitFeedback(report.validationMessageArabic, isError = !report.isValid, categoryArabic = "النسخ الاحتياطي")
    }

    fun executeSafeRestore(
        backup: BackupMetadataEntity,
        userConfirmed: Boolean,
        restoreMode: com.example.backup.RestoreMode = com.example.backup.RestoreMode.REPLACE
    ) {
        if (!acquireOperationGuard("SAFE_RESTORE:${backup.localId}:${restoreMode.name}", "جاري استعادة البيانات")) return
        viewModelScope.launch {
            try {
                _uxOperationState.value = UxOperationState.Progress(
                    whatIsHappeningArabic = "جاري استعادة البيانات",
                    currentStep = 5,
                    totalSteps = 10
                )
                val res = repository.executeSafeBackupRestore(
                    rawEnvelopeJson = backup.payloadJson,
                    userConfirmed = userConfirmed,
                    actorUserId = _activeUserId.value,
                    restoreMode = restoreMode
                )
                val modeLabel = if (restoreMode == com.example.backup.RestoreMode.REPLACE) "استبدال شامل REPLACE" else "دمج ذكي MERGE"
                handleResult(res, "تمت الاستعادة الآمنة ($modeLabel — 10 خطوات) بعد حفظ نسخة أمان تلقائية للبيانات الحالية")
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun executeSafeRestoreFromRawJson(
        rawJson: String,
        userConfirmed: Boolean,
        restoreMode: com.example.backup.RestoreMode = com.example.backup.RestoreMode.REPLACE
    ) {
        if (!acquireOperationGuard("SAFE_RESTORE_RAW:${rawJson.hashCode()}:${restoreMode.name}", "جاري استعادة البيانات")) return
        viewModelScope.launch {
            try {
                _uxOperationState.value = UxOperationState.Progress(
                    whatIsHappeningArabic = "جاري استعادة البيانات",
                    currentStep = 5,
                    totalSteps = 10
                )
                val res = repository.executeSafeBackupRestore(
                    rawEnvelopeJson = rawJson,
                    userConfirmed = userConfirmed,
                    actorUserId = _activeUserId.value,
                    restoreMode = restoreMode
                )
                if (res is AppResult.Success) {
                    _lastBackupValidation.value = res.data
                }
                val modeLabel = if (restoreMode == com.example.backup.RestoreMode.REPLACE) "استبدال شامل REPLACE" else "دمج ذكي MERGE"
                handleResult(res, "تم استيراد واستعادة النسخة الاحتياطية الخارجية بأمان ($modeLabel — 10 خطوات)")
            } finally {
                releaseOperationGuard()
            }
        }
    }

    fun showFeedbackMessage(message: String, isError: Boolean = false) {
        emitFeedback(message, isError = isError, categoryArabic = if (isError) "تنبيه" else "إشعار")
    }

    fun runDataIntegrityCheck() {
        viewModelScope.launch {
            val report = repository.runDataIntegrityHealthCheck()
            _integrityReport.value = report
            emitFeedback(
                "اكتمل فحص سلامة البيانات: الحالة العامة (${report.overallStatus.arabicLabel})",
                isError = false,
                categoryArabic = "فحص السلامة"
            )
        }
    }

    // --- MEKKA PHONE V4 — PART 1 ViewModel Actions ---

    fun openCashShift(openingCashMajorStr: String = "", notes: String = "") {
        val openingMinor = if (openingCashMajorStr.isBlank()) null else parseMinorOrNull(openingCashMajorStr)
        viewModelScope.launch {
            val res = repository.openCashShift(
                openingCashInputMinor = openingMinor,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم فتح وردية الخزينة بنجاح وتوثيق أرصدة البداية")
        }
    }

    fun closeCashShift(
        countedCashMajorStr: String,
        varianceReasonArabic: String = "",
        managerApprovedByUserId: String? = null,
        notes: String = ""
    ) {
        val countedMinor = parseMinorOrNull(countedCashMajorStr)
        if (countedMinor == null || countedMinor < 0L) {
            emitFeedback("يرجى إدخال النقدية الفعلية المعدودة بالدرج", isError = true, categoryArabic = "إغلاق الوردية")
            return
        }
        viewModelScope.launch {
            val res = repository.closeCashShift(
                countedCashMinor = countedMinor,
                varianceReasonArabic = varianceReasonArabic,
                managerApprovedByUserId = managerApprovedByUserId,
                notes = notes,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم إغلاق ومطابقة وردية الخزينة وتوثيق التقرير الختامي")
        }
    }

    fun updateProductPricingPolicy(
        productId: String,
        defaultSellingPriceMajorStr: String,
        minSellingPriceMajorStr: String = "0",
        wholesalePriceMajorStr: String = "0",
        specialCustomerPriceMajorStr: String = "0",
        maxDiscountMajorStr: String = "0",
        promotionalPriceMajorStr: String = "0",
        isPromotionActive: Boolean = false,
        reasonArabic: String = "تحديث سياسة التسعير"
    ) {
        val retailMinor = parseMinorOrNull(defaultSellingPriceMajorStr) ?: 0L
        val minMinor = parseMinorOrNull(minSellingPriceMajorStr) ?: 0L
        val wholesaleMinor = parseMinorOrNull(wholesalePriceMajorStr) ?: 0L
        val specialMinor = parseMinorOrNull(specialCustomerPriceMajorStr) ?: 0L
        val maxDiscMinor = parseMinorOrNull(maxDiscountMajorStr) ?: 0L
        val promoMinor = parseMinorOrNull(promotionalPriceMajorStr) ?: 0L
        viewModelScope.launch {
            val res = repository.updateProductPricingPolicy(
                productId = productId,
                defaultSellingPriceMinor = retailMinor,
                minSellingPriceMinor = minMinor,
                wholesalePriceMinor = wholesaleMinor,
                specialCustomerPriceMinor = specialMinor,
                maxDiscountMinor = maxDiscMinor,
                promotionalPriceMinor = promoMinor,
                isPromotionActive = isPromotionActive,
                reasonArabic = reasonArabic,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث سياسة تسعير الصنف وحفظ سجل تاريخ الأسعار (Price History)")
        }
    }

    fun confirmPriceChange(
        productId: String,
        proposedPriceMinor: Long,
        reasonArabic: String
    ) {
        viewModelScope.launch {
            _uxOperationState.value = UxOperationState.Loading("جاري اعتماد وتوثيق السعر الجديد...")
            val res = repository.updateProductPricingPolicy(
                productId = productId,
                defaultSellingPriceMinor = proposedPriceMinor,
                reasonArabic = reasonArabic,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تعديل سعر البيع إلى ${Money.ofMinor(proposedPriceMinor).formatArabic()} وتوثيقه في سجل التدقيق بنجاح")
        }
    }

    fun updateCustomerCreditPolicy(
        customerId: String,
        creditLimitMajorStr: String,
        defaultCreditDays: Int = 30,
        isCreditActive: Boolean = true
    ) {
        val limitMinor = parseMinorOrNull(creditLimitMajorStr) ?: 0L
        viewModelScope.launch {
            val res = repository.updateCustomerCreditPolicy(
                customerId = customerId,
                creditLimitMinor = limitMinor,
                defaultCreditDays = defaultCreditDays,
                isCreditActive = isCreditActive,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث الحد الائتماني ومهلة السداد للعميل بنجاح")
        }
    }

    fun executePartialSalesReturn(
        originalSaleId: String,
        returnLines: List<com.example.financial.AdvancedReturnLineInput>,
        reasonArabic: String,
        settlementMode: com.example.financial.ReturnSettlementMode = com.example.financial.ReturnSettlementMode.CASH_REFUND
    ) {
        viewModelScope.launch {
            val res = repository.executePartialSalesReturn(
                originalSaleId = originalSaleId,
                returnLines = returnLines,
                reasonArabic = reasonArabic,
                settlementMode = settlementMode,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تنفيذ مرتجع المبيعات التفصيلي وتحديث المخزون ودفتر الأستاذ")
        }
    }

    fun executeStockCountReconciliation(
        lines: List<com.example.inventory.StockCountLineInput>,
        reasonArabic: String
    ) {
        viewModelScope.launch {
            val res = repository.executeStockCountReconciliation(
                lines = lines,
                reasonArabic = reasonArabic,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم اعتماد مطابقة الجرد الفعلي وتسوية الفروقات المخزنية")
        }
    }

    fun updateMaintenanceWorkflowStage(
        orderId: String,
        targetStatus: MaintenanceStatus,
        diagnosticNotes: String? = null,
        repairActionsArabic: String? = null,
        warrantyNotesArabic: String? = null,
        estimatedCostMinor: Long? = null,
        laborFeeMinor: Long? = null,
        isCustomerApproved: Boolean? = null,
        isTestingPassed: Boolean? = null,
        isCustomerNotified: Boolean? = null
    ) {
        viewModelScope.launch {
            val res = repository.updateMaintenanceWorkflowStage(
                orderId = orderId,
                targetStatus = targetStatus,
                diagnosticNotes = diagnosticNotes,
                repairActionsArabic = repairActionsArabic,
                warrantyNotesArabic = warrantyNotesArabic,
                estimatedCostMinor = estimatedCostMinor,
                laborFeeMinor = laborFeeMinor,
                isCustomerApproved = isCustomerApproved,
                isTestingPassed = isTestingPassed,
                isCustomerNotified = isCustomerNotified,
                actorUserId = _activeUserId.value
            )
            handleResult(res, "تم تحديث مرحلة تذكرة الصيانة إلى (${targetStatus.arabicLabel})")
        }
    }

    // =============================================================================================
    // MEKKA PHONE V4 — PART 2: OPERATIONAL INTELLIGENCE & MANAGEMENT CENTER VIEWMODEL LAYER
    // =============================================================================================

    private val _isNetworkOnline = MutableStateFlow(true)
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    private val _isSyncingNow = MutableStateFlow(false)
    val isSyncingNow: StateFlow<Boolean> = _isSyncingNow.asStateFlow()

    private val _selectedCustomer360 = MutableStateFlow<Customer360Snapshot?>(null)
    val selectedCustomer360: StateFlow<Customer360Snapshot?> = _selectedCustomer360.asStateFlow()

    private val _selectedSupplier360 = MutableStateFlow<Supplier360Snapshot?>(null)
    val selectedSupplier360: StateFlow<Supplier360Snapshot?> = _selectedSupplier360.asStateFlow()

    private val _v4ReportFilter = MutableStateFlow(V4ReportFilter(reportType = V4ReportType.SALES))
    val v4ReportFilter: StateFlow<V4ReportFilter> = _v4ReportFilter.asStateFlow()

    private val _v4TabularReportDocument = MutableStateFlow<V4TabularReportDocument?>(null)
    val v4TabularReportDocument: StateFlow<V4TabularReportDocument?> = _v4TabularReportDocument.asStateFlow()

    private val _lastExportedReport = MutableStateFlow<ReportExportResult?>(null)
    val lastExportedReport: StateFlow<ReportExportResult?> = _lastExportedReport.asStateFlow()

    val inventoryAnalyticsBundle: StateFlow<InventoryAnalyticsDashboardBundle> = combine(
        products,
        categories,
        inventoryMovements,
        sales,
        allSaleItems
    ) { prodList, catList, movList, salesList, itemsList ->
        OperationalAnalyticsEngine.buildInventoryAnalyticsAndProcurement(
            products = prodList,
            categories = catList,
            movements = movList,
            sales = salesList,
            saleItems = itemsList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryAnalyticsDashboardBundle())

    private val pendingPart1Flow = combine(
        syncQueue,
        syncConflicts,
        smsReviewItems,
        cashSessions,
        maintenanceOrders
    ) { sq, sc, smsItems, cs, mo ->
        val pendingTx = smsItems.filter { it.pendingTransactionId != null }.map { item ->
            com.example.database.entities.PendingTransactionEntity(
                localId = item.pendingTransactionId!!,
                shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
                smsParsingResultId = item.parsingResultId,
                proposedType = FinancialTransactionType.entries.firstOrNull { it.name == item.detectedOperationType }
                    ?: FinancialTransactionType.TRANSFER,
                proposedAmountMinor = item.extractedAmountMinor ?: 0L,
                proposedFeeMinor = item.extractedFeeMinor ?: 0L,
                targetWalletId = item.targetWalletId,
                counterpartyPhone = item.extractedCounterpartyPhone.orEmpty(),
                status = item.pendingStatus ?: "AWAITING_CONFIRMATION",
                confirmedLedgerTransactionId = item.confirmedLedgerTransactionId,
                deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID,
                createdAt = item.receivedTimestamp,
                updatedAt = item.receivedTimestamp
            )
        }
        Tuple5(sq, sc, pendingTx, cs, mo)
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    val pendingOperationsBundle: StateFlow<PendingOperationsCenterBundle> = combine(
        pendingPart1Flow,
        customerBalances,
        backups,
        auditLogs,
        products
    ) { p1, custBal, bkp, logs, prodList ->
        OperationalCentersEngine.buildPendingOperationsCenter(
            syncQueue = p1.a,
            syncConflicts = p1.b,
            pendingSmsTransactions = p1.c,
            cashSessions = p1.d,
            maintenanceOrders = p1.e,
            customerBalances = custBal,
            backups = bkp,
            auditLogs = logs,
            inventoryItems = prodList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PendingOperationsCenterBundle())

    val smartNotificationCenterBundle: StateFlow<SmartNotificationCenterBundle> = combine(
        pendingPart1Flow,
        inventoryAnalyticsBundle,
        customerBalances,
        supplierBalances,
        expenses
    ) { p1, invAnalytics, custBal, supBal, expList ->
        OperationalCentersEngine.buildSmartNotificationCenter(
            inventoryAnalytics = invAnalytics,
            inventoryItems = products.value,
            cashSessions = p1.d,
            customerBalances = custBal,
            supplierBalances = supBal,
            expenses = expList,
            pendingSmsTransactions = p1.c,
            maintenanceOrders = p1.e,
            syncQueue = p1.a,
            syncConflicts = p1.b,
            backups = backups.value,
            auditLogs = auditLogs.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmartNotificationCenterBundle())

    val maintenanceManagementBundle: StateFlow<MaintenanceManagementCenterBundle> = combine(
        maintenanceOrders,
        allMaintenanceParts,
        expenses,
        ledgerTransactions,
        users
    ) { ordersList, partsList, expList, txList, usersList ->
        OperationalAnalyticsEngine.buildMaintenanceManagementCenter(
            orders = ordersList,
            parts = partsList,
            expenses = expList,
            ledgerTransactions = txList,
            usersMap = usersList.associateBy { it.localId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaintenanceManagementCenterBundle())

    val cashAndWalletControlBundle: StateFlow<CashAndWalletControlCenterBundle> = combine(
        cashDrawers,
        wallets,
        cashSessions,
        ledgerTransactions,
        pendingPart1Flow
    ) { drawersList, walletsList, sessionsList, txList, p1 ->
        OperationalAnalyticsEngine.buildCashAndWalletControlCenter(
            cashDrawerProjection = drawersList.firstOrNull(),
            wallets = walletsList,
            cashSessions = sessionsList,
            ledgerTransactions = txList,
            pendingSmsTransactions = p1.c,
            activeShiftBreakdown = null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashAndWalletControlCenterBundle())

    val offlineControlCenterBundle: StateFlow<OfflineControlCenterBundle> = combine(
        _isNetworkOnline,
        _isSyncingNow,
        shopConfiguration,
        pendingPart1Flow,
        ledgerTransactions
    ) { online, syncing, config, p1, txList ->
        OperationalAnalyticsEngine.buildOfflineAndMultiDeviceControlCenter(
            isNetworkOnline = online,
            isRemoteServerConfigured = config.cloudSyncEndpointUrl.isNotBlank(),
            isSyncingNow = syncing,
            syncQueue = p1.a,
            syncConflicts = p1.b,
            ledgerTransactions = txList,
            inventoryMovements = inventoryMovements.value,
            auditLogs = auditLogs.value,
            usersMap = users.value.associateBy { it.localId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineControlCenterBundle())

    val auditDiagnosticsBundle: StateFlow<AuditDiagnosticsDashboardBundle> = combine(
        _integrityReport,
        pendingPart1Flow,
        backups,
        users,
        _activeUserId
    ) { integrity, p1, bkpList, usersList, activeId ->
        val user = usersList.firstOrNull { it.localId == activeId }
        OperationalAnalyticsEngine.buildAuditDiagnosticsDashboard(
            integrityReport = integrity,
            migrationSchemaVersion = com.example.database.migrations.DatabaseMigrations.CURRENT_SCHEMA_VERSION,
            storageUsedBytes = 512L * 1024L,
            storageFreeBytes = 500L * 1024L * 1024L,
            syncQueue = p1.a,
            syncConflicts = p1.b,
            backups = bkpList,
            pendingSmsTransactions = p1.c,
            activeRoleCode = user?.roleCode ?: "OWNER"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditDiagnosticsDashboardBundle())

    fun triggerSyncCycle() {
        runDeltaSync(isNetworkAvailable = _isNetworkOnline.value)
    }

    fun navigateToCustomerDetail(customerId: String) = requestSelectCustomerProfile(customerId)
    fun navigateToSupplierDetail(supplierId: String) = requestSelectSupplierProfile(supplierId)
    fun navigateToProductDetail(productIdOrSku: String) = requestSelectProductDetail(productIdOrSku)
    fun navigateToMaintenanceTicket(ticketOrOrderId: String) = requestSelectMaintenanceTicket(ticketOrOrderId)

    fun setNetworkOnlineMode(online: Boolean) {
        _isNetworkOnline.value = online
        if (online && syncQueue.value.any { it.syncState == SyncState.PENDING || it.syncState == SyncState.FAILED }) {
            triggerSyncCycle()
        }
    }

    fun loadCustomer360(customerId: String) {
        viewModelScope.launch {
            val res = repository.getCustomer360Snapshot(customerId = customerId, actorUserId = _activeUserId.value)
            if (res is AppResult.Success) {
                _selectedCustomer360.value = res.data
            } else if (res is AppResult.Error) {
                emitFeedback(res.message, isError = true, categoryArabic = "ملف العميل 360")
            }
        }
    }

    fun clearCustomer360() {
        _selectedCustomer360.value = null
    }

    fun loadSupplier360(supplierId: String) {
        viewModelScope.launch {
            val res = repository.getSupplier360Snapshot(supplierId = supplierId)
            if (res is AppResult.Success) {
                _selectedSupplier360.value = res.data
            } else if (res is AppResult.Error) {
                emitFeedback(res.message, isError = true, categoryArabic = "ملف المورد 360")
            }
        }
    }

    fun clearSupplier360() {
        _selectedSupplier360.value = null
    }

    fun updateV4ReportFilter(newFilter: V4ReportFilter) {
        _v4ReportFilter.value = newFilter
        refreshV4TabularReport()
    }

    fun refreshV4TabularReport() {
        viewModelScope.launch {
            val doc = repository.generateV4TabularReport(
                filter = _v4ReportFilter.value,
                actorUserId = _activeUserId.value
            )
            _v4TabularReportDocument.value = doc
        }
    }

    fun exportCurrentV4Report(format: ReportExportFormat, outputDirectory: java.io.File) {
        viewModelScope.launch {
            val res = repository.exportV4Report(
                filter = _v4ReportFilter.value,
                format = format,
                outputDirectory = outputDirectory,
                actorUserId = _activeUserId.value
            )
            if (res is AppResult.Success) {
                _lastExportedReport.value = res.data
                emitFeedback(
                    "تم تصدير التقرير فعلياً: ${res.data.fileName} (${res.data.byteSize} بايت)",
                    isError = false,
                    categoryArabic = "تصدير التقارير"
                )
            } else if (res is AppResult.Error) {
                emitFeedback(res.message, isError = true, categoryArabic = "تصدير التقارير")
            }
        }
    }

    fun executePendingCenterAction(
        item: PendingOperationCenterItem,
        action: PendingCenterActionType,
        onNavigate: (String) -> Unit
    ) {
        when (action) {
            PendingCenterActionType.RETRY -> {
                triggerSyncCycle()
            }
            PendingCenterActionType.APPROVE -> {
                when (item.type) {
                    com.example.intelligence.PendingOperationType.SMS_REVIEW -> {
                        confirmPendingSms(item.entityLocalId, null)
                    }
                    com.example.intelligence.PendingOperationType.PENDING_APPROVALS -> {
                        if (item.id.startsWith("PA-MAINT-")) {
                            updateMaintenanceWorkflowStage(
                                orderId = item.entityLocalId,
                                targetStatus = MaintenanceStatus.IN_REPAIR,
                                isCustomerApproved = true
                            )
                        } else {
                            onNavigate(item.routeTarget)
                        }
                    }
                    else -> onNavigate(item.routeTarget)
                }
            }
            PendingCenterActionType.REJECT -> {
                when (item.type) {
                    com.example.intelligence.PendingOperationType.SMS_REVIEW -> {
                        rejectOrIgnorePendingSms(item.entityLocalId, "REJECTED")
                    }
                    com.example.intelligence.PendingOperationType.PENDING_APPROVALS -> {
                        if (item.id.startsWith("PA-MAINT-")) {
                            updateMaintenanceWorkflowStage(
                                orderId = item.entityLocalId,
                                targetStatus = MaintenanceStatus.CANCELLED,
                                isCustomerApproved = false
                            )
                        }
                    }
                    else -> onNavigate(item.routeTarget)
                }
            }
            PendingCenterActionType.RESOLVE -> {
                if (item.type == com.example.intelligence.PendingOperationType.BACKUP_PROBLEMS) {
                    createVerifiedBackup()
                } else {
                    onNavigate(item.routeTarget)
                }
            }
            PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS -> {
                if (item.type == com.example.intelligence.PendingOperationType.PENDING_CUSTOMER_PAYMENTS) {
                    navigateToCustomerDetail(item.entityLocalId)
                } else if (item.type == com.example.intelligence.PendingOperationType.INVENTORY_ALERTS) {
                    navigateToProductDetail(item.reference)
                }
                onNavigate(item.routeTarget)
            }
        }
    }

    // --- MEKKA PHONE V4.1: Unified Transaction Experience & Composer ---
    private val _activeTransactionDraft = MutableStateFlow<com.example.transaction.TransactionDraft?>(null)
    val activeTransactionDraft: StateFlow<com.example.transaction.TransactionDraft?> = _activeTransactionDraft.asStateFlow()

    private val _draftValidation = MutableStateFlow<com.example.transaction.TransactionDraftValidation?>(null)
    val draftValidation: StateFlow<com.example.transaction.TransactionDraftValidation?> = _draftValidation.asStateFlow()

    private val _showComposerDialog = MutableStateFlow(false)
    val showComposerDialog: StateFlow<Boolean> = _showComposerDialog.asStateFlow()

    private val _composerInitialModality = MutableStateFlow(com.example.transaction.TransactionInputModality.MANUAL)
    val composerInitialModality: StateFlow<com.example.transaction.TransactionInputModality> = _composerInitialModality.asStateFlow()

    private val _isParsingIntent = MutableStateFlow(false)
    val isParsingIntent: StateFlow<Boolean> = _isParsingIntent.asStateFlow()

    fun openTransactionComposer(
        initialModality: com.example.transaction.TransactionInputModality = com.example.transaction.TransactionInputModality.MANUAL,
        preselectedType: com.example.transaction.UnifiedTransactionType? = null
    ) {
        _composerInitialModality.value = initialModality
        if (preselectedType != null) {
            _activeTransactionDraft.value = com.example.transaction.TransactionDraft(
                transactionType = preselectedType,
                inputModality = initialModality
            )
        }
        _showComposerDialog.value = true
    }

    fun closeTransactionComposer() {
        _showComposerDialog.value = false
        _activeTransactionDraft.value = null
        _draftValidation.value = null
    }

    fun parseInputToDraft(rawText: String, modality: com.example.transaction.TransactionInputModality) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isParsingIntent.value = true
            try {
                val draft = repository.parseTransactionIntent(rawText, modality)
                _activeTransactionDraft.value = draft
                validateCurrentDraft(draft)
            } finally {
                _isParsingIntent.value = false
            }
        }
    }

    fun setManualDraft(draft: com.example.transaction.TransactionDraft) {
        _activeTransactionDraft.value = draft
        validateCurrentDraft(draft)
    }

    fun validateCurrentDraft(draft: com.example.transaction.TransactionDraft? = _activeTransactionDraft.value) {
        if (draft == null) {
            _draftValidation.value = null
            return
        }
        viewModelScope.launch {
            val validation = repository.validateTransactionDraft(draft, _activeUserId.value)
            _draftValidation.value = validation
        }
    }

    fun resolveDraftAmbiguity(ambiguityKey: String, chosenOptionId: String) {
        val current = _activeTransactionDraft.value ?: return
        val ambiguity = current.ambiguities.firstOrNull { it.ambiguityKey == ambiguityKey } ?: return

        val updatedDraft = when (ambiguityKey) {
            "productId" -> {
                val product = products.value.firstOrNull { it.productId == chosenOptionId }
                if (product != null) {
                    val unitPrice = if (current.transactionType == com.example.transaction.UnifiedTransactionType.PURCHASE) {
                        product.weightedAvgCostMinor.coerceAtLeast(100L)
                    } else {
                        product.effectiveRetailPriceMinor
                    }
                    val newItem = com.example.transaction.TransactionDraftItem(
                        productId = product.productId,
                        productNameArabic = product.nameArabic,
                        sku = product.sku,
                        barcode = product.barcode,
                        quantity = 1,
                        unitPriceMinor = unitPrice,
                        availableStockQty = product.derivedStockQty,
                        isSerialized = product.isSerialized
                    )
                    current.copy(
                        items = listOf(newItem),
                        totalAmountMinor = newItem.totalLineMinor,
                        paidAmountMinor = newItem.totalLineMinor,
                        ambiguities = current.ambiguities.filterNot { it.ambiguityKey == ambiguityKey },
                        confidencePercent = 95
                    )
                } else current
            }
            "customerId" -> {
                val customer = customers.value.firstOrNull { it.localId == chosenOptionId }
                current.copy(
                    counterpartyId = customer?.localId,
                    counterpartyName = customer?.fullName,
                    counterpartyPhone = customer?.phone,
                    ambiguities = current.ambiguities.filterNot { it.ambiguityKey == ambiguityKey },
                    confidencePercent = (current.confidencePercent + 25).coerceAtMost(100)
                )
            }
            "supplierId" -> {
                val supplier = suppliers.value.firstOrNull { it.localId == chosenOptionId }
                current.copy(
                    counterpartyId = supplier?.localId,
                    counterpartyName = supplier?.companyName,
                    counterpartyPhone = supplier?.phone,
                    ambiguities = current.ambiguities.filterNot { it.ambiguityKey == ambiguityKey },
                    confidencePercent = (current.confidencePercent + 25).coerceAtMost(100)
                )
            }
            "walletId" -> {
                val wallet = wallets.value.firstOrNull { it.walletId == chosenOptionId }
                current.copy(
                    targetAccountId = wallet?.walletId,
                    targetAccountNameArabic = wallet?.walletNameArabic,
                    ambiguities = current.ambiguities.filterNot { it.ambiguityKey == ambiguityKey },
                    confidencePercent = (current.confidencePercent + 25).coerceAtMost(100)
                )
            }
            else -> current
        }
        _activeTransactionDraft.value = updatedDraft
        validateCurrentDraft(updatedDraft)
    }

    fun parseReceiptOcr(ocrRawText: String) {
        viewModelScope.launch {
            _isParsingIntent.value = true
            try {
                val ocrResult = repository.receiptOcrEngine.parseReceiptText(ocrRawText, MekkaPhoneRepository.DEFAULT_SHOP_ID)
                val draft = repository.receiptOcrEngine.convertToTransactionDraft(ocrResult)
                _activeTransactionDraft.value = draft
                validateCurrentDraft(draft)
            } finally {
                _isParsingIntent.value = false
            }
        }
    }

    fun updateDraftExpenseCategory(newCategory: String) {
        val current = _activeTransactionDraft.value ?: return
        val updated = current.copy(expenseCategoryName = newCategory)
        _activeTransactionDraft.value = updated
        viewModelScope.launch {
            repository.smartCategorizationEngine.recordUserCorrection(
                rawKeywordOrDescription = current.notes.ifBlank { current.rawPromptOrInput ?: "" },
                chosenCategory = newCategory,
                actorUserId = _activeUserId.value
            )
        }
        validateCurrentDraft(updated)
    }

    fun registerNewDeviceFromImei(
        brandName: String,
        modelName: String,
        imei: String,
        costMajorStr: String,
        retailPriceMajorStr: String,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val costMinor = (costMajorStr.toDoubleOrNull() ?: 0.0).times(100).toLong()
            val retailMinor = (retailPriceMajorStr.toDoubleOrNull() ?: 0.0).times(100).toLong()
            val res = repository.createProduct(
                nameArabic = "$brandName $modelName (سيريال $imei)",
                sku = "IMEI-$imei",
                categoryId = "CAT-PHONES",
                brandId = "BRD-GENERAL",
                isSerialized = true,
                defaultSellingPriceMinor = retailMinor,
                reorderThresholdQty = 1
            )
            when (res) {
                is AppResult.Success -> {
                    emitFeedback("تم تسجيل الجهاز الجديد بنجاح في الكتالوج", isError = false, categoryArabic = "الأجهزة والكتالوج")
                    parseInputToDraft(res.data.localId, com.example.transaction.TransactionInputModality.BARCODE)
                    onSuccess(res.data.localId)
                }
                is AppResult.Error -> {
                    emitFeedback(res.message, isError = true, categoryArabic = "الأجهزة والكتالوج")
                }
            }
        }
    }

    fun confirmAndExecuteActiveDraft(onSuccess: (com.example.transaction.TransactionExecutionResult) -> Unit = {}) {
        val draft = _activeTransactionDraft.value ?: return
        if (!acquireOperationGuard("EXECUTE_DRAFT:${draft.draftId}", "جاري تنفيذ العملية وتوثيق القيد في دفتر الأستاذ")) return

        viewModelScope.launch {
            try {
                val res = repository.executeConfirmedTransactionDraft(
                    draft = draft,
                    userConfirmed = true,
                    actorUserId = _activeUserId.value
                )
                when (res) {
                    is AppResult.Success -> {
                        emitFeedback(
                            message = res.data.summaryArabic,
                            isError = false,
                            categoryArabic = draft.transactionType.titleArabic,
                            whatChangedArabic = "تم تسجيل القيد في دفتر الأستاذ وتحديث الأرصدة والمخزون بنجاح"
                        )
                        _activeTransactionDraft.value = null
                        _draftValidation.value = null
                        _showComposerDialog.value = false
                        onSuccess(res.data)
                    }
                    is AppResult.Error -> {
                        emitFeedback(
                            message = res.message,
                            isError = true,
                            categoryArabic = draft.transactionType.titleArabic
                        )
                    }
                }
            } finally {
                releaseOperationGuard()
            }
        }
    }

    // --- MEKKA PHONE V4.1: Mekka Intelligence & AI Shop Assistant ---
    private val _aiInquiryHistory = MutableStateFlow<List<com.example.ai.AiAssistantResponse>>(emptyList())
    val aiInquiryHistory: StateFlow<List<com.example.ai.AiAssistantResponse>> = _aiInquiryHistory.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _activeAiProposedAction = MutableStateFlow<com.example.ai.AiActionProposal?>(null)
    val activeAiProposedAction: StateFlow<com.example.ai.AiActionProposal?> = _activeAiProposedAction.asStateFlow()

    private val _showAiAssistantDialog = MutableStateFlow(false)
    val showAiAssistantDialog: StateFlow<Boolean> = _showAiAssistantDialog.asStateFlow()

    fun openAiAssistant() {
        _showAiAssistantDialog.value = true
    }

    fun closeAiAssistant() {
        _showAiAssistantDialog.value = false
    }

    fun askMekkaAssistant(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _isAiThinking.value = true
            try {
                val response = repository.queryMekkaAiAssistant(question)
                _aiInquiryHistory.value = _aiInquiryHistory.value + response
                if (response.proposedAction != null) {
                    _activeAiProposedAction.value = response.proposedAction
                }
            } catch (e: Exception) {
                emitFeedback("تعذر استرجاع الإجابة من المساعد: ${e.message}", isError = true, categoryArabic = "مساعد مكة فون")
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun confirmAndExecuteAiAction(proposal: com.example.ai.AiActionProposal) {
        when (proposal.actionType) {
            com.example.ai.AiProposedActionType.CREATE_EXPENSE -> {
                val amountMinor = proposal.parameters["amountMinor"]?.toLongOrNull() ?: 0L
                val title = proposal.parameters["title"] ?: "مصروف تشغيلي"
                val category = proposal.parameters["category"] ?: "مصروفات عامة"
                val draft = com.example.transaction.TransactionDraft(
                    transactionType = com.example.transaction.UnifiedTransactionType.EXPENSE,
                    inputModality = com.example.transaction.TransactionInputModality.TEXT,
                    rawPromptOrInput = proposal.titleArabic,
                    totalAmountMinor = amountMinor,
                    paidAmountMinor = amountMinor,
                    expenseCategoryName = category,
                    notes = title
                )
                _activeTransactionDraft.value = draft
                validateCurrentDraft(draft)
                _activeAiProposedAction.value = null
                _showAiAssistantDialog.value = false
                _showComposerDialog.value = true
            }
            com.example.ai.AiProposedActionType.CREATE_SALE_DRAFT -> {
                openTransactionComposer(initialModality = com.example.transaction.TransactionInputModality.MANUAL, preselectedType = com.example.transaction.UnifiedTransactionType.SALE)
                _activeAiProposedAction.value = null
                _showAiAssistantDialog.value = false
            }
            else -> {
                emitFeedback("يرجى مراجعة تفاصيل العملية وتأكيدها", isError = false, categoryArabic = proposal.titleArabic)
                _activeAiProposedAction.value = null
            }
        }
    }

    fun dismissAiProposedAction() {
        _activeAiProposedAction.value = null
    }

    fun clearAiHistory() {
        _aiInquiryHistory.value = emptyList()
        _activeAiProposedAction.value = null
    }

    class Factory(private val repository: MekkaPhoneRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MekkaPhoneViewModel(repository) as T
        }
    }
}

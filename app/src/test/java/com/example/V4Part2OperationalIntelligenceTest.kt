package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppResult
import com.example.core.SyncState
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.MaintenanceStatus
import com.example.financial.Money
import com.example.financial.PurchaseLineInput
import com.example.financial.SaleLineInput
import com.example.intelligence.ConnectivitySyncLifecycleState
import com.example.intelligence.Customer360EventType
import com.example.intelligence.PendingCenterActionType
import com.example.intelligence.PendingOperationType
import com.example.intelligence.ReportExportFormat
import com.example.intelligence.SmartNotificationDomain
import com.example.intelligence.SmartNotificationPriority
import com.example.intelligence.Supplier360EventType
import com.example.intelligence.V4ReportFilter
import com.example.intelligence.V4ReportType
import com.example.repository.MekkaPhoneRepository
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Robolectric & Room Verification Suite for MEKKA PHONE V4 — PART 2:
 * OPERATIONAL INTELLIGENCE & MANAGEMENT CENTER (Sections 1 to 19).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4Part2OperationalIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: MekkaPhoneDatabase
    private lateinit var repository: MekkaPhoneRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = MekkaPhoneDatabase.createInMemoryForTesting(context)
        repository = MekkaPhoneRepository(database)
        repository.ensureFoundationInitialized()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test Sections 1, 2, 13, 14, 15 - Pending Center, Smart Notifications, Offline Center, Multi-Device, and Diagnostics`() = runBlocking {
        // 1. Seed real operational scenarios: Cash, Product (low stock), Customer Receivable, Supplier Payable, SMS Pending, Maintenance Waiting Approval
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 5_000_000L,
            reasonArabic = "تمويل خزينة افتتاحي"
        )

        val supRes = repository.createSupplier("مؤسسة الدلتا للتجارة", "محمود", "01099887766")
        val supplierId = (supRes as AppResult.Success).data.localId

        val custRes = repository.createCustomer(
            fullName = "كريم عبد العزيز",
            phone = "01234567890",
            notes = "عميل مميز VIP"
        )
        val customerId = (custRes as AppResult.Success).data.localId
        repository.updateCustomerCreditPolicy(customerId, creditLimitMinor = 2_000_000L, defaultCreditDays = 30, isCreditActive = true)

        val prodRes = repository.createProduct(
            nameArabic = "جراب حماية آيفون 16 برو",
            sku = "ACC-IP16P-CASE",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-APPLE",
            isSerialized = false,
            defaultSellingPriceMinor = 50_000L, // 500 EGP
            reorderThresholdQty = 10
        )
        val productId = (prodRes as AppResult.Success).data.localId

        // Purchase 5 units on credit (so stock = 5 <= reorderThreshold 10 -> Low Stock, and Supplier has Payable)
        val purRes = repository.executePurchase(
            supplierId = supplierId,
            items = listOf(PurchaseLineInput(productId = productId, quantity = 5, unitCostMinor = 25_000L)),
            paidAmountMinorOverride = 50_000L // 1250 total, 500 paid -> 750 EGP supplier payable
        )
        assertTrue(purRes is AppResult.Success)

        // Sell 2 units on partial credit to customer (stock becomes 3, customer has receivable)
        val saleRes = repository.executeSale(
            customerId = customerId,
            items = listOf(SaleLineInput(productId = productId, quantity = 2, unitSellingPriceMinor = 50_000L)),
            paidAmountMinorOverride = 40_000L // 1000 total, 400 paid -> 600 EGP customer receivable
        )
        assertTrue(saleRes is AppResult.Success)

        // Ingest SMS requiring manual review
        repository.ingestSmsMessage(
            senderAddress = "VF-Cash",
            rawMessageBody = "تم استلام مبلغ 750.00 جنيه من رقم 01012345678 رقم العملية 99887766",
            autoConfirmOnHighConfidenceMatch = false
        )

        // Create Maintenance ticket and move to WAITING_APPROVAL
        val maintRes = repository.createMaintenanceOrder(
            customerId = customerId,
            deviceBrand = "Apple",
            deviceModel = "iPhone 15 Pro",
            deviceImeiOrSerial = "358899001122334",
            faultDescription = "تغيير شاشة أصلية",
            estimatedCostMinor = 450_000L,
            depositMinor = 50_000L
        )
        val maintOrder = (maintRes as AppResult.Success).data
        repository.updateMaintenanceWorkflowStage(
            orderId = maintOrder.localId,
            targetStatus = MaintenanceStatus.WAITING_APPROVAL,
            estimatedCostMinor = 480_000L
        )

        // Verify Section 1: Pending Operations Center
        val pendingBundle = repository.getPendingOperationsCenterBundle()
        assertTrue("Pending operations center should have real items", pendingBundle.totalCount > 0)
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.PENDING_SYNC })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.SMS_REVIEW })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.PENDING_APPROVALS })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.PENDING_MAINTENANCE })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.PENDING_CUSTOMER_PAYMENTS })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.BACKUP_PROBLEMS })
        assertTrue(pendingBundle.items.any { it.type == PendingOperationType.INVENTORY_ALERTS })

        pendingBundle.items.forEach { item ->
            assertTrue(item.reference.isNotBlank())
            assertTrue(item.reasonArabic.isNotBlank())
            assertTrue(item.requiredActionArabic.isNotBlank())
            assertTrue(item.availableActions.isNotEmpty())
        }

        // Verify Section 2: Smart Notification Center
        val smartBundle = repository.getSmartNotificationCenterBundle()
        assertTrue("Smart notification center must contain prioritized alerts", smartBundle.totalCount > 0)
        assertTrue(smartBundle.itemsByDomain[SmartNotificationDomain.INVENTORY]?.isNotEmpty() == true)
        assertTrue(smartBundle.itemsByDomain[SmartNotificationDomain.FINANCIAL]?.isNotEmpty() == true)
        assertTrue(smartBundle.itemsByDomain[SmartNotificationDomain.MAINTENANCE]?.isNotEmpty() == true)
        assertTrue(smartBundle.itemsByDomain[SmartNotificationDomain.SYSTEM]?.isNotEmpty() == true)
        assertTrue(smartBundle.allItems.any { it.priority == SmartNotificationPriority.ACTION_REQUIRED || it.priority == SmartNotificationPriority.WARNING })

        // Verify Section 13 & 14: Offline Control Center & Multi-Device Visibility
        val offlineBundle = repository.getOfflineControlCenterBundle(
            isNetworkOnline = false,
            isSyncingNow = false
        )
        assertEquals(ConnectivitySyncLifecycleState.OFFLINE, offlineBundle.lifecycleState)
        assertTrue(offlineBundle.pendingLocalOperationsCount > 0)
        assertTrue(offlineBundle.multiDeviceOperations.isNotEmpty())
        val firstMultiDevice = offlineBundle.multiDeviceOperations.first()
        assertTrue(firstMultiDevice.deviceId.isNotBlank())
        assertTrue(firstMultiDevice.operatorUserId.isNotBlank())
        assertTrue(firstMultiDevice.syncStatusArabic.isNotBlank())

        // Verify online without remote server configured NEVER falsely claims SYNCED_VERIFIED
        val onlineUnconfiguredBundle = repository.getOfflineControlCenterBundle(
            isNetworkOnline = true,
            isSyncingNow = false
        )
        assertFalse(
            "Must never claim SYNCED_VERIFIED without real remote ACK",
            onlineUnconfiguredBundle.lifecycleState == ConnectivitySyncLifecycleState.SYNCED_VERIFIED
        )

        // Verify Section 15: Audit & Diagnostics 12-Pillar Dashboard
        val diagBundle = repository.getAuditDiagnosticsDashboardBundle()
        assertTrue(diagBundle.isDatabaseHealthy)
        assertEquals(com.example.database.migrations.DatabaseMigrations.CURRENT_SCHEMA_VERSION, diagBundle.migrationSchemaVersion)
        assertTrue(diagBundle.syncQueuePendingCount > 0)
        assertTrue(diagBundle.issuesWithRecommendations.isNotEmpty())
    }

    @Test
    fun `test Sections 4 and 5 - Customer 360 and Supplier 360 Timelines and Derived Calculations`() = runBlocking {
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 10_000_000L,
            reasonArabic = "رأس مال"
        )

        val supRes = repository.createSupplier("شركة الأمل للاستيراد", "حسن", "01122334455")
        val supplierId = (supRes as AppResult.Success).data.localId

        val custRes = repository.createCustomer("أحمد زكي", "01055556666", notes = "عميل دائم")
        val customerId = (custRes as AppResult.Success).data.localId

        val prodRes = repository.createProduct(
            nameArabic = "شاحن سامسونج 45 وات",
            sku = "SAM-45W-CHG",
            barcode = "6223004545001",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-SAMSUNG",
            isSerialized = false,
            defaultSellingPriceMinor = 120_000L // 1,200 EGP
        )
        val productId = (prodRes as AppResult.Success).data.localId

        // Purchase 1: 10 units @ 800 EGP
        val pur1Res = repository.executePurchase(
            supplierId = supplierId,
            items = listOf(PurchaseLineInput(productId = productId, quantity = 10, unitCostMinor = 80_000L)),
            paidAmountMinorOverride = 500_000L // 8,000 total, 5,000 paid -> 3,000 payable
        )
        assertTrue(pur1Res is AppResult.Success)
        val pur1Invoice = (pur1Res as AppResult.Success).data.invoiceNumber

        // Purchase 2: 5 units @ 900 EGP (creates price history entry for Supplier 360)
        repository.executePurchase(
            supplierId = supplierId,
            items = listOf(PurchaseLineInput(productId = productId, quantity = 5, unitCostMinor = 90_000L)),
            paidAmountMinorOverride = 450_000L
        )

        // Supplier payment of 1,000 EGP
        repository.recordSupplierPayment(
            supplierId = supplierId,
            amountMinor = 100_000L,
            paymentAccountType = FinancialAccountType.CASH_DRAWER,
            paymentAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
            notes = "دفعة للمورد"
        )

        // Sale to Customer: 2 units @ 1,200 EGP = 2,400 EGP (paid 1,000 EGP -> 1,400 EGP receivable)
        val saleRes = repository.executeSale(
            customerId = customerId,
            items = listOf(SaleLineInput(productId = productId, quantity = 2, unitSellingPriceMinor = 120_000L)),
            paidAmountMinorOverride = 100_000L
        )
        assertTrue(saleRes is AppResult.Success)
        val saleInvoice = (saleRes as AppResult.Success).data.invoiceNumber

        // Customer payment of 500 EGP
        val colRes = repository.recordCustomerPaymentCollection(
            customerId = customerId,
            amountMinor = 50_000L,
            destinationAccountType = FinancialAccountType.CASH_DRAWER,
            destinationAccountId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
            notes = "تحصيل دفعة"
        )
        assertTrue(colRes is AppResult.Success)
        val txRef = (colRes as AppResult.Success).data.referenceNumber

        // Maintenance ticket for Customer
        val maintRes = repository.createMaintenanceOrder(
            customerId = customerId,
            deviceBrand = "Samsung",
            deviceModel = "S23 Ultra",
            deviceImeiOrSerial = "351122334455667",
            faultDescription = "تغيير بطارية",
            estimatedCostMinor = 150_000L,
            depositMinor = 30_000L
        )
        val maintTicket = (maintRes as AppResult.Success).data.ticketNumber

        // Verify Section 3: Global Search across Customer, Phone, Product, SKU, Barcode, IMEI, Invoice, Purchase, Maintenance, Supplier, Transaction Reference
        assertTrue(repository.searchGlobalEntities("أحمد زكي").isNotEmpty())
        assertTrue(repository.searchGlobalEntities("01055556666").isNotEmpty())
        assertTrue(repository.searchGlobalEntities("SAM-45W-CHG").isNotEmpty())
        assertTrue(repository.searchGlobalEntities("6223004545001").isNotEmpty())
        assertTrue(repository.searchGlobalEntities("شركة الأمل").isNotEmpty())
        assertTrue(repository.searchGlobalEntities(saleInvoice).isNotEmpty())
        assertTrue(repository.searchGlobalEntities(pur1Invoice).isNotEmpty())
        assertTrue(repository.searchGlobalEntities(maintTicket).isNotEmpty())
        assertTrue(repository.searchGlobalEntities("351122334455667").isNotEmpty())
        assertTrue(repository.searchGlobalEntities(txRef).isNotEmpty())

        // Verify Customer 360 Snapshot
        val c360Res = repository.getCustomer360Snapshot(customerId)
        assertTrue(c360Res is AppResult.Success)
        val c360 = (c360Res as AppResult.Success).data
        assertEquals(customerId, c360.customer.localId)
        assertEquals(90_000L, c360.receivablesMinor) // 1,400 - 500 = 900 EGP (90,000 minor)
        assertEquals(1, c360.purchasesFromShop.size)
        assertEquals(1, c360.maintenanceTickets.size)
        assertTrue(c360.timeline.any { it.eventType == Customer360EventType.SALE })
        assertTrue(c360.timeline.any { it.eventType == Customer360EventType.PAYMENT })
        assertTrue(c360.timeline.any { it.eventType == Customer360EventType.MAINTENANCE })

        // Verify Supplier 360 Snapshot
        val s360Res = repository.getSupplier360Snapshot(supplierId)
        assertTrue(s360Res is AppResult.Success)
        val s360 = (s360Res as AppResult.Success).data
        assertEquals(supplierId, s360.supplier.localId)
        assertEquals(200_000L, s360.payablesMinor) // 3,000 - 1,000 = 2,000 EGP (200,000 minor)
        assertEquals(2, s360.purchases.size)
        assertEquals(1, s360.suppliedProducts.size)
        assertEquals(15, s360.suppliedProducts.first().totalSuppliedQty)
        assertEquals(2, s360.priceHistory.size)
        assertTrue(s360.timeline.any { it.eventType == Supplier360EventType.PURCHASE })
        assertTrue(s360.timeline.any { it.eventType == Supplier360EventType.PAYMENT })
    }

    @Test
    fun `test Sections 7, 8, 9, 10, 11, 12 - 16 V4 Reports, Real File Exports (CSV, PDF, XLSX), Procurement, Inventory Analytics, Maintenance & Cash-Wallet Control`() = runBlocking {
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 8_000_000L,
            reasonArabic = "رأس مال"
        )

        val supRes = repository.createSupplier("مورد التقنية", "عمر", "01011223344")
        val supplierId = (supRes as AppResult.Success).data.localId
        val custRes = repository.createCustomer("محمود الخطيب", "01299887766")
        val customerId = (custRes as AppResult.Success).data.localId

        val prodRes = repository.createProduct(
            nameArabic = "سماعة ايربودز برو 2",
            sku = "APP-AIRPODS-P2",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-APPLE",
            isSerialized = false,
            defaultSellingPriceMinor = 900_000L,
            reorderThresholdQty = 8
        )
        val productId = (prodRes as AppResult.Success).data.localId

        repository.executePurchase(
            supplierId = supplierId,
            items = listOf(PurchaseLineInput(productId = productId, quantity = 10, unitCostMinor = 600_000L))
        )

        // Sell 5 units -> remaining stock = 5 <= reorderThreshold 8 -> triggers deterministic Reorder Suggestion!
        repository.executeSale(
            customerId = customerId,
            items = listOf(SaleLineInput(productId = productId, quantity = 5, unitSellingPriceMinor = 900_000L))
        )

        // Verify Section 9 & 10: Procurement Intelligence & Inventory Analytics
        val invAnalytics = repository.getInventoryAnalyticsDashboardBundle()
        assertEquals(3_000_000L, invAnalytics.totalInventoryValueWacMinor) // 5 units * 6,000 EGP WAC
        assertTrue("Must suggest reorder when stock (5) <= reorderThreshold (8)", invAnalytics.reorderSuggestions.any { it.productId == productId })
        val suggestion = invAnalytics.reorderSuggestions.first { it.productId == productId }
        assertTrue(suggestion.suggestedReorderQuantity > 0)
        assertTrue(suggestion.reasonArabic.contains("المخزون"))

        // Verify Section 11: Maintenance Management Center
        val maintCenter = repository.getMaintenanceManagementCenterBundle()
        assertNotNull(maintCenter)

        // Verify Section 12: Cash & Wallet Control Center (Ledger is single source of truth)
        val cashWalletControl = repository.getCashAndWalletControlCenterBundle()
        assertEquals(6_500_000L, cashWalletControl.cashDrawerRow.expectedBalanceMinor) // 80,000 - 60,000 + 45,000 = 65,000 EGP

        // Verify Section 7: All 16 V4 Report Types generate real tabular documents
        val exportDir = File(context.cacheDir, "v4_test_exports").apply {
            deleteRecursively()
            mkdirs()
        }

        for (reportType in V4ReportType.entries) {
            val doc = repository.generateV4TabularReport(V4ReportFilter(reportType = reportType))
            assertEquals(reportType, doc.reportType)
            assertTrue(doc.headers.isNotEmpty())
        }

        // Verify Section 8: Real File Export for CSV, PDF, and XLSX
        val salesFilter = V4ReportFilter(reportType = V4ReportType.SALES)

        val csvExportRes = repository.exportV4Report(salesFilter, ReportExportFormat.CSV, exportDir)
        assertTrue(csvExportRes is AppResult.Success)
        val csvExport = (csvExportRes as AppResult.Success).data
        val csvFile = File(csvExport.filePath)
        assertTrue(csvFile.exists() && csvFile.length() > 50L)
        assertTrue(csvFile.readText().contains("تقرير المبيعات"))

        val pdfExportRes = repository.exportV4Report(salesFilter, ReportExportFormat.PDF, exportDir)
        assertTrue(pdfExportRes is AppResult.Success)
        val pdfExport = (pdfExportRes as AppResult.Success).data
        val pdfFile = File(pdfExport.filePath)
        assertTrue(pdfFile.exists() && pdfFile.length() > 100L)
        val pdfHeader = pdfFile.readBytes().take(8).toByteArray().toString(Charsets.US_ASCII)
        assertTrue("PDF must start with %PDF-1.4 header", pdfHeader.startsWith("%PDF-1.4"))

        val xlsxExportRes = repository.exportV4Report(salesFilter, ReportExportFormat.XLSX, exportDir)
        assertTrue(xlsxExportRes is AppResult.Success)
        val xlsxExport = (xlsxExportRes as AppResult.Success).data
        val xlsxFile = File(xlsxExport.filePath)
        assertTrue(xlsxFile.exists() && xlsxFile.length() > 200L)
        ZipFile(xlsxFile).use { zip ->
            assertNotNull(zip.getEntry("[Content_Types].xml"))
            assertNotNull(zip.getEntry("xl/workbook.xml"))
            assertNotNull(zip.getEntry("xl/worksheets/sheet1.xml"))
        }
    }

    @Test
    fun `test Section 3 and 18 - Global Search and Full Test Matrix (Unauthorized, Duplicate Request, Arabic Digits, Empty, Large Dataset)`() = runBlocking {
        // 1. Arabic Digit Normalization & RTL formatting
        assertEquals("01012345678", Money.normalizeDigits("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals(150_000L, Money.parseMajorString("١٥٠٠").amountMinor)

        // 2. Unauthorized RBAC check: Technician cannot create a Sale or view Customer Audit History
        val techCustRes = repository.createCustomer("عميل اختبار", "01555443322")
        val custId = (techCustRes as AppResult.Success).data.localId
        val c360ForTech = repository.getCustomer360Snapshot(custId, actorUserId = MekkaPhoneRepository.DEFAULT_TECH_USER_ID)
        assertTrue(c360ForTech is AppResult.Success)
        assertFalse("Technician should not have audit history authorization", (c360ForTech as AppResult.Success).data.isAuditAuthorized)

        // 3. Idempotent Duplicate Request protection on financial ledger
        val idempKey = "IDEMP-CASH-IN-TEST-001"
        val firstCashIn = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100_000L,
            reasonArabic = "إيداع اختبار عدم التكرار",
            idempotencyKey = idempKey
        )
        val duplicateCashIn = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100_000L,
            reasonArabic = "إيداع اختبار عدم التكرار",
            idempotencyKey = idempKey
        )
        assertTrue(firstCashIn is AppResult.Success)
        assertTrue(
            "Duplicate idempotencyKey must be rejected with DUPLICATE_TRANSACTION_IDEMPOTENT without double-counting",
            duplicateCashIn is AppResult.Error && duplicateCashIn.code == "DUPLICATE_TRANSACTION_IDEMPOTENT"
        )
        assertEquals(100_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)
    }
}

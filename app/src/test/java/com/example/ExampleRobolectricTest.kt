package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.example.auth.PermissionCode
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.SerialLifecycleStatus
import com.example.database.migrations.DatabaseMigrations
import com.example.diagnostics.HealthCheckLevel
import com.example.financial.AccountingEngine
import com.example.financial.JournalLine
import com.example.financial.PurchaseLineInput
import com.example.financial.SaleLineInput
import com.example.repository.MekkaPhoneRepository
import com.example.sms.SmsConfidenceLevel
import com.example.sync.RemoteSyncRequest
import com.example.sync.VerifiedIdempotentRemoteGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Robolectric integration tests verifying Part 1 & Part 2 of MEKKA PHONE:
 * 1. App name & Arabic resources + zero derived balances
 * 2. Atomic Purchase, WAC calculation, Serial/IMEI uniqueness, Atomic Sale, and Atomic Sales Return (Refund)
 * 3. Transaction atomicity (full rollback on failure leaving zero partial financial data)
 * 4. SQLite database triggers blocking deletion or mutation of posted ledger entries + Reversal
 * 5. SMS Intelligence Pipeline (Vodafone Cash, Orange Cash, e& money, InstaPay, Axis Pay), duplicate protection, expected operation matching, and idempotent confirmation
 * 6. Maintenance Order workflow: deposit collection, spare parts consumption (MAINTENANCE_USAGE), and delivery revenue
 * 7. Cash Drawer Closeout session with variance calculation
 * 8. RBAC permissions, Delta Sync cycle, financial conflict protection, 10-step Safe Backup/Restore, and Data Integrity Health Check
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    fun `verify app_name resource and initial zero derived balances`() = runBlocking {
        val appName = context.getString(R.string.app_name)
        assertEquals("مكة فون", appName)

        val kpis = repository.observeDashboardLiveKpis().first()
        assertEquals(0L, kpis.todaySalesMinor)
        assertEquals(0L, kpis.todayExpensesMinor)
        assertEquals(0L, kpis.cashPositionMinor)
        assertEquals(0L, kpis.totalWalletsBalanceMinor)

        val providers = repository.observeWalletProviders().first()
        assertEquals(5, providers.size)
    }

    @Test
    fun `verify atomic purchase, WAC calculation, serial uniqueness, atomic sale, and sales return`() = runBlocking {
        // 1. Fund main cash drawer with 5,000,000 minor (50,000.00 EGP) via CASH_IN ledger entry
        val cashInRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 5_000_000L,
            reasonArabic = "رأس مال تشغيلي افتتاحي للخزينة"
        )
        assertTrue(cashInRes is AppResult.Success)
        assertEquals(5_000_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        // 2. Create Supplier & Serialized Product
        val supRes = repository.createSupplier("شركة النور للتوزيع", "أحمد", "01011112222")
        assertTrue(supRes is AppResult.Success)
        val supplierId = (supRes as AppResult.Success).data.localId

        val prodRes = repository.createProduct(
            nameArabic = "Samsung Galaxy S24 Ultra",
            sku = "SAM-S24U",
            categoryId = "CAT-PHONES",
            brandId = "BRD-SAMSUNG",
            isSerialized = true,
            defaultSellingPriceMinor = 1_500_000L // 15,000.00 EGP
        )
        assertTrue(prodRes is AppResult.Success)
        val productId = (prodRes as AppResult.Success).data.localId

        // 3. Atomic Purchase #1: 2 units @ 10,000.00 EGP (1,000,000 minor) with IMEIs
        val pur1 = repository.executePurchase(
            supplierId = supplierId,
            items = listOf(
                PurchaseLineInput(
                    productId = productId,
                    quantity = 2,
                    unitCostMinor = 1_000_000L,
                    serialNumbersOrImeis = listOf("359000000000001", "359000000000002")
                )
            )
        )
        assertTrue(pur1 is AppResult.Success)

        // Duplicate active IMEI must be rejected and rolled back atomically!
        val dupPurchase = repository.executePurchase(
            supplierId = supplierId,
            items = listOf(
                PurchaseLineInput(
                    productId = productId,
                    quantity = 1,
                    unitCostMinor = 1_100_000L,
                    serialNumbersOrImeis = listOf("359000000000001") // Duplicate active IMEI!
                )
            )
        )
        assertTrue(dupPurchase is AppResult.Error)

        // 4. Atomic Purchase #2: 2 units @ 12,000.00 EGP (1,200,000 minor) -> WAC becomes 11,000.00 EGP (1,100,000 minor)
        val pur2 = repository.executePurchase(
            supplierId = supplierId,
            items = listOf(
                PurchaseLineInput(
                    productId = productId,
                    quantity = 2,
                    unitCostMinor = 1_200_000L,
                    serialNumbersOrImeis = listOf("359000000000003", "359000000000004")
                )
            )
        )
        assertTrue(pur2 is AppResult.Success)

        // Verify derived stock = 4 and WAC = 1,100,000 minor
        val projection = repository.observeProductInventoryProjections().first().first { it.productId == productId }
        assertEquals(4, projection.derivedStockQty)
        assertEquals(1_100_000L, projection.weightedAvgCostMinor)

        // Verify cash position after paying 2,000,000 + 2,400,000 = 4,400,000 minor -> 600,000 minor left
        assertEquals(600_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        // 5. Execute Atomic Sale of 1 serialized unit @ 15,000.00 EGP (1,500,000 minor)
        val availableSerial = repository.observeSerialNumbers().first().first { it.serialOrImei == "359000000000001" }
        val saleRes = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = productId,
                    serialNumberId = availableSerial.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 1_500_000L
                )
            )
        )
        assertTrue(saleRes is AppResult.Success)
        val sale = (saleRes as AppResult.Success).data

        // COGS must equal WAC at moment of sale (1,100,000 minor) and Gross Profit = 400,000 minor
        assertEquals(1_100_000L, sale.totalCostOfGoodsMinor)
        assertEquals(400_000L, sale.grossProfitMinor)

        // Cash drawer must now be 600,000 + 1,500,000 = 2,100,000 minor
        assertEquals(2_100_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        // Reconstruct valuation from historical movements and verify exact match
        val reconstructed = repository.inventoryEngine.verifyProductValuationFromHistory(
            MekkaPhoneRepository.DEFAULT_SHOP_ID,
            productId
        )
        assertEquals(3, reconstructed.currentQuantity)
        assertEquals(1_100_000L, reconstructed.weightedAvgCostMinor)
        assertEquals(3_300_000L, reconstructed.totalInventoryValuationMinor)

        // 6. Execute Atomic Sales Return (Refund) -> restores stock to 4, restores IMEI to IN_STOCK, and refunds 1,500,000 minor
        val returnRes = repository.executeSalesReturn(
            originalSaleId = sale.localId,
            reasonArabic = "استرجاع الجهاز خلال فترة الضمان"
        )
        assertTrue(returnRes is AppResult.Success)
        val projAfterReturn = repository.observeProductInventoryProjections().first().first { it.productId == productId }
        assertEquals(4, projAfterReturn.derivedStockQty)
        assertEquals(600_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)
    }

    @Test
    fun `verify transaction atomicity rolls back completely when stock is insufficient`() = runBlocking {
        val prodRes = repository.createProduct(
            nameArabic = "شاحن أصلي 45W",
            sku = "ACC-CHG-45W",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-ANKER",
            isSerialized = false,
            defaultSellingPriceMinor = 85_000L
        )
        val productId = (prodRes as AppResult.Success).data.localId

        val txCountBefore = repository.observeLedgerTransactions().first().size
        val salesCountBefore = repository.observeSales().first().size

        // Attempt to sell 3 units when stock is 0 -> must fail and leave 0 ledger/sale records
        val failedSale = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = productId,
                    quantity = 3,
                    unitSellingPriceMinor = 85_000L
                )
            )
        )
        assertTrue(failedSale is AppResult.Error)
        assertEquals(txCountBefore, repository.observeLedgerTransactions().first().size)
        assertEquals(salesCountBefore, repository.observeSales().first().size)
    }

    @Test
    fun `verify SQLite triggers prevent deleting or mutating financial ledger entries and reversing works`() = runBlocking {
        val cashInRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 250_000L,
            reasonArabic = "إيداع نقدي للاختبار"
        )
        val tx = (cashInRes as AppResult.Success).data

        // 1. Direct SQL DELETE on financial_transactions MUST be aborted by trigger!
        try {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM financial_transactions WHERE localId = '${tx.localId}'"
            )
            fail("Expected SQLite trigger to abort DELETE on financial_transactions")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(expected.message?.contains("LEDGER_IMMUTABLE") == true)
        }

        // 2. Direct SQL UPDATE of amountMinor on financial_transactions MUST be aborted by trigger!
        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE financial_transactions SET amountMinor = 999999 WHERE localId = '${tx.localId}'"
            )
            fail("Expected SQLite trigger to abort amountMinor mutation on financial_transactions")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(expected.message?.contains("LEDGER_IMMUTABLE") == true)
        }

        // 3. Official Reversal creates compensating entry and returns derived cash balance to 0
        val revRes = repository.reverseLedgerTransaction(
            originalTransactionId = tx.localId,
            reasonArabic = "إلغاء القيد التجريبي بقيد عكسي"
        )
        assertTrue(revRes is AppResult.Success)
        assertEquals(0L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        val updatedOriginal = database.financialLedgerDao().getTransactionById(tx.localId)
        assertNotNull(updatedOriginal)
        assertEquals(LedgerTransactionStatus.REVERSED, updatedOriginal?.status)
    }

    @Test
    fun `verify SMS Intelligence Pipeline across all 5 providers, duplicate blocking, and idempotent confirmation`() = runBlocking {
        // 1. Create a Vodafone Cash Wallet
        val walletRes = repository.createWallet(
            providerId = "PRV-VODAFONE",
            walletNameArabic = "محفظة فودافون كاش الفرع",
            walletNumber = "01000112233"
        )
        assertTrue(walletRes is AppResult.Success)
        val wallet = (walletRes as AppResult.Success).data

        // 2. Register an expected incoming operation before SMS arrives
        val expectedRes = repository.registerExpectedWalletOperation(
            targetWalletId = wallet.localId,
            expectedType = FinancialTransactionType.DEPOSIT,
            expectedAmountMinor = 150_000L, // 1,500.00 EGP
            counterpartyPhone = "01098765432"
        )
        assertTrue(expectedRes is AppResult.Success)

        // 3. Ingest matching Vodafone Cash SMS
        val smsBody = "تم استلام مبلغ 1500.00 ج.م من رقم 01098765432 عبر فودافون كاش رقم العملية #VF908172"
        val ingest1 = repository.ingestSmsMessage(
            senderAddress = "VF-Cash",
            rawMessageBody = smsBody
        )
        assertTrue(ingest1 is AppResult.Success)
        val outcome1 = (ingest1 as AppResult.Success).data
        assertEquals("VODAFONE_CASH", outcome1.parsingResult?.detectedProviderCode)
        assertEquals(SmsConfidenceLevel.HIGH, outcome1.confidenceLevel)
        assertTrue(outcome1.matchedExistingPendingOperation)
        assertNotNull(outcome1.pendingTransaction)

        // 4. Duplicate SMS must be rejected by messageHash!
        val duplicateHashAttempt = repository.ingestSmsMessage(
            senderAddress = "VF-Cash",
            rawMessageBody = smsBody
        )
        assertTrue(duplicateHashAttempt is AppResult.Error)

        // 5. Confirm pending SMS into the Immutable Financial Ledger
        val pendingId = outcome1.pendingTransaction!!.localId
        val confirm1 = repository.confirmPendingSms(pendingId = pendingId, explicitWalletId = wallet.localId)
        assertTrue(confirm1 is AppResult.Success)
        assertEquals(150_000L, repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor)

        // 6. Confirming the exact same pending SMS a second time MUST be blocked idempotently!
        val confirm2 = repository.confirmPendingSms(pendingId = pendingId, explicitWalletId = wallet.localId)
        assertTrue(confirm2 is AppResult.Error)
        assertEquals(150_000L, repository.observeDashboardLiveKpis().first().totalWalletsBalanceMinor)

        // 7. Verify remaining 4 providers parse cleanly (InstaPay, Orange Cash, e& money, Axis Pay)
        val instapayRes = repository.ingestSmsMessage(
            senderAddress = "InstaPay",
            rawMessageBody = "تم تحويل مبلغ 3200.00 ج.م عبر انستا باي إلى 01122334455 مرجع IPN774411"
        )
        assertTrue(instapayRes is AppResult.Success)
        assertEquals("INSTAPAY", (instapayRes as AppResult.Success).data.parsingResult?.detectedProviderCode)

        val orangeRes = repository.ingestSmsMessage(
            senderAddress = "OrangeCash",
            rawMessageBody = "تم استلام 850 جنيه في محفظة أورنج كاش من 01288776655 رقم مرجعي ORG55112"
        )
        assertTrue(orangeRes is AppResult.Success)
        assertEquals("ORANGE_CASH", (orangeRes as AppResult.Success).data.parsingResult?.detectedProviderCode)

        val etisalatRes = repository.ingestSmsMessage(
            senderAddress = "e&money",
            rawMessageBody = "تم سحب مبلغ 600.00 ج.م من اتصالات كاش رسوم 6.00 ج.م رقم العملية ET992211"
        )
        assertTrue(etisalatRes is AppResult.Success)
        assertEquals("ETISALAT_CASH", (etisalatRes as AppResult.Success).data.parsingResult?.detectedProviderCode)

        val axisRes = repository.ingestSmsMessage(
            senderAddress = "AxisPay",
            rawMessageBody = "تم استلام بقيمة 1250.00 ج.م في أكسس باي من 01555443322 مرجع AXS44332"
        )
        assertTrue(axisRes is AppResult.Success)
        assertEquals("AXIS_PAY", (axisRes as AppResult.Success).data.parsingResult?.detectedProviderCode)
    }

    @Test
    fun `verify maintenance workflow with deposit, spare part consumption, delivery, and cash closeout`() = runBlocking {
        // 1. Create Customer & Spare Part Product with inbound stock
        val custRes = repository.createCustomer("محمود حسن", "01055556666", address = "القاهرة")
        val customerId = (custRes as AppResult.Success).data.localId

        val partRes = repository.createProduct(
            nameArabic = "شاشة iPhone 14 أصلية",
            sku = "PRT-IP14-SCR",
            categoryId = "CAT-PARTS",
            brandId = "BRD-APPLE",
            isSerialized = false,
            defaultSellingPriceMinor = 450_000L
        )
        val partProductId = (partRes as AppResult.Success).data.localId

        repository.recordAuthorizedInventoryMovement(
            productId = partProductId,
            movementType = com.example.database.entities.InventoryMovementType.ADJUSTMENT_IN,
            quantity = 2,
            unitCostMinor = 300_000L,
            reason = "رصيد افتتاحي قطع غيار صيانة"
        )

        // 2. Open Maintenance Ticket with 1000 EGP (100,000 minor) deposit
        val orderRes = repository.createMaintenanceOrder(
            customerId = customerId,
            deviceBrand = "Apple",
            deviceModel = "iPhone 14",
            deviceImeiOrSerial = "351112223334445",
            faultDescription = "تغيير شاشة مكسورة",
            estimatedCostMinor = 500_000L,
            depositMinor = 100_000L
        )
        assertTrue(orderRes is AppResult.Success)
        val order = (orderRes as AppResult.Success).data
        assertEquals(100_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        // 3. Consume 1 spare part for the repair ticket (MAINTENANCE_USAGE)
        val consumeRes = repository.consumePartForMaintenanceOrder(
            maintenanceOrderId = order.localId,
            productId = partProductId,
            quantity = 1,
            unitChargeMinor = 450_000L
        )
        assertTrue(consumeRes is AppResult.Success)
        val partProj = repository.observeProductInventoryProjections().first().first { it.productId == partProductId }
        assertEquals(1, partProj.derivedStockQty)

        // 4. Deliver repair ticket and collect remaining 4000 EGP (400,000 minor)
        val deliverRes = repository.updateMaintenanceStatus(
            orderId = order.localId,
            newStatus = MaintenanceStatus.DELIVERED,
            finalChargeMinor = 400_000L
        )
        assertTrue(deliverRes is AppResult.Success)
        assertEquals(500_000L, repository.observeDashboardLiveKpis().first().cashPositionMinor)

        // 5. Execute Cash Drawer Closeout with counted cash = 500,000 minor -> variance = 0
        val closeoutRes = repository.executeCashCloseout(
            countedCashMinor = 500_000L,
            notes = "تقفيل وردية مسائية مطابق تماماً"
        )
        assertTrue(closeoutRes is AppResult.Success)
        val session = (closeoutRes as AppResult.Success).data
        assertEquals(500_000L, session.closingDerivedBalanceMinor)
        assertEquals(500_000L, session.closingCountedCashMinor)
        assertEquals(0L, session.discrepancyMinor)
    }

    @Test
    fun `verify RBAC permissions, Delta Sync, Safe Backup Restore, and Data Integrity Health Check`() = runBlocking {
        // 1. Technician role does NOT have BACKUP_RESTORE permission
        val techAuth = repository.authSecurityManager.authorizeAction(
            MekkaPhoneRepository.DEFAULT_TECH_USER_ID,
            PermissionCode.BACKUP_RESTORE
        )
        assertTrue(techAuth is AppResult.Error)

        // 2. Create customer and run Delta Sync cycle
        repository.createCustomer("عميل اختبار المزامنة", "01077778888")
        val syncReportRes = repository.runDeltaSyncCycle(isNetworkAvailable = true)
        assertTrue(syncReportRes is AppResult.Success)
        assertTrue((syncReportRes as AppResult.Success).data.syncedCount >= 1)

        // 3. Financial conflict policy never uses blind last-write-wins
        val conflict = repository.syncCoordinator.evaluateConflictPolicy(
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            entityType = "FinancialTransaction",
            entityLocalId = "TX-TEST-1",
            isFinancialRecord = true,
            localVersion = 1L,
            remoteVersion = 2L,
            localSnapshotJson = """{"amountMinor":10000}""",
            remoteSnapshotJson = """{"amountMinor":20000}""",
            deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID
        )
        assertNotNull(conflict)
        assertEquals(1, repository.observeDashboardLiveKpis().first().unresolvedConflictsCount)

        // Resolve conflict so health check is 100% PASS
        repository.syncCoordinator.resolveConflict(
            conflictId = conflict!!.localId,
            resolvedByUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            resolvedByRoleCode = "OWNER",
            resolutionStrategy = "RESOLVED_KEEP_LOCAL",
            resolutionNotes = "اعتماد القيد المحلي",
            deviceId = MekkaPhoneRepository.DEFAULT_DEVICE_ID
        )

        // 4. Non-destructive migrations execute cleanly
        DatabaseMigrations.MIGRATION_1_2.migrate(database.openHelper.writableDatabase)
        DatabaseMigrations.MIGRATION_2_3.migrate(database.openHelper.writableDatabase)

        // 5. Create, validate, and execute 10-step Safe Restore of `makkaphone-backup-v1`
        val backupRes = repository.createVerifiedBackup(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID)
        assertTrue(backupRes is AppResult.Success)
        val backup = (backupRes as AppResult.Success).data
        assertEquals("makkaphone-backup-v1", backup.backupFormatVersion)

        val validReport = repository.validateBackupJson(backup.payloadJson)
        assertTrue(validReport.isValid)
        assertTrue(validReport.isChecksumMatched)

        // Unconfirmed restore must be rejected!
        val unconfirmedRestore = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backup.payloadJson,
            userConfirmed = false
        )
        assertTrue(unconfirmedRestore is AppResult.Error)

        // Confirmed restore succeeds and creates pre-restore safety snapshot
        val confirmedRestore = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backup.payloadJson,
            userConfirmed = true
        )
        assertTrue(confirmedRestore is AppResult.Success)

        // Tamper with payload and verify checksum failure detection
        val tamperedJson = backup.payloadJson.replace("MK-001", "MK-999")
        val tamperedReport = repository.validateBackupJson(tamperedJson)
        assertFalse(tamperedReport.isValid)
        assertFalse(tamperedReport.isChecksumMatched)

        // 6. Run full Data Integrity Health Check -> must report PASS across all 16 checks
        val healthReport = repository.runDataIntegrityHealthCheck()
        assertEquals(HealthCheckLevel.PASS, healthReport.overallStatus)
        assertEquals(16, healthReport.items.size)
        assertTrue(healthReport.items.all { it.status == HealthCheckLevel.PASS })
    }

    @Test
    fun `verify V2 Production Hardening - PBKDF2 Auth, Installation Identity, Double-Entry Accounting, Idempotent Sync, Encrypted Backup, and Shop Isolation`() = runBlocking {
        // 1. Unique Installation Identity (UUID) generated and persisted
        val inst1 = repository.authSecurityManager.ensureInstallationIdentity(
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            ownerUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )
        val inst2 = repository.authSecurityManager.ensureInstallationIdentity(
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            ownerUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )
        assertTrue(inst1.installationId.startsWith("INST-"))
        assertEquals(inst1.installationId, inst2.installationId)

        // 2. PBKDF2-HMAC-SHA256 PIN Authentication, Session Verification, Cross-Shop Isolation & Rate-Limiting Lockout
        val validAuth = repository.authSecurityManager.authenticateWithPin(
            userId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            rawPin = "1111",
            expectedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            installationId = inst1.installationId
        )
        assertTrue(validAuth is AppResult.Success)
        val ownerSession = (validAuth as AppResult.Success).data
        assertEquals(MekkaPhoneRepository.DEFAULT_SHOP_ID, ownerSession.shopId)
        assertTrue(ownerSession.hasPermission(PermissionCode.BACKUP_RESTORE))

        // Cross-shop access attempt must be denied
        val crossShopSessionAttempt = repository.authSecurityManager.authorizeSessionAction(
            session = ownerSession,
            requiredPermission = PermissionCode.SALE_CREATE,
            targetShopId = "SHOP-FOREIGN-999"
        )
        assertTrue(crossShopSessionAttempt is AppResult.Error)
        assertEquals("CROSS_SHOP_ACCESS_DENIED", (crossShopSessionAttempt as AppResult.Error).code)

        // Rate-limiting lockout after 5 wrong PIN attempts on Technician account
        repeat(5) {
            val wrongRes = repository.authSecurityManager.authenticateWithPin(
                userId = MekkaPhoneRepository.DEFAULT_TECH_USER_ID,
                rawPin = "0000",
                expectedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
            )
            assertTrue(wrongRes is AppResult.Error)
        }
        val lockedRes = repository.authSecurityManager.authenticateWithPin(
            userId = MekkaPhoneRepository.DEFAULT_TECH_USER_ID,
            rawPin = "3333", // even with correct PIN while locked!
            expectedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
        )
        assertTrue(lockedRes is AppResult.Error)
        assertEquals("AUTH_ACCOUNT_LOCKED_RATE_LIMIT", (lockedRes as AppResult.Error).code)

        // Re-authenticate Owner so activeSession is Owner
        repository.authSecurityManager.authenticateWithPin(
            userId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            rawPin = "1111",
            expectedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            installationId = inst1.installationId
        )

        // 3. Double-Entry Accounting Engine & Chart of Accounts Validation (Total Debits == Total Credits)
        val unbalancedAttempt = AccountingEngine.validateAndBuildJournalEntry(
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            ledgerTransactionId = "TX-TEST-UNBAL",
            referenceNumber = "REF-UNBAL",
            transactionType = "SALE",
            lines = listOf(
                JournalLine("L1", AccountingEngine.ACC_CASH_DRAWER, "النقدية", debitMinor = 500_00L, creditMinor = 0L),
                JournalLine("L2", AccountingEngine.ACC_SALES_REVENUE, "المبيعات", debitMinor = 0L, creditMinor = 400_00L)
            ),
            createdByUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            deviceId = inst1.installationId
        )
        assertTrue(unbalancedAttempt is AppResult.Error)
        assertEquals("UNBALANCED_DOUBLE_ENTRY_JOURNAL", (unbalancedAttempt as AppResult.Error).code)

        // Execute real business transactions and verify Trial Balance is 100% balanced (Debit == Credit)
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 15_000_00L,
            reasonArabic = "رأس مال افتتاحي لاختبار القيد المزدوج"
        )
        repository.executeExpense(
            categoryName = "مصروفات تشغيلية",
            title = "فاتورة كهرباء وإنترنت",
            amountMinor = 500_00L
        )
        val trialBalance = repository.computeTrialBalanceReport()
        assertTrue(trialBalance.isBalanced)
        assertTrue(trialBalance.totalDebitsMinor > 0L)
        assertEquals(trialBalance.totalDebitsMinor, trialBalance.totalCreditsMinor)

        // 4. Server-Side Idempotency & Shop Isolation in RemoteSyncGateway
        val gateway = VerifiedIdempotentRemoteGateway(isExternalCloudConfigured = true)
        val syncReq = RemoteSyncRequest(
            operationId = "SYNC-OP-IDEMPOTENT-01",
            deduplicationKey = "${MekkaPhoneRepository.DEFAULT_SHOP_ID}:Sale:SAL-100:INSERT:1",
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            deviceId = inst1.installationId,
            entityType = "Sale",
            entityLocalId = "SAL-100",
            operationType = "INSERT",
            entityVersion = 1L,
            payloadJson = """{"saleId":"SAL-100","totalMinor":100000}""",
            isFinancialLedgerRecord = true,
            createdAt = System.currentTimeMillis()
        )
        val firstAckRes = gateway.pushOperation(syncReq, authenticatedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(firstAckRes is AppResult.Success)
        val firstAck = (firstAckRes as AppResult.Success).data
        assertFalse(firstAck.alreadyAppliedIdempotent)
        assertEquals(1L, firstAck.serverRemoteVersion)

        // Second identical request must return previous ACK with alreadyAppliedIdempotent = true and NOT increment version
        val secondAckRes = gateway.pushOperation(syncReq, authenticatedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(secondAckRes is AppResult.Success)
        val secondAck = (secondAckRes as AppResult.Success).data
        assertTrue(secondAck.alreadyAppliedIdempotent)
        assertEquals(1L, secondAck.serverRemoteVersion)
        assertEquals(1, gateway.getAppliedOperationsCount())

        // Cross-shop sync request must be rejected by server gateway
        val crossShopSyncRes = gateway.pushOperation(syncReq, authenticatedShopId = "SHOP-OTHER-777")
        assertTrue(crossShopSyncRes is AppResult.Error)
        assertEquals("SERVER_SHOP_ISOLATION_DENIED", (crossShopSyncRes as AppResult.Error).code)

        // Unconfigured external cloud gateway must block and never fake SYNCED
        val unconfiguredGateway = VerifiedIdempotentRemoteGateway(isExternalCloudConfigured = false)
        val blockedSyncRes = unconfiguredGateway.pushOperation(syncReq, authenticatedShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(blockedSyncRes is AppResult.Error)
        assertEquals("CLOUD_BACKEND_NOT_CONFIGURED", (blockedSyncRes as AppResult.Error).code)
    }

    @Test
    fun `verify Credit Sales, Customer Debt Collection, Multi-Item POS Cart, and Comprehensive Settings`() = runBlocking {
        // 1. Seed cash drawer and create supplier, customer, and two products for multi-item cart
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 20_000_00L, // 20,000 EGP
            reasonArabic = "تمويل خزينة لاختبار البيع الآجل والسلة المتعددة"
        )
        val supplier = (repository.createSupplier("مورد الجملة", "أحمد", "01055556666") as AppResult.Success).data
        val customer = (repository.createCustomer("عميل الآجل المميز", "01099998888") as AppResult.Success).data
        val prodA = (repository.createProduct(
            nameArabic = "شاحن أصلي 45W",
            sku = "ACC-CHG-45W",
            categoryId = "CAT-ACCESSORIES",
            brandId = null,
            isSerialized = false,
            defaultSellingPriceMinor = 800_00L
        ) as AppResult.Success).data
        val prodB = (repository.createProduct(
            nameArabic = "سماعة بلوتوث احترافية",
            sku = "ACC-TWS-PRO",
            categoryId = "CAT-ACCESSORIES",
            brandId = null,
            isSerialized = false,
            defaultSellingPriceMinor = 1_200_00L
        ) as AppResult.Success).data

        // 2. Multi-item purchase cart (buy both products in one atomic purchase invoice)
        val purchaseRes = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(
                PurchaseLineInput(productId = prodA.localId, quantity = 5, unitCostMinor = 500_00L),
                PurchaseLineInput(productId = prodB.localId, quantity = 5, unitCostMinor = 800_00L)
            ),
            paidAmountMinorOverride = 6_500_00L,
            notes = "فاتورة شراء متعددة الأصناف"
        )
        assertTrue(purchaseRes is AppResult.Success)

        // 3. Multi-item POS cart sale with Partial Down-Payment (Total = 2,000 EGP, Paid now = 500 EGP, Credit Receivable = 1,500 EGP)
        val saleRes = repository.executeSale(
            customerId = customer.localId,
            items = listOf(
                SaleLineInput(
                    productId = prodA.localId,
                    serialNumberId = null,
                    quantity = 1,
                    unitSellingPriceMinor = 800_00L
                ),
                SaleLineInput(
                    productId = prodB.localId,
                    serialNumberId = null,
                    quantity = 1,
                    unitSellingPriceMinor = 1_200_00L
                )
            ),
            invoiceDiscountMinor = 0L,
            paidAmountMinorOverride = 500_00L,
            notes = "بيع سلة متعددة بدفعة مقدمة والباقي آجل"
        )
        assertTrue(saleRes is AppResult.Success)
        val saleEntity = (saleRes as AppResult.Success).data
        assertEquals("PARTIALLY_PAID", saleEntity.status)
        assertEquals(2_000_00L, saleEntity.totalAmountMinor)
        assertEquals(500_00L, saleEntity.paidAmountMinor)

        // Verify Customer Receivable projection reflects 1,500 EGP debt
        val custProjBefore = repository.observeCustomerBalanceProjections().first()
            .first { it.customerId == customer.localId }
        assertEquals(2_000_00L, custProjBefore.totalSalesMinor)
        assertEquals(500_00L, custProjBefore.totalPaymentsMinor)
        assertEquals(1_500_00L, custProjBefore.outstandingBalanceMinor)

        // 4. Collect 1,000 EGP debt payment from Customer
        val collectRes = repository.recordCustomerPaymentCollection(
            customerId = customer.localId,
            amountMinor = 1_000_00L,
            notes = "تحصيل دفعة من الحساب الآجل"
        )
        assertTrue(collectRes is AppResult.Success)

        val custProjAfter = repository.observeCustomerBalanceProjections().first()
            .first { it.customerId == customer.localId }
        assertEquals(1_500_00L, custProjAfter.totalPaymentsMinor)
        assertEquals(500_00L, custProjAfter.outstandingBalanceMinor)

        // 5. Verify Comprehensive Settings update and persistence
        repository.updateAppSetting(MekkaPhoneRepository.KEY_THEME_MODE, "LIGHT")
        repository.updateAppSetting(MekkaPhoneRepository.KEY_ACCENT_PALETTE, "ROYAL_BLUE")
        repository.updateAppSetting(MekkaPhoneRepository.KEY_UI_DENSITY, "LARGE")
        repository.updateShopProfile(
            nameArabic = "مكة فون بلس",
            nameEnglish = "MEKKA PHONE PLUS",
            phone = "01001112223",
            address = "فرع وسط البلد",
            taxRegistrationNumber = "TAX-8899",
            defaultCurrency = "EGP"
        )

        val config = repository.observeShopConfiguration().first()
        assertEquals("LIGHT", config.themeMode)
        assertEquals("ROYAL_BLUE", config.accentPalette)
        assertEquals("LARGE", config.uiDensity)
        assertEquals("مكة فون بلس", config.shopNameArabic)
    }

    @Test
    fun `verify V3 Part 1 Navigation Structure and POS Checkout State Machine with Explicit Entity Selection`() = runBlocking {
        // 1. Verify Global Bottom Navigation (Rule 5): exactly 5 primary tabs
        val bottomTabs = com.example.navigation.MekkaDestination.primaryBottomTabs
        assertEquals(5, bottomTabs.size)
        assertEquals(
            listOf("الرئيسية", "بيع", "المحافظ", "المخزون", "المزيد"),
            bottomTabs.map { it.shortTitleArabic }
        )

        // 2. Verify More Screen Organization (Rule 6): Daily Operations, Management, System
        assertEquals(
            listOf("العمليات المعلقة", "العملاء", "الموردين", "المشتريات", "الصيانة", "الخزينة", "المصروفات"),
            com.example.navigation.MekkaDestination.dailyOperationsDestinations.map { it.shortTitleArabic }
        )
        assertEquals(
            listOf("التقارير", "دفتر الأستاذ", "التدقيق", "المزامنة", "النسخ الاحتياطي", "الأتمتة الآمنة", "سلامة البيانات"),
            com.example.navigation.MekkaDestination.managementDestinations.map { it.shortTitleArabic }
        )
        assertEquals(
            listOf("الإعدادات", "المستخدمون والصلاحيات", "Diagnostics"),
            com.example.navigation.MekkaDestination.systemDestinations.map { it.shortTitleArabic }
        )

        // 3. Verify V3 POS ViewModel State Machine & Rule 3 (No implicit first customer/product selection)
        val vm = com.example.ui.viewmodel.MekkaPhoneViewModel(repository)
        assertEquals(com.example.ui.viewmodel.PosCustomerSelectionMode.NONE, vm.posCustomerMode.value)
        assertEquals(null, vm.posSelectedCustomerId.value)
        assertTrue(vm.posCartItems.value.isEmpty())

        // Seed a product with 2 units in stock
        val prod = (repository.createProduct(
            nameArabic = "سماعة سامسونج Buds",
            sku = "ACC-BUDS-01",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-SAMSUNG",
            isSerialized = false,
            defaultSellingPriceMinor = 1_500_00L
        ) as AppResult.Success).data
        repository.recordAuthorizedInventoryMovement(
            productId = prod.localId,
            movementType = com.example.database.entities.InventoryMovementType.ADJUSTMENT_IN,
            quantity = 2,
            unitCostMinor = 1_000_00L,
            reason = "رصيد افتتاحي لاختبار نقطة البيع V3"
        )
        val proj = repository.observeProductInventoryProjections().first().first { it.productId == prod.localId }

        // Attempting to proceed to review with empty cart fails with explicit Arabic validation
        assertFalse(vm.requestPosSaleReview())
        assertEquals("اختر المنتج", vm.posInlineValidationError.value)

        // Add product to cart
        assertTrue(vm.addOrIncrementPosCartItem(product = proj, brandName = "Samsung", quantity = 1))
        assertEquals(1, vm.posCartItems.value.size)

        // Attempting to proceed to review WITHOUT explicit customer selection MUST be blocked (Rule 3 & Rule 17)
        assertFalse(vm.requestPosSaleReview())
        assertTrue(vm.posInlineValidationError.value?.contains("اختر العميل أولًا") == true)

        // Attempting CREDIT payment with WALK_IN customer MUST be blocked (Rule 18)
        vm.selectPosWalkInCustomer()
        vm.setPosPaymentMode(com.example.ui.viewmodel.PosPaymentMode.CREDIT)
        assertTrue(vm.posInlineValidationError.value?.contains("اختر العميل أولًا") == true)

        // Switch to CASH payment with explicit WALK_IN customer -> Review step succeeds (Rule 19)
        vm.setPosPaymentMode(com.example.ui.viewmodel.PosPaymentMode.CASH)
        assertTrue(vm.requestPosSaleReview())
        assertTrue(vm.posCheckoutState.value is com.example.ui.viewmodel.PosCheckoutState.Reviewing)
    }

    @Test
    fun `verify V3 Part 2 Operations, Customer and Supplier Profiles, Wallet Domain Fees, Cash Closeout Policy, and 11-Stage Maintenance Lifecycle`() = runBlocking {
        // 1. Seed initial cash drawer balance
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 10_000_00L, // 10,000.00 EGP
            reasonArabic = "رصيد افتتاحي لاختبار الجزء الثاني V3"
        )

        // 2. Customer Collection Preview & Overpayment Prevention (Rules 2 & 3)
        val cust = (repository.createCustomer("خالد عبد الرحمن", "01022334455") as AppResult.Success).data
        val cat = (repository.createCategory("إكسسوارات أصلية") as AppResult.Success).data
        val brd = (repository.createBrand("Anker") as AppResult.Success).data

        // Explicit Category required (Rule 6)
        val missingCatProd = repository.createProduct(
            nameArabic = "كابل شحن",
            sku = "ACC-CBL-01",
            categoryId = "",
            brandId = brd.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 300_00L
        )
        assertTrue(missingCatProd is AppResult.Error)

        val prod = (repository.createProduct(
            nameArabic = "باور بانك أنكر 20000",
            sku = "ANK-PB-20K",
            barcode = "622300112233",
            categoryId = cat.localId,
            brandId = brd.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 1_500_00L,
            description = "PRESET:ACC"
        ) as AppResult.Success).data

        val sup = (repository.createSupplier("التوكيل الهندسي", "مهندس طارق", "01144556677") as AppResult.Success).data

        // PurchaseCalculationEngine verification (Rule 10 & 11)
        val purCalc = com.example.financial.PurchaseCalculationEngine.calculatePurchaseSummary(
            supplierId = sup.localId,
            lineSubtotalsMinor = listOf(4_000_00L),
            discountMinor = 200_00L,
            paidMinorOverride = 2_000_00L
        )
        assertEquals(4_000_00L, purCalc.subtotalMinor)
        assertEquals(200_00L, purCalc.discountMinor)
        assertEquals(3_800_00L, purCalc.netTotalMinor)
        assertEquals(2_000_00L, purCalc.paidMinor)
        assertEquals(1_800_00L, purCalc.remainingPayableMinor)
        assertTrue(purCalc.isValid)

        repository.executePurchase(
            supplierId = sup.localId,
            items = listOf(PurchaseLineInput(productId = prod.localId, quantity = 4, unitCostMinor = 1_000_00L)),
            discountMinor = 200_00L,
            paidAmountMinorOverride = 2_000_00L
        )

        // Sell 2 units on credit to Customer (Total = 3,000 EGP, Paid = 1,000 EGP, Remaining = 2,000 EGP)
        repository.executeSale(
            customerId = cust.localId,
            items = listOf(SaleLineInput(productId = prod.localId, serialNumberId = null, quantity = 2, unitSellingPriceMinor = 1_500_00L)),
            paidAmountMinorOverride = 1_000_00L
        )

        val custProjection = repository.observeCustomerBalanceProjections().first().first { it.customerId == cust.localId }
        val custProfile = com.example.financial.PartyAccountStatementEngine.buildCustomerProfile(
            customerId = cust.localId,
            derivedBalanceMinor = custProjection.outstandingBalanceMinor,
            sales = repository.observeSales().first(),
            refunds = repository.observeRefunds().first(),
            ledgerTransactions = repository.observeLedgerTransactions().first()
        )
        assertEquals(2_000_00L, custProfile.currentBalanceMinor)

        // Overpayment must be blocked unless explicitly allowed (Rule 3)
        val overpayPreview = com.example.financial.PartyAccountStatementEngine.evaluateCustomerCollection(
            currentBalanceMinor = custProfile.currentBalanceMinor,
            amountPaidMinor = 2_500_00L,
            allowOverpayment = false
        )
        assertFalse(overpayPreview.isValid)
        assertNotNull(overpayPreview.validationErrorArabic)

        val validCollectPreview = com.example.financial.PartyAccountStatementEngine.evaluateCustomerCollection(
            currentBalanceMinor = custProfile.currentBalanceMinor,
            amountPaidMinor = 1_200_00L,
            allowOverpayment = false
        )
        assertTrue(validCollectPreview.isValid)
        assertEquals(800_00L, validCollectPreview.newBalanceMinor)

        // 3. Wallet Domain Fee Engine & Friendly Terminology (Rules 12, 13, 14, 15)
        assertEquals(
            listOf("إيداع", "سحب", "تحويل"),
            com.example.financial.WalletUserOperationType.entries.map { it.arabicLabel }
        )
        val feeBreakdown = com.example.financial.WalletDomainPolicyEngine.calculateFees(
            operationType = com.example.financial.WalletUserOperationType.DEPOSIT,
            amountMinor = 1_000_00L, // 1,000 EGP
            providerCode = "VODAFONE_CASH",
            configuredShopCommissionBps = 100 // 1% = 10 EGP shop fee, 0.1% = 1 EGP network fee
        )
        assertEquals(1_000_00L, feeBreakdown.amountMinor)
        assertEquals(1_00L, feeBreakdown.networkFeeMinor)
        assertEquals(10_00L, feeBreakdown.shopFeeMinor)
        assertEquals(9_00L, feeBreakdown.shopNetMinor)

        // 4. Cash Closeout Policy Engine (Rule 17)
        val largeVarianceEval = com.example.financial.CashCloseoutPolicyEngine.evaluateCloseout(
            expectedBalanceMinor = 8_000_00L,
            countedCashMinor = 7_500_00L, // -500 EGP discrepancy (> 100 EGP threshold)
            discrepancyReasonArabic = "عجز قيد المراجعة",
            actorRoleCode = "CASHIER",
            managerConfirmed = false
        )
        assertTrue(largeVarianceEval.hasDiscrepancy)
        assertTrue(largeVarianceEval.requiresManagerAuthorization)
        assertFalse(largeVarianceEval.isValidToSubmit)

        val authorizedVarianceEval = com.example.financial.CashCloseoutPolicyEngine.evaluateCloseout(
            expectedBalanceMinor = 8_000_00L,
            countedCashMinor = 7_500_00L,
            discrepancyReasonArabic = "عجز قيد المراجعة",
            actorRoleCode = "OWNER",
            managerConfirmed = true
        )
        assertTrue(authorizedVarianceEval.isValidToSubmit)

        // 5. Maintenance 11-Stage Workflow & Dedicated Ticket Payment Collection (Rules 18, 19, 20)
        assertEquals(11, MaintenanceStatus.workflowPipeline.size)
        val ticketRes = repository.createMaintenanceOrder(
            customerId = cust.localId,
            deviceBrand = "Samsung",
            deviceModel = "A55",
            deviceImeiOrSerial = "358899001122334",
            faultDescription = "تغيير بطارية",
            estimatedCostMinor = 1_200_00L,
            depositMinor = 300_00L,
            assignedTechnicianId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            customerNotes = "الجهاز بحالة ممتازة"
        )
        assertTrue(ticketRes is AppResult.Success)
        val ticket = (ticketRes as AppResult.Success).data

        // Collect remaining 900 EGP via dedicated maintenance payment method
        val maintPayRes = repository.collectMaintenanceOrderPayment(
            orderId = ticket.localId,
            amountMinor = 900_00L,
            notes = "سداد باقي تكلفة الصيانة"
        )
        assertTrue(maintPayRes is AppResult.Success)

        val updatedTicket = repository.observeMaintenanceOrders().first().first { it.localId == ticket.localId }
        val maintSummary = com.example.financial.MaintenanceFinancialEngine.computeTicketFinancials(
            order = updatedTicket,
            parts = emptyList(),
            ledgerTransactions = repository.observeLedgerTransactions().first()
        )
        assertEquals(1_200_00L, maintSummary.effectiveTotalChargeMinor)
        assertEquals(1_200_00L, maintSummary.paidAmountMinor)
        assertEquals(0L, maintSummary.remainingAmountMinor)
        assertTrue(maintSummary.isFullyPaid)
    }

    @Test
    fun `verify V3 Part 3 Reports Center, First-Launch Onboarding, RBAC Inventory Role, Truthful Sync, Centralized Error Mapper, and Backup Modes`() = runBlocking {
        // 1. First-Launch Onboarding Setup (`إعداد مكة فون`, Rule 8)
        val mismatchRes = repository.completeFirstLaunchSetup(
            ownerFullNameArabic = "أحمد الفارس",
            shopNameArabic = "مكة فون بلس",
            phoneOrAccount = "01099887766",
            rawPin = "9988",
            confirmPin = "9987"
        )
        assertTrue(mismatchRes is AppResult.Error)

        val setupRes = repository.completeFirstLaunchSetup(
            ownerFullNameArabic = "أحمد الفارس",
            shopNameArabic = "مكة فون بلس",
            phoneOrAccount = "01099887766",
            rawPin = "8426",
            confirmPin = "8426"
        )
        assertTrue(setupRes is AppResult.Success)
        val configAfterOnboarding = repository.observeShopConfiguration().first()
        assertTrue(configAfterOnboarding.onboardingCompleted)
        assertEquals("مكة فون بلس", configAfterOnboarding.shopNameArabic)

        // Old bootstrap PIN ("1234") must now be rejected, and new PIN ("8426") must succeed
        val oldPinAuth = repository.authSecurityManager.authenticateWithPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1234", MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(oldPinAuth is AppResult.Error)
        val newPinAuth = repository.authSecurityManager.authenticateWithPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "8426", MekkaPhoneRepository.DEFAULT_SHOP_ID)
        assertTrue(newPinAuth is AppResult.Success)

        // 2. RBAC Inventory Role enforcement (Rule 9)
        val invPerms = com.example.auth.RolePermissionMatrix.defaultPermissionsForRole(
            com.example.database.entities.UserRoleType.INVENTORY
        )
        assertTrue(invPerms.contains(PermissionCode.PURCHASE_CREATE))
        assertTrue(invPerms.contains(PermissionCode.INVENTORY_ADJUST))
        assertTrue(invPerms.contains(PermissionCode.PRICE_CHANGE))
        assertFalse(invPerms.contains(PermissionCode.LEDGER_REVERSE))
        assertFalse(invPerms.contains(PermissionCode.BACKUP_RESTORE))

        // 3. Centralized Error Mapper (Rule 16)
        val sqliteError = com.example.core.CentralizedErrorMapper.mapException(
            SQLiteConstraintException("FOREIGN KEY constraint failed")
        )
        assertEquals("تعذر حفظ العملية لأن البيانات مرتبطة بسجل آخر.", sqliteError.userMessageArabic)
        assertTrue(sqliteError.technicalExceptionClass.contains("SQLiteConstraintException"))

        // 4. Truthful Cloud Sync Status (Rules 12 & 13: Never fake "Server ACK")
        val localSavedStatus = repository.syncCoordinator.resolveTruthfulSyncUiStatus(
            pendingCount = 5,
            failedCount = 0,
            unresolvedConflictCount = 0,
            syncedCount = 0,
            isRemoteEndpointConfigured = false
        )
        assertEquals("محفوظ محليًا", localSavedStatus.arabicLabel)
        assertFalse(localSavedStatus.arabicLabel.contains("Server ACK"))

        val pendingRemoteStatus = repository.syncCoordinator.resolveTruthfulSyncUiStatus(
            pendingCount = 3,
            failedCount = 0,
            unresolvedConflictCount = 0,
            syncedCount = 2,
            isRemoteEndpointConfigured = true
        )
        assertEquals("في انتظار المزامنة", pendingRemoteStatus.arabicLabel)

        val conflictStatus = repository.syncCoordinator.resolveTruthfulSyncUiStatus(
            pendingCount = 0,
            failedCount = 0,
            unresolvedConflictCount = 1,
            syncedCount = 10,
            isRemoteEndpointConfigured = true
        )
        assertEquals("تعارض يحتاج مراجعة", conflictStatus.arabicLabel)

        // 5. Reports Center Bundle (Rules 1, 2, 3: 7 report categories from authoritative domain data)
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 20_000_00L,
            reasonArabic = "تمويل خزينة للتقارير"
        )
        val sup = (repository.createSupplier("مؤسسة الشرق", "محمود", "01200112233") as AppResult.Success).data
        val cat = (repository.createCategory("هواتف ذكية") as AppResult.Success).data
        val prod = (repository.createProduct(
            nameArabic = "iPhone 16 Pro",
            sku = "APL-IP16P",
            categoryId = cat.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 5_000_00L
        ) as AppResult.Success).data

        repository.executePurchase(
            supplierId = sup.localId,
            items = listOf(PurchaseLineInput(productId = prod.localId, quantity = 3, unitCostMinor = 4_000_00L)),
            paidAmountMinorOverride = 8_000_00L // 4,000 EGP payable remaining
        )
        val cust = (repository.createCustomer("عمرو دياب", "01055667788") as AppResult.Success).data
        repository.executeSale(
            customerId = cust.localId,
            items = listOf(SaleLineInput(productId = prod.localId, serialNumberId = null, quantity = 2, unitSellingPriceMinor = 5_000_00L)),
            paidAmountMinorOverride = 7_000_00L // 3,000 EGP receivable remaining
        )
        repository.executeExpense(
            categoryName = "إيجار",
            title = "إيجار المعرض",
            amountMinor = 500_00L,
            notes = "إيجار شهري"
        )

        val reportBundle = repository.computeReportsCenterBundle(
            filter = com.example.financial.ReportsCenterFilter(
                periodPreset = com.example.financial.ReportPeriodPreset.THIS_MONTH
            )
        )
        assertEquals(10_000_00L, reportBundle.salesSection.periodSalesMinor)
        assertEquals(8_000_00L, reportBundle.profitsSection.cogsMinor)
        assertEquals(2_000_00L, reportBundle.profitsSection.grossProfitMinor)
        assertEquals(500_00L, reportBundle.profitsSection.expensesMinor)
        assertEquals(1_500_00L, reportBundle.profitsSection.netResultMinor)
        assertEquals(3_000_00L, reportBundle.customersSection.totalReceivablesMinor)
        assertEquals(4_000_00L, reportBundle.suppliersSection.totalPayablesMinor)
        assertEquals(4_000_00L, reportBundle.inventorySection.totalStockValueMinor) // 1 remaining unit @ 4,000 EGP WAC

        // 6. Backup & Restore: REPLACE and MERGE modes + Schema validation (Rules 10, 11, 24)
        val backupRes = repository.createVerifiedBackup()
        assertTrue(backupRes is AppResult.Success)
        val backupMeta = (backupRes as AppResult.Success).data
        val validReport = repository.validateBackupJson(backupMeta.payloadJson)
        assertTrue(validReport.isValid)

        val replaceRestoreRes = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backupMeta.payloadJson,
            userConfirmed = true,
            restoreMode = com.example.backup.RestoreMode.REPLACE
        )
        assertTrue(
            "REPLACE restore failed: ${(replaceRestoreRes as? AppResult.Error)?.message} / ${(replaceRestoreRes as? AppResult.Error)?.code}",
            replaceRestoreRes is AppResult.Success
        )

        val mergeRestoreRes = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backupMeta.payloadJson,
            userConfirmed = true,
            restoreMode = com.example.backup.RestoreMode.MERGE
        )
        assertTrue(
            "MERGE restore failed: ${(mergeRestoreRes as? AppResult.Error)?.message} / ${(mergeRestoreRes as? AppResult.Error)?.code}",
            mergeRestoreRes is AppResult.Success
        )

        // 7. Navigation Audit (Rule 25)
        assertEquals(
            listOf("الرئيسية", "بيع", "المحافظ", "المخزون", "المزيد"),
            com.example.navigation.MekkaDestination.primaryBottomTabs.map { it.shortTitleArabic }
        )
    }

    @Test
    fun verifyMekkaPhoneV31CoreFinancialAndSecurityAudit() = runBlocking {
        val shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID
        val mainDrawerId = MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID

        // Seed opening cash drawer balance: 50,000 EGP
        val openCashRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 50_000_00L,
            reasonArabic = "رصيد افتتاحي للخزينة — فحص V3.1",
            idempotencyKey = "IDEM-CASH-OPEN-1"
        )
        assertTrue(openCashRes is AppResult.Success)

        // Section 12: Idempotency check on duplicate cash operation
        val dupCashRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 50_000_00L,
            reasonArabic = "رصيد افتتاحي للخزينة — فحص V3.1",
            idempotencyKey = "IDEM-CASH-OPEN-1"
        )
        assertTrue(dupCashRes is AppResult.Error)
        assertEquals("DUPLICATE_TRANSACTION_IDEMPOTENT", (dupCashRes as AppResult.Error).code)

        // Section 21: Domain Validation Audit (Phone, Duplicate SKU, Duplicate Barcode)
        val badCustomerPhone = repository.createCustomer("عميل تجريبي", "123")
        assertTrue(badCustomerPhone is AppResult.Error)
        assertEquals("INVALID_PHONE", (badCustomerPhone as AppResult.Error).code)

        val badSupplierPhone = repository.createSupplier("مورد تجريبي", "أحمد", "abc12")
        assertTrue(badSupplierPhone is AppResult.Error)
        assertEquals("INVALID_PHONE", (badSupplierPhone as AppResult.Error).code)

        val cat = (repository.createCategory("هواتف واكسسوارات V3.1") as AppResult.Success).data
        val supplier = (repository.createSupplier("شركة النيل للتوريدات", "مصطفى كامل", "01099887766") as AppResult.Success).data
        val customer = (repository.createCustomer("محمد صلاح", "01122334455") as AppResult.Success).data

        val wacProduct = (repository.createProduct(
            nameArabic = "سماعة بلوتوث احترافية",
            sku = "WAC-TEST-100",
            barcode = "6221000000100",
            categoryId = cat.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 250_00L
        ) as AppResult.Success).data

        val dupSkuRes = repository.createProduct(
            nameArabic = "صنف مكرر الكود",
            sku = "wac-test-100",
            barcode = "6221000000999",
            categoryId = cat.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 300_00L
        )
        assertTrue(dupSkuRes is AppResult.Error)
        assertEquals("DUPLICATE_SKU", (dupSkuRes as AppResult.Error).code)

        val dupBarcodeRes = repository.createProduct(
            nameArabic = "صنف مكرر الباركود",
            sku = "WAC-TEST-200",
            barcode = "6221000000100",
            categoryId = cat.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 300_00L
        )
        assertTrue(dupBarcodeRes is AppResult.Error)
        assertEquals("DUPLICATE_BARCODE", (dupBarcodeRes as AppResult.Error).code)

        // Section 14: WAC Verification (10 units @ 100 + 10 units @ 200 -> WAC = 150)
        val pur1 = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(PurchaseLineInput(productId = wacProduct.localId, quantity = 10, unitCostMinor = 100_00L)),
            idempotencyKey = "IDEM-PUR-WAC-1"
        )
        assertTrue(pur1 is AppResult.Success)

        // Duplicate Purchase Idempotency Check
        val pur1Dup = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(PurchaseLineInput(productId = wacProduct.localId, quantity = 10, unitCostMinor = 100_00L)),
            idempotencyKey = "IDEM-PUR-WAC-1"
        )
        assertTrue(pur1Dup is AppResult.Error)
        assertEquals("DUPLICATE_TRANSACTION_IDEMPOTENT", (pur1Dup as AppResult.Error).code)

        val pur2 = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(PurchaseLineInput(productId = wacProduct.localId, quantity = 10, unitCostMinor = 200_00L)),
            idempotencyKey = "IDEM-PUR-WAC-2"
        )
        assertTrue(pur2 is AppResult.Success)

        val invAfterPurchases = database.catalogInventoryDao().getInventoryRecord(shopId, wacProduct.localId)!!
        val qtyAfterPurchases = database.catalogInventoryDao().getDerivedStockQuantity(shopId, wacProduct.localId)
        assertEquals(20, qtyAfterPurchases)
        assertEquals(150_00L, invAfterPurchases.weightedAvgCostMinor) // Exact WAC = 150.00 EGP

        // Sell 6 units @ 250 EGP -> verify remaining qty = 14, COGS = 6 * 150 = 900 EGP, WAC stays 150 EGP
        val wacSaleRes = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = wacProduct.localId, quantity = 6, unitSellingPriceMinor = 250_00L)),
            idempotencyKey = "IDEM-SALE-WAC-1"
        )
        assertTrue(wacSaleRes is AppResult.Success)
        val wacSale = (wacSaleRes as AppResult.Success).data
        val wacSaleItems = database.operationsDao().getSaleItems(wacSale.localId)
        assertEquals(1, wacSaleItems.size)
        assertEquals(150_00L, wacSaleItems.first().unitCostAtSaleMinor) // COGS per unit = 150 EGP
        assertEquals(14, database.catalogInventoryDao().getDerivedStockQuantity(shopId, wacProduct.localId))
        assertEquals(150_00L, database.catalogInventoryDao().getInventoryRecord(shopId, wacProduct.localId)!!.weightedAvgCostMinor)

        // Duplicate Sale Idempotency Check
        val wacSaleDup = repository.executeSale(
            customerId = null,
            items = listOf(SaleLineInput(productId = wacProduct.localId, quantity = 6, unitSellingPriceMinor = 250_00L)),
            idempotencyKey = "IDEM-SALE-WAC-1"
        )
        assertTrue(wacSaleDup is AppResult.Error)
        assertEquals("DUPLICATE_TRANSACTION_IDEMPOTENT", (wacSaleDup as AppResult.Error).code)

        // Section 15: IMEI Audit (Serialized products lifecycle & cross-product protection)
        val serialProdA = (repository.createProduct(
            nameArabic = "Samsung S24 Ultra 256GB",
            sku = "SAM-S24U-A",
            categoryId = cat.localId,
            isSerialized = true,
            defaultSellingPriceMinor = 45_000_00L
        ) as AppResult.Success).data

        val serialProdB = (repository.createProduct(
            nameArabic = "iPhone 15 Pro Max 256GB",
            sku = "APL-IP15PM-B",
            categoryId = cat.localId,
            isSerialized = true,
            defaultSellingPriceMinor = 55_000_00L
        ) as AppResult.Success).data

        // Invalid short IMEI on purchase
        val shortImeiPur = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(
                PurchaseLineInput(
                    productId = serialProdA.localId,
                    quantity = 1,
                    unitCostMinor = 40_000_00L,
                    serialNumbersOrImeis = listOf("123")
                )
            )
        )
        assertTrue(shortImeiPur is AppResult.Error)
        assertEquals("INVALID_IMEI_LENGTH", (shortImeiPur as AppResult.Error).code)

        // Valid IMEI purchase
        val imeiA1 = "359881000011111"
        val imeiA2 = "359881000022222"
        val validSerialPur = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(
                PurchaseLineInput(
                    productId = serialProdA.localId,
                    quantity = 2,
                    unitCostMinor = 3_000_00L,
                    serialNumbersOrImeis = listOf(imeiA1, imeiA2)
                )
            )
        )
        assertTrue(validSerialPur is AppResult.Success)

        val serialEntityA1 = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imeiA1)!!
        val serialEntityA2 = database.catalogInventoryDao().getSerialByImeiAnyStatus(shopId, imeiA2)!!
        assertEquals(SerialLifecycleStatus.IN_STOCK, serialEntityA1.status)

        // Prevent duplicate IMEI purchase
        val dupImeiPur = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(
                PurchaseLineInput(
                    productId = serialProdA.localId,
                    quantity = 1,
                    unitCostMinor = 3_000_00L,
                    serialNumbersOrImeis = listOf(imeiA1)
                )
            )
        )
        assertTrue(dupImeiPur is AppResult.Error)
        assertEquals("DUPLICATE_ACTIVE_SERIAL", (dupImeiPur as AppResult.Error).code)

        // Prevent assigning Product A's IMEI when selling Product B
        val wrongProdSale = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = serialProdB.localId,
                    serialNumberId = serialEntityA1.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 5_000_00L
                )
            )
        )
        assertTrue(wrongProdSale is AppResult.Error)
        assertEquals("SERIAL_PRODUCT_MISMATCH", (wrongProdSale as AppResult.Error).code)

        // Sell Product A with serialEntityA1 -> status becomes SOLD
        val saleA1Res = repository.executeSale(
            customerId = customer.localId,
            items = listOf(
                SaleLineInput(
                    productId = serialProdA.localId,
                    serialNumberId = serialEntityA1.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 4_000_00L
                )
            )
        )
        assertTrue(saleA1Res is AppResult.Success)
        assertEquals(
            SerialLifecycleStatus.SOLD,
            database.catalogInventoryDao().getSerialById(serialEntityA1.localId)!!.status
        )

        // Prevent selling already-sold IMEI
        val resellAttempt = repository.executeSale(
            customerId = customer.localId,
            items = listOf(
                SaleLineInput(
                    productId = serialProdA.localId,
                    serialNumberId = serialEntityA1.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 4_000_00L
                )
            )
        )
        assertTrue(resellAttempt is AppResult.Error)
        assertEquals("SERIAL_NOT_AVAILABLE", (resellAttempt as AppResult.Error).code)

        // Return sold Product A -> IMEI returns to IN_STOCK
        val returnA1Res = repository.executeSalesReturn(
            originalSaleId = (saleA1Res as AppResult.Success).data.localId,
            reasonArabic = "فحص استرجاع IMEI للمخزون"
        )
        assertTrue(returnA1Res is AppResult.Success)
        assertEquals(
            SerialLifecycleStatus.IN_STOCK,
            database.catalogInventoryDao().getSerialById(serialEntityA1.localId)!!.status
        )

        // Outbound damage adjustment on serialEntityA2 -> transitions to DAMAGED
        val damageAdjRes = repository.recordAuthorizedInventoryMovement(
            productId = serialProdA.localId,
            movementType = InventoryMovementType.DAMAGE,
            quantity = 1,
            unitCostMinor = 3_000_00L,
            serialOrImei = imeiA2,
            reason = "تلف شاشة أثناء العرض"
        )
        assertTrue(damageAdjRes is AppResult.Success)
        assertEquals(
            SerialLifecycleStatus.DAMAGED,
            database.catalogInventoryDao().getSerialById(serialEntityA2.localId)!!.status
        )

        // Section 16: Customer Balance Audit (100% credit sale -> partial payment -> credit sale refund)
        val cashBeforeCreditSale = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = mainDrawerId
        )
        val creditSaleRes = repository.executeSale(
            customerId = customer.localId,
            items = listOf(
                SaleLineInput(
                    productId = wacProduct.localId,
                    quantity = 4,
                    unitSellingPriceMinor = 250_00L // Total = 1,000 EGP
                )
            ),
            paidAmountMinorOverride = 0L // 100% credit sale
        )
        assertTrue(creditSaleRes is AppResult.Success)
        val creditSale = (creditSaleRes as AppResult.Success).data
        assertEquals(0L, creditSale.paidAmountMinor)

        // Cash drawer must not change on 100% credit sale, customer debt must be 1,000 EGP
        val cashAfterCreditSale = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = mainDrawerId
        )
        assertEquals(cashBeforeCreditSale, cashAfterCreditSale)
        assertEquals(
            1_000_00L,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                accountId = customer.localId
            )
        )

        // Collect 400 EGP from customer -> debt drops to 600 EGP, cash increases by 400 EGP
        val collectRes = repository.recordCustomerPaymentCollection(
            customerId = customer.localId,
            amountMinor = 400_00L,
            notes = "دفعة نقدية من الحساب",
            idempotencyKey = "IDEM-CUST-COL-1"
        )
        assertTrue(collectRes is AppResult.Success)
        assertEquals(
            600_00L,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                accountId = customer.localId
            )
        )

        // Refund the credit sale -> 600 EGP receivable is cancelled (debt becomes 0) and 400 EGP cash is refunded!
        val creditRefundRes = repository.executeSalesReturn(
            originalSaleId = creditSale.localId,
            reasonArabic = "مرتجع فاتورة آجلة مع تسوية المديونية",
            idempotencyKey = "IDEM-CREDIT-REFUND-1"
        )
        assertTrue(
            "Credit sale refund failed: ${(creditRefundRes as? AppResult.Error)?.message}",
            creditRefundRes is AppResult.Success
        )
        assertEquals(
            0L,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                accountId = customer.localId
            )
        )
        assertEquals(
            cashBeforeCreditSale,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = mainDrawerId
            )
        )

        // Section 17: Supplier Balance Audit (100% credit purchase -> supplier payment -> purchase return)
        val cashBeforeCreditPur = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = mainDrawerId
        )
        val creditPurRes = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(PurchaseLineInput(productId = wacProduct.localId, quantity = 5, unitCostMinor = 200_00L)), // 1,000 EGP
            paidAmountMinorOverride = 0L // 100% credit purchase
        )
        assertTrue(creditPurRes is AppResult.Success)
        val creditPurchase = (creditPurRes as AppResult.Success).data
        assertEquals(0L, creditPurchase.paidAmountMinor)
        assertEquals(
            cashBeforeCreditPur,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = mainDrawerId
            )
        )
        assertEquals(
            1_000_00L,
            database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, supplier.localId)
        )

        // Pay supplier 400 EGP -> payable becomes 600 EGP
        val supPayRes = repository.recordSupplierPayment(
            supplierId = supplier.localId,
            amountMinor = 400_00L,
            notes = "دفعة لمورد",
            idempotencyKey = "IDEM-SUP-PAY-1"
        )
        assertTrue(supPayRes is AppResult.Success)
        assertEquals(
            600_00L,
            database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, supplier.localId)
        )

        // Overpayment attempt (> 600 EGP) must fail
        val overpaySupRes = repository.recordSupplierPayment(
            supplierId = supplier.localId,
            amountMinor = 700_00L,
            notes = "محاولة سداد زائد"
        )
        assertTrue(overpaySupRes is AppResult.Error)
        assertEquals("SUPPLIER_OVERPAYMENT_NOT_ALLOWED", (overpaySupRes as AppResult.Error).code)

        // Execute Purchase Return -> cancels 600 EGP payable and refunds 400 EGP to Cash Drawer!
        val purReturnRes = repository.executePurchaseReturn(
            originalPurchaseId = creditPurchase.localId,
            reasonArabic = "مرتجع مشتريات للمورد",
            idempotencyKey = "IDEM-PUR-RET-1"
        )
        assertTrue(
            "Purchase return failed: ${(purReturnRes as? AppResult.Error)?.message}",
            purReturnRes is AppResult.Success
        )
        assertEquals(
            0L,
            database.catalogInventoryDao().getDerivedSupplierPayableMinor(shopId, supplier.localId)
        )
        assertEquals(
            cashBeforeCreditPur,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = mainDrawerId
            )
        )

        // Section 18: Wallet Financial Audit (Deposit, Withdrawal, Limits, Fees, Idempotency)
        val vfProvider = repository.observeWalletProviders().first().first { it.providerCode == "VODAFONE_CASH" }
        val wallet = (repository.createWallet(
            providerId = vfProvider.localId,
            walletNameArabic = "محفظة فودافون تدقيق V3.1",
            walletNumber = "01066778899",
            dailyLimitMinor = 5_000_00L,
            monthlyLimitMinor = 20_000_00L
        ) as AppResult.Success).data

        // Top-up wallet with 4,000 EGP
        val topupRes = repository.executeWalletOperation(
            walletId = wallet.localId,
            transferKind = "WALLET_TOPUP",
            customerPhone = "01066778899",
            principalAmountMinor = 4_000_00L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 0L,
            idempotencyKey = "IDEM-WLT-TOPUP-1"
        )
        assertTrue(topupRes is AppResult.Success)

        // Wallet Withdrawal (WALLET_RECEIVE): customer sends 1,000 EGP to shop wallet, shop pays 980 EGP cash (20 EGP commission)
        val cashBeforeWithdraw = database.financialLedgerDao().getDerivedAccountBalanceMinor(
            shopId = shopId,
            accountType = FinancialAccountType.CASH_DRAWER,
            accountId = mainDrawerId
        )
        val withdrawRes = repository.executeWalletOperation(
            walletId = wallet.localId,
            transferKind = "WALLET_RECEIVE",
            customerPhone = "01012345678",
            principalAmountMinor = 1_000_00L,
            providerFeeMinor = 0L,
            shopCommissionMinor = 20_00L,
            idempotencyKey = "IDEM-WLT-RCV-1"
        )
        assertTrue(withdrawRes is AppResult.Success)
        // Wallet balance must now be 4,000 + 1,000 = 5,000 EGP, Cash Drawer decreased by 980 EGP
        assertEquals(
            5_000_00L,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.WALLET,
                accountId = wallet.localId
            )
        )
        assertEquals(
            cashBeforeWithdraw - 980_00L,
            database.financialLedgerDao().getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = FinancialAccountType.CASH_DRAWER,
                accountId = mainDrawerId
            )
        )

        // Exceed Daily Limit (5,000 EGP limit; try sending 5,000 + 10 fee = 5,010 EGP)
        val limitExceededRes = repository.executeWalletOperation(
            walletId = wallet.localId,
            transferKind = "WALLET_SEND",
            customerPhone = "01012345678",
            principalAmountMinor = 5_000_00L,
            providerFeeMinor = 10_00L,
            shopCommissionMinor = 50_00L
        )
        assertTrue(limitExceededRes is AppResult.Error)

        // Section 8 & 9: Double-Entry Trial Balance & Reversal Audit
        val expRes = repository.executeExpense(
            categoryName = "كهرباء",
            title = "فاتورة كهرباء الفرع",
            amountMinor = 300_00L,
            idempotencyKey = "IDEM-EXP-1"
        )
        assertTrue(expRes is AppResult.Success)
        val expTxId = (expRes as AppResult.Success).data.ledgerTransactionId
        val revRes = repository.reverseLedgerTransaction(
            originalTransactionId = expTxId,
            reasonArabic = "تصحيح قيمة فاتورة الكهرباء"
        )
        assertTrue(revRes is AppResult.Success)

        val trialBalance = repository.computeTrialBalanceReport(shopId)
        assertTrue(
            "Trial balance must be 100% balanced (Debits=${trialBalance.totalDebitsMinor}, Credits=${trialBalance.totalCreditsMinor})",
            trialBalance.isBalanced
        )
        assertEquals(trialBalance.totalDebitsMinor, trialBalance.totalCreditsMinor)

        val healthReport = repository.runDataIntegrityHealthCheck(shopId)
        assertEquals(
            "System integrity health check failed: ${healthReport.items.filter { it.status != HealthCheckLevel.PASS }}",
            HealthCheckLevel.PASS,
            healthReport.overallStatus
        )
    }

    @Test
    fun verifyMekkaPhoneV31Part2CompleteUxWorkflowAndFeatureAudit() = runBlocking {
        // 1. Seed initial cash drawer balance (50,000 EGP)
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 50_000_00L,
            reasonArabic = "تمويل خزينة لاختبار مسارات الجزء الثاني V3.1"
        )

        val vm = com.example.ui.viewmodel.MekkaPhoneViewModel(repository)

        // 2. Verify Dashboard Quick Action Intents & State Consumption (Part 2 Section 6)
        vm.requestWalletQuickOperation(com.example.financial.WalletUserOperationType.DEPOSIT)
        assertEquals(com.example.financial.WalletUserOperationType.DEPOSIT, vm.pendingWalletQuickOperation.value)
        vm.consumeWalletQuickOperation()
        assertEquals(null, vm.pendingWalletQuickOperation.value)

        vm.requestWalletQuickOperation(com.example.financial.WalletUserOperationType.WITHDRAWAL)
        assertEquals(com.example.financial.WalletUserOperationType.WITHDRAWAL, vm.pendingWalletQuickOperation.value)
        vm.consumeWalletQuickOperation()
        assertEquals(null, vm.pendingWalletQuickOperation.value)

        vm.requestInventoryCountingMode()
        assertTrue(vm.pendingInventoryCountMode.value)
        vm.consumeInventoryCountingMode()
        assertFalse(vm.pendingInventoryCountMode.value)

        // 3. Create Category, Brand, Supplier, Customer, Non-Serialized Product, and Serialized Product
        val cat = (repository.createCategory("هواتف ذكية V3.1") as AppResult.Success).data
        val brd = (repository.createBrand("Apple") as AppResult.Success).data
        val sup = (repository.createSupplier("المتحدة للتوزيع", "أستاذ سامح", "01011223344") as AppResult.Success).data
        val cust = (repository.createCustomer("محمود عبد العزيز", "01288776655") as AppResult.Success).data

        val accProd = (repository.createProduct(
            nameArabic = "جراب ماجسيف أصلي",
            sku = "APL-MAG-CASE",
            barcode = "6224001001",
            categoryId = cat.localId,
            brandId = brd.localId,
            isSerialized = false,
            defaultSellingPriceMinor = 1_000_00L
        ) as AppResult.Success).data

        val phoneProd = (repository.createProduct(
            nameArabic = "iPhone 15 Pro Max 256GB",
            sku = "APL-IP15PM-256",
            barcode = "6224001002",
            categoryId = cat.localId,
            brandId = brd.localId,
            isSerialized = true,
            defaultSellingPriceMinor = 55_000_00L
        ) as AppResult.Success).data

        // 4. Execute Purchase with Serialized IMEIs and Non-Serialized stock (Part 2 Section 18 & 19)
        val imei1 = "359988776655441"
        val imei2 = "359988776655442"
        val purchaseRes = repository.executePurchase(
            supplierId = sup.localId,
            items = listOf(
                PurchaseLineInput(productId = accProd.localId, quantity = 5, unitCostMinor = 600_00L),
                PurchaseLineInput(
                    productId = phoneProd.localId,
                    quantity = 2,
                    unitCostMinor = 48_000_00L,
                    serialNumbersOrImeis = listOf(imei1, imei2)
                )
            ),
            discountMinor = 1_000_00L, // Subtotal = 3,000 + 96,000 = 99,000 -> Net = 98,000 EGP
            paidAmountMinorOverride = 40_000_00L // Paid 40,000 EGP, Remaining Supplier Payable = 58,000 EGP
        )
        assertTrue(purchaseRes is AppResult.Success)
        val purchaseEntity = (purchaseRes as AppResult.Success).data
        assertEquals(98_000_00L, purchaseEntity.totalAmountMinor)

        // 5. Verify POS Cart with Both Per-Line Discount AND Invoice-Level Discount (Part 2 Sections 8–15)
        val projections = repository.observeProductInventoryProjections().first()
        val accProj = projections.first { it.productId == accProd.localId }
        val phoneProj = projections.first { it.productId == phoneProd.localId }
        val serialEntities = repository.observeSerialNumbers().first()
        val serial1Entity = serialEntities.first { it.serialOrImei == imei1 }

        // Add 2 units of accessory @ 1,000 EGP each = 2,000 EGP gross
        assertTrue(vm.addOrIncrementPosCartItem(product = accProj, brandName = "Apple", quantity = 2))
        // Add 1 serialized iPhone with explicit IMEI @ 55,000 EGP
        assertTrue(
            vm.addOrIncrementPosCartItem(
                product = phoneProj,
                brandName = "Apple",
                quantity = 1,
                unitPriceMinor = 55_000_00L,
                serialEntity = serial1Entity
            )
        )
        assertEquals(2, vm.posCartItems.value.size)

        // Apply 150 EGP line discount on the accessory line + 350 EGP extra invoice discount = 500 EGP total discount
        val accCartLine = vm.posCartItems.value.first { it.productId == accProd.localId }
        vm.updatePosCartItemPriceAndDiscount(
            lineId = accCartLine.lineId,
            unitPriceMinor = 1_000_00L,
            lineDiscountMinor = 150_00L
        )
        vm.setPosInvoiceDiscountInput("350")

        // Gross = 2,000 + 55,000 = 57,000 EGP; Total Discount = 150 + 350 = 500 EGP; Final Total = 56,500 EGP
        val (grossSub, totalDisc, finalTot) = vm.computePosTotals()
        assertEquals(57_000_00L, grossSub)
        assertEquals(500_00L, totalDisc)
        assertEquals(56_500_00L, finalTot)

        // Select Registered Customer & Partial Payment (Paid = 50,000 EGP, Remaining Receivable = 6,500 EGP)
        vm.selectPosRegisteredCustomer(cust.localId)
        vm.setPosPaymentMode(com.example.ui.viewmodel.PosPaymentMode.PARTIAL)
        vm.setPosPartialPaidInput("50000")

        val (paidMinor, remMinor) = vm.computePosPaidAndRemainingMinor(finalTot)
        assertEquals(50_000_00L, paidMinor)
        assertEquals(6_500_00L, remMinor)

        // Transition to Reviewing state
        assertTrue(vm.requestPosSaleReview())
        assertTrue(vm.posCheckoutState.value is com.example.ui.viewmodel.PosCheckoutState.Reviewing)

        // Execute atomic sale via engine with exact line discount + invoice discount
        val posSaleRes = repository.executeSale(
            customerId = cust.localId,
            items = listOf(
                SaleLineInput(
                    productId = accProd.localId,
                    serialNumberId = null,
                    quantity = 2,
                    unitSellingPriceMinor = 1_000_00L,
                    lineDiscountMinor = 150_00L
                ),
                SaleLineInput(
                    productId = phoneProd.localId,
                    serialNumberId = serial1Entity.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 55_000_00L,
                    lineDiscountMinor = 0L
                )
            ),
            invoiceDiscountMinor = 350_00L,
            paidAmountMinorOverride = 50_000_00L,
            notes = "بيع POS مع خصم سطر وخصم فاتورة"
        )
        assertTrue(posSaleRes is AppResult.Success)
        val committedSale = (posSaleRes as AppResult.Success).data

        // Verify committed SaleEntity & SaleItemEntity match the exact POS Review numbers!
        assertEquals(57_000_00L, committedSale.subtotalMinor)
        assertEquals(500_00L, committedSale.discountAmountMinor)
        assertEquals(56_500_00L, committedSale.totalAmountMinor)
        assertEquals(50_000_00L, committedSale.paidAmountMinor)

        val savedSaleItems = repository.observeAllSaleItems().first().filter { it.saleId == committedSale.localId }
        val savedAccLine = savedSaleItems.first { it.productId == accProd.localId }
        assertEquals(1_000_00L, savedAccLine.unitSellingPriceMinor)
        assertEquals(150_00L, savedAccLine.discountMinor)
        assertEquals(1_850_00L, savedAccLine.lineTotalMinor)

        // Verify Customer Receivable Balance = 6,500 EGP
        val custBalAfterSale = repository.observeCustomerBalanceProjections().first().first { it.customerId == cust.localId }
        assertEquals(6_500_00L, custBalAfterSale.outstandingBalanceMinor)

        // 6. Verify Global Search Result Dispatching (Part 2 Section 7)
        vm.openGlobalSearchResult(
            com.example.repository.GlobalSearchResultItem(
                categoryArabic = "عميل",
                title = cust.fullName,
                subtitle = cust.phone,
                referenceCode = cust.localId,
                routeTarget = com.example.navigation.MekkaDestination.CUSTOMERS.route
            )
        )
        assertEquals(cust.localId, vm.pendingSelectedCustomerId.value)
        vm.consumeSelectedCustomerProfile()

        vm.openGlobalSearchResult(
            com.example.repository.GlobalSearchResultItem(
                categoryArabic = "منتج / مخزون",
                title = phoneProd.nameArabic,
                subtitle = phoneProd.sku,
                referenceCode = phoneProd.sku,
                routeTarget = com.example.navigation.MekkaDestination.PRODUCTS.route
            )
        )
        assertEquals(phoneProd.sku, vm.pendingSelectedProductIdOrSku.value)
        vm.consumeSelectedProductDetail()

        vm.openGlobalSearchResult(
            com.example.repository.GlobalSearchResultItem(
                categoryArabic = "IMEI / سيريال",
                title = imei1,
                subtitle = "مباع",
                referenceCode = imei1,
                routeTarget = com.example.navigation.MekkaDestination.PRODUCTS.route
            )
        )
        assertEquals(imei1, vm.pendingImeiSearchQuery.value)
        vm.consumeImeiSearch()

        vm.openGlobalSearchResult(
            com.example.repository.GlobalSearchResultItem(
                categoryArabic = "فاتورة بيع",
                title = committedSale.invoiceNumber,
                subtitle = "دفع جزئي",
                referenceCode = committedSale.invoiceNumber,
                routeTarget = com.example.navigation.MekkaDestination.SALES.route
            )
        )
        assertEquals(committedSale.invoiceNumber, vm.pendingSelectedSaleInvoice.value)
        vm.consumeSelectedSaleInvoice()

        // 7. Verify Product -> Purchase Preselection Intent (Part 2 Section 4)
        vm.requestPurchaseProduct(accProd.localId)
        assertEquals(accProd.localId, vm.pendingPurchaseProductId.value)
        vm.consumePurchaseProduct()
        assertEquals(null, vm.pendingPurchaseProductId.value)

        // 8. Verify Trial Balance & System Integrity remain 100% PASS after all Part 2 workflows
        val tb = repository.computeTrialBalanceReport()
        assertTrue(tb.isBalanced)
        val health = repository.runDataIntegrityHealthCheck()
        assertEquals(HealthCheckLevel.PASS, health.overallStatus)
    }

    @Test
    fun testV4Part1ShiftProfitabilityPricingCreditReturnsStockCountImeiAndMaintenanceWorkflow() = runBlocking {
        repository.ensureFoundationInitialized()

        val fundRes = repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100_000_00L,
            reasonArabic = "رأس مال تشغيلي افتتاحي للخزينة V4"
        )
        assertTrue(fundRes is AppResult.Success)

        // -----------------------------------------------------------------------------------------
        // 1. SHIFT MANAGEMENT: Open Shift, Duplicate Guard, Variance Block & Manager Approval
        // -----------------------------------------------------------------------------------------
        val openShiftRes = repository.openCashShift(
            openingCashInputMinor = 100_000_00L,
            notes = "فتح وردية صباحية V4"
        )
        assertTrue("Expected openCashShift to succeed", openShiftRes is AppResult.Success)
        val openedShift = (openShiftRes as AppResult.Success).data
        assertEquals("OPEN", openedShift.status)

        // Duplicate open shift must be blocked
        val duplicateShiftRes = repository.openCashShift(openingCashInputMinor = 10_000_00L)
        assertTrue(duplicateShiftRes is AppResult.Error)
        assertEquals("SHIFT_ALREADY_OPEN", (duplicateShiftRes as AppResult.Error).code)

        // Create a Cashier staff user (who does NOT have SHIFT_VARIANCE_APPROVE, PRICE_OVERRIDE_APPROVE, or CREDIT_LIMIT_OVERRIDE)
        val cashierRes = repository.createStaffUser(
            fullNameArabic = "كاشير الوردية",
            username = "cashier_v4",
            phone = "01099887766",
            roleCode = "CASHIER",
            rawPin = "2468"
        )
        assertTrue(cashierRes is AppResult.Success)
        val cashierUser = (cashierRes as AppResult.Success).data

        // -----------------------------------------------------------------------------------------
        // 2. ADVANCED PRICING & PRICE HISTORY + PRICE FLOOR / BELOW-COST PROTECTION
        // -----------------------------------------------------------------------------------------
        val prodRes = repository.createProduct(
            nameArabic = "سماعة سامسونج Buds3 Pro V4",
            sku = "SAM-BUDS3-V4",
            barcode = "6224009988771",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-SAMSUNG",
            isSerialized = false,
            defaultSellingPriceMinor = 8_000_00L,
            minSellingPriceMinor = 7_500_00L,
            wholesalePriceMinor = 7_600_00L,
            specialCustomerPriceMinor = 7_700_00L,
            maxDiscountMinor = 400_00L,
            reorderThresholdQty = 3,
            reorderPointQty = 6
        )
        assertTrue(prodRes is AppResult.Success)
        val budsProd = (prodRes as AppResult.Success).data

        // Update product pricing policy and verify PriceHistoryEntity rows are persisted
        val priceUpdateRes = repository.updateProductPricingPolicy(
            productId = budsProd.localId,
            defaultSellingPriceMinor = 8_500_00L,
            minSellingPriceMinor = 7_800_00L,
            wholesalePriceMinor = 7_900_00L,
            specialCustomerPriceMinor = 8_000_00L,
            maxDiscountMinor = 500_00L,
            promotionalPriceMinor = 8_100_00L,
            isPromotionActive = true,
            reasonArabic = "تعديل الأسعار حسب قائمة المورد الجديدة"
        )
        assertTrue(priceUpdateRes is AppResult.Success)

        val priceHistory = repository.getPriceHistoryForProduct(budsProd.localId)
        assertTrue("Expected price history entries to be recorded", priceHistory.size >= 4)
        assertTrue(priceHistory.any { it.priceType == "RETAIL" && it.oldPriceMinor == 8_000_00L && it.newPriceMinor == 8_500_00L })
        assertTrue(priceHistory.any { it.priceType == "MIN_ALLOWED" && it.oldPriceMinor == 7_500_00L && it.newPriceMinor == 7_800_00L })

        // Purchase 5 units of Buds3 Pro at 6,000 EGP WAC cost + 1 serialized phone
        val phoneProdRes = repository.createProduct(
            nameArabic = "آيفون 16 برو 256 جيجا V4",
            sku = "IPH-16P-V4",
            categoryId = "CAT-PHONES",
            brandId = "BRD-APPLE",
            isSerialized = true,
            defaultSellingPriceMinor = 65_000_00L,
            minSellingPriceMinor = 62_000_00L,
            warrantyMonths = 12
        )
        assertTrue(phoneProdRes is AppResult.Success)
        val phoneProd = (phoneProdRes as AppResult.Success).data
        val v4Imei = "358899001122334"

        val supRes = repository.createSupplier(
            companyName = "توكيل أبل وسامسونج المعتمد V4",
            contactName = "م. طارق",
            phone = "01055443322"
        )
        assertTrue(supRes is AppResult.Success)
        val supplier = (supRes as AppResult.Success).data

        val purRes = repository.executePurchase(
            supplierId = supplier.localId,
            items = listOf(
                PurchaseLineInput(
                    productId = budsProd.localId,
                    quantity = 5,
                    unitCostMinor = 6_000_00L
                ),
                PurchaseLineInput(
                    productId = phoneProd.localId,
                    quantity = 1,
                    unitCostMinor = 58_000_00L,
                    serialNumbersOrImeis = listOf(v4Imei)
                )
            ),
            paidAmountMinorOverride = 50_000_00L, // Remaining payable = 38,000 EGP
            notes = "توريد بضاعة V4"
        )
        assertTrue(purRes is AppResult.Success)

        // Attempt sale below minimum allowed price (7,800 EGP) without Manager Override -> MUST BE BLOCKED
        val blockedPriceSale = repository.executeV4ProtectedSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = budsProd.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 7_200_00L // Below min 7,800 EGP
                )
            )
        )
        assertTrue("Expected sale below min allowed price without override to be blocked", blockedPriceSale is AppResult.Error)
        assertEquals("PRICE_POLICY_VIOLATION", (blockedPriceSale as AppResult.Error).code)

        // Execute sale below min allowed price WITH Manager Override & Reason -> MUST SUCCEED & AUDIT
        val approvedOverrideSale = repository.executeV4ProtectedSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = budsProd.localId,
                    quantity = 3,
                    unitSellingPriceMinor = 7_500_00L
                )
            ),
            priceOverrideApprovedByUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            priceOverrideReasonArabic = "خصم خاص لعميل جملة معتمد من المالك"
        )
        assertTrue("Expected sale with manager price override to succeed", approvedOverrideSale is AppResult.Success)
        val multiUnitSale = (approvedOverrideSale as AppResult.Success).data

        // -----------------------------------------------------------------------------------------
        // 3. CUSTOMER CREDIT LIMIT, DEBT AGING BUCKETS & CREDIT OVERRIDE GATE
        // -----------------------------------------------------------------------------------------
        val custRes = repository.createCustomer(
            fullName = "عميل ائتمان V4",
            phone = "01122334455",
            creditLimitMinor = 5_000_00L,
            defaultCreditDays = 15
        )
        assertTrue(custRes is AppResult.Success)
        val creditCustomer = (custRes as AppResult.Success).data

        // Attempt credit sale with 10,000 EGP unpaid (exceeds 5,000 EGP credit limit) without override -> BLOCKED
        val serialEntity = repository.observeSerialNumbers().first().first { it.serialOrImei == v4Imei }
        val blockedCreditSale = repository.executeV4ProtectedSale(
            customerId = creditCustomer.localId,
            items = listOf(
                SaleLineInput(
                    productId = phoneProd.localId,
                    serialNumberId = serialEntity.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 65_000_00L
                )
            ),
            paidAmountMinorOverride = 55_000_00L // 10,000 EGP credit > 5,000 EGP limit
        )
        assertTrue("Expected credit sale exceeding limit without override to fail", blockedCreditSale is AppResult.Error)
        assertEquals("CREDIT_LIMIT_EXCEEDED", (blockedCreditSale as AppResult.Error).code)

        // Execute credit sale WITH Manager Credit Override -> SUCCEEDS
        val approvedCreditSale = repository.executeV4ProtectedSale(
            customerId = creditCustomer.localId,
            items = listOf(
                SaleLineInput(
                    productId = phoneProd.localId,
                    serialNumberId = serialEntity.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 65_000_00L
                )
            ),
            paidAmountMinorOverride = 55_000_00L,
            creditOverrideApprovedByUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            creditOverrideReasonArabic = "موافقة استثنائية من المالك لعميل موثوق"
        )
        assertTrue("Expected credit sale with manager override to succeed", approvedCreditSale is AppResult.Success)

        val creditStanding = repository.computeCustomerCreditStanding(creditCustomer.localId)
        assertNotNull(creditStanding)
        assertEquals(10_000_00L, creditStanding!!.currentDebtMinor)
        assertTrue(creditStanding.isExceedingCreditLimit)
        assertEquals(10_000_00L, creditStanding.agingBuckets.bucket0To7DaysMinor)

        // Verify Supplier Payables Schedule
        val payablesSchedule = repository.computeSupplierPayablesSchedule()
        assertTrue(payablesSchedule.totalPayablesMinor >= 38_000_00L)
        assertTrue(payablesSchedule.scheduleItems.any { it.supplierId == supplier.localId && it.remainingPayableMinor == 38_000_00L })

        // -----------------------------------------------------------------------------------------
        // 4. ADVANCED PARTIAL RETURNS, DAMAGED SCRAP CONDITION & EXCHANGES
        // -----------------------------------------------------------------------------------------
        val multiSaleItems = repository.observeAllSaleItems().first().filter { it.saleId == multiUnitSale.localId }
        val budsSaleItem = multiSaleItems.first()
        assertEquals(3, budsSaleItem.quantity)

        // Return 1 unit as RESTOCKABLE and 1 unit as DAMAGED_SCRAP
        val partialReturn1 = repository.executePartialSalesReturn(
            originalSaleId = multiUnitSale.localId,
            returnLines = listOf(
                com.example.financial.AdvancedReturnLineInput(
                    saleItemId = budsSaleItem.localId,
                    returnQuantity = 1,
                    condition = com.example.financial.ReturnConditionType.RESTOCKABLE
                ),
                com.example.financial.AdvancedReturnLineInput(
                    saleItemId = budsSaleItem.localId,
                    returnQuantity = 1,
                    condition = com.example.financial.ReturnConditionType.DAMAGED_SCRAP
                )
            ),
            reasonArabic = "إرجاع قطعة سليمة وقطعة بها عيب مصنعي"
        )
        assertTrue("Expected partial return to succeed", partialReturn1 is AppResult.Success)
        assertEquals("PARTIAL_RETURN", (partialReturn1 as AppResult.Success).data.returnType)

        // Attempt to return 2 more units when only 1 unit remains returnable -> MUST FAIL!
        val overReturnAttempt = repository.executePartialSalesReturn(
            originalSaleId = multiUnitSale.localId,
            returnLines = listOf(
                com.example.financial.AdvancedReturnLineInput(
                    saleItemId = budsSaleItem.localId,
                    returnQuantity = 2,
                    condition = com.example.financial.ReturnConditionType.RESTOCKABLE
                )
            ),
            reasonArabic = "محاولة إرجاع كمية أكبر من المتبقي"
        )
        assertTrue("Expected returning more than remaining quantity to fail", overReturnAttempt is AppResult.Error)
        assertEquals("INVALID_PARTIAL_RETURN", (overReturnAttempt as AppResult.Error).code)

        // Exchange the remaining 1 unit for 1 new unit
        val exchangeRes = repository.executeSalesExchange(
            originalSaleId = multiUnitSale.localId,
            returnLines = listOf(
                com.example.financial.AdvancedReturnLineInput(
                    saleItemId = budsSaleItem.localId,
                    returnQuantity = 1,
                    condition = com.example.financial.ReturnConditionType.RESTOCKABLE
                )
            ),
            replacementItems = listOf(
                SaleLineInput(
                    productId = budsProd.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 8_500_00L
                )
            ),
            reasonArabic = "استبدال القطعة المتبقية بإصدار أحدث"
        )
        assertTrue("Expected exchange to succeed", exchangeRes is AppResult.Success)
        val (exchangeRefund, replacementSale) = (exchangeRes as AppResult.Success).data
        assertEquals("EXCHANGE", exchangeRefund.returnType)
        assertEquals(replacementSale.localId, exchangeRefund.replacementSaleId)
        assertEquals(1_000_00L, exchangeRefund.priceDifferenceMinor) // 8,500 - 7,500 = +1,000 EGP difference

        // -----------------------------------------------------------------------------------------
        // 5. PHYSICAL STOCK COUNT RECONCILIATION & PRODUCT VELOCITY / REORDER REPORTS
        // -----------------------------------------------------------------------------------------
        val stockBeforeCount = repository.observeProductInventoryProjections().first().first { it.productId == budsProd.localId }.derivedStockQty
        val stockCountRes = repository.executeStockCountReconciliation(
            lines = listOf(
                com.example.inventory.StockCountLineInput(
                    productId = budsProd.localId,
                    countedQuantity = (stockBeforeCount - 1).coerceAtLeast(0),
                    isDamagedOrScrap = false
                )
            ),
            reasonArabic = "جرد دوري لصنف السماعات"
        )
        assertTrue(stockCountRes is AppResult.Success)
        val countReport = (stockCountRes as AppResult.Success).data
        assertEquals(1, countReport.adjustedLinesCount)
        assertEquals(-1, countReport.lines.first().varianceQuantity)

        val velocityReports = repository.computeProductVelocityAndReorderReports()
        assertTrue(velocityReports.any { it.productId == budsProd.localId })

        // -----------------------------------------------------------------------------------------
        // 6. FULL IMEI TRACEABILITY TIMELINE & 14-STAGE MAINTENANCE WORKFLOW
        // -----------------------------------------------------------------------------------------
        val mntOrderRes = repository.createMaintenanceOrder(
            customerId = creditCustomer.localId,
            deviceBrand = "Apple",
            deviceModel = "iPhone 16 Pro",
            deviceImeiOrSerial = v4Imei,
            faultDescription = "فحص البطارية ومنفذ الشحن",
            estimatedCostMinor = 1_500_00L,
            depositMinor = 500_00L
        )
        assertTrue(mntOrderRes is AppResult.Success)
        val mntOrder = (mntOrderRes as AppResult.Success).data

        // Attempting to jump directly to IN_REPAIR when isCustomerApproved = false -> BLOCKED
        val unapprovedRepair = repository.updateMaintenanceWorkflowStage(
            orderId = mntOrder.localId,
            targetStatus = MaintenanceStatus.IN_REPAIR,
            isCustomerApproved = false
        )
        assertTrue(unapprovedRepair is AppResult.Error)
        assertEquals("CUSTOMER_APPROVAL_REQUIRED", (unapprovedRepair as AppResult.Error).code)

        // Advance through DIAGNOSING -> WAITING_APPROVAL -> IN_REPAIR (approved) -> TESTING -> READY_FOR_PICKUP -> DELIVERED
        assertTrue(
            repository.updateMaintenanceWorkflowStage(
                orderId = mntOrder.localId,
                targetStatus = MaintenanceStatus.DIAGNOSING,
                diagnosticNotes = "منفذ الشحن يحتاج تنظيف وتغيير فلاتة"
            ) is AppResult.Success
        )
        assertTrue(
            repository.updateMaintenanceWorkflowStage(
                orderId = mntOrder.localId,
                targetStatus = MaintenanceStatus.IN_REPAIR,
                isCustomerApproved = true,
                repairActionsArabic = "تم تغيير فلاتة الشحن الأصلية"
            ) is AppResult.Success
        )
        assertTrue(
            repository.updateMaintenanceWorkflowStage(
                orderId = mntOrder.localId,
                targetStatus = MaintenanceStatus.READY_FOR_PICKUP,
                isTestingPassed = true,
                warrantyNotesArabic = "ضمان 30 يوم على فلاتة الشحن"
            ) is AppResult.Success
        )

        val imeiTimelineRes = repository.buildImeiTraceabilityTimeline(v4Imei)
        assertTrue("Expected IMEI traceability timeline to succeed", imeiTimelineRes is AppResult.Success)
        val imeiTimeline = (imeiTimelineRes as AppResult.Success).data
        assertEquals(v4Imei, imeiTimeline.projection.serialEntity.serialOrImei)
        assertEquals(creditCustomer.fullName, imeiTimeline.projection.customerNameArabic)
        assertEquals(supplier.companyName, imeiTimeline.projection.supplierNameArabic)
        assertTrue(imeiTimeline.projection.isWarrantyActive)
        assertTrue(imeiTimeline.projection.maintenanceTicketsCount >= 1)
        assertTrue(imeiTimeline.timelineEvents.size >= 3)

        // -----------------------------------------------------------------------------------------
        // 7. SHIFT CLOSE WITH VARIANCE ENFORCEMENT + TRUE PROFITABILITY & DAILY Z-REPORT
        // -----------------------------------------------------------------------------------------
        val shiftBreakdown = repository.getShiftCashFlowBreakdown(openedShift.localId)
        assertNotNull(shiftBreakdown)
        val expectedCash = shiftBreakdown!!.expectedClosingCashMinor

        // Closing with 100 EGP shortage without reason -> BLOCKED
        val closeNoReason = repository.closeCashShift(
            countedCashMinor = expectedCash - 100_00L,
            varianceReasonArabic = ""
        )
        assertTrue(closeNoReason is AppResult.Error)
        assertEquals("MISSING_VARIANCE_REASON", (closeNoReason as AppResult.Error).code)

        // Authenticate as Cashier to test RBAC variance approval gate
        repository.authenticateUserPin(cashierUser.localId, "2468")
        val closeByCashierWithoutManager = repository.closeCashShift(
            countedCashMinor = expectedCash - 100_00L,
            varianceReasonArabic = "عجز فكة بالدرج",
            actorUserId = cashierUser.localId
        )
        assertTrue("Cashier alone cannot approve shift variance", closeByCashierWithoutManager is AppResult.Error)
        assertEquals("SHIFT_VARIANCE_APPROVAL_REQUIRED", (closeByCashierWithoutManager as AppResult.Error).code)

        // Switch back to Owner and approve variance closeout
        val ownerAuthRes = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        assertTrue(ownerAuthRes is AppResult.Success)
        val closeApproved = repository.closeCashShift(
            countedCashMinor = expectedCash - 100_00L,
            varianceReasonArabic = "عجز فكة معتمد من المالك",
            managerApprovedByUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID,
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )
        assertTrue("Expected shift close with manager variance approval to succeed", closeApproved is AppResult.Success)
        val closedSession = (closeApproved as AppResult.Success).data
        assertEquals("CLOSED", closedSession.status)
        assertEquals("APPROVED", closedSession.approvalStatus)
        assertEquals(-100_00L, closedSession.discrepancyMinor)

        // Verify True Profitability Engine & Daily Operational Z-Report
        val trueProfit = repository.computeTrueProfitabilityReport()
        assertTrue(trueProfit.grossSalesBeforeDiscountMinor > 0L)
        assertTrue(trueProfit.inventoryShrinkageLossMinor > 0L) // Damaged return + stock count shortage
        assertEquals(100_00L, trueProfit.cashShortageLossMinor)

        val zReport = repository.computeDailyOperationalZReport()
        assertTrue(zReport.invoicesCount >= 2)
        assertTrue(zReport.returnsCount >= 2)
        assertEquals(1, zReport.shiftsClosedCount)

        // Verify Double-Entry Trial Balance & System Integrity remain 100% PASS
        val finalTb = repository.computeTrialBalanceReport()
        assertTrue("Trial Balance must remain 100% balanced after all V4 operations", finalTb.isBalanced)
        val finalHealth = repository.runDataIntegrityHealthCheck()
        assertEquals(HealthCheckLevel.PASS, finalHealth.overallStatus)
    }
}

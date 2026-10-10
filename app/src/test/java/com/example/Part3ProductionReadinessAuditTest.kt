package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.PermissionCode
import com.example.auth.RolePermissionMatrix
import com.example.backup.RestoreMode
import com.example.core.AppResult
import com.example.core.SyncState
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementType
import com.example.diagnostics.HealthCheckLevel
import com.example.financial.PurchaseLineInput
import com.example.financial.ReportsCenterFilter
import com.example.financial.SaleLineInput
import com.example.repository.MekkaPhoneRepository
import com.example.sync.SyncUiStatus
import com.example.ui.viewmodel.MekkaPhoneViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part3ProductionReadinessAuditTest {

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
    fun section4_authenticationFlow_firstLaunch_wrongPin_lockout_logout_andSessionExpiry() = runBlocking {
        // 1. First-launch onboarding replaces bootstrap PIN with user-chosen PBKDF2-HMAC-SHA256 PIN
        val onboardRes = repository.completeFirstLaunchSetup(
            ownerFullNameArabic = "أحمد مكة",
            shopNameArabic = "مكة فون الرئيسي",
            phoneOrAccount = "01099887766",
            rawPin = "9876",
            confirmPin = "9876"
        )
        assertTrue("First launch setup must succeed", onboardRes is AppResult.Success)
        val ownerUser = database.coreShopAuthDao().getUserById(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID)!!
        val storedHash = ownerUser.pinHashSha256.orEmpty()
        assertTrue("PIN hash must use PBKDF2-HMAC-SHA256 format", storedHash.startsWith("pbkdf2$"))
        assertFalse("Plaintext PIN must never be stored in hash", storedHash.contains("9876"))

        // 2. Old bootstrap PIN ("1111") must now be rejected
        val oldPinRes = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        assertTrue("Old bootstrap PIN must fail", oldPinRes is AppResult.Error)

        // 3. New PIN ("9876") succeeds
        val validLogin = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "9876")
        assertTrue("New Owner PIN must succeed", validLogin is AppResult.Success)

        // 4. Repeated wrong PINs (5 attempts) trigger brute-force lockout on Cashier account
        repeat(5) {
            val wrongRes = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_CASHIER_USER_ID, "0000")
            assertTrue(wrongRes is AppResult.Error)
        }
        val lockedOutAttemptWithCorrectPin = repository.authenticateUserPin(
            MekkaPhoneRepository.DEFAULT_CASHIER_USER_ID,
            "2222"
        )
        assertTrue(
            "Account must be locked after 5 failed attempts even if correct PIN is entered",
            lockedOutAttemptWithCorrectPin is AppResult.Error
        )
        assertEquals("AUTH_ACCOUNT_LOCKED_RATE_LIMIT", (lockedOutAttemptWithCorrectPin as AppResult.Error).code)

        // 5. Logout / Lock Session blocks all protected repository operations
        repository.lockSession()
        assertTrue("Session must be marked locked after lockSession()", repository.isSessionLocked.value)

        val blockedCreateCustomer = repository.createCustomer(
            fullName = "عميل أثناء القفل",
            phone = "01012345678"
        )
        assertTrue("Operations while session is locked must fail", blockedCreateCustomer is AppResult.Error)
        assertEquals("UNAUTHENTICATED_SESSION", (blockedCreateCustomer as AppResult.Error).code)

        // 6. Unlock session with valid Owner PIN
        val unlockRes = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "9876")
        assertTrue("Unlock with valid PIN must succeed", unlockRes is AppResult.Success)
        assertFalse("Session must no longer be locked", repository.isSessionLocked.value)

        // 7. Session Expiration test (simulate 13 hours elapsed > 12h TTL)
        val activeSession = repository.activeSession.value!!
        val futureTimestamp = System.currentTimeMillis() + (13L * 60L * 60L * 1000L)
        val expiredAuthCheck = repository.authSecurityManager.authorizeSessionAction(
            session = activeSession,
            requiredPermission = PermissionCode.SALE_CREATE,
            targetShopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            nowMillis = futureTimestamp
        )
        assertTrue("Expired session must be rejected", expiredAuthCheck is AppResult.Error)
        assertEquals("SESSION_EXPIRED", (expiredAuthCheck as AppResult.Error).code)
    }

    @Test
    fun section5_and_6_rbacMatrix_and_privilegeEscalationPrevention() = runBlocking {
        // Verify all 4 standard roles exist in database
        val users = repository.observeUsers().first()
        val roleCodes = users.map { it.roleCode }.toSet()
        assertTrue(
            "All 4 roles (OWNER, CASHIER, TECHNICIAN, INVENTORY) must be seeded",
            roleCodes.containsAll(setOf("OWNER", "CASHIER", "TECHNICIAN", "INVENTORY"))
        )

        // Owner creates a baseline product & stock for testing other roles
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        val prodRes = repository.createProduct(
            nameArabic = "سماعة سامسونج أصلية",
            sku = "SAM-BUDS-01",
            categoryId = "CAT-ACCESSORIES",
            brandId = "BRD-SAMSUNG",
            isSerialized = false,
            defaultSellingPriceMinor = 1500_00L
        )
        assertTrue(prodRes is AppResult.Success)
        val product = (prodRes as AppResult.Success).data
        assertTrue(
            repository.recordAuthorizedInventoryMovement(
                productId = product.localId,
                movementType = InventoryMovementType.ADJUSTMENT_IN,
                quantity = 10,
                unitCostMinor = 1000_00L,
                reason = "رصيد افتتاحي"
            ) is AppResult.Success
        )

        // --- TEST 1: CASHIER ROLE ---
        val cashierLogin = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_CASHIER_USER_ID, "2222")
        assertTrue("Cashier login must succeed", cashierLogin is AppResult.Success)
        assertEquals(MekkaPhoneRepository.DEFAULT_CASHIER_USER_ID, repository.activeUserId)

        // Cashier ALLOWED: Sale
        val cashierSale = repository.executeSale(
            customerId = null,
            items = listOf(
                SaleLineInput(
                    productId = product.localId,
                    serialNumberId = null,
                    quantity = 1,
                    unitSellingPriceMinor = 1500_00L
                )
            )
        )
        assertTrue("Cashier must be allowed to execute a sale", cashierSale is AppResult.Success)

        // Cashier BLOCKED: Edit/Create Product, Adjust Inventory, Backup, Sensitive Reports, Manage Users, System Config
        assertEquals(
            "PERMISSION_DENIED",
            (repository.createProduct(
                nameArabic = "منتج غير مصرح",
                sku = "UNAUTH-01",
                categoryId = "CAT-ACCESSORIES",
                brandId = null,
                isSerialized = false,
                defaultSellingPriceMinor = 500_00L
            ) as AppResult.Error).code
        )

        assertEquals(
            "PERMISSION_DENIED",
            (repository.recordAuthorizedInventoryMovement(
                productId = product.localId,
                movementType = InventoryMovementType.ADJUSTMENT_IN,
                quantity = 5,
                unitCostMinor = 1000_00L,
                reason = "محاولة تعديل مخزون من الكاشير"
            ) as AppResult.Error).code
        )

        assertEquals("PERMISSION_DENIED", (repository.createVerifiedBackup() as AppResult.Error).code)
        assertEquals(
            "PERMISSION_DENIED",
            (repository.getAuthorizedSensitiveReports(ReportsCenterFilter()) as AppResult.Error).code
        )
        assertEquals(
            "PERMISSION_DENIED",
            (repository.createStaffUser(
                fullNameArabic = "موظف وهمي",
                username = "fakestaff",
                roleCode = "CASHIER",
                rawPin = "5555"
            ) as AppResult.Error).code
        )
        assertEquals(
            "PERMISSION_DENIED",
            (repository.updateShopProfile(
                nameArabic = "تعديل غير مصرح",
                nameEnglish = "UNAUTH",
                phone = "01000000000",
                address = "القاهرة",
                taxRegistrationNumber = "",
                defaultCurrency = "EGP"
            ) as AppResult.Error).code
        )

        // PRIVILEGE ESCALATION ATTEMPT: Cashier passes DEFAULT_OWNER_USER_ID explicitly while logged in as Cashier
        val escalationAttempt = repository.createVerifiedBackup(
            actorUserId = MekkaPhoneRepository.DEFAULT_OWNER_USER_ID
        )
        assertTrue("Impersonating Owner while logged in as Cashier must be blocked", escalationAttempt is AppResult.Error)
        assertEquals("PRIVILEGE_ESCALATION_BLOCKED", (escalationAttempt as AppResult.Error).code)

        // PRIVILEGE ESCALATION ATTEMPT via switchActiveUser without PIN
        repository.switchActiveUser(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID)
        assertEquals(
            "switchActiveUser without PIN must not escalate from Cashier to Owner",
            MekkaPhoneRepository.DEFAULT_CASHIER_USER_ID,
            repository.activeUserId
        )

        // --- TEST 2: TECHNICIAN ROLE ---
        val techLogin = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_TECH_USER_ID, "3333")
        assertTrue("Technician login must succeed", techLogin is AppResult.Success)
        assertFalse(RolePermissionMatrix.hasPermission("TECHNICIAN", PermissionCode.SALE_CREATE))
        assertFalse(RolePermissionMatrix.hasPermission("TECHNICIAN", PermissionCode.BACKUP_RESTORE))
        assertTrue(RolePermissionMatrix.hasPermission("TECHNICIAN", PermissionCode.MAINTENANCE_MANAGE))

        assertEquals(
            "PERMISSION_DENIED",
            (repository.executeSale(
                customerId = null,
                items = listOf(
                    SaleLineInput(
                        productId = product.localId,
                        serialNumberId = null,
                        quantity = 1,
                        unitSellingPriceMinor = 1500_00L
                    )
                )
            ) as AppResult.Error).code
        )

        // --- TEST 3: INVENTORY ROLE ---
        val invLogin = repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_INVENTORY_USER_ID, "4444")
        assertTrue("Inventory user login must succeed", invLogin is AppResult.Success)
        assertTrue(
            "Inventory role must be able to adjust stock",
            repository.recordAuthorizedInventoryMovement(
                productId = product.localId,
                movementType = InventoryMovementType.ADJUSTMENT_IN,
                quantity = 2,
                unitCostMinor = 1000_00L,
                reason = "جرد مخزني معتمد"
            ) is AppResult.Success
        )
        assertEquals(
            "PERMISSION_DENIED",
            (repository.executeSale(
                customerId = null,
                items = listOf(
                    SaleLineInput(
                        productId = product.localId,
                        serialNumberId = null,
                        quantity = 1,
                        unitSellingPriceMinor = 1500_00L
                    )
                )
            ) as AppResult.Error).code
        )
        assertEquals("PERMISSION_DENIED", (repository.createVerifiedBackup() as AppResult.Error).code)
    }

    @Test
    fun section11_to_16_backupEncryption_completeness_corruptRestoreFailSafe_replaceRestore_andMergeRestore() = runBlocking {
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")

        // 1. Populate comprehensive operational & financial data across tables
        val cust = (repository.createCustomer("عميل النسخ الاحتياطي", "01055556666") as AppResult.Success).data
        val sup = (repository.createSupplier("مورد النسخ الاحتياطي", "أستاذ خالد", "01122223333") as AppResult.Success).data
        val prod = (repository.createProduct(
            nameArabic = "آيفون 15 برو 256",
            sku = "IPH-15P-256",
            categoryId = "CAT-PHONES",
            brandId = "BRD-APPLE",
            isSerialized = true,
            defaultSellingPriceMinor = 55000_00L
        ) as AppResult.Success).data

        // Fund main drawer to buy inventory
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 100000_00L,
            reasonArabic = "رصيد افتتاحي للخزينة"
        )

        val purRes = repository.executePurchase(
            supplierId = sup.localId,
            items = listOf(
                PurchaseLineInput(
                    productId = prod.localId,
                    quantity = 2,
                    unitCostMinor = 45000_00L,
                    serialNumbersOrImeis = listOf("359999000011111", "359999000022222")
                )
            ),
            paidAmountMinorOverride = 80000_00L,
            notes = "شراء آيفون"
        )
        assertTrue(purRes is AppResult.Success)

        val serialsBefore = repository.observeSerialNumbers().first()
        val firstSerial = serialsBefore.first { it.serialOrImei == "359999000011111" }

        // Execute a partial credit sale
        val saleRes = repository.executeSale(
            customerId = cust.localId,
            items = listOf(
                SaleLineInput(
                    productId = prod.localId,
                    serialNumberId = firstSerial.localId,
                    quantity = 1,
                    unitSellingPriceMinor = 55000_00L
                )
            ),
            paidAmountMinorOverride = 50000_00L,
            notes = "بيع آيفون بمتبقي آجل"
        )
        assertTrue(saleRes is AppResult.Success)

        // Create a Maintenance Order
        val maintRes = repository.createMaintenanceOrder(
            customerId = cust.localId,
            deviceBrand = "Apple",
            deviceModel = "iPhone 13",
            deviceImeiOrSerial = "351111222233333",
            faultDescription = "تغيير بطارية",
            estimatedCostMinor = 2500_00L,
            depositMinor = 500_00L
        )
        assertTrue(maintRes is AppResult.Success)

        val preBackupTrialBalance = repository.computeTrialBalanceReport()
        assertTrue("Trial balance must be balanced before backup", preBackupTrialBalance.isBalanced)
        val preBackupCustProj = repository.observeCustomerBalanceProjections().first().first { it.customerId == cust.localId }
        assertEquals(5000_00L, preBackupCustProj.outstandingBalanceMinor)

        // 2. Create Encrypted Verified Backup (makkaphone-backup-v1)
        val backupRes = repository.createVerifiedBackup()
        assertTrue("Backup creation must succeed", backupRes is AppResult.Success)
        val backupMeta = (backupRes as AppResult.Success).data
        assertEquals("makkaphone-backup-v1", backupMeta.backupFormatVersion)
        assertTrue(
            "Backup payload at rest must contain AES-256-GCM encrypted ciphertext",
            backupMeta.payloadJson.contains("encryptedPayloadBase64") &&
                !backupMeta.payloadJson.contains("359999000011111")
        )

        // 3. Validate backup integrity
        val validation = repository.validateBackupJson(backupMeta.payloadJson)
        assertTrue("Encrypted backup validation must pass", validation.isValid)
        assertTrue("Backup checksum must match", validation.isChecksumMatched)

        // 4. Corrupted backup restore MUST fail safely without overwriting valid data
        val corruptedPayload = backupMeta.payloadJson.replace("MK-001", "MK-CORRUPT")
        val corruptRestoreRes = repository.executeSafeBackupRestore(
            rawEnvelopeJson = corruptedPayload,
            userConfirmed = true,
            restoreMode = RestoreMode.REPLACE
        )
        assertTrue("Corrupted backup restore must fail safely", corruptRestoreRes is AppResult.Error)
        // Verify existing data is untouched after failed restore
        assertEquals(
            5000_00L,
            repository.observeCustomerBalanceProjections().first().first { it.customerId == cust.localId }.outstandingBalanceMinor
        )

        // 5. Mutate current database (add another customer & expense), then perform REPLACE restore
        val extraCust = (repository.createCustomer("عميل مؤقت بعد النسخة", "01077778888") as AppResult.Success).data
        repository.executeExpense(
            categoryName = "مرافق وكهرباء",
            title = "كهرباء مؤقتة",
            amountMinor = 300_00L
        )
        assertEquals(2, repository.observeCustomers().first().size)

        val replaceRestoreRes = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backupMeta.payloadJson,
            userConfirmed = true,
            restoreMode = RestoreMode.REPLACE
        )
        assertTrue("REPLACE restore of valid backup must succeed", replaceRestoreRes is AppResult.Success)

        // 6. Verify all state after REPLACE restore matches original backup snapshot AND safety backup was created
        val allBackupsAfterRestore = repository.observeBackups().first()
        assertTrue(
            "A pre-restore safety backup (PRE_RESTORE_SAFETY) must exist",
            allBackupsAfterRestore.size >= 2
        )

        val restoredCustomers = repository.observeCustomers().first()
        assertEquals("Customer count must match backup snapshot after REPLACE", 1, restoredCustomers.size)
        assertEquals("عميل النسخ الاحتياطي", restoredCustomers.first().fullName)

        val restoredReceivable = repository.observeCustomerBalanceProjections().first()
            .first { it.customerId == cust.localId }.outstandingBalanceMinor
        assertEquals("Customer receivable balance must be preserved", 5000_00L, restoredReceivable)

        val restoredPayable = repository.observeSupplierBalanceProjections().first()
            .first { it.supplierId == sup.localId }.outstandingPayableMinor
        assertEquals("Supplier payable balance must be preserved", 10000_00L, restoredPayable)

        val restoredStock = repository.observeProductInventoryProjections().first()
            .first { it.productId == prod.localId }.derivedStockQty
        assertEquals("Inventory stock must be preserved", 1, restoredStock)

        val postRestoreTrialBalance = repository.computeTrialBalanceReport()
        assertTrue("Trial balance must be balanced after restore", postRestoreTrialBalance.isBalanced)
        assertEquals(preBackupTrialBalance.totalDebitsMinor, postRestoreTrialBalance.totalDebitsMinor)

        // 7. Test deterministic MERGE restore mode: add a new local customer, then MERGE the backup snapshot
        //    -> must keep the new local customer AND not duplicate financial transactions!
        repository.createCustomer("عميل محلي جديد للدمج", "01066665555")
        val mergeRestoreRes = repository.executeSafeBackupRestore(
            rawEnvelopeJson = backupMeta.payloadJson,
            userConfirmed = true,
            restoreMode = RestoreMode.MERGE
        )
        assertTrue("Conflict-safe MERGE restore must succeed", mergeRestoreRes is AppResult.Success)
        assertEquals(
            "MERGE restore must preserve both local and backup customers without duplication",
            2,
            repository.observeCustomers().first().size
        )
        val postMergeTrialBalance = repository.computeTrialBalanceReport()
        assertTrue("Trial balance must remain balanced after MERGE restore", postMergeTrialBalance.isBalanced)
        assertEquals(
            "MERGE restore must never duplicate ledger transactions",
            preBackupTrialBalance.totalDebitsMinor,
            postMergeTrialBalance.totalDebitsMinor
        )
    }

    @Test
    fun section17_to_24_truthfulSync_offlineOutbox_retryBackoff_conflictDetection_andFinancialSyncIdempotency() = runBlocking {
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        val viewModel = MekkaPhoneViewModel(repository)

        // 1. Offline-first operation creates durable Outbox entries; truthfulSyncStatus never claims fake Cloud Sync when endpoint is blank
        val custRes = repository.createCustomer("عميل المزامنة", "01234567890")
        assertTrue(custRes is AppResult.Success)

        val queueBefore = repository.observeSyncQueue().first()
        assertTrue("Outbox must contain pending operation", queueBefore.isNotEmpty())
        assertEquals(SyncState.PENDING, queueBefore.first().syncState)

        // Verify ViewModel truthfulSyncStatus is SAVED_LOCALLY when no cloud endpoint URL is configured
        val uiStatus = viewModel.truthfulSyncStatus.first()
        assertEquals(SyncUiStatus.SAVED_LOCALLY, uiStatus)

        // 2. Test offline sync attempt (isNetworkAvailable = false)
        val offlineSync = repository.runDeltaSyncCycle(isNetworkAvailable = false)
        assertTrue("Sync with no network must return error without losing outbox records", offlineSync is AppResult.Error)
        assertEquals("OFFLINE_MODE_ACTIVE", (offlineSync as AppResult.Error).code)

        // 3. Test transient failure & exponential backoff (simulateTransientFailure = true)
        val transientFailSync = repository.runDeltaSyncCycle(isNetworkAvailable = true, simulateTransientFailure = true)
        assertTrue(transientFailSync is AppResult.Success)
        val reportAfterFail = (transientFailSync as AppResult.Success).data
        assertTrue("Failed items must enter RETRYING state with backoff", reportAfterFail.retryingCount > 0)
        val retryingItem = repository.observeSyncQueue().first().first()
        assertEquals(SyncState.RETRYING, retryingItem.syncState)
        assertEquals(1, retryingItem.retryCount)

        // 4. Financial Sync Safety (Section 24): Cash/Sale locally -> Sync -> Retry -> Network failure -> Retry = ONE financial transaction
        repository.executeCashOperation(
            operationType = FinancialTransactionType.CASH_IN,
            amountMinor = 5000_00L,
            reasonArabic = "تمويل خزينة للاختبار"
        )
        val txCountAfterFirstPost = repository.observeLedgerTransactions().first().size

        // Run sync cycles (with failure and then success)
        repository.runDeltaSyncCycle(isNetworkAvailable = true, simulateTransientFailure = true)
        repository.runDeltaSyncCycle(isNetworkAvailable = true, simulateTransientFailure = false)

        val txCountAfterRetries = repository.observeLedgerTransactions().first().size
        assertEquals(
            "Financial transaction must NEVER be duplicated across retries or sync cycles",
            txCountAfterFirstPost,
            txCountAfterRetries
        )

        // 5. Conflict Handling (Section 23): Device A vs Device B version conflict
        val customer = (custRes as AppResult.Success).data
        val conflict = repository.syncCoordinator.evaluateConflictPolicy(
            shopId = MekkaPhoneRepository.DEFAULT_SHOP_ID,
            entityType = "Customer",
            entityLocalId = customer.localId,
            isFinancialRecord = true,
            localVersion = 2L,
            remoteVersion = 5L,
            localSnapshotJson = """{"id":"${customer.localId}","name":"تعديل من جهاز أ","version":2}""",
            remoteSnapshotJson = """{"id":"${customer.localId}","name":"تعديل من جهاز ب","version":5}""",
            deviceId = "DEV-A"
        )
        assertNotNull("Conflict between Device A and Device B must be detected", conflict)
        val conflicts = repository.observeSyncConflicts().first()
        assertTrue(
            "Conflict record must be persisted for review",
            conflicts.any { it.resolutionStatus == "UNRESOLVED" && it.entityLocalId == customer.localId }
        )
    }

    @Test
    fun section25_to_27_largeDatasetPerformanceAndIntegrityStressTest() = runBlocking {
        repository.authenticateUserPin(MekkaPhoneRepository.DEFAULT_OWNER_USER_ID, "1111")
        val startMs = System.currentTimeMillis()

        // Seed 50 customers, 50 products, 50 opening stock movements, and 25 financial SMS messages
        for (i in 1..50) {
            val phone = String.format("0109000%04d", i)
            repository.createCustomer(fullName = "عميل اختبار الأداء رقم $i", phone = phone)
            val prodRes = repository.createProduct(
                nameArabic = "منتج قياس الأداء رقم $i",
                sku = String.format("PERF-SKU-%04d", i),
                categoryId = "CAT-ACCESSORIES",
                brandId = "BRD-ANKER",
                isSerialized = false,
                defaultSellingPriceMinor = (200_00L + i * 10_00L)
            )
            if (prodRes is AppResult.Success) {
                repository.recordAuthorizedInventoryMovement(
                    productId = prodRes.data.localId,
                    movementType = InventoryMovementType.ADJUSTMENT_IN,
                    quantity = 20,
                    unitCostMinor = 100_00L,
                    reason = "رصيد افتتاحي للأداء #$i"
                )
            }
        }

        for (i in 1..25) {
            repository.ingestSmsMessage(
                senderAddress = "VF-Cash",
                rawMessageBody = "تم استلام مبلغ ${100 + i}.00 ج.م من رقم 01011112222 عبر فودافون كاش رقم العملية #PERF$i"
            )
        }

        val customersLoaded = repository.observeCustomerBalanceProjections().first()
        val productsLoaded = repository.observeProductInventoryProjections().first()
        val smsLoaded = repository.observeSmsReviewItems().first()
        val trialBalance = repository.computeTrialBalanceReport()
        val integrityReport = repository.runDataIntegrityHealthCheck()
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(50, customersLoaded.size)
        assertEquals(50, productsLoaded.size)
        assertEquals(25, smsLoaded.size)
        assertTrue("Trial balance must remain balanced under load", trialBalance.isBalanced)
        assertEquals("Data integrity health check must pass", HealthCheckLevel.PASS, integrityReport.overallStatus)
        assertTrue("Large dataset operations and queries must complete within reasonable time (<20s)", elapsedMs < 20_000L)
    }
}

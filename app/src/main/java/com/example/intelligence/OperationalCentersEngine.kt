package com.example.intelligence

import com.example.core.SyncState
import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.SupplierBalanceProjection
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.PendingTransactionEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.database.entities.UserEntity
import com.example.financial.CustomerCreditAgingEngine
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.financial.PartyAccountStatementEngine
import com.example.financial.SupplierPayablesScheduleEngine
import com.example.navigation.MekkaRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object OperationalCentersEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    // =============================================================================================
    // 1 — PENDING OPERATIONS CENTER
    // =============================================================================================
    fun buildPendingOperationsCenter(
        syncQueue: List<SyncQueueEntity>,
        syncConflicts: List<SyncConflictEntity>,
        pendingSmsTransactions: List<PendingTransactionEntity>,
        cashSessions: List<CashSessionEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        customerBalances: List<CustomerBalanceProjection>,
        backups: List<BackupMetadataEntity>,
        auditLogs: List<AuditLogEntity>,
        inventoryItems: List<ProductInventoryProjection>,
        usersMap: Map<String, UserEntity> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): PendingOperationsCenterBundle {
        val items = mutableListOf<PendingOperationCenterItem>()

        // 1. Pending Sync
        syncQueue.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.SYNCING }.forEach { sq ->
            items += PendingOperationCenterItem(
                id = "PS-${sq.localId}",
                type = PendingOperationType.PENDING_SYNC,
                statusArabic = sq.syncState.arabicLabel,
                createdAt = sq.createdAt,
                userId = "SYSTEM",
                userNameArabic = "طابور المزامنة (${sq.deviceId})",
                reference = "${sq.entityType}:${sq.entityLocalId}",
                entityLocalId = sq.localId,
                reasonArabic = "عملية محلية (${FriendlyTerminologyMapper.toFriendlyOperationArabic(sq.operationType)}) بانتظار الرفع",
                requiredActionArabic = "تشغيل المزامنة ومصادقة الخادم",
                availableActions = listOf(PendingCenterActionType.RETRY, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
        }

        // 2. Sync Failed & 5. Failed Operations
        syncQueue.filter { it.syncState == SyncState.FAILED }.forEach { sq ->
            val err = sq.lastErrorMessage ?: "تعذر الاتصال بالخادم (محاولة #${sq.retryCount})"
            items += PendingOperationCenterItem(
                id = "SF-${sq.localId}",
                type = PendingOperationType.SYNC_FAILED,
                statusArabic = "فشل مزامنة (${sq.retryCount}/${sq.maxRetries})",
                createdAt = sq.lastAttemptAt ?: sq.updatedAt,
                userId = "SYSTEM",
                userNameArabic = "جهاز ${sq.deviceId}",
                reference = "${sq.entityType}:${sq.entityLocalId}",
                entityLocalId = sq.localId,
                reasonArabic = err,
                requiredActionArabic = "إعادة محاولة المزامنة أو فحص الاتصال",
                availableActions = listOf(PendingCenterActionType.RETRY, PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
            if (sq.retryCount >= 2 || sq.isFinancialLedgerRecord) {
                items += PendingOperationCenterItem(
                    id = "FO-${sq.localId}",
                    type = PendingOperationType.FAILED_OPERATIONS,
                    statusArabic = "عملية متعثرة",
                    createdAt = sq.lastAttemptAt ?: sq.updatedAt,
                    userId = "SYSTEM",
                    userNameArabic = "محرك العمليات (${sq.deviceId})",
                    reference = sq.deduplicationKey,
                    entityLocalId = sq.localId,
                    reasonArabic = "فشل متكرر لعملية حساسة: $err",
                    requiredActionArabic = "مراجعة العملية وإعادة المحاولة فوراً",
                    availableActions = listOf(PendingCenterActionType.RETRY, PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                    routeTarget = MekkaRoute.DIAGNOSTICS
                )
            }
        }

        // 3. Sync Conflicts
        syncConflicts.filter { !it.isResolved }.forEach { sc ->
            items += PendingOperationCenterItem(
                id = "SC-${sc.localId}",
                type = PendingOperationType.SYNC_CONFLICTS,
                statusArabic = "تعارض غير محلول",
                createdAt = sc.detectedAt,
                userId = sc.resolvedByUserId ?: "SYSTEM",
                userNameArabic = "جهاز ${sc.deviceId}",
                reference = "${sc.entityType}:${sc.entityLocalId}",
                entityLocalId = sc.localId,
                reasonArabic = sc.conflictReasonArabic,
                requiredActionArabic = if (sc.isFinancialRecord) "حسم التعارض بقيد تسوية محاسبي معتمد" else "مراجعة النسختين وحسم التعارض",
                availableActions = listOf(PendingCenterActionType.RESOLVE, PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.CONFLICTS
            )
        }

        // 4. SMS Review
        pendingSmsTransactions.filter { it.status == "AWAITING_CONFIRMATION" }.forEach { pt ->
            items += PendingOperationCenterItem(
                id = "SMS-${pt.localId}",
                type = PendingOperationType.SMS_REVIEW,
                statusArabic = "بانتظار المراجعة والاعتماد",
                createdAt = pt.createdAt,
                userId = "SMS_PIPELINE",
                userNameArabic = "محرك رسائل المحافظ",
                reference = pt.counterpartyPhone.ifBlank { pt.localId },
                entityLocalId = pt.localId,
                reasonArabic = "رسالة ${pt.proposedType.arabicLabel} بمبلغ ${Money.ofMinor(pt.proposedAmountMinor).formatArabic()} تحتاج تأكيداً قبل الترحيل للدفتر",
                requiredActionArabic = "اعتماد الترحيل لدفتر الأستاذ أو رفض الرسالة",
                availableActions = listOf(PendingCenterActionType.APPROVE, PendingCenterActionType.REJECT, PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.SMS_REVIEW,
                amountMinor = pt.proposedAmountMinor
            )
        }

        // 6. Pending Approvals (Shift variance approvals & maintenance customer approvals)
        cashSessions.filter {
            it.approvalStatus == "PENDING_APPROVAL" || (it.discrepancyMinor != null && it.discrepancyMinor != 0L && it.approvedByUserId == null)
        }.forEach { cs ->
            val userName = usersMap[cs.closedByUserId ?: cs.openedByUserId]?.fullNameArabic ?: (cs.closedByUserId ?: cs.openedByUserId)
            val diff = cs.discrepancyMinor ?: 0L
            items += PendingOperationCenterItem(
                id = "PA-SHIFT-${cs.localId}",
                type = PendingOperationType.PENDING_APPROVALS,
                statusArabic = "بانتظار اعتماد المدير",
                createdAt = cs.closedAt ?: cs.updatedAt,
                userId = cs.closedByUserId ?: cs.openedByUserId,
                userNameArabic = userName,
                reference = cs.localId,
                entityLocalId = cs.localId,
                reasonArabic = "فرق إغلاق خزينة بمقدار ${Money.ofMinor(diff).formatArabic()} — السبب: ${cs.varianceReasonArabic.ifBlank { "غير محدد" }}",
                requiredActionArabic = "مراجعة فرق الوردية واعتماد التسوية",
                availableActions = listOf(PendingCenterActionType.APPROVE, PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.CASH_DRAWER,
                amountMinor = abs(diff)
            )
        }

        maintenanceOrders.filter { it.status == MaintenanceStatus.WAITING_APPROVAL || it.status == MaintenanceStatus.COST_ESTIMATED }.forEach { mo ->
            val userName = usersMap[mo.assignedTechnicianId ?: mo.receivedByUserId]?.fullNameArabic ?: mo.receivedByUserId
            items += PendingOperationCenterItem(
                id = "PA-MAINT-${mo.localId}",
                type = PendingOperationType.PENDING_APPROVALS,
                statusArabic = mo.status.arabicLabel,
                createdAt = mo.updatedAt,
                userId = mo.receivedByUserId,
                userNameArabic = userName,
                reference = mo.ticketNumber,
                entityLocalId = mo.localId,
                reasonArabic = "تذكرة صيانة (${mo.deviceBrand} ${mo.deviceModel}) للعميل ${mo.customerNameSnapshot} بانتظار موافقة التكلفة (${Money.ofMinor(mo.estimatedCostMinor).formatArabic()})",
                requiredActionArabic = "تأكيد موافقة العميل لبدء الإصلاح أو الإلغاء",
                availableActions = listOf(PendingCenterActionType.APPROVE, PendingCenterActionType.REJECT, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.MAINTENANCE,
                amountMinor = mo.estimatedCostMinor
            )
        }

        // 7. Pending Maintenance
        maintenanceOrders.filter { it.status.isPendingWork }.forEach { mo ->
            val isOverdue = mo.expectedDeliveryAt != null && mo.expectedDeliveryAt < nowMillis
            val userName = usersMap[mo.assignedTechnicianId ?: mo.receivedByUserId]?.fullNameArabic ?: mo.receivedByUserId
            items += PendingOperationCenterItem(
                id = "PM-${mo.localId}",
                type = PendingOperationType.PENDING_MAINTENANCE,
                statusArabic = if (isOverdue) "متأخرة (${mo.status.arabicLabel})" else mo.status.arabicLabel,
                createdAt = mo.receivedAt,
                userId = mo.assignedTechnicianId ?: mo.receivedByUserId,
                userNameArabic = userName,
                reference = mo.ticketNumber,
                entityLocalId = mo.localId,
                reasonArabic = "جهاز ${mo.deviceBrand} ${mo.deviceModel} — العطل: ${mo.faultDescription}",
                requiredActionArabic = "استكمال مرحلة الصيانة وتحديث حالة التذكرة",
                availableActions = listOf(PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.MAINTENANCE,
                amountMinor = (mo.finalTotalMinor.takeIf { it > 0L } ?: mo.estimatedCostMinor)
            )
        }

        // 8. Pending Customer Payments
        customerBalances.filter { it.outstandingBalanceMinor > 0L }.forEach { cb ->
            items += PendingOperationCenterItem(
                id = "PCP-${cb.customerId}",
                type = PendingOperationType.PENDING_CUSTOMER_PAYMENTS,
                statusArabic = if (cb.creditLimitMinor in 1 until cb.outstandingBalanceMinor) "تجاوز الحد الائتماني" else "مديونية قائمة",
                createdAt = nowMillis,
                userId = "ACCOUNTING",
                userNameArabic = cb.fullName,
                reference = cb.phone,
                entityLocalId = cb.customerId,
                reasonArabic = "مستحقات آجلة قائمة على العميل ${cb.fullName} بقيمة ${Money.ofMinor(cb.outstandingBalanceMinor).formatArabic()}",
                requiredActionArabic = "متابعة السداد أو تسجيل دفعة تحصيل",
                availableActions = listOf(PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.CUSTOMERS,
                amountMinor = cb.outstandingBalanceMinor
            )
        }

        // 9. Backup Problems
        val latestBackup = backups.maxByOrNull { it.createdAt }
        if (latestBackup == null) {
            items += PendingOperationCenterItem(
                id = "BP-NONE",
                type = PendingOperationType.BACKUP_PROBLEMS,
                statusArabic = "لا توجد نسخة احتياطية",
                createdAt = nowMillis,
                userId = "SYSTEM",
                userNameArabic = "حماية البيانات",
                reference = "BACKUP-MISSING",
                entityLocalId = "BACKUP-MISSING",
                reasonArabic = "لم يتم إنشاء أي نسخة احتياطية مشفرة للمتجر حتى الآن",
                requiredActionArabic = "إنشاء نسخة احتياطية فورية وتأمينها",
                availableActions = listOf(PendingCenterActionType.RESOLVE, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.BACKUP
            )
        } else {
            val backupAgeDays = ((nowMillis - latestBackup.createdAt).coerceAtLeast(0L) / DAY_MILLIS).toInt()
            if (!latestBackup.isVerified || backupAgeDays >= 3) {
                items += PendingOperationCenterItem(
                    id = "BP-${latestBackup.localId}",
                    type = PendingOperationType.BACKUP_PROBLEMS,
                    statusArabic = if (!latestBackup.isVerified) "نسخة غير محققة" else "نسخة قديمة ($backupAgeDays يوم)",
                    createdAt = latestBackup.createdAt,
                    userId = latestBackup.createdByUserId,
                    userNameArabic = usersMap[latestBackup.createdByUserId]?.fullNameArabic ?: latestBackup.createdByUserId,
                    reference = latestBackup.fileName,
                    entityLocalId = latestBackup.localId,
                    reasonArabic = if (!latestBackup.isVerified) "فشل التحقق من البصمة الرقمية للنسخة الاحتياطية" else "مرّ $backupAgeDays يوم منذ آخر نسخة احتياطية",
                    requiredActionArabic = "إنشاء نسخة احتياطية محدثة والتحقق من سلامتها",
                    availableActions = listOf(PendingCenterActionType.RESOLVE, PendingCenterActionType.OPEN_DETAILS),
                    routeTarget = MekkaRoute.BACKUP
                )
            }
        }

        // 10. Restore Warnings
        auditLogs.filter { it.actionType.equals("BACKUP_RESTORE", ignoreCase = true) }.take(3).forEach { log ->
            items += PendingOperationCenterItem(
                id = "RW-${log.localId}",
                type = PendingOperationType.RESTORE_WARNINGS,
                statusArabic = "تنبيه استعادة بيانات",
                createdAt = log.timestamp,
                userId = log.actorUserId,
                userNameArabic = usersMap[log.actorUserId]?.fullNameArabic ?: log.actorUserId,
                reference = log.referenceNumber.ifBlank { log.entityId },
                entityLocalId = log.localId,
                reasonArabic = "تمت عملية استعادة نسخة احتياطية (${log.summaryArabic}) — يُنصح بفحص توازن دفتر الأستاذ",
                requiredActionArabic = "مراجعة فحص سلامة البيانات بعد الاستعادة",
                availableActions = listOf(PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
        }

        // 11. Inventory Alerts
        inventoryItems.filter { it.isActive && (it.derivedStockQty <= 0 || it.isLowStockAlert || it.isReorderRecommended) }.forEach { prod ->
            items += PendingOperationCenterItem(
                id = "IA-${prod.productId}",
                type = PendingOperationType.INVENTORY_ALERTS,
                statusArabic = prod.stockStatusArabic,
                createdAt = nowMillis,
                userId = "INVENTORY",
                userNameArabic = "رقابة المخزون",
                reference = prod.sku,
                entityLocalId = prod.productId,
                reasonArabic = "الصنف (${prod.nameArabic}) رصيده الحالي (${prod.derivedStockQty}) وأقل من حد إعادة الطلب (${prod.reorderPointQty})",
                requiredActionArabic = "إصدار أمر شراء أو توريد بضاعة من المورد",
                availableActions = listOf(PendingCenterActionType.REVIEW, PendingCenterActionType.OPEN_DETAILS),
                routeTarget = MekkaRoute.INVENTORY,
                amountMinor = prod.weightedAvgCostMinor
            )
        }

        val sorted = items.sortedByDescending { it.createdAt }
        val counts = PendingOperationType.entries.associateWith { t -> sorted.count { it.type == t } }
        return PendingOperationsCenterBundle(
            totalCount = sorted.size,
            countsByType = counts,
            items = sorted
        )
    }

    // =============================================================================================
    // 2 — SMART NOTIFICATION CENTER
    // =============================================================================================
    fun buildSmartNotificationCenter(
        inventoryAnalytics: InventoryAnalyticsDashboardBundle,
        inventoryItems: List<ProductInventoryProjection>,
        cashSessions: List<CashSessionEntity>,
        customerBalances: List<CustomerBalanceProjection>,
        supplierBalances: List<SupplierBalanceProjection>,
        expenses: List<ExpenseEntity>,
        pendingSmsTransactions: List<PendingTransactionEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        syncQueue: List<SyncQueueEntity>,
        syncConflicts: List<SyncConflictEntity>,
        backups: List<BackupMetadataEntity>,
        auditLogs: List<AuditLogEntity>,
        storageFreeBytes: Long = 500L * 1024L * 1024L,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartNotificationCenterBundle {
        val alerts = mutableListOf<SmartNotificationItem>()

        // --- INVENTORY ---
        for (prod in inventoryItems.filter { it.isActive }) {
            if (prod.derivedStockQty <= 0) {
                alerts += SmartNotificationItem(
                    id = "N-OOS-${prod.productId}",
                    domain = SmartNotificationDomain.INVENTORY,
                    subType = SmartNotificationSubType.OUT_OF_STOCK,
                    priority = SmartNotificationPriority.CRITICAL,
                    titleArabic = "نفاد المخزون: ${prod.nameArabic}",
                    messageArabic = "الرصيد الفعلي للصنف (${prod.sku}) وصل إلى صفر. يجب التوريد لتجنب خسارة المبيعات.",
                    referenceCode = prod.sku,
                    entityId = prod.productId,
                    createdAt = nowMillis,
                    routeTarget = MekkaRoute.INVENTORY
                )
            } else if (prod.derivedStockQty <= prod.reorderThresholdQty) {
                alerts += SmartNotificationItem(
                    id = "N-LOW-${prod.productId}",
                    domain = SmartNotificationDomain.INVENTORY,
                    subType = SmartNotificationSubType.LOW_STOCK,
                    priority = SmartNotificationPriority.ACTION_REQUIRED,
                    titleArabic = "مخزون منخفض: ${prod.nameArabic}",
                    messageArabic = "المتبقي بالمخزن (${prod.derivedStockQty}) وحدات فقط (حد التنبيه: ${prod.reorderThresholdQty}).",
                    referenceCode = prod.sku,
                    entityId = prod.productId,
                    createdAt = nowMillis,
                    routeTarget = MekkaRoute.INVENTORY
                )
            }
        }

        for (slow in inventoryAnalytics.slowMovingItems.take(5)) {
            alerts += SmartNotificationItem(
                id = "N-SLOW-${slow.productId}",
                domain = SmartNotificationDomain.INVENTORY,
                subType = SmartNotificationSubType.SLOW_MOVING,
                priority = SmartNotificationPriority.INFO,
                titleArabic = "صنف بطيء الحركة: ${slow.nameArabic}",
                messageArabic = "الرصيد (${slow.currentStockQty}) بقيمة ${Money.ofMinor(slow.stockValueWacMinor).formatArabic()} ومعدل دورانه منخفض.",
                referenceCode = slow.sku,
                entityId = slow.productId,
                createdAt = nowMillis,
                routeTarget = MekkaRoute.INVENTORY
            )
        }

        for (dead in inventoryAnalytics.deadStockItems.take(5)) {
            alerts += SmartNotificationItem(
                id = "N-DEAD-${dead.productId}",
                domain = SmartNotificationDomain.INVENTORY,
                subType = SmartNotificationSubType.DEAD_STOCK,
                priority = SmartNotificationPriority.WARNING,
                titleArabic = "مخزون راكد: ${dead.nameArabic}",
                messageArabic = "رأس مال مجمد بقيمة ${Money.ofMinor(dead.stockValueWacMinor).formatArabic()} منذ ${dead.ageDaysSinceLastInbound} يوم بدون مبيعات.",
                referenceCode = dead.sku,
                entityId = dead.productId,
                createdAt = nowMillis,
                routeTarget = MekkaRoute.INVENTORY
            )
        }

        // --- FINANCIAL ---
        for (cs in cashSessions.filter { (it.discrepancyMinor ?: 0L) != 0L }) {
            val diff = cs.discrepancyMinor ?: 0L
            alerts += SmartNotificationItem(
                id = "N-VAR-${cs.localId}",
                domain = SmartNotificationDomain.FINANCIAL,
                subType = SmartNotificationSubType.SHIFT_VARIANCE,
                priority = if (abs(diff) >= 100_00L) SmartNotificationPriority.CRITICAL else SmartNotificationPriority.WARNING,
                titleArabic = "فرق جرد خزينة بالوردية",
                messageArabic = "تم تسجيل فرق خزينة بقيمة ${Money.ofMinor(diff).formatArabic()} (${cs.varianceReasonArabic.ifBlank { "بدون سبب" }}).",
                referenceCode = cs.localId,
                entityId = cs.localId,
                createdAt = cs.closedAt ?: cs.updatedAt,
                routeTarget = MekkaRoute.CASH_DRAWER
            )
        }

        for (cb in customerBalances.filter { it.outstandingBalanceMinor > 0L }) {
            val exceeds = cb.creditLimitMinor in 1 until cb.outstandingBalanceMinor
            alerts += SmartNotificationItem(
                id = "N-REC-${cb.customerId}",
                domain = SmartNotificationDomain.FINANCIAL,
                subType = SmartNotificationSubType.OUTSTANDING_RECEIVABLE,
                priority = if (exceeds) SmartNotificationPriority.CRITICAL else SmartNotificationPriority.ACTION_REQUIRED,
                titleArabic = "مديونية عميل: ${cb.fullName}",
                messageArabic = "مستحق للتحصيل ${Money.ofMinor(cb.outstandingBalanceMinor).formatArabic()}" +
                    if (exceeds) " (متجاوز للحد الائتماني ${Money.ofMinor(cb.creditLimitMinor).formatArabic()})" else "",
                referenceCode = cb.phone,
                entityId = cb.customerId,
                createdAt = nowMillis,
                routeTarget = MekkaRoute.CUSTOMERS
            )
        }

        for (sb in supplierBalances.filter { it.outstandingPayableMinor > 0L }) {
            alerts += SmartNotificationItem(
                id = "N-PAY-${sb.supplierId}",
                domain = SmartNotificationDomain.FINANCIAL,
                subType = SmartNotificationSubType.SUPPLIER_PAYABLE,
                priority = SmartNotificationPriority.WARNING,
                titleArabic = "مستحقات مورد: ${sb.companyName}",
                messageArabic = "رصيد مستحق السداد للمورد بقيمة ${Money.ofMinor(sb.outstandingPayableMinor).formatArabic()}.",
                referenceCode = sb.phone,
                entityId = sb.supplierId,
                createdAt = nowMillis,
                routeTarget = MekkaRoute.SUPPLIERS
            )
        }

        if (expenses.isNotEmpty()) {
            val avgExp = expenses.map { it.amountMinor }.average().toLong()
            val threshold = maxOf(avgExp * 2L, 1_500_00L) // >= 2x average and at least 1,500 EGP
            expenses.filter { it.amountMinor >= threshold }.take(3).forEach { exp ->
                alerts += SmartNotificationItem(
                    id = "N-UEXP-${exp.localId}",
                    domain = SmartNotificationDomain.FINANCIAL,
                    subType = SmartNotificationSubType.UNUSUAL_EXPENSE,
                    priority = SmartNotificationPriority.WARNING,
                    titleArabic = "مصروف مرتفع غير اعتيادي: ${exp.title}",
                    messageArabic = "تم تسجيل مصروف (${exp.categoryName}) بقيمة ${Money.ofMinor(exp.amountMinor).formatArabic()}.",
                    referenceCode = exp.localId,
                    entityId = exp.localId,
                    createdAt = exp.expenseTimestamp,
                    routeTarget = MekkaRoute.EXPENSES
                )
            }
        }

        for (pt in pendingSmsTransactions.filter { it.status == "AWAITING_CONFIRMATION" }) {
            alerts += SmartNotificationItem(
                id = "N-PWAL-${pt.localId}",
                domain = SmartNotificationDomain.FINANCIAL,
                subType = SmartNotificationSubType.PENDING_WALLET_OPERATION,
                priority = SmartNotificationPriority.ACTION_REQUIRED,
                titleArabic = "عملية محفظة معلقة للمراجعة",
                messageArabic = "${pt.proposedType.arabicLabel} بمبلغ ${Money.ofMinor(pt.proposedAmountMinor).formatArabic()} (${pt.counterpartyPhone}).",
                referenceCode = pt.localId,
                entityId = pt.localId,
                createdAt = pt.createdAt,
                routeTarget = MekkaRoute.SMS_REVIEW
            )
        }

        // --- MAINTENANCE ---
        val startOfToday = nowMillis - (nowMillis % DAY_MILLIS)
        val endOfToday = startOfToday + DAY_MILLIS - 1L
        for (mo in maintenanceOrders) {
            if (mo.status.isPendingWork && mo.expectedDeliveryAt != null) {
                if (mo.expectedDeliveryAt < startOfToday) {
                    alerts += SmartNotificationItem(
                        id = "N-MOVD-${mo.localId}",
                        domain = SmartNotificationDomain.MAINTENANCE,
                        subType = SmartNotificationSubType.OVERDUE,
                        priority = SmartNotificationPriority.CRITICAL,
                        titleArabic = "صيانة متأخرة التسليم: ${mo.ticketNumber}",
                        messageArabic = "جهاز ${mo.deviceBrand} ${mo.deviceModel} للعميل ${mo.customerNameSnapshot} تجاوز موعد التسليم.",
                        referenceCode = mo.ticketNumber,
                        entityId = mo.localId,
                        createdAt = mo.expectedDeliveryAt,
                        routeTarget = MekkaRoute.MAINTENANCE
                    )
                } else if (mo.expectedDeliveryAt in startOfToday..endOfToday) {
                    alerts += SmartNotificationItem(
                        id = "N-MDUE-${mo.localId}",
                        domain = SmartNotificationDomain.MAINTENANCE,
                        subType = SmartNotificationSubType.DUE_TODAY,
                        priority = SmartNotificationPriority.ACTION_REQUIRED,
                        titleArabic = "موعد تسليم صيانة اليوم: ${mo.ticketNumber}",
                        messageArabic = "جهاز ${mo.deviceBrand} ${mo.deviceModel} مستحق التسليم اليوم للعميل ${mo.customerNameSnapshot}.",
                        referenceCode = mo.ticketNumber,
                        entityId = mo.localId,
                        createdAt = mo.receivedAt,
                        routeTarget = MekkaRoute.MAINTENANCE
                    )
                }
            }
            if (mo.status == MaintenanceStatus.READY_FOR_PICKUP || mo.status == MaintenanceStatus.CUSTOMER_NOTIFIED) {
                alerts += SmartNotificationItem(
                    id = "N-MRDY-${mo.localId}",
                    domain = SmartNotificationDomain.MAINTENANCE,
                    subType = SmartNotificationSubType.READY_FOR_DELIVERY,
                    priority = SmartNotificationPriority.INFO,
                    titleArabic = "جهاز جاهز للتسليم: ${mo.ticketNumber}",
                    messageArabic = "الجهاز ${mo.deviceBrand} ${mo.deviceModel} جاهز للتسليم للعميل ${mo.customerNameSnapshot}.",
                    referenceCode = mo.ticketNumber,
                    entityId = mo.localId,
                    createdAt = mo.updatedAt,
                    routeTarget = MekkaRoute.MAINTENANCE
                )
            } else if (mo.status == MaintenanceStatus.WAITING_APPROVAL || mo.status == MaintenanceStatus.COST_ESTIMATED) {
                alerts += SmartNotificationItem(
                    id = "N-MWAP-${mo.localId}",
                    domain = SmartNotificationDomain.MAINTENANCE,
                    subType = SmartNotificationSubType.WAITING_CUSTOMER_APPROVAL,
                    priority = SmartNotificationPriority.ACTION_REQUIRED,
                    titleArabic = "بانتظار موافقة العميل: ${mo.ticketNumber}",
                    messageArabic = "تذكرة ${mo.deviceBrand} ${mo.deviceModel} بانتظار موافقة العميل على التكلفة (${Money.ofMinor(mo.estimatedCostMinor).formatArabic()}).",
                    referenceCode = mo.ticketNumber,
                    entityId = mo.localId,
                    createdAt = mo.updatedAt,
                    routeTarget = MekkaRoute.MAINTENANCE
                )
            }
        }

        // --- SYSTEM ---
        val failedSyncs = syncQueue.filter { it.syncState == SyncState.FAILED }
        if (failedSyncs.isNotEmpty()) {
            alerts += SmartNotificationItem(
                id = "N-SFAIL",
                domain = SmartNotificationDomain.SYSTEM,
                subType = SmartNotificationSubType.SYNC_FAILED,
                priority = SmartNotificationPriority.CRITICAL,
                titleArabic = "فشل مزامنة (${failedSyncs.size} عمليات)",
                messageArabic = "توجد عمليات محلية تعذر رفعها للخادم: ${failedSyncs.first().lastErrorMessage ?: "خطأ اتصال"}.",
                referenceCode = failedSyncs.first().localId,
                entityId = failedSyncs.first().localId,
                createdAt = failedSyncs.maxOf { it.updatedAt },
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
        }

        for (sc in syncConflicts.filter { !it.isResolved }) {
            alerts += SmartNotificationItem(
                id = "N-SCONF-${sc.localId}",
                domain = SmartNotificationDomain.SYSTEM,
                subType = SmartNotificationSubType.CONFLICT,
                priority = SmartNotificationPriority.CRITICAL,
                titleArabic = "تعارض بيانات يتطلب الحسم",
                messageArabic = sc.conflictReasonArabic,
                referenceCode = "${sc.entityType}:${sc.entityLocalId}",
                entityId = sc.localId,
                createdAt = sc.detectedAt,
                routeTarget = MekkaRoute.CONFLICTS
            )
        }

        val latestBackup = backups.maxByOrNull { it.createdAt }
        if (latestBackup == null || !latestBackup.isVerified) {
            alerts += SmartNotificationItem(
                id = "N-SBAK",
                domain = SmartNotificationDomain.SYSTEM,
                subType = SmartNotificationSubType.BACKUP_FAILED,
                priority = SmartNotificationPriority.WARNING,
                titleArabic = "تنبيه النسخ الاحتياطي",
                messageArabic = if (latestBackup == null) "لم يتم أخذ نسخة احتياطية للمتجر بعد." else "آخر نسخة احتياطية غير محققة البصمة.",
                referenceCode = latestBackup?.fileName ?: "NO_BACKUP",
                entityId = latestBackup?.localId,
                createdAt = latestBackup?.createdAt ?: nowMillis,
                routeTarget = MekkaRoute.BACKUP
            )
        }

        auditLogs.filter { it.actionType.equals("BACKUP_RESTORE", ignoreCase = true) }.take(1).forEach { log ->
            alerts += SmartNotificationItem(
                id = "N-SREST-${log.localId}",
                domain = SmartNotificationDomain.SYSTEM,
                subType = SmartNotificationSubType.RESTORE_WARNING,
                priority = SmartNotificationPriority.WARNING,
                titleArabic = "تحذير ما بعد استعادة النسخة",
                messageArabic = "تمت استعادة نسخة احتياطية (${log.summaryArabic}) — يرجى التحقق من سلامة الأرصدة.",
                referenceCode = log.referenceNumber.ifBlank { log.localId },
                entityId = log.localId,
                createdAt = log.timestamp,
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
        }

        if (storageFreeBytes < 100L * 1024L * 1024L) { // < 100 MB
            alerts += SmartNotificationItem(
                id = "N-SSTOR",
                domain = SmartNotificationDomain.SYSTEM,
                subType = SmartNotificationSubType.STORAGE_PROBLEM,
                priority = SmartNotificationPriority.CRITICAL,
                titleArabic = "انخفاض حرج في مساحة التخزين",
                messageArabic = "المساحة المتبقية بالجهاز أقل من 100 ميجابايت مما قد يعيق حفظ الفواتير والنسخ الاحتياطي.",
                referenceCode = "STORAGE_LOW",
                createdAt = nowMillis,
                routeTarget = MekkaRoute.DIAGNOSTICS
            )
        }

        val sorted = alerts.sortedWith(
            compareByDescending<SmartNotificationItem> { it.priority.rank }
                .thenByDescending { it.createdAt }
        )
        return SmartNotificationCenterBundle(
            totalCount = sorted.size,
            criticalCount = sorted.count { it.priority == SmartNotificationPriority.CRITICAL },
            actionRequiredCount = sorted.count { it.priority == SmartNotificationPriority.ACTION_REQUIRED },
            warningCount = sorted.count { it.priority == SmartNotificationPriority.WARNING },
            infoCount = sorted.count { it.priority == SmartNotificationPriority.INFO },
            itemsByDomain = SmartNotificationDomain.entries.associateWith { d -> sorted.filter { it.domain == d } },
            allItems = sorted
        )
    }

    // =============================================================================================
    // 4 — CUSTOMER 360 TIMELINE & PROFILE ENGINE
    // =============================================================================================
    fun buildCustomer360Snapshot(
        customer: CustomerEntity,
        derivedBalanceMinor: Long,
        allSales: List<SaleEntity>,
        allRefunds: List<RefundEntity>,
        allMaintenance: List<MaintenanceOrderEntity>,
        allTransfers: List<TransferEntity>,
        allLedgerTransactions: List<TransactionEntity>,
        allAuditLogs: List<AuditLogEntity>,
        activeRoleCode: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Customer360Snapshot {
        val customerSales = allSales.filter { it.customerId == customer.localId }.sortedByDescending { it.saleTimestamp }
        val saleIds = customerSales.map { it.localId }.toSet()
        val customerRefunds = allRefunds.filter { it.originalSaleId in saleIds }.sortedByDescending { it.refundTimestamp }
        val customerMaintenance = allMaintenance.filter {
            it.customerId == customer.localId || (customer.phone.isNotBlank() && it.customerPhoneSnapshot == customer.phone)
        }.sortedByDescending { it.receivedAt }
        val customerPayments = allLedgerTransactions.filter {
            it.customerId == customer.localId &&
                it.status == LedgerTransactionStatus.POSTED &&
                (it.relatedEntityType == "CUSTOMER_COLLECTION" || it.relatedEntityType == "SALE" || it.relatedEntityType == "MAINTENANCE")
        }.sortedByDescending { it.transactionTimestamp }

        val normPhone = Money.normalizeDigits(customer.phone).trim()
        val walletOps = if (normPhone.isNotBlank()) {
            allTransfers.filter { Money.normalizeDigits(it.customerPhoneOrAccount).contains(normPhone) }
                .sortedByDescending { it.transferTimestamp }
        } else emptyList()

        val financialProfile = PartyAccountStatementEngine.buildCustomerProfile(
            customerId = customer.localId,
            derivedBalanceMinor = derivedBalanceMinor,
            sales = customerSales,
            refunds = customerRefunds,
            ledgerTransactions = allLedgerTransactions
        )
        val creditStanding = CustomerCreditAgingEngine.computeCustomerCreditStanding(
            customer = customer,
            derivedBalanceMinor = derivedBalanceMinor,
            customerSales = customerSales,
            nowMillis = nowMillis
        )

        val isAuditAuthorized = activeRoleCode.uppercase() in setOf("OWNER", "MANAGER", "ACCOUNTANT")
        val relevantEntityIds = (saleIds + customerRefunds.map { it.localId } + customerMaintenance.map { it.localId } + customer.localId).toSet()
        val auditHistory = if (isAuditAuthorized) {
            allAuditLogs.filter {
                it.entityId in relevantEntityIds || it.summaryArabic.contains(customer.fullName) || (normPhone.isNotBlank() && it.summaryArabic.contains(normPhone))
            }.sortedByDescending { it.timestamp }
        } else emptyList()

        val timeline = mutableListOf<Customer360TimelineEvent>()

        for (sale in customerSales) {
            val unpaid = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
            timeline += Customer360TimelineEvent(
                id = "TL-SALE-${sale.localId}",
                timestamp = sale.saleTimestamp,
                dateFormatted = formatDate(sale.saleTimestamp),
                eventType = Customer360EventType.SALE,
                titleArabic = "فاتورة بيع #${sale.invoiceNumber}",
                subtitleArabic = "الإجمالي: ${Money.ofMinor(sale.totalAmountMinor).formatArabic()} • المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()}",
                referenceNumber = sale.invoiceNumber,
                amountMinor = sale.totalAmountMinor,
                statusArabic = FriendlyTerminologyMapper.toFriendlyStatusArabic(sale.status),
                entityId = sale.localId,
                routeTarget = MekkaRoute.SALES_POS
            )
            if (unpaid > 0L && sale.status != "REFUNDED") {
                timeline += Customer360TimelineEvent(
                    id = "TL-REC-${sale.localId}",
                    timestamp = sale.saleTimestamp + 1L,
                    dateFormatted = formatDate(sale.saleTimestamp),
                    eventType = Customer360EventType.RECEIVABLE,
                    titleArabic = "مديونية آجلة على الفاتورة #${sale.invoiceNumber}",
                    subtitleArabic = "متبقي بذمة العميل: ${Money.ofMinor(unpaid).formatArabic()}",
                    referenceNumber = sale.invoiceNumber,
                    amountMinor = unpaid,
                    statusArabic = "آجل مستحق",
                    entityId = sale.localId,
                    routeTarget = MekkaRoute.CUSTOMERS
                )
            }
        }

        for (pay in customerPayments.filter { it.relatedEntityType == "CUSTOMER_COLLECTION" }) {
            timeline += Customer360TimelineEvent(
                id = "TL-PAY-${pay.localId}",
                timestamp = pay.transactionTimestamp,
                dateFormatted = formatDate(pay.transactionTimestamp),
                eventType = Customer360EventType.PAYMENT,
                titleArabic = "تحصيل دفعة #${pay.referenceNumber}",
                subtitleArabic = pay.descriptionArabic,
                referenceNumber = pay.referenceNumber,
                amountMinor = pay.amountMinor,
                statusArabic = "محصل ومعتمد",
                entityId = pay.localId,
                routeTarget = MekkaRoute.LEDGER
            )
        }

        for (maint in customerMaintenance) {
            timeline += Customer360TimelineEvent(
                id = "TL-MAINT-${maint.localId}",
                timestamp = maint.receivedAt,
                dateFormatted = formatDate(maint.receivedAt),
                eventType = Customer360EventType.MAINTENANCE,
                titleArabic = "صيانة ${maint.deviceBrand} ${maint.deviceModel} (#${maint.ticketNumber})",
                subtitleArabic = "العطل: ${maint.faultDescription} • الحالة: ${maint.status.arabicLabel}",
                referenceNumber = maint.ticketNumber,
                amountMinor = maint.finalTotalMinor.takeIf { it > 0L } ?: maint.estimatedCostMinor,
                statusArabic = maint.status.arabicLabel,
                entityId = maint.localId,
                routeTarget = MekkaRoute.MAINTENANCE
            )
        }

        for (ref in customerRefunds) {
            timeline += Customer360TimelineEvent(
                id = "TL-REF-${ref.localId}",
                timestamp = ref.refundTimestamp,
                dateFormatted = formatDate(ref.refundTimestamp),
                eventType = Customer360EventType.REFUND,
                titleArabic = "مرتجع مبيعات #${ref.refundNumber}",
                subtitleArabic = "السبب: ${ref.reason} (${ref.settlementMode})",
                referenceNumber = ref.refundNumber,
                amountMinor = ref.refundAmountMinor,
                statusArabic = "مسترد",
                entityId = ref.localId,
                routeTarget = MekkaRoute.SALES_POS
            )
        }

        for (wop in walletOps) {
            timeline += Customer360TimelineEvent(
                id = "TL-WAL-${wop.localId}",
                timestamp = wop.transferTimestamp,
                dateFormatted = formatDate(wop.transferTimestamp),
                eventType = Customer360EventType.WALLET_OPERATION,
                titleArabic = "عملية محفظة #${wop.transferReference}",
                subtitleArabic = "${wop.transferKind} — رقم: ${wop.customerPhoneOrAccount}",
                referenceNumber = wop.transferReference,
                amountMinor = wop.principalAmountMinor,
                statusArabic = "منفذة",
                entityId = wop.localId,
                routeTarget = MekkaRoute.WALLETS
            )
        }

        if (customer.notes.isNotBlank()) {
            timeline += Customer360TimelineEvent(
                id = "TL-NOTE-${customer.localId}",
                timestamp = customer.updatedAt,
                dateFormatted = formatDate(customer.updatedAt),
                eventType = Customer360EventType.NOTE,
                titleArabic = "ملاحظة مسجلة بملف العميل",
                subtitleArabic = customer.notes,
                referenceNumber = customer.localId,
                amountMinor = null,
                statusArabic = "ملاحظة",
                entityId = customer.localId,
                routeTarget = MekkaRoute.CUSTOMERS
            )
        }

        if (customer.creditLimitMinor > 0L) {
            timeline += Customer360TimelineEvent(
                id = "TL-CRED-${customer.localId}",
                timestamp = customer.createdAt,
                dateFormatted = formatDate(customer.createdAt),
                eventType = Customer360EventType.CREDIT_POLICY,
                titleArabic = "سياسة الائتمان المفعلة للعميل",
                subtitleArabic = "الحد الائتماني: ${Money.ofMinor(customer.creditLimitMinor).formatArabic()} • مهلة السداد: ${customer.defaultCreditDays} يوم",
                referenceNumber = customer.localId,
                amountMinor = customer.creditLimitMinor,
                statusArabic = if (creditStanding.isExceedingCreditLimit) "متجاوز للحد" else "ضمن الحد",
                entityId = customer.localId,
                routeTarget = MekkaRoute.CUSTOMERS
            )
        }

        for (audit in auditHistory.take(15)) {
            timeline += Customer360TimelineEvent(
                id = "TL-AUD-${audit.localId}",
                timestamp = audit.timestamp,
                dateFormatted = formatDate(audit.timestamp),
                eventType = Customer360EventType.AUDIT,
                titleArabic = FriendlyTerminologyMapper.toFriendlyAuditActionArabic(audit.actionType),
                subtitleArabic = audit.summaryArabic,
                referenceNumber = audit.referenceNumber.ifBlank { audit.entityId },
                amountMinor = null,
                statusArabic = audit.actorRoleCode,
                entityId = audit.localId,
                routeTarget = MekkaRoute.AUDIT_LOGS
            )
        }

        return Customer360Snapshot(
            customer = customer,
            financialProfile = financialProfile,
            creditStanding = creditStanding,
            purchasesFromShop = customerSales,
            payments = customerPayments,
            receivablesMinor = derivedBalanceMinor.coerceAtLeast(0L),
            maintenanceTickets = customerMaintenance,
            refunds = customerRefunds,
            walletOperations = walletOps,
            notes = customer.notes,
            auditHistory = auditHistory,
            isAuditAuthorized = isAuditAuthorized,
            timeline = timeline.sortedByDescending { it.timestamp }
        )
    }

    // =============================================================================================
    // 5 — SUPPLIER 360 TIMELINE & PROFILE ENGINE
    // =============================================================================================
    fun buildSupplier360Snapshot(
        supplier: SupplierEntity,
        supplierBalanceProjection: SupplierBalanceProjection?,
        allPurchases: List<PurchaseEntity>,
        allPurchaseItems: List<PurchaseItemEntity>,
        allProducts: List<ProductInventoryProjection>,
        allLedgerTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): Supplier360Snapshot {
        val supplierPurchases = allPurchases.filter { it.supplierId == supplier.localId }.sortedByDescending { it.purchaseTimestamp }
        val purchaseIds = supplierPurchases.map { it.localId }.toSet()
        val purchaseMap = supplierPurchases.associateBy { it.localId }
        val supplierPurchaseItems = allPurchaseItems.filter { it.purchaseId in purchaseIds }

        val supplierPayments = allLedgerTransactions.filter {
            it.supplierId == supplier.localId &&
                it.status == LedgerTransactionStatus.POSTED &&
                (it.relatedEntityType == "SUPPLIER_PAYMENT" || (it.relatedEntityType == "PURCHASE" && it.amountMinor > 0L))
        }.sortedByDescending { it.transactionTimestamp }

        val supplierReturns = allLedgerTransactions.filter {
            it.supplierId == supplier.localId &&
                it.status == LedgerTransactionStatus.POSTED &&
                it.relatedEntityType == "PURCHASE_RETURN"
        }.sortedByDescending { it.transactionTimestamp }

        val derivedPayable = supplierBalanceProjection?.outstandingPayableMinor ?: run {
            val unpaid = supplierPurchases.sumOf { (it.totalAmountMinor - it.paidAmountMinor).coerceAtLeast(0L) }
            val extraPaid = allLedgerTransactions.filter {
                it.supplierId == supplier.localId && it.status == LedgerTransactionStatus.POSTED && it.relatedEntityType == "SUPPLIER_PAYMENT"
            }.sumOf { it.amountMinor }
            val rets = supplierReturns.sumOf { it.amountMinor }
            (unpaid - extraPaid - rets).coerceAtLeast(0L)
        }

        val financialProfile = PartyAccountStatementEngine.buildSupplierProfile(
            supplierId = supplier.localId,
            derivedBalanceMinor = derivedPayable,
            purchases = supplierPurchases,
            ledgerTransactions = allLedgerTransactions
        )

        val projList = listOf(
            supplierBalanceProjection ?: SupplierBalanceProjection(
                supplierId = supplier.localId,
                companyName = supplier.companyName,
                contactName = supplier.contactName,
                phone = supplier.phone,
                address = supplier.address,
                taxNumber = supplier.taxNumber,
                notes = supplier.notes,
                isActive = supplier.isActive,
                outstandingPayableMinor = derivedPayable,
                totalPurchasesMinor = financialProfile.totalPurchasesMinor,
                totalPaymentsMinor = financialProfile.totalPaymentsMinor
            )
        )
        val payablesSummary = SupplierPayablesScheduleEngine.buildPayablesSchedule(
            purchases = supplierPurchases,
            supplierBalances = projList,
            nowMillis = nowMillis
        )

        val productMap = allProducts.associateBy { it.productId }

        // Supplied Products & Price History derived strictly from actual PurchaseItemEntity rows
        val priceHistory = supplierPurchaseItems.mapNotNull { item ->
            val parentPurchase = purchaseMap[item.purchaseId] ?: return@mapNotNull null
            val prod = productMap[item.productId]
            SupplierPriceHistoryEntry(
                id = "SPH-${item.localId}",
                productId = item.productId,
                productNameArabic = prod?.nameArabic ?: item.productId,
                sku = prod?.sku ?: "-",
                invoiceNumber = parentPurchase.invoiceNumber,
                unitCostMinor = item.unitCostMinor,
                quantity = item.quantity,
                timestamp = parentPurchase.purchaseTimestamp
            )
        }.sortedByDescending { it.timestamp }

        val suppliedProducts = supplierPurchaseItems.groupBy { it.productId }.map { (prodId, items) ->
            val prod = productMap[prodId]
            val sortedByTime = items.sortedByDescending { purchaseMap[it.purchaseId]?.purchaseTimestamp ?: it.createdAt }
            val latestItem = sortedByTime.first()
            val latestTs = purchaseMap[latestItem.purchaseId]?.purchaseTimestamp ?: latestItem.createdAt
            SupplierSuppliedProductSummary(
                productId = prodId,
                sku = prod?.sku ?: "-",
                nameArabic = prod?.nameArabic ?: prodId,
                totalSuppliedQty = items.sumOf { it.quantity },
                lastPurchaseCostMinor = latestItem.unitCostMinor,
                minPurchaseCostMinor = items.minOf { it.unitCostMinor },
                maxPurchaseCostMinor = items.maxOf { it.unitCostMinor },
                currentWacMinor = prod?.weightedAvgCostMinor ?: latestItem.unitCostMinor,
                currentStockQty = prod?.derivedStockQty ?: 0,
                lastSuppliedAt = latestTs
            )
        }.sortedByDescending { it.lastSuppliedAt }

        val timeline = mutableListOf<Supplier360TimelineEvent>()

        for (pur in supplierPurchases) {
            val unpaid = (pur.totalAmountMinor - pur.paidAmountMinor).coerceAtLeast(0L)
            timeline += Supplier360TimelineEvent(
                id = "STL-PUR-${pur.localId}",
                timestamp = pur.purchaseTimestamp,
                dateFormatted = formatDate(pur.purchaseTimestamp),
                eventType = Supplier360EventType.PURCHASE,
                titleArabic = "فاتورة شراء #${pur.invoiceNumber}",
                subtitleArabic = "الإجمالي: ${Money.ofMinor(pur.totalAmountMinor).formatArabic()} • المسدد: ${Money.ofMinor(pur.paidAmountMinor).formatArabic()}",
                referenceNumber = pur.invoiceNumber,
                amountMinor = pur.totalAmountMinor,
                statusArabic = if (unpaid == 0L) "مسددة بالكامل" else "آجل جزئي/كلي",
                entityId = pur.localId,
                routeTarget = MekkaRoute.PURCHASES
            )
            if (unpaid > 0L) {
                timeline += Supplier360TimelineEvent(
                    id = "STL-PAYB-${pur.localId}",
                    timestamp = pur.purchaseTimestamp + 1L,
                    dateFormatted = formatDate(pur.purchaseTimestamp),
                    eventType = Supplier360EventType.PAYABLE,
                    titleArabic = "مستحق آجل للمورد على فاتورة #${pur.invoiceNumber}",
                    subtitleArabic = "متبقي للمورد: ${Money.ofMinor(unpaid).formatArabic()}",
                    referenceNumber = pur.invoiceNumber,
                    amountMinor = unpaid,
                    statusArabic = "مستحق السداد",
                    entityId = pur.localId,
                    routeTarget = MekkaRoute.SUPPLIERS
                )
            }
        }

        for (pay in supplierPayments.filter { it.relatedEntityType == "SUPPLIER_PAYMENT" }) {
            timeline += Supplier360TimelineEvent(
                id = "STL-PAY-${pay.localId}",
                timestamp = pay.transactionTimestamp,
                dateFormatted = formatDate(pay.transactionTimestamp),
                eventType = Supplier360EventType.PAYMENT,
                titleArabic = "سداد دفعة للمورد #${pay.referenceNumber}",
                subtitleArabic = pay.descriptionArabic,
                referenceNumber = pay.referenceNumber,
                amountMinor = pay.amountMinor,
                statusArabic = "مسدد ومعتمد",
                entityId = pay.localId,
                routeTarget = MekkaRoute.LEDGER
            )
        }

        for (ret in supplierReturns) {
            timeline += Supplier360TimelineEvent(
                id = "STL-RET-${ret.localId}",
                timestamp = ret.transactionTimestamp,
                dateFormatted = formatDate(ret.transactionTimestamp),
                eventType = Supplier360EventType.RETURN,
                titleArabic = "مرتجع مشتريات للمورد #${ret.referenceNumber}",
                subtitleArabic = ret.descriptionArabic,
                referenceNumber = ret.referenceNumber,
                amountMinor = ret.amountMinor,
                statusArabic = "مرتجع معتمد",
                entityId = ret.localId,
                routeTarget = MekkaRoute.PURCHASES
            )
        }

        return Supplier360Snapshot(
            supplier = supplier,
            financialProfile = financialProfile,
            payablesSummary = payablesSummary,
            purchases = supplierPurchases,
            payments = supplierPayments,
            returns = supplierReturns,
            payablesMinor = derivedPayable,
            suppliedProducts = suppliedProducts,
            priceHistory = priceHistory,
            statementLines = financialProfile.statementLines,
            timeline = timeline.sortedByDescending { it.timestamp }
        )
    }
}

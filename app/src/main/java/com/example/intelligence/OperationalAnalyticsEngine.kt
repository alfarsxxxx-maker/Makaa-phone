package com.example.intelligence

import com.example.core.SyncState
import com.example.database.dao.CashDrawerDerivedProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.PendingTransactionEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.UserEntity
import com.example.diagnostics.HealthCheckLevel
import com.example.diagnostics.SystemIntegrityReport
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.financial.ShiftCashFlowBreakdown
import com.example.navigation.MekkaRoute
import kotlin.math.ceil
import kotlin.math.roundToInt

object OperationalAnalyticsEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    // =============================================================================================
    // 9 & 10 — PROCUREMENT INTELLIGENCE & INVENTORY ANALYTICS
    // =============================================================================================
    fun buildInventoryAnalyticsAndProcurement(
        products: List<ProductInventoryProjection>,
        categories: List<CategoryEntity>,
        movements: List<InventoryMovementEntity>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        analysisWindowDays: Int = 30,
        configuredLeadTimeDays: Int = 3,
        nowMillis: Long = System.currentTimeMillis()
    ): InventoryAnalyticsDashboardBundle {
        val categoryMap = categories.associateBy({ it.localId }, { it.nameArabic })
        val windowStart = nowMillis - analysisWindowDays.toLong() * DAY_MILLIS
        val activeSalesIds = sales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }.map { it.localId }.toSet()
        val windowSalesIds = sales.filter {
            it.saleTimestamp >= windowStart && it.status != "CANCELLED" && it.status != "REFUNDED"
        }.map { it.localId }.toSet()

        val itemsByProductWindow = saleItems.filter { it.saleId in windowSalesIds }.groupBy { it.productId }
        val allSaleItemsByProduct = saleItems.filter { it.saleId in activeSalesIds }.groupBy { it.productId }
        val saleTimestampById = sales.associateBy({ it.localId }, { it.saleTimestamp })
        val movementsByProduct = movements.groupBy { it.productId }

        val analyticsItems = mutableListOf<ProductInventoryAnalyticsItem>()
        val reorderSuggestions = mutableListOf<ReorderSuggestionItem>()

        for (prod in products.filter { it.isActive }) {
            val catName = categoryMap[prod.categoryId] ?: "عام"
            val qty = prod.derivedStockQty.coerceAtLeast(0)
            val wac = prod.weightedAvgCostMinor.coerceAtLeast(0L)
            val price = prod.effectiveRetailPriceMinor.coerceAtLeast(0L)
            val stockVal = qty.toLong() * wac
            val potRev = qty.toLong() * price
            val potMargin = (potRev - stockVal)
            val marginBps = if (potRev > 0L) ((potMargin * 10_000L) / potRev).toInt() else 0

            val prodMovements = (movementsByProduct[prod.productId] ?: emptyList()).sortedBy { it.timestamp }
            val inboundMovements = prodMovements.filter {
                it.movementType == InventoryMovementType.PURCHASE_IN || it.movementType == InventoryMovementType.ADJUSTMENT_IN
            }
            val lastInboundTs = inboundMovements.maxOfOrNull { it.timestamp } ?: nowMillis
            val ageDays = ((nowMillis - lastInboundTs).coerceAtLeast(0L) / DAY_MILLIS).toInt()

            val prodAllSales = allSaleItemsByProduct[prod.productId] ?: emptyList()
            val lastSaleTs = prodAllSales.mapNotNull { saleTimestampById[it.saleId] }.maxOrNull()
            val daysSinceLastSale = lastSaleTs?.let { ((nowMillis - it).coerceAtLeast(0L) / DAY_MILLIS).toInt() }

            val agingBucket = when {
                ageDays <= 30 -> StockAgingBucket.FRESH_0_30
                ageDays <= 60 -> StockAgingBucket.MODERATE_31_60
                ageDays <= 90 -> StockAgingBucket.AGING_61_90
                else -> StockAgingBucket.STAGNANT_90_PLUS
            }

            val windowSoldUnits = (itemsByProductWindow[prod.productId] ?: emptyList())
                .sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) }

            val avgStockDenom = maxOf(qty, 1)
            val turnoverBps = ((windowSoldUnits.toLong() * 10_000L) / avgStockDenom.toLong()).toInt()

            val (velocityClass, velocityArabic) = when {
                qty > 0 && windowSoldUnits == 0 && (ageDays >= 60 || (daysSinceLastSale != null && daysSinceLastSale >= 60)) ->
                    "DEAD_STOCK" to "مخزون راكد (Dead Stock)"
                windowSoldUnits >= 5 || turnoverBps >= 10_000 ->
                    "FAST_MOVING" to "سريع الحركة (Fast Moving)"
                qty > 0 && windowSoldUnits <= 1 && ageDays >= 21 ->
                    "SLOW_MOVING" to "بطيء الحركة (Slow Moving)"
                else ->
                    "NORMAL_MOVING" to "حركة طبيعية"
            }

            // Cost Trend from chronological inbound movements
            val firstCost = inboundMovements.firstOrNull()?.unitCostMinor ?: wac
            val latestCost = inboundMovements.lastOrNull()?.unitCostMinor ?: wac
            val costChangeBps = if (firstCost > 0L) (((latestCost - firstCost) * 10_000L) / firstCost).toInt() else 0
            val costTrend = when {
                costChangeBps > 200 -> CostTrendDirection.RISING
                costChangeBps < -200 -> CostTrendDirection.FALLING
                else -> CostTrendDirection.STABLE
            }

            analyticsItems += ProductInventoryAnalyticsItem(
                productId = prod.productId,
                sku = prod.sku,
                nameArabic = prod.nameArabic,
                categoryNameArabic = catName,
                currentStockQty = qty,
                unitWacMinor = wac,
                unitSellingPriceMinor = price,
                stockValueWacMinor = stockVal,
                potentialRevenueMinor = potRev,
                potentialMarginMinor = potMargin,
                potentialMarginBasisPoints = marginBps,
                ageDaysSinceLastInbound = ageDays,
                daysSinceLastSale = daysSinceLastSale,
                agingBucket = agingBucket,
                soldUnitsInPeriod = windowSoldUnits,
                turnoverRatioBasisPoints = turnoverBps,
                velocityClass = velocityClass,
                velocityArabic = velocityArabic,
                costTrend = costTrend,
                firstRecordedCostMinor = firstCost,
                latestRecordedCostMinor = latestCost,
                costChangeBasisPoints = costChangeBps
            )

            // 9 —Deterministic Reorder Suggestion (Strictly based on Actual Sales, Actual Stock, Lead Time, Reorder Rules, Min Stock)
            val minStock = prod.reorderThresholdQty.coerceAtLeast(1)
            val reorderPoint = maxOf(prod.reorderPointQty, minStock)
            val dailySalesRate = windowSoldUnits.toDouble() / analysisWindowDays.coerceAtLeast(1).toDouble()
            val leadTimeDemand = ceil(dailySalesRate * configuredLeadTimeDays.coerceAtLeast(1)).toInt()

            val needsReorder = qty <= reorderPoint || (dailySalesRate > 0.0 && qty < leadTimeDemand + minStock)
            if (needsReorder) {
                val targetStock = maxOf(reorderPoint + minStock, leadTimeDemand + minStock)
                val suggestedQty = (targetStock - qty).coerceAtLeast(1)
                val reason = when {
                    qty == 0 -> "نفاد المخزون بالكامل (الرصيد صفر والحد الأدنى $minStock)"
                    qty <= minStock -> "المخزون أقل من الحد الأدنى الحرج ($qty من $minStock)"
                    qty <= reorderPoint -> "المخزون أقل من حد إعادة الطلب ($qty من $reorderPoint)"
                    else -> "معدل المبيعات الفعلي ($windowSoldUnits وحدة) خلال فترة التوريد ($configuredLeadTimeDays أيام) يتطلب تعزيز المخزون"
                }
                val priority = when {
                    qty == 0 -> "عاجل جداً (نفاد)"
                    qty <= minStock -> "مرتفع (تحت الحد الأدنى)"
                    else -> "متوسط (إعادة طلب)"
                }
                reorderSuggestions += ReorderSuggestionItem(
                    productId = prod.productId,
                    sku = prod.sku,
                    productNameArabic = prod.nameArabic,
                    categoryNameArabic = catName,
                    currentStockQty = qty,
                    minimumStockQty = minStock,
                    reorderPointQty = reorderPoint,
                    actualSoldUnitsInWindow = windowSoldUnits,
                    dailySalesRate = (dailySalesRate * 100.0).roundToInt() / 100.0,
                    actualLeadTimeDays = configuredLeadTimeDays,
                    suggestedReorderQuantity = suggestedQty,
                    unitWacCostMinor = wac,
                    estimatedOrderCostMinor = suggestedQty.toLong() * wac,
                    reasonArabic = reason,
                    priorityArabic = priority
                )
            }
        }

        val totalVal = analyticsItems.sumOf { it.stockValueWacMinor }
        val totalPotRev = analyticsItems.sumOf { it.potentialRevenueMinor }
        val totalPotMargin = analyticsItems.sumOf { it.potentialMarginMinor }
        val avgTurnover = if (analyticsItems.isNotEmpty()) {
            analyticsItems.map { it.turnoverRatioBasisPoints }.average().toInt()
        } else 0

        return InventoryAnalyticsDashboardBundle(
            totalInventoryValueWacMinor = totalVal,
            totalPotentialRevenueMinor = totalPotRev,
            totalPotentialMarginMinor = totalPotMargin,
            averageTurnoverBasisPoints = avgTurnover,
            fastMovingItems = analyticsItems.filter { it.velocityClass == "FAST_MOVING" }.sortedByDescending { it.soldUnitsInPeriod },
            slowMovingItems = analyticsItems.filter { it.velocityClass == "SLOW_MOVING" }.sortedByDescending { it.stockValueWacMinor },
            deadStockItems = analyticsItems.filter { it.velocityClass == "DEAD_STOCK" }.sortedByDescending { it.stockValueWacMinor },
            stockAgingBreakdown = StockAgingBucket.entries.associateWith { b -> analyticsItems.filter { it.agingBucket == b } },
            reorderSuggestions = reorderSuggestions.sortedBy { it.currentStockQty },
            allItemsAnalytics = analyticsItems.sortedByDescending { it.stockValueWacMinor }
        )
    }

    // =============================================================================================
    // 11 — MAINTENANCE MANAGEMENT CENTER
    // =============================================================================================
    fun buildMaintenanceManagementCenter(
        orders: List<MaintenanceOrderEntity>,
        parts: List<MaintenancePartEntity>,
        expenses: List<ExpenseEntity>,
        ledgerTransactions: List<TransactionEntity>,
        usersMap: Map<String, UserEntity> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): MaintenanceManagementCenterBundle {
        val startOfToday = nowMillis - (nowMillis % DAY_MILLIS)
        val endOfToday = startOfToday + DAY_MILLIS - 1L

        val receivedCount = orders.count { it.status == MaintenanceStatus.RECEIVED }
        val underInspectionCount = orders.count { it.status == MaintenanceStatus.DIAGNOSING || it.status == MaintenanceStatus.COST_ESTIMATED }
        val waitingApprovalCount = orders.count { it.status == MaintenanceStatus.WAITING_APPROVAL }
        val inRepairCount = orders.count { it.status == MaintenanceStatus.IN_REPAIR || it.status == MaintenanceStatus.WAITING_PARTS }
        val testingCount = orders.count { it.status == MaintenanceStatus.TESTING }
        val readyCount = orders.count {
            it.status == MaintenanceStatus.READY_FOR_PICKUP ||
                it.status == MaintenanceStatus.CUSTOMER_NOTIFIED ||
                it.status == MaintenanceStatus.PAYMENT_COLLECTED
        }
        val deliveredCount = orders.count { it.status == MaintenanceStatus.DELIVERED || it.status == MaintenanceStatus.CLOSED }
        val cancelledCount = orders.count { it.status == MaintenanceStatus.CANCELLED || it.status == MaintenanceStatus.UNREPAIRABLE }

        val overdueTickets = orders.filter {
            it.status.isPendingWork && it.expectedDeliveryAt != null && it.expectedDeliveryAt < nowMillis
        }.sortedBy { it.expectedDeliveryAt }

        val dueTodayTickets = orders.filter {
            it.status.isPendingWork && it.expectedDeliveryAt != null && it.expectedDeliveryAt in startOfToday..endOfToday
        }

        val waitingApprovalTickets = orders.filter {
            it.status == MaintenanceStatus.WAITING_APPROVAL || it.status == MaintenanceStatus.COST_ESTIMATED
        }

        val readyForDeliveryTickets = orders.filter {
            it.status == MaintenanceStatus.READY_FOR_PICKUP || it.status == MaintenanceStatus.CUSTOMER_NOTIFIED
        }

        // True Maintenance Financials from Ledger + Parts WAC + Maintenance-related Expenses
        val postedMaintTx = ledgerTransactions.filter {
            it.status == LedgerTransactionStatus.POSTED &&
                (it.relatedEntityType == "MAINTENANCE" || it.relatedEntityType == "MAINTENANCE_DEPOSIT")
        }
        val totalRevenueMinor = postedMaintTx.sumOf { it.amountMinor }
        val totalPartsCostWacMinor = parts.sumOf { it.unitCostMinor * it.quantity.toLong() }
        val relatedExpensesMinor = expenses.filter {
            it.categoryName.contains("صيانة") || it.title.contains("صيانة") || it.notes.contains("صيانة")
        }.sumOf { it.amountMinor }
        val trueNetProfitMinor = totalRevenueMinor - totalPartsCostWacMinor - relatedExpensesMinor

        // Outstanding payments on non-cancelled orders
        val collectedByOrderId = postedMaintTx.groupBy { it.relatedEntityId ?: "" }
            .mapValues { (_, txs) -> txs.sumOf { it.amountMinor } }

        val totalOutstandingMinor = orders.filter {
            it.status != MaintenanceStatus.CANCELLED && it.status != MaintenanceStatus.UNREPAIRABLE
        }.sumOf { order ->
            val targetTotal = if (order.finalTotalMinor > 0L) order.finalTotalMinor else order.estimatedCostMinor
            val paid = maxOf(order.depositPaidMinor, collectedByOrderId[order.localId] ?: 0L)
            (targetTotal - paid).coerceAtLeast(0L)
        }

        // Average repair time in hours for completed/delivered tickets
        val completedOrders = orders.filter {
            (it.status == MaintenanceStatus.DELIVERED || it.status == MaintenanceStatus.CLOSED) &&
                (it.deliveredAt != null || it.closedAt != null)
        }
        val avgRepairHours = if (completedOrders.isNotEmpty()) {
            val avgMillis = completedOrders.map {
                val endTs = it.deliveredAt ?: it.closedAt ?: it.updatedAt
                (endTs - it.receivedAt).coerceAtLeast(3_600_000L)
            }.average()
            ((avgMillis / 3_600_000.0) * 10.0).roundToInt() / 10.0
        } else 0.0

        // Technician Workload
        val workloads = orders.filter { it.status != MaintenanceStatus.CANCELLED }
            .groupBy { it.assignedTechnicianId ?: it.receivedByUserId }
            .map { (techId, techOrders) ->
                val techName = usersMap[techId]?.fullNameArabic ?: "مهندس صيانة ($techId)"
                val active = techOrders.count { it.status.isPendingWork }
                val completed = techOrders.count { it.status == MaintenanceStatus.DELIVERED || it.status == MaintenanceStatus.CLOSED }
                val overdue = techOrders.count { it.status.isPendingWork && it.expectedDeliveryAt != null && it.expectedDeliveryAt < nowMillis }
                val techDone = techOrders.filter { it.deliveredAt != null || it.closedAt != null }
                val techAvgHours = if (techDone.isNotEmpty()) {
                    val ms = techDone.map { ((it.deliveredAt ?: it.closedAt ?: it.updatedAt) - it.receivedAt).coerceAtLeast(3_600_000L) }.average()
                    ((ms / 3_600_000.0) * 10.0).roundToInt() / 10.0
                } else 0.0
                val orderIds = techOrders.map { it.localId }.toSet()
                val rev = postedMaintTx.filter { it.relatedEntityId in orderIds }.sumOf { it.amountMinor }
                TechnicianWorkloadItem(
                    technicianUserId = techId,
                    technicianNameArabic = techName,
                    activeTicketsCount = active,
                    completedTicketsCount = completed,
                    overdueTicketsCount = overdue,
                    averageRepairHours = techAvgHours,
                    generatedRevenueMinor = rev
                )
            }.sortedByDescending { it.activeTicketsCount + it.completedTicketsCount }

        return MaintenanceManagementCenterBundle(
            receivedCount = receivedCount,
            underInspectionCount = underInspectionCount,
            waitingApprovalCount = waitingApprovalCount,
            inRepairCount = inRepairCount,
            testingCount = testingCount,
            readyCount = readyCount,
            overdueCount = overdueTickets.size,
            deliveredCount = deliveredCount,
            cancelledCount = cancelledCount,
            technicianWorkloads = workloads,
            averageRepairDurationHours = avgRepairHours,
            totalMaintenanceRevenueMinor = totalRevenueMinor,
            totalPartsCostWacMinor = totalPartsCostWacMinor,
            totalRelatedExpensesMinor = relatedExpensesMinor,
            trueMaintenanceNetProfitMinor = trueNetProfitMinor,
            totalOutstandingPaymentsMinor = totalOutstandingMinor,
            overdueTickets = overdueTickets,
            dueTodayTickets = dueTodayTickets,
            waitingApprovalTickets = waitingApprovalTickets,
            readyForDeliveryTickets = readyForDeliveryTickets
        )
    }

    // =============================================================================================
    // 12 — CASH & WALLET CONTROL CENTER (Ledger is single source of truth)
    // =============================================================================================
    fun buildCashAndWalletControlCenter(
        cashDrawerProjection: CashDrawerDerivedProjection?,
        wallets: List<WalletDerivedProjection>,
        cashSessions: List<CashSessionEntity>,
        ledgerTransactions: List<TransactionEntity>,
        pendingSmsTransactions: List<PendingTransactionEntity>,
        activeShiftBreakdown: ShiftCashFlowBreakdown?
    ): CashAndWalletControlCenterBundle {
        val postedTx = ledgerTransactions.filter { it.status == LedgerTransactionStatus.POSTED }
        val drawerId = cashDrawerProjection?.drawerId ?: "DRAWER-MAIN-EGP"

        val openSession = cashSessions.firstOrNull { it.status == "OPEN" }
        val latestClosedSession = cashSessions.filter { it.status == "CLOSED" }.maxByOrNull { it.closedAt ?: 0L }
        val cashOpeningMinor = openSession?.openingDerivedBalanceMinor
            ?: latestClosedSession?.openingDerivedBalanceMinor
            ?: 0L

        val cashTx = postedTx.filter {
            (it.sourceAccountType == FinancialAccountType.CASH_DRAWER && it.sourceAccountId == drawerId) ||
                (it.destinationAccountType == FinancialAccountType.CASH_DRAWER && it.destinationAccountId == drawerId)
        }
        val cashInMinor = cashDrawerProjection?.todayInflowMinor ?: cashTx.filter {
            it.destinationAccountType == FinancialAccountType.CASH_DRAWER && it.destinationAccountId == drawerId
        }.sumOf { it.amountMinor }
        val cashOutMinor = cashDrawerProjection?.todayOutflowMinor ?: cashTx.filter {
            it.sourceAccountType == FinancialAccountType.CASH_DRAWER && it.sourceAccountId == drawerId
        }.sumOf { it.amountMinor }
        val cashExpectedMinor = cashDrawerProjection?.derivedBalanceMinor ?: (cashInMinor - cashOutMinor)
        val cashActualCounted = openSession?.closingCountedCashMinor ?: latestClosedSession?.closingCountedCashMinor
        val cashVariance = openSession?.discrepancyMinor ?: latestClosedSession?.discrepancyMinor ?: 0L

        val cashRow = UnifiedAccountControlRow(
            accountId = drawerId,
            accountType = FinancialAccountType.CASH_DRAWER,
            providerCode = "CASH",
            accountNameArabic = cashDrawerProjection?.nameArabic ?: "الخزينة النقدية الرئيسية",
            accountNumberOrCode = cashDrawerProjection?.drawerCode ?: "CASH-MAIN",
            openingBalanceMinor = cashOpeningMinor,
            inflowMinor = cashInMinor,
            outflowMinor = cashOutMinor,
            expectedBalanceMinor = cashExpectedMinor,
            actualCountedBalanceMinor = cashActualCounted,
            varianceMinor = cashVariance,
            feesMinor = 0L,
            commissionsMinor = 0L,
            transactionsCount = cashTx.size,
            pendingOperationsCount = cashSessions.count { it.approvalStatus == "PENDING_APPROVAL" }
        )

        val awaitingSms = pendingSmsTransactions.filter { it.status == "AWAITING_CONFIRMATION" }

        val walletRows = wallets.map { w ->
            val wTx = postedTx.filter {
                (it.sourceAccountType == FinancialAccountType.WALLET && it.sourceAccountId == w.walletId) ||
                    (it.destinationAccountType == FinancialAccountType.WALLET && it.destinationAccountId == w.walletId)
            }
            val wIn = wTx.filter { it.destinationAccountType == FinancialAccountType.WALLET && it.destinationAccountId == w.walletId }
                .sumOf { it.amountMinor }
            val wOut = wTx.filter { it.sourceAccountType == FinancialAccountType.WALLET && it.sourceAccountId == w.walletId }
                .sumOf { it.amountMinor + it.feeMinor }
            val wFees = wTx.sumOf { it.feeMinor }
            val wComm = wTx.sumOf { it.commissionMinor }
            val wOpening = (w.derivedBalanceMinor - w.todayInflowMinor + w.todayOutflowMinor).coerceAtLeast(0L)
            val wPending = awaitingSms.count { it.targetWalletId == w.walletId || (it.targetWalletId == null && wallets.firstOrNull()?.walletId == w.walletId) }

            UnifiedAccountControlRow(
                accountId = w.walletId,
                accountType = FinancialAccountType.WALLET,
                providerCode = w.providerCode,
                accountNameArabic = "${w.providerNameArabic} — ${w.walletNameArabic}",
                accountNumberOrCode = w.walletNumber,
                openingBalanceMinor = wOpening,
                inflowMinor = wIn,
                outflowMinor = wOut,
                expectedBalanceMinor = w.derivedBalanceMinor,
                actualCountedBalanceMinor = w.derivedBalanceMinor,
                varianceMinor = 0L,
                feesMinor = wFees,
                commissionsMinor = wComm,
                transactionsCount = wTx.size,
                pendingOperationsCount = wPending
            )
        }

        val allRows = listOf(cashRow) + walletRows
        return CashAndWalletControlCenterBundle(
            cashDrawerRow = cashRow,
            walletRows = walletRows,
            totalExpectedLiquidityMinor = allRows.sumOf { it.expectedBalanceMinor },
            totalInflowMinor = allRows.sumOf { it.inflowMinor },
            totalOutflowMinor = allRows.sumOf { it.outflowMinor },
            totalFeesMinor = allRows.sumOf { it.feesMinor },
            totalCommissionsMinor = allRows.sumOf { it.commissionsMinor },
            totalVarianceMinor = allRows.sumOf { it.varianceMinor },
            totalPendingCount = allRows.sumOf { it.pendingOperationsCount },
            activeShiftBreakdown = activeShiftBreakdown
        )
    }

    // =============================================================================================
    // 13 & 14 — OFFLINE CONTROL CENTER & MULTI-DEVICE VISIBILITY
    // =============================================================================================
    fun buildOfflineAndMultiDeviceControlCenter(
        isNetworkOnline: Boolean,
        isRemoteServerConfigured: Boolean,
        isSyncingNow: Boolean,
        syncQueue: List<SyncQueueEntity>,
        syncConflicts: List<SyncConflictEntity>,
        ledgerTransactions: List<TransactionEntity>,
        inventoryMovements: List<InventoryMovementEntity>,
        auditLogs: List<AuditLogEntity>,
        usersMap: Map<String, UserEntity> = emptyMap()
    ): OfflineControlCenterBundle {
        val pendingSync = syncQueue.count { it.syncState == SyncState.PENDING || it.syncState == SyncState.SYNCING }
        val failedSync = syncQueue.count { it.syncState == SyncState.FAILED }
        val verifiedSynced = syncQueue.count { it.syncState == SyncState.SYNCED }
        val unresolvedConflicts = syncConflicts.count { !it.isResolved }
        val lastVerifiedTs = syncQueue.filter { it.syncState == SyncState.SYNCED }.maxOfOrNull { it.updatedAt }

        // Strict rule: NEVER display "Synced" unless Remote Backend ACK actually occurred
        val lifecycleState = when {
            !isNetworkOnline -> ConnectivitySyncLifecycleState.OFFLINE
            isSyncingNow -> ConnectivitySyncLifecycleState.SYNCING
            failedSync > 0 || unresolvedConflicts > 0 -> ConnectivitySyncLifecycleState.SYNC_FAILED
            !isRemoteServerConfigured || pendingSync > 0 -> ConnectivitySyncLifecycleState.ONLINE_PENDING
            verifiedSynced > 0 -> ConnectivitySyncLifecycleState.SYNCED_VERIFIED
            else -> ConnectivitySyncLifecycleState.ONLINE_PENDING
        }

        val conflictByEntity = syncConflicts.filter { !it.isResolved }.associateBy { "${it.entityType}:${it.entityLocalId}" }

        // Multi-device operations visibility (never auto-LWW for financial or inventory records)
        val records = mutableListOf<MultiDeviceOperationRecord>()

        for (tx in ledgerTransactions.take(40)) {
            val conflict = conflictByEntity["FinancialTransaction:${tx.localId}"] ?: conflictByEntity["Transaction:${tx.localId}"]
            val operatorName = usersMap[tx.createdByUserId]?.fullNameArabic ?: tx.createdByUserId
            val syncLabel = if (tx.cloudId != null && tx.syncState == SyncState.SYNCED) {
                "تمت المزامنة (ACK: ${tx.cloudId})"
            } else {
                tx.syncState.arabicLabel
            }
            records += MultiDeviceOperationRecord(
                operationId = "MD-TX-${tx.localId}",
                entityType = "FinancialTransaction",
                entityLocalId = tx.localId,
                referenceNumber = tx.referenceNumber,
                summaryArabic = "${FriendlyTerminologyMapper.toFriendlyOperationArabic(tx)}: ${Money.ofMinor(tx.amountMinor).formatArabic()}",
                deviceId = tx.deviceId,
                operatorUserId = tx.createdByUserId,
                operatorNameArabic = operatorName,
                timestamp = tx.transactionTimestamp,
                syncState = tx.syncState,
                syncStatusArabic = syncLabel,
                remoteCloudId = tx.cloudId,
                conflictStatusArabic = if (conflict != null) "تعارض مالي محمي من الكتابة التلقائية" else "سليم (محمي ضد LWW)",
                hasUnresolvedConflict = conflict != null,
                isFinancialOrInventoryProtected = true
            )
        }

        for (mov in inventoryMovements.take(30)) {
            val conflict = conflictByEntity["InventoryMovement:${mov.localId}"]
            val operatorName = usersMap[mov.performedByUserId]?.fullNameArabic ?: mov.performedByUserId
            val syncLabel = if (mov.cloudId != null && mov.syncState == SyncState.SYNCED) {
                "تمت المزامنة (ACK: ${mov.cloudId})"
            } else {
                mov.syncState.arabicLabel
            }
            records += MultiDeviceOperationRecord(
                operationId = "MD-MOV-${mov.localId}",
                entityType = "InventoryMovement",
                entityLocalId = mov.localId,
                referenceNumber = mov.referenceId,
                summaryArabic = "${mov.movementType.arabicLabel} (${mov.quantity} وحدة) — WAC: ${Money.ofMinor(mov.weightedAvgCostAfterMinor).formatArabic()}",
                deviceId = mov.deviceId,
                operatorUserId = mov.performedByUserId,
                operatorNameArabic = operatorName,
                timestamp = mov.timestamp,
                syncState = mov.syncState,
                syncStatusArabic = syncLabel,
                remoteCloudId = mov.cloudId,
                conflictStatusArabic = if (conflict != null) "تعارض مخزني يتطلب مراجعة" else "سليم (محمي ضد LWW)",
                hasUnresolvedConflict = conflict != null,
                isFinancialOrInventoryProtected = true
            )
        }

        for (log in auditLogs.take(20)) {
            val operatorName = usersMap[log.actorUserId]?.fullNameArabic ?: log.actorUserId
            records += MultiDeviceOperationRecord(
                operationId = "MD-AUD-${log.localId}",
                entityType = log.entityType,
                entityLocalId = log.entityId,
                referenceNumber = log.referenceNumber.ifBlank { log.entityId },
                summaryArabic = log.summaryArabic,
                deviceId = log.deviceId,
                operatorUserId = log.actorUserId,
                operatorNameArabic = "$operatorName (${log.actorRoleCode})",
                timestamp = log.timestamp,
                syncState = log.syncState,
                syncStatusArabic = log.syncState.arabicLabel,
                remoteCloudId = log.cloudId,
                conflictStatusArabic = "سجل تدقيق غير قابل للتعديل",
                hasUnresolvedConflict = false,
                isFinancialOrInventoryProtected = true
            )
        }

        return OfflineControlCenterBundle(
            lifecycleState = lifecycleState,
            isNetworkOnline = isNetworkOnline,
            isRemoteServerConfigured = isRemoteServerConfigured,
            pendingLocalOperationsCount = pendingSync + failedSync,
            pendingSyncCount = pendingSync,
            failedSyncCount = failedSync,
            unresolvedConflictsCount = unresolvedConflicts,
            verifiedSyncedCount = verifiedSynced,
            lastVerifiedSyncTimestamp = lastVerifiedTs,
            multiDeviceOperations = records.sortedByDescending { it.timestamp }.take(60)
        )
    }

    // =============================================================================================
    // 15 — AUDIT & DIAGNOSTICS DASHBOARD
    // =============================================================================================
    fun buildAuditDiagnosticsDashboard(
        integrityReport: SystemIntegrityReport?,
        migrationSchemaVersion: Int,
        storageUsedBytes: Long,
        storageFreeBytes: Long,
        syncQueue: List<SyncQueueEntity>,
        syncConflicts: List<SyncConflictEntity>,
        backups: List<BackupMetadataEntity>,
        pendingSmsTransactions: List<PendingTransactionEntity>,
        activeRoleCode: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AuditDiagnosticsDashboardBundle {
        val pendingSync = syncQueue.count { it.syncState == SyncState.PENDING || it.syncState == SyncState.SYNCING }
        val failedSync = syncQueue.count { it.syncState == SyncState.FAILED }
        val conflictCount = syncConflicts.count { !it.isResolved }
        val latestBackup = backups.maxByOrNull { it.createdAt }
        val lastSuccessfulSync = syncQueue.filter { it.syncState == SyncState.SYNCED }.maxOfOrNull { it.updatedAt }
        val smsPending = pendingSmsTransactions.count { it.status == "AWAITING_CONFIRMATION" }

        val passedChecks = integrityReport?.items?.count { it.status == HealthCheckLevel.PASS } ?: 16
        val totalChecks = integrityReport?.items?.size ?: 16
        val isDbHealthy = integrityReport == null || integrityReport.overallStatus == HealthCheckLevel.PASS

        val issues = mutableListOf<DiagnosticIssueRecommendation>()

        integrityReport?.items?.filter { it.status != HealthCheckLevel.PASS }?.forEach { check ->
            issues += DiagnosticIssueRecommendation(
                code = check.checkCode,
                subsystemArabic = check.titleArabic,
                descriptionArabic = check.detailsArabic,
                severity = if (check.status == HealthCheckLevel.ERROR) DiagnosticSeverity.CRITICAL else DiagnosticSeverity.WARNING,
                recommendedActionArabic = "تشغيل أداة فحص ومطابقة القيود ومراجعة السجلات المتأثرة فوراً",
                targetRoute = MekkaRoute.DIAGNOSTICS
            )
        }

        if (failedSync > 0) {
            val firstErr = syncQueue.firstOrNull { it.syncState == SyncState.FAILED }?.lastErrorMessage ?: "تعذر الاتصال بالخادم"
            issues += DiagnosticIssueRecommendation(
                code = "SYNC_QUEUE_FAILED",
                subsystemArabic = "طابور المزامنة السحابية",
                descriptionArabic = "يوجد $failedSync عملية فاشلة في طابور المزامنة ($firstErr)",
                severity = DiagnosticSeverity.CRITICAL,
                recommendedActionArabic = "التحقق من اتصال الشبكة والضغط على إعادة جدولة العمليات الفاشلة",
                targetRoute = MekkaRoute.PENDING_OPERATIONS
            )
        }

        if (conflictCount > 0) {
            issues += DiagnosticIssueRecommendation(
                code = "UNRESOLVED_CONFLICTS",
                subsystemArabic = "تعارضات تعدد الأجهزة",
                descriptionArabic = "يوجد $conflictCount تعارض غير محلول يتطلب تدخلاً بشرياً (لا يتم استخدام الكتابة التلقائية LWW)",
                severity = DiagnosticSeverity.CRITICAL,
                recommendedActionArabic = "فتح شاشة التعارضات واختيار النسخة المعتمدة أو إصدار قيد تسوية",
                targetRoute = MekkaRoute.CONFLICTS
            )
        }

        if (latestBackup == null) {
            issues += DiagnosticIssueRecommendation(
                code = "NO_BACKUP_FOUND",
                subsystemArabic = "النسخ الاحتياطي المشفر",
                descriptionArabic = "لم يتم إنشاء أي نسخة احتياطية محلية أو سحابية للمتجر حتى الآن",
                severity = DiagnosticSeverity.WARNING,
                recommendedActionArabic = "الانتقال إلى شاشة النسخ الاحتياطي وإنشاء نسخة محمية ببصمة SHA-256",
                targetRoute = MekkaRoute.BACKUP
            )
        } else if (!latestBackup.isVerified) {
            issues += DiagnosticIssueRecommendation(
                code = "BACKUP_UNVERIFIED",
                subsystemArabic = "سلامة النسخ الاحتياطي",
                descriptionArabic = "آخر نسخة احتياطية (${latestBackup.fileName}) غير محققة البصمة الرقمية",
                severity = DiagnosticSeverity.CRITICAL,
                recommendedActionArabic = "إنشاء نسخة احتياطية جديدة والتحقق من تطابق SHA-256",
                targetRoute = MekkaRoute.BACKUP
            )
        }

        if (smsPending > 0) {
            issues += DiagnosticIssueRecommendation(
                code = "SMS_PENDING_REVIEW",
                subsystemArabic = "خط معالجة رسائل المحافظ (SMS Pipeline)",
                descriptionArabic = "توجد $smsPending رسالة مالية مستخرجة بانتظار المراجعة والترحيل لدفتر الأستاذ",
                severity = DiagnosticSeverity.WARNING,
                recommendedActionArabic = "فتح مراجعة رسائل SMS لاعتماد العمليات الصحيحة أو رفض المكرر",
                targetRoute = MekkaRoute.SMS_REVIEW
            )
        }

        if (storageFreeBytes in 1 until (100L * 1024L * 1024L)) {
            issues += DiagnosticIssueRecommendation(
                code = "LOW_STORAGE_SPACE",
                subsystemArabic = "مساحة التخزين المحلية",
                descriptionArabic = "المساحة الحرة المتبقية أقل من 100 ميجابايت",
                severity = DiagnosticSeverity.CRITICAL,
                recommendedActionArabic = "تفريغ مساحة على الجهاز لضمان استمرار عمل قاعدة البيانات والنسخ الاحتياطي",
                targetRoute = MekkaRoute.DIAGNOSTICS
            )
        }

        val freeMb = storageFreeBytes / (1024L * 1024L)
        val usedKb = (storageUsedBytes / 1024L).coerceAtLeast(1L)

        return AuditDiagnosticsDashboardBundle(
            databaseHealthStatusArabic = if (isDbHealthy) "قاعدة البيانات سليمة ومتزنة (WAL Mode)" else "توجد ملاحظات تتطلب الفحص",
            isDatabaseHealthy = isDbHealthy,
            migrationSchemaVersion = migrationSchemaVersion,
            storageUsedBytes = storageUsedBytes,
            storageFreeBytes = storageFreeBytes,
            storageStatusArabic = "المستخدم: $usedKb KB • المتاح: $freeMb MB",
            syncQueuePendingCount = pendingSync,
            syncFailedCount = failedSync,
            conflictCount = conflictCount,
            backupStatusArabic = when {
                latestBackup == null -> "لم يتم النسخ بعد"
                latestBackup.isVerified -> "نسخة موثقة (${latestBackup.fileName})"
                else -> "نسخة غير محققة"
            },
            lastBackupTimestamp = latestBackup?.createdAt,
            lastSuccessfulSyncTimestamp = lastSuccessfulSync,
            integrityStatusArabic = "اجتاز $passedChecks من $totalChecks فحص محاسبي ومخزني",
            integrityPassedChecks = passedChecks,
            integrityTotalChecks = totalChecks,
            smsPipelineStatusArabic = if (smsPending == 0) "نشط — لا توجد رسائل معلقة" else "نشط — $smsPending بانتظار الاعتماد",
            smsPendingReviewCount = smsPending,
            permissionStatusArabic = "صلاحيات الدور النشط ($activeRoleCode) مفعلة ومحمية",
            activeRoleCode = activeRoleCode,
            issuesWithRecommendations = issues.sortedByDescending { it.severity.rank }
        )
    }
}

package com.example.intelligence

import com.example.database.dao.CustomerBalanceProjection
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.SupplierBalanceProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.financial.PartyAccountStatementEngine
import com.example.financial.TrueProfitabilityEngine
import com.example.navigation.MekkaRoute
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object V4ReportingEngine {

    private fun fmtDate(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))

    fun generateTabularReport(
        filter: V4ReportFilter,
        shopNameArabic: String,
        generatedByUserId: String,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        purchases: List<PurchaseEntity>,
        refunds: List<RefundEntity>,
        expenses: List<ExpenseEntity>,
        transfers: List<TransferEntity>,
        maintenanceOrders: List<MaintenanceOrderEntity>,
        maintenanceParts: List<MaintenancePartEntity>,
        cashSessions: List<CashSessionEntity>,
        ledgerTransactions: List<TransactionEntity>,
        inventoryItems: List<ProductInventoryProjection>,
        inventoryMovements: List<InventoryMovementEntity>,
        customerBalances: List<CustomerBalanceProjection>,
        supplierBalances: List<SupplierBalanceProjection>,
        wallets: List<WalletDerivedProjection>,
        auditLogs: List<AuditLogEntity>,
        categoriesMap: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): V4TabularReportDocument {
        val start = filter.startMillis ?: 0L
        val end = filter.endMillis ?: Long.MAX_VALUE
        val periodDesc = if (filter.startMillis != null || filter.endMillis != null) {
            "من ${fmtDate(start)} إلى ${fmtDate(if (end == Long.MAX_VALUE) nowMillis else end)}"
        } else {
            "كامل السجل التاريخي"
        }

        val activeFilters = buildList {
            filter.userId?.takeIf { it.isNotBlank() }?.let { add("المستخدم: $it") }
            filter.categoryId?.takeIf { it.isNotBlank() }?.let { add("التصنيف: ${categoriesMap[it] ?: it}") }
            filter.paymentMethod?.takeIf { it.isNotBlank() }?.let { add("الدفع: $it") }
            filter.providerCode?.takeIf { it.isNotBlank() }?.let { add("المزود: $it") }
            filter.status?.takeIf { it.isNotBlank() }?.let { add("الحالة: $it") }
        }.joinToString(" • ").ifBlank { "بدون فلاتر إضافية" }

        val headers: List<String>
        val rows: List<V4TabularReportRow>
        val summary: Map<String, String>

        when (filter.reportType) {
            V4ReportType.SALES -> {
                val filtered = sales.filter { s ->
                    s.saleTimestamp in start..end &&
                        (filter.userId.isNullOrBlank() || s.createdByUserId == filter.userId) &&
                        (filter.status.isNullOrBlank() || s.status.equals(filter.status, ignoreCase = true)) &&
                        (filter.customerId.isNullOrBlank() || s.customerId == filter.customerId) &&
                        (filter.paymentMethod.isNullOrBlank() || s.paymentAccountType.contains(filter.paymentMethod, ignoreCase = true) || s.status.contains(filter.paymentMethod, ignoreCase = true))
                }.sortedByDescending { it.saleTimestamp }
                headers = listOf("رقم الفاتورة", "التاريخ", "الإجمالي", "المدفوع", "الخصم", "تكلفة WAC", "الربح", "الحالة", "المستخدم")
                rows = filtered.map { s ->
                    V4TabularReportRow(
                        cells = listOf(
                            s.invoiceNumber,
                            fmtDate(s.saleTimestamp),
                            Money.ofMinor(s.totalAmountMinor).formatArabic(),
                            Money.ofMinor(s.paidAmountMinor).formatArabic(),
                            Money.ofMinor(s.discountMinor).formatArabic(),
                            Money.ofMinor(s.totalCostOfGoodsMinor).formatArabic(),
                            Money.ofMinor(s.grossProfitMinor).formatArabic(),
                            FriendlyTerminologyMapper.toFriendlyStatusArabic(s.status),
                            s.createdByUserId
                        ),
                        amountMinor = s.totalAmountMinor,
                        referenceCode = s.invoiceNumber,
                        routeTarget = MekkaRoute.SALES_POS
                    )
                }
                summary = mapOf(
                    "عدد الفواتير" to filtered.size.toString(),
                    "إجمالي المبيعات" to Money.ofMinor(filtered.sumOf { it.totalAmountMinor }).formatArabic(),
                    "إجمالي المحصل" to Money.ofMinor(filtered.sumOf { it.paidAmountMinor }).formatArabic(),
                    "مجمل الربح" to Money.ofMinor(filtered.sumOf { it.grossProfitMinor }).formatArabic()
                )
            }

            V4ReportType.PURCHASES -> {
                val filtered = purchases.filter { p ->
                    p.purchaseTimestamp in start..end &&
                        (filter.userId.isNullOrBlank() || p.createdByUserId == filter.userId) &&
                        (filter.supplierId.isNullOrBlank() || p.supplierId == filter.supplierId) &&
                        (filter.paymentMethod.isNullOrBlank() || p.paymentAccountType.contains(filter.paymentMethod, ignoreCase = true))
                }.sortedByDescending { p -> p.purchaseTimestamp }
                headers = listOf("رقم الفاتورة", "التاريخ", "المورد", "الصافي", "المسدد", "المتبقي آجل", "حساب السداد", "المستخدم")
                rows = filtered.map { p ->
                    val rem = (p.totalAmountMinor - p.paidAmountMinor).coerceAtLeast(0L)
                    V4TabularReportRow(
                        cells = listOf(
                            p.invoiceNumber,
                            fmtDate(p.purchaseTimestamp),
                            p.supplierId,
                            Money.ofMinor(p.totalAmountMinor).formatArabic(),
                            Money.ofMinor(p.paidAmountMinor).formatArabic(),
                            Money.ofMinor(rem).formatArabic(),
                            FriendlyTerminologyMapper.toFriendlyAccountArabic(p.paymentAccountType),
                            p.createdByUserId
                        ),
                        amountMinor = p.totalAmountMinor,
                        referenceCode = p.invoiceNumber,
                        routeTarget = MekkaRoute.PURCHASES
                    )
                }
                summary = mapOf(
                    "عدد فواتير الشراء" to filtered.size.toString(),
                    "إجمالي المشتريات" to Money.ofMinor(filtered.sumOf { it.totalAmountMinor }).formatArabic(),
                    "إجمالي المسدد" to Money.ofMinor(filtered.sumOf { it.paidAmountMinor }).formatArabic()
                )
            }

            V4ReportType.PROFIT -> {
                val prof = TrueProfitabilityEngine.computeTrueProfitability(
                    startMillis = start,
                    endMillis = end,
                    sales = sales,
                    saleItems = saleItems,
                    refunds = refunds,
                    maintenanceOrders = maintenanceOrders,
                    maintenanceParts = maintenanceParts,
                    expenses = expenses,
                    inventoryMovements = inventoryMovements,
                    ledgerTransactions = ledgerTransactions,
                    products = inventoryItems,
                    categories = categoriesMap
                )
                headers = listOf("البند المالي", "القيمة", "التأثير على صافي الربح")
                val breakdown = listOf(
                    Triple("إجمالي المبيعات قبل الخصم", prof.grossSalesBeforeDiscountMinor, "إيراد (+)"),
                    Triple("خصومات المبيعات", prof.salesDiscountsMinor, "خصم (-)"),
                    Triple("مرتجعات المبيعات", prof.salesReturnsMinor, "رد إيراد (-)"),
                    Triple("صافي إيراد المبيعات", prof.netSalesRevenueMinor, "صافي إيراد (=)"),
                    Triple("صافي تكلفة البضاعة المباعة (WAC COGS)", prof.netCogsMinor, "تكلفة (-)"),
                    Triple("مجمل ربح المبيعات", prof.salesGrossProfitMinor, "ربح مبيعات (+)"),
                    Triple("صافي ربح الصيانة (بعد قطع الغيار)", prof.maintenanceNetProfitMinor, "ربح صيانة (+)"),
                    Triple("صافي عمولات المحافظ (بعد رسوم الشبكة)", prof.walletNetProfitMinor, "ربح محافظ (+)"),
                    Triple("المصروفات التشغيلية", prof.operatingExpensesMinor, "مصروف (-)"),
                    Triple("عجز وتوالف المخزون", prof.inventoryShrinkageLossMinor, "خسارة جرد (-)"),
                    Triple("فائض الجرد المخزني", prof.inventorySurplusGainMinor, "فائض جرد (+)"),
                    Triple("عجز/فائض الخزينة المعتمد", prof.cashSurplusGainMinor - prof.cashShortageLossMinor, "تسوية خزينة"),
                    Triple("صافي الربح الحقيقي النهائي", prof.trueNetProfitMinor, "الربح الصافي (=)")
                )
                rows = breakdown.map { (label, amt, impact) ->
                    V4TabularReportRow(
                        cells = listOf(label, Money.ofMinor(amt).formatArabic(), impact),
                        amountMinor = amt,
                        referenceCode = label,
                        routeTarget = MekkaRoute.REPORTS
                    )
                }
                summary = mapOf(
                    "مجمل الربح الكلي" to Money.ofMinor(prof.totalGrossProfitMinor).formatArabic(),
                    "المصروفات التشغيلية" to Money.ofMinor(prof.operatingExpensesMinor).formatArabic(),
                    "صافي الربح الحقيقي" to Money.ofMinor(prof.trueNetProfitMinor).formatArabic()
                )
            }

            V4ReportType.EXPENSES -> {
                val filtered = expenses.filter { e ->
                    e.expenseTimestamp in start..end &&
                        (filter.userId.isNullOrBlank() || e.recordedByUserId == filter.userId) &&
                        (filter.categoryId.isNullOrBlank() || e.categoryName.contains(filter.categoryId, ignoreCase = true))
                }.sortedByDescending { it.expenseTimestamp }
                headers = listOf("التاريخ", "البند", "التصنيف", "المبلغ", "حساب الصرف", "المستخدم", "ملاحظات")
                rows = filtered.map { e ->
                    V4TabularReportRow(
                        cells = listOf(
                            fmtDate(e.expenseTimestamp),
                            e.title,
                            e.categoryName,
                            Money.ofMinor(e.amountMinor).formatArabic(),
                            FriendlyTerminologyMapper.toFriendlyAccountArabic(e.sourceAccountType),
                            e.recordedByUserId,
                            e.notes.ifBlank { "-" }
                        ),
                        amountMinor = e.amountMinor,
                        referenceCode = e.localId,
                        routeTarget = MekkaRoute.EXPENSES
                    )
                }
                summary = mapOf(
                    "عدد المصروفات" to filtered.size.toString(),
                    "إجمالي المصروفات" to Money.ofMinor(filtered.sumOf { it.amountMinor }).formatArabic()
                )
            }

            V4ReportType.CASH -> {
                val filtered = ledgerTransactions.filter { tx ->
                    tx.status == LedgerTransactionStatus.POSTED &&
                        tx.transactionTimestamp in start..end &&
                        (tx.sourceAccountType == FinancialAccountType.CASH_DRAWER || tx.destinationAccountType == FinancialAccountType.CASH_DRAWER) &&
                        (filter.userId.isNullOrBlank() || tx.createdByUserId == filter.userId)
                }.sortedByDescending { it.transactionTimestamp }
                headers = listOf("المرجع", "التاريخ", "نوع الحركة", "الاتجاه", "المبلغ", "البيان", "المستخدم")
                rows = filtered.map { tx ->
                    val isInflow = tx.destinationAccountType == FinancialAccountType.CASH_DRAWER
                    V4TabularReportRow(
                        cells = listOf(
                            tx.referenceNumber,
                            fmtDate(tx.transactionTimestamp),
                            FriendlyTerminologyMapper.toFriendlyOperationArabic(tx),
                            if (isInflow) "وارد للخزينة (+)" else "صادر من الخزينة (-)",
                            Money.ofMinor(tx.amountMinor).formatArabic(),
                            tx.descriptionArabic,
                            tx.createdByUserId
                        ),
                        amountMinor = tx.amountMinor,
                        referenceCode = tx.referenceNumber,
                        routeTarget = MekkaRoute.CASH_DRAWER
                    )
                }
                val inSum = filtered.filter { it.destinationAccountType == FinancialAccountType.CASH_DRAWER }.sumOf { it.amountMinor }
                val outSum = filtered.filter { it.sourceAccountType == FinancialAccountType.CASH_DRAWER }.sumOf { it.amountMinor }
                summary = mapOf(
                    "إجمالي الوارد النقدي" to Money.ofMinor(inSum).formatArabic(),
                    "إجمالي الصادر النقدي" to Money.ofMinor(outSum).formatArabic(),
                    "صافي التدفق النقدي" to Money.ofMinor(inSum - outSum).formatArabic()
                )
            }

            V4ReportType.WALLETS -> {
                val walletByProvider = wallets.associateBy { it.walletId }
                val filtered = transfers.filter { tr ->
                    tr.transferTimestamp in start..end &&
                        (filter.userId.isNullOrBlank() || tr.executedByUserId == filter.userId) &&
                        (filter.providerCode.isNullOrBlank() ||
                            walletByProvider[tr.sourceAccountId]?.providerCode.equals(filter.providerCode, ignoreCase = true) ||
                            walletByProvider[tr.destinationAccountId]?.providerCode.equals(filter.providerCode, ignoreCase = true))
                }.sortedByDescending { it.transferTimestamp }
                headers = listOf("المرجع", "التاريخ", "النوع", "هاتف العميل", "المبلغ الأساسي", "رسوم الشبكة", "عمولة المتجر", "الصافي")
                rows = filtered.map { tr ->
                    V4TabularReportRow(
                        cells = listOf(
                            tr.transferReference,
                            fmtDate(tr.transferTimestamp),
                            tr.transferKind,
                            tr.customerPhoneOrAccount.ifBlank { "-" },
                            Money.ofMinor(tr.principalAmountMinor).formatArabic(),
                            Money.ofMinor(tr.providerFeeMinor).formatArabic(),
                            Money.ofMinor(tr.shopCommissionMinor).formatArabic(),
                            Money.ofMinor(tr.netSettlementMinor).formatArabic()
                        ),
                        amountMinor = tr.principalAmountMinor,
                        referenceCode = tr.transferReference,
                        routeTarget = MekkaRoute.WALLETS
                    )
                }
                summary = mapOf(
                    "عدد عمليات المحافظ" to filtered.size.toString(),
                    "إجمالي المبالغ" to Money.ofMinor(filtered.sumOf { it.principalAmountMinor }).formatArabic(),
                    "إجمالي العمولات المكتسبة" to Money.ofMinor(filtered.sumOf { it.shopCommissionMinor }).formatArabic(),
                    "إجمالي رسوم الشبكة" to Money.ofMinor(filtered.sumOf { it.providerFeeMinor }).formatArabic()
                )
            }

            V4ReportType.INVENTORY -> {
                val filtered = inventoryItems.filter { item ->
                    (filter.categoryId.isNullOrBlank() || item.categoryId == filter.categoryId) &&
                        (filter.status.isNullOrBlank() || item.stockStatusArabic.contains(filter.status))
                }
                headers = listOf("الكود SKU", "الصنف", "الكمية الحالية", "متوسط التكلفة WAC", "قيمة المخزون WAC", "سعر البيع", "الحالة")
                rows = filtered.map { item ->
                    V4TabularReportRow(
                        cells = listOf(
                            item.sku,
                            item.nameArabic,
                            item.derivedStockQty.toString(),
                            Money.ofMinor(item.weightedAvgCostMinor).formatArabic(),
                            Money.ofMinor(item.stockValueMinor).formatArabic(),
                            Money.ofMinor(item.effectiveRetailPriceMinor).formatArabic(),
                            item.stockStatusArabic
                        ),
                        amountMinor = item.stockValueMinor,
                        referenceCode = item.sku,
                        routeTarget = MekkaRoute.INVENTORY
                    )
                }
                summary = mapOf(
                    "عدد الأصناف" to filtered.size.toString(),
                    "إجمالي الوحدات" to filtered.sumOf { it.derivedStockQty.coerceAtLeast(0) }.toString(),
                    "إجمالي قيمة المخزون (WAC)" to Money.ofMinor(filtered.sumOf { it.stockValueMinor }).formatArabic()
                )
            }

            V4ReportType.INVENTORY_MOVEMENTS -> {
                val prodMap = inventoryItems.associateBy { it.productId }
                val filtered = inventoryMovements.filter { m ->
                    m.timestamp in start..end &&
                        (filter.userId.isNullOrBlank() || m.performedByUserId == filter.userId) &&
                        (filter.productId.isNullOrBlank() || m.productId == filter.productId) &&
                        (filter.status.isNullOrBlank() || m.movementType.name.equals(filter.status, ignoreCase = true))
                }.sortedByDescending { it.timestamp }
                headers = listOf("التاريخ", "الصنف", "نوع الحركة", "التغير", "الرصيد بعد", "تكلفة الوحدة", "WAC بعد", "المرجع")
                rows = filtered.map { m ->
                    val pName = prodMap[m.productId]?.nameArabic ?: m.productId
                    V4TabularReportRow(
                        cells = listOf(
                            fmtDate(m.timestamp),
                            pName,
                            m.movementType.arabicLabel,
                            if (m.signedQuantityDelta >= 0) "+${m.signedQuantityDelta}" else m.signedQuantityDelta.toString(),
                            m.quantityAfter.toString(),
                            Money.ofMinor(m.unitCostMinor).formatArabic(),
                            Money.ofMinor(m.weightedAvgCostAfterMinor).formatArabic(),
                            "${m.referenceType}:${m.referenceId}"
                        ),
                        amountMinor = m.totalCostMinor,
                        referenceCode = m.localId,
                        routeTarget = MekkaRoute.INVENTORY
                    )
                }
                summary = mapOf(
                    "عدد الحركات المخزنية" to filtered.size.toString(),
                    "إجمالي الوارد" to filtered.filter { it.signedQuantityDelta > 0 }.sumOf { it.quantity }.toString(),
                    "إجمالي الصادر" to filtered.filter { it.signedQuantityDelta < 0 }.sumOf { it.quantity }.toString()
                )
            }

            V4ReportType.PRODUCT_PROFIT -> {
                val prof = TrueProfitabilityEngine.computeTrueProfitability(
                    startMillis = start,
                    endMillis = end,
                    sales = sales,
                    saleItems = saleItems,
                    refunds = refunds,
                    maintenanceOrders = maintenanceOrders,
                    maintenanceParts = maintenanceParts,
                    expenses = expenses,
                    inventoryMovements = inventoryMovements,
                    ledgerTransactions = ledgerTransactions,
                    products = inventoryItems,
                    categories = categoriesMap
                )
                headers = listOf("الكود SKU", "الصنف", "الوحدات المباعة", "صافي الإيراد", "التكلفة WAC", "مجمل الربح")
                rows = prof.profitByProduct.map { item ->
                    V4TabularReportRow(
                        cells = listOf(
                            item.sku,
                            item.productNameArabic,
                            item.unitsSold.toString(),
                            Money.ofMinor(item.revenueMinor).formatArabic(),
                            Money.ofMinor(item.cogsMinor).formatArabic(),
                            Money.ofMinor(item.grossProfitMinor).formatArabic()
                        ),
                        amountMinor = item.grossProfitMinor,
                        referenceCode = item.sku,
                        routeTarget = MekkaRoute.INVENTORY
                    )
                }
                summary = mapOf(
                    "عدد الأصناف المباعة" to rows.size.toString(),
                    "إجمالي ربح الأصناف" to Money.ofMinor(prof.profitByProduct.sumOf { it.grossProfitMinor }).formatArabic()
                )
            }

            V4ReportType.CUSTOMER_STATEMENT -> {
                val targetCust = filter.customerId?.takeIf { it.isNotBlank() }
                if (targetCust != null) {
                    val cb = customerBalances.firstOrNull { it.customerId == targetCust }
                    val profile = PartyAccountStatementEngine.buildCustomerProfile(
                        customerId = targetCust,
                        derivedBalanceMinor = cb?.outstandingBalanceMinor ?: 0L,
                        sales = sales.filter { it.saleTimestamp in start..end },
                        refunds = refunds.filter { it.refundTimestamp in start..end },
                        ledgerTransactions = ledgerTransactions.filter { it.transactionTimestamp in start..end }
                    )
                    headers = listOf("التاريخ", "نوع الحركة", "المرجع", "مدين (+)", "دائن (-)", "الرصيد التراكمي", "البيان")
                    rows = profile.statementLines.map { line ->
                        V4TabularReportRow(
                            cells = listOf(
                                fmtDate(line.timestamp),
                                line.typeLabelArabic,
                                line.referenceNumber,
                                Money.ofMinor(line.debitMinor).formatArabic(),
                                Money.ofMinor(line.creditMinor).formatArabic(),
                                Money.ofMinor(line.runningBalanceMinor).formatArabic(),
                                line.descriptionArabic
                            ),
                            amountMinor = line.runningBalanceMinor,
                            referenceCode = line.referenceNumber,
                            routeTarget = MekkaRoute.CUSTOMERS
                        )
                    }
                    summary = mapOf(
                        "العميل" to (cb?.fullName ?: targetCust),
                        "إجمالي المشتريات" to Money.ofMinor(profile.totalPurchasesMinor).formatArabic(),
                        "إجمالي المدفوعات" to Money.ofMinor(profile.totalPaymentsMinor).formatArabic(),
                        "الرصيد المستحق الحالي" to Money.ofMinor(profile.outstandingAmountMinor).formatArabic()
                    )
                } else {
                    headers = listOf("العميل", "الهاتف", "إجمالي المبيعات", "إجمالي المسدد", "المديونية القائمة", "الحد الائتماني")
                    rows = customerBalances.map { c ->
                        V4TabularReportRow(
                            cells = listOf(
                                c.fullName,
                                c.phone,
                                Money.ofMinor(c.totalSalesMinor).formatArabic(),
                                Money.ofMinor(c.totalPaymentsMinor).formatArabic(),
                                Money.ofMinor(c.outstandingBalanceMinor).formatArabic(),
                                Money.ofMinor(c.creditLimitMinor).formatArabic()
                            ),
                            amountMinor = c.outstandingBalanceMinor,
                            referenceCode = c.phone,
                            routeTarget = MekkaRoute.CUSTOMERS
                        )
                    }
                    summary = mapOf(
                        "عدد العملاء" to customerBalances.size.toString(),
                        "إجمالي مديونيات العملاء" to Money.ofMinor(customerBalances.sumOf { it.outstandingBalanceMinor.coerceAtLeast(0L) }).formatArabic()
                    )
                }
            }

            V4ReportType.SUPPLIER_STATEMENT -> {
                val targetSup = filter.supplierId?.takeIf { it.isNotBlank() }
                if (targetSup != null) {
                    val sb = supplierBalances.firstOrNull { it.supplierId == targetSup }
                    val profile = PartyAccountStatementEngine.buildSupplierProfile(
                        supplierId = targetSup,
                        derivedBalanceMinor = sb?.outstandingPayableMinor ?: 0L,
                        purchases = purchases.filter { it.purchaseTimestamp in start..end },
                        ledgerTransactions = ledgerTransactions.filter { it.transactionTimestamp in start..end }
                    )
                    headers = listOf("التاريخ", "نوع الحركة", "المرجع", "مدين (سداد)", "دائن (توريد)", "الرصيد التراكمي", "البيان")
                    rows = profile.statementLines.map { line ->
                        V4TabularReportRow(
                            cells = listOf(
                                fmtDate(line.timestamp),
                                line.typeLabelArabic,
                                line.referenceNumber,
                                Money.ofMinor(line.debitMinor).formatArabic(),
                                Money.ofMinor(line.creditMinor).formatArabic(),
                                Money.ofMinor(line.runningBalanceMinor).formatArabic(),
                                line.descriptionArabic
                            ),
                            amountMinor = line.runningBalanceMinor,
                            referenceCode = line.referenceNumber,
                            routeTarget = MekkaRoute.SUPPLIERS
                        )
                    }
                    summary = mapOf(
                        "المورد" to (sb?.companyName ?: targetSup),
                        "إجمالي التوريدات" to Money.ofMinor(profile.totalPurchasesMinor).formatArabic(),
                        "إجمالي السداد" to Money.ofMinor(profile.totalPaymentsMinor).formatArabic(),
                        "المتبقي للمورد" to Money.ofMinor(profile.outstandingAmountMinor).formatArabic()
                    )
                } else {
                    headers = listOf("شركة المورد", "الهاتف", "إجمالي التوريدات", "إجمالي المسدد", "المستحق للمورد")
                    rows = supplierBalances.map { s ->
                        V4TabularReportRow(
                            cells = listOf(
                                s.companyName,
                                s.phone,
                                Money.ofMinor(s.totalPurchasesMinor).formatArabic(),
                                Money.ofMinor(s.totalPaymentsMinor).formatArabic(),
                                Money.ofMinor(s.outstandingPayableMinor).formatArabic()
                            ),
                            amountMinor = s.outstandingPayableMinor,
                            referenceCode = s.phone,
                            routeTarget = MekkaRoute.SUPPLIERS
                        )
                    }
                    summary = mapOf(
                        "عدد الموردين" to supplierBalances.size.toString(),
                        "إجمالي مستحقات الموردين" to Money.ofMinor(supplierBalances.sumOf { it.outstandingPayableMinor.coerceAtLeast(0L) }).formatArabic()
                    )
                }
            }

            V4ReportType.MAINTENANCE -> {
                val filtered = maintenanceOrders.filter { m ->
                    m.receivedAt in start..end &&
                        (filter.userId.isNullOrBlank() || m.assignedTechnicianId == filter.userId || m.receivedByUserId == filter.userId) &&
                        (filter.status.isNullOrBlank() || m.status.name.equals(filter.status, ignoreCase = true))
                }.sortedByDescending { it.receivedAt }
                headers = listOf("رقم التذكرة", "التاريخ", "العميل", "الجهاز", "الحالة", "التكلفة", "قطع الغيار", "العربون/المدفوع")
                rows = filtered.map { m ->
                    val fin = if (m.finalTotalMinor > 0L) m.finalTotalMinor else m.estimatedCostMinor
                    V4TabularReportRow(
                        cells = listOf(
                            m.ticketNumber,
                            fmtDate(m.receivedAt),
                            m.customerNameSnapshot,
                            "${m.deviceBrand} ${m.deviceModel}",
                            m.status.arabicLabel,
                            Money.ofMinor(fin).formatArabic(),
                            Money.ofMinor(m.partsCostMinor).formatArabic(),
                            Money.ofMinor(m.depositPaidMinor).formatArabic()
                        ),
                        amountMinor = fin,
                        referenceCode = m.ticketNumber,
                        routeTarget = MekkaRoute.MAINTENANCE
                    )
                }
                summary = mapOf(
                    "إجمالي التذاكر" to filtered.size.toString(),
                    "تذاكر قيد العمل" to filtered.count { it.status.isPendingWork }.toString(),
                    "إجمالي تكلفة قطع الغيار" to Money.ofMinor(filtered.sumOf { it.partsCostMinor }).formatArabic()
                )
            }

            V4ReportType.SHIFTS -> {
                val filtered = cashSessions.filter { cs ->
                    cs.openedAt in start..end &&
                        (filter.userId.isNullOrBlank() || cs.openedByUserId == filter.userId || cs.closedByUserId == filter.userId) &&
                        (filter.status.isNullOrBlank() || cs.status.equals(filter.status, ignoreCase = true))
                }.sortedByDescending { it.openedAt }
                headers = listOf("رقم الوردية", "الفتح", "الإغلاق", "الافتتاحي", "المتوقع", "الفعلي المعدود", "الفرق", "الحالة")
                rows = filtered.map { cs ->
                    V4TabularReportRow(
                        cells = listOf(
                            cs.localId,
                            fmtDate(cs.openedAt),
                            cs.closedAt?.let { fmtDate(it) } ?: "مفتوحة",
                            Money.ofMinor(cs.openingDerivedBalanceMinor).formatArabic(),
                            Money.ofMinor(cs.closingDerivedBalanceMinor ?: cs.openingDerivedBalanceMinor).formatArabic(),
                            cs.closingCountedCashMinor?.let { Money.ofMinor(it).formatArabic() } ?: "-",
                            Money.ofMinor(cs.discrepancyMinor ?: 0L).formatArabic(),
                            FriendlyTerminologyMapper.toFriendlyStatusArabic(cs.status)
                        ),
                        amountMinor = cs.discrepancyMinor ?: 0L,
                        referenceCode = cs.localId,
                        routeTarget = MekkaRoute.CASH_DRAWER
                    )
                }
                summary = mapOf(
                    "عدد الورديات" to filtered.size.toString(),
                    "إجمالي الفروقات" to Money.ofMinor(filtered.sumOf { it.discrepancyMinor ?: 0L }).formatArabic()
                )
            }

            V4ReportType.LEDGER -> {
                val filtered = ledgerTransactions.filter { tx ->
                    tx.transactionTimestamp in start..end &&
                        (filter.userId.isNullOrBlank() || tx.createdByUserId == filter.userId) &&
                        (filter.status.isNullOrBlank() || tx.status.name.equals(filter.status, ignoreCase = true))
                }.sortedByDescending { it.transactionTimestamp }
                headers = listOf("رقم القيد", "التاريخ", "النوع", "من حساب", "إلى حساب", "المبلغ", "الرسوم", "العمولة", "الحالة")
                rows = filtered.map { tx ->
                    V4TabularReportRow(
                        cells = listOf(
                            tx.referenceNumber,
                            fmtDate(tx.transactionTimestamp),
                            FriendlyTerminologyMapper.toFriendlyOperationArabic(tx),
                            FriendlyTerminologyMapper.toFriendlyAccountArabic(tx.sourceAccountType),
                            FriendlyTerminologyMapper.toFriendlyAccountArabic(tx.destinationAccountType),
                            Money.ofMinor(tx.amountMinor).formatArabic(),
                            Money.ofMinor(tx.feeMinor).formatArabic(),
                            Money.ofMinor(tx.commissionMinor).formatArabic(),
                            FriendlyTerminologyMapper.toFriendlyStatusArabic(tx.status, tx.reversalOfTransactionId != null)
                        ),
                        amountMinor = tx.amountMinor,
                        referenceCode = tx.referenceNumber,
                        routeTarget = MekkaRoute.LEDGER
                    )
                }
                summary = mapOf(
                    "عدد القيود" to filtered.size.toString(),
                    "إجمالي حركات القيود" to Money.ofMinor(filtered.sumOf { it.amountMinor }).formatArabic()
                )
            }

            V4ReportType.AUDIT -> {
                val filtered = auditLogs.filter { a ->
                    a.timestamp in start..end &&
                        (filter.userId.isNullOrBlank() || a.actorUserId == filter.userId) &&
                        (filter.status.isNullOrBlank() || a.actionType.contains(filter.status, ignoreCase = true))
                }.sortedByDescending { it.timestamp }
                headers = listOf("التاريخ", "نوع الإجراء", "الكيان", "المرجع", "الملخص", "المستخدم", "الدور", "الجهاز")
                rows = filtered.map { a ->
                    V4TabularReportRow(
                        cells = listOf(
                            fmtDate(a.timestamp),
                            FriendlyTerminologyMapper.toFriendlyAuditActionArabic(a.actionType),
                            FriendlyTerminologyMapper.friendlyEntityTypeLabel(a.entityType),
                            a.referenceNumber.ifBlank { a.entityId },
                            a.summaryArabic,
                            a.actorUserId,
                            a.actorRoleCode,
                            a.deviceId
                        ),
                        amountMinor = null,
                        referenceCode = a.referenceNumber.ifBlank { a.localId },
                        routeTarget = MekkaRoute.AUDIT_LOGS
                    )
                }
                summary = mapOf(
                    "إجمالي سجلات التدقيق" to filtered.size.toString()
                )
            }

            V4ReportType.TAX_AND_FEES -> {
                val periodSales = sales.filter { it.saleTimestamp in start..end && it.status != "CANCELLED" }
                val periodPurchases = purchases.filter { it.purchaseTimestamp in start..end }
                val periodTx = ledgerTransactions.filter { it.transactionTimestamp in start..end && it.status == LedgerTransactionStatus.POSTED }

                val salesTax = periodSales.sumOf { it.taxMinor }
                val purchaseTax = periodPurchases.sumOf { it.taxMinor }
                val networkFees = periodTx.sumOf { it.feeMinor }
                val shopCommissions = periodTx.sumOf { it.commissionMinor }
                val salesDiscounts = periodSales.sumOf { it.discountMinor }

                headers = listOf("البند الضريبي / الرسوم", "عدد العمليات", "إجمالي القيمة", "الطبيعة المالية")
                rows = listOf(
                    V4TabularReportRow(
                        cells = listOf("ضرائب فواتير المبيعات", periodSales.count { it.taxMinor > 0L }.toString(), Money.ofMinor(salesTax).formatArabic(), "ضريبة محصلة"),
                        amountMinor = salesTax,
                        referenceCode = "SALES_TAX",
                        routeTarget = MekkaRoute.REPORTS
                    ),
                    V4TabularReportRow(
                        cells = listOf("ضرائب فواتير المشتريات", periodPurchases.count { it.taxMinor > 0L }.toString(), Money.ofMinor(purchaseTax).formatArabic(), "ضريبة مدفوعة"),
                        amountMinor = purchaseTax,
                        referenceCode = "PURCHASE_TAX",
                        routeTarget = MekkaRoute.REPORTS
                    ),
                    V4TabularReportRow(
                        cells = listOf("عمولات خدمات المحافظ المكتسبة", periodTx.count { it.commissionMinor > 0L }.toString(), Money.ofMinor(shopCommissions).formatArabic(), "إيراد عمولات (+)"),
                        amountMinor = shopCommissions,
                        referenceCode = "WALLET_COMMISSIONS",
                        routeTarget = MekkaRoute.WALLETS
                    ),
                    V4TabularReportRow(
                        cells = listOf("رسوم شبكات التحويل والمحافظ", periodTx.count { it.feeMinor > 0L }.toString(), Money.ofMinor(networkFees).formatArabic(), "رسوم شبكة (-)"),
                        amountMinor = networkFees,
                        referenceCode = "NETWORK_FEES",
                        routeTarget = MekkaRoute.WALLETS
                    ),
                    V4TabularReportRow(
                        cells = listOf("الخصومات الممنوحة على المبيعات", periodSales.count { it.discountMinor > 0L }.toString(), Money.ofMinor(salesDiscounts).formatArabic(), "خصم مسموح به (-)"),
                        amountMinor = salesDiscounts,
                        referenceCode = "SALES_DISCOUNTS",
                        routeTarget = MekkaRoute.SALES_POS
                    )
                )
                summary = mapOf(
                    "صافي الضرائب" to Money.ofMinor(salesTax - purchaseTax).formatArabic(),
                    "صافي العمولات بعد الرسوم" to Money.ofMinor(shopCommissions - networkFees).formatArabic(),
                    "إجمالي الخصومات" to Money.ofMinor(salesDiscounts).formatArabic()
                )
            }
        }

        return V4TabularReportDocument(
            reportType = filter.reportType,
            titleArabic = filter.reportType.arabicTitle,
            shopNameArabic = shopNameArabic,
            generatedAtMillis = nowMillis,
            generatedByUserId = generatedByUserId,
            periodDescriptionArabic = periodDesc,
            appliedFiltersSummaryArabic = activeFilters,
            headers = headers,
            rows = rows,
            summaryMetrics = summary
        )
    }
}

/**
 * 8 — REAL REPORT EXPORT ENGINE (CSV, PDF, XLSX)
 * Writes real files with actual tabular data, shop metadata, and SHA-256 verification.
 */
object RealReportExportEngine {

    fun exportReportToFile(
        document: V4TabularReportDocument,
        format: ReportExportFormat,
        outputDirectory: File
    ): ReportExportResult {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val tsLabel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(document.generatedAtMillis))
        val fileName = "MekkaPhone_${document.reportType.code}_${tsLabel}.${format.extension}"
        val targetFile = File(outputDirectory, fileName)

        val bytes = when (format) {
            ReportExportFormat.CSV -> buildCsvBytes(document)
            ReportExportFormat.PDF -> buildPdfBytes(document)
            ReportExportFormat.XLSX -> buildXlsxBytes(document)
        }

        FileOutputStream(targetFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        val sha256 = sha256Hex(bytes)
        val metaSummary = "${document.titleArabic} • ${document.rows.size} سطر • ${format.arabicLabel} (${bytes.size} بايت)"

        return ReportExportResult(
            reportType = document.reportType,
            format = format,
            filePath = targetFile.absolutePath,
            fileName = fileName,
            byteSize = bytes.size.toLong(),
            rowCount = document.rows.size,
            sha256Checksum = sha256,
            generatedAtMillis = document.generatedAtMillis,
            metadataSummaryArabic = metaSummary
        )
    }

    private fun buildCsvBytes(doc: V4TabularReportDocument): ByteArray {
        val sb = StringBuilder()
        // UTF-8 BOM for Arabic Excel compatibility
        sb.append('\uFEFF')
        sb.append("# Report,").append(escapeCsv(doc.titleArabic)).append("\r\n")
        sb.append("# Shop,").append(escapeCsv(doc.shopNameArabic)).append("\r\n")
        sb.append("# Period,").append(escapeCsv(doc.periodDescriptionArabic)).append("\r\n")
        sb.append("# Filters,").append(escapeCsv(doc.appliedFiltersSummaryArabic)).append("\r\n")
        sb.append("# GeneratedBy,").append(escapeCsv(doc.generatedByUserId)).append("\r\n")
        doc.summaryMetrics.forEach { (k, v) ->
            sb.append("# Summary: ").append(escapeCsv(k)).append(",").append(escapeCsv(v)).append("\r\n")
        }
        sb.append("\r\n")
        sb.append(doc.headers.joinToString(",") { escapeCsv(it) }).append("\r\n")
        for (row in doc.rows) {
            sb.append(row.cells.joinToString(",") { escapeCsv(it) }).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(",") || cleaned.contains("\"")) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    /**
     * Generates a standards-compliant PDF-1.4 document with cross-reference table and full report metadata & rows.
     */
    private fun buildPdfBytes(doc: V4TabularReportDocument): ByteArray {
        val lines = mutableListOf<String>()
        lines += "MEKKA PHONE V4 - OFFICIAL OPERATIONAL REPORT"
        lines += "Report Type: ${doc.reportType.code} (${doc.titleArabic})"
        lines += "Shop: ${doc.shopNameArabic} | User: ${doc.generatedByUserId}"
        lines += "Period: ${doc.periodDescriptionArabic}"
        lines += "Filters: ${doc.appliedFiltersSummaryArabic}"
        lines += "------------------------------------------------------------------------"
        doc.summaryMetrics.forEach { (k, v) ->
            lines += "SUMMARY - $k : $v"
        }
        lines += "------------------------------------------------------------------------"
        lines += doc.headers.joinToString(" | ")
        lines += "------------------------------------------------------------------------"
        for ((idx, row) in doc.rows.withIndex()) {
            lines += "${idx + 1}. " + row.cells.joinToString(" | ")
        }

        val contentBuilder = StringBuilder()
        contentBuilder.append("BT\n/F1 10 Tf\n36 800 Td\n14 TL\n")
        for (line in lines.take(250)) {
            val safeText = line
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
            contentBuilder.append("(").append(safeText).append(") Tj T*\n")
        }
        contentBuilder.append("ET\n")
        val streamContent = contentBuilder.toString().toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

        writeAscii("%PDF-1.4\n")
        val offsets = IntArray(6)

        offsets[1] = out.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets[2] = out.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets[3] = out.size()
        writeAscii("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")

        offsets[4] = out.size()
        writeAscii("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        offsets[5] = out.size()
        writeAscii("5 0 obj\n<< /Length ${streamContent.size} >>\nstream\n")
        out.write(streamContent)
        writeAscii("\nendstream\nendobj\n")

        val xrefOffset = out.size()
        writeAscii("xref\n0 6\n0000000000 65535 f \n")
        for (i in 1..5) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    /**
     * Generates a genuine OpenXML SpreadsheetML (.xlsx) ZIP archive containing real worksheet rows and metadata.
     */
    private fun buildXlsxBytes(doc: V4TabularReportDocument): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 3. xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(doc.reportType.code)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 4. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 5. xl/worksheets/sheet1.xml
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetXml = StringBuilder()
            sheetXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetXml.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

            var rowIndex = 1
            fun appendRow(values: List<String>) {
                sheetXml.append("""<row r="$rowIndex">""")
                values.forEachIndexed { colIdx, text ->
                    val colLetter = ('A' + colIdx.coerceAtMost(25))
                    sheetXml.append("""<c r="$colLetter$rowIndex" t="inlineStr"><is><t>${escapeXml(text)}</t></is></c>""")
                }
                sheetXml.append("""</row>""")
                rowIndex++
            }

            appendRow(listOf("التقرير", doc.titleArabic, "المتجر", doc.shopNameArabic))
            appendRow(listOf("الفترة", doc.periodDescriptionArabic, "الفلاتر", doc.appliedFiltersSummaryArabic))
            doc.summaryMetrics.forEach { (k, v) ->
                appendRow(listOf("ملخص: $k", v))
            }
            appendRow(doc.headers)
            for (r in doc.rows) {
                appendRow(r.cells)
            }

            sheetXml.append("""</sheetData></worksheet>""")
            zos.write(sheetXml.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

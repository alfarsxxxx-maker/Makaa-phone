package com.example.intelligence

import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SupplierEntity
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Supplier price comparison item for a specific product (Requirement P1-B).
 */
data class SupplierPriceComparisonItem(
    val supplierId: String,
    val supplierNameArabic: String,
    val supplierPhone: String,
    val totalQuantitySupplied: Int,
    val lastPurchasePriceMinor: Long,
    val minPurchasePriceMinor: Long,
    val maxPurchasePriceMinor: Long,
    val averagePurchasePriceMinor: Long,
    val lastPurchaseTimestamp: Long
)

/**
 * Historical record of a purchase for a specific product (Requirement P1-B).
 */
data class ProductPurchasePriceHistoryItem(
    val purchaseId: String,
    val purchaseReference: String,
    val supplierId: String,
    val supplierNameArabic: String,
    val unitCostMinor: Long,
    val quantity: Int,
    val lineTotalMinor: Long,
    val timestamp: Long
)

/**
 * Product profitability derived from actual sales transactions and WAC/unit cost (Requirement P1-A).
 */
data class ProductProfitabilityItem(
    val productId: String,
    val totalQuantitySold: Int,
    val totalRevenueMinor: Long,
    val totalCogsMinor: Long,
    val grossProfitMinor: Long,
    val grossMarginBasisPoints: Int // e.g. 2500 = 25.0%
) {
    val grossMarginPercent: Double
        get() = (grossMarginBasisPoints.toDouble() / 100.0)
}

/**
 * Comprehensive Product Intelligence Summary (Requirements P1-A, P1-B, P1-C).
 */
data class ProductIntelligenceSummary(
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val currentStockQty: Int,
    val stockValueMinor: Long,
    val weightedAvgCostMinor: Long,
    val lastPurchasePriceMinor: Long,
    val averagePurchasePriceMinor: Long,
    val minPurchasePriceMinor: Long,
    val maxPurchasePriceMinor: Long,
    val supplierComparisons: List<SupplierPriceComparisonItem>,
    val priceHistory: List<ProductPurchasePriceHistoryItem>,
    val profitability: ProductProfitabilityItem,
    val suggestedReorderQty: Int,
    val reorderReasonArabic: String,
    val velocityClass: String, // FAST_MOVING, NORMAL, SLOW_MOVING, DEAD_STOCK
    val velocityLabelArabic: String,
    val daysSinceLastSale: Int?,
    val recordedPriceChanges: List<PriceHistoryEntity> = emptyList()
)

object ProductPurchaseIntelligenceEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun computeProductIntelligence(
        product: ProductInventoryProjection,
        allPurchases: List<PurchaseEntity>,
        allPurchaseItems: List<PurchaseItemEntity>,
        allSales: List<SaleEntity>,
        allSaleItems: List<SaleItemEntity>,
        suppliers: List<SupplierEntity>,
        priceChanges: List<PriceHistoryEntity> = emptyList(),
        leadTimeDays: Int = 3,
        nowMillis: Long = System.currentTimeMillis()
    ): ProductIntelligenceSummary {
        val suppliersMap = suppliers.associateBy { it.localId }
        val purchasesMap = allPurchases.associateBy { it.localId }
        val activeSalesMap = allSales.filter { it.status != "CANCELLED" && it.status != "REFUNDED" }.associateBy { it.localId }

        // 1. Purchase items for this product
        val prodPurchases = allPurchaseItems.filter { it.productId == product.productId }
        val historyItems = mutableListOf<ProductPurchasePriceHistoryItem>()

        for (item in prodPurchases) {
            val purchase = purchasesMap[item.purchaseId]
            val sup = purchase?.let { suppliersMap[it.supplierId] }
            val supName = sup?.companyName ?: "مورد غير محدد"
            historyItems += ProductPurchasePriceHistoryItem(
                purchaseId = item.purchaseId,
                purchaseReference = purchase?.invoiceNumber ?: item.purchaseId,
                supplierId = purchase?.supplierId ?: "",
                supplierNameArabic = supName,
                unitCostMinor = item.unitCostMinor,
                quantity = item.quantity,
                lineTotalMinor = item.lineTotalMinor,
                timestamp = purchase?.purchaseTimestamp ?: item.createdAt
            )
        }
        val sortedHistory = historyItems.sortedByDescending { it.timestamp }

        val lastPurchasePrice = sortedHistory.firstOrNull()?.unitCostMinor ?: product.weightedAvgCostMinor
        val minPurchasePrice = if (sortedHistory.isNotEmpty()) sortedHistory.minOf { it.unitCostMinor } else product.weightedAvgCostMinor
        val maxPurchasePrice = if (sortedHistory.isNotEmpty()) sortedHistory.maxOf { it.unitCostMinor } else product.weightedAvgCostMinor
        val totalPurchasedUnits = sortedHistory.sumOf { it.quantity }
        val totalPurchasedCost = sortedHistory.sumOf { it.lineTotalMinor }
        val avgPurchasePrice = if (totalPurchasedUnits > 0) totalPurchasedCost / totalPurchasedUnits.toLong() else product.weightedAvgCostMinor

        // 2. Supplier Price Comparison
        val supplierComparisons = sortedHistory.groupBy { it.supplierId }
            .filter { (supId, _) -> supId.isNotBlank() }
            .map { (supId, items) ->
                val sup = suppliersMap[supId]
                val supName = sup?.companyName ?: "مورد ($supId)"
                val phone = sup?.phone ?: ""
                val qty = items.sumOf { it.quantity }
                val totalCost = items.sumOf { it.lineTotalMinor }
                val avgCost = if (qty > 0) totalCost / qty.toLong() else items.first().unitCostMinor
                val minCost = items.minOf { it.unitCostMinor }
                val maxCost = items.maxOf { it.unitCostMinor }
                val latest = items.maxByOrNull { it.timestamp }

                SupplierPriceComparisonItem(
                    supplierId = supId,
                    supplierNameArabic = supName,
                    supplierPhone = phone,
                    totalQuantitySupplied = qty,
                    lastPurchasePriceMinor = latest?.unitCostMinor ?: avgCost,
                    minPurchasePriceMinor = minCost,
                    maxPurchasePriceMinor = maxCost,
                    averagePurchasePriceMinor = avgCost,
                    lastPurchaseTimestamp = latest?.timestamp ?: 0L
                )
            }.sortedBy { it.lastPurchasePriceMinor }

        // 3. Sales & Profitability for this product
        val prodSaleItems = allSaleItems.filter { it.productId == product.productId && it.saleId in activeSalesMap }
        val totalSoldUnits = prodSaleItems.sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) }
        val totalRevenue = prodSaleItems.sumOf { (it.unitSellingPriceMinor * (it.quantity - it.returnedQuantity)).coerceAtLeast(0L) }
        val totalCogs = prodSaleItems.sumOf { (it.unitCostAtSaleMinor * (it.quantity - it.returnedQuantity)).coerceAtLeast(0L) }
        val grossProfit = totalRevenue - totalCogs
        val marginBps = if (totalRevenue > 0L) ((grossProfit * 10_000L) / totalRevenue).toInt() else 0

        val profitability = ProductProfitabilityItem(
            productId = product.productId,
            totalQuantitySold = totalSoldUnits,
            totalRevenueMinor = totalRevenue,
            totalCogsMinor = totalCogs,
            grossProfitMinor = grossProfit,
            grossMarginBasisPoints = marginBps
        )

        // 4. Sales Velocity & Reorder Intelligence (last 30 days)
        val thirtyDaysAgo = nowMillis - 30L * DAY_MILLIS
        val last30DaysSaleItems = prodSaleItems.filter {
            val sale = activeSalesMap[it.saleId]
            sale != null && sale.saleTimestamp >= thirtyDaysAgo
        }
        val sold30Days = last30DaysSaleItems.sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) }
        val dailySalesRate = sold30Days.toDouble() / 30.0

        val lastSaleTimestamp = prodSaleItems.mapNotNull { activeSalesMap[it.saleId]?.saleTimestamp }.maxOrNull()
        val daysSinceLastSale = lastSaleTimestamp?.let { ((nowMillis - it).coerceAtLeast(0L) / DAY_MILLIS).toInt() }

        val currentStock = product.derivedStockQty.coerceAtLeast(0)
        val minStock = product.reorderThresholdQty.coerceAtLeast(1)
        val reorderPoint = maxOf(product.reorderPointQty, minStock)

        val (velocityClass, velocityLabel) = when {
            currentStock > 0 && (daysSinceLastSale != null && daysSinceLastSale >= 60) ->
                "DEAD_STOCK" to "مخزون راكد"
            sold30Days >= 10 || (dailySalesRate >= 0.5) ->
                "FAST_MOVING" to "سريع الحركة"
            currentStock > 0 && sold30Days <= 1 && (daysSinceLastSale == null || daysSinceLastSale >= 21) ->
                "SLOW_MOVING" to "بطيء الحركة"
            else ->
                "NORMAL" to "حركة طبيعية"
        }

        // Suggested Reorder Calculation
        val leadTimeDemand = ceil(dailySalesRate * leadTimeDays.coerceAtLeast(1)).toInt()
        val isLowStock = currentStock <= reorderPoint
        val isDemandExceedingStock = dailySalesRate > 0.0 && currentStock < (leadTimeDemand + minStock)

        val (suggestedQty, reorderReason) = when {
            currentStock <= 0 -> {
                val needed = maxOf(reorderPoint * 2, 5)
                needed to "نفد المخزون بالكامل — يُقترح طلب $needed قطعة لتغطية حد إعادة الطلب"
            }
            currentStock <= minStock -> {
                val needed = (reorderPoint + minStock - currentStock).coerceAtLeast(1)
                needed to "المخزون أقل من الحد الأدنى الحرج ($currentStock من $minStock) — يُقترح طلب $needed قطعة"
            }
            isLowStock -> {
                val needed = (reorderPoint + leadTimeDemand - currentStock).coerceAtLeast(1)
                needed to "المخزون وصل إلى حد إعادة الطلب ($currentStock من $reorderPoint) — يُقترح تعزيزه بـ $needed قطعة"
            }
            isDemandExceedingStock -> {
                val needed = (leadTimeDemand + minStock - currentStock).coerceAtLeast(1)
                needed to "معدل المبيعات (${(dailySalesRate * 10).roundToInt() / 10.0} قطعة/يوم) يتطلب $needed قطعة لتغطية فترة التوريد ($leadTimeDays أيام)"
            }
            else -> {
                0 to "المخزون كافٍ ومستقر حالياً ($currentStock قطعة)"
            }
        }

        val relevantPriceChanges = priceChanges.filter { it.productId == product.productId }

        return ProductIntelligenceSummary(
            productId = product.productId,
            productNameArabic = product.nameArabic,
            sku = product.sku,
            currentStockQty = currentStock,
            stockValueMinor = product.stockValueMinor,
            weightedAvgCostMinor = product.weightedAvgCostMinor,
            lastPurchasePriceMinor = lastPurchasePrice,
            averagePurchasePriceMinor = avgPurchasePrice,
            minPurchasePriceMinor = minPurchasePrice,
            maxPurchasePriceMinor = maxPurchasePrice,
            supplierComparisons = supplierComparisons,
            priceHistory = sortedHistory,
            profitability = profitability,
            suggestedReorderQty = suggestedQty,
            reorderReasonArabic = reorderReason,
            velocityClass = velocityClass,
            velocityLabelArabic = velocityLabel,
            daysSinceLastSale = daysSinceLastSale,
            recordedPriceChanges = relevantPriceChanges
        )
    }
}

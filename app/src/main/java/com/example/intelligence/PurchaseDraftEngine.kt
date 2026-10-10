package com.example.intelligence

import com.example.core.AppResult
import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.SupplierEntity
import com.example.financial.Money
import java.util.UUID

enum class PurchaseDraftStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    CONVERTED,
    CANCELLED
}

data class SuggestedReorderItem(
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val currentStockQty: Int,
    val targetStockQty: Int,
    val suggestedQuantity: Int,
    val estimatedUnitCostMinor: Long,
    val estimatedTotalCostMinor: Long,
    val urgencyLevelArabic: String, // عاجل (نافد), منخفض, وقائي
    val primarySupplierId: String? = null,
    val primarySupplierNameArabic: String = "مورد مقترح"
)

data class PurchaseDraftLineItem(
    val lineId: String = UUID.randomUUID().toString(),
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val quantity: Int,
    val estimatedUnitCostMinor: Long,
    val estimatedLineTotalMinor: Long = quantity * estimatedUnitCostMinor
)

data class PurchaseDraftRecord(
    val draftId: String,
    val draftNumber: String,
    val supplierId: String?,
    val supplierNameArabic: String,
    val items: List<PurchaseDraftLineItem>,
    val totalEstimatedCostMinor: Long,
    val status: PurchaseDraftStatus,
    val notes: String = "",
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val convertedPurchaseId: String? = null
)

object PurchaseDraftEngine {

    /**
     * Requirement P2-D: Assembles the Smart Suggested Purchases Reorder List from real inventory velocity and thresholds.
     */
    fun buildSuggestedReorderList(
        forecastItems: List<ProductDemandForecastItem>,
        suppliers: List<SupplierEntity>,
        productProjections: List<ProductInventoryProjection>
    ): List<SuggestedReorderItem> {
        val projectionsMap = productProjections.associateBy { it.productId }
        val defaultSupplier = suppliers.firstOrNull()

        return forecastItems
            .mapNotNull { forecast ->
                val proj = projectionsMap[forecast.productId]
                val reorderPoint = proj?.reorderPointQty ?: 5
                val minStock = proj?.reorderThresholdQty ?: 3
                val sku = proj?.sku ?: forecast.productId
                val unitCost = proj?.weightedAvgCostMinor ?: 0L

                val isLow = forecast.currentStockQty <= reorderPoint
                if (forecast.suggestedReorderQty <= 0 && !isLow) {
                    return@mapNotNull null
                }

                val qty = if (forecast.suggestedReorderQty > 0) forecast.suggestedReorderQty else (reorderPoint * 2 - forecast.currentStockQty).coerceAtLeast(3)
                val targetStock = forecast.currentStockQty + qty
                val urgency = when {
                    forecast.currentStockQty <= 0 -> "عاجل (نافد تماماً)"
                    forecast.currentStockQty <= minStock -> "أولوية مرتفعة (مخزون منخفض)"
                    else -> "إعادة طلب وقائية"
                }

                SuggestedReorderItem(
                    productId = forecast.productId,
                    productNameArabic = forecast.productNameArabic,
                    sku = sku,
                    currentStockQty = forecast.currentStockQty,
                    targetStockQty = targetStock,
                    suggestedQuantity = qty,
                    estimatedUnitCostMinor = unitCost,
                    estimatedTotalCostMinor = qty * unitCost,
                    urgencyLevelArabic = urgency,
                    primarySupplierId = defaultSupplier?.localId,
                    primarySupplierNameArabic = defaultSupplier?.companyName ?: "مورد عام"
                )
            }.sortedByDescending { it.urgencyLevelArabic.contains("نافد") }
    }

    /**
     * Requirement P2-E: Creates a new Purchase Draft (No financial effect whatsoever).
     */
    fun createDraft(
        draftId: String = "DRAFT-${UUID.randomUUID().toString().take(8).uppercase()}",
        supplierId: String?,
        supplierNameArabic: String,
        items: List<PurchaseDraftLineItem>,
        notes: String = "",
        actorUserId: String
    ): PurchaseDraftRecord {
        val totalCost = items.sumOf { it.estimatedLineTotalMinor }
        val num = "PR-DFT-${System.currentTimeMillis() % 100000}"
        return PurchaseDraftRecord(
            draftId = draftId,
            draftNumber = num,
            supplierId = supplierId,
            supplierNameArabic = supplierNameArabic.ifBlank { "مورد عام" },
            items = items,
            totalEstimatedCostMinor = totalCost,
            status = PurchaseDraftStatus.DRAFT,
            notes = notes,
            createdByUserId = actorUserId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Transition draft state (DRAFT -> REVIEW -> APPROVED -> CANCELLED).
     */
    fun transitionStatus(
        draft: PurchaseDraftRecord,
        newStatus: PurchaseDraftStatus
    ): AppResult<PurchaseDraftRecord> {
        if (draft.status == PurchaseDraftStatus.CONVERTED) {
            return AppResult.Error("مسودة الشراء محولة بالفعل إلى فاتورة شراء رسمية ولا يمكن تعديل حالتها.")
        }
        val updated = draft.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        return AppResult.Success(updated)
    }
}

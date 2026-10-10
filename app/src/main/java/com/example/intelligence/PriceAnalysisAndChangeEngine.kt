package com.example.intelligence

import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.financial.Money

/**
 * Data model for product pricing and profit margin analysis (Requirement P2-G).
 */
data class ProductPriceAnalysis(
    val productId: String,
    val currentSellingPriceMinor: Long,
    val currentCostWacMinor: Long,
    val currentMarginPercent: Double,
    val isMarginCompressed: Boolean,
    val marginTrendArabic: String,
    val purchaseCostTrendArabic: String,
    val lastPurchasePriceMinor: Long
)

/**
 * Live preview model for proposing and validating price changes before committing (Requirement P2-G).
 */
data class PriceChangePreview(
    val proposedPriceMinor: Long,
    val currentMarginPercent: Double,
    val proposedMarginPercent: Double,
    val estimatedProfitDiffPerUnitMinor: Long,
    val warningArabic: String? = null
)

/**
 * Inventory demand forecast model for each catalog item (Requirement P2-C).
 */
data class ProductDemandForecastItem(
    val productId: String,
    val productNameArabic: String,
    val currentStockQty: Int,
    val avgDailyVelocity: Double,
    val estimatedDaysRemaining: Int?,
    val suggestedReorderQty: Int,
    val confidenceLabelArabic: String,
    val explanationArabic: String
)

object PriceAnalysisAndChangeEngine {

    fun analyzeProductPrice(
        product: ProductInventoryProjection,
        productPurchaseItems: List<PurchaseItemEntity> = emptyList(),
        priceHistory: List<PriceHistoryEntity> = emptyList()
    ): ProductPriceAnalysis {
        val selling = product.defaultSellingPriceMinor
        val wac = product.weightedAvgCostMinor.coerceAtLeast(0L)

        val marginPercent = if (selling > 0L) {
            val profit = selling - wac
            ((profit.toDouble() / selling.toDouble()) * 100.0).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val sortedPurchases = productPurchaseItems.sortedByDescending { it.createdAt }
        val lastPurchasePrice = sortedPurchases.firstOrNull()?.unitCostMinor ?: wac

        val purchaseCostTrend = when {
            sortedPurchases.size >= 2 -> {
                val latestCost = sortedPurchases[0].unitCostMinor
                val prevCost = sortedPurchases[1].unitCostMinor
                when {
                    latestCost > prevCost -> "ارتفاع في تكلفة التوريد الأخيرة (+${Money.ofMinor(latestCost - prevCost).formatArabic()})"
                    latestCost < prevCost -> "انخفاض في تكلفة التوريد (-${Money.ofMinor(prevCost - latestCost).formatArabic()})"
                    else -> "تكلفة التوريد مستقرة"
                }
            }
            lastPurchasePrice > wac && wac > 0L -> "آخر شراء أعلى من المتوسط المرجح"
            else -> "تكلفة الشراء متوافقة مع المتوسط"
        }

        val isMarginCompressed = marginPercent < 15.0 || (lastPurchasePrice > selling)
        val marginTrendArabic = when {
            lastPurchasePrice > selling -> "خسارة فعلية: تكلفة التوريد أعلى من سعر البيع!"
            marginPercent < 10.0 -> "هامش ربح حرج ومنخفض جداً (أقل من 10%)"
            marginPercent < 15.0 -> "هامش ربح مضغوط دون المعدل المستهدف (15%)"
            marginPercent >= 30.0 -> "هامش ربح ممتاز وصحي (أعلى من 30%)"
            else -> "هامش ربح طبيعي ومستقر"
        }

        return ProductPriceAnalysis(
            productId = product.productId,
            currentSellingPriceMinor = selling,
            currentCostWacMinor = wac,
            currentMarginPercent = marginPercent,
            isMarginCompressed = isMarginCompressed,
            marginTrendArabic = marginTrendArabic,
            purchaseCostTrendArabic = purchaseCostTrend,
            lastPurchasePriceMinor = lastPurchasePrice
        )
    }

    fun previewPriceChange(
        product: ProductInventoryProjection,
        proposedSellingPriceMinor: Long
    ): PriceChangePreview {
        val selling = product.defaultSellingPriceMinor
        val wac = product.weightedAvgCostMinor.coerceAtLeast(0L)

        val currentMargin = if (selling > 0L) {
            (((selling - wac).toDouble() / selling.toDouble()) * 100.0).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val proposedMargin = if (proposedSellingPriceMinor > 0L) {
            (((proposedSellingPriceMinor - wac).toDouble() / proposedSellingPriceMinor.toDouble()) * 100.0).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val profitDiff = proposedSellingPriceMinor - selling

        val warning = when {
            proposedSellingPriceMinor <= 0L -> "سعر البيع يجب أن يكون أكبر من الصفر"
            proposedSellingPriceMinor < wac -> "تحذير: السعر المقترح أقل من متوسط تكلفة الشراء (${Money.ofMinor(wac).formatArabic()})!"
            proposedMargin < 5.0 -> "تنبيه: هامش الربح المقترح ضئيل جداً (${"%.1f".format(proposedMargin)}%)"
            proposedSellingPriceMinor > (selling * 3L) && selling > 0L -> "تنبيه: السعر المقترح أعلى بثلاثة أضعاف من السعر الحالي"
            else -> null
        }

        return PriceChangePreview(
            proposedPriceMinor = proposedSellingPriceMinor,
            currentMarginPercent = currentMargin,
            proposedMarginPercent = proposedMargin,
            estimatedProfitDiffPerUnitMinor = profitDiff,
            warningArabic = warning
        )
    }
}

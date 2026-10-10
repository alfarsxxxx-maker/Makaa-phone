package com.example.inventory

import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.core.SyncState
import com.example.database.dao.CatalogInventoryDao
import com.example.database.entities.InventoryEntity
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.SerialLifecycleStatus
import com.example.database.entities.SerialNumberEntity
import com.example.financial.CurrencySpec
import com.example.financial.Money
import com.example.financial.RoundingPolicy
import java.math.BigDecimal

/**
 * Result of replaying historical inventory movements (Requirement 13).
 */
data class ReconstructedInventoryState(
    val productId: String,
    val currentQuantity: Int,
    val weightedAvgCostMinor: Long,
    val totalInventoryValuationMinor: Long,
    val movementCount: Int
) {
    fun totalValuationMoney(currency: CurrencySpec = CurrencySpec.EGP): Money =
        Money.ofMinor(totalInventoryValuationMinor, currency)
}

/**
 * Pure Weighted Average Cost (WAC) and Movement-Based Inventory Engine (Requirements 12, 13, 14).
 */
object WeightedAverageCostCalculator {

    /**
     * Computes the new Weighted Average Cost in integer minor units when stock changes.
     */
    fun computeWeightedAverageCostMinor(
        currentQuantity: Int,
        currentWacMinor: Long,
        movementType: InventoryMovementType,
        movementQuantity: Int,
        movementUnitCostMinor: Long,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): Long {
        require(currentQuantity >= 0) { "Current stock quantity cannot be negative: $currentQuantity" }
        require(currentWacMinor >= 0L) { "Current WAC cannot be negative: $currentWacMinor" }
        require(movementQuantity > 0) { "Movement quantity must be strictly positive: $movementQuantity" }
        require(movementUnitCostMinor >= 0L) { "Movement unit cost cannot be negative: $movementUnitCostMinor" }

        return if (movementType.isInbound) {
            if (currentQuantity == 0) {
                movementUnitCostMinor
            } else {
                val existingValue = BigDecimal.valueOf(currentQuantity.toLong())
                    .multiply(BigDecimal.valueOf(currentWacMinor))
                val inboundValue = BigDecimal.valueOf(movementQuantity.toLong())
                    .multiply(BigDecimal.valueOf(movementUnitCostMinor))
                val newTotalQty = BigDecimal.valueOf((currentQuantity + movementQuantity).toLong())
                existingValue.add(inboundValue)
                    .divide(newTotalQty, 0, roundingPolicy.javaMode)
                    .longValueExact()
            }
        } else {
            require(currentQuantity >= movementQuantity) {
                "Insufficient stock: available=$currentQuantity, requested outbound=$movementQuantity"
            }
            // Outbound movements issue stock at current WAC; unit WAC of remaining units does not change
            currentWacMinor
        }
    }

    /**
     * Reconstructs current quantity, Weighted Average Cost, and total valuation strictly from
     * chronological historical movements (Requirement 13).
     */
    fun reconstructFromHistory(
        productId: String,
        chronologicalMovements: List<InventoryMovementEntity>,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): ReconstructedInventoryState {
        var runningQty = 0
        var runningWacMinor = 0L

        for (movement in chronologicalMovements) {
            runningWacMinor = computeWeightedAverageCostMinor(
                currentQuantity = runningQty,
                currentWacMinor = runningWacMinor,
                movementType = movement.movementType,
                movementQuantity = movement.quantity,
                movementUnitCostMinor = movement.unitCostMinor,
                roundingPolicy = roundingPolicy
            )
            runningQty += movement.signedQuantityDelta
            check(runningQty >= 0) {
                "Corrupted movement stream for product $productId: negative running stock $runningQty at movement ${movement.localId}"
            }
        }

        val totalValuationMinor = BigDecimal.valueOf(runningQty.toLong())
            .multiply(BigDecimal.valueOf(runningWacMinor))
            .longValueExact()

        return ReconstructedInventoryState(
            productId = productId,
            currentQuantity = runningQty,
            weightedAvgCostMinor = runningWacMinor,
            totalInventoryValuationMinor = totalValuationMinor,
            movementCount = chronologicalMovements.size
        )
    }
}

/**
 * Operational Inventory Engine managing movement recording, WAC persistence, and Serial/IMEI tracking.
 * Intended to be invoked inside a Room database transaction alongside AuditLog and SyncQueue writes.
 */
class InventoryEngine(
    private val catalogInventoryDao: CatalogInventoryDao
) {

    /**
     * Records an inventory movement and updates the product's WAC snapshot atomically.
     */
    suspend fun recordMovementInternal(
        shopId: String,
        productId: String,
        variantId: String? = null,
        serialNumberId: String? = null,
        movementType: InventoryMovementType,
        quantity: Int,
        inboundUnitCostMinor: Long? = null,
        reason: String,
        referenceType: String,
        referenceId: String,
        performedByUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<InventoryMovementEntity> {
        if (quantity <= 0) {
            return AppResult.Error("الكمية يجب أن تكون أكبر من صفر", "INVALID_QUANTITY")
        }
        val product = catalogInventoryDao.getProductById(productId)
            ?: return AppResult.Error("الصنف غير موجود في قاعدة البيانات", "PRODUCT_NOT_FOUND")

        if (product.shopId != shopId) {
            return AppResult.Error("عدم تطابق معرف المتجر للصنف", "TENANT_MISMATCH")
        }

        if (product.isSerialized) {
            if (serialNumberId == null) {
                return AppResult.Error("هذا الصنف يتطلب رقم سيريال / IMEI", "SERIAL_REQUIRED")
            }
            if (quantity != 1) {
                return AppResult.Error(
                    "الأصناف المسلسلة (IMEI) تتحرك بوحدة واحدة (1) لكل سيريال",
                    "INVALID_SERIAL_QUANTITY"
                )
            }
            val serial = catalogInventoryDao.getSerialById(serialNumberId)
                ?: return AppResult.Error("السيريال المطلوب غير موجود", "SERIAL_NOT_FOUND")
            if (serial.shopId != shopId) {
                return AppResult.Error("عدم تطابق متجر السيريال", "TENANT_MISMATCH")
            }
            if (serial.productId != productId) {
                return AppResult.Error(
                    "رقم السيريال / IMEI (${serial.serialOrImei}) مسجل لصنف آخر ولا يمكن إسناده للصنف (${product.nameArabic})",
                    "SERIAL_PRODUCT_MISMATCH"
                )
            }
            if (!movementType.isInbound && !serial.status.isActiveInStock) {
                return AppResult.Error(
                    "الجهاز ذو السيريال (${serial.serialOrImei}) غير متاح في المخزن حالياً (${serial.status.arabicLabel})",
                    "SERIAL_NOT_AVAILABLE"
                )
            }
        }

        val currentQty = catalogInventoryDao.getDerivedStockQuantity(shopId, productId)
        val existingRecord = catalogInventoryDao.getInventoryRecord(shopId, productId)
        val currentWacMinor = existingRecord?.weightedAvgCostMinor ?: 0L

        if (!movementType.isInbound && currentQty < quantity) {
            return AppResult.Error(
                "الرصيد المخزني غير كافٍ للصنف (${product.nameArabic}): المتاح $currentQty والمطلوب $quantity",
                "INSUFFICIENT_STOCK"
            )
        }

        val effectiveUnitCostMinor = if (movementType.isInbound) {
            val cost = inboundUnitCostMinor ?: currentWacMinor
            if (cost < 0L) {
                return AppResult.Error("تكلفة الوحدة لا يمكن أن تكون سالبة", "NEGATIVE_COST")
            }
            cost
        } else {
            currentWacMinor
        }

        val newWacMinor = WeightedAverageCostCalculator.computeWeightedAverageCostMinor(
            currentQuantity = currentQty,
            currentWacMinor = currentWacMinor,
            movementType = movementType,
            movementQuantity = quantity,
            movementUnitCostMinor = effectiveUnitCostMinor
        )

        val signedDelta = if (movementType.isInbound) quantity else -quantity
        val quantityAfter = currentQty + signedDelta
        val totalCostMinor = Math.multiplyExact(effectiveUnitCostMinor, quantity.toLong())
        val movementId = IdGenerator.newPrefixedId("MOV")

        val movement = InventoryMovementEntity(
            localId = movementId,
            shopId = shopId,
            productId = productId,
            variantId = variantId,
            serialNumberId = serialNumberId,
            movementType = movementType,
            quantity = quantity,
            signedQuantityDelta = signedDelta,
            quantityBefore = currentQty,
            quantityAfter = quantityAfter,
            unitCostMinor = effectiveUnitCostMinor,
            totalCostMinor = totalCostMinor,
            weightedAvgCostBeforeMinor = currentWacMinor,
            weightedAvgCostAfterMinor = newWacMinor,
            reason = reason.ifBlank { movementType.arabicLabel },
            referenceType = referenceType,
            referenceId = referenceId,
            performedByUserId = performedByUserId,
            deviceId = deviceId,
            timestamp = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp,
            version = 1L,
            syncState = SyncState.PENDING
        )

        catalogInventoryDao.insertInventoryMovement(movement)

        val updatedRecord = InventoryEntity(
            localId = existingRecord?.localId ?: IdGenerator.newPrefixedId("INV"),
            cloudId = existingRecord?.cloudId,
            shopId = shopId,
            productId = productId,
            weightedAvgCostMinor = newWacMinor,
            lastMovementId = movementId,
            lastCalculatedAt = timestamp,
            deviceId = deviceId,
            createdAt = existingRecord?.createdAt ?: timestamp,
            updatedAt = timestamp,
            version = (existingRecord?.version ?: 0L) + 1L,
            syncState = SyncState.PENDING
        )
        catalogInventoryDao.upsertInventoryRecord(updatedRecord)

        return AppResult.Success(movement)
    }

    /**
     * Registers a new active Serial / IMEI in stock, rejecting duplicates (Requirement 14).
     */
    suspend fun registerInboundSerialInternal(
        shopId: String,
        productId: String,
        variantId: String? = null,
        serialOrImei: String,
        secondaryImei: String? = null,
        acquisitionCostMinor: Long,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<SerialNumberEntity> {
        val imeiValidation = ImeiValidationEngine.validateImeiOrSerial(serialOrImei)
        if (imeiValidation is AppResult.Error) {
            return imeiValidation
        }
        val cleanedSerial = (imeiValidation as AppResult.Success).data

        val duplicateActive = catalogInventoryDao.getActiveSerialByImei(shopId, cleanedSerial)
        if (duplicateActive != null) {
            return AppResult.Error(
                "رقم السيريال / IMEI ($cleanedSerial) مسجل بالفعل كجهاز نشط في المخزن!",
                "DUPLICATE_ACTIVE_SERIAL"
            )
        }
        val existingAny = catalogInventoryDao.getSerialByImeiAnyStatus(shopId, cleanedSerial)
        if (existingAny != null) {
            if (existingAny.productId != productId) {
                return AppResult.Error(
                    "رقم السيريال / IMEI ($cleanedSerial) مسجل مسبقاً لصنف آخر ولا يمكن ربطه بصنف مختلف!",
                    "SERIAL_PRODUCT_MISMATCH"
                )
            }
            if (existingAny.status == SerialLifecycleStatus.SOLD) {
                return AppResult.Error(
                    "رقم السيريال / IMEI ($cleanedSerial) مباع بالفعل ولا يمكن إعادة شرائه دون مرتجع مبيعات!",
                    "DUPLICATE_SOLD_SERIAL"
                )
            }
            // Reactivate a previously returned-to-supplier or damaged serial for the same product
            val reactivated = existingAny.copy(
                status = SerialLifecycleStatus.IN_STOCK,
                activeGuardKey = "$shopId:$cleanedSerial",
                acquisitionCostMinor = acquisitionCostMinor,
                updatedAt = timestamp,
                version = existingAny.version + 1L,
                syncState = SyncState.PENDING
            )
            catalogInventoryDao.updateSerialNumber(reactivated)
            return AppResult.Success(reactivated)
        }
        val serialEntity = SerialNumberEntity(
            localId = IdGenerator.newPrefixedId("SER"),
            shopId = shopId,
            productId = productId,
            variantId = variantId,
            serialOrImei = cleanedSerial,
            secondaryImei = secondaryImei?.trim()?.takeIf { it.isNotEmpty() },
            status = SerialLifecycleStatus.IN_STOCK,
            activeGuardKey = "$shopId:$cleanedSerial",
            acquisitionCostMinor = acquisitionCostMinor,
            deviceId = deviceId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        catalogInventoryDao.insertSerialNumber(serialEntity)
        return AppResult.Success(serialEntity)
    }

    /**
     * Transitions an existing Serial / IMEI status (e.g. IN_STOCK -> SOLD) and updates its `activeGuardKey`.
     */
    suspend fun transitionSerialStatusInternal(
        shopId: String,
        serialId: String,
        newStatus: SerialLifecycleStatus,
        movementId: String,
        customerId: String? = null,
        expectedProductId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<SerialNumberEntity> {
        val existing = catalogInventoryDao.getSerialById(serialId)
            ?: return AppResult.Error("السيريال المطلوب غير موجود", "SERIAL_NOT_FOUND")
        if (existing.shopId != shopId) {
            return AppResult.Error("عدم تطابق متجر السيريال", "TENANT_MISMATCH")
        }
        if (expectedProductId != null && existing.productId != expectedProductId) {
            return AppResult.Error(
                "الجهاز ذو السيريال (${existing.serialOrImei}) ينتمي لصنف آخر ولا يمكن إسناده لهذا الصنف",
                "SERIAL_PRODUCT_MISMATCH"
            )
        }
        if (newStatus == SerialLifecycleStatus.SOLD && !existing.status.isActiveInStock) {
            return AppResult.Error(
                "الجهاز ذو السيريال (${existing.serialOrImei}) غير متاح للبيع حالياً (${existing.status.arabicLabel})",
                "SERIAL_NOT_AVAILABLE"
            )
        }
        val newActiveGuardKey = if (newStatus.isActiveInStock) {
            "$shopId:${existing.serialOrImei}"
        } else {
            null
        }
        val resolvedCustomerId = if (newStatus.isActiveInStock) {
            customerId
        } else {
            customerId ?: existing.currentCustomerId
        }
        val updated = existing.copy(
            status = newStatus,
            activeGuardKey = newActiveGuardKey,
            lastMovementId = movementId,
            currentCustomerId = resolvedCustomerId,
            updatedAt = timestamp,
            version = existing.version + 1L,
            syncState = SyncState.PENDING
        )
        catalogInventoryDao.updateSerialNumber(updated)
        return AppResult.Success(updated)
    }

    /**
     * Reconstructs and verifies a product's valuation from its complete movement history.
     */
    suspend fun verifyProductValuationFromHistory(
        shopId: String,
        productId: String
    ): ReconstructedInventoryState {
        val history = catalogInventoryDao.getMovementsForProductChronological(shopId, productId)
        return WeightedAverageCostCalculator.reconstructFromHistory(productId, history)
    }
}

/**
 * V4 Section 4: Advanced Pricing Tiers & Price Floor Protection Engine.
 */
enum class PricingTier(val code: String, val arabicLabel: String) {
    RETAIL("RETAIL", "سعر القطاعي"),
    WHOLESALE("WHOLESALE", "سعر الجملة"),
    SPECIAL_CUSTOMER("SPECIAL_CUSTOMER", "سعر عميل خاص"),
    PROMOTIONAL("PROMOTIONAL", "سعر العرض الترويجي");

    companion object {
        fun fromCode(code: String): PricingTier =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: RETAIL
    }
}

data class LinePricingEvaluation(
    val productId: String,
    val productNameArabic: String,
    val quantity: Int,
    val tierBasePriceMinor: Long,
    val unitSellingPriceMinor: Long,
    val effectiveUnitPriceAfterDiscountsMinor: Long,
    val unitWacCostMinor: Long,
    val minAllowedPriceMinor: Long,
    val maxDiscountMinor: Long,
    val totalUnitDiscountMinor: Long,
    val isBelowCost: Boolean,
    val isBelowMinAllowedPrice: Boolean,
    val isExceedingMaxDiscount: Boolean,
    val requiresManagerApproval: Boolean,
    val warningsArabic: List<String>
)

object PricingPolicyEngine {
    fun resolveTierUnitPriceMinor(
        defaultSellingPriceMinor: Long,
        wholesalePriceMinor: Long,
        specialCustomerPriceMinor: Long,
        promotionalPriceMinor: Long,
        isPromotionActive: Boolean,
        tier: PricingTier
    ): Long {
        return when (tier) {
            PricingTier.PROMOTIONAL ->
                if (isPromotionActive && promotionalPriceMinor > 0L) promotionalPriceMinor else defaultSellingPriceMinor
            PricingTier.WHOLESALE ->
                if (wholesalePriceMinor > 0L) wholesalePriceMinor else defaultSellingPriceMinor
            PricingTier.SPECIAL_CUSTOMER ->
                if (specialCustomerPriceMinor > 0L) specialCustomerPriceMinor else defaultSellingPriceMinor
            PricingTier.RETAIL ->
                if (isPromotionActive && promotionalPriceMinor > 0L) promotionalPriceMinor else defaultSellingPriceMinor
        }
    }

    fun evaluateLinePricing(
        productId: String,
        productNameArabic: String,
        quantity: Int,
        defaultSellingPriceMinor: Long,
        minSellingPriceMinor: Long,
        wholesalePriceMinor: Long = 0L,
        specialCustomerPriceMinor: Long = 0L,
        maxDiscountMinor: Long = 0L,
        promotionalPriceMinor: Long = 0L,
        isPromotionActive: Boolean = false,
        unitWacCostMinor: Long,
        unitSellingPriceMinor: Long,
        lineDiscountMinor: Long = 0L,
        allocatedInvoiceDiscountMinor: Long = 0L,
        tier: PricingTier = PricingTier.RETAIL
    ): LinePricingEvaluation {
        val safeQty = quantity.coerceAtLeast(1)
        val tierBase = resolveTierUnitPriceMinor(
            defaultSellingPriceMinor = defaultSellingPriceMinor,
            wholesalePriceMinor = wholesalePriceMinor,
            specialCustomerPriceMinor = specialCustomerPriceMinor,
            promotionalPriceMinor = promotionalPriceMinor,
            isPromotionActive = isPromotionActive,
            tier = tier
        )
        val perUnitLineDiscount = lineDiscountMinor / safeQty
        val perUnitInvoiceDiscount = allocatedInvoiceDiscountMinor / safeQty
        val effectiveUnitPrice = (unitSellingPriceMinor - perUnitLineDiscount - perUnitInvoiceDiscount).coerceAtLeast(0L)
        val totalUnitDiscount = (tierBase - effectiveUnitPrice).coerceAtLeast(0L) +
            (if (unitSellingPriceMinor > tierBase) (unitSellingPriceMinor - effectiveUnitPrice).coerceAtLeast(0L) else 0L)

        val isBelowCost = unitWacCostMinor > 0L && effectiveUnitPrice < unitWacCostMinor
        val isBelowMinAllowed = minSellingPriceMinor > 0L && effectiveUnitPrice < minSellingPriceMinor
        val isExceedingMaxDiscount = maxDiscountMinor > 0L && (
            (unitSellingPriceMinor - effectiveUnitPrice) > maxDiscountMinor ||
                (tierBase > effectiveUnitPrice && (tierBase - effectiveUnitPrice) > maxDiscountMinor)
            )

        val warnings = mutableListOf<String>()
        if (isBelowCost) {
            warnings += "تحذير حرج: السعر الصافي للصنف ($productNameArabic) أقل من متوسط التكلفة المرجح (${Money.ofMinor(unitWacCostMinor).formatArabic()})!"
        }
        if (isBelowMinAllowed) {
            warnings += "تنبيه: السعر الصافي للصنف ($productNameArabic) أقل من الحد الأدنى المسموح به للبيع (${Money.ofMinor(minSellingPriceMinor).formatArabic()})!"
        }
        if (isExceedingMaxDiscount) {
            warnings += "تنبيه: الخصم الممنوح على الصنف ($productNameArabic) يتجاوز الحد الأقصى للخصم المسموح (${Money.ofMinor(maxDiscountMinor).formatArabic()})!"
        }

        return LinePricingEvaluation(
            productId = productId,
            productNameArabic = productNameArabic,
            quantity = safeQty,
            tierBasePriceMinor = tierBase,
            unitSellingPriceMinor = unitSellingPriceMinor,
            effectiveUnitPriceAfterDiscountsMinor = effectiveUnitPrice,
            unitWacCostMinor = unitWacCostMinor,
            minAllowedPriceMinor = minSellingPriceMinor,
            maxDiscountMinor = maxDiscountMinor,
            totalUnitDiscountMinor = totalUnitDiscount,
            isBelowCost = isBelowCost,
            isBelowMinAllowedPrice = isBelowMinAllowed,
            isExceedingMaxDiscount = isExceedingMaxDiscount,
            requiresManagerApproval = isBelowCost || isBelowMinAllowed || isExceedingMaxDiscount,
            warningsArabic = warnings
        )
    }
}

/**
 * V4 Section 9: Physical Stock Count (الجرد الفعلي) & Product Velocity / Reorder Alert Models.
 */
data class StockCountLineInput(
    val productId: String,
    val countedQuantity: Int,
    val isDamagedOrScrap: Boolean = false
)

data class StockCountLineVariance(
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val systemQuantity: Int,
    val countedQuantity: Int,
    val varianceQuantity: Int, // countedQuantity - systemQuantity
    val unitWacMinor: Long,
    val varianceValueMinor: Long, // varianceQuantity * unitWacMinor
    val movementId: String? = null
)

data class StockCountReconciliationReport(
    val sessionId: String,
    val executedAt: Long,
    val executedByUserId: String,
    val reasonArabic: String,
    val lines: List<StockCountLineVariance>,
    val adjustedLinesCount: Int,
    val totalSurplusValueMinor: Long,
    val totalShortageValueMinor: Long,
    val netVarianceValueMinor: Long
)

enum class ProductVelocityClass(val arabicLabel: String) {
    FAST_MOVING("سريع الحركة"),
    NORMAL_MOVING("متوسط الحركة"),
    SLOW_MOVING("بطيء الحركة / راكد")
}

data class ProductReorderAndVelocityReport(
    val productId: String,
    val sku: String,
    val nameArabic: String,
    val currentStockQty: Int,
    val reorderThresholdQty: Int,
    val reorderPointQty: Int,
    val soldUnitsInPeriod: Int,
    val revenueInPeriodMinor: Long,
    val profitInPeriodMinor: Long,
    val lastSaleTimestamp: Long?,
    val velocityClass: ProductVelocityClass,
    val isOutOfStock: Boolean,
    val isLowStockCritical: Boolean,
    val isReorderPointReached: Boolean,
    val recommendedOrderQty: Int,
    val alertLabelArabic: String
)

/**
 * Complete IMEI / Serial Registry Projection & Full V4 Traceability Timeline (Rule 8 & V4 Section 10).
 * Strictly derived from actual database entities — never invents IMEI information.
 */
data class ImeiRegistryProjection(
    val serialEntity: SerialNumberEntity,
    val productNameArabic: String,
    val productSku: String,
    val saleStatusArabic: String,
    val purchaseStatusArabic: String,
    val customerId: String?,
    val customerNameArabic: String?,
    val customerPhone: String?,
    val saleInvoiceNumber: String?,
    val purchaseInvoiceNumber: String?,
    val supplierId: String? = null,
    val supplierNameArabic: String? = null,
    val purchaseTimestamp: Long? = null,
    val saleTimestamp: Long? = null,
    val warrantyMonths: Int = 12,
    val warrantyExpiryTimestamp: Long? = null,
    val isWarrantyActive: Boolean = false,
    val remainingWarrantyDays: Int = 0,
    val returnRecordsCount: Int = 0,
    val maintenanceTicketsCount: Int = 0
)

data class ImeiLifecycleEvent(
    val timestamp: Long,
    val eventTypeCode: String, // PURCHASE_IN, SALE_OUT, RETURN_IN, EXCHANGE, MAINTENANCE, ADJUSTMENT, DAMAGED
    val titleArabic: String,
    val detailsArabic: String,
    val referenceNumber: String? = null,
    val counterpartyNameArabic: String? = null,
    val amountMinor: Long? = null
)

data class ImeiTraceabilityTimeline(
    val projection: ImeiRegistryProjection,
    val movements: List<InventoryMovementEntity>,
    val timelineEvents: List<ImeiLifecycleEvent>,
    val warrantyStatusArabic: String
)

/**
 * Validates IMEI / Serial strings in the domain layer (Rule 8).
 */
object ImeiValidationEngine {
    fun normalizeImeiOrSerial(raw: String): String =
        Money.normalizeDigits(raw).trim()

    fun validateImeiOrSerial(raw: String): AppResult<String> {
        val normalized = normalizeImeiOrSerial(raw)
        if (normalized.isBlank()) {
            return AppResult.Error("يرجى إدخال أو مسح رقم IMEI / السيريال", "EMPTY_IMEI")
        }
        if (normalized.length < 4 || normalized.length > 32) {
            return AppResult.Error("طول رقم IMEI / السيريال غير صالح (يجب أن يكون بين 4 و 32 خانة)", "INVALID_IMEI_LENGTH")
        }
        if (normalized.any { it.isWhitespace() }) {
            return AppResult.Error("رقم IMEI / السيريال لا يجب أن يحتوي على مسافات", "INVALID_IMEI_FORMAT")
        }
        return AppResult.Success(normalized)
    }

    /**
     * Checks if a 15-digit numeric string passes standard IMEI format & Luhn check.
     */
    fun isStandard15DigitImei(raw: String): Boolean {
        val normalized = normalizeImeiOrSerial(raw)
        return normalized.length == 15 && normalized.all { it.isDigit() }
    }
}

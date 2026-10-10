package com.example.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.SyncState

/**
 * Supported Inventory Movement Types (Requirement 12).
 */
enum class InventoryMovementType(val isInbound: Boolean, val arabicLabel: String) {
    PURCHASE_IN(isInbound = true, arabicLabel = "توريد مشتريات"),
    SALE_OUT(isInbound = false, arabicLabel = "صرف مبيعات"),
    RETURN_IN(isInbound = true, arabicLabel = "مرتجع مبيعات للمخزن"),
    RETURN_OUT(isInbound = false, arabicLabel = "مرتجع مشتريات للمورد"),
    ADJUSTMENT_IN(isInbound = true, arabicLabel = "تسوية جردية (إضافة)"),
    ADJUSTMENT_OUT(isInbound = false, arabicLabel = "تسوية جردية (عجز)"),
    DAMAGE(isInbound = false, arabicLabel = "تالف / هالك"),
    TRANSFER_IN(isInbound = true, arabicLabel = "تحويل مخزني وارد"),
    TRANSFER_OUT(isInbound = false, arabicLabel = "تحويل مخزني صادر"),
    MAINTENANCE_USAGE(isInbound = false, arabicLabel = "قطع غيار صيانة")
}

/**
 * Lifecycle status of a serialized item / IMEI (Requirement 7 & 14).
 */
enum class SerialLifecycleStatus(val isActiveInStock: Boolean, val arabicLabel: String) {
    IN_STOCK(isActiveInStock = true, arabicLabel = "متاح بالمخزن"),
    RESERVED(isActiveInStock = true, arabicLabel = "محجوز لعميل"),
    UNDER_MAINTENANCE(isActiveInStock = true, arabicLabel = "قيد الصيانة"),
    RESERVED_MAINTENANCE(isActiveInStock = true, arabicLabel = "محجوز للصيانة"),
    SOLD(isActiveInStock = false, arabicLabel = "مباع"),
    RETURNED(isActiveInStock = true, arabicLabel = "مرتجع للمخزن"),
    RETURNED_TO_SUPPLIER(isActiveInStock = false, arabicLabel = "مرتجع للمورد"),
    DAMAGED(isActiveInStock = false, arabicLabel = "تالف")
}

@Entity(
    tableName = "customers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "phone"], unique = true),
        Index(value = ["shopId", "fullName"]),
        Index(value = ["syncState"])
    ]
)
data class CustomerEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val fullName: String,
    val phone: String,
    val secondaryPhone: String? = null,
    val nationalId: String? = null,
    val address: String = "",
    val creditLimitMinor: Long = 0L,
    val defaultCreditDays: Int = 30,
    val notes: String = "",
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "suppliers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "phone"], unique = true),
        Index(value = ["shopId", "companyName"]),
        Index(value = ["syncState"])
    ]
)
data class SupplierEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val companyName: String,
    val contactName: String = "",
    val phone: String,
    val address: String = "",
    val taxNumber: String? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "nameArabic"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val nameArabic: String,
    val parentCategoryId: String? = null,
    val sortOrder: Int = 0,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "brands",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "name"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class BrandEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val name: String,
    val countryOfOrigin: String = "",
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

/**
 * Product catalog definition (Requirement 5, 12, 13, 14).
 * Notice: Stock quantities and weighted average cost are never arbitrarily overwritten;
 * they are governed by InventoryMovementEntity and verified in InventoryEntity.
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["localId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["localId"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["shopId", "sku"], unique = true),
        Index(value = ["shopId", "barcode"]),
        Index(value = ["shopId", "categoryId"]),
        Index(value = ["shopId", "brandId"]),
        Index(value = ["syncState"])
    ]
)
data class ProductEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val categoryId: String,
    val brandId: String? = null,
    val sku: String,
    val barcode: String? = null,
    val nameArabic: String,
    val modelNumber: String = "",
    val isSerialized: Boolean = false, // True for mobile phones / tablets with IMEI
    val defaultSellingPriceMinor: Long,
    val minSellingPriceMinor: Long = 0L,
    val wholesalePriceMinor: Long = 0L,
    val specialCustomerPriceMinor: Long = 0L,
    val maxDiscountMinor: Long = 0L,
    val promotionalPriceMinor: Long = 0L,
    val isPromotionActive: Boolean = false,
    val reorderThresholdQty: Int = 2,
    val reorderPointQty: Int = 5,
    val warrantyMonths: Int = 12,
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
) {
    val minAllowedPriceMinor: Long get() = minSellingPriceMinor
}

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["shopId", "variantSku"], unique = true),
        Index(value = ["productId"])
    ]
)
data class ProductVariantEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val productId: String,
    val variantSku: String,
    val colorName: String = "",
    val storageCapacity: String = "", // e.g., "256GB / 8GB RAM"
    val sellingPriceMinor: Long,
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Serial / IMEI registry (Requirement 6 & 14).
 * `activeGuardKey` is non-null ("$shopId:$serialOrImei") ONLY while the device is active in stock,
 * and null when sold/returned, allowing a unique index that strictly prevents duplicate active serial numbers
 * while still allowing historical re-purchase if a phone is bought back later!
 */
@Entity(
    tableName = "serial_numbers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["activeGuardKey"], unique = true),
        Index(value = ["shopId", "serialOrImei"]),
        Index(value = ["productId"]),
        Index(value = ["status"])
    ]
)
data class SerialNumberEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val productId: String,
    val variantId: String? = null,
    val serialOrImei: String,
    val secondaryImei: String? = null,
    val status: SerialLifecycleStatus = SerialLifecycleStatus.IN_STOCK,
    val activeGuardKey: String? = "$shopId:$serialOrImei",
    val acquisitionCostMinor: Long,
    val lastMovementId: String? = null,
    val currentCustomerId: String? = null,
    val notes: String = "",
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Movement-verified Inventory valuation state per Product (Requirement 12 & 13).
 * Updated ONLY inside atomic InventoryMovement transactions; reproducible from `inventory_movements`.
 */
@Entity(
    tableName = "inventory_records",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "productId"], unique = true),
        Index(value = ["productId"])
    ]
)
data class InventoryEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val productId: String,
    val weightedAvgCostMinor: Long = 0L,
    val lastMovementId: String? = null,
    val lastCalculatedAt: Long,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Immutable Inventory Movement Ledger (Requirement 12 & 13).
 * Every stock change MUST record an InventoryMovementEntity with full cost & WAC trail.
 */
@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["performedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "productId", "timestamp"]),
        Index(value = ["shopId", "movementType"]),
        Index(value = ["serialNumberId"]),
        Index(value = ["referenceType", "referenceId"]),
        Index(value = ["performedByUserId"]),
        Index(value = ["syncState"])
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val productId: String,
    val variantId: String? = null,
    val serialNumberId: String? = null,
    val movementType: InventoryMovementType,
    val quantity: Int, // Strictly > 0 (enforced by DB trigger & domain validation)
    val signedQuantityDelta: Int, // +quantity for inbound, -quantity for outbound
    val quantityBefore: Int,
    val quantityAfter: Int, // Strictly >= 0 (no impossible negative stock)
    val unitCostMinor: Long, // Inbound acquisition cost or outbound WAC cost at time of movement
    val totalCostMinor: Long,
    val weightedAvgCostBeforeMinor: Long,
    val weightedAvgCostAfterMinor: Long,
    val reason: String,
    val referenceType: String, // PURCHASE, SALE, MAINTENANCE, ADJUSTMENT, TRANSFER, RETURN
    val referenceId: String,
    val performedByUserId: String,
    val deviceId: String,
    val timestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Immutable Price History Audit Trail (V4 Part 1 Section 4 — Advanced Pricing).
 * Records every change to Retail, Wholesale, Special Customer, Minimum Allowed, Promotional, or Max Discount prices
 * with Old Price, New Price, User, Date/Time, and Reason.
 */
@Entity(
    tableName = "price_history",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["changedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "productId", "changedAt"]),
        Index(value = ["productId"]),
        Index(value = ["changedByUserId"]),
        Index(value = ["syncState"])
    ]
)
data class PriceHistoryEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val productId: String,
    val priceType: String, // RETAIL, WHOLESALE, SPECIAL_CUSTOMER, MIN_ALLOWED, PROMOTIONAL, MAX_DISCOUNT
    val oldPriceMinor: Long,
    val newPriceMinor: Long,
    val changedByUserId: String,
    val changedByRoleCode: String = "OWNER",
    val reasonArabic: String,
    val deviceId: String,
    val changedAt: Long,
    val syncState: SyncState = SyncState.PENDING
)


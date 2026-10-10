package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.database.entities.BrandEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.InventoryEntity
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.ProductEntity
import com.example.database.entities.ProductVariantEntity
import com.example.database.entities.SerialLifecycleStatus
import com.example.database.entities.SerialNumberEntity
import com.example.database.entities.SupplierEntity
import kotlinx.coroutines.flow.Flow

/**
 * Projection combining Product catalog metadata with movement-derived stock quantity and Weighted Average Cost.
 */
data class ProductInventoryProjection(
    val productId: String,
    val sku: String,
    val barcode: String?,
    val nameArabic: String,
    val categoryId: String,
    val brandId: String?,
    val isSerialized: Boolean,
    val defaultSellingPriceMinor: Long,
    val minSellingPriceMinor: Long,
    val wholesalePriceMinor: Long = 0L,
    val specialCustomerPriceMinor: Long = 0L,
    val maxDiscountMinor: Long = 0L,
    val promotionalPriceMinor: Long = 0L,
    val isPromotionActive: Boolean = false,
    val reorderThresholdQty: Int,
    val reorderPointQty: Int = 5,
    val warrantyMonths: Int,
    val isActive: Boolean,
    val derivedStockQty: Int,
    val weightedAvgCostMinor: Long
) {
    val quantityOnHand: Int get() = derivedStockQty
    val retailPriceMinor: Long get() = defaultSellingPriceMinor

    val stockValueMinor: Long
        get() = derivedStockQty.coerceAtLeast(0).toLong() * weightedAvgCostMinor

    val effectiveRetailPriceMinor: Long
        get() = if (isPromotionActive && promotionalPriceMinor > 0L) promotionalPriceMinor else defaultSellingPriceMinor

    val isLowStockAlert: Boolean
        get() = derivedStockQty <= reorderThresholdQty

    val isReorderRecommended: Boolean
        get() = derivedStockQty <= reorderPointQty

    val stockStatusArabic: String
        get() = when {
            derivedStockQty <= 0 -> "نفد المخزون"
            derivedStockQty <= reorderThresholdQty -> "مخزون منخفض حرج"
            derivedStockQty <= reorderPointQty -> "يُنصح بإعادة الطلب"
            else -> "متوفر"
        }
}

/**
 * Customer projection with ledger-derived outstanding receivable balance and sales totals (Requirement 2 & 29).
 */
data class CustomerBalanceProjection(
    val customerId: String,
    val fullName: String,
    val phone: String,
    val nationalId: String?,
    val address: String,
    val creditLimitMinor: Long,
    val defaultCreditDays: Int = 30,
    val notes: String,
    val isActive: Boolean,
    val outstandingBalanceMinor: Long,
    val totalSalesMinor: Long,
    val totalPaymentsMinor: Long
)

/**
 * Supplier projection with ledger-derived outstanding payable balance and purchase totals (Requirement 3 & 29).
 */
data class SupplierBalanceProjection(
    val supplierId: String,
    val companyName: String,
    val contactName: String,
    val phone: String,
    val address: String,
    val taxNumber: String?,
    val notes: String,
    val isActive: Boolean,
    val outstandingPayableMinor: Long,
    val totalPurchasesMinor: Long,
    val totalPaymentsMinor: Long
)

@Dao
interface CatalogInventoryDao {
    @Query("SELECT * FROM customers WHERE shopId = :shopId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeCustomers(shopId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE shopId = :shopId AND isDeleted = 0")
    suspend fun getAllCustomers(shopId: String): List<CustomerEntity>

    @Query(
        """
        SELECT
            c.localId AS customerId,
            c.fullName AS fullName,
            c.phone AS phone,
            c.nationalId AS nationalId,
            c.address AS address,
            c.creditLimitMinor AS creditLimitMinor,
            c.defaultCreditDays AS defaultCreditDays,
            c.notes AS notes,
            c.isActive AS isActive,
            COALESCE((
                SELECT SUM(tl.signedImpactMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId 
                  AND tl.accountType = 'EXTERNAL_CUSTOMER' 
                  AND tl.accountId = c.localId
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND (ft.relatedEntityType IS NULL OR ft.relatedEntityType != 'MAINTENANCE_DEPOSIT')
            ), 0) AS outstandingBalanceMinor,
            COALESCE((
                SELECT SUM(s.totalAmountMinor)
                FROM sales s
                WHERE s.shopId = :shopId AND s.customerId = c.localId AND s.status != 'REFUNDED'
            ), 0) AS totalSalesMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'EXTERNAL_CUSTOMER'
                  AND tl.accountId = c.localId
                  AND tl.direction = 'OUTFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
            ), 0) AS totalPaymentsMinor
        FROM customers c
        WHERE c.shopId = :shopId AND c.isDeleted = 0
        ORDER BY c.createdAt DESC
        """
    )
    fun observeCustomerBalanceProjections(shopId: String): Flow<List<CustomerBalanceProjection>>

    @Query("SELECT * FROM customers WHERE localId = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Upsert
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM suppliers WHERE shopId = :shopId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeSuppliers(shopId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE shopId = :shopId AND isDeleted = 0")
    suspend fun getAllSuppliers(shopId: String): List<SupplierEntity>

    @Query(
        """
        SELECT
            sup.localId AS supplierId,
            sup.companyName AS companyName,
            sup.contactName AS contactName,
            sup.phone AS phone,
            sup.address AS address,
            sup.taxNumber AS taxNumber,
            sup.notes AS notes,
            sup.isActive AS isActive,
            COALESCE((
                SELECT SUM(p.totalAmountMinor - p.paidAmountMinor)
                FROM purchases p
                WHERE p.shopId = :shopId AND p.supplierId = sup.localId
            ), 0) - COALESCE((
                SELECT SUM(ft.amountMinor)
                FROM financial_transactions ft
                WHERE ft.shopId = :shopId AND ft.supplierId = sup.localId
                  AND ft.status = 'POSTED' AND ft.relatedEntityType = 'SUPPLIER_PAYMENT'
            ), 0) - COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId AND tl.accountType = 'EXTERNAL_SUPPLIER' AND tl.accountId = sup.localId
                  AND tl.direction = 'INFLOW'
                  AND ft.status = 'POSTED' AND ft.relatedEntityType = 'PURCHASE_RETURN'
            ), 0) AS outstandingPayableMinor,
            COALESCE((
                SELECT SUM(p.totalAmountMinor)
                FROM purchases p
                WHERE p.shopId = :shopId AND p.supplierId = sup.localId
            ), 0) AS totalPurchasesMinor,
            COALESCE((
                SELECT SUM(ft.amountMinor)
                FROM financial_transactions ft
                WHERE ft.shopId = :shopId AND ft.supplierId = sup.localId
                  AND ft.status = 'POSTED' AND ft.transactionType IN ('PURCHASE', 'CASH_OUT', 'TRANSFER')
                  AND (ft.relatedEntityType = 'PURCHASE' OR ft.relatedEntityType = 'SUPPLIER_PAYMENT')
            ), 0) AS totalPaymentsMinor
        FROM suppliers sup
        WHERE sup.shopId = :shopId AND sup.isDeleted = 0
        ORDER BY sup.createdAt DESC
        """
    )
    fun observeSupplierBalanceProjections(shopId: String): Flow<List<SupplierBalanceProjection>>

    @Query(
        """
        SELECT
            COALESCE((
                SELECT SUM(p.totalAmountMinor - p.paidAmountMinor)
                FROM purchases p
                WHERE p.shopId = :shopId AND p.supplierId = :supplierId
            ), 0) - COALESCE((
                SELECT SUM(ft.amountMinor)
                FROM financial_transactions ft
                WHERE ft.shopId = :shopId AND ft.supplierId = :supplierId
                  AND ft.status = 'POSTED' AND ft.relatedEntityType = 'SUPPLIER_PAYMENT'
            ), 0) - COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId AND tl.accountType = 'EXTERNAL_SUPPLIER' AND tl.accountId = :supplierId
                  AND tl.direction = 'INFLOW'
                  AND ft.status = 'POSTED' AND ft.relatedEntityType = 'PURCHASE_RETURN'
            ), 0)
        """
    )
    suspend fun getDerivedSupplierPayableMinor(shopId: String, supplierId: String): Long

    @Query("SELECT * FROM suppliers WHERE localId = :supplierId LIMIT 1")
    suspend fun getSupplierById(supplierId: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: SupplierEntity)

    @Upsert
    suspend fun upsertSuppliers(suppliers: List<SupplierEntity>)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM categories WHERE shopId = :shopId AND isDeleted = 0 ORDER BY sortOrder ASC, nameArabic ASC")
    fun observeCategories(shopId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE shopId = :shopId")
    suspend fun getAllCategories(shopId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity)

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM brands WHERE shopId = :shopId AND isDeleted = 0 ORDER BY name ASC")
    fun observeBrands(shopId: String): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE shopId = :shopId")
    suspend fun getAllBrands(shopId: String): List<BrandEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrand(brand: BrandEntity)

    @Upsert
    suspend fun upsertBrands(brands: List<BrandEntity>)

    @Query("SELECT * FROM inventory_records WHERE shopId = :shopId")
    suspend fun getAllInventoryRecords(shopId: String): List<InventoryEntity>

    @Upsert
    suspend fun upsertInventoryRecords(records: List<InventoryEntity>)

    @Query("SELECT * FROM products WHERE shopId = :shopId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeProducts(shopId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE shopId = :shopId AND isDeleted = 0")
    suspend fun getAllProducts(shopId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE localId = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE shopId = :shopId AND (sku = :code OR barcode = :code) AND isDeleted = 0 LIMIT 1")
    suspend fun getProductBySkuOrBarcode(shopId: String, code: String): ProductEntity?

    @Query("SELECT * FROM products WHERE shopId = :shopId AND UPPER(sku) = UPPER(:sku) LIMIT 1")
    suspend fun getProductBySku(shopId: String, sku: String): ProductEntity?

    @Query("SELECT * FROM products WHERE shopId = :shopId AND barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(shopId: String, barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity)

    @Upsert
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVariant(variant: ProductVariantEntity)

    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    fun observeVariantsForProduct(productId: String): Flow<List<ProductVariantEntity>>

    /**
     * Derives current stock quantity strictly from `inventory_movements` and combines with WAC from `inventory_records`.
     */
    @Query(
        """
        SELECT
            p.localId AS productId,
            p.sku AS sku,
            p.barcode AS barcode,
            p.nameArabic AS nameArabic,
            p.categoryId AS categoryId,
            p.brandId AS brandId,
            p.isSerialized AS isSerialized,
            p.defaultSellingPriceMinor AS defaultSellingPriceMinor,
            p.minSellingPriceMinor AS minSellingPriceMinor,
            p.wholesalePriceMinor AS wholesalePriceMinor,
            p.specialCustomerPriceMinor AS specialCustomerPriceMinor,
            p.maxDiscountMinor AS maxDiscountMinor,
            p.promotionalPriceMinor AS promotionalPriceMinor,
            p.isPromotionActive AS isPromotionActive,
            p.reorderThresholdQty AS reorderThresholdQty,
            p.reorderPointQty AS reorderPointQty,
            p.warrantyMonths AS warrantyMonths,
            p.isActive AS isActive,
            COALESCE((SELECT SUM(m.signedQuantityDelta) FROM inventory_movements m WHERE m.productId = p.localId AND m.shopId = :shopId), 0) AS derivedStockQty,
            COALESCE((SELECT ir.weightedAvgCostMinor FROM inventory_records ir WHERE ir.productId = p.localId AND ir.shopId = :shopId LIMIT 1), 0) AS weightedAvgCostMinor
        FROM products p
        WHERE p.shopId = :shopId AND p.isDeleted = 0 AND p.isActive = 1
        ORDER BY p.createdAt DESC
        """
    )
    fun observeProductInventoryProjections(shopId: String): Flow<List<ProductInventoryProjection>>

    @Query(
        """
        SELECT
            p.localId AS productId,
            p.sku AS sku,
            p.barcode AS barcode,
            p.nameArabic AS nameArabic,
            p.categoryId AS categoryId,
            p.brandId AS brandId,
            p.isSerialized AS isSerialized,
            p.defaultSellingPriceMinor AS defaultSellingPriceMinor,
            p.minSellingPriceMinor AS minSellingPriceMinor,
            p.wholesalePriceMinor AS wholesalePriceMinor,
            p.specialCustomerPriceMinor AS specialCustomerPriceMinor,
            p.maxDiscountMinor AS maxDiscountMinor,
            p.promotionalPriceMinor AS promotionalPriceMinor,
            p.isPromotionActive AS isPromotionActive,
            p.reorderThresholdQty AS reorderThresholdQty,
            p.reorderPointQty AS reorderPointQty,
            p.warrantyMonths AS warrantyMonths,
            p.isActive AS isActive,
            COALESCE((SELECT SUM(m.signedQuantityDelta) FROM inventory_movements m WHERE m.productId = p.localId AND m.shopId = :shopId), 0) AS derivedStockQty,
            COALESCE((SELECT ir.weightedAvgCostMinor FROM inventory_records ir WHERE ir.productId = p.localId AND ir.shopId = :shopId LIMIT 1), 0) AS weightedAvgCostMinor
        FROM products p
        WHERE p.shopId = :shopId AND p.isDeleted = 0 AND p.isActive = 1
        ORDER BY p.createdAt DESC
        """
    )
    suspend fun getProductInventoryProjections(shopId: String): List<ProductInventoryProjection>

    /**
     * Counts low-stock products whose movement-derived stock is <= reorderThresholdQty.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT
                p.localId,
                p.reorderThresholdQty,
                COALESCE((SELECT SUM(m.signedQuantityDelta) FROM inventory_movements m WHERE m.productId = p.localId AND m.shopId = :shopId), 0) AS currentQty
            FROM products p
            WHERE p.shopId = :shopId AND p.isDeleted = 0 AND p.isActive = 1
        ) WHERE currentQty <= reorderThresholdQty
        """
    )
    fun observeLowStockProductCount(shopId: String): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(signedQuantityDelta), 0)
        FROM inventory_movements
        WHERE shopId = :shopId AND productId = :productId
        """
    )
    suspend fun getDerivedStockQuantity(shopId: String, productId: String): Int

    @Query("SELECT * FROM inventory_records WHERE shopId = :shopId AND productId = :productId LIMIT 1")
    suspend fun getInventoryRecord(shopId: String, productId: String): InventoryEntity?

    @Upsert
    suspend fun upsertInventoryRecord(record: InventoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryMovement(movement: InventoryMovementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertInventoryMovements(movements: List<InventoryMovementEntity>)

    @Query("SELECT * FROM inventory_movements WHERE shopId = :shopId AND productId = :productId ORDER BY timestamp ASC, createdAt ASC")
    suspend fun getMovementsForProductChronological(shopId: String, productId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE shopId = :shopId ORDER BY timestamp ASC, createdAt ASC")
    suspend fun getAllMovementsChronological(shopId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE shopId = :shopId ORDER BY timestamp DESC, createdAt DESC LIMIT :limit")
    fun observeAllInventoryMovements(shopId: String, limit: Int = 200): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM serial_numbers WHERE shopId = :shopId ORDER BY updatedAt DESC")
    fun observeSerialNumbers(shopId: String): Flow<List<SerialNumberEntity>>

    @Query("SELECT * FROM serial_numbers WHERE shopId = :shopId")
    suspend fun getAllSerialNumbers(shopId: String): List<SerialNumberEntity>

    @Query("SELECT * FROM serial_numbers WHERE shopId = :shopId AND productId = :productId AND status = :status ORDER BY createdAt ASC")
    fun observeAvailableSerialsForProduct(
        shopId: String,
        productId: String,
        status: SerialLifecycleStatus = SerialLifecycleStatus.IN_STOCK
    ): Flow<List<SerialNumberEntity>>

    @Query("SELECT * FROM serial_numbers WHERE localId = :serialId LIMIT 1")
    suspend fun getSerialById(serialId: String): SerialNumberEntity?

    @Query("SELECT * FROM serial_numbers WHERE shopId = :shopId AND serialOrImei = :serialOrImei LIMIT 1")
    suspend fun getSerialByImeiAnyStatus(shopId: String, serialOrImei: String): SerialNumberEntity?

    @Query("SELECT * FROM serial_numbers WHERE shopId = :shopId AND serialOrImei = :serialOrImei AND activeGuardKey IS NOT NULL LIMIT 1")
    suspend fun getActiveSerialByImei(shopId: String, serialOrImei: String): SerialNumberEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSerialNumber(serialNumber: SerialNumberEntity)

    @Upsert
    suspend fun upsertSerialNumbers(serials: List<SerialNumberEntity>)

    @Update
    suspend fun updateSerialNumber(serialNumber: SerialNumberEntity)

    @Query("SELECT * FROM inventory_movements WHERE shopId = :shopId AND serialNumberId = :serialNumberId ORDER BY timestamp ASC, createdAt ASC")
    suspend fun getMovementsForSerialChronological(shopId: String, serialNumberId: String): List<InventoryMovementEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPriceHistory(entry: PriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPriceHistoryList(entries: List<PriceHistoryEntity>)

    @Upsert
    suspend fun upsertPriceHistory(entries: List<PriceHistoryEntity>)

    @Query("SELECT * FROM price_history WHERE shopId = :shopId AND productId = :productId ORDER BY changedAt DESC")
    fun observePriceHistoryForProduct(shopId: String, productId: String): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE shopId = :shopId AND productId = :productId ORDER BY changedAt DESC")
    suspend fun getPriceHistoryForProduct(shopId: String, productId: String): List<PriceHistoryEntity>

    @Query("SELECT * FROM price_history WHERE shopId = :shopId ORDER BY changedAt DESC LIMIT :limit")
    fun observeRecentPriceHistory(shopId: String, limit: Int = 200): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE shopId = :shopId ORDER BY changedAt DESC")
    suspend fun getAllPriceHistory(shopId: String): List<PriceHistoryEntity>
}

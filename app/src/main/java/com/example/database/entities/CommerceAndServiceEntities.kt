package com.example.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.SyncState

enum class MaintenanceStatus(val arabicLabel: String, val isPendingWork: Boolean, val stageOrder: Int = 1) {
    RECEIVED("استلام الجهاز", true, 1),
    DIAGNOSING("فحص", true, 2),
    COST_ESTIMATED("تقدير تكلفة", true, 3),
    WAITING_APPROVAL("موافقة العميل", true, 4),
    IN_REPAIR("قيد الإصلاح", true, 5),
    WAITING_PARTS("قطع الغيار", true, 6),
    TESTING("اختبار", true, 7),
    READY_FOR_PICKUP("جاهز", true, 8),
    CUSTOMER_NOTIFIED("إخطار العميل", true, 9),
    PAYMENT_COLLECTED("تحصيل", true, 10),
    DELIVERED("تسليم", false, 11),
    CLOSED("مغلق ومكتمل", false, 12),
    CANCELLED("ملغي (تم رد العربون إن وجد)", false, 99),
    UNREPAIRABLE("تعذر الإصلاح (مرتجع)", false, 100);

    companion object {
        val INSPECTION: MaintenanceStatus get() = DIAGNOSING
        val ESTIMATE: MaintenanceStatus get() = COST_ESTIMATED
        val CUSTOMER_APPROVAL: MaintenanceStatus get() = WAITING_APPROVAL
        val READY: MaintenanceStatus get() = READY_FOR_PICKUP

        val workflowPipeline: List<MaintenanceStatus> = listOf(
            RECEIVED,
            DIAGNOSING,
            WAITING_APPROVAL,
            IN_REPAIR,
            WAITING_PARTS,
            TESTING,
            READY_FOR_PICKUP,
            CUSTOMER_NOTIFIED,
            PAYMENT_COLLECTED,
            DELIVERED,
            CLOSED
        )

        val v4FullWorkflowPipeline: List<MaintenanceStatus> = listOf(
            RECEIVED,
            DIAGNOSING,
            COST_ESTIMATED,
            WAITING_APPROVAL,
            IN_REPAIR,
            WAITING_PARTS,
            TESTING,
            READY_FOR_PICKUP,
            CUSTOMER_NOTIFIED,
            PAYMENT_COLLECTED,
            DELIVERED,
            CLOSED
        )
    }
}

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["localId"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "invoiceNumber"], unique = true),
        Index(value = ["shopId", "supplierId"]),
        Index(value = ["createdByUserId"]),
        Index(value = ["ledgerTransactionId"]),
        Index(value = ["syncState"])
    ]
)
data class PurchaseEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val supplierId: String,
    val invoiceNumber: String,
    val currency: String = "EGP",
    val subtotalMinor: Long,
    val discountMinor: Long = 0L,
    val taxMinor: Long = 0L,
    val totalAmountMinor: Long,
    val paidAmountMinor: Long,
    val paymentAccountType: String, // CASH_DRAWER or WALLET
    val paymentAccountId: String,
    val ledgerTransactionId: String,
    val notes: String = "",
    val createdByUserId: String,
    val deviceId: String,
    val purchaseTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["localId"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["purchaseId"]),
        Index(value = ["productId"]),
        Index(value = ["serialNumberId"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val purchaseId: String,
    val productId: String,
    val variantId: String? = null,
    val serialNumberId: String? = null,
    val serialOrImei: String? = null,
    val quantity: Int,
    val unitCostMinor: Long,
    val lineTotalMinor: Long,
    val inventoryMovementId: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["localId"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "invoiceNumber"], unique = true),
        Index(value = ["shopId", "saleTimestamp"]),
        Index(value = ["customerId"]),
        Index(value = ["createdByUserId"]),
        Index(value = ["ledgerTransactionId"]),
        Index(value = ["syncState"])
    ]
)
data class SaleEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val customerId: String? = null,
    val invoiceNumber: String,
    val currency: String = "EGP",
    val subtotalMinor: Long,
    val discountMinor: Long = 0L,
    val taxMinor: Long = 0L,
    val totalAmountMinor: Long,
    val paidAmountMinor: Long = totalAmountMinor,
    val totalCostOfGoodsMinor: Long,
    val grossProfitMinor: Long,
    val paymentAccountType: String, // CASH_DRAWER or WALLET
    val paymentAccountId: String,
    val ledgerTransactionId: String,
    val status: String = "COMPLETED", // COMPLETED, PARTIALLY_PAID, CREDIT, PARTIALLY_REFUNDED, REFUNDED
    val dueDateTimestamp: Long? = null,
    val shiftId: String? = null,
    val pricingTierUsed: String = "RETAIL",
    val notes: String = "",
    val createdByUserId: String,
    val deviceId: String,
    val saleTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
) {
    val discountAmountMinor: Long get() = discountMinor
    val totalCogsMinor: Long get() = totalCostOfGoodsMinor
}

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["localId"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["saleId"]),
        Index(value = ["productId"]),
        Index(value = ["serialNumberId"])
    ]
)
data class SaleItemEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val saleId: String,
    val productId: String,
    val variantId: String? = null,
    val serialNumberId: String? = null,
    val serialOrImei: String? = null,
    val quantity: Int,
    val returnedQuantity: Int = 0,
    val unitSellingPriceMinor: Long,
    val unitCostAtSaleMinor: Long, // WAC at moment of sale
    val discountMinor: Long = 0L,
    val lineTotalMinor: Long,
    val inventoryMovementId: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "maintenance_orders",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["localId"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["receivedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "ticketNumber"], unique = true),
        Index(value = ["shopId", "status"]),
        Index(value = ["customerId"]),
        Index(value = ["receivedByUserId"]),
        Index(value = ["syncState"])
    ]
)
data class MaintenanceOrderEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val ticketNumber: String,
    val customerId: String,
    val customerNameSnapshot: String,
    val customerPhoneSnapshot: String,
    val deviceBrand: String,
    val deviceModel: String,
    val deviceImeiOrSerial: String = "",
    val deviceSerial: String = "",
    val deviceConditionArabic: String = "",
    val accessoriesReceivedArabic: String = "",
    val faultDescription: String,
    val diagnosticNotes: String = "",
    val repairActionsArabic: String = "",
    val warrantyNotesArabic: String = "",
    val estimatedCostMinor: Long,
    val depositPaidMinor: Long = 0L,
    val laborFeeMinor: Long = 0L,
    val partsCostMinor: Long = 0L,
    val finalTotalMinor: Long = 0L,
    val status: MaintenanceStatus = MaintenanceStatus.RECEIVED,
    val assignedTechnicianId: String? = null,
    val receivedByUserId: String,
    val completedLedgerTransactionId: String? = null,
    val isCustomerApproved: Boolean = false,
    val customerApprovedAt: Long? = null,
    val isCustomerNotified: Boolean = false,
    val customerNotifiedAt: Long? = null,
    val isTestingPassed: Boolean = false,
    val expectedDeliveryAt: Long? = null,
    val deviceId: String,
    val receivedAt: Long,
    val deliveredAt: Long? = null,
    val closedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "maintenance_parts",
    foreignKeys = [
        ForeignKey(
            entity = MaintenanceOrderEntity::class,
            parentColumns = ["localId"],
            childColumns = ["maintenanceOrderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["localId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["maintenanceOrderId"]),
        Index(value = ["productId"])
    ]
)
data class MaintenancePartEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val maintenanceOrderId: String,
    val productId: String,
    val quantity: Int,
    val unitCostMinor: Long,
    val unitChargeMinor: Long,
    val inventoryMovementId: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["recordedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "expenseTimestamp"]),
        Index(value = ["shopId", "categoryName"]),
        Index(value = ["recordedByUserId"]),
        Index(value = ["ledgerTransactionId"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val categoryName: String, // إيجار، كهرباء، رواتب، أدوات صيانة، نثريات
    val title: String,
    val amountMinor: Long,
    val currency: String = "EGP",
    val sourceAccountType: String, // CASH_DRAWER or WALLET
    val sourceAccountId: String,
    val ledgerTransactionId: String,
    val notes: String = "",
    val recordedByUserId: String,
    val deviceId: String,
    val expenseTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "refunds",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["localId"],
            childColumns = ["originalSaleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "originalSaleId"]),
        Index(value = ["ledgerTransactionId"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class RefundEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val originalSaleId: String,
    val refundNumber: String,
    val refundAmountMinor: Long,
    val currency: String = "EGP",
    val reason: String,
    val returnType: String = "FULL_RETURN", // FULL_RETURN, PARTIAL_RETURN, EXCHANGE, UNLINKED_OVERRIDE
    val settlementMode: String = "CASH_REFUND", // CASH_REFUND, CUSTOMER_CREDIT, WALLET_REFUND, EXCHANGE_OFFSET
    val productCondition: String = "RESTOCKABLE", // RESTOCKABLE, DAMAGED_SCRAP, MIXED
    val replacementSaleId: String? = null,
    val priceDifferenceMinor: Long = 0L,
    val returnedItemsSnapshotJson: String = "[]",
    val refundAccountType: String,
    val refundAccountId: String,
    val ledgerTransactionId: String,
    val approvedByUserId: String,
    val deviceId: String,
    val refundTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["executedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "transferTimestamp"]),
        Index(value = ["executedByUserId"]),
        Index(value = ["ledgerTransactionId"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class TransferEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val transferReference: String,
    val transferKind: String, // WALLET_SEND, WALLET_RECEIVE, CASH_TO_WALLET, WALLET_TO_CASH, INTERNAL
    val sourceAccountType: String,
    val sourceAccountId: String,
    val destinationAccountType: String,
    val destinationAccountId: String,
    val customerPhoneOrAccount: String = "",
    val principalAmountMinor: Long,
    val providerFeeMinor: Long = 0L,
    val shopCommissionMinor: Long = 0L,
    val netSettlementMinor: Long,
    val currency: String = "EGP",
    val ledgerTransactionId: String,
    val notes: String = "",
    val executedByUserId: String,
    val deviceId: String,
    val transferTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

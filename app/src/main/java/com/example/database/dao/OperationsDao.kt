package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.PendingTransactionEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SmsMessageEntity
import com.example.database.entities.SmsParsingResultEntity
import com.example.database.entities.TransferEntity
import kotlinx.coroutines.flow.Flow

/**
 * Combined projection for the SMS Review Center (`مراجعة رسائل المعاملات`, Requirement 26).
 */
data class SmsReviewItemProjection(
    val smsId: String,
    val senderAddress: String,
    val messageBody: String,
    val messageHashSha256: String,
    val receivedTimestamp: Long,
    val isProcessed: Boolean,
    val parsingResultId: String?,
    val detectedProviderCode: String?,
    val detectedOperationType: String?,
    val extractedAmountMinor: Long?,
    val extractedFeeMinor: Long?,
    val extractedCounterpartyPhone: String?,
    val extractedReference: String?,
    val confidenceBps: Int?,
    val pendingTransactionId: String?,
    val pendingStatus: String?,
    val targetWalletId: String?,
    val confirmedLedgerTransactionId: String?
)

@Dao
interface OperationsDao {
    @Query("SELECT * FROM purchases WHERE shopId = :shopId ORDER BY purchaseTimestamp DESC")
    fun observePurchases(shopId: String): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE shopId = :shopId ORDER BY purchaseTimestamp ASC")
    suspend fun getAllPurchases(shopId: String): List<PurchaseEntity>

    @Upsert
    suspend fun upsertPurchases(purchases: List<PurchaseEntity>)

    @Query("SELECT * FROM purchase_items WHERE shopId = :shopId")
    suspend fun getAllPurchaseItems(shopId: String): List<PurchaseItemEntity>

    @Upsert
    suspend fun upsertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM sales WHERE shopId = :shopId ORDER BY saleTimestamp ASC")
    suspend fun getAllSales(shopId: String): List<SaleEntity>

    @Upsert
    suspend fun upsertSales(sales: List<SaleEntity>)

    @Query("SELECT * FROM sale_items WHERE shopId = :shopId")
    suspend fun getAllSaleItems(shopId: String): List<SaleItemEntity>

    @Upsert
    suspend fun upsertSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM maintenance_orders WHERE shopId = :shopId")
    suspend fun getAllMaintenanceOrders(shopId: String): List<MaintenanceOrderEntity>

    @Upsert
    suspend fun upsertMaintenanceOrders(orders: List<MaintenanceOrderEntity>)

    @Query("SELECT * FROM maintenance_parts WHERE shopId = :shopId")
    suspend fun getAllMaintenanceParts(shopId: String): List<MaintenancePartEntity>

    @Upsert
    suspend fun upsertMaintenanceParts(parts: List<MaintenancePartEntity>)

    @Query("SELECT * FROM expenses WHERE shopId = :shopId")
    suspend fun getAllExpenses(shopId: String): List<ExpenseEntity>

    @Upsert
    suspend fun upsertExpenses(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM refunds WHERE shopId = :shopId")
    suspend fun getAllRefunds(shopId: String): List<RefundEntity>

    @Upsert
    suspend fun upsertRefunds(refunds: List<RefundEntity>)

    @Query("SELECT * FROM transfers WHERE shopId = :shopId")
    suspend fun getAllTransfers(shopId: String): List<TransferEntity>

    @Upsert
    suspend fun upsertTransfers(transfers: List<TransferEntity>)

    @Query("SELECT * FROM sms_messages WHERE shopId = :shopId")
    suspend fun getAllSmsMessages(shopId: String): List<SmsMessageEntity>

    @Upsert
    suspend fun upsertSmsMessages(messages: List<SmsMessageEntity>)

    @Query("SELECT * FROM sms_parsing_results WHERE shopId = :shopId")
    suspend fun getAllSmsParsingResults(shopId: String): List<SmsParsingResultEntity>

    @Upsert
    suspend fun upsertSmsParsingResults(results: List<SmsParsingResultEntity>)

    @Query("SELECT * FROM pending_transactions WHERE shopId = :shopId")
    suspend fun getAllPendingTransactions(shopId: String): List<PendingTransactionEntity>

    @Upsert
    suspend fun upsertPendingTransactions(pending: List<PendingTransactionEntity>)

    @Query("SELECT * FROM purchases WHERE localId = :purchaseId LIMIT 1")
    suspend fun getPurchaseById(purchaseId: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE shopId = :shopId AND supplierId = :supplierId ORDER BY purchaseTimestamp DESC")
    suspend fun getPurchasesForSupplier(shopId: String, supplierId: String): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: String): List<PurchaseItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM sales WHERE shopId = :shopId ORDER BY saleTimestamp DESC")
    fun observeSales(shopId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sale_items WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun observeAllSaleItems(shopId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun observeAllPurchaseItems(shopId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM maintenance_parts WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun observeAllMaintenanceParts(shopId: String): Flow<List<MaintenancePartEntity>>

    @Query("SELECT * FROM sales WHERE shopId = :shopId AND customerId = :customerId ORDER BY saleTimestamp DESC")
    fun observeSalesForCustomer(shopId: String, customerId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE shopId = :shopId AND customerId = :customerId ORDER BY saleTimestamp DESC")
    suspend fun getSalesForCustomer(shopId: String, customerId: String): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE localId = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: String): SaleEntity?

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Update
    suspend fun updateSaleItem(item: SaleItemEntity)

    @Update
    suspend fun updateSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: String): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM maintenance_orders WHERE shopId = :shopId ORDER BY receivedAt DESC")
    fun observeMaintenanceOrders(shopId: String): Flow<List<MaintenanceOrderEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM maintenance_orders
        WHERE shopId = :shopId AND status IN ('RECEIVED', 'DIAGNOSING', 'COST_ESTIMATED', 'WAITING_APPROVAL', 'WAITING_PARTS', 'IN_REPAIR', 'TESTING', 'READY_FOR_PICKUP', 'CUSTOMER_NOTIFIED', 'PAYMENT_COLLECTED')
        """
    )
    fun observePendingRepairsCount(shopId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM maintenance_orders WHERE shopId = :shopId AND status = 'READY_FOR_PICKUP'")
    fun observeReadyRepairsCount(shopId: String): Flow<Int>

    @Query("SELECT * FROM maintenance_orders WHERE localId = :orderId LIMIT 1")
    suspend fun getMaintenanceOrderById(orderId: String): MaintenanceOrderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaintenanceOrder(order: MaintenanceOrderEntity)

    @Update
    suspend fun updateMaintenanceOrder(order: MaintenanceOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaintenancePart(part: MaintenancePartEntity)

    @Query("SELECT * FROM maintenance_parts WHERE maintenanceOrderId = :orderId")
    fun observeMaintenanceParts(orderId: String): Flow<List<MaintenancePartEntity>>

    @Query("SELECT * FROM maintenance_parts WHERE maintenanceOrderId = :orderId")
    suspend fun getMaintenanceParts(orderId: String): List<MaintenancePartEntity>

    @Query("SELECT * FROM expenses WHERE shopId = :shopId ORDER BY expenseTimestamp DESC")
    fun observeExpenses(shopId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM refunds WHERE shopId = :shopId ORDER BY refundTimestamp DESC")
    fun observeRefunds(shopId: String): Flow<List<RefundEntity>>

    @Query("SELECT COALESCE(SUM(refundAmountMinor), 0) FROM refunds WHERE originalSaleId = :saleId")
    suspend fun getTotalRefundedForSaleMinor(saleId: String): Long

    @Query("SELECT * FROM refunds WHERE originalSaleId = :saleId ORDER BY refundTimestamp DESC")
    suspend fun getRefundsForSale(saleId: String): List<RefundEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRefund(refund: RefundEntity)

    @Query("SELECT * FROM transfers WHERE shopId = :shopId ORDER BY transferTimestamp DESC")
    fun observeTransfers(shopId: String): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransfer(transfer: TransferEntity)

    // --- SMS Intelligence Queries ---
    @Query("SELECT * FROM sms_messages WHERE shopId = :shopId ORDER BY receivedTimestamp DESC")
    fun observeSmsMessages(shopId: String): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE shopId = :shopId AND messageHashSha256 = :messageHash LIMIT 1")
    suspend fun getSmsByMessageHash(shopId: String, messageHash: String): SmsMessageEntity?

    @Query(
        """
        SELECT * FROM sms_parsing_results
        WHERE shopId = :shopId
          AND detectedProviderCode = :providerCode
          AND extractedReference = :reference
          AND extractedReference != ''
        LIMIT 1
        """
    )
    suspend fun getParsingResultByProviderAndReference(
        shopId: String,
        providerCode: String,
        reference: String
    ): SmsParsingResultEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSmsMessage(sms: SmsMessageEntity)

    @Update
    suspend fun updateSmsMessage(sms: SmsMessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSmsParsingResult(result: SmsParsingResultEntity)

    @Query("SELECT * FROM sms_parsing_results WHERE smsMessageId = :smsId LIMIT 1")
    suspend fun getParsingResultForSms(smsId: String): SmsParsingResultEntity?

    @Query("SELECT * FROM pending_transactions WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun observePendingTransactions(shopId: String): Flow<List<PendingTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE shopId = :shopId AND status = 'AWAITING_CONFIRMATION'")
    fun observeAwaitingSmsCount(shopId: String): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE localId = :pendingId LIMIT 1")
    suspend fun getPendingTransactionById(pendingId: String): PendingTransactionEntity?

    @Query(
        """
        SELECT * FROM pending_transactions
        WHERE shopId = :shopId
          AND status = 'AWAITING_CONFIRMATION'
          AND smsParsingResultId IS NULL
          AND proposedAmountMinor = :amountMinor
        ORDER BY createdAt DESC
        """
    )
    suspend fun findUnmatchedExpectedPendingOperations(
        shopId: String,
        amountMinor: Long
    ): List<PendingTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingTransaction(pending: PendingTransactionEntity)

    @Update
    suspend fun updatePendingTransaction(pending: PendingTransactionEntity)

    @Query(
        """
        SELECT
            s.localId AS smsId,
            s.senderAddress AS senderAddress,
            s.messageBody AS messageBody,
            s.messageHashSha256 AS messageHashSha256,
            s.receivedTimestamp AS receivedTimestamp,
            s.isProcessed AS isProcessed,
            p.localId AS parsingResultId,
            p.detectedProviderCode AS detectedProviderCode,
            p.detectedOperationType AS detectedOperationType,
            p.extractedAmountMinor AS extractedAmountMinor,
            p.extractedFeeMinor AS extractedFeeMinor,
            p.extractedCounterpartyPhone AS extractedCounterpartyPhone,
            p.extractedReference AS extractedReference,
            p.confidenceBps AS confidenceBps,
            pt.localId AS pendingTransactionId,
            pt.status AS pendingStatus,
            pt.targetWalletId AS targetWalletId,
            pt.confirmedLedgerTransactionId AS confirmedLedgerTransactionId
        FROM sms_messages s
        LEFT JOIN sms_parsing_results p ON p.smsMessageId = s.localId
        LEFT JOIN pending_transactions pt ON pt.smsParsingResultId = p.localId
        WHERE s.shopId = :shopId
        ORDER BY s.receivedTimestamp DESC
        """
    )
    fun observeSmsReviewItems(shopId: String): Flow<List<SmsReviewItemProjection>>
}

package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.database.entities.CashDrawerEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransactionLineEntity
import com.example.database.entities.WalletEntity
import com.example.database.entities.WalletProviderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Derived Wallet Balance & Period Usage Projection (Requirement 9 & 11).
 * All monetary figures are calculated dynamically from valid ledger entries in `transaction_lines`!
 */
data class WalletDerivedProjection(
    val walletId: String,
    val providerId: String,
    val providerCode: String,
    val providerNameArabic: String,
    val walletNameArabic: String,
    val walletNumber: String,
    val currency: String,
    val dailyLimitMinor: Long,
    val monthlyLimitMinor: Long,
    val derivedBalanceMinor: Long,
    val todayInflowMinor: Long,
    val todayOutflowMinor: Long,
    val monthOutflowMinor: Long,
    val totalFeesMinor: Long
)

/**
 * Derived Cash Drawer Balance Projection (Requirement 9 & 13).
 */
data class CashDrawerDerivedProjection(
    val drawerId: String,
    val drawerCode: String,
    val nameArabic: String,
    val currency: String,
    val isMainDrawer: Boolean,
    val derivedBalanceMinor: Long,
    val todayInflowMinor: Long,
    val todayOutflowMinor: Long
)

/**
 * Authoritative Posted Ledger Projection (Requirement 4 & 5).
 * Guarantees that only 'POSTED' parent transactions impact financial balances,
 * excluding REVERSED, DRAFT, PENDING, FAILED, or CANCELLED records.
 */
data class AuthoritativePostedLedgerProjection(
    val accountType: FinancialAccountType,
    val accountId: String,
    val postedBalanceMinor: Long,
    val postedInflowsMinor: Long,
    val postedOutflowsMinor: Long,
    val postedTransactionsCount: Int
)

@Dao
interface FinancialLedgerDao {
    @Query("SELECT * FROM wallet_providers WHERE shopId = :shopId AND isActive = 1 ORDER BY nameArabic ASC")
    fun observeWalletProviders(shopId: String): Flow<List<WalletProviderEntity>>

    @Query("SELECT * FROM wallet_providers WHERE shopId = :shopId AND providerCode = :providerCode LIMIT 1")
    suspend fun getWalletProviderByCode(shopId: String, providerCode: String): WalletProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletProviders(providers: List<WalletProviderEntity>)

    @Query("SELECT * FROM wallets WHERE shopId = :shopId AND isActive = 1 ORDER BY createdAt ASC")
    fun observeWallets(shopId: String): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE shopId = :shopId")
    suspend fun getAllWallets(shopId: String): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE localId = :walletId LIMIT 1")
    suspend fun getWalletById(walletId: String): WalletEntity?

    @Query(
        """
        SELECT w.* FROM wallets w
        INNER JOIN wallet_providers wp ON wp.localId = w.providerId
        WHERE w.shopId = :shopId AND wp.providerCode = :providerCode AND w.isActive = 1
        ORDER BY w.createdAt ASC LIMIT 1
        """
    )
    suspend fun getFirstWalletForProviderCode(shopId: String, providerCode: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWallet(wallet: WalletEntity)

    @Upsert
    suspend fun upsertWallets(wallets: List<WalletEntity>)

    @Query("SELECT * FROM cash_drawers WHERE shopId = :shopId AND isActive = 1 ORDER BY isMainDrawer DESC, createdAt ASC")
    fun observeCashDrawers(shopId: String): Flow<List<CashDrawerEntity>>

    @Query("SELECT * FROM cash_drawers WHERE shopId = :shopId")
    suspend fun getAllCashDrawers(shopId: String): List<CashDrawerEntity>

    @Upsert
    suspend fun upsertCashDrawers(drawers: List<CashDrawerEntity>)

    @Query("SELECT * FROM cash_sessions WHERE shopId = :shopId")
    suspend fun getAllCashSessions(shopId: String): List<CashSessionEntity>

    @Upsert
    suspend fun upsertCashSessions(sessions: List<CashSessionEntity>)

    @Query("SELECT * FROM cash_drawers WHERE localId = :drawerId LIMIT 1")
    suspend fun getCashDrawerById(drawerId: String): CashDrawerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashDrawer(drawer: CashDrawerEntity)

    @Query("SELECT * FROM cash_sessions WHERE shopId = :shopId ORDER BY openedAt DESC")
    fun observeCashSessions(shopId: String): Flow<List<CashSessionEntity>>

    @Query("SELECT * FROM cash_sessions WHERE shopId = :shopId AND cashDrawerId = :drawerId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpenCashSession(shopId: String, drawerId: String): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE shopId = :shopId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getAnyOpenCashSession(shopId: String): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE localId = :sessionId LIMIT 1")
    suspend fun getCashSessionById(sessionId: String): CashSessionEntity?

    @Query(
        """
        SELECT * FROM financial_transactions
        WHERE shopId = :shopId
          AND status = 'POSTED'
          AND (shiftId = :shiftId OR (transactionTimestamp >= :openedAt AND (:closedAt IS NULL OR transactionTimestamp <= :closedAt)))
        ORDER BY transactionTimestamp ASC
        """
    )
    suspend fun getTransactionsForShiftWindow(
        shopId: String,
        shiftId: String,
        openedAt: Long,
        closedAt: Long?
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashSession(session: CashSessionEntity)

    @Update
    suspend fun updateCashSession(session: CashSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionsIgnoreDuplicates(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactionLines(lines: List<TransactionLineEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionLinesIgnoreDuplicates(lines: List<TransactionLineEntity>)

    @Query(
        """
        UPDATE financial_transactions
        SET status = 'REVERSED',
            reversedByTransactionId = :reversalTxId,
            updatedAt = :updatedAt,
            version = version + 1,
            syncState = 'PENDING'
        WHERE localId = :originalTxId AND status = 'POSTED'
        """
    )
    suspend fun markTransactionReversed(originalTxId: String, reversalTxId: String, updatedAt: Long): Int

    @Query("SELECT * FROM financial_transactions WHERE localId = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): TransactionEntity?

    @Query("SELECT * FROM financial_transactions WHERE shopId = :shopId AND referenceNumber = :reference LIMIT 1")
    suspend fun getTransactionByReference(shopId: String, reference: String): TransactionEntity?

    @Query("SELECT * FROM financial_transactions WHERE shopId = :shopId AND metadataJson LIKE :idempotencyPattern LIMIT 1")
    suspend fun findTransactionByIdempotencyPattern(shopId: String, idempotencyPattern: String): TransactionEntity?

    @Query(
        """
        SELECT COALESCE(SUM(tl.amountMinor), 0)
        FROM transaction_lines tl
        INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
        WHERE tl.shopId = :shopId
          AND tl.accountType = 'WALLET'
          AND tl.accountId = :walletId
          AND tl.direction = 'OUTFLOW'
          AND ft.status = 'POSTED'
          AND ft.transactionType != 'REVERSAL'
          AND tl.lineTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun getWalletOutflowInRangeMinor(
        shopId: String,
        walletId: String,
        startMillis: Long,
        endMillis: Long
    ): Long

    @Query("SELECT * FROM transaction_lines WHERE transactionId = :transactionId ORDER BY createdAt ASC")
    suspend fun getLinesForTransaction(transactionId: String): List<TransactionLineEntity>

    @Query("SELECT * FROM transaction_lines WHERE shopId = :shopId ORDER BY lineTimestamp ASC, createdAt ASC")
    suspend fun getAllTransactionLinesChronological(shopId: String): List<TransactionLineEntity>

    @Query("SELECT * FROM financial_transactions WHERE shopId = :shopId ORDER BY transactionTimestamp DESC, createdAt DESC LIMIT :limit")
    fun observeLedgerTransactions(shopId: String, limit: Int = 250): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE shopId = :shopId ORDER BY transactionTimestamp ASC, createdAt ASC")
    suspend fun getAllTransactionsChronological(shopId: String): List<TransactionEntity>

    @Query(
        """
        SELECT COALESCE(SUM(tl.signedImpactMinor), 0)
        FROM transaction_lines tl
        INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
        WHERE tl.shopId = :shopId 
          AND tl.accountType = :accountType 
          AND tl.accountId = :accountId
          AND ft.status = 'POSTED'
          AND ft.transactionType != 'REVERSAL'
        """
    )
    suspend fun getDerivedAccountBalanceMinor(
        shopId: String,
        accountType: FinancialAccountType,
        accountId: String
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(tl.signedImpactMinor), 0)
        FROM transaction_lines tl
        INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
        WHERE tl.shopId = :shopId 
          AND tl.accountType = 'CASH_DRAWER'
          AND ft.status = 'POSTED'
          AND ft.transactionType != 'REVERSAL'
        """
    )
    fun observeTotalCashPositionMinor(shopId: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(tl.signedImpactMinor), 0)
        FROM transaction_lines tl
        INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
        WHERE tl.shopId = :shopId 
          AND tl.accountType = 'WALLET'
          AND ft.status = 'POSTED'
          AND ft.transactionType != 'REVERSAL'
        """
    )
    fun observeTotalWalletsBalanceMinor(shopId: String): Flow<Long>

    @Query(
        """
        SELECT
            w.localId AS walletId,
            w.providerId AS providerId,
            wp.providerCode AS providerCode,
            wp.nameArabic AS providerNameArabic,
            w.walletNameArabic AS walletNameArabic,
            w.walletNumber AS walletNumber,
            w.currency AS currency,
            w.dailyLimitMinor AS dailyLimitMinor,
            w.monthlyLimitMinor AS monthlyLimitMinor,
            COALESCE((
                SELECT SUM(tl.signedImpactMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId 
                  AND tl.accountType = 'WALLET' 
                  AND tl.accountId = w.localId
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
            ), 0) AS derivedBalanceMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'WALLET'
                  AND tl.accountId = w.localId
                  AND tl.direction = 'INFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND tl.lineTimestamp BETWEEN :startOfDayMillis AND :endOfDayMillis
            ), 0) AS todayInflowMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'WALLET'
                  AND tl.accountId = w.localId
                  AND tl.direction = 'OUTFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND tl.lineTimestamp BETWEEN :startOfDayMillis AND :endOfDayMillis
            ), 0) AS todayOutflowMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'WALLET'
                  AND tl.accountId = w.localId
                  AND tl.direction = 'OUTFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND tl.lineTimestamp BETWEEN :startOfMonthMillis AND :endOfMonthMillis
            ), 0) AS monthOutflowMinor,
            COALESCE((
                SELECT SUM(ft.feeMinor)
                FROM financial_transactions ft
                WHERE ft.shopId = :shopId
                  AND (ft.sourceAccountId = w.localId OR ft.destinationAccountId = w.localId)
                  AND ft.status = 'POSTED'
            ), 0) AS totalFeesMinor
        FROM wallets w
        INNER JOIN wallet_providers wp ON wp.localId = w.providerId
        WHERE w.shopId = :shopId AND w.isActive = 1
        ORDER BY w.createdAt ASC
        """
    )
    fun observeWalletDerivedProjections(
        shopId: String,
        startOfDayMillis: Long,
        endOfDayMillis: Long,
        startOfMonthMillis: Long,
        endOfMonthMillis: Long
    ): Flow<List<WalletDerivedProjection>>

    @Query(
        """
        SELECT
            cd.localId AS drawerId,
            cd.drawerCode AS drawerCode,
            cd.nameArabic AS nameArabic,
            cd.currency AS currency,
            cd.isMainDrawer AS isMainDrawer,
            COALESCE((
                SELECT SUM(tl.signedImpactMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId 
                  AND tl.accountType = 'CASH_DRAWER' 
                  AND tl.accountId = cd.localId
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
            ), 0) AS derivedBalanceMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'CASH_DRAWER'
                  AND tl.accountId = cd.localId
                  AND tl.direction = 'INFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND tl.lineTimestamp BETWEEN :startOfDayMillis AND :endOfDayMillis
            ), 0) AS todayInflowMinor,
            COALESCE((
                SELECT SUM(tl.amountMinor)
                FROM transaction_lines tl
                INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
                WHERE tl.shopId = :shopId
                  AND tl.accountType = 'CASH_DRAWER'
                  AND tl.accountId = cd.localId
                  AND tl.direction = 'OUTFLOW'
                  AND ft.status = 'POSTED'
                  AND ft.transactionType != 'REVERSAL'
                  AND tl.lineTimestamp BETWEEN :startOfDayMillis AND :endOfDayMillis
            ), 0) AS todayOutflowMinor
        FROM cash_drawers cd
        WHERE cd.shopId = :shopId AND cd.isActive = 1
        ORDER BY cd.isMainDrawer DESC, cd.createdAt ASC
        """
    )
    fun observeCashDrawerDerivedProjections(
        shopId: String,
        startOfDayMillis: Long,
        endOfDayMillis: Long
    ): Flow<List<CashDrawerDerivedProjection>>

    @Query(
        """
        SELECT COALESCE(SUM(totalAmountMinor), 0)
        FROM sales
        WHERE shopId = :shopId
          AND status != 'REFUNDED'
          AND saleTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    fun observeSalesTotalForRange(shopId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0)
        FROM financial_transactions
        WHERE shopId = :shopId
          AND transactionType = 'EXPENSE'
          AND status = 'POSTED'
          AND transactionTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    fun observeExpensesTotalForRange(shopId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0)
        FROM financial_transactions
        WHERE shopId = :shopId
          AND transactionType = 'PURCHASE'
          AND status = 'POSTED'
          AND transactionTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    fun observePurchasesTotalForRange(shopId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(commissionMinor), 0)
        FROM financial_transactions
        WHERE shopId = :shopId
          AND status = 'POSTED'
          AND transactionTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    fun observeCommissionsTotalForRange(shopId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT 
            tl.accountType AS accountType,
            tl.accountId AS accountId,
            COALESCE(SUM(tl.signedImpactMinor), 0) AS postedBalanceMinor,
            COALESCE(SUM(CASE WHEN tl.direction = 'INFLOW' THEN tl.amountMinor ELSE 0 END), 0) AS postedInflowsMinor,
            COALESCE(SUM(CASE WHEN tl.direction = 'OUTFLOW' THEN tl.amountMinor ELSE 0 END), 0) AS postedOutflowsMinor,
            COUNT(DISTINCT ft.localId) AS postedTransactionsCount
        FROM transaction_lines tl
        INNER JOIN financial_transactions ft ON ft.localId = tl.transactionId
        WHERE tl.shopId = :shopId
          AND ft.status = 'POSTED'
          AND ft.transactionType != 'REVERSAL'
          AND tl.accountType = :accountType
          AND tl.accountId = :accountId
        GROUP BY tl.accountType, tl.accountId
        """
    )
    suspend fun getAuthoritativePostedLedgerProjection(
        shopId: String,
        accountType: FinancialAccountType,
        accountId: String
    ): AuthoritativePostedLedgerProjection?
}

package com.example.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.SyncState

/**
 * Supported Immutable Financial Transaction Types (Requirement 8).
 */
enum class FinancialTransactionType(val arabicLabel: String) {
    SALE("مبيعات"),
    PURCHASE("مشتريات"),
    DEPOSIT("إيداع / تغذية محفظة"),
    WITHDRAW("سحب نقدي من محفظة"),
    TRANSFER("تحويل بين الحسابات"),
    EXPENSE("مصروفات تشغيلية"),
    REFUND("مرتجع مبيعات"),
    ADJUSTMENT("قيد تسوية محاسبية"),
    CASH_IN("توريد نقدية للخزينة"),
    CASH_OUT("صرف نقدية من الخزينة"),
    REVERSAL("قيد عكسي تصحيحي")
}

/**
 * Ledger Transaction Status.
 * Once POSTED, ledger entries are immutable; corrections require a REVERSAL or ADJUSTMENT transaction.
 */
enum class LedgerTransactionStatus {
    POSTED,
    REVERSED
}

/**
 * Financial Account Type in the double-entry / directional ledger.
 */
enum class FinancialAccountType {
    CASH_DRAWER,
    WALLET,
    EXTERNAL_CUSTOMER,
    EXTERNAL_SUPPLIER,
    OPERATING_EXPENSE,
    REVENUE_COMMISSION,
    EQUITY_OPENING_OR_ADJUSTMENT
}

@Entity(
    tableName = "wallet_providers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "providerCode"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class WalletProviderEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val providerCode: String, // VODAFONE_CASH, INSTAPAY, ORANGE_CASH, ETISALAT_CASH, WE_PAY, FAWRY
    val nameArabic: String,
    val defaultFeeBasisPoints: Int = 100, // e.g., 1% = 100 bps
    val defaultCommissionBasisPoints: Int = 100,
    val defaultDailyLimitMinor: Long = 60_000_00L, // 60,000.00 EGP in piasters
    val defaultMonthlyLimitMinor: Long = 200_000_00L, // 200,000.00 EGP in piasters
    val smsSenderFilter: String = "",
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Electronic Wallet entity (Requirement 5 & 9).
 * CRITICAL INVARIANT (Requirement 9):
 * Does NOT contain `balance`, `dailyUsed`, or `monthlyUsed` mutable columns!
 * All balances and daily/monthly usage are derived in real time from `financial_transactions` / `transaction_lines`.
 */
@Entity(
    tableName = "wallets",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WalletProviderEntity::class,
            parentColumns = ["localId"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "walletNumber"], unique = true),
        Index(value = ["shopId", "providerId"]),
        Index(value = ["syncState"])
    ]
)
data class WalletEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val providerId: String,
    val walletNameArabic: String,
    val walletNumber: String, // Phone number or handle
    val currency: String = "EGP",
    val dailyLimitMinor: Long = 60_000_00L,
    val monthlyLimitMinor: Long = 200_000_00L,
    val simSlotIndex: Int? = null,
    val isActive: Boolean = true,
    val operationalStatus: String = "ACTIVE", // ACTIVE, SUSPENDED, DISABLED
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Physical / Operational Cash Drawer (Requirement 5 & 9).
 * CRITICAL INVARIANT (Requirement 9):
 * Does NOT contain a mutable `balance` column!
 * Cash balance is derived strictly from valid ledger entries in `transaction_lines` / `financial_transactions`.
 */
@Entity(
    tableName = "cash_drawers",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "drawerCode"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class CashDrawerEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val drawerCode: String,
    val nameArabic: String,
    val currency: String = "EGP",
    val isMainDrawer: Boolean = true,
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Shift / Cash Drawer Session for opening & closing reconciliation.
 */
@Entity(
    tableName = "cash_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CashDrawerEntity::class,
            parentColumns = ["localId"],
            childColumns = ["cashDrawerId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["localId"],
            childColumns = ["openedByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "cashDrawerId", "status"]),
        Index(value = ["openedByUserId"]),
        Index(value = ["syncState"])
    ]
)
data class CashSessionEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val cashDrawerId: String,
    val openedByUserId: String,
    val closedByUserId: String? = null,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingDerivedBalanceMinor: Long,
    val closingDerivedBalanceMinor: Long? = null,
    val closingCountedCashMinor: Long? = null,
    val discrepancyMinor: Long? = null,
    val openingCashInputMinor: Long = 0L,
    val openingWalletBalancesJson: String = "{}",
    val closingWalletBalancesJson: String = "{}",
    val varianceReasonArabic: String = "",
    val approvedByUserId: String? = null,
    val approvalStatus: String = "NOT_REQUIRED", // NOT_REQUIRED, APPROVED, PENDING_APPROVAL
    val explicitAdjustmentTxId: String? = null,
    val status: String = "OPEN", // OPEN, CLOSED
    val notes: String = "",
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * IMMUTABLE FINANCIAL TRANSACTION LEDGER HEADER (Requirement 8, 9, 10, 11, 20).
 * Protected by database triggers against deletion or amount/account mutation.
 */
@Entity(
    tableName = "financial_transactions",
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
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "referenceNumber"], unique = true),
        Index(value = ["shopId", "transactionTimestamp"]),
        Index(value = ["shopId", "transactionType", "transactionTimestamp"]),
        Index(value = ["sourceAccountType", "sourceAccountId"]),
        Index(value = ["destinationAccountType", "destinationAccountId"]),
        Index(value = ["customerId"]),
        Index(value = ["supplierId"]),
        Index(value = ["relatedEntityType", "relatedEntityId"]),
        Index(value = ["reversalOfTransactionId"]),
        Index(value = ["createdByUserId"]),
        Index(value = ["syncState"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val referenceNumber: String,
    val transactionType: FinancialTransactionType,
    val amountMinor: Long, // Strictly > 0 (enforced by SQLite trigger)
    val feeMinor: Long = 0L,
    val commissionMinor: Long = 0L,
    val currency: String = "EGP",
    val sourceAccountType: FinancialAccountType,
    val sourceAccountId: String,
    val destinationAccountType: FinancialAccountType,
    val destinationAccountId: String,
    val customerId: String? = null,
    val supplierId: String? = null,
    val shiftId: String? = null,
    val relatedEntityType: String? = null, // SALE, PURCHASE, EXPENSE, REFUND, TRANSFER, MAINTENANCE, ADJUSTMENT
    val relatedEntityId: String? = null,
    val reversalOfTransactionId: String? = null,
    val reversedByTransactionId: String? = null,
    val status: LedgerTransactionStatus = LedgerTransactionStatus.POSTED,
    val descriptionArabic: String,
    val metadataJson: String = "{}",
    val ledgerHashSha256: String,
    val createdByUserId: String,
    val deviceId: String,
    val transactionTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Immutable Ledger Posting Lines for exact account balance derivation (Requirement 8 & 9).
 * `signedImpactMinor` is positive when funds enter the account (`DEBIT` for asset accounts like Cash/Wallet)
 * and negative when funds leave the account (`CREDIT` for asset accounts).
 */
@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["shopId", "accountType", "accountId", "lineTimestamp"]),
        Index(value = ["shopId", "lineTimestamp"])
    ]
)
data class TransactionLineEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val transactionId: String,
    val accountType: FinancialAccountType,
    val accountId: String,
    val direction: String, // INFLOW or OUTFLOW
    val amountMinor: Long, // Strictly > 0
    val signedImpactMinor: Long, // +amountMinor for INFLOW, -amountMinor for OUTFLOW
    val currency: String = "EGP",
    val lineNote: String = "",
    val lineTimestamp: Long,
    val deviceId: String,
    val createdAt: Long
)

/**
 * SMS Message entity for Wallet SMS Transaction Intelligence foundation (Requirement 5).
 */
@Entity(
    tableName = "sms_messages",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "messageHashSha256"], unique = true),
        Index(value = ["shopId", "receivedTimestamp"])
    ]
)
data class SmsMessageEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val senderAddress: String,
    val messageBody: String,
    val messageHashSha256: String,
    val simSlotIndex: Int? = null,
    val receivedTimestamp: Long,
    val isProcessed: Boolean = false,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "sms_parsing_results",
    foreignKeys = [
        ForeignKey(
            entity = SmsMessageEntity::class,
            parentColumns = ["localId"],
            childColumns = ["smsMessageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["smsMessageId"], unique = true),
        Index(value = ["shopId", "extractedReference"])
    ]
)
data class SmsParsingResultEntity(
    @PrimaryKey val localId: String,
    val shopId: String,
    val smsMessageId: String,
    val detectedProviderCode: String,
    val detectedOperationType: String, // DEPOSIT, WITHDRAW, TRANSFER
    val extractedAmountMinor: Long,
    val extractedFeeMinor: Long = 0L,
    val extractedCounterpartyPhone: String = "",
    val extractedReference: String = "",
    val confidenceBps: Int = 10_000,
    val createdAt: Long
)

@Entity(
    tableName = "pending_transactions",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "status"]),
        Index(value = ["smsParsingResultId"])
    ]
)
data class PendingTransactionEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val smsParsingResultId: String? = null,
    val proposedType: FinancialTransactionType,
    val proposedAmountMinor: Long,
    val proposedFeeMinor: Long = 0L,
    val targetWalletId: String? = null,
    val counterpartyPhone: String = "",
    val status: String = "AWAITING_CONFIRMATION", // AWAITING_CONFIRMATION, CONFIRMED, REJECTED
    val confirmedLedgerTransactionId: String? = null,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Local-First Synchronization Queue (Requirement 4, 6, 19).
 * Unique constraint on `deduplicationKey` prevents duplicate synchronization operations.
 */
@Entity(
    tableName = "sync_queue",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["deduplicationKey"], unique = true),
        Index(value = ["shopId", "syncState", "createdAt"]),
        Index(value = ["entityType", "entityLocalId"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey val localId: String,
    val shopId: String,
    val entityType: String,
    val entityLocalId: String,
    val operationType: String, // INSERT, UPDATE, SOFT_DELETE, LEDGER_POST
    val entityVersion: Long,
    val deduplicationKey: String, // "$shopId:$entityType:$entityLocalId:$operationType:$entityVersion"
    val payloadJson: String,
    val isFinancialLedgerRecord: Boolean = false,
    val syncState: SyncState = SyncState.PENDING,
    val retryCount: Int = 0,
    val lastErrorMessage: String? = null,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAttemptAt: Long? = null
) {
    val maxRetries: Int get() = 5
}

/**
 * Reviewable Synchronization Conflict (Requirement 20).
 * Financial records NEVER use blind last-write-wins; any conflict creates a reviewable record here.
 */
@Entity(
    tableName = "sync_conflicts",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "resolutionStatus"]),
        Index(value = ["entityType", "entityLocalId"])
    ]
)
data class SyncConflictEntity(
    @PrimaryKey val localId: String,
    val shopId: String,
    val entityType: String,
    val entityLocalId: String,
    val isFinancialRecord: Boolean,
    val localVersion: Long,
    val remoteVersion: Long,
    val localSnapshotJson: String,
    val remoteSnapshotJson: String,
    val conflictReasonArabic: String,
    val resolutionStatus: String = "UNRESOLVED", // UNRESOLVED, RESOLVED_COMPENSATING_ENTRY, RESOLVED_KEEP_LOCAL, RESOLVED_ACCEPT_REMOTE
    val resolvedByUserId: String? = null,
    val resolutionNotes: String? = null,
    val compensatingTransactionId: String? = null,
    val deviceId: String,
    val detectedAt: Long,
    val resolvedAt: Long? = null
) {
    val isResolved: Boolean get() = resolutionStatus != "UNRESOLVED"
}

/**
 * Immutable Audit Log (Requirement 18).
 */
@Entity(
    tableName = "audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "timestamp"]),
        Index(value = ["shopId", "actionType"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["actorUserId"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val actorUserId: String,
    val actorRoleCode: String,
    val actionType: String, // SALE, PURCHASE, REFUND, EXPENSE, WALLET_OP, CASH_ADJUST, INVENTORY_ADJUST, PRICE_CHANGE, PERMISSION_CHANGE, BACKUP_RESTORE, CONFLICT_RESOLUTION
    val entityType: String,
    val entityId: String,
    val summaryArabic: String,
    val reasonArabic: String = "",
    val referenceNumber: String = "",
    val beforeStateJson: String? = null,
    val afterStateJson: String? = null,
    val deviceId: String,
    val timestamp: Long,
    val syncState: SyncState = SyncState.PENDING
)

/**
 * Backup Metadata for `makkaphone-backup-v1` (Requirement 21).
 */
@Entity(
    tableName = "backup_metadata",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "createdAt"]),
        Index(value = ["checksumSha256"], unique = true)
    ]
)
data class BackupMetadataEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val backupFormatVersion: String = "makkaphone-backup-v1",
    val schemaVersion: Int,
    val appVersion: String,
    val deviceId: String,
    val checksumSha256: String,
    val totalRecordsCount: Int,
    val ledgerTransactionsCount: Int,
    val inventoryMovementsCount: Int,
    val payloadJson: String,
    val isVerified: Boolean = true,
    val createdByUserId: String,
    val createdAt: Long
) {
    val fileSizeBytes: Long get() = payloadJson.length.toLong()
    val fileName: String get() = "MekkaBackup_${localId.takeLast(8)}.json"
    val recordCount: Int get() = totalRecordsCount
}


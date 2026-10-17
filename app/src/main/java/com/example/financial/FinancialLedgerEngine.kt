package com.example.financial

import androidx.room.withTransaction
import com.example.auth.AuthSecurityManager
import com.example.auth.PermissionCode
import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.core.SyncState
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SerialLifecycleStatus
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransactionLineEntity
import com.example.database.entities.TransferEntity
import com.example.inventory.InventoryEngine
import com.example.sync.SyncCoordinator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.math.abs

/**
 * Input line for an atomic Sale operation.
 */
data class SaleLineInput(
    val productId: String,
    val variantId: String? = null,
    val serialNumberId: String? = null,
    val quantity: Int,
    val unitSellingPriceMinor: Long,
    val lineDiscountMinor: Long = 0L
)

/**
 * P0-E: Split payment input line for POS sales across multiple accounts (Cash, Wallet, etc.).
 */
data class SalePaymentSplit(
    val accountType: FinancialAccountType,
    val accountId: String,
    val amountMinor: Long,
    val methodLabelArabic: String = ""
)

/**
 * Input line for an atomic Purchase operation.
 */
data class PurchaseLineInput(
    val productId: String,
    val variantId: String? = null,
    val quantity: Int,
    val unitCostMinor: Long,
    val serialNumbersOrImeis: List<String> = emptyList()
)

private class AtomicRollbackException(val error: AppResult.Error) : RuntimeException(error.message)

/**
 * Immutable Financial Transaction Ledger & Atomic Business Operation Engine (Requirements 8, 9, 10, 11, 13, 14, 16, 50, 51).
 */
class FinancialLedgerEngine(
    private val database: MekkaPhoneDatabase,
    private val inventoryEngine: InventoryEngine,
    private val authSecurityManager: AuthSecurityManager,
    private val syncCoordinator: SyncCoordinator
) {
    private val ledgerDao = database.financialLedgerDao()
    private val operationsDao = database.operationsDao()
    private val catalogDao = database.catalogInventoryDao()
    private val systemDao = database.systemIntegrityDao()
    private val executionMutex = Mutex()

    private fun computeDayAndMonthBounds(nowMillis: Long): Pair<Pair<Long, Long>, Pair<Long, Long>> {
        val dayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = dayCal.timeInMillis
        dayCal.set(Calendar.HOUR_OF_DAY, 23)
        dayCal.set(Calendar.MINUTE, 59)
        dayCal.set(Calendar.SECOND, 59)
        dayCal.set(Calendar.MILLISECOND, 999)
        val endOfDay = dayCal.timeInMillis

        val monthCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = monthCal.timeInMillis
        monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        monthCal.set(Calendar.HOUR_OF_DAY, 23)
        monthCal.set(Calendar.MINUTE, 59)
        monthCal.set(Calendar.SECOND, 59)
        monthCal.set(Calendar.MILLISECOND, 999)
        val endOfMonth = monthCal.timeInMillis
        return (startOfDay to endOfDay) to (startOfMonth to endOfMonth)
    }

    /**
     * Posts an immutable transaction header and its directional account impact lines inside an active transaction.
     * Enforces non-negative derived balances for asset accounts (`CASH_DRAWER`, `WALLET`).
     */
    suspend fun postLedgerTransactionInternal(
        shopId: String,
        transactionType: FinancialTransactionType,
        amountMinor: Long,
        feeMinor: Long = 0L,
        commissionMinor: Long = 0L,
        currency: String = "EGP",
        sourceAccountType: FinancialAccountType,
        sourceAccountId: String,
        destinationAccountType: FinancialAccountType,
        destinationAccountId: String,
        customerId: String? = null,
        supplierId: String? = null,
        relatedEntityType: String? = null,
        relatedEntityId: String? = null,
        reversalOfTransactionId: String? = null,
        descriptionArabic: String,
        metadataJson: String = "{}",
        createdByUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        additionalLines: List<Triple<FinancialAccountType, String, Long>> = emptyList(),
        idempotencyKey: String? = null,
        shiftId: String? = null
    ): AppResult<TransactionEntity> {
        if (amountMinor <= 0L) {
            return AppResult.Error("قيمة العملية المالية يجب أن تكون أكبر من الصفر", "INVALID_AMOUNT")
        }
        if (feeMinor < 0L || commissionMinor < 0L) {
            return AppResult.Error("الرسوم والعمولات لا يمكن أن تكون سالبة", "INVALID_FEE")
        }

        val resolvedShiftId = shiftId ?: ledgerDao.getAnyOpenCashSession(shopId)?.localId

        val cleanIdempotencyKey = idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanIdempotencyKey != null) {
            val existingTx = ledgerDao.findTransactionByIdempotencyPattern(
                shopId = shopId,
                idempotencyPattern = "%\"idempotencyKey\":\"$cleanIdempotencyKey\"%"
            )
            if (existingTx != null) {
                return AppResult.Error(
                    "تم منع تكرار العملية المالية: هذه العملية مسجلة مسبقاً بنفس مفتاح عدم التكرار ($cleanIdempotencyKey)",
                    "DUPLICATE_TRANSACTION_IDEMPOTENT"
                )
            }
        }

        if (sourceAccountType == FinancialAccountType.CASH_DRAWER || sourceAccountType == FinancialAccountType.WALLET) {
            val totalSourceOutflow = amountMinor + feeMinor
            val currentDerivedBalance = ledgerDao.getDerivedAccountBalanceMinor(
                shopId = shopId,
                accountType = sourceAccountType,
                accountId = sourceAccountId
            )
            if (currentDerivedBalance < totalSourceOutflow) {
                val curFormatted = Money.ofMinor(currentDerivedBalance).formatArabic()
                val reqFormatted = Money.ofMinor(totalSourceOutflow).formatArabic()
                return AppResult.Error(
                    "الرصيد المشتق من دفتر الأستاذ غير كافٍ في الحساب المصدر: المتاح ($curFormatted) والمطلوب ($reqFormatted)",
                    "INSUFFICIENT_DERIVED_BALANCE"
                )
            }
        }

        val txId = IdGenerator.newPrefixedId("TX")
        val refNum = IdGenerator.newReferenceNumber(transactionType.name.take(3), timestamp)
        val hashInput = "$shopId|$refNum|${transactionType.name}|$amountMinor|$feeMinor|$commissionMinor|" +
            "${sourceAccountType.name}|$sourceAccountId|${destinationAccountType.name}|$destinationAccountId|$timestamp"
        val ledgerHash = IdGenerator.sha256Hex(hashInput)

        // Enforce Double-Entry Accounting Rule: Total Debits == Total Credits before posting!
        val baseMetadataJson = if (metadataJson.contains("journalEntryId")) {
            val decoded = AccountingEngine.decodeJournalEntryFromJson(metadataJson)
            if (decoded == null || !decoded.isBalanced || decoded.totalDebitMinor != decoded.totalCreditMinor) {
                return AppResult.Error(
                    "رفض القيد المحاسبي: عدم توازن القيد المزدوج (Debits != Credits)",
                    "UNBALANCED_DOUBLE_ENTRY_JOURNAL"
                )
            }
            metadataJson
        } else {
            val journalRes = AccountingEngine.buildGeneralLedgerJournalEntry(
                shopId = shopId,
                ledgerTransactionId = txId,
                referenceNumber = refNum,
                transactionType = transactionType,
                amountMinor = amountMinor,
                feeMinor = feeMinor,
                commissionMinor = commissionMinor,
                sourceAccountType = sourceAccountType,
                sourceAccountId = sourceAccountId,
                destinationAccountType = destinationAccountType,
                destinationAccountId = destinationAccountId,
                customerId = customerId,
                supplierId = supplierId,
                createdByUserId = createdByUserId,
                deviceId = deviceId,
                timestamp = timestamp
            )
            if (journalRes is AppResult.Error) {
                return journalRes
            }
            AccountingEngine.encodeJournalEntryToJson((journalRes as AppResult.Success).data)
        }
        val finalMetadataJson = if (cleanIdempotencyKey != null && baseMetadataJson.endsWith("}")) {
            baseMetadataJson.dropLast(1) + ""","idempotencyKey":"$cleanIdempotencyKey"}"""
        } else {
            baseMetadataJson
        }

        val txEntity = TransactionEntity(
            localId = txId,
            shopId = shopId,
            referenceNumber = refNum,
            transactionType = transactionType,
            amountMinor = amountMinor,
            feeMinor = feeMinor,
            commissionMinor = commissionMinor,
            currency = currency,
            sourceAccountType = sourceAccountType,
            sourceAccountId = sourceAccountId,
            destinationAccountType = destinationAccountType,
            destinationAccountId = destinationAccountId,
            customerId = customerId,
            supplierId = supplierId,
            shiftId = resolvedShiftId,
            relatedEntityType = relatedEntityType,
            relatedEntityId = relatedEntityId,
            reversalOfTransactionId = reversalOfTransactionId,
            status = LedgerTransactionStatus.POSTED,
            descriptionArabic = descriptionArabic,
            metadataJson = finalMetadataJson,
            ledgerHashSha256 = ledgerHash,
            createdByUserId = createdByUserId,
            deviceId = deviceId,
            transactionTimestamp = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp,
            version = 1L,
            syncState = SyncState.PENDING
        )

        val lines = mutableListOf<TransactionLineEntity>()

        val sourceOutflowTotal = amountMinor + feeMinor
        lines.add(
            TransactionLineEntity(
                localId = IdGenerator.newPrefixedId("TXL"),
                shopId = shopId,
                transactionId = txId,
                accountType = sourceAccountType,
                accountId = sourceAccountId,
                direction = "OUTFLOW",
                amountMinor = sourceOutflowTotal,
                signedImpactMinor = -sourceOutflowTotal,
                currency = currency,
                lineNote = "صادر: $descriptionArabic",
                lineTimestamp = timestamp,
                deviceId = deviceId,
                createdAt = timestamp
            )
        )

        lines.add(
            TransactionLineEntity(
                localId = IdGenerator.newPrefixedId("TXL"),
                shopId = shopId,
                transactionId = txId,
                accountType = destinationAccountType,
                accountId = destinationAccountId,
                direction = "INFLOW",
                amountMinor = amountMinor,
                signedImpactMinor = amountMinor,
                currency = currency,
                lineNote = "وارد: $descriptionArabic",
                lineTimestamp = timestamp,
                deviceId = deviceId,
                createdAt = timestamp
            )
        )

        for ((accType, accId, signedImpact) in additionalLines) {
            if (signedImpact != 0L) {
                val absAmt = abs(signedImpact)
                lines.add(
                    TransactionLineEntity(
                        localId = IdGenerator.newPrefixedId("TXL"),
                        shopId = shopId,
                        transactionId = txId,
                        accountType = accType,
                        accountId = accId,
                        direction = if (signedImpact > 0) "INFLOW" else "OUTFLOW",
                        amountMinor = absAmt,
                        signedImpactMinor = signedImpact,
                        currency = currency,
                        lineNote = "تسوية إضافية: $descriptionArabic",
                        lineTimestamp = timestamp,
                        deviceId = deviceId,
                        createdAt = timestamp
                    )
                )
            }
        }

        // Enforce economic zero-sum balance across accounts
        val netBalanceDiff = lines.sumOf { it.signedImpactMinor }
        if (netBalanceDiff != 0L) {
            val balancingDirection = if (netBalanceDiff < 0L) "INFLOW" else "OUTFLOW"
            val absBalancing = abs(netBalanceDiff)
            val balancingAccount = if (netBalanceDiff < 0L) "EXPENSE_FEE_BALANCING" else "REVENUE_COMMISSION_BALANCING"
            lines.add(
                TransactionLineEntity(
                    localId = IdGenerator.newPrefixedId("TXL"),
                    shopId = shopId,
                    transactionId = txId,
                    accountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                    accountId = balancingAccount,
                    direction = balancingDirection,
                    amountMinor = absBalancing,
                    signedImpactMinor = -netBalanceDiff,
                    currency = currency,
                    lineNote = "تسوية توازن القيد المحاسبي: $descriptionArabic",
                    lineTimestamp = timestamp,
                    deviceId = deviceId,
                    createdAt = timestamp
                )
            )
        }

        val invariantCheck = FinancialInvariantValidator.validateBeforeCommit(txEntity, lines)
        if (invariantCheck is AppResult.Error) {
            return invariantCheck
        }

        ledgerDao.insertTransaction(txEntity)
        ledgerDao.insertTransactionLines(lines)

        syncCoordinator.enqueueOperationInternal(
            shopId = shopId,
            entityType = "FinancialTransaction",
            entityLocalId = txId,
            operationType = "LEDGER_POST",
            entityVersion = 1L,
            payloadJson = """{"txId":"$txId","ref":"$refNum","type":"${transactionType.name}","amountMinor":$amountMinor,"hash":"$ledgerHash"}""",
            isFinancialLedgerRecord = true,
            deviceId = deviceId,
            timestamp = timestamp
        )

        return AppResult.Success(txEntity)
    }

    /**
     * Reverses a posted financial ledger transaction by posting an explicit compensating `REVERSAL` entry.
     */
    suspend fun reverseTransaction(
        shopId: String,
        originalTransactionId: String,
        reasonArabic: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<TransactionEntity> {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.LEDGER_REVERSE)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val original = ledgerDao.getTransactionById(originalTransactionId)
                    ?: throw AtomicRollbackException(AppResult.Error("القيد المالي الأصلي غير موجود", "TX_NOT_FOUND"))

                if (original.shopId != shopId) {
                    throw AtomicRollbackException(AppResult.Error("عدم تطابق المتجر للقيد المالي", "TENANT_MISMATCH"))
                }
                if (original.status == LedgerTransactionStatus.REVERSED) {
                    throw AtomicRollbackException(AppResult.Error("تم عكس هذا القيد مسبقاً", "ALREADY_REVERSED"))
                }
                if (original.transactionType == FinancialTransactionType.REVERSAL) {
                    throw AtomicRollbackException(AppResult.Error("لا يمكن عكس قيد عكسي مباشرة؛ أنشئ قيد تسوية جديد", "CANNOT_REVERSE_REVERSAL"))
                }

                val originalLines = ledgerDao.getLinesForTransaction(originalTransactionId)
                for (line in originalLines) {
                    if (line.signedImpactMinor > 0 &&
                        (line.accountType == FinancialAccountType.CASH_DRAWER || line.accountType == FinancialAccountType.WALLET)
                    ) {
                        val currentDerived = ledgerDao.getDerivedAccountBalanceMinor(shopId, line.accountType, line.accountId)
                        if (currentDerived < line.signedImpactMinor) {
                            throw AtomicRollbackException(
                                AppResult.Error(
                                    "لا يمكن عكس القيد لأن الحساب المستفيد لا يملك رصيداً كافياً لرد المبلغ",
                                    "INSUFFICIENT_BALANCE_FOR_REVERSAL"
                                )
                            )
                        }
                    }
                }

                val reversalTxId = IdGenerator.newPrefixedId("TX")
                val refNum = IdGenerator.newReferenceNumber("REV", timestamp)
                val hashInput = "$shopId|$refNum|REVERSAL|${original.amountMinor}|${original.localId}|$timestamp"
                val ledgerHash = IdGenerator.sha256Hex(hashInput)

                val originalJournal = AccountingEngine.decodeJournalEntryFromJson(original.metadataJson)
                val reversalJournalJson = if (originalJournal != null) {
                    val swappedLines = originalJournal.lines.map { line ->
                        line.copy(
                            lineId = IdGenerator.newPrefixedId("JRL"),
                            debitMinor = line.creditMinor,
                            creditMinor = line.debitMinor,
                            memoArabic = "عكس: ${line.memoArabic}"
                        )
                    }
                    val revJrnRes = AccountingEngine.validateAndBuildJournalEntry(
                        shopId = shopId,
                        ledgerTransactionId = reversalTxId,
                        referenceNumber = refNum,
                        transactionType = "REVERSAL",
                        lines = swappedLines,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (revJrnRes is AppResult.Error) {
                        throw AtomicRollbackException(revJrnRes)
                    }
                    AccountingEngine.encodeJournalEntryToJson((revJrnRes as AppResult.Success).data)
                } else {
                    val revJrnRes = AccountingEngine.buildGeneralLedgerJournalEntry(
                        shopId = shopId,
                        ledgerTransactionId = reversalTxId,
                        referenceNumber = refNum,
                        transactionType = FinancialTransactionType.REVERSAL,
                        amountMinor = original.amountMinor,
                        feeMinor = original.feeMinor,
                        commissionMinor = original.commissionMinor,
                        sourceAccountType = original.destinationAccountType,
                        sourceAccountId = original.destinationAccountId,
                        destinationAccountType = original.sourceAccountType,
                        destinationAccountId = original.sourceAccountId,
                        customerId = original.customerId,
                        supplierId = original.supplierId,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (revJrnRes is AppResult.Error) {
                        throw AtomicRollbackException(revJrnRes)
                    }
                    AccountingEngine.encodeJournalEntryToJson((revJrnRes as AppResult.Success).data)
                }

                val reversalTx = TransactionEntity(
                    localId = reversalTxId,
                    shopId = shopId,
                    referenceNumber = refNum,
                    transactionType = FinancialTransactionType.REVERSAL,
                    amountMinor = original.amountMinor,
                    feeMinor = original.feeMinor,
                    commissionMinor = original.commissionMinor,
                    currency = original.currency,
                    sourceAccountType = original.destinationAccountType,
                    sourceAccountId = original.destinationAccountId,
                    destinationAccountType = original.sourceAccountType,
                    destinationAccountId = original.sourceAccountId,
                    customerId = original.customerId,
                    supplierId = original.supplierId,
                    relatedEntityType = "REVERSAL",
                    relatedEntityId = original.localId,
                    reversalOfTransactionId = original.localId,
                    status = LedgerTransactionStatus.POSTED,
                    descriptionArabic = "قيد عكسي للقيد (${original.referenceNumber}): $reasonArabic",
                    metadataJson = reversalJournalJson,
                    ledgerHashSha256 = ledgerHash,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    transactionTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                val compensatingLines = originalLines.map { line ->
                    val invertedDirection = if (line.direction == "INFLOW") "OUTFLOW" else "INFLOW"
                    TransactionLineEntity(
                        localId = IdGenerator.newPrefixedId("TXL"),
                        shopId = shopId,
                        transactionId = reversalTxId,
                        accountType = line.accountType,
                        accountId = line.accountId,
                        direction = invertedDirection,
                        amountMinor = line.amountMinor,
                        signedImpactMinor = -line.signedImpactMinor,
                        currency = line.currency,
                        lineNote = "عكس سطر القيد ${original.referenceNumber}: $reasonArabic",
                        lineTimestamp = timestamp,
                        deviceId = deviceId,
                        createdAt = timestamp
                    )
                }

                ledgerDao.insertTransaction(reversalTx)
                ledgerDao.insertTransactionLines(compensatingLines)
                val updatedRows = ledgerDao.markTransactionReversed(original.localId, reversalTxId, timestamp)
                if (updatedRows != 1) {
                    throw AtomicRollbackException(AppResult.Error("تعذر تحديث حالة القيد الأصلي", "REVERSAL_STATE_ERROR"))
                }

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "LEDGER_REVERSAL",
                        entityType = "FinancialTransaction",
                        entityId = reversalTxId,
                        summaryArabic = "إصدار قيد عكسي ($refNum) لإلغاء أثر القيد (${original.referenceNumber}) — السبب: $reasonArabic",
                        beforeStateJson = """{"originalTxId":"${original.localId}","status":"POSTED"}""",
                        afterStateJson = """{"originalTxId":"${original.localId}","status":"REVERSED","reversalTxId":"$reversalTxId"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "FinancialTransaction",
                    entityLocalId = reversalTxId,
                    operationType = "LEDGER_POST",
                    entityVersion = 1L,
                    payloadJson = """{"txId":"$reversalTxId","reversalOf":"${original.localId}","amountMinor":${original.amountMinor}}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(reversalTx)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("فشل تنفيذ القيد العكسي الذري: ${e.message}", "ATOMIC_REVERSAL_FAILED", e)
        }
    }

    /**
     * ATOMIC SALE OPERATION (Requirement 8, 11, 51):
     * Supports full payment, partial down-payment, or 100% credit sale (tracking outstanding receivable on Customer in Ledger!).
     */
    suspend fun executeAtomicSale(
        shopId: String,
        customerId: String?,
        items: List<SaleLineInput>,
        invoiceDiscountMinor: Long = 0L,
        taxBasisPoints: Int = 0,
        paidAmountMinorOverride: Long? = null,
        paymentAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER, // CASH_DRAWER or WALLET
        paymentAccountId: String = "MAIN_DRAWER",
        splitPayments: List<SalePaymentSplit> = emptyList(),
        notes: String = "",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null,
        allowCreditLimitOverride: Boolean = false
    ): AppResult<SaleEntity> = executionMutex.withLock {
        if (items.isEmpty()) {
            return AppResult.Error("لا يمكن إتمام فاتورة بيع بدون أصناف", "EMPTY_SALE_ITEMS")
        }
        if (splitPayments.isEmpty() && paymentAccountType != FinancialAccountType.CASH_DRAWER && paymentAccountType != FinancialAccountType.WALLET) {
            return AppResult.Error("حساب التحصيل يجب أن يكون خزينة أو محفظة إلكترونية", "INVALID_PAYMENT_ACCOUNT")
        }

        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.SALE_CREATE)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val saleId = IdGenerator.newPrefixedId("SAL")
                val invoiceNumber = IdGenerator.newReferenceNumber("INV-S", timestamp)
                val preparedSaleItems = mutableListOf<SaleItemEntity>()
                val grossLineMoneyTotals = mutableListOf<Money>()
                var totalLineDiscountsMinor = 0L
                var totalCogsMinor = 0L

                for (line in items) {
                    if (line.quantity <= 0 || line.unitSellingPriceMinor <= 0L) {
                        throw AtomicRollbackException(
                            AppResult.Error("الكمية وسعر البيع يجب أن يكونا أكبر من الصفر", "INVALID_SALE_LINE")
                        )
                    }
                    if (line.lineDiscountMinor < 0L) {
                        throw AtomicRollbackException(
                            AppResult.Error("قيمة الخصم لا يمكن أن تكون سالبة", "INVALID_LINE_DISCOUNT")
                        )
                    }
                    val grossLineMinor = Math.multiplyExact(line.unitSellingPriceMinor, line.quantity.toLong())
                    val netLineMinor = Math.subtractExact(
                        grossLineMinor,
                        line.lineDiscountMinor
                    )
                    if (netLineMinor <= 0L) {
                        throw AtomicRollbackException(
                            AppResult.Error("إجمالي السطر بعد الخصم يجب أن يكون أكبر من الصفر", "INVALID_LINE_TOTAL")
                        )
                    }

                    val movementRes = inventoryEngine.recordMovementInternal(
                        shopId = shopId,
                        productId = line.productId,
                        variantId = line.variantId,
                        serialNumberId = line.serialNumberId,
                        movementType = InventoryMovementType.SALE_OUT,
                        quantity = line.quantity,
                        reason = "فاتورة مبيعات رقم $invoiceNumber",
                        referenceType = "SALE",
                        referenceId = saleId,
                        performedByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (movementRes is AppResult.Error) {
                        throw AtomicRollbackException(movementRes)
                    }
                    val movement = (movementRes as AppResult.Success).data

                    var serialImeiSnapshot: String? = null
                    if (line.serialNumberId != null) {
                        val serialRes = inventoryEngine.transitionSerialStatusInternal(
                            shopId = shopId,
                            serialId = line.serialNumberId,
                            newStatus = SerialLifecycleStatus.SOLD,
                            movementId = movement.localId,
                            customerId = customerId,
                            expectedProductId = line.productId,
                            timestamp = timestamp
                        )
                        if (serialRes is AppResult.Error) {
                            throw AtomicRollbackException(serialRes)
                        }
                        serialImeiSnapshot = (serialRes as AppResult.Success).data.serialOrImei
                    }

                    totalCogsMinor = Math.addExact(totalCogsMinor, movement.totalCostMinor)
                    totalLineDiscountsMinor = Math.addExact(totalLineDiscountsMinor, line.lineDiscountMinor)
                    grossLineMoneyTotals.add(Money.ofMinor(grossLineMinor))

                    preparedSaleItems.add(
                        SaleItemEntity(
                            localId = IdGenerator.newPrefixedId("SLI"),
                            shopId = shopId,
                            saleId = saleId,
                            productId = line.productId,
                            variantId = line.variantId,
                            serialNumberId = line.serialNumberId,
                            serialOrImei = serialImeiSnapshot,
                            quantity = line.quantity,
                            unitSellingPriceMinor = line.unitSellingPriceMinor,
                            unitCostAtSaleMinor = movement.unitCostMinor,
                            discountMinor = line.lineDiscountMinor,
                            lineTotalMinor = netLineMinor,
                            inventoryMovementId = movement.localId,
                            deviceId = deviceId,
                            createdAt = timestamp,
                            updatedAt = timestamp
                        )
                    )
                }

                val combinedDiscountMinor = Math.addExact(totalLineDiscountsMinor, invoiceDiscountMinor.coerceAtLeast(0L))
                val breakdown = MonetaryCalculator.calculateInvoiceBreakdown(
                    lineTotals = grossLineMoneyTotals,
                    fixedDiscountMinor = combinedDiscountMinor,
                    taxBasisPoints = taxBasisPoints
                )

                val finalPayableMinor = breakdown.finalPayableTotal.amountMinor
                if (finalPayableMinor <= 0L) {
                    throw AtomicRollbackException(
                        AppResult.Error("صافي الفاتورة يجب أن يكون أكبر من الصفر", "ZERO_INVOICE_TOTAL")
                    )
                }

                val actualPaidMinor = (paidAmountMinorOverride ?: finalPayableMinor).coerceIn(0L, finalPayableMinor)
                val unpaidCreditMinor = finalPayableMinor - actualPaidMinor

                if (unpaidCreditMinor > 0L && customerId.isNullOrBlank()) {
                    throw AtomicRollbackException(
                        AppResult.Error(
                            "لا يمكن البيع الآجل أو الجزئي لعميل نقدي بدون اختيار عميل مسجل",
                            "CREDIT_SALE_REQUIRES_CUSTOMER"
                        )
                    )
                }

                if (unpaidCreditMinor > 0L && !customerId.isNullOrBlank()) {
                    val customer = catalogDao.getCustomerById(customerId)
                        ?: throw AtomicRollbackException(AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND"))
                    if (!allowCreditLimitOverride && customer.creditLimitMinor > 0L) {
                        val currentDebtMinor = ledgerDao.getDerivedAccountBalanceMinor(
                            shopId = shopId,
                            accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                            accountId = customerId
                        )
                        if (currentDebtMinor + unpaidCreditMinor > customer.creditLimitMinor) {
                            throw AtomicRollbackException(
                                AppResult.Error(
                                    "تجاوز الحد الائتماني المسموح للعميل (${Money.ofMinor(customer.creditLimitMinor).formatArabic()})",
                                    "CREDIT_LIMIT_EXCEEDED"
                                )
                            )
                        }
                    }
                }

                // Build and validate Double-Entry Sale + COGS/Inventory Journal Entry (Requirement 21)
                val saleJournalRes = AccountingEngine.buildSaleJournalEntry(
                    shopId = shopId,
                    ledgerTransactionId = saleId,
                    referenceNumber = invoiceNumber,
                    totalSaleAmountMinor = finalPayableMinor,
                    paidCashAmountMinor = actualPaidMinor,
                    totalCogsMinor = totalCogsMinor,
                    customerId = customerId,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp
                )
                if (saleJournalRes is AppResult.Error) {
                    throw AtomicRollbackException(saleJournalRes)
                }
                val saleJournalJson = AccountingEngine.encodeJournalEntryToJson((saleJournalRes as AppResult.Success).data)

                if (splitPayments.isNotEmpty()) {
                    val totalSplit = splitPayments.sumOf { it.amountMinor }
                    if (totalSplit != actualPaidMinor) {
                        throw AtomicRollbackException(
                            AppResult.Error(
                                "مجموع مبالغ الدفع المجزأة (${Money.ofMinor(totalSplit).formatArabic()}) لا يطابق المبلغ المدفوع (${Money.ofMinor(actualPaidMinor).formatArabic()})",
                                "SPLIT_PAYMENT_MISMATCH"
                            )
                        )
                    }
                    if (splitPayments.any { it.amountMinor <= 0L }) {
                        throw AtomicRollbackException(
                            AppResult.Error("مبالغ وسائل الدفع يجب أن تكون أكبر من الصفر", "INVALID_SPLIT_AMOUNT")
                        )
                    }
                }

                val ledgerRes = if (actualPaidMinor == 0L && !customerId.isNullOrBlank()) {
                    // 100% Credit Sale: post full invoice receivable onto EXTERNAL_CUSTOMER without touching Cash/Wallet
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.SALE,
                        amountMinor = finalPayableMinor,
                        sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        sourceAccountId = "CREDIT_SALE_REVENUE",
                        destinationAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        destinationAccountId = customerId,
                        customerId = customerId,
                        relatedEntityType = "SALE",
                        relatedEntityId = saleId,
                        descriptionArabic = "فاتورة مبيعات آجلة رقم $invoiceNumber (مديونية على العميل)",
                        metadataJson = saleJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        idempotencyKey = idempotencyKey
                    )
                } else if (splitPayments.isNotEmpty()) {
                    // Split Payment: post first split as primary destination, and subsequent splits as additional lines
                    val firstSplit = splitPayments[0]
                    val subsequentSplits = splitPayments.drop(1).flatMap { split ->
                        listOf(
                            Triple(split.accountType, split.accountId, split.amountMinor),
                            Triple(FinancialAccountType.EXTERNAL_CUSTOMER, customerId ?: "WALK_IN_CUSTOMER", -split.amountMinor)
                        )
                    }
                    val extraCustomerChargeLine = if (!customerId.isNullOrBlank()) {
                        listOf(Triple(FinancialAccountType.EXTERNAL_CUSTOMER, customerId, finalPayableMinor))
                    } else {
                        emptyList()
                    }
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.SALE,
                        amountMinor = firstSplit.amountMinor,
                        sourceAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        sourceAccountId = customerId ?: "WALK_IN_CUSTOMER",
                        destinationAccountType = firstSplit.accountType,
                        destinationAccountId = firstSplit.accountId,
                        customerId = customerId,
                        relatedEntityType = "SALE",
                        relatedEntityId = saleId,
                        descriptionArabic = "تحصيل فاتورة مبيعات مجزأة رقم $invoiceNumber (مجموع الدفع: ${Money.ofMinor(actualPaidMinor).formatArabic()})",
                        metadataJson = saleJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        additionalLines = extraCustomerChargeLine + subsequentSplits,
                        idempotencyKey = idempotencyKey
                    )
                } else {
                    // Full or Partial Payment: collect actualPaidMinor into Cash/Wallet and record full invoice charge on Customer if registered
                    val extraCustomerChargeLine = if (!customerId.isNullOrBlank()) {
                        listOf(Triple(FinancialAccountType.EXTERNAL_CUSTOMER, customerId, finalPayableMinor))
                    } else {
                        emptyList()
                    }
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.SALE,
                        amountMinor = actualPaidMinor,
                        sourceAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        sourceAccountId = customerId ?: "WALK_IN_CUSTOMER",
                        destinationAccountType = paymentAccountType,
                        destinationAccountId = paymentAccountId,
                        customerId = customerId,
                        relatedEntityType = "SALE",
                        relatedEntityId = saleId,
                        descriptionArabic = if (unpaidCreditMinor > 0L) {
                            "تحصيل جزئي لفاتورة مبيعات رقم $invoiceNumber (متبقي آجل: ${Money.ofMinor(unpaidCreditMinor).formatArabic()})"
                        } else {
                            "تحصيل فاتورة مبيعات رقم $invoiceNumber"
                        },
                        metadataJson = saleJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        additionalLines = extraCustomerChargeLine,
                        idempotencyKey = idempotencyKey
                    )
                }
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val ledgerTx = (ledgerRes as AppResult.Success).data

                val grossProfitMinor = breakdown.netTaxableAmount.amountMinor - totalCogsMinor
                val saleStatus = when {
                    actualPaidMinor == 0L -> "CREDIT"
                    unpaidCreditMinor > 0L -> "PARTIALLY_PAID"
                    else -> "COMPLETED"
                }

                val saleEntity = SaleEntity(
                    localId = saleId,
                    shopId = shopId,
                    customerId = customerId,
                    invoiceNumber = invoiceNumber,
                    subtotalMinor = breakdown.grossSubtotal.amountMinor,
                    discountMinor = breakdown.discountAmount.amountMinor,
                    taxMinor = breakdown.taxAmount.amountMinor,
                    totalAmountMinor = finalPayableMinor,
                    paidAmountMinor = actualPaidMinor,
                    totalCostOfGoodsMinor = totalCogsMinor,
                    grossProfitMinor = grossProfitMinor,
                    paymentAccountType = if (splitPayments.isNotEmpty()) "SPLIT" else paymentAccountType.name,
                    paymentAccountId = if (splitPayments.isNotEmpty()) {
                        splitPayments.joinToString(" + ") { "${it.methodLabelArabic.ifBlank { it.accountType.name }}: ${Money.ofMinor(it.amountMinor).formatArabic()}" }
                    } else {
                        paymentAccountId
                    },
                    ledgerTransactionId = ledgerTx.localId,
                    status = saleStatus,
                    notes = notes,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    saleTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                operationsDao.insertSale(saleEntity)
                operationsDao.insertSaleItems(preparedSaleItems)

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "SALE",
                        entityType = "Sale",
                        entityId = saleId,
                        summaryArabic = "إتمام فاتورة مبيعات ($invoiceNumber) بقيمة ${breakdown.finalPayableTotal.formatArabic()} (المدفوع: ${Money.ofMinor(actualPaidMinor).formatArabic()})",
                        beforeStateJson = null,
                        afterStateJson = """{"saleId":"$saleId","invoice":"$invoiceNumber","totalMinor":${saleEntity.totalAmountMinor},"paidMinor":$actualPaidMinor,"ledgerTxId":"${ledgerTx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Sale",
                    entityLocalId = saleId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"saleId":"$saleId","invoice":"$invoiceNumber","totalMinor":${saleEntity.totalAmountMinor},"paidMinor":$actualPaidMinor}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(saleEntity)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تم إلغاء عملية البيع الذرية بالكامل لمنع تضارب البيانات: ${e.message}", "ATOMIC_SALE_ROLLED_BACK", e)
        }
    }

    /**
     * ATOMIC CUSTOMER DEBT COLLECTION (`تحصيل دفعة من مديونية عميل`):
     * Reduces the customer's derived receivable balance in `transaction_lines` and increases Cash Drawer or Wallet balance.
     */
    suspend fun executeCustomerPaymentCollection(
        shopId: String,
        customerId: String,
        amountMinor: Long,
        destinationAccountType: FinancialAccountType, // CASH_DRAWER or WALLET
        destinationAccountId: String,
        notes: String = "",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<TransactionEntity> = executionMutex.withLock {
        if (amountMinor <= 0L) {
            return@withLock AppResult.Error("مبلغ التحصيل يجب أن يكون أكبر من الصفر", "INVALID_COLLECTION_AMOUNT")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.SALE_CREATE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val customer = catalogDao.getCustomerById(customerId)
                    ?: throw AtomicRollbackException(AppResult.Error("العميل غير موجود", "CUSTOMER_NOT_FOUND"))

                val currentCustomerDebtMinor = ledgerDao.getDerivedAccountBalanceMinor(
                    shopId = shopId,
                    accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                    accountId = customerId
                )
                if (currentCustomerDebtMinor <= 0L) {
                    throw AtomicRollbackException(
                        AppResult.Error("لا توجد مديونية قائمة على العميل لتحصيلها", "NO_OUTSTANDING_DEBT")
                    )
                }
                if (amountMinor > currentCustomerDebtMinor) {
                    throw AtomicRollbackException(
                        AppResult.Error(
                            "مبلغ التحصيل (${Money.ofMinor(amountMinor).formatArabic()}) يتجاوز المديونية القائمة على العميل (${Money.ofMinor(currentCustomerDebtMinor).formatArabic()})",
                            "OVERPAYMENT_NOT_ALLOWED"
                        )
                    )
                }

                val txType = if (destinationAccountType == FinancialAccountType.CASH_DRAWER) {
                    FinancialTransactionType.CASH_IN
                } else {
                    FinancialTransactionType.DEPOSIT
                }

                val ledgerRes = postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = txType,
                    amountMinor = amountMinor,
                    sourceAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                    sourceAccountId = customerId,
                    destinationAccountType = destinationAccountType,
                    destinationAccountId = destinationAccountId,
                    customerId = customerId,
                    relatedEntityType = "CUSTOMER_COLLECTION",
                    relatedEntityId = customerId,
                    descriptionArabic = "تحصيل دفعة حساب من العميل (${customer.fullName}): $notes".trim(),
                    metadataJson = """{"customerId":"$customerId","amountMinor":$amountMinor}""",
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    idempotencyKey = idempotencyKey
                )
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val tx = (ledgerRes as AppResult.Success).data

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "CUSTOMER_COLLECTION",
                        entityType = "FinancialTransaction",
                        entityId = tx.localId,
                        summaryArabic = "تحصيل مديونية من العميل (${customer.fullName}) بقيمة ${Money.ofMinor(amountMinor).formatArabic()}",
                        beforeStateJson = null,
                        afterStateJson = """{"txId":"${tx.localId}","customerId":"$customerId","amountMinor":$amountMinor}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                AppResult.Success(tx)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("فشل تسجيل تحصيل دفعة العميل: ${e.message}", "CUSTOMER_COLLECTION_ERROR", e)
        }
    }

    /**
     * ATOMIC SALES RETURN / REFUND OPERATION (Requirement 9):
     * - Links to `originalSaleId`
     * - Verifies product, quantity, IMEI, and refundable amount
     * - Restores returned stock via `InventoryMovementType.RETURN_IN` at original sale cost
     * - Updates serialized IMEI back to `IN_STOCK` (or `RETURNED`)
     * - Posts a compensating `FinancialTransactionType.REFUND` ledger transaction
     * - Never deletes the original Sale or original Ledger entry!
     */
    suspend fun executeAtomicSalesReturn(
        shopId: String,
        originalSaleId: String,
        reasonArabic: String,
        refundAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        refundAccountId: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<RefundEntity> = executionMutex.withLock {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.REFUND_APPROVE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val sale = operationsDao.getSaleById(originalSaleId)
                    ?: throw AtomicRollbackException(AppResult.Error("فاتورة البيع الأصلية غير موجودة", "SALE_NOT_FOUND"))

                if (sale.status == "REFUNDED") {
                    throw AtomicRollbackException(
                        AppResult.Error("تم عمل مرتجع كامل لهذه الفاتورة مسبقاً", "SALE_ALREADY_REFUNDED")
                    )
                }

                val alreadyRefunded = operationsDao.getTotalRefundedForSaleMinor(originalSaleId)
                val refundableAmountMinor = sale.totalAmountMinor - alreadyRefunded
                if (refundableAmountMinor <= 0L) {
                    throw AtomicRollbackException(
                        AppResult.Error("لا يوجد مبلغ متبقٍ قابل للاسترداد في هذه الفاتورة", "NOTHING_TO_REFUND")
                    )
                }

                val saleItems = operationsDao.getSaleItems(originalSaleId)
                val refundId = IdGenerator.newPrefixedId("RFD")
                val refundNumber = IdGenerator.newReferenceNumber("RFD", timestamp)

                // Restore each sold item to inventory via RETURN_IN movement and restore IMEI status
                for (item in saleItems) {
                    val movRes = inventoryEngine.recordMovementInternal(
                        shopId = shopId,
                        productId = item.productId,
                        variantId = item.variantId,
                        serialNumberId = item.serialNumberId,
                        movementType = InventoryMovementType.RETURN_IN,
                        quantity = item.quantity,
                        inboundUnitCostMinor = item.unitCostAtSaleMinor,
                        reason = "مرتجع مبيعات رقم $refundNumber للفاتورة ${sale.invoiceNumber}",
                        referenceType = "REFUND",
                        referenceId = refundId,
                        performedByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (movRes is AppResult.Error) {
                        throw AtomicRollbackException(movRes)
                    }
                    val mov = (movRes as AppResult.Success).data

                    if (item.serialNumberId != null) {
                        val serRes = inventoryEngine.transitionSerialStatusInternal(
                            shopId = shopId,
                            serialId = item.serialNumberId,
                            newStatus = SerialLifecycleStatus.IN_STOCK,
                            movementId = mov.localId,
                            customerId = null,
                            expectedProductId = item.productId,
                            timestamp = timestamp
                        )
                        if (serRes is AppResult.Error) {
                            throw AtomicRollbackException(serRes)
                        }
                    }
                }

                // Calculate how much of the refund cancels unpaid customer receivable vs refunds paid cash/wallet
                val unpaidOnSaleMinor = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
                val currentCustomerDebtMinor = if (!sale.customerId.isNullOrBlank()) {
                    ledgerDao.getDerivedAccountBalanceMinor(
                        shopId = shopId,
                        accountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        accountId = sale.customerId
                    ).coerceAtLeast(0L)
                } else {
                    0L
                }
                val receivableReductionMinor = if (!sale.customerId.isNullOrBlank() && unpaidOnSaleMinor > 0L) {
                    minOf(refundableAmountMinor, unpaidOnSaleMinor, currentCustomerDebtMinor)
                } else {
                    0L
                }
                val cashOutRefundMinor = (refundableAmountMinor - receivableReductionMinor).coerceAtLeast(0L)

                // Build and validate Double-Entry Refund + COGS/Inventory Reversal Journal Entry (Requirement 24)
                val restoredCogsMinor = saleItems.sumOf { Math.multiplyExact(it.unitCostAtSaleMinor, it.quantity.toLong()) }
                val refundJournalRes = AccountingEngine.buildRefundJournalEntry(
                    shopId = shopId,
                    ledgerTransactionId = refundId,
                    referenceNumber = refundNumber,
                    refundAmountMinor = refundableAmountMinor,
                    restoredCogsMinor = restoredCogsMinor,
                    customerId = sale.customerId,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    cashRefundAmountMinor = cashOutRefundMinor,
                    receivableReductionMinor = receivableReductionMinor
                )
                if (refundJournalRes is AppResult.Error) {
                    throw AtomicRollbackException(refundJournalRes)
                }
                val refundJournalJson = AccountingEngine.encodeJournalEntryToJson((refundJournalRes as AppResult.Success).data)

                // Post compensating REFUND transaction in the immutable ledger
                val ledgerRes = if (cashOutRefundMinor == 0L && !sale.customerId.isNullOrBlank()) {
                    // 100% Credit Sale Refund: reduce customer's receivable debt without touching Cash Drawer
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.REFUND,
                        amountMinor = receivableReductionMinor,
                        sourceAccountType = FinancialAccountType.EXTERNAL_CUSTOMER,
                        sourceAccountId = sale.customerId,
                        destinationAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        destinationAccountId = "CREDIT_SALE_RETURN",
                        customerId = sale.customerId,
                        relatedEntityType = "REFUND",
                        relatedEntityId = refundId,
                        descriptionArabic = "مرتجع مبيعات آجلة ($refundNumber) وتخفيض مديونية العميل للفاتورة (${sale.invoiceNumber}): $reasonArabic",
                        metadataJson = refundJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        idempotencyKey = idempotencyKey
                    )
                } else {
                    val extraLines = if (receivableReductionMinor > 0L && !sale.customerId.isNullOrBlank()) {
                        listOf(Triple(FinancialAccountType.EXTERNAL_CUSTOMER, sale.customerId, -receivableReductionMinor))
                    } else {
                        emptyList()
                    }
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.REFUND,
                        amountMinor = cashOutRefundMinor,
                        sourceAccountType = refundAccountType,
                        sourceAccountId = refundAccountId,
                        destinationAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        destinationAccountId = "SALES_RETURN_REFUND",
                        customerId = sale.customerId,
                        relatedEntityType = "REFUND",
                        relatedEntityId = refundId,
                        descriptionArabic = "مرتجع مبيعات ($refundNumber) للفاتورة الأصلية (${sale.invoiceNumber}): $reasonArabic",
                        metadataJson = refundJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        additionalLines = extraLines,
                        idempotencyKey = idempotencyKey
                    )
                }
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val ledgerTx = (ledgerRes as AppResult.Success).data

                val refundEntity = RefundEntity(
                    localId = refundId,
                    shopId = shopId,
                    originalSaleId = originalSaleId,
                    refundNumber = refundNumber,
                    refundAmountMinor = refundableAmountMinor,
                    reason = reasonArabic.ifBlank { "مرتجع مبيعات معتمد" },
                    refundAccountType = refundAccountType.name,
                    refundAccountId = refundAccountId,
                    ledgerTransactionId = ledgerTx.localId,
                    approvedByUserId = actorUserId,
                    deviceId = deviceId,
                    refundTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
                operationsDao.insertRefund(refundEntity)

                // Mark original sale as REFUNDED without deleting it
                operationsDao.updateSale(
                    sale.copy(
                        status = "REFUNDED",
                        updatedAt = timestamp,
                        version = sale.version + 1L,
                        syncState = SyncState.PENDING
                    )
                )

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "REFUND",
                        entityType = "Refund",
                        entityId = refundId,
                        summaryArabic = "اعتماد مرتجع مبيعات ($refundNumber) للفاتورة (${sale.invoiceNumber}) بمبلغ ${Money.ofMinor(refundableAmountMinor).formatArabic()}",
                        beforeStateJson = """{"saleId":"$originalSaleId","status":"COMPLETED"}""",
                        afterStateJson = """{"saleId":"$originalSaleId","status":"REFUNDED","refundId":"$refundId","ledgerTxId":"${ledgerTx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Refund",
                    entityLocalId = refundId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"refundId":"$refundId","originalSaleId":"$originalSaleId","amountMinor":$refundableAmountMinor}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(refundEntity)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تعذر تنفيذ مرتجع المبيعات الذري: ${e.message}", "ATOMIC_REFUND_ERROR", e)
        }
    }

    /**
     * ATOMIC PURCHASE OPERATION (Requirement 10, 11, 12, 13, 14):
     * Supports full or partial payment (`paidAmountMinor <= totalAmountMinor`) and updates WAC + Serials + Ledger.
     */
    suspend fun executeAtomicPurchase(
        shopId: String,
        supplierId: String,
        items: List<PurchaseLineInput>,
        discountMinor: Long = 0L,
        paidAmountMinorOverride: Long? = null,
        paymentAccountType: FinancialAccountType, // CASH_DRAWER or WALLET
        paymentAccountId: String,
        notes: String = "",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<PurchaseEntity> = executionMutex.withLock {
        if (items.isEmpty()) {
            return@withLock AppResult.Error("لا يمكن تسجيل فاتورة مشتريات بدون أصناف", "EMPTY_PURCHASE_ITEMS")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PURCHASE_CREATE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val purchaseId = IdGenerator.newPrefixedId("PUR")
                val invoiceNumber = IdGenerator.newReferenceNumber("INV-P", timestamp)
                val preparedPurchaseItems = mutableListOf<PurchaseItemEntity>()
                val lineTotals = mutableListOf<Money>()

                for (line in items) {
                    if (line.quantity <= 0 || line.unitCostMinor <= 0L) {
                        throw AtomicRollbackException(
                            AppResult.Error("الكمية وتكلفة الشراء يجب أن تكونا أكبر من الصفر", "INVALID_PURCHASE_LINE")
                        )
                    }
                    val product = catalogDao.getProductById(line.productId)
                        ?: throw AtomicRollbackException(AppResult.Error("الصنف غير موجود", "PRODUCT_NOT_FOUND"))

                    if (product.isSerialized) {
                        if (line.serialNumbersOrImeis.size != line.quantity) {
                            throw AtomicRollbackException(
                                AppResult.Error(
                                    "الصنف (${product.nameArabic}) يتطلب إدخال ${line.quantity} سيريال/IMEI بالضبط",
                                    "SERIAL_COUNT_MISMATCH"
                                )
                            )
                        }
                        for (rawSerial in line.serialNumbersOrImeis) {
                            val serialRes = inventoryEngine.registerInboundSerialInternal(
                                shopId = shopId,
                                productId = line.productId,
                                variantId = line.variantId,
                                serialOrImei = rawSerial,
                                acquisitionCostMinor = line.unitCostMinor,
                                deviceId = deviceId,
                                timestamp = timestamp
                            )
                            if (serialRes is AppResult.Error) {
                                throw AtomicRollbackException(serialRes)
                            }
                            val serialEntity = (serialRes as AppResult.Success).data

                            val movRes = inventoryEngine.recordMovementInternal(
                                shopId = shopId,
                                productId = line.productId,
                                variantId = line.variantId,
                                serialNumberId = serialEntity.localId,
                                movementType = InventoryMovementType.PURCHASE_IN,
                                quantity = 1,
                                inboundUnitCostMinor = line.unitCostMinor,
                                reason = "توريد مشتريات $invoiceNumber (IMEI: ${serialEntity.serialOrImei})",
                                referenceType = "PURCHASE",
                                referenceId = purchaseId,
                                performedByUserId = actorUserId,
                                deviceId = deviceId,
                                timestamp = timestamp
                            )
                            if (movRes is AppResult.Error) {
                                throw AtomicRollbackException(movRes)
                            }
                            val movement = (movRes as AppResult.Success).data

                            preparedPurchaseItems.add(
                                PurchaseItemEntity(
                                    localId = IdGenerator.newPrefixedId("PRI"),
                                    shopId = shopId,
                                    purchaseId = purchaseId,
                                    productId = line.productId,
                                    variantId = line.variantId,
                                    serialNumberId = serialEntity.localId,
                                    serialOrImei = serialEntity.serialOrImei,
                                    quantity = 1,
                                    unitCostMinor = line.unitCostMinor,
                                    lineTotalMinor = line.unitCostMinor,
                                    inventoryMovementId = movement.localId,
                                    deviceId = deviceId,
                                    createdAt = timestamp,
                                    updatedAt = timestamp
                                )
                            )
                        }
                    } else {
                        val movRes = inventoryEngine.recordMovementInternal(
                            shopId = shopId,
                            productId = line.productId,
                            variantId = line.variantId,
                            movementType = InventoryMovementType.PURCHASE_IN,
                            quantity = line.quantity,
                            inboundUnitCostMinor = line.unitCostMinor,
                            reason = "توريد مشتريات فاتورة $invoiceNumber",
                            referenceType = "PURCHASE",
                            referenceId = purchaseId,
                            performedByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp
                        )
                        if (movRes is AppResult.Error) {
                            throw AtomicRollbackException(movRes)
                        }
                        val movement = (movRes as AppResult.Success).data
                        val lineTotalMinor = Math.multiplyExact(line.unitCostMinor, line.quantity.toLong())
                        preparedPurchaseItems.add(
                            PurchaseItemEntity(
                                localId = IdGenerator.newPrefixedId("PRI"),
                                shopId = shopId,
                                purchaseId = purchaseId,
                                productId = line.productId,
                                variantId = line.variantId,
                                quantity = line.quantity,
                                unitCostMinor = line.unitCostMinor,
                                lineTotalMinor = lineTotalMinor,
                                inventoryMovementId = movement.localId,
                                deviceId = deviceId,
                                createdAt = timestamp,
                                updatedAt = timestamp
                            )
                        )
                    }

                    lineTotals.add(Money.ofMinor(Math.multiplyExact(line.unitCostMinor, line.quantity.toLong())))
                }

                val breakdown = MonetaryCalculator.calculateInvoiceBreakdown(
                    lineTotals = lineTotals,
                    fixedDiscountMinor = discountMinor
                )
                val totalPayableMinor = breakdown.finalPayableTotal.amountMinor
                val actualPaidMinor = (paidAmountMinorOverride ?: totalPayableMinor).coerceIn(0L, totalPayableMinor)

                val purchaseJournalRes = AccountingEngine.buildPurchaseJournalEntry(
                    shopId = shopId,
                    ledgerTransactionId = purchaseId,
                    referenceNumber = invoiceNumber,
                    totalPurchaseAmountMinor = totalPayableMinor,
                    paidCashAmountMinor = actualPaidMinor,
                    supplierId = supplierId,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp
                )
                if (purchaseJournalRes is AppResult.Error) {
                    throw AtomicRollbackException(purchaseJournalRes)
                }
                val purchaseJournalJson = AccountingEngine.encodeJournalEntryToJson((purchaseJournalRes as AppResult.Success).data)

                val ledgerRes = if (actualPaidMinor == 0L) {
                    // 100% Credit Purchase: post full payable without touching Cash Drawer / Wallet
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.PURCHASE,
                        amountMinor = totalPayableMinor,
                        sourceAccountType = FinancialAccountType.EXTERNAL_SUPPLIER,
                        sourceAccountId = supplierId,
                        destinationAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        destinationAccountId = "CREDIT_PURCHASE_INVENTORY",
                        supplierId = supplierId,
                        relatedEntityType = "PURCHASE_CREDIT",
                        relatedEntityId = purchaseId,
                        descriptionArabic = "فاتورة مشتريات آجلة رقم $invoiceNumber (مستحق للمورد)",
                        metadataJson = purchaseJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        idempotencyKey = idempotencyKey
                    )
                } else {
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.PURCHASE,
                        amountMinor = actualPaidMinor,
                        sourceAccountType = paymentAccountType,
                        sourceAccountId = paymentAccountId,
                        destinationAccountType = FinancialAccountType.EXTERNAL_SUPPLIER,
                        destinationAccountId = supplierId,
                        supplierId = supplierId,
                        relatedEntityType = "PURCHASE",
                        relatedEntityId = purchaseId,
                        descriptionArabic = "سداد فاتورة مشتريات رقم $invoiceNumber",
                        metadataJson = purchaseJournalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        idempotencyKey = idempotencyKey
                    )
                }
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val ledgerTx = (ledgerRes as AppResult.Success).data

                val purchaseEntity = PurchaseEntity(
                    localId = purchaseId,
                    shopId = shopId,
                    supplierId = supplierId,
                    invoiceNumber = invoiceNumber,
                    subtotalMinor = breakdown.grossSubtotal.amountMinor,
                    discountMinor = breakdown.discountAmount.amountMinor,
                    taxMinor = 0L,
                    totalAmountMinor = totalPayableMinor,
                    paidAmountMinor = actualPaidMinor,
                    paymentAccountType = paymentAccountType.name,
                    paymentAccountId = paymentAccountId,
                    ledgerTransactionId = ledgerTx.localId,
                    notes = notes,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    purchaseTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                operationsDao.insertPurchase(purchaseEntity)
                operationsDao.insertPurchaseItems(preparedPurchaseItems)

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "PURCHASE",
                        entityType = "Purchase",
                        entityId = purchaseId,
                        summaryArabic = "توريد فاتورة مشتريات ($invoiceNumber) بقيمة ${breakdown.finalPayableTotal.formatArabic()} (المدفوع: ${Money.ofMinor(actualPaidMinor).formatArabic()})",
                        beforeStateJson = null,
                        afterStateJson = """{"purchaseId":"$purchaseId","invoice":"$invoiceNumber","totalMinor":$totalPayableMinor,"paidMinor":$actualPaidMinor}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Purchase",
                    entityLocalId = purchaseId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"purchaseId":"$purchaseId","invoice":"$invoiceNumber","totalMinor":$totalPayableMinor}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(purchaseEntity)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تم التراجع عن عملية الشراء الذرية بالكامل: ${e.message}", "ATOMIC_PURCHASE_ROLLED_BACK", e)
        }
    }

    /**
     * ATOMIC PURCHASE RETURN TO SUPPLIER:
     * - Deducts returned stock via `InventoryMovementType.RETURN_OUT`
     * - Transitions any returned serial/IMEI to `SerialLifecycleStatus.RETURNED_TO_SUPPLIER`
     * - Reduces supplier payable (`outstandingPayableMinor`) and/or refunds cash into Cash Drawer/Wallet
     * - Posts a balanced Double-Entry JournalEntry (`DR Accounts Payable 2000` + `DR Cash 1000` == `CR Inventory 1200`)
     */
    suspend fun executeAtomicPurchaseReturn(
        shopId: String,
        originalPurchaseId: String,
        reasonArabic: String = "مرتجع مشتريات للمورد",
        refundAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        refundAccountId: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<TransactionEntity> = executionMutex.withLock {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.PURCHASE_CREATE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val purchase = operationsDao.getPurchaseById(originalPurchaseId)
                    ?: throw AtomicRollbackException(AppResult.Error("فاتورة المشتريات الأصلية غير موجودة", "PURCHASE_NOT_FOUND"))

                val existingReturnTx = ledgerDao.getAllTransactionsChronological(shopId).firstOrNull {
                    it.relatedEntityType == "PURCHASE_RETURN" &&
                        it.relatedEntityId == originalPurchaseId &&
                        it.status == LedgerTransactionStatus.POSTED
                }
                if (existingReturnTx != null) {
                    throw AtomicRollbackException(
                        AppResult.Error("تم عمل مرتجع كامل لفاتورة المشتريات هذه مسبقاً", "PURCHASE_ALREADY_RETURNED")
                    )
                }

                val purchaseItems = operationsDao.getPurchaseItems(originalPurchaseId)
                if (purchaseItems.isEmpty()) {
                    throw AtomicRollbackException(AppResult.Error("لا توجد أصناف في فاتورة المشتريات", "EMPTY_PURCHASE_ITEMS"))
                }

                val returnRef = IdGenerator.newReferenceNumber("PRT", timestamp)
                for (item in purchaseItems) {
                    val movRes = inventoryEngine.recordMovementInternal(
                        shopId = shopId,
                        productId = item.productId,
                        variantId = item.variantId,
                        serialNumberId = item.serialNumberId,
                        movementType = InventoryMovementType.RETURN_OUT,
                        quantity = item.quantity,
                        reason = "مرتجع مشتريات للمورد رقم $returnRef للفاتورة ${purchase.invoiceNumber}",
                        referenceType = "PURCHASE_RETURN",
                        referenceId = originalPurchaseId,
                        performedByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (movRes is AppResult.Error) {
                        throw AtomicRollbackException(movRes)
                    }
                    val mov = (movRes as AppResult.Success).data

                    if (item.serialNumberId != null) {
                        val serRes = inventoryEngine.transitionSerialStatusInternal(
                            shopId = shopId,
                            serialId = item.serialNumberId,
                            newStatus = SerialLifecycleStatus.RETURNED_TO_SUPPLIER,
                            movementId = mov.localId,
                            expectedProductId = item.productId,
                            timestamp = timestamp
                        )
                        if (serRes is AppResult.Error) {
                            throw AtomicRollbackException(serRes)
                        }
                    }
                }

                val returnTotalMinor = purchase.totalAmountMinor
                val currentSupplierPayableMinor = catalogDao.getDerivedSupplierPayableMinor(shopId, purchase.supplierId)
                    .coerceAtLeast(0L)
                val payableReductionMinor = minOf(returnTotalMinor, currentSupplierPayableMinor)
                val cashRefundInMinor = (returnTotalMinor - payableReductionMinor).coerceAtLeast(0L)

                val journalRes = AccountingEngine.buildPurchaseReturnJournalEntry(
                    shopId = shopId,
                    ledgerTransactionId = originalPurchaseId,
                    referenceNumber = returnRef,
                    returnTotalMinor = returnTotalMinor,
                    payableReductionMinor = payableReductionMinor,
                    cashRefundInMinor = cashRefundInMinor,
                    supplierId = purchase.supplierId,
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp
                )
                if (journalRes is AppResult.Error) {
                    throw AtomicRollbackException(journalRes)
                }
                val journalJson = AccountingEngine.encodeJournalEntryToJson((journalRes as AppResult.Success).data)

                val ledgerRes = if (cashRefundInMinor == 0L) {
                    // 100% Payable reduction on supplier account
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.ADJUSTMENT,
                        amountMinor = payableReductionMinor,
                        sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        sourceAccountId = "PURCHASE_RETURN_INVENTORY",
                        destinationAccountType = FinancialAccountType.EXTERNAL_SUPPLIER,
                        destinationAccountId = purchase.supplierId,
                        supplierId = purchase.supplierId,
                        relatedEntityType = "PURCHASE_RETURN",
                        relatedEntityId = originalPurchaseId,
                        descriptionArabic = "مرتجع مشتريات للمورد ($returnRef) وتخفيض المديونية المستحقة للفاتورة (${purchase.invoiceNumber}): $reasonArabic",
                        metadataJson = journalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        idempotencyKey = idempotencyKey
                    )
                } else {
                    val extraSupplierLines = if (payableReductionMinor > 0L) {
                        listOf(Triple(FinancialAccountType.EXTERNAL_SUPPLIER, purchase.supplierId, payableReductionMinor))
                    } else {
                        emptyList()
                    }
                    postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.CASH_IN,
                        amountMinor = cashRefundInMinor,
                        sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                        sourceAccountId = "PURCHASE_RETURN_CASH_REFUND",
                        destinationAccountType = refundAccountType,
                        destinationAccountId = refundAccountId,
                        supplierId = purchase.supplierId,
                        relatedEntityType = "PURCHASE_RETURN",
                        relatedEntityId = originalPurchaseId,
                        descriptionArabic = "مرتجع مشتريات للمورد ($returnRef) واسترداد نقدي للفاتورة (${purchase.invoiceNumber}): $reasonArabic",
                        metadataJson = journalJson,
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp,
                        additionalLines = extraSupplierLines,
                        idempotencyKey = idempotencyKey
                    )
                }
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val tx = (ledgerRes as AppResult.Success).data

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "PURCHASE_RETURN",
                        entityType = "Purchase",
                        entityId = originalPurchaseId,
                        summaryArabic = "مرتجع مشتريات للمورد ($returnRef) بقيمة ${Money.ofMinor(returnTotalMinor).formatArabic()} (تخفيض آجل: ${Money.ofMinor(payableReductionMinor).formatArabic()}، مسترد نقداً: ${Money.ofMinor(cashRefundInMinor).formatArabic()})",
                        beforeStateJson = """{"purchaseId":"$originalPurchaseId","totalMinor":$returnTotalMinor}""",
                        afterStateJson = """{"purchaseId":"$originalPurchaseId","payableReducedMinor":$payableReductionMinor,"cashRefundInMinor":$cashRefundInMinor,"ledgerTxId":"${tx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                AppResult.Success(tx)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تعذر تنفيذ مرتجع المشتريات الذري: ${e.message}", "ATOMIC_PURCHASE_RETURN_ERROR", e)
        }
    }

    /**
     * ATOMIC ELECTRONIC WALLET & TRANSFER OPERATION (Requirement 11, 12, 16):
     */
    suspend fun executeAtomicWalletTransfer(
        shopId: String,
        walletId: String,
        cashDrawerId: String = "DRAWER-MAIN-EGP",
        targetWalletId: String? = null,
        transferKind: String, // WALLET_SEND, WALLET_RECEIVE, WALLET_TOPUP, WALLET_TO_CASH, CASH_TO_WALLET
        customerPhone: String,
        principalAmountMinor: Long,
        providerFeeMinor: Long = 0L,
        shopCommissionMinor: Long = 0L,
        notes: String = "",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<TransferEntity> = executionMutex.withLock {
        if (principalAmountMinor <= 0L) {
            return@withLock AppResult.Error("مبلغ العملية يجب أن يكون أكبر من الصفر", "INVALID_AMOUNT")
        }
        if (providerFeeMinor < 0L || shopCommissionMinor < 0L) {
            return@withLock AppResult.Error("الرسوم والعمولات لا يمكن أن تكون سالبة", "INVALID_FEE")
        }
        if (walletId.isBlank()) {
            return@withLock AppResult.Error("يرجى اختيار المحفظة بشكل صريح أولاً", "MISSING_WALLET")
        }
        if (transferKind == "WALLET_SEND" || transferKind == "WALLET_RECEIVE") {
            val normalizedPhone = Money.normalizeDigits(customerPhone).trim()
            if (normalizedPhone.length < 8) {
                return@withLock AppResult.Error("يرجى إدخال رقم هاتف أو حساب عميل صحيح (8 أرقام على الأقل)", "INVALID_PHONE")
            }
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.WALLET_OPERATE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val wallet = ledgerDao.getWalletById(walletId)
                    ?: throw AtomicRollbackException(AppResult.Error("المحفظة الإلكترونية غير موجودة", "WALLET_NOT_FOUND"))
                if (wallet.shopId != shopId) {
                    throw AtomicRollbackException(AppResult.Error("المحفظة لا تنتمي للمتجر الحالي", "TENANT_MISMATCH"))
                }
                if (!wallet.isActive || wallet.operationalStatus != "ACTIVE") {
                    throw AtomicRollbackException(
                        AppResult.Error(
                            "المحفظة الإلكترونية معلقة أو غير نشطة (${wallet.operationalStatus}) ولا يمكن إجراء عمليات عليها",
                            "WALLET_INACTIVE_OR_SUSPENDED"
                        )
                    )
                }

                val opType = WalletOperationType.fromCodeOrNull(transferKind) ?: WalletOperationType.CUSTOMER_DEPOSIT

                val effectiveTargetWalletId = if (opType == WalletOperationType.WALLET_TRANSFER) {
                    val candidateTarget = (targetWalletId ?: cashDrawerId).trim()
                    if (candidateTarget.isBlank() || candidateTarget == walletId) {
                        throw AtomicRollbackException(
                            AppResult.Error("تحويل بين المحافظ يتطلب تحديد محفظة مستلمة صالحة ومختلفة عن محفظة الإرسال", "INVALID_TARGET_WALLET")
                        )
                    }
                    val targetWallet = ledgerDao.getWalletById(candidateTarget)
                        ?: throw AtomicRollbackException(AppResult.Error("محفظة الاستلام غير موجودة ($candidateTarget)", "TARGET_WALLET_NOT_FOUND"))
                    if (targetWallet.shopId != shopId) {
                        throw AtomicRollbackException(AppResult.Error("محفظة الاستلام لا تنتمي للمتجر الحالي", "TARGET_WALLET_TENANT_MISMATCH"))
                    }
                    if (!targetWallet.isActive || targetWallet.operationalStatus != "ACTIVE") {
                        throw AtomicRollbackException(
                            AppResult.Error(
                                "محفظة الاستلام معلقة أو غير نشطة (${targetWallet.operationalStatus}) ولا يمكن استلام تحويل عليها",
                                "TARGET_WALLET_INACTIVE_OR_SUSPENDED"
                            )
                        )
                    }
                    candidateTarget
                } else null

                if (opType != WalletOperationType.WALLET_TRANSFER && opType != WalletOperationType.WALLET_ADJUSTMENT) {
                    val drawer = ledgerDao.getCashDrawerById(cashDrawerId)
                        ?: throw AtomicRollbackException(AppResult.Error("درج النقدية المحدد غير موجود ($cashDrawerId)", "CASH_DRAWER_NOT_FOUND"))
                    if (drawer.shopId != shopId) {
                        throw AtomicRollbackException(AppResult.Error("درج النقدية لا ينتمي للمتجر الحالي", "DRAWER_TENANT_MISMATCH"))
                    }
                }

                if (opType == WalletOperationType.CUSTOMER_DEPOSIT || opType == WalletOperationType.WALLET_TO_CASH || opType == WalletOperationType.WALLET_TRANSFER) {
                    val (dayBounds, monthBounds) = computeDayAndMonthBounds(timestamp)
                    val requiredOutflow = principalAmountMinor + providerFeeMinor
                    if (wallet.dailyLimitMinor > 0L) {
                        val todayOutflow = ledgerDao.getWalletOutflowInRangeMinor(
                            shopId = shopId,
                            walletId = walletId,
                            startMillis = dayBounds.first,
                            endMillis = dayBounds.second
                        )
                        if (todayOutflow + requiredOutflow > wallet.dailyLimitMinor) {
                            throw AtomicRollbackException(
                                AppResult.Error(
                                    "المبلغ يتجاوز الحد اليومي المتبقي للمحفظة (${Money.ofMinor((wallet.dailyLimitMinor - todayOutflow).coerceAtLeast(0L)).formatArabic()})",
                                    "WALLET_DAILY_LIMIT_EXCEEDED"
                                )
                            )
                        }
                    }
                    if (wallet.monthlyLimitMinor > 0L) {
                        val monthOutflow = ledgerDao.getWalletOutflowInRangeMinor(
                            shopId = shopId,
                            walletId = walletId,
                            startMillis = monthBounds.first,
                            endMillis = monthBounds.second
                        )
                        if (monthOutflow + requiredOutflow > wallet.monthlyLimitMinor) {
                            throw AtomicRollbackException(
                                AppResult.Error(
                                    "المبلغ يتجاوز الحد الشهري المتبقي للمحفظة (${Money.ofMinor((wallet.monthlyLimitMinor - monthOutflow).coerceAtLeast(0L)).formatArabic()})",
                                    "WALLET_MONTHLY_LIMIT_EXCEEDED"
                                )
                            )
                        }
                    }
                }

                val transferId = IdGenerator.newPrefixedId("TRF")
                val refNumber = IdGenerator.newReferenceNumber("WLT", timestamp)

                val ledgerRes = when (opType) {
                    WalletOperationType.CUSTOMER_DEPOSIT -> {
                        // Customer Deposit: Cash Drawer receives (Principal + Commission), Shop Wallet sends (Principal + ProviderFee)
                        val totalCashIn = principalAmountMinor + shopCommissionMinor
                        val netProfit = (shopCommissionMinor - providerFeeMinor).coerceAtLeast(0L)
                        val extraLines = if (netProfit > 0L) {
                            listOf(Triple(FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT, "WALLET_COMMISSION_REVENUE", -netProfit))
                        } else emptyList()

                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.TRANSFER,
                            amountMinor = principalAmountMinor + providerFeeMinor,
                            feeMinor = 0L,
                            commissionMinor = shopCommissionMinor,
                            sourceAccountType = FinancialAccountType.WALLET,
                            sourceAccountId = walletId,
                            destinationAccountType = FinancialAccountType.CASH_DRAWER,
                            destinationAccountId = cashDrawerId,
                            relatedEntityType = "TRANSFER",
                            relatedEntityId = transferId,
                            descriptionArabic = "إيداع محفظة للعميل ($customerPhone) — مرجع $refNumber",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            additionalLines = if (totalCashIn != (principalAmountMinor + providerFeeMinor)) {
                                listOf(Triple(FinancialAccountType.CASH_DRAWER, cashDrawerId, totalCashIn - (principalAmountMinor + providerFeeMinor))) + extraLines
                            } else extraLines,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    WalletOperationType.CUSTOMER_WITHDRAWAL -> {
                        // Customer Withdrawal: Shop Wallet receives Principal, Cash Drawer pays out (Principal - Commission)
                        val netCashOut = (principalAmountMinor - shopCommissionMinor).coerceAtLeast(1L)
                        val netProfit = (shopCommissionMinor - providerFeeMinor).coerceAtLeast(0L)
                        val extraLines = mutableListOf<Triple<FinancialAccountType, String, Long>>()
                        // Destination wallet gets full principal
                        if (principalAmountMinor != netCashOut) {
                            extraLines.add(Triple(FinancialAccountType.WALLET, walletId, principalAmountMinor - netCashOut))
                        }
                        if (netProfit > 0L) {
                            extraLines.add(Triple(FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT, "WALLET_COMMISSION_REVENUE", -netProfit))
                        }

                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.WITHDRAW,
                            amountMinor = netCashOut,
                            feeMinor = providerFeeMinor,
                            commissionMinor = shopCommissionMinor,
                            sourceAccountType = FinancialAccountType.CASH_DRAWER,
                            sourceAccountId = cashDrawerId,
                            destinationAccountType = FinancialAccountType.WALLET,
                            destinationAccountId = walletId,
                            relatedEntityType = "TRANSFER",
                            relatedEntityId = transferId,
                            descriptionArabic = "سحب محفظة وارد من ($customerPhone) وصرف نقدية — مرجع $refNumber",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            additionalLines = extraLines,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    WalletOperationType.WALLET_TO_CASH -> {
                        // Internal Transfer from Wallet to Cash Drawer
                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.TRANSFER,
                            amountMinor = principalAmountMinor,
                            feeMinor = providerFeeMinor,
                            commissionMinor = 0L,
                            sourceAccountType = FinancialAccountType.WALLET,
                            sourceAccountId = walletId,
                            destinationAccountType = FinancialAccountType.CASH_DRAWER,
                            destinationAccountId = cashDrawerId,
                            relatedEntityType = "TRANSFER",
                            relatedEntityId = transferId,
                            descriptionArabic = "تسييل وتحويل داخلي من المحفظة إلى الخزينة — مرجع $refNumber",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    WalletOperationType.CASH_TO_WALLET -> {
                        // Internal Transfer from Cash Drawer to Wallet
                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.TRANSFER,
                            amountMinor = principalAmountMinor,
                            feeMinor = providerFeeMinor,
                            commissionMinor = 0L,
                            sourceAccountType = FinancialAccountType.CASH_DRAWER,
                            sourceAccountId = cashDrawerId,
                            destinationAccountType = FinancialAccountType.WALLET,
                            destinationAccountId = walletId,
                            relatedEntityType = "TRANSFER",
                            relatedEntityId = transferId,
                            descriptionArabic = "إيداع وتحويل داخلي من الخزينة إلى المحفظة — مرجع $refNumber",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    WalletOperationType.WALLET_TRANSFER -> {
                        // Internal Transfer between Wallets
                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.TRANSFER,
                            amountMinor = principalAmountMinor,
                            feeMinor = providerFeeMinor,
                            commissionMinor = 0L,
                            sourceAccountType = FinancialAccountType.WALLET,
                            sourceAccountId = walletId,
                            destinationAccountType = FinancialAccountType.WALLET,
                            destinationAccountId = effectiveTargetWalletId ?: cashDrawerId, // Explicit target wallet ID
                            relatedEntityType = "TRANSFER",
                            relatedEntityId = transferId,
                            descriptionArabic = "تحويل داخلي بين المحافظ — مرجع $refNumber",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    WalletOperationType.WALLET_ADJUSTMENT -> {
                        // Ledger adjustment for wallet
                        postLedgerTransactionInternal(
                            shopId = shopId,
                            transactionType = FinancialTransactionType.ADJUSTMENT,
                            amountMinor = principalAmountMinor,
                            feeMinor = 0L,
                            commissionMinor = 0L,
                            sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
                            sourceAccountId = "WALLET_ADJUSTMENT_COUNTER",
                            destinationAccountType = FinancialAccountType.WALLET,
                            destinationAccountId = walletId,
                            relatedEntityType = "WALLET_ADJUSTMENT",
                            relatedEntityId = transferId,
                            descriptionArabic = "تسوية رصيد محفظة عبر الأستاذ — مرجع $refNumber: $notes",
                            createdByUserId = actorUserId,
                            deviceId = deviceId,
                            timestamp = timestamp,
                            idempotencyKey = idempotencyKey
                        )
                    }
                }

                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val ledgerTx = (ledgerRes as AppResult.Success).data

                val transferEntity = TransferEntity(
                    localId = transferId,
                    shopId = shopId,
                    transferReference = refNumber,
                    transferKind = transferKind,
                    sourceAccountType = ledgerTx.sourceAccountType.name,
                    sourceAccountId = ledgerTx.sourceAccountId,
                    destinationAccountType = ledgerTx.destinationAccountType.name,
                    destinationAccountId = ledgerTx.destinationAccountId,
                    customerPhoneOrAccount = customerPhone,
                    principalAmountMinor = principalAmountMinor,
                    providerFeeMinor = providerFeeMinor,
                    shopCommissionMinor = shopCommissionMinor,
                    netSettlementMinor = principalAmountMinor + shopCommissionMinor - providerFeeMinor,
                    ledgerTransactionId = ledgerTx.localId,
                    notes = notes,
                    executedByUserId = actorUserId,
                    deviceId = deviceId,
                    transferTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                operationsDao.insertTransfer(transferEntity)

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "WALLET_OP",
                        entityType = "Transfer",
                        entityId = transferId,
                        summaryArabic = "عملية محفظة ($transferKind) بمبلغ ${Money.ofMinor(principalAmountMinor).formatArabic()} وعمولة ${Money.ofMinor(shopCommissionMinor).formatArabic()}",
                        beforeStateJson = null,
                        afterStateJson = """{"transferId":"$transferId","kind":"$transferKind","principalMinor":$principalAmountMinor,"ledgerTxId":"${ledgerTx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Transfer",
                    entityLocalId = transferId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"transferId":"$transferId","ref":"$refNumber","principalMinor":$principalAmountMinor}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(transferEntity)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تعذر إتمام عملية المحفظة الذرية: ${e.message}", "ATOMIC_WALLET_ERROR", e)
        }
    }

    /**
     * Requirement 14: Authoritative Wallet Reconciliation.
     * Compares system ledger balance vs actual provider statement balance.
     * Never mutates balances directly; any variance is posted as an immutable WALLET_ADJUSTMENT ledger entry.
     */
    suspend fun reconcileWallet(
        shopId: String,
        walletId: String,
        actualCountedBalanceMinor: Long,
        reasonArabic: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<WalletReconciliationResult> = executionMutex.withLock {
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.WALLET_OPERATE)
        if (auth is AppResult.Error) return@withLock auth

        return@withLock try {
            database.withTransaction {
                val wallet = ledgerDao.getWalletById(walletId)
                    ?: throw AtomicRollbackException(AppResult.Error("المحفظة الإلكترونية غير موجودة", "WALLET_NOT_FOUND"))

                val systemBalanceMinor = ledgerDao.getDerivedAccountBalanceMinor(
                    shopId = shopId,
                    accountType = FinancialAccountType.WALLET,
                    accountId = walletId
                )

                val varianceMinor = actualCountedBalanceMinor - systemBalanceMinor
                val status = ReconciliationStatus.of(varianceMinor)
                var adjustmentTxId: String? = null

                if (varianceMinor != 0L) {
                    val absDiff = abs(varianceMinor)
                    val isOverage = varianceMinor > 0L
                    val srcType = if (isOverage) FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT else FinancialAccountType.WALLET
                    val srcId = if (isOverage) "WALLET_RECONCILIATION_OVERAGE" else walletId
                    val dstType = if (isOverage) FinancialAccountType.WALLET else FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT
                    val dstId = if (isOverage) walletId else "WALLET_RECONCILIATION_SHORTAGE"

                    val txRes = postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.ADJUSTMENT,
                        amountMinor = absDiff,
                        sourceAccountType = srcType,
                        sourceAccountId = srcId,
                        destinationAccountType = dstType,
                        destinationAccountId = dstId,
                        relatedEntityType = "WALLET_ADJUSTMENT",
                        relatedEntityId = walletId,
                        descriptionArabic = "تسوية جرد محفظة (${wallet.walletNameArabic}) الفارق: ${Money.ofMinor(absDiff).formatArabic()} (${status.arabicLabel}) — $reasonArabic",
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (txRes is AppResult.Error) {
                        throw AtomicRollbackException(txRes)
                    }
                    adjustmentTxId = (txRes as AppResult.Success).data.localId
                }

                val auditId = IdGenerator.newPrefixedId("AUD")
                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = auditId,
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = "OPERATOR",
                        actionType = "WALLET_RECONCILIATION",
                        entityType = "Wallet",
                        entityId = walletId,
                        summaryArabic = "جرد محفظة (${wallet.walletNameArabic}): الفعلي ${Money.ofMinor(actualCountedBalanceMinor).formatArabic()}، الدفتري ${Money.ofMinor(systemBalanceMinor).formatArabic()}، الفارق ${Money.ofMinor(varianceMinor).formatArabic()}",
                        beforeStateJson = """{"systemBalanceMinor":$systemBalanceMinor}""",
                        afterStateJson = """{"actualCountedBalanceMinor":$actualCountedBalanceMinor,"varianceMinor":$varianceMinor,"adjustmentTxId":${adjustmentTxId?.let { "\"$it\"" } ?: "null"}}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                AppResult.Success(
                    WalletReconciliationResult(
                        walletId = walletId,
                        walletNameArabic = wallet.walletNameArabic,
                        providerCode = wallet.providerId,
                        systemDerivedBalanceMinor = systemBalanceMinor,
                        actualCountedBalanceMinor = actualCountedBalanceMinor,
                        varianceMinor = varianceMinor,
                        status = status,
                        adjustmentTransactionId = adjustmentTxId,
                        auditLogId = auditId,
                        timestamp = timestamp
                    )
                )
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error(e.message ?: "تعذر تسوية جرد المحفظة", "WALLET_RECONCILIATION_ERROR", e)
        }
    }

    /**
     * ATOMIC CASH DRAWER OPERATION (`CASH_IN`, `CASH_OUT`, `ADJUSTMENT`).
     */
    suspend fun executeAtomicCashOperation(
        shopId: String,
        cashDrawerId: String,
        operationType: FinancialTransactionType, // CASH_IN, CASH_OUT, ADJUSTMENT
        amountMinor: Long,
        reasonArabic: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<TransactionEntity> = executionMutex.withLock {
        if (operationType == FinancialTransactionType.ADJUSTMENT && reasonArabic.isBlank()) {
            return@withLock AppResult.Error("يرجى إدخال سبب التسوية النقدية بشكل صريح", "EMPTY_ADJUSTMENT_REASON")
        }
        val requiredPerm = if (operationType == FinancialTransactionType.ADJUSTMENT) {
            PermissionCode.CASH_ADJUST
        } else {
            PermissionCode.CASH_OPERATE
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, requiredPerm)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val isInflow = operationType == FinancialTransactionType.CASH_IN ||
                    operationType == FinancialTransactionType.ADJUSTMENT
                val srcType = if (isInflow) FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT else FinancialAccountType.CASH_DRAWER
                val srcId = if (isInflow) "OWNER_TREASURY" else cashDrawerId
                val dstType = if (isInflow) FinancialAccountType.CASH_DRAWER else FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT
                val dstId = if (isInflow) cashDrawerId else "OWNER_TREASURY"

                val ledgerRes = postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = operationType,
                    amountMinor = amountMinor,
                    sourceAccountType = srcType,
                    sourceAccountId = srcId,
                    destinationAccountType = dstType,
                    destinationAccountId = dstId,
                    relatedEntityType = "CASH_DRAWER",
                    relatedEntityId = cashDrawerId,
                    descriptionArabic = reasonArabic.ifBlank { operationType.arabicLabel },
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    idempotencyKey = idempotencyKey
                )
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val tx = (ledgerRes as AppResult.Success).data

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "CASH_OP",
                        entityType = "FinancialTransaction",
                        entityId = tx.localId,
                        summaryArabic = "${operationType.arabicLabel} بقيمة ${Money.ofMinor(amountMinor).formatArabic()}: $reasonArabic",
                        beforeStateJson = null,
                        afterStateJson = """{"txId":"${tx.localId}","type":"${operationType.name}","amountMinor":$amountMinor}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                AppResult.Success(tx)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("فشل تنفيذ عملية الخزينة الذرية: ${e.message}", "ATOMIC_CASH_ERROR", e)
        }
    }

    /**
     * ATOMIC CASH DRAWER SHIFT CLOSEOUT (Requirements 13 & 14):
     * Compares Expected Cash (derived from Ledger) vs Counted Physical Cash.
     * If `Expected Cash != Counted Cash`, records the difference as an auditable `Closeout Difference`
     * (`ADJUSTMENT` or `CASH_OUT`) ledger entry without mutating any balance counter directly!
     */
    suspend fun executeCashDrawerCloseout(
        shopId: String,
        cashDrawerId: String,
        countedCashMinor: Long,
        notes: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<CashSessionEntity> {
        if (countedCashMinor < 0L) {
            return AppResult.Error("النقدية الفعلية المعدودة لا يمكن أن تكون سالبة", "NEGATIVE_COUNTED_CASH")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.CASH_OPERATE)
        if (auth is AppResult.Error) return auth
        val actor = (auth as AppResult.Success).data

        return try {
            database.withTransaction {
                val expectedBalanceMinor = ledgerDao.getDerivedAccountBalanceMinor(
                    shopId = shopId,
                    accountType = FinancialAccountType.CASH_DRAWER,
                    accountId = cashDrawerId
                )
                val discrepancyMinor = countedCashMinor - expectedBalanceMinor

                // If there is a closeout difference, post an explicit compensating ledger entry
                if (discrepancyMinor != 0L) {
                    val absDiff = abs(discrepancyMinor)
                    val isSurplus = discrepancyMinor > 0L
                    val srcType = if (isSurplus) FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT else FinancialAccountType.CASH_DRAWER
                    val srcId = if (isSurplus) "CLOSEOUT_SURPLUS" else cashDrawerId
                    val dstType = if (isSurplus) FinancialAccountType.CASH_DRAWER else FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT
                    val dstId = if (isSurplus) cashDrawerId else "CLOSEOUT_SHORTAGE"

                    val diffRes = postLedgerTransactionInternal(
                        shopId = shopId,
                        transactionType = FinancialTransactionType.ADJUSTMENT,
                        amountMinor = absDiff,
                        sourceAccountType = srcType,
                        sourceAccountId = srcId,
                        destinationAccountType = dstType,
                        destinationAccountId = dstId,
                        relatedEntityType = "CASH_CLOSEOUT",
                        relatedEntityId = cashDrawerId,
                        descriptionArabic = if (isSurplus) {
                            "تسوية إغلاق وردية (فائض نقدي): ${Money.ofMinor(absDiff).formatArabic()} — $notes"
                        } else {
                            "تسوية إغلاق وردية (عجز نقدي): ${Money.ofMinor(absDiff).formatArabic()} — $notes"
                        },
                        createdByUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                    if (diffRes is AppResult.Error) {
                        throw AtomicRollbackException(diffRes)
                    }
                }

                val openSession = ledgerDao.getOpenCashSession(shopId, cashDrawerId)
                val closedSession = if (openSession != null) {
                    val updated = openSession.copy(
                        closedByUserId = actorUserId,
                        closedAt = timestamp,
                        closingDerivedBalanceMinor = expectedBalanceMinor,
                        closingCountedCashMinor = countedCashMinor,
                        discrepancyMinor = discrepancyMinor,
                        status = "CLOSED",
                        notes = notes,
                        updatedAt = timestamp,
                        version = openSession.version + 1L
                    )
                    ledgerDao.updateCashSession(updated)
                    updated
                } else {
                    val created = CashSessionEntity(
                        localId = IdGenerator.newPrefixedId("CSH"),
                        shopId = shopId,
                        cashDrawerId = cashDrawerId,
                        openedByUserId = actorUserId,
                        closedByUserId = actorUserId,
                        openedAt = timestamp - 3600_000L,
                        closedAt = timestamp,
                        openingDerivedBalanceMinor = expectedBalanceMinor,
                        closingDerivedBalanceMinor = expectedBalanceMinor,
                        closingCountedCashMinor = countedCashMinor,
                        discrepancyMinor = discrepancyMinor,
                        status = "CLOSED",
                        notes = notes,
                        deviceId = deviceId,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )
                    ledgerDao.insertCashSession(created)
                    created
                }

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "CASH_CLOSEOUT",
                        entityType = "CashSession",
                        entityId = closedSession.localId,
                        summaryArabic = "إغلاق وردية الخزينة — المتوقع: ${Money.ofMinor(expectedBalanceMinor).formatArabic()}، الفعلي: ${Money.ofMinor(countedCashMinor).formatArabic()}، الفرق: ${Money.ofMinor(discrepancyMinor).formatArabic()}",
                        beforeStateJson = """{"expectedMinor":$expectedBalanceMinor}""",
                        afterStateJson = """{"countedMinor":$countedCashMinor,"discrepancyMinor":$discrepancyMinor}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                AppResult.Success(closedSession)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("تعذر إغلاق الوردية: ${e.message}", "CLOSEOUT_ERROR", e)
        }
    }

    /**
     * ATOMIC EXPENSE OPERATION (Requirement 15):
     * Records an ExpenseEntity + FinancialTransaction(EXPENSE) + AuditLog + SyncQueue.
     */
    suspend fun executeAtomicExpense(
        shopId: String,
        categoryName: String,
        title: String,
        amountMinor: Long,
        sourceAccountType: FinancialAccountType, // CASH_DRAWER or WALLET
        sourceAccountId: String,
        notes: String = "",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        idempotencyKey: String? = null
    ): AppResult<ExpenseEntity> = executionMutex.withLock {
        if (title.isBlank() || amountMinor <= 0L) {
            return@withLock AppResult.Error("يرجى إدخال بيان المصروف ومبلغ أكبر من الصفر", "INVALID_EXPENSE")
        }
        val auth = authSecurityManager.authorizeAction(actorUserId, PermissionCode.EXPENSE_CREATE)
        if (auth is AppResult.Error) return@withLock auth
        val actor = (auth as AppResult.Success).data

        return@withLock try {
            database.withTransaction {
                val expenseId = IdGenerator.newPrefixedId("EXP")

                val ledgerRes = postLedgerTransactionInternal(
                    shopId = shopId,
                    transactionType = FinancialTransactionType.EXPENSE,
                    amountMinor = amountMinor,
                    sourceAccountType = sourceAccountType,
                    sourceAccountId = sourceAccountId,
                    destinationAccountType = FinancialAccountType.OPERATING_EXPENSE,
                    destinationAccountId = categoryName,
                    relatedEntityType = "EXPENSE",
                    relatedEntityId = expenseId,
                    descriptionArabic = "مصروف ($categoryName): $title",
                    createdByUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    idempotencyKey = idempotencyKey
                )
                if (ledgerRes is AppResult.Error) {
                    throw AtomicRollbackException(ledgerRes)
                }
                val tx = (ledgerRes as AppResult.Success).data

                val expenseEntity = ExpenseEntity(
                    localId = expenseId,
                    shopId = shopId,
                    categoryName = categoryName,
                    title = title,
                    amountMinor = amountMinor,
                    sourceAccountType = sourceAccountType.name,
                    sourceAccountId = sourceAccountId,
                    ledgerTransactionId = tx.localId,
                    notes = notes,
                    recordedByUserId = actorUserId,
                    deviceId = deviceId,
                    expenseTimestamp = timestamp,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                operationsDao.insertExpense(expenseEntity)

                systemDao.insertAuditLog(
                    AuditLogEntity(
                        localId = IdGenerator.newPrefixedId("AUD"),
                        shopId = shopId,
                        actorUserId = actorUserId,
                        actorRoleCode = actor.roleCode,
                        actionType = "EXPENSE",
                        entityType = "Expense",
                        entityId = expenseId,
                        summaryArabic = "تسجيل مصروف تشغيلي ($title) بقيمة ${Money.ofMinor(amountMinor).formatArabic()}",
                        beforeStateJson = null,
                        afterStateJson = """{"expenseId":"$expenseId","amountMinor":$amountMinor,"ledgerTxId":"${tx.localId}"}""",
                        deviceId = deviceId,
                        timestamp = timestamp
                    )
                )

                syncCoordinator.enqueueOperationInternal(
                    shopId = shopId,
                    entityType = "Expense",
                    entityLocalId = expenseId,
                    operationType = "INSERT",
                    entityVersion = 1L,
                    payloadJson = """{"expenseId":"$expenseId","title":"$title","amountMinor":$amountMinor}""",
                    isFinancialLedgerRecord = true,
                    deviceId = deviceId,
                    timestamp = timestamp
                )

                AppResult.Success(expenseEntity)
            }
        } catch (e: AtomicRollbackException) {
            e.error
        } catch (e: Exception) {
            AppResult.Error("فشل تسجيل المصروف الذري: ${e.message}", "ATOMIC_EXPENSE_ERROR", e)
        }
    }
}

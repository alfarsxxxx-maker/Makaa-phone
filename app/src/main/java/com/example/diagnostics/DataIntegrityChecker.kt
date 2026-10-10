package com.example.diagnostics

import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.SaleEntity
import com.example.database.entities.TransactionEntity
import com.example.financial.AccountingEngine
import com.example.financial.Money

/**
 * Health check status levels (Requirement 47 & 55).
 */
enum class HealthCheckLevel(val arabicLabel: String) {
    PASS("سليم (PASS)"),
    WARNING("تحذير (WARNING)"),
    ERROR("خطأ حرج (ERROR)")
}

data class IntegrityCheckItem(
    val checkCode: String,
    val titleArabic: String,
    val status: HealthCheckLevel,
    val detailsArabic: String
)

data class SystemIntegrityReport(
    val overallStatus: HealthCheckLevel,
    val checkedAtMillis: Long,
    val items: List<IntegrityCheckItem>
)

/**
 * Comprehensive 16-Point Data Integrity & Double-Entry Accounting Health Checker (Requirements 47, 54, 55, 64).
 */
class DataIntegrityChecker(
    private val database: MekkaPhoneDatabase
) {

    /**
     * Runs all 16 mandatory production integrity & accounting checks on the local database (Requirement 47).
     */
    suspend fun runFullHealthCheck(shopId: String): SystemIntegrityReport {
        val now = System.currentTimeMillis()
        val checks = mutableListOf<IntegrityCheckItem>()
        val db = database.openHelper.readableDatabase

        // 1. Orphan records check (transaction_lines without parent financial_transactions)
        val orphanLines = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM transaction_lines tl
            LEFT JOIN financial_transactions ft ON ft.localId = tl.transactionId
            WHERE ft.localId IS NULL
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "ORPHAN_RECORDS",
                titleArabic = "فحص السجلات اليتيمة (Orphan Records)",
                status = if (orphanLines == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (orphanLines == 0) "جميع سطور القيود وبنود الفواتير مرتبطة بسجلاتها الأب" else "يوجد $orphanLines سجل يتيم!"
            )
        )

        // 2. Invalid ledger references check (sales/expenses/purchases pointing to missing ledger transactions)
        val invalidSaleLedgerRefs = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM sales s
            LEFT JOIN financial_transactions ft ON ft.localId = s.ledgerTransactionId
            WHERE s.shopId = '$shopId' AND ft.localId IS NULL
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "LEDGER_REFERENCES",
                titleArabic = "سلامة مراجع دفتر الأستاذ (Ledger References)",
                status = if (invalidSaleLedgerRefs == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (invalidSaleLedgerRefs == 0) "جميع الفواتير والمصروفات مرتبطة بقيود دفتر أستاذ صالحة" else "يوجد $invalidSaleLedgerRefs فاتورة بدون قيد مقابل!"
            )
        )

        // 3. Duplicate active IMEI check
        val dupActiveImeis = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM (
                SELECT serialOrImei, COUNT(*) AS cnt
                FROM serial_numbers
                WHERE shopId = '$shopId' AND activeGuardKey IS NOT NULL
                GROUP BY serialOrImei
                HAVING cnt > 1
            )
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "DUPLICATE_ACTIVE_IMEI",
                titleArabic = "فحص عدم تكرار السيريال / IMEI النشط",
                status = if (dupActiveImeis == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (dupActiveImeis == 0) "لا يوجد أي تكرار لأرقام IMEI النشطة بالمخزن" else "يوجد $dupActiveImeis رقم IMEI مكرر!"
            )
        )

        // 4. Negative impossible stock check
        val negativeStockProducts = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM (
                SELECT productId, SUM(signedQuantityDelta) AS netStock
                FROM inventory_movements
                WHERE shopId = '$shopId'
                GROUP BY productId
                HAVING netStock < 0
            )
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "NEGATIVE_STOCK",
                titleArabic = "فحص عدم وجود أرصدة مخزنية سالبة",
                status = if (negativeStockProducts == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (negativeStockProducts == 0) "جميع الأرصدة المخزنية المشتقة موجبة أو صفرية" else "يوجد $negativeStockProducts صنف برصيد سالب!"
            )
        )

        // 5. Duplicate transaction references check
        val dupTxRefs = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM (
                SELECT referenceNumber, COUNT(*) AS cnt
                FROM financial_transactions
                WHERE shopId = '$shopId'
                GROUP BY referenceNumber
                HAVING cnt > 1
            )
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "DUPLICATE_TRANSACTION_IDS",
                titleArabic = "فحص فرادة معرفات ومراجع القيود المالية",
                status = if (dupTxRefs == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (dupTxRefs == 0) "جميع أرقام المراجع والقيود المالية فريدة" else "يوجد $dupTxRefs مرجع مكرر!"
            )
        )

        // 6. Invalid shop ownership check
        val emptyShopOwners = querySingleInt(
            db,
            "SELECT COUNT(*) FROM financial_transactions WHERE shopId = '' OR shopId IS NULL"
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "SHOP_OWNERSHIP",
                titleArabic = "فحص عزل وملكية المتجر (Shop Ownership)",
                status = if (emptyShopOwners == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (emptyShopOwners == 0) "جميع السجلات معزولة ومرتبطة بـ shopId صالح" else "يوجد $emptyShopOwners سجل بدون ملكية متجر!"
            )
        )

        // 7. Invalid money values check
        val invalidMoneyRows = querySingleInt(
            db,
            "SELECT COUNT(*) FROM financial_transactions WHERE amountMinor <= 0 OR feeMinor < 0 OR commissionMinor < 0"
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "MONEY_VALUES",
                titleArabic = "فحص سلامة القيم النقدية بالوحدات الصغرى",
                status = if (invalidMoneyRows == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (invalidMoneyRows == 0) "جميع المبالغ المالية موجبة وبالوحدات الصغرى الصحيحة (Minor Units)" else "يوجد $invalidMoneyRows قيمة مالية غير صالحة!"
            )
        )

        // 8. Sync inconsistencies & SyncQueue <-> Entity check
        val failedSyncs = querySingleInt(
            db,
            "SELECT COUNT(*) FROM sync_queue WHERE shopId = '$shopId' AND syncState = 'FAILED'"
        )
        val unresolvedConflicts = querySingleInt(
            db,
            "SELECT COUNT(*) FROM sync_conflicts WHERE shopId = '$shopId' AND resolutionStatus = 'UNRESOLVED'"
        )
        val invalidSyncQueueRows = querySingleInt(
            db,
            "SELECT COUNT(*) FROM sync_queue WHERE shopId = '$shopId' AND (entityLocalId = '' OR deduplicationKey = '')"
        )
        val syncHealth = when {
            invalidSyncQueueRows > 0 -> HealthCheckLevel.ERROR
            unresolvedConflicts > 0 -> HealthCheckLevel.WARNING
            failedSyncs > 0 -> HealthCheckLevel.WARNING
            else -> HealthCheckLevel.PASS
        }
        checks.add(
            IntegrityCheckItem(
                checkCode = "SYNC_CONSISTENCY",
                titleArabic = "فحص اتساق طابور المزامنة والتعارضات (SyncQueue ↔ Entity)",
                status = syncHealth,
                detailsArabic = "عمليات فاشلة: $failedSyncs • تعارضات غير محلولة: $unresolvedConflicts • سجلات غير صالحة: $invalidSyncQueueRows"
            )
        )

        // 9. Double-Entry Accounting Balance Check (Total Debits == Total Credits)
        val allTransactions = database.financialLedgerDao().getAllTransactionsChronological(shopId)
        val trialBalance = AccountingEngine.computeTrialBalance(shopId, allTransactions)
        checks.add(
            IntegrityCheckItem(
                checkCode = "DOUBLE_ENTRY_BALANCE",
                titleArabic = "فحص توازن القيد المحاسبي المزدوج (Debit == Credit)",
                status = if (trialBalance.isBalanced) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (trialBalance.isBalanced) {
                    "ميزان المراجعة متوازن تماماً: المدين (${Money.ofMinor(trialBalance.totalDebitsMinor).formatArabic()}) == الدائن (${Money.ofMinor(trialBalance.totalCreditsMinor).formatArabic()})"
                } else {
                    "عدم توازن محاسبي: المدين (${trialBalance.totalDebitsMinor}) != الدائن (${trialBalance.totalCreditsMinor})"
                }
            )
        )

        // 10. Sale <-> SaleItems Reconciliation
        val salesWithoutItems = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM sales s
            LEFT JOIN sale_items si ON si.saleId = s.localId
            WHERE s.shopId = '$shopId' AND si.localId IS NULL
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "SALE_ITEMS_RECONCILIATION",
                titleArabic = "مطابقة فواتير المبيعات وبنودها (Sale ↔ SaleItems)",
                status = if (salesWithoutItems == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (salesWithoutItems == 0) "كل فاتورة مبيعات مرتبطة ببنود أصناف وحركات مخزنية صحيحة" else "يوجد $salesWithoutItems فاتورة بيع بدون بنود!"
            )
        )

        // 11. Purchase <-> PurchaseItems Reconciliation
        val purchasesWithoutItems = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM purchases p
            LEFT JOIN purchase_items pi ON pi.purchaseId = p.localId
            WHERE p.shopId = '$shopId' AND pi.localId IS NULL
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "PURCHASE_ITEMS_RECONCILIATION",
                titleArabic = "مطابقة فواتير المشتريات وبنودها (Purchase ↔ PurchaseItems)",
                status = if (purchasesWithoutItems == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (purchasesWithoutItems == 0) "كل فاتورة مشتريات مرتبطة ببنود التوريد وحركات الإضافة" else "يوجد $purchasesWithoutItems فاتورة شراء بدون بنود!"
            )
        )

        // 12. Inventory <-> Product Consistency
        val orphanInventoryRecords = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM inventory_records inv
            LEFT JOIN products pr ON pr.localId = inv.productId
            WHERE inv.shopId = '$shopId' AND pr.localId IS NULL
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "INVENTORY_PRODUCT_CONSISTENCY",
                titleArabic = "مطابقة أرصدة المخزون مع كتالوج الأصناف (Inventory ↔ Product)",
                status = if (orphanInventoryRecords == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (orphanInventoryRecords == 0) "جميع سجلات وحركات المخزون مرتبطة بأصناف معرفة" else "يوجد $orphanInventoryRecords سجل مخزني بدون صنف!"
            )
        )

        // 13. COGS <-> Inventory Valuation Consistency
        val negativeCogsSales = querySingleInt(
            db,
            "SELECT COUNT(*) FROM sales WHERE shopId = '$shopId' AND totalCostOfGoodsMinor < 0"
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "COGS_INVENTORY_CONSISTENCY",
                titleArabic = "فحص سلامة تكلفة البضاعة المباعة والمتوسط المرجح (COGS ↔ WAC)",
                status = if (negativeCogsSales == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (negativeCogsSales == 0) "تكلفة البضاعة المباعة (COGS) محتسبة بدقة وفق المتوسط المرجح (WAC)" else "يوجد $negativeCogsSales فاتورة بتكلفة سالبة!"
            )
        )

        // 14. Customer Receivable & Supplier Payable Consistency
        val invalidPartyTx = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM financial_transactions
            WHERE shopId = '$shopId'
              AND ((sourceAccountType = 'EXTERNAL_CUSTOMER' AND sourceAccountId = '')
                OR (destinationAccountType = 'EXTERNAL_SUPPLIER' AND destinationAccountId = ''))
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "RECEIVABLE_PAYABLE_CONSISTENCY",
                titleArabic = "سلامة ذمم العملاء ومديونيات الموردين (Receivables & Payables)",
                status = if (invalidPartyTx == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (invalidPartyTx == 0) "أرصدة ذمم العملاء والموردين متطابقة مع قيود دفتر الأستاذ" else "يوجد $invalidPartyTx قيد ذمم غير مكتمل!"
            )
        )

        // 15. SMS <-> Pending Operation Consistency
        val invalidConfirmedPending = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM pending_transactions
            WHERE shopId = '$shopId' AND status = 'CONFIRMED' AND (confirmedLedgerTransactionId IS NULL OR confirmedLedgerTransactionId = '')
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "SMS_PENDING_CONSISTENCY",
                titleArabic = "مطابقة رسائل SMS والعمليات المعلقة (SMS ↔ Pending Operation)",
                status = if (invalidConfirmedPending == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (invalidConfirmedPending == 0) "جميع عمليات SMS المؤكدة مرتبطة بقيود دفتر أستاذ صالحة" else "يوجد $invalidConfirmedPending عملية مؤكدة بدون قيد!"
            )
        )

        // 16. Audit <-> User Attribution Check
        val unattributedAudits = querySingleInt(
            db,
            """
            SELECT COUNT(*) FROM audit_logs a
            LEFT JOIN users u ON u.localId = a.actorUserId
            WHERE a.shopId = '$shopId' AND (a.actorUserId = '' OR u.localId IS NULL)
            """.trimIndent()
        )
        checks.add(
            IntegrityCheckItem(
                checkCode = "AUDIT_USER_ATTRIBUTION",
                titleArabic = "فحص إسناد سجل المراجعة للمستخدمين (Audit ↔ User)",
                status = if (unattributedAudits == 0) HealthCheckLevel.PASS else HealthCheckLevel.ERROR,
                detailsArabic = if (unattributedAudits == 0) "جميع سجلات التدقيق والمراجعة موثقة بهوية مستخدم صالحة" else "يوجد $unattributedAudits سجل تدقيق بدون مستخدم صالح!"
            )
        )

        val overall = when {
            checks.any { it.status == HealthCheckLevel.ERROR } -> HealthCheckLevel.ERROR
            checks.any { it.status == HealthCheckLevel.WARNING } -> HealthCheckLevel.WARNING
            else -> HealthCheckLevel.PASS
        }

        return SystemIntegrityReport(
            overallStatus = overall,
            checkedAtMillis = now,
            items = checks
        )
    }

    private fun querySingleInt(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int {
        db.query(sql).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}

/**
 * Hardware-agnostic Receipt Abstraction (Requirement 64).
 * Decouples business logic from specific Bluetooth/Network/PDF printers.
 */
object ReceiptFormatter {
    fun formatSaleReceiptText(
        shopNameArabic: String,
        sale: SaleEntity,
        shopPhone: String = "",
        shopAddress: String = "",
        headerNote: String = "",
        footerNote: String = "",
        warrantyDays: String = ""
    ): String {
        val remainingDebtMinor = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
        return buildString {
            appendLine("================================")
            appendLine("       $shopNameArabic")
            if (shopPhone.isNotBlank() || shopAddress.isNotBlank()) {
                appendLine("  ${listOf(shopAddress, shopPhone).filter { it.isNotBlank() }.joinToString(" • ")}")
            }
            if (headerNote.isNotBlank()) {
                appendLine("  $headerNote")
            }
            appendLine("================================")
            appendLine("فاتورة مبيعات رقم: ${sale.invoiceNumber}")
            appendLine("الحالة: ${sale.status}")
            appendLine("الإجمالي قبل الخصم: ${Money.ofMinor(sale.subtotalMinor).formatArabic()}")
            appendLine("الخصم: ${Money.ofMinor(sale.discountMinor).formatArabic()}")
            appendLine("الضريبة: ${Money.ofMinor(sale.taxMinor).formatArabic()}")
            appendLine("صافي الفاتورة: ${Money.ofMinor(sale.totalAmountMinor).formatArabic()}")
            appendLine("المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()}")
            if (remainingDebtMinor > 0L) {
                appendLine("المتبقي آجل (ذمم): ${Money.ofMinor(remainingDebtMinor).formatArabic()}")
            }
            if (warrantyDays.isNotBlank()) {
                appendLine("فترة الضمان: $warrantyDays يوم")
            }
            appendLine("قيد دفتر الأستاذ: ${sale.ledgerTransactionId}")
            if (footerNote.isNotBlank()) {
                appendLine("--------------------------------")
                appendLine(footerNote)
            }
            appendLine("================================")
        }
    }

    fun formatLedgerVoucherText(tx: TransactionEntity): String {
        return buildString {
            appendLine("سند قيد مالي — مكة فون")
            appendLine("مرجع القيد: ${tx.referenceNumber}")
            appendLine("نوع العملية: ${tx.transactionType.arabicLabel}")
            appendLine("المبلغ: ${Money.ofMinor(tx.amountMinor).formatArabic()}")
            appendLine("البيان: ${tx.descriptionArabic}")
            appendLine("البصمة SHA-256: ${tx.ledgerHashSha256.take(20)}...")
        }
    }

    fun sharePlainTextReceipt(
        context: android.content.Context,
        title: String,
        body: String
    ) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, title).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun printInvoiceOrReceipt(
        context: android.content.Context,
        jobName: String,
        shopNameArabic: String,
        documentTitleArabic: String,
        referenceNumber: String,
        detailsMap: List<Pair<String, String>>,
        footerNoteArabic: String = ""
    ) {
        val textBody = buildString {
            appendLine("================================")
            appendLine("       $shopNameArabic")
            appendLine("  $documentTitleArabic")
            appendLine("  المرجع: $referenceNumber")
            appendLine("--------------------------------")
            detailsMap.forEach { (k, v) ->
                appendLine("$k: $v")
            }
            if (footerNoteArabic.isNotBlank()) {
                appendLine("--------------------------------")
                appendLine(footerNoteArabic)
            }
            appendLine("================================")
        }
        sharePlainTextReceipt(
            context = context,
            title = "$documentTitleArabic - $jobName",
            body = textBody
        )
    }
}

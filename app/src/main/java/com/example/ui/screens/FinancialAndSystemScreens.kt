package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.backup.BackupValidationReport
import com.example.database.dao.CashDrawerDerivedProjection
import com.example.database.dao.SmsReviewItemProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.UserEntity
import com.example.database.entities.WalletProviderEntity
import com.example.diagnostics.HealthCheckLevel
import com.example.diagnostics.SystemIntegrityReport
import com.example.financial.FriendlyTerminologyMapper
import com.example.repository.MekkaPhoneRepository
import com.example.repository.MonthlyFinancialReport
import com.example.repository.ShopConfigurationProfile
import com.example.sms.SmsConfidenceLevel
import com.example.ui.components.ExpandableTechnicalDetails
import com.example.ui.components.MekkaEmptyStateCard
import com.example.ui.components.QrVerificationBadgeCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.WalletLimitProgressBar
import com.example.ui.components.formatMoneyMinor
import com.example.ui.components.formatTimestampArabic
import com.example.ui.components.launchWhatsAppMessage
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.MekkaGoldPrimary

@Composable
fun ExpensesScreen(
    expenses: List<ExpenseEntity>,
    onExecuteExpense: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var categoryName by rememberSaveable { mutableStateOf("مصروفات تشغيلية") }
    var title by rememberSaveable { mutableStateOf("") }
    var amountStr by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("expenses_screen_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeaderRow(title = "المصروفات التشغيلية", badgeText = "${expenses.size} مصروف")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تسجيل مصروف ذري (يخصم من الخزينة عبر قيد EXPENSE)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("مصروفات تشغيلية", "إيجار ومرافق", "رواتب وأجور", "مستلزمات صيانة").forEach { cat ->
                            FilterChip(
                                selected = categoryName == cat,
                                onClick = { categoryName = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("بيان المصروف") },
                            modifier = Modifier.weight(1.3f).testTag("expense_title_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("المبلغ (ج.م)") },
                            modifier = Modifier.weight(1f).testTag("expense_amount_input"),
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            onExecuteExpense(categoryName, title, amountStr, notes)
                            title = ""
                            amountStr = ""
                        },
                        modifier = Modifier.fillMaxWidth().testTag("submit_expense_button")
                    ) {
                        Text("صرف وتسجيل في دفتر الأستاذ")
                    }
                }
            }
        }

        if (expenses.isEmpty()) {
            item {
                MekkaEmptyStateCard(
                    title = "لا توجد مصروفات مسجلة",
                    description = "تُسجل جميع المصروفات بقيد مالي غير قابل للتعديل في دفتر الأستاذ وتنعكس فوراً في مؤشرات اليوم والشهر.",
                    icon = Icons.Default.RequestQuote
                )
            }
        } else {
            items(expenses, key = { it.localId }) { exp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(exp.title, style = MaterialTheme.typography.titleMedium)
                            Text("${exp.categoryName} • ${formatTimestampArabic(exp.expenseTimestamp)}", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            text = formatMoneyMinor(exp.amountMinor),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = LedgerMonoFontFamily),
                            color = FinancialNegative
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun OperationsLedgerScreen(
    transactions: List<TransactionEntity>,
    auditLogs: List<AuditLogEntity>,
    syncQueue: List<SyncQueueEntity>,
    conflicts: List<SyncConflictEntity>,
    onReverseTransaction: (String, String) -> Unit,
    onRunDeltaSync: () -> Unit = {},
    onResolveConflict: (String, String, String) -> Unit = { _, _, _ -> },
    initialTab: Int = 0,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var activeTab by rememberSaveable(initialTab) { mutableStateOf(initialTab.coerceIn(0, 2)) } // 0: Ledger, 1: Audit, 2: Sync & Conflicts
    var reversalReason by rememberSaveable { mutableStateOf("تصحيح خطأ إدخال تشغيلي") }
    var pendingReversalTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var authorizedReversalConfirmed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("operations_ledger_screen_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeaderRow(
                title = "دفتر الأستاذ العام، التدقيق، والمزامنة",
                badgeText = "${transactions.size} قيد"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("دفتر الأستاذ (${transactions.size})") },
                    modifier = Modifier.testTag("tab_immutable_ledger")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("سجل التدقيق (${auditLogs.size})") },
                    modifier = Modifier.testTag("tab_audit_logs")
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    label = { Text("المزامنة (${syncQueue.size})") },
                    modifier = Modifier.testTag("tab_sync_queue")
                )
            }
        }

        if (activeTab == 0) {
            item {
                OutlinedTextField(
                    value = reversalReason,
                    onValueChange = { reversalReason = it },
                    label = { Text("سبب التصحيح الافتراضي عند إصدار قيد عكسي معتمد") },
                    modifier = Modifier.fillMaxWidth().testTag("reversal_reason_input"),
                    singleLine = true
                )
            }
            if (transactions.isEmpty()) {
                item {
                    MekkaEmptyStateCard(
                        title = "دفتر الأستاذ المالي خالٍ من القيود",
                        description = "جميع القيود المالية محمية بقيود قاعدة بيانات تمنع الحذف أو التعديل؛ يتم التصحيح حصرياً عبر قيد عكسي معتمد.",
                        icon = Icons.Default.ReceiptLong
                    )
                }
            } else {
                items(transactions, key = { it.localId }) { tx ->
                    LedgerTransactionRowCard(
                        tx = tx,
                        onReverseClick = {
                            authorizedReversalConfirmed = false
                            pendingReversalTx = tx
                        }
                    )
                }
            }
        } else if (activeTab == 1) {
            // Rule 6: Audit Log Normal View (Who, Role, Action, Time, Object, Result) + Collapsible Technical Details
            if (auditLogs.isEmpty()) {
                item {
                    MekkaEmptyStateCard(
                        title = "سجل المراجعة والتدقيق فارغ",
                        description = "كل عملية مالية أو مخزنية أو أمنية تُسجل تلقائياً هنا مع هوية المنفذ، الدور، الإجراء، التوقيت، والنتيجة.",
                        icon = Icons.Default.Security
                    )
                }
            } else {
                items(auditLogs, key = { it.localId }) { log ->
                    val friendlyRole = FriendlyTerminologyMapper.toFriendlyRoleArabic(log.actorRoleCode)
                    val friendlyAction = FriendlyTerminologyMapper.toFriendlyAuditActionArabic(log.actionType)
                    val friendlyWho = when {
                        log.actorUserId.contains("OWNER", ignoreCase = true) -> "مالك المتجر"
                        log.actorUserId.contains("CASHIER", ignoreCase = true) -> "مسؤول المبيعات والخزينة"
                        log.actorUserId.contains("TECH", ignoreCase = true) -> "مهندس الصيانة"
                        else -> friendlyRole
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPillBadge(text = friendlyAction, color = MekkaGoldPrimary)
                                StatusPillBadge(text = "النتيجة: تم الاعتماد", color = FinancialPositive)
                            }
                            Text(
                                text = "العنصر والبيان: ${log.summaryArabic}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "المنفذ: $friendlyWho • الدور: $friendlyRole",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "الوقت: ${formatTimestampArabic(log.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExpandableTechnicalDetails(titleArabic = "التفاصيل الفنية والتشخيصية") {
                                Text("معرف المستخدم (User ID): ${log.actorUserId}", style = MaterialTheme.typography.bodySmall)
                                Text("معرف الجهاز (Device ID): ${log.deviceId}", style = MaterialTheme.typography.bodySmall)
                                Text("المرجع الداخلي: ${log.entityType} / ${log.entityId}", style = MaterialTheme.typography.bodySmall)
                                log.beforeStateJson?.let {
                                    Text("الحالة السابقة: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                log.afterStateJson?.let {
                                    Text("الحالة اللاحقة: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Rule 12 & 13: Truthful Sync Queue & Conflict View (never claims fake "Server ACK")
            val pendingCount = syncQueue.count { it.syncState.name == "PENDING" || it.syncState.name == "SYNCING" }
            val failedCount = syncQueue.count { it.syncState.name == "FAILED" || it.syncState.name == "RETRYING" }
            val unresolvedConflicts = conflicts.count { !it.isResolved }
            val overallSyncLabel = when {
                unresolvedConflicts > 0 -> "تعارض يحتاج مراجعة ($unresolvedConflicts)"
                failedCount > 0 -> "فشلت المزامنة ($failedCount)"
                pendingCount > 0 -> "محفوظ محليًا • في انتظار المزامنة ($pendingCount)"
                else -> "محفوظ محليًا"
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حالة الحفظ والمزامنة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPillBadge(
                                text = overallSyncLabel,
                                color = if (unresolvedConflicts > 0 || failedCount > 0) FinancialWarning else FinancialPositive
                            )
                        }
                        Text(
                            text = "إجمالي السجلات في طابور الخروج المحلي (Outbox): ${syncQueue.size} عملية • تعارضات تحتاج مراجعة: $unresolvedConflicts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRunDeltaSync,
                            modifier = Modifier.fillMaxWidth().testTag("run_delta_sync_button")
                        ) {
                            Text("فحص وتشغيل المزامنة التزايدية الآن")
                        }
                    }
                }
            }

            if (conflicts.isNotEmpty()) {
                item {
                    SectionHeaderRow(title = "تعارض يحتاج مراجعة", badgeText = "${conflicts.size} تعارض")
                }
                items(conflicts, key = { it.localId }) { conf ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, FinancialNegative),
                        colors = CardDefaults.cardColors(containerColor = FinancialNegative.copy(alpha = 0.10f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(conf.conflictReasonArabic, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "الحالة: ${if (conf.isResolved) "تمت المراجعة والحسم" else "تعارض يحتاج مراجعة"}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (!conf.isResolved) {
                                OutlinedButton(
                                    onClick = {
                                        onResolveConflict(
                                            conf.localId,
                                            "RESOLVED_KEEP_LOCAL",
                                            "اعتماد النسخة المحلية الموثقة في دفتر الأستاذ"
                                        )
                                    },
                                    modifier = Modifier.testTag("resolve_conflict_btn_${conf.localId}")
                                ) {
                                    Text("حسم التعارض (الإبقاء على السجل المحلي الموثق)")
                                }
                            }
                        }
                    }
                }
            }

            items(syncQueue, key = { it.localId }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "سجل: ${item.entityType} • الوقت: ${formatTimestampArabic(item.createdAt)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            StatusPillBadge(
                                text = item.syncState.arabicLabel,
                                color = when (item.syncState.name) {
                                    "SYNCED" -> FinancialPositive
                                    "FAILED", "CONFLICT" -> FinancialNegative
                                    else -> FinancialInfo
                                }
                            )
                        }
                        ExpandableTechnicalDetails(titleArabic = "التفاصيل الفنية للمزامنة") {
                            Text(
                                text = "مفتاح منع التكرار (Idempotency Key): ${item.deduplicationKey}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "عدد المحاولات: ${item.retryCount} / ${item.maxRetries}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            item.lastErrorMessage?.let {
                                Text("آخر رسالة فنية: $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // Rule 5: Structured Financial Reversal Dialog (Transaction -> سبب التصحيح -> Authorization -> Reverse -> Audit entry)
    pendingReversalTx?.let { tx ->
        val friendlyOp = FriendlyTerminologyMapper.toFriendlyOperationArabic(tx.transactionType, tx.descriptionArabic)
        AlertDialog(
            onDismissRequest = { pendingReversalTx = null },
            title = {
                Text(
                    text = "تصحيح مالي معتمد (قيد عكسي)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. العملية المراد تصحيحها: $friendlyOp (${formatMoneyMinor(tx.amountMinor)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "البيان الأصلي: ${tx.descriptionArabic}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = reversalReason,
                        onValueChange = { reversalReason = it },
                        label = { Text("2. سبب التصحيح الإلزامي") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_reversal_reason_input"),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("خطأ في المبلغ", "تكرار القيد", "إلغاء بطلب العميل").forEach { preset ->
                            FilterChip(
                                selected = reversalReason == preset,
                                onClick = { reversalReason = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = authorizedReversalConfirmed,
                            onCheckedChange = { authorizedReversalConfirmed = it },
                            modifier = Modifier.testTag("confirm_reversal_authorization_checkbox")
                        )
                        Text(
                            text = "3. تفويض أمني: أؤكد إصدار قيد عكسي مقابل وتوثيقه في سجل التدقيق دون حذف القيد الأصلي",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = tx.localId
                        val reason = reversalReason.ifBlank { "تصحيح خطأ إدخال تشغيلي" }
                        pendingReversalTx = null
                        onReverseTransaction(targetId, reason)
                    },
                    enabled = reversalReason.isNotBlank(),
                    modifier = Modifier.testTag("confirm_execute_reversal_button")
                ) {
                    Text("اعتماد وإصدار القيد العكسي")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReversalTx = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun LedgerTransactionRowCard(
    tx: TransactionEntity,
    onReverseClick: () -> Unit
) {
    val context = LocalContext.current
    val isReversal = tx.transactionType == FinancialTransactionType.REVERSAL || tx.reversalOfTransactionId != null
    val statusColor = when {
        tx.status.name == "REVERSED" -> FinancialWarning
        isReversal -> FinancialInfo
        else -> FinancialPositive
    }
    val friendlyOperation = FriendlyTerminologyMapper.toFriendlyOperationArabic(tx.transactionType, tx.descriptionArabic)
    val friendlySourceAccount = FriendlyTerminologyMapper.toFriendlyAccountArabic(tx.sourceAccountType, tx.sourceAccountId)
    val friendlyDestAccount = FriendlyTerminologyMapper.toFriendlyAccountArabic(tx.destinationAccountType, tx.destinationAccountId)
    val friendlyStatus = FriendlyTerminologyMapper.toFriendlyStatusArabic(tx.status.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ledger_tx_card_${tx.referenceNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Rule 4: Normal users see readable information: العملية | المبلغ | الوقت | الحساب | الحالة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPillBadge(text = friendlyOperation, color = statusColor)
                    StatusPillBadge(text = friendlyStatus, color = statusColor)
                }
                Text(
                    text = formatMoneyMinor(tx.amountMinor),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = LedgerMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor
                )
            }
            Text(
                text = tx.descriptionArabic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الحساب: $friendlySourceAccount ➔ $friendlyDestAccount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الوقت: ${formatTimestampArabic(tx.transactionTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tx.status.name == "POSTED" && !isReversal) {
                    OutlinedButton(
                        onClick = onReverseClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reverse_tx_btn_${tx.localId}")
                    ) {
                        Text("تصحيح مالي (قيد عكسي معتمد)")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val voucherText = com.example.diagnostics.ReceiptFormatter.formatLedgerVoucherText(tx)
                        com.example.diagnostics.ReceiptFormatter.sharePlainTextReceipt(
                            context,
                            "مشاركة سند قيد #${tx.referenceNumber}",
                            voucherText
                        )
                    },
                    modifier = Modifier.testTag("share_tx_voucher_btn_${tx.localId}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة سند القيد")
                    Text(" سند")
                }
            }
            // Technical fields & Multi-Device Visibility belong in details/diagnostics (Rule 4 & Section 14)
            ExpandableTechnicalDetails(titleArabic = "التفاصيل الفنية ورؤية تعدد الأجهزة (Multi-Device)") {
                Text("رقم المرجع: #${tx.referenceNumber} • المعرف المحلي: ${tx.localId}", style = MaterialTheme.typography.bodySmall)
                Text("الجهاز (Device): ${tx.deviceId} • المنفذ (Operator): ${tx.createdByUserId}", style = MaterialTheme.typography.bodySmall)
                Text("التوقيت (Timestamp): ${formatTimestampArabic(tx.transactionTimestamp)}", style = MaterialTheme.typography.bodySmall)
                Text("حالة المزامنة (Sync Status): ${tx.syncState.arabicLabel} • المعرف السحابي (Remote ID): ${tx.cloudId ?: "غير متزامن بعد"}", style = MaterialTheme.typography.bodySmall)
                Text("حالة التعارض (Conflict Status): ${if (tx.syncState == com.example.core.SyncState.CONFLICT) "تعارض يتطلب مراجعة يدوية (لا يُستخدم LWW للمال)" else "سليم (لا يوجد تعارض)"}", style = MaterialTheme.typography.bodySmall)
                Text("المصدر الفني: ${tx.sourceAccountType.name} (${tx.sourceAccountId})", style = MaterialTheme.typography.bodySmall)
                Text("الوجهة الفنية: ${tx.destinationAccountType.name} (${tx.destinationAccountId})", style = MaterialTheme.typography.bodySmall)
                tx.reversedByTransactionId?.let {
                    Text("معرف القيد العكسي المرتبط: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(
    monthlyReport: MonthlyFinancialReport,
    shopConfig: ShopConfigurationProfile = ShopConfigurationProfile(),
    onShowFeedback: (String, Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val csvContent = remember(monthlyReport, shopConfig) {
        buildString {
            appendLine("Metric_Code,Metric_Arabic,Amount_Minor,Amount_Formatted")
            appendLine("TODAY_SALES,مبيعات اليوم,${monthlyReport.todaySalesMinor},\"${formatMoneyMinor(monthlyReport.todaySalesMinor)}\"")
            appendLine("TODAY_PURCHASES,مشتريات اليوم,${monthlyReport.todayPurchasesMinor},\"${formatMoneyMinor(monthlyReport.todayPurchasesMinor)}\"")
            appendLine("TODAY_EXPENSES,مصروفات اليوم,${monthlyReport.todayExpensesMinor},\"${formatMoneyMinor(monthlyReport.todayExpensesMinor)}\"")
            appendLine("MONTH_SALES,إجمالي مبيعات الشهر,${monthlyReport.monthSalesMinor},\"${formatMoneyMinor(monthlyReport.monthSalesMinor)}\"")
            appendLine("MONTH_PURCHASES,إجمالي مشتريات الشهر,${monthlyReport.monthPurchasesMinor},\"${formatMoneyMinor(monthlyReport.monthPurchasesMinor)}\"")
            appendLine("MONTH_EXPENSES,إجمالي مصروفات الشهر,${monthlyReport.monthExpensesMinor},\"${formatMoneyMinor(monthlyReport.monthExpensesMinor)}\"")
            appendLine("MONTH_COMMISSIONS,إجمالي عمولات المحافظ بالشهر,${monthlyReport.monthCommissionsMinor},\"${formatMoneyMinor(monthlyReport.monthCommissionsMinor)}\"")
            appendLine("INVENTORY_WAC_VALUATION,تقييم المخزون الحالي بالمتوسط المرجح WAC,${monthlyReport.totalInventoryValuationMinor},\"${formatMoneyMinor(monthlyReport.totalInventoryValuationMinor)}\"")
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                onShowFeedback("تم تصدير التقرير المالي بصيغة CSV بنجاح", false)
            }.onFailure { e ->
                onShowFeedback("فشل تصدير ملف CSV: ${e.message}", true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reports_screen_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeaderRow(
                title = "التقارير المالية اليومية والشهرية وتقييم المخزون",
                badgeText = "مشتقة من التوقيتات"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, FinancialPositive.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ملخص اليوم الحالي (مشتق من نطاقات توقيت اليوم)", style = MaterialTheme.typography.titleMedium)
                    ReportMetricRow("مبيعات اليوم", formatMoneyMinor(monthlyReport.todaySalesMinor), FinancialPositive)
                    ReportMetricRow("مشتريات اليوم", formatMoneyMinor(monthlyReport.todayPurchasesMinor), FinancialInfo)
                    ReportMetricRow("مصروفات اليوم", formatMoneyMinor(monthlyReport.todayExpensesMinor), FinancialNegative)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MekkaGoldPrimary.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ملخص الشهر الحالي وتقييم المخزون (WAC)", style = MaterialTheme.typography.titleMedium)
                    ReportMetricRow("إجمالي مبيعات الشهر", formatMoneyMinor(monthlyReport.monthSalesMinor), FinancialPositive)
                    ReportMetricRow("إجمالي مشتريات الشهر", formatMoneyMinor(monthlyReport.monthPurchasesMinor), FinancialInfo)
                    ReportMetricRow("إجمالي مصروفات الشهر", formatMoneyMinor(monthlyReport.monthExpensesMinor), FinancialNegative)
                    ReportMetricRow("إجمالي عمولات المحافظ بالشهر", formatMoneyMinor(monthlyReport.monthCommissionsMinor), MekkaGoldPrimary)
                    ReportMetricRow("تقييم المخزون الحالي بالمتوسط المرجح (WAC)", formatMoneyMinor(monthlyReport.totalInventoryValuationMinor), MekkaGoldPrimary)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تصدير ومشاركة التقارير المالية (CSV / ملخص نصي)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                exportCsvLauncher.launch("mekkaphone-financial-report-${System.currentTimeMillis()}.csv")
                            },
                            modifier = Modifier.weight(1f).testTag("export_report_csv_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(" تصدير CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                val summaryText = buildString {
                                    appendLine("تقرير ${shopConfig.shopNameArabic} (${shopConfig.shopNameEnglish})")
                                    appendLine("================================")
                                    appendLine("مبيعات اليوم: ${formatMoneyMinor(monthlyReport.todaySalesMinor)}")
                                    appendLine("مشتريات اليوم: ${formatMoneyMinor(monthlyReport.todayPurchasesMinor)}")
                                    appendLine("مصروفات اليوم: ${formatMoneyMinor(monthlyReport.todayExpensesMinor)}")
                                    appendLine("--------------------------------")
                                    appendLine("مبيعات الشهر: ${formatMoneyMinor(monthlyReport.monthSalesMinor)}")
                                    appendLine("مشتريات الشهر: ${formatMoneyMinor(monthlyReport.monthPurchasesMinor)}")
                                    appendLine("مصروفات الشهر: ${formatMoneyMinor(monthlyReport.monthExpensesMinor)}")
                                    appendLine("عمولات المحافظ بالشهر: ${formatMoneyMinor(monthlyReport.monthCommissionsMinor)}")
                                    appendLine("تقييم المخزون الحالي (WAC): ${formatMoneyMinor(monthlyReport.totalInventoryValuationMinor)}")
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "التقرير المالي - ${shopConfig.shopNameArabic}")
                                    putExtra(Intent.EXTRA_TEXT, summaryText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير المالي"))
                            },
                            modifier = Modifier.weight(1f).testTag("share_report_summary_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(" مشاركة التقرير")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val summaryText = buildString {
                                appendLine("📊 التقرير المالي - ${shopConfig.shopNameArabic}")
                                appendLine("• مبيعات اليوم: ${formatMoneyMinor(monthlyReport.todaySalesMinor)}")
                                appendLine("• مشتريات اليوم: ${formatMoneyMinor(monthlyReport.todayPurchasesMinor)}")
                                appendLine("• مصروفات اليوم: ${formatMoneyMinor(monthlyReport.todayExpensesMinor)}")
                                appendLine("• مبيعات الشهر: ${formatMoneyMinor(monthlyReport.monthSalesMinor)}")
                                appendLine("• عمولات المحافظ بالشهر: ${formatMoneyMinor(monthlyReport.monthCommissionsMinor)}")
                                appendLine("• تقييم المخزون (WAC): ${formatMoneyMinor(monthlyReport.totalInventoryValuationMinor)}")
                            }
                            launchWhatsAppMessage(context, shopConfig.shopPhone, summaryText)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("whatsapp_report_summary_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(" إرسال ملخص التقرير المالي عبر واتساب")
                    }
                    QrVerificationBadgeCard(
                        titleArabic = "رمز التحقق الرقمي للتقرير المالي المعتمد (QR Verification)",
                        referenceCode = "RPT-${shopConfig.shopNameEnglish.take(6).uppercase()}-${monthlyReport.monthSalesMinor}",
                        payloadText = "بصمة التحقق المالي للمبيعات (${formatMoneyMinor(monthlyReport.monthSalesMinor)}) وتقييم المخزون المشتق (${formatMoneyMinor(monthlyReport.totalInventoryValuationMinor)})"
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun ReportMetricRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = LedgerMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndBackupScreen(
    users: List<UserEntity>,
    activeUserId: String,
    backups: List<BackupMetadataEntity>,
    lastValidation: BackupValidationReport?,
    integrityReport: SystemIntegrityReport? = null,
    shopConfig: ShopConfigurationProfile = ShopConfigurationProfile(),
    onSwitchUser: (String) -> Unit,
    onUpdateShopIdentity: (String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateSetting: (String, String, String) -> Unit = { _, _, _ -> },
    onResetSettings: () -> Unit = {},
    onCreateBackup: () -> Unit,
    onValidateBackup: (BackupMetadataEntity) -> Unit,
    onValidateRawJson: (String) -> Unit = {},
    onExecuteSafeRestore: (BackupMetadataEntity, Boolean) -> Unit = { _, _ -> },
    onExecuteSafeRestoreFromRawJson: (String, Boolean) -> Unit = { _, _ -> },
    onRunHealthCheck: () -> Unit = {},
    onShowFeedback: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToUsersPermissions: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var confirmRestoreChecked by rememberSaveable { mutableStateOf(false) }

    // Shop Identity editable state synced with shopConfig
    var shopNameAr by rememberSaveable(shopConfig.shopNameArabic) { mutableStateOf(shopConfig.shopNameArabic) }
    var shopNameEn by rememberSaveable(shopConfig.shopNameEnglish) { mutableStateOf(shopConfig.shopNameEnglish) }
    var shopPhone by rememberSaveable(shopConfig.shopPhone) { mutableStateOf(shopConfig.shopPhone) }
    var shopAddress by rememberSaveable(shopConfig.shopAddress) { mutableStateOf(shopConfig.shopAddress) }
    var taxRegNo by rememberSaveable(shopConfig.taxRegistrationNumber) { mutableStateOf(shopConfig.taxRegistrationNumber) }
    var currencyCode by rememberSaveable(shopConfig.defaultCurrency) { mutableStateOf(shopConfig.defaultCurrency) }

    // Operational numeric/text settings
    var taxPercentInput by rememberSaveable(shopConfig.defaultTaxPercent) { mutableStateOf(shopConfig.defaultTaxPercent) }
    var warrantyDaysInput by rememberSaveable(shopConfig.defaultWarrantyDays) { mutableStateOf(shopConfig.defaultWarrantyDays) }
    var receiptHeaderInput by rememberSaveable(shopConfig.receiptHeaderNote) { mutableStateOf(shopConfig.receiptHeaderNote) }
    var receiptFooterInput by rememberSaveable(shopConfig.receiptFooterNote) { mutableStateOf(shopConfig.receiptFooterNote) }
    var creditLimitInput by rememberSaveable(shopConfig.defaultCustomerCreditLimitMajor) { mutableStateOf(shopConfig.defaultCustomerCreditLimitMajor) }
    var lowStockThresholdInput by rememberSaveable(shopConfig.defaultLowStockThreshold) { mutableStateOf(shopConfig.defaultLowStockThreshold.toString()) }
    var walletDailyLimitInput by rememberSaveable(shopConfig.defaultWalletDailyLimitMajor) { mutableStateOf(shopConfig.defaultWalletDailyLimitMajor) }
    var walletMonthlyLimitInput by rememberSaveable(shopConfig.defaultWalletMonthlyLimitMajor) { mutableStateOf(shopConfig.defaultWalletMonthlyLimitMajor) }

    // File Export & Import Launchers for makkaphone-backup-v1
    var pendingExportPayload by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
                onShowFeedback("تم تصدير ملف النسخة الاحتياطية (makkaphone-backup-v1) إلى ذاكرة الجهاز بنجاح", false)
            }.onFailure { e ->
                onShowFeedback("تعذر حفظ ملف النسخة الاحتياطية: ${e.message}", true)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    if (confirmRestoreChecked) {
                        onExecuteSafeRestoreFromRawJson(content, true)
                    } else {
                        onValidateRawJson(content)
                    }
                }
            }.onFailure { e ->
                onShowFeedback("تعذر قراءة ملف النسخة الاحتياطية الخارجي: ${e.message}", true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_backup_screen_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeaderRow(
                title = "مركز الإعدادات الشامل، التخصيص، والنسخ الاحتياطي",
                badgeText = "تحكم كامل"
            )
        }

        // 1. Theme, Palette & UI Density Customization Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_appearance_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("المظهر، نظام الألوان الفاتح/الداكن، وكثافة الواجهة", style = MaterialTheme.typography.titleMedium)
                    }

                    Text("وضع الإضاءة (Theme Mode):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "LIGHT" to "نهاري فاتح (الافتراضي)",
                            "DARK" to "ليلي داكن",
                            "SYSTEM" to "تلقائي"
                        ).forEach { (modeKey, label) ->
                            FilterChip(
                                selected = shopConfig.themeMode.uppercase() == modeKey,
                                onClick = {
                                    onUpdateSetting(MekkaPhoneRepository.KEY_THEME_MODE, modeKey, "وضع المظهر: $label")
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("setting_theme_$modeKey")
                            )
                        }
                    }

                    Text("بالتة الألوان الرئيسية للهوية البصرية (Accent Palette):", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "EMERALD" to "الزمرد الملكي",
                            "ROYAL_BLUE" to "الأزرق المؤسسي",
                            "GOLD" to "الذهبي الدافئ",
                            "SLATE" to "الرمادي الاحترافي"
                        ).forEach { (palKey, label) ->
                            FilterChip(
                                selected = shopConfig.accentPalette.uppercase() == palKey,
                                onClick = {
                                    onUpdateSetting(MekkaPhoneRepository.KEY_ACCENT_PALETTE, palKey, "بالتة الألوان: $label")
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("setting_palette_$palKey")
                            )
                        }
                    }

                    Text("حجم الخطوط وكثافة عرض البيانات (UI Density):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "COMPACT" to "مدمجة (بيانات أكثر)",
                            "COMFORTABLE" to "قياسية مريحة",
                            "LARGE" to "كبيرة وواضحة"
                        ).forEach { (densKey, label) ->
                            FilterChip(
                                selected = shopConfig.uiDensity.uppercase() == densKey,
                                onClick = {
                                    onUpdateSetting(MekkaPhoneRepository.KEY_UI_DENSITY, densKey, "كثافة الواجهة: $label")
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("setting_density_$densKey")
                            )
                        }
                    }
                }
            }
        }

        // 2. Shop Identity & Branch Profile Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_shop_identity_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("هوية المتجر، بيانات الاتصال، والعملة الافتراضية", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = shopNameAr,
                            onValueChange = { shopNameAr = it },
                            label = { Text("اسم المتجر بالعربية") },
                            modifier = Modifier.weight(1f).testTag("setting_shop_name_ar_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = shopNameEn,
                            onValueChange = { shopNameEn = it },
                            label = { Text("الاسم بالإنجليزية") },
                            modifier = Modifier.weight(1f).testTag("setting_shop_name_en_input"),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = shopPhone,
                            onValueChange = { shopPhone = it },
                            label = { Text("هاتف الفرع (يظهر بالإيصال)") },
                            modifier = Modifier.weight(1f).testTag("setting_shop_phone_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = taxRegNo,
                            onValueChange = { taxRegNo = it },
                            label = { Text("الرقم الضريبي / السجل") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = shopAddress,
                        onValueChange = { shopAddress = it },
                        label = { Text("عنوان الفرع التفصيلي") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_shop_address_input"),
                        singleLine = true
                    )
                    Text("العملة الافتراضية للمتجر:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("EGP", "SAR", "AED", "USD").forEach { cur ->
                            FilterChip(
                                selected = currencyCode == cur,
                                onClick = { currencyCode = cur },
                                label = { Text(cur) },
                                modifier = Modifier.testTag("setting_currency_$cur")
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onUpdateShopIdentity(
                                shopNameAr,
                                shopNameEn,
                                shopPhone,
                                shopAddress,
                                taxRegNo,
                                currencyCode
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_shop_identity_button")
                    ) {
                        Text("حفظ هوية المتجر وتطبيقها على لوحة التحكم والإيصالات")
                    }
                }
            }
        }

        // 3. POS, Invoicing, Warranty & Credit Policy Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_pos_policy_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("إعدادات الفواتير، الضرائب، البيع الآجل، والضمان", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = taxPercentInput,
                            onValueChange = { taxPercentInput = it },
                            label = { Text("نسبة الضريبة الافتراضية (%)") },
                            modifier = Modifier.weight(1f).testTag("setting_tax_percent_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = warrantyDaysInput,
                            onValueChange = { warrantyDaysInput = it },
                            label = { Text("أيام الضمان الافتراضية") },
                            modifier = Modifier.weight(1f).testTag("setting_warranty_days_input"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = creditLimitInput,
                        onValueChange = { creditLimitInput = it },
                        label = { Text("الحد الائتماني الافتراضي للعميل الجديد (ج.م)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_credit_limit_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = receiptHeaderInput,
                        onValueChange = { receiptHeaderInput = it },
                        label = { Text("ترويسة إيصال المبيعات (Receipt Header)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_receipt_header_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = receiptFooterInput,
                        onValueChange = { receiptFooterInput = it },
                        label = { Text("تذييل إيصال المبيعات وشروط الاستبدال (Receipt Footer)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_receipt_footer_input"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_DEFAULT_TAX_PERCENT, taxPercentInput, "نسبة الضريبة")
                            onUpdateSetting(MekkaPhoneRepository.KEY_DEFAULT_WARRANTY_DAYS, warrantyDaysInput, "أيام الضمان")
                            onUpdateSetting(MekkaPhoneRepository.KEY_DEFAULT_CUSTOMER_CREDIT_LIMIT, creditLimitInput, "الحد الائتماني للعميل")
                            onUpdateSetting(MekkaPhoneRepository.KEY_RECEIPT_HEADER_NOTE, receiptHeaderInput, "ترويسة الإيصال")
                            onUpdateSetting(MekkaPhoneRepository.KEY_RECEIPT_FOOTER_NOTE, receiptFooterInput, "تذييل الإيصال")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_pos_settings_button")
                    ) {
                        Text("حفظ إعدادات الفواتير والضرائب والائتمان")
                    }

                    HorizontalDivider()

                    SettingToggleRow(
                        title = "تفعيل البيع الآجل والبيع بالدفعة المقدمة (Credit & Partial POS)",
                        subtitle = "يسمح بتسجيل المبالغ المتبقية كذمم مدينة على حساب العميل في دفتر الأستاذ",
                        checked = shopConfig.allowCreditSales,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_ALLOW_CREDIT_SALES, it.toString(), "البيع الآجل")
                        }
                    )

                    SettingToggleRow(
                        title = "إلزام اختيار عميل مسجل عند البيع الآجل أو الجزئي",
                        subtitle = "يمنع البيع الآجل للعميل النقدي غير المسجل لضمان دقة كشف حساب المستحقات",
                        checked = shopConfig.requireCustomerForCredit,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_REQUIRE_CUSTOMER_FOR_CREDIT, it.toString(), "إلزام العميل للبيع الآجل")
                        }
                    )

                    SettingToggleRow(
                        title = "إظهار تحليل تكلفة البضاعة المباعة (WAC) ومجمل الربح في نقطة البيع",
                        subtitle = "يمكن إخفاؤه عند عمل الكاشير أمام العملاء للحفاظ على سرية هامش الربح",
                        checked = shopConfig.showProfitAnalysisOnPos,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_SHOW_PROFIT_ON_POS, it.toString(), "إظهار الربح في نقطة البيع")
                        }
                    )
                }
            }
        }

        // 4. Inventory, Wallets & SMS Intelligence Rules Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_inventory_wallets_sms_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قواعد المخزون، حدود المحافظ، وذكاء رسائل SMS", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lowStockThresholdInput,
                            onValueChange = { lowStockThresholdInput = it },
                            label = { Text("حد نواقص المخزون الافتراضي") },
                            modifier = Modifier.weight(1f).testTag("setting_low_stock_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = walletDailyLimitInput,
                            onValueChange = { walletDailyLimitInput = it },
                            label = { Text("الحد اليومي للمحفظة (ج.م)") },
                            modifier = Modifier.weight(1f).testTag("setting_wallet_daily_limit_input"),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = walletMonthlyLimitInput,
                        onValueChange = { walletMonthlyLimitInput = it },
                        label = { Text("الحد الشهري للمحفظة الإلكترونية (ج.م)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_wallet_monthly_limit_input"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_LOW_STOCK_THRESHOLD, lowStockThresholdInput, "حد نواقص المخزون")
                            onUpdateSetting(MekkaPhoneRepository.KEY_WALLET_DAILY_LIMIT, walletDailyLimitInput, "الحد اليومي للمحفظة")
                            onUpdateSetting(MekkaPhoneRepository.KEY_WALLET_MONTHLY_LIMIT, walletMonthlyLimitInput, "الحد الشهري للمحفظة")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_limits_settings_button")
                    ) {
                        Text("حفظ حدود المخزون والمحافظ الإلكترونية")
                    }

                    HorizontalDivider()

                    SettingToggleRow(
                        title = "التقاط رسائل SMS المالية الواردة تلقائياً (SmsBroadcastReceiver)",
                        subtitle = "تحليل الرسائل فور وصولها للهاتف وإدراجها في طابور المراجعة",
                        checked = shopConfig.autoCaptureSmsEnabled,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_AUTO_CAPTURE_SMS, it.toString(), "الالتقاط التلقائي لرسائل SMS")
                        }
                    )

                    SettingToggleRow(
                        title = "الاعتماد التلقائي لرسائل SMS ذات الثقة العالية المطابقة لعملية متوقعة",
                        subtitle = "يرحّل القيد تلقائياً إذا تطابق المبلغ والمزود مع عملية متوقعة مسجلة مسبقاً",
                        checked = shopConfig.smsHighConfidenceAutoConfirm,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_SMS_HIGH_CONFIDENCE_AUTO, it.toString(), "اعتماد SMS عالي الثقة")
                        }
                    )

                    SettingToggleRow(
                        title = "جدولة المزامنة التزايدية تلقائياً بعد كل قيد مالي (Auto Delta Sync)",
                        subtitle = "يدفع التغييرات لطابور المزامنة السحابية فور استقرار الاتصال",
                        checked = shopConfig.autoSyncOnFinancialPost,
                        onCheckedChange = {
                            onUpdateSetting(MekkaPhoneRepository.KEY_AUTO_SYNC_ON_POST, it.toString(), "المزامنة التلقائية")
                        }
                    )

                    OutlinedButton(
                        onClick = onResetSettings,
                        modifier = Modifier.fillMaxWidth().testTag("reset_default_settings_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(" استعادة الإعدادات الافتراضية للمتجر والمظهر")
                    }
                }
            }
        }

        // 5. System Management & Diagnostics Navigation (Rule 4 & Rule 6: Developer RBAC/SMS tools isolated in Diagnostics)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إدارة المستخدمين وأدوات التشخيص المتقدمة", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "تم عزل أدوات المطورين واختبار الصلاحيات والرسائل في قسم Diagnostics المخصص لمنع التداخل مع العمل اليومي للكاشير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onNavigateToUsersPermissions,
                            modifier = Modifier.weight(1f).testTag("settings_open_users_permissions_btn")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Text(" المستخدمون والصلاحيات")
                        }
                        OutlinedButton(
                            onClick = onNavigateToDiagnostics,
                            modifier = Modifier.weight(1f).testTag("settings_open_diagnostics_btn")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Text(" Diagnostics")
                        }
                    }
                }
            }
        }

        // 6. Data Integrity Health Check Card (Requirement 55)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("data_integrity_health_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, FinancialPositive.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فحص سلامة البيانات الشامل (Data Integrity Health Check)", style = MaterialTheme.typography.titleMedium)
                        if (integrityReport != null) {
                            val badgeColor = when (integrityReport.overallStatus) {
                                HealthCheckLevel.PASS -> FinancialPositive
                                HealthCheckLevel.WARNING -> FinancialWarning
                                HealthCheckLevel.ERROR -> FinancialNegative
                            }
                            StatusPillBadge(text = integrityReport.overallStatus.arabicLabel, color = badgeColor)
                        }
                    }
                    Button(
                        onClick = onRunHealthCheck,
                        modifier = Modifier.fillMaxWidth().testTag("run_health_check_button")
                    ) {
                        Text("تشغيل فحص سلامة قاعدة البيانات والقيد المزدوج (16 فحصاً)")
                    }
                    integrityReport?.items?.forEach { check ->
                        val cColor = when (check.status) {
                            HealthCheckLevel.PASS -> FinancialPositive
                            HealthCheckLevel.WARNING -> FinancialWarning
                            HealthCheckLevel.ERROR -> FinancialNegative
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(check.titleArabic, style = MaterialTheme.typography.labelLarge)
                                Text(check.detailsArabic, style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPillBadge(text = check.status.name, color = cColor)
                        }
                    }
                }
            }
        }

        // 7. Full-Table Backup, External File Export/Import & Safe 10-Step Restore
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MekkaGoldPrimary.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نظام النسخ الاحتياطي الشامل لجميع الجداول (makkaphone-backup-v1)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "يشمل كافة الجداول (الأصناف، العملاء، الموردين، المبيعات وبنودها، المشتريات وبنودها، الصيانة وقطعها، المصروفات، المرتجعات، التحويلات، الخزينة والورديات، المحافظ، رسائل SMS، الإعدادات، التدقيق، ودفتر الأستاذ) مع بصمة SHA-256.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onCreateBackup,
                        modifier = Modifier.fillMaxWidth().testTag("create_backup_button")
                    ) {
                        Text("إنشاء نسخة احتياطية موثقة شاملة (makkaphone-backup-v1)")
                    }

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("import_external_backup_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Text(
                            if (confirmRestoreChecked) {
                                " استيراد واستعادة ملف نسخة احتياطية خارجي من الجهاز"
                            } else {
                                " استيراد وفحص ملف نسخة احتياطية خارجي (.json)"
                            }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmRestoreChecked,
                            onCheckedChange = { confirmRestoreChecked = it },
                            modifier = Modifier.testTag("confirm_restore_checkbox")
                        )
                        Text("أؤكد مراجعة ملخص النسخة الاحتياطية والموافقة الصريحة قبل الاستعادة الآمنة")
                    }

                    if (lastValidation != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("backup_validation_report_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (lastValidation.isValid) {
                                    FinancialPositive.copy(alpha = 0.12f)
                                } else {
                                    FinancialNegative.copy(alpha = 0.12f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = lastValidation.validationMessageArabic,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (lastValidation.isValid) FinancialPositive else FinancialNegative
                                )
                                Text(
                                    text = "الصيغة: ${lastValidation.backupFormatVersion} • Schema: v${lastValidation.schemaVersion} • إجمالي السجلات: ${lastValidation.recordCount} • SHA-256: ${lastValidation.computedChecksumSha256.take(16)}...",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (backups.isEmpty()) {
            item {
                MekkaEmptyStateCard(
                    title = "لا توجد نسخ احتياطية محفوظة بعد",
                    description = "أنشئ نسخة احتياطية بصيغة makkaphone-backup-v1 للتحقق من سلامة البصمة الرقمية وتصديرها كملف JSON خارجي.",
                    icon = Icons.Default.Backup
                )
            }
        } else {
            items(backups, key = { it.localId }) { bkp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bkp.backupFormatVersion, style = MaterialTheme.typography.titleMedium)
                            StatusPillBadge(text = "Schema v${bkp.schemaVersion} • ${bkp.totalRecordsCount} سجل", color = MekkaGoldPrimary)
                        }
                        Text(
                            text = "SHA-256: ${bkp.checksumSha256.take(24)}... • قيود الأستاذ: ${bkp.ledgerTransactionsCount} • حركات المخزن: ${bkp.inventoryMovementsCount}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onValidateBackup(bkp) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("فحص وتوثيق")
                            }
                            Button(
                                onClick = { onExecuteSafeRestore(bkp, confirmRestoreChecked) },
                                modifier = Modifier.weight(1f).testTag("restore_backup_btn_${bkp.localId}")
                            ) {
                                Text("استعادة آمنة")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingExportPayload = bkp.payloadJson
                                    exportBackupLauncher.launch("makkaphone-backup-v1-${bkp.createdAt}.json")
                                },
                                modifier = Modifier.weight(1f).testTag("export_backup_file_btn_${bkp.localId}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Text(" حفظ كملف")
                            }
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية مكة فون (${bkp.backupFormatVersion})")
                                        putExtra(Intent.EXTRA_TEXT, bkp.payloadJson)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة ملف النسخة الاحتياطية"))
                                },
                                modifier = Modifier.weight(1f).testTag("share_backup_btn_${bkp.localId}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text(" مشاركة")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

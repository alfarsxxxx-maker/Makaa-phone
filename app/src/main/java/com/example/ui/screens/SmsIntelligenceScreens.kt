package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.dao.SmsReviewItemProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.FinancialTransactionType
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.repository.MekkaPhoneRepository
import com.example.sms.SmsConfidenceLevel
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatSmsTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

/**
 * Rule 22: Categories for «الرسائل المالية»
 * - تحتاج مراجعة
 * - تم اعتمادها
 * - مرفوضة
 * - غير معروفة
 */
enum class SmsProductionCategory(val arabicLabel: String) {
    NEEDS_REVIEW("تحتاج مراجعة"),
    APPROVED("تم اعتمادها"),
    REJECTED("مرفوضة"),
    UNKNOWN("غير معروفة")
}

private fun categorizeSmsItem(item: SmsReviewItemProjection): SmsProductionCategory {
    val pending = item.pendingStatus?.uppercase()
    return when {
        pending == "AWAITING_CONFIRMATION" -> SmsProductionCategory.NEEDS_REVIEW
        pending == "CONFIRMED" || item.confirmedLedgerTransactionId != null -> SmsProductionCategory.APPROVED
        pending == "REJECTED" || pending == "IGNORED" -> SmsProductionCategory.REJECTED
        else -> SmsProductionCategory.UNKNOWN
    }
}

// ============================================================================
// 21, 22, 23, 24, 25. SMS INTELLIGENCE & PRODUCTION UI («الرسائل المالية»)
// Critical Rule: «SMS is evidence, NOT financial truth.»
// Production UI shows ONLY real SMS records (no test/sample SMS injection UI).
// ============================================================================

@Composable
fun SmsIntelligenceScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val smsReviewItems by viewModel.smsReviewItems.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasSmsPermission = result[Manifest.permission.RECEIVE_SMS] == true ||
            result[Manifest.permission.READ_SMS] == true
    }

    var selectedCategory by rememberSaveable { mutableStateOf(SmsProductionCategory.NEEDS_REVIEW) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pageSize by rememberSaveable { mutableIntStateOf(40) }

    // Modals
    var showTransparentPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showRegisterExpectedOpDialog by rememberSaveable { mutableStateOf(false) }
    var detailsSmsId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSmsId by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredItems = remember(smsReviewItems, selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        smsReviewItems.filter { item ->
            val cat = categorizeSmsItem(item)
            val matchesCat = cat == selectedCategory
            val matchesQuery = q.isEmpty() ||
                item.senderAddress.lowercase().contains(q) ||
                (item.detectedProviderCode?.lowercase()?.contains(q) == true) ||
                (item.extractedReference?.lowercase()?.contains(q) == true) ||
                (item.extractedCounterpartyPhone?.lowercase()?.contains(q) == true) ||
                item.messageBody.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    val pagedItems = remember(filteredItems, pageSize) {
        filteredItems.take(pageSize)
    }

    val categoryCounts = remember(smsReviewItems) {
        SmsProductionCategory.entries.associateWith { cat ->
            smsReviewItems.count { categorizeSmsItem(it) == cat }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sms_intelligence_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header & Rule 21 Evidence Principle Card («الرسائل المالية»)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الرسائل المالية",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "قاعدة الحوكمة: الرسائل النصية دليل مساعد وليست حقيقة مالية تلقائية",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MekkaEmeraldGreen
                            )
                        }
                        if (wallets.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showRegisterExpectedOpDialog = true },
                                modifier = Modifier.testTag("open_expected_op_dialog_btn")
                            ) {
                                Text("+ عملية متوقعة")
                            }
                        }
                    }

                    // Rule 21: Visual Pipeline Representation
                    SmsPipelineOverviewStrip()

                    // Rule 24: Transparent Automatic SMS Capture Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_auto_capture_status_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (hasSmsPermission && shopConfig.autoCaptureSmsEnabled) FinancialPositive else MekkaGoldAccent
                                    )
                                    Column {
                                        Text(
                                            text = "الالتقاط التلقائي لرسائل المحافظ المالية",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (hasSmsPermission) {
                                                "الصلاحية مفعلة • تُضاف رسائل المحافظ تلقائياً لقسم «تحتاج مراجعة» دون تغيير الأرصدة تلقائياً"
                                            } else {
                                                "غير مفعلة • اضغط على «توضيح وتفعيل الصلاحية» لمعرفة كيف تُقرأ الرسائل المالية بأمان"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = shopConfig.autoCaptureSmsEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !hasSmsPermission) {
                                            showTransparentPermissionDialog = true
                                        } else {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_AUTO_CAPTURE_SMS,
                                                enabled.toString(),
                                                "تبديل الالتقاط التلقائي لرسائل SMS"
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("sms_auto_capture_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showTransparentPermissionDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sms_permission_explanation_button")
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (hasSmsPermission) "سياسة خصوصية ومعالجة الرسائل" else "توضيح وتفعيل صلاحية الرسائل")
                                }
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            pageSize = 40
                        },
                        label = { Text("بحث بالمرسل، المزود، رقم المرجع، أو رقم الهاتف...") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_search_input")
                    )

                    // Rule 22: 4 Production Categories (تحتاج مراجعة / تم اعتمادها / مرفوضة / غير معروفة)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SmsProductionCategory.entries) { category ->
                            val count = categoryCounts[category] ?: 0
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = category
                                    pageSize = 40
                                },
                                label = {
                                    Text(
                                        text = "${category.arabicLabel} ($count)",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.16f),
                                    selectedLabelColor = MekkaEmeraldGreen
                                ),
                                modifier = Modifier.testTag("sms_category_tab_${category.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // 2. Categorized SMS List or Empty State (Rule 22 & Rule 27)
        if (pagedItems.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = "لا توجد رسائل في قسم «${selectedCategory.arabicLabel}» حالياً.",
                    subtitleArabic = "عند ورود رسائل مالية من مزودي المحافظ (فودافون كاش، إنستا باي، أورنج، اتصالات، WE) ستظهر هنا كدليل مساعد لمراجعتها واعتمادها.",
                    actionButtonLabel = "عرض سياسة الالتقاط التلقائي",
                    onActionClick = { showTransparentPermissionDialog = true }
                )
            }
        } else {
            items(pagedItems, key = { it.smsId }) { item ->
                SmsProductionMessageCard(
                    item = item,
                    onApproveClick = { confirmSmsId = item.smsId },
                    onRejectClick = {
                        val targetId = item.pendingTransactionId ?: item.smsId
                        viewModel.rejectOrIgnorePendingSms(targetId, "REJECTED")
                    },
                    onDetailsClick = { detailsSmsId = item.smsId }
                )
            }

            if (filteredItems.size > pagedItems.size) {
                item {
                    OutlinedButton(
                        onClick = { pageSize += 40 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عرض المزيد (${pagedItems.size} من ${filteredItems.size})")
                    }
                }
            }
        }
    }

    // Rule 24: Transparent Permission & Privacy Explanation Dialog
    if (showTransparentPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showTransparentPermissionDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MekkaEmeraldGreen)
                    Text("الشفافية الكاملة في قراءة الرسائل المالية", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransparencyBulletRow(
                        title = "1. ما هي الرسائل التي يتم قراءتها؟",
                        body = "فقط إشعارات العمليات المالية الواردة من مقدمي خدمات المحافظ المعتمدة (فودافون كاش، أورنج كاش، اتصالات كاش، WE Pay، وإنستا باي). يتم تجاهل أي رسائل شخصية."
                    )
                    TransparencyBulletRow(
                        title = "2. لماذا نحتاج هذه الصلاحية؟",
                        body = "لتوفير وقت الكاشير في استخراج المبلغ ورقم المرجع ورقم الهاتف ومطابقتها مع العمليات المتوقعة."
                    )
                    TransparencyBulletRow(
                        title = "3. كيف تتم معالجة الرسائل؟",
                        body = "تُعالج الرسائل محلياً بالكامل داخل جهازك عبر مراحل: التحليل ➔ التطبيع ➔ التحقق ➔ المطابقة ➔ حساب درجة الثقة."
                    )
                    TransparencyBulletRow(
                        title = "4. الرسائل دليل مساعد وليست حقيقة مالية تلقائية:",
                        body = "لا تقوم أي رسالة SMS بتغيير رصيد أي محفظة أو إضافة قيد في دفتر الأستاذ تلقائياً، بل توضع في قسم «تحتاج مراجعة» حتى تضغط على «اعتماد»."
                    )
                }
            },
            confirmButton = {
                if (!hasSmsPermission) {
                    Button(
                        onClick = {
                            showTransparentPermissionDialog = false
                            smsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier.testTag("request_sms_permission_button")
                    ) {
                        Text("موافق — منح صلاحية الرسائل")
                    }
                } else {
                    Button(
                        onClick = { showTransparentPermissionDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                    ) {
                        Text("تم الفهم")
                    }
                }
            },
            dismissButton = {
                if (!hasSmsPermission) {
                    TextButton(onClick = { showTransparentPermissionDialog = false }) {
                        Text("ليس الآن")
                    }
                }
            }
        )
    }

    // Confirm / Approve SMS Modal («اعتماد»)
    val itemToConfirm = remember(smsReviewItems, confirmSmsId) {
        smsReviewItems.firstOrNull { it.smsId == confirmSmsId }
    }
    if (itemToConfirm != null) {
        ConfirmSmsEvidenceDialog(
            item = itemToConfirm,
            wallets = wallets,
            onDismiss = { confirmSmsId = null },
            onConfirm = { explicitWalletId ->
                val pendingId = itemToConfirm.pendingTransactionId ?: itemToConfirm.smsId
                viewModel.confirmPendingSms(pendingId = pendingId, walletId = explicitWalletId)
                confirmSmsId = null
            }
        )
    }

    // Rule 23: SMS Details Modal with collapsible «التفاصيل الفنية»
    val itemForDetails = remember(smsReviewItems, detailsSmsId) {
        smsReviewItems.firstOrNull { it.smsId == detailsSmsId }
    }
    if (itemForDetails != null) {
        SmsDetailsDialog(
            item = itemForDetails,
            onDismiss = { detailsSmsId = null },
            onApprove = {
                detailsSmsId = null
                confirmSmsId = itemForDetails.smsId
            },
            onReject = {
                val targetId = itemForDetails.pendingTransactionId ?: itemForDetails.smsId
                viewModel.rejectOrIgnorePendingSms(targetId, "REJECTED")
                detailsSmsId = null
            }
        )
    }

    // Register Expected Wallet Operation Modal
    if (showRegisterExpectedOpDialog) {
        RegisterExpectedWalletOpDialog(
            wallets = wallets,
            onDismiss = { showRegisterExpectedOpDialog = false },
            onSubmit = { walletId, txType, amountStr, phoneStr ->
                viewModel.registerExpectedWalletOperation(
                    walletId = walletId,
                    expectedType = txType,
                    amountMajorStr = amountStr,
                    counterpartyPhone = phoneStr
                )
                showRegisterExpectedOpDialog = false
            }
        )
    }
}

/**
 * Rule 21: Pipeline visualization strip:
 * SMS ↓ Parse ↓ Normalize ↓ Validate ↓ Match ↓ Confidence ↓ Pending Review if needed ↓ Confirm ↓ Financial Transaction
 */
@Composable
private fun SmsPipelineOverviewStrip() {
    val steps = listOf(
        "رسالة SMS",
        "تحليل النص",
        "تطبيع البيانات",
        "تحقق وعدم تكرار",
        "مطابقة العملية",
        "تقييم الثقة",
        "تحتاج مراجعة",
        "اعتماد يدوي",
        "قيد مالي ذري"
    )
    Surface(
        color = MekkaEmeraldGreen.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(steps.size) { index ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${steps[index]}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MekkaEmeraldGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (index < steps.lastIndex) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Rule 22: Each message shows:
 * - Provider
 * - Amount
 * - Sender
 * - Reference
 * - Time
 * - Status
 * Actions: اعتماد / رفض / تفاصيل
 */
@Composable
private fun SmsProductionMessageCard(
    item: SmsReviewItemProjection,
    onApproveClick: () -> Unit,
    onRejectClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val category = categorizeSmsItem(item)
    val statusArabic = when (category) {
        SmsProductionCategory.NEEDS_REVIEW -> "تحتاج مراجعة"
        SmsProductionCategory.APPROVED -> "تم اعتمادها"
        SmsProductionCategory.REJECTED -> "مرفوضة"
        SmsProductionCategory.UNKNOWN -> "غير معروفة"
    }
    val statusColor = when (category) {
        SmsProductionCategory.NEEDS_REVIEW -> FinancialWarning
        SmsProductionCategory.APPROVED -> FinancialPositive
        SmsProductionCategory.REJECTED -> FinancialNegative
        SmsProductionCategory.UNKNOWN -> FinancialInfo
    }

    val friendlyProvider = FriendlyTerminologyMapper.friendlyProviderName(item.detectedProviderCode)
    val amountMinor = item.extractedAmountMinor ?: 0L
    val pendingId = item.pendingTransactionId ?: item.smsId
    val canReview = category == SmsProductionCategory.NEEDS_REVIEW && item.pendingTransactionId != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_review_card_$pendingId"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Provider + Status + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MekkaEmeraldGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = friendlyProvider,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MekkaEmeraldGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusArabic,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = if (amountMinor > 0L) Money.ofMinor(amountMinor).formatArabic() else "غير محدد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (amountMinor > 0L) MekkaEmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sender + Reference + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "المرسل: ${item.senderAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "المرجع: ${item.extractedReference?.takeIf { it.isNotBlank() } ?: "بدون مرجع"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الوقت: ${formatSmsTimestamp(item.receivedTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.extractedCounterpartyPhone.isNullOrBlank()) {
                    Text(
                        text = "الطرف الآخر: ${item.extractedCounterpartyPhone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Rule 22 Actions: اعتماد / رفض / تفاصيل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canReview) {
                    Button(
                        onClick = onApproveClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_sms_btn_$pendingId")
                    ) {
                        Text("اعتماد", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRejectClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FinancialNegative),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reject_sms_btn_$pendingId")
                    ) {
                        Text("رفض", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onDetailsClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("details_sms_btn_$pendingId")
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تفاصيل", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Rule 23: SMS DETAILS
 * Technical information is hidden under an expandable section: «التفاصيل الفنية»
 * (Parser, Confidence, Matching rules, Provider detection, Raw evidence, Processing state).
 */
@Composable
private fun SmsDetailsDialog(
    item: SmsReviewItemProjection,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    val category = categorizeSmsItem(item)
    val canApproveOrReject = category == SmsProductionCategory.NEEDS_REVIEW && item.pendingTransactionId != null
    val confLevel = SmsConfidenceLevel.fromBps(item.confidenceBps ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("تفاصيل الرسالة المالية", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main user-friendly financial summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmsDetailRow("المزود:", FriendlyTerminologyMapper.friendlyProviderName(item.detectedProviderCode))
                        SmsDetailRow(
                            "المبلغ المستخرج:",
                            Money.ofMinor(item.extractedAmountMinor ?: 0L).formatArabic()
                        )
                        SmsDetailRow(
                            "الرسوم المستخرجة:",
                            Money.ofMinor(item.extractedFeeMinor ?: 0L).formatArabic()
                        )
                        SmsDetailRow("اسم المرسل:", item.senderAddress)
                        SmsDetailRow("رقم المرجع:", item.extractedReference ?: "غير متوفر")
                        SmsDetailRow("رقم الطرف الآخر:", item.extractedCounterpartyPhone ?: "غير متوفر")
                        SmsDetailRow("وقت الاستلام:", formatSmsTimestamp(item.receivedTimestamp))
                        SmsDetailRow("الحالة الحالية:", category.arabicLabel)
                    }
                }

                Text("نص الرسالة الواردة:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.messageBody,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Rule 23: Collapsible «التفاصيل الفنية»
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTechnicalDetails = !showTechnicalDetails }
                        .testTag("toggle_sms_technical_details_btn"),
                    color = MekkaGoldAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = MekkaGoldAccent)
                            Text(
                                text = "التفاصيل الفنية",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "توسيع التفاصيل الفنية"
                        )
                    }
                }

                AnimatedVisibility(visible = showTechnicalDetails) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_technical_details_section")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SmsDetailRow("المحلل (Parser):", "MekkaSmsRegexParser v3.0 (Multi-Provider)")
                            SmsDetailRow(
                                "درجة الثقة (Confidence):",
                                "${confLevel.arabicLabel} (${(item.confidenceBps ?: 0) / 100}%)"
                            )
                            SmsDetailRow(
                                "قواعد المطابقة (Matching Rules):",
                                if (item.targetWalletId != null) "مطابقة محفظة تلقائية (${item.targetWalletId})"
                                else "يتطلب تحديد المحفظة عند الاعتماد"
                            )
                            SmsDetailRow("اكتشاف المزود (Provider Detection):", item.detectedProviderCode ?: "UNKNOWN")
                            SmsDetailRow("نوع العملية المكتشف:", item.detectedOperationType ?: "UNKNOWN")
                            SmsDetailRow("معرف الدليل الخام (Raw Evidence ID):", item.smsId)
                            SmsDetailRow(
                                "حالة المعالجة الداخلية (Processing State):",
                                "${if (item.isProcessed) "PROCESSED" else "RAW"} / ${item.pendingStatus ?: "NONE"}"
                            )
                            if (item.confirmedLedgerTransactionId != null) {
                                SmsDetailRow("قيد دفتر الأستاذ المرتبط:", item.confirmedLedgerTransactionId)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canApproveOrReject) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FinancialNegative)
                    ) {
                        Text("رفض")
                    }
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                    ) {
                        Text("اعتماد الرسالة")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        },
        dismissButton = {
            if (canApproveOrReject) {
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

@Composable
private fun ConfirmSmsEvidenceDialog(
    item: SmsReviewItemProjection,
    wallets: List<WalletDerivedProjection>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Explicit wallet selection — pre-fill only if already matched by expected operation, otherwise require explicit click
    var selectedWalletId by rememberSaveable(item.targetWalletId) {
        mutableStateOf(item.targetWalletId.orEmpty())
    }
    var errText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("اعتماد الرسالة وترحيل القيد المالي", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "بمجرد الاعتماد سيتم إنشاء قيد مالي ذري غير قابل للتعديل في دفتر الأستاذ للمحفظة المختارة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("المزود: ${FriendlyTerminologyMapper.friendlyProviderName(item.detectedProviderCode)}", fontWeight = FontWeight.Bold)
                        Text(
                            text = "المبلغ: ${Money.ofMinor(item.extractedAmountMinor ?: 0L).formatArabic()}",
                            fontWeight = FontWeight.ExtraBold,
                            color = MekkaEmeraldGreen
                        )
                        Text("المرجع: ${item.extractedReference ?: "-"}")
                    }
                }

                Text("اختر المحفظة المستهدفة للترحيل بشكل صريح *:", fontWeight = FontWeight.Bold)
                if (wallets.isEmpty()) {
                    Text(
                        text = "لا توجد محافظ مسجلة بعد. يرجى إضافة محفظة من شاشة (المحافظ) أولاً.",
                        color = FinancialNegative,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.walletId }) { wal ->
                            FilterChip(
                                selected = selectedWalletId == wal.walletId,
                                onClick = {
                                    selectedWalletId = wal.walletId
                                    errText = null
                                },
                                label = { Text("${wal.walletNameArabic} (${wal.walletNumber})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = MekkaEmeraldGreen
                                ),
                                modifier = Modifier.testTag("confirm_sms_select_wallet_${wal.walletNumber}")
                            )
                        }
                    }
                }

                if (!errText.isNullOrBlank()) {
                    Text(errText!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedWalletId.isBlank()) {
                        errText = "يرجى اختيار المحفظة المستهدفة أولاً"
                    } else {
                        onConfirm(selectedWalletId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                modifier = Modifier.testTag("confirm_sms_final_submit_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأكيد واعتماد في دفتر الأستاذ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun RegisterExpectedWalletOpDialog(
    wallets: List<WalletDerivedProjection>,
    onDismiss: () -> Unit,
    onSubmit: (String, FinancialTransactionType, String, String) -> Unit
) {
    var selectedWalletId by rememberSaveable { mutableStateOf("") }
    var isDeposit by rememberSaveable { mutableStateOf(true) }
    var expectedAmountStr by rememberSaveable { mutableStateOf("") }
    var counterpartyPhone by rememberSaveable { mutableStateOf("") }
    var errText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل عملية محفظة متوقعة للمطابقة", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "سجل بيانات العملية المتوقع وصول رسالة SMS لها ليقوم محرك المطابقة برفع درجة الثقة وربطها تلقائياً عند وصول الرسالة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("اختر المحفظة *:", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(wallets, key = { it.walletId }) { wal ->
                        FilterChip(
                            selected = selectedWalletId == wal.walletId,
                            onClick = {
                                selectedWalletId = wal.walletId
                                errText = null
                            },
                            label = { Text("${wal.walletNameArabic} (${wal.walletNumber})") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isDeposit,
                        onClick = { isDeposit = true },
                        label = { Text("إيداع / استلام وارد") }
                    )
                    FilterChip(
                        selected = !isDeposit,
                        onClick = { isDeposit = false },
                        label = { Text("سحب / تحويل صادر") }
                    )
                }
                OutlinedTextField(
                    value = expectedAmountStr,
                    onValueChange = {
                        expectedAmountStr = it
                        errText = null
                    },
                    label = { Text("المبلغ المتوقع (ج.م) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = counterpartyPhone,
                    onValueChange = { counterpartyPhone = it },
                    label = { Text("رقم هاتف الطرف الآخر (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errText.isNullOrBlank()) {
                    Text(errText!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        selectedWalletId.isBlank() -> errText = "يرجى اختيار المحفظة أولاً"
                        expectedAmountStr.isBlank() -> errText = "يرجى إدخال المبلغ المتوقع"
                        else -> onSubmit(
                            selectedWalletId,
                            if (isDeposit) FinancialTransactionType.DEPOSIT else FinancialTransactionType.WITHDRAW,
                            expectedAmountStr,
                            counterpartyPhone
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("حفظ في طابور المطابقة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun SmsDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun TransparencyBulletRow(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MekkaEmeraldGreen
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

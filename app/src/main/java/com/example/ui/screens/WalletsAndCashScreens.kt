package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.TransferEntity
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.financial.WalletDomainPolicyEngine
import com.example.financial.WalletUserOperationType
import com.example.ui.components.LocalizedFormValidator
import com.example.ui.components.MekkaProcessingButton
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

private fun formatWalletTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

// ============================================================================
// 12, 13, 14, 15. WALLETS & 8-STEP WALLET OPERATION WIZARD
// Operations: إيداع / سحب / تحويل (Never expose WALLET_TOPUP / WALLET_SEND / WALLET_RECEIVE)
// ============================================================================

@Composable
fun WalletsScreen(viewModel: MekkaPhoneViewModel) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val providers by viewModel.walletProviders.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()
    val pendingQuickOp by viewModel.pendingWalletQuickOperation.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // Rule 12: User-friendly operation types (إيداع / سحب / تحويل)
    var selectedOperationType by rememberSaveable { mutableStateOf(WalletUserOperationType.DEPOSIT) }
    // Rule 13: Explicit Wallet / Provider selection — NEVER implicit firstOrNull()!
    var selectedWalletId by rememberSaveable { mutableStateOf("") }
    var amountMajorStr by rememberSaveable { mutableStateOf("") }
    var customerPhoneOrAccount by rememberSaveable { mutableStateOf("") }
    var customNetworkFeeStr by rememberSaveable { mutableStateOf("") }
    var customShopFeeStr by rememberSaveable { mutableStateOf("") }
    var operationNotes by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    // 8-Step Wizard State (Rule 13):
    // 1: اختر المحفظة / المزود
    // 2: أدخل المبلغ
    // 3: حساب الرسوم
    // 4: عرض الإجمالي
    // 5: مراجعة
    // 6: تأكيد
    // 7: نجاح
    // 8: إيصال
    var wizardStep by rememberSaveable { mutableIntStateOf(1) }
    var completedTransfer by remember { mutableStateOf<TransferEntity?>(null) }
    var showAddWalletDialog by rememberSaveable { mutableStateOf(false) }
    var historyPageSize by rememberSaveable { mutableIntStateOf(30) }

    androidx.compose.runtime.LaunchedEffect(pendingQuickOp) {
        val op = pendingQuickOp
        if (op != null) {
            selectedOperationType = op
            validationError = null
            completedTransfer = null
            wizardStep = if (selectedWalletId.isBlank()) 1 else 2
            viewModel.consumeWalletQuickOperation()
        }
    }

    val selectedWallet = remember(wallets, selectedWalletId) {
        wallets.firstOrNull { it.walletId == selectedWalletId }
    }

    // Rule 14: Domain-calculated Fee Breakdown (Amount, Network fee, Shop fee, Customer total, Shop net)
    val feeBreakdown = remember(
        selectedOperationType,
        amountMajorStr,
        selectedWallet?.providerCode,
        shopConfig.defaultWalletCommissionBps,
        customNetworkFeeStr,
        customShopFeeStr
    ) {
        viewModel.calculateWalletFeeBreakdown(
            operationType = selectedOperationType,
            amountMajorStr = amountMajorStr,
            providerCode = selectedWallet?.providerCode ?: "VODAFONE_CASH",
            customNetworkFeeMajorStr = customNetworkFeeStr,
            customShopFeeMajorStr = customShopFeeStr
        )
    }

    // Active step indicator calculation (Steps 1..8)
    val currentVisualStep = remember(wizardStep, selectedWalletId, amountMajorStr) {
        when {
            wizardStep >= 5 -> wizardStep
            selectedWalletId.isBlank() -> 1
            amountMajorStr.isBlank() || feeBreakdown.amountMinor <= 0L -> 2
            wizardStep in 3..4 -> wizardStep
            else -> 4
        }
    }

    val wizardStepLabels = listOf(
        1 to "1. اختر المحفظة / المزود",
        2 to "2. أدخل المبلغ",
        3 to "3. حساب الرسوم",
        4 to "4. عرض الإجمالي",
        5 to "5. مراجعة",
        6 to "6. تأكيد",
        7 to "7. نجاح",
        8 to "8. إيصال"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wallets_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CashAndWalletControlCenterCard(viewModel = viewModel)
        }

        // Top Header & Add Wallet Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "المحافظ الإلكترونية",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            val totalWalletBalance = wallets.sumOf { it.derivedBalanceMinor }
                            Text(
                                text = "إجمالي أرصدة المحافظ: ${Money.ofMinor(totalWalletBalance).formatArabic()} • المحافظ النشطة: ${wallets.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddWalletDialog = true },
                            modifier = Modifier.testTag("add_wallet_top_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة محفظة", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ محفظة")
                        }
                    }

                    // 8-Step Wizard Progress Strip (Rule 13)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wizardStepLabels) { (stepNum, label) ->
                            val isCurrent = currentVisualStep == stepNum
                            val isDone = currentVisualStep > stepNum
                            Surface(
                                color = when {
                                    isCurrent -> MekkaEmeraldGreen
                                    isDone -> MekkaEmeraldGreen.copy(alpha = 0.16f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                                        isDone -> MekkaEmeraldGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Wallets Cards Overview
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(wallets, key = { it.walletId }) { w ->
                    val isSelected = selectedWalletId == w.walletId
                    val dailyRem = (w.dailyLimitMinor - w.todayOutflowMinor).coerceAtLeast(0L)
                    val dailyProgress = if (w.dailyLimitMinor > 0L) {
                        (w.todayOutflowMinor.toFloat() / w.dailyLimitMinor.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Card(
                        modifier = Modifier
                            .width(265.dp)
                            .testTag("wallet_card_${w.walletId}"),
                        onClick = {
                            selectedWalletId = w.walletId
                            validationError = null
                            if (wizardStep < 2) wizardStep = 2
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MekkaEmeraldGreen.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
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
                                Text(
                                    text = w.walletNameArabic,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = w.providerNameArabic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MekkaEmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "الرقم: ${w.walletNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Money.ofMinor(w.derivedBalanceMinor).formatArabic(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MekkaEmeraldGreen
                            )
                            LinearProgressIndicator(
                                progress = { dailyProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (dailyProgress >= 0.85f) FinancialWarning else MekkaEmeraldGreen
                            )
                            Text(
                                text = "المتبقي اليومي: ${Money.ofMinor(dailyRem).formatArabic()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Wizard Step 7 & 8: Success & Receipt View (Rule 13)
        if ((wizardStep == 7 || wizardStep == 8) && completedTransfer != null) {
            val tr = completedTransfer!!
            val friendlyKind = WalletDomainPolicyEngine.friendlyOperationLabel(tr.transferKind, tr.notes)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_wizard_receipt_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "نجاح العملية",
                            tint = FinancialPositive,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (wizardStep == 7) "7. نجاح العملية ($friendlyKind)" else "8. إيصال عملية المحفظة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FinancialPositive
                        )
                        Text(
                            text = "رقم المرجع: #${tr.transferReference}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("نوع العملية:")
                            Text(friendlyKind, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("رقم هاتف / حساب العميل:")
                            Text(tr.customerPhoneOrAccount, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المبلغ الأساسي:")
                            Text(Money.ofMinor(tr.principalAmountMinor).formatArabic(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("رسوم الشبكة:")
                            Text(Money.ofMinor(tr.providerFeeMinor).formatArabic(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("عمولة المحل:")
                            Text(Money.ofMinor(tr.shopCommissionMinor).formatArabic(), fontWeight = FontWeight.Bold, color = FinancialPositive)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("صافي التسوية:")
                            Text(Money.ofMinor(tr.netSettlementMinor).formatArabic(), fontWeight = FontWeight.ExtraBold, color = MekkaEmeraldGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("التوقيت:")
                            Text(formatWalletTimestamp(tr.transferTimestamp), style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (wizardStep == 7) {
                                Button(
                                    onClick = { wizardStep = 8 },
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("عرض الإيصال (خطوة 8)", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val receiptText = "إيصال مكة فون | $friendlyKind | مرجع #${tr.transferReference} | المبلغ: ${Money.ofMinor(tr.principalAmountMinor).formatArabic()} | هاتف: ${tr.customerPhoneOrAccount}"
                                        clipboard.setText(AnnotatedString(receiptText))
                                        viewModel.showFeedbackMessage("تم نسخ إيصال المحفظة للحافظة")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الإيصال", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ الإيصال")
                                }
                            }

                            Button(
                                onClick = {
                                    // Reset wizard for new operation
                                    selectedWalletId = ""
                                    amountMajorStr = ""
                                    customerPhoneOrAccount = ""
                                    customNetworkFeeStr = ""
                                    customShopFeeStr = ""
                                    operationNotes = ""
                                    validationError = null
                                    completedTransfer = null
                                    wizardStep = 1
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_new_operation_button")
                            ) {
                                Text("عملية محفظة جديدة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Main 6-Step Interactive Wizard Card (Rules 12, 13, 14, 15)
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
                        // Rule 12: إيداع / سحب / تحويل
                        Text(
                            text = "نوع عملية المحفظة:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WalletUserOperationType.entries.forEach { opType ->
                                val selected = selectedOperationType == opType
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedOperationType = opType
                                        validationError = null
                                    },
                                    label = {
                                        Text(
                                            text = opType.arabicLabel,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.18f),
                                        selectedLabelColor = MekkaEmeraldGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wallet_op_type_${opType.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = selectedOperationType.arabicDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()

                        // Step 1: «اختر المحفظة / المزود»
                        Text(
                            text = "الخطوة 1: اختر المحفظة / المزود",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(wallets, key = { it.walletId }) { w ->
                                FilterChip(
                                    selected = selectedWalletId == w.walletId,
                                    onClick = {
                                        selectedWalletId = w.walletId
                                        validationError = null
                                        if (wizardStep < 2) wizardStep = 2
                                    },
                                    label = {
                                        Text("${w.walletNameArabic} (${Money.ofMinor(w.derivedBalanceMinor).formatArabic()})")
                                    },
                                    modifier = Modifier.testTag("wallet_select_${w.walletId}")
                                )
                            }
                        }

                        // Step 2: «أدخل المبلغ» + رقم هاتف العميل
                        Text(
                            text = "الخطوة 2: أدخل المبلغ ورقم هاتف / حساب العميل",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = amountMajorStr,
                                onValueChange = {
                                    amountMajorStr = it
                                    validationError = null
                                    if (wizardStep < 3) wizardStep = 3
                                },
                                label = { Text("المبلغ (ج.م) *") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_amount_input")
                            )
                            OutlinedTextField(
                                value = customerPhoneOrAccount,
                                onValueChange = {
                                    customerPhoneOrAccount = it
                                    validationError = null
                                },
                                label = { Text("رقم هاتف العميل / الحساب *") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_phone_input")
                            )
                        }

                        // Step 3: «حساب الرسوم» (Configurable domain fees + optional adjustment)
                        Text(
                            text = "الخطوة 3: حساب الرسوم (تُحتسب تلقائياً وفق إعدادات المتجر مع إمكانية التعديل)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customNetworkFeeStr,
                                onValueChange = {
                                    customNetworkFeeStr = it
                                    validationError = null
                                },
                                label = {
                                    Text("رسوم الشبكة (الافتراضي: ${Money.ofMinor(feeBreakdown.networkFeeMinor).formatArabic()})")
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_network_fee_input")
                            )
                            OutlinedTextField(
                                value = customShopFeeStr,
                                onValueChange = {
                                    customShopFeeStr = it
                                    validationError = null
                                },
                                label = {
                                    Text("عمولة المحل (الافتراضي: ${Money.ofMinor(feeBreakdown.shopFeeMinor).formatArabic()})")
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallet_shop_fee_input")
                            )
                        }

                        OutlinedTextField(
                            value = operationNotes,
                            onValueChange = { operationNotes = it },
                            label = { Text("ملاحظات إضافية (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Step 4: «عرض الإجمالي» (Rule 14: Amount, Network fee, Shop fee, Customer total, Shop net)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_fee_breakdown_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "الخطوة 4: عرض الإجمالي وتفصيل الرسوم",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المبلغ الأساسي:")
                                    Text(Money.ofMinor(feeBreakdown.amountMinor).formatArabic(), fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("رسوم الشبكة:")
                                    Text(Money.ofMinor(feeBreakdown.networkFeeMinor).formatArabic(), fontWeight = FontWeight.Bold, color = FinancialWarning)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("عمولة المحل:")
                                    Text(Money.ofMinor(feeBreakdown.shopFeeMinor).formatArabic(), fontWeight = FontWeight.Bold, color = FinancialPositive)
                                }
                                HorizontalDivider()
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = if (selectedOperationType == WalletUserOperationType.WITHDRAWAL) {
                                            "إجمالي العميل (المستحق صرفه للعميل):"
                                        } else {
                                            "إجمالي العميل (المطلوب تحصيله من العميل):"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = Money.ofMinor(feeBreakdown.customerTotalMinor).formatArabic(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MekkaEmeraldGreen
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("صافي ربح المحل من العملية:")
                                    Text(
                                        text = Money.ofMinor(feeBreakdown.shopNetMinor).formatArabic(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (feeBreakdown.shopNetMinor >= 0L) FinancialPositive else FinancialNegative
                                    )
                                }
                            }
                        }

                        if (!validationError.isNullOrBlank()) {
                            Text(
                                text = validationError!!,
                                color = FinancialNegative,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Step 5 CTA: «مراجعة»
                        Button(
                            onClick = {
                                val check = viewModel.validateWalletWizardOperation(
                                    walletId = selectedWalletId,
                                    operationType = selectedOperationType,
                                    amountMajorStr = amountMajorStr,
                                    customerPhoneOrAccount = customerPhoneOrAccount,
                                    feeBreakdown = feeBreakdown
                                )
                                if (!check.isValid) {
                                    validationError = check.errorArabic
                                    return@Button
                                }
                                validationError = null
                                wizardStep = 5
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_wizard_review_button")
                        ) {
                            Text("الخطوة 5: مراجعة العملية قبل التأكيد", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Historical Wallet Operations (Friendly Arabic Labels — Rule 12)
        item {
            Text(
                text = "سجل عمليات المحافظ (${transfers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (transfers.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = "لا توجد عمليات محافظ مسجلة بعد.",
                    subtitleArabic = "اختر المحفظة بشكل صريح من الأعلى وأدخل المبلغ لتنفيذ أول عملية إيداع أو سحب أو تحويل.",
                    actionButtonLabel = "إضافة محفظة جديدة",
                    onActionClick = {
                        showAddWalletDialog = true
                    }
                )
            }
        } else {
            items(transfers.take(historyPageSize), key = { it.localId }) { tr ->
                val friendlyLabel = WalletDomainPolicyEngine.friendlyOperationLabel(tr.transferKind, tr.notes)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MekkaEmeraldGreen.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = friendlyLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MekkaEmeraldGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "مرجع #${tr.transferReference}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "الطرف المقابل: ${tr.customerPhoneOrAccount} • ${formatWalletTimestamp(tr.transferTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "رسوم الشبكة: ${Money.ofMinor(tr.providerFeeMinor).formatArabic()} • عمولة المحل: ${Money.ofMinor(tr.shopCommissionMinor).formatArabic()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Money.ofMinor(tr.principalAmountMinor).formatArabic(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MekkaEmeraldGreen
                        )
                    }
                }
            }

            if (transfers.size > historyPageSize) {
                item {
                    OutlinedButton(
                        onClick = { historyPageSize += 30 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عرض المزيد من عمليات المحافظ")
                    }
                }
            }
        }
    }

    // Step 5 & Step 6 Dialog: «مراجعة» + «تأكيد» (Rule 13 & V3.2 Section 16: Provider, Operation, Amount, Fee, Net amount, Balance before, Balance after)
    if (wizardStep == 5 || wizardStep == 6) {
        val walletBalanceBeforeMinor = selectedWallet?.derivedBalanceMinor ?: 0L
        val walletBalanceAfterMinor = when (selectedOperationType) {
            WalletUserOperationType.WITHDRAWAL -> walletBalanceBeforeMinor + feeBreakdown.amountMinor
            WalletUserOperationType.DEPOSIT,
            WalletUserOperationType.TRANSFER -> (walletBalanceBeforeMinor - feeBreakdown.amountMinor - feeBreakdown.networkFeeMinor).coerceAtLeast(0L)
        }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) wizardStep = 4 },
            title = {
                Text(
                    text = "5. مراجعة و 6. تأكيد عملية (${selectedOperationType.arabicLabel})",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المحفظة / المزود: ${selectedWallet?.walletNameArabic ?: "-"} (${selectedWallet?.providerNameArabic ?: "-"})", fontWeight = FontWeight.Bold)
                    Text("نوع العملية: ${selectedOperationType.arabicLabel}", fontWeight = FontWeight.Bold, color = MekkaEmeraldGreen)
                    Text("رقم العميل / الحساب: $customerPhoneOrAccount")
                    HorizontalDivider()
                    Text("• المبلغ الأساسي: ${Money.ofMinor(feeBreakdown.amountMinor).formatArabic()}")
                    Text("• رسوم الشبكة: ${Money.ofMinor(feeBreakdown.networkFeeMinor).formatArabic()}")
                    Text("• عمولة المحل: ${Money.ofMinor(feeBreakdown.shopFeeMinor).formatArabic()}")
                    Text(
                        text = "• إجمالي العميل: ${Money.ofMinor(feeBreakdown.customerTotalMinor).formatArabic()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = MekkaEmeraldGreen
                    )
                    Text(
                        text = "• صافي ربح المحل: ${Money.ofMinor(feeBreakdown.shopNetMinor).formatArabic()}",
                        fontWeight = FontWeight.Bold,
                        color = FinancialPositive
                    )
                    HorizontalDivider()
                    Text("• الرصيد قبل العملية: ${Money.ofMinor(walletBalanceBeforeMinor).formatArabic()}", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "• الرصيد بعد العملية: ${Money.ofMinor(walletBalanceAfterMinor).formatArabic()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = MekkaEmeraldGreen
                    )

                    if (isProcessing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("جاري تنفيذ العملية وترحيل القيد في دفتر الأستاذ...")
                        }
                    }

                    if (!validationError.isNullOrBlank()) {
                        Text(validationError!!, color = FinancialNegative, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                MekkaProcessingButton(
                    idleTextArabic = "6. تأكيد وتنفيذ العملية",
                    processingTextArabic = "جارٍ تنفيذ عملية المحفظة...",
                    isProcessing = isProcessing,
                    onClick = {
                        wizardStep = 6
                        viewModel.executeWalletWizardSafe(
                            walletId = selectedWalletId,
                            operationType = selectedOperationType,
                            customerPhoneOrAccount = customerPhoneOrAccount,
                            feeBreakdown = feeBreakdown,
                            notes = operationNotes,
                            onSuccess = { transfer ->
                                completedTransfer = transfer
                                wizardStep = 7 // Step 7: نجاح
                            },
                            onError = { err ->
                                validationError = err
                                wizardStep = 5
                            }
                        )
                    },
                    containerColor = MekkaEmeraldGreen,
                    modifier = Modifier.testTag("wallet_wizard_confirm_button")
                )
            },
            dismissButton = {
                if (!isProcessing) {
                    TextButton(onClick = { wizardStep = 4 }) {
                        Text("رجوع للتعديل")
                    }
                }
            }
        )
    }

    // Add Wallet Modal
    if (showAddWalletDialog) {
        var selectedProviderId by rememberSaveable { mutableStateOf("") }
        var newWalletName by rememberSaveable { mutableStateOf("") }
        var newWalletNumber by rememberSaveable { mutableStateOf("") }
        var addWalletErr by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddWalletDialog = false },
            title = { Text("+ تسجيل محفظة إلكترونية جديدة", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر مزود الخدمة بشكل صريح:", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(providers, key = { it.localId }) { prov ->
                            FilterChip(
                                selected = selectedProviderId == prov.localId,
                                onClick = {
                                    selectedProviderId = prov.localId
                                    addWalletErr = null
                                },
                                label = { Text(prov.nameArabic) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newWalletName,
                        onValueChange = {
                            newWalletName = it
                            addWalletErr = null
                        },
                        label = { Text("اسم المحفظة (مثال: فودافون كاش خط 2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newWalletNumber,
                        onValueChange = {
                            newWalletNumber = it
                            addWalletErr = null
                        },
                        label = { Text("رقم المحفظة / الخط") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!addWalletErr.isNullOrBlank()) {
                        Text(addWalletErr!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            selectedProviderId.isBlank() -> addWalletErr = "يرجى اختيار مزود الخدمة أولاً"
                            newWalletName.isBlank() || newWalletNumber.isBlank() -> addWalletErr = "يرجى إدخال اسم ورقم المحفظة"
                            else -> {
                                viewModel.createWallet(selectedProviderId, newWalletName, newWalletNumber)
                                showAddWalletDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                ) {
                    Text("حفظ المحفظة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWalletDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

// ============================================================================
// 16 & 17. CASH DRAWER («الخزينة») & GUIDED CASH CLOSEOUT («إغلاق الوردية»)
// Actions: إيداع نقدية / صرف نقدية / تسوية فرق / إغلاق الوردية
// ============================================================================

enum class CashDrawerFriendlyAction(
    val arabicLabel: String,
    val internalTxType: FinancialTransactionType
) {
    CASH_DEPOSIT("إيداع نقدية", FinancialTransactionType.CASH_IN),
    CASH_WITHDRAWAL("صرف نقدية", FinancialTransactionType.CASH_OUT),
    DISCREPANCY_ADJUSTMENT("تسوية فرق", FinancialTransactionType.ADJUSTMENT)
}

@Composable
fun CashDrawerScreen(viewModel: MekkaPhoneViewModel) {
    val drawers by viewModel.cashDrawers.collectAsStateWithLifecycle()
    val sessions by viewModel.cashSessions.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()

    val mainDrawer = drawers.firstOrNull { it.isMainDrawer } ?: drawers.firstOrNull()
    val expectedBalanceMinor = mainDrawer?.derivedBalanceMinor ?: 0L

    var activeModalAction by rememberSaveable { mutableStateOf<CashDrawerFriendlyAction?>(null) }
    var showCloseoutWizard by rememberSaveable { mutableStateOf(false) }
    var txPageSize by rememberSaveable { mutableIntStateOf(30) }

    val cashTransactions = remember(ledgerTransactions) {
        ledgerTransactions.filter {
            it.sourceAccountType == FinancialAccountType.CASH_DRAWER ||
                it.destinationAccountType == FinancialAccountType.CASH_DRAWER
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cash_drawer_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Live Expected Balance Card (Rule 16)
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
                        Column {
                            Text(
                                text = "الخزينة",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = mainDrawer?.nameArabic ?: "درج النقدية الرئيسي",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = MekkaEmeraldGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = Money.ofMinor(expectedBalanceMinor).formatArabic(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MekkaEmeraldGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "الوارد النقدي اليوم",
                            value = Money.ofMinor(mainDrawer?.todayInflowMinor ?: 0L).formatArabic(),
                            valueColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "المنصرف النقدي اليوم",
                            value = Money.ofMinor(mainDrawer?.todayOutflowMinor ?: 0L).formatArabic(),
                            valueColor = FinancialNegative,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    // Rule 16 Actions: إيداع نقدية / صرف نقدية / تسوية فرق / إغلاق الوردية
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { activeModalAction = CashDrawerFriendlyAction.CASH_DEPOSIT },
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cash_action_deposit")
                        ) {
                            Text("إيداع نقدية", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { activeModalAction = CashDrawerFriendlyAction.CASH_WITHDRAWAL },
                            colors = ButtonDefaults.buttonColors(containerColor = FinancialNegative),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cash_action_withdraw")
                        ) {
                            Text("صرف نقدية", fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { activeModalAction = CashDrawerFriendlyAction.DISCREPANCY_ADJUSTMENT },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cash_action_adjustment")
                        ) {
                            Text("تسوية فرق", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showCloseoutWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cash_action_closeout")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "إغلاق الوردية", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إغلاق الوردية", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Shift Closeout History
        if (sessions.isNotEmpty()) {
            item {
                Text(
                    text = "سجل إغلاق الورديات والمطابقة (${sessions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            items(sessions.take(10), key = { it.localId }) { session ->
                val disc = session.discrepancyMinor ?: 0L
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (session.status == "CLOSED") "وردية مغلقة" else "وردية مفتوحة",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatWalletTimestamp(session.closedAt ?: session.openedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الرصيد المتوقع: ${Money.ofMinor(session.closingDerivedBalanceMinor ?: session.openingDerivedBalanceMinor).formatArabic()}")
                            Text("النقدية الفعلية: ${Money.ofMinor(session.closingCountedCashMinor ?: 0L).formatArabic()}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الفرق: ${Money.ofMinor(disc).formatArabic()}",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (disc == 0L) FinancialPositive else FinancialNegative
                            )
                            if (session.notes.isNotBlank()) {
                                Text(
                                    text = session.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Cash Transactions (Friendly Arabic labels — Rule 16: never expose enum names)
        item {
            Text(
                text = "حركات الخزينة النقدية (${cashTransactions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (cashTransactions.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = "لا توجد حركات نقدية مسجلة في الخزينة بعد.",
                    subtitleArabic = "سجل إيداع نقدية أو ابدأ عملية بيع لتحديث رصيد الخزينة.",
                    actionButtonLabel = "إيداع نقدية",
                    onActionClick = { activeModalAction = CashDrawerFriendlyAction.CASH_DEPOSIT }
                )
            }
        } else {
            items(cashTransactions.take(txPageSize), key = { it.localId }) { tx ->
                val isInflow = tx.destinationAccountType == FinancialAccountType.CASH_DRAWER
                val friendlyLabel = FriendlyTerminologyMapper.friendlyCashTransactionLabel(tx)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friendlyLabel,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isInflow) FinancialPositive else FinancialNegative
                            )
                            Text(
                                text = tx.descriptionArabic,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "مرجع #${tx.referenceNumber} • ${formatWalletTimestamp(tx.transactionTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Money.ofMinor(tx.amountMinor).formatArabic(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isInflow) FinancialPositive else FinancialNegative
                        )
                    }
                }
            }

            if (cashTransactions.size > txPageSize) {
                item {
                    OutlinedButton(
                        onClick = { txPageSize += 30 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عرض المزيد من حركات الخزينة")
                    }
                }
            }
        }
    }

    // Friendly Cash Operation Dialog (إيداع نقدية / صرف نقدية / تسوية فرق)
    val currentAction = activeModalAction
    if (currentAction != null) {
        var amountStr by rememberSaveable { mutableStateOf("") }
        var reasonStr by rememberSaveable { mutableStateOf("") }
        var errText by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { activeModalAction = null },
            title = {
                Text(currentAction.arabicLabel, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            errText = null
                        },
                        label = { Text("المبلغ النقدي (ج.م) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_op_amount_input")
                    )
                    OutlinedTextField(
                        value = reasonStr,
                        onValueChange = {
                            reasonStr = it
                            errText = null
                        },
                        label = { Text("البيان / السبب *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_op_reason_input")
                    )
                    if (!errText.isNullOrBlank()) {
                        Text(errText!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (amountStr.isBlank()) {
                            errText = "يرجى إدخال المبلغ"
                            return@Button
                        }
                        if (reasonStr.isBlank()) {
                            errText = "يرجى كتابة سبب واضح للعملية"
                            return@Button
                        }
                        viewModel.executeCashOperation(
                            operationType = currentAction.internalTxType,
                            amountMajorStr = amountStr,
                            reasonArabic = "${currentAction.arabicLabel} — ${reasonStr.trim()}"
                        )
                        activeModalAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("cash_op_confirm_button")
                ) {
                    Text("تأكيد ${currentAction.arabicLabel}")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeModalAction = null }) { Text("إلغاء") }
            }
        )
    }

    // Rule 17: Guided Cash Closeout Workflow Dialog («إغلاق الوردية»)
    if (showCloseoutWizard) {
        GuidedCashCloseoutDialog(
            viewModel = viewModel,
            isProcessing = isProcessing,
            onDismiss = { showCloseoutWizard = false }
        )
    }
}

/**
 * Rule 17: Guided Cash Closeout Workflow
 * Shows: الرصيد المتوقع, النقدية الفعلية, الفرق
 * If there is a difference, requires: Reason, Optional note, and Manager/Owner authorization for large discrepancies.
 */
@Composable
private fun GuidedCashCloseoutDialog(
    viewModel: MekkaPhoneViewModel,
    isProcessing: Boolean,
    onDismiss: () -> Unit
) {
    var countedCashStr by rememberSaveable { mutableStateOf("") }
    var discrepancyReason by rememberSaveable { mutableStateOf("") }
    var optionalNote by rememberSaveable { mutableStateOf("") }
    var managerConfirmed by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) } // 0 = Form & Evaluation, 1 = Success
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    val evaluation = remember(countedCashStr, discrepancyReason, managerConfirmed) {
        viewModel.evaluateCashCloseoutState(
            countedCashMajorStr = countedCashStr,
            discrepancyReasonArabic = discrepancyReason,
            managerConfirmed = managerConfirmed
        )
    }

    val presetReasons = listOf(
        "خطأ في عد الفكة",
        "مصروف نثري غير مسجل بعد",
        "تحصيل نقدي قيد المراجعة",
        "عجز أو زيادة بالدرج"
    )

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = if (step == 0) "تأكيد إغلاق الخزينة" else "تم إغلاق الوردية بنجاح",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            if (step == 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "تم الإغلاق", tint = FinancialPositive, modifier = Modifier.size(48.dp))
                    Text("تم إغلاق الوردية وحفظ محضر المطابقة في السجل!", fontWeight = FontWeight.ExtraBold, color = FinancialPositive)
                    Text("الرصيد المتوقع: ${Money.ofMinor(evaluation.expectedBalanceMinor).formatArabic()}")
                    Text("الرصيد الفعلي: ${Money.ofMinor(evaluation.countedCashMinor).formatArabic()}")
                    Text(
                        text = "الفرق المسجل: ${Money.ofMinor(evaluation.discrepancyMinor).formatArabic()}",
                        fontWeight = FontWeight.Bold,
                        color = if (evaluation.discrepancyMinor == 0L) FinancialPositive else FinancialNegative
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "سيتم تسجيل الرصيد الفعلي وإغلاق يوم العمل.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Rule 17 & V3.2 Section 15: الرصيد المتوقع / الرصيد الفعلي / الفرق
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد المتوقع:")
                                Text(
                                    text = Money.ofMinor(evaluation.expectedBalanceMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                            if (countedCashStr.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الرصيد الفعلي:")
                                    Text(
                                        text = Money.ofMinor(evaluation.countedCashMinor).formatArabic(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الفرق:")
                                    Text(
                                        text = Money.ofMinor(evaluation.discrepancyMinor).formatArabic(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (evaluation.discrepancyMinor == 0L) FinancialPositive else FinancialNegative
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "لا يمكن تعديل عملية الإغلاق بعد اعتمادها إلا من خلال صلاحية مخولة.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FinancialWarning
                    )

                    OutlinedTextField(
                        value = countedCashStr,
                        onValueChange = {
                            countedCashStr = it
                            errorMsg = null
                        },
                        label = { Text("النقدية الفعلية المعدودة بالخزينة (ج.م) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("closeout_counted_cash_input")
                    )

                    if (countedCashStr.isNotBlank() && evaluation.hasDiscrepancy) {
                        Text(
                            text = "يوجد فرق قدره (${Money.ofMinor(evaluation.discrepancyMinor).formatArabic()}) — يرجى تحديد سبب الفرق:",
                            style = MaterialTheme.typography.labelLarge,
                            color = FinancialWarning,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presetReasons) { r ->
                                FilterChip(
                                    selected = discrepancyReason == r,
                                    onClick = {
                                        discrepancyReason = r
                                        errorMsg = null
                                    },
                                    label = { Text(r) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = discrepancyReason,
                            onValueChange = {
                                discrepancyReason = it
                                errorMsg = null
                            },
                            label = { Text("سبب الفرق (إلزامي عند وجود فرق) *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("closeout_discrepancy_reason_input")
                        )

                        if (evaluation.requiresManagerAuthorization) {
                            Surface(
                                color = FinancialWarning.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = managerConfirmed,
                                        onCheckedChange = {
                                            managerConfirmed = it
                                            errorMsg = null
                                        },
                                        modifier = Modifier.testTag("closeout_manager_auth_checkbox")
                                    )
                                    Text(
                                        text = "اعتماد المدير / المالك للفرق الكبير (أكبر من 100 ج.م)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = optionalNote,
                        onValueChange = { optionalNote = it },
                        label = { Text("ملاحظة إضافية (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val activeErr = errorMsg ?: evaluation.validationErrorArabic
                    if (!activeErr.isNullOrBlank()) {
                        Text(
                            text = activeErr,
                            color = FinancialNegative,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                MekkaProcessingButton(
                    idleTextArabic = "تأكيد الإغلاق",
                    processingTextArabic = "جارٍ إغلاق الخزينة...",
                    isProcessing = isProcessing,
                    enabled = evaluation.isValidToSubmit,
                    onClick = {
                        viewModel.executeCashCloseoutSafe(
                            countedCashMajorStr = countedCashStr,
                            discrepancyReasonArabic = discrepancyReason,
                            optionalNote = optionalNote,
                            managerConfirmed = managerConfirmed,
                            onSuccess = { step = 1 },
                            onError = { err -> errorMsg = err }
                        )
                    },
                    containerColor = MekkaEmeraldGreen,
                    modifier = Modifier.testTag("closeout_confirm_button")
                )
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                ) {
                    Text("إغلاق")
                }
            }
        },
        dismissButton = {
            if (step == 0 && !isProcessing) {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

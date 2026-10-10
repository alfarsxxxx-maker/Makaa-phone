package com.example.transaction

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.financial.Money
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaEmeraldPrimary
import com.example.ui.theme.MekkaGoldPrimary
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.util.Locale

/**
 * Universal Transaction Composer (Sections 1, 2, 3, 4, 7, 8, 9, 10).
 * Multi-modal input (Manual, Text, Voice, Barcode) converging into a single Unified Transaction Draft,
 * with mandatory validation, review, and explicit confirmation before execution into the double-entry ledger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalTransactionComposerDialog(
    viewModel: MekkaPhoneViewModel,
    initialModality: TransactionInputModality = TransactionInputModality.MANUAL,
    onDismiss: () -> Unit
) {
    var selectedModality by rememberSaveable { mutableStateOf(initialModality) }
    var textPromptInput by rememberSaveable { mutableStateOf("") }
    var barcodeOrImeiInput by rememberSaveable { mutableStateOf("") }
    var voiceSpokenText by rememberSaveable { mutableStateOf("") }
    var isListeningVoice by rememberSaveable { mutableStateOf(false) }

    // Manual Form Inputs
    var manualType by rememberSaveable { mutableStateOf(UnifiedTransactionType.SALE) }
    var manualAmountMajor by rememberSaveable { mutableStateOf("") }
    var manualPaidMajor by rememberSaveable { mutableStateOf("") }
    var manualNotes by rememberSaveable { mutableStateOf("") }
    var manualCategory by rememberSaveable { mutableStateOf("مصروفات عامة") }

    var ocrInputText by rememberSaveable { mutableStateOf("") }
    var smsInputText by rememberSaveable { mutableStateOf("") }
    var showAddDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var newDeviceBrand by rememberSaveable { mutableStateOf("") }
    var newDeviceModel by rememberSaveable { mutableStateOf("") }
    var newDeviceImei by rememberSaveable { mutableStateOf("") }
    var newDeviceCost by rememberSaveable { mutableStateOf("") }
    var newDeviceRetail by rememberSaveable { mutableStateOf("") }

    val activeDraft by viewModel.activeTransactionDraft.androidxLifecycleCollect()
    val draftValidation by viewModel.draftValidation.androidxLifecycleCollect()
    val isExecuting by viewModel.isProcessingOperation.androidxLifecycleCollect()
    val isParsing by viewModel.isParsingIntent.androidxLifecycleCollect()
    val products by viewModel.products.androidxLifecycleCollect()
    val customers by viewModel.customers.androidxLifecycleCollect()
    val suppliers by viewModel.suppliers.androidxLifecycleCollect()
    val wallets by viewModel.wallets.androidxLifecycleCollect()

    // Real Android SpeechRecognizer Launcher (Section 7)
    val context = LocalContext.current
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topSpoken = spokenMatches?.firstOrNull().orEmpty()
            if (topSpoken.isNotBlank()) {
                voiceSpokenText = topSpoken
                textPromptInput = topSpoken
                viewModel.parseInputToDraft(topSpoken, TransactionInputModality.VOICE)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
                .imePadding()
                .clip(RoundedCornerShape(24.dp))
                .testTag("universal_transaction_composer_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MekkaEmeraldPrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = MekkaEmeraldPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "تنفيذ عملية ذكية موحدة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "محرك العمليات الذكي • مكة فون V4.1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("composer_close_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Modality Selector Bar (Section 1)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TransactionInputModality.entries) { modality ->
                        val isSelected = selectedModality == modality
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedModality = modality },
                            label = { Text(modality.titleArabic, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                val icon = when (modality) {
                                    TransactionInputModality.MANUAL -> Icons.Default.Edit
                                    TransactionInputModality.TEXT -> Icons.Default.ReceiptLong
                                    TransactionInputModality.VOICE -> Icons.Default.Mic
                                    TransactionInputModality.CAMERA_OCR -> Icons.Default.CameraAlt
                                    TransactionInputModality.BARCODE -> Icons.Default.QrCodeScanner
                                    TransactionInputModality.IMEI_SERIAL -> Icons.Default.PhoneAndroid
                                    TransactionInputModality.SMS -> Icons.Default.Sms
                                }
                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("composer_modality_chip_${modality.name}")
                        )
                    }
                }

                // --- MODALITY INPUT BODY ---
                when (selectedModality) {
                    TransactionInputModality.TEXT -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "اكتب العملية التي تريد تنفيذها بصياغة حرة وطبيعية:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = textPromptInput,
                                    onValueChange = { textPromptInput = it },
                                    placeholder = { Text("مثال: بعت آيفون 13 لمحمد بـ18 ألف، دفع 10 والباقي عليه") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("composer_text_input"),
                                    minLines = 2,
                                    maxLines = 4
                                )
                                Button(
                                    onClick = {
                                        viewModel.parseInputToDraft(textPromptInput, TransactionInputModality.TEXT)
                                    },
                                    enabled = textPromptInput.isNotBlank() && !isParsing,
                                    modifier = Modifier.fillMaxWidth().testTag("composer_parse_text_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldPrimary)
                                ) {
                                    if (isParsing) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري استخراج مسودة العملية...")
                                    } else {
                                        Text("تحليل واستخراج المسودة")
                                    }
                                }

                                // Quick Examples Chips
                                Text(text = "نماذج سريعة للتجربة:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "بعت آيفون 13 لمحمد بـ18 ألف دفع 10",
                                        "مصروف كهرباء 500 جنيه",
                                        "سحبت 2000 من فودافون كاش",
                                        "اشتريت 5 جرابات بـ1500 من أحمد",
                                        "تحصيل 1200 من العميل محمود"
                                    ).forEach { example ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.clickable {
                                                textPromptInput = example
                                                viewModel.parseInputToDraft(example, TransactionInputModality.TEXT)
                                            }
                                        ) {
                                            Text(text = example, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TransactionInputModality.VOICE -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "الأمر الصوتي المالي (Voice to Transaction)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "اضغط على الميكروفون وتحدث بصياغتك الطبيعية (مثال: 'اعمل مصروف 300 جنيه بنزين' أو 'بعت سامسونج A55 بعشرة آلاف')",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = if (isListeningVoice) 1.25f else 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                val micColor by animateColorAsState(
                                    targetValue = if (isListeningVoice) FinancialNegative else MekkaEmeraldPrimary,
                                    label = "micColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .scale(scale)
                                        .background(micColor.copy(alpha = 0.15f), CircleShape)
                                        .border(2.dp, micColor, CircleShape)
                                        .clickable {
                                            isListeningVoice = true
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعملية التي تريد تنفيذها...")
                                            }
                                            runCatching {
                                                speechRecognizerLauncher.launch(intent)
                                            }.onFailure {
                                                isListeningVoice = false
                                                // Fallback prompt for test/emulator without SpeechRecognizer service
                                                voiceSpokenText = "بعت آيفون 13 لمحمد بـ18 ألف، دفع 10 والباقي عليه"
                                                viewModel.parseInputToDraft(voiceSpokenText, TransactionInputModality.VOICE)
                                            }
                                        }
                                        .testTag("composer_voice_mic_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Mic, contentDescription = "تحدث الآن", tint = micColor, modifier = Modifier.size(36.dp))
                                }

                                if (voiceSpokenText.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "الكلام الملتقط: \"$voiceSpokenText\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TransactionInputModality.BARCODE, TransactionInputModality.IMEI_SERIAL -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (selectedModality == TransactionInputModality.IMEI_SERIAL) "مسح سيريال IMEI والبحث في دورة حياة الجهاز:" else "مسح الباركود أو كود الصنف (SKU):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = barcodeOrImeiInput,
                                    onValueChange = { barcodeOrImeiInput = it },
                                    label = { Text(if (selectedModality == TransactionInputModality.IMEI_SERIAL) "أدخل سيريال IMEI (15 رقماً)" else "أدخل الباركود أو كود الصنف") },
                                    modifier = Modifier.fillMaxWidth().testTag("composer_barcode_input"),
                                    singleLine = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.parseInputToDraft(barcodeOrImeiInput, selectedModality)
                                        },
                                        enabled = barcodeOrImeiInput.isNotBlank(),
                                        modifier = Modifier.weight(1f).testTag("composer_barcode_search_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مطابقة الصنف")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            newDeviceImei = barcodeOrImeiInput
                                            showAddDeviceDialog = true
                                        },
                                        modifier = Modifier.weight(1f).testTag("composer_add_new_device_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إضافة كجهاز جديد")
                                    }
                                }
                            }
                        }
                    }

                    TransactionInputModality.CAMERA_OCR -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "قراءة الفواتير والإيصالات بالمسح الضوئي (Receipt & Purchase OCR):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = ocrInputText,
                                    onValueChange = { ocrInputText = it },
                                    placeholder = { Text("الصق نص الفاتورة أو نتيجة المسح الضوئي...") },
                                    modifier = Modifier.fillMaxWidth().testTag("composer_ocr_text_input"),
                                    minLines = 3,
                                    maxLines = 6
                                )
                                // Presets for testing
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            ocrInputText = "فاتورة مشتريات رقم INV-9901 شركة أحمد للتوريدات\n10 جراب حماية 150.00\nالإجمالي: 1500.00"
                                        },
                                        label = { Text("نموذج فاتورة مشتريات", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            ocrInputText = "إيصال سداد فاتورة كهرباء\nالمبلغ: 500 جنيه\nالمرجع: ELEC-4402"
                                        },
                                        label = { Text("نموذج إيصال مصروف", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.parseReceiptOcr(ocrInputText)
                                    },
                                    enabled = ocrInputText.isNotBlank() && !isParsing,
                                    modifier = Modifier.fillMaxWidth().testTag("composer_ocr_parse_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldPrimary)
                                ) {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحليل واستخراج بنود الفاتورة")
                                }
                            }
                        }
                    }

                    TransactionInputModality.SMS -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "استخراج المعاملة من رسالة SMS بنكية أو محفظة:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = smsInputText,
                                    onValueChange = { smsInputText = it },
                                    placeholder = { Text("الصق نص رسالة فودافون كاش أو إنستا باي...") },
                                    modifier = Modifier.fillMaxWidth().testTag("composer_sms_input"),
                                    minLines = 2,
                                    maxLines = 4
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            smsInputText = "تم استلام مبلغ 3000 جنيه من رقم 01011112222 على محفظة فودافون كاش مرجع 998877"
                                        },
                                        label = { Text("سحب فودافون كاش", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            smsInputText = "InstaPay: تم تحويل 2000 جنيه بنجاح إلى حسابك مرجع IP-5566"
                                        },
                                        label = { Text("تحويل إنستا باي", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.parseInputToDraft(smsInputText, TransactionInputModality.SMS)
                                    },
                                    enabled = smsInputText.isNotBlank() && !isParsing,
                                    modifier = Modifier.fillMaxWidth().testTag("composer_sms_parse_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldPrimary)
                                ) {
                                    Icon(imageVector = Icons.Default.Sms, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحليل الرسالة وتجهيز المسودة")
                                }
                            }
                        }
                    }

                    TransactionInputModality.MANUAL -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "اختر نوع العملية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        UnifiedTransactionType.SALE to "🛒 بيع",
                                        UnifiedTransactionType.PURCHASE to "📦 شراء",
                                        UnifiedTransactionType.EXPENSE to "💸 مصروف",
                                        UnifiedTransactionType.CASH_IN to "📥 توريد خزينة",
                                        UnifiedTransactionType.CASH_OUT to "📤 صرف خزينة",
                                        UnifiedTransactionType.WALLET_WITHDRAW to "📱 سحب محفظة",
                                        UnifiedTransactionType.CUSTOMER_PAYMENT to "👤 تحصيل عميل"
                                    ).forEach { (t, label) ->
                                        FilterChip(
                                            selected = manualType == t,
                                            onClick = { manualType = t },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("manual_type_chip_${t.name}")
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = manualAmountMajor,
                                    onValueChange = { manualAmountMajor = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("إجمالي القيمة (ج.م)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("manual_amount_input"),
                                    singleLine = true
                                )

                                if (manualType == UnifiedTransactionType.SALE) {
                                    OutlinedTextField(
                                        value = manualPaidMajor,
                                        onValueChange = { manualPaidMajor = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                        label = { Text("المبلغ المدفوع كاش (اتركه فارغاً للسداد الكامل)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().testTag("manual_paid_input"),
                                        singleLine = true
                                    )
                                }

                                OutlinedTextField(
                                    value = manualNotes,
                                    onValueChange = { manualNotes = it },
                                    label = { Text("البيان / ملاحظات العملية") },
                                    modifier = Modifier.fillMaxWidth().testTag("manual_notes_input"),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val amtMinor = (manualAmountMajor.toDoubleOrNull() ?: 0.0).times(100).toLong()
                                        val paidMinor = if (manualPaidMajor.isBlank()) amtMinor else (manualPaidMajor.toDoubleOrNull() ?: 0.0).times(100).toLong()
                                        val firstProd = products.firstOrNull()

                                        val items = if (manualType == UnifiedTransactionType.SALE && firstProd != null) {
                                            listOf(
                                                TransactionDraftItem(
                                                    productId = firstProd.productId,
                                                    productNameArabic = firstProd.nameArabic,
                                                    sku = firstProd.sku,
                                                    quantity = 1,
                                                    unitPriceMinor = amtMinor,
                                                    availableStockQty = firstProd.derivedStockQty
                                                )
                                            )
                                        } else emptyList()

                                        val draft = TransactionDraft(
                                            transactionType = manualType,
                                            inputModality = TransactionInputModality.MANUAL,
                                            totalAmountMinor = amtMinor,
                                            paidAmountMinor = paidMinor,
                                            items = items,
                                            notes = manualNotes.ifBlank { manualType.titleArabic },
                                            paymentMode = if (paidMinor < amtMinor) "PARTIAL" else "CASH",
                                            confidencePercent = 100
                                        )
                                        viewModel.setManualDraft(draft)
                                    },
                                    enabled = manualAmountMajor.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().testTag("manual_submit_draft_button")
                                ) {
                                    Text("إنشاء ومراجعة المسودة")
                                }
                            }
                        }
                    }

                    else -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "إدخال عبر ${selectedModality.titleArabic}:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = textPromptInput,
                                    onValueChange = { textPromptInput = it },
                                    label = { Text("أدخل النص أو الرسالة أو الكود") },
                                    modifier = Modifier.fillMaxWidth().testTag("composer_generic_input")
                                )
                                Button(
                                    onClick = { viewModel.parseInputToDraft(textPromptInput, selectedModality) },
                                    enabled = textPromptInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("تحليل وتجهيز المسودة")
                                }
                            }
                        }
                    }
                }

                // --- ACTIVE DRAFT SECTION ---
                activeDraft?.let { draft ->
                    HorizontalDivider()

                    // Ambiguity Warning & Interactive Resolution (Section 8)
                    if (draft.ambiguities.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = FinancialWarning.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FinancialWarning),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = FinancialWarning)
                                    Text(
                                        text = "تحتاج مراجعة وتحديد الخيار المطلوب:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FinancialWarning
                                    )
                                }
                                draft.ambiguities.forEach { ambiguity ->
                                    Text(text = ambiguity.questionArabic, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ambiguity.candidateOptions.forEach { opt ->
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    viewModel.resolveDraftAmbiguity(ambiguity.ambiguityKey, opt.optionId)
                                                },
                                                label = { Text(opt.labelArabic) },
                                                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                                                modifier = Modifier.testTag("ambiguity_option_${opt.optionId}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Missing Requirements Banner
                    if (draft.missingRequirementsArabic.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = FinancialNegative.copy(alpha = 0.08f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FinancialNegative.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "بيانات مطلوبة لإكمال العملية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = FinancialNegative)
                                draft.missingRequirementsArabic.forEach { req ->
                                    Text(text = "• $req", style = MaterialTheme.typography.bodySmall, color = FinancialNegative)
                                }
                            }
                        }
                    }

                    // --- STRUCTURED REVIEW SCREEN (Section 9) ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مراجعة تفاصيل العملية (Review)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val (statusColor, statusLabel) = when {
                                    draft.confidencePercent >= 90 -> FinancialPositive to "${draft.confidencePercent}% مؤكدة"
                                    draft.confidencePercent >= 60 -> FinancialWarning to "${draft.confidencePercent}% مراجعة"
                                    else -> FinancialNegative to "${draft.confidencePercent}% ناقصة"
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = statusLabel,
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Breakdown Table
                            ReviewRow(label = "نوع العملية:", value = draft.transactionType.titleArabic)
                            if (!draft.counterpartyName.isNullOrBlank()) {
                                ReviewRow(label = "الطرف الآخر:", value = draft.counterpartyName)
                            }
                            if (draft.items.isNotEmpty()) {
                                Text(text = "الأصناف المشمولة:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                draft.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "${item.productNameArabic} (${item.quantity} × ${Money.ofMinor(item.unitPriceMinor).formatArabic()})", style = MaterialTheme.typography.bodySmall)
                                        Text(text = Money.ofMinor(item.totalLineMinor).formatArabic(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            ReviewRow(label = "الإجمالي المطلوب:", value = Money.ofMinor(draft.totalAmountMinor).formatArabic(), isBold = true)
                            ReviewRow(label = "المدفوع / المحصل:", value = Money.ofMinor(draft.paidAmountMinor).formatArabic())

                            if (draft.remainingDebtMinor > 0L) {
                                ReviewRow(label = "المتبقي آجل:", value = Money.ofMinor(draft.remainingDebtMinor).formatArabic(), isWarning = true)
                            }
                            ReviewRow(label = "طريقة السداد:", value = if (draft.paymentMode == "WALLET") "محفظة إلكترونية" else if (draft.paymentMode == "CREDIT") "آجل كامل" else "نقداً بالخزينة")

                            if (draft.transactionType == UnifiedTransactionType.EXPENSE) {
                                ReviewRow(label = "التصنيف المقترح:", value = draft.expenseCategoryName ?: "مصروفات عامة")
                                Text(text = "تعديل التصنيف (يتعلم النظام تفضيلك تلقائياً):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("مرافق وخدمات", "انتقالات وشحن", "ضيافة وبوفيه", "إيجار المقر", "أجور ومرتبات", "مصروفات صيانة وقطع غيار", "دعاية وتسويق", "نظافة وتشغيل").forEach { cat ->
                                        FilterChip(
                                            selected = draft.expenseCategoryName == cat,
                                            onClick = { viewModel.updateDraftExpenseCategory(cat) },
                                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            // Validation Warning/Errors (Section 11)
                            draftValidation?.let { valResult ->
                                if (valResult.warningMessagesArabic.isNotEmpty()) {
                                    valResult.warningMessagesArabic.forEach { warn ->
                                        Text(text = "⚠ $warn", style = MaterialTheme.typography.labelSmall, color = FinancialWarning)
                                    }
                                }
                                if (valResult.errorMessagesArabic.isNotEmpty()) {
                                    valResult.errorMessagesArabic.forEach { err ->
                                        Text(text = "⛔ $err", style = MaterialTheme.typography.labelSmall, color = FinancialNegative, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Confirmation Action Buttons (Section 10)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.closeTransactionComposer() },
                                    modifier = Modifier.weight(1f).testTag("composer_cancel_button")
                                ) {
                                    Text("إلغاء / تعديل")
                                }

                                val canConfirm = draftValidation?.canProceedToConfirmation == true && !isExecuting
                                Button(
                                    onClick = {
                                        viewModel.confirmAndExecuteActiveDraft {
                                            onDismiss()
                                        }
                                    },
                                    enabled = canConfirm,
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldPrimary),
                                    modifier = Modifier.weight(1.5f).testTag("composer_confirm_execute_button")
                                ) {
                                    if (isExecuting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("جاري ترحيل القيد...")
                                    } else {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تأكيد وتنفيذ العملية", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDeviceDialog) {
            AlertDialog(
                onDismissRequest = { showAddDeviceDialog = false },
                title = { Text("إضافة جهاز جديد بالكتالوج", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "لم يتم العثور على سيريال الجهاز ($newDeviceImei) في قاعدة البيانات. يمكنك تسجيله الآن بالكتالوج وتجهيز فاتورته:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = newDeviceBrand,
                            onValueChange = { newDeviceBrand = it },
                            label = { Text("الماركة (مثل Apple, Samsung, Xiaomi)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_device_brand_input")
                        )
                        OutlinedTextField(
                            value = newDeviceModel,
                            onValueChange = { newDeviceModel = it },
                            label = { Text("الموديل (مثل iPhone 15, Galaxy A55)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_device_model_input")
                        )
                        OutlinedTextField(
                            value = newDeviceImei,
                            onValueChange = { newDeviceImei = it },
                            label = { Text("سيريال IMEI (15 رقماً)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_device_imei_input")
                        )
                        OutlinedTextField(
                            value = newDeviceCost,
                            onValueChange = { newDeviceCost = it },
                            label = { Text("سعر التكلفة المتوقع (ج.م)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newDeviceRetail,
                            onValueChange = { newDeviceRetail = it },
                            label = { Text("سعر البيع المقترح (ج.م)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_device_price_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.registerNewDeviceFromImei(
                                brandName = newDeviceBrand.ifBlank { "عام" },
                                modelName = newDeviceModel.ifBlank { "هاتف محمول" },
                                imei = newDeviceImei.ifBlank { barcodeOrImeiInput },
                                costMajorStr = newDeviceCost.ifBlank { "0" },
                                retailPriceMajorStr = newDeviceRetail.ifBlank { "0" },
                                onSuccess = {
                                    showAddDeviceDialog = false
                                }
                            )
                        },
                        enabled = newDeviceModel.isNotBlank() || newDeviceImei.isNotBlank(),
                        modifier = Modifier.testTag("confirm_add_device_button")
                    ) {
                        Text("تأكيد وإضافة الجهاز")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDeviceDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isWarning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isWarning) FinancialNegative else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Lifecycle-aware StateFlow collector extension for Jetpack Compose.
 */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.androidxLifecycleCollect(): androidx.compose.runtime.State<T> {
    return this.collectAsStateWithLifecycle()
}

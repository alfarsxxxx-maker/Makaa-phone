package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.backup.BackupValidationReport
import com.example.backup.RestoreMode
import com.example.database.entities.BackupMetadataEntity
import com.example.financial.FriendlyTerminologyMapper
import com.example.navigation.MekkaDestination
import com.example.repository.MekkaPhoneRepository
import com.example.sync.MekkaBackgroundSyncScheduler
import com.example.sync.BackgroundSyncPolicy
import com.example.ui.components.ExpandableTechnicalDetails
import com.example.ui.components.LocalizedFormValidator
import com.example.ui.components.MekkaProcessingButton
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.UnsavedChangesDialog
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaGoldPrimary
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reorganized Settings Center (`الإعدادات`, Part 3 Rule 7).
 *
 * Replaces one huge monolithic screen with 9 structured sections:
 * 0. الحساب والأمان (Current user, PIN/security, Sessions, Roles)
 * 1. بيانات المتجر (Shop name, Phone, Address, Logo, Receipt identity)
 * 2. الفواتير (Receipt settings, Printer, Paper size, Tax settings)
 * 3. المحاسبة (Accounts, Financial settings, Fiscal settings)
 * 4. المخزون (Reorder rules, Serial settings, Inventory behavior)
 * 5. المحافظ وSMS (Providers, Fee rules, SMS settings)
 * 6. النسخ والمزامنة (Backup, Restore, Cloud sync, Sync status)
 * 7. المظهر (Theme, Density, Accent)
 * 8. Diagnostics (Database integrity, Logs, Health checker, Developer tools)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorganizedSettingsScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToDestination: (MekkaDestination) -> Unit,
    onLockSession: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val activeUserId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val walletProviders by viewModel.walletProviders.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val truthfulSyncStatus by viewModel.truthfulSyncStatus.collectAsStateWithLifecycle()
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val errorLogs by viewModel.diagnosticErrorLogs.collectAsStateWithLifecycle()

    var selectedCategory by rememberSaveable { mutableIntStateOf(0) }

    val settingsTabs = listOf(
        "الحساب والأمان" to Icons.Default.Security,
        "بيانات المتجر" to Icons.Default.Storefront,
        "الفواتير" to Icons.AutoMirrored.Filled.ReceiptLong,
        "المحاسبة" to Icons.Default.AccountBalance,
        "المخزون" to Icons.Default.Inventory2,
        "المحافظ وSMS" to Icons.Default.AccountBalanceWallet,
        "النسخ والمزامنة" to Icons.Default.CloudSync,
        "المظهر" to Icons.Default.Palette,
        "التشخيص" to Icons.Default.Build
    )

    // Editable state mirrors
    var shopNameAr by rememberSaveable(shopConfig.shopNameArabic) { mutableStateOf(shopConfig.shopNameArabic) }
    var shopNameEn by rememberSaveable(shopConfig.shopNameEnglish) { mutableStateOf(shopConfig.shopNameEnglish) }
    var shopPhone by rememberSaveable(shopConfig.shopPhone) { mutableStateOf(shopConfig.shopPhone) }
    var shopAddress by rememberSaveable(shopConfig.shopAddress) { mutableStateOf(shopConfig.shopAddress) }
    var taxRegNo by rememberSaveable(shopConfig.taxRegistrationNumber) { mutableStateOf(shopConfig.taxRegistrationNumber) }
    var currencyCode by rememberSaveable(shopConfig.defaultCurrency) { mutableStateOf(shopConfig.defaultCurrency) }

    var receiptHeaderInput by rememberSaveable(shopConfig.receiptHeaderNote) { mutableStateOf(shopConfig.receiptHeaderNote) }
    var receiptFooterInput by rememberSaveable(shopConfig.receiptFooterNote) { mutableStateOf(shopConfig.receiptFooterNote) }
    var taxPercentInput by rememberSaveable(shopConfig.defaultTaxPercent) { mutableStateOf(shopConfig.defaultTaxPercent) }
    var warrantyDaysInput by rememberSaveable(shopConfig.defaultWarrantyDays) { mutableStateOf(shopConfig.defaultWarrantyDays) }

    var creditLimitInput by rememberSaveable(shopConfig.defaultCustomerCreditLimitMajor) { mutableStateOf(shopConfig.defaultCustomerCreditLimitMajor) }
    var fiscalMonthInput by rememberSaveable(shopConfig.fiscalYearStartMonth) { mutableStateOf(shopConfig.fiscalYearStartMonth) }

    var lowStockThresholdInput by rememberSaveable(shopConfig.defaultLowStockThreshold) { mutableStateOf(shopConfig.defaultLowStockThreshold.toString()) }

    var walletDailyLimitInput by rememberSaveable(shopConfig.defaultWalletDailyLimitMajor) { mutableStateOf(shopConfig.defaultWalletDailyLimitMajor) }
    var walletMonthlyLimitInput by rememberSaveable(shopConfig.defaultWalletMonthlyLimitMajor) { mutableStateOf(shopConfig.defaultWalletMonthlyLimitMajor) }

    var cloudEndpointInput by rememberSaveable(shopConfig.cloudSyncEndpointUrl) { mutableStateOf(shopConfig.cloudSyncEndpointUrl) }
    var showUnsavedSettingsDialog by remember { mutableStateOf(false) }

    val hasUnsavedShopIdentity = shopNameAr != shopConfig.shopNameArabic ||
        shopNameEn != shopConfig.shopNameEnglish ||
        shopPhone != shopConfig.shopPhone ||
        shopAddress != shopConfig.shopAddress ||
        taxRegNo != shopConfig.taxRegistrationNumber

    val shopPhoneValidation = remember(shopPhone) {
        if (shopPhone.isBlank()) null else LocalizedFormValidator.validateEgyptianPhone(shopPhone, required = false)
    }

    BackHandler {
        if (hasUnsavedShopIdentity) {
            showUnsavedSettingsDialog = true
        } else {
            onBack()
        }
    }

    if (showUnsavedSettingsDialog) {
        UnsavedChangesDialog(
            changesSummaryArabic = "لديك تعديلات غير محفوظة في بيانات وهوية المتجر.",
            canSaveDirectly = shopNameAr.isNotBlank() && (shopPhoneValidation?.isValid != false),
            onContinueEditing = { showUnsavedSettingsDialog = false },
            onSaveChanges = {
                showUnsavedSettingsDialog = false
                viewModel.updateShopIdentity(
                    nameArabic = shopNameAr,
                    nameEnglish = shopNameEn,
                    phone = shopPhone,
                    address = shopAddress,
                    taxRegistrationNumber = taxRegNo,
                    defaultCurrency = currencyCode
                )
                onBack()
            },
            onDiscardChanges = {
                showUnsavedSettingsDialog = false
                onBack()
            }
        )
    }

    val activeUser = users.firstOrNull { it.localId == activeUserId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reorganized_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (hasUnsavedShopIdentity) {
                                showUnsavedSettingsDialog = true
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Column {
                        Text(
                            text = "إعدادات النظام المتقدمة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مقسمة حسب النطاق التشغيلي والمحاسبي والأمني",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = selectedCategory,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                settingsTabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = title,
                                    fontWeight = if (selectedCategory == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.testTag("settings_tab_$index")
                    )
                }
            }
        }

        when (selectedCategory) {
            0 -> {
                // 1. الحساب والأمان (Current user, PIN/security, Sessions, Roles)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "الحساب الحالي والجلسة النشطة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "المستخدم النشط: ${activeUser?.fullNameArabic ?: "مالك المتجر"} (${activeUser?.username ?: "owner"})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "الدور الوظيفي: ${FriendlyTerminologyMapper.toFriendlyRoleArabic(activeUser?.roleCode ?: "OWNER")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "تشفير رمز الدخول: PBKDF2-HMAC-SHA256 مع حماية القفل المؤقت بعد محاولات خاطئة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToDestination(MekkaDestination.USERS_PERMISSIONS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_users_permissions_button")
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                    Text(" الموظفون والأدوار وPIN")
                                }
                                OutlinedButton(
                                    onClick = onLockSession,
                                    modifier = Modifier.testTag("settings_lock_session_button")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Text(" قفل الجلسة")
                                }
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "الأدوار المعتمدة في النظام (RBAC)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("• مالك المتجر (OWNER): كافة الصلاحيات المالية والإدارية والنسخ الاحتياطي والقيود العكسية.")
                            Text("• كاشير ومبيعات (CASHIER): فواتير البيع، التحصيل، وعمليات الخزينة والمحافظ.")
                            Text("• مهندس صيانة (TECHNICIAN): استلام الأجهزة، تحديث حالة التذاكر، وصرف قطع الغيار.")
                            Text("• مسؤول مخازن ومشتريات (INVENTORY): فواتير الشراء، جرد المخزون، وإدارة أصناف وسيريالات IMEI.")
                        }
                    }
                }
            }

            1 -> {
                // 2. بيانات المتجر (Shop name, Phone, Address, Logo/Identity, Receipt identity)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "بيانات وهوية المتجر",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = shopNameAr,
                                onValueChange = { shopNameAr = it },
                                label = { Text("اسم المتجر (بالعربية)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_shop_name_ar_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = shopNameEn,
                                onValueChange = { shopNameEn = it },
                                label = { Text("الاسم التجاري بالإنجليزية / الشعار النصي") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_shop_name_en_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = shopPhone,
                                onValueChange = { shopPhone = it },
                                label = { Text("رقم هاتف المتجر وواتساب") },
                                isError = shopPhoneValidation?.isValid == false,
                                supportingText = {
                                    if (shopPhoneValidation?.isValid == false) {
                                        Text(shopPhoneValidation.errorMessageArabic ?: "أدخل رقم هاتف صحيح.", color = FinancialNegative)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_shop_phone_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = shopAddress,
                                onValueChange = { shopAddress = it },
                                label = { Text("عنوان الفرع") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_shop_address_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = taxRegNo,
                                onValueChange = { taxRegNo = it },
                                label = { Text("الرقم الضريبي / السجل التجاري (اختياري)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    viewModel.updateShopIdentity(
                                        nameArabic = shopNameAr,
                                        nameEnglish = shopNameEn,
                                        phone = shopPhone,
                                        address = shopAddress,
                                        taxRegistrationNumber = taxRegNo,
                                        defaultCurrency = currencyCode
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_shop_identity_button")
                            ) {
                                Text("حفظ بيانات المتجر")
                            }
                        }
                    }
                }
            }

            2 -> {
                // 3. الفواتير (Receipt settings, Printer, Paper size, Tax settings)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "إعدادات الفواتير والطباعة والضرائب",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("نوع الطابعة الحرارية / القياسية:", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "THERMAL_80MM" to "حرارية 80mm",
                                    "THERMAL_58MM" to "حرارية 58mm",
                                    "A4_STANDARD" to "طابعة A4"
                                ).forEach { (code, label) ->
                                    FilterChip(
                                        selected = shopConfig.printerType == code,
                                        onClick = {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_PRINTER_TYPE,
                                                code,
                                                "نوع الطابعة"
                                            )
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Text("مقاس ورق الطباعة:", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("80mm", "58mm", "A4").forEach { size ->
                                    FilterChip(
                                        selected = shopConfig.paperSize == size,
                                        onClick = {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_PAPER_SIZE,
                                                size,
                                                "مقاس الورق"
                                            )
                                        },
                                        label = { Text(size) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = taxPercentInput,
                                    onValueChange = { taxPercentInput = it },
                                    label = { Text("نسبة الضريبة (%)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = warrantyDaysInput,
                                    onValueChange = { warrantyDaysInput = it },
                                    label = { Text("فترة الضمان (أيام)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = receiptHeaderInput,
                                onValueChange = { receiptHeaderInput = it },
                                label = { Text("ترويسة الفاتورة (Receipt Header)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = receiptFooterInput,
                                onValueChange = { receiptFooterInput = it },
                                label = { Text("تذييل الفاتورة وشروط الاستبدال (Receipt Footer)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    viewModel.updateAppSetting(MekkaPhoneRepository.KEY_DEFAULT_TAX_PERCENT, taxPercentInput, "نسبة الضريبة")
                                    viewModel.updateAppSetting(MekkaPhoneRepository.KEY_DEFAULT_WARRANTY_DAYS, warrantyDaysInput, "فترة الضمان")
                                    viewModel.updateAppSetting(MekkaPhoneRepository.KEY_RECEIPT_HEADER_NOTE, receiptHeaderInput, "ترويسة الفاتورة")
                                    viewModel.updateAppSetting(MekkaPhoneRepository.KEY_RECEIPT_FOOTER_NOTE, receiptFooterInput, "تذييل الفاتورة")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_invoice_settings_button")
                            ) {
                                Text("حفظ إعدادات الفواتير والطباعة")
                            }
                        }
                    }
                }
            }

            3 -> {
                // 4. المحاسبة (Accounts, Financial settings, Fiscal settings)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "الإعدادات المحاسبية والمالية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("العملة الافتراضية:", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("EGP" to "ج.م (EGP)", "SAR" to "ر.س (SAR)", "USD" to "دولار (USD)").forEach { (code, label) ->
                                    FilterChip(
                                        selected = currencyCode == code,
                                        onClick = {
                                            currencyCode = code
                                            viewModel.updateShopIdentity(
                                                shopNameAr,
                                                shopNameEn,
                                                shopPhone,
                                                shopAddress,
                                                taxRegNo,
                                                code
                                            )
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            SettingSwitchRow(
                                title = "السماح بالبيع الآجل / الجزئي للعملاء",
                                checked = shopConfig.allowCreditSales,
                                onCheckedChange = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_ALLOW_CREDIT_SALES,
                                        it.toString(),
                                        "البيع الآجل"
                                    )
                                }
                            )
                            SettingSwitchRow(
                                title = "إلزام ربط العميل عند البيع الآجل أو الجزئي",
                                checked = shopConfig.requireCustomerForCredit,
                                onCheckedChange = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_REQUIRE_CUSTOMER_FOR_CREDIT,
                                        it.toString(),
                                        "ربط العميل للائتمان"
                                    )
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = creditLimitInput,
                                    onValueChange = { creditLimitInput = it },
                                    label = { Text("السقف الائتماني الافتراضي للعميل") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = fiscalMonthInput,
                                    onValueChange = { fiscalMonthInput = it },
                                    label = { Text("شهر بداية السنة المالية (1-12)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_DEFAULT_CUSTOMER_CREDIT_LIMIT,
                                        creditLimitInput,
                                        "السقف الائتماني"
                                    )
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_FISCAL_YEAR_START_MONTH,
                                        fiscalMonthInput,
                                        "بداية السنة المالية"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ السياسات المحاسبية")
                            }
                        }
                    }
                }
            }

            4 -> {
                // 5. المخزون (Reorder rules, Serial settings, Inventory behavior)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "قواعد المخزون، حد إعادة الطلب، والسيريال (IMEI)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = lowStockThresholdInput,
                                onValueChange = { lowStockThresholdInput = it },
                                label = { Text("حد التنبيه الافتراضي لنقص المخزون (Reorder Point)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            SettingSwitchRow(
                                title = "إلزام إدخال واختيار سيريال IMEI للأجهزة المتسلسلة عند الشراء والبيع",
                                checked = shopConfig.requireSerialOnDevices,
                                onCheckedChange = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_REQUIRE_SERIAL_ON_DEVICES,
                                        it.toString(),
                                        "إلزام سيريال IMEI"
                                    )
                                }
                            )
                            Surface(
                                color = FinancialPositive.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "سياسة تقييم المخزون المفعلة: المتوسط المرجح للتكلفة (WAC)",
                                        fontWeight = FontWeight.Bold,
                                        color = FinancialPositive
                                    )
                                    Text(
                                        text = "منع الرصيد السالب مفعل دائماً، ولا يتغير رصيد أي صنف إلا عبر حركة مخزنية موثقة.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_LOW_STOCK_THRESHOLD,
                                        lowStockThresholdInput,
                                        "حد نقص المخزون"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ إعدادات المخزون")
                            }
                        }
                    }
                }
            }

            5 -> {
                // 6. المحافظ وSMS (Providers, Fee rules, SMS settings)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "إعدادات المحافظ الإلكترونية وذكاء رسائل SMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = walletDailyLimitInput,
                                    onValueChange = { walletDailyLimitInput = it },
                                    label = { Text("السقف اليومي للمحفظة") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = walletMonthlyLimitInput,
                                    onValueChange = { walletMonthlyLimitInput = it },
                                    label = { Text("السقف الشهري للمحفظة") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            SettingSwitchRow(
                                title = "تفعيل الالتقاط التلقائي لرسائل المحافظ كأدلة مراجعة (SMS Evidence)",
                                checked = shopConfig.autoCaptureSmsEnabled,
                                onCheckedChange = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_AUTO_CAPTURE_SMS,
                                        it.toString(),
                                        "الالتقاط التلقائي لـ SMS"
                                    )
                                }
                            )
                            Text(
                                text = "المزودون المعتمدون (${walletProviders.size}): " +
                                    walletProviders.joinToString(" • ") { it.nameArabic },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_WALLET_DAILY_LIMIT,
                                        walletDailyLimitInput,
                                        "السقف اليومي للمحفظة"
                                    )
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_WALLET_MONTHLY_LIMIT,
                                        walletMonthlyLimitInput,
                                        "السقف الشهري للمحفظة"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ إعدادات المحافظ وSMS")
                            }
                        }
                    }
                }
            }

            6 -> {
                // 7. النسخ والمزامنة (Backup, Restore, Cloud sync, Sync status)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المزامنة السحابية وحالة الاتصال",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusPillBadge(
                                    text = truthfulSyncStatus.arabicLabel,
                                    color = if (shopConfig.cloudSyncEndpointUrl.isNotBlank()) FinancialPositive else FinancialInfo
                                )
                            }
                            Text(
                                text = if (shopConfig.cloudSyncEndpointUrl.isBlank()) {
                                    "الحالة الحالية: محفوظ محليًا (Local-First) — لم يتم ربط خادم سحابي خارجي بعد. جميع العمليات تحفظ بأمان في قاعدة البيانات وطابور المزامنة."
                                } else {
                                    "الخادم السحابي المرتبط: ${shopConfig.cloudSyncEndpointUrl}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = cloudEndpointInput,
                                onValueChange = { cloudEndpointInput = it },
                                label = { Text("رابط خادم المزامنة السحابي (HTTPS Endpoint اختياري)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            SettingSwitchRow(
                                title = "المزامنة التلقائية بالخلفية عند توفر الاتصال",
                                checked = shopConfig.autoSyncOnFinancialPost,
                                onCheckedChange = { enabled ->
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_AUTO_SYNC_ON_POST,
                                        enabled.toString(),
                                        "المزامنة التلقائية"
                                    )
                                    MekkaBackgroundSyncScheduler.scheduleOrUpdateBackgroundSync(
                                        context = context,
                                        policy = BackgroundSyncPolicy(
                                            autoSyncEnabled = enabled,
                                            requireUnmeteredWifi = shopConfig.cloudSyncWifiOnly,
                                            remoteEndpointUrl = cloudEndpointInput
                                        )
                                    )
                                }
                            )
                            SettingSwitchRow(
                                title = "حصر المزامنة بالخلفية على شبكات Wi-Fi غير المحدودة فقط",
                                checked = shopConfig.cloudSyncWifiOnly,
                                onCheckedChange = { wifiOnly ->
                                    viewModel.updateAppSetting(
                                        MekkaPhoneRepository.KEY_CLOUD_SYNC_WIFI_ONLY,
                                        wifiOnly.toString(),
                                        "مزامنة Wi-Fi فقط"
                                    )
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.updateAppSetting(
                                            MekkaPhoneRepository.KEY_CLOUD_SYNC_ENDPOINT_URL,
                                            cloudEndpointInput.trim(),
                                            "رابط الخادم السحابي"
                                        )
                                        MekkaBackgroundSyncScheduler.scheduleOrUpdateBackgroundSync(
                                            context = context,
                                            policy = BackgroundSyncPolicy(
                                                autoSyncEnabled = shopConfig.autoSyncOnFinancialPost,
                                                requireUnmeteredWifi = shopConfig.cloudSyncWifiOnly,
                                                remoteEndpointUrl = cloudEndpointInput.trim()
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("حفظ إعدادات المزامنة")
                                }
                                OutlinedButton(
                                    onClick = { onNavigateToDestination(MekkaDestination.BACKUP) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                    Text(" مركز النسخ الاحتياطي")
                                }
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ملخص النسخ الاحتياطي المشفر",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "عدد النسخ المحفوظة: ${backups.size} نسخة • التشفير: AES-256-GCM + ضغط GZIP + بصمة SHA-256",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            7 -> {
                // 8. المظهر (Theme, Density, Accent)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "المظهر، الألوان، وكثافة الواجهة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("وضع الإضاءة (Theme):", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "LIGHT" to "فاتح (Light)",
                                    "DARK" to "داكن (Dark)",
                                    "SYSTEM" to "تلقائي"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = shopConfig.themeMode == mode,
                                        onClick = {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_THEME_MODE,
                                                mode,
                                                "وضع الإضاءة"
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("theme_chip_${mode.lowercase()}")
                                    )
                                }
                            }
                            Text("السمة اللونية (Accent Palette):", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "EMERALD" to "زمردي مكة",
                                    "ROYAL_BLUE" to "أزرق ملكي",
                                    "GOLD" to "ذهبي كلاسيكي",
                                    "SLATE" to "رمادي احترافي"
                                ).forEach { (palette, label) ->
                                    FilterChip(
                                        selected = shopConfig.accentPalette == palette,
                                        onClick = {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_ACCENT_PALETTE,
                                                palette,
                                                "السمة اللونية"
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("palette_chip_${palette.lowercase()}")
                                    )
                                }
                            }
                            Text("كثافة العناصر (Density):", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "COMPACT" to "مدمج",
                                    "COMFORTABLE" to "مريح",
                                    "LARGE" to "كبير وواضح"
                                ).forEach { (density, label) ->
                                    FilterChip(
                                        selected = shopConfig.uiDensity == density,
                                        onClick = {
                                            viewModel.updateAppSetting(
                                                MekkaPhoneRepository.KEY_UI_DENSITY,
                                                density,
                                                "كثافة الواجهة"
                                            )
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            8 -> {
                // 9. Diagnostics (Database integrity, Logs, Health checker, Developer tools)
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "التشخيص وفحص سلامة قاعدة البيانات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حالة الفحص الأخيرة: ${integrityReport?.overallStatus?.arabicLabel ?: "سليمة ومتطابقة"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FinancialPositive
                            )
                            Text(
                                text = "سجل الأخطاء المترجمة للتشخيص: ${errorLogs.size} حدث",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.runDataIntegrityCheck() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("فحص سلامة القاعدة الآن")
                                }
                                OutlinedButton(
                                    onClick = { onNavigateToDestination(MekkaDestination.DIAGNOSTICS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_diagnostics_center_button")
                                ) {
                                    Text("فتح مركز التشخيص الكامل")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Dedicated Backup & Restore UX (`النسخ الاحتياطي`, Part 3 Rules 10 & 11).
 *
 * Shows:
 * - Last backup (`آخر نسخة احتياطية`)
 * - Backup status (`حالة النسخة`)
 * - Backup size (`حجم النسخة`)
 * - Backup date (`تاريخ النسخة`)
 * - Integrity status (`حالة السلامة والتشفير`)
 *
 * Actions:
 * - إنشاء نسخة احتياطية
 * - استعادة نسخة (Supports REPLACE & MERGE)
 * - فحص النسخة
 *
 * Warns user before restore and requires explicit confirmation dialog for destructive REPLACE restore.
 */
@Composable
fun BackupAndRestoreCenterScreen(
    viewModel: MekkaPhoneViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val lastValidation by viewModel.lastBackupValidation.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()

    var selectedRestoreMode by rememberSaveable { mutableStateOf(RestoreMode.REPLACE) }
    var pendingDestructiveRestoreBackup by remember { mutableStateOf<BackupMetadataEntity?>(null) }
    var pendingDestructiveRestoreRawJson by remember { mutableStateOf<String?>(null) }
    var confirmDestructiveChecked by remember { mutableStateOf(false) }
    var pendingExportPayload by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.forLanguageTag("ar-EG")) }
    val latestBackup = backups.firstOrNull()

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
                viewModel.showFeedbackMessage("تم تصدير النسخة الاحتياطية المشفرة إلى ملف خارجي بنجاح", false)
            }.onFailure {
                viewModel.showFeedbackMessage("تعذر تصدير ملف النسخة الاحتياطية", true)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val rawJson = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }.orEmpty()
                if (rawJson.isNotBlank()) {
                    viewModel.validateRawBackupJson(rawJson)
                    if (selectedRestoreMode == RestoreMode.REPLACE) {
                        confirmDestructiveChecked = false
                        pendingDestructiveRestoreRawJson = rawJson
                    } else {
                        viewModel.executeSafeRestoreFromRawJson(
                            rawJson = rawJson,
                            userConfirmed = true,
                            restoreMode = RestoreMode.MERGE
                        )
                    }
                }
            }.onFailure {
                viewModel.showFeedbackMessage("تعذر قراءة ملف النسخة الاحتياطية المختار", true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("backup_restore_center_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
                Column {
                    Text(
                        text = "النسخ الاحتياطي والاستعادة الآمنة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تشفير AES-256-GCM • ضغط GZIP • بصمة SHA-256 • نسخة أمان استباقية قبل الاستعادة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Summary Card: Last backup, Status, Size, Date, Integrity status (Rule 11)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حالة النسخ الاحتياطي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPillBadge(
                            text = if (latestBackup != null) "مؤمّن ومشفر" else "لا توجد نسخة بعد",
                            color = if (latestBackup != null) FinancialPositive else FinancialWarning
                        )
                    }

                    if (latestBackup == null) {
                        Text(
                            text = "لم يتم إنشاء نسخة احتياطية بعد. يوصى بإنشاء نسخة احتياطية مشفرة الآن لحماية السجلات المالية والمخزنية.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val sizeKb = (latestBackup.fileSizeBytes / 1024L).coerceAtLeast(1L)
                        Text("• آخر نسخة احتياطية: ${latestBackup.fileName}")
                        Text("• حالة النسخة: صالحة ومكتملة (${latestBackup.recordCount} سجل)")
                        Text("• حجم النسخة: $sizeKb كيلوبايت (${latestBackup.fileSizeBytes} بايت)")
                        Text("• تاريخ النسخة: ${dateFormat.format(Date(latestBackup.createdAt))}")
                        Text(
                            text = "• حالة السلامة (Integrity): بصمة SHA-256 مطابقة • مخطط قاعدة البيانات v${latestBackup.schemaVersion}",
                            color = FinancialPositive,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider()

                    // Primary Actions: إنشاء نسخة احتياطية | استعادة نسخة | فحص النسخة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MekkaProcessingButton(
                            idleLabelArabic = "إنشاء نسخة احتياطية",
                            processingLabelArabic = "جاري إنشاء النسخة الاحتياطية...",
                            isProcessing = isProcessing,
                            onClick = { viewModel.createVerifiedBackup() },
                            modifier = Modifier.weight(1f),
                            testTag = "create_verified_backup_button"
                        )
                        if (latestBackup != null) {
                            OutlinedButton(
                                onClick = { viewModel.validateExistingBackup(latestBackup) },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("validate_latest_backup_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Text(" فحص النسخة")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_external_backup_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text(" استعادة نسخة من ملف خارجي")
                    }
                }
            }
        }

        // Restore Mode Selector & Warning Banner (Rule 10 & 11)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = FinancialWarning.copy(alpha = 0.10f)
                ),
                border = BorderStroke(1.dp, FinancialWarning.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = FinancialWarning)
                        Text(
                            text = "تحذير ونمط الاستعادة (REPLACE / MERGE)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "قبل أي عملية استعادة، يقوم النظام تلقائياً بإنشاء نسخة احتياطية استباقية (Pre-Restore Safety Backup) والتحقق من التشفير وبصمة SHA-256 وتوافق إصدار قاعدة البيانات.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRestoreMode == RestoreMode.REPLACE,
                            onClick = { selectedRestoreMode = RestoreMode.REPLACE },
                            label = { Text("استبدال شامل (REPLACE)") },
                            modifier = Modifier.testTag("restore_mode_replace_chip")
                        )
                        FilterChip(
                            selected = selectedRestoreMode == RestoreMode.MERGE,
                            onClick = { selectedRestoreMode = RestoreMode.MERGE },
                            label = { Text("دمج آمن (MERGE)") },
                            modifier = Modifier.testTag("restore_mode_merge_chip")
                        )
                    }
                }
            }
        }

        // Last Validation Report Card
        lastValidation?.let { report ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (report.isValid) {
                            FinancialPositive.copy(alpha = 0.10f)
                        } else {
                            FinancialNegative.copy(alpha = 0.10f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (report.isValid) "نتيجة فحص النسخة: سليمة ومطابقة" else "نتيجة فحص النسخة: غير صالحة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isValid) FinancialPositive else FinancialNegative
                        )
                        Text(
                            text = report.validationMessageArabic,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Saved Backups List
        item {
            Text(
                text = "سجل النسخ الاحتياطية المحفوظة (${backups.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(backups, key = { it.localId }) { backup ->
            val sizeKb = (backup.fileSizeBytes / 1024L).coerceAtLeast(1L)
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = backup.fileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "التاريخ: ${dateFormat.format(Date(backup.createdAt))} • الحجم: $sizeKb KB • السجلات: ${backup.recordCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPillBadge(text = "SHA-256 ✓", color = FinancialPositive)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.validateExistingBackup(backup) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("validate_backup_btn_${backup.localId}")
                        ) {
                            Text("فحص النسخة")
                        }
                        OutlinedButton(
                            onClick = {
                                pendingExportPayload = backup.payloadJson
                                exportBackupLauncher.launch(backup.fileName)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(" تصدير")
                        }
                        Button(
                            onClick = {
                                confirmDestructiveChecked = false
                                pendingDestructiveRestoreBackup = backup
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedRestoreMode == RestoreMode.REPLACE) FinancialNegative else FinancialWarning
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_backup_btn_${backup.localId}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Text(if (selectedRestoreMode == RestoreMode.REPLACE) " استبدال البيانات" else " استعادة النسخة")
                        }
                    }

                    ExpandableTechnicalDetails(titleArabic = "التفاصيل الفنية وبصمة التشفير") {
                        Text("معرف النسخة: ${backup.localId}", style = MaterialTheme.typography.bodySmall)
                        Text("بصمة SHA-256: ${backup.checksumSha256}", style = MaterialTheme.typography.bodySmall)
                        Text("إصدار المخطط: Schema v${backup.schemaVersion} • App: ${backup.appVersion}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    // Explicit Level 3 Confirmation Dialog for Restore (REPLACE & MERGE - Section 14, 15, 17)
    if (pendingDestructiveRestoreBackup != null || pendingDestructiveRestoreRawJson != null) {
        val isReplace = selectedRestoreMode == RestoreMode.REPLACE
        AlertDialog(
            onDismissRequest = {
                pendingDestructiveRestoreBackup = null
                pendingDestructiveRestoreRawJson = null
            },
            title = {
                Text(
                    text = if (isReplace) "تأكيد استبدال البيانات الشامل (REPLACE)" else "تأكيد دمج واستعادة النسخة الاحتياطية (MERGE)",
                    fontWeight = FontWeight.Bold,
                    color = if (isReplace) FinancialNegative else FinancialWarning
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isReplace) {
                            "ماذا سيحدث: سيتم أخذ نسخة أمان استباقية تلقائية أولاً، ثم استبدال جميع جداول قاعدة البيانات الحالية ببيانات النسخة المختارة بعد التحقق من بصمة SHA-256."
                        } else {
                            "ماذا سيحدث: سيتم أخذ نسخة أمان استباقية تلقائية أولاً، ثم دمج السجلات المفقودة من النسخة الاحتياطية في قاعدة البيانات الحالية دون مسح السجلات الأحدث."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "هل يمكن التراجع؟ لا يمكن التراجع التلقائي بعد إتمام الاستعادة إلا عبر استرجاع نسخة الأمان الاستباقية التي ينشئها النظام قبل التنفيذ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { confirmDestructiveChecked = !confirmDestructiveChecked }
                    ) {
                        Checkbox(
                            checked = confirmDestructiveChecked,
                            onCheckedChange = { confirmDestructiveChecked = it },
                            modifier = Modifier.testTag("confirm_destructive_restore_checkbox")
                        )
                        Text(
                            text = if (isReplace) {
                                "أؤكد رغبتي في استبدال البيانات الحالية واستعادة هذه النسخة الاحتياطية"
                            } else {
                                "أؤكد رغبتي في دمج بيانات هذه النسخة الاحتياطية مع البيانات الحالية"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b = pendingDestructiveRestoreBackup
                        val raw = pendingDestructiveRestoreRawJson
                        val modeToRun = selectedRestoreMode
                        pendingDestructiveRestoreBackup = null
                        pendingDestructiveRestoreRawJson = null
                        if (b != null) {
                            viewModel.executeSafeRestore(
                                backup = b,
                                userConfirmed = true,
                                restoreMode = modeToRun
                            )
                        } else if (raw != null) {
                            viewModel.executeSafeRestoreFromRawJson(
                                rawJson = raw,
                                userConfirmed = true,
                                restoreMode = modeToRun
                            )
                        }
                    },
                    enabled = confirmDestructiveChecked && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReplace) FinancialNegative else FinancialWarning
                    ),
                    modifier = Modifier.testTag("confirm_destructive_restore_button")
                ) {
                    Text(if (isReplace) "استبدال البيانات واستعادة النسخة" else "تأكيد دمج واستعادة النسخة")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDestructiveRestoreBackup = null
                        pendingDestructiveRestoreRawJson = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

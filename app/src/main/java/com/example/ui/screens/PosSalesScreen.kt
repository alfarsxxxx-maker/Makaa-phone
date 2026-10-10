package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.SaleEntity
import com.example.database.entities.SerialLifecycleStatus
import com.example.diagnostics.ReceiptFormatter
import com.example.financial.Money
import com.example.ui.components.BarcodeCameraScannerDialog
import com.example.ui.components.LocalizedFormValidator
import com.example.ui.components.MekkaProcessingButton
import com.example.ui.components.MekkaProfessionalConfirmationDialog
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThermalReceiptPreviewDialog
import com.example.ui.components.formatMoneyMinor
import com.example.ui.components.formatTimestampArabic
import com.example.ui.components.launchWhatsAppMessage
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaBlueInfo
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.viewmodel.MekkaPhoneViewModel
import com.example.ui.viewmodel.PosCartItem
import com.example.ui.viewmodel.PosCheckoutState
import com.example.ui.viewmodel.PosCustomerSelectionMode
import com.example.ui.viewmodel.PosPaymentMode

/**
 * MEKKA PHONE V3 — Complete POS / Sales Redesign (Rules 13–24)
 * Strictly implements the workflow:
 * PRODUCT SEARCH → PRODUCT SELECTION → CART → CUSTOMER → PAYMENT → REVIEW → CONFIRM → SUCCESS → RECEIPT
 *
 * Safety & Financial Rules enforced:
 * - Never silently selects first customer or first product
 * - Requires explicit IMEI selection/scanning for serialized products
 * - Persistent cart in ViewModel
 * - Explicit Sale Review dialog before committing
 * - Double-submission lock & Processing indicator
 * - Clears cart ONLY after atomic sale success; preserves cart on error
 */
@Composable
fun PosSalesScreen(viewModel: MekkaPhoneViewModel) {
    val context = LocalContext.current

    val products by viewModel.products.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val serials by viewModel.serialNumbers.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerBalances by viewModel.customerBalances.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val refunds by viewModel.refunds.collectAsStateWithLifecycle()
    val shopConfig by viewModel.shopConfiguration.collectAsStateWithLifecycle()
    val pendingInvoiceRef by viewModel.pendingSelectedSaleInvoice.collectAsStateWithLifecycle()

    val cartItems by viewModel.posCartItems.collectAsStateWithLifecycle()
    val customerMode by viewModel.posCustomerMode.collectAsStateWithLifecycle()
    val selectedCustomerId by viewModel.posSelectedCustomerId.collectAsStateWithLifecycle()
    val paymentMode by viewModel.posPaymentMode.collectAsStateWithLifecycle()
    val partialPaidInput by viewModel.posPartialPaidMajorInput.collectAsStateWithLifecycle()
    val invoiceDiscountInput by viewModel.posInvoiceDiscountMajorInput.collectAsStateWithLifecycle()
    val notesInput by viewModel.posNotesInput.collectAsStateWithLifecycle()
    val inlineError by viewModel.posInlineValidationError.collectAsStateWithLifecycle()
    val checkoutState by viewModel.posCheckoutState.collectAsStateWithLifecycle()

    var activePosSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = نقطة البيع (POS), 1 = سجل الفواتير والمرتجعات

    // Search & Scanner state
    var productSearchQuery by rememberSaveable { mutableStateOf("") }
    var showBarcodeScanner by rememberSaveable { mutableStateOf(false) }

    // Serialized product IMEI picker modal state
    var pendingSerializedProduct by remember { mutableStateOf<ProductInventoryProjection?>(null) }
    var imeiFilterQuery by rememberSaveable { mutableStateOf("") }

    // Cart line edit dialog state (price & discount per line)
    var editingCartLine by remember { mutableStateOf<PosCartItem?>(null) }
    var editPriceInput by rememberSaveable { mutableStateOf("") }
    var editDiscountInput by rememberSaveable { mutableStateOf("") }

    // Customer selection & Quick Add Customer state
    var customerSearchQuery by rememberSaveable { mutableStateOf("") }
    var showCustomerPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showQuickAddCustomerDialog by rememberSaveable { mutableStateOf(false) }
    var newCustomerName by rememberSaveable { mutableStateOf("") }
    var newCustomerPhone by rememberSaveable { mutableStateOf("") }
    var newCustomerError by rememberSaveable { mutableStateOf<String?>(null) }

    // Receipt preview state
    var previewReceiptSale by remember { mutableStateOf<SaleEntity?>(null) }
    var previewReceiptLines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var previewCustomerPhone by rememberSaveable { mutableStateOf("") }

    // Sales return dialog state
    var returnTargetSale by remember { mutableStateOf<SaleEntity?>(null) }
    var returnReasonInput by rememberSaveable { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(pendingInvoiceRef, sales, allSaleItems, products, customers) {
        val ref = pendingInvoiceRef
        if (!ref.isNullOrBlank()) {
            val matchedSale = sales.firstOrNull {
                it.invoiceNumber.equals(ref, ignoreCase = true) || it.localId == ref
            }
            if (matchedSale != null) {
                activePosSubTab = 1
                val itemsForSale = allSaleItems.filter { it.saleId == matchedSale.localId }
                val lines = itemsForSale.map { si ->
                    val prodName = products.firstOrNull { it.productId == si.productId }?.nameArabic ?: si.productId
                    "$prodName × ${si.quantity}" to formatMoneyMinor(si.lineTotalMinor)
                }
                previewReceiptSale = matchedSale
                previewReceiptLines = lines.ifEmpty {
                    listOf("رقم الفاتورة" to matchedSale.invoiceNumber, "الصافي" to formatMoneyMinor(matchedSale.totalAmountMinor))
                }
                previewCustomerPhone = customers.firstOrNull { it.localId == matchedSale.customerId }?.phone.orEmpty()
                viewModel.consumeSelectedSaleInvoice()
            }
        }
    }

    val brandMap = remember(brands) { brands.associate { it.localId to it.name } }
    val selectedCustomer = remember(customers, selectedCustomerId) {
        customers.firstOrNull { it.localId == selectedCustomerId }
    }
    val selectedCustomerBalanceMinor = remember(customerBalances, selectedCustomerId) {
        customerBalances.firstOrNull { it.customerId == selectedCustomerId }?.outstandingBalanceMinor ?: 0L
    }

    val (grossSubtotalMinor, totalDiscountMinor, finalTotalMinor) = remember(
        cartItems,
        invoiceDiscountInput,
        shopConfig.defaultTaxPercent
    ) {
        viewModel.computePosTotals()
    }
    val (paidAmountMinor, remainingAmountMinor) = remember(
        finalTotalMinor,
        paymentMode,
        partialPaidInput
    ) {
        viewModel.computePosPaidAndRemainingMinor(finalTotalMinor)
    }

    // 1. Camera Barcode / IMEI Scanner Dialog
    if (showBarcodeScanner) {
        BarcodeCameraScannerDialog(
            titleArabic = "مسح باركود المنتج أو سيريال IMEI",
            onBarcodeDetected = { code ->
                showBarcodeScanner = false
                val trimmed = code.trim()
                // Check if scanned code matches an IN_STOCK IMEI directly
                val matchedSerial = serials.firstOrNull {
                    it.serialOrImei.equals(trimmed, ignoreCase = true) && it.status == SerialLifecycleStatus.IN_STOCK
                }
                if (matchedSerial != null) {
                    val prod = products.firstOrNull { it.productId == matchedSerial.productId }
                    if (prod != null) {
                        viewModel.addOrIncrementPosCartItem(
                            product = prod,
                            brandName = prod.brandId?.let { brandMap[it] },
                            quantity = 1,
                            unitPriceMinor = prod.defaultSellingPriceMinor,
                            serialEntity = matchedSerial
                        )
                        return@BarcodeCameraScannerDialog
                    }
                }
                // Check if scanned code matches a non-serialized product SKU or barcode
                val matchedProd = products.firstOrNull {
                    it.sku.equals(trimmed, ignoreCase = true) ||
                        (it.barcode?.equals(trimmed, ignoreCase = true) == true)
                }
                if (matchedProd != null) {
                    if (matchedProd.isSerialized) {
                        pendingSerializedProduct = matchedProd
                        imeiFilterQuery = ""
                    } else {
                        viewModel.addOrIncrementPosCartItem(
                            product = matchedProd,
                            brandName = matchedProd.brandId?.let { brandMap[it] }
                        )
                    }
                } else {
                    productSearchQuery = trimmed
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    // 2. Serialized Product IMEI Selection Dialog (Rule 15: Require IMEI selection/scanning)
    pendingSerializedProduct?.let { serProd ->
        val availableSerials = serials.filter {
            it.productId == serProd.productId &&
                it.status == SerialLifecycleStatus.IN_STOCK &&
                cartItems.none { c -> c.serialNumberId == it.localId } &&
                (imeiFilterQuery.isBlank() || it.serialOrImei.contains(imeiFilterQuery.trim(), ignoreCase = true))
        }
        AlertDialog(
            onDismissRequest = { pendingSerializedProduct = null },
            title = {
                Text(
                    text = "اختر سيريال / IMEI للجهاز",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${serProd.nameArabic} • السعر: ${formatMoneyMinor(serProd.defaultSellingPriceMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = imeiFilterQuery,
                        onValueChange = { imeiFilterQuery = it },
                        label = { Text("ابحث عن رقم IMEI / سيريال") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_imei_search_input")
                    )
                    if (availableSerials.isEmpty()) {
                        Surface(
                            color = MekkaCrimsonError.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد أرقام IMEI متاحة بالمخزن لهذا الجهاز حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MekkaCrimsonError,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            availableSerials.take(8).forEach { sr ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.addOrIncrementPosCartItem(
                                                product = serProd,
                                                brandName = serProd.brandId?.let { brandMap[it] },
                                                quantity = 1,
                                                unitPriceMinor = serProd.defaultSellingPriceMinor,
                                                serialEntity = sr
                                            )
                                            pendingSerializedProduct = null
                                        }
                                        .testTag("pos_select_imei_${sr.serialOrImei}"),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "IMEI: ${sr.serialOrImei}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = LedgerMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        MekkaStatusBadge(text = "إضافة للسلة +", color = MekkaEmeraldGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingSerializedProduct = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // 3. Edit Cart Item Price & Line Discount Dialog
    editingCartLine?.let { line ->
        AlertDialog(
            onDismissRequest = { editingCartLine = null },
            title = {
                Text(
                    text = "تعديل السعر أو الخصم (${line.productNameArabic})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it },
                        label = { Text("سعر بيع الوحدة (ج.م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_edit_line_price_input")
                    )
                    OutlinedTextField(
                        value = editDiscountInput,
                        onValueChange = { editDiscountInput = it },
                        label = { Text("خصم على هذا البند (ج.م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_edit_line_discount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newPriceMinor = runCatching { Money.parseMajorString(editPriceInput).amountMinor }.getOrNull() ?: 0L
                        val newDiscMinor = if (editDiscountInput.isBlank()) {
                            0L
                        } else {
                            runCatching { Money.parseMajorString(editDiscountInput).amountMinor }.getOrNull() ?: -1L
                        }
                        viewModel.updatePosCartItemPriceAndDiscount(
                            lineId = line.lineId,
                            unitPriceMinor = newPriceMinor,
                            lineDiscountMinor = newDiscMinor
                        )
                        editingCartLine = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCartLine = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 4. Customer Selection Dialog (Rule 17: Search + Existing Customer + Walk-in + Add Customer)
    if (showCustomerPickerSheet) {
        val filteredCustomers = customers.filter {
            customerSearchQuery.isBlank() ||
                it.fullName.contains(customerSearchQuery.trim(), ignoreCase = true) ||
                it.phone.contains(customerSearchQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showCustomerPickerSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختيار العميل",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            showCustomerPickerSheet = false
                            newCustomerName = ""
                            newCustomerPhone = ""
                            newCustomerError = null
                            showQuickAddCustomerDialog = true
                        },
                        modifier = Modifier.testTag("pos_open_quick_add_customer_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("عميل جديد")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Explicit Walk-in Customer Option
                    OutlinedButton(
                        onClick = {
                            viewModel.selectPosWalkInCustomer()
                            showCustomerPickerSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_select_walk_in_btn")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عميل نقدي مباشر (Walk-in)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = customerSearchQuery,
                        onValueChange = { customerSearchQuery = it },
                        label = { Text("ابحث باسم العميل أو رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_customer_search_input")
                    )

                    if (filteredCustomers.isEmpty()) {
                        Text(
                            text = "لا يوجد عملاء مطابقون للبحث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            filteredCustomers.take(10).forEach { cust ->
                                val custBal = customerBalances.firstOrNull { it.customerId == cust.localId }?.outstandingBalanceMinor ?: 0L
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.selectPosRegisteredCustomer(cust.localId)
                                            showCustomerPickerSheet = false
                                        }
                                        .testTag("pos_customer_option_${cust.localId}"),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cust.fullName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cust.phone,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = LedgerMonoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (custBal > 0L) {
                                            MekkaStatusBadge(
                                                text = "مديونية: ${formatMoneyMinor(custBal)}",
                                                color = MekkaAmberWarning
                                            )
                                        } else {
                                            MekkaStatusBadge(
                                                text = "اختيار",
                                                color = MekkaEmeraldGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPickerSheet = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // 5. Quick Add Customer Dialog from POS (Rule 17: Add customer)
    if (showQuickAddCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddCustomerDialog = false },
            title = {
                Text(
                    text = "إضافة عميل جديد سريعاً",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (newCustomerError != null) {
                        Text(
                            text = newCustomerError.orEmpty(),
                            color = MekkaCrimsonError,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = {
                            newCustomerName = it
                            newCustomerError = null
                        },
                        label = { Text("اسم العميل الثلاثي") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_new_customer_name_input")
                    )
                    OutlinedTextField(
                        value = newCustomerPhone,
                        onValueChange = {
                            newCustomerPhone = it
                            newCustomerError = LocalizedFormValidator.validateEgyptianPhone(it, required = false).errorMessageArabic
                        },
                        label = { Text("رقم الهاتف") },
                        isError = newCustomerError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_new_customer_phone_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomerName.isBlank()) {
                            newCustomerError = "يرجى إدخال اسم العميل"
                            return@Button
                        }
                        val phoneErr = LocalizedFormValidator.validateEgyptianPhone(newCustomerPhone, required = true).errorMessageArabic
                        if (phoneErr != null) {
                            newCustomerError = phoneErr
                            return@Button
                        }
                        viewModel.addCustomer(
                            fullName = newCustomerName.trim(),
                            phone = newCustomerPhone.trim(),
                            nationalId = "",
                            address = "",
                            notes = "أضيف من نقطة البيع POS"
                        )
                        showQuickAddCustomerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("pos_save_new_customer_btn")
                ) {
                    Text("حفظ العميل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddCustomerDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 6. Sale Review Dialog (Rule 19 & Rule 20: مراجعة البيع → تعديل / تأكيد البيع)
    if (checkoutState is PosCheckoutState.Reviewing || checkoutState is PosCheckoutState.Processing) {
        val isProcessing = checkoutState is PosCheckoutState.Processing
        val reviewCustomerName = when (customerMode) {
            PosCustomerSelectionMode.WALK_IN -> "عميل نقدي مباشر"
            PosCustomerSelectionMode.REGISTERED -> selectedCustomer?.fullName ?: "عميل مسجل"
            PosCustomerSelectionMode.NONE -> "غير محدد"
        }
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) viewModel.dismissPosSaleReview()
            },
            modifier = Modifier.testTag("pos_sale_review_dialog"),
            title = {
                Text(
                    text = "مراجعة البيع قبل التأكيد النهائي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("العميل:", style = MaterialTheme.typography.bodySmall)
                                Text(reviewCustomerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("طريقة الدفع:", style = MaterialTheme.typography.bodySmall)
                                MekkaStatusBadge(text = paymentMode.labelArabic, color = MekkaEmeraldGreen)
                            }
                        }
                    }

                    Text(
                        text = "الأصناف (${cartItems.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    cartItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${item.productNameArabic} × ${item.quantity}${if (item.serialOrImei != null) " [IMEI: ${item.serialOrImei}]" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatMoneyMinor(item.netLineTotalMinor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (totalDiscountMinor > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي الخصم:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatMoneyMinor(totalDiscountMinor),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily),
                                color = MekkaAmberWarning
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإجمالي النهائي:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatMoneyMinor(finalTotalMinor),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = LedgerMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MekkaEmeraldGreen
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المدفوع:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatMoneyMinor(paidAmountMinor),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily),
                            color = FinancialPositive
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المتبقي (آجل):", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatMoneyMinor(remainingAmountMinor),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = LedgerMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (remainingAmountMinor > 0L) MekkaCrimsonError else FinancialPositive
                        )
                    }

                    if (isProcessing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "جاري تنفيذ البيع الذري وترحيل القيد...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MekkaProcessingButton(
                    idleTextArabic = "تأكيد البيع",
                    processingTextArabic = "جارٍ تسجيل البيع...",
                    isProcessing = isProcessing,
                    onClick = { viewModel.confirmAndExecutePosSale() },
                    containerColor = MekkaEmeraldGreen,
                    modifier = Modifier.testTag("pos_confirm_sale_btn")
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissPosSaleReview() },
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("pos_edit_sale_btn")
                ) {
                    Text("تعديل")
                }
            }
        )
    }

    // 7. Sale Success Dialog / Sheet (Rule 21 & Rule 22: تم البيع بنجاح + عرض الفاتورة / مشاركة / WhatsApp / طباعة / بيع جديد)
    if (checkoutState is PosCheckoutState.Success) {
        val successState = checkoutState as PosCheckoutState.Success
        val sale = successState.sale
        val saleRemainingMinor = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
        val receiptLines = remember(successState) {
            successState.itemsSnapshot.map { item ->
                val label = "${item.productNameArabic} × ${item.quantity}${if (item.serialOrImei != null) " (${item.serialOrImei})" else ""}"
                label to formatMoneyMinor(item.netLineTotalMinor)
            } + listOf(
                "طريقة الدفع" to successState.paymentModeLabel
            )
        }
        val plainReceiptText = remember(successState, shopConfig) {
            buildString {
                appendLine("🧾 *فاتورة مبيعات — ${shopConfig.shopNameArabic}*")
                appendLine("رقم الفاتورة: ${sale.invoiceNumber}")
                appendLine("العميل: ${successState.customerNameArabic}")
                appendLine("------------------------")
                receiptLines.forEach { (k, v) -> appendLine("• $k: $v") }
                appendLine("------------------------")
                appendLine("الإجمالي: ${formatMoneyMinor(sale.totalAmountMinor)}")
                appendLine("المدفوع: ${formatMoneyMinor(sale.paidAmountMinor)}")
                appendLine("المتبقي: ${formatMoneyMinor(saleRemainingMinor)}")
                if (shopConfig.receiptFooterNote.isNotBlank()) {
                    appendLine(shopConfig.receiptFooterNote)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { /* Require explicit action */ },
            modifier = Modifier.testTag("pos_sale_success_dialog"),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MekkaEmeraldGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "تم البيع بنجاح",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MekkaEmeraldGreen
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MekkaEmeraldGreen.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("رقم الفاتورة:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = sale.invoiceNumber,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = LedgerMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الإجمالي:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = formatMoneyMinor(sale.totalAmountMinor),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = LedgerMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MekkaEmeraldGreen
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المدفوع:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = formatMoneyMinor(sale.paidAmountMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المتبقي:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = formatMoneyMinor(saleRemainingMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = LedgerMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (saleRemainingMinor > 0L) MekkaCrimsonError else FinancialPositive
                                )
                            }
                        }
                    }

                    // Actions: عرض الفاتورة • مشاركة • WhatsApp • طباعة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                previewReceiptSale = sale
                                previewReceiptLines = receiptLines
                                previewCustomerPhone = successState.customerPhone
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_success_preview_receipt_btn")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عرض الفاتورة")
                        }
                        OutlinedButton(
                            onClick = {
                                ReceiptFormatter.printInvoiceOrReceipt(
                                    context = context,
                                    jobName = "MekkaPhone_${sale.invoiceNumber}",
                                    shopNameArabic = shopConfig.shopNameArabic,
                                    documentTitleArabic = "فاتورة مبيعات ضريبية مبسطة",
                                    referenceNumber = sale.invoiceNumber,
                                    detailsMap = receiptLines + listOf(
                                        "العميل" to successState.customerNameArabic,
                                        "الإجمالي" to formatMoneyMinor(sale.totalAmountMinor),
                                        "المدفوع" to formatMoneyMinor(sale.paidAmountMinor),
                                        "المتبقي" to formatMoneyMinor(saleRemainingMinor)
                                    ),
                                    footerNoteArabic = shopConfig.receiptFooterNote
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_success_print_btn")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("طباعة")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ReceiptFormatter.sharePlainTextReceipt(
                                    context = context,
                                    title = "فاتورة #${sale.invoiceNumber}",
                                    body = plainReceiptText
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_success_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة")
                        }
                        OutlinedButton(
                            onClick = {
                                launchWhatsAppMessage(
                                    context = context,
                                    rawPhone = successState.customerPhone,
                                    messageArabic = plainReceiptText
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_success_whatsapp_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startNewPosSale() },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pos_new_sale_btn")
                ) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بيع جديد", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 8. Thermal / PDF Receipt Preview Dialog (58mm / 80mm / PDF)
    previewReceiptSale?.let { sale ->
        val custName = customers.firstOrNull { it.localId == sale.customerId }?.fullName ?: "عميل نقدي مباشر"
        val remMinor = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
        ThermalReceiptPreviewDialog(
            shopNameArabic = shopConfig.shopNameArabic,
            shopPhone = shopConfig.shopPhone,
            documentTitleArabic = "فاتورة مبيعات",
            referenceNumber = sale.invoiceNumber,
            customerName = custName,
            customerPhone = previewCustomerPhone,
            detailsLines = previewReceiptLines.ifEmpty {
                listOf(
                    "صافي الفاتورة" to formatMoneyMinor(sale.totalAmountMinor),
                    "حالة الدفع" to sale.status
                )
            },
            totalMinor = sale.totalAmountMinor,
            paidMinor = sale.paidAmountMinor,
            remainingMinor = remMinor,
            footerNoteArabic = shopConfig.receiptFooterNote,
            onDismiss = { previewReceiptSale = null },
            context = context
        )
    }

    // 9. Atomic Sales Return Dialog (Level 3 High-Risk Financial Cancellation)
    returnTargetSale?.let { sale ->
        AlertDialog(
            onDismissRequest = { returnTargetSale = null },
            title = {
                Text(
                    text = "تأكيد مرتجع فاتورة مبيعات #${sale.invoiceNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MekkaCrimsonError
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "سيتم إعادة الأصناف والسيريالات إلى المخزون وإصدار قيد عكسي محاسبي (REFUND) بقيمة ${formatMoneyMinor(sale.totalAmountMinor)}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MekkaCrimsonError.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "تنبيه: لا يتم حذف الفاتورة الأصلية من السجل التاريخي، بل يوثق المرتجع بقيد مستقل لا يمكن التراجع عنه.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MekkaCrimsonError,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = returnReasonInput,
                        onValueChange = { returnReasonInput = it },
                        label = { Text("سبب المرتجع (إلزامي للتدقيق)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_return_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.executeSalesReturn(
                            originalSaleId = sale.localId,
                            reasonArabic = returnReasonInput.trim()
                        )
                        returnTargetSale = null
                        returnReasonInput = ""
                    },
                    enabled = returnReasonInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaCrimsonError),
                    modifier = Modifier.testTag("pos_confirm_return_btn")
                ) {
                    Text("تأكيد استرجاع الفاتورة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { returnTargetSale = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Main POS Layout
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activePosSubTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = activePosSubTab == 0,
                onClick = { activePosSubTab = 0 },
                modifier = Modifier.testTag("pos_tab_checkout"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (cartItems.isEmpty()) "نقطة البيع (POS)" else "نقطة البيع (${cartItems.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
            Tab(
                selected = activePosSubTab == 1,
                onClick = { activePosSubTab = 1 },
                modifier = Modifier.testTag("pos_tab_history"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("سجل الفواتير (${sales.size})", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (activePosSubTab == 0) {
            val normalizedQuery = Money.normalizeDigits(productSearchQuery).trim().lowercase()
            val matchedProducts = remember(products, normalizedQuery) {
                if (normalizedQuery.isEmpty()) {
                    products
                } else {
                    products.filter { p ->
                        p.nameArabic.lowercase().contains(normalizedQuery) ||
                            p.sku.lowercase().contains(normalizedQuery) ||
                            (p.barcode?.lowercase()?.contains(normalizedQuery) == true)
                    }
                }
            }
            val matchedInStockSerials = remember(serials, normalizedQuery, cartItems) {
                if (normalizedQuery.length < 3) {
                    emptyList()
                } else {
                    serials.filter { s ->
                        s.status == SerialLifecycleStatus.IN_STOCK &&
                            s.serialOrImei.lowercase().contains(normalizedQuery) &&
                            cartItems.none { it.serialNumberId == s.localId }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("sales_screen_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Inline Validation Alert Banner (Rule 23: Never silently ignore invalid input)
                if (inlineError != null) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pos_inline_validation_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = MekkaCrimsonError.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MekkaCrimsonError.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MekkaCrimsonError
                                    )
                                    Text(
                                        text = inlineError.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MekkaCrimsonError
                                    )
                                }
                                IconButton(onClick = { viewModel.clearPosValidationError() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "إغلاق التنبيه", tint = MekkaCrimsonError)
                                }
                            }
                        }
                    }
                }

                // STEP 1 & 2: PRODUCT SEARCH & PRODUCT SELECTION (Rules 14 & 15)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1. اختيار المنتج أو مسح الباركود / IMEI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { showBarcodeScanner = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("pos_scan_barcode_btn")
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح باركود", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مسح باركود / IMEI")
                                }
                            }

                            // Top Product Search Bar (Rule 14: «ابحث عن منتج / SKU / IMEI / باركود»)
                            OutlinedTextField(
                                value = productSearchQuery,
                                onValueChange = { productSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pos_product_search_input"),
                                placeholder = {
                                    Text("ابحث عن منتج / SKU / IMEI / باركود")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (productSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { productSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Direct IMEI Search Matches (if cashier typed an IMEI)
                            if (matchedInStockSerials.isNotEmpty()) {
                                Text(
                                    text = "أجهزة مطابقة برقم الـ IMEI:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MekkaEmeraldGreen
                                )
                                matchedInStockSerials.take(4).forEach { sr ->
                                    val prod = products.firstOrNull { it.productId == sr.productId }
                                    if (prod != null) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    viewModel.addOrIncrementPosCartItem(
                                                        product = prod,
                                                        brandName = prod.brandId?.let { brandMap[it] },
                                                        quantity = 1,
                                                        unitPriceMinor = prod.defaultSellingPriceMinor,
                                                        serialEntity = sr
                                                    )
                                                    productSearchQuery = ""
                                                }
                                                .testTag("pos_direct_imei_match_${sr.serialOrImei}"),
                                            color = MekkaEmeraldGreen.copy(alpha = 0.10f),
                                            border = BorderStroke(1.dp, MekkaEmeraldGreen.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${prod.nameArabic} — IMEI: ${sr.serialOrImei}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "السعر: ${formatMoneyMinor(prod.defaultSellingPriceMinor)}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                MekkaStatusBadge(text = "إضافة للسلة +", color = MekkaEmeraldGreen)
                                            }
                                        }
                                    }
                                }
                            }

                            // Product Catalog Selection List (Rule 15: Name, Available Qty, Selling Price, SKU, Brand, Image/Icon)
                            if (matchedProducts.isEmpty()) {
                                Text(
                                    text = if (products.isEmpty()) {
                                        "لا توجد منتجات مسجلة في المخزون حالياً. أضف منتجات من تبويب «المخزون»."
                                    } else {
                                        "لا توجد منتجات مطابقة لـ «$productSearchQuery»"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    matchedProducts.take(8).forEach { prod ->
                                        val bName = prod.brandId?.let { brandMap[it] }
                                        val outOfStock = prod.derivedStockQty <= 0
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(enabled = !outOfStock) {
                                                    if (prod.isSerialized) {
                                                        pendingSerializedProduct = prod
                                                        imeiFilterQuery = ""
                                                    } else {
                                                        viewModel.addOrIncrementPosCartItem(
                                                            product = prod,
                                                            brandName = bName
                                                        )
                                                    }
                                                }
                                                .testTag("pos_product_card_${prod.sku}"),
                                            color = if (outOfStock) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                if (outOfStock) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Product Thumbnail / Category Avatar
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (prod.isSerialized) MekkaGoldAccent.copy(alpha = 0.14f)
                                                                else MekkaEmeraldGreen.copy(alpha = 0.12f)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (prod.isSerialized) Icons.Default.PhonelinkSetup else Icons.Default.Inventory2,
                                                            contentDescription = prod.nameArabic,
                                                            tint = if (prod.isSerialized) MekkaGoldAccent else MekkaEmeraldGreen
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = prod.nameArabic,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = buildString {
                                                                append("SKU: ${prod.sku}")
                                                                if (!bName.isNullOrBlank()) append(" • الماركة: $bName")
                                                                if (prod.isSerialized) append(" • يتطلب IMEI")
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Column(
                                                    horizontalAlignment = Alignment.End,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = formatMoneyMinor(prod.defaultSellingPriceMinor),
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontFamily = LedgerMonoFontFamily,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MekkaEmeraldGreen
                                                    )
                                                    MekkaStatusBadge(
                                                        text = if (outOfStock) "نفذت الكمية (0)" else "المتاح: ${prod.derivedStockQty}",
                                                        color = if (outOfStock) MekkaCrimsonError else MekkaEmeraldGreen
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 3: PERSISTENT CART (Rule 16: Show Product, Qty, Price, Discount, IMEI, Line Total, and prominent إجمالي السلة)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_cart_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MekkaEmeraldGreen)
                                    Text(
                                        text = "2. سلة المبيعات (${cartItems.sumOf { it.quantity }} قطعة)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (cartItems.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearPosCart() },
                                        modifier = Modifier.testTag("pos_clear_cart_btn")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MekkaCrimsonError, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إفراغ السلة", color = MekkaCrimsonError)
                                    }
                                }
                            }

                            if (cartItems.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "السلة فارغة حالياً — اختر منتجاً أو امسح باركود/IMEI لإضافته للسلة.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                cartItems.forEach { item ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pos_cart_item_${item.sku}"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.productNameArabic,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = buildString {
                                                            append("SKU: ${item.sku}")
                                                            if (item.serialOrImei != null) {
                                                                append(" • IMEI: ${item.serialOrImei}")
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = formatMoneyMinor(item.netLineTotalMinor),
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontFamily = LedgerMonoFontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MekkaEmeraldGreen
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "سعر الوحدة: ${formatMoneyMinor(item.unitPriceMinor)}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    if (item.lineDiscountMinor > 0L) {
                                                        Text(
                                                            text = "خصم البند: ${formatMoneyMinor(item.lineDiscountMinor)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MekkaAmberWarning
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (!item.isSerialized) {
                                                        IconButton(
                                                            onClick = {
                                                                if (item.quantity > 1) {
                                                                    viewModel.updatePosCartItemQuantity(item.lineId, item.quantity - 1)
                                                                } else {
                                                                    viewModel.removePosCartItem(item.lineId)
                                                                }
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Remove, contentDescription = "إنقاص الكمية")
                                                        }
                                                        Text(
                                                            text = "${item.quantity}",
                                                            style = MaterialTheme.typography.titleSmall.copy(
                                                                fontFamily = LedgerMonoFontFamily,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 6.dp)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.updatePosCartItemQuantity(item.lineId, item.quantity + 1)
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = "زيادة الكمية")
                                                        }
                                                    } else {
                                                        MekkaStatusBadge(text = "1 جهاز مسلسل", color = MekkaGoldAccent)
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            editingCartLine = item
                                                            editPriceInput = "%.2f".format(java.util.Locale.US, item.unitPriceMinor / 100.0)
                                                            editDiscountInput = if (item.lineDiscountMinor > 0L) {
                                                                "%.2f".format(java.util.Locale.US, item.lineDiscountMinor / 100.0)
                                                            } else {
                                                                ""
                                                            }
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = "تعديل السعر أو الخصم", tint = MekkaBlueInfo)
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.removePosCartItem(item.lineId) },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "حذف من السلة", tint = MekkaCrimsonError)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Invoice-level Discount Input
                                OutlinedTextField(
                                    value = invoiceDiscountInput,
                                    onValueChange = { viewModel.setPosInvoiceDiscountInput(it) },
                                    label = { Text("خصم إضافي على الفاتورة (ج.م — اختياري)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pos_invoice_discount_input")
                                )
                            }

                            // Prominent Cart Total Banner (Rule 16: Show إجمالي السلة prominently)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pos_cart_total_banner"),
                                shape = RoundedCornerShape(14.dp),
                                color = MekkaEmeraldGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MekkaEmeraldGreen.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "إجمالي السلة",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (totalDiscountMinor > 0L) {
                                            Text(
                                                text = "بعد خصم ${formatMoneyMinor(totalDiscountMinor)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MekkaAmberWarning
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatMoneyMinor(finalTotalMinor),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = LedgerMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = MekkaEmeraldGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // STEP 4: EXPLICIT CUSTOMER SELECTION (Rule 17: Never silently select the first customer)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "3. تحديد العميل (إلزامي)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                MekkaStatusBadge(
                                    text = when (customerMode) {
                                        PosCustomerSelectionMode.NONE -> "اختر العميل أولًا"
                                        PosCustomerSelectionMode.WALK_IN -> "عميل نقدي مباشر"
                                        PosCustomerSelectionMode.REGISTERED -> selectedCustomer?.fullName ?: "عميل مسجل"
                                    },
                                    color = if (customerMode == PosCustomerSelectionMode.NONE) MekkaAmberWarning else MekkaEmeraldGreen
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.selectPosWalkInCustomer() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pos_quick_walkin_btn"),
                                    colors = if (customerMode == PosCustomerSelectionMode.WALK_IN) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MekkaEmeraldGreen.copy(alpha = 0.14f),
                                            contentColor = MekkaEmeraldGreen
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("عميل نقدي مباشر", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        customerSearchQuery = ""
                                        showCustomerPickerSheet = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pos_open_customer_picker_btn"),
                                    colors = if (customerMode == PosCustomerSelectionMode.REGISTERED) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MekkaEmeraldGreen.copy(alpha = 0.14f),
                                            contentColor = MekkaEmeraldGreen
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (customerMode == PosCustomerSelectionMode.REGISTERED && selectedCustomer != null) {
                                            selectedCustomer.fullName
                                        } else {
                                            "اختيار / إضافة عميل"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // STEP 5: PAYMENT METHOD & CREDIT / PARTIAL SUMMARY (Rule 18)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "4. طريقة الدفع",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PosPaymentMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = paymentMode == mode,
                                        onClick = { viewModel.setPosPaymentMode(mode) },
                                        label = {
                                            Text(
                                                text = mode.labelArabic,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pos_payment_mode_${mode.name.lowercase()}")
                                    )
                                }
                            }

                            // Partial Payment Details (Rule 18: Show Total, Paid, Remaining)
                            if (paymentMode == PosPaymentMode.PARTIAL) {
                                OutlinedTextField(
                                    value = partialPaidInput,
                                    onValueChange = { viewModel.setPosPartialPaidInput(it) },
                                    label = { Text("المبلغ المدفوع الآن (ج.م)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pos_partial_paid_input")
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("الإجمالي:", style = MaterialTheme.typography.bodySmall)
                                            Text(formatMoneyMinor(finalTotalMinor), style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily, fontWeight = FontWeight.Bold))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("المدفوع:", style = MaterialTheme.typography.bodySmall)
                                            Text(formatMoneyMinor(paidAmountMinor), style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily), color = FinancialPositive)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("المتبقي على العميل:", style = MaterialTheme.typography.bodySmall)
                                            Text(formatMoneyMinor(remainingAmountMinor), style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily, fontWeight = FontWeight.Bold), color = MekkaCrimsonError)
                                        }
                                    }
                                }
                            }

                            // Credit / Partial Customer Balance Preview (Rule 18: Show Customer, Existing balance, New balance)
                            if (paymentMode == PosPaymentMode.CREDIT || paymentMode == PosPaymentMode.PARTIAL) {
                                Surface(
                                    color = MekkaAmberWarning.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("العميل:", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = selectedCustomer?.fullName ?: "اختر عميلاً مسجلاً أولًا",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("الرصيد السابق:", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = formatMoneyMinor(selectedCustomerBalanceMinor),
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily)
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("الرصيد الجديد بعد الفاتورة:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = formatMoneyMinor(selectedCustomerBalanceMinor + remainingAmountMinor),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = LedgerMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MekkaCrimsonError
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { viewModel.setPosNotesInput(it) },
                                label = { Text("ملاحظات الفاتورة أو الضمان (اختياري)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pos_notes_input")
                            )

                            // STEP 6: REVIEW SALE BUTTON (Rule 19: مراجعة البيع)
                            Button(
                                onClick = { viewModel.requestPosSaleReview() },
                                enabled = checkoutState !is PosCheckoutState.Processing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .testTag("pos_review_sale_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MekkaEmeraldGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PointOfSale, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "مراجعة البيع (${formatMoneyMinor(finalTotalMinor)})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Secondary Tab: Sales Invoices History & Atomic Returns
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("sales_history_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sales.isEmpty()) {
                    item {
                        Text(
                            text = "لم يتم إصدار فواتير مبيعات بعد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(sales, key = { it.localId }) { sale ->
                        val isReturned = sale.status == "REFUNDED" || sale.status == "RETURNED" || refunds.any { it.originalSaleId == sale.localId }
                        val saleCustName = customers.firstOrNull { it.localId == sale.customerId }?.fullName ?: "عميل نقدي مباشر"
                        val saleRemMinor = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                        text = "فاتورة #${sale.invoiceNumber}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = LedgerMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    StatusPillBadge(
                                        text = if (isReturned) "مرتجع" else com.example.financial.FriendlyTerminologyMapper.toFriendlyStatusArabic(sale.status),
                                        color = if (isReturned) FinancialNegative else FinancialPositive
                                    )
                                }
                                Text(
                                    text = "العميل: $saleCustName • التاريخ: ${formatTimestampArabic(sale.saleTimestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "الصافي: ${formatMoneyMinor(sale.totalAmountMinor)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = LedgerMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MekkaEmeraldGreen
                                    )
                                    Text(
                                        text = "المدفوع: ${formatMoneyMinor(sale.paidAmountMinor)} • المتبقي: ${formatMoneyMinor(saleRemMinor)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val itemsForSale = allSaleItems.filter { it.saleId == sale.localId }
                                            val lines = itemsForSale.map { si ->
                                                val prodName = products.firstOrNull { it.productId == si.productId }?.nameArabic ?: si.productId
                                                "$prodName × ${si.quantity}" to formatMoneyMinor(si.lineTotalMinor)
                                            }
                                            previewReceiptSale = sale
                                            previewReceiptLines = lines.ifEmpty {
                                                listOf(
                                                    "رقم الفاتورة" to sale.invoiceNumber,
                                                    "الصافي" to formatMoneyMinor(sale.totalAmountMinor)
                                                )
                                            }
                                            previewCustomerPhone = customers.firstOrNull { it.localId == sale.customerId }?.phone.orEmpty()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("عرض الفاتورة")
                                    }
                                    if (!isReturned) {
                                        OutlinedButton(
                                            onClick = {
                                                returnTargetSale = sale
                                                returnReasonInput = ""
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Undo, contentDescription = null, tint = MekkaCrimsonError, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("مرتجع", color = MekkaCrimsonError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

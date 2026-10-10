package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.PurchaseEntity
import com.example.financial.Money
import com.example.financial.PurchaseCalculationEngine
import com.example.financial.PurchaseLineInput
import com.example.intelligence.PurchaseDraftRecord
import com.example.intelligence.PurchaseDraftStatus
import com.example.intelligence.SuggestedReorderItem
import com.example.inventory.ImeiValidationEngine
import com.example.ui.components.MekkaEmptyStateCard
import com.example.ui.components.MekkaProcessingButton
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.components.UnsavedChangesDialog
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.MekkaGoldAccent
import com.example.ui.viewmodel.MekkaPhoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun formatPurchaseDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

data class PurchaseDraftItemUi(
    val draftId: String,
    val productId: String,
    val productNameArabic: String,
    val sku: String,
    val isSerialized: Boolean,
    val quantity: Int,
    val unitCostMinor: Long,
    val serialsList: List<String>
) {
    val lineSubtotalMinor: Long
        get() = quantity.toLong() * unitCostMinor
}

// ============================================================================
// 10 & 11. PURCHASES WORKFLOW & PURCHASE SAFETY
// Select supplier ↓ Add products ↓ Quantity ↓ Cost ↓ IMEI where applicable ↓
// Discount ↓ Paid ↓ Remaining ↓ Review ↓ Confirm ↓ Success
// ============================================================================

@Composable
fun PurchasesScreen(viewModel: MekkaPhoneViewModel) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()
    val pendingProdId by viewModel.pendingPurchaseProductId.collectAsStateWithLifecycle()
    val purchaseDrafts by viewModel.purchaseDrafts.collectAsStateWithLifecycle()
    val suggestedReorders by viewModel.suggestedReorderList.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()

    var currentPurchaseTab by rememberSaveable { mutableIntStateOf(0) }
    var draftToConvert by remember { mutableStateOf<PurchaseDraftRecord?>(null) }
    var convertPaymentAccountType by remember { mutableStateOf(FinancialAccountType.CASH_DRAWER) }
    var convertPaymentAccountId by remember { mutableStateOf(com.example.repository.MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID) }
    var showCreateDraftDialog by remember { mutableStateOf(false) }
    var draftSupplierId by rememberSaveable { mutableStateOf("") }
    var draftNotesInput by rememberSaveable { mutableStateOf("") }
    var selectedReorderProductIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val returnedPurchaseIds = remember(ledgerTransactions) {
        ledgerTransactions
            .filter {
                it.relatedEntityType == "PURCHASE_RETURN" &&
                    it.status == com.example.database.entities.LedgerTransactionStatus.POSTED
            }
            .mapNotNull { it.relatedEntityId }
            .toSet()
    }

    // Explicit supplier & product selection — NEVER use firstOrNull() (Rule 10)
    var selectedSupplierId by rememberSaveable { mutableStateOf("") }
    var supplierSearchQuery by rememberSaveable { mutableStateOf("") }

    var selectedProductId by rememberSaveable { mutableStateOf("") }
    var productSearchQuery by rememberSaveable { mutableStateOf("") }
    var itemQuantityStr by rememberSaveable { mutableStateOf("1") }
    var returnTargetPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var returnReasonInput by rememberSaveable { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(pendingProdId, products) {
        val pid = pendingProdId
        if (!pid.isNullOrBlank() && products.any { it.productId == pid }) {
            selectedProductId = pid
            viewModel.consumePurchaseProduct()
        }
    }
    var itemUnitCostStr by rememberSaveable { mutableStateOf("") }
    var itemImeisInput by rememberSaveable { mutableStateOf("") }

    var draftItems by remember { mutableStateOf<List<PurchaseDraftItemUi>>(emptyList()) }
    var discountMajorStr by rememberSaveable { mutableStateOf("0") }
    var paidAmountMajorStr by rememberSaveable { mutableStateOf("") }
    var invoiceNotes by rememberSaveable { mutableStateOf("") }
    var inlineError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveDraftItem by remember { mutableStateOf<PurchaseDraftItemUi?>(null) }
    var showUnsavedDiscardDialog by remember { mutableStateOf(false) }

    // Workflow state: 0 = Form, 1 = Review Dialog, 2 = Success Screen
    var workflowStage by rememberSaveable { mutableIntStateOf(0) }
    var completedPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var historyPageSize by rememberSaveable { mutableIntStateOf(30) }

    val hasUnsavedPurchaseDraft = draftItems.isNotEmpty() || itemUnitCostStr.isNotBlank() || itemImeisInput.isNotBlank()
    BackHandler(enabled = workflowStage == 0 && hasUnsavedPurchaseDraft) {
        showUnsavedDiscardDialog = true
    }

    val selectedSupplier = remember(suppliers, selectedSupplierId) {
        suppliers.firstOrNull { it.localId == selectedSupplierId }
    }
    val selectedProduct = remember(products, selectedProductId) {
        products.firstOrNull { it.productId == selectedProductId }
    }

    val filteredSuppliers = remember(suppliers, supplierSearchQuery) {
        val q = Money.normalizeDigits(supplierSearchQuery).trim().lowercase()
        if (q.isEmpty()) suppliers
        else suppliers.filter {
            it.companyName.lowercase().contains(q) ||
                it.contactName.lowercase().contains(q) ||
                Money.normalizeDigits(it.phone).lowercase().contains(q)
        }
    }

    val filteredProducts = remember(products, productSearchQuery) {
        val q = Money.normalizeDigits(productSearchQuery).trim().lowercase()
        if (q.isEmpty()) products
        else products.filter {
            it.nameArabic.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                (it.barcode?.lowercase()?.contains(q) == true)
        }
    }

    val discountMinor = remember(discountMajorStr) {
        if (discountMajorStr.isBlank()) 0L
        else runCatching { Money.parseMajorString(discountMajorStr).amountMinor }.getOrDefault(0L)
    }
    val paidOverrideMinor = remember(paidAmountMajorStr) {
        if (paidAmountMajorStr.isBlank()) null
        else runCatching { Money.parseMajorString(paidAmountMajorStr).amountMinor }.getOrNull()
    }

    // Domain Purchase Calculation (Rule 10 & Rule 29)
    val calculationSummary = remember(selectedSupplierId, draftItems, discountMinor, paidOverrideMinor) {
        PurchaseCalculationEngine.calculatePurchaseSummary(
            supplierId = selectedSupplierId,
            lineSubtotalsMinor = draftItems.map { it.lineSubtotalMinor },
            discountMinor = discountMinor,
            paidMinorOverride = paidOverrideMinor
        )
    }

    // Success Screen (Rule 10 & Rule 11)
    if (workflowStage == 2 && completedPurchase != null) {
        val pur = completedPurchase!!
        val remaining = (pur.totalAmountMinor - pur.paidAmountMinor).coerceAtLeast(0L)
        BackHandler {
            workflowStage = 0
            completedPurchase = null
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("purchase_success_screen"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "نجاح التوريد",
                            tint = FinancialPositive,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "تم اعتماد وترحيل فاتورة الشراء بنجاح",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = FinancialPositive,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "رقم الفاتورة: #${pur.invoiceNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الفاتورة قبل الخصم:")
                            Text(Money.ofMinor(pur.subtotalMinor).formatArabic(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الخصم:")
                            Text(Money.ofMinor(pur.discountMinor).formatArabic(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("صافي الفاتورة:")
                            Text(Money.ofMinor(pur.totalAmountMinor).formatArabic(), fontWeight = FontWeight.ExtraBold, color = MekkaEmeraldGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المبلغ المدفوع:")
                            Text(Money.ofMinor(pur.paidAmountMinor).formatArabic(), fontWeight = FontWeight.Bold, color = FinancialPositive)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي آجل للمورد:")
                            Text(Money.ofMinor(remaining).formatArabic(), fontWeight = FontWeight.Bold, color = if (remaining > 0L) FinancialWarning else FinancialPositive)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                workflowStage = 0
                                completedPurchase = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_purchase_after_success_button")
                        ) {
                            Text("بدء فاتورة شراء جديدة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("purchases_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "المشتريات والتوريد المخزني",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "اختر المورد ← أضف الأصناف والكميات والتكلفة و IMEI ← الخصم والمدفوع والمتبقي ← المراجعة والتأكيد الذري",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Primary Navigation TabRow for Purchases (Rule 10 & Requirements P2-D & P2-E)
        item {
            val tabs = listOf(
                "فاتورة شراء" to Icons.Default.Add,
                "مسودات الشراء (${purchaseDrafts.size})" to Icons.Default.Drafts,
                "إعادة الطلب الذكية (${suggestedReorders.size})" to Icons.Default.LocalShipping,
                "سجل الفواتير (${purchases.size})" to Icons.AutoMirrored.Filled.ReceiptLong
            )
            ScrollableTabRow(
                selectedTabIndex = currentPurchaseTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { idx, (label, icon) ->
                    Tab(
                        selected = currentPurchaseTab == idx,
                        onClick = { currentPurchaseTab = idx },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
                                Text(label, fontWeight = if (currentPurchaseTab == idx) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        modifier = Modifier.testTag("purchases_tab_$idx")
                    )
                }
            }
        }

        if (currentPurchaseTab == 0) {
        // Step 1: Explicit Searchable Supplier Selection (Rule 10)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Text(
                            text = "1. اختر المورد (إلزامي وبشكل صريح)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (selectedSupplier != null) {
                            Surface(
                                color = MekkaEmeraldGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "المورد: ${selectedSupplier.companyName}",
                                    color = MekkaEmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = supplierSearchQuery,
                        onValueChange = { supplierSearchQuery = it },
                        label = { Text("بحث عن مورد بالاسم أو رقم الهاتف...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث مورد") },
                        trailingIcon = {
                            if (supplierSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { supplierSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_supplier_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredSuppliers, key = { it.localId }) { sup ->
                            val isSelected = selectedSupplierId == sup.localId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSupplierId = sup.localId
                                    inlineError = null
                                },
                                label = {
                                    Text("${sup.companyName} (${sup.phone})")
                                },
                                modifier = Modifier.testTag("purchase_select_supplier_${sup.localId}")
                            )
                        }
                    }
                }
            }
        }

        // Step 2: Add Products + Quantity + Cost + IMEI (Rule 10)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. إضافة المنتجات والكمية والتكلفة و IMEI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = productSearchQuery,
                        onValueChange = { productSearchQuery = it },
                        label = { Text("بحث عن منتج بالاسم أو SKU أو الباركود...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث منتج") },
                        trailingIcon = {
                            if (productSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { productSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_product_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredProducts, key = { it.productId }) { prod ->
                            val isSelected = selectedProductId == prod.productId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProductId = prod.productId
                                    if (itemUnitCostStr.isBlank() && prod.weightedAvgCostMinor > 0L) {
                                        itemUnitCostStr = (prod.weightedAvgCostMinor / 100.0).toString()
                                    }
                                    inlineError = null
                                },
                                label = {
                                    Text("${prod.nameArabic} (المتاح: ${prod.derivedStockQty})")
                                },
                                modifier = Modifier.testTag("purchase_select_product_${prod.productId}")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemQuantityStr,
                            onValueChange = {
                                itemQuantityStr = it
                                inlineError = null
                            },
                            label = { Text("الكمية *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_item_qty_input")
                        )
                        OutlinedTextField(
                            value = itemUnitCostStr,
                            onValueChange = {
                                itemUnitCostStr = it
                                inlineError = null
                            },
                            label = { Text("تكلفة الوحدة (ج.م) *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_item_cost_input")
                        )
                    }

                    if (selectedProduct?.isSerialized == true) {
                        OutlinedTextField(
                            value = itemImeisInput,
                            onValueChange = {
                                itemImeisInput = it
                                inlineError = null
                            },
                            label = { Text("أرقام IMEI / السيريال (افصل بفاصلة أو سطر جديد بعدد الكمية) *") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purchase_item_imei_input")
                        )
                    }

                    Button(
                        onClick = {
                            val prod = selectedProduct
                            if (prod == null) {
                                inlineError = "يرجى اختيار المنتج بشكل صريح أولاً"
                                return@Button
                            }
                            val qty = itemQuantityStr.trim().toIntOrNull()
                            val unitCostMinor = runCatching { Money.parseMajorString(itemUnitCostStr).amountMinor }.getOrNull()
                            if (qty == null || qty <= 0) {
                                inlineError = "يرجى إدخال كمية صحيحة أكبر من الصفر"
                                return@Button
                            }
                            if (unitCostMinor == null || unitCostMinor <= 0L) {
                                inlineError = "يرجى إدخال تكلفة شراء صحيحة للوحدة"
                                return@Button
                            }
                            val serials = if (prod.isSerialized) {
                                itemImeisInput.split(",", "\n", ";")
                                    .map { ImeiValidationEngine.normalizeImeiOrSerial(it) }
                                    .filter { it.isNotEmpty() }
                            } else {
                                emptyList()
                            }
                            if (prod.isSerialized && serials.size != qty) {
                                inlineError = "المنتج (${prod.nameArabic}) يتطلب إدخال $qty رقم IMEI مطابق للكمية (المدخل حالياً: ${serials.size})"
                                return@Button
                            }
                            draftItems = draftItems + PurchaseDraftItemUi(
                                draftId = UUID.randomUUID().toString(),
                                productId = prod.productId,
                                productNameArabic = prod.nameArabic,
                                sku = prod.sku,
                                isSerialized = prod.isSerialized,
                                quantity = qty,
                                unitCostMinor = unitCostMinor,
                                serialsList = serials
                            )
                            // Reset item entry fields for next item
                            selectedProductId = ""
                            itemQuantityStr = "1"
                            itemUnitCostStr = ""
                            itemImeisInput = ""
                            inlineError = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة للفاتورة", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة الصنف إلى فاتورة الشراء", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Step 3: Draft Items List + Discount + Paid + Remaining + Review CTA (Rule 10 & Rule 11)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "3. بنود الفاتورة والحسابات (${draftItems.size} صنف)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (draftItems.isEmpty()) {
                        Text(
                            text = "لم تتم إضافة أصناف بعد. اختر المنتج أعلاه واضغط «إضافة الصنف إلى فاتورة الشراء».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        draftItems.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.productNameArabic, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "الكمية: ${item.quantity} × ${Money.ofMinor(item.unitCostMinor).formatArabic()} = ${Money.ofMinor(item.lineSubtotalMinor).formatArabic()}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (item.serialsList.isNotEmpty()) {
                                            Text(
                                                text = "IMEIs: ${item.serialsList.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MekkaEmeraldGreen
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            pendingRemoveDraftItem = item
                                        },
                                        modifier = Modifier.testTag("purchase_remove_draft_item_${item.productId}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف البند", tint = FinancialNegative)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discountMajorStr,
                            onValueChange = {
                                discountMajorStr = it
                                inlineError = null
                            },
                            label = { Text("الخصم (ج.م)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_discount_input")
                        )
                        OutlinedTextField(
                            value = paidAmountMajorStr,
                            onValueChange = {
                                paidAmountMajorStr = it
                                inlineError = null
                            },
                            label = { Text("المبلغ المدفوع (اتركه فارغاً للسداد الكامل)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_paid_input")
                        )
                    }

                    OutlinedTextField(
                        value = invoiceNotes,
                        onValueChange = { invoiceNotes = it },
                        label = { Text("ملاحظات فاتورة الشراء (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Domain Summary: Subtotal, Discount, Net Total, Paid, Remaining
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الأصناف:")
                                Text(Money.ofMinor(calculationSummary.subtotalMinor).formatArabic(), fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الخصم:")
                                Text(Money.ofMinor(calculationSummary.discountMinor).formatArabic(), fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("صافي الفاتورة:")
                                Text(
                                    text = Money.ofMinor(calculationSummary.netTotalMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المدفوع نقداً الآن:")
                                Text(
                                    text = Money.ofMinor(calculationSummary.paidMinor).formatArabic(),
                                    fontWeight = FontWeight.Bold,
                                    color = FinancialPositive
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المتبقي آجل للمورد:")
                                Text(
                                    text = Money.ofMinor(calculationSummary.remainingPayableMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (calculationSummary.remainingPayableMinor > 0L) FinancialWarning else FinancialPositive
                                )
                            }
                        }
                    }

                    if (!inlineError.isNullOrBlank()) {
                        Text(
                            text = inlineError!!,
                            color = FinancialNegative,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (!calculationSummary.isValid) {
                                inlineError = calculationSummary.validationErrorArabic
                                return@Button
                            }
                            inlineError = null
                            workflowStage = 1 // Open Review Dialog
                        },
                        enabled = !isProcessing && draftItems.isNotEmpty() && selectedSupplierId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_open_review_button")
                    ) {
                        Text("مراجعة فاتورة الشراء قبل التأكيد", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        } // End of currentPurchaseTab == 0 (New Purchase)

        // =========================================================================================
        // Tab 1: Purchase Drafts Center (P2-E: DRAFT, REVIEW, APPROVED, CONVERTED, CANCELLED)
        // =========================================================================================
        if (currentPurchaseTab == 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مسودات الشراء (${purchaseDrafts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "بدون أي أثر مالي قبل التحويل الفعلي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (purchaseDrafts.isEmpty()) {
                item {
                    EmptyStateGuidanceCard(
                        titleArabic = "لا توجد مسودات شراء حالياً.",
                        subtitleArabic = "يمكنك إنشاء مسودة شراء من قائمة «إعادة الطلب الذكية» لتجميع النواقص ومراجعتها قبل الشراء.",
                        actionButtonLabel = "عرض نواقص المخزون وإعادة الطلب",
                        onActionClick = { currentPurchaseTab = 2 }
                    )
                }
            } else {
                items(purchaseDrafts, key = { it.draftId }) { draft ->
                    val statusColor = when (draft.status) {
                        PurchaseDraftStatus.DRAFT -> MekkaAmberWarning
                        PurchaseDraftStatus.REVIEW -> FinancialInfo
                        PurchaseDraftStatus.APPROVED -> MekkaEmeraldGreen
                        PurchaseDraftStatus.CONVERTED -> MekkaGoldAccent
                        PurchaseDraftStatus.CANCELLED -> MekkaCrimsonError
                    }
                    val statusArabic = when (draft.status) {
                        PurchaseDraftStatus.DRAFT -> "مسودة مبدئية"
                        PurchaseDraftStatus.REVIEW -> "قيد المراجعة"
                        PurchaseDraftStatus.APPROVED -> "معتمدة للشراء"
                        PurchaseDraftStatus.CONVERTED -> "مرحلة رسمياً"
                        PurchaseDraftStatus.CANCELLED -> "ملغاة"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_draft_card_${draft.draftId}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "مسودة #${draft.draftNumber}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "المورد: ${draft.supplierNameArabic} • ${draft.items.size} أصناف",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                MekkaStatusBadge(text = statusArabic, color = statusColor)
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            draft.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${item.productNameArabic} (${item.quantity} × ${Money.ofMinor(item.estimatedUnitCostMinor).formatArabic()})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = Money.ofMinor(item.estimatedLineTotalMinor).formatArabic(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (draft.notes.isNotBlank()) {
                                Text(
                                    text = "ملاحظات: ${draft.notes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "إجمالي التكلفة التقديرية: ${Money.ofMinor(draft.totalEstimatedCostMinor).formatArabic()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaEmeraldGreen
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (draft.status) {
                                    PurchaseDraftStatus.DRAFT -> {
                                        TextButton(onClick = { viewModel.updatePurchaseDraftStatus(draft.draftId, PurchaseDraftStatus.CANCELLED) }) {
                                            Text("إلغاء المسودة", color = MekkaCrimsonError)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { viewModel.updatePurchaseDraftStatus(draft.draftId, PurchaseDraftStatus.REVIEW) },
                                            colors = ButtonDefaults.buttonColors(containerColor = FinancialInfo)
                                        ) {
                                            Text("إرسال للمراجعة")
                                        }
                                    }
                                    PurchaseDraftStatus.REVIEW -> {
                                        TextButton(onClick = { viewModel.updatePurchaseDraftStatus(draft.draftId, PurchaseDraftStatus.DRAFT) }) {
                                            Text("إعادة للتعديل")
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { viewModel.updatePurchaseDraftStatus(draft.draftId, PurchaseDraftStatus.APPROVED) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                                        ) {
                                            Text("اعتماد المسودة")
                                        }
                                    }
                                    PurchaseDraftStatus.APPROVED -> {
                                        Button(
                                            onClick = { draftToConvert = draft },
                                            colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                                            modifier = Modifier.testTag("convert_draft_btn_${draft.draftId}")
                                        ) {
                                            Text("تحويل لفاتورة شراء رسمية", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    PurchaseDraftStatus.CONVERTED -> {
                                        Text(
                                            text = "تم التوريد والترحيل الدفتري",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MekkaEmeraldGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    PurchaseDraftStatus.CANCELLED -> {
                                        TextButton(onClick = { viewModel.updatePurchaseDraftStatus(draft.draftId, PurchaseDraftStatus.DRAFT) }) {
                                            Text("إعادة تفعيل كمسودة")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================================
        // Tab 2: Smart Reorder List (P2-D: Suggested Purchases + Create Purchase Draft)
        // =========================================================================================
        if (currentPurchaseTab == 2) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "اقتراحات إعادة الطلب الذكية (${suggestedReorders.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "تجميع الأصناف التي بلغت حد الطلب لإنشاء مسودة شراء معتمدة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (suggestedReorders.isEmpty()) {
                item {
                    EmptyStateGuidanceCard(
                        titleArabic = "المخزون متوازن ومكتمل!",
                        subtitleArabic = "لا توجد أصناف تحتاج إعادة طلب حالياً بناءً على معدلات البيع وسياسة النواقص.",
                        actionButtonLabel = "عرض المخزون",
                        onActionClick = { }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = selectedReorderProductIds.size == suggestedReorders.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                selectedReorderProductIds = if (allSelected) emptySet() else suggestedReorders.map { it.productId }.toSet()
                            }
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { chk ->
                                    selectedReorderProductIds = if (chk) suggestedReorders.map { it.productId }.toSet() else emptySet()
                                }
                            )
                            Text("تحديد الكل (${selectedReorderProductIds.size}/${suggestedReorders.size})", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                val selectedItems = if (selectedReorderProductIds.isEmpty()) suggestedReorders else suggestedReorders.filter { it.productId in selectedReorderProductIds }
                                val firstSupplier = suppliers.firstOrNull { it.localId == selectedItems.firstOrNull()?.primarySupplierId } ?: suppliers.firstOrNull()
                                viewModel.createPurchaseDraftFromSuggestedReorder(
                                    supplierId = firstSupplier?.localId,
                                    supplierNameArabic = firstSupplier?.companyName ?: "مورد عام",
                                    items = selectedItems,
                                    notes = "مسودة شراء آلية مقترحة من نظام إعادة الطلب الذكي"
                                )
                                currentPurchaseTab = 1 // Switch to drafts tab
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier.testTag("create_purchase_draft_from_reorders_button")
                        ) {
                            Text("إنشاء مسودة شراء", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(suggestedReorders, key = { it.productId }) { item ->
                    val isChecked = item.productId in selectedReorderProductIds
                    val urgencyColor = when {
                        item.urgencyLevelArabic.contains("نافد") -> MekkaCrimsonError
                        item.urgencyLevelArabic.contains("منخفض") -> MekkaAmberWarning
                        else -> FinancialInfo
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suggested_reorder_item_${item.productId}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { chk ->
                                    selectedReorderProductIds = if (chk) selectedReorderProductIds + item.productId else selectedReorderProductIds - item.productId
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item.productNameArabic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    MekkaStatusBadge(text = item.urgencyLevelArabic, color = urgencyColor)
                                }
                                Text(
                                    text = "SKU: ${item.sku} • الرصيد: ${item.currentStockQty} • المستهدف: ${item.targetStockQty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "الكمية المقترحة: ${item.suggestedQuantity} قطعة • التكلفة: ${Money.ofMinor(item.estimatedTotalCostMinor).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================================
        // Tab 3: Historical Purchases List (Paginated)
        // =========================================================================================
        if (currentPurchaseTab == 3) {
        item {
            Text(
                text = "سجل فواتير المشتريات (${purchases.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (purchases.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = "لا توجد فواتير مشتريات مسجلة بعد.",
                    subtitleArabic = "اختر المورد بشكل صريح من القائمة أعلاه وأضف الأصناف لتسجيل أول فاتورة توريد للمخزون.",
                    actionButtonLabel = "استعراض الموردين أعلاه",
                    onActionClick = {
                        supplierSearchQuery = ""
                    }
                )
            }
        } else {
            items(purchases.take(historyPageSize), key = { it.localId }) { pur ->
                val supName = suppliers.firstOrNull { it.localId == pur.supplierId }?.companyName ?: pur.supplierId
                val remaining = (pur.totalAmountMinor - pur.paidAmountMinor).coerceAtLeast(0L)
                val isReturned = pur.localId in returnedPurchaseIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فاتورة شراء #${pur.invoiceNumber}", fontWeight = FontWeight.Bold)
                                Text("المورد: $supName • ${formatPurchaseDate(pur.purchaseTimestamp)}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "المسدد: ${Money.ofMinor(pur.paidAmountMinor).formatArabic()} • الآجل: ${Money.ofMinor(remaining).formatArabic()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (remaining > 0L) FinancialWarning else FinancialPositive
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.ofMinor(pur.totalAmountMinor).formatArabic(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isReturned) FinancialNegative else MekkaEmeraldGreen
                                )
                                Text(
                                    text = when {
                                        isReturned -> "مرتجع للمورد"
                                        remaining == 0L -> "مسدد بالكامل"
                                        pur.paidAmountMinor == 0L -> "شراء آجل"
                                        else -> "سداد جزئي"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isReturned) FinancialNegative else if (remaining > 0L) FinancialWarning else FinancialPositive
                                )
                            }
                        }
                        if (!isReturned) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        returnTargetPurchase = pur
                                        returnReasonInput = ""
                                    },
                                    modifier = Modifier.testTag("purchase_return_btn_${pur.localId}")
                                ) {
                                    Text("مرتجع مشتريات للمورد", color = FinancialNegative, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (purchases.size > historyPageSize) {
                item {
                    OutlinedButton(
                        onClick = { historyPageSize += 30 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عرض المزيد من فواتير المشتريات")
                    }
                }
            }
        }
        } // End of currentPurchaseTab == 3
    }

    // Convert Purchase Draft to Official Purchase Dialog (Requirement P2-E)
    draftToConvert?.let { draft ->
        AlertDialog(
            onDismissRequest = { draftToConvert = null },
            title = {
                Text("تحويل مسودة الشراء #${draft.draftNumber} إلى فاتورة رسمية", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المورد: ${draft.supplierNameArabic}")
                    Text("إجمالي القيمة التقديرية: ${Money.ofMinor(draft.totalEstimatedCostMinor).formatArabic()}")
                    Text("اختر وسيلة الدفع لتسجيل القيد المالي وترحيل الكميات للمخزن:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = convertPaymentAccountType == FinancialAccountType.CASH_DRAWER,
                            onClick = {
                                convertPaymentAccountType = FinancialAccountType.CASH_DRAWER
                                convertPaymentAccountId = com.example.repository.MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
                            },
                            label = { Text("الخزينة النقدية") }
                        )
                        FilterChip(
                            selected = convertPaymentAccountType == FinancialAccountType.WALLET,
                            onClick = {
                                convertPaymentAccountType = FinancialAccountType.WALLET
                                convertPaymentAccountId = wallets.firstOrNull()?.walletId ?: ""
                            },
                            label = { Text("محفظة إلكترونية") }
                        )
                    }
                    if (convertPaymentAccountType == FinancialAccountType.WALLET) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets) { w ->
                                FilterChip(
                                    selected = convertPaymentAccountId == w.walletId,
                                    onClick = { convertPaymentAccountId = w.walletId },
                                    label = { Text("${w.walletNameArabic} (${Money.ofMinor(w.derivedBalanceMinor).formatArabic()})") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dId = draft.draftId
                        draftToConvert = null
                        viewModel.convertPurchaseDraftToActualPurchase(
                            draftId = dId,
                            paymentAccountType = convertPaymentAccountType,
                            paymentAccountId = convertPaymentAccountId
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("confirm_convert_purchase_draft_button")
                ) {
                    Text("تأكيد التوريد والترحيل الدفتري", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToConvert = null }) { Text("إلغاء") }
            }
        )
    }

    // Purchase Return Confirmation Dialog
    returnTargetPurchase?.let { targetPur ->
        AlertDialog(
            onDismissRequest = { if (!isProcessing) returnTargetPurchase = null },
            title = {
                Text("تأكيد مرتجع مشتريات للمورد (#${targetPur.invoiceNumber})", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "سيتم خصم الأصناف من المخزون وعكس قيد الفاتورة وتحديث حساب المورد والخزينة بقيمة ${Money.ofMinor(targetPur.totalAmountMinor).formatArabic()}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = returnReasonInput,
                        onValueChange = { returnReasonInput = it },
                        label = { Text("سبب المرتجع للمورد") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_return_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.executePurchaseReturn(
                            originalPurchaseId = targetPur.localId,
                            reasonArabic = returnReasonInput.ifBlank { "مرتجع مشتريات للمورد" }
                        )
                        returnTargetPurchase = null
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = FinancialNegative),
                    modifier = Modifier.testTag("confirm_purchase_return_button")
                ) {
                    Text("تأكيد المرتجع للمورد")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { returnTargetPurchase = null },
                    enabled = !isProcessing
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Level 2 Confirmation: Remove Item from Purchase Draft (Section 14)
    pendingRemoveDraftItem?.let { itemToRemove ->
        AlertDialog(
            onDismissRequest = { pendingRemoveDraftItem = null },
            title = { Text("إزالة الصنف من مسودة الشراء؟", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل تريد إزالة «${itemToRemove.productNameArabic}» (الكمية: ${itemToRemove.quantity}) من فاتورة الشراء الحالية؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        draftItems = draftItems.filterNot { it.draftId == itemToRemove.draftId }
                        pendingRemoveDraftItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinancialWarning),
                    modifier = Modifier.testTag("confirm_remove_purchase_draft_item_button")
                ) {
                    Text("إزالة البند")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveDraftItem = null }) {
                    Text("تراجع")
                }
            }
        )
    }

    // Section 18: Unsaved Changes Dialog
    if (showUnsavedDiscardDialog) {
        UnsavedChangesDialog(
            changesSummaryArabic = "لديك مسودة فاتورة شراء تحتوي على ${draftItems.size} أصناف لم يتم ترحيلها بعد.",
            canSaveDirectly = calculationSummary.isValid && draftItems.isNotEmpty() && selectedSupplierId.isNotBlank(),
            onContinueEditing = { showUnsavedDiscardDialog = false },
            onSaveChanges = {
                showUnsavedDiscardDialog = false
                if (calculationSummary.isValid) {
                    workflowStage = 1
                }
            },
            onDiscardChanges = {
                showUnsavedDiscardDialog = false
                draftItems = emptyList()
                selectedSupplierId = ""
                selectedProductId = ""
                itemUnitCostStr = ""
                itemImeisInput = ""
                discountMajorStr = "0"
                paidAmountMajorStr = ""
                invoiceNotes = ""
                inlineError = null
            }
        )
    }

    // Step 4: Purchase Review & Safe Atomic Confirmation Dialog (Section 16: Supplier, Products, Cost, Paid, Remaining)
    if (workflowStage == 1) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) workflowStage = 0 },
            title = {
                Text("مراجعة وتأكيد فاتورة الشراء قبل الاعتماد", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• المورد: ${selectedSupplier?.companyName ?: "-"}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• المنتجات والأصناف (${draftItems.size} صنف / ${draftItems.sumOf { it.quantity }} قطعة):",
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            draftItems.forEach { line ->
                                Text(
                                    text = "- ${line.productNameArabic}: ${line.quantity} × ${Money.ofMinor(line.unitCostMinor).formatArabic()} = ${Money.ofMinor(line.lineSubtotalMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("• إجمالي التكلفة قبل الخصم: ${Money.ofMinor(calculationSummary.subtotalMinor).formatArabic()}")
                    if (calculationSummary.discountMinor > 0L) {
                        Text("• الخصم: ${Money.ofMinor(calculationSummary.discountMinor).formatArabic()}")
                    }
                    Text(
                        text = "• صافي تكلفة الشراء: ${Money.ofMinor(calculationSummary.netTotalMinor).formatArabic()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = MekkaEmeraldGreen
                    )
                    Text(
                        text = "• المبلغ المدفوع الآن: ${Money.ofMinor(calculationSummary.paidMinor).formatArabic()}",
                        color = FinancialPositive,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• المتبقي آجل للمورد: ${Money.ofMinor(calculationSummary.remainingPayableMinor).formatArabic()}",
                        color = if (calculationSummary.remainingPayableMinor > 0L) FinancialWarning else FinancialPositive,
                        fontWeight = FontWeight.Bold
                    )

                    if (!inlineError.isNullOrBlank()) {
                        Text(
                            text = inlineError!!,
                            color = FinancialNegative,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                MekkaProcessingButton(
                    idleLabelArabic = "تأكيد التوريد نهائياً",
                    processingLabelArabic = "جارٍ تسجيل فاتورة الشراء...",
                    isProcessing = isProcessing,
                    containerColor = MekkaEmeraldGreen,
                    testTag = "purchase_confirm_atomic_button",
                    onClick = {
                        val purchaseInputs = draftItems.map { item ->
                            PurchaseLineInput(
                                productId = item.productId,
                                quantity = item.quantity,
                                unitCostMinor = item.unitCostMinor,
                                serialNumbersOrImeis = item.serialsList
                            )
                        }
                        viewModel.executeSafePurchaseOrder(
                            supplierId = selectedSupplierId,
                            cartItems = purchaseInputs,
                            discountMajorStr = discountMajorStr,
                            paidAmountMajorStr = paidAmountMajorStr,
                            notes = invoiceNotes.ifBlank { "فاتورة توريد مخزني" },
                            onSuccess = { createdPurchase ->
                                // Rule 11: Clear form ONLY after success!
                                draftItems = emptyList()
                                selectedSupplierId = ""
                                selectedProductId = ""
                                discountMajorStr = "0"
                                paidAmountMajorStr = ""
                                invoiceNotes = ""
                                inlineError = null
                                completedPurchase = createdPurchase
                                workflowStage = 2
                            },
                            onError = { err ->
                                // Rule 11: Preserve form on failure!
                                inlineError = err
                            }
                        )
                    }
                )
            },
            dismissButton = {
                if (!isProcessing) {
                    TextButton(onClick = { workflowStage = 0 }) {
                        Text("رجوع للتعديل")
                    }
                }
            }
        )
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppResult
import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.BrandEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.financial.FriendlyTerminologyMapper
import com.example.financial.Money
import com.example.inventory.ImeiRegistryProjection
import com.example.inventory.ImeiValidationEngine
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

import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.SupplierEntity
import com.example.intelligence.PriceAnalysisAndChangeEngine
import com.example.intelligence.PriceChangePreview
import com.example.intelligence.ProductDemandForecastItem
import com.example.intelligence.ProductIntelligenceSummary
import com.example.intelligence.ProductPurchaseIntelligenceEngine
import com.example.ui.components.MekkaStatusBadge
import com.example.ui.theme.MekkaAmberWarning
import com.example.ui.theme.MekkaCrimsonError

private fun formatInventoryTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

enum class InventoryListFilter(val arabicLabel: String) {
    ALL("الكل"),
    LOW_STOCK("منخفض"),
    OUT_OF_STOCK("نفد"),
    FAST_MOVING("سريع الحركة"),
    SLOW_MOVING("بطيء الحركة"),
    DEAD_STOCK("مخزون راكد"),
    SERIALIZED("Serialized"),
    NON_SERIALIZED("غير Serialized")
}

// ============================================================================
// 5, 6, 7, 8, 9. PRODUCTS / INVENTORY + PRODUCT CREATION + DETAILS + IMEI + MOVEMENTS
// ============================================================================

@Composable
fun ProductsScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit = {}
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val movements by viewModel.inventoryMovements.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val allPurchaseItems by viewModel.allPurchaseItems.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    val imeiProjections by viewModel.imeiRegistryProjections.collectAsStateWithLifecycle()
    val demandForecasts by viewModel.demandForecasts.collectAsStateWithLifecycle()
    val pendingCountMode by viewModel.pendingInventoryCountMode.collectAsStateWithLifecycle()
    val pendingProductRef by viewModel.pendingSelectedProductIdOrSku.collectAsStateWithLifecycle()
    val pendingImeiQuery by viewModel.pendingImeiSearchQuery.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(InventoryListFilter.ALL) }
    var pageSize by rememberSaveable { mutableIntStateOf(50) }

    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateProductDialog by rememberSaveable { mutableStateOf(false) }
    var showBarcodeScannerDialog by rememberSaveable { mutableStateOf(false) }
    var showImeiManagementView by rememberSaveable { mutableStateOf(false) }
    var initialImeiSearchQuery by rememberSaveable { mutableStateOf("") }
    var showAdjustmentDialogForProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var isInventoryCountMode by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(pendingCountMode) {
        if (pendingCountMode) {
            isInventoryCountMode = true
            selectedProductId = null
            showImeiManagementView = false
            viewModel.consumeInventoryCountingMode()
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingProductRef, products) {
        val ref = pendingProductRef
        if (!ref.isNullOrBlank()) {
            val matched = products.firstOrNull {
                it.productId == ref || it.sku.equals(ref, ignoreCase = true)
            }
            if (matched != null) {
                selectedProductId = matched.productId
                showImeiManagementView = false
                viewModel.consumeSelectedProductDetail()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingImeiQuery) {
        val imei = pendingImeiQuery
        if (!imei.isNullOrBlank()) {
            initialImeiSearchQuery = imei
            selectedProductId = null
            showImeiManagementView = true
            viewModel.consumeImeiSearch()
        }
    }

    val selectedProduct = remember(products, selectedProductId) {
        selectedProductId?.let { id -> products.firstOrNull { it.productId == id } }
    }

    when {
        // 7. Dedicated Product Details Screen
        selectedProduct != null -> {
            val categoryName = categories.firstOrNull { it.localId == selectedProduct.categoryId }?.nameArabic ?: "-"
            val brandName = brands.firstOrNull { it.localId == selectedProduct.brandId }?.name ?: "-"
            ProductDetailsProfileView(
                product = selectedProduct,
                categoryNameArabic = categoryName,
                brandNameArabic = brandName,
                productImeis = imeiProjections.filter { it.serialEntity.productId == selectedProduct.productId },
                productMovements = movements.filter { it.productId == selectedProduct.productId },
                productSaleItems = allSaleItems.filter { it.productId == selectedProduct.productId },
                salesById = sales.associateBy { it.localId },
                productPurchaseItems = allPurchaseItems.filter { it.productId == selectedProduct.productId },
                purchasesById = purchases.associateBy { it.localId },
                suppliers = suppliers,
                priceHistory = priceHistory,
                demandForecast = demandForecasts.firstOrNull { it.productId == selectedProduct.productId },
                onConfirmPriceChange = { newPriceMinor, reason ->
                    viewModel.confirmPriceChange(
                        productId = selectedProduct.productId,
                        proposedPriceMinor = newPriceMinor,
                        reasonArabic = reason
                    )
                },
                onBack = { selectedProductId = null },
                onOpenMovementAdjustment = {
                    showAdjustmentDialogForProductId = selectedProduct.productId
                },
                onSellProductInPos = {
                    if (!selectedProduct.isSerialized && selectedProduct.derivedStockQty > 0) {
                        viewModel.addOrIncrementPosCartItem(
                            product = selectedProduct,
                            brandName = brandName.takeIf { it != "-" }
                        )
                    }
                    onNavigateToRoute(com.example.navigation.MekkaDestination.SALES.route)
                },
                onPurchaseProduct = {
                    viewModel.requestPurchaseProduct(selectedProduct.productId)
                    onNavigateToRoute(com.example.navigation.MekkaDestination.PURCHASES.route)
                },
                onCopyImei = { imei ->
                    viewModel.showFeedbackMessage("تم نسخ IMEI ($imei) للحافظة")
                }
            )
        }

        // 8. Dedicated IMEI Management Screen
        showImeiManagementView -> {
            ImeiManagementScreenView(
                imeiProjections = imeiProjections,
                initialSearchQuery = initialImeiSearchQuery,
                onBack = {
                    showImeiManagementView = false
                    initialImeiSearchQuery = ""
                },
                onFeedback = { msg, err -> viewModel.showFeedbackMessage(msg, err) }
            )
        }

        // 5. Main Inventory List Screen («المخزون»)
        else -> {
            val filteredProducts = remember(products, searchQuery, selectedFilter, allSaleItems) {
                val q = Money.normalizeDigits(searchQuery).trim().lowercase()
                val unitsSoldByProd = allSaleItems.groupBy { it.productId }
                    .mapValues { (_, items) -> items.sumOf { (it.quantity - it.returnedQuantity).coerceAtLeast(0) } }

                products.filter { prod ->
                    val matchesSearch = q.isEmpty() ||
                        prod.nameArabic.lowercase().contains(q) ||
                        prod.sku.lowercase().contains(q) ||
                        (prod.barcode?.lowercase()?.contains(q) == true)
                    val unitsSold = unitsSoldByProd[prod.productId] ?: 0
                    val matchesFilter = when (selectedFilter) {
                        InventoryListFilter.ALL -> true
                        InventoryListFilter.LOW_STOCK -> prod.derivedStockQty in 1..prod.reorderThresholdQty
                        InventoryListFilter.OUT_OF_STOCK -> prod.derivedStockQty <= 0
                        InventoryListFilter.FAST_MOVING -> unitsSold >= 5
                        InventoryListFilter.SLOW_MOVING -> prod.derivedStockQty > 0 && unitsSold in 1..4
                        InventoryListFilter.DEAD_STOCK -> prod.derivedStockQty > 0 && unitsSold == 0
                        InventoryListFilter.SERIALIZED -> prod.isSerialized
                        InventoryListFilter.NON_SERIALIZED -> !prod.isSerialized
                    }
                    matchesSearch && matchesFilter
                }
            }

            val pagedProducts = remember(filteredProducts, pageSize) {
                filteredProducts.take(pageSize)
            }

            val totalValuationMinor = remember(products) {
                products.sumOf { it.stockValueMinor }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("inventory_screen_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProcurementAndInventoryAnalyticsSection(viewModel = viewModel)
                }

                // Header & Top Actions (Rule 5)
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
                                        text = "المخزون",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "الأصناف: ${products.size} • القيمة الإجمالية (WAC): ${Money.ofMinor(totalValuationMinor).formatArabic()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showCreateProductDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                    modifier = Modifier.testTag("add_product_top_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "إضافة منتج", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ منتج", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Top Action Bar: بحث / مسح باركود / IMEI (Rule 5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        pageSize = 50
                                    },
                                    label = { Text("بحث بالاسم أو SKU أو الباركود...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inventory_search_input")
                                )

                                OutlinedButton(
                                    onClick = { showBarcodeScannerDialog = true },
                                    modifier = Modifier.testTag("inventory_scan_barcode_button")
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح باركود", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مسح باركود")
                                }

                                OutlinedButton(
                                    onClick = { showImeiManagementView = true },
                                    modifier = Modifier.testTag("inventory_imei_button")
                                ) {
                                    Icon(Icons.Default.Smartphone, contentDescription = "IMEI", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("IMEI")
                                }
                            }

                            // Filters: الكل / منخفض / نفد / Serialized / غير Serialized (Rule 5)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(InventoryListFilter.entries) { filter ->
                                    val selected = selectedFilter == filter
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedFilter = filter
                                            pageSize = 50
                                        },
                                        label = {
                                            Text(
                                                text = filter.arabicLabel,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.16f),
                                            selectedLabelColor = MekkaEmeraldGreen
                                        ),
                                        modifier = Modifier.testTag("inventory_filter_${filter.name.lowercase()}")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = isInventoryCountMode,
                                        onClick = { isInventoryCountMode = !isInventoryCountMode },
                                        label = {
                                            Text(
                                                text = if (isInventoryCountMode) "وضع الجرد نشط ✓" else "وضع الجرد الفعلي",
                                                fontWeight = if (isInventoryCountMode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MekkaGoldAccent.copy(alpha = 0.22f),
                                            selectedLabelColor = MekkaGoldAccent
                                        ),
                                        modifier = Modifier.testTag("inventory_count_mode_chip")
                                    )
                                }
                            }

                            if (isInventoryCountMode) {
                                Surface(
                                    color = MekkaGoldAccent.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inventory_counting_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "وضع الجرد الفعلي للمخزون (Stocktaking)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "اضغط على «تسوية الجرد» أمام أي صنف لتسجيل حركة جرد أو مطابقة الرصيد الفعلي.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { isInventoryCountMode = false }) {
                                            Text("إنهاء الجرد")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty State (Rule 27 exact example!)
                if (filteredProducts.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = if (products.isEmpty()) {
                                "لا توجد منتجات بعد."
                            } else {
                                "لا توجد منتجات مطابقة لبحثك أو الفلتر المحدد."
                            },
                            subtitleArabic = if (products.isEmpty()) {
                                "أضف أول منتج للمخزون للبدء."
                            } else {
                                "جرّب البحث بكلمة أخرى أو اختر فلتر «الكل» أو أضف منتجاً جديداً."
                            },
                            actionButtonLabel = "+ إضافة منتج",
                            onActionClick = { showCreateProductDialog = true },
                            testTag = "inventory_empty_state_card"
                        )
                    }
                } else {
                    items(pagedProducts, key = { it.productId }) { prod ->
                        val isOut = prod.derivedStockQty <= 0
                        val isLow = prod.derivedStockQty in 1..prod.reorderThresholdQty
                        val catName = categories.firstOrNull { it.localId == prod.categoryId }?.nameArabic ?: "-"
                        val brandName = brands.firstOrNull { it.localId == prod.brandId }?.name ?: "-"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProductId = prod.productId }
                                .testTag("product_card_${prod.productId}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isOut -> FinancialNegative.copy(alpha = 0.12f)
                                                    isLow -> FinancialWarning.copy(alpha = 0.14f)
                                                    else -> MekkaEmeraldGreen.copy(alpha = 0.12f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (prod.isSerialized) Icons.Default.Smartphone else Icons.Default.Inventory,
                                            contentDescription = prod.nameArabic,
                                            tint = when {
                                                isOut -> FinancialNegative
                                                isLow -> FinancialWarning
                                                else -> MekkaEmeraldGreen
                                            }
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = prod.nameArabic,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "SKU: ${prod.sku} • الفئة: $catName • الماركة: $brandName" +
                                                (if (prod.isSerialized) " • Serialized/IMEI" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "سعر البيع: ${Money.ofMinor(prod.defaultSellingPriceMinor).formatArabic()} • متوسط التكلفة: ${Money.ofMinor(prod.weightedAvgCostMinor).formatArabic()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = when {
                                            isOut -> FinancialNegative.copy(alpha = 0.12f)
                                            isLow -> FinancialWarning.copy(alpha = 0.14f)
                                            else -> FinancialPositive.copy(alpha = 0.12f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isOut -> "نفد (0)"
                                                isLow -> "منخفض (${prod.derivedStockQty})"
                                                else -> "متاح (${prod.derivedStockQty})"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isOut -> FinancialNegative
                                                isLow -> FinancialWarning
                                                else -> FinancialPositive
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (isInventoryCountMode) {
                                        OutlinedButton(
                                            onClick = { showAdjustmentDialogForProductId = prod.productId },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("product_quick_count_btn_${prod.productId}")
                                        ) {
                                            Text("تسوية الجرد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { selectedProductId = prod.productId },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("التفاصيل والحركات", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredProducts.size > pageSize) {
                        item {
                            OutlinedButton(
                                onClick = { pageSize += 50 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inventory_load_more_button")
                            ) {
                                Text("عرض المزيد من المنتجات (${pagedProducts.size} من ${filteredProducts.size})")
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. Product Creation Dialog (Explicit Category & Brand selection — NEVER firstOrNull())
    if (showCreateProductDialog) {
        ProductCreationModalDialog(
            categories = categories,
            brands = brands,
            onDismiss = { showCreateProductDialog = false },
            onAddCategory = { catName, onCreated ->
                viewModel.addCategory(catName, onCreated)
            },
            onAddBrand = { brandName, onCreated ->
                viewModel.addBrand(brandName, onCreated)
            },
            onCreateProduct = { name, sku, barcode, catId, brandId, sellingPriceStr, thresholdStr, isSerialized, imageDesc ->
                viewModel.addProduct(
                    nameArabic = name,
                    sku = sku,
                    categoryId = catId,
                    brandId = brandId,
                    isSerialized = isSerialized,
                    sellingPriceMajorStr = sellingPriceStr,
                    reorderThresholdStr = thresholdStr,
                    barcode = barcode,
                    imageOrDescription = imageDesc,
                    onSuccess = {
                        showCreateProductDialog = false
                    }
                )
            }
        )
    }

    // Barcode Scanner / Lookup Modal
    if (showBarcodeScannerDialog) {
        BarcodeOrImeiLookupDialog(
            titleArabic = "مسح أو إدخال الباركود / الكود",
            hintArabic = "امسح الباركود بالكاميرا/القارئ أو أدخله للبحث الفوري...",
            onDismiss = { showBarcodeScannerDialog = false },
            onScanned = { code ->
                showBarcodeScannerDialog = false
                val exactMatch = products.firstOrNull {
                    it.barcode?.equals(code, ignoreCase = true) == true ||
                        it.sku.equals(code, ignoreCase = true)
                }
                if (exactMatch != null) {
                    selectedProductId = exactMatch.productId
                } else {
                    searchQuery = code
                }
            }
        )
    }

    // Friendly Inventory Adjustment Dialog (Rule 9)
    val adjustTargetProduct = remember(products, showAdjustmentDialogForProductId) {
        showAdjustmentDialogForProductId?.let { id -> products.firstOrNull { it.productId == id } }
    }
    if (adjustTargetProduct != null) {
        FriendlyInventoryAdjustmentDialog(
            product = adjustTargetProduct,
            onDismiss = { showAdjustmentDialogForProductId = null },
            onSubmit = { movType, qtyStr, costStr, imei, friendlyReason ->
                viewModel.recordInventoryAdjustment(
                    productId = adjustTargetProduct.productId,
                    movementType = movType,
                    quantityStr = qtyStr,
                    unitCostMajorStr = costStr,
                    serialOrImei = imei,
                    reason = friendlyReason
                )
                showAdjustmentDialogForProductId = null
            }
        )
    }
}

/**
 * Rule 6: Product Creation Dialog
 * Fields: اسم المنتج, SKU, Barcode, الفئة, العلامة التجارية, سعر البيع, حد إعادة الطلب, Serialized / IMEI, صورة
 * Category and Brand MUST be explicitly selected — NEVER use firstOrNull() as an implicit business selection!
 */
@Composable
private fun ProductCreationModalDialog(
    categories: List<CategoryEntity>,
    brands: List<BrandEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (String, (CategoryEntity) -> Unit) -> Unit,
    onAddBrand: (String, (BrandEntity) -> Unit) -> Unit,
    onCreateProduct: (
        nameArabic: String,
        sku: String,
        barcode: String?,
        categoryId: String,
        brandId: String,
        sellingPriceStr: String,
        reorderThresholdStr: String,
        isSerialized: Boolean,
        imageDesc: String
    ) -> Unit
) {
    var nameArabic by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    // CRITICAL Rule 6: Explicit selection only — starts empty, NEVER categories.firstOrNull() or brands.firstOrNull()!
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    var selectedBrandId by rememberSaveable { mutableStateOf("") }
    var sellingPriceStr by rememberSaveable { mutableStateOf("") }
    var reorderThresholdStr by rememberSaveable { mutableStateOf("2") }
    var isSerialized by rememberSaveable { mutableStateOf(false) }
    var imageUriOrTag by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    var showQuickAddCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var showQuickAddBrand by rememberSaveable { mutableStateOf(false) }
    var newBrandName by rememberSaveable { mutableStateOf("") }

    // Zero-permission Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUriOrTag = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("+ تعريف منتج جديد بالمخزون", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameArabic,
                    onValueChange = {
                        nameArabic = it
                        validationError = null
                    },
                    label = { Text("اسم المنتج *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_product_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = {
                            sku = it
                            validationError = null
                        },
                        label = { Text("كود SKU *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_product_sku_input")
                    )
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode (الباركود)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_product_barcode_input")
                    )
                }

                // Explicit Category Selection (Rule 6)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الفئة * (اختر الفئة بشكل صريح):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showQuickAddCategory = !showQuickAddCategory }) {
                        Text("+ فئة جديدة")
                    }
                }
                if (showQuickAddCategory) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("اسم الفئة الجديدة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onAddCategory(newCategoryName) { created ->
                                        selectedCategoryId = created.localId
                                        newCategoryName = ""
                                        showQuickAddCategory = false
                                    }
                                }
                            }
                        ) {
                            Text("إضافة")
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it.localId }) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.localId,
                            onClick = {
                                selectedCategoryId = cat.localId
                                validationError = null
                            },
                            label = { Text(cat.nameArabic) },
                            modifier = Modifier.testTag("select_category_${cat.localId}")
                        )
                    }
                }

                // Explicit Brand Selection (Rule 6)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "العلامة التجارية * (اختر الماركة بشكل صريح):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showQuickAddBrand = !showQuickAddBrand }) {
                        Text("+ علامة جديدة")
                    }
                }
                if (showQuickAddBrand) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newBrandName,
                            onValueChange = { newBrandName = it },
                            label = { Text("اسم العلامة التجارية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newBrandName.isNotBlank()) {
                                    onAddBrand(newBrandName) { created ->
                                        selectedBrandId = created.localId
                                        newBrandName = ""
                                        showQuickAddBrand = false
                                    }
                                }
                            }
                        ) {
                            Text("إضافة")
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(brands, key = { it.localId }) { br ->
                        FilterChip(
                            selected = selectedBrandId == br.localId,
                            onClick = {
                                selectedBrandId = br.localId
                                validationError = null
                            },
                            label = { Text(br.name) },
                            modifier = Modifier.testTag("select_brand_${br.localId}")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sellingPriceStr,
                        onValueChange = {
                            sellingPriceStr = it
                            validationError = null
                        },
                        label = { Text("سعر البيع (ج.م) *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_product_price_input")
                    )
                    OutlinedTextField(
                        value = reorderThresholdStr,
                        onValueChange = { reorderThresholdStr = it },
                        label = { Text("حد إعادة الطلب") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_product_threshold_input")
                    )
                }

                // Serialized / IMEI Switch
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Serialized / IMEI", fontWeight = FontWeight.Bold)
                            Text(
                                text = "تفعيل تتبع كل قطعة برقم سيريال / IMEI مستقل عند الشراء والبيع",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSerialized,
                            onCheckedChange = { isSerialized = it },
                            modifier = Modifier.testTag("create_product_serialized_switch")
                        )
                    }
                }

                // Product Image (صورة)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("صورة المنتج", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (imageUriOrTag.isBlank()) "لم يتم إرفاق صورة بعد" else "تم إرفاق صورة للمنتج",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (imageUriOrTag.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else FinancialPositive
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }.onFailure {
                                imageUriOrTag = "product_photo_attached"
                            }
                        },
                        modifier = Modifier.testTag("create_product_pick_image_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "اختيار صورة", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار صورة")
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        nameArabic.isBlank() -> validationError = "يرجى إدخال اسم المنتج"
                        sku.isBlank() -> validationError = "يرجى إدخال كود المنتج (SKU)"
                        selectedCategoryId.isBlank() -> validationError = "يرجى اختيار الفئة بشكل صريح أولاً"
                        selectedBrandId.isBlank() -> validationError = "يرجى اختيار العلامة التجارية بشكل صريح أولاً"
                        sellingPriceStr.isBlank() -> validationError = "يرجى إدخال سعر البيع"
                        else -> {
                            onCreateProduct(
                                nameArabic,
                                sku,
                                barcode.takeIf { it.isNotBlank() },
                                selectedCategoryId,
                                selectedBrandId,
                                sellingPriceStr,
                                reorderThresholdStr,
                                isSerialized,
                                imageUriOrTag
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                modifier = Modifier.testTag("confirm_create_product_button")
            ) {
                Text("حفظ المنتج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Rule 7: Product Details Screen
 * Shows: Product, Current stock, Selling price, Average cost, Stock value, Reorder threshold,
 * IMEIs, Inventory movements, Sales history, Purchase history.
 * WAC and stock value come strictly from the domain/inventory projection.
 */
@Composable
private fun ProductDetailsProfileView(
    product: ProductInventoryProjection,
    categoryNameArabic: String,
    brandNameArabic: String,
    productImeis: List<ImeiRegistryProjection>,
    productMovements: List<InventoryMovementEntity>,
    productSaleItems: List<SaleItemEntity>,
    salesById: Map<String, SaleEntity>,
    productPurchaseItems: List<PurchaseItemEntity>,
    purchasesById: Map<String, PurchaseEntity>,
    suppliers: List<SupplierEntity> = emptyList(),
    priceHistory: List<PriceHistoryEntity> = emptyList(),
    demandForecast: ProductDemandForecastItem? = null,
    onConfirmPriceChange: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onOpenMovementAdjustment: () -> Unit,
    onSellProductInPos: () -> Unit,
    onPurchaseProduct: () -> Unit,
    onCopyImei: (String) -> Unit
) {
    BackHandler { onBack() }
    val clipboard = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf("حركات المخزون", "أرقام IMEI", "سجل المبيعات", "سجل المشتريات", "تحليلات الشراء والربحية")

    var showPriceChangeAssistantDialog by remember { mutableStateOf(false) }
    var proposedPriceStr by remember { mutableStateOf("") }
    var priceChangeReasonInput by remember { mutableStateOf("") }
    var priceChangeError by remember { mutableStateOf<String?>(null) }
    val priceAnalysis = remember(product, productPurchaseItems, priceHistory) {
        PriceAnalysisAndChangeEngine.analyzeProductPrice(product, productPurchaseItems, priceHistory)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_details_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("product_details_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للمخزون")
                            }
                            Column {
                                Text(
                                    text = product.nameArabic,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "SKU: ${product.sku} • الباركود: ${product.barcode ?: "-"} • الفئة: $categoryNameArabic • الماركة: $brandNameArabic",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onOpenMovementAdjustment,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier.testTag("product_record_movement_button")
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "تسوية مخزنية", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حركة / تسوية")
                        }
                    }

                    // Cross-Navigation Actions: Product -> Sale (POS) or Purchase (Part 2 Section 4)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSellProductInPos,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_detail_sell_pos_button")
                        ) {
                            Text("بيع الصنف (نقطة البيع)", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onPurchaseProduct,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_detail_purchase_button")
                        ) {
                            Text("توريد / شراء كمية", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider()

                    // Rule 7 Metrics: Current stock, Selling price, Average cost (WAC), Stock value, Reorder threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "الرصيد الحالي",
                            value = "${product.derivedStockQty} قطعة",
                            valueColor = if (product.derivedStockQty <= 0) FinancialNegative else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "سعر البيع",
                            value = Money.ofMinor(product.defaultSellingPriceMinor).formatArabic(),
                            valueColor = MekkaEmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "متوسط التكلفة (WAC)",
                            value = Money.ofMinor(product.weightedAvgCostMinor).formatArabic(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "قيمة المخزون الحالية",
                            value = Money.ofMinor(product.stockValueMinor).formatArabic(),
                            valueColor = MekkaGoldAccent,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "حد إعادة الطلب",
                            value = "${product.reorderThresholdQty} قطعة",
                            valueColor = FinancialWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabTitles.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("product_detail_tab_$idx")
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> { // حركات المخزون (Rule 9: Friendly terminology, no internal fields exposed)
                if (productMovements.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد حركات مخزنية لهذا المنتج بعد.",
                            subtitleArabic = "سجل فاتورة شراء أو حركة دخول للمخزون لتحديث الرصيد.",
                            actionButtonLabel = "تسجيل حركة مخزنية",
                            onActionClick = onOpenMovementAdjustment
                        )
                    }
                } else {
                    items(productMovements, key = { it.localId }) { mov ->
                        val friendlyCategory = FriendlyTerminologyMapper.friendlyInventoryMovementCategory(
                            movementType = mov.movementType,
                            reason = mov.reason
                        )
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
                                        text = friendlyCategory,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (mov.movementType.isInbound) FinancialPositive else FinancialNegative
                                    )
                                    Text(
                                        text = mov.reason.ifBlank { mov.movementType.arabicLabel },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = formatInventoryTimestamp(mov.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "الكمية: ${mov.quantity}",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (mov.movementType.isInbound) FinancialPositive else FinancialNegative
                                    )
                                    Text(
                                        text = "تكلفة الوحدة: ${Money.ofMinor(mov.unitCostMinor).formatArabic()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> { // IMEIs (Rule 7 & Rule 8)
                if (productImeis.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد أرقام IMEI مسجلة لهذا الصنف.",
                            subtitleArabic = if (product.isSerialized) {
                                "أضف أجهزة جديدة عبر فواتير المشتريات أو حركة دخول للمخزون."
                            } else {
                                "هذا الصنف غير مفعل له تتبع السيريال (غير Serialized)."
                            },
                            actionButtonLabel = "تسجيل دخول للمخزون",
                            onActionClick = onOpenMovementAdjustment
                        )
                    }
                } else {
                    items(productImeis, key = { it.serialEntity.localId }) { imeiProj ->
                        ImeiRegistryItemCard(
                            projection = imeiProj,
                            onCopyImei = { imei ->
                                clipboard.setText(AnnotatedString(imei))
                                onCopyImei(imei)
                            }
                        )
                    }
                }
            }

            2 -> { // سجل المبيعات (Rule 7)
                if (productSaleItems.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لم يتم بيع هذا المنتج بعد.",
                            subtitleArabic = "ستظهر هنا جميع فواتير المبيعات التي تضمنت هذا المنتج.",
                            actionButtonLabel = "عرض حركات المخزون",
                            onActionClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(productSaleItems, key = { it.localId }) { item ->
                        val sale = salesById[item.saleId]
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
                                Column {
                                    Text(
                                        text = "فاتورة بيع #${sale?.invoiceNumber ?: item.saleId}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!item.serialOrImei.isNullOrBlank()) {
                                        Text("IMEI: ${item.serialOrImei}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        text = "الكمية: ${item.quantity} • سعر الوحدة: ${Money.ofMinor(item.unitSellingPriceMinor).formatArabic()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Money.ofMinor(item.lineTotalMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }

            3 -> { // سجل المشتريات (Rule 7)
                if (productPurchaseItems.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد فواتير شراء مسجلة لهذا المنتج بعد.",
                            subtitleArabic = "ستظهر هنا جميع فواتير التوريد والشراء الخاصة بهذا المنتج.",
                            actionButtonLabel = "عرض حركات المخزون",
                            onActionClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(productPurchaseItems, key = { it.localId }) { item ->
                        val purchase = purchasesById[item.purchaseId]
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
                                Column {
                                    Text(
                                        text = "فاتورة شراء #${purchase?.invoiceNumber ?: item.purchaseId}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!item.serialOrImei.isNullOrBlank()) {
                                        Text("IMEI: ${item.serialOrImei}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        text = "الكمية: ${item.quantity} • تكلفة الوحدة: ${Money.ofMinor(item.unitCostMinor).formatArabic()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Money.ofMinor(item.lineTotalMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FinancialInfo
                                )
                            }
                        }
                    }
                }
            }

            4 -> { // تحليلات الشراء والربحية والتسعير الذكي (Requirements P2-C, P2-F & P2-G)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_price_analysis_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = null,
                                        tint = MekkaEmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "تحليل الأسعار وهوامش الربحية",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                MekkaStatusBadge(
                                    text = if (priceAnalysis.isMarginCompressed) "تراجع في الهامش" else "هامش مستقر",
                                    color = if (priceAnalysis.isMarginCompressed) MekkaAmberWarning else MekkaEmeraldGreen
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProfileMetricBox(
                                    label = "سعر البيع الحالي",
                                    value = Money.ofMinor(priceAnalysis.currentSellingPriceMinor).formatArabic(),
                                    valueColor = MekkaEmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileMetricBox(
                                    label = "متوسط التكلفة (WAC)",
                                    value = Money.ofMinor(priceAnalysis.currentCostWacMinor).formatArabic(),
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileMetricBox(
                                    label = "هامش الربح الإجمالي",
                                    value = "${"%.1f".format(priceAnalysis.currentMarginPercent)}%",
                                    valueColor = if (priceAnalysis.currentMarginPercent < 15.0) MekkaAmberWarning else MekkaEmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (priceAnalysis.isMarginCompressed) {
                                Surface(
                                    color = MekkaAmberWarning.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = MekkaAmberWarning,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = priceAnalysis.marginTrendArabic,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "حالة الهامش: ${priceAnalysis.marginTrendArabic}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "اتجاه تكلفة التوريد: ${priceAnalysis.purchaseCostTrendArabic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    proposedPriceStr = (product.defaultSellingPriceMinor / 100L).toString()
                                    priceChangeReasonInput = "تعديل سعر البيع لمواكبة التكاليف"
                                    priceChangeError = null
                                    showPriceChangeAssistantDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_price_change_assistant_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مساعد تعديل السعر (Price Change Assistant)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Inventory Demand Forecast Section (P2-C)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_demand_forecast_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = MekkaEmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "توقعات الطلب والمخزون المتبقي (Demand Forecast)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                MekkaStatusBadge(
                                    text = demandForecast?.confidenceLabelArabic ?: "تقديري",
                                    color = MekkaGoldAccent
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProfileMetricBox(
                                    label = "الرصيد المتاح",
                                    value = "${product.derivedStockQty} قطعة",
                                    valueColor = if (product.derivedStockQty <= 0) FinancialNegative else FinancialPositive,
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileMetricBox(
                                    label = "معدل البيع اليومي",
                                    value = "${"%.1f".format(demandForecast?.avgDailyVelocity ?: 0.0)} /يوم",
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                val daysText = if (demandForecast?.estimatedDaysRemaining != null) "≈ ${demandForecast.estimatedDaysRemaining} يوم" else "غير متاح"
                                val daysColor = if ((demandForecast?.estimatedDaysRemaining ?: 99) <= 3) MekkaCrimsonError else MekkaEmeraldGreen
                                ProfileMetricBox(
                                    label = "المخزون المتبقي يكفي",
                                    value = daysText,
                                    valueColor = daysColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            demandForecast?.explanationArabic?.let { exp ->
                                Text(
                                    text = exp,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if ((demandForecast?.suggestedReorderQty ?: 0) > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الكمية المقترحة لإعادة الطلب: ${demandForecast?.suggestedReorderQty} قطعة",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MekkaEmeraldGreen
                                    )
                                    Button(
                                        onClick = onPurchaseProduct,
                                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                        modifier = Modifier.testTag("demand_forecast_reorder_btn")
                                    ) {
                                        Text("طلب توريد")
                                    }
                                }
                            }
                        }
                    }
                }

                // Price History & Audit Log Trace (P2-G)
                item {
                    Text(
                        text = "سجل التعديلات السعرية (${priceHistory.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (priceHistory.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "السعر الحالي هو السعر المعتمد منذ إنشاء الصنف، ولم تطرأ عليه أي تعديلات سابقة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(priceHistory.sortedByDescending { it.changedAt }, key = { it.localId }) { ph ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "السعر السابق: ${Money.ofMinor(ph.oldPriceMinor).formatArabic()} ← الجديد: ${Money.ofMinor(ph.newPriceMinor).formatArabic()}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "السبب: ${ph.reasonArabic} • التوثيق: ${ph.changedByUserId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatInventoryTimestamp(ph.changedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Price Change Assistant Preview & Confirmation Dialog (Requirement P2-G)
    if (showPriceChangeAssistantDialog) {
        val proposedMinor = runCatching { Money.parseMajorString(proposedPriceStr).amountMinor }.getOrNull() ?: 0L
        val preview = remember(proposedMinor, product) {
            PriceAnalysisAndChangeEngine.previewPriceChange(product, proposedMinor)
        }

        AlertDialog(
            onDismissRequest = {
                showPriceChangeAssistantDialog = false
                priceChangeError = null
            },
            title = {
                Text("مساعد تعديل سعر الصنف (${product.nameArabic})", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (priceChangeError != null) {
                        Surface(
                            color = MekkaCrimsonError.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = priceChangeError.orEmpty(),
                                color = MekkaCrimsonError,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = proposedPriceStr,
                        onValueChange = {
                            proposedPriceStr = it
                            priceChangeError = null
                        },
                        label = { Text("سعر البيع الجديد المقترح (ج.م)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("proposed_price_input")
                    )

                    OutlinedTextField(
                        value = priceChangeReasonInput,
                        onValueChange = {
                            priceChangeReasonInput = it
                            priceChangeError = null
                        },
                        label = { Text("سبب تعديل السعر (للتوثيق في سجل التدقيق)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("price_change_reason_input")
                    )

                    // Live Preview Analysis (Requirement P2-G)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("السعر الحالي:", style = MaterialTheme.typography.bodySmall)
                                Text(Money.ofMinor(product.defaultSellingPriceMinor).formatArabic(), fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("السعر الجديد:", style = MaterialTheme.typography.bodySmall)
                                Text(Money.ofMinor(proposedMinor).formatArabic(), fontWeight = FontWeight.Bold, color = MekkaEmeraldGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الهامش الحالي ← الجديد:", style = MaterialTheme.typography.bodySmall)
                                Text("${"%.1f".format(preview.currentMarginPercent)}% ← ${"%.1f".format(preview.proposedMarginPercent)}%", fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الفارق التقديري بالوحدة:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = Money.ofMinor(preview.estimatedProfitDiffPerUnitMinor).formatArabic(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (preview.estimatedProfitDiffPerUnitMinor >= 0L) MekkaEmeraldGreen else MekkaCrimsonError
                                )
                            }
                            preview.warningArabic?.let { warn ->
                                Surface(
                                    color = MekkaCrimsonError.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = warn,
                                        color = MekkaCrimsonError,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (proposedMinor <= 0L) {
                            priceChangeError = "أدخل سعر بيع صحيحاً أكبر من صفر"
                            return@Button
                        }
                        val reason = priceChangeReasonInput.trim().ifBlank { "تعديل السعر عبر المساعد الذكي" }
                        onConfirmPriceChange(proposedMinor, reason)
                        showPriceChangeAssistantDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("confirm_price_change_assistant_button")
                ) {
                    Text("تأكيد وتوثيق بالتدقيق", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPriceChangeAssistantDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * Rule 8: Dedicated IMEI Management Screen
 * Supports: Search IMEI, Scan IMEI/barcode, Copy IMEI, Product association, Sale status,
 * Purchase status, Customer association, Invoice association.
 */
@Composable
private fun ImeiManagementScreenView(
    imeiProjections: List<ImeiRegistryProjection>,
    initialSearchQuery: String = "",
    onBack: () -> Unit,
    onFeedback: (String, Boolean) -> Unit
) {
    BackHandler { onBack() }
    val clipboard = LocalClipboardManager.current
    var imeiSearchQuery by rememberSaveable(initialSearchQuery) { mutableStateOf(initialSearchQuery) }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, IN_STOCK, SOLD
    var showScanDialog by rememberSaveable { mutableStateOf(false) }

    val filteredImeis = remember(imeiProjections, imeiSearchQuery, statusFilter) {
        val q = ImeiValidationEngine.normalizeImeiOrSerial(imeiSearchQuery).lowercase()
        imeiProjections.filter { proj ->
            val matchesQuery = q.isEmpty() ||
                proj.serialEntity.serialOrImei.lowercase().contains(q) ||
                proj.productNameArabic.lowercase().contains(q) ||
                (proj.customerNameArabic?.lowercase()?.contains(q) == true) ||
                (proj.saleInvoiceNumber?.lowercase()?.contains(q) == true) ||
                (proj.purchaseInvoiceNumber?.lowercase()?.contains(q) == true)
            val matchesStatus = when (statusFilter) {
                "IN_STOCK" -> proj.serialEntity.status.name == "IN_STOCK"
                "SOLD" -> proj.serialEntity.status.name == "SOLD"
                else -> true
            }
            matchesQuery && matchesStatus
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("imei_management_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("imei_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للمخزون")
                            }
                            Column {
                                Text(
                                    text = "إدارة وتتبع أرقام IMEI / السيريال",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "إجمالي السيريالات المسجلة: ${imeiProjections.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showScanDialog = true },
                            modifier = Modifier.testTag("imei_scan_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح IMEI", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح IMEI")
                        }
                    }

                    OutlinedTextField(
                        value = imeiSearchQuery,
                        onValueChange = { imeiSearchQuery = it },
                        label = { Text("بحث برقم IMEI أو اسم الجهاز أو العميل أو الفاتورة...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث IMEI") },
                        trailingIcon = {
                            if (imeiSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { imeiSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("imei_search_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = statusFilter == "ALL",
                            onClick = { statusFilter = "ALL" },
                            label = { Text("الكل (${imeiProjections.size})") }
                        )
                        FilterChip(
                            selected = statusFilter == "IN_STOCK",
                            onClick = { statusFilter = "IN_STOCK" },
                            label = { Text("متاح بالمخزن") }
                        )
                        FilterChip(
                            selected = statusFilter == "SOLD",
                            onClick = { statusFilter = "SOLD" },
                            label = { Text("مباع") }
                        )
                    }
                }
            }
        }

        if (filteredImeis.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = "لا توجد أرقام IMEI مطابقة.",
                    subtitleArabic = "تُسجل أرقام IMEI الحقيقية عند توريد الأجهزة السيريال في فواتير الشراء أو حركات الدخول للمخزون.",
                    actionButtonLabel = "مسح باركود / IMEI",
                    onActionClick = { showScanDialog = true }
                )
            }
        } else {
            items(filteredImeis, key = { it.serialEntity.localId }) { proj ->
                ImeiRegistryItemCard(
                    projection = proj,
                    onCopyImei = { imei ->
                        clipboard.setText(AnnotatedString(imei))
                        onFeedback("تم نسخ رقم IMEI ($imei) للحافظة", false)
                    }
                )
            }
        }
    }

    if (showScanDialog) {
        BarcodeOrImeiLookupDialog(
            titleArabic = "مسح أو التحقق من رقم IMEI",
            hintArabic = "أدخل أو امسح رقم IMEI للتحقق من صحته والبحث عنه...",
            onDismiss = { showScanDialog = false },
            onScanned = { rawCode ->
                val validation = ImeiValidationEngine.validateImeiOrSerial(rawCode)
                if (validation is AppResult.Error) {
                    onFeedback(validation.message, true)
                } else if (validation is AppResult.Success) {
                    imeiSearchQuery = validation.data
                    showScanDialog = false
                }
            }
        )
    }
}

@Composable
private fun ImeiRegistryItemCard(
    projection: ImeiRegistryProjection,
    onCopyImei: (String) -> Unit
) {
    val sr = projection.serialEntity
    val isAvailable = sr.status.name == "IN_STOCK"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("imei_card_${sr.serialOrImei}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = "IMEI",
                        tint = if (isAvailable) FinancialPositive else FinancialInfo
                    )
                    Text(
                        text = "IMEI: ${sr.serialOrImei}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isAvailable) FinancialPositive.copy(alpha = 0.12f) else FinancialInfo.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = projection.saleStatusArabic,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isAvailable) FinancialPositive else FinancialInfo,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCopyImei(sr.serialOrImei) },
                        modifier = Modifier.testTag("copy_imei_${sr.serialOrImei}")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ IMEI", modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "المنتج المرتبط: ${projection.productNameArabic} (SKU: ${projection.productSku})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "حالة الشراء: ${projection.purchaseStatusArabic}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!projection.customerNameArabic.isNullOrBlank()) {
                Text(
                    text = "العميل المرتبط: ${projection.customerNameArabic} (${projection.customerPhone ?: "-"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!projection.saleInvoiceNumber.isNullOrBlank() || !projection.purchaseInvoiceNumber.isNullOrBlank()) {
                Text(
                    text = "الفواتير المرتبطة: " +
                        (projection.purchaseInvoiceNumber?.let { "شراء #$it " } ?: "") +
                        (projection.saleInvoiceNumber?.let { "• بيع #$it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MekkaEmeraldGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BarcodeOrImeiLookupDialog(
    titleArabic: String,
    hintArabic: String,
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit
) {
    var codeInput by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleArabic, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hintArabic, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("الباركود / IMEI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (codeInput.isNotBlank()) {
                        onScanned(codeInput.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("تأكيد وبحث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * Rule 9: Friendly Inventory Movements Dialog
 * Allows choosing friendly movement categories:
 * - دخول للمخزون
 * - خروج من المخزون
 * - تسوية
 * - تالف
 * - نقل
 */
@Composable
private fun FriendlyInventoryAdjustmentDialog(
    product: ProductInventoryProjection,
    onDismiss: () -> Unit,
    onSubmit: (
        movementType: InventoryMovementType,
        quantityStr: String,
        unitCostMajorStr: String,
        serialOrImei: String?,
        friendlyReason: String
    ) -> Unit
) {
    val friendlyOptions: List<Pair<String, InventoryMovementType>> = listOf(
        "دخول للمخزون" to InventoryMovementType.ADJUSTMENT_IN,
        "خروج من المخزون" to InventoryMovementType.ADJUSTMENT_OUT,
        "تسوية" to InventoryMovementType.ADJUSTMENT_IN,
        "تالف" to InventoryMovementType.DAMAGE,
        "نقل" to InventoryMovementType.TRANSFER_OUT
    )
    var selectedOptionIndex by rememberSaveable { mutableIntStateOf(0) }
    var quantityStr by rememberSaveable { mutableStateOf("1") }
    var unitCostStr by rememberSaveable {
        mutableStateOf(
            if (product.weightedAvgCostMinor > 0L) (product.weightedAvgCostMinor / 100.0).toString()
            else (product.defaultSellingPriceMinor / 100.0).toString()
        )
    }
    var serialOrImei by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    val (selectedLabel, selectedMovementType) = friendlyOptions[selectedOptionIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("تسجيل حركة مخزون — ${product.nameArabic}", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نوع الحركة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(friendlyOptions.indices.toList()) { idx ->
                        val (label, _) = friendlyOptions[idx]
                        FilterChip(
                            selected = selectedOptionIndex == idx,
                            onClick = { selectedOptionIndex = idx },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("الكمية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedMovementType.isInbound) {
                    OutlinedTextField(
                        value = unitCostStr,
                        onValueChange = { unitCostStr = it },
                        label = { Text("تكلفة الوحدة (ج.م)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (product.isSerialized) {
                    OutlinedTextField(
                        value = serialOrImei,
                        onValueChange = { serialOrImei = it },
                        label = { Text("رقم IMEI / السيريال") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("سبب الحركة / البيان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val reason = if (notes.isBlank()) selectedLabel else "$selectedLabel — ${notes.trim()}"
                    onSubmit(
                        selectedMovementType,
                        quantityStr,
                        unitCostStr,
                        serialOrImei.takeIf { it.isNotBlank() },
                        reason
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("حفظ الحركة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

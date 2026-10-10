package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.CustomerEntity
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.UserEntity
import com.example.financial.MaintenanceFinancialEngine
import com.example.financial.MaintenanceFinancialSummary
import com.example.financial.Money
import com.example.ui.components.ThermalReceiptPreviewDialog
import com.example.ui.components.VoiceDictationIconButton
import com.example.ui.components.launchPhoneDialer
import com.example.ui.components.launchWhatsAppMessage
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

private fun formatMaintenanceTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

enum class MaintenanceListFilter(val arabicLabel: String) {
    ALL("الكل"),
    IN_REPAIR("قيد العمل"),
    READY("جاهز"),
    UNPAID("غير مسدد بالكامل"),
    DELIVERED("تم التسليم")
}

// ============================================================================
// 18, 19, 20. MAINTENANCE — WORKFLOW REDESIGN, TICKET CREATION & TICKET DETAIL
// Lifecycle: استلام الجهاز ↓ فحص ↓ تقدير تكلفة ↓ موافقة العميل ↓ قيد الإصلاح ↓
//            قطع الغيار ↓ اختبار ↓ جاهز ↓ إخطار العميل ↓ تحصيل ↓ تسليم
// ============================================================================

@Composable
fun MaintenanceScreen(viewModel: MekkaPhoneViewModel) {
    val orders by viewModel.maintenanceOrders.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val allParts by viewModel.allMaintenanceParts.collectAsStateWithLifecycle()
    val financialSummaries by viewModel.maintenanceFinancialSummaries.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()
    val pendingTicketRef by viewModel.pendingSelectedMaintenanceTicket.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(MaintenanceListFilter.ALL) }
    var pageSize by rememberSaveable { mutableIntStateOf(40) }

    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTicketDialog by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(pendingTicketRef, orders) {
        val targetRef = pendingTicketRef
        if (!targetRef.isNullOrBlank()) {
            val matched = orders.firstOrNull {
                it.localId == targetRef || it.ticketNumber.equals(targetRef, ignoreCase = true)
            }
            if (matched != null) {
                selectedOrderId = matched.localId
                viewModel.consumeSelectedMaintenanceTicket()
            }
        }
    }

    val selectedOrder = remember(orders, selectedOrderId) {
        orders.firstOrNull { it.localId == selectedOrderId }
    }

    if (selectedOrder != null) {
        val summary = financialSummaries[selectedOrder.localId]
            ?: MaintenanceFinancialEngine.computeTicketFinancials(selectedOrder, emptyList(), emptyList())
        val ticketParts = remember(allParts, selectedOrder.localId) {
            allParts.filter { it.maintenanceOrderId == selectedOrder.localId }
        }
        val customer = remember(customers, selectedOrder.customerId) {
            customers.firstOrNull { it.localId == selectedOrder.customerId }
        }
        val technician = remember(users, selectedOrder.assignedTechnicianId) {
            users.firstOrNull { it.localId == selectedOrder.assignedTechnicianId }
        }

        MaintenanceTicketDetailView(
            order = selectedOrder,
            summary = summary,
            parts = ticketParts,
            allProducts = products,
            customer = customer,
            technician = technician,
            availableSpareParts = products.filter { !it.isSerialized && it.derivedStockQty > 0 },
            isProcessing = isProcessing,
            onBack = { selectedOrderId = null },
            onUpdateStatus = { newStatus ->
                viewModel.updateMaintenanceOrderStatus(
                    orderId = selectedOrder.localId,
                    newStatus = newStatus,
                    finalChargeMinor = 0L
                )
            },
            onNotifyCustomer = { context ->
                val phone = customer?.phone ?: selectedOrder.customerPhoneSnapshot
                val msg = "مرحباً ${selectedOrder.customerNameSnapshot}، نود إخطاركم بأن جهازكم (${selectedOrder.deviceBrand} ${selectedOrder.deviceModel} - تذكرة #${selectedOrder.ticketNumber}) أصبح جاهزاً للاستلام في مركز صيانة مكة فون. المتبقي للسداد: ${Money.ofMinor(summary.remainingAmountMinor).formatArabic()}."
                launchWhatsAppMessage(context, phone, msg)
                viewModel.notifyMaintenanceCustomer(selectedOrder.localId)
            },
            onCollectPayment = { amountStr, notesStr, onSuccess, onError ->
                viewModel.collectMaintenanceTicketPayment(
                    orderId = selectedOrder.localId,
                    amountMajorStr = amountStr,
                    notes = notesStr,
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onDeliverTicket = {
                viewModel.updateMaintenanceOrderStatus(
                    orderId = selectedOrder.localId,
                    newStatus = MaintenanceStatus.DELIVERED,
                    finalChargeMinor = 0L
                )
            },
            onConsumePart = { partProductId, qtyStr, unitChargeStr ->
                viewModel.consumeMaintenancePart(
                    orderId = selectedOrder.localId,
                    productId = partProductId,
                    quantityStr = qtyStr,
                    unitChargeMajorStr = unitChargeStr
                )
            }
        )
        return
    }

    val filteredOrders = remember(orders, searchQuery, selectedFilter, financialSummaries) {
        val q = searchQuery.trim().lowercase()
        orders.filter { ord ->
            val summary = financialSummaries[ord.localId]
            val matchesFilter = when (selectedFilter) {
                MaintenanceListFilter.ALL -> true
                MaintenanceListFilter.IN_REPAIR -> ord.status.isPendingWork && ord.status.stageOrder < 8
                MaintenanceListFilter.READY -> ord.status == MaintenanceStatus.READY_FOR_PICKUP ||
                    ord.status == MaintenanceStatus.CUSTOMER_NOTIFIED ||
                    ord.status == MaintenanceStatus.PAYMENT_COLLECTED
                MaintenanceListFilter.UNPAID -> (summary?.remainingAmountMinor ?: 0L) > 0L && ord.status != MaintenanceStatus.CANCELLED
                MaintenanceListFilter.DELIVERED -> ord.status == MaintenanceStatus.DELIVERED
            }
            val matchesSearch = q.isEmpty() ||
                ord.ticketNumber.lowercase().contains(q) ||
                ord.customerNameSnapshot.lowercase().contains(q) ||
                ord.customerPhoneSnapshot.lowercase().contains(q) ||
                ord.deviceBrand.lowercase().contains(q) ||
                ord.deviceModel.lowercase().contains(q) ||
                ord.deviceImeiOrSerial.lowercase().contains(q) ||
                ord.faultDescription.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    val pagedOrders = remember(filteredOrders, pageSize) {
        filteredOrders.take(pageSize)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("maintenance_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MaintenanceManagementCenterCard(viewModel = viewModel)
        }

        // Header & Top Actions
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
                                text = "مركز الصيانة والضمان",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "إجمالي التذاكر: ${orders.size} • قيد العمل: ${orders.count { it.status.isPendingWork }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showCreateTicketDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier.testTag("open_create_maintenance_ticket_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "استلام جهاز")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ استلام جهاز", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Rule 18: Visual Lifecycle Pipeline Banner
                    MaintenanceWorkflowOverviewStrip()

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            pageSize = 40
                        },
                        label = { Text("بحث برقم التذكرة، اسم العميل، الهاتف، الموديل، أو IMEI...") },
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
                            .fillMaxWidth()
                            .testTag("maintenance_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MaintenanceListFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = {
                                    selectedFilter = filter
                                    pageSize = 40
                                },
                                label = { Text(filter.arabicLabel, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.16f),
                                    selectedLabelColor = MekkaEmeraldGreen
                                ),
                                modifier = Modifier.testTag("maint_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        if (pagedOrders.isEmpty()) {
            item {
                EmptyStateGuidanceCard(
                    titleArabic = if (orders.isEmpty()) "لا توجد تذاكر صيانة مسجلة بعد." else "لا توجد تذاكر مطابقة لبحثك.",
                    subtitleArabic = if (orders.isEmpty()) {
                        "افتح أول تذكرة استلام جهاز صيانة لتوثيق العطل والتكلفة ومتابعة مراحل الإصلاح."
                    } else {
                        "جرّب تغيير فلتر الحالة أو البحث برقم تذكرة آخر."
                    },
                    actionButtonLabel = "+ استلام جهاز صيانة",
                    onActionClick = { showCreateTicketDialog = true }
                )
            }
        } else {
            items(pagedOrders, key = { it.localId }) { order ->
                val summary = financialSummaries[order.localId]
                    ?: MaintenanceFinancialEngine.computeTicketFinancials(order, emptyList(), emptyList())
                MaintenanceTicketRowCard(
                    order = order,
                    summary = summary,
                    onClick = { selectedOrderId = order.localId }
                )
            }

            if (filteredOrders.size > pagedOrders.size) {
                item {
                    OutlinedButton(
                        onClick = { pageSize += 40 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("load_more_maintenance_button")
                    ) {
                        Text("عرض المزيد (${pagedOrders.size} من ${filteredOrders.size})")
                    }
                }
            }
        }
    }

    if (showCreateTicketDialog) {
        CreateMaintenanceTicketDialog(
            customers = customers,
            users = users,
            products = products,
            onDismiss = { showCreateTicketDialog = false },
            onQuickAddCustomer = { name, phone ->
                viewModel.addCustomer(name, phone, "", "", "")
            },
            onCreateTicket = { custId, brand, model, imei, fault, estCost, deposit, techId, custNotes, photoUri, initialStatus, initialPartId ->
                viewModel.createMaintenanceOrderFull(
                    customerId = custId,
                    deviceBrand = brand,
                    deviceModel = model,
                    deviceImei = imei,
                    faultDescription = fault,
                    estimatedCostMajorStr = estCost,
                    depositMajorStr = deposit,
                    assignedTechnicianId = techId,
                    customerNotes = custNotes,
                    photoAttachmentNotes = photoUri,
                    initialStatus = initialStatus,
                    initialPartProductId = initialPartId
                )
                showCreateTicketDialog = false
            }
        )
    }
}

/**
 * Rule 18: Visual representation of the 11-step Maintenance Workflow:
 * استلام الجهاز ↓ فحص ↓ تقدير تكلفة ↓ موافقة العميل ↓ قيد الإصلاح ↓ قطع الغيار ↓ اختبار ↓ جاهز ↓ إخطار العميل ↓ تحصيل ↓ تسليم
 */
@Composable
fun MaintenanceWorkflowOverviewStrip(
    currentStatus: MaintenanceStatus? = null
) {
    val stages = MaintenanceStatus.workflowPipeline
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "مسار دورة الصيانة المعتمدة (11 مرحلة):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(stages.size) { idx ->
                    val stage = stages[idx]
                    val isCurrent = currentStatus == stage
                    val isCompleted = currentStatus != null && stage.stageOrder < currentStatus.stageOrder
                    val bgColor = when {
                        isCurrent -> MekkaEmeraldGreen
                        isCompleted -> MekkaEmeraldGreen.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val txtColor = when {
                        isCurrent -> Color.White
                        isCompleted -> MekkaEmeraldGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${stage.stageOrder}. ${stage.arabicLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = txtColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (idx < stages.lastIndex) {
                            Text(
                                text = " ← ",
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

@Composable
private fun MaintenanceTicketRowCard(
    order: MaintenanceOrderEntity,
    summary: MaintenanceFinancialSummary,
    onClick: () -> Unit
) {
    val statusColor = when {
        order.status == MaintenanceStatus.DELIVERED -> FinancialPositive
        order.status == MaintenanceStatus.CANCELLED -> FinancialNegative
        order.status.stageOrder >= 8 -> MekkaGoldAccent
        else -> FinancialInfo
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("maintenance_card_${order.ticketNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        text = "تذكرة #${order.ticketNumber} • ${order.deviceBrand} ${order.deviceModel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "العميل: ${order.customerNameSnapshot} (${order.customerPhoneSnapshot.ifBlank { "بدون هاتف" }})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${order.status.stageOrder}/11 • ${order.status.arabicLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = "العطل: ${order.faultDescription}",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التكلفة: ${Money.ofMinor(summary.effectiveTotalChargeMinor).formatArabic()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "المسدد: ${Money.ofMinor(summary.paidAmountMinor).formatArabic()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinancialPositive,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "المتبقي: ${Money.ofMinor(summary.remainingAmountMinor).formatArabic()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.remainingAmountMinor > 0L) FinancialNegative else FinancialPositive,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ============================================================================
// 19. MAINTENANCE TICKET CREATION DIALOG
// Required: Customer (explicit), Device, IMEI/Serial, Fault, Photos, Deposit,
// Estimated cost, Parts, Technician, Status, Customer notes
// ============================================================================

@Composable
private fun CreateMaintenanceTicketDialog(
    customers: List<CustomerEntity>,
    users: List<UserEntity>,
    products: List<ProductInventoryProjection>,
    onDismiss: () -> Unit,
    onQuickAddCustomer: (String, String) -> Unit,
    onCreateTicket: (
        customerId: String,
        deviceBrand: String,
        deviceModel: String,
        deviceImei: String,
        faultDescription: String,
        estimatedCostMajorStr: String,
        depositMajorStr: String,
        assignedTechnicianUserId: String?,
        customerNotes: String,
        intakePhotoUri: String,
        initialStatus: MaintenanceStatus,
        initialPartId: String?
    ) -> Unit
) {
    // Explicit customer selection — NEVER use first customer automatically! (Rule 19)
    var selectedCustomerId by rememberSaveable { mutableStateOf("") }
    var customerSearch by rememberSaveable { mutableStateOf("") }
    var showInlineNewCustomer by rememberSaveable { mutableStateOf(false) }
    var newCustomerName by rememberSaveable { mutableStateOf("") }
    var newCustomerPhone by rememberSaveable { mutableStateOf("") }

    var deviceBrand by rememberSaveable { mutableStateOf("") }
    var deviceModel by rememberSaveable { mutableStateOf("") }
    var deviceImei by rememberSaveable { mutableStateOf("") }
    var faultDescription by rememberSaveable { mutableStateOf("") }
    var estimatedCostStr by rememberSaveable { mutableStateOf("") }
    var depositAmountStr by rememberSaveable { mutableStateOf("0") }
    var selectedTechnicianId by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf(MaintenanceStatus.RECEIVED) }
    var selectedPartProductId by rememberSaveable { mutableStateOf("") }
    var customerNotes by rememberSaveable { mutableStateOf("") }
    var intakePhotoUri by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            intakePhotoUri = uri.toString()
        }
    }

    val filteredCustomers = remember(customers, customerSearch) {
        val q = customerSearch.trim().lowercase()
        if (q.isEmpty()) customers.take(15)
        else customers.filter {
            it.fullName.lowercase().contains(q) || it.phone.lowercase().contains(q)
        }.take(15)
    }

    val spareParts = remember(products) {
        products.filter { !it.isSerialized && it.derivedStockQty > 0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("+ فتح تذكرة صيانة واستلام جهاز", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Explicit Customer Selection (Rule 19)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. العميل صاحب الجهاز (اختيار صريح إلزامي) *:", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showInlineNewCustomer = !showInlineNewCustomer }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ عميل جديد")
                    }
                }

                if (showInlineNewCustomer) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = newCustomerName,
                                onValueChange = { newCustomerName = it },
                                label = { Text("اسم العميل الجديد *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newCustomerPhone,
                                onValueChange = { newCustomerPhone = it },
                                label = { Text("رقم الهاتف *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (newCustomerName.isNotBlank() && newCustomerPhone.isNotBlank()) {
                                        onQuickAddCustomer(newCustomerName, newCustomerPhone)
                                        newCustomerName = ""
                                        newCustomerPhone = ""
                                        showInlineNewCustomer = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ العميل")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customerSearch,
                    onValueChange = { customerSearch = it },
                    label = { Text("ابحث عن العميل بالاسم أو الهاتف...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maint_customer_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredCustomers, key = { it.localId }) { cust ->
                        val isSelected = selectedCustomerId == cust.localId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCustomerId = cust.localId
                                validationError = null
                            },
                            label = { Text("${cust.fullName} (${cust.phone})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.2f),
                                selectedLabelColor = MekkaEmeraldGreen
                            ),
                            modifier = Modifier.testTag("maint_select_customer_${cust.localId}")
                        )
                    }
                }

                // 2. Device & IMEI/Serial
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deviceBrand,
                        onValueChange = {
                            deviceBrand = it
                            validationError = null
                        },
                        label = { Text("ماركة الجهاز *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("maint_brand_input")
                    )
                    OutlinedTextField(
                        value = deviceModel,
                        onValueChange = {
                            deviceModel = it
                            validationError = null
                        },
                        label = { Text("موديل الجهاز *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("maint_model_input")
                    )
                }

                OutlinedTextField(
                    value = deviceImei,
                    onValueChange = { deviceImei = it },
                    label = { Text("رقم IMEI / السيريال (إن وجد)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maint_imei_input")
                )

                // 3. Fault Description
                OutlinedTextField(
                    value = faultDescription,
                    onValueChange = {
                        faultDescription = it
                        validationError = null
                    },
                    label = { Text("وصف العطل بالتفصيل *") },
                    trailingIcon = {
                        VoiceDictationIconButton(
                            promptArabic = "تحدث بوصف عطل الجهاز...",
                            onResultText = { spoken ->
                                faultDescription = if (faultDescription.isBlank()) spoken else "$faultDescription - $spoken"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maint_fault_input")
                )

                // 4. Estimated Cost & Deposit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = estimatedCostStr,
                        onValueChange = {
                            estimatedCostStr = it
                            validationError = null
                        },
                        label = { Text("التكلفة المتوقعة (ج.م) *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("maint_cost_input")
                    )
                    OutlinedTextField(
                        value = depositAmountStr,
                        onValueChange = { depositAmountStr = it },
                        label = { Text("العربون المسدد (ج.م)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("maint_deposit_input")
                    )
                }

                // 5. Technician Selection
                Text("الفني المسؤول عن الإصلاح:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users, key = { it.localId }) { u ->
                        FilterChip(
                            selected = selectedTechnicianId == u.localId,
                            onClick = { selectedTechnicianId = u.localId },
                            label = { Text("${u.fullNameArabic} (${u.roleCode})") }
                        )
                    }
                }

                // 6. Initial Status Selection
                Text("حالة التذكرة عند الاستلام:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MaintenanceStatus.workflowPipeline.take(5)) { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.arabicLabel) }
                        )
                    }
                }

                // 7. Spare Part Association (Optional at intake)
                if (spareParts.isNotEmpty()) {
                    Text("قطعة الغيار المطلوبة (اختياري):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(spareParts.take(10), key = { it.productId }) { part ->
                            FilterChip(
                                selected = selectedPartProductId == part.productId,
                                onClick = {
                                    selectedPartProductId = if (selectedPartProductId == part.productId) "" else part.productId
                                },
                                label = { Text("${part.nameArabic} (متاح: ${part.derivedStockQty})") }
                            )
                        }
                    }
                }

                // 8. Customer Notes
                OutlinedTextField(
                    value = customerNotes,
                    onValueChange = { customerNotes = it },
                    label = { Text("ملاحظات العميل / حالة الجهاز الظاهرية (خدوش، متعلقات...)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maint_customer_notes_input")
                )

                // 9. Photos (Zero-permission Android Photo Picker)
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_maintenance_photo_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (intakePhotoUri.isNotBlank()) "تم إرفاق صورة حالة الجهاز ✓"
                        else "إرفاق صورة الجهاز عند الاستلام (Photo Picker)"
                    )
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
                        selectedCustomerId.isBlank() -> validationError = "يرجى اختيار العميل بشكل صريح أولاً"
                        deviceBrand.isBlank() || deviceModel.isBlank() -> validationError = "يرجى إدخال ماركة وموديل الجهاز"
                        faultDescription.isBlank() -> validationError = "يرجى كتابة وصف العطل"
                        else -> {
                            val selectedPartName = spareParts.firstOrNull { it.productId == selectedPartProductId }?.nameArabic
                            val combinedNotes = buildString {
                                if (customerNotes.isNotBlank()) append(customerNotes.trim())
                                if (!selectedPartName.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" | ")
                                    append("قطعة غيار مقترحة: $selectedPartName")
                                }
                            }
                            onCreateTicket(
                                selectedCustomerId,
                                deviceBrand,
                                deviceModel,
                                deviceImei,
                                faultDescription,
                                estimatedCostStr.ifBlank { "0" },
                                depositAmountStr.ifBlank { "0" },
                                selectedTechnicianId.takeIf { it.isNotBlank() },
                                combinedNotes,
                                intakePhotoUri,
                                selectedStatus,
                                selectedPartProductId.takeIf { it.isNotBlank() }
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                modifier = Modifier.testTag("save_maintenance_button")
            ) {
                Text("حفظ وفتح التذكرة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// ============================================================================
// 20. MAINTENANCE DETAIL SCREEN
// Shows: Ticket number, Customer, Device, Fault, Current status, Technician,
// Estimated cost, Paid, Remaining, Parts, Timeline, Notes
// Actions depend on current status:
// - If "قيد الإصلاح" (or earlier): «تحديث الحالة»
// - If "جاهز": «إخطار العميل»
// - If unpaid: «تحصيل»
// - If ready and paid: «تسليم الجهاز»
// ============================================================================

@Composable
private fun MaintenanceTicketDetailView(
    order: MaintenanceOrderEntity,
    summary: MaintenanceFinancialSummary,
    parts: List<MaintenancePartEntity>,
    allProducts: List<ProductInventoryProjection>,
    customer: CustomerEntity?,
    technician: UserEntity?,
    availableSpareParts: List<ProductInventoryProjection>,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onUpdateStatus: (MaintenanceStatus) -> Unit,
    onNotifyCustomer: (android.content.Context) -> Unit,
    onCollectPayment: (String, String, () -> Unit, (String) -> Unit) -> Unit,
    onDeliverTicket: () -> Unit,
    onConsumePart: (String, String, String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var showUpdateStatusDialog by rememberSaveable { mutableStateOf(false) }
    var showCollectPaymentDialog by rememberSaveable { mutableStateOf(false) }
    var showConsumePartDialog by rememberSaveable { mutableStateOf(false) }
    var showThermalReceipt by rememberSaveable { mutableStateOf(false) }

    val customerPhone = customer?.phone ?: order.customerPhoneSnapshot

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("maintenance_ticket_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Ticket Header & Status Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("maint_detail_back_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                            }
                            Column {
                                Text(
                                    text = "تذكرة صيانة #${order.ticketNumber}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "تاريخ الاستلام: ${formatMaintenanceTimestamp(order.receivedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = MekkaEmeraldGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = order.status.arabicLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MekkaEmeraldGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 11-Stage Visual Pipeline highlighting current status
                    MaintenanceWorkflowOverviewStrip(currentStatus = order.status)

                    HorizontalDivider()

                    // Ticket Core Details Grid
                    DetailRowItem("العميل:", "${order.customerNameSnapshot} (${customerPhone.ifBlank { "بدون رقم" }})")
                    DetailRowItem("الجهاز:", "${order.deviceBrand} ${order.deviceModel}")
                    DetailRowItem("IMEI / السيريال:", order.deviceImeiOrSerial.ifBlank { "غير مسجل" })
                    DetailRowItem("وصف العطل:", order.faultDescription)
                    DetailRowItem(
                        "الفني المسؤول:",
                        technician?.fullNameArabic ?: order.assignedTechnicianId ?: "فريق الصيانة العام"
                    )
                    if (order.diagnosticNotes.isNotBlank()) {
                        DetailRowItem("ملاحظات العميل والفحص:", order.diagnosticNotes)
                    }

                    // Financial Summary Boxes (Estimated Cost, Paid, Remaining)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "التكلفة الإجمالية",
                            value = Money.ofMinor(summary.effectiveTotalChargeMinor).formatArabic(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "المدفوع",
                            value = Money.ofMinor(summary.paidAmountMinor).formatArabic(),
                            valueColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "المتبقي للسداد",
                            value = Money.ofMinor(summary.remainingAmountMinor).formatArabic(),
                            valueColor = if (summary.remainingAmountMinor > 0L) FinancialNegative else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "قطع الغيار",
                            value = Money.ofMinor(summary.partsCustomerChargeMinor).formatArabic(),
                            valueColor = FinancialInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    // Rule 20: Status-Dependent Dynamic Actions!
                    Text(
                        text = "الإجراءات المتاحة حسب الحالة الحالية (${order.status.arabicLabel}):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. If in repair / diagnosing / waiting approval / testing -> «تحديث الحالة»
                        if (order.status != MaintenanceStatus.DELIVERED && order.status != MaintenanceStatus.CANCELLED) {
                            Button(
                                onClick = { showUpdateStatusDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = FinancialInfo),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("maint_action_update_status")
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحديث الحالة", fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2. If "جاهز" (READY_FOR_PICKUP) -> «إخطار العميل»
                        if (order.status == MaintenanceStatus.READY_FOR_PICKUP) {
                            Button(
                                onClick = { onNotifyCustomer(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("maint_action_notify_customer")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إخطار العميل", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 3. If unpaid (remainingAmountMinor > 0) -> «تحصيل»
                        if (summary.remainingAmountMinor > 0L && order.status != MaintenanceStatus.CANCELLED) {
                            Button(
                                onClick = { showCollectPaymentDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("maint_action_collect_payment")
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحصيل (${Money.ofMinor(summary.remainingAmountMinor).formatArabic()})", fontWeight = FontWeight.Bold)
                            }
                        }

                        // 4. If ready (stage >= 8) and paid (isFullyPaid) and not yet delivered -> «تسليم الجهاز»
                        val isReadyStage = order.status == MaintenanceStatus.READY_FOR_PICKUP ||
                            order.status == MaintenanceStatus.CUSTOMER_NOTIFIED ||
                            order.status == MaintenanceStatus.PAYMENT_COLLECTED
                        if (isReadyStage && summary.isFullyPaid) {
                            Button(
                                onClick = onDeliverTicket,
                                colors = ButtonDefaults.buttonColors(containerColor = FinancialPositive),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("maint_action_deliver_device")
                            ) {
                                Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تسليم الجهاز", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // Secondary communication & receipt actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchPhoneDialer(context, customerPhone) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اتصال")
                        }
                        OutlinedButton(
                            onClick = {
                                val msg = "مرحباً ${order.customerNameSnapshot}، بخصوص تذكرة صيانة #${order.ticketNumber} (${order.deviceBrand} ${order.deviceModel}): الحالة الحالية (${order.status.arabicLabel})."
                                launchWhatsAppMessage(context, customerPhone, msg)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                        OutlinedButton(
                            onClick = { showThermalReceipt = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إيصال التذكرة")
                        }
                    }
                }
            }
        }

        // 2. Parts Section (قطع الغيار)
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
                            text = "قطع الغيار المصروفة للتذكرة (${parts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (order.status != MaintenanceStatus.DELIVERED && order.status != MaintenanceStatus.CANCELLED) {
                            OutlinedButton(
                                onClick = { showConsumePartDialog = true },
                                modifier = Modifier.testTag("open_consume_part_dialog_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("صرف قطعة غيار")
                            }
                        }
                    }

                    if (parts.isEmpty()) {
                        Text(
                            text = "لم يتم صرف قطع غيار من المخزن لهذه التذكرة حتى الآن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        parts.forEach { part ->
                            val partName = allProducts.firstOrNull { it.productId == part.productId }?.nameArabic ?: part.productId
                            val lineChargeMinor = part.unitChargeMinor * part.quantity.toLong()
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                                        Text(partName, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "الكمية: ${part.quantity} • التكلفة المخزنية: ${Money.ofMinor(part.unitCostMinor).formatArabic()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = Money.ofMinor(lineChargeMinor).formatArabic(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MekkaEmeraldGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Timeline Section (الجدول الزمني لمراحل التذكرة)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الجدول الزمني لتتبع حالة الجهاز (Timeline)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    MaintenanceStatus.workflowPipeline.forEach { stage ->
                        val isPassedOrCurrent = stage.stageOrder <= order.status.stageOrder &&
                            order.status != MaintenanceStatus.CANCELLED
                        val isCurrent = stage == order.status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCurrent -> MekkaGoldAccent
                                            isPassedOrCurrent -> MekkaEmeraldGreen
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stage.stageOrder.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPassedOrCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stage.arabicLabel,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isPassedOrCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (stage == MaintenanceStatus.RECEIVED) {
                                    Text(
                                        text = "تم الاستلام: ${formatMaintenanceTimestamp(order.receivedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (stage == MaintenanceStatus.DELIVERED && order.deliveredAt != null) {
                                    Text(
                                        text = "تم التسليم: ${formatMaintenanceTimestamp(order.deliveredAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FinancialPositive
                                    )
                                }
                            }
                            if (isCurrent) {
                                Text(
                                    text = "المرحلة الحالية",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaGoldAccent
                                )
                            } else if (isPassedOrCurrent) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "مكتمل",
                                    tint = MekkaEmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Status Update Dialog
    if (showUpdateStatusDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateStatusDialog = false },
            title = { Text("تحديث حالة التذكرة #${order.ticketNumber}", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("اختر المرحلة الجديدة في مسار الصيانة:")
                    MaintenanceStatus.workflowPipeline.forEach { st ->
                        val isCurrent = st == order.status
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateStatus(st)
                                    showUpdateStatusDialog = false
                                },
                            color = if (isCurrent) MekkaEmeraldGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${st.stageOrder}. ${st.arabicLabel}",
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                                if (isCurrent) {
                                    Text("الحالية", color = MekkaEmeraldGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdateStatusDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Payment Collection Dialog («تحصيل»)
    if (showCollectPaymentDialog) {
        var amountStr by rememberSaveable {
            mutableStateOf(Money.ofMinor(summary.remainingAmountMinor).toDecimalString())
        }
        var notesStr by rememberSaveable { mutableStateOf("") }
        var errText by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showCollectPaymentDialog = false },
            title = { Text("تحصيل مستحقات تذكرة #${order.ticketNumber}", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إجمالي المطلوب: ${Money.ofMinor(summary.effectiveTotalChargeMinor).formatArabic()}")
                    Text("المدفوع سابقاً: ${Money.ofMinor(summary.paidAmountMinor).formatArabic()}")
                    Text(
                        text = "المتبقي الحالي: ${Money.ofMinor(summary.remainingAmountMinor).formatArabic()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = FinancialNegative
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            errText = null
                        },
                        label = { Text("المبلغ المحصل الآن (ج.م) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("maint_collect_amount_input")
                    )
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("ملاحظات التحصيل (اختياري)") },
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
                        onCollectPayment(
                            amountStr,
                            notesStr,
                            { showCollectPaymentDialog = false },
                            { err -> errText = err }
                        )
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier.testTag("maint_confirm_collect_button")
                ) {
                    Text(if (isProcessing) "جاري التحصيل..." else "تأكيد التحصيل وترحيل القيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCollectPaymentDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Consume Spare Part Dialog («صرف قطعة غيار»)
    if (showConsumePartDialog) {
        var selectedPartId by rememberSaveable { mutableStateOf("") }
        var qtyStr by rememberSaveable { mutableStateOf("1") }
        var unitPriceStr by rememberSaveable { mutableStateOf("") }
        var partErr by rememberSaveable { mutableStateOf<String?>(null) }

        val selectedPart = availableSpareParts.firstOrNull { it.productId == selectedPartId }

        AlertDialog(
            onDismissRequest = { showConsumePartDialog = false },
            title = { Text("صرف قطعة غيار للتذكرة #${order.ticketNumber}", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (availableSpareParts.isEmpty()) {
                        Text(
                            text = "لا توجد قطع غيار غير مسلسلة متوفرة في المخزون حالياً.",
                            color = FinancialWarning
                        )
                    } else {
                        Text("اختر قطعة الغيار من المخزون:", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableSpareParts, key = { it.productId }) { part ->
                                FilterChip(
                                    selected = selectedPartId == part.productId,
                                    onClick = {
                                        selectedPartId = part.productId
                                        unitPriceStr = Money.ofMinor(part.defaultSellingPriceMinor).toDecimalString()
                                        partErr = null
                                    },
                                    label = { Text("${part.nameArabic} (متاح: ${part.derivedStockQty})") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = qtyStr,
                            onValueChange = { qtyStr = it },
                            label = { Text("الكمية المصروفة *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = unitPriceStr,
                            onValueChange = { unitPriceStr = it },
                            label = { Text("سعر احتساب القطعة على العميل (ج.م) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!partErr.isNullOrBlank()) {
                        Text(partErr!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (availableSpareParts.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (selectedPart == null) {
                                partErr = "يرجى اختيار قطعة الغيار أولاً"
                            } else {
                                onConsumePart(selectedPart.productId, qtyStr, unitPriceStr)
                                showConsumePartDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                    ) {
                        Text("صرف وإضافة للتذكرة")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsumePartDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showThermalReceipt) {
        ThermalReceiptPreviewDialog(
            shopNameArabic = "مكة فون — مركز الصيانة المتخصص",
            shopPhone = customerPhone,
            documentTitleArabic = "إيصال استلام وضمان صيانة",
            referenceNumber = order.ticketNumber,
            customerName = order.customerNameSnapshot,
            customerPhone = customerPhone,
            detailsLines = listOf(
                "الجهاز والموديل" to "${order.deviceBrand} ${order.deviceModel}",
                "السيريال / IMEI" to order.deviceImeiOrSerial.ifBlank { "-" },
                "تشخيص العطل" to order.faultDescription,
                "الحالة الحالية" to order.status.arabicLabel
            ),
            totalMinor = summary.effectiveTotalChargeMinor,
            paidMinor = summary.paidAmountMinor,
            remainingMinor = summary.remainingAmountMinor,
            footerNoteArabic = "المركز غير مسؤول عن الأجهزة التي لا يتم استلامها خلال 30 يوماً من إشعار الجاهزية.",
            onDismiss = { showThermalReceipt = false },
            context = context
        )
    }
}

@Composable
private fun DetailRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f)
        )
    }
}

package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.entities.CustomerEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.SupplierEntity
import com.example.financial.CustomerFinancialProfile
import com.example.financial.Money
import com.example.financial.PartyAccountStatementEngine
import com.example.financial.SupplierFinancialProfile
import com.example.navigation.MekkaDestination
import com.example.repository.MekkaPhoneRepository
import com.example.ui.components.LocalizedFormValidator
import com.example.ui.components.MekkaProcessingButton
import com.example.ui.components.MekkaProfessionalConfirmationDialog
import com.example.ui.components.UnsavedChangesDialog
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

private fun formatPartyDateTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))

// ============================================================================
// 1, 2, 3. CUSTOMERS — LIST + DEDICATED PROFILE + COLLECTION WORKFLOW
// ============================================================================

enum class CustomerBalanceFilter(val arabicLabel: String) {
    ALL("الكل"),
    HAS_DUES("عليهم مستحقات"),
    NO_DUES("بدون مستحقات")
}

@Composable
fun CustomersScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit = {}
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerProfiles by viewModel.customerProfiles.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val refunds by viewModel.refunds.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()
    val pendingCustomerId by viewModel.pendingSelectedCustomerId.collectAsStateWithLifecycle()
    val customer360 by viewModel.selectedCustomer360.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CustomerBalanceFilter.ALL) }
    var pageSize by rememberSaveable { mutableIntStateOf(50) }

    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddCustomerDialog by rememberSaveable { mutableStateOf(false) }
    var collectionCustomerId by rememberSaveable { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(pendingCustomerId, customers) {
        val targetId = pendingCustomerId
        if (!targetId.isNullOrBlank() && customers.any { it.localId == targetId }) {
            selectedCustomerId = targetId
            viewModel.consumeSelectedCustomerProfile()
        }
    }

    val selectedCustomer = remember(customers, selectedCustomerId) {
        selectedCustomerId?.let { id -> customers.firstOrNull { it.localId == id } }
    }

    // Dedicated Customer Profile Screen (Rule 2 + V4 Part 2 Customer 360)
    if (selectedCustomer != null) {
        androidx.compose.runtime.LaunchedEffect(selectedCustomer.localId) {
            viewModel.loadCustomer360(selectedCustomer.localId)
        }
        val profile = customerProfiles[selectedCustomer.localId] ?: CustomerFinancialProfile(
            customerId = selectedCustomer.localId,
            currentBalanceMinor = 0L,
            totalPurchasesMinor = 0L,
            totalPaymentsMinor = 0L,
            totalRefundsMinor = 0L,
            outstandingAmountMinor = 0L,
            statementLines = emptyList()
        )
        CustomerDetailProfileView(
            customer = selectedCustomer,
            profile = profile,
            customer360 = customer360?.takeIf { it.customer.localId == selectedCustomer.localId },
            customerSales = sales.filter { it.customerId == selectedCustomer.localId },
            customerRefunds = refunds.filter { ref ->
                sales.any { it.localId == ref.originalSaleId && it.customerId == selectedCustomer.localId }
            },
            customerPayments = ledgerTransactions.filter {
                it.customerId == selectedCustomer.localId &&
                    it.status == LedgerTransactionStatus.POSTED &&
                    (it.relatedEntityType == "CUSTOMER_COLLECTION" || it.relatedEntityType == "SALE")
            },
            onBack = {
                viewModel.clearCustomer360()
                selectedCustomerId = null
            },
            onStartSaleForCustomer = {
                viewModel.selectPosRegisteredCustomer(selectedCustomer.localId)
                onNavigateToRoute(MekkaDestination.SALES.route)
            },
            onOpenCollection = {
                collectionCustomerId = selectedCustomer.localId
            },
            onArchiveCustomer = {
                viewModel.archiveCustomer(selectedCustomer.localId)
                selectedCustomerId = null
            },
            onOpenTimelineEvent = { event ->
                if (event.eventType == com.example.intelligence.Customer360EventType.MAINTENANCE) {
                    viewModel.navigateToMaintenanceTicket(event.referenceNumber)
                }
                onNavigateToRoute(event.routeTarget)
            }
        )
    } else {
        val filteredCustomers = remember(customers, customerProfiles, searchQuery, selectedFilter) {
            val q = Money.normalizeDigits(searchQuery).trim().lowercase()
            customers.filter { customer ->
                val balanceMinor = customerProfiles[customer.localId]?.outstandingAmountMinor ?: 0L
                val matchesSearch = q.isEmpty() ||
                    customer.fullName.lowercase().contains(q) ||
                    Money.normalizeDigits(customer.phone).lowercase().contains(q) ||
                    (customer.nationalId?.lowercase()?.contains(q) == true)
                val matchesFilter = when (selectedFilter) {
                    CustomerBalanceFilter.ALL -> true
                    CustomerBalanceFilter.HAS_DUES -> balanceMinor > 0L
                    CustomerBalanceFilter.NO_DUES -> balanceMinor <= 0L
                }
                matchesSearch && matchesFilter
            }
        }

        val pagedCustomers = remember(filteredCustomers, pageSize) {
            filteredCustomers.take(pageSize)
        }

        val totalReceivablesMinor = remember(customerProfiles) {
            customerProfiles.values.sumOf { it.outstandingAmountMinor }
        }
        val customersWithDuesCount = remember(customerProfiles) {
            customerProfiles.values.count { it.outstandingAmountMinor > 0L }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("customers_screen_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header: «العملاء» + Actions (Rule 1)
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
                                    text = "العملاء",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "إجمالي العملاء: ${customers.size} • عليهم مستحقات: $customersWithDuesCount (${Money.ofMinor(totalReceivablesMinor).formatArabic()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddCustomerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                modifier = Modifier.testTag("add_customer_top_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ إضافة عميل", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                pageSize = 50
                            },
                            label = { Text("بحث باسم العميل أو رقم الهاتف...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_search_input")
                        )

                        // Filter Chips: الكل / عليهم مستحقات / بدون مستحقات
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CustomerBalanceFilter.entries) { filter ->
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
                                    modifier = Modifier.testTag("customer_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Empty State (Rule 27)
            if (filteredCustomers.isEmpty()) {
                item {
                    EmptyStateGuidanceCard(
                        titleArabic = if (customers.isEmpty()) {
                            "لا يوجد عملاء مسجلون بعد."
                        } else {
                            "لا توجد نتائج مطابقة لبحثك أو الفلتر المحدد."
                        },
                        subtitleArabic = if (customers.isEmpty()) {
                            "أضف أول عميل للبدء في تسجيل المبيعات الآجلة وكشوف الحسابات والتحصيلات."
                        } else {
                            "جرّب تغيير كلمات البحث أو اختيار فلتر «الكل» أو أضف عميلاً جديداً."
                        },
                        actionButtonLabel = "+ إضافة عميل",
                        onActionClick = { showAddCustomerDialog = true },
                        testTag = "customers_empty_state_card"
                    )
                }
            } else {
                items(pagedCustomers, key = { it.localId }) { customer ->
                    val profile = customerProfiles[customer.localId]
                    val outstandingMinor = profile?.outstandingAmountMinor ?: 0L
                    val totalPurchasesMinor = profile?.totalPurchasesMinor ?: 0L

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomerId = customer.localId }
                            .testTag("customer_card_${customer.localId}"),
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (outstandingMinor > 0L) FinancialNegative.copy(alpha = 0.12f)
                                            else MekkaEmeraldGreen.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = customer.fullName,
                                        tint = if (outstandingMinor > 0L) FinancialNegative else MekkaEmeraldGreen
                                    )
                                }
                                Column {
                                    Text(
                                        text = customer.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "هاتف: ${customer.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "إجمالي المشتريات: ${Money.ofMinor(totalPurchasesMinor).formatArabic()}",
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
                                    color = if (outstandingMinor > 0L) {
                                        FinancialNegative.copy(alpha = 0.12f)
                                    } else {
                                        FinancialPositive.copy(alpha = 0.12f)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (outstandingMinor > 0L) {
                                            "مستحق: ${Money.ofMinor(outstandingMinor).formatArabic()}"
                                        } else {
                                            "خالص الذمة (0.00 ج.م)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (outstandingMinor > 0L) FinancialNegative else FinancialPositive,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (outstandingMinor > 0L) {
                                        OutlinedButton(
                                            onClick = { collectionCustomerId = customer.localId },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("quick_collect_${customer.localId}")
                                        ) {
                                            Text("تحصيل", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    TextButton(
                                        onClick = { selectedCustomerId = customer.localId },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("الملف الكامل", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Pagination Load More button (Rule 1 & Rule 28)
                if (filteredCustomers.size > pageSize) {
                    item {
                        OutlinedButton(
                            onClick = { pageSize += 50 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customers_load_more_button")
                        ) {
                            Text("عرض المزيد من العملاء (${pagedCustomers.size} من ${filteredCustomers.size})")
                        }
                    }
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerModalDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { name, phone, nationalId, address, notes ->
                viewModel.addCustomer(name, phone, nationalId, address, notes)
                showAddCustomerDialog = false
            }
        )
    }

    // Guided Customer Collection Workflow Dialog (Rule 3)
    val collectionTargetCustomer = remember(customers, collectionCustomerId) {
        collectionCustomerId?.let { id -> customers.firstOrNull { it.localId == id } }
    }
    if (collectionTargetCustomer != null) {
        val currentBal = customerProfiles[collectionTargetCustomer.localId]?.outstandingAmountMinor ?: 0L
        CustomerCollectionWizardDialog(
            customer = collectionTargetCustomer,
            currentBalanceMinor = currentBal,
            wallets = wallets,
            isProcessing = isProcessing,
            onDismiss = { collectionCustomerId = null },
            onExecuteCollection = { amountStr, accountType, accountId, allowOverpayment, notes, onSuccess, onError ->
                viewModel.executeCustomerCollectionSafe(
                    customerId = collectionTargetCustomer.localId,
                    amountMajorStr = amountStr,
                    paymentAccountType = accountType,
                    paymentAccountId = accountId,
                    allowOverpayment = allowOverpayment,
                    notes = notes,
                    onSuccess = { onSuccess() },
                    onError = onError
                )
            }
        )
    }
}

@Composable
private fun CustomerDetailProfileView(
    customer: CustomerEntity,
    profile: CustomerFinancialProfile,
    customer360: com.example.intelligence.Customer360Snapshot?,
    customerSales: List<com.example.database.entities.SaleEntity>,
    customerRefunds: List<com.example.database.entities.RefundEntity>,
    customerPayments: List<com.example.database.entities.TransactionEntity>,
    onBack: () -> Unit,
    onStartSaleForCustomer: () -> Unit,
    onOpenCollection: () -> Unit,
    onArchiveCustomer: () -> Unit,
    onOpenTimelineEvent: (com.example.intelligence.Customer360TimelineEvent) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var selectedSectionTab by rememberSaveable { mutableIntStateOf(0) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    val sectionTitles = listOf("المبيعات", "المدفوعات", "كشف الحساب", "المرتجعات", "الخط الزمني 360", "الصيانة والمحافظ")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("customer_detail_profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Bar & Profile Summary Card (Rule 2)
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
                                modifier = Modifier.testTag("customer_profile_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع لقائمة العملاء")
                            }
                            Column {
                                Text(
                                    text = customer.fullName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "هاتف: ${customer.phone}" +
                                        (if (customer.address.isNotBlank()) " • ${customer.address}" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = { showArchiveConfirmDialog = true },
                            modifier = Modifier.testTag("customer_archive_button")
                        ) {
                            Text("أرشفة العميل", color = FinancialNegative, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showArchiveConfirmDialog) {
                        MekkaProfessionalConfirmationDialog(
                            titleArabic = "تأكيد أرشفة ملف العميل",
                            whatWillHappenArabic = "سيتم نقل العميل «${customer.fullName}» إلى الأرشيف وإخفاؤه من قوائم البيع النشطة مع الاحتفاظ الكامل بسجلاته المالية.",
                            whatDataWillChangeArabic = "العميل: ${customer.fullName}\nالرصيد المستحق الحالي: ${Money.ofMinor(profile.outstandingAmountMinor).formatArabic()}",
                            canBeUndoneArabic = "لا يتم حذف أي قيد محاسبي أو فاتورة سابقة من دفتر الأستاذ.",
                            whatUserMustDoArabic = "تأكد من عدم وجود تحصيل جارٍ قبل تأكيد الأرشفة.",
                            confirmButtonLabelArabic = "تأكيد الأرشفة",
                            cancelButtonLabelArabic = "إلغاء",
                            isDestructive = true,
                            onConfirm = {
                                showArchiveConfirmDialog = false
                                onArchiveCustomer()
                            },
                            onDismiss = { showArchiveConfirmDialog = false }
                        )
                    }

                    HorizontalDivider()

                    // Financial Summary Metrics (from Financial/Domain layer — Rule 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "الرصيد الحالي",
                            value = Money.ofMinor(profile.currentBalanceMinor).formatArabic(),
                            valueColor = if (profile.currentBalanceMinor > 0L) FinancialNegative else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "إجمالي المشتريات",
                            value = Money.ofMinor(profile.totalPurchasesMinor).formatArabic(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "المدفوعات",
                            value = Money.ofMinor(profile.totalPaymentsMinor).formatArabic(),
                            valueColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "المبلغ المستحق",
                            value = Money.ofMinor(profile.outstandingAmountMinor).formatArabic(),
                            valueColor = if (profile.outstandingAmountMinor > 0L) FinancialNegative else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (customer360 != null) {
                        Customer360SummaryBanner(snapshot = customer360)
                    }

                    // Primary Actions: بيع / تحصيل / اتصال / WhatsApp (Rule 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartSaleForCustomer,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_action_sale")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = "بيع", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("بيع", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onOpenCollection,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaGoldAccent),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_action_collect")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = "تحصيل", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحصيل", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                    context.startActivity(dialIntent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_action_call")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اتصال")
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val cleanPhone = Money.normalizeDigits(customer.phone).replace("+", "").trim()
                                    val intlPhone = if (cleanPhone.startsWith("0")) "2$cleanPhone" else cleanPhone
                                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intlPhone"))
                                    context.startActivity(waIntent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_action_whatsapp")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                    }
                }
            }
        }

        // Section Tabs: المبيعات / المدفوعات / كشف الحساب / المرتجعات (Rule 2)
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedSectionTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                sectionTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSectionTab == index,
                        onClick = { selectedSectionTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedSectionTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("customer_profile_tab_$index")
                    )
                }
            }
        }

        when (selectedSectionTab) {
            0 -> { // المبيعات
                if (customerSales.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد مبيعات مسجلة لهذا العميل بعد.",
                            subtitleArabic = "ابدأ فاتورة بيع جديدة للعميل من نقطة البيع.",
                            actionButtonLabel = "بدء عملية بيع للعميل",
                            onActionClick = onStartSaleForCustomer
                        )
                    }
                } else {
                    items(customerSales, key = { it.localId }) { sale ->
                        val unpaid = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
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
                                    Text("فاتورة #${sale.invoiceNumber}", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = formatPartyDateTime(sale.saleTimestamp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()} • المتبقي: ${Money.ofMinor(unpaid).formatArabic()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = Money.ofMinor(sale.totalAmountMinor).formatArabic(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (sale.status == "REFUNDED") FinancialNegative else MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }

            1 -> { // المدفوعات
                if (customerPayments.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد مدفوعات أو تحصيلات مسجلة بعد.",
                            subtitleArabic = "يمكنك تسجيل دفعة تحصيل جديدة لتخفيض الرصيد المستحق على العميل.",
                            actionButtonLabel = "تحصيل دفعة الآن",
                            onActionClick = onOpenCollection
                        )
                    }
                } else {
                    items(customerPayments, key = { it.localId }) { tx ->
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
                                    Text(tx.descriptionArabic, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "مرجع: ${tx.referenceNumber} • ${formatPartyDateTime(tx.transactionTimestamp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Money.ofMinor(tx.amountMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FinancialPositive
                                )
                            }
                        }
                    }
                }
            }

            2 -> { // كشف الحساب
                if (profile.statementLines.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "كشف الحساب فارغ حالياً.",
                            subtitleArabic = "ستظهر جميع الفواتير والتحصيلات والمرتجعات والرصيد التراكمي هنا تلقائياً.",
                            actionButtonLabel = "بدء عملية بيع",
                            onActionClick = onStartSaleForCustomer
                        )
                    }
                } else {
                    items(profile.statementLines, key = { it.id }) { line ->
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
                                        text = "${line.typeLabelArabic} (#${line.referenceNumber})",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatPartyDateTime(line.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = line.descriptionArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "مدين: ${Money.ofMinor(line.debitMinor).formatArabic()} | دائن: ${Money.ofMinor(line.creditMinor).formatArabic()}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "الرصيد: ${Money.ofMinor(line.runningBalanceMinor).formatArabic()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (line.runningBalanceMinor > 0L) FinancialNegative else FinancialPositive
                                    )
                                }
                            }
                        }
                    }
                }
            }

            3 -> { // المرتجعات
                if (customerRefunds.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد مرتجعات مسجلة لهذا العميل.",
                            subtitleArabic = "تظهر هنا أي فواتير مبيعات تم استرجاعها للعميل.",
                            actionButtonLabel = "عرض المبيعات",
                            onActionClick = { selectedSectionTab = 0 }
                        )
                    }
                } else {
                    items(customerRefunds, key = { it.localId }) { ref ->
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
                                    Text("مرتجع #${ref.refundNumber}", fontWeight = FontWeight.Bold)
                                    Text(ref.reason, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatPartyDateTime(ref.refundTimestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Money.ofMinor(ref.refundAmountMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FinancialNegative
                                )
                            }
                        }
                    }
                }
            }

            4 -> { // الخط الزمني 360 (Customer Timeline)
                val timeline = customer360?.timeline.orEmpty()
                if (timeline.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "الخط الزمني للعميل فارغ حالياً.",
                            subtitleArabic = "تظهر هنا كافة المبيعات، المدفوعات، الصيانة، المرتجعات، وحركات المحافظ مرتبة زمنياً.",
                            actionButtonLabel = "بدء عملية بيع",
                            onActionClick = onStartSaleForCustomer
                        )
                    }
                } else {
                    items(timeline, key = { it.id }) { ev ->
                        Timeline360InteractiveCard(
                            event = ev,
                            onOpenEventSource = onOpenTimelineEvent
                        )
                    }
                }
            }

            5 -> { // الصيانة والمحافظ وسجل الرقابة
                val maintList = customer360?.maintenanceTickets.orEmpty()
                val walletList = customer360?.walletOperations.orEmpty()
                val auditList = customer360?.auditHistory.orEmpty()
                if (maintList.isEmpty() && walletList.isEmpty() && auditList.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد تذاكر صيانة أو حركات محافظ مرتبطة بهذا العميل.",
                            subtitleArabic = "أي تذكرة صيانة أو تحويل محفظة برقم هاتف العميل يظهر هنا تلقائياً.",
                            actionButtonLabel = "عرض الخط الزمني 360",
                            onActionClick = { selectedSectionTab = 4 }
                        )
                    }
                } else {
                    items(maintList, key = { "c360_m_${it.localId}" }) { m ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("صيانة #${m.ticketNumber} — ${m.deviceBrand} ${m.deviceModel}", fontWeight = FontWeight.Bold)
                                    Text("العطل: ${m.faultDescription} • الحالة: ${m.status.arabicLabel}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Money.ofMinor(m.finalTotalMinor).formatArabic(), fontWeight = FontWeight.ExtraBold, color = FinancialInfo)
                            }
                        }
                    }
                    items(walletList, key = { "c360_w_${it.localId}" }) { w ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("عملية محفظة (${w.transferKind})", fontWeight = FontWeight.Bold)
                                    Text("الهاتف: ${w.customerPhoneOrAccount} • مرجع: ${w.transferReference}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Money.ofMinor(w.principalAmountMinor).formatArabic(), fontWeight = FontWeight.ExtraBold, color = MekkaEmeraldGreen)
                            }
                        }
                    }
                    items(auditList, key = { "c360_a_${it.localId}" }) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("سجل رقابة: ${log.actionType}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("${log.summaryArabic} • ${formatPartyDateTime(log.timestamp)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rule 3: Customer Collection Guided Workflow
 * Customer ↓ Current balance ↓ Amount ↓ Payment method ↓ Review ↓ Confirm ↓ Success
 * Shows Previous balance, Amount paid, New balance, and prevents overpayment unless explicitly supported.
 */
@Composable
private fun CustomerCollectionWizardDialog(
    customer: CustomerEntity,
    currentBalanceMinor: Long,
    wallets: List<com.example.database.dao.WalletDerivedProjection>,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onExecuteCollection: (
        amountStr: String,
        accountType: FinancialAccountType,
        accountId: String,
        allowOverpayment: Boolean,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    // Steps: 0 = Input (Amount + Method), 1 = Review, 2 = Success
    var step by rememberSaveable { mutableIntStateOf(0) }
    var amountStr by rememberSaveable {
        mutableStateOf(if (currentBalanceMinor > 0L) (currentBalanceMinor / 100.0).toString() else "")
    }
    var paymentMethod by rememberSaveable { mutableStateOf("CASH_DRAWER") } // CASH_DRAWER or WALLET
    var selectedWalletId by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val previousBalanceSnapshot by remember { mutableStateOf(currentBalanceMinor) }

    val parsedAmountMinor = remember(amountStr) {
        runCatching { Money.parseMajorString(amountStr).amountMinor }.getOrNull()
    }
    val preview = remember(previousBalanceSnapshot, parsedAmountMinor) {
        PartyAccountStatementEngine.evaluateCustomerCollection(
            currentBalanceMinor = previousBalanceSnapshot,
            amountPaidMinor = parsedAmountMinor,
            allowOverpayment = false
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = when (step) {
                    0 -> "تحصيل مستحقات من العميل"
                    1 -> "مراجعة وتأكيد التحصيل"
                    else -> "تم التحصيل بنجاح"
                },
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Customer & Current Balance banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("العميل: ${customer.fullName} (${customer.phone})", fontWeight = FontWeight.Bold)
                        Text(
                            text = "الرصيد السابق: ${Money.ofMinor(preview.previousBalanceMinor).formatArabic()}",
                            color = if (preview.previousBalanceMinor > 0L) FinancialNegative else FinancialPositive,
                            fontWeight = FontWeight.Bold
                        )
                        if (parsedAmountMinor != null && parsedAmountMinor > 0L) {
                            Text(
                                text = "المبلغ المدفوع: ${Money.ofMinor(preview.amountPaidMinor).formatArabic()}",
                                color = MekkaEmeraldGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الرصيد الجديد بعد التحصيل: ${Money.ofMinor(preview.newBalanceMinor.coerceAtLeast(0L)).formatArabic()}",
                                color = FinancialInfo,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = {
                                amountStr = it
                                errorText = null
                            },
                            label = { Text("المبلغ المحصل (ج.م)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collection_amount_input")
                        )

                        Text("طريقة الدفع:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paymentMethod == "CASH_DRAWER",
                                onClick = {
                                    paymentMethod = "CASH_DRAWER"
                                    errorText = null
                                },
                                label = { Text("نقداً بالخزينة") }
                            )
                            FilterChip(
                                selected = paymentMethod == "WALLET",
                                onClick = {
                                    paymentMethod = "WALLET"
                                    errorText = null
                                },
                                label = { Text("محفظة إلكترونية") }
                            )
                        }

                        if (paymentMethod == "WALLET") {
                            Text("اختر المحفظة المستلمة:", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(wallets, key = { it.walletId }) { w ->
                                    FilterChip(
                                        selected = selectedWalletId == w.walletId,
                                        onClick = {
                                            selectedWalletId = w.walletId
                                            errorText = null
                                        },
                                        label = { Text(w.walletNameArabic) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات التحصيل (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val validationMsg = errorText ?: preview.validationErrorArabic
                        if (!validationMsg.isNullOrBlank()) {
                            Text(
                                text = validationMsg,
                                color = FinancialNegative,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    1 -> {
                        val methodLabel = if (paymentMethod == "WALLET") {
                            val wName = wallets.firstOrNull { it.walletId == selectedWalletId }?.walletNameArabic ?: "محفظة"
                            "محفظة إلكترونية ($wName)"
                        } else {
                            "نقداً بالخزينة الرئيسية"
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MekkaEmeraldGreen.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("مراجعة دفعة العميل قبل الاعتماد النهائي:", fontWeight = FontWeight.Bold)
                                Text("• العميل: ${customer.fullName} (${customer.phone})", fontWeight = FontWeight.SemiBold)
                                Text("• الرصيد الحالي قبل الدفعة: ${Money.ofMinor(preview.previousBalanceMinor).formatArabic()}")
                                Text(
                                    text = "• مبلغ الدفعة المحصلة: ${Money.ofMinor(preview.amountPaidMinor).formatArabic()}",
                                    color = FinancialPositive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• الرصيد المتبقي بعد الدفعة: ${Money.ofMinor(preview.newBalanceMinor.coerceAtLeast(0L)).formatArabic()}",
                                    color = FinancialInfo,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("• طريقة التحصيل: $methodLabel")
                                if (notes.isNotBlank()) {
                                    Text("• ملاحظات: $notes")
                                }
                            }
                        }
                        if (!errorText.isNullOrBlank()) {
                            Text(errorText!!, color = FinancialNegative, fontWeight = FontWeight.Bold)
                        }
                    }

                    2 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "تم التحصيل بنجاح",
                                tint = FinancialPositive,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "تم تحصيل الدفعة وتحديث رصيد العميل بنجاح!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = FinancialPositive,
                                textAlign = TextAlign.Center
                            )
                            Text("الرصيد السابق: ${Money.ofMinor(preview.previousBalanceMinor).formatArabic()}")
                            Text("المبلغ المدفوع: ${Money.ofMinor(preview.amountPaidMinor).formatArabic()}")
                            Text(
                                text = "الرصيد الجديد: ${Money.ofMinor(preview.newBalanceMinor.coerceAtLeast(0L)).formatArabic()}",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> {
                    Button(
                        onClick = {
                            if (!preview.isValid) {
                                errorText = preview.validationErrorArabic
                                return@Button
                            }
                            if (paymentMethod == "WALLET" && selectedWalletId.isBlank()) {
                                errorText = "يرجى اختيار المحفظة المستلمة بشكل صريح"
                                return@Button
                            }
                            errorText = null
                            step = 1
                        },
                        enabled = preview.isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier.testTag("collection_review_button")
                    ) {
                        Text("مراجعة التحصيل")
                    }
                }
                1 -> {
                    MekkaProcessingButton(
                        idleLabelArabic = "تأكيد التحصيل",
                        processingLabelArabic = "جارٍ تسجيل الدفعة...",
                        isProcessing = isProcessing,
                        containerColor = MekkaEmeraldGreen,
                        testTag = "collection_confirm_button",
                        onClick = {
                            val accType = if (paymentMethod == "WALLET") FinancialAccountType.WALLET else FinancialAccountType.CASH_DRAWER
                            val accId = if (paymentMethod == "WALLET") selectedWalletId else MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID
                            onExecuteCollection(
                                amountStr,
                                accType,
                                accId,
                                false,
                                notes.ifBlank { "تحصيل نقدي من العميل" },
                                { step = 2 },
                                { err -> errorText = err }
                            )
                        }
                    )
                }
                else -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier.testTag("collection_done_button")
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        },
        dismissButton = {
            if (step == 1 && !isProcessing) {
                TextButton(onClick = { step = 0 }) {
                    Text("تعديل البيانات")
                }
            } else if (step == 0 && !isProcessing) {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        }
    )
}

@Composable
private fun AddCustomerModalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var nationalId by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = fullName.isNotBlank() || phone.isNotBlank() || nationalId.isNotBlank() || address.isNotBlank() || notes.isNotBlank()
    val phoneValidation = remember(phone) {
        if (phone.isBlank()) null else LocalizedFormValidator.validateEgyptianPhone(phone, required = true)
    }
    val nameValidation = remember(fullName) {
        if (fullName.isBlank()) null else LocalizedFormValidator.validateRequiredName(fullName, "اسم العميل", minLength = 2)
    }
    val canSaveNow = fullName.trim().length >= 2 && phone.isNotBlank() && (phoneValidation?.isValid != false)

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            changesSummaryArabic = "لديك بيانات عميل جديد لم يتم حفظها بعد.",
            canSaveDirectly = canSaveNow,
            onContinueEditing = { showUnsavedDialog = false },
            onSaveChanges = {
                showUnsavedDialog = false
                if (canSaveNow) {
                    onSave(fullName.trim(), phone.trim(), nationalId.trim(), address.trim(), notes.trim())
                }
            },
            onDiscardChanges = {
                showUnsavedDialog = false
                onDismiss()
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        title = { Text("+ إضافة عميل جديد", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        errorMsg = null
                    },
                    label = { Text("اسم العميل رباعي أو ثلاثي *") },
                    isError = nameValidation?.isValid == false,
                    supportingText = {
                        if (nameValidation?.isValid == false) {
                            Text(nameValidation.errorMessageArabic ?: "", color = FinancialNegative)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errorMsg = null
                    },
                    label = { Text("رقم الهاتف *") },
                    isError = phoneValidation?.isValid == false,
                    supportingText = {
                        if (phoneValidation?.isValid == false) {
                            Text(phoneValidation.errorMessageArabic ?: "أدخل رقم هاتف صحيح.", color = FinancialNegative)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_phone_input")
                )
                OutlinedTextField(
                    value = nationalId,
                    onValueChange = { nationalId = it },
                    label = { Text("الرقم القومي (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات ائتمانية (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank() || phone.isBlank()) {
                        errorMsg = "يرجى إدخال اسم العميل ورقم الهاتف"
                        return@Button
                    }
                    val phoneCheck = LocalizedFormValidator.validateEgyptianPhone(phone, required = true)
                    if (!phoneCheck.isValid) {
                        errorMsg = phoneCheck.errorMessageArabic
                        return@Button
                    }
                    onSave(fullName.trim(), phone.trim(), nationalId.trim(), address.trim(), notes.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                modifier = Modifier.testTag("save_customer_confirm_button")
            ) {
                Text("حفظ العميل")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (hasUnsavedChanges) {
                        showUnsavedDialog = true
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("إلغاء")
            }
        }
    )
}

// ============================================================================
// 4. SUPPLIERS — LIST + DEDICATED PROFILE + PAYMENT WORKFLOW
// ============================================================================

enum class SupplierBalanceFilter(val arabicLabel: String) {
    ALL("الكل"),
    HAS_PAYABLES("لهم مستحقات"),
    NO_PAYABLES("بدون مستحقات")
}

@Composable
fun SuppliersScreen(
    viewModel: MekkaPhoneViewModel,
    onNavigateToRoute: (String) -> Unit = {}
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplierProfiles by viewModel.supplierProfiles.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val ledgerTransactions by viewModel.ledgerTransactions.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingOperation.collectAsStateWithLifecycle()
    val pendingSupplierId by viewModel.pendingSelectedSupplierId.collectAsStateWithLifecycle()
    val supplier360 by viewModel.selectedSupplier360.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(SupplierBalanceFilter.ALL) }
    var pageSize by rememberSaveable { mutableIntStateOf(50) }

    // Never use the first supplier as an implicit default! (Rule 4)
    var selectedSupplierId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddSupplierDialog by rememberSaveable { mutableStateOf(false) }
    var paymentSupplierId by rememberSaveable { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(pendingSupplierId, suppliers) {
        val targetId = pendingSupplierId
        if (!targetId.isNullOrBlank() && suppliers.any { it.localId == targetId }) {
            selectedSupplierId = targetId
            viewModel.consumeSelectedSupplierProfile()
        }
    }

    val selectedSupplier = remember(suppliers, selectedSupplierId) {
        selectedSupplierId?.let { id -> suppliers.firstOrNull { it.localId == id } }
    }

    if (selectedSupplier != null) {
        androidx.compose.runtime.LaunchedEffect(selectedSupplier.localId) {
            viewModel.loadSupplier360(selectedSupplier.localId)
        }
        val profile = supplierProfiles[selectedSupplier.localId] ?: SupplierFinancialProfile(
            supplierId = selectedSupplier.localId,
            currentBalanceMinor = 0L,
            totalPurchasesMinor = 0L,
            totalPaymentsMinor = 0L,
            outstandingAmountMinor = 0L,
            statementLines = emptyList()
        )
        SupplierDetailProfileView(
            supplier = selectedSupplier,
            profile = profile,
            supplier360 = supplier360?.takeIf { it.supplier.localId == selectedSupplier.localId },
            supplierPurchases = purchases.filter { it.supplierId == selectedSupplier.localId },
            supplierPayments = ledgerTransactions.filter {
                it.supplierId == selectedSupplier.localId &&
                    it.status == LedgerTransactionStatus.POSTED &&
                    (it.relatedEntityType == "SUPPLIER_PAYMENT" || it.relatedEntityType == "PURCHASE")
            },
            onBack = {
                viewModel.clearSupplier360()
                selectedSupplierId = null
            },
            onOpenPayment = { paymentSupplierId = selectedSupplier.localId },
            onNavigateToRoute = onNavigateToRoute
        )
    } else {
        val filteredSuppliers = remember(suppliers, supplierProfiles, searchQuery, selectedFilter) {
            val q = Money.normalizeDigits(searchQuery).trim().lowercase()
            suppliers.filter { sup ->
                val payableMinor = supplierProfiles[sup.localId]?.outstandingAmountMinor ?: 0L
                val matchesSearch = q.isEmpty() ||
                    sup.companyName.lowercase().contains(q) ||
                    sup.contactName.lowercase().contains(q) ||
                    Money.normalizeDigits(sup.phone).lowercase().contains(q)
                val matchesFilter = when (selectedFilter) {
                    SupplierBalanceFilter.ALL -> true
                    SupplierBalanceFilter.HAS_PAYABLES -> payableMinor > 0L
                    SupplierBalanceFilter.NO_PAYABLES -> payableMinor <= 0L
                }
                matchesSearch && matchesFilter
            }
        }

        val pagedSuppliers = remember(filteredSuppliers, pageSize) {
            filteredSuppliers.take(pageSize)
        }

        val totalPayablesMinor = remember(supplierProfiles) {
            supplierProfiles.values.sumOf { it.outstandingAmountMinor }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("suppliers_screen_list"),
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
                            Column {
                                Text(
                                    text = "الموردين",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "إجمالي الموردين: ${suppliers.size} • مستحقات الموردين: ${Money.ofMinor(totalPayablesMinor).formatArabic()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddSupplierDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                                modifier = Modifier.testTag("add_supplier_top_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة مورد", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ إضافة مورد", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                pageSize = 50
                            },
                            label = { Text("بحث باسم الشركة أو المندوب أو الهاتف...") },
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
                                .testTag("supplier_search_input")
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SupplierBalanceFilter.entries) { filter ->
                                val selected = selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedFilter = filter
                                        pageSize = 50
                                    },
                                    label = { Text(filter.arabicLabel) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MekkaEmeraldGreen.copy(alpha = 0.16f),
                                        selectedLabelColor = MekkaEmeraldGreen
                                    ),
                                    modifier = Modifier.testTag("supplier_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (filteredSuppliers.isEmpty()) {
                item {
                    EmptyStateGuidanceCard(
                        titleArabic = if (suppliers.isEmpty()) "لا يوجد موردون مسجلون بعد." else "لا يوجد موردون مطابقون للبحث.",
                        subtitleArabic = "أضف أول مورد لتسجيل فواتير المشتريات وكشوف حسابات الموردين.",
                        actionButtonLabel = "+ إضافة مورد",
                        onActionClick = { showAddSupplierDialog = true }
                    )
                }
            } else {
                items(pagedSuppliers, key = { it.localId }) { sup ->
                    val profile = supplierProfiles[sup.localId]
                    val outstanding = profile?.outstandingAmountMinor ?: 0L
                    val purchasesTotal = profile?.totalPurchasesMinor ?: 0L

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSupplierId = sup.localId }
                            .testTag("supplier_card_${sup.localId}"),
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (outstanding > 0L) FinancialWarning.copy(alpha = 0.14f)
                                            else MekkaEmeraldGreen.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = sup.companyName,
                                        tint = if (outstanding > 0L) FinancialWarning else MekkaEmeraldGreen
                                    )
                                }
                                Column {
                                    Text(sup.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "المندوب: ${sup.contactName.ifBlank { "-" }} • هاتف: ${sup.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "إجمالي التوريدات: ${Money.ofMinor(purchasesTotal).formatArabic()}",
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
                                    color = if (outstanding > 0L) FinancialWarning.copy(alpha = 0.14f) else FinancialPositive.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (outstanding > 0L) "مستحق له: ${Money.ofMinor(outstanding).formatArabic()}" else "خالص (0.00 ج.م)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (outstanding > 0L) FinancialWarning else FinancialPositive,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (outstanding > 0L) {
                                        OutlinedButton(
                                            onClick = { paymentSupplierId = sup.localId },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("سداد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    TextButton(
                                        onClick = { selectedSupplierId = sup.localId },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("الملف الكامل", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredSuppliers.size > pageSize) {
                    item {
                        OutlinedButton(
                            onClick = { pageSize += 50 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("عرض المزيد من الموردين (${pagedSuppliers.size} من ${filteredSuppliers.size})")
                        }
                    }
                }
            }
        }
    }

    if (showAddSupplierDialog) {
        AddSupplierModalDialog(
            onDismiss = { showAddSupplierDialog = false },
            onSave = { company, contact, phone ->
                viewModel.addSupplier(company, contact, phone)
                showAddSupplierDialog = false
            }
        )
    }

    val paymentTargetSupplier = remember(suppliers, paymentSupplierId) {
        paymentSupplierId?.let { id -> suppliers.firstOrNull { it.localId == id } }
    }
    if (paymentTargetSupplier != null) {
        val currentPayable = supplierProfiles[paymentTargetSupplier.localId]?.outstandingAmountMinor ?: 0L
        SupplierPaymentWizardDialog(
            supplier = paymentTargetSupplier,
            currentPayableMinor = currentPayable,
            isProcessing = isProcessing,
            onDismiss = { paymentSupplierId = null },
            onExecutePayment = { amountStr, notes, onSuccess, onError ->
                viewModel.executeSupplierPaymentSafe(
                    supplierId = paymentTargetSupplier.localId,
                    amountMajorStr = amountStr,
                    allowOverpayment = false,
                    notes = notes,
                    onSuccess = { onSuccess() },
                    onError = onError
                )
            }
        )
    }
}

@Composable
private fun SupplierDetailProfileView(
    supplier: SupplierEntity,
    profile: SupplierFinancialProfile,
    supplier360: com.example.intelligence.Supplier360Snapshot?,
    supplierPurchases: List<com.example.database.entities.PurchaseEntity>,
    supplierPayments: List<com.example.database.entities.TransactionEntity>,
    onBack: () -> Unit,
    onOpenPayment: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("كشف الحساب", "المشتريات", "المدفوعات", "الخط الزمني 360", "الأصناف وتاريخ الأسعار", "المرتجعات")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("supplier_detail_profile_screen"),
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
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                            }
                            Column {
                                Text(
                                    text = supplier.companyName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "المندوب: ${supplier.contactName.ifBlank { "-" }} • هاتف: ${supplier.phone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "الرصيد الحالي",
                            value = Money.ofMinor(profile.currentBalanceMinor).formatArabic(),
                            valueColor = if (profile.currentBalanceMinor > 0L) FinancialWarning else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "إجمالي المشتريات",
                            value = Money.ofMinor(profile.totalPurchasesMinor).formatArabic(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "المدفوعات المسددة",
                            value = Money.ofMinor(profile.totalPaymentsMinor).formatArabic(),
                            valueColor = FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricBox(
                            label = "المبلغ المستحق للمورد",
                            value = Money.ofMinor(profile.outstandingAmountMinor).formatArabic(),
                            valueColor = if (profile.outstandingAmountMinor > 0L) FinancialWarning else FinancialPositive,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (supplier360 != null) {
                        Supplier360SummaryBanner(snapshot = supplier360)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenPayment,
                            colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = "سداد للمورد", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("سداد دفعة للمورد", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phone}")))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اتصال بالمورد")
                        }
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
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> { // كشف الحساب
                if (profile.statementLines.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "كشف حساب المورد فارغ حالياً.",
                            subtitleArabic = "تظهر هنا جميع فواتير الشراء والدفعات المسددة للمورد.",
                            actionButtonLabel = "تسجيل سداد للمورد",
                            onActionClick = onOpenPayment
                        )
                    }
                } else {
                    items(profile.statementLines, key = { it.id }) { line ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${line.typeLabelArabic} (#${line.referenceNumber})", fontWeight = FontWeight.Bold)
                                    Text(formatPartyDateTime(line.timestamp), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(line.descriptionArabic, style = MaterialTheme.typography.bodySmall)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("مسدد: ${Money.ofMinor(line.debitMinor).formatArabic()} | توريد: ${Money.ofMinor(line.creditMinor).formatArabic()}")
                                    Text(
                                        text = "المستحق: ${Money.ofMinor(line.runningBalanceMinor).formatArabic()}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (line.runningBalanceMinor > 0L) FinancialWarning else FinancialPositive
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> { // المشتريات
                if (supplierPurchases.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد فواتير شراء مسجلة من هذا المورد بعد.",
                            subtitleArabic = "سجل فاتورة شراء جديدة من شاشة المشتريات والتوريد.",
                            actionButtonLabel = "عرض كشف الحساب",
                            onActionClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(supplierPurchases, key = { it.localId }) { pur ->
                        val rem = (pur.totalAmountMinor - pur.paidAmountMinor).coerceAtLeast(0L)
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
                                    Text("فاتورة شراء #${pur.invoiceNumber}", fontWeight = FontWeight.Bold)
                                    Text(formatPartyDateTime(pur.purchaseTimestamp), style = MaterialTheme.typography.bodySmall)
                                    Text("المسدد: ${Money.ofMinor(pur.paidAmountMinor).formatArabic()} • الآجل: ${Money.ofMinor(rem).formatArabic()}")
                                }
                                Text(
                                    text = Money.ofMinor(pur.totalAmountMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MekkaEmeraldGreen
                                )
                            }
                        }
                    }
                }
            }

            2 -> { // المدفوعات
                if (supplierPayments.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد دفعات سداد مسجلة لهذا المورد.",
                            subtitleArabic = "سجل دفعة سداد للمورد لتخفيض الرصيد المستحق له.",
                            actionButtonLabel = "سداد دفعة للمورد",
                            onActionClick = onOpenPayment
                        )
                    }
                } else {
                    items(supplierPayments, key = { it.localId }) { tx ->
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
                                    Text(tx.descriptionArabic, fontWeight = FontWeight.Bold)
                                    Text("مرجع: ${tx.referenceNumber} • ${formatPartyDateTime(tx.transactionTimestamp)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = Money.ofMinor(tx.amountMinor).formatArabic(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FinancialPositive
                                )
                            }
                        }
                    }
                }
            }

            3 -> { // الخط الزمني 360 للمورد
                val timeline = supplier360?.timeline.orEmpty()
                if (timeline.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "الخط الزمني للمورد فارغ حالياً.",
                            subtitleArabic = "تظهر هنا فواتير التوريد والدفعات والمرتجعات مرتبة زمنياً.",
                            actionButtonLabel = "عرض كشف الحساب",
                            onActionClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(timeline, key = { it.id }) { ev ->
                        SupplierTimeline360InteractiveCard(
                            event = ev,
                            onOpenEventSource = { onNavigateToRoute(it.routeTarget) }
                        )
                    }
                }
            }

            4 -> { // الأصناف الموردة وتاريخ الأسعار (Products & Price History)
                val suppliedProducts = supplier360?.suppliedProducts.orEmpty()
                val priceHistory = supplier360?.priceHistory.orEmpty()
                if (suppliedProducts.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد أصناف موردة مسجلة من هذا المورد بعد.",
                            subtitleArabic = "تظهر هنا الأصناف التي تم توريدها وتاريخ أسعار الشراء لكل صنف.",
                            actionButtonLabel = "عرض المشتريات",
                            onActionClick = { selectedTab = 1 }
                        )
                    }
                } else {
                    items(suppliedProducts, key = { "s360_prod_${it.productId}" }) { sp ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${sp.nameArabic} (${sp.sku})", fontWeight = FontWeight.Bold)
                                    Text("إجمالي الكمية الموردة: ${sp.totalSuppliedQty} • آخر تكلفة: ${Money.ofMinor(sp.lastPurchaseCostMinor).formatArabic()}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Money.ofMinor(sp.lastPurchaseCostMinor * sp.totalSuppliedQty.toLong()).formatArabic(), fontWeight = FontWeight.ExtraBold, color = MekkaEmeraldGreen)
                            }
                        }
                    }
                    items(priceHistory, key = { "s360_ph_${it.id}" }) { ph ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تاريخ سعر: ${ph.productNameArabic} (فاتورة #${ph.invoiceNumber})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("الكمية: ${ph.quantity} • ${formatPartyDateTime(ph.timestamp)}", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(Money.ofMinor(ph.unitCostMinor).formatArabic(), fontWeight = FontWeight.Bold, color = FinancialInfo)
                            }
                        }
                    }
                }
            }

            5 -> { // مرتجعات المشتريات (Returns)
                val returnsList = supplier360?.returns.orEmpty()
                if (returnsList.isEmpty()) {
                    item {
                        EmptyStateGuidanceCard(
                            titleArabic = "لا توجد مرتجعات مشتريات لهذا المورد.",
                            subtitleArabic = "تظهر هنا القيود العكسية وحركات إرجاع البضاعة للمورد.",
                            actionButtonLabel = "عرض كشف الحساب",
                            onActionClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(returnsList, key = { "s360_ret_${it.localId}" }) { ret ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ret.descriptionArabic, fontWeight = FontWeight.Bold)
                                    Text("مرجع: ${ret.referenceNumber} • ${formatPartyDateTime(ret.transactionTimestamp)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Money.ofMinor(ret.amountMinor).formatArabic(), fontWeight = FontWeight.ExtraBold, color = FinancialWarning)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierPaymentWizardDialog(
    supplier: SupplierEntity,
    currentPayableMinor: Long,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onExecutePayment: (
        amountStr: String,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var amountStr by rememberSaveable {
        mutableStateOf(if (currentPayableMinor > 0L) (currentPayableMinor / 100.0).toString() else "")
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val previousPayableSnapshot by remember { mutableStateOf(currentPayableMinor) }

    val parsedAmountMinor = remember(amountStr) {
        runCatching { Money.parseMajorString(amountStr).amountMinor }.getOrNull()
    }
    val preview = remember(previousPayableSnapshot, parsedAmountMinor) {
        PartyAccountStatementEngine.evaluateSupplierPayment(
            currentOutstandingMinor = previousPayableSnapshot,
            amountPaidMinor = parsedAmountMinor,
            allowOverpayment = false
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = when (step) {
                    0 -> "سداد مستحقات لمورد"
                    1 -> "مراجعة وتأكيد السداد"
                    else -> "تم السداد بنجاح"
                },
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("المورد: ${supplier.companyName} (${supplier.phone})", fontWeight = FontWeight.Bold)
                        Text(
                            text = "الرصيد السابق للمورد: ${Money.ofMinor(preview.previousBalanceMinor).formatArabic()}",
                            color = if (preview.previousBalanceMinor > 0L) FinancialWarning else FinancialPositive,
                            fontWeight = FontWeight.Bold
                        )
                        if (parsedAmountMinor != null && parsedAmountMinor > 0L) {
                            Text("المبلغ المسدد: ${Money.ofMinor(preview.amountPaidMinor).formatArabic()}", color = MekkaEmeraldGreen, fontWeight = FontWeight.Bold)
                            Text("الرصيد المتبقي بعد السداد: ${Money.ofMinor(preview.newBalanceMinor).formatArabic()}", color = FinancialInfo, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = {
                                amountStr = it
                                errorText = null
                            },
                            label = { Text("مبلغ السداد (ج.م)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("بيان / ملاحظات السداد") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val validationMsg = errorText ?: preview.validationErrorArabic
                        if (!validationMsg.isNullOrBlank()) {
                            Text(validationMsg, color = FinancialNegative, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Text("هل تؤكد صرف مبلغ ${Money.ofMinor(preview.amountPaidMinor).formatArabic()} من الخزينة لسداد المورد (${supplier.companyName})؟")
                        if (!errorText.isNullOrBlank()) {
                            Text(errorText!!, color = FinancialNegative, fontWeight = FontWeight.Bold)
                        }
                    }
                    2 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "تم", tint = FinancialPositive, modifier = Modifier.size(48.dp))
                            Text("تم ترحيل سند السداد بنجاح!", fontWeight = FontWeight.ExtraBold, color = FinancialPositive)
                            Text("الرصيد الجديد للمورد: ${Money.ofMinor(preview.newBalanceMinor).formatArabic()}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> {
                    Button(
                        onClick = {
                            if (!preview.isValid) {
                                errorText = preview.validationErrorArabic
                                return@Button
                            }
                            step = 1
                        },
                        enabled = preview.isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
                    ) {
                        Text("مراجعة السداد")
                    }
                }
                1 -> {
                    MekkaProcessingButton(
                        idleLabelArabic = "تأكيد السداد",
                        processingLabelArabic = "جارٍ تسجيل الدفعة...",
                        isProcessing = isProcessing,
                        containerColor = MekkaEmeraldGreen,
                        testTag = "supplier_payment_confirm_button",
                        onClick = {
                            onExecutePayment(
                                amountStr,
                                notes.ifBlank { "سداد نقدي لمورد" },
                                { step = 2 },
                                { err -> errorText = err }
                            )
                        }
                    )
                }
                else -> {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)) {
                        Text("إغلاق")
                    }
                }
            }
        },
        dismissButton = {
            if (step == 1 && !isProcessing) {
                TextButton(onClick = { step = 0 }) { Text("تعديل") }
            } else if (step == 0 && !isProcessing) {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

@Composable
private fun AddSupplierModalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var companyName by rememberSaveable { mutableStateOf("") }
    var contactName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = companyName.isNotBlank() || contactName.isNotBlank() || phone.isNotBlank()
    val phoneValidation = remember(phone) {
        if (phone.isBlank()) null else LocalizedFormValidator.validateEgyptianPhone(phone, required = true)
    }
    val canSaveNow = companyName.trim().length >= 2 && phone.isNotBlank() && (phoneValidation?.isValid != false)

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            changesSummaryArabic = "لديك بيانات مورد جديد لم يتم حفظها بعد.",
            canSaveDirectly = canSaveNow,
            onContinueEditing = { showUnsavedDialog = false },
            onSaveChanges = {
                showUnsavedDialog = false
                if (canSaveNow) {
                    onSave(companyName.trim(), contactName.trim(), phone.trim())
                }
            },
            onDiscardChanges = {
                showUnsavedDialog = false
                onDismiss()
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        title = { Text("+ إضافة مورد جديد", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = {
                        companyName = it
                        errorMsg = null
                    },
                    label = { Text("اسم الشركة / المورد *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("اسم مسؤول المبيعات / المندوب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errorMsg = null
                    },
                    label = { Text("رقم الهاتف *") },
                    isError = phoneValidation?.isValid == false,
                    supportingText = {
                        if (phoneValidation?.isValid == false) {
                            Text(phoneValidation.errorMessageArabic ?: "أدخل رقم هاتف صحيح.", color = FinancialNegative)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = FinancialNegative, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (companyName.isBlank() || phone.isBlank()) {
                        errorMsg = "يرجى إدخال اسم المورد ورقم الهاتف"
                        return@Button
                    }
                    val phoneCheck = LocalizedFormValidator.validateEgyptianPhone(phone, required = true)
                    if (!phoneCheck.isValid) {
                        errorMsg = phoneCheck.errorMessageArabic
                        return@Button
                    }
                    onSave(companyName.trim(), contactName.trim(), phone.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen)
            ) {
                Text("حفظ المورد")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (hasUnsavedChanges) {
                        showUnsavedDialog = true
                    } else {
                        onDismiss()
                    }
                }
            ) { Text("إلغاء") }
        }
    )
}

// ============================================================================
// SHARED DESIGN SYSTEM COMPONENTS FOR PART 2 (Upgraded to V3.2 Section 20)
// ============================================================================

@Composable
fun ProfileMetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
        }
    }
}

@Composable
fun EmptyStateGuidanceCard(
    titleArabic: String,
    subtitleArabic: String,
    actionButtonLabel: String,
    onActionClick: () -> Unit,
    testTag: String = "empty_state_card"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MekkaEmeraldGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = titleArabic,
                    tint = MekkaEmeraldGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = titleArabic,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitleArabic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                modifier = Modifier.testTag("${testTag}_action_button")
            ) {
                Text(actionButtonLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

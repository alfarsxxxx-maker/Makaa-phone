package com.example.transaction

import com.example.database.MekkaPhoneDatabase
import com.example.database.dao.ProductInventoryProjection
import com.example.database.dao.WalletDerivedProjection
import com.example.database.entities.CustomerEntity
import com.example.database.entities.SupplierEntity
import com.example.financial.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Intelligent Transaction Intent & Natural Language Parser (Sections 2, 6, 7 & 8).
 * Parses Arabic natural language text/voice transcript into a structured [TransactionDraft].
 * Matches entities against Room database (Products, Customers, Suppliers, Wallets).
 * NEVER mutates the database directly!
 */
class IntelligentTransactionParser(
    private val database: MekkaPhoneDatabase,
    private val categorizationEngine: SmartCategorizationEngine = SmartCategorizationEngine(database)
) {
    private val catalogDao = database.catalogInventoryDao()
    private val ledgerDao = database.financialLedgerDao()

    suspend fun parseNaturalInput(
        rawText: String,
        modality: TransactionInputModality,
        shopId: String
    ): TransactionDraft = withContext(Dispatchers.IO) {
        val withSpelledNumbers = normalizeArabicSpelledNumbers(rawText)
        val normalized = Money.normalizeDigits(withSpelledNumbers).trim()
        val lower = normalized.lowercase()

        // 1. Load catalog projections for matching
        val products = catalogDao.observeProductInventoryProjections(shopId).first()
        val customers = catalogDao.observeCustomers(shopId).first()
        val suppliers = catalogDao.observeSuppliers(shopId).first()
        val now = System.currentTimeMillis()
        val wallets = ledgerDao.observeWalletDerivedProjections(shopId, now - 86400000L, now, now - 86400000L, now).first()

        // 2. Identify Primary Operation Type
        val opType = detectOperationType(lower)

        // 3. Extract Financial Amounts (Total, Paid, Remaining)
        val extractedAmounts = extractAllAmountsMinor(lower)
        val primaryAmountMinor = extractedAmounts.firstOrNull() ?: 0L
        val secondaryPaidAmountMinor = if (extractedAmounts.size >= 2) extractedAmounts[1] else primaryAmountMinor

        when (opType) {
            UnifiedTransactionType.EXPENSE -> parseExpenseDraft(lower, rawText, primaryAmountMinor, modality)
            UnifiedTransactionType.CASH_IN -> parseCashInDraft(lower, rawText, primaryAmountMinor, modality)
            UnifiedTransactionType.CASH_OUT -> parseCashOutDraft(lower, rawText, primaryAmountMinor, modality)
            UnifiedTransactionType.WALLET_WITHDRAW,
            UnifiedTransactionType.WALLET_DEPOSIT -> parseWalletDraft(lower, rawText, opType, primaryAmountMinor, wallets, modality)
            UnifiedTransactionType.CUSTOMER_PAYMENT -> parseCustomerPaymentDraft(lower, rawText, primaryAmountMinor, customers, modality)
            UnifiedTransactionType.SUPPLIER_PAYMENT -> parseSupplierPaymentDraft(lower, rawText, primaryAmountMinor, suppliers, modality)
            UnifiedTransactionType.PURCHASE -> parsePurchaseDraft(lower, rawText, primaryAmountMinor, secondaryPaidAmountMinor, products, suppliers, modality)
            else -> parseSaleDraft(lower, rawText, primaryAmountMinor, secondaryPaidAmountMinor, products, customers, modality)
        }
    }

    private fun normalizeArabicSpelledNumbers(input: String): String {
        var s = input
        // Written compound numbers
        s = s.replace(Regex("""ثمانية\s*عشر\s*(?:ألف|الف|آلاف)"""), "18000")
        s = s.replace(Regex("""تسعة\s*عشر\s*(?:ألف|الف|آلاف)"""), "19000")
        s = s.replace(Regex("""سبعة\s*عشر\s*(?:ألف|الف|آلاف)"""), "17000")
        s = s.replace(Regex("""ستة\s*عشر\s*(?:ألف|الف|آلاف)"""), "16000")
        s = s.replace(Regex("""خمسة\s*عشر\s*(?:ألف|الف|آلاف)"""), "15000")
        s = s.replace(Regex("""أربعة\s*عشر\s*(?:ألف|الف|آلاف)"""), "14000")
        s = s.replace(Regex("""ثلاثة\s*عشر\s*(?:ألف|الف|آلاف)"""), "13000")
        s = s.replace(Regex("""اثنا\s*عشر\s*(?:ألف|الف|آلاف)"""), "12000")
        s = s.replace(Regex("""أحد\s*عشر\s*(?:ألف|الف|آلاف)"""), "11000")
        s = s.replace(Regex("""عشرة\s*(?:آلاف|الاف|ألف|الف)"""), "10000")
        s = s.replace(Regex("""تسعة\s*(?:آلاف|الاف|ألف|الف)"""), "9000")
        s = s.replace(Regex("""ثمانية\s*(?:آلاف|الاف|ألف|الف)"""), "8000")
        s = s.replace(Regex("""سبعة\s*(?:آلاف|الاف|ألف|الف)"""), "7000")
        s = s.replace(Regex("""ستة\s*(?:آلاف|الاف|ألف|الف)"""), "6000")
        s = s.replace(Regex("""خمسة\s*(?:آلاف|الاف|ألف|الف)"""), "5000")
        s = s.replace(Regex("""أربعة\s*(?:آلاف|الاف|ألف|الف)"""), "4000")
        s = s.replace(Regex("""ثلاثة\s*(?:آلاف|الاف|ألف|الف)"""), "3000")
        s = s.replace(Regex("""ألفين|الفين"""), "2000")
        s = s.replace(Regex("""خمسمائة|خمسمية"""), "500")
        s = s.replace(Regex("""ألف\s*و\s*خمسمائة|الف\s*و\s*خمسمية"""), "1500")
        s = s.replace(Regex("""عشر\s*(?:جرابات|قطع)"""), "10 جرابات")
        return s
    }

    private fun detectOperationType(text: String): UnifiedTransactionType = when {
        text.contains("مصروف") || text.contains("مصاريف") || text.contains("صرفت") || text.contains("كهرباء") || text.contains("بنزين") || text.contains("فاتورة مياه") ->
            UnifiedTransactionType.EXPENSE
        text.contains("اشتريت") || text.contains("شراء") || text.contains("فاتورة شراء") || text.contains("توريد بضاعة") ->
            UnifiedTransactionType.PURCHASE
        text.contains("تحصيل من") || text.contains("سدد العميل") || text.contains("دفع العميل") || text.contains("استلمت من") || text.contains("قبضت من") ->
            UnifiedTransactionType.CUSTOMER_PAYMENT
        text.contains("سددت للمورد") || text.contains("دفعت للمورد") || text.contains("سداد مورد") || text.contains("دفعت لـ") || (text.contains("دفعت") && text.contains("مورد")) ->
            UnifiedTransactionType.SUPPLIER_PAYMENT
        (text.contains("سحبت") || text.contains("سحب")) && (text.contains("فودافون") || text.contains("اورنج") || text.contains("اتصالات") || text.contains("محفظة") || text.contains("كاش") || text.contains("انستا")) ->
            UnifiedTransactionType.WALLET_WITHDRAW
        text.contains("ايداع محفظة") || text.contains("إيداع محفظة") || text.contains("شحن فودافون كاش") || text.contains("شحن محفظة") || text.contains("اودعت") ->
            UnifiedTransactionType.WALLET_DEPOSIT
        text.contains("توريد نقدية") || text.contains("تمويل خزينة") || text.contains("ايداع في الخزينة") || text.contains("إيداع في الخزنة") ->
            UnifiedTransactionType.CASH_IN
        text.contains("صرف نقدية") || text.contains("سحب من الخزينة") || text.contains("عهدة") ->
            UnifiedTransactionType.CASH_OUT
        text.contains("مرتجع") || text.contains("استرجاع") ->
            UnifiedTransactionType.REFUND
        text.contains("صيانة") || text.contains("تصليح") ->
            UnifiedTransactionType.MAINTENANCE_PAYMENT
        else -> UnifiedTransactionType.SALE // Default to SALE for phrases like "بعت", "فاتورة", "جراب", "ايفون", etc.
    }

    private suspend fun parseExpenseDraft(
        lower: String,
        rawText: String,
        amountMinor: Long,
        modality: TransactionInputModality
    ): TransactionDraft {
        val cleanTitle = rawText
            .replace(Regex("""(?:اعمل|سجل|صرف|تسجيل)?\s*(?:مصروف|مصاريف)"""), "")
            .replace(Regex("""[0-9]+(?:\.[0-9]{1,2})?\s*(?:الف|آلاف|ألف|ج|جنيه|ج\.م)?"""), "")
            .trim()
            .ifBlank { "مصروف تشغيلي" }

        val category = categorizationEngine.suggestCategory(cleanTitle.ifBlank { rawText })

        val missing = if (amountMinor <= 0L) listOf("المبلغ المالي المصروف") else emptyList()
        val confidence = if (amountMinor > 0L) 95 else 50

        return TransactionDraft(
            transactionType = UnifiedTransactionType.EXPENSE,
            inputModality = modality,
            rawPromptOrInput = rawText,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "CASH",
            expenseCategoryName = category,
            notes = cleanTitle,
            confidencePercent = confidence,
            missingRequirementsArabic = missing
        )
    }

    private fun parseCashInDraft(
        lower: String,
        rawText: String,
        amountMinor: Long,
        modality: TransactionInputModality
    ): TransactionDraft {
        val missing = if (amountMinor <= 0L) listOf("مبلغ التوريد النقدي") else emptyList()
        return TransactionDraft(
            transactionType = UnifiedTransactionType.CASH_IN,
            inputModality = modality,
            rawPromptOrInput = rawText,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "CASH",
            notes = "توريد نقدية للخزينة الرئيسية: $rawText",
            confidencePercent = if (amountMinor > 0L) 95 else 50,
            missingRequirementsArabic = missing
        )
    }

    private fun parseCashOutDraft(
        lower: String,
        rawText: String,
        amountMinor: Long,
        modality: TransactionInputModality
    ): TransactionDraft {
        val missing = if (amountMinor <= 0L) listOf("مبلغ الصرف النقدي") else emptyList()
        return TransactionDraft(
            transactionType = UnifiedTransactionType.CASH_OUT,
            inputModality = modality,
            rawPromptOrInput = rawText,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "CASH",
            notes = "صرف نقدية من الخزينة: $rawText",
            confidencePercent = if (amountMinor > 0L) 95 else 50,
            missingRequirementsArabic = missing
        )
    }

    private fun parseWalletDraft(
        lower: String,
        rawText: String,
        opType: UnifiedTransactionType,
        amountMinor: Long,
        wallets: List<WalletDerivedProjection>,
        modality: TransactionInputModality
    ): TransactionDraft {
        val matchedWallet = wallets.firstOrNull { w ->
            lower.contains(w.providerCode.lowercase()) ||
                lower.contains(w.providerNameArabic.lowercase()) ||
                lower.contains(w.walletNameArabic.lowercase()) ||
                (w.walletNumber.length >= 6 && lower.contains(w.walletNumber))
        } ?: wallets.firstOrNull()

        val ambiguities = if (matchedWallet == null && wallets.isNotEmpty()) {
            listOf(
                TransactionAmbiguity(
                    ambiguityKey = "walletId",
                    questionArabic = "حدد المحفظة الإلكترونية المستهدفة للعملية:",
                    candidateOptions = wallets.map {
                        AmbiguityOption(
                            optionId = it.walletId,
                            labelArabic = "${it.walletNameArabic} (${it.providerNameArabic})",
                            secondaryHintArabic = "الرصيد: ${Money.ofMinor(it.derivedBalanceMinor).formatArabic()}"
                        )
                    }
                )
            )
        } else emptyList()

        val missing = if (amountMinor <= 0L) listOf("مبلغ معاملة المحفظة") else emptyList()
        val confidence = if (amountMinor > 0L && matchedWallet != null) 90 else 55

        return TransactionDraft(
            transactionType = opType,
            inputModality = modality,
            rawPromptOrInput = rawText,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "WALLET",
            targetAccountId = matchedWallet?.walletId,
            targetAccountNameArabic = matchedWallet?.walletNameArabic,
            notes = rawText,
            confidencePercent = confidence,
            ambiguities = ambiguities,
            missingRequirementsArabic = missing
        )
    }

    private fun parseCustomerPaymentDraft(
        lower: String,
        rawText: String,
        amountMinor: Long,
        customers: List<CustomerEntity>,
        modality: TransactionInputModality
    ): TransactionDraft {
        val matchedCustomer = customers.firstOrNull { c ->
            lower.contains(c.fullName.lowercase()) || (c.phone.length >= 8 && lower.contains(c.phone))
        }

        val ambiguities = if (matchedCustomer == null && customers.isNotEmpty()) {
            listOf(
                TransactionAmbiguity(
                    ambiguityKey = "customerId",
                    questionArabic = "اختر العميل الذي سدد المستحقات:",
                    candidateOptions = customers.take(5).map {
                        AmbiguityOption(optionId = it.localId, labelArabic = it.fullName, secondaryHintArabic = it.phone)
                    }
                )
            )
        } else emptyList()

        val missing = mutableListOf<String>()
        if (amountMinor <= 0L) missing.add("المبلغ المحصل من العميل")
        if (matchedCustomer == null && ambiguities.isEmpty()) missing.add("تحديد العميل")

        return TransactionDraft(
            transactionType = UnifiedTransactionType.CUSTOMER_PAYMENT,
            inputModality = modality,
            rawPromptOrInput = rawText,
            counterpartyId = matchedCustomer?.localId,
            counterpartyName = matchedCustomer?.fullName,
            counterpartyPhone = matchedCustomer?.phone,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "CASH",
            notes = "تحصيل مستحقات عميل: $rawText",
            confidencePercent = if (amountMinor > 0L && matchedCustomer != null) 95 else 60,
            ambiguities = ambiguities,
            missingRequirementsArabic = missing
        )
    }

    private fun parseSupplierPaymentDraft(
        lower: String,
        rawText: String,
        amountMinor: Long,
        suppliers: List<SupplierEntity>,
        modality: TransactionInputModality
    ): TransactionDraft {
        val matchedSupplier = suppliers.firstOrNull { s ->
            lower.contains(s.companyName.lowercase()) ||
                (s.contactName.isNotBlank() && lower.contains(s.contactName.lowercase())) ||
                lower.contains(s.phone)
        }

        val ambiguities = if (matchedSupplier == null && suppliers.isNotEmpty()) {
            listOf(
                TransactionAmbiguity(
                    ambiguityKey = "supplierId",
                    questionArabic = "اختر المورد الذي تم سداد الدفعة له:",
                    candidateOptions = suppliers.take(5).map {
                        AmbiguityOption(optionId = it.localId, labelArabic = it.companyName, secondaryHintArabic = it.contactName.ifBlank { it.phone })
                    }
                )
            )
        } else emptyList()

        val missing = mutableListOf<String>()
        if (amountMinor <= 0L) missing.add("المبلغ المسدد للمورد")
        if (matchedSupplier == null && ambiguities.isEmpty()) missing.add("تحديد المورد")

        return TransactionDraft(
            transactionType = UnifiedTransactionType.SUPPLIER_PAYMENT,
            inputModality = modality,
            rawPromptOrInput = rawText,
            counterpartyId = matchedSupplier?.localId,
            counterpartyName = matchedSupplier?.companyName,
            counterpartyPhone = matchedSupplier?.phone,
            totalAmountMinor = amountMinor,
            paidAmountMinor = amountMinor,
            paymentMode = "CASH",
            notes = "سداد دفعة مورد: $rawText",
            confidencePercent = if (amountMinor > 0L && matchedSupplier != null) 95 else 60,
            ambiguities = ambiguities,
            missingRequirementsArabic = missing
        )
    }

    private fun parsePurchaseDraft(
        lower: String,
        rawText: String,
        primaryAmountMinor: Long,
        secondaryPaidAmountMinor: Long,
        products: List<ProductInventoryProjection>,
        suppliers: List<SupplierEntity>,
        modality: TransactionInputModality
    ): TransactionDraft {
        val matchedSupplier = suppliers.firstOrNull { s ->
            lower.contains(s.companyName.lowercase()) ||
                (s.contactName.isNotBlank() && lower.contains(s.contactName.lowercase())) ||
                lower.contains(s.phone)
        }

        val qty = extractQuantity(lower) ?: 1
        val matchedProducts = findMatchingProducts(lower, products)

        val ambiguities = mutableListOf<TransactionAmbiguity>()
        val selectedProduct = if (matchedProducts.size == 1) {
            matchedProducts.first()
        } else if (matchedProducts.size > 1) {
            ambiguities.add(
                TransactionAmbiguity(
                    ambiguityKey = "productId",
                    questionArabic = "وجدت أكثر من صنف يطابق الوصف، يرجى تحديد الصنف المشتري:",
                    candidateOptions = matchedProducts.map {
                        AmbiguityOption(
                            optionId = it.productId,
                            labelArabic = it.nameArabic,
                            secondaryHintArabic = "كود: ${it.sku} • المخزون الحالي: ${it.derivedStockQty}"
                        )
                    }
                )
            )
            null
        } else null

        val items = if (selectedProduct != null) {
            val unitPriceMinor = if (primaryAmountMinor > 0L) {
                primaryAmountMinor / qty
            } else {
                selectedProduct.weightedAvgCostMinor.coerceAtLeast(100L)
            }
            listOf(
                TransactionDraftItem(
                    productId = selectedProduct.productId,
                    productNameArabic = selectedProduct.nameArabic,
                    sku = selectedProduct.sku,
                    barcode = selectedProduct.barcode,
                    quantity = qty,
                    unitPriceMinor = unitPriceMinor,
                    availableStockQty = selectedProduct.derivedStockQty,
                    isSerialized = selectedProduct.isSerialized
                )
            )
        } else emptyList()

        val totalMinor = if (items.isNotEmpty()) items.sumOf { it.totalLineMinor } else primaryAmountMinor
        val isCredit = lower.contains("اجل") || lower.contains("آجل") || lower.contains("متبقي") || lower.contains("باقي")
        val paidMinor = if (isCredit && secondaryPaidAmountMinor < totalMinor) secondaryPaidAmountMinor else totalMinor

        val missing = mutableListOf<String>()
        if (totalMinor <= 0L) missing.add("إجمالي تكلفة المشتريات")
        if (selectedProduct == null && ambiguities.isEmpty()) missing.add("تحديد الصنف المشتري")
        if (matchedSupplier == null) missing.add("تحديد المورد")

        val confidence = when {
            items.isNotEmpty() && matchedSupplier != null && totalMinor > 0L -> 95
            items.isNotEmpty() && totalMinor > 0L -> 80
            ambiguities.isNotEmpty() -> 50
            else -> 40
        }

        return TransactionDraft(
            transactionType = UnifiedTransactionType.PURCHASE,
            inputModality = modality,
            rawPromptOrInput = rawText,
            counterpartyId = matchedSupplier?.localId,
            counterpartyName = matchedSupplier?.companyName,
            counterpartyPhone = matchedSupplier?.phone,
            items = items,
            totalAmountMinor = totalMinor,
            paidAmountMinor = paidMinor,
            paymentMode = if (isCredit) "CREDIT" else "CASH",
            notes = rawText,
            confidencePercent = confidence,
            ambiguities = ambiguities,
            missingRequirementsArabic = missing
        )
    }

    private fun parseSaleDraft(
        lower: String,
        rawText: String,
        primaryAmountMinor: Long,
        secondaryPaidAmountMinor: Long,
        products: List<ProductInventoryProjection>,
        customers: List<CustomerEntity>,
        modality: TransactionInputModality
    ): TransactionDraft {
        val ambiguities = mutableListOf<TransactionAmbiguity>()

        // Customer detection (Sections 13 & 14)
        val matchedCustomers = customers.filter { c ->
            val fName = c.fullName.lowercase()
            lower.contains(fName) || (c.phone.length >= 8 && lower.contains(c.phone)) ||
                (fName.split(" ").firstOrNull()?.let { it.length >= 3 && lower.contains(it) } == true)
        }

        val matchedCustomer = when {
            matchedCustomers.size == 1 -> matchedCustomers.first()
            matchedCustomers.size > 1 -> {
                ambiguities.add(
                    TransactionAmbiguity(
                        ambiguityKey = "customerId",
                        questionArabic = "وجدت أكثر من عميل مطابق، يرجى اختيار العميل المطلوب:",
                        candidateOptions = matchedCustomers.map {
                            AmbiguityOption(
                                optionId = it.localId,
                                labelArabic = it.fullName,
                                secondaryHintArabic = "هاتف: ${it.phone}"
                            )
                        }
                    )
                )
                null
            }
            else -> null
        }

        val qty = extractQuantity(lower) ?: 1
        val matchedProducts = findMatchingProducts(lower, products)

        val selectedProduct = if (matchedProducts.size == 1) {
            matchedProducts.first()
        } else if (matchedProducts.size > 1) {
            ambiguities.add(
                TransactionAmbiguity(
                    ambiguityKey = "productId",
                    questionArabic = "وجدت أكثر من صنف مطابق، يرجى اختيار المنتج المطلوب بيعه:",
                    candidateOptions = matchedProducts.map {
                        AmbiguityOption(
                            optionId = it.productId,
                            labelArabic = it.nameArabic,
                            secondaryHintArabic = "سعر البيع: ${Money.ofMinor(it.effectiveRetailPriceMinor).formatArabic()} • المتاح: ${it.derivedStockQty} قطعة"
                        )
                    }
                )
            )
            null
        } else null

        // 1. Detect explicit total price
        val priceMatch = Regex("""(?:بـ|ب|بسعر|سعر|بقيمة|بمبلغ)\s*([0-9]+(?:\.[0-9]{1,2})?)\s*(?:الف|آلاف|ألف|k)?""").find(lower)
        val explicitPriceMinor: Long? = priceMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val isThousand = m.value.contains("الف") || m.value.contains("آلاف") || m.value.contains("ألف") || m.value.contains("k") || (num < 100.0 && num > 0.0)
            if (isThousand) (num * 1000.0 * 100.0).toLong() else (num * 100.0).toLong()
        }

        // 2. Detect explicit paid amount
        val paidMatch = Regex("""(?:دفع|سدد|واصل|مقدم|استلمت|قبضت)\s*([0-9]+(?:\.[0-9]{1,2})?)\s*(?:الف|آلاف|ألف|k)?""").find(lower)
        val explicitPaidMinor: Long? = paidMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val targetTotal = explicitPriceMinor ?: primaryAmountMinor
            val isThousand = m.value.contains("الف") || m.value.contains("آلاف") || m.value.contains("ألف") || m.value.contains("k") || (targetTotal >= 100_000L && num <= 100.0 && num > 0.0)
            if (isThousand) (num * 1000.0 * 100.0).toLong() else (num * 100.0).toLong()
        }

        val items = if (selectedProduct != null) {
            val unitPriceMinor = if (explicitPriceMinor != null && qty == 1) {
                explicitPriceMinor
            } else if (explicitPriceMinor != null) {
                explicitPriceMinor / qty
            } else if (primaryAmountMinor > 0L && qty == 1) {
                primaryAmountMinor
            } else if (primaryAmountMinor > 0L) {
                primaryAmountMinor / qty
            } else {
                selectedProduct.effectiveRetailPriceMinor
            }
            listOf(
                TransactionDraftItem(
                    productId = selectedProduct.productId,
                    productNameArabic = selectedProduct.nameArabic,
                    sku = selectedProduct.sku,
                    barcode = selectedProduct.barcode,
                    quantity = qty,
                    unitPriceMinor = unitPriceMinor,
                    availableStockQty = selectedProduct.derivedStockQty,
                    isSerialized = selectedProduct.isSerialized
                )
            )
        } else emptyList()

        val totalMinor = explicitPriceMinor ?: (if (items.isNotEmpty()) items.sumOf { it.totalLineMinor } else primaryAmountMinor)

        // Payment terms (Cash, Credit, Partial)
        val hasPartialOrCredit = lower.contains("اجل") || lower.contains("آجل") || lower.contains("باقي") || lower.contains("الباقي") || lower.contains("دفع")
        val paidMinor = if (explicitPaidMinor != null && explicitPaidMinor < totalMinor) {
            explicitPaidMinor
        } else if (hasPartialOrCredit && secondaryPaidAmountMinor > 0L && secondaryPaidAmountMinor < totalMinor) {
            secondaryPaidAmountMinor
        } else if (lower.contains("آجل كامل") || (hasPartialOrCredit && (explicitPaidMinor == 0L || secondaryPaidAmountMinor == 0L))) {
            0L
        } else {
            totalMinor
        }

        val paymentMode = when {
            paidMinor == 0L -> "CREDIT"
            paidMinor < totalMinor -> "PARTIAL"
            lower.contains("محفظة") || lower.contains("فودافون") || lower.contains("انستا") -> "WALLET"
            else -> "CASH"
        }

        val missing = mutableListOf<String>()
        if (totalMinor <= 0L) {
            missing.add("ما سعر البيع؟") // Section 14 Voice Error Handling
        }
        if (selectedProduct == null && ambiguities.none { it.ambiguityKey == "productId" }) {
            missing.add("تحديد المنتج المباع")
        }
        if ((paymentMode == "CREDIT" || paymentMode == "PARTIAL") && matchedCustomer == null && ambiguities.none { it.ambiguityKey == "customerId" }) {
            missing.add("تحديد العميل (البيع الآجل أو الجزئي يتطلب اختيار عميل مسجل)")
        }

        val confidence = when {
            items.isNotEmpty() && totalMinor > 0L && ambiguities.isEmpty() -> 95
            items.isNotEmpty() && totalMinor > 0L -> 75
            ambiguities.isNotEmpty() -> 50
            else -> 40
        }

        return TransactionDraft(
            transactionType = UnifiedTransactionType.SALE,
            inputModality = modality,
            rawPromptOrInput = rawText,
            counterpartyId = matchedCustomer?.localId,
            counterpartyName = matchedCustomer?.fullName,
            counterpartyPhone = matchedCustomer?.phone,
            items = items,
            totalAmountMinor = totalMinor,
            paidAmountMinor = paidMinor,
            paymentMode = paymentMode,
            notes = rawText,
            confidencePercent = confidence,
            ambiguities = ambiguities,
            missingRequirementsArabic = missing
        )
    }

    private fun findMatchingProducts(lower: String, products: List<ProductInventoryProjection>): List<ProductInventoryProjection> {
        val exactMatch = products.filter { p ->
            lower.contains(p.sku.lowercase()) || (p.barcode != null && lower.contains(p.barcode.lowercase()))
        }
        if (exactMatch.isNotEmpty()) return exactMatch

        val tokenMatches = products.filter { p ->
            val pName = p.nameArabic.lowercase()
            val words = pName.split(" ").filter { it.length >= 3 }
            words.any { w -> lower.contains(w) }
        }
        return tokenMatches.take(6)
    }

    private fun extractQuantity(lower: String): Int? {
        val regex = Regex("""(?:عدد|كمية|اشتريت|بعت)?\s*([0-9]+)\s*(?:قطع|قطعة|جرابات|جراب|شاحن|سماعة|جهاز)""")
        val match = regex.find(lower)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractAllAmountsMinor(lower: String): List<Long> {
        val results = mutableListOf<Long>()
        // Match numbers followed by thousand identifiers (ألف / الف / k)
        val thousandRegex = Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:الف|آلاف|ألف|k)""")
        for (m in thousandRegex.findAll(lower)) {
            val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
            results.add((num * 1000.0 * 100.0).toLong())
        }

        // Match standalone numbers with currency hints (e.g. "500 جنيه", "300 ج.م")
        val currencyRegex = Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:ج|جنيه|ج\.م|egp)""")
        for (m in currencyRegex.findAll(lower)) {
            val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val minor = (num * 100.0).toLong()
            if (minor > 0L && results.none { it == minor || it == minor * 1000 }) {
                results.add(minor)
            }
        }

        // Match larger standalone numbers (>= 100) not attached to small model numbers
        val largeNumberRegex = Regex("""(?<![a-zA-Z0-9_\-])([0-9]{3,})(?![a-zA-Z0-9_\-])""")
        for (m in largeNumberRegex.findAll(lower)) {
            val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val minor = (num * 100.0).toLong()
            if (minor > 0L && results.none { it == minor || it == minor * 1000 }) {
                results.add(minor)
            }
        }

        return results
    }
}

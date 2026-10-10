package com.example.transaction

import com.example.database.MekkaPhoneDatabase
import com.example.database.dao.ProductInventoryProjection
import com.example.database.entities.SupplierEntity
import com.example.financial.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receipt & Purchase OCR Extraction Engine (V4.1 Part 2 - Sections 16, 17 & 18).
 * Parses scanned receipt/invoice text into structured purchase or expense line items.
 * Performs safety checks on doubtful fields and prepares a reviewable [TransactionDraft].
 * NEVER mutates database directly!
 */
data class OcrExtractedLineItem(
    val productNameRaw: String,
    val matchedProductId: String? = null,
    val matchedProductNameArabic: String? = null,
    val quantity: Int = 1,
    val unitPriceMinor: Long = 0L,
    val lineTotalMinor: Long = 0L,
    val isDoubtful: Boolean = false
)

data class OcrExtractionResult(
    val isPurchaseInvoice: Boolean,
    val supplierNameRaw: String? = null,
    val matchedSupplierId: String? = null,
    val matchedSupplierName: String? = null,
    val invoiceNumberRaw: String? = null,
    val dateParsed: Long? = null,
    val items: List<OcrExtractedLineItem> = emptyList(),
    val totalAmountMinor: Long = 0L,
    val paidAmountMinor: Long = 0L,
    val overallConfidencePercent: Int = 85,
    val doubtfulFieldsArabic: List<String> = emptyList(),
    val rawTextExtracted: String = ""
)

class ReceiptOcrEngine(
    private val database: MekkaPhoneDatabase
) {
    private val catalogDao = database.catalogInventoryDao()

    /**
     * Parses raw text from camera capture or OCR scanner.
     */
    suspend fun parseReceiptText(
        ocrRawText: String,
        shopId: String = "SHOP-01"
    ): OcrExtractionResult = withContext(Dispatchers.IO) {
        val lines = ocrRawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val doubtful = mutableListOf<String>()

        val suppliers = catalogDao.observeSuppliers(shopId).first()
        val products = catalogDao.observeProductInventoryProjections(shopId).first()

        var detectedSupplier: SupplierEntity? = null
        var invoiceNum: String? = null
        var extractedDateMillis: Long? = null
        val extractedItems = mutableListOf<OcrExtractedLineItem>()
        var extractedTotalMinor = 0L
        var extractedPaidMinor = 0L

        // 1. Detect Supplier
        for (line in lines) {
            val lowerLine = line.lowercase()
            val matched = suppliers.firstOrNull { s ->
                lowerLine.contains(s.companyName.lowercase()) ||
                    (s.contactName.isNotBlank() && lowerLine.contains(s.contactName.lowercase())) ||
                    lowerLine.contains(s.phone)
            }
            if (matched != null) {
                detectedSupplier = matched
                break
            }
        }
        if (detectedSupplier == null) {
            // Check for generic supplier keywords
            for (line in lines) {
                if (line.contains("شركة") || line.contains("مؤسسة") || line.contains("مورد") || line.contains("محلات") || line.contains("سنتر")) {
                    // Raw supplier line hint
                    doubtful.add("المورد غير مسجل في قاعدة الموردين: \"$line\"")
                    break
                }
            }
        }

        // 2. Detect Invoice Number
        val invRegex = Regex("""(?:فاتورة|رقم|invoice|inv|bill|no)[:\s#]*([A-Za-z0-9\-_]+)""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = invRegex.find(line)
            if (match != null) {
                invoiceNum = match.groupValues[1]
                break
            }
        }

        // 3. Detect Date
        val dateRegex = Regex("""(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{4})""")
        for (line in lines) {
            val match = dateRegex.find(line)
            if (match != null) {
                val str = match.groupValues[1]
                extractedDateMillis = parseDateSafe(str)
                if (extractedDateMillis != null) break
            }
        }

        // 4. Line Items & Amounts Extraction
        val amountRegex = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""")
        for (line in lines) {
            val lowerLine = line.lowercase()
            if (lowerLine.contains("إجمالي") || lowerLine.contains("الاجمالي") || lowerLine.contains("total") || lowerLine.contains("الصافي")) {
                val match = amountRegex.findAll(line).lastOrNull()
                if (match != null) {
                    val amt = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    extractedTotalMinor = (amt * 100.0).toLong()
                }
                continue
            }
            if (lowerLine.contains("مدفوع") || lowerLine.contains("مسدد") || lowerLine.contains("paid") || lowerLine.contains("نقد")) {
                val match = amountRegex.findAll(line).lastOrNull()
                if (match != null) {
                    val amt = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    extractedPaidMinor = (amt * 100.0).toLong()
                }
                continue
            }

            // Match product catalog lines
            val matchedProduct = findBestProductMatch(line, products)
            val numbersInLine = amountRegex.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()

            if (numbersInLine.isNotEmpty() && (matchedProduct != null || line.length >= 4)) {
                val qty = if (numbersInLine.size >= 2) numbersInLine.first().toInt().coerceIn(1, 1000) else 1
                val price = if (numbersInLine.size >= 2) {
                    (numbersInLine[1] * 100.0).toLong()
                } else if (numbersInLine.size == 1) {
                    (numbersInLine[0] * 100.0).toLong()
                } else 0L

                val lineTotal = if (numbersInLine.size >= 3) {
                    (numbersInLine[2] * 100.0).toLong()
                } else {
                    qty.toLong() * price
                }

                val isDoubtful = matchedProduct == null || price <= 0L
                if (isDoubtful && matchedProduct == null) {
                    doubtful.add("صنف غير مطابق بالكتالوج: \"${line.take(30)}\"")
                }

                extractedItems.add(
                    OcrExtractedLineItem(
                        productNameRaw = line,
                        matchedProductId = matchedProduct?.productId,
                        matchedProductNameArabic = matchedProduct?.nameArabic,
                        quantity = qty,
                        unitPriceMinor = price,
                        lineTotalMinor = lineTotal,
                        isDoubtful = isDoubtful
                    )
                )
            }
        }

        if (extractedTotalMinor <= 0L && extractedItems.isNotEmpty()) {
            extractedTotalMinor = extractedItems.sumOf { it.lineTotalMinor }
        }
        if (extractedPaidMinor <= 0L) {
            extractedPaidMinor = extractedTotalMinor
        }

        // Calculate confidence
        var confidence = 90
        if (detectedSupplier == null) confidence -= 20
        if (extractedTotalMinor <= 0L) confidence -= 30
        if (extractedItems.any { it.isDoubtful }) confidence -= 15
        if (invoiceNum == null) confidence -= 10
        confidence = confidence.coerceIn(10, 98)

        val isPurchase = detectedSupplier != null || extractedItems.isNotEmpty()

        OcrExtractionResult(
            isPurchaseInvoice = isPurchase,
            supplierNameRaw = detectedSupplier?.companyName,
            matchedSupplierId = detectedSupplier?.localId,
            matchedSupplierName = detectedSupplier?.companyName,
            invoiceNumberRaw = invoiceNum,
            dateParsed = extractedDateMillis ?: System.currentTimeMillis(),
            items = extractedItems,
            totalAmountMinor = extractedTotalMinor,
            paidAmountMinor = extractedPaidMinor,
            overallConfidencePercent = confidence,
            doubtfulFieldsArabic = doubtful,
            rawTextExtracted = ocrRawText
        )
    }

    /**
     * Converts OCR extraction into a reviewable [TransactionDraft].
     */
    fun convertToTransactionDraft(ocrResult: OcrExtractionResult): TransactionDraft {
        val draftItems = ocrResult.items.map { item ->
            TransactionDraftItem(
                productId = item.matchedProductId ?: "PRD-TEMP",
                productNameArabic = item.matchedProductNameArabic ?: item.productNameRaw,
                sku = "OCR-ITEM",
                quantity = item.quantity,
                unitPriceMinor = item.unitPriceMinor
            )
        }

        val missing = mutableListOf<String>()
        if (ocrResult.matchedSupplierId == null) {
            missing.add("المورد غير محدد — يرجى اختيار مورد مسجل لفاتورة الشراء")
        }
        if (ocrResult.totalAmountMinor <= 0L) {
            missing.add("قيمة الفاتورة الإجمالية غير واضحة")
        }

        val type = if (ocrResult.isPurchaseInvoice) UnifiedTransactionType.PURCHASE else UnifiedTransactionType.EXPENSE

        return TransactionDraft(
            transactionType = type,
            inputModality = TransactionInputModality.CAMERA_OCR,
            rawPromptOrInput = ocrResult.rawTextExtracted,
            counterpartyId = ocrResult.matchedSupplierId,
            counterpartyName = ocrResult.matchedSupplierName,
            items = draftItems,
            totalAmountMinor = ocrResult.totalAmountMinor,
            paidAmountMinor = ocrResult.paidAmountMinor,
            paymentMode = if (ocrResult.paidAmountMinor < ocrResult.totalAmountMinor) "PARTIAL" else "CASH",
            notes = "فاتورة شراء بالمسح الضوئي OCR #${ocrResult.invoiceNumberRaw ?: ""}".trim(),
            confidencePercent = ocrResult.overallConfidencePercent,
            missingRequirementsArabic = missing
        )
    }

    private fun findBestProductMatch(line: String, products: List<ProductInventoryProjection>): ProductInventoryProjection? {
        val lower = line.lowercase()
        return products.firstOrNull { p ->
            lower.contains(p.sku.lowercase()) ||
                (p.barcode != null && lower.contains(p.barcode.lowercase())) ||
                p.nameArabic.lowercase().split(" ").filter { it.length >= 3 }.any { lower.contains(it) }
        }
    }

    private fun parseDateSafe(dateStr: String): Long? {
        val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy")
        for (f in formats) {
            try {
                val parsed = SimpleDateFormat(f, Locale.US).parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return null
    }
}

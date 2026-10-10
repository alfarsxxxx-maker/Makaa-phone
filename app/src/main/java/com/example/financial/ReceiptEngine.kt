package com.example.financial

import com.example.database.entities.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P0-N — Domain-Driven Receipt Engine.
 * Generates authoritative, reusable receipt documents directly from domain data.
 * Supports:
 * - Sale Receipts
 * - Wallet Transaction Receipts
 * - Customer Statement Receipts
 * - Cash Shift Closeout Receipts
 */
enum class DomainReceiptType(val arabicTitle: String) {
    SALE("فاتورة مبيعات"),
    WALLET("إيصال معاملة محفظة"),
    CUSTOMER_STATEMENT("كشف حساب عميل"),
    SHIFT_SUMMARY("تقرير إغلاق وردية")
}

data class DomainReceiptLineItem(
    val nameArabic: String,
    val quantity: Int,
    val unitPriceFormatted: String,
    val totalFormatted: String
)

data class DomainReceiptDocument(
    val receiptType: DomainReceiptType,
    val shopNameArabic: String,
    val shopPhone: String,
    val referenceNumber: String,
    val dateTimeFormatted: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val items: List<DomainReceiptLineItem> = emptyList(),
    val principalAmountFormatted: String,
    val discountFormatted: String? = null,
    val commissionFormatted: String? = null,
    val providerCostFeeFormatted: String? = null,
    val totalPayableFormatted: String,
    val paymentMethodArabic: String,
    val statusArabic: String,
    val cashierName: String,
    val notesArabic: String = "",
    val footerArabic: String = "شكراً لتعاملكم معنا • مكة فون"
)

object ReceiptEngine {

    private fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.forLanguageTag("ar-EG"))
        return sdf.format(Date(millis))
    }

    fun buildSaleReceipt(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        customer: CustomerEntity?,
        shopName: String = "مكة فون",
        shopPhone: String = "01000000000",
        cashierName: String = "الكاشير"
    ): DomainReceiptDocument {
        val lineItems = items.map { item ->
            DomainReceiptLineItem(
                nameArabic = item.serialOrImei?.let { "صنف #${item.productId.takeLast(4)} (IMEI: $it)" } ?: "صنف #${item.productId.takeLast(6)}",
                quantity = item.quantity,
                unitPriceFormatted = Money.ofMinor(item.unitSellingPriceMinor).formatArabic(),
                totalFormatted = Money.ofMinor(item.lineTotalMinor).formatArabic()
            )
        }

        return DomainReceiptDocument(
            receiptType = DomainReceiptType.SALE,
            shopNameArabic = shopName,
            shopPhone = shopPhone,
            referenceNumber = sale.invoiceNumber,
            dateTimeFormatted = formatTimestamp(sale.saleTimestamp),
            customerName = customer?.fullName ?: "عميل نقدي",
            customerPhone = customer?.phone,
            items = lineItems,
            principalAmountFormatted = Money.ofMinor(sale.subtotalMinor).formatArabic(),
            discountFormatted = if (sale.discountMinor > 0L) Money.ofMinor(sale.discountMinor).formatArabic() else null,
            totalPayableFormatted = Money.ofMinor(sale.totalAmountMinor).formatArabic(),
            paymentMethodArabic = when {
                sale.paymentAccountType.contains("WALLET") -> "محفظة إلكترونية"
                sale.paymentAccountType.contains("SPLIT") -> "دفع مجزأ (نقدي + محفظة)"
                else -> "نقداً (الخزينة)"
            },
            statusArabic = when (sale.status) {
                "COMPLETED" -> "مكتملة ومسددة"
                "PARTIALLY_REFUNDED" -> "مرتجع جزئي"
                "REFUNDED" -> "مرتجع كامل"
                else -> sale.status
            },
            cashierName = cashierName,
            notesArabic = sale.notes
        )
    }

    fun buildWalletReceipt(
        transfer: TransferEntity,
        walletNameArabic: String,
        shopName: String = "مكة فون",
        shopPhone: String = "01000000000",
        cashierName: String = "الكاشير"
    ): DomainReceiptDocument {
        val opArabic = when (transfer.transferKind) {
            "WALLET_DEPOSIT" -> "إيداع محفظة"
            "WALLET_WITHDRAW" -> "سحب محفظة"
            "WALLET_TRANSFER" -> "تحويل بين المحافظ"
            else -> transfer.transferKind
        }

        val totalCollected = transfer.principalAmountMinor + transfer.shopCommissionMinor

        return DomainReceiptDocument(
            receiptType = DomainReceiptType.WALLET,
            shopNameArabic = shopName,
            shopPhone = shopPhone,
            referenceNumber = transfer.transferReference,
            dateTimeFormatted = formatTimestamp(transfer.transferTimestamp),
            customerName = "عميل محفظة",
            customerPhone = transfer.customerPhoneOrAccount,
            principalAmountFormatted = Money.ofMinor(transfer.principalAmountMinor).formatArabic(),
            commissionFormatted = Money.ofMinor(transfer.shopCommissionMinor).formatArabic(),
            providerCostFeeFormatted = if (transfer.providerFeeMinor > 0L) Money.ofMinor(transfer.providerFeeMinor).formatArabic() else null,
            totalPayableFormatted = Money.ofMinor(totalCollected).formatArabic(),
            paymentMethodArabic = "$walletNameArabic ($opArabic)",
            statusArabic = "معاملة ناجحة ومرحلة",
            cashierName = cashierName,
            notesArabic = transfer.notes
        )
    }

    fun buildCustomerStatementReceipt(
        customer: CustomerEntity,
        transactions: List<TransactionEntity>,
        currentBalanceMinor: Long,
        shopName: String = "مكة فون",
        shopPhone: String = "01000000000"
    ): DomainReceiptDocument {
        val lineItems = transactions.take(15).map { tx ->
            DomainReceiptLineItem(
                nameArabic = "${tx.transactionType.arabicLabel}: ${tx.descriptionArabic}",
                quantity = 1,
                unitPriceFormatted = Money.ofMinor(tx.amountMinor).formatArabic(),
                totalFormatted = Money.ofMinor(tx.amountMinor).formatArabic()
            )
        }

        return DomainReceiptDocument(
            receiptType = DomainReceiptType.CUSTOMER_STATEMENT,
            shopNameArabic = shopName,
            shopPhone = shopPhone,
            referenceNumber = "STMT-${customer.localId.takeLast(6)}",
            dateTimeFormatted = formatTimestamp(System.currentTimeMillis()),
            customerName = customer.fullName,
            customerPhone = customer.phone,
            items = lineItems,
            principalAmountFormatted = Money.ofMinor(currentBalanceMinor).formatArabic(),
            totalPayableFormatted = Money.ofMinor(currentBalanceMinor).formatArabic(),
            paymentMethodArabic = "ذمة مدينة / حساب عميل",
            statusArabic = if (currentBalanceMinor > 0L) "مديونية قائمة" else "حساب مسدد بالكامل",
            cashierName = "إدارة الحسابات",
            notesArabic = "الرصيد القائم واجب السداد: ${Money.ofMinor(currentBalanceMinor).formatArabic()}"
        )
    }

    fun buildShiftReportReceipt(
        breakdown: ShiftCashFlowBreakdown,
        shopName: String = "مكة فون",
        shopPhone: String = "01000000000",
        cashierName: String = "الكاشير"
    ): DomainReceiptDocument {
        val lines = listOf(
            DomainReceiptLineItem("الرصيد الافتتاحي للوردية", 1, Money.ofMinor(breakdown.openingCashMinor).formatArabic(), Money.ofMinor(breakdown.openingCashMinor).formatArabic()),
            DomainReceiptLineItem("مبيعات نقدية", 1, Money.ofMinor(breakdown.cashSalesInMinor).formatArabic(), Money.ofMinor(breakdown.cashSalesInMinor).formatArabic()),
            DomainReceiptLineItem("تحصيلات من العملاء", 1, Money.ofMinor(breakdown.customerCollectionsInMinor).formatArabic(), Money.ofMinor(breakdown.customerCollectionsInMinor).formatArabic()),
            DomainReceiptLineItem("نقدية واردة من المحافظ", 1, Money.ofMinor(breakdown.walletCashInMinor).formatArabic(), Money.ofMinor(breakdown.walletCashInMinor).formatArabic()),
            DomainReceiptLineItem("نقدية منصرفة للمحافظ", 1, Money.ofMinor(breakdown.walletCashOutMinor).formatArabic(), Money.ofMinor(breakdown.walletCashOutMinor).formatArabic()),
            DomainReceiptLineItem("مصروفات تشغيلية", 1, Money.ofMinor(breakdown.expensesCashOutMinor).formatArabic(), Money.ofMinor(breakdown.expensesCashOutMinor).formatArabic()),
            DomainReceiptLineItem("سداد للموردين", 1, Money.ofMinor(breakdown.supplierPaymentsOutMinor).formatArabic(), Money.ofMinor(breakdown.supplierPaymentsOutMinor).formatArabic()),
            DomainReceiptLineItem("مرتجعات مبيعات نقدية", 1, Money.ofMinor(breakdown.refundsCashOutMinor).formatArabic(), Money.ofMinor(breakdown.refundsCashOutMinor).formatArabic())
        )

        return DomainReceiptDocument(
            receiptType = DomainReceiptType.SHIFT_SUMMARY,
            shopNameArabic = shopName,
            shopPhone = shopPhone,
            referenceNumber = breakdown.shiftId,
            dateTimeFormatted = formatTimestamp(breakdown.closedAt ?: System.currentTimeMillis()),
            items = lines,
            principalAmountFormatted = Money.ofMinor(breakdown.expectedClosingCashMinor).formatArabic(),
            totalPayableFormatted = Money.ofMinor(breakdown.countedClosingCashMinor ?: breakdown.expectedClosingCashMinor).formatArabic(),
            paymentMethodArabic = "جرد الخزينة النقدية",
            statusArabic = breakdown.varianceStatus.arabicLabel,
            cashierName = cashierName,
            notesArabic = "الرصيد المتوقع: ${Money.ofMinor(breakdown.expectedClosingCashMinor).formatArabic()} • الفعلي: ${Money.ofMinor(breakdown.countedClosingCashMinor ?: 0L).formatArabic()} • الفرق: ${Money.ofMinor(breakdown.varianceMinor).formatArabic()}"
        )
    }
}

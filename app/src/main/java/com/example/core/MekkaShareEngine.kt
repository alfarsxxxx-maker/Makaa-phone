package com.example.core

import android.content.Context
import android.content.Intent
import com.example.database.entities.CustomerEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransferEntity
import com.example.financial.CustomerFinancialProfile
import com.example.financial.Money
import com.example.financial.ShiftCashFlowBreakdown
import com.example.financial.SupplierFinancialProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified Share Engine (Requirement P1-E & P1-S).
 * Formats clean, professional Arabic receipts & statements and shares them via the native Android Share Sheet.
 */
object MekkaShareEngine {

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.forLanguageTag("ar-EG")).format(Date(millis))
    }

    /**
     * Dispatches text through Android Share Sheet.
     */
    fun shareText(context: Context, title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }
    }

    /**
     * Builds share-ready Customer Statement text.
     */
    fun buildCustomerStatement(
        customer: CustomerEntity,
        profile: CustomerFinancialProfile,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = ""
    ): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ كشف حساب عميل — $shopNameArabic ★")
        if (shopPhone.isNotBlank()) sb.appendLine("هاتف المتجر: $shopPhone")
        sb.appendLine("تاريخ الإصدار: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("العميل: ${customer.fullName}")
        sb.appendLine("الهاتف: ${customer.phone}")
        if (customer.address.isNotBlank()) sb.appendLine("العنوان: ${customer.address}")
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("إجمالي المشتريات: ${Money.ofMinor(profile.totalPurchasesMinor).formatArabic()}")
        sb.appendLine("إجمالي المسدد: ${Money.ofMinor(profile.totalPaymentsMinor).formatArabic()}")
        sb.appendLine("الرصيد المستحق الحالي: ${Money.ofMinor(profile.outstandingAmountMinor).formatArabic()}")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("سجل المعاملات:")

        if (profile.statementLines.isEmpty()) {
            sb.appendLine("لا توجد حركات مسجلة.")
        } else {
            for (line in profile.statementLines.take(30)) {
                sb.appendLine("• ${line.typeLabelArabic} #${line.referenceNumber} | ${formatTimestamp(line.timestamp)}")
                sb.appendLine("  ${line.descriptionArabic}")
                if (line.debitMinor > 0L) sb.appendLine("  مدين (+): ${Money.ofMinor(line.debitMinor).formatArabic()}")
                if (line.creditMinor > 0L) sb.appendLine("  دائن (-): ${Money.ofMinor(line.creditMinor).formatArabic()}")
                sb.appendLine("  الرصيد بعد الحركة: ${Money.ofMinor(line.runningBalanceMinor).formatArabic()}")
                sb.appendLine("────────────────────────────────────")
            }
        }
        sb.appendLine("شكراً لتعاملكم مع $shopNameArabic.")
        return sb.toString()
    }

    /**
     * Builds share-ready Supplier Statement text.
     */
    fun buildSupplierStatement(
        supplier: SupplierEntity,
        profile: SupplierFinancialProfile,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = ""
    ): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ كشف حساب مورد — $shopNameArabic ★")
        if (shopPhone.isNotBlank()) sb.appendLine("هاتف المتجر: $shopPhone")
        sb.appendLine("تاريخ الإصدار: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("المورد: ${supplier.companyName}")
        sb.appendLine("المسؤول: ${supplier.contactName}")
        sb.appendLine("الهاتف: ${supplier.phone}")
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("إجمالي التوريدات: ${Money.ofMinor(profile.totalPurchasesMinor).formatArabic()}")
        sb.appendLine("إجمالي المدفوع للمورد: ${Money.ofMinor(profile.totalPaymentsMinor).formatArabic()}")
        sb.appendLine("المبلغ المستحق للمورد: ${Money.ofMinor(profile.outstandingAmountMinor).formatArabic()}")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("سجل التوريدات والمدفوعات:")

        if (profile.statementLines.isEmpty()) {
            sb.appendLine("لا توجد حركات مسجلة.")
        } else {
            for (line in profile.statementLines.take(30)) {
                sb.appendLine("• ${line.typeLabelArabic} #${line.referenceNumber} | ${formatTimestamp(line.timestamp)}")
                sb.appendLine("  ${line.descriptionArabic}")
                if (line.debitMinor > 0L) sb.appendLine("  مسدد للمورد (+): ${Money.ofMinor(line.debitMinor).formatArabic()}")
                if (line.creditMinor > 0L) sb.appendLine("  فاتورة توريد (-): ${Money.ofMinor(line.creditMinor).formatArabic()}")
                sb.appendLine("  المستحق المتبقي: ${Money.ofMinor(line.runningBalanceMinor).formatArabic()}")
                sb.appendLine("────────────────────────────────────")
            }
        }
        return sb.toString()
    }

    /**
     * Builds share-ready Sale Receipt text.
     */
    fun buildSaleReceipt(
        sale: SaleEntity,
        saleItems: List<SaleItemEntity>,
        customerName: String,
        cashierName: String,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = "",
        productNames: Map<String, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ فاتورة بيع نقدية — $shopNameArabic ★")
        if (shopPhone.isNotBlank()) sb.appendLine("هاتف: $shopPhone")
        sb.appendLine("فاتورة رقم: #${sale.invoiceNumber}")
        sb.appendLine("التاريخ: ${formatTimestamp(sale.saleTimestamp)}")
        sb.appendLine("العميل: $customerName")
        sb.appendLine("الكاشير: $cashierName")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("الأصناف المباعة:")
        for (item in saleItems) {
            val netQty = (item.quantity - item.returnedQuantity).coerceAtLeast(0)
            val lineTotal = netQty * item.unitSellingPriceMinor
            val pName = productNames[item.productId] ?: "صنف #${item.productId.takeLast(6)}"
            sb.appendLine("• $pName")
            sb.appendLine("  الكمية: $netQty × ${Money.ofMinor(item.unitSellingPriceMinor).formatArabic()} = ${Money.ofMinor(lineTotal).formatArabic()}")
        }
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("المجموع الفرعي: ${Money.ofMinor(sale.subtotalMinor).formatArabic()}")
        if (sale.discountMinor > 0L) {
            sb.appendLine("الخصم: -${Money.ofMinor(sale.discountMinor).formatArabic()}")
        }
        sb.appendLine("الإجمالي المستحق: ${Money.ofMinor(sale.totalAmountMinor).formatArabic()}")
        sb.appendLine("المدفوع: ${Money.ofMinor(sale.paidAmountMinor).formatArabic()}")
        val remaining = (sale.totalAmountMinor - sale.paidAmountMinor).coerceAtLeast(0L)
        if (remaining > 0L) {
            sb.appendLine("المتبقي بذمة العميل: ${Money.ofMinor(remaining).formatArabic()}")
        }
        val methodLabel = when (sale.paymentAccountType) {
            "CASH_DRAWER" -> "نقداً (الخزينة)"
            "WALLET" -> "محفظة إلكترونية"
            else -> sale.paymentAccountType
        }
        sb.appendLine("طريقة الدفع: $methodLabel")
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("سعدنا بخدمتكم في $shopNameArabic!")
        return sb.toString()
    }

    /**
     * Builds share-ready Wallet Operation Receipt text.
     */
    fun buildWalletReceipt(
        transfer: TransferEntity,
        walletNameArabic: String,
        cashierName: String,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = ""
    ): String {
        val opArabic = when (transfer.transferKind) {
            "WALLET_DEPOSIT" -> "إيداع محفظة"
            "WALLET_WITHDRAW" -> "سحب نقدية من محفظة"
            "WALLET_TRANSFER" -> "تحويل رصيد"
            else -> transfer.transferKind
        }
        val totalCollected = transfer.principalAmountMinor + transfer.shopCommissionMinor

        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ إيصال عملية محفظة — $shopNameArabic ★")
        if (shopPhone.isNotBlank()) sb.appendLine("هاتف: $shopPhone")
        sb.appendLine("نوع العملية: $opArabic")
        sb.appendLine("المحفظة: $walletNameArabic")
        sb.appendLine("رقم العملية / المرجع: ${transfer.transferReference}")
        sb.appendLine("التاريخ: ${formatTimestamp(transfer.transferTimestamp)}")
        sb.appendLine("رقم هاتف العميل: ${transfer.customerPhoneOrAccount}")
        sb.appendLine("الكاشير: $cashierName")
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("مبلغ العملية: ${Money.ofMinor(transfer.principalAmountMinor).formatArabic()}")
        if (transfer.shopCommissionMinor > 0L) {
            sb.appendLine("رسوم الخدمة والعمولة: ${Money.ofMinor(transfer.shopCommissionMinor).formatArabic()}")
        }
        sb.appendLine("إجمالي المبلغ: ${Money.ofMinor(totalCollected).formatArabic()}")
        sb.appendLine("الحالة: عملية ناجحة ومرحلة بالدفاتر")
        sb.appendLine("════════════════════════════════════")
        return sb.toString()
    }

    /**
     * Builds share-ready Shift Handover & Closing Report text (Requirement P1-H).
     */
    fun buildShiftHandoverReport(
        fromUserName: String,
        toUserName: String,
        breakdown: ShiftCashFlowBreakdown,
        countedCashMinor: Long,
        varianceMinor: Long,
        handoverReason: String,
        timestamp: Long = System.currentTimeMillis(),
        shopNameArabic: String = "مكة فون"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ محضر تسليم وردية — $shopNameArabic ★")
        sb.appendLine("التاريخ والوقت: ${formatTimestamp(timestamp)}")
        sb.appendLine("المسلِّم (الكاشير السابق): $fromUserName")
        sb.appendLine("المستلِم (الكاشير الحالي): $toUserName")
        sb.appendLine("════════════════════════════════════")
        val cashIn = breakdown.manualCashInMinor + breakdown.customerCollectionsInMinor
        val cashOut = breakdown.supplierPaymentsOutMinor + breakdown.manualCashOutMinor
        sb.appendLine("بيان تدفقات النقدية الفعلية:")
        sb.appendLine("• الرصيد الافتتاحي للوردية: ${Money.ofMinor(breakdown.openingCashMinor).formatArabic()}")
        sb.appendLine("• مبيعات نقدية (+): ${Money.ofMinor(breakdown.cashSalesInMinor).formatArabic()}")
        sb.appendLine("• إيداعات وتمويل نقدية (+): ${Money.ofMinor(cashIn).formatArabic()}")
        sb.appendLine("• سحوبات محافظ كاش (+): ${Money.ofMinor(breakdown.walletCashInMinor).formatArabic()}")
        sb.appendLine("• إيداعات محافظ من الدرج (-): ${Money.ofMinor(breakdown.walletCashOutMinor).formatArabic()}")
        sb.appendLine("• مصروفات الوردية (-): ${Money.ofMinor(breakdown.expensesCashOutMinor).formatArabic()}")
        sb.appendLine("• مرتجعات نقدية (-): ${Money.ofMinor(breakdown.refundsCashOutMinor).formatArabic()}")
        sb.appendLine("• سحوبات وتوريدات نقدية (-): ${Money.ofMinor(cashOut).formatArabic()}")
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("النقدية الدفترية المتوقعة: ${Money.ofMinor(breakdown.expectedClosingCashMinor).formatArabic()}")
        sb.appendLine("النقدية الفعلية المسلَّمة: ${Money.ofMinor(countedCashMinor).formatArabic()}")
        val varLabel = when {
            varianceMinor == 0L -> "مطابق تماماً (صفر)"
            varianceMinor > 0L -> "زيادة نقدية (+${Money.ofMinor(varianceMinor).formatArabic()})"
            else -> "عجز نقدي (${Money.ofMinor(varianceMinor).formatArabic()})"
        }
        sb.appendLine("فارق التسليم: $varLabel")
        if (handoverReason.isNotBlank()) {
            sb.appendLine("ملاحظات التسليم: $handoverReason")
        }
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("تم التوثيق إلكترونياً واعتماد محضر التسليم.")
        return sb.toString()
    }

    /**
     * Requirement P2-I: Customer Balance Summary text.
     */
    fun buildCustomerBalanceSummary(
        customerName: String,
        phone: String,
        outstandingBalanceMinor: Long,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = ""
    ): String {
        val amountFormatted = Money.ofMinor(outstandingBalanceMinor).formatArabic()
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ بيان رصيد العميل — $shopNameArabic ★")
        sb.appendLine("تاريخ الإصدار: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("العميل: $customerName | هاتف: $phone")
        sb.appendLine("────────────────────────────────────")
        if (outstandingBalanceMinor > 0L) {
            sb.appendLine("الرصيد المستحق الحالي بذمتكم: $amountFormatted")
            sb.appendLine("متاح السداد كاش في الفرع أو عبر المحافظ الإلكترونية.")
        } else {
            sb.appendLine("الحساب مسدد بالكامل (رصيد صفر). لا توجد أي مستحقات.")
        }
        if (shopPhone.isNotBlank()) sb.appendLine("للتواصل والاستفسار: $shopPhone")
        sb.appendLine("شكراً لتعاملكم الراقي مع $shopNameArabic.")
        sb.appendLine("════════════════════════════════════")
        return sb.toString()
    }

    /**
     * Requirement P2-I: Purchase Summary text for suppliers.
     */
    fun buildPurchaseSummary(
        invoiceNumber: String,
        supplierName: String,
        totalAmountMinor: Long,
        paidAmountMinor: Long,
        remainingMinor: Long,
        itemsCount: Int,
        timestamp: Long = System.currentTimeMillis(),
        shopNameArabic: String = "مكة فون"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════")
        sb.appendLine("★ ملخص فاتورة شراء وتوريد — $shopNameArabic ★")
        sb.appendLine("رقم الفاتورة: #$invoiceNumber")
        sb.appendLine("المورد: $supplierName")
        sb.appendLine("التاريخ: ${formatTimestamp(timestamp)}")
        sb.appendLine("عدد الأصناف الموردة: $itemsCount صنف")
        sb.appendLine("────────────────────────────────────")
        sb.appendLine("إجمالي الفاتورة: ${Money.ofMinor(totalAmountMinor).formatArabic()}")
        sb.appendLine("المبلغ المسدد: ${Money.ofMinor(paidAmountMinor).formatArabic()}")
        sb.appendLine("المتبقي آجل للمورد: ${Money.ofMinor(remainingMinor).formatArabic()}")
        sb.appendLine("الحالة: تم الاستلام والتوريد للمخزن الدفتري بنجاح")
        sb.appendLine("════════════════════════════════════")
        return sb.toString()
    }
}

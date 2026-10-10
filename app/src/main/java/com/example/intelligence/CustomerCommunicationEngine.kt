package com.example.intelligence

import com.example.database.entities.CustomerEntity
import com.example.database.entities.SaleEntity
import com.example.database.entities.TransactionEntity
import com.example.financial.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CustomerFollowUpType {
    OUTSTANDING_BALANCE,
    INACTIVE_CUSTOMER,
    FREQUENT_VALUED_CUSTOMER,
    WELCOME_NEW_CUSTOMER
}

data class CustomerFollowUpOpportunity(
    val customerId: String,
    val customerNameArabic: String,
    val phone: String,
    val type: CustomerFollowUpType,
    val badgeLabelArabic: String,
    val reasonArabic: String,
    val outstandingAmountMinor: Long,
    val totalPurchasesMinor: Long,
    val daysSinceLastActivity: Int
)

data class GeneratedMessageDraft(
    val titleArabic: String,
    val messageBodyArabic: String,
    val recipientPhone: String,
    val recipientName: String,
    val isFinancialDataVerified: Boolean = true
)

object CustomerCommunicationEngine {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Requirement P2-H: Identifies real customer follow-up opportunities grounded purely in ledger & sales data.
     */
    fun detectFollowUpOpportunities(
        customers: List<CustomerEntity>,
        customerBalances: Map<String, Long>,
        sales: List<SaleEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomerFollowUpOpportunity> {
        val opportunities = mutableListOf<CustomerFollowUpOpportunity>()
        val salesByCustomer = sales.filter { it.status != "CANCELLED" }.groupBy { it.customerId }

        for (customer in customers) {
            val balMinor = customerBalances[customer.localId] ?: 0L
            val cSales = salesByCustomer[customer.localId] ?: emptyList()
            val totalPurchased = cSales.sumOf { it.totalAmountMinor }
            val lastSale = cSales.maxByOrNull { it.saleTimestamp }
            val daysSince = if (lastSale != null) {
                ((nowMillis - lastSale.saleTimestamp) / DAY_MILLIS).toInt()
            } else {
                ((nowMillis - customer.createdAt) / DAY_MILLIS).toInt()
            }

            when {
                // 1. Outstanding Balance
                balMinor > 0L -> {
                    opportunities += CustomerFollowUpOpportunity(
                        customerId = customer.localId,
                        customerNameArabic = customer.fullName,
                        phone = customer.phone,
                        type = CustomerFollowUpType.OUTSTANDING_BALANCE,
                        badgeLabelArabic = "مستحق تحصيل",
                        reasonArabic = "يوجد رصيد مستحق بذمة العميل بقيمة ${Money.ofMinor(balMinor).formatArabic()}.",
                        outstandingAmountMinor = balMinor,
                        totalPurchasesMinor = totalPurchased,
                        daysSinceLastActivity = daysSince
                    )
                }
                // 2. High Value Frequent Customer
                cSales.size >= 5 && daysSince <= 14 -> {
                    opportunities += CustomerFollowUpOpportunity(
                        customerId = customer.localId,
                        customerNameArabic = customer.fullName,
                        phone = customer.phone,
                        type = CustomerFollowUpType.FREQUENT_VALUED_CUSTOMER,
                        badgeLabelArabic = "عميل مميز",
                        reasonArabic = "هذا العميل من الأكثر نشاطاً بمعدل ${cSales.size} عمليات مشتريات مسجلة.",
                        outstandingAmountMinor = 0L,
                        totalPurchasesMinor = totalPurchased,
                        daysSinceLastActivity = daysSince
                    )
                }
                // 3. Inactive Customer (> 30 days)
                daysSince >= 30 && cSales.isNotEmpty() -> {
                    opportunities += CustomerFollowUpOpportunity(
                        customerId = customer.localId,
                        customerNameArabic = customer.fullName,
                        phone = customer.phone,
                        type = CustomerFollowUpType.INACTIVE_CUSTOMER,
                        badgeLabelArabic = "عميل غير نشط",
                        reasonArabic = "لم تتم أي عملية مع هذا العميل منذ $daysSince يوماً.",
                        outstandingAmountMinor = balMinor,
                        totalPurchasesMinor = totalPurchased,
                        daysSinceLastActivity = daysSince
                    )
                }
            }
        }
        return opportunities.sortedByDescending { it.outstandingAmountMinor }
    }

    /**
     * Requirement P2-I & P2-J: Generates polite, concise, verified Arabic messages for sharing.
     */
    fun generateCustomerBalanceSummaryMessage(
        customer: CustomerEntity,
        outstandingBalanceMinor: Long,
        shopNameArabic: String = "مكة فون",
        shopPhone: String = ""
    ): GeneratedMessageDraft {
        val amountFormatted = Money.ofMinor(outstandingBalanceMinor).formatArabic()
        val text = buildString {
            appendLine("مرحباً بك أستاذ ${customer.fullName}،")
            appendLine("نتشرف بتعاملك دائماً مع $shopNameArabic.")
            if (outstandingBalanceMinor > 0L) {
                appendLine("نحيط سيادتكم علماً بأن الرصيد المستحق الحالي هو: $amountFormatted.")
                appendLine("متاح السداد عبر المحافظ الإلكترونية، أو بزيارة الفرع.")
            } else {
                appendLine("نحيط سيادتكم علماً بأن حسابكم مسدد بالكامل ولا توجد أي مبالغ مستحقة.")
            }
            if (shopPhone.isNotBlank()) appendLine("للاستفسار: $shopPhone")
            appendLine("شكراً لثقتكم الغالية!")
        }

        return GeneratedMessageDraft(
            titleArabic = "بيان رصيد العميل",
            messageBodyArabic = text.trim(),
            recipientPhone = customer.phone,
            recipientName = customer.fullName,
            isFinancialDataVerified = true
        )
    }

    fun generatePaymentConfirmationMessage(
        customer: CustomerEntity,
        paymentAmountMinor: Long,
        newBalanceMinor: Long,
        paymentReference: String,
        shopNameArabic: String = "مكة فون"
    ): GeneratedMessageDraft {
        val paidFormatted = Money.ofMinor(paymentAmountMinor).formatArabic()
        val balFormatted = Money.ofMinor(newBalanceMinor).formatArabic()
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date())

        val text = buildString {
            appendLine("عزيزنا ${customer.fullName}،")
            appendLine("تم استلام دفعة بقيمة: $paidFormatted بنجاح بتاريخ $dateStr.")
            appendLine("رقم الإيصال: #$paymentReference")
            if (newBalanceMinor > 0L) {
                appendLine("المتبقي بالحساب: $balFormatted.")
            } else {
                appendLine("الحساب مسدد بالكامل (رصيد صفر).")
            }
            appendLine("سعدنا بخدمتكم في $shopNameArabic.")
        }

        return GeneratedMessageDraft(
            titleArabic = "إشعار استلام دفعة",
            messageBodyArabic = text.trim(),
            recipientPhone = customer.phone,
            recipientName = customer.fullName,
            isFinancialDataVerified = true
        )
    }
}

package com.example.financial

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * Supported currencies with explicit minor-unit precision (Requirement 10).
 * For EGP and SAR, 1 major unit = 100 minor units (100 piasters / halalas).
 */
enum class CurrencySpec(
    val code: String,
    val arabicSymbol: String,
    val minorUnitScale: Int,
    val minorFactor: Long
) {
    EGP(code = "EGP", arabicSymbol = "ج.م", minorUnitScale = 2, minorFactor = 100L),
    SAR(code = "SAR", arabicSymbol = "ر.س", minorUnitScale = 2, minorFactor = 100L),
    USD(code = "USD", arabicSymbol = "$", minorUnitScale = 2, minorFactor = 100L);

    companion object {
        fun fromCode(code: String): CurrencySpec =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: EGP
    }
}

/**
 * Explicit rounding modes for monetary division, fee, discount, tax, and Weighted Average Cost calculations.
 */
enum class RoundingPolicy(val javaMode: RoundingMode) {
    HALF_UP(RoundingMode.HALF_UP),
    HALF_EVEN(RoundingMode.HALF_EVEN),
    DOWN(RoundingMode.DOWN),
    UP(RoundingMode.UP),
    FLOOR(RoundingMode.FLOOR),
    CEILING(RoundingMode.CEILING)
}

/**
 * Immutable Money value type backed strictly by 64-bit integer minor units (e.g., piasters).
 * Floating-point types (Double/Float) are forbidden as the source of financial truth.
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencySpec = CurrencySpec.EGP
) : Comparable<Money> {

    val isZero: Boolean get() = amountMinor == 0L
    val isPositive: Boolean get() = amountMinor > 0L
    val isNegative: Boolean get() = amountMinor < 0L

    fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: cannot combine ${currency.code} with ${other.currency.code}"
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currency)
    }

    operator fun times(multiplier: Int): Money =
        Money(Math.multiplyExact(amountMinor, multiplier.toLong()), currency)

    operator fun times(multiplier: Long): Money =
        Money(Math.multiplyExact(amountMinor, multiplier), currency)

    operator fun unaryMinus(): Money =
        Money(Math.negateExact(amountMinor), currency)

    fun absValue(): Money =
        Money(if (amountMinor == Long.MIN_VALUE) throw ArithmeticException("Overflow") else abs(amountMinor), currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    /**
     * Formats integer minor units into a deterministic decimal string without floating-point drift.
     */
    fun toDecimalString(): String {
        val sign = if (amountMinor < 0) "-" else ""
        val absMinor = abs(amountMinor)
        val major = absMinor / currency.minorFactor
        val minor = absMinor % currency.minorFactor
        return String.format(Locale.US, "%s%d.%0${currency.minorUnitScale}d", sign, major, minor)
    }

    fun formatArabic(includeCurrency: Boolean = true): String {
        val sign = if (amountMinor < 0) "-" else ""
        val absMinor = abs(amountMinor)
        val major = absMinor / currency.minorFactor
        val minor = absMinor % currency.minorFactor
        val formattedMajor = String.format(Locale.US, "%,d", major)
        val formattedMinor = String.format(Locale.US, "%0${currency.minorUnitScale}d", minor)
        return if (includeCurrency) {
            "$sign$formattedMajor.$formattedMinor ${currency.arabicSymbol}"
        } else {
            "$sign$formattedMajor.$formattedMinor"
        }
    }

    companion object {
        fun zero(currency: CurrencySpec = CurrencySpec.EGP): Money = Money(0L, currency)

        fun ofMinor(amountMinor: Long, currency: CurrencySpec = CurrencySpec.EGP): Money =
            Money(amountMinor, currency)

        fun ofMajor(majorUnits: Long, minorUnits: Int = 0, currency: CurrencySpec = CurrencySpec.EGP): Money {
            require(minorUnits >= 0 && minorUnits < currency.minorFactor) {
                "Minor units must be in 0 until ${currency.minorFactor}"
            }
            val sign = if (majorUnits < 0) -1L else 1L
            val totalMinor = Math.addExact(
                Math.multiplyExact(abs(majorUnits), currency.minorFactor),
                minorUnits.toLong()
            ) * sign
            return Money(totalMinor, currency)
        }

        /**
         * Parses a user-entered string (supporting Arabic-Indic digits ٠-٩ and standard digits 0-9)
         * into exact minor units using BigDecimal string parsing (never Double/Float).
         */
        fun parseMajorString(
            rawInput: String,
            currency: CurrencySpec = CurrencySpec.EGP,
            roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
        ): Money {
            val normalized = normalizeDigits(rawInput).trim().replace(",", "")
            require(normalized.isNotEmpty()) { "Monetary input cannot be empty" }
            val bd = try {
                BigDecimal(normalized)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid monetary amount: '$rawInput'", e)
            }
            val scaled = bd.multiply(BigDecimal.valueOf(currency.minorFactor))
                .setScale(0, roundingPolicy.javaMode)
            return Money(scaled.longValueExact(), currency)
        }

        fun normalizeDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                    in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                    '٫', ',' -> sb.append('.')
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}

/**
 * Fee rule specification using integer minor units and basis points (100 bps = 1.00%, 10000 bps = 100%).
 */
data class FeeRule(
    val fixedFeeMinor: Long = 0L,
    val rateBasisPoints: Int = 0,
    val minFeeMinor: Long = 0L,
    val maxFeeMinor: Long? = null,
    val roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
) {
    init {
        require(fixedFeeMinor >= 0L) { "Fixed fee cannot be negative" }
        require(rateBasisPoints >= 0) { "Fee basis points cannot be negative" }
        require(minFeeMinor >= 0L) { "Minimum fee cannot be negative" }
        if (maxFeeMinor != null) {
            require(maxFeeMinor >= minFeeMinor) { "Max fee cannot be less than min fee" }
        }
    }
}

/**
 * Complete financial breakdown for an atomic transaction or invoice.
 */
data class InvoiceFinancialBreakdown(
    val grossSubtotal: Money,
    val discountAmount: Money,
    val netTaxableAmount: Money,
    val taxAmount: Money,
    val serviceFeeAmount: Money,
    val commissionAmount: Money,
    val finalPayableTotal: Money
)

/**
 * Deterministic Monetary Calculator (Requirement 10).
 * All rates use basis points (1% = 100 bps, 14% VAT = 1400 bps).
 */
object MonetaryCalculator {
    const val BASIS_POINTS_DIVISOR = 10_000L

    /**
     * Applies a basis-point rate (e.g., 150 bps = 1.5%) to a Money value using exact BigDecimal integer scaling.
     */
    fun applyBasisPoints(
        base: Money,
        basisPoints: Int,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): Money {
        require(basisPoints >= 0) { "Basis points cannot be negative: $basisPoints" }
        if (basisPoints == 0 || base.isZero) return Money.zero(base.currency)
        val exactMinor = BigDecimal.valueOf(base.amountMinor)
            .multiply(BigDecimal.valueOf(basisPoints.toLong()))
            .divide(BigDecimal.valueOf(BASIS_POINTS_DIVISOR), 0, roundingPolicy.javaMode)
            .longValueExact()
        return Money(exactMinor, base.currency)
    }

    /**
     * Calculates provider/wallet fee from a FeeRule.
     */
    fun calculateFee(principal: Money, rule: FeeRule): Money {
        require(!principal.isNegative) { "Principal amount cannot be negative for fee calculation" }
        if (principal.isZero) return Money.zero(principal.currency)
        val proportionalMinor = applyBasisPoints(principal, rule.rateBasisPoints, rule.roundingPolicy).amountMinor
        var rawFee = Math.addExact(rule.fixedFeeMinor, proportionalMinor)
        if (rawFee < rule.minFeeMinor) {
            rawFee = rule.minFeeMinor
        }
        if (rule.maxFeeMinor != null && rawFee > rule.maxFeeMinor) {
            rawFee = rule.maxFeeMinor
        }
        return Money(rawFee, principal.currency)
    }

    /**
     * Calculates shop commission on wallet transfers or services.
     */
    fun calculateCommission(
        principal: Money,
        commissionBasisPoints: Int,
        fixedCommissionMinor: Long = 0L,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): Money {
        require(!principal.isNegative) { "Principal cannot be negative" }
        require(commissionBasisPoints >= 0 && fixedCommissionMinor >= 0L) {
            "Commission parameters cannot be negative"
        }
        if (principal.isZero) return Money.zero(principal.currency)
        val ratePart = applyBasisPoints(principal, commissionBasisPoints, roundingPolicy).amountMinor
        return Money(Math.addExact(ratePart, fixedCommissionMinor), principal.currency)
    }

    /**
     * Calculates discount with a strict guard ensuring discount never exceeds gross subtotal.
     */
    fun calculateDiscount(
        subtotal: Money,
        discountBasisPoints: Int = 0,
        fixedDiscountMinor: Long = 0L,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): Money {
        require(!subtotal.isNegative) { "Subtotal cannot be negative" }
        require(discountBasisPoints in 0..10_000) { "Discount basis points must be between 0 and 10000" }
        require(fixedDiscountMinor >= 0L) { "Fixed discount cannot be negative" }
        val pctPart = applyBasisPoints(subtotal, discountBasisPoints, roundingPolicy).amountMinor
        val combined = Math.addExact(pctPart, fixedDiscountMinor)
        val capped = minOf(combined, subtotal.amountMinor)
        return Money(capped, subtotal.currency)
    }

    /**
     * Calculates exclusive tax (added on top of net amount) or extracts inclusive tax.
     */
    fun calculateTax(
        netOrGrossAmount: Money,
        taxBasisPoints: Int,
        taxInclusive: Boolean = false,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): Money {
        require(!netOrGrossAmount.isNegative) { "Taxable base cannot be negative" }
        require(taxBasisPoints >= 0) { "Tax rate basis points cannot be negative" }
        if (taxBasisPoints == 0 || netOrGrossAmount.isZero) return Money.zero(netOrGrossAmount.currency)

        return if (!taxInclusive) {
            applyBasisPoints(netOrGrossAmount, taxBasisPoints, roundingPolicy)
        } else {
            // Tax = Gross * rate / (10000 + rate)
            val taxMinor = BigDecimal.valueOf(netOrGrossAmount.amountMinor)
                .multiply(BigDecimal.valueOf(taxBasisPoints.toLong()))
                .divide(
                    BigDecimal.valueOf(BASIS_POINTS_DIVISOR + taxBasisPoints.toLong()),
                    0,
                    roundingPolicy.javaMode
                )
                .longValueExact()
            Money(taxMinor, netOrGrossAmount.currency)
        }
    }

    /**
     * Builds a complete, validated invoice breakdown without losing a single minor unit.
     */
    fun calculateInvoiceBreakdown(
        lineTotals: List<Money>,
        currency: CurrencySpec = CurrencySpec.EGP,
        discountBasisPoints: Int = 0,
        fixedDiscountMinor: Long = 0L,
        taxBasisPoints: Int = 0,
        taxInclusive: Boolean = false,
        feeRule: FeeRule? = null,
        commissionBasisPoints: Int = 0,
        fixedCommissionMinor: Long = 0L,
        roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP
    ): InvoiceFinancialBreakdown {
        val subtotal = lineTotals.fold(Money.zero(currency)) { acc, item ->
            require(item.currency == currency) { "Line currency mismatch" }
            require(!item.isNegative) { "Line total cannot be negative" }
            acc + item
        }
        val discount = calculateDiscount(subtotal, discountBasisPoints, fixedDiscountMinor, roundingPolicy)
        val afterDiscount = subtotal - discount
        val tax = calculateTax(afterDiscount, taxBasisPoints, taxInclusive, roundingPolicy)
        val netTaxable = if (taxInclusive) afterDiscount - tax else afterDiscount
        val serviceFee = feeRule?.let { calculateFee(afterDiscount, it) } ?: Money.zero(currency)
        val commission = calculateCommission(afterDiscount, commissionBasisPoints, fixedCommissionMinor, roundingPolicy)
        val finalPayable = if (taxInclusive) {
            afterDiscount + serviceFee + commission
        } else {
            afterDiscount + tax + serviceFee + commission
        }
        return InvoiceFinancialBreakdown(
            grossSubtotal = subtotal,
            discountAmount = discount,
            netTaxableAmount = netTaxable,
            taxAmount = tax,
            serviceFeeAmount = serviceFee,
            commissionAmount = commission,
            finalPayableTotal = finalPayable
        )
    }

    /**
     * Allocates a total Money amount proportionally across weights (e.g., distributing invoice discount across lines)
     * ensuring the sum of allocated amounts equals `total` to the exact minor unit.
     */
    fun allocateProportionally(total: Money, weights: List<Long>): List<Money> {
        require(weights.isNotEmpty()) { "Weights cannot be empty" }
        require(weights.all { it >= 0L }) { "Weights must be non-negative" }
        val sumWeights = weights.sum()
        if (sumWeights == 0L || total.isZero) {
            return weights.map { Money.zero(total.currency) }
        }
        val results = LongArray(weights.size)
        var allocated = 0L
        for (i in weights.indices) {
            val share = (total.amountMinor * weights[i]) / sumWeights
            results[i] = share
            allocated += share
        }
        var remainder = total.amountMinor - allocated
        val step = if (remainder >= 0) 1L else -1L
        var idx = 0
        while (remainder != 0L) {
            if (weights[idx] > 0L) {
                results[idx] += step
                remainder -= step
            }
            idx = (idx + 1) % weights.size
        }
        return results.map { Money(it, total.currency) }
    }
}

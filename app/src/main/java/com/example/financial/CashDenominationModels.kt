package com.example.financial

/**
 * Denomination Counter Domain Models (P0-B: Cash Drawer & Shift Management).
 * Supports standard Egyptian Currency denominations (200, 100, 50, 20, 10, 5, 1 EGP)
 * and is strictly extensible for future denominations or coin units.
 */
data class CashDenomination(
    val valueEgp: Int,
    val labelArabic: String,
    val isPaperNote: Boolean = true
)

data class DenominationEntry(
    val denomination: CashDenomination,
    val count: Int
) {
    val totalEgp: Long get() = denomination.valueEgp.toLong() * count.coerceAtLeast(0)
    val totalMinor: Long get() = totalEgp * 100L
}

data class DenominationCountBreakdown(
    val entries: List<DenominationEntry>,
    val totalCountedMinor: Long,
    val formattedTotalArabic: String
) {
    companion object {
        fun fromCounts(counts: Map<Int, Int>): DenominationCountBreakdown {
            val entries = EgyptianCashDenominations.ALL.map { denom ->
                val qty = counts[denom.valueEgp] ?: 0
                DenominationEntry(denom, qty)
            }
            val totalMinor = entries.sumOf { it.totalMinor }
            return DenominationCountBreakdown(
                entries = entries,
                totalCountedMinor = totalMinor,
                formattedTotalArabic = Money.ofMinor(totalMinor).formatArabic()
            )
        }
    }
}

object EgyptianCashDenominations {
    val NOTE_1000 = CashDenomination(1000, "فئة 1000 ج.م")
    val NOTE_500 = CashDenomination(500, "فئة 500 ج.م")
    val NOTE_200 = CashDenomination(200, "فئة 200 ج.م")
    val NOTE_100 = CashDenomination(100, "فئة 100 ج.م")
    val NOTE_50 = CashDenomination(50, "فئة 50 ج.م")
    val NOTE_20 = CashDenomination(20, "فئة 20 ج.م")
    val NOTE_10 = CashDenomination(10, "فئة 10 ج.م")
    val NOTE_5 = CashDenomination(5, "فئة 5 ج.م")
    val COIN_1 = CashDenomination(1, "فئة 1 ج.م", isPaperNote = false)

    val ALL: List<CashDenomination> = listOf(
        NOTE_1000,
        NOTE_500,
        NOTE_200,
        NOTE_100,
        NOTE_50,
        NOTE_20,
        NOTE_10,
        NOTE_5,
        COIN_1
    )

    fun calculateTotalCountedMinor(counts: Map<Int, Int>): Long {
        return counts.entries.sumOf { (denomValue, count) ->
            if (count > 0) denomValue.toLong() * count * 100L else 0L
        }
    }
}

/**
 * Requirement P1-H: Shift Handover Record.
 */
data class ShiftHandoverRecord(
    val handoverId: String,
    val fromUserId: String,
    val fromUserNameArabic: String,
    val toUserId: String,
    val toUserNameArabic: String,
    val previousShiftId: String,
    val newShiftId: String,
    val countedCashMinor: Long,
    val expectedCashMinor: Long,
    val varianceMinor: Long,
    val handoverReasonArabic: String,
    val timestamp: Long,
    val breakdown: ShiftCashFlowBreakdown
)


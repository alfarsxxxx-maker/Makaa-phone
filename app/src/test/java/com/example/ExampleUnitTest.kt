package com.example

import com.example.core.SyncState
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.InventoryMovementType
import com.example.financial.CurrencySpec
import com.example.financial.FeeRule
import com.example.financial.MonetaryCalculator
import com.example.financial.Money
import com.example.financial.RoundingPolicy
import com.example.inventory.WeightedAverageCostCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Money calculation safety, basis-point fees/commissions/discounts/taxes,
 * and Weighted Average Cost (WAC) inventory reproducibility (Requirements 10, 12, 13, 26).
 */
class ExampleUnitTest {

    @Test
    fun `money minor units arithmetic and Arabic digit parsing are exact`() {
        val m1 = Money.parseMajorString("١٢٥٠.٧٥", CurrencySpec.EGP)
        val m2 = Money.ofMajor(249, 25, CurrencySpec.EGP)
        assertEquals(125_075L, m1.amountMinor)
        assertEquals(24_925L, m2.amountMinor)

        val sum = m1 + m2
        assertEquals(150_000L, sum.amountMinor)
        assertEquals("1500.00", sum.toDecimalString())
    }

    @Test
    fun `monetary calculator computes fees commissions discounts and taxes accurately`() {
        val line1 = Money.ofMajor(10_000, 0, CurrencySpec.EGP) // 10,000.00 EGP
        val line2 = Money.ofMajor(5_000, 0, CurrencySpec.EGP)  // 5,000.00 EGP

        val breakdown = MonetaryCalculator.calculateInvoiceBreakdown(
            lineTotals = listOf(line1, line2),
            discountBasisPoints = 500, // 5% = 750.00 EGP
            taxBasisPoints = 1400,     // 14% VAT on 14,250.00 = 1,995.00 EGP
            feeRule = FeeRule(fixedFeeMinor = 5_00L, rateBasisPoints = 100), // 5.00 + 1% of 14,250 = 147.50 EGP
            commissionBasisPoints = 50 // 0.5% of 14,250 = 71.25 EGP
        )

        assertEquals(1_500_000L, breakdown.grossSubtotal.amountMinor)
        assertEquals(75_000L, breakdown.discountAmount.amountMinor)
        assertEquals(1_425_000L, breakdown.netTaxableAmount.amountMinor)
        assertEquals(199_500L, breakdown.taxAmount.amountMinor)
        assertEquals(14_750L, breakdown.serviceFeeAmount.amountMinor)
        assertEquals(7_125L, breakdown.commissionAmount.amountMinor)
        assertEquals(1_646_375L, breakdown.finalPayableTotal.amountMinor)
    }

    @Test
    fun `proportional allocation never loses a single minor unit`() {
        val total = Money.ofMinor(10_000L) // 100.00 EGP
        val allocated = MonetaryCalculator.allocateProportionally(total, listOf(1L, 1L, 1L))
        assertEquals(3, allocated.size)
        assertEquals(10_000L, allocated.sumOf { it.amountMinor })
        assertTrue(allocated.all { it.amountMinor == 3_333L || it.amountMinor == 3_334L })
    }

    @Test
    fun `weighted average cost calculator computes and reconstructs valuation from movement stream`() {
        // Inbound 1: 10 units @ 1,000.00 EGP (100,000 minor) -> WAC = 100,000
        val wac1 = WeightedAverageCostCalculator.computeWeightedAverageCostMinor(
            currentQuantity = 0,
            currentWacMinor = 0L,
            movementType = InventoryMovementType.PURCHASE_IN,
            movementQuantity = 10,
            movementUnitCostMinor = 100_000L
        )
        assertEquals(100_000L, wac1)

        // Inbound 2: 10 units @ 1,200.00 EGP (120,000 minor) -> WAC = 110,000 (1,100.00 EGP)
        val wac2 = WeightedAverageCostCalculator.computeWeightedAverageCostMinor(
            currentQuantity = 10,
            currentWacMinor = wac1,
            movementType = InventoryMovementType.PURCHASE_IN,
            movementQuantity = 10,
            movementUnitCostMinor = 120_000L
        )
        assertEquals(110_000L, wac2)

        // Outbound Sale: 5 units -> WAC remains 110,000, remaining qty = 15
        val wac3 = WeightedAverageCostCalculator.computeWeightedAverageCostMinor(
            currentQuantity = 20,
            currentWacMinor = wac2,
            movementType = InventoryMovementType.SALE_OUT,
            movementQuantity = 5,
            movementUnitCostMinor = wac2,
            roundingPolicy = RoundingPolicy.HALF_UP
        )
        assertEquals(110_000L, wac3)

        val movements = listOf(
            InventoryMovementEntity(
                localId = "M1",
                shopId = "S1",
                productId = "P1",
                movementType = InventoryMovementType.PURCHASE_IN,
                quantity = 10,
                signedQuantityDelta = 10,
                quantityBefore = 0,
                quantityAfter = 10,
                unitCostMinor = 100_000L,
                totalCostMinor = 1_000_000L,
                weightedAvgCostBeforeMinor = 0L,
                weightedAvgCostAfterMinor = wac1,
                reason = "P1",
                referenceType = "PURCHASE",
                referenceId = "R1",
                performedByUserId = "U1",
                deviceId = "D1",
                timestamp = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
                syncState = SyncState.PENDING
            ),
            InventoryMovementEntity(
                localId = "M2",
                shopId = "S1",
                productId = "P1",
                movementType = InventoryMovementType.PURCHASE_IN,
                quantity = 10,
                signedQuantityDelta = 10,
                quantityBefore = 10,
                quantityAfter = 20,
                unitCostMinor = 120_000L,
                totalCostMinor = 1_200_000L,
                weightedAvgCostBeforeMinor = wac1,
                weightedAvgCostAfterMinor = wac2,
                reason = "P2",
                referenceType = "PURCHASE",
                referenceId = "R2",
                performedByUserId = "U1",
                deviceId = "D1",
                timestamp = 2000L,
                createdAt = 2000L,
                updatedAt = 2000L,
                syncState = SyncState.PENDING
            ),
            InventoryMovementEntity(
                localId = "M3",
                shopId = "S1",
                productId = "P1",
                movementType = InventoryMovementType.SALE_OUT,
                quantity = 5,
                signedQuantityDelta = -5,
                quantityBefore = 20,
                quantityAfter = 15,
                unitCostMinor = wac2,
                totalCostMinor = 550_000L,
                weightedAvgCostBeforeMinor = wac2,
                weightedAvgCostAfterMinor = wac3,
                reason = "S1",
                referenceType = "SALE",
                referenceId = "R3",
                performedByUserId = "U1",
                deviceId = "D1",
                timestamp = 3000L,
                createdAt = 3000L,
                updatedAt = 3000L,
                syncState = SyncState.PENDING
            )
        )

        val reconstructed = WeightedAverageCostCalculator.reconstructFromHistory("P1", movements)
        assertEquals(15, reconstructed.currentQuantity)
        assertEquals(110_000L, reconstructed.weightedAvgCostMinor)
        assertEquals(1_650_000L, reconstructed.totalInventoryValuationMinor)
    }
}

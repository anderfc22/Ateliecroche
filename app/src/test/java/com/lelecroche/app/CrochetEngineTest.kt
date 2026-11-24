package com.lelecroche.app

import org.junit.Assert.*
import org.junit.Test

class CrochetEngineTest {
    @Test fun convertsMetersToKilograms() {
        val yarn = CrochetEngine.Material(name = "Barbante", unit = "kg", metersPerKg = 200.0)
        assertEquals(0.5, CrochetEngine.convert(yarn, 100.0, "m"), 1e-9)
    }

    @Test fun convertsKilogramsToMeters() {
        val yarn = CrochetEngine.Material(name = "Barbante", unit = "m", metersPerKg = 200.0)
        assertEquals(400.0, CrochetEngine.convert(yarn, 2.0, "kg"), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun doesNotConvertUnits() {
        CrochetEngine.convert(CrochetEngine.Material(name = "Botão", unit = "un"), 2.0, "g")
    }

    @Test fun calculatesFullProductCostAndSuggestedPrice() {
        val yarn = CrochetEngine.Material(name = "Fio", unit = "kg", averageCost = 80.0)
        val recipe = listOf(CrochetEngine.RecipeLine(yarn, 0.1, "kg"))
        val cost = CrochetEngine.calculateCost(recipe, laborMinutes = 120.0, hourlyRate = 15.0, overheadPerPiece = 2.0)
        assertEquals(8.0, cost.materials, 1e-9)
        assertEquals(30.0, cost.labor, 1e-9)
        assertEquals(2.0, cost.overhead, 1e-9)
        assertEquals(40.0, cost.total, 1e-9)
        val pricing = CrochetEngine.calculatePricing(cost, null, 100.0, null)
        assertEquals(80.0, pricing.suggestedPrice, 1e-9)
        assertEquals(80.0, pricing.finalPrice, 1e-9)
    }

    @Test fun calculatesWeightedAveragePurchaseCost() {
        val average = CrochetEngine.weightedAverageCost(2.0, 10.0, 2.0, 60.0)
        assertEquals(20.0, average, 1e-9)
    }

    @Test fun suggestsReplenishmentToDoubleMinimumStock() {
        assertEquals(7.5, CrochetEngine.shoppingSuggestion(10.0, 12.5), 1e-9)
        assertEquals(0.0, CrochetEngine.shoppingSuggestion(10.0, 25.0), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeMaterialStock() {
        CrochetEngine.stockAfter(1.0, -2.0, "Fio", "g")
    }

    @Test fun productionMovesOnlyForward() {
        assertEquals("andamento", CrochetEngine.productionNext("fila"))
        CrochetEngine.validateProductionTransition("fila", "andamento")
        CrochetEngine.validateProductionTransition("andamento", "acabamento")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBackwardProductionTransition() {
        CrochetEngine.validateProductionTransition("acabamento", "andamento")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSkippingProductionStages() {
        CrochetEngine.validateProductionTransition("fila", "pronta")
    }

    @Test fun validatesDepositsPaymentsAndRefunds() {
        CrochetEngine.validateDeposit(30.0, 100.0)
        CrochetEngine.validatePayment(20.0, balance = 70.0, paid = 30.0)
        CrochetEngine.validatePayment(-10.0, balance = 70.0, paid = 30.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPaymentAboveBalance() {
        CrochetEngine.validatePayment(80.0, balance = 70.0, paid = 30.0)
    }

    @Test fun computesWeightedProductionProgress() {
        assertEquals(55, CrochetEngine.orderProgress(listOf(1.0 to "andamento", 1.0 to "acabamento"), "aberto"))
    }

    @Test fun manualPriceOverridesSuggestedPrice() {
        val cost = CrochetEngine.CostBreakdown(8.0, 30.0, 2.0, 40.0)
        val pricing = CrochetEngine.calculatePricing(cost, null, 100.0, 50.0)
        assertEquals(80.0, pricing.suggestedPrice, 1e-9)
        assertEquals(50.0, pricing.finalPrice, 1e-9)
        assertEquals(20.0, pricing.realMarginPercent, 1e-9)
    }

    @Test fun aggregatesMaterialDemandForRepeatedRecipeLines() {
        val yarn = CrochetEngine.Material(id = 7, name = "Fio", unit = "kg", averageCost = 10.0)
        val recipe = listOf(
            CrochetEngine.RecipeLine(yarn, 0.1, "kg"),
            CrochetEngine.RecipeLine(yarn, 0.2, "kg")
        )
        assertEquals(0.9, CrochetEngine.productionDemand(recipe, 3.0)[7L]!!, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blocksProductionWhenMaterialsAreInsufficient() {
        val yarn = CrochetEngine.Material(id = 1, name = "Fio", unit = "kg", stock = 1.0)
        CrochetEngine.validateProductionMaterials(mapOf(yarn to 1.5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDepositAboveOrderTotal() {
        CrochetEngine.validateDeposit(101.0, 100.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRefundAboveAmountPaid() {
        CrochetEngine.validatePayment(-31.0, balance = 70.0, paid = 30.0)
    }
}
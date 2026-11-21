package com.lelecroche.app

import java.math.BigDecimal
import java.math.RoundingMode

/** Regras de domínio portadas do lib.php do site Lelê Crochê. */
object CrochetEngine {
    const val EPSILON = 1e-9
    val productionFlow = listOf("fila", "andamento", "acabamento", "pronta")
    val stageWeight = mapOf("fila" to 0, "andamento" to 35, "acabamento" to 75, "pronta" to 100)

    data class Material(val id: Long = 0, val name: String, val unit: String, val metersPerKg: Double = 0.0, val stock: Double = 0.0, val minStock: Double = 0.0, val averageCost: Double = 0.0)
    data class RecipeLine(val material: Material, val quantity: Double, val unit: String)
    data class CostBreakdown(val materials: Double, val labor: Double, val overhead: Double, val total: Double)
    data class ProductPricing(val cost: CostBreakdown, val suggestedPrice: Double, val finalPrice: Double, val realMarginPercent: Double)

    fun round2(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Conversão original: mesma unidade; kg <-> m exige metros por kg; unidade não converte. */
    fun convert(material: Material, quantity: Double, fromUnit: String): Double {
        require(quantity.isFinite()) { "Quantidade inválida" }
        require(fromUnit in setOf("kg", "m", "un") && material.unit in setOf("kg", "m", "un")) { "Unidade deve ser kg, m ou un" }
        if (fromUnit == material.unit) return quantity
        require(fromUnit != "un" && material.unit != "un") { "'${material.name}' é controlado em ${material.unit}; use a mesma unidade" }
        require(material.metersPerKg > 0.0) { "Informe os metros por kg de '${material.name}' para converter entre kg e m" }
        return if (material.unit == "kg") quantity / material.metersPerKg else quantity * material.metersPerKg
    }

    fun calculateCost(recipe: List<RecipeLine>, laborMinutes: Double, hourlyRate: Double, overheadPerPiece: Double): CostBreakdown {
        require(laborMinutes >= 0 && hourlyRate >= 0 && overheadPerPiece >= 0)
        val materials = recipe.sumOf { line ->
            require(line.quantity > 0) { "Quantidade da receita deve ser maior que zero" }
            convert(line.material, line.quantity, line.unit) * line.material.averageCost
        }
        val labor = laborMinutes / 60.0 * hourlyRate
        return CostBreakdown(round2(materials), round2(labor), round2(overheadPerPiece), round2(materials + labor + overheadPerPiece))
    }

    fun calculatePricing(cost: CostBreakdown, marginPercent: Double?, defaultMarginPercent: Double, manualPrice: Double?): ProductPricing {
        val margin = marginPercent ?: defaultMarginPercent
        require(margin >= 0)
        val suggested = cost.total * (1.0 + margin / 100.0)
        val final = manualPrice ?: suggested
        require(final >= 0)
        val realMargin = if (final > 0) (final - cost.total) / final * 100.0 else 0.0
        return ProductPricing(cost, round2(suggested), round2(final), round1(realMargin))
    }

    fun weightedAverageCost(currentStock: Double, currentAverage: Double, purchaseQty: Double, purchaseTotal: Double): Double {
        require(currentStock >= 0 && currentAverage >= 0 && purchaseQty > 0 && purchaseTotal >= 0)
        val totalQty = currentStock + purchaseQty
        return if (totalQty > 0) (currentStock * currentAverage + purchaseTotal) / totalQty else currentAverage
    }

    fun shoppingSuggestion(minimumStock: Double, currentStock: Double): Double =
        if (minimumStock > 0) round3((minimumStock * 2.0 - currentStock).coerceAtLeast(0.0)) else 0.0

    fun stockAfter(currentStock: Double, delta: Double, materialName: String, unit: String): Double {
        val next = currentStock + delta
        require(next >= -EPSILON) { "Estoque insuficiente de '$materialName': há ${formatQuantity(currentStock)} $unit, faltam ${formatQuantity(-next)} $unit" }
        return round6(next.coerceAtLeast(0.0))
    }

    fun productionNext(current: String): String {
        val index = productionFlow.indexOf(current)
        require(index >= 0 && index < productionFlow.lastIndex) { "Ordem de produção já está '$current'" }
        return productionFlow[index + 1]
    }

    fun validateProductionTransition(current: String, target: String) {
        if (current == "pronta" || current == "cancelada") throw IllegalArgumentException("Ordem já está '$current'")
        if (target == "cancelada") return
        val from = productionFlow.indexOf(current)
        val to = productionFlow.indexOf(target)
        require(to >= 0 && from >= 0 && to > from) { "Transição inválida: $current -> $target" }
    }

    fun productionDemand(recipe: List<RecipeLine>, productionQuantity: Double): Map<Long, Double> {
        require(productionQuantity > 0)
        return recipe.groupBy { it.material.id }.mapValues { (_, lines) -> lines.sumOf { convert(it.material, it.quantity, it.unit) * productionQuantity } }
    }

    fun validateProductionMaterials(demand: Map<Material, Double>) {
        val missing = demand.filter { (material, needed) -> material.stock + EPSILON < needed }
        require(missing.isEmpty()) { "Material insuficiente para concluir: " + missing.entries.joinToString("; ") { (m, q) -> "${m.name} (faltam ${formatQuantity(q - m.stock)} ${m.unit})" } }
    }

    fun validateDeposit(deposit: Double, total: Double) {
        require(deposit >= 0) { "Sinal inválido" }
        require(deposit <= total + EPSILON) { "Sinal maior que o total do pedido" }
    }

    fun validatePayment(amount: Double, balance: Double, paid: Double) {
        require(amount.isFinite() && amount != 0.0) { "Valor deve ser diferente de zero" }
        require(!(amount > 0 && amount > balance + EPSILON)) { "Valor maior que o saldo do pedido" }
        require(!(amount < 0 && -amount > paid + EPSILON)) { "Estorno maior que o total já pago" }
    }

    fun orderProgress(production: List<Pair<Double, String>>, orderStatus: String): Int {
        if (orderStatus == "entregue") return 100
        val total = production.sumOf { it.first }
        if (total <= 0) return 0
        val weighted = production.sumOf { (qty, status) -> qty * (stageWeight[status] ?: 0) }
        return BigDecimal.valueOf(weighted / total).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    private fun round1(value: Double) = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    private fun round3(value: Double) = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toDouble()
    private fun round6(value: Double) = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toDouble()
    private fun formatQuantity(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

package com.lelecroche.app

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Operações transacionais do ateliê, portadas das regras do lib.php para SQLite local. */
class CrochetRepository(private val helper: AppDatabase) {
    data class OrderLineInput(val productId: Long, val quantity: Double, val unitPrice: Double? = null, val note: String = "")
    data class InventoryCount(val materialId: Long, val counted: Double)

    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
        pairs.forEach { (key, value) -> when (value) {
            null -> putNull(key); is String -> put(key, value); is Int -> put(key, value); is Long -> put(key, value)
            is Double -> put(key, value); is Float -> put(key, value); is Boolean -> put(key, if (value) 1 else 0)
            else -> put(key, value.toString())
        } }
    }
    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { val result = block(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.double(name: String) = getDouble(getColumnIndexOrThrow(name))
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.nullableDouble(name: String): Double? {
        val i = getColumnIndexOrThrow(name); return if (isNull(i)) null else getDouble(i)
    }
    private fun material(db: SQLiteDatabase, id: Long): CrochetEngine.Material {
        db.rawQuery("SELECT * FROM materials WHERE id=?", arrayOf(id.toString())).use { c ->
            require(c.moveToFirst()) { "Material não encontrado" }
            return CrochetEngine.Material(c.long("id"), c.string("name"), c.string("unit"), c.double("meters_per_kg"), c.double("stock"), c.double("min_stock"), c.double("avg_cost"))
        }
    }
    private fun setting(db: SQLiteDatabase, key: String, fallback: Double): Double {
        db.rawQuery("SELECT svalue FROM settings WHERE skey=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.string("svalue").toDoubleOrNull() ?: fallback else fallback
        }
    }
    private fun moveMaterialStock(db: SQLiteDatabase, m: CrochetEngine.Material, delta: Double, reason: String, ref: String = "", note: String = "") {
        val next = CrochetEngine.stockAfter(m.stock, delta, m.name, m.unit)
        db.update("materials", values("stock" to next), "id=?", arrayOf(m.id.toString()))
        db.insertOrThrow("stock_movements", null, values("material_id" to m.id, "qty" to delta, "reason" to reason, "ref" to ref, "note" to note, "created_at" to now()))
    }

    fun recordPurchase(materialId: Long, quantity: Double, unit: String, totalCost: Double, supplier: String = "", lot: String = "", method: String = ""): Long = transaction { db ->
        require(quantity > 0) { "Quantidade deve ser maior que zero" }; require(totalCost >= 0) { "Custo total inválido" }
        val m = material(db, materialId); val baseQuantity = CrochetEngine.convert(m, quantity, unit)
        require(baseQuantity > 0) { "Quantidade inválida" }
        val average = CrochetEngine.weightedAverageCost(m.stock, m.averageCost, baseQuantity, totalCost)
        db.update("materials", values("avg_cost" to average, "supplier" to supplier.take(120), "lot" to lot.take(60)), "id=?", arrayOf(materialId.toString()))
        val purchaseId = db.insertOrThrow("purchases", null, values("material_id" to materialId, "qty" to baseQuantity, "total_cost" to totalCost, "supplier" to supplier.take(120), "lot" to lot.take(60), "method" to method.take(40), "created_at" to now()))
        moveMaterialStock(db, m, baseQuantity, "compra", purchaseId.toString(), if (lot.isNotBlank()) "lote $lot" else "")
        db.delete("shopping_list", "material_id=?", arrayOf(materialId.toString())); purchaseId
    }

    fun adjustMaterialStock(materialId: Long, quantity: Double, unit: String, reason: String = "ajuste", note: String = "") = transaction { db ->
        require(quantity.isFinite() && quantity != 0.0) { "Quantidade deve ser diferente de zero" }
        val m = material(db, materialId); val delta = CrochetEngine.convert(m, quantity, unit)
        moveMaterialStock(db, m, delta, if (reason == "perda") "perda" else "ajuste", note = note.take(255))
    }

    fun applyInventory(counts: List<InventoryCount>): Int = transaction { db ->
        require(counts.isNotEmpty()) { "Nenhuma contagem informada" }; var changed = 0
        counts.forEach { item -> require(item.counted >= 0) { "Contagem não pode ser negativa" }; val m = material(db, item.materialId); val delta = item.counted - m.stock
            if (kotlin.math.abs(delta) > CrochetEngine.EPSILON) { moveMaterialStock(db, m, delta, "inventario", note = "contagem de inventário"); changed++ }
        }; changed
    }

    private fun productPricing(db: SQLiteDatabase, productId: Long): CrochetEngine.ProductPricing {
        db.rawQuery("SELECT * FROM products WHERE id=?", arrayOf(productId.toString())).use { p ->
            require(p.moveToFirst()) { "Peça não encontrada" }; val recipe = mutableListOf<CrochetEngine.RecipeLine>()
            db.rawQuery("SELECT r.qty,r.unit,m.id material_id,m.name,m.unit material_unit,m.meters_per_kg,m.stock,m.min_stock,m.avg_cost FROM recipe_items r JOIN materials m ON m.id=r.material_id WHERE r.product_id=? ORDER BY r.id", arrayOf(productId.toString())).use { c ->
                while (c.moveToNext()) { val m = CrochetEngine.Material(c.long("material_id"), c.string("name"), c.string("material_unit"), c.double("meters_per_kg"), c.double("stock"), c.double("min_stock"), c.double("avg_cost")); recipe += CrochetEngine.RecipeLine(m, c.double("qty"), c.string("unit")) }
            }
            val cost = CrochetEngine.calculateCost(recipe, p.double("labor_minutes"), setting(db, "hourly_rate", 15.0), setting(db, "overhead_per_piece", 2.0))
            return CrochetEngine.calculatePricing(cost, p.nullableDouble("margin_pct"), setting(db, "default_margin_pct", 100.0), p.nullableDouble("price"))
        }
    }

    fun previewProductPricing(productId: Long): CrochetEngine.ProductPricing = productPricing(helper.readableDatabase, productId)

    fun archiveProduct(productId: Long) = transaction { db ->
        db.rawQuery("SELECT id FROM products WHERE id=?", arrayOf(productId.toString())).use { require(it.moveToFirst()) { "Peça não encontrada" } }
        db.update("products", values("active" to 0), "id=?", arrayOf(productId.toString()))
    }

    fun saveProduct(id: Long?, name: String, description: String, category: String, laborMinutes: Double, manualPrice: Double?, marginPercent: Double?, stock: Double?, inCatalog: Boolean, recipe: List<CrochetEngine.RecipeLine>): Long = transaction { db ->
        require(name.isNotBlank()) { "Informe o nome da peça" }; require(laborMinutes >= 0 && (manualPrice == null || manualPrice >= 0) && (marginPercent == null || marginPercent >= 0)); require(stock == null || stock >= 0)
        recipe.forEach { require(it.quantity > 0) { "Quantidade da receita deve ser maior que zero" }; CrochetEngine.convert(it.material, it.quantity, it.unit) }
        val productId = if (id == null) db.insertOrThrow("products", null, values("name" to name.trim(), "description" to description.take(255), "category" to category.take(40), "labor_minutes" to laborMinutes, "price" to manualPrice, "margin_pct" to marginPercent, "stock" to (stock ?: 0.0), "in_catalog" to if (inCatalog) 1 else 0)) else {
            val oldStock = db.rawQuery("SELECT stock FROM products WHERE id=?", arrayOf(id.toString())).use { c -> require(c.moveToFirst()) { "Peça não encontrada" }; c.double("stock") }
            db.update("products", values("name" to name.trim(), "description" to description.take(255), "category" to category.take(40), "labor_minutes" to laborMinutes, "price" to manualPrice, "margin_pct" to marginPercent, "stock" to (stock ?: oldStock), "in_catalog" to if (inCatalog) 1 else 0), "id=?", arrayOf(id.toString()))
            db.delete("recipe_items", "product_id=?", arrayOf(id.toString())); id
        }
        recipe.forEach { db.insertOrThrow("recipe_items", null, values("product_id" to productId, "material_id" to it.material.id, "qty" to it.quantity, "unit" to it.unit)) }; productId
    }

    private fun takeProducts(db: SQLiteDatabase, lines: List<OrderLineInput>) { lines.forEach { line ->
        db.rawQuery("SELECT name,stock FROM products WHERE id=?", arrayOf(line.productId.toString())).use { c -> require(c.moveToFirst()) { "Peça não encontrada" }; val stock = c.double("stock"); require(stock + CrochetEngine.EPSILON >= line.quantity) { "Estoque insuficiente de '${c.string("name")}': há $stock, solicitado ${line.quantity}" }; db.update("products", values("stock" to (stock - line.quantity)), "id=?", arrayOf(line.productId.toString())) }
    } }

    fun createOrder(customerId: Long?, lines: List<OrderLineInput>, kind: String, channel: String = "", dueDate: String = "", notes: String = "", deposit: Double = 0.0, depositMethod: String = "", autoProduction: Boolean = true): Long = transaction { db ->
        require(lines.isNotEmpty()) { "Adicione ao menos um item" }
        if (customerId != null) db.rawQuery("SELECT id FROM customers WHERE id=?", arrayOf(customerId.toString())).use { require(it.moveToFirst()) { "Cliente não encontrado" } }
        val normalizedKind = if (kind == "encomenda") "encomenda" else "pronta"
        val priced = lines.map { line -> require(line.quantity > 0) { "Quantidade deve ser maior que zero" }; val pricing = productPricing(db, line.productId); Triple(line, line.unitPrice ?: pricing.finalPrice, pricing.cost.total) }
        val total = CrochetEngine.round2(priced.sumOf { it.first.quantity * it.second }); CrochetEngine.validateDeposit(deposit, total)
        if (normalizedKind == "pronta") takeProducts(db, lines)
        val orderId = db.insertOrThrow("orders", null, values("customer_id" to customerId, "channel" to channel.take(40), "kind" to normalizedKind, "status" to if (normalizedKind == "pronta") "entregue" else "aberto", "due_date" to dueDate, "notes" to notes.take(255), "total" to total, "stock_deducted" to if (normalizedKind == "pronta") 1 else 0, "created_at" to now()))
        priced.forEach { (line, price, cost) -> db.insertOrThrow("order_items", null, values("order_id" to orderId, "product_id" to line.productId, "qty" to line.quantity, "unit_price" to price, "unit_cost" to cost, "note" to line.note.take(160)))
            if (normalizedKind == "encomenda" && autoProduction) db.insertOrThrow("production_orders", null, values("product_id" to line.productId, "qty" to line.quantity, "status" to "fila", "order_id" to orderId, "created_at" to now()))
        }
        if (deposit > 0) db.insertOrThrow("payments", null, values("order_id" to orderId, "amount" to deposit, "method" to depositMethod.take(40), "created_at" to now())); orderId
    }

    fun addProduction(productId: Long, quantity: Double, orderId: Long? = null): Long = transaction { db ->
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        db.rawQuery("SELECT id FROM products WHERE id=? AND active=1", arrayOf(productId.toString())).use { require(it.moveToFirst()) { "Peça não encontrada" } }
        if (orderId != null) db.rawQuery("SELECT id FROM orders WHERE id=?", arrayOf(orderId.toString())).use { require(it.moveToFirst()) { "Pedido não encontrado" } }
        db.insertOrThrow("production_orders", null, values("product_id" to productId, "qty" to quantity, "status" to "fila", "order_id" to orderId, "created_at" to now()))
    }

    private fun finishProduction(db: SQLiteDatabase, productionId: Long) {
        db.rawQuery("SELECT product_id,qty FROM production_orders WHERE id=?", arrayOf(productionId.toString())).use { po ->
            require(po.moveToFirst()) { "Ordem de produção não encontrada" }; val productId = po.long("product_id"); val quantity = po.double("qty")
            val productName = db.rawQuery("SELECT name FROM products WHERE id=?", arrayOf(productId.toString())).use { p -> require(p.moveToFirst()); p.string("name") }
            val requirements = mutableListOf<Pair<CrochetEngine.Material, Double>>()
            db.rawQuery("SELECT r.qty,r.unit,m.id material_id,m.name,m.unit material_unit,m.meters_per_kg,m.stock,m.min_stock,m.avg_cost FROM recipe_items r JOIN materials m ON m.id=r.material_id WHERE r.product_id=?", arrayOf(productId.toString())).use { c ->
                while (c.moveToNext()) { val m = CrochetEngine.Material(c.long("material_id"), c.string("name"), c.string("material_unit"), c.double("meters_per_kg"), c.double("stock"), c.double("min_stock"), c.double("avg_cost")); requirements += m to (CrochetEngine.convert(m, c.double("qty"), c.string("unit")) * quantity) }
            }
            val grouped = requirements.groupBy { it.first.id }.values.map { entries -> entries.first().first to entries.sumOf { it.second } }
            CrochetEngine.validateProductionMaterials(grouped.toMap())
            grouped.forEach { (m, needed) -> moveMaterialStock(db, m, -needed, "producao", productionId.toString(), "${quantity}x $productName") }
            db.execSQL("UPDATE products SET stock=stock+? WHERE id=?", arrayOf(quantity, productId))
        }
    }

    private fun setProductionStatusInternal(db: SQLiteDatabase, id: Long, target: String) {
        db.rawQuery("SELECT status FROM production_orders WHERE id=?", arrayOf(id.toString())).use { c -> require(c.moveToFirst()) { "Ordem de produção não encontrada" }; val current = c.string("status"); CrochetEngine.validateProductionTransition(current, target)
            when (target) { "cancelada" -> db.update("production_orders", values("status" to target), "id=?", arrayOf(id.toString())); "pronta" -> { finishProduction(db, id); db.update("production_orders", values("status" to target, "finished_at" to now()), "id=?", arrayOf(id.toString())) }; else -> db.update("production_orders", values("status" to target), "id=?", arrayOf(id.toString())) }
        }
    }
    fun setProductionStatus(id: Long, status: String) = transaction { db -> setProductionStatusInternal(db, id, status) }

    fun addPayment(orderId: Long, amount: Double, method: String) = transaction { db ->
        db.rawQuery("SELECT status,total FROM orders WHERE id=?", arrayOf(orderId.toString())).use { c -> require(c.moveToFirst()) { "Pedido não encontrado" }; require(c.string("status") != "solicitado") { "Confirme o pedido antes de registrar pagamento" }; val total = c.double("total"); val paid = db.rawQuery("SELECT COALESCE(SUM(amount),0) paid FROM payments WHERE order_id=?", arrayOf(orderId.toString())).use { p -> p.moveToFirst(); p.double("paid") }; CrochetEngine.validatePayment(amount, total - paid, paid) }
        db.insertOrThrow("payments", null, values("order_id" to orderId, "amount" to amount, "method" to method.take(40), "created_at" to now()))
    }

    fun setOrderStatus(orderId: Long, target: String) = transaction { db ->
        db.rawQuery("SELECT status,stock_deducted FROM orders WHERE id=?", arrayOf(orderId.toString())).use { c -> require(c.moveToFirst()) { "Pedido não encontrado" }; val current = c.string("status"); val deducted = c.getInt(c.getColumnIndexOrThrow("stock_deducted")) != 0
            val lines = mutableListOf<OrderLineInput>(); db.rawQuery("SELECT product_id,qty FROM order_items WHERE order_id=?", arrayOf(orderId.toString())).use { items -> while (items.moveToNext()) lines += OrderLineInput(items.long("product_id"), items.double("qty")) }
            when (target) {
                "entregue" -> { require(current == "aberto") { "Pedido está '$current'" }; if (!deducted) takeProducts(db, lines); db.update("orders", values("status" to "entregue", "stock_deducted" to 1), "id=?", arrayOf(orderId.toString())) }
                "cancelado" -> { require(current != "cancelado") { "Pedido já está cancelado" }; if (deducted) lines.forEach { db.execSQL("UPDATE products SET stock=stock+? WHERE id=?", arrayOf(it.quantity, it.productId)) }; db.execSQL("UPDATE production_orders SET status='cancelada' WHERE order_id=? AND status IN ('fila','andamento','acabamento')", arrayOf(orderId)); db.update("orders", values("status" to "cancelado", "stock_deducted" to 0), "id=?", arrayOf(orderId.toString())) }
                else -> throw IllegalArgumentException("Status deve ser 'entregue' ou 'cancelado'")
            }
        }
    }

    fun advanceOrder(orderId: Long) = transaction { db ->
        db.rawQuery("SELECT status FROM orders WHERE id=?", arrayOf(orderId.toString())).use { c -> require(c.moveToFirst()) { "Pedido não encontrado" }; require(c.string("status") == "aberto") { "Pedido não está aberto" } }
        val pending = mutableListOf<Pair<Long, String>>(); db.rawQuery("SELECT id,status FROM production_orders WHERE order_id=? AND status IN ('fila','andamento','acabamento') ORDER BY id", arrayOf(orderId.toString())).use { c -> while (c.moveToNext()) pending += c.long("id") to c.string("status") }
        require(pending.isNotEmpty()) { "Esta encomenda não tem peças em produção" }; pending.forEach { (id, status) -> setProductionStatusInternal(db, id, CrochetEngine.productionNext(status)) }
    }

    data class MaterialDemand(val material: CrochetEngine.Material, val required: Double, val shortage: Double, val blockedOrderIds: List<Long>)

    fun saveMaterial(id: Long?, name: String, kind: String = "fio", color: String = "", brand: String = "", unit: String, metersPerKg: Double = 0.0, minimumStock: Double = 0.0): Long = transaction { db ->
        require(name.isNotBlank()) { "Informe o nome do material" }
        require(unit in setOf("kg", "m", "un")) { "Unidade deve ser kg, m ou un" }
        require(metersPerKg >= 0 && minimumStock >= 0)
        if (id == null) {
            db.insertOrThrow("materials", null, values("name" to name.trim(), "kind" to kind.take(40), "color" to color.take(80), "brand" to brand.take(80), "unit" to unit, "meters_per_kg" to metersPerKg, "min_stock" to minimumStock))
        } else {
            val old = material(db, id)
            if (old.unit != unit) {
                val movements = db.rawQuery("SELECT COUNT(*) n FROM stock_movements WHERE material_id=?", arrayOf(id.toString())).use { c -> c.moveToFirst(); c.getInt(0) }
                require(movements == 0) { "A unidade não pode mudar depois que o material teve movimentações" }
            }
            db.update("materials", values("name" to name.trim(), "kind" to kind.take(40), "color" to color.take(80), "brand" to brand.take(80), "unit" to unit, "meters_per_kg" to metersPerKg, "min_stock" to minimumStock), "id=?", arrayOf(id.toString()))
            id
        }
    }

    fun materialDemand(): List<MaterialDemand> {
        val db = helper.readableDatabase
        data class Acc(val material: CrochetEngine.Material, var required: Double = 0.0, val orders: MutableSet<Long> = linkedSetOf())
        val totals = linkedMapOf<Long, Acc>()
        db.rawQuery("SELECT id,product_id,qty,order_id FROM production_orders WHERE status IN ('fila','andamento','acabamento') ORDER BY id", null).use { production ->
            while (production.moveToNext()) {
                val orderId = if (production.isNull(production.getColumnIndexOrThrow("order_id"))) null else production.getLong(production.getColumnIndexOrThrow("order_id"))
                val productId = production.getLong(production.getColumnIndexOrThrow("product_id"))
                val quantity = production.getDouble(production.getColumnIndexOrThrow("qty"))
                db.rawQuery("SELECT r.qty,r.unit,m.id material_id,m.name,m.unit material_unit,m.meters_per_kg,m.stock,m.min_stock,m.avg_cost FROM recipe_items r JOIN materials m ON m.id=r.material_id WHERE r.product_id=?", arrayOf(productId.toString())).use { recipe ->
                    while (recipe.moveToNext()) {
                        val material = CrochetEngine.Material(recipe.getLong(recipe.getColumnIndexOrThrow("material_id")), recipe.getString(recipe.getColumnIndexOrThrow("name")), recipe.getString(recipe.getColumnIndexOrThrow("material_unit")), recipe.getDouble(recipe.getColumnIndexOrThrow("meters_per_kg")), recipe.getDouble(recipe.getColumnIndexOrThrow("stock")), recipe.getDouble(recipe.getColumnIndexOrThrow("min_stock")), recipe.getDouble(recipe.getColumnIndexOrThrow("avg_cost")))
                        val needed = try { CrochetEngine.convert(material, recipe.getDouble(recipe.getColumnIndexOrThrow("qty")), recipe.getString(recipe.getColumnIndexOrThrow("unit"))) * quantity } catch (_: IllegalArgumentException) { continue }
                        val acc = totals.getOrPut(material.id) { Acc(material) }
                        acc.required += needed
                        if (orderId != null) acc.orders += orderId
                    }
                }
            }
        }
        return totals.values.map { MaterialDemand(it.material, it.required, (it.required - it.material.stock).coerceAtLeast(0.0), it.orders.toList()) }
    }

    fun shoppingSuggestion(materialId: Long): Double {
        val m = material(helper.readableDatabase, materialId)
        return CrochetEngine.shoppingSuggestion(m.minStock, m.stock)
    }

    fun addToShoppingList(materialId: Long, quantity: Double = 0.0) = transaction { db ->
        require(quantity >= 0)
        material(db, materialId)
        db.insertWithOnConflict("shopping_list", null, values("material_id" to materialId, "qty" to quantity, "created_at" to now()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFromShoppingList(materialId: Long) = transaction { db ->
        db.delete("shopping_list", "material_id=?", arrayOf(materialId.toString()))
    }

    fun confirmCatalogRequest(orderId: Long, dueDate: String = "") = transaction { db ->
        db.rawQuery("SELECT status FROM orders WHERE id=?", arrayOf(orderId.toString())).use { c ->
            require(c.moveToFirst()) { "Pedido não encontrado" }
            require(c.string("status") == "solicitado") { "Este pedido não pode ser confirmado" }
        }
        db.update("orders", values("status" to "aberto", "kind" to "encomenda", "due_date" to dueDate), "id=?", arrayOf(orderId.toString()))
        val quantities = linkedMapOf<Long, Double>()
        db.rawQuery("SELECT product_id,qty FROM order_items WHERE order_id=? ORDER BY id", arrayOf(orderId.toString())).use { c ->
            while (c.moveToNext()) {
                val pid = c.getLong(c.getColumnIndexOrThrow("product_id"))
                quantities[pid] = (quantities[pid] ?: 0.0) + c.getDouble(c.getColumnIndexOrThrow("qty"))
            }
        }
        quantities.forEach { (pid, qty) ->
            val stock = db.rawQuery("SELECT stock FROM products WHERE id=?", arrayOf(pid.toString())).use { c -> require(c.moveToFirst()); c.getDouble(0) }
            val shortage = qty - stock
            if (shortage > CrochetEngine.EPSILON) {
                db.insertOrThrow("production_orders", null, values("product_id" to pid, "qty" to shortage, "status" to "fila", "order_id" to orderId, "created_at" to now()))
            }
        }
    }

    fun rejectCatalogRequest(orderId: Long) = transaction { db ->
        db.rawQuery("SELECT status FROM orders WHERE id=?", arrayOf(orderId.toString())).use { c ->
            require(c.moveToFirst()) { "Pedido não encontrado" }
            require(c.string("status") == "solicitado") { "Este pedido não pode ser recusado" }
        }
        db.update("orders", values("status" to "cancelado"), "id=?", arrayOf(orderId.toString()))
    }

    fun updateOrder(orderId: Long, dueDate: String, stageNote: String, notes: String, channel: String) = transaction { db ->
        db.rawQuery("SELECT id FROM orders WHERE id=?", arrayOf(orderId.toString())).use { require(it.moveToFirst()) { "Pedido não encontrado" } }
        db.update("orders", values("due_date" to dueDate, "stage_note" to stageNote.take(160), "notes" to notes.take(255), "channel" to channel.take(40)), "id=?", arrayOf(orderId.toString()))
    }

    fun addExpense(description: String, amount: Double, category: String = "", method: String = "") = transaction { db ->
        require(description.isNotBlank()) { "Informe a descrição da despesa" }
        require(amount > 0) { "Valor deve ser maior que zero" }
        db.insertOrThrow("expenses", null, values("description" to description.take(160), "amount" to amount, "category" to category.take(60), "method" to method.take(40), "created_at" to now()))
    }

    fun addIncome(description: String, amount: Double, method: String = "") = transaction { db ->
        require(description.isNotBlank()) { "Informe a descrição da receita" }
        require(amount > 0) { "Valor deve ser maior que zero" }
        db.insertOrThrow("incomes", null, values("description" to description.take(160), "amount" to amount, "method" to method.take(40), "created_at" to now()))
    }

    fun saveNumericSettings(hourlyRate: Double, overheadPerPiece: Double, defaultMarginPercent: Double, monthlyGoal: Double) = transaction { db ->
        require(hourlyRate >= 0 && overheadPerPiece >= 0 && defaultMarginPercent >= 0 && monthlyGoal >= 0)
        mapOf("hourly_rate" to hourlyRate, "overhead_per_piece" to overheadPerPiece, "default_margin_pct" to defaultMarginPercent, "monthly_goal" to monthlyGoal).forEach { (key, value) ->
            db.insertWithOnConflict("settings", null, values("skey" to key, "svalue" to value.toString()), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

}

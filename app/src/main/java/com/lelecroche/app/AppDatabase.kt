package com.lelecroche.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Customer(val id: Long, val name: String, val phone: String, val notes: String)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "lele_croche.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',city TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',labor_minutes REAL NOT NULL DEFAULT 0,price REAL,margin_pct REAL,stock REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,category TEXT NOT NULL DEFAULT '',photo_id INTEGER,photo2_id INTEGER,photo3_id INTEGER,in_catalog INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE materials(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,kind TEXT NOT NULL DEFAULT 'fio',color TEXT NOT NULL DEFAULT '',brand TEXT NOT NULL DEFAULT '',unit TEXT NOT NULL,meters_per_kg REAL NOT NULL DEFAULT 0,stock REAL NOT NULL DEFAULT 0,min_stock REAL NOT NULL DEFAULT 0,avg_cost REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,supplier TEXT NOT NULL DEFAULT '',lot TEXT NOT NULL DEFAULT '',photo_id INTEGER)")
        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER,channel TEXT NOT NULL DEFAULT '',kind TEXT NOT NULL,status TEXT NOT NULL,due_date TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',total REAL NOT NULL DEFAULT 0,stock_deducted INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,stage_note TEXT NOT NULL DEFAULT '',source TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL,amount REAL NOT NULL,method TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE stock_movements(id INTEGER PRIMARY KEY AUTOINCREMENT,material_id INTEGER NOT NULL,qty REAL NOT NULL,reason TEXT NOT NULL,ref TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT NOT NULL,amount REAL NOT NULL,category TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL,method TEXT NOT NULL DEFAULT '')")
        extended(db)
    }
    private fun extended(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_items(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,material_id INTEGER NOT NULL,qty REAL NOT NULL,unit TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS order_items(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL,product_id INTEGER NOT NULL,qty REAL NOT NULL,unit_price REAL NOT NULL,unit_cost REAL NOT NULL DEFAULT 0,note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS production_orders(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,qty REAL NOT NULL,status TEXT NOT NULL DEFAULT 'fila',order_id INTEGER,created_at TEXT NOT NULL,finished_at TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases(id INTEGER PRIMARY KEY AUTOINCREMENT,material_id INTEGER NOT NULL,qty REAL NOT NULL,total_cost REAL NOT NULL,supplier TEXT NOT NULL DEFAULT '',lot TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL,method TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS settings(skey TEXT PRIMARY KEY,svalue TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS incomes(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT NOT NULL,amount REAL NOT NULL,method TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shopping_list(id INTEGER PRIMARY KEY AUTOINCREMENT,material_id INTEGER NOT NULL UNIQUE,qty REAL NOT NULL DEFAULT 0,created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS photos(id INTEGER PRIMARY KEY AUTOINCREMENT,mime TEXT NOT NULL,data TEXT NOT NULL,created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS catalog_hits(id INTEGER PRIMARY KEY AUTOINCREMENT,ip_hash TEXT NOT NULL,created_at TEXT NOT NULL)")
    }
    private fun ensureColumn(db:SQLiteDatabase, table:String, column:String, definition:String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while(c.moveToNext()) if(c.getString(c.getColumnIndexOrThrow("name")) == column) found = true
            found
        }
        if(!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        // Migração aditiva: preserva os registros já existentes no aparelho.
        if(oldVersion < 3) {
            ensureColumn(db,"customers","city","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"materials","supplier","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"materials","lot","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"materials","photo_id","INTEGER")
            ensureColumn(db,"products","category","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"products","photo_id","INTEGER")
            ensureColumn(db,"products","photo2_id","INTEGER")
            ensureColumn(db,"products","photo3_id","INTEGER")
            ensureColumn(db,"products","in_catalog","INTEGER NOT NULL DEFAULT 1")
            ensureColumn(db,"orders","stage_note","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"orders","source","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"purchases","method","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"expenses","method","TEXT NOT NULL DEFAULT ''")
            ensureColumn(db,"order_items","note","TEXT NOT NULL DEFAULT ''")
            extended(db)
        }
    }
    private fun now()=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
    private fun cv(vararg p:Pair<String,Any?>)=ContentValues().apply{p.forEach{(k,v)->when(v){null->putNull(k);is String->put(k,v);is Int->put(k,v);is Long->put(k,v);is Double->put(k,v);is Float->put(k,v);else->put(k,v.toString())}}}
    fun rows(sql:String,args:Array<String> = emptyArray()):List<Map<String,String>>{val out=mutableListOf<Map<String,String>>();readableDatabase.rawQuery(sql,args).use{c->while(c.moveToNext()){val m=linkedMapOf<String,String>();for(i in 0 until c.columnCount)m[c.getColumnName(i)]=if(c.isNull(i)) "" else c.getString(i);out+=m}};return out}
    fun count(t:String)=rows("SELECT COUNT(*) n FROM $t").firstOrNull()?.get("n")?.toIntOrNull()?:0
    fun scalar(sql:String)=rows(sql).firstOrNull()?.values?.firstOrNull()?.toDoubleOrNull()?:0.0
    fun getCustomers()=rows("SELECT id,name,phone,notes FROM customers ORDER BY name COLLATE NOCASE").map{Customer(it["id"]!!.toLong(),it["name"].orEmpty(),it["phone"].orEmpty(),it["notes"].orEmpty())}
    fun saveCustomer(id:Long?,name:String,phone:String,notes:String):Long{val v=cv("name" to name.trim(),"phone" to phone.trim(),"notes" to notes.trim());if(id==null)return writableDatabase.insertOrThrow("customers",null,v);writableDatabase.update("customers",v,"id=?",arrayOf(id.toString()));return id}
    fun deleteCustomer(id:Long)=writableDatabase.delete("customers","id=?",arrayOf(id.toString()))>0
    fun saveMaterial(name:String,kind:String,color:String,brand:String,unit:String,stock:Double,min:Double,cost:Double){writableDatabase.insertOrThrow("materials",null,cv("name" to name,"kind" to kind,"color" to color,"brand" to brand,"unit" to unit,"stock" to stock,"min_stock" to min,"avg_cost" to cost))}
    fun saveProduct(name:String,desc:String,cat:String,price:Double,stock:Double,labor:Double){writableDatabase.insertOrThrow("products",null,cv("name" to name,"description" to desc,"category" to cat,"price" to price,"stock" to stock,"labor_minutes" to labor))}
    fun saveExpense(desc:String,amount:Double,cat:String){writableDatabase.insertOrThrow("expenses",null,cv("description" to desc,"amount" to amount,"category" to cat,"created_at" to now()))}
    fun addProduction(pid:Long,qty:Double){writableDatabase.insertOrThrow("production_orders",null,cv("product_id" to pid,"qty" to qty,"status" to "fila","created_at" to now()))}
    fun updateProduction(id:Long,status:String){writableDatabase.update("production_orders",cv("status" to status,"finished_at" to if(status=="concluida") now() else null),"id=?",arrayOf(id.toString()))}
    fun addPayment(oid:Long,amount:Double,method:String){writableDatabase.insertOrThrow("payments",null,cv("order_id" to oid,"amount" to amount,"method" to method,"created_at" to now()))}
    fun addOrder(cid:Long?,pid:Long,qty:Double,price:Double,kind:String,due:String,note:String,deposit:Double){val db=writableDatabase;db.beginTransaction();try{val total=qty*price;val oid=db.insertOrThrow("orders",null,cv("customer_id" to cid,"kind" to kind,"status" to if(kind=="pronta")"entregue" else "aberto","due_date" to due,"notes" to note,"total" to total,"stock_deducted" to if(kind=="pronta")1 else 0,"created_at" to now()));db.insertOrThrow("order_items",null,cv("order_id" to oid,"product_id" to pid,"qty" to qty,"unit_price" to price,"unit_cost" to 0.0));if(deposit>0)db.insertOrThrow("payments",null,cv("order_id" to oid,"amount" to deposit,"method" to "Sinal","created_at" to now()));if(kind=="pronta")db.execSQL("UPDATE products SET stock=MAX(0,stock-?) WHERE id=?",arrayOf(qty,pid)) else db.insertOrThrow("production_orders",null,cv("product_id" to pid,"qty" to qty,"status" to "fila","order_id" to oid,"created_at" to now()));db.setTransactionSuccessful()}finally{db.endTransaction()}}
    fun orderStatus(id:Long,status:String){writableDatabase.update("orders",cv("status" to status),"id=?",arrayOf(id.toString()))}
    fun deleteRow(table:String,id:Long){writableDatabase.delete(table,"id=?",arrayOf(id.toString()))}
    fun stockMove(id:Long,qty:Double,note:String){val db=writableDatabase;db.beginTransaction();try{db.execSQL("UPDATE materials SET stock=stock+? WHERE id=?",arrayOf(qty,id));db.insertOrThrow("stock_movements",null,cv("material_id" to id,"qty" to qty,"reason" to if(qty>=0)"entrada" else "saida","note" to note,"created_at" to now()));db.setTransactionSuccessful()}finally{db.endTransaction()}}
    fun purchase(id:Long,qty:Double,total:Double,supplier:String){val db=writableDatabase;db.beginTransaction();try{db.insertOrThrow("purchases",null,cv("material_id" to id,"qty" to qty,"total_cost" to total,"supplier" to supplier,"created_at" to now()));db.execSQL("UPDATE materials SET stock=stock+?,avg_cost=CASE WHEN stock+?>0 THEN ((stock*avg_cost)+?)/(stock+?) ELSE avg_cost END WHERE id=?",arrayOf(qty,qty,total,qty,id));db.insertOrThrow("stock_movements",null,cv("material_id" to id,"qty" to qty,"reason" to "compra","note" to supplier,"created_at" to now()));db.insertOrThrow("expenses",null,cv("description" to "Compra de material #$id","amount" to total,"category" to "Materiais","created_at" to now()));db.setTransactionSuccessful()}finally{db.endTransaction()}}

    companion object {
        val BACKUP_TABLES = listOf("materials", "products", "customers", "orders", "recipe_items", "order_items", "payments", "stock_movements", "production_orders", "purchases", "expenses", "incomes", "shopping_list", "photos", "catalog_hits", "settings")
    }

    fun exportTableJson(table: String): JSONArray {
        require(table in BACKUP_TABLES) { "Tabela não permitida." }
        val result = JSONArray()
        val order = if (table == "settings") "skey" else "id"
        readableDatabase.rawQuery("SELECT * FROM $table ORDER BY $order", null).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    if (cursor.isNull(i)) {
                        row.put(cursor.getColumnName(i), JSONObject.NULL)
                    } else {
                        when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(cursor.getColumnName(i), cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(cursor.getColumnName(i), cursor.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_BLOB -> row.put(cursor.getColumnName(i), android.util.Base64.encodeToString(cursor.getBlob(i), android.util.Base64.NO_WRAP))
                            else -> row.put(cursor.getColumnName(i), cursor.getString(i))
                        }
                    }
                }
                result.put(row)
            }
        }
        return result
    }

    fun restoreBackup(root: JSONObject) {
        val version = root.optInt("schema_version", 1)
        require(version == 1) { "Versão de backup não suportada." }
        val payload = linkedMapOf<String, JSONArray>()
        for (table in BACKUP_TABLES) {
            val value = root.opt(table)
            if (value == null || value == JSONObject.NULL) {
                require(table == "photos" || table == "catalog_hits") { "Backup incompleto: falta a tabela $table." }
                payload[table] = JSONArray()
            } else {
                require(value is JSONArray) { "Formato inválido na tabela $table." }
                payload[table] = value
            }
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            // Valida todas as colunas antes de apagar qualquer registro.
            val allowedColumns = BACKUP_TABLES.associateWith { table ->
                db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
            for ((table, array) in payload) {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: throw IllegalArgumentException("Registro inválido em $table.")
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        require(key in allowedColumns.getValue(table)) { "Coluna desconhecida em $table: $key." }
                        val value = row.get(key)
                        require(value === JSONObject.NULL || value is String || value is Number || value is Boolean) {
                            "Valor inválido em $table.$key."
                        }
                    }
                }
            }

            BACKUP_TABLES.asReversed().forEach { db.delete(it, null, null) }
            // A ordem respeita os registros-base antes dos registros que os referenciam.
            for (table in BACKUP_TABLES) {
                val array = payload.getValue(table)
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val values = ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val value = row.get(key)) {
                            JSONObject.NULL -> values.putNull(key)
                            is String -> values.put(key, value)
                            is Int -> values.put(key, value)
                            is Long -> values.put(key, value)
                            is Double -> values.put(key, value)
                            is Float -> values.put(key, value)
                            is Number -> values.put(key, value.toDouble())
                            is Boolean -> values.put(key, if (value) 1 else 0)
                        }
                    }
                    db.insertOrThrow(table, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun customerCount()=count("customers")
}

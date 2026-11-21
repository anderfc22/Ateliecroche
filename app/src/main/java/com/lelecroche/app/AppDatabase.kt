package com.lelecroche.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(
    val id: Long,
    val name: String,
    val phone: String,
    val notes: String
)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "lele_croche.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                labor_minutes REAL NOT NULL DEFAULT 0,
                price REAL,
                margin_pct REAL,
                stock REAL NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE materials (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'fio',
                color TEXT NOT NULL DEFAULT '',
                brand TEXT NOT NULL DEFAULT '',
                unit TEXT NOT NULL,
                meters_per_kg REAL NOT NULL DEFAULT 0,
                stock REAL NOT NULL DEFAULT 0,
                min_stock REAL NOT NULL DEFAULT 0,
                avg_cost REAL NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                channel TEXT NOT NULL DEFAULT '',
                kind TEXT NOT NULL,
                status TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                total REAL NOT NULL DEFAULT 0,
                stock_deducted INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                FOREIGN KEY(customer_id) REFERENCES customers(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                amount REAL NOT NULL,
                method TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                FOREIGN KEY(order_id) REFERENCES orders(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                material_id INTEGER NOT NULL,
                qty REAL NOT NULL,
                reason TEXT NOT NULL,
                ref TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                FOREIGN KEY(material_id) REFERENCES materials(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações incrementais serão adicionadas sem apagar os dados existentes.
    }

    fun getCustomers(): List<Customer> {
        val result = mutableListOf<Customer>()
        readableDatabase.query("customers", arrayOf("id", "name", "phone", "notes"),
            null, null, null, null, "name COLLATE NOCASE ASC").use { cursor ->
            while (cursor.moveToNext()) {
                result += Customer(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3)
                )
            }
        }
        return result
    }

    fun saveCustomer(id: Long?, name: String, phone: String, notes: String): Long {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("notes", notes.trim())
        }
        return if (id == null) writableDatabase.insertOrThrow("customers", null, values)
        else {
            writableDatabase.update("customers", values, "id = ?", arrayOf(id.toString()))
            id
        }
    }

    fun deleteCustomer(id: Long): Boolean {
        return writableDatabase.delete("customers", "id = ?", arrayOf(id.toString())) > 0
    }

    fun customerCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM customers", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }
}

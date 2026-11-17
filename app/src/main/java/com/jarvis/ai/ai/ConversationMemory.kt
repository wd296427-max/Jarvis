package com.jarvis.ai.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Private on-device conversation memory. Stores a bounded history for AI context. */
class ConversationMemory(context: Context) : SQLiteOpenHelper(context, "jarvis_memory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE turns (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }

    @Synchronized
    fun add(role: String, content: String) {
        writableDatabase.insert("turns", null, ContentValues().apply {
            put("role", role)
            put("content", content.take(12000))
            put("created_at", System.currentTimeMillis())
        })
        writableDatabase.execSQL("DELETE FROM turns WHERE id NOT IN (SELECT id FROM turns ORDER BY id DESC LIMIT 40)")
    }

    @Synchronized
    fun recent(limit: Int = 12): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT role, content FROM (SELECT id, role, content FROM turns ORDER BY id DESC LIMIT ?) ORDER BY id ASC",
            arrayOf(limit.coerceIn(1, 40).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0) to cursor.getString(1)
        }
        return result
    }

    @Synchronized
    fun clear() { writableDatabase.delete("turns", null, null) }
}

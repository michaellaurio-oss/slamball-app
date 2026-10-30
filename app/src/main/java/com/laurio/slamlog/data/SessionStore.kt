package com.laurio.slamlog.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Saves workout history as a JSON file in the app's private storage. */
class SessionStore(context: Context) {
    private val file = File(context.filesDir, "sessions.json")

    @Synchronized
    fun load(): List<Session> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length())
                .map { Session.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.startMillis }
        } catch (e: Exception) {
            // Keep a copy of an unreadable file instead of silently overwriting it later.
            runCatching { file.copyTo(File(file.parentFile, "sessions-unreadable-${System.currentTimeMillis()}.json")) }
            file.delete()
            emptyList()
        }
    }

    @Synchronized
    fun save(session: Session) {
        write(load().filter { it.id != session.id } + session)
    }

    @Synchronized
    fun delete(id: Long) {
        write(load().filter { it.id != id })
    }

    private fun write(list: List<Session>) {
        val arr = JSONArray()
        list.sortedBy { it.startMillis }.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "sessions.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}

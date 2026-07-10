package com.example.freessm

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SsmFileLogger {
    private var fileOutputStream: FileOutputStream? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun startNewSession(context: Context) {
        try {
            closeSession()
            // Сюда Android разрешает писать файлы мгновенно и без лишних вопросов!
            val appSpecificDir = context.getExternalFilesDir(null)
            if (appSpecificDir != null && !appSpecificDir.exists()) {
                appSpecificDir.mkdirs()
            }

            val logFile = File(appSpecificDir, "ssm_raw_dump.txt")
            fileOutputStream = FileOutputStream(logFile, true)

            logText("=== СТАРТ НОВОЙ СЕССИИ ЗАПИСИ ДАМПА ===")
        } catch (e: Exception) {
            SsmLogger.log("[FILE LOG ERROR]: Сбой создания файла: ${e.message}")
        }
    }

    @Synchronized
    fun logText(text: String) {
        try {
            val timestamp = timeFormat.format(Date())
            fileOutputStream?.write("[$timestamp] $text\n".toByteArray())
            fileOutputStream?.flush()
        } catch (e: Exception) {}
    }

    // САМОЕ ГЛАВНОЕ: Запись сырых Hex-байт Tx или Rx
    @Synchronized
    fun logHexBytes(direction: String, bytes: ByteArray, length: Int) {
        if (length <= 0) return
        try {
            val timestamp = timeFormat.format(Date())
            val hexString = bytes.slice(0 until length).joinToString(" ") { String.format("%02X", it) }
            fileOutputStream?.write("[$timestamp] $direction: $hexString\n".toByteArray())
            fileOutputStream?.flush()
        } catch (e: Exception) {}
    }

    fun closeSession() {
        try {
            logText("=== КОНЕЦ СЕССИИ ЗАПИСИ ДАМПА ===")
            fileOutputStream?.close()
            fileOutputStream = null
        } catch (e: Exception) {}
    }
}

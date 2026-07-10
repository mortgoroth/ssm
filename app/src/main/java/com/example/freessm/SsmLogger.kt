package com.example.freessm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SsmLogger {
    private val logBuilder = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Метод для записи любой строки из любого места программы
    @Synchronized
    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        logBuilder.append("[$timestamp] $message\n")
    }

    // Чтение всего накопленного лога для вывода на экран
    @Synchronized
    fun getFullLog(): String {
        return if (logBuilder.isEmpty()) "Журнал пуст. Запустите опрос..." else logBuilder.toString()
    }

    // Сброс журнала
    @Synchronized
    fun clear() {
        logBuilder.setLength(0)
    }
}

package com.example.freessm

import java.util.Locale

object SsmEcuMap {
    val fullSensorList = mutableListOf(
        // === БАЗОВАЯ ПАЧКА ДАТЧИКОВ ===
        SensorItem(title = "Coolant Temperature", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x08.toByte()), unit = "°C") { buf, index ->
            if (index >= 0 && index < buf.size) "${(buf[index].toInt() and 0xFF) - 40}" else "---"
        },

        SensorItem(title = "Engine Speed (Тахометр)", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x0E.toByte()), unit = "rpm") { buf, index ->
            if (index >= 0 && index + 1 < buf.size) {
                val hi = buf[index].toInt() and 0xFF
                val lo = buf[index + 1].toInt() and 0xFF
                "${((hi shl 8) or lo) / 4.0}"
            } else "---"
        },

        SensorItem(title = "Vehicle Speed", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x10.toByte()), unit = "km/h") { buf, index ->
            if (index >= 0 && index < buf.size) "${buf[index].toInt() and 0xFF}" else "---"
        },

        SensorItem(title = "Ignition Timing (УОЗ)", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x11.toByte()), unit = "deg") { buf, index ->
            if (index >= 0 && index < buf.size) "${((buf[index].toInt() and 0xFF) / 2.0) - 64}" else "---"
        },

        SensorItem(title = "Throttle Opening Angle", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x15.toByte()), unit = "%") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.1f", (buf[index].toInt() and 0xFF) / 2.55) else "---"
        },

        SensorItem(title = "Mass Airflow Voltage", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x1D.toByte()), unit = "V") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.2f", (buf[index].toInt() and 0xFF) / 50.0) else "---"
        },

        SensorItem(title = "Battery Voltage", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x1C.toByte()), unit = "V") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.2f", (buf[index].toInt() and 0xFF) * 0.08) else "---"
        },

        SensorItem(title = "Knock Correction", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x12.toByte()), unit = "deg") { buf, index ->
            if (index >= 0 && index < buf.size) "${((buf[index].toInt() and 0xFF) / 2.0) - 64}" else "---"
        },

        SensorItem(title = "Manifold Abs. Pressure", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x1B.toByte()), unit = "bar") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.2f", (buf[index].toInt() and 0xFF) * 0.01) else "---"
        },

        // === НОВАЯ ПАЧКА ДАТЧИКОВ (Итерация 2+) ===
        SensorItem(title = "Engine Load", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x0C.toByte()), unit = "cu") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.2f", (buf[index].toInt() and 0xFF) * 0.05) else "---"
        },

        SensorItem(title = "Atmospheric Pressure", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x14.toByte()), unit = "bar") { buf, index ->
            if (index >= 0 && index < buf.size) String.format(Locale.US, "%.2f", (buf[index].toInt() and 0xFF) * 0.01) else "---"
        },

        SensorItem(title = "A/F Correction #1", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x28.toByte()), unit = "%") { buf, index ->
            if (index >= 0 && index < buf.size) "${(buf[index].toInt() and 0xFF) - 128}" else "---"
        },

        // === ДИСКРЕТНЫЕ ФЛАГИ / ПЕРЕКЛЮЧАТЕЛИ (Адрес 0x63) ===
        SensorItem(title = "Neutral Position Sw.", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x63.toByte()), unit = "0/1") { buf, index ->
            if (index >= 0 && index < buf.size) {
                // Бит 0: Проверяем, включена ли нейтраль
                if ((buf[index].toInt() and 0x01) != 0) "ON (Нейтраль)" else "OFF (Передача)"
            } else "---"
        },

        SensorItem(title = "A/C Switch", addresses = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x63.toByte()), unit = "0/1") { buf, index ->
            if (index >= 0 && index < buf.size) {
                // Бит 4: Проверяем, нажал ли водитель кнопку кондиционера (0x10 = 16 в дес.)
                if ((buf[index].toInt() and 0x10) != 0) "ON" else "OFF"
            } else "---"
        }
    )
    // === 2. БАЗА ТЕСТОВЫХ НАБОРОВ БАЙТ (Для домашней эмуляции) ===
    val mockDataMap = mapOf(
        0x08 to arrayOf(0x39.toByte(), 0x3E.toByte(), 0x44.toByte()), // Температура ОЖ
        0x0E to arrayOf(0x28.toByte(), 0x2A.toByte(), 0x31.toByte()), // Тахометр
        0x10 to arrayOf(0x00.toByte()),                               // Скорость
        0x11 to arrayOf(0x9E.toByte(), 0x9F.toByte(), 0xA0.toByte()), // УОЗ
        0x15 to arrayOf(0x3E.toByte(), 0x39.toByte(), 0x35.toByte()), // Дроссель
        0x1D to arrayOf(0x32.toByte(), 0x35.toByte()),                // МАФ вольтаж
        0x1C to arrayOf(0xAF.toByte()),                               // Вольтметр
        0x12 to arrayOf(0x80.toByte()),                               // Детонация
        0x1B to arrayOf(0x23.toByte()),                               // MAP давление
        0x0C to arrayOf(0x1F.toByte(), 0x22.toByte()),                // Нагрузка
        0x14 to arrayOf(0x64.toByte()),                               // Атм. давление
        0x28 to arrayOf(0x80.toByte(), 0x84.toByte()),                // Топливная коррекция
        0x63 to arrayOf(0x11.toByte(), 0x01.toByte())                 // Флаги (нейтраль/кондей)
    )
}

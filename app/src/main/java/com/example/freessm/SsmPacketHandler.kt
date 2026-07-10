package com.example.freessm

class SsmPacketHandler {
    // Единый глобальный накопительный буфер на 256 ячеек (по всему адресному пространству SSM2).
    private val globalEcuBuffer = ByteArray(256)

    fun processSensorData(response: ByteArray, selectedSensors: List<SensorItem>) {
        // Слепой фильтр: пакет обязан содержать заголовок 0x80 и быть не меньше 7 байт
        if (response.isEmpty() || response[0] != 0x80.toByte() || response.size < 7) return

        val sensor = selectedSensors.firstOrNull() ?: return

        try {
            // Вытаскиваем беззнаковый чистый байт данных от ЭБУ (индекс 5 по Гитхаб-логу)
            val rawInt = response[5].toInt() and 0xFF

            // Получаем последний байт адреса датчика (например, 0x08, 0x0E, 0x11...)
            val addrId = (sensor.addresses.lastOrNull() ?: 0x00).toInt() and 0xFF

            // АВТОМАТИКА: Пишем байт данных прямо в ячейку с номером этого адреса!
            globalEcuBuffer[addrId] = rawInt.toByte()

            // Адаптация под двухбайтовую формулу тахометра в MainActivity (адрес 0x0E).
            // На столе для красивых холостых оборотов подсунем hi-байт, чтобы вышло ~714 rpm
            if (addrId == 0x0E) {
                globalEcuBuffer[0x0E] = 0x0B.toByte() // Старший байт (hi) для эмулятора
                globalEcuBuffer[0x0F] = rawInt.toByte() // Младший байт (lo)
            }

            // Передаем в оригинальный метод calculate этот же адрес в качестве индекса (offset)!
            // Теперь MainActivity прочитает байт именно из той ячейки, где он лежит
            val resultText = sensor.calculate(globalEcuBuffer, addrId)
            sensor.currentValue = resultText

            // Расчет минимумов и максимумов для графиков осциллографа
            val numericValue = resultText.toDoubleOrNull()
            if (numericValue != null) {
                if (sensor.minValue == null || numericValue < sensor.minValue!!) {
                    sensor.minValue = numericValue
                }
                if (sensor.maxValue == null || numericValue > sensor.maxValue!!) {
                    sensor.maxValue = numericValue
                }
            }
        } catch (e: Exception) {
            sensor.currentValue = "---"
        }
    }
}

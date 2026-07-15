package com.example.freessm

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class EmulatorChannel (private val context: Context) : SsmChannel {
    override val chipset: String = "Virtual Emulator"
    override var debugLog: String = ""
    override var connectReport: String = ""

    override fun open(): Boolean {
        debugLog += "Virtual Emulator Opened\n"
        return true
    }

    fun close() {
        debugLog += "Virtual Emulator Closed\n"
    }

    private var loopCounter = 0

    override fun sendAndReceive(requestPacket: ByteArray, expectedResponseSize: Int): Pair<ByteArray, Int> {
        // Эмулируем задержку шины автомобиля, чтобы софт не улетал в космос
        Thread.sleep(30)

        // По дилерскому протоколу SSM2 команда чтения блоков лежит в 4-м байте запроса (A8)
        if (requestPacket.size >= 5 && requestPacket[4] == 0xA8.toByte()) {
            // Берем последний значащий байт адреса датчика (например, 0x0E для оборотов)
            val addrId = (requestPacket[requestPacket.size - 2].toInt()) and 0xFF

            // Дергаем байт плеера логов из нашей карты
            val bytePackets = SsmEcuMap.mockDataMap[addrId] ?: arrayOf(0x00.toByte())
            val liveByte = bytePackets[loopCounter % bytePackets.size]
            loopCounter++

            // Собираем эталонный 7-байтовый пакет ответа Субару: 80 F0 10 [LEN] [CMD] [DATA] [CS]
            val mockRx = byteArrayOf(
                0x80.toByte(), 0xF0.toByte(), 0x10.toByte(), 0x02.toByte(), 0xE8.toByte(),
                liveByte,
                0x00.toByte() // Упрощенная заглушка контрольной суммы для эмулятора
            )
            return Pair(mockRx, 7)
        }

        return Pair(ByteArray(0), 0)
    }

    override fun getRomId(): String = "1C 08 00 01 00" // Родной паспорт твоего EJ202

    override fun readBlock(address: ByteArray, length: Int): ByteArray {
        // Переводим запрашиваемый адрес в HEX строку (например: "000004")
        val addressHex = address.joinToString("") { String.format("%02X", it) }

        try {
            // 🟢 Открываем поток чтения файла из папки assets
            val inputStream = context.assets.open("ecu_polling_dump.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            // Читаем файл построчно в поисках нужного адреса обмена
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue

                // Ищем строку, в которой фигурирует наш запрашиваемый адрес памяти
                if (currentLine.contains(addressHex, ignoreCase = true)) {
                    // Твой дамп — это текстовый лог. Если находим строку ответа ЭБУ,
                    // парсим её обратно в массив байт нужной длины
                    // (Для примера с ROM-ID возвращаем эталонные 5 байт из дампа)
                    if (addressHex == "000004") {
                        reader.close()
                        return byteArrayOf(0x1C.toByte(), 0x08.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte())
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            debugLog += "Emulator Error reading dump: ${e.message}\n"
        }

        // Если адрес в дампе не найден, возвращаем пустой массив заданной длины
        return ByteArray(length)
    }

}
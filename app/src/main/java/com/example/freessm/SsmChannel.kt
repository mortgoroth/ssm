package com.example.freessm

interface SsmChannel {
    // Открыть канал (для машины — Fast Init 4800/9600, для эмулятора — мгновенный успех)
    fun open(): Boolean

    // Универсальная транзакция: отправить пакет Tx и вернуть массив Rx (вместе с логом дебага)
    fun sendAndReceive(requestPacket: ByteArray, expectedResponseSize: Int): Pair<ByteArray, Int>

    // Выдать технический паспорт для заполнения левой панели и консоли
    fun getRomId(): String
    val chipset: String
    val debugLog: String
    val connectReport: String

    fun readBlock(address: ByteArray, length: Int): ByteArray

    fun requestRomId(): String {
        // Стартовый адрес ПЗУ для чтения ROM-ID на Subaru: 0x000004
        val romAddress = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x04.toByte())

        // Читаем блок длиной 5 байт
        val response = readBlock(romAddress, 5)

        // Если ЭБУ вернул ровно 5 байт данных, переводим их в красивую HEX-строку
        if (response.size == 5) {
            return response.joinToString(" ") { String.format("%02X", it) }
        }

        return "FAILED"
    }
}

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

}

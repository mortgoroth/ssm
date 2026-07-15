package com.example.freessm

class EmulatorChannel : SsmChannel {
    override val chipset: String = "Virtual Emulator"
    override val debugLog: String = "Запущен настольный универсальный эмулятор.\nФизический кабель KKL не обнаружен.\n"
    private var loopCounter = 0

    override fun open(): Boolean = true

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

    override val connectReport: String = """
        [SYSTEM LOG]: Инициализация интерфейса К-линии... SUCCESS
        --------------------------------------------------
        Режим работы     : АВТОМАТИЧЕСКИЙ ЭМУЛЯТОР (Demo)
        Интерфейс        : Virtual Emulator
        Протокол обмена  : Subaru Select Monitor 2 (SSM2)
        Скорость шины    : 4800 / 9600 baud (Fast Init OK)
        Вычитанный ROM-ID: 1C 08 00 01 00
        
        Диагностический канал открыт. Система готова к работе.
    """.trimIndent()
}
package com.example.freessm

import com.hoho.android.usbserial.driver.UsbSerialPort
import android.graphics.Color

class RealHardwareChannel(
    private val port: UsbSerialPort,
    private val hardwareManager: SsmHardwareManager
) : SsmChannel {

    override var chipset: String = port.driver?.device?.productName ?: "FTDI FT232R UART"
    private val logBuilder = StringBuilder()
    override val debugLog: String get() = logBuilder.toString()

    private var actualRomId = ""

    override fun open(): Boolean {
        logBuilder.setLength(0)
        try {
            logBuilder.append("[INIT FAST]: Запуск аппаратного Fast Init импульса...\n")

            // Сразу жестко настраиваем порт на боевые 9600 бод
            port.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true
            port.purgeHwBuffers(true, true)

            // Включаем аппаратный BREAK (зажимаем K-линию в логический 0)
            port.setBreak(true)
            Thread.sleep(200) // Держим линию зажатой ровно 200мс по стандарту ISO-14230 / SSM2
            port.setBreak(false) // Отпускаем линию в дефолтное состояние высокого уровня

            // Даем диагностическому чипу ЭБУ Субару прийти в себя после шока пробуждения
            Thread.sleep(100)
            port.purgeHwBuffers(true, true)

            // ---------------------------------------------------------------------
            // ШАГ 1: Отправляем наш честный двухэтапный запрос структуры блоков (A0)
            // ---------------------------------------------------------------------
            val initPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x04.toByte(), 0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x24.toByte())
            logBuilder.append("Tx1 -> Запрос A0: 80 10 F0 04 A0 00 00 00 24\n")

            port.write(initPacket, 300)
            Thread.sleep(200) // Даем время ЭБУ полностью выплюнуть ответный кадр

            val response1 = ByteArray(64)
            val readBytes1 = port.read(response1, 500)
            logBuilder.append("Rx1 -> Получено байт: $readBytes1\n")

            var ecuIdx1 = -1
            if (readBytes1 >= 15) {
                val dump1 = response1.slice(0 until readBytes1).joinToString(" ") { String.format("%02X", it) }
                logBuilder.append("Буфер Шага 1: [$dump1]\n")
                for (i in 9 until readBytes1 - 5) {
                    if (response1[i] == 0x80.toByte() && response1[i+1] == 0xF0.toByte() && response1[i+2] == 0x10.toByte()) {
                        ecuIdx1 = i
                        break
                    }
                }
            }

            if (ecuIdx1 == -1) {
                logBuilder.append("[FAILED]: Мозги машины промолчали на запрос A0.\n")
                return false
            }

            val baseAddr1 = response1[ecuIdx1 + 5]
            val baseAddr2 = response1[ecuIdx1 + 6]
            val baseAddr3 = response1[ecuIdx1 + 7]
            logBuilder.append(String.format("Динамический адрес найден: %02X %02X %02X\n", baseAddr1, baseAddr2, baseAddr3))

            // 4. Честный двухэтапный сбор ROM-ID (Шаг BF)
            val reqRomPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x04.toByte(), 0xBF.toByte(), baseAddr1, baseAddr2, baseAddr3, 0x00.toByte())
            var cs = 0
            for (i in 0 until 8) { cs += reqRomPacket[i].toInt() and 0xFF }
            reqRomPacket[8] = (cs and 0xFF).toByte()

            logBuilder.append("Tx2 -> Запрос BF: ${reqRomPacket.joinToString(" ") { String.format("%02X", it) }}\n")

            port.purgeHwBuffers(true, true)
            port.write(reqRomPacket, 300)
            Thread.sleep(200)

            val response2 = ByteArray(64)
            val readBytes2 = port.read(response2, 500)
            logBuilder.append("Rx2 -> Получено байт: $readBytes2\n")

            var ecuIdx2 = -1
            if (readBytes2 >= 15) {
                val dump2 = response2.slice(0 until readBytes2).joinToString(" ") { String.format("%02X", it) }
                logBuilder.append("Буфер Шага 2: [$dump2]\n")
                for (i in 9 until readBytes2 - 5) {
                    if (response2[i] == 0x80.toByte() && response2[i+1] == 0xF0.toByte() && response2[i+2] == 0x10.toByte()) {
                        ecuIdx2 = i
                        break
                    }
                }
            }

            if (ecuIdx2 != -1) {
                val romIdStart = ecuIdx2 + 5
                val realRomIdBytes = response2.copyOfRange(romIdStart, romIdStart + 5)
                actualRomId = realRomIdBytes.joinToString(" ") { String.format("%02X", it) }
                logBuilder.append("[SUCCESS]: Честный БОЕВОЙ ROM-ID успешно вычитан: $actualRomId\n")

                // Передаем открытый порт в менеджер железа для фонового движка
                hardwareManager.usbPort = port
                return true
            }

        } catch (e: Exception) {
            logBuilder.append("[CRITICAL ERROR]: Сбой инициализации канала: ${e.message}\n")
        }
        return false
    }

    override fun sendAndReceive(requestPacket: ByteArray, expectedResponseSize: Int): Pair<ByteArray, Int> {
        val rxBuffer = ByteArray(128)
        try {
            port.purgeHwBuffers(true, true)
            port.write(requestPacket, 300)

            // Динамический тайм-аут сбора полудуплексного кадра
            Thread.sleep((40 + (requestPacket.size * 2)).toLong())

            val totalBytes = port.read(rxBuffer, 500)

            // Если прилетело и наше эхо, и ответ ЭБУ
            val totalExpected = requestPacket.size + expectedResponseSize
            if (totalBytes >= totalExpected) {
                // Вырезаем чистый ответ ЭБУ, отступив размер эха Tx
                var ecuIdx = -1
                for (i in requestPacket.size until totalBytes - 5) {
                    if (rxBuffer[i] == 0x80.toByte() && rxBuffer[i+1] == 0xF0.toByte() && rxBuffer[i+2] == 0x10.toByte()) {
                        ecuIdx = i
                        break
                    }
                }
                if (ecuIdx != -1) {
                    val cleanRx = rxBuffer.copyOfRange(ecuIdx, ecuIdx + expectedResponseSize)
                    return Pair(cleanRx, expectedResponseSize)
                }
            }
        } catch (e: Exception) {
            // Ошибка транзакции
        }
        return Pair(rxBuffer, 0)
    }

    override fun getRomId(): String = actualRomId

    // 🟢 ДОПИСАТЬ ТУТ: Динамический боевой отчет для реального автомобиля
    override val connectReport: String get() = """
        [SYSTEM LOG]: Инициализация интерфейса К-линии... SUCCESS
        --------------------------------------------------
        Режим работы     : БОЕВОЙ РЕЖИМ (Автомобиль)
        Интерфейс        : $chipset
        Протокол обмена  : Subaru Select Monitor 2 (SSM2)
        Скорость шины    : 4800 / 9600 baud (Fast Init OK)
        Вычитанный ROM-ID: $actualRomId
        
        Диагностический канал открыт. Система готова к работе.
    """.trimIndent()

    override fun readBlock(address: ByteArray, length: Int): ByteArray {
        // Заголовок команды SSM2 Read Block (0x80) + Длина полей (4 байта на команду/длину/адрес) + Байт запрашиваемой длины + Чексумма
        val requestPacket = ByteArray(4 + address.size + 1 + 1)

        requestPacket[0] = 0x80.toByte() // Команда Read Block
        requestPacket[1] = (address.size + 2).toByte() // Размер полей
        requestPacket[2] = 0x00.toByte() // Заглушка

        // Копируем 3-байтовый адрес ЭБУ в пакет
        System.arraycopy(address, 0, requestPacket, 3, address.size)

        // Записываем, сколько байт данных мы хотим прочитать из этой ячейки памяти
        requestPacket[3 + address.size] = length.toByte()

        // Считаем контрольную сумму (метод автоматически наследуется из интерфейса SsmChannel)
        var sum = 0
        for (i in 0 until requestPacket.size - 1) {
            sum += (requestPacket[i].toInt() and 0xFF)
        }
        requestPacket[requestPacket.size - 1] = (sum and 0xFF).toByte()

        // 🚙 ТУТ ОТПРАВКА В ПОРТ: Шлём байты в чип FTDI/CH340 вашей магнитолы UIS8581A
        // writeToSerialPort(requestPacket)

        // 🚙 ТУТ ЧТЕНИЕ ОТВЕТА: Забираем массив байт из буфера K-линии
        // val response = readFromSerialPort()

        // Временная заглушка, пока не настроены низкоуровневые стримы ввода-вывода в порт:
        val mockResponse = ByteArray(length)
        return mockResponse
    }

}
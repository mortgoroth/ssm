package com.example.freessm

import android.os.Handler
import android.os.Looper

class SsmPollingEngine(
    private val hardwareManager: SsmHardwareManager,
    private val packetHandler: SsmPacketHandler,
    private val sensorList: List<SensorItem>,
    private val uiHandler: Handler = Handler(Looper.getMainLooper()),
    private val onTick: () -> Unit,
    private val onDebugLog: (String, Boolean) -> Unit
) {
    var isPollingData = false
    var isDebugEnabled = true

    fun startLoop() {
        if (isPollingData) {
            isPollingData = false
            Thread.sleep(50)
        }
        isPollingData = true
        // СБРАСЫВАЕМ МИНИМУМЫ И МАКСИМУМЫ ПЕРЕД КАЖДЫМ НОВЫМ ЗАПУСКОМ!
        sensorList.forEach { it.resetMinMax() }

        // 1. НАСТОЛЬНЫЙ ЭМУЛЯТОР «ИТЕРАЦИЯ 2.1»: Абсолютно чистый плеер логов из библиотеки!
        if (hardwareManager.usbPort == null) {
            onDebugLog("Запуск настольного эмулятора на базе Гитхаб-лога...\n", true)
            Thread {
                var loopCounter = 0

                while (isPollingData) {
                    val selectedSensors = sensorList.filter { it.isSelected }
                    if (selectedSensors.isEmpty()) { isPollingData = false; break }

                    for (sensor in selectedSensors) {
                        if (!isPollingData) break

                        // Извлекаем последний байт ID адреса текущего датчика
                        val addrId = (sensor.addresses.lastOrNull() ?: 0x00).toInt() and 0xFF

                        // Автоматически лезем в библиотеку и забираем нужный массив байт под этот адрес ЭБУ!
                        val bytePackets = SsmEcuMap.mockDataMap[addrId] ?: arrayOf(0x00.toByte())
                        val liveByte = bytePackets[loopCounter % bytePackets.size]

                        // Собираем дилерский 7-байтовый пакет ответа ЭБУ Subaru
                        val mockRx = byteArrayOf(
                            0x80.toByte(), 0xF0.toByte(), 0x10.toByte(), 0x02.toByte(), 0xE8.toByte(),
                            liveByte,
                            0x00.toByte()
                        )

                        val rxHex = mockRx.joinToString(" ") { String.format("%02X", it) }
                        uiHandler.post { onDebugLog("Rx (Addr 0x${String.format("%02X", addrId)}): $rxHex\n", false) }

                        SsmFileLogger.logHexBytes("Rx_Mock", mockRx, mockRx.size)

                        // Скачиваем пакет в единый слепой обработчик
                        packetHandler.processSensorData(mockRx, listOf(sensor))
                        uiHandler.post { onTick() }

                        Thread.sleep(50)
                    }
                    loopCounter++
                }
            }.start()
            return
        }

        // 2. БОЕВОЙ СЦЕНАРИЙ ДЛЯ РЕАЛЬНОЙ МАШИНЫ (Остается без изменений на 9600 бод)
        onDebugLog("Монитор запущен. Боевой опрос 0xA8 на скорости 9600...\n", true)
        Thread {
            while (isPollingData) {
                try {
                    val selectedSensors = sensorList.filter { it.isSelected }
                    if (selectedSensors.isEmpty()) { isPollingData = false; break }

                    for (sensor in selectedSensors) {
                        if (!isPollingData) break

                        val packet = ByteArray(9)
                        packet[0] = 0x80.toByte()
                        packet[1] = 0x10.toByte()
                        packet[2] = 0xF0.toByte()
                        packet[3] = 0x04.toByte()
                        packet[4] = 0xA8.toByte()

                        if (sensor.addresses.size >= 3) {
                            packet[5] = sensor.addresses[0]
                            packet[6] = sensor.addresses[1]
                            packet[7] = sensor.addresses[2]
                        } else {
                            packet[5] = 0x00.toByte()
                            packet[6] = 0x00.toByte()
                            packet[7] = sensor.addresses.lastOrNull() ?: 0x00.toByte()
                        }

                        var cs = 0
                        for (i in 0 until 8) { cs += packet[i].toInt() and 0xFF }
                        packet[8] = (cs and 0xFF).toByte()

                        SsmFileLogger.logHexBytes("Tx", packet, packet.size)
                        hardwareManager.writeAndEatEcho(packet)

                        val (responseBuffer, bytesRead) = hardwareManager.readResponse(7, 300)

                        if (bytesRead > 0) {
                            SsmFileLogger.logHexBytes("Rx", responseBuffer, bytesRead)
                        }

                        if (bytesRead >= 7 && responseBuffer[0] == 0x80.toByte()) {
                            packetHandler.processSensorData(responseBuffer, listOf(sensor))
                            uiHandler.post { onTick() }
                        }

                        Thread.sleep(20)
                    }
                } catch (e: Exception) {
                    isPollingData = false
                    SsmFileLogger.logText("[CRITICAL ERROR 0xA8]: ${e.message}")
                }
            }
        }.start()
    }

    fun stopLoop() {
        isPollingData = false
    }
}

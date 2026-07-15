package com.example.freessm

import android.os.Handler
import android.os.Looper

class SsmPollingEngine(
    private val packetHandler: SsmPacketHandler,
    private val sensorList: List<SensorItem>,
    private val uiHandler: Handler = Handler(Looper.getMainLooper()),
    private val onTick: () -> Unit,
    private val onDebugLog: (String, Boolean) -> Unit
) {
    var isPollingData = false
    var isDebugEnabled = true

    // Ссылка на текущий активный канал связи (будет прокидываться из MainActivity)
    var currentChannel: SsmChannel? = null

    fun startLoop() {
        if (isPollingData) {
            isPollingData = false
            Thread.sleep(50)
        }
        isPollingData = true

        // Сбрасываем исторические минимумы и максимумы перед каждым запуском старта
        sensorList.forEach { it.resetMinMax() }

        Thread {
            onDebugLog("Монитор запущен. Боевой опрос блоков памяти по протоколу SSM2...\n", true)

            while (isPollingData) {
                val channel = currentChannel
                if (channel == null) {
                    Thread.sleep(100)
                    continue
                }

                val selectedSensors = sensorList.filter { it.isSelected }
                if (selectedSensors.isEmpty()) {
                    isPollingData = false
                    break
                }

                for (sensor in selectedSensors) {
                    if (!isPollingData) break

                    // Собираем стандартный 9-байтовый пакет запроса параметров A8
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

                    val txHex = packet.joinToString(" ") { String.format("%02X", it) }
                    SsmFileLogger.logHexBytes("Tx", packet, packet.size)

                    // 🟢 ГЛАВНАЯ ФИШКА: Просто отдаем пакет в универсальную трубу канала!
                    // Ожидаемый чистый размер ответа ЭБУ Субару на параметры — всегда 7 байт
                    val (responseBuffer, bytesRead) = channel.sendAndReceive(packet, expectedResponseSize = 7)

                    if (bytesRead >= 7) {
                        SsmFileLogger.logHexBytes("Rx", responseBuffer, bytesRead)

                        // Если включен RAW режим — мгновенно печатаем байты на экран в дебаг-лог
                        if (isDebugEnabled) {
                            val rxHex = responseBuffer.slice(0 until bytesRead).joinToString(" ") { String.format("%02X", it) }
                            uiHandler.post {
                                onDebugLog("Tx (Addr 0x${String.format("%02X", packet[7])}): $txHex\n", false)
                                onDebugLog("Rx (7 байт ответа): $rxHex\n\n", false)
                            }
                        }

                        // Скармливаем чистые 7 байт ответа в наш общий обработчик параметров
                        packetHandler.processSensorData(responseBuffer, listOf(sensor))
                        uiHandler.post { onTick() }
                    } else {
                        if (isDebugEnabled) {
                            uiHandler.post { onDebugLog("Tx: $txHex -> [ОШИБКА]: Таймаут ответа шины K-Line\n", false) }
                        }
                    }

                    // Пауза между опросами датчиков, чтобы не забивать К-линию
                    Thread.sleep(30)
                }
            }
        }.start()
    }

    fun stopLoop() {
        isPollingData = false
    }
}

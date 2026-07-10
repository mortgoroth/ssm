package com.example.freessm

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialPort

class SsmConnectManager (
    private val context: android.content.Context,
    private val hardwareManager: SsmHardwareManager,
    private val btnTestConnect: Button,
    private val btnCodes: Button,
    private val btnData: Button,
    private val lblRomId: TextView,
    private val lblEngineType: TextView,
    private val lblMeasuringBlocksHeader: TextView,
    private val lblEcuHeader: TextView
) {
    @SuppressLint("SetTextI18n")
    fun connectToEcu(): Boolean {
        SsmLogger.log("[CONNECT]: Старт дилерского подключения...")

        // 🟢 ИСПРАВЛЕНО: Добавляем честный физический поиск шнурка FTDI через UsbManager магнитолы
        val usbManager = btnTestConnect.context.getSystemService(android.content.Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val availableDrivers = com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
            val device = driver.device

            // Проверяем системные права Андроида на шнурок
            if (!usbManager.hasPermission(device)) {
                val permissionIntent = android.app.PendingIntent.getBroadcast(
                    btnTestConnect.context, 0, android.content.Intent("com.example.freessm.USB_PERMISSION"),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)
                return false // Ждем, пока водитель нажмет ОК в окне Андроида
            }

            try {
                // Открываем физическое соединение с чипом FTDI
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    val targetPort = driver.ports[0]
                    targetPort.open(connection)

                    // Записываем открытый порт в наше общее железо!
                    hardwareManager.usbPort = targetPort
                }
            } catch (e: Exception) {
                SsmLogger.log("[CONNECT ERROR]: Не удалось открыть порт: ${e.message}")
            }
        }

        // 1. НАСТОЛЬНЫЙ ЭМУЛЯТОР
        if (hardwareManager.usbPort == null) {
            SsmLogger.log("[EMULATOR]: Шнурок не найден. Включаем эмулятор паспорта...")

            // 🟢 SUCCESS (Demo): Красим заголовок в мягкий зеленый, текст делаем белым для контраста
            lblEcuHeader.setBackgroundColor(Color.parseColor("#4CAF50"))
            lblEcuHeader.setTextColor(Color.WHITE)

            btnTestConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))

            btnTestConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            val blueColor = Color.parseColor("#2196F3")
            btnCodes.backgroundTintList = ColorStateList.valueOf(blueColor)
            btnData.backgroundTintList = ColorStateList.valueOf(blueColor)
            btnCodes.isEnabled = true
            btnData.isEnabled = true

            lblRomId.text = "ROM-ID:  1C 08 00 01 00"
            lblEngineType.text = "Engine Type: JDM Forester SF5 (EJ202)"
            lblMeasuringBlocksHeader.text = "Measuring Blocks:\n  Data: 18  Switches: 22"
            return true
        }

        // 2. БОЕВОЙ РЕЖИМ
        val port = hardwareManager.usbPort ?: return false

        try {
            // Жесткая настройка порта: 9600 бод, 8 дат-бит, стоп-бит 1, без четности!
            port.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true
            port.purgeHwBuffers(true, true)
            Thread.sleep(50)
        } catch (e: Exception) {
            SsmLogger.log("[CONNECT ERROR]: Ошибка настройки порта на 9600: ${e.message}")
            return false
        }

        // =====================================================================
        // 🟢 ИСПРАВЛЕНО ПО ОБРАЗУ И ПОДОБИЮ "ПЕРВОГО РАБОЧЕГО": БЕЗ writeAndEatEcho!
        // =====================================================================

        // Пакет №1: Инициализационный стук
        val initPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x01.toByte(), 0x00.toByte(), 0x81.toByte())
        SsmLogger.log("Tx1 -> Стук в шину: 80 10 F0 01 00 81")

        try {
            port.purgeHwBuffers(true, true)
            port.write(initPacket, 300) // Пишем напрямую, как в первой версии!
            Thread.sleep(100)

            // Выгребаем эхо и ответный байт 0x81 одним махом
            val initBuffer = ByteArray(32)
            port.read(initBuffer, 200)
        } catch (e: Exception) {
            SsmLogger.log("[CONNECT ERROR]: Ошибка пакета №1: ${e.message}")
            return false
        }

        // Пакет №2: Запрос паспорта BF 40, который приносит реальный ROM-ID!
        val reqRomIdPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x01.toByte(), 0xBF.toByte(), 0x40.toByte())
        SsmLogger.log("Tx2 -> Запрос ROM-ID: 80 10 F0 01 BF 40")

        val response = ByteArray(64)
        var readBytes = 0

        try {
            port.purgeHwBuffers(true, true)
            port.write(reqRomIdPacket, 300) // Пишем напрямую в К-линию!
            Thread.sleep(150) // Даем ЭБУ Субару время ответить на фиксированной скорости

            // Читаем всё, что прилетело из физического буфера чипа FTDI
            readBytes = port.read(response, 500)
        } catch (e: Exception) {
            SsmLogger.log("[CONNECT ERROR]: Ошибка пакета №2: ${e.message}")
            return false
        }

        SsmLogger.log("Rx2 <- Количество байт в буфере: $readBytes")

        // 🟢 ТОЧЕЧНЫЙ ДИАГНОСТИЧЕСКИЙ ВЫВОД: Выводим ВСЕ полученные байты БЕЗ условий и проверок!
        if (readBytes > 0) {
            val dumpHex = response.slice(0 until readBytes).joinToString(" ") { String.format("%02X", it) }
            SsmLogger.log("ФАКТИЧЕСКИЙ НАБОР БАЙТ В БУФЕРЕ: [$dumpHex]")
        } else {
            SsmLogger.log("ВНИМАНИЕ: Физический буфер пуст (0 байт)!")
        }

        // Временное гибкое условие, чтобы приложение не упало, пока мы смотрим лог
        if (readBytes >= 6) {
            var realRomIdHex = ""

            // Если прилетело ровно 6 байт, проверяем чистую сигнатуру ответа ЭБУ
            if (readBytes == 6 && response[0] == 0x80.toByte()) {
                val realRomIdBytes = response.copyOfRange(1, 6) // Берем байты как есть для теста
                realRomIdHex = realRomIdBytes.joinToString(" ") { String.format("%02X", it) }
            } else {
                // Стандартный поиск по сигнатуре для длинного кадра
                var ecuIdx = -1
                for (i in 0 until readBytes - 10) {
                    if (response[i] == 0x80.toByte() && response[i+1] == 0xF0.toByte() && response[i+2] == 0x10.toByte()) {
                        ecuIdx = i
                        break
                    }
                }
                if (ecuIdx != -1) {
                    val romIdStart = ecuIdx + 5
                    if (romIdStart + 5 <= readBytes) {
                        val realRomIdBytes = response.copyOfRange(romIdStart, romIdStart + 5)
                        realRomIdHex = realRomIdBytes.joinToString(" ") { String.format("%02X", it) }
                    }
                }
            }

            if (realRomIdHex.isNotEmpty()) {
                lblRomId.text = "ROM-ID:  $realRomIdHex"
                lblEngineType.text = "Engine Type: JDM Forester SF5 (EJ202)"
                lblMeasuringBlocksHeader.text = "Measuring Blocks:\n  Data: 18  Switches: 22"

                lblEcuHeader.setBackgroundColor(Color.parseColor("#4CAF50"))
                lblEcuHeader.setTextColor(Color.WHITE)

                btnTestConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                btnCodes.isEnabled = true
                btnData.isEnabled = true
                return true
            }
        }

        // При ошибке красим заголовок в красный, кнопку не трогаем
        lblEcuHeader.setBackgroundColor(Color.parseColor("#E53935"))
        lblEcuHeader.setTextColor(Color.WHITE)
        SsmLogger.log("[CONNECT FAILED]: Паспорт на BF 40 не получен.")
        return false
    }
}

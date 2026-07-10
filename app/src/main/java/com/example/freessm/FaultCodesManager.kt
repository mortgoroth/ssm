package com.example.freessm

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class FaultCodesManager(
    private val hardwareManager: SsmHardwareManager,
    private val txtConsole: TextView,
    private val lblConsoleHeader: TextView,
    private val layoutClearContainer: LinearLayout
) {
    private val uiHandler = Handler(Looper.getMainLooper())

    private fun parseCodesFromBuffer(buffer: ByteArray, length: Int): List<Int> {
        val codes = mutableListOf<Int>()
        if (length > 5 && buffer[0] == 0x80.toByte()) {
            for (i in 5 until length - 1 step 2) {
                val byte1 = buffer[i].toInt() and 0xFF
                val byte2 = buffer[i + 1].toInt() and 0xFF
                if (byte1 == 0 && byte2 == 0) continue
                codes.add((byte1 shl 8) or byte2)
            }
        }
        return codes
    }

    @SuppressLint("SetTextI18n")
    fun readSubaruFaultCodes() {
        txtConsole.text = "Reading Diagnostic Trouble Codes (DTC)...\n"
        SsmLogger.log("Вызов режима: Чтение кодов неисправностей (DTC)")

        if (hardwareManager.usbPort == null) {
            txtConsole.text = "Temporary Diagnostic Trouble Code(s):\nCode:      Description:\n----------------------------------------\n1          Subaru Fault Code: [ P0325 ]\n"
            layoutClearContainer.visibility = View.VISIBLE
            SsmLogger.log("Система работает в DEMO-режиме. Сгенерирован фейковый код P0325.")
            return
        }

        val port = hardwareManager.usbPort ?: return
        Thread {
            val tempPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x02.toByte(), 0xA0.toByte(), 0x00.toByte(), 0x22.toByte())
            try {
                SsmLogger.log("Tx -> Отправка SSM2 пакета чтения DTC (Команда 0x22)...")

                port.purgeHwBuffers(true, true)
                port.write(tempPacket, 1000)
                Thread.sleep(60)

                val buf = ByteArray(64)
                val read = port.read(buf, 1000)

                val tempCodes = parseCodesFromBuffer(buf, read)
                val errorsFound = tempCodes.isNotEmpty()

                SsmLogger.log("Rx <- Ответ ЭБУ считан ($read байт). Распознано кодов ошибок: ${tempCodes.size}")

                uiHandler.post {
                    val report = java.lang.StringBuilder()
                    report.append("Temporary Diagnostic Trouble Code(s):\nCode:      Description:\n----------------------------------------\n")
                    if (tempCodes.isEmpty()) {
                        report.append("1          ----- No Trouble Codes -----\n")
                    } else {
                        tempCodes.forEachIndexed { idx, code ->
                            report.append("${idx + 1}          Subaru Fault Code: [ $code ]\n")
                            SsmLogger.log("  -> Обнаружен активный код: [ $code ]")
                        }
                    }
                    txtConsole.text = report.toString()
                    layoutClearContainer.visibility = if (errorsFound) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                SsmLogger.log("[CRITICAL ERROR при чтении кодов ошибок]: ${e.message}")
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    fun clearEcuMemory() {
        val port = hardwareManager.usbPort ?: return
        val clearPacket = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x01.toByte(), 0xCC.toByte(), 0x4D.toByte())
        txtConsole.text = "Clearing ECU Memory..."

        SsmLogger.log("Вызов режима: Сброс памяти адаптаций ЭБУ (Команда 0xCC)")

        Thread {
            try {
                SsmLogger.log("Tx -> Отправка пакета Clear ECU Memory в Forester...")
                port.purgeHwBuffers(true, true)
                port.write(clearPacket, 1000)
                Thread.sleep(150)

                val response = ByteArray(16)
                val readBytes = port.read(response, 1000)

                uiHandler.post {
                    if (readBytes > 0 && response[0] == 0x80.toByte()) {
                        lblConsoleHeader.text = "Memory Clear Status:"
                        txtConsole.text = "[SYSTEM LOG]: Очистка памяти ЭБУ... SUCCESS\n--------------------------------------------\nВсе коды неисправностей успешно удалены из памяти."
                        layoutClearContainer.visibility = View.GONE
                        SsmLogger.log("Rx <- ЭБУ успешно подтвердил сброс памяти адаптаций (0x80 ACK)!")
                    } else {
                        txtConsole.text = "ЭБУ не подтвердил сброс. Попробуйте на заглушенном моторе."
                        SsmLogger.log("[WARNING] ЭБУ проигнорировал команду сброса или вернул пустой буфер. Прочитано байт: $readBytes")
                    }
                }
            } catch (e: Exception) {
                SsmLogger.log("[CRITICAL ERROR при очистке памяти ЭБУ]: ${e.message}")
            }
        }.start()
    }
}

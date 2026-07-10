package com.example.freessm

import com.hoho.android.usbserial.driver.UsbSerialPort

//class SsmHardwareManager(private val context: android.content.Context) {
class SsmHardwareManager() {
    var usbPort: UsbSerialPort? = null

    fun writeAndEatEcho(packet: ByteArray): Boolean {
        val port = usbPort ?: return false
        try {
            port.write(packet, 300)
            val echoBuffer = ByteArray(packet.size)
            port.read(echoBuffer, 200)
            return true
        } catch (e: Exception) { return false }
    }

    fun readResponse(size: Int, timeout: Int): Pair<ByteArray, Int> {
        val buffer = ByteArray(size)
        val port = usbPort ?: return Pair(buffer, 0)
        return try {
            val bytesRead = port.read(buffer, timeout)
            Pair(buffer, bytesRead)
        } catch (e: Exception) {
            Pair(buffer, 0)
        }
    }
}

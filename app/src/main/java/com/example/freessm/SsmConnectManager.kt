package com.example.freessm

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber

class SsmConnectManager(
    private val context: Context,
    private val hardwareManager: SsmHardwareManager
) {
    // Хранилище текущего активного канала связи
    var activeChannel: SsmChannel? = null
        private set

    fun initializeChannel(): SsmChannel {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
            val device = driver.device

            // Запрос системных прав Андроида (флаг UPDATE_CURRENT для старых магнитол)
            if (!usbManager.hasPermission(device)) {
                val permissionIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, android.content.Intent("com.example.freessm.USB_PERMISSION"),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)

                // Временный пустой канал, пока водитель не нажмет ОК в окне Андроида
                val stub = EmulatorChannel()
                activeChannel = stub
                return stub
            }

            try {
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    val targetPort = driver.ports[0]
                    targetPort.open(connection)

                    // 🚙 Шнурок на месте! Создаем и возвращаем боевой железный канал
                    val hardwareChannel = RealHardwareChannel(targetPort, hardwareManager)
                    activeChannel = hardwareChannel
                    return hardwareChannel
                }
            } catch (e: Exception) {
                // Если порт занят фоновым процессором TPMS, уходим на эмулятор
            }
        }

        // 🤖 Шнурка физически нет! Незаметно подсовываем приложению плеер логов
        val emulatorChannel = EmulatorChannel()
        activeChannel = emulatorChannel
        return emulatorChannel
    }
}

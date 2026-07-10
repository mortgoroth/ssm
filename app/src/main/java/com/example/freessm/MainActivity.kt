package com.example.freessm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var lblConsoleHeader: TextView
    private lateinit var btnToggleViewMode: Button
    private lateinit var layoutTableContainer: LinearLayout
    private lateinit var layoutGraphContainer: LinearLayout
    private lateinit var ssmGraphView: SsmGraphView
//    private var isGraphViewMode = false
    private var currentViewMode = 0 // 0 - Таблица, 1 - Компакт, 2 - График
    private lateinit var compactAdapter: SsmCompactAdapter
    private lateinit var hardwareManager: SsmHardwareManager
    private lateinit var connectManager: SsmConnectManager
    private lateinit var faultCodesManager: FaultCodesManager

    private val packetHandler = SsmPacketHandler()

    private lateinit var pollingEngine: SsmPollingEngine
    private lateinit var viewController: SsmViewController

    private lateinit var lblRomId: TextView
    private lateinit var txtConsole: TextView
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var rvSensors: RecyclerView
    private lateinit var btnStartStop: Button
    private lateinit var btnClear: Button

    private lateinit var btnGlobalLogs: Button
    private lateinit var txtGlobalLogConsole: TextView
    private lateinit var btnClearSystemLog: Button
    private lateinit var layoutClearContainer: LinearLayout
    private lateinit var lblEcuHeader: TextView
    private lateinit var btnTestConnect: Button
    private lateinit var btnCodes: Button
    private lateinit var btnData: Button

    private lateinit var layoutDebugContainer: LinearLayout
    private lateinit var txtDebugLog: TextView

    private lateinit var lblEngineType: TextView
    private lateinit var lblMeasuringBlocksHeader: TextView
    private lateinit var layoutLegendContainer: LinearLayout
    private lateinit var sensorAdapter: SensorAdapter

    private val sensorList = SsmEcuMap.fullSensorList

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkTimeAndSetTheme()

        setContentView(R.layout.activity_main)

        lblEcuHeader = findViewById(R.id.lblEcuHeader)

        hardwareManager = SsmHardwareManager()

        btnTestConnect = findViewById(R.id.btnTestConnect)
        btnCodes = findViewById(R.id.btnCodes)
        btnData = findViewById(R.id.btnData)
        btnClear = findViewById(R.id.btnClear)
        btnGlobalLogs = findViewById(R.id.btnGlobalLogs)
        txtGlobalLogConsole = findViewById(R.id.txtGlobalLogConsole)
        btnClearSystemLog = findViewById(R.id.btnClearSystemLog)
        layoutClearContainer = findViewById(R.id.layoutClearContainer)
        lblConsoleHeader = findViewById(R.id.lblConsoleHeader)
        lblEngineType = findViewById(R.id.lblEngineType)
        lblRomId = findViewById(R.id.lblRomId)
        lblMeasuringBlocksHeader = findViewById(R.id.lblMeasuringBlocksHeader)
        layoutLegendContainer = findViewById(R.id.layoutLegendContainer)
        txtConsole = findViewById(R.id.txtConsole)
        viewFlipper = findViewById(R.id.viewFlipper)
        rvSensors = findViewById(R.id.rvSensors)
        btnStartStop = findViewById(R.id.btnStartStop)
        layoutDebugContainer = findViewById(R.id.layoutDebugContainer)
        txtDebugLog = findViewById(R.id.txtDebugLog)
        btnToggleViewMode = findViewById(R.id.btnToggleViewMode)
        layoutTableContainer = findViewById(R.id.layoutTableContainer)
        layoutGraphContainer = findViewById(R.id.layoutGraphContainer)
        ssmGraphView = findViewById(R.id.ssmGraphView)

        val rvCompact = findViewById<RecyclerView>(R.id.rvCompact)
        rvCompact.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        compactAdapter = SsmCompactAdapter(emptyList())
        rvCompact.adapter = compactAdapter


        val btnExit = findViewById<Button>(R.id.btnExit)
        val btnDebugMode = findViewById<ToggleButton>(R.id.btnDebugMode)

        // Инициализируем хелпер графики
        viewController = SsmViewController(this, btnCodes, btnData, btnGlobalLogs, btnClear, layoutLegendContainer, ssmGraphView)

        connectManager = SsmConnectManager(
            context = this,
            hardwareManager = hardwareManager,
            btnTestConnect = btnTestConnect,
            btnCodes = btnCodes,
            btnData = btnData,
            lblRomId = lblRomId,
            lblEngineType = lblEngineType,
            lblMeasuringBlocksHeader = lblMeasuringBlocksHeader,
            lblEcuHeader = lblEcuHeader
        )

        faultCodesManager = FaultCodesManager(
            hardwareManager, txtConsole, lblConsoleHeader, layoutClearContainer
        )

        // Инициализируем фоновый движок через лямбда-коллбеки
        pollingEngine = SsmPollingEngine(
            hardwareManager, packetHandler, sensorList,
            onTick = {
//                if (findViewById<View>(R.id.rvCompact).visibility == View.VISIBLE) {
//                    compactAdapter = SsmCompactAdapter(sensorList.filter { it.isSelected })
//                    findViewById<RecyclerView>(R.id.rvCompact).adapter = compactAdapter
//                }
                if (currentViewMode == 1) { // 1 - это наш режим Compact
                    compactAdapter.notifyDataSetChanged()
                }

                sensorAdapter.notifyDataSetChanged()

                viewController.updateGraphLegend(sensorList, currentViewMode == 2, pollingEngine.isDebugEnabled)
                // Передаем точки на график
                sensorList.filter { it.isSelected }.forEach { sensor ->
                    sensor.currentValue.toDoubleOrNull()?.let { ssmGraphView.addDataPoint(sensor.title, it) }
                }

                val btnDebugModeLocal = findViewById<ToggleButton>(R.id.btnDebugMode)
                layoutDebugContainer.visibility = if (btnDebugModeLocal.isChecked) View.VISIBLE else View.GONE

            },
            onDebugLog = { logText, shouldClear ->
                if (shouldClear) txtDebugLog.text = ""
                txtDebugLog.append(logText)
            }
        )

        val updateViewModeButtonState = {
            val hasSelected = sensorList.any { it.isSelected }
            btnToggleViewMode.isEnabled = hasSelected
            btnToggleViewMode.alpha = if (hasSelected) 1.0f else 0.7f

            // Контроль главной кнопки Старт/Стоп
            // Если опрос УЖЕ идет — кнопка всегда активна (чтобы можно было нажать Stop!)
            if (pollingEngine.isPollingData) {
                btnStartStop.isEnabled = true
                btnStartStop.alpha = 1.0f
            } else {
                // Если опрос стоит — кнопка активна только при наличии галочек
                btnStartStop.isEnabled = hasSelected
                btnStartStop.alpha = if (hasSelected) 1.0f else 0.7f
            }
        }

        sensorAdapter = SensorAdapter(sensorList) {
            updateViewModeButtonState()

            pollingEngine.isPollingData
        }
        rvSensors.layoutManager = LinearLayoutManager(this)
        rvSensors.adapter = sensorAdapter

        updateViewModeButtonState() // Блокируем кнопку на старте

        btnDebugMode.setOnCheckedChangeListener { _, isChecked ->
            pollingEngine.isDebugEnabled = isChecked
//            layoutDebugContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        btnTestConnect.setOnClickListener {
            // 1. Принудительно возвращаем правую панель на Слайд 0 (консоль)
            viewFlipper.displayedChild = 0

            // Менеджер сам знает, эмулятор это или боевой шнурок, и вернет true/false.
            val isSuccess = connectManager.connectToEcu()

            if (isSuccess) {
                // 🚙 УСПЕХ: Мгновенно собираем данные и пишем рапорт в терминал БЕЗ всяких всплывающих окон!
                val chipName = hardwareManager.usbPort?.driver?.device?.productName ?: "Virtual Emulator"
                val realRomId = lblRomId.text.toString().replace("ROM-ID:  ", "")
                val realEngine = lblEngineType.text.toString().replace("Engine Type:  ", "")

                lblConsoleHeader.text = "Connection Test Status:"
                layoutClearContainer.visibility = View.GONE
                txtConsole.text = """
                    [SYSTEM LOG]: Инициализация интерфейса... SUCCESS
                    --------------------------------------------------
                    USB Chipset      : $chipName
                    Bus Protocol     : Subaru Select Monitor 2 (SSM2)
                    Baud Rate        : 4800 baud (Fixed K-Line)
                    Detected ECU     : $realEngine
                    ECU ROM-ID       : $realRomId
                    
                    Система полностью готова к чтению параметров.
                    Выберите интересующий режим в левой панели управления.
                """.trimIndent()
            } else {
                lblConsoleHeader.text = "Connection Test Status: FAILED"
                layoutClearContainer.visibility = View.GONE
                txtConsole.text = """
                    [ERROR]: Нет ответа от блока управления двигателем!
                    --------------------------------------------------
                    Возможные причины неисправности:
                    1. Зажигание машины выключено (переведите ключ в положение ON).
                    2. Фоновое приложение магнитолы (например, TPMS) заблокировало USB-порт.
                    3. Плохой контакт в диагностическом разъеме OBD2.
                    
                    Проверьте кабель и повторите попытку подключения.
                """.trimIndent()
            }
        }

        btnCodes.setOnClickListener {
            lblConsoleHeader.text = "Diagnostic Trouble Codes:"
            txtConsole.text = "Подключение к ЭБУ... Чтение кодов неисправностей..."
            if (pollingEngine.isPollingData) {
                pollingEngine.stopLoop()
                btnStartStop.text = "Start"
                btnStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))
                sensorAdapter.notifyDataSetChanged()
            }
            viewFlipper.displayedChild = 0
            if (connectManager.connectToEcu()) faultCodesManager.readSubaruFaultCodes()
            viewController.highlightActiveMenuButton(btnCodes)
        }

        btnData.setOnClickListener {
            viewFlipper.displayedChild = 1
            sensorAdapter.notifyDataSetChanged()
            connectManager.connectToEcu()
            viewController.highlightActiveMenuButton(btnData)
        }

        btnGlobalLogs.setOnClickListener {
            viewFlipper.displayedChild = 2
            txtGlobalLogConsole.text = SsmLogger.getFullLog()

            // Настраиваем подсветку: красим кнопку логов в активный тёмно-синий
            viewController.highlightActiveMenuButton(btnGlobalLogs)
        }

        // Кнопка очистки внутри самого экрана логов
        btnClearSystemLog.setOnClickListener {
            SsmLogger.clear()
            txtGlobalLogConsole.text = SsmLogger.getFullLog()
        }

        btnClear.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Сброс памяти ЭБУ").setIcon(android.R.drawable.ic_dialog_alert)
            builder.setMessage("Вы уверены, что хотите очистить память ЭБУ и сбросить все адаптации двигателя?")
            builder.setPositiveButton("Да, сбросить") { dialog, _ ->
                dialog.dismiss()
                if (pollingEngine.isPollingData) {
                    pollingEngine.stopLoop()
                    btnStartStop.text = "Start"
                    btnStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))
                    sensorAdapter.notifyDataSetChanged()
                }
                viewFlipper.displayedChild = 0
                if (connectManager.connectToEcu()) faultCodesManager.clearEcuMemory()
                viewController.highlightActiveMenuButton(btnCodes) // Возвращаем фокус на экран терминала отчетов
            }
            builder.setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            builder.show()
        }

        btnStartStop.setOnClickListener {
            if (pollingEngine.isPollingData) {
                pollingEngine.stopLoop()
                btnStartStop.text = "Start"
                btnStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))
                SsmFileLogger.closeSession()
                sensorAdapter.notifyDataSetChanged()
            } else {
                if (sensorList.count { it.isSelected } == 0) return@setOnClickListener
                sensorList.forEach { it.minValue = null; it.maxValue = null }
                ssmGraphView.clearAllGraphs()
                sensorList.sortWith(compareByDescending { it.isSelected })
                sensorAdapter.notifyDataSetChanged()

                updateViewModeButtonState()
                pollingEngine.startLoop()
                SsmFileLogger.startNewSession(this)
                btnStartStop.text = "Stop"
                btnStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED))
            }
        }

        btnToggleViewMode.setOnClickListener {
            val activeSensors = sensorList.filter { it.isSelected }
            if (activeSensors.isEmpty()) return@setOnClickListener

            // Листаем режим отображения вперед по кругу
            currentViewMode = (currentViewMode + 1) % 3

            // Находим нашу сетку 3 колонки (локально, чтобы не плодить переменные)
            val rvCompactLocal = findViewById<RecyclerView>(R.id.rvCompact)
            // Находим тумблер дебага
            val btnDebugMode = findViewById<ToggleButton>(R.id.btnDebugMode)

            when (currentViewMode) {
                0 -> { // РЕЖИМ 0: ТАБЛИЦА СЕНСОРОВ
                    layoutGraphContainer.visibility = View.GONE
                    rvCompactLocal.visibility = View.GONE
                    layoutTableContainer.visibility = View.VISIBLE
                    btnToggleViewMode.text = "View: Table"
                    btnToggleViewMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#757575")))
                }
                1 -> { // РЕЖИМ 1: КОМПАКТНЫЙ ВИД (3 Колонки)
                    layoutTableContainer.visibility = View.GONE
                    layoutGraphContainer.visibility = View.GONE
                    rvCompactLocal.visibility = View.VISIBLE
                    btnToggleViewMode.text = "View: Compact"
                    btnToggleViewMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))

                    // Пушим в адаптер актуальный набор параметров
                    compactAdapter = SsmCompactAdapter(activeSensors)
                    rvCompactLocal.adapter = compactAdapter
                }
                2 -> { // РЕЖИМ 2: ОСЦИЛЛОГРАФ / ГРАФИКИ
                    layoutTableContainer.visibility = View.GONE
                    rvCompactLocal.visibility = View.GONE
                    layoutGraphContainer.visibility = View.VISIBLE
                    btnToggleViewMode.text = "View: Graphs"
                    btnToggleViewMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#008000")))
                }
            }

            // Фиксируем окно дебага: его видимость полностью изолирована от экранов и подчиняется кнопке дебага!
            layoutDebugContainer.visibility = if (btnDebugMode.isChecked) View.VISIBLE else View.GONE

            val isGraphActive = (currentViewMode == 2)
            viewController.updateGraphLegend(sensorList, isGraphActive, pollingEngine.isDebugEnabled)
        }

        btnExit.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingEngine.stopLoop()
        SsmFileLogger.closeSession()
        try { hardwareManager.usbPort?.close() } catch (e: Exception) {}
    }

    private fun checkTimeAndSetTheme() {
        val calendar = java.util.Calendar.getInstance()

        // Получаем текущий месяц (в Андроиде они считаются от 0 до 11: 0 - Январь, 11 - Декабрь)
        val month = calendar.get(java.util.Calendar.MONTH)

        // Получаем текущее время на часах в минутах от начала суток (чтобы легко сравнивать)
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        val currentMinutesFromMidnight = (currentHour * 60) + currentMinute

        // 📊 Массив статистики: Время ВОСХОДА для каждого месяца (в минутах от 00:00)
        // Янв (08:30), Фев (07:40), Мар (06:40), Апр (05:20), Май (04:10), Июн (03:40)
        // Июл (04:10), Авг (05:00), Сен (06:00), Окт (07:00), Ноя (08:00), Дек (08:40)
        val sunriseSchedule = intArrayOf(
            510, 460, 400, 320, 250, 220,
            250, 300, 360, 420, 480, 520
        )

        // 📊 Массив статистики: Время ЗАКАТА для каждого месяца (в минутах от 00:00)
        // Янв (16:30), Фев (17:30), Мар (18:30), Апр (19:40), Май (20:40), Июн (21:20)
        // Июл (21:00), Авг (20:00), Сен (18:40), Окт (17:20), Ноя (16:20), Дек (16:00)
        val sunsetSchedule = intArrayOf(
            990,  1050, 1110, 1180, 1240, 1280,
            1260, 1200, 1120, 1040, 980,  960
        )

        // Достаем из нашей базы точные минуты для текущего месяца
        val todaySunrise = sunriseSchedule[month]
        val todaySunset = sunsetSchedule[month]

        // 🌙 Ночь наступает, если мы проснулись ДО рассвета ИЛИ за рулем уже ПОСЛЕ заката
        val isNight = currentMinutesFromMidnight < todaySunrise || currentMinutesFromMidnight >= todaySunset

        val targetMode = if (isNight) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES // Включаем ночь
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO  // Включаем день
        }

        val currentAndroidMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isCurrentAndroidNight = currentAndroidMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNight != isCurrentAndroidNight) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

}

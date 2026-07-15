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
//    private lateinit var lblEcuHeader: View
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
    private lateinit var themeHelper: SsmThemeHelper


    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeHelper = SsmThemeHelper(this)
        themeHelper.checkTimeAndSetTheme()

        setContentView(R.layout.activity_main)

//        lblEcuHeader = findViewById(R.id.lblEcuHeader)

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

        connectManager = SsmConnectManager(context = this, hardwareManager = hardwareManager)
        // Инициализируем хелпер графики
        viewController = SsmViewController(this, btnTestConnect, btnCodes, btnData, btnGlobalLogs, btnClear, layoutLegendContainer, ssmGraphView, connectManager)

        // Поиск нижнего черного текстового поля для вывода сырых байт дебага
        val txtDebugLogConsole = findViewById<TextView>(R.id.txtDebugLogConsole)

        faultCodesManager = FaultCodesManager(
            hardwareManager, txtConsole, lblConsoleHeader, layoutClearContainer
        )

        // Инициализируем фоновый движок через лямбда-коллбеки
        pollingEngine = SsmPollingEngine(
            packetHandler = packetHandler, // Твой оригинальный SsmPacketHandler
            sensorList = sensorList,       // Твой список SensorItem
            onTick = {
                // Логика переключения компактного режима во время опроса
                if (findViewById<View>(R.id.rvCompact).visibility == View.VISIBLE) {
                    compactAdapter = SsmCompactAdapter(sensorList.filter { it.isSelected })
                    findViewById<RecyclerView>(R.id.rvCompact).adapter = compactAdapter
                }

                if (currentViewMode == 1) { // Режим Compact
                    compactAdapter.notifyDataSetChanged()
                }

                sensorAdapter.notifyDataSetChanged()

                // Обновляем легенду графиков
                viewController.updateGraphLegend(sensorList, isGraphViewMode = currentViewMode == 2, pollingEngine.isDebugEnabled)

                // Передаем живые точки на экран кастомного графика-осциллографа
                sensorList.filter { it.isSelected }.forEach { sensor ->
                    sensor.currentValue.toDoubleOrNull()?.let { ssmGraphView.addDataPoint(sensor.title, it) }
                }

            },
            onDebugLog = { logText, shouldClean ->
                // Логика вывода бегущих байт параметров в нижнее черное окно во время Start опроса
                if (shouldClean) txtDebugLogConsole.text = ""
                txtDebugLogConsole.append(logText)
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
            layoutDebugContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnTestConnect.setOnClickListener {
            window.decorView.findViewById<android.view.View>(R.id.btnClear)?.let { btn ->
                btn.visibility = android.view.View.GONE
            }
            viewFlipper.displayedChild = 0

            val channel = connectManager.initializeChannel()
            val isConnectSuccess = channel.open()

            // Выводим отладочный лог K-Line в консоль терминала на Слайде 0
            txtDebugLogConsole.text = channel.debugLog

            if (isConnectSuccess) {
                pollingEngine.currentChannel = channel
                val realRomId = channel.requestRomId()
                lblRomId.text = "ROM-ID:  $realRomId"

                lblEngineType.text = if (channel.chipset == "Virtual Emulator") "Engine Type: JDM Forester SF5 (EJ202)" else "Engine Type: Subaru Forester SF5"
                lblMeasuringBlocksHeader.text = "Measuring Blocks:\n  Data: 18  Switches: 22"

                val greenColor = android.graphics.Color.parseColor("#4CAF50")

                // Кнопка CONNECT триумфально загорается ЗЕЛЕНЫМ цветом
                btnTestConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(greenColor)
                btnTestConnect.setTextColor(android.graphics.Color.WHITE)

                val blueColor = android.graphics.Color.parseColor("#2196F3")
                val blueStateList = android.content.res.ColorStateList.valueOf(blueColor)

                // Разблокируем кнопки разделов диагностики и возвращаем им дефолтный голубой цвет
                btnCodes.backgroundTintList = blueStateList
                btnData.backgroundTintList = blueStateList
                btnGlobalLogs.backgroundTintList = blueStateList

                btnCodes.isEnabled = true
                btnData.isEnabled = true

                txtConsole.text = channel.connectReport

            } else {
                pollingEngine.currentChannel = null
                val redColor = android.graphics.Color.parseColor("#E53935")

                // При ошибке кнопка CONNECT железно загорается КРАСНЫМ цветом
                btnTestConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(redColor)
                btnTestConnect.setTextColor(android.graphics.Color.WHITE)

                btnCodes.isEnabled = false
                btnData.isEnabled = false
                lblRomId.text = "ROM-ID:  FAILED"
                lblEngineType.text = "Engine Type: No Response"
                txtConsole.text = channel.connectReport
            }
        }

        btnCodes.setOnClickListener {
            if (connectManager.activeChannel == null) return@setOnClickListener

            lblConsoleHeader.text = "Diagnostic Trouble Codes:"
            txtConsole.text = "Подключение к ЭБУ... Чтение кодов неисправностей..."
            if (pollingEngine.isPollingData) {
                pollingEngine.stopLoop()
                btnStartStop.text = "Start"
                btnStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))
                sensorAdapter.notifyDataSetChanged()
            }
            viewFlipper.displayedChild = 0
            if (connectManager.activeChannel != null) faultCodesManager.readSubaruFaultCodes()

            viewController.highlightActiveMenuButton(btnCodes)
            window.decorView.findViewById<android.view.View>(R.id.btnClear)?.visibility = android.view.View.VISIBLE
        }

        btnData.setOnClickListener {
            // 🟢 ИСПРАВЛЕНО: Если связи с ЭБУ нет — блокируем вход в параметры мотора
            if (connectManager.activeChannel == null) return@setOnClickListener
            viewFlipper.displayedChild = 1
            sensorAdapter.notifyDataSetChanged()
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
                if (connectManager.activeChannel != null) faultCodesManager.clearEcuMemory()
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

}

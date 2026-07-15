package com.example.freessm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SsmViewController(
    private val context: Context,
    private val btnTestConnect: Button,
    private val btnCodes: Button,
    private val btnData: Button,
    private val btnGlobalLogs: Button,
    private val btnClear: Button,
    private val layoutLegendContainer: LinearLayout,
    private val ssmGraphView: SsmGraphView,
    private val connectManager: SsmConnectManager
) {
    fun highlightActiveMenuButton(activeButton: android.widget.Button) {
        val isConnected = connectManager.activeChannel != null
        val allButtons = listOf(btnTestConnect, btnCodes, btnData, btnGlobalLogs)

        for (btn in allButtons) {
            if (btn == activeButton) {
                // 🟢 ДЛЯ АКТИВНОЙ КНОПКИ:
                if (btn.id == R.id.btnTestConnect) {
                    btn.setTextColor(android.graphics.Color.WHITE)
                } else {
                    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))
                    btn.setTextColor(android.graphics.Color.WHITE)
                }
            } else {
                // ⚪ ДЛЯ ВСЕХ ОСТАЛЬНЫХ (НЕАКТИВНЫХ) КНОПОК:
                if (btn.id == R.id.btnTestConnect) {
                    // 🟢 ИСПРАВЛЕНО: Убрали обращение к pollingEngine.
                    // Если связь с машиной есть — держим кнопку зелёной, иначе — возвращаем дефолтный голубой.
                    if (isConnected) {
                        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")))
                    } else {
                        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")))
                    }
                    btn.setTextColor(android.graphics.Color.WHITE)
                    continue
                }

                if (btn.id == R.id.btnCodes || btn.id == R.id.btnData) {
                    if (!isConnected) {
                        btn.isEnabled = false
                        continue
                    }
                }

                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")))
                btn.setTextColor(android.graphics.Color.WHITE)
            }
        }
    }

    fun updateGraphLegend(sensorList: List<SensorItem>, isGraphViewMode: Boolean, isDebugEnabled: Boolean) {
        val selectedSensors = sensorList.filter { it.isSelected }

        // Если режим графиков выключен или список пуст — полностью очищаем панель
        if (!isGraphViewMode || selectedSensors.isEmpty()) {
            layoutLegendContainer.removeAllViews()
            return
        }

        // Принудительно очищаем и перестраиваем сетку легенды, так как её структура зависит от режима Дебага
        layoutLegendContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)

        // ОПРЕДЕЛЯЕМ ЛИМИТ ЭЛЕМЕНТОВ В ОДНОЙ СТРОКЕ:
        // Если дебаг включен — на экране тесно, переносим строку каждые 2 датчика. Иначе — каждые 4.
        val maxItemsPerRow = if (isDebugEnabled) 2 else 4

        var currentRowLayout: LinearLayout? = null

        selectedSensors.forEachIndexed { index, sensor ->
            // Создаем новую горизонтальную строчку-контейнер, если достигли лимита или это самый первый элемент
            if (index % maxItemsPerRow == 0) {
                currentRowLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }
                layoutLegendContainer.addView(currentRowLayout)
            }

            // Надуваем плашку датчика
            val legendItem = inflater.inflate(R.layout.item_legend, currentRowLayout, false)
            val colorIndicator = legendItem.findViewById<View>(R.id.viewColorIndicator)
            val tvLegendText = legendItem.findViewById<TextView>(R.id.tvLegendText)

            // Красим маркер
            val graphColors = ssmGraphView.colors
            colorIndicator.setBackgroundColor(graphColors[index % graphColors.size])

            // Заполняем данные
            tvLegendText.text = "${sensor.title}: ${sensor.currentValue} ${sensor.unit}"

            // Добавляем плашку в текущую активную строку
            currentRowLayout?.addView(legendItem)
        }
    }

}

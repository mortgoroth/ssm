package com.example.freessm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SsmViewController(
    private val context: Context,
    private val btnCodes: Button,
    private val btnData: Button,
    private val btnGlobalLogs: Button,
    private val btnClear: Button,
    private val layoutLegendContainer: LinearLayout,
    private val ssmGraphView: SsmGraphView
) {
    fun highlightActiveMenuButton(activeButton: android.widget.Button) {
        // 1. Собираем все кнопки левой панели в один список для массовой обработки
        val allButtons = listOf(btnCodes, btnData, btnGlobalLogs)

        for (btn in allButtons) {
            if (btn == activeButton) {
                // 🟢 ДЛЯ АКТИВНОЙ КНОПКИ:
                // Красим её в твой фирменный цвет (например, глубокий синий)
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F377E")))

                // Фишка: силой заставляем текст становиться ярко-белым (или контрастным теме)
                btn.setTextColor(android.graphics.Color.WHITE)
            } else {
                // ⚪ ДЛЯ ВСЕХ ОСТАЛЬНЫХ (НЕАКТИВНЫХ) КНОПОК:
                // Возвращаем стандартный серый/синий цвет фона из твоей разметки
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")))

                // Текст неактивных кнопок пусть всегда берет основной адаптивный цвет системы!
                btn.setTextColor(activeButton.context.getColor(android.R.color.white))
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

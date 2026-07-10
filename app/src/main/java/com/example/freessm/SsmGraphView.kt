package com.example.freessm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SsmGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Хранилище истории точек для каждого датчика (максимум 100 последних значений)
    private val sensorHistories = mutableMapOf<String, MutableList<Double>>()
    private val maxPoints = 100

    // Настройки кистей для рисования
    private val linePaint = Paint().apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    // Список фиксированных цветов для разных датчиков
    val colors = listOf(
        Color.BLUE,                                  // Синий (Температура)
        Color.RED,                                   // Красный (Тахометр)
        Color.parseColor("#008000"),                 // Зеленый (Скорость)
        Color.parseColor("#A000A0"),                 // Фиолетовый (УОЗ)
        Color.parseColor("#FF8C00"),                 // Оранжевый (Дроссель)
        Color.parseColor("#00FFFF"),                 // Голубой / Циан
        Color.parseColor("#FF00FF"),                 // Пурпурный / Маджента
        Color.parseColor("#7FFF00"),                 // Салатовый
        Color.parseColor("#FFD700"),                 // Золотой / Желтый
        Color.parseColor("#FF1493"),                 // Розовый неон
        Color.parseColor("#1E90FF"),                 // Светло-синий
        Color.parseColor("#8B4513"),                 // Коричневый
        Color.parseColor("#4682B4"),                 // Стальной синий
        Color.parseColor("#00FA9A"),                 // Мятный
        Color.parseColor("#FF4500")                  // Красно-оранжевый
    )

    // Метод добавления новой точки замера в график
    fun addDataPoint(sensorTitle: String, value: Double) {
        if (!sensorHistories.containsKey(sensorTitle)) {
            sensorHistories[sensorTitle] = mutableListOf()
        }
        val history = sensorHistories[sensorTitle]!!
        history.add(value)

        // Держим в памяти только последние 100 точек, чтобы не переполнять ОЗУ
        if (history.size > maxPoints) {
            history.removeAt(0)
        }
        // Принудительно командуем экрану перерисовать график
        invalidate()
    }

    // Полный сброс графиков при нажатии кнопки Stop
    fun clearAllGraphs() {
        sensorHistories.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Рисуем сетку на заднем фоне (как в FreeSSM)
        val gridLines = 5
        for (i in 1 until gridLines) {
            val y = h * i / gridLines
            canvas.drawLine(0f, y, w, y, gridPaint)
            val x = w * i / gridLines
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }

        // 2. Рисуем бегущие линии графиков датчиков
        var colorIndex = 0
        for ((_, history) in sensorHistories) {
            if (history.isEmpty()) continue

            linePaint.color = colors[colorIndex % colors.size]
            colorIndex++

            // Автоматическое масштабирование графика по высоте
            val maxVal = history.maxOrNull() ?: 1.0
            val minVal = history.minOrNull() ?: 0.0
            val delta = if (maxVal == minVal) 1.0 else maxVal - minVal

            val stepX = w / (maxPoints - 1)
            var lastX = 0f

            // Вычисляем стартовую Y координату первой точки
            val firstYNorm = ((history[0] - minVal) / delta).toFloat()
            var lastY = h - (firstYNorm * (h - 20f) + 10f)

            for (i in 1 until history.size) {
                val currentX = i * stepX
                val normY = ((history[i] - minVal) / delta).toFloat()
                val currentY = h - (normY * (h - 20f) + 10f)

                // Проводим линию между прошлой и текущей точкой замера
                canvas.drawLine(lastX, lastY, currentX, currentY, linePaint)

                lastX = currentX
                lastY = currentY
            }
        }
    }
}

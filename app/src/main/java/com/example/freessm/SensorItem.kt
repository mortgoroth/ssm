package com.example.freessm

data class SensorItem(
    val title: String,
    val addresses: ByteArray,
    val unit: String,
    var isSelected: Boolean = false,
    var currentValue: String = "---",
    var minValue: Double? = null,
    var maxValue: Double? = null,
    val calculate: (ByteArray, Int) -> String
) {
    // Вспомогательный метод для быстрого сброса истории при каждом новом старте опроса
    fun resetMinMax() {
        minValue = null
        maxValue = null
    }
}

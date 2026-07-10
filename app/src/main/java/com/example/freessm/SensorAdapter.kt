package com.example.freessm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SensorAdapter(
    private val sensors: List<SensorItem>,
    private val isPollingProvider: () -> Boolean // Передаем состояние опроса из MainActivity
) : RecyclerView.Adapter<SensorAdapter.SensorViewHolder>() {

    class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutSensorRow: LinearLayout = view.findViewById(R.id.layoutSensorRow)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMin: TextView = view.findViewById(R.id.tvMin)
        val tvValue: TextView = view.findViewById(R.id.tvValue)
        val tvMax: TextView = view.findViewById(R.id.tvMax)
        val tvUnit: TextView = view.findViewById(R.id.tvUnit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = if (isPollingProvider()) {
            sensors.filter { it.isSelected }[position]
        } else {
            sensors[position]
        }

        holder.tvTitle.text = sensor.title
        holder.tvValue.text = sensor.currentValue
        holder.tvUnit.text = sensor.unit

        // Выводим Min и Max, если они уже успели рассчитаться
        holder.tvMin.text = sensor.minValue?.let { String.format("%.1f", it) } ?: "---"
        holder.tvMax.text = sensor.maxValue?.let { String.format("%.1f", it) } ?: "---"

        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = sensor.isSelected

        // КЛИК ПО ВСЕЙ СТРОКЕ: Переключает галочку, только если опрос НЕ запущен
        holder.layoutSensorRow.setOnClickListener {
            if (!isPollingProvider()) {
                sensor.isSelected = !sensor.isSelected
                holder.cbSelect.isChecked = sensor.isSelected
                isPollingProvider()
            }
        }
    }

    // Если опрос запущен — показываем только отмеченные. Если на паузе — показываем ВСЕ строки.
    override fun getItemCount(): Int {
        return if (isPollingProvider()) {
            sensors.count { it.isSelected }
        } else {
            sensors.size
        }
    }
}

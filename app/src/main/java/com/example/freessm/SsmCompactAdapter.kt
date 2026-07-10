package com.example.freessm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SsmCompactAdapter(private val sensors: List<SensorItem>) :
    RecyclerView.Adapter<SsmCompactAdapter.CompactViewHolder>() {

    class CompactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val lblTitle: TextView = view.findViewById(R.id.lblCompactTitle)
        val lblValue: TextView = view.findViewById(R.id.lblCompactValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sensor_compact, parent, false)
        return CompactViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompactViewHolder, position: Int) {
        val sensor = sensors[position]
        // Убираем переносы строк для красивого отображения в сетке
        holder.lblTitle.text = sensor.title.replace("\n", " ")
        holder.lblValue.text = "${sensor.currentValue} ${sensor.unit}"
    }

    override fun getItemCount(): Int = sensors.size
}

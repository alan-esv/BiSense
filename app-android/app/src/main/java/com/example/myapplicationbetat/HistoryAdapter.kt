package com.example.myapplicationbetat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HistoryAdapter(private var items: List<HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPeriodLabel: TextView = view.findViewById(R.id.tvPeriodLabel)
        val tvTotalConsumption: TextView = view.findViewById(R.id.tvTotalConsumption)
        val tvTotalInjection: TextView = view.findViewById(R.id.tvTotalInjection)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_row, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]

        holder.tvPeriodLabel.text = item.periodLabel

        // Formatear los totales a 2 decimales para la UI
        val consumptionText = String.format(Locale.getDefault(), "C: %.2f kWh", item.totalConsumption)
        val injectionText = String.format(Locale.getDefault(), "I: %.2f kWh", item.totalInjection)

        holder.tvTotalConsumption.text = consumptionText
        holder.tvTotalInjection.text = injectionText
    }

    override fun getItemCount() = items.size

    // Función para actualizar la lista de datos
    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
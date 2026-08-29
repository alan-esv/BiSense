package com.example.myapplicationbetat

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Locale
import com.github.mikephil.charting.components.LegendEntry
import kotlin.text.get


class GraphsFragment : Fragment() {

    private lateinit var lineChart: LineChart
    private lateinit var tvStatus: TextView
    private lateinit var btnCombined: Button
    private lateinit var btnConsumption: Button
    private lateinit var btnInjection: Button

    // Listas
    private var fullConsumptionEntries: List<Entry> = emptyList()
    private var fullInjectionEntries: List<Entry> = emptyList()

    private var combinedEntries: List<Entry> = emptyList()
    private var combinedColors: List<Int> = emptyList()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val zonaMexico = ZoneId.of("America/Mexico_City")

    // Colores definidos
    private val colorConsumo = Color.parseColor("#FF5722") // Rojo/Naranja
    private val colorInyeccion = Color.parseColor("#008746") // Verde

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_graphs, container, false)
        lineChart = view.findViewById(R.id.lineChart)
        tvStatus = view.findViewById(R.id.tvStatusGrafica)
        btnCombined = view.findViewById(R.id.btnCombined)
        btnConsumption = view.findViewById(R.id.btnConsumption)
        btnInjection = view.findViewById(R.id.btnInjection)

        setupChart()
        cargarDatos()

        btnCombined.setOnClickListener { displayChart("combined") }
        btnConsumption.setOnClickListener { displayChart("consumption") }
        btnInjection.setOnClickListener { displayChart("injection") }

        return view
    }

    private fun setupChart() {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val colorTexto = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.legend.textColor = colorTexto

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = colorTexto
        xAxis.valueFormatter = object : ValueFormatter() {
            private val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                val zdt = Instant.ofEpochMilli(value.toLong()).atZone(zonaMexico)
                return zdt.format(formatter)
            }
        }

        lineChart.axisLeft.textColor = colorTexto
        lineChart.axisLeft.resetAxisMinimum()
        lineChart.axisRight.isEnabled = false
    }

    private fun obtenerMediciones(medidorId: String) {
        val inicioHoyZdt = LocalDate.now(zonaMexico).atStartOfDay(zonaMexico)
        val fechaInicio = java.util.Date.from(inicioHoyZdt.toInstant())

        tvStatus.text = "Cargando gráfica..."

        db.collection("mediciones")
            .whereEqualTo("id_medidor", medidorId)
            .whereGreaterThan("timestamp", fechaInicio)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (!isAdded || view == null) return@addOnSuccessListener
                if (result.isEmpty) {
                    tvStatus.text = "No hay puntos de muestreo hoy"
                    return@addOnSuccessListener
                }

                val tempConsumo = ArrayList<Entry>()
                val tempInyeccion = ArrayList<Entry>()
                val tempCombined = ArrayList<Entry>()
                val tempColors = ArrayList<Int>()

                var ultimoConsumo = -1f
                var ultimaInyeccion = -1f

                for (doc in result) {
                    val timeMillis = doc.getTimestamp("timestamp")?.toInstant()?.toEpochMilli()?.toFloat() ?: 0f
                    val consumoAcumulado = doc.getDouble("consumo_kwh")?.toFloat() ?: 0f
                    val inyeccionAcumulada = doc.getDouble("inyeccion_kwh")?.toFloat() ?: 0f

                    val deltaConsumo = if (ultimoConsumo == -1f) 0f else consumoAcumulado - ultimoConsumo
                    val deltaInyeccion = if (ultimaInyeccion == -1f) 0f else inyeccionAcumulada - ultimaInyeccion

                    ultimoConsumo = consumoAcumulado
                    ultimaInyeccion = inyeccionAcumulada

                    // 1. Datos para gráficas individuales
                    tempConsumo.add(Entry(timeMillis, deltaConsumo))
                    tempInyeccion.add(Entry(timeMillis, deltaInyeccion))

                    // 2. Lógica Combinada (Bicolor)
                    val neto = deltaConsumo - deltaInyeccion

                    tempCombined.add(Entry(timeMillis, neto))

                    val colorPunto = if (neto >= 0f) colorConsumo else colorInyeccion
                    tempColors.add(colorPunto)
                }

                fullConsumptionEntries = tempConsumo
                fullInjectionEntries = tempInyeccion
                combinedEntries = tempCombined
                combinedColors = tempColors

                displayChart("combined")
                tvStatus.text = "Gráfica: ${result.size()} puntos"
            }
    }

    private fun displayChart(type: String) {
        lineChart.data = null

        val dataSetList = when (type) {
            "combined" -> {
                lineChart.axisLeft.resetAxisMinimum() // permite negativos automáticos
                lineChart.axisLeft.setDrawZeroLine(true)
                val set = LineDataSet(combinedEntries, "Balance (Consumo/Inyección)")
                set.colors = combinedColors
                set.lineWidth = 3f
                set.setDrawCircles(true)
                set.circleRadius = 4f
                set.setCircleColors(combinedColors)
                set.setDrawValues(false)
                set.setDrawFilled(false)
                listOf(set)
            }
            "consumption" -> {
                lineChart.axisLeft.axisMinimum = 0f
                lineChart.axisLeft.setDrawZeroLine(false)
                listOf(createDataSet(fullConsumptionEntries, "Consumo (kWh)", colorConsumo, false))
            }
            "injection" -> {
                lineChart.axisLeft.axisMinimum = 0f
                lineChart.axisLeft.setDrawZeroLine(false)
                listOf(createDataSet(fullInjectionEntries, "Inyección (kWh)", colorInyeccion, true))
            }
            else -> emptyList()
        }
        val legendEntries = arrayListOf<LegendEntry>()

        legendEntries.add(
            LegendEntry(
                "Consumo (kWh)       ",
                Legend.LegendForm.LINE,
                10f,
                2f,
                null,
                colorConsumo
            )
        )

        legendEntries.add(
            LegendEntry(
                "Inyección (kWh)       ",
                Legend.LegendForm.LINE,
                10f,
                2f,
                null,
                colorInyeccion
            )
        )

        lineChart.legend.setCustom(legendEntries)

        if (dataSetList.isNotEmpty()) {
            lineChart.data = LineData(dataSetList)
            lineChart.notifyDataSetChanged()
            lineChart.animateX(800)
            lineChart.invalidate()
        }
        highlightButton(type)
    }

    private fun cargarDatos() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                val medidorId = document.getString("id_medidor")
                if (!medidorId.isNullOrEmpty()) {
                    obtenerMediciones(medidorId)
                } else {
                    tvStatus.text = "No hay medidor vinculado"
                }
            }
    }

    private fun createDataSet(entries: List<Entry>, label: String, color: Int, filled: Boolean): LineDataSet {
        val set = LineDataSet(entries, label)
        set.color = color
        set.setCircleColor(color)
        set.lineWidth = 2f
        set.setDrawValues(false)

        when {
            entries.size < 40 -> {
                set.setDrawCircles(true)
                set.circleRadius = 4f
            }
            entries.size < 80 -> {
                set.setDrawCircles(true)
                set.circleRadius = 2f
            }
            else -> {
                set.setDrawCircles(false)
            }
        }

        set.setDrawFilled(filled)
        if (filled) {
            set.fillAlpha = 50
            set.fillColor = color
        }

        set.mode = LineDataSet.Mode.LINEAR

        return set
    }

    private fun highlightButton(activeType: String) {
        val buttons = mapOf("combined" to btnCombined, "consumption" to btnConsumption, "injection" to btnInjection)
        buttons.forEach { (type, button) ->
            if (type == activeType) {
                button.setBackgroundColor(colorInyeccion)
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.TRANSPARENT)
                button.setTextColor(Color.GRAY)
            }
        }
    }
}
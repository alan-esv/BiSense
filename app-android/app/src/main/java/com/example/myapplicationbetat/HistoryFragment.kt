package com.example.myapplicationbetat

import android.graphics.Color
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var tvStatusHistory: TextView
    private lateinit var adapter: HistoryAdapter

    private lateinit var btnGroupDay: Button
    private lateinit var btnGroupWeek: Button
    private lateinit var btnGroupMonth: Button
    private lateinit var btnGroupYear: Button

    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var layoutPaging: View

    private var currentGroupType: String = "Día"

    private var currentPageIndex = 0

    private val pageSizeDay = 7
    private val pageSizeWeek = 4
    private val pageSizeMonth = 6

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var medidorId: String? = null

    private val cacheHistorial = mutableMapOf<String, List<HistoryItem>>()

    private val hasOlderData = mutableMapOf<String, Boolean>()

    private val zonaMexico = ZoneId.of("America/Mexico_City")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory)
        tvStatusHistory = view.findViewById(R.id.tvStatusHistory)
        btnGroupDay = view.findViewById(R.id.btnGroupDay)
        btnGroupWeek = view.findViewById(R.id.btnGroupWeek)
        btnGroupMonth = view.findViewById(R.id.btnGroupMonth)
        btnGroupYear = view.findViewById(R.id.btnGroupYear)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        layoutPaging = view.findViewById(R.id.layoutPaging)

        setupRecyclerView()
        setupListeners()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadMedidorIdAndFetchAllData()
    }

    override fun onPause() {
        super.onPause()
        // Al salir del fragmento, limpiar cache de TODOS los filtros excepto Día
        limpiarCacheExceptoDias()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cacheHistorial.clear()
        hasOlderData.clear()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(emptyList())
        recyclerViewHistory.layoutManager = LinearLayoutManager(context)
        recyclerViewHistory.adapter = adapter
    }

    private fun setupListeners() {
        btnGroupDay.setOnClickListener { cambiarFiltro("Día") }
        btnGroupWeek.setOnClickListener { cambiarFiltro("Semana") }
        btnGroupMonth.setOnClickListener { cambiarFiltro("Mes") }
        btnGroupYear.setOnClickListener { cambiarFiltro("Año") }

        // PREV = ir a datos MÁS ANTIGUOS (página anterior en el tiempo)
        btnPrev.setOnClickListener { irAPaginaAntigua() }

        // NEXT = ir a datos MÁS RECIENTES (página siguiente hacia el presente)
        btnNext.setOnClickListener { irAPaginaReciente() }

        highlightButton("Día")
    }

    private fun loadMedidorIdAndFetchAllData() {
        val userId = auth.currentUser?.uid ?: return
        tvStatusHistory.text = "Buscando ID de medidor..."

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                medidorId = document.getString("id_medidor")

                if (medidorId.isNullOrEmpty()) {
                    tvStatusHistory.text = "Error: No hay medidor vinculado."
                } else {
                    cambiarFiltro("Día")
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                tvStatusHistory.text = "Error: ${e.message}"
            }
    }

    private fun cambiarFiltro(tipo: String) {
        if (currentGroupType != tipo) {
            limpiarCacheFiltrosAnteriores(tipo)
        }

        currentGroupType = tipo
        currentPageIndex = 0 // Siempre empezar en la página más reciente
        highlightButton(tipo)

        // Ocultar paginación para Año
        if (tipo == "Año") {
            layoutPaging.visibility = View.GONE
        }

        cargarPagina(tipo, currentPageIndex)
    }

    private fun limpiarCacheFiltrosAnteriores(nuevoFiltro: String) {
        val keysAEliminar = cacheHistorial.keys.filter { key ->
            !key.startsWith("Día_") && !key.startsWith("${nuevoFiltro}_")
        }

        keysAEliminar.forEach { key ->
            cacheHistorial.remove(key)
            Log.d("Historial", "Cache eliminado: $key")
        }

        val tiposAEliminar = hasOlderData.keys.filter { it != "Día" && it != nuevoFiltro }
        tiposAEliminar.forEach { hasOlderData.remove(it) }
    }

    private fun limpiarCacheExceptoDias() {
        // Al cambiar de actividad/fragmento, mantener solo cache de Día
        val keysAEliminar = cacheHistorial.keys.filter { !it.startsWith("Día_") }
        keysAEliminar.forEach { key ->
            cacheHistorial.remove(key)
            Log.d("Historial", "Cache eliminado (onPause): $key")
        }

        val tiposAEliminar = hasOlderData.keys.filter { it != "Día" }
        tiposAEliminar.forEach { hasOlderData.remove(it) }
    }

    private fun cargarPagina(tipo: String, pageIndex: Int) {
        val cacheKey = "${tipo}_${pageIndex}"

        if (cacheHistorial.containsKey(cacheKey)) {
            Log.d("Historial", "Usando caché para $cacheKey")
            mostrarDatosEnPantalla(cacheHistorial[cacheKey] ?: emptyList())
            actualizarBotonesPaginacion()
        } else {
            Log.d("Historial", "Cargando datos para $cacheKey desde Firebase")
            fetchPaginaOptimizada(tipo, pageIndex)
        }
    }

    private fun fetchPaginaOptimizada(tipo: String, pageIndex: Int) {
        val id = medidorId ?: return
        tvStatusHistory.text = "Cargando historial..."

        val rangos = generarRangosDePagina(tipo, pageIndex)
        val resultados = mutableListOf<HistoryItem>()
        var procesosCompletados = 0
        val totalProcesos = rangos.size

        for (rango in rangos) {
            val baseQuery = db.collection("mediciones")
                .whereEqualTo("id_medidor", id)
                .whereGreaterThanOrEqualTo("timestamp", rango.inicio)
                .whereLessThanOrEqualTo("timestamp", rango.fin)

            // Primera medición del período (la más antigua)
            val tareaInicio = baseQuery
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(1)
                .get()

            // Última medición del período (la más reciente)
            val tareaFin = baseQuery
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()

            com.google.android.gms.tasks.Tasks.whenAllComplete(tareaInicio, tareaFin)
                .addOnCompleteListener {
                    procesosCompletados++

                    val docI = tareaInicio.result?.documents?.firstOrNull()
                    val docF = tareaFin.result?.documents?.firstOrNull()

                    if (docI != null && docF != null) {
                        // Valores iniciales (primera medición del período)
                        val vIC = docI.getDouble("consumo_kwh") ?: 0.0
                        val vII = docI.getDouble("inyeccion_kwh") ?: 0.0

                        // Valores finales (última medición del período)
                        val vFC = docF.getDouble("consumo_kwh") ?: 0.0
                        val vFI = docF.getDouble("inyeccion_kwh") ?: 0.0

                        // Valores finales SIEMPRE serán >= valores iniciales
                        val cTotal = vFC - vIC
                        val iTotal = vFI - vII

                        Log.d("Historial", "${rango.label}: Consumo=$cTotal kWh (${vFC}-${vIC}), Inyección=$iTotal kWh (${vFI}-${vII})")

                        resultados.add(HistoryItem(
                            periodLabel = rango.label,
                            totalConsumption = cTotal,
                            totalInjection = iTotal,
                            periodStartDate = rango.inicio
                        ))
                    } else {
                        // Si no hay datos para este período, agregar registro vacío
                        Log.d("Historial", "${rango.label}: Sin datos")
                        resultados.add(HistoryItem(
                            periodLabel = rango.label,
                            totalConsumption = 0.0,
                            totalInjection = 0.0,
                            periodStartDate = rango.inicio
                        ))
                    }

                    if (procesosCompletados == totalProcesos) {
                        if (!isAdded) return@addOnCompleteListener

                        // Ordenar por fecha descendente (más reciente primero)
                        val listaFinal = resultados.sortedByDescending { it.periodStartDate }

                        // Guardar en caché
                        val cacheKey = "${tipo}_${pageIndex}"
                        cacheHistorial[cacheKey] = listaFinal

                        mostrarDatosEnPantalla(listaFinal)

                        if (!hasOlderData.containsKey(tipo)) {
                            verificarDatosAntiguos(tipo, pageIndex, rangos.lastOrNull()?.inicio)
                        } else {
                            actualizarBotonesPaginacion()
                        }
                    }
                }
        }
    }

    private fun verificarDatosAntiguos(tipo: String, pageIndex: Int, fechaInicioUltimoPeriodo: Date?) {
        val id = medidorId ?: return
        if (fechaInicioUltimoPeriodo == null) {
            hasOlderData[tipo] = false
            actualizarBotonesPaginacion()
            return
        }

        // Verificar si existe al menos 1 medición ANTES del primer día del último periodo mostrado
        db.collection("mediciones")
            .whereEqualTo("id_medidor", id)
            .whereLessThan("timestamp", fechaInicioUltimoPeriodo)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded) return@addOnSuccessListener
                hasOlderData[tipo] = !querySnapshot.isEmpty
                Log.d("Historial", "Hay datos más antiguos en $tipo: ${hasOlderData[tipo]}")
                actualizarBotonesPaginacion()
            }
            .addOnFailureListener { e ->
                Log.e("Historial", "Error verificando datos antiguos: ${e.message}")
                hasOlderData[tipo] = false
                actualizarBotonesPaginacion()
            }
    }

    private fun generarRangosDePagina(tipo: String, pageIndex: Int): List<RangoTiempo> {
        val lista = mutableListOf<RangoTiempo>()
        val ahora = ZonedDateTime.now(zonaMexico)

        when (tipo) {
            "Día" -> {
                val offsetDias = pageIndex * pageSizeDay
                for (i in 0 until pageSizeDay) {
                    val diasAtras = offsetDias + i
                    val dia = ahora.minusDays(diasAtras.toLong())

                    // Inicio del día: 00:00:00
                    val inicio = Date.from(dia.toLocalDate().atStartOfDay(zonaMexico).toInstant())

                    // Fin del día: 23:59:59.999
                    val fin = Date.from(dia.toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zonaMexico).toInstant())

                    val label = dia.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
                    lista.add(RangoTiempo(inicio, fin, label))
                }
            }
            "Semana" -> {
                val offsetSemanas = pageIndex * pageSizeWeek
                val fmtDiaMes = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

                for (i in 0 until pageSizeWeek) {
                    val semanasAtras = offsetSemanas + i
                    val lunes = ahora.minusWeeks(semanasAtras.toLong())
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    // Inicio: Lunes 00:00:00
                    val inicio = Date.from(lunes.toLocalDate().atStartOfDay(zonaMexico).toInstant())

                    val domingo = lunes.plusDays(6)
                    // Fin: Domingo 23:59:59
                    val fin = Date.from(domingo.toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zonaMexico).toInstant())

                    val label = "${lunes.format(fmtDiaMes)} - ${domingo.format(fmtDiaMes)}"

                    lista.add(RangoTiempo(inicio, fin, label))
                }
            }
            "Mes" -> {
                val offsetMeses = pageIndex * pageSizeMonth
                for (i in 0 until pageSizeMonth) {
                    val mesesAtras = offsetMeses + i
                    val mes = ahora.minusMonths(mesesAtras.toLong())

                    // Inicio del mes: día 1 a las 00:00:00
                    val inicio = Date.from(mes.with(TemporalAdjusters.firstDayOfMonth())
                        .toLocalDate().atStartOfDay(zonaMexico).toInstant())

                    // Fin del mes: último día a las 23:59:59.999
                    val fin = Date.from(mes.with(TemporalAdjusters.lastDayOfMonth())
                        .toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zonaMexico).toInstant())

                    val label = mes.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    lista.add(RangoTiempo(inicio, fin, label))
                }
            }
            "Año" -> {
                // Para año, mostrar solo el año actual (sin paginación)
                val inicio = Date.from(ahora.with(TemporalAdjusters.firstDayOfYear())
                    .toLocalDate().atStartOfDay(zonaMexico).toInstant())
                val fin = Date.from(ahora.with(TemporalAdjusters.lastDayOfYear())
                    .toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zonaMexico).toInstant())
                val label = ahora.format(DateTimeFormatter.ofPattern("yyyy", Locale.getDefault()))
                lista.add(RangoTiempo(inicio, fin, label))
            }
        }
        return lista
    }

    private fun mostrarDatosEnPantalla(items: List<HistoryItem>) {
        if (!isAdded) return
        adapter.updateData(items)

        val tipoLabel = when(currentGroupType) {
            "Día" -> "días"
            "Semana" -> "semanas"
            "Mes" -> "meses"
            "Año" -> "año"
            else -> "registros"
        }

        tvStatusHistory.text = "Mostrando ${items.size} $tipoLabel (página ${currentPageIndex + 1})"
    }

    private fun irAPaginaAntigua() {
        // Ir a datos más antiguos = incrementar índice de página
        currentPageIndex++
        cargarPagina(currentGroupType, currentPageIndex)
    }

    private fun irAPaginaReciente() {
        // Ir a datos más recientes = decrementar índice de página
        if (currentPageIndex > 0) {
            currentPageIndex--
            cargarPagina(currentGroupType, currentPageIndex)
        }
    }

    private fun actualizarBotonesPaginacion() {
        if (!isAdded) return

        // Mostrar/ocultar layout de paginación
        when (currentGroupType) {
            "Año" -> {
                layoutPaging.visibility = View.GONE
                return
            }
            "Día", "Semana", "Mes" -> {
                layoutPaging.visibility = View.VISIBLE
            }
        }

        val puedeIrAReciente = currentPageIndex > 0
        btnNext.isEnabled = puedeIrAReciente
        btnNext.alpha = if (puedeIrAReciente) 1.0f else 0.3f

        val hayDatosAntiguos = hasOlderData[currentGroupType] ?: false
        val cacheKeyActual = "${currentGroupType}_${currentPageIndex}"
        val datosActuales = cacheHistorial[cacheKeyActual] ?: emptyList()

        // Solo habilitar si tenemos datos en la página actual Y hay más datos antiguos
        val puedeIrAntiguo = datosActuales.isNotEmpty() && hayDatosAntiguos
        btnPrev.isEnabled = puedeIrAntiguo
        btnPrev.alpha = if (puedeIrAntiguo) 1.0f else 0.3f

        Log.d("Historial", "Botones: Página=$currentPageIndex, Next=$puedeIrAReciente, Prev=$puedeIrAntiguo")
    }

    private fun highlightButton(activeType: String) {
        val buttons = mapOf(
            "Día" to btnGroupDay,
            "Semana" to btnGroupWeek,
            "Mes" to btnGroupMonth,
            "Año" to btnGroupYear
        )
        val activeColor = requireContext().getColor(R.color.verdePrincipal)
        val inactiveColor = requireContext().getColor(R.color.verdeIntermedio)

        buttons.forEach { (type, button) ->
            if (type == activeType) {
                button.setBackgroundColor(activeColor)
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.TRANSPARENT)
                button.setTextColor(inactiveColor)
            }
        }
    }

    private data class RangoTiempo(val inicio: Date, val fin: Date, val label: String)
}
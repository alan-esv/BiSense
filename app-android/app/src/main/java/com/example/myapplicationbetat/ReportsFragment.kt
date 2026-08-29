package com.example.myapplicationbetat

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.abs
import androidx.activity.result.ActivityResultLauncher

class ReportsFragment : Fragment() {
    // Views
    private lateinit var tvDateStart: TextView
    private lateinit var tvDateEnd: TextView
    private lateinit var btnGenerateReport: Button
    private lateinit var btnQuickToday: Button
    private lateinit var btnQuickWeek: Button
    private lateinit var btnQuickMonth: Button
    private lateinit var btnQuickYear: Button
    private lateinit var cbIncludeChart: CheckBox
    private lateinit var cbIncludeTable: CheckBox
    private lateinit var cbIncludeSummary: CheckBox

    // Fechas
    private var dateStart: ZonedDateTime? = null
    private var dateEnd: ZonedDateTime? = null
    private val zonaMexico = ZoneId.of("America/Mexico_City")

    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    fetchDataAndGeneratePdf(it)
                } ?: run {
                    Toast.makeText(context, "Operación cancelada.", Toast.LENGTH_SHORT).show()
                    restoreGenerateButton() // Rehabilitar si hay error con el archivo
                }
            } else {
                restoreGenerateButton()
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
    private val fileDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.getDefault())
    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.getDefault())

    data class MedicionEnergia(
        val timestamp: ZonedDateTime,
        val consumo_kwh: Double,
        val inyeccion_kwh: Double,
        val importe: Double,
        val esGanancia: Boolean,
        val esResumenDiario: Boolean = false
    )
    private val medicionesParaReporte = mutableListOf<MedicionEnergia>()

    private val mediciones = mutableListOf<MedicionEnergia>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        tvDateStart = view.findViewById(R.id.tvDateStart)
        tvDateEnd = view.findViewById(R.id.tvDateEnd)
        btnGenerateReport = view.findViewById(R.id.btnGenerateReport)
        btnQuickToday = view.findViewById(R.id.btnQuickToday)
        btnQuickWeek = view.findViewById(R.id.btnQuickWeek)
        btnQuickMonth = view.findViewById(R.id.btnQuickMonth)
        btnQuickYear = view.findViewById(R.id.btnQuickYear)
        cbIncludeChart = view.findViewById(R.id.cbIncludeChart)
        cbIncludeTable = view.findViewById(R.id.cbIncludeTable)
        cbIncludeSummary = view.findViewById(R.id.cbIncludeSummary)

        tvDateStart.setOnClickListener { showDatePicker(true) }
        tvDateEnd.setOnClickListener { showDatePicker(false) }
        btnQuickToday.setOnClickListener { setQuickRangeToday() }
        btnQuickWeek.setOnClickListener { setQuickRangeLast7Days() }
        btnQuickMonth.setOnClickListener { setQuickRangeThisMonth() }
        btnQuickYear.setOnClickListener { setQuickRangeThisYear() }
        btnGenerateReport.setOnClickListener { generatePdfReport() }

        return view
    }

    private fun showDatePicker(isStart: Boolean) {
        val ahora = ZonedDateTime.now(zonaMexico)
        val initial = if (isStart) dateStart ?: ahora else dateEnd ?: ahora

        DatePickerDialog(requireContext(), { _, year, month, day ->
            val selectedDate = LocalDate.of(year, month + 1, day)
            if (isStart) {
                dateStart = selectedDate.atStartOfDay(zonaMexico)
                tvDateStart.text = dateStart?.format(dateFormat)
            } else {
                dateEnd = selectedDate.atTime(LocalTime.MAX).atZone(zonaMexico)
                tvDateEnd.text = dateEnd?.format(dateFormat)
            }
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun generatePdfReport() {
        val start = dateStart
        val end = dateEnd
        if (start == null || end == null || start.isAfter(end)) {
            Toast.makeText(context, "Selecciona un rango válido.", Toast.LENGTH_SHORT).show()
            return
        }

        btnGenerateReport.isEnabled = false
        btnGenerateReport.text = "Preparando..."

        val fileName = "Reporte_${start.format(fileDateFormat)}_${end.format(fileDateFormat)}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        createDocumentLauncher.launch(intent)
    }

    // --- CÁLCULO DE TARIFA CFE TIPO 1 ---
    private fun calcularCostoCFE(kwhTotales: Double): Double {
        var costo = 0.0
        var restante = kwhTotales

        if (restante > 0) {
            val basico = minOf(restante, 75.0)
            costo += basico * 1.119
            restante -= basico
        }
        if (restante > 0) {
            val intermedio = minOf(restante, 65.0)
            costo += intermedio * 1.361
            restante -= intermedio
        }
        if (restante > 0) {
            costo += restante * 3.98
        }
        return costo
    }

    private fun fetchDataAndGeneratePdf(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val start = dateStart ?: return
        val end = dateEnd ?: return

        btnGenerateReport.isEnabled = false
        btnGenerateReport.text = "Procesando..."

        db.collection("usuarios").document(userId).get().addOnSuccessListener { userDoc ->
            if (!isAdded || view == null) return@addOnSuccessListener

            val medidorId = userDoc.getString("id_medidor") ?: ""
            val nombre = userDoc.getString("nombre") ?: "Usuario"
            val email = userDoc.getString("email") ?: ""

            val diasDiferencia = java.time.temporal.ChronoUnit.DAYS.between(start, end)

            if (diasDiferencia >7) {
                // RANGOS LARGOS (2 Lecturas por día)
                val documentosTotales = Collections.synchronizedList(mutableListOf<com.google.firebase.firestore.DocumentSnapshot>())
                var diasProcesados = 0
                val totalDias = (diasDiferencia + 1).toInt()

                var fechaActual = start.toLocalDate()
                val fechaFin = end.toLocalDate()

                while (!fechaActual.isAfter(fechaFin)) {
                    val inicioDia = fechaActual.atStartOfDay(zonaMexico)
                    val finDia = fechaActual.atTime(LocalTime.MAX).atZone(zonaMexico)

                    val baseQuery = db.collection("mediciones")
                        .whereEqualTo("id_medidor", medidorId)
                        .whereGreaterThanOrEqualTo("timestamp", Date.from(inicioDia.toInstant()))
                        .whereLessThanOrEqualTo("timestamp", Date.from(finDia.toInstant()))

                    val tareaInicio = baseQuery.orderBy("timestamp", Query.Direction.ASCENDING).limit(1).get()
                    val tareaFin = baseQuery.orderBy("timestamp", Query.Direction.DESCENDING).limit(1).get()

                    com.google.android.gms.tasks.Tasks.whenAllComplete(tareaInicio, tareaFin)
                        .addOnCompleteListener {
                            diasProcesados++

                            val docI = tareaInicio.result?.documents?.firstOrNull()
                            val docF = tareaFin.result?.documents?.firstOrNull()

                            if (docI != null) documentosTotales.add(docI)
                            if (docF != null && docF.id != docI?.id) documentosTotales.add(docF)

                            if (diasProcesados == totalDias) {
                                if (!isAdded) return@addOnCompleteListener

                                val docsOrdenados = documentosTotales.sortedBy {
                                    it.getTimestamp("timestamp")?.toDate()
                                }

                                if (docsOrdenados.isEmpty()) {
                                    Toast.makeText(context, "No hay registros en este periodo.", Toast.LENGTH_SHORT).show()
                                    restoreGenerateButton()
                                    return@addOnCompleteListener
                                }

                                procesarDatos(docsOrdenados, true)
                                generarYGuardarPdf(uri, nombre, email, medidorId)
                            }
                        }
                    fechaActual = fechaActual.plusDays(1)
                }

            } else {

                var medicionesRef = db.collection("mediciones")
                    .whereEqualTo("id_medidor", medidorId)
                    .whereGreaterThanOrEqualTo("timestamp", Date.from(start.toInstant()))
                    .whereLessThanOrEqualTo("timestamp", Date.from(end.toInstant()))

                medicionesRef = medicionesRef.orderBy("timestamp", Query.Direction.ASCENDING)

                medicionesRef.get().addOnSuccessListener { snapshot ->
                    if (!isAdded) return@addOnSuccessListener

                    if (snapshot == null || snapshot.isEmpty) {
                        Toast.makeText(context, "No hay registros para este periodo.", Toast.LENGTH_SHORT).show()
                        restoreGenerateButton()
                        return@addOnSuccessListener
                    }

                    procesarDatos(snapshot.documents, false)
                    generarYGuardarPdf(uri, nombre, email, medidorId)
                }.addOnFailureListener {
                    restoreGenerateButton()
                }
            }
        }
    }

    // Función auxiliar para calcular totales y lanzar la escritura del PDF
    private fun generarYGuardarPdf(uri: Uri, nombre: String, email: String, medidorId: String) {
        var consumoTotalReal = 0.0
        var inyeccionTotalReal = 0.0

        medicionesParaReporte.forEach {
            consumoTotalReal += it.consumo_kwh
            inyeccionTotalReal += it.inyeccion_kwh
        }

        writePdfToUri(uri, nombre, email, medidorId, consumoTotalReal, inyeccionTotalReal)
    }

    private fun procesarDatos(documentos: List<com.google.firebase.firestore.DocumentSnapshot>, resumenDiario: Boolean) {
        medicionesParaReporte.clear()

        if (resumenDiario) {
            val agrupadosPorDia = documentos.groupBy {
                it.getTimestamp("timestamp")?.toDate()?.toInstant()?.atZone(zonaMexico)?.toLocalDate()
            }

            // Variable unificada para el saldo neto acumulado (puede ser negativo si hay crédito solar)
            var acumuladoNetoGlobal = 0.0

            agrupadosPorDia.forEach { (fecha, docs) ->
                if (fecha != null && docs.size >= 2) {
                    val primero = docs.first()
                    val ultimo = docs.last()

                    val deltaC = maxOf(0.0, (ultimo.getDouble("consumo_kwh") ?: 0.0) - (primero.getDouble("consumo_kwh") ?: 0.0))
                    val deltaI = maxOf(0.0, (ultimo.getDouble("inyeccion_kwh") ?: 0.0) - (primero.getDouble("inyeccion_kwh") ?: 0.0))

                    // Calcular el impacto neto de este día
                    val deltaNetoHoy = deltaC - deltaI

                    // Evaluar el costo antes de modificar el balance (asegurando no evaluar negativos en CFE)
                    val costoAntes = calcularCostoCFE(maxOf(0.0, acumuladoNetoGlobal))

                    // Actualizar el saldo neto global
                    acumuladoNetoGlobal += deltaNetoHoy

                    // Calcular el costo después del cambio
                    val costoDespues = calcularCostoCFE(maxOf(0.0, acumuladoNetoGlobal))

                    // La diferencia es el importe real asignado a este día
                    val costoHoy = costoDespues - costoAntes

                    val esGanancia = deltaI > deltaC

                    medicionesParaReporte.add(MedicionEnergia(
                        timestamp = fecha.atStartOfDay(zonaMexico),
                        consumo_kwh = deltaC,
                        inyeccion_kwh = deltaI,
                        importe = if (esGanancia) 0.0 else costoHoy, // Si es ganancia neta, el costo de la fila es 0
                        esGanancia = esGanancia,
                        esResumenDiario = true
                    ))
                }
            }
        } else {
            var previoC = documentos.first().getDouble("consumo_kwh") ?: 0.0
            var previoI = documentos.first().getDouble("inyeccion_kwh") ?: 0.0

            // Variable unificada para rangos cortos
            var acumuladoNetoGlobal = 0.0

            documentos.forEachIndexed { index, doc ->
                if (index > 0) {
                    val rawC = doc.getDouble("consumo_kwh") ?: 0.0
                    val rawI = doc.getDouble("inyeccion_kwh") ?: 0.0

                    val deltaC = maxOf(0.0, rawC - previoC)
                    val deltaI = maxOf(0.0, rawI - previoI)

                    val deltaNetoIntervalo = deltaC - deltaI

                    val costoAntes = calcularCostoCFE(maxOf(0.0, acumuladoNetoGlobal))
                    acumuladoNetoGlobal += deltaNetoIntervalo
                    val costoDespues = calcularCostoCFE(maxOf(0.0, acumuladoNetoGlobal))

                    val costoIntervalo = costoDespues - costoAntes

                    val esGanancia = deltaI > deltaC

                    medicionesParaReporte.add(MedicionEnergia(
                        timestamp = doc.getTimestamp("timestamp")!!.toDate().toInstant().atZone(zonaMexico),
                        consumo_kwh = deltaC,
                        inyeccion_kwh = deltaI,
                        importe = if (esGanancia) 0.0 else costoIntervalo,
                        esGanancia = esGanancia,
                        esResumenDiario = false
                    ))
                    previoC = rawC
                    previoI = rawI
                }
            }
        }
    }

    private fun drawSummarySection(canvas: Canvas, startY: Float, pageWidth: Int, totalConsumo: Double, totalInyeccion: Double, balance: Double): Float {
        var y = startY
        val paint = Paint()
        val marginX = 40f

        paint.color = Color.BLACK
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Resumen de Facturación Estimada", marginX, y, paint)
        y += 30f

        paint.color = Color.parseColor("#F1F8E9")
        paint.style = Paint.Style.FILL
        canvas.drawRect(marginX, y - 5, pageWidth - 40f, y + 115, paint)

        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        y += 15f

        val costoBruto = calcularCostoCFE(totalConsumo)
        val valorInyeccion = calcularCostoCFE(totalInyeccion)
        val netoKwh = totalConsumo - totalInyeccion

        val pagoNetoMxn = if (netoKwh > 0) calcularCostoCFE(netoKwh) else 0.0

        canvas.drawText("Consumo Total: ${"%.3f".format(totalConsumo)} kWh  ->  Costo Bruto: $${"%.2f".format(costoBruto)} MXN", marginX + 10, y, paint)
        y += 20f

        paint.color = Color.parseColor("#2E7D32")
        canvas.drawText("Inyección Total: ${"%.3f".format(totalInyeccion)} kWh  ->  Valor Generado: $${"%.2f".format(valorInyeccion)} MXN", marginX + 10, y, paint)
        y += 20f

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f

        val textoFinal = if (totalInyeccion > totalConsumo) {
            "Balance: A FAVOR (${"%.3f".format(abs(netoKwh))} kWh)"
        } else {
            "Total a Pagar Estimado: $${"%.2f".format(pagoNetoMxn)} MXN"
        }

        canvas.drawText(textoFinal, marginX + 10, y + 5, paint)

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.color = Color.DKGRAY
        y += 25f
        canvas.drawText("* Basado en tarifa doméstica Tipo 1 de CDMX (75kWh básico, 65kWh intermedio).", marginX + 10, y, paint)

        return y + 40f
    }

    private fun writePdfToUri(uri: Uri, nombre: String, email: String, medidorId: String, totalC: Double, totalI: Double) {
        try {
            val currentContext = if (isAdded) requireContext() else return
            val outputStream = currentContext.contentResolver.openOutputStream(uri) ?: return
            val balance = totalC - totalI

            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = 50f

            yPos = drawProfessionalHeader(canvas, yPos, pageWidth, nombre, email, medidorId)
            if (cbIncludeSummary.isChecked) {
                yPos = drawSummarySection(canvas, yPos, pageWidth, totalC, totalI, balance)
            }
            if (cbIncludeChart.isChecked) {
                if (yPos > pageHeight - 350) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    yPos = 50f
                }
                yPos = drawEnergyChart(canvas, yPos, pageWidth)
            }
            document.finishPage(page)

            if (cbIncludeTable.isChecked) {
                drawDataTable(document, pageNumber, pageWidth, pageHeight)
            }

            document.writeTo(outputStream)
            document.close()
            outputStream.close()
            Toast.makeText(currentContext, "PDF generado con éxito", Toast.LENGTH_SHORT).show()
            restoreGenerateButton()
        } catch (e: Exception) {
            restoreGenerateButton()
        }
    }

    private fun drawProfessionalHeader(canvas: Canvas, startY: Float, pageWidth: Int, nombre: String, email: String, medidorId: String): Float {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val logo = BitmapFactory.decodeResource(resources, R.drawable.img1776803538603, options)
        var y = startY
        val paint = Paint()
        val marginX = 40f
        paint.color = Color.parseColor("#008746")
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val logoWidth = 80f
        val logoHeight = 80f

        val left = pageWidth - logoWidth - 40f
        val top = startY - 20f

        val destRect = RectF(left, top, left + logoWidth, top + logoHeight)
        canvas.drawBitmap(logo, null, destRect, null)
        canvas.drawText("REPORTE DE BALANCE ENERGÉTICO", marginX, y, paint)
        logo.recycle()
        y += 35f

        paint.textSize = 12f
        paint.color = Color.GRAY
        paint.typeface = Typeface.DEFAULT
        val periodo = "${dateStart?.format(dateFormat)} - ${dateEnd?.format(dateFormat)}"
        canvas.drawText("Periodo: $periodo", marginX, y, paint)
        y += 20f

        paint.textSize = 11f
        canvas.drawText("Cliente: $nombre", marginX, y, paint)
        y += 16f
        canvas.drawText("Medidor: $medidorId", marginX, y, paint)
        y += 20f

        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawLine(marginX, y, pageWidth - 40f, y, paint)
        return y + 25f
    }


    private fun drawEnergyChart(canvas: Canvas, startY: Float, pageWidth: Int): Float {
        val paint = Paint()
        val marginX = 40f
        val chartHeight = 180f
        val chartWidth = pageWidth - 2 * marginX
        var y = startY + 10f

        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Tendencias de Consumo e Inyección", marginX, y, paint)
        y += 18f

        if (medicionesParaReporte.isEmpty()) {
            paint.textSize = 10f
            paint.color = Color.RED
            canvas.drawText("Sin datos para graficar", marginX, y + 20, paint)
            return y + 40f
        }

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.color = Color.DKGRAY
        canvas.drawText("Eje Y: Energía en kWh | Eje X: Tiempo (Fecha y Hora local)", marginX, y, paint)
        y += 30f

        val chartBottom = y + chartHeight
        val maxValue = (medicionesParaReporte.maxOfOrNull { maxOf(it.consumo_kwh, it.inyeccion_kwh) } ?: 1.0).toFloat()
        val finalMax = if (maxValue <= 0) 1.0f else maxValue

        paint.textSize = 10f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("kWh", marginX - 30f, y - 10f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.strokeWidth = 1f
        paint.style = Paint.Style.FILL

        for (i in 0..4) {
            val gridY = chartBottom - (chartHeight / 4 * i)
            paint.color = Color.parseColor("#EEEEEE")
            canvas.drawLine(marginX, gridY, marginX + chartWidth, gridY, paint)
            paint.color = Color.GRAY
            canvas.drawText("${"%.3f".format(finalMax / 4 * i)}", marginX - 35f, gridY + 4f, paint)
        }

        if (medicionesParaReporte.size > 1) {
            val pointSpacing = chartWidth / (medicionesParaReporte.size - 1)

            val totalPoints = medicionesParaReporte.size
            val maxLabels = 6
            val indicesToDraw = mutableSetOf<Int>()

            if (totalPoints > 0) {
                indicesToDraw.add(0)
                indicesToDraw.add(totalPoints - 1)
                if (totalPoints > 2) {
                    val step = (totalPoints - 1) / (maxLabels - 1).toFloat()
                    for (k in 1 until maxLabels - 1) {
                        indicesToDraw.add((k * step).toInt())
                    }
                }
            }

            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE

            medicionesParaReporte.forEachIndexed { i, _ ->
                if (indicesToDraw.contains(i)) {
                    val px = marginX + (i * pointSpacing)
                    canvas.drawLine(px, y, px, chartBottom, paint)
                }
            }
        }

        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        canvas.drawLine(marginX, chartBottom, marginX + chartWidth, chartBottom, paint)
        canvas.drawLine(marginX, y, marginX, chartBottom, paint)

        if (medicionesParaReporte.size > 1) {
            val scaleY = chartHeight / finalMax
            val pointSpacing = chartWidth / (medicionesParaReporte.size - 1)

            dibujarLineasGrafica(canvas, marginX, chartBottom, scaleY, pointSpacing, paint)

            paint.color = Color.DKGRAY
            paint.textSize = 8f
            paint.style = Paint.Style.FILL

            val esResumen = medicionesParaReporte.first().esResumenDiario
            val axisFormatter = if (esResumen) DateTimeFormatter.ofPattern("dd/MM") else DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault())

            val margenVerticalEtiquetas = 35f

            val totalPoints = medicionesParaReporte.size
            val maxLabels = 6
            val indicesToDraw = mutableSetOf<Int>()

            if (totalPoints > 0) {
                indicesToDraw.add(0)
                indicesToDraw.add(totalPoints - 1)
                if (totalPoints > 2) {
                    val step = (totalPoints - 1) / (maxLabels - 1).toFloat()
                    for (k in 1 until maxLabels - 1) {
                        indicesToDraw.add((k * step).toInt())
                    }
                }
            }

            medicionesParaReporte.forEachIndexed { i, m ->
                if (indicesToDraw.contains(i)) {
                    val px = marginX + (i * pointSpacing)
                    val label = m.timestamp.format(axisFormatter)

                    canvas.save()
                    canvas.rotate(-45f, px, chartBottom + margenVerticalEtiquetas)
                    canvas.drawText(label, px, chartBottom + margenVerticalEtiquetas, paint)
                    canvas.restore()
                }
            }
        }

        // Leyenda
        y = chartBottom + 90f
        paint.textSize = 10f
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#F44336")
        canvas.drawCircle(marginX + 20, y, 4f, paint)
        paint.color = Color.BLACK
        canvas.drawText("Consumo", marginX + 30, y + 4, paint)

        paint.color = Color.parseColor("#4CAF50")
        canvas.drawCircle(marginX + 120, y, 4f, paint)
        paint.color = Color.BLACK
        canvas.drawText("Inyección", marginX + 130, y + 4, paint)

        return y + 30f
    }



    private fun dibujarLineasGrafica(canvas: Canvas, marginX: Float, chartBottom: Float, scaleY: Float, pointSpacing: Float, paint: Paint) {
        val esResumen = medicionesParaReporte.firstOrNull()?.esResumenDiario ?: false

        paint.color = Color.parseColor("#F44336")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f // Tu grosor original
        val pathC = Path()
        medicionesParaReporte.forEachIndexed { i, m ->
            val px = marginX + (i * pointSpacing)
            val py = chartBottom - (m.consumo_kwh.toFloat() * scaleY)
            if (i == 0) pathC.moveTo(px, py) else pathC.lineTo(px, py)
        }
        canvas.drawPath(pathC, paint)

        paint.color = Color.parseColor("#4CAF50")
        val pathI = Path()
        medicionesParaReporte.forEachIndexed { i, m ->
            val px = marginX + (i * pointSpacing)
            val py = chartBottom - (m.inyeccion_kwh.toFloat() * scaleY)
            if (i == 0) pathI.moveTo(px, py) else pathI.lineTo(px, py)
        }
        canvas.drawPath(pathI, paint)

        if (esResumen) {
            paint.style = Paint.Style.FILL
            medicionesParaReporte.forEachIndexed { i, m ->
                val px = marginX + (i * pointSpacing)
                paint.color = Color.parseColor("#F44336")
                canvas.drawCircle(px, chartBottom - (m.consumo_kwh.toFloat() * scaleY), 3f, paint)
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawCircle(px, chartBottom - (m.inyeccion_kwh.toFloat() * scaleY), 3f, paint)
            }
        }
    }

    private fun drawDataTable(document: PdfDocument, startPageNumber: Int, pageWidth: Int, pageHeight: Int): Int {
        var pageNum = startPageNumber + 1
        val marginX = 40f
        val rowHeight = 22f
        val paint = Paint()
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 50f

        // Título de la tabla adaptativo
        val esResumen = medicionesParaReporte.firstOrNull()?.esResumenDiario ?: false
        val tituloTabla = if (esResumen) "Resumen Diario de Energía" else "Desglose Detallado de Registros"

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(tituloTabla, marginX, y, paint)
        y += 25f

        // Encabezados
        paint.color = Color.parseColor("#008746")
        paint.style = Paint.Style.FILL
        canvas.drawRect(marginX, y, pageWidth - 40f, y + rowHeight, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        canvas.drawText(if (esResumen) "Fecha" else "Fecha/Hora", marginX + 5, y + 15, paint)
        canvas.drawText("Consumo", marginX + 110, y + 15, paint)
        canvas.drawText("Inyección", marginX + 180, y + 15, paint)
        canvas.drawText("Costo Est.", marginX + 260, y + 15, paint)
        canvas.drawText("Tipo", marginX + 350, y + 15, paint)
        y += rowHeight

        val dateFormatTabla = if (esResumen) DateTimeFormatter.ofPattern("dd/MM/yyyy")
        else DateTimeFormatter.ofPattern("dd/MM HH:mm")

        medicionesParaReporte.forEachIndexed { index, m ->
            if (y > pageHeight - 100) {
                document.finishPage(page)
                pageNum++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 50f
            }

            paint.color = if (index % 2 == 0) Color.parseColor("#F9F9F9") else Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(marginX, y, pageWidth - 40f, y + rowHeight, paint)

            paint.color = Color.BLACK
            canvas.drawText(m.timestamp.format(dateFormatTabla), marginX + 5, y + 15, paint)
            canvas.drawText("${"%.4f".format(m.consumo_kwh)} kWh", marginX + 110, y + 15, paint)
            canvas.drawText("${"%.4f".format(m.inyeccion_kwh)} kWh", marginX + 180, y + 15, paint)
            canvas.drawText("$${"%.4f".format(m.importe)}", marginX + 260, y + 15, paint)

            val (texto, color) = if (m.esGanancia) Pair("GANANCIA", "#2E7D32") else Pair("CONSUMO", "#C62828")
            paint.color = Color.parseColor(color)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(texto, marginX + 350, y + 15, paint)
            paint.typeface = Typeface.DEFAULT

            y += rowHeight
        }

        // Texto informativo final
        y += 30f

        val textPaint = android.text.TextPaint(paint).apply {
            textSize = 8.5f
            color = Color.GRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val infoZona = "Valores calculados para la zona CDMX (Tarifa 1 CFE). Los importes están en Pesos Mexicanos (MXN).\n\n" +
                "Nota: Los reportes de rangos cortos calculan la energía de forma aislada iniciando en el escalafón básico. " +
                "Para una simulación real alineada a su recibo de CFE, emita un reporte que abarque el mes o bimestre completo."

        val anchoDisponible = pageWidth - (marginX.toInt() * 2) // 595 - 80 = 515 píxeles

        val staticLayout = android.text.StaticLayout.Builder.obtain(
            infoZona, 0, infoZona.length, textPaint, anchoDisponible
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f) // 1.2 de espacio entre renglones para que sea legible
            .build()

        canvas.save()
        canvas.translate(marginX, y)
        staticLayout.draw(canvas)
        canvas.restore()
        y += staticLayout.height


        document.finishPage(page)
        return pageNum
    }

    private fun restoreGenerateButton() {
        btnGenerateReport.isEnabled = true
        btnGenerateReport.text = "GENERAR REPORTE PDF"
    }

    private fun setQuickRangeToday() {
        val hoy = LocalDate.now(zonaMexico)
        dateStart = hoy.atStartOfDay(zonaMexico)
        dateEnd = hoy.atTime(LocalTime.MAX).atZone(zonaMexico)
        actualizarTextos()
    }

    private fun setQuickRangeLast7Days() {
        val hoy = LocalDate.now(zonaMexico)
        dateEnd = hoy.atTime(LocalTime.MAX).atZone(zonaMexico)
        dateStart = hoy.minusDays(6).atStartOfDay(zonaMexico)
        actualizarTextos()
    }

    private fun setQuickRangeThisMonth() {
        val hoy = LocalDate.now(zonaMexico)
        dateStart = hoy.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zonaMexico)
        dateEnd = hoy.atTime(LocalTime.MAX).atZone(zonaMexico)
        actualizarTextos()
    }

    private fun setQuickRangeThisYear() {
        val hoy = LocalDate.now(zonaMexico)
        dateStart = hoy.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zonaMexico)
        dateEnd = hoy.atTime(LocalTime.MAX).atZone(zonaMexico)
        actualizarTextos()
    }

    private fun actualizarTextos() {
        tvDateStart.text = dateStart?.format(dateFormat)
        tvDateEnd.text = dateEnd?.format(dateFormat)
    }
}
package com.example.myapplicationbetat

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    // Referencias a la UI
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: ScrollView
    private lateinit var tvSaludo: TextView
    private lateinit var layoutSinMedidor: LinearLayout
    private lateinit var etIdMedidor: TextInputEditText
    private lateinit var btnConectarMedidor: Button
    private lateinit var layoutConMedidor: LinearLayout
    private lateinit var tvEstadoBeneficio: TextView
    private lateinit var btnRefresh: ImageButton

    private lateinit var tvVoltaje: TextView
    private lateinit var tvCorriente: TextView
    private lateinit var tvConsumoActual: TextView
    private lateinit var tvInyeccionActual: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvModoActual: TextView // ← 💾 Agregada variable para el modo


    // Referencias a Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // 1. Inicializar Vistas
        progressBar = view.findViewById(R.id.progressBarHome)
        layoutContent = view.findViewById(R.id.layoutContentHome)

        tvSaludo = view.findViewById(R.id.tvSaludo)
        layoutSinMedidor = view.findViewById(R.id.layoutSinMedidor)
        etIdMedidor = view.findViewById(R.id.etIdMedidor)
        btnConectarMedidor = view.findViewById(R.id.btnConectarMedidor)
        layoutConMedidor = view.findViewById(R.id.layoutConMedidor)
        tvEstadoBeneficio = view.findViewById(R.id.tvEstadoBeneficio)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        tvVoltaje = view.findViewById(R.id.tvVoltaje)
        tvCorriente = view.findViewById(R.id.tvCorriente)
        tvConsumoActual = view.findViewById(R.id.tvConsumoActual)
        tvInyeccionActual = view.findViewById(R.id.tvInyeccionActual)
        tvTimestamp = view.findViewById(R.id.tvTimestamp)
        tvModoActual = view.findViewById(R.id.tvModoActual) // ← 💾 Mapeo con el XML

        // 2. Cargar lógica
        mostrarCargando(true)
        configurarUsuario()

        // 3. Configurar botón de conectar
        btnConectarMedidor.setOnClickListener {
            vincularMedidor()
        }

        // 4. Configurar botón de refresh
        btnRefresh.setOnClickListener {
            iniciarRefresh()
        }

        return view
    }


    private fun iniciarRefresh() {
        // Evitar múltiples refresh simultáneos
        btnRefresh.isEnabled = false

        // Mostrar loading visual
        mostrarCargando(true)

        // Animación
        animarRefresh()

        // Mensaje temporal
        tvTimestamp.text = "Actualizando datos..."

        // Recargar datos
        configurarUsuario()
    }

    private fun configurarUsuario() {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            mostrarCargando(false)
            btnRefresh.isEnabled = true
            return
        }

        db.collection("usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener

                if (document != null && document.exists()) {
                    // A. Saludo
                    val nombre = document.getString("nombre") ?: "Usuario"
                    tvSaludo.text = "Hola, $nombre"

                    // B. Verificar Medidor
                    val idMedidor = document.getString("id_medidor")

                    if (idMedidor.isNullOrEmpty()) {
                        mostrarLayoutSinMedidor()
                    } else {
                        mostrarLayoutConMedidor()
                        calcularBeneficio(idMedidor)
                        cargarDatosEnTiempoReal(idMedidor)
                    }

                    mostrarCargando(false)
                    btnRefresh.isEnabled = true
                } else {
                    mostrarCargando(false)
                    btnRefresh.isEnabled = true
                    Toast.makeText(context, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                mostrarCargando(false)
                btnRefresh.isEnabled = true
                Toast.makeText(context, "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarDatosEnTiempoReal(idMedidor: String) {
        // Obtener las últimas 2 lecturas para calcular diferencias
        db.collection("mediciones")
            .whereEqualTo("id_medidor", idMedidor)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(2)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                if (snapshot != null && !snapshot.isEmpty) {
                    val documentos = snapshot.documents

                    if (documentos.size >= 2) {
                        // 2 lecturas para comparar
                        val lecturaReciente = documentos[0]
                        val lecturaAnterior = documentos[1]

                        // Obtener valores de la lectura reciente (voltaje y corriente actuales)
                        val voltaje = lecturaReciente.getDouble("voltaje_V") ?: 0.0
                        val corriente = lecturaReciente.getDouble("corriente_A") ?: 0.0

                        // Obtener kWh acumulados de ambas lecturas
                        val consumoReciente = lecturaReciente.getDouble("consumo_kwh") ?: 0.0
                        val inyeccionReciente = lecturaReciente.getDouble("inyeccion_kwh") ?: 0.0

                        val consumoAnterior = lecturaAnterior.getDouble("consumo_kwh") ?: 0.0
                        val inyeccionAnterior = lecturaAnterior.getDouble("inyeccion_kwh") ?: 0.0

                        // Calcular diferencias (consumo/inyección en el intervalo)
                        val consumoDiferencia = consumoReciente - consumoAnterior
                        val inyeccionDiferencia = inyeccionReciente - inyeccionAnterior

                        // Obtener timestamps
                        val timestampReciente = lecturaReciente.getDate("timestamp")
                        val timestampAnterior = lecturaAnterior.getDate("timestamp")

                        // Calcular intervalo de tiempo
                        var intervaloMinutos = 0L
                        if (timestampReciente != null && timestampAnterior != null) {
                            val diferenciaMs = timestampReciente.time - timestampAnterior.time
                            intervaloMinutos = TimeUnit.MILLISECONDS.toMinutes(diferenciaMs)
                        }

                        // Actualizar UI
                        tvVoltaje.text = String.format("%.1f V", voltaje)
                        tvCorriente.text = String.format("%.2f A", corriente)
                        tvConsumoActual.text = String.format("%.3f kWh", consumoDiferencia)
                        tvInyeccionActual.text = String.format("%.3f kWh", inyeccionDiferencia)

                        //Evaluando 30 seg
                        when {
                            consumoDiferencia > 0.0 && inyeccionDiferencia <= 0.0 -> {
                                tvModoActual.text = "(-CONSUMO-)"
                                tvModoActual.setTextColor(Color.parseColor("#C62828")) // Rojo CFE
                                tvModoActual.visibility = View.VISIBLE
                            }
                            inyeccionDiferencia > 0.0 && consumoDiferencia <= 0.0 -> {
                                tvModoActual.text = "(-INYECCIÓN-)"
                                tvModoActual.setTextColor(Color.parseColor("#2E7D32")) // Verde Solar
                                tvModoActual.visibility = View.VISIBLE
                            }
                            else -> {
                                // Caso intermedio: Reposo (0 y 0) o microcortes de red, se oculta el cuadro
                                tvModoActual.visibility = View.GONE
                            }
                        }
                        // ====================================================================

                        // Formatear timestamp
                        if (timestampReciente != null) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            tvTimestamp.text = "Última actualización: ${sdf.format(timestampReciente)}"
                        } else {
                            tvTimestamp.text = "Sin fecha disponible"
                        }

                        Log.d("HomeFragment", "Datos cargados - V: $voltaje, A: $corriente, " +
                                "Consumo: $consumoDiferencia kWh, Inyección: $inyeccionDiferencia kWh")

                    } else if (documentos.size == 1) {
                        // Solo hay 1 lectura, mostrar valores actuales sin diferencias
                        val lectura = documentos[0]

                        val voltaje = lectura.getDouble("voltaje_V") ?: 0.0
                        val corriente = lectura.getDouble("corriente_A") ?: 0.0
                        val timestamp = lectura.getDate("timestamp")

                        tvVoltaje.text = String.format("%.1f V", voltaje)
                        tvCorriente.text = String.format("%.2f A", corriente)
                        tvConsumoActual.text = "-- kWh"
                        tvInyeccionActual.text = "-- kWh"
                        tvModoActual.visibility = View.GONE // Ocultar por falta de historial comparativo

                        if (timestamp != null) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            tvTimestamp.text = "Última actualización: ${sdf.format(timestamp)}"
                        }

                    }
                } else {
                    // No hay datos
                    tvVoltaje.text = "-- V"
                    tvCorriente.text = "-- A"
                    tvConsumoActual.text = "-- kWh"
                    tvInyeccionActual.text = "-- kWh"
                    tvTimestamp.text = "Sin datos disponibles"
                    tvModoActual.visibility = View.GONE // Ocultar por falta de datos
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e("HomeFragment", "Error al cargar datos del Medidor", e)
                tvVoltaje.text = "Error"
                tvCorriente.text = "Error"
                tvConsumoActual.text = "Error"
                tvInyeccionActual.text = "Error"
                tvTimestamp.text = "Error al cargar datos"
                tvModoActual.visibility = View.GONE
            }
    }

    private fun calcularBeneficio(idMedidor: String) {
        tvEstadoBeneficio.text = "Calculando..."
        tvEstadoBeneficio.setTextColor(Color.GRAY)

        // 1. Definir el rango de tiempo: Hoy en CDMX (00:00:00 hasta ahora)
        val zonaMexico = java.time.ZoneId.of("America/Mexico_City")
        val hoy = java.time.LocalDate.now(zonaMexico)
        val inicioDia = java.util.Date.from(hoy.atStartOfDay(zonaMexico).toInstant())
        val finDia = java.util.Date.from(java.time.Instant.now())

        // 2. Referencia a la colección
        val medicionesRef = db.collection("mediciones")
            .whereEqualTo("id_medidor", idMedidor)
            .whereGreaterThanOrEqualTo("timestamp", inicioDia)
            .whereLessThanOrEqualTo("timestamp", finDia)

        // 3. Tarea para la PRIMERA lectura del día
        val tareaInicio = medicionesRef.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING).limit(1).get()

        // 4. Tarea para la ÚLTIMA lectura del día
        val tareaFin = medicionesRef.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1).get()

        // 5. Ejecutar ambas y comparar
        com.google.android.gms.tasks.Tasks.whenAllComplete(tareaInicio, tareaFin)
            .addOnSuccessListener {
                val snapshotInicio = tareaInicio.result
                val snapshotFin = tareaFin.result

                if (snapshotInicio != null && !snapshotInicio.isEmpty && snapshotFin != null && !snapshotFin.isEmpty) {
                    val docI = snapshotInicio.documents[0]
                    val docF = snapshotFin.documents[0]
                    val ultimoTimestamp = docF.getDate("timestamp")

                    if (ultimoTimestamp != null) {
                        val ahora = System.currentTimeMillis()
                        val diferencia = ahora - ultimoTimestamp.time

                        // 5 minutos de tolerancia
                        val limiteDesconexion = 5 * 60 * 1000L

                        if (diferencia > limiteDesconexion) {
                            val minutos = diferencia / 60000

                            tvEstadoBeneficio.text = "ERROR DE CONEXIÓN (${minutos} min)"
                            tvEstadoBeneficio.setTextColor(Color.RED)

                            Log.w(
                                "HomeFragment",
                                "Dispositivo desconectado. Última lectura hace $minutos minutos"
                            )

                            return@addOnSuccessListener
                        }
                    }
                    val consumoDia = (docF.getDouble("consumo_kwh") ?: 0.0) - (docI.getDouble("consumo_kwh") ?: 0.0)
                    val inyeccionDia = (docF.getDouble("inyeccion_kwh") ?: 0.0) - (docI.getDouble("inyeccion_kwh") ?: 0.0)

                    actualizarUIBeneficio(consumoDia, inyeccionDia)
                } else {
                    tvEstadoBeneficio.text = "SIN DATOS HOY"
                }
            }
    }

    private fun actualizarUIBeneficio(consumo: Double, inyeccion: Double) {
        when {
            // CASO 1: Hay inyección pero el consumo de la red es 0 (¡Inyección Pura / Autosuficiencia!)
            consumo <= 0 && inyeccion > 0 -> {
                tvEstadoBeneficio.text = "EFICIENCIA SOLAR: AUTOSUFICIENTE"
                tvEstadoBeneficio.setTextColor(Color.parseColor("#008746")) // Verde CFE / Ecológico
            }

            // CASO 2: No hay registros de ningún tipo todavía
            consumo <= 0 && inyeccion <= 0 -> {
                tvEstadoBeneficio.text = "SIN DATOS HOY"
                tvEstadoBeneficio.setTextColor(Color.GRAY)
            }

            // CASO 3: Consumo normal
            else -> {
                val ratio = inyeccion / consumo
                val porcentajeBeneficio = (ratio * 100).coerceAtMost(100.0) // Limita el techo al 100%

                val porcentajeEntero = Math.round(porcentajeBeneficio).toInt()

                when {
                    // NIVEL 1: ALTO (Más del 50% de la energía del día ha sido compensada)
                    ratio >= 0.50 -> {
                        tvEstadoBeneficio.text = String.format("BENEFICIO EXCELENTE (~%d%% cubierto)", porcentajeEntero)
                        tvEstadoBeneficio.setTextColor(Color.parseColor("#008746")) // Verde
                    }
                    // NIVEL 2: REGULAR (Entre 15% y 50%)
                    ratio >= 0.15 -> {
                        tvEstadoBeneficio.text = String.format("BENEFICIO BALANCEADO (~%d%% cubierto)", porcentajeEntero)
                        tvEstadoBeneficio.setTextColor(Color.parseColor("#FFA000")) // Ámbar
                    }
                    // NIVEL 3: BAJO (Menos del 15%)
                    else -> {
                        tvEstadoBeneficio.text = String.format("BENEFICIO LIMITADO (~%d%% cubierto)", porcentajeEntero)
                        tvEstadoBeneficio.setTextColor(Color.RED)
                    }
                }
            }
        }

        Log.d("HomeFragment", "Cálculo del día - Consumo: $consumo kWh, Inyección: $inyeccion kWh")
    }

    private fun vincularMedidor() {
        val nuevoId = etIdMedidor.text.toString().trim()
        val userId = auth.currentUser?.uid ?: return

        if (nuevoId.isEmpty()) return

        mostrarCargando(true)

        db.collection("usuarios").document(userId)
            .update("id_medidor", nuevoId)
            .addOnSuccessListener {
                Toast.makeText(context, "Medidor vinculado!", Toast.LENGTH_SHORT).show()
                configurarUsuario()
            }
            .addOnFailureListener {
                mostrarCargando(false)
                btnRefresh.isEnabled = true
                Toast.makeText(context, "Error al vincular", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animarRefresh() {
        btnRefresh.animate()
            .rotation(360f)
            .setDuration(500)
            .withEndAction {
                btnRefresh.rotation = 0f
            }
            .start()
    }

    private fun mostrarCargando(estaCargando: Boolean) {
        if (estaCargando) {
            progressBar.visibility = View.VISIBLE
            layoutContent.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            layoutContent.visibility = View.VISIBLE
        }
    }

    private fun mostrarLayoutSinMedidor() {
        layoutSinMedidor.visibility = View.VISIBLE
        layoutConMedidor.visibility = View.GONE
    }

    private fun mostrarLayoutConMedidor() {
        layoutSinMedidor.visibility = View.GONE
        layoutConMedidor.visibility = View.VISIBLE
    }
}
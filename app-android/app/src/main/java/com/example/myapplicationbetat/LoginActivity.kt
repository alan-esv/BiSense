package com.example.myapplicationbetat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializa Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Encuentra vistas
        val etEmailLogin = findViewById<TextInputEditText>(R.id.etEmailLogin)
        val etPasswordLogin = findViewById<TextInputEditText>(R.id.etPasswordLogin)
        // Recuperar ultimas credenciales guardadas
        val prefs = getSharedPreferences("CredencialesLogin", MODE_PRIVATE)

        val correoGuardado = prefs.getString("ultimo_correo", "")
        val contrasenaGuardada = prefs.getString("ultima_contrasena", "")

        if (!correoGuardado.isNullOrEmpty() && !contrasenaGuardada.isNullOrEmpty()) {

            etEmailLogin.setText(correoGuardado)
            etPasswordLogin.setText(contrasenaGuardada)
        }
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val tvIrARegistro = findViewById<TextView>(R.id.tvIrARegistro)

        btnEntrar.setOnClickListener {
            // Obtener el texto de los inputs
            val email = etEmailLogin.text.toString().trim()
            val password = etPasswordLogin.text.toString().trim()

            // Validaciones
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Iniciar sesion
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val prefs = getSharedPreferences("CredencialesLogin", MODE_PRIVATE)

                        prefs.edit()
                            .putString("ultimo_correo", email)
                            .putString("ultima_contrasena", password)
                            .apply()
                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

                        // Navega a la pantalla principal
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish() // Cierra el login
                    } else {
                        // Si falla (ej. contraseña incorrecta)
                        Toast.makeText(this, "Error al iniciar sesión: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvIrARegistro.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
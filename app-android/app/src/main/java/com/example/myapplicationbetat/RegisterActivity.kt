package com.example.myapplicationbetat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    // 1. Declara la instancia de Firebase Auth
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 2. Inicializa Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 3. Encuentra tus vistas (Inputs y Botones)
        val etNombre = findViewById<TextInputEditText>(R.id.etNombre)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnCrearCuenta = findViewById<Button>(R.id.btnCrearCuenta)
        val tvIrALogin = findViewById<TextView>(R.id.tvIrALogin)

        // 4. Modifica el Click Listener del botón
        btnCrearCuenta.setOnClickListener {
            // 5. Obtiene el texto de los inputs
            val nombre = etNombre.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 6. Validaciones simples
            if (nombre.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 7. Crear usuario en Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {

                        val userId = task.result.user?.uid ?: return@addOnCompleteListener

                        val usuarioData = hashMapOf(
                            "nombre" to nombre,
                            "email" to email
                        )

                        db.collection("usuarios").document(userId)
                            .set(usuarioData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Cuenta creada y vinculada", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }


                else {
                        // Si falla la autenticación (ej. email repetido)
                        Toast.makeText(this, "Error al registrarse: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvIrALogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}
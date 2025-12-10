package com.example.omg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.omg.model.EstadoResponse
import com.example.omg.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PromoTrabajadorActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_trabajador)

        auth = FirebaseAuth.getInstance()

        val btnConvertirse = findViewById<Button>(R.id.btnConvertirse)
        btnConvertirse.setOnClickListener {
            checkUserStatus()
        }

        val btnRegresar = findViewById<Button>(R.id.btnRegresar)
        btnRegresar.setOnClickListener {
            finish()
        }
    }

    private fun checkUserStatus() {
        // 1. Obtener email (prioridad: SharedPreferences > Firebase)
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var correo = prefs.getString("correo_postulante", null)

        if (correo.isNullOrEmpty()) {
            correo = auth.currentUser?.email
        }
        
        Log.d("PromoTrabajador", "Verificando estado para el correo: '$correo'")

        if (correo.isNullOrEmpty()) {
            Toast.makeText(this, "No se encontró un correo asociado", Toast.LENGTH_SHORT).show()
            navegarSegunEstado("no_registrado")
            return
        }

        val apiService = RetrofitClient.instance

        // Llamada al backend
        apiService.verificarEstado(correo).enqueue(object : Callback<EstadoResponse> {
            override fun onResponse(call: Call<EstadoResponse>, response: Response<EstadoResponse>) {
                Log.d("PromoTrabajador", "Respuesta Code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val estado = response.body()?.estado ?: "no_registrado"
                    val nombre = response.body()?.nombre
                    Log.d("PromoTrabajador", "Estado recibido: '$estado' para usuario: $nombre")
                    
                    navegarSegunEstado(estado)
                } else {
                    Log.e("PromoTrabajador", "Error en respuesta: ${response.errorBody()?.string()}")
                    navegarSegunEstado("no_registrado")
                }
            }

            override fun onFailure(call: Call<EstadoResponse>, t: Throwable) {
                Log.e("PromoTrabajador", "Fallo en conexión: ${t.message}")
                Toast.makeText(this@PromoTrabajadorActivity, "Error de conexión: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun navegarSegunEstado(estado: String) {
        // Normalizar el estado por si acaso llega con mayúsculas
        val estadoNormalizado = estado.lowercase()

        val intent = when (estadoNormalizado) {
            "admitido" -> Intent(this, TrabajadorHomeActivity::class.java)
            "pendiente" -> Intent(this, FaceValidationActivity::class.java)
            "denegado" -> {
                Toast.makeText(this, "Tu solicitud fue denegada. Contacta soporte.", Toast.LENGTH_LONG).show()
                null
            }
            // "no_registrado" u otros
            else -> Intent(this, FormularioRegistroActivity::class.java) 
        }

        intent?.let {
            startActivity(it)
            if (estadoNormalizado == "admitido") finish() // Cerrar promo si ya es trabajador
        }
    }
}

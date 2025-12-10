package com.example.omg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.omg.model.EstadoResponse
import com.example.omg.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    private lateinit var searchBar: EditText
    private lateinit var iconLeft: ImageView
    private lateinit var iconRight: ImageView
    private lateinit var navHome: ImageView
    private lateinit var navProfile: ImageView
    private lateinit var navNotifications: ImageView
    private lateinit var animatedCirclesView: AnimatedCirclesView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas
        initViews()

        // Configurar listeners
        setupListeners()

        // Cargar servicios favoritos del usuario
        loadFavoriteServices()

        // Mostrar bienvenida
        val user = auth.currentUser
        Toast.makeText(
            this,
            "Bienvenido ${user?.displayName ?: user?.phoneNumber ?: "Usuario"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun initViews() {
        searchBar = findViewById(R.id.searchBar)
        iconLeft = findViewById(R.id.iconLeft)
        iconRight = findViewById(R.id.iconRight)
        navHome = findViewById(R.id.navHome)
        navProfile = findViewById(R.id.navProfile)
        navNotifications = findViewById(R.id.navNotifications)
        animatedCirclesView = findViewById(R.id.animatedCirclesView)
    }


    private fun setupListeners() {
        // Listener para el ícono de "Modo Trabajador"
        iconLeft.setOnClickListener {
            checkUserStatusAndNavigate()
        }

        searchBar.setOnClickListener {
            openSearch()
        }

        // Deshabilitar edición directa en la barra
        searchBar.isFocusable = false
        searchBar.isFocusableInTouchMode = false
        searchBar.isClickable = true


        iconRight.setOnClickListener {
            val intent = Intent(this, AiAssistantActivity::class.java)
            startActivity(intent)
        }

        navHome.setOnClickListener {
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
            // Ya estamos en Home
        }

        navProfile.setOnClickListener {
            // Navegar a perfil
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        navNotifications.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkUserStatusAndNavigate() {
        // Priorizar el correo registrado en el formulario (SharedPreferences)
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var correo = prefs.getString("correo_postulante", null)

        // Si no hay en prefs, usar el de Auth
        if (correo.isNullOrEmpty()) {
            correo = auth.currentUser?.email
        }

        Log.d("HomeActivity", "Verificando estado para el correo: $correo")

        if (correo.isNullOrEmpty()) {
            // Si no hay correo, no es trabajador. Va a la promo.
            startActivity(Intent(this, PromoTrabajadorActivity::class.java))
            return
        }

        val apiService = RetrofitClient.instance
        apiService.verificarEstado(correo).enqueue(object : Callback<EstadoResponse> {
            override fun onResponse(call: Call<EstadoResponse>, response: Response<EstadoResponse>) {
                Log.d("HomeActivity", "Respuesta del servidor: Code=${response.code()}, Body=${response.body()}")
                
                if (response.isSuccessful) {
                    val estado = response.body()?.estado
                    Log.d("HomeActivity", "Estado recibido: $estado")
                    
                    if (estado == "admitido") {
                        // Si es admitido, va directo al Home del Trabajador
                        startActivity(Intent(this@HomeActivity, TrabajadorHomeActivity::class.java))
                    } else {
                        // Para cualquier otro estado, va a la pantalla de promo que gestionará el flujo
                        startActivity(Intent(this@HomeActivity, PromoTrabajadorActivity::class.java))
                    }
                } else {
                    Log.e("HomeActivity", "Error en respuesta: ${response.errorBody()?.string()}")
                    // En caso de error, fallback a la pantalla de promo
                    startActivity(Intent(this@HomeActivity, PromoTrabajadorActivity::class.java))
                }
            }

            override fun onFailure(call: Call<EstadoResponse>, t: Throwable) {
                Log.e("HomeActivity", "Fallo en conexión: ${t.message}")
                Toast.makeText(this@HomeActivity, "Error de conexión: ${t.message}", Toast.LENGTH_LONG).show()
                // En caso de fallo, fallback a la pantalla de promo
                startActivity(Intent(this@HomeActivity, PromoTrabajadorActivity::class.java))
            }
        })
    }

    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
    }

    private fun loadFavoriteServices() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val favoriteServices = document.get("favoriteServices") as? List<String>

                    if (!favoriteServices.isNullOrEmpty()) {
                        animatedCirclesView.setFavoriteServices(favoriteServices)
                        Log.d("HomeActivity", "Servicios cargados: $favoriteServices")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeActivity", "Error al cargar servicios: ${e.message}")
            }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity() // Cerrar todas las actividades
    }
}
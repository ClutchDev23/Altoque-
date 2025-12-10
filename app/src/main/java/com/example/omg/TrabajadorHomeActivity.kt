package com.example.omg

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.omg.model.EstadoResponse
import com.example.omg.network.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TrabajadorHomeActivity : AppCompatActivity() {

    private lateinit var switchEstado: SwitchMaterial
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // UI Elements for Requests
    private lateinit var cardRequest: CardView
    private lateinit var tvName: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvSolicitudesTitle: TextView
    private lateinit var btnAceptarTrabajo: Button

    private var requestListener: ListenerRegistration? = null
    private var directRequestListener: ListenerRegistration? = null
    private var myServiceType: String? = null
    private var currentRequestId: String? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2000
        private const val CHANNEL_ID = "trabajador_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trabajador_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initViews()
        
        // 1. Verificar estado del trabajador antes de permitirle hacer nada
        verificarEstadoTrabajador()

        setupListeners()
        createNotificationChannel()
    }

    private fun verificarEstadoTrabajador() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var correo = prefs.getString("correo_postulante", null)

        if (correo.isNullOrEmpty()) {
            correo = auth.currentUser?.email
        }

        if (correo.isNullOrEmpty()) {
            Toast.makeText(this, "Error: No se encontró correo registrado", Toast.LENGTH_LONG).show()
            volverAModoUsuario()
            return
        }

        val apiService = RetrofitClient.instance
        apiService.verificarEstado(correo).enqueue(object : Callback<EstadoResponse> {
            override fun onResponse(call: Call<EstadoResponse>, response: Response<EstadoResponse>) {
                if (response.isSuccessful) {
                    val estado = response.body()?.estado
                    
                    if (estado == "admitido") {
                        // Si es admitido, procedemos a chequear si tenemos su servicio configurado
                        checkServiceType()
                    } else {
                        Toast.makeText(this@TrabajadorHomeActivity, "Tu cuenta no está activa o fue denegada.", Toast.LENGTH_LONG).show()
                        volverAModoUsuario()
                    }
                } else {
                    Toast.makeText(this@TrabajadorHomeActivity, "Error verificando cuenta.", Toast.LENGTH_SHORT).show()
                    volverAModoUsuario()
                }
            }

            override fun onFailure(call: Call<EstadoResponse>, t: Throwable) {
                Toast.makeText(this@TrabajadorHomeActivity, "Error de conexión: ${t.message}", Toast.LENGTH_SHORT).show()
                // Por seguridad volvemos al modo usuario si no podemos validar
                volverAModoUsuario()
            }
        })
    }

    private fun checkServiceType() {
        myServiceType = sharedPreferences.getString("tipo_servicio_trabajador", null)
        
        if (myServiceType == null) {
            // Si no está en prefs, intentar buscarlo en Firestore (fallback)
            fetchServiceTypeFromFirestore()
        } else {
            // Si tenemos servicio, restaurar estado UI
            restoreUIState()
        }
    }

    private fun restoreUIState() {
        val isOnline = sharedPreferences.getBoolean("is_online", false)
        switchEstado.isChecked = isOnline
        if (isOnline) {
             if (checkLocationPermission()) {
                 activarModoOnline()
             }
        }
        updateStatusUI(isOnline)
    }

    private fun initViews() {
        switchEstado = findViewById(R.id.switchEstado)
        cardRequest = findViewById(R.id.cardRequest)
        tvName = findViewById(R.id.tvName)
        tvDescription = findViewById(R.id.tvDescription)
        tvSolicitudesTitle = findViewById(R.id.tvSolicitudes)
        btnAceptarTrabajo = findViewById(R.id.btnAceptarTrabajo)

        cardRequest.visibility = View.GONE
        tvSolicitudesTitle.text = "ESPERANDO SOLICITUDES..."
    }

    private fun setupListeners() {
        val btnVolverUsuario = findViewById<View>(R.id.btnVolverUsuario)
        btnVolverUsuario.setOnClickListener {
            volverAModoUsuario()
        }

        switchEstado.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("is_online", isChecked).apply()
            
            if (isChecked) {
                if (checkLocationPermission()) {
                    activarModoOnline()
                } else {
                    switchEstado.isChecked = false
                    requestLocationPermission()
                }
            } else {
                desactivarModoOnline()
            }
        }

        btnAceptarTrabajo.setOnClickListener {
            aceptarTrabajoActual()
        }
    }

    private fun aceptarTrabajoActual() {
        val requestId = currentRequestId ?: return
        
        db.collection("service_requests").document(requestId)
            .update("status", "accepted")
            .addOnSuccessListener {
                Toast.makeText(this, "¡Trabajo aceptado!", Toast.LENGTH_SHORT).show()
                // La UI se actualizará automáticamente gracias al listener
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al aceptar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun volverAModoUsuario() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    private fun fetchServiceTypeFromFirestore() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
             // 1. Intentamos en 'users' (donde el formulario guarda el dato de respaldo)
             db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                 if (doc.exists() && doc.contains("workerService")) {
                     myServiceType = doc.getString("workerService")
                     sharedPreferences.edit().putString("tipo_servicio_trabajador", myServiceType).apply()
                     restoreUIState()
                 } else {
                     // 2. Si no, intentamos en 'active_workers' (legacy)
                     db.collection("active_workers").document(uid).get().addOnSuccessListener { workerDoc ->
                          if (workerDoc.exists() && workerDoc.contains("service")) {
                              myServiceType = workerDoc.getString("service")
                              sharedPreferences.edit().putString("tipo_servicio_trabajador", myServiceType).apply()
                              restoreUIState()
                          } else {
                              Toast.makeText(this, "Error: No se identifica tu servicio. Contacta soporte.", Toast.LENGTH_LONG).show()
                              switchEstado.isEnabled = false
                          }
                     }
                 }
             }.addOnFailureListener {
                 Toast.makeText(this, "Error al recuperar datos del servicio.", Toast.LENGTH_SHORT).show()
             }
        }
    }

    private fun activarModoOnline() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                
                updateStatusUI(true)
                updateWorkerLocationInFirestore(true, lat, lng)
                startListeningForRequests(lat, lng)
                
                Toast.makeText(this, "🔴 Modo Trabajador Activado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun desactivarModoOnline() {
        updateStatusUI(false)
        updateWorkerLocationInFirestore(false, 0.0, 0.0)
        stopListeningForRequests()
        
        cardRequest.visibility = View.GONE
        tvSolicitudesTitle.text = "SOLICITUDES"
        Toast.makeText(this, "⚫ Modo Offline", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusUI(isOnline: Boolean) {
        if (isOnline) {
            switchEstado.text = "En Línea"
            switchEstado.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            switchEstado.text = "Offline"
            switchEstado.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun updateWorkerLocationInFirestore(isOnline: Boolean, lat: Double, lng: Double) {
        val user = auth.currentUser ?: return
        val service = myServiceType ?: return // No podemos guardar sin servicio
        
        val workerData = hashMapOf(
            "uid" to user.uid,
            "name" to (user.displayName ?: "Trabajador"),
            "email" to (user.email ?: ""),
            "service" to service,
            "latitude" to lat,
            "longitude" to lng,
            "isOnline" to isOnline,
            "lastUpdated" to System.currentTimeMillis()
        )

        db.collection("active_workers").document(user.uid)
            .set(workerData)
            .addOnSuccessListener { 
                Log.d("TrabajadorHome", "Ubicación actualizada en Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("TrabajadorHome", "Error actualizando ubicación", e)
            }
    }

    private fun startListeningForRequests(workerLat: Double, workerLng: Double) {
        stopListeningForRequests()
        
        val service = myServiceType ?: return
        val userId = auth.currentUser?.uid ?: return

        // 1. Escuchar solicitudes "searching" (Búsquedas cercanas generales)
        requestListener = db.collection("service_requests")
            .whereEqualTo("service", service)
            .whereEqualTo("status", "searching")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots.documents) {
                        val reqLat = doc.getDouble("latitude") ?: 0.0
                        val reqLng = doc.getDouble("longitude") ?: 0.0
                        val userName = doc.getString("userName") ?: "Usuario"
                        
                        val results = FloatArray(1)
                        Location.distanceBetween(workerLat, workerLng, reqLat, reqLng, results)
                        val distanceInMeters = results[0]

                        if (distanceInMeters < 10000) {
                            runOnUiThread {
                                // Solo mostrar búsqueda si NO hay una solicitud activa
                                if (currentRequestId == null) {
                                    showRequestCard(userName, distanceInMeters, "searching")
                                    sendNotification("¡Nuevo cliente cerca!", "$userName busca un $service")
                                }
                            }
                            return@addSnapshotListener
                        }
                    }
                }
            }

        // 2. Escuchar solicitudes DIRECTAS ("requested" o "accepted")
        // No usamos whereIn para evitar problemas de índices compuestos. Filtramos localmente.
        directRequestListener = db.collection("service_requests")
            .whereEqualTo("providerId", userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }

                if (snapshots != null && !snapshots.isEmpty) {
                    // Buscar si hay alguna activa
                    val doc = snapshots.documents.firstOrNull { 
                        val status = it.getString("status")
                        status == "requested" || status == "accepted"
                    }
                    
                    if (doc != null) {
                        currentRequestId = doc.id
                        val userName = doc.getString("clientName") ?: "Cliente"
                        val status = doc.getString("status") ?: "requested"
                        
                        runOnUiThread {
                            showRequestCard(userName, 0f, status)
                            if (status == "requested") {
                                sendNotification("¡SOLICITUD DE TRABAJO! 🔔", "$userName te quiere contratar.")
                            }
                        }
                    } else {
                        // Si no hay activas, limpiamos (si teniamos una)
                        if (currentRequestId != null) {
                            currentRequestId = null
                            runOnUiThread {
                                cardRequest.visibility = View.GONE
                                tvSolicitudesTitle.text = "ESPERANDO SOLICITUDES..."
                            }
                        }
                    }
                }
            }
    }

    private fun stopListeningForRequests() {
        requestListener?.remove()
        requestListener = null
        directRequestListener?.remove()
        directRequestListener = null
    }

    private fun showRequestCard(userName: String, distanceMeters: Float, type: String) {
        try {
            cardRequest.visibility = View.VISIBLE
            tvName.text = userName

            if (type == "requested") {
                // ESTADO: PENDIENTE DE ACEPTAR
                tvSolicitudesTitle.text = "¡SOLICITUD DE TRABAJO!"
                tvSolicitudesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                tvDescription.text = "El cliente quiere contratarte. ¿Aceptas?"
                
                // Mostrar botón aceptar
                btnAceptarTrabajo.visibility = View.VISIBLE
                
                try {
                    cardRequest.setCardBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
                } catch (e: Exception) {
                    cardRequest.setCardBackgroundColor(0xFF6200EE.toInt())
                }
                
            } else if (type == "accepted") {
                // ESTADO: ACEPTADO / TRABAJO EN CURSO
                tvSolicitudesTitle.text = "¡TE HAN CONTRATADO!"
                tvSolicitudesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                tvDescription.text = "Has aceptado el trabajo. ¡Ponte en contacto!"
                
                // Ocultar botón aceptar
                btnAceptarTrabajo.visibility = View.GONE
                
                try {
                    cardRequest.setCardBackgroundColor(ContextCompat.getColor(this, R.color.teal_700))
                } catch (e: Exception) {
                    cardRequest.setCardBackgroundColor(0xFF018786.toInt())
                }

            } else {
                // ESTADO: BUSCANDO (SEARCHING)
                tvSolicitudesTitle.text = "NUEVA SOLICITUD"
                tvSolicitudesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                val distanceKm = String.format("%.1f", distanceMeters / 1000)
                tvDescription.text = "Busca servicio de $myServiceType a $distanceKm km de ti."
                
                btnAceptarTrabajo.visibility = View.GONE
                
                try {
                    cardRequest.setCardBackgroundColor(ContextCompat.getColor(this, R.color.teal_200))
                } catch (e: Exception) {
                    cardRequest.setCardBackgroundColor(0xFF03DAC5.toInt())
                }
            }
        } catch (e: Exception) {
            Log.e("TrabajadorHome", "Error actualizando UI", e)
        }
    }

    // --- Notificaciones Locales ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones Trabajador"
            val descriptionText = "Avisos de nuevos trabajos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications) // Asegúrate de tener este icono
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(if (title.contains("CONFIRMADA")) 2002 else 1001, builder.build())
        } catch (e: SecurityException) {
            // Manejar permiso de notificación en Android 13+ si es necesario
        }
    }

    // --- Permisos ---

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchEstado.isChecked = true
            } else {
                switchEstado.isChecked = false
            }
        }
    }
}

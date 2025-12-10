package com.example.omg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProviderDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btnBack: ImageButton
    private lateinit var ivProviderPhoto: ImageView
    private lateinit var tvProviderName: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvReviews: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvExperience: TextView
    private lateinit var tvAveragePrice: TextView
    private lateinit var tvServices: TextView
    private lateinit var btnViewDetails: View
    private lateinit var detailsContainer: LinearLayout
    private lateinit var btnViewPortfolio: View
    private lateinit var btnCall: View
    private lateinit var btnMessage: View
    private lateinit var btnBackToList: View
    private lateinit var btnRequestService: View

    private var googleMap: GoogleMap? = null
    private var providerLat: Double = 0.0
    private var providerLng: Double = 0.0
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0
    private var providerPhone: String = ""
    private var providerName: String = ""
    private var providerId: String = ""
    private var providerService: String = ""
    private var isDetailsVisible = false

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val CHANNEL_ID = "service_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Obtener datos del intent
        getIntentData()

        // Inicializar vistas
        initViews()

        // Configurar mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapDetailFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar listeners
        setupListeners()

        // Llenar datos
        fillProviderData()
        
        createNotificationChannel()
    }

    private fun getIntentData() {
        providerId = intent.getStringExtra("PROVIDER_ID") ?: ""
        providerLat = intent.getDoubleExtra("PROVIDER_LAT", 0.0)
        providerLng = intent.getDoubleExtra("PROVIDER_LNG", 0.0)
        userLat = intent.getDoubleExtra("USER_LAT", 0.0)
        userLng = intent.getDoubleExtra("USER_LNG", 0.0)
        providerPhone = intent.getStringExtra("PROVIDER_PHONE") ?: ""
        providerName = intent.getStringExtra("PROVIDER_NAME") ?: "Proveedor"
        providerService = intent.getStringExtra("PROVIDER_SERVICE") ?: "General"
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        ivProviderPhoto = findViewById(R.id.ivProviderPhoto)
        tvProviderName = findViewById(R.id.tvProviderName)
        tvRating = findViewById(R.id.tvRating)
        tvReviews = findViewById(R.id.tvReviews)
        tvArrivalTime = findViewById(R.id.tvArrivalTime)
        tvExperience = findViewById(R.id.tvExperience)
        tvAveragePrice = findViewById(R.id.tvAveragePrice)
        tvServices = findViewById(R.id.tvServices)
        btnViewDetails = findViewById(R.id.btnViewDetails)
        detailsContainer = findViewById(R.id.detailsContainer)
        btnViewPortfolio = findViewById(R.id.btnViewPortfolio)
        btnCall = findViewById(R.id.btnCall)
        btnMessage = findViewById(R.id.btnMessage)
        btnBackToList = findViewById(R.id.btnBackToList)
        btnRequestService = findViewById(R.id.btnRequestService)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnBackToList.setOnClickListener { finish() }

        btnViewDetails.setOnClickListener { toggleDetails() }

        btnViewPortfolio.setOnClickListener {
            Toast.makeText(this, "Próximamente: Galería de trabajos", Toast.LENGTH_SHORT).show()
        }

        btnCall.setOnClickListener {
            if (providerPhone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$providerPhone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Número no disponible", Toast.LENGTH_SHORT).show()
            }
        }

        btnMessage.setOnClickListener {
            Toast.makeText(this, "Próximamente: Chat en tiempo real", Toast.LENGTH_SHORT).show()
        }
        
        btnRequestService.setOnClickListener {
            requestService()
        }
    }
    
    private fun requestService() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show()
            return
        }

        // Crear objeto de solicitud en Firestore
        val requestId = db.collection("service_requests").document().id
        val requestData = hashMapOf(
            "id" to requestId,
            "clientId" to user.uid,
            "clientName" to (user.displayName ?: "Usuario"),
            "providerId" to providerId, // ID del trabajador
            "service" to providerService,
            "status" to "requested", // Nuevo estado "solicitado"
            "clientLat" to userLat,
            "clientLng" to userLng,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("service_requests").document(requestId)
            .set(requestData)
            .addOnSuccessListener {
                Toast.makeText(this, "¡Servicio solicitado!", Toast.LENGTH_SHORT).show()
                sendServiceOnTheWayNotification()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al solicitar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleDetails() {
        isDetailsVisible = !isDetailsVisible
        detailsContainer.visibility = if (isDetailsVisible) View.VISIBLE else View.GONE
    }

    private fun fillProviderData() {
        tvProviderName.text = providerName
        tvRating.text = intent.getFloatExtra("PROVIDER_RATING", 0f).toString()
        tvReviews.text = "(${intent.getIntExtra("PROVIDER_REVIEWS", 0)})"
        tvArrivalTime.text = "5 minutos"
        tvExperience.text = intent.getStringExtra("PROVIDER_EXPERIENCE") ?: "N/A"
        tvAveragePrice.text = intent.getStringExtra("PROVIDER_PRICE") ?: "Consultar"

        // Servicios (simulado o del intent)
        val description = intent.getStringExtra("PROVIDER_DESCRIPTION") ?: "Servicios generales"
        tvServices.text = description

        // Cargar foto
        val photoUrl = intent.getStringExtra("PROVIDER_PHOTO")
        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_user)
                .into(ivProviderPhoto)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        
        val userLoc = LatLng(userLat, userLng)
        val providerLoc = LatLng(providerLat, providerLng)

        // Marcador Usuario
        map.addMarker(MarkerOptions()
            .position(userLoc)
            .title("Tu ubicación")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

        // Marcador Proveedor
        map.addMarker(MarkerOptions()
            .position(providerLoc)
            .title(providerName))

        // Mover cámara
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(providerLoc, 15f))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Servicios Activos"
            val descriptionText = "Notificaciones de estado del servicio"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendServiceOnTheWayNotification() {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Servicio en Camino 🚀")
            .setContentText("$providerName está yendo a tu ubicación.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3001, builder.build())
    }
}

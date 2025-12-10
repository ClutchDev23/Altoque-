package com.example.omg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProvidersListActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btnBack: ImageButton
    private lateinit var tvServiceName: TextView
    private lateinit var tvProvidersCount: TextView
    private lateinit var rvProviders: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var serviceName: String = ""
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0
    private val providersList = mutableListOf<Provider>()
    private lateinit var providersAdapter: ProvidersAdapter

    companion object {
        private const val CHANNEL_ID = "user_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_providers_list)

        // Obtener datos del intent
        serviceName = intent.getStringExtra("SERVICE_NAME") ?: "Servicio"
        userLat = intent.getDoubleExtra("LATITUDE", -12.0931)
        userLng = intent.getDoubleExtra("LONGITUDE", -77.0465)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        createNotificationChannel()

        // 1. Crear solicitud en Firestore para que los trabajadores escuchen
        createServiceRequest()

        initViews()
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        setupRecyclerView()
        setupListeners()
        loadProviders()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvServiceName = findViewById(R.id.tvServiceName)
        tvProvidersCount = findViewById(R.id.tvProvidersCount)
        rvProviders = findViewById(R.id.rvProviders)
        progressBar = findViewById(R.id.progressBar)
        mapView = findViewById(R.id.mapView)

        tvServiceName.text = serviceName
    }

    private fun setupRecyclerView() {
        providersAdapter = ProvidersAdapter(providersList) { provider ->
            val intent = Intent(this, ProviderDetailActivity::class.java).apply {
                putExtra("PROVIDER_ID", provider.id)
                putExtra("PROVIDER_NAME", provider.name)
                putExtra("PROVIDER_PHOTO", provider.photoUrl)
                putExtra("PROVIDER_RATING", provider.rating)
                putExtra("PROVIDER_REVIEWS", provider.reviewCount)
                putExtra("PROVIDER_SERVICE", provider.service)
                putExtra("PROVIDER_PRICE", provider.averagePrice)
                putExtra("PROVIDER_EXPERIENCE", provider.experience)
                putExtra("PROVIDER_DESCRIPTION", provider.description)
                putExtra("PROVIDER_LAT", provider.latitude)
                putExtra("PROVIDER_LNG", provider.longitude)
                putExtra("PROVIDER_PHONE", provider.phone)
                putExtra("USER_LAT", userLat)
                putExtra("USER_LNG", userLng)
            }
            startActivity(intent)
        }

        rvProviders.apply {
            layoutManager = LinearLayoutManager(this@ProvidersListActivity)
            adapter = providersAdapter
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            // Opcional: Cancelar la solicitud si sale
            finish()
        }
    }

    private fun createServiceRequest() {
        val user = auth.currentUser ?: return
        val requestId = db.collection("service_requests").document().id

        val request = hashMapOf(
            "id" to requestId,
            "uid" to user.uid,
            "userName" to (user.displayName ?: "Usuario"),
            "service" to serviceName,
            "latitude" to userLat,
            "longitude" to userLng,
            "status" to "searching",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("service_requests").document(requestId)
            .set(request)
            .addOnSuccessListener {
                Log.d("ProvidersList", "Solicitud creada ID: $requestId")
            }
            .addOnFailureListener { e ->
                Log.e("ProvidersList", "Error creando solicitud", e)
            }
    }

    private fun loadProviders() {
        progressBar.visibility = View.VISIBLE
        providersList.clear()

        // 1. Escuchar trabajadores ONLINE en Firestore
        // NOTA: Para producción usar GeoFirestore o consultas con rangos de lat/lng.
        // Aquí descargamos todos los 'active_workers' y filtramos localmente por servicio y distancia.
        
        db.collection("active_workers")
            .whereEqualTo("isOnline", true)
            .whereEqualTo("service", serviceName)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ProvidersList", "Listen failed.", e)
                    return@addSnapshotListener
                }

                providersList.clear()

                if (snapshots != null) {
                    for (doc in snapshots) {
                        val lat = doc.getDouble("latitude") ?: 0.0
                        val lng = doc.getDouble("longitude") ?: 0.0
                        val name = doc.getString("name") ?: "Trabajador"
                        val uid = doc.getString("uid") ?: ""

                        // Calcular distancia
                        val results = FloatArray(1)
                        Location.distanceBetween(userLat, userLng, lat, lng, results)
                        val distanceInMeters = results[0]

                        // Filtro de distancia (ej: 10km)
                        if (distanceInMeters < 10000) {
                            // Crear objeto Provider
                            // Faltan datos como rating, precio, etc. en 'active_workers',
                            // idealmente deberían estar ahí o hacer un fetch doble.
                            // Usaremos placeholders por ahora.
                            val p = Provider(
                                id = uid,
                                name = name,
                                photoUrl = "",
                                service = serviceName,
                                rating = 5.0f, // Placeholder
                                reviewCount = 10,
                                priceRange = "S/ ??",
                                averagePrice = "S/ ??",
                                arrivalTime = "${(distanceInMeters / 100).toInt()} min",
                                description = "Trabajador activo cerca de ti",
                                experience = "Verificado",
                                servicesOffered = listOf(serviceName),
                                latitude = lat,
                                longitude = lng,
                                phone = "",
                                isAvailable = true
                            )
                            providersList.add(p)
                            
                            // Notificar al usuario que se encontró a alguien
                            sendUserNotification(name, distanceInMeters)
                        }
                    }
                }

                // Si no hay reales, mezclar con mocks para que no se vea vacío en demo
                if (providersList.isEmpty()) {
                    providersList.addAll(getMockProviders())
                }

                providersAdapter.notifyDataSetChanged()
                tvProvidersCount.text = "${providersList.size} proveedores cercanos"
                progressBar.visibility = View.GONE
                updateMapMarkers()
            }
    }
    
    private fun updateMapMarkers() {
        googleMap?.clear()
        val userLocation = LatLng(userLat, userLng)

        googleMap?.addMarker(
            MarkerOptions()
                .position(userLocation)
                .title("Tu ubicación")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .zIndex(1.0f)
        )

        providersList.forEach { provider ->
            val providerLocation = LatLng(provider.latitude, provider.longitude)
            googleMap?.addMarker(
                MarkerOptions()
                    .position(providerLocation)
                    .title(provider.name)
                    .snippet("${provider.service} - ${provider.arrivalTime}")
            )
        }
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 14f))
    }

    private fun sendUserNotification(workerName: String, distanceMeters: Float) {
        val distanceKm = String.format("%.1f", distanceMeters / 1000)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("¡Trabajador encontrado!")
            .setContentText("$workerName está disponible a $distanceKm km")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2001, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones Usuario"
            val descriptionText = "Avisos de proveedores cercanos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        val userLocation = LatLng(userLat, userLng)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 14f))
        if (providersList.isNotEmpty()) updateMapMarkers()
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    private fun getMockProviders(): List<Provider> {
        return listOf(
            Provider(
                id = "mock1",
                name = "Aitor Tilla (Demo)",
                photoUrl = "",
                service = serviceName,
                rating = 4.8f,
                reviewCount = 142,
                priceRange = "S/ 50-80",
                averagePrice = "S/ 65",
                arrivalTime = "5 minutos",
                description = "Experto en $serviceName (Datos de prueba)",
                experience = "10 años",
                servicesOffered = listOf(serviceName),
                latitude = userLat + 0.005,
                longitude = userLng + 0.005,
                phone = "+51999888777",
                isAvailable = true
            )
        )
    }

    private fun createMockProviders() { }
}

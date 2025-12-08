package com.example.omg

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var layoutAnimation: LinearLayout
    private lateinit var layoutUpload: LinearLayout
    private lateinit var layoutAnalyzing: LinearLayout
    private lateinit var layoutResult: ScrollView

    private lateinit var circlePulse: View
    private lateinit var dot1: ImageView
    private lateinit var dot2: ImageView
    private lateinit var dot3: ImageView
    private lateinit var dot4: ImageView
    private lateinit var dot5: ImageView
    private lateinit var dot6: ImageView

    private lateinit var photoPlaceholder: LinearLayout
    private lateinit var ivUploadedPhoto: ImageView
    private lateinit var photoActions: LinearLayout
    private lateinit var btnTakePhoto: ImageView
    private lateinit var btnDeletePhoto: ImageView
    private lateinit var etProblemDescription: EditText
    private lateinit var btnAnalyze: Button

    private lateinit var tvAiResponse: TextView
    private lateinit var layoutSuggestedService: LinearLayout
    private lateinit var tvSuggestedService: TextView
    private lateinit var btnFindProvider: Button
    private lateinit var btnNewAnalysis: Button
    private lateinit var btnClose: ImageView
    private lateinit var btnCloseResult: ImageView

    private var currentPhotoBitmap: Bitmap? = null
    private var suggestedService: String? = null
    private val GEMINI_API_KEY = "AIzaSyAg9quXZV414T1nHYqI-NxlbEhjgeauJnc"

    private val servicesList = listOf(
        "Gasfitería", "Jardinería", "Repartidor de Gas", "Servicio de Limpieza",
        "Mozos", "Masajista", "Manicure", "Técnico", "Profesores",
        "Electricista", "Plomería", "Carpintería", "Pintura", "Delivery", "Seguridad"
    )

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                currentPhotoBitmap = imageBitmap
                showUploadedPhoto()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                currentPhotoBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, it))
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, it)
                }
                showUploadedPhoto()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog()
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_assistant)

        initViews()
        startLoadingAnimation()
        setupListeners()
    }

    private fun initViews() {
        layoutAnimation = findViewById(R.id.layoutAnimation)
        layoutUpload = findViewById(R.id.layoutUpload)
        layoutAnalyzing = findViewById(R.id.layoutAnalyzing)
        layoutResult = findViewById(R.id.layoutResult)

        circlePulse = findViewById(R.id.circlePulse)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        dot4 = findViewById(R.id.dot4)
        dot5 = findViewById(R.id.dot5)
        dot6 = findViewById(R.id.dot6)

        photoPlaceholder = findViewById(R.id.photoPlaceholder)
        ivUploadedPhoto = findViewById(R.id.ivUploadedPhoto)
        photoActions = findViewById(R.id.photoActions)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnDeletePhoto = findViewById(R.id.btnDeletePhoto)
        etProblemDescription = findViewById(R.id.etProblemDescription)
        btnAnalyze = findViewById(R.id.btnAnalyze)

        tvAiResponse = findViewById(R.id.tvAiResponse)
        layoutSuggestedService = findViewById(R.id.layoutSuggestedService)
        tvSuggestedService = findViewById(R.id.tvSuggestedService)
        btnFindProvider = findViewById(R.id.btnFindProvider)
        btnNewAnalysis = findViewById(R.id.btnNewAnalysis)
        btnClose = findViewById(R.id.btnClose)
        btnCloseResult = findViewById(R.id.btnCloseResult)
    }

    private fun startLoadingAnimation() {
        val scaleUp = ObjectAnimator.ofFloat(circlePulse, "scaleX", 1f, 1.2f)
        scaleUp.duration = 1000
        scaleUp.repeatMode = ValueAnimator.REVERSE
        scaleUp.repeatCount = ValueAnimator.INFINITE
        scaleUp.start()

        val scaleUpY = ObjectAnimator.ofFloat(circlePulse, "scaleY", 1f, 1.2f)
        scaleUpY.duration = 1000
        scaleUpY.repeatMode = ValueAnimator.REVERSE
        scaleUpY.repeatCount = ValueAnimator.INFINITE
        scaleUpY.start()

        animateOrbitingDots()

        Handler(Looper.getMainLooper()).postDelayed({
            showUploadScreen()
        }, 2000)
    }

    private fun animateOrbitingDots() {
        val dots = listOf(dot1, dot2, dot3, dot4, dot5, dot6)
        val radius = 100f

        dots.forEachIndexed { index, dot ->
            val animator = ValueAnimator.ofFloat(0f, 360f)
            animator.duration = 3000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = LinearInterpolator()

            val offset = (360f / dots.size) * index

            animator.addUpdateListener { animation ->
                val angle = animation.animatedValue as Float + offset
                val radian = Math.toRadians(angle.toDouble())

                val x = (radius * cos(radian)).toFloat()
                val y = (radius * sin(radian)).toFloat()

                dot.translationX = x
                dot.translationY = y
            }

            animator.start()
        }
    }

    private fun showUploadScreen() {
        layoutAnimation.visibility = View.GONE
        layoutUpload.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        btnClose.setOnClickListener { finish() }
        btnCloseResult.setOnClickListener { resetToUpload() }

        photoPlaceholder.setOnClickListener {
            checkCameraPermission()
        }

        btnTakePhoto.setOnClickListener {
            checkCameraPermission()
        }

        btnDeletePhoto.setOnClickListener {
            currentPhotoBitmap = null
            hideUploadedPhoto()
        }

        btnAnalyze.setOnClickListener {
            analyzeWithAI()
        }

        btnFindProvider.setOnClickListener {
            suggestedService?.let {
                val intent = Intent(this, ProvidersListActivity::class.java).apply {
                    putExtra("SERVICE_NAME", it)
                    putExtra("LATITUDE", -12.0931)
                    putExtra("LONGITUDE", -77.0465)
                }
                startActivity(intent)
            }
        }

        btnNewAnalysis.setOnClickListener {
            resetToUpload()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                showImageSourceDialog()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Tomar foto", "Elegir de la galería")
        AlertDialog.Builder(this)
            .setTitle("Elige una imagen")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(takePictureIntent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun showUploadedPhoto() {
        photoPlaceholder.visibility = View.GONE
        ivUploadedPhoto.visibility = View.VISIBLE
        photoActions.visibility = View.VISIBLE
        ivUploadedPhoto.setImageBitmap(currentPhotoBitmap)
        btnAnalyze.isEnabled = true
    }

    private fun hideUploadedPhoto() {
        photoPlaceholder.visibility = View.VISIBLE
        ivUploadedPhoto.visibility = View.GONE
        photoActions.visibility = View.GONE
        btnAnalyze.isEnabled = false
    }

    private fun analyzeWithAI() {
        layoutUpload.visibility = View.GONE
        layoutAnalyzing.visibility = View.VISIBLE

        val description = etProblemDescription.text.toString()

        CoroutineScope(Dispatchers.IO).launch {
            val response = callGeminiAPI(description)

            withContext(Dispatchers.Main) {
                showResult(response)
            }
        }
    }

    private suspend fun callGeminiAPI(userDescription: String): String {
        return try {
            // URL actualizada a la versión 2.5-flash como solicitaste
            val url = URL("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val prompt = buildPrompt(userDescription)

            val jsonRequest = JSONObject()
            val contents = JSONArray()
            val content = JSONObject()
            val parts = JSONArray()

            if (currentPhotoBitmap != null) {
                val imageBase64 = bitmapToBase64(currentPhotoBitmap!!)
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mime_type", "image/jpeg")
                inlineData.put("data", imageBase64)
                imagePart.put("inlineData", inlineData)
                parts.put(imagePart)
            }

            val textPart = JSONObject()
            textPart.put("text", prompt)
            parts.put(textPart)

            content.put("parts", parts)
            contents.put(content)
            jsonRequest.put("contents", contents)

            val outputStream = connection.outputStream
            outputStream.write(jsonRequest.toString().toByteArray())
            outputStream.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }

                val jsonResponse = JSONObject(response)
                val candidates = jsonResponse.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")

                text
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Sin detalles"
                "Error $responseCode: $errorResponse"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun buildPrompt(userDescription: String): String {
        val serviceListString = servicesList.joinToString(", ")
        return """
            Analiza la imagen (si hay una) y la descripción: "$userDescription".
            
            Tienes esta lista de servicios disponibles: [$serviceListString].
            
            1. Si el problema se puede resolver con uno de los servicios de la lista, responde ÚNICAMENTE con el nombre exacto de ese servicio.
            2. Si el problema NO coincide con ninguno de la lista, responde DIRECTAMENTE con: "El servicio que necesitas es [tu sugerencia aquí]" (ej. "El servicio que necesitas es un veterinario").
        """.trimIndent()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun showResult(response: String) {
        layoutAnalyzing.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE

        val cleanResponse = response.trim().replace(".", "")

        // Verificamos si la respuesta es exactamente uno de nuestros servicios
        val matchedService = servicesList.firstOrNull { it.equals(cleanResponse, ignoreCase = true) }

        if (matchedService != null) {
            // Caso: Encontró un servicio de la lista
            tvAiResponse.text = "Basado en el análisis, hemos encontrado el servicio ideal para ti."
            
            suggestedService = matchedService
            tvSuggestedService.text = matchedService
            layoutSuggestedService.visibility = View.VISIBLE
            btnFindProvider.visibility = View.VISIBLE
        } else {
            // Caso: NO encontró match en la lista (o es un error), muestra lo que dijo la IA
            tvAiResponse.text = response
            
            suggestedService = null
            layoutSuggestedService.visibility = View.GONE
            btnFindProvider.visibility = View.GONE
        }
    }

    private fun resetToUpload() {
        layoutResult.visibility = View.GONE
        layoutUpload.visibility = View.VISIBLE
        currentPhotoBitmap = null
        hideUploadedPhoto()
        etProblemDescription.text.clear()
        suggestedService = null
        layoutSuggestedService.visibility = View.GONE
    }
}

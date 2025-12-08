package com.example.omg

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
    private lateinit var btnFindProvider: Button
    private lateinit var btnNewAnalysis: Button
    private lateinit var btnClose: ImageView
    private lateinit var btnCloseResult: ImageView

    private var currentPhotoBitmap: Bitmap? = null
    private val GEMINI_API_KEY = "AIzaSyDFKgdTpfM23dGuxzmLhiS1EPvHgsClsEg"

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
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
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
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
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(takePictureIntent)
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
            val url = URL("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY")
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

            android.util.Log.d("AiAssistant", "URL: ${url}")
            android.util.Log.d("AiAssistant", "Request: ${jsonRequest.toString()}")

            val outputStream = connection.outputStream
            outputStream.write(jsonRequest.toString().toByteArray())
            outputStream.close()

            val responseCode = connection.responseCode
            android.util.Log.d("AiAssistant", "Response code: $responseCode")

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
                android.util.Log.e("AiAssistant", "Error: $errorResponse")
                "Error $responseCode: $errorResponse"
            }
        } catch (e: Exception) {
            android.util.Log.e("AiAssistant", "Exception", e)
            "Error: ${e.message}"
        }
    }

    private fun buildPrompt(userDescription: String): String {
        return """
            Eres un asistente experto en diagnóstico de problemas del hogar y servicios técnicos.
            
            ${if (currentPhotoBitmap != null) "Analiza la imagen proporcionada y " else ""}
            ${if (userDescription.isNotEmpty()) "el usuario describe: \"$userDescription\"" else ""}
            
            Por favor, proporciona:
            1. Un diagnóstico claro del problema
            2. Posibles causas
            3. Nivel de urgencia (Bajo/Medio/Alto)
            4. Qué tipo de profesional necesita (electricista, gasfitero, etc.)
            5. Estimación de costo aproximado en soles peruanos
            
            Responde de forma clara, concisa y útil.
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
        tvAiResponse.text = response
    }

    private fun resetToUpload() {
        layoutResult.visibility = View.GONE
        layoutUpload.visibility = View.VISIBLE
        currentPhotoBitmap = null
        hideUploadedPhoto()
        etProblemDescription.text.clear()
    }
}
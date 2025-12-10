package com.example.omg

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.omg.model.Postulante
import com.example.omg.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale

class FormularioRegistroActivity : AppCompatActivity() {

    private lateinit var nombreEditText: EditText
    private lateinit var dniEditText: EditText
    private lateinit var celularEditText: EditText
    private lateinit var fechaNacimientoEditText: EditText
    private lateinit var correoEditText: EditText
    private lateinit var spinnerServicio: Spinner
    private lateinit var btnUploadLicencia: Button
    private lateinit var tvLicenciaStatus: TextView
    private lateinit var btnUploadCul: Button
    private lateinit var tvCulStatus: TextView
    private lateinit var btnSiguiente: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Variables para almacenar los Base64
    private var licenciaBase64: String? = null
    private var culBase64: String? = null

    private var isLicenciaUpload = true

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64String = uriToBase64(it)
            if (isLicenciaUpload) {
                licenciaBase64 = base64String
                tvLicenciaStatus.text = "Licencia cargada correctamente ✅"
                tvLicenciaStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            } else {
                culBase64 = base64String
                tvCulStatus.text = "CUL cargado correctamente ✅"
                tvCulStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario_registro)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()

        fechaNacimientoEditText.setOnClickListener { mostrarCalendario() }
        setupSpinner()

        btnUploadLicencia.setOnClickListener {
            isLicenciaUpload = true
            getContent.launch("image/*")
        }

        btnUploadCul.setOnClickListener {
            isLicenciaUpload = false
            getContent.launch("image/*")
        }

        setupSubmitButton()
    }

    private fun initViews() {
        nombreEditText = findViewById(R.id.nombreEditText)
        dniEditText = findViewById(R.id.dniEditText)
        celularEditText = findViewById(R.id.celularEditText)
        fechaNacimientoEditText = findViewById(R.id.fechaNacimientoEditText)
        correoEditText = findViewById(R.id.correoEditText)
        spinnerServicio = findViewById(R.id.spinnerServicio)
        btnUploadLicencia = findViewById(R.id.btnUploadLicencia)
        tvLicenciaStatus = findViewById(R.id.tvLicenciaStatus)
        btnUploadCul = findViewById(R.id.btnUploadCul)
        tvCulStatus = findViewById(R.id.tvCulStatus)
        btnSiguiente = findViewById(R.id.btnSiguiente)
    }

    private fun setupSpinner() {
        val servicios = arrayOf(
            "Seleccionar...",
            "Gasfitería",
            "Electricidad",
            "Limpieza",
            "Carpintería",
            "Pintura",
            "Albañilería",
            "Informática",
            "Otro"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, servicios)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServicio.adapter = adapter
    }

    private fun setupSubmitButton() {
        val apiService = RetrofitClient.instance

        btnSiguiente.setOnClickListener {
            val nombre = nombreEditText.text.toString().trim()
            val dni = dniEditText.text.toString().trim()
            val celular = celularEditText.text.toString().trim()
            val fecha = fechaNacimientoEditText.text.toString().trim()
            val correo = correoEditText.text.toString().trim()
            val servicio = spinnerServicio.selectedItem.toString()

            if (nombre.isEmpty() || dni.length != 8 || celular.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                Toast.makeText(this, "Completa los datos correctamente", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (servicio == "Seleccionar...") {
                Toast.makeText(this, "Selecciona un servicio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val postulante = Postulante(
                nombre = nombre,
                dni = dni,
                celular = celular,
                fecha_nacimiento = fecha,
                correo = correo,
                servicio = servicio,
                licencia_municipal_base64 = licenciaBase64,
                cul_base64 = culBase64
            )

            apiService.enviarDatos(postulante).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        // 1. Guardar localmente
                        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("correo_postulante", correo)
                            .putString("tipo_servicio_trabajador", servicio) 
                            .apply()

                        // 2. Guardar en Firestore (Respaldo para recuperar si cambia de dispositivo)
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            val data = hashMapOf("workerService" to servicio)
                            db.collection("users").document(uid)
                                .set(data, SetOptions.merge())
                        }

                        Toast.makeText(this@FormularioRegistroActivity, "Datos enviados correctamente", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@FormularioRegistroActivity, FaceValidationActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@FormularioRegistroActivity, "Error servidor: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@FormularioRegistroActivity, "Error conexión: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun mostrarCalendario() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val fechaSeleccionada = String.format(Locale.getDefault(), "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                fechaNacimientoEditText.setText(fechaSeleccionada)
            },
            year, month, day
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

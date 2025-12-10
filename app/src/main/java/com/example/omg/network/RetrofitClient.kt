package com.example.omg.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    
    // ⚠️ PEGA AQUÍ LA URL QUE TE DE NGROK (Cambia cada vez que reinicias ngrok)
    // Asegúrate de que termine con una barra "/"
    // Ejemplo: "https://tu-codigo-ngrok.ngrok-free.app/"
    private const val BASE_URL = "https://66cfa1e957e5.ngrok-free.app/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

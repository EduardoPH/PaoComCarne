package com.example.data.remote

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object SupabaseConfig {
    // Supabase project provided by user: host db.iodvkbzszlrphuqvywbr.supabase.co
    const val DEFAULT_SUPABASE_URL = "https://iodvkbzszlrphuqvywbr.supabase.co"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.dudunega1325"

    private const val PREFS_NAME = "supabase_config_prefs"
    private const val KEY_URL = "supabase_url"
    private const val KEY_KEY = "supabase_anon_key"

    fun getUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
    }

    fun getKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_KEY, DEFAULT_ANON_KEY) ?: DEFAULT_ANON_KEY
    }

    fun saveConfig(context: Context, url: String, key: String) {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, cleanUrl)
            .putString(KEY_KEY, key)
            .apply()
        SupabaseClient.rebuildClient(context)
    }
}

object SupabaseClient {
    private var service: SupabaseService? = null
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun getService(context: Context): SupabaseService {
        return service ?: synchronized(this) {
            service ?: buildService(context).also { service = it }
        }
    }

    fun rebuildClient(context: Context) {
        synchronized(this) {
            service = buildService(context)
        }
    }

    private fun buildService(context: Context): SupabaseService {
        var rawUrl = SupabaseConfig.getUrl(context)
        if (!rawUrl.endsWith("/")) rawUrl += "/"

        val authInterceptor = Interceptor { chain ->
            val key = SupabaseConfig.getKey(context)
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(rawUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(SupabaseService::class.java)
    }
}

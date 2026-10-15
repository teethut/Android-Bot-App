package com.teethut.twiprobot.data.datasource

import android.util.Log
import com.teethut.twiprobot.domain.model.PidSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.Locale

class RobotRestApi {
    private val tag = "RobotRestApi"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun getPidSettings(host: String = "192.168.4.1", port: Int = 80): Result<PidSettings> =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/get_pid"
                val request = Request.Builder().url(url).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                    }

                    val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                    val json = JSONObject(body)

                    val settings = PidSettings(
                        kp1 = json.optDouble("kp1", 40.0).toFloat(),
                        ki1 = json.optDouble("ki1", 0.1).toFloat(),
                        kd1 = json.optDouble("kd1", 0.0).toFloat(),
                        kp2 = json.optDouble("kp2", -0.0015).toFloat(),
                        ki2 = json.optDouble("ki2", 0.0).toFloat(),
                        kd2 = json.optDouble("kd2", 0.00002).toFloat(),
                        angleOffset = json.optDouble("ao", 6.0).toFloat(),
                        maxAccel = json.optDouble("ma", 40000.0).toFloat(),
                        maxStepsPerSec = json.optDouble("msps", 20000.0).toFloat(),
                        maxSteer = json.optDouble("mstr", 2500.0).toFloat(),
                        steerAccel = json.optDouble("sta", 8000.0).toFloat()
                    )
                    Result.success(settings)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to get PID settings: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun setPidSettings(
        settings: PidSettings,
        host: String = "192.168.4.1",
        port: Int = 80
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(port)
                .addPathSegment("set_pid")
                .addQueryParameter("kp1", String.format(Locale.US, "%.4f", settings.kp1))
                .addQueryParameter("ki1", String.format(Locale.US, "%.4f", settings.ki1))
                .addQueryParameter("kd1", String.format(Locale.US, "%.4f", settings.kd1))
                .addQueryParameter("kp2", String.format(Locale.US, "%.5f", settings.kp2))
                .addQueryParameter("ki2", String.format(Locale.US, "%.5f", settings.ki2))
                .addQueryParameter("kd2", String.format(Locale.US, "%.5f", settings.kd2))
                .addQueryParameter("ao", String.format(Locale.US, "%.2f", settings.angleOffset))
                .addQueryParameter("ma", String.format(Locale.US, "%.1f", settings.maxAccel))
                .addQueryParameter("msps", String.format(Locale.US, "%.1f", settings.maxStepsPerSec))
                .addQueryParameter("mstr", String.format(Locale.US, "%.1f", settings.maxSteer))
                .addQueryParameter("sta", String.format(Locale.US, "%.1f", settings.steerAccel))
                .build()

            val request = Request.Builder().url(urlBuilder).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed with status: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update PID settings: ${e.message}")
            Result.failure(e)
        }
    }
}


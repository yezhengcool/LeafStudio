package com.leafstudio.tvplayer.utils

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 激活管理器 - 通过云端API
 */
object ActivationManager {

    private const val TAG = "ActivationManager"

    // API 端点 - 通过 Cloudflare Worker 路由
    private const val API_URL = "https://yezheng.dpdns.org/api/activation.php"

    data class ActivationInfo(
        val isValid: Boolean,
        val remainingSeconds: Long,
        val expiryTime: Long,
        val message: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cache(null)  // 禁用缓存
        .build()
        
    fun getMachineCode(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val md5 = MessageDigest.getInstance("MD5")
            val digest = md5.digest(androidId.toByteArray())
            digest.joinToString("") { "%02x".format(it) }.substring(0, 16).uppercase()
        } catch (e: Exception) {
            e.printStackTrace()
            "UNKNOWN_DEVICE"
        }
    }

    suspend fun checkActivationStatus(context: Context): ActivationInfo = withContext(Dispatchers.IO) {
        val machineCode = getMachineCode(context)
        
        try {
            val requestJson = JSONObject().apply {
                put("action", "check")
                put("machineCode", machineCode)
            }

            android.util.Log.d(TAG, "Request URL: $API_URL")
            android.util.Log.d(TAG, "Request Body: $requestJson")

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            android.util.Log.d(TAG, "Request Method: ${request.method}")
            android.util.Log.d(TAG, "Request Headers: ${request.headers}")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            android.util.Log.d(TAG, "Response Code: ${response.code}")
            android.util.Log.d(TAG, "Response Body: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("API错误: ${response.code} - $responseBody")
            }

            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                throw Exception(result.getString("error"))
            }

            return@withContext ActivationInfo(
                isValid = result.getBoolean("isValid"),
                remainingSeconds = result.getLong("remainingSeconds"),
                expiryTime = result.getLong("expiryTime"),
                message = result.getString("message")
            )

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e(TAG, "Activation check failed", e)
            return@withContext ActivationInfo(false, 0, 0, "网络错误: ${e.message}")
        }
    }

    suspend fun activateWithCode(context: Context, code: String): String = withContext(Dispatchers.IO) {
        val machineCode = getMachineCode(context)

        try {
            val requestJson = JSONObject().apply {
                put("action", "activate")
                put("machineCode", machineCode)
                put("activationCode", code)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("API错误: ${response.code} - $responseBody")
            }

            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                return@withContext "激活失败: ${result.getString("error")}"
            }

            return@withContext result.getString("message")

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "激活失败: ${e.message}"
        }
    }
}

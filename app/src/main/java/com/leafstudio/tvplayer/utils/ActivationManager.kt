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
        
    /**
     * 获取机器码 - 使用设备硬件信息，不受应用签名影响
     * 
     * Android 8.0+ 中，ANDROID_ID 会根据应用签名生成不同的值
     * 为了确保同一设备使用不同签名的APK时机器码保持一致，
     * 我们使用设备硬件信息的组合来生成机器码（不使用ANDROID_ID）
     */
    @Suppress("DEPRECATION")
    fun getMachineCode(context: Context): String {
        return try {
            // 收集设备硬件信息（这些信息不受应用签名影响）
            val deviceInfo = buildString {
                // 主板名称
                append(android.os.Build.BOARD)
                append("|")
                // 品牌
                append(android.os.Build.BRAND)
                append("|")
                // 设备名
                append(android.os.Build.DEVICE)
                append("|")
                // 硬件名称
                append(android.os.Build.HARDWARE)
                append("|")
                // 制造商
                append(android.os.Build.MANUFACTURER)
                append("|")
                // 产品名称
                append(android.os.Build.PRODUCT)
                append("|")
                // 型号
                append(android.os.Build.MODEL)
                append("|")
                
                // 设备序列号（Android 8.0以下不需要权限）
                try {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        append(android.os.Build.SERIAL)
                        append("|")
                    }
                } catch (_: Exception) {
                    // Android 8.0+ 需要READ_PHONE_STATE权限，我们不使用
                }
                
                // WiFi Mac地址（尝试获取，不强制要求）
                // 注意：Android 10+ 会返回随机化的Mac地址
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    if (wifiManager != null) {
                        val wifiInfo = wifiManager.connectionInfo
                        val macAddress = wifiInfo?.macAddress
                        if (macAddress != null && macAddress != "02:00:00:00:00:00") {
                            append(macAddress)
                            append("|")
                        }
                    }
                } catch (_: Exception) {
                    // 无法获取Mac地址，不影响继续
                }
            }
            
            android.util.Log.d(TAG, "Device Info for Machine Code: $deviceInfo")
            
            // 对组合的设备信息进行MD5加密
            val md5 = MessageDigest.getInstance("MD5")
            val digest = md5.digest(deviceInfo.toByteArray())
            
            // 取前16位并转为大写
            val machineCode = digest.joinToString("") { "%02x".format(it) }.substring(0, 16).uppercase()
            android.util.Log.d(TAG, "Generated Machine Code: $machineCode")
            
            machineCode
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e(TAG, "Failed to generate machine code", e)
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

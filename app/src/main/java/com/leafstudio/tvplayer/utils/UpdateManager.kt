package com.leafstudio.tvplayer.utils

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateManager {
    // 使用用户提供的 URL，假设是目录则追加 version.json，如果是文件则直接使用
    // 这里根据用户习惯，通常提供的目录 URL 下会有 version.json
    private const val UPDATE_URL = "https://yezheng.dpdns.org/tv/update/version.json"
    
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val description: String,
        val forceUpdate: Boolean
    )

    /**
     * 检查更新
     * @param context 上下文
     * @param isManual 是否手动检查（手动检查时，无更新也会提示）
     */
    fun checkUpdate(context: Context, isManual: Boolean = false) {
        if (isManual) {
            Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(UPDATE_URL)
                    .header("User-Agent", "LeafStudio TVPlayer")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (jsonStr != null) {
                        try {
                            val json = JSONObject(jsonStr)
                            val updateInfo = UpdateInfo(
                                versionCode = json.optInt("versionCode"),
                                versionName = json.optString("versionName"),
                                downloadUrl = json.optString("downloadUrl"),
                                description = json.optString("description", "发现新版本，请更新"),
                                forceUpdate = json.optBoolean("forceUpdate", false)
                            )

                            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                            val currentVersionCode = packageInfo.versionCode

                            Handler(Looper.getMainLooper()).post {
                                if (updateInfo.versionCode > currentVersionCode) {
                                    showUpdateDialog(context, updateInfo)
                                } else if (isManual) {
                                    Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (isManual) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "解析更新信息失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    if (isManual) {
                         Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "检查更新失败: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isManual) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "检查更新出错: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        try {
            // 检查用户是否选择了"下次不再弹出"
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val skipUpdate = prefs.getBoolean("skip_update_prompt", false)
            
            // 如果用户之前选择了不再弹出且不是强制更新，则跳过
            if (skipUpdate && !updateInfo.forceUpdate) {
                return
            }
            
            // 创建自定义布局
            val dialogView = android.view.LayoutInflater.from(context).inflate(
                android.R.layout.select_dialog_multichoice, null
            )
            val checkBox = android.widget.CheckBox(context)
            checkBox.text = "下次不再弹出"
            checkBox.setTextColor(android.graphics.Color.WHITE)
            checkBox.setPadding(50, 20, 50, 20)
            
            // 创建包含消息和复选框的布局
            val layout = android.widget.LinearLayout(context)
            layout.orientation = android.widget.LinearLayout.VERTICAL
            layout.setPadding(50, 30, 50, 10)
            
            val messageView = android.widget.TextView(context)
            messageView.text = updateInfo.description
            messageView.setTextColor(android.graphics.Color.WHITE)
            messageView.textSize = 16f
            
            layout.addView(messageView)
            
            // 只在非强制更新时显示复选框
            if (!updateInfo.forceUpdate) {
                layout.addView(checkBox)
            }
            
            val builder = AlertDialog.Builder(context)
                .setTitle("发现新版本: ${updateInfo.versionName}")
                .setView(layout)
                .setPositiveButton("立即更新") { _, _ ->
                    // 保存用户选择
                    if (!updateInfo.forceUpdate && checkBox.isChecked) {
                        prefs.edit().putBoolean("skip_update_prompt", true).apply()
                    }
                    downloadApk(context, updateInfo.downloadUrl)
                }
            
            if (updateInfo.forceUpdate) {
                builder.setCancelable(false)
            } else {
                builder.setNegativeButton("稍后") { dialog, _ ->
                    // 保存用户选择
                    if (checkBox.isChecked) {
                        prefs.edit().putBoolean("skip_update_prompt", true).apply()
                    }
                    dialog.dismiss()
                }
                builder.setCancelable(true)
            }
            
            builder.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadApk(context: Context, url: String) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setTitle("正在下载")
        progressDialog.setMessage("请稍候...")
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.max = 100
        progressDialog.setCancelable(false)
        progressDialog.show()

        Thread {
            try {
                android.util.Log.d("UpdateManager", "开始下载: $url")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                    
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LeafStudio TVPlayer")
                    .build()
                    
                val response = client.newCall(request).execute()
                
                android.util.Log.d("UpdateManager", "响应码: ${response.code}")
                
                if (response.isSuccessful) {
                    val body = response.body
                    val contentLength = body?.contentLength() ?: 0
                    val inputStream = body?.byteStream()
                    
                    android.util.Log.d("UpdateManager", "文件大小: $contentLength bytes")
                    
                    // 下载到外部私有目录，不需要申请权限
                    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                    if (file.exists()) {
                        file.delete()
                    }
                    
                    val outputStream = FileOutputStream(file)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0
                    
                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            Handler(Looper.getMainLooper()).post {
                                progressDialog.progress = progress
                                progressDialog.setMessage("已下载: ${totalBytesRead / 1024}KB / ${contentLength / 1024}KB")
                            }
                        }
                    }
                    
                    outputStream.flush()
                    outputStream.close()
                    inputStream?.close()
                    
                    android.util.Log.d("UpdateManager", "下载完成: ${file.absolutePath}")
                    
                    Handler(Looper.getMainLooper()).post {
                        progressDialog.dismiss()
                        installApk(context, file)
                    }
                } else {
                    val errorMsg = "下载失败\nHTTP ${response.code}: ${response.message}\nURL: $url"
                    android.util.Log.e("UpdateManager", errorMsg)
                    
                    Handler(Looper.getMainLooper()).post {
                        progressDialog.dismiss()
                        
                        // 显示详细的错误对话框
                        AlertDialog.Builder(context)
                            .setTitle("下载失败")
                            .setMessage("HTTP 错误码: ${response.code}\n${response.message}\n\n请检查:\n1. 文件是否已上传到服务器\n2. URL 是否正确\n3. 服务器是否可访问\n\n下载地址:\n$url")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("UpdateManager", "下载异常: ${e.message}", e)
                
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    
                    AlertDialog.Builder(context)
                        .setTitle("下载出错")
                        .setMessage("错误信息: ${e.message}\n\n下载地址:\n$url")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun installApk(context: Context, file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

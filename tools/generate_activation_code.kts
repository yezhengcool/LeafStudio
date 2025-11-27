#!/usr/bin/env kotlin

/**
 * LeafStudio 激活码生成器
 * 
 * 使用方法:
 * kotlinc -script generate_activation_code.kts <机器码> <天数>
 * 
 * 示例:
 * kotlinc -script generate_activation_code.kts A1B2C3D4E5F6G7H8 30
 */

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

// 密钥 - 必须与 App 中的密钥一致
const val SECRET_KEY = "LeafStudio2024!@"

/**
 * 生成激活码
 */
fun generateActivationCode(machineCode: String, expiryDate: String): String {
    try {
        // 将机器码和过期时间组合
        val data = "$machineCode|$expiryDate"
        
        // 使用 AES 加密
        val key = SECRET_KEY.padEnd(16, '0').substring(0, 16)
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(data.toByteArray())
        
        // 转换为 Base64
        val base64 = java.util.Base64.getEncoder().encodeToString(encrypted)
        
        // 格式化为 XXXX-XXXX-XXXX-XXXX 格式
        return formatActivationCode(base64)
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }
}

/**
 * 格式化激活码
 */
fun formatActivationCode(code: String): String {
    val cleaned = code.replace(Regex("[^A-Za-z0-9]"), "")
    val formatted = StringBuilder()
    
    for (i in cleaned.indices) {
        if (i > 0 && i % 4 == 0) {
            formatted.append("-")
        }
        formatted.append(cleaned[i])
    }
    
    return formatted.toString().uppercase()
}

/**
 * 生成指定天数后的激活码
 */
fun generateActivationCodeByDays(machineCode: String, days: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, days)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val expiryDate = sdf.format(calendar.time)
    
    return generateActivationCode(machineCode, expiryDate)
}

// 主程序
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("使用方法: kotlinc -script generate_activation_code.kts <机器码> <天数>")
        println("示例: kotlinc -script generate_activation_code.kts A1B2C3D4E5F6G7H8 30")
        return
    }
    
    val machineCode = args[0]
    val days = args[1].toIntOrNull() ?: 30
    
    println("=" * 50)
    println("LeafStudio 激活码生成器")
    println("=" * 50)
    println("机器码: $machineCode")
    println("有效期: $days 天")
    println("-" * 50)
    
    val activationCode = generateActivationCodeByDays(machineCode, days)
    
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, days)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val expiryDate = sdf.format(calendar.time)
    
    println("激活码: $activationCode")
    println("过期时间: $expiryDate")
    println("=" * 50)
}

// 运行主程序
main(args)

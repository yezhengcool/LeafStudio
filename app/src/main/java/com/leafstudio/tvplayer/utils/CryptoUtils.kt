package com.leafstudio.tvplayer.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * URL 加密/解密工具
 * 用于保护 M3U 文件中的播放地址
 */
object CryptoUtils {

    // 密钥（32字节 = 256位）- 请修改为您自己的密钥
    private const val SECRET_KEY = "LeafStudio2024SecretKey12345"
    
    // 初始化向量（16字节）
    private const val IV = "1234567890123456"

    /**
     * AES 加密
     */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(IV.toByteArray())
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray())
            
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // 加密失败返回原文
        }
    }

    /**
     * AES 解密
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(IV.toByteArray())
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
            val decrypted = cipher.doFinal(encrypted)
            
            String(decrypted)
        } catch (e: Exception) {
            encryptedText // 解密失败返回原文（可能本身就是明文）
        }
    }

    /**
     * 判断字符串是否为 Base64 编码（可能是加密的）
     */
    fun isEncrypted(text: String): Boolean {
        return try {
            // 简单判断：Base64 字符串不包含特殊字符
            text.matches(Regex("^[A-Za-z0-9+/=]+$")) && text.length > 20
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 智能解密：自动判断是否需要解密
     */
    fun smartDecrypt(text: String): String {
        return if (isEncrypted(text) && !text.startsWith("http")) {
            decrypt(text)
        } else {
            text
        }
    }
}

package com.leafstudio.tvplayer.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * 设备类型检测工具
 */
object DeviceUtils {
    
    /**
     * 检测是否为 TV 设备
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    
    /**
     * 检测是否有触摸屏
     */
    fun hasTouchScreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }
}

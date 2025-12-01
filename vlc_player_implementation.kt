/**
 * 初始化 VLC 播放器（用于软解）
 */
private fun initializeVLCPlayer(currentUrl: String) {
    try {
        android.util.Log.d("PlaybackActivity", "VLC: 开始初始化, URL=$currentUrl")
        
        // 隐藏 ExoPlayer 和 IJKPlayer 容器
        binding.playerView.visibility = View.GONE
        binding.playerView.player = null
        binding.flIjkContainer.visibility = View.GONE
        binding.flIjkContainer.removeAllViews()
        
        // 显示 VLC 容器（使用 IJK 容器复用）
        binding.flIjkContainer.visibility = View.VISIBLE
        
        // 创建 TextureView for VLC
        val textureView = android.view.TextureView(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        params.gravity = android.view.Gravity.CENTER
        textureView.layoutParams = params
        binding.flIjkContainer.addView(textureView)
        
        // 初始化 libVLC
        val options = ArrayList<String>()
        options.add("--aout=opensles")
        options.add("--audio-time-stretch") // 音频时间拉伸
        options.add("-vvv") // 详细日志
        
        libVLC = org.videolan.libvlc.LibVLC(this, options)
        vlcMediaPlayer = org.videolan.libvlc.MediaPlayer(libVLC)
        
        // 设置视频输出
        textureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                vlcMediaPlayer?.let { player ->
                    val vlcVout = player.vlcVout
                    if (!vlcVout.areViewsAttached()) {
                        vlcVout.setVideoSurface(android.view.Surface(surface), null)
                        vlcVout.attachViews()
                    }
                }
            }
            
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                vlcMediaPlayer?.vlcVout?.setWindowSize(width, height)
            }
            
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                vlcMediaPlayer?.vlcVout?.detachViews()
                return true
            }
            
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
        
        // 创建媒体
        val media = org.videolan.libvlc.Media(libVLC, android.net.Uri.parse(currentUrl))
        media.setHWDecoderEnabled(false, false) // 强制软解
        
        vlcMediaPlayer?.media = media
        
        // 设置事件监听
        vlcMediaPlayer?.setEventListener { event ->
            runOnUiThread {
                when (event.type) {
                    org.videolan.libvlc.MediaPlayer.Event.Playing -> {
                        android.util.Log.d("PlaybackActivity", "VLC: 开始播放")
                        binding.ivLoadingBackground.visibility = View.GONE
                        hasTriedAllRoutes = false
                    }
                    org.videolan.libvlc.MediaPlayer.Event.EncounteredError -> {
                        android.util.Log.e("PlaybackActivity", "VLC: 播放错误")
                       Toast.makeText(this, "VLC播放错误，尝试切换线路", Toast.LENGTH_SHORT).show()
                        handlePlaybackError(null)
                    }
                    org.videolan.libvlc.MediaPlayer.Event.EndReached -> {
                        android.util.Log.d("PlaybackActivity", "VLC: 播放结束")
                        finish()
                    }
                    org.videolan.libvlc.MediaPlayer.Event.Buffering -> {
                        val percent = event.buffering
                        if (percent < 100f) {
                            binding.ivLoadingBackground.visibility = View.VISIBLE
                        } else {
                            binding.ivLoadingBackground.visibility = View.GONE
                        }
                    }
                }
            }
        }
        
        // 延迟播放，确保 Surface 就绪
        textureView.post {
            vlcMediaPlayer?.play()
        }
        
        media.release()
        
    } catch (e: Exception) {
        e.printStackTrace()
        runOnUiThread {
            Toast.makeText(this, "VLC初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            // 切换回系统解码
            currentDecoderType = 0
            updateDecoderButtonText()
            releasePlayer()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                initializePlayer()
            }, 300)
        }
    }
}

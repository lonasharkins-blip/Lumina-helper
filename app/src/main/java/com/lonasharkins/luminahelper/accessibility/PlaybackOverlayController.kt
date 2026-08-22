package com.lonasharkins.luminahelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lonasharkins.luminahelper.model.ScreenPoint
import com.lonasharkins.luminahelper.playback.PreparedPlayback

internal class PlaybackOverlayController(
    private val service: AccessibilityService,
    private val dispatchChord: (Collection<ScreenPoint>, Long) -> Boolean,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var activeView: View? = null
    private var engine: MidiPlaybackEngine? = null

    fun show(playback: PreparedPlayback): Boolean {
        dismiss()
        return runCatching {
            showControls(playback)
            true
        }.getOrElse {
            dismiss()
            false
        }
    }

    fun dismiss() {
        engine?.release()
        engine = null
        activeView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        activeView = null
    }

    private fun showControls(playback: PreparedPlayback) {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(9))
            background = roundedBackground(Color.argb(242, 25, 19, 37), dp(16).toFloat())
            elevation = dp(10).toFloat()
        }
        val title = TextView(service).apply {
            text = playback.songName
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = "${playback.songName}. Arraste para mover os controles"
            setPadding(dp(6), dp(3), dp(6), dp(5))
        }
        val details = TextView(service).apply {
            val transpose = if (playback.transposeSemitones >= 0) {
                "+${playback.transposeSemitones}"
            } else {
                playback.transposeSemitones.toString()
            }
            text = "${playback.profileName} • ${playback.speedPercent}% • $transpose semitons"
            setTextColor(Color.rgb(182, 156, 255))
            textSize = 11f
            setPadding(dp(6), 0, dp(6), dp(4))
        }
        val status = TextView(service).apply {
            text = "Pronto — toque em Iniciar"
            setTextColor(Color.rgb(113, 230, 209))
            textSize = 12f
            setPadding(dp(6), 0, dp(6), dp(5))
        }
        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val playButton = Button(service).apply {
            text = "Iniciar"
            isAllCaps = false
            contentDescription = "Iniciar reprodução"
        }
        val pauseButton = Button(service).apply {
            text = "Pausar"
            isAllCaps = false
            isEnabled = false
            contentDescription = "Pausar reprodução"
        }
        val stopButton = Button(service).apply {
            text = "Parar"
            isAllCaps = false
            contentDescription = "Parar e fechar controles"
        }

        fun renderState(state: PlaybackState) {
            when (state) {
                PlaybackState.READY -> {
                    status.text = "Pronto — toque em Iniciar"
                    playButton.text = "Iniciar"
                    playButton.isEnabled = true
                    pauseButton.isEnabled = false
                }

                PlaybackState.PLAYING -> {
                    status.text = "Tocando"
                    playButton.isEnabled = false
                    pauseButton.isEnabled = true
                }

                PlaybackState.PAUSED -> {
                    status.text = "Pausado"
                    playButton.text = "Continuar"
                    playButton.isEnabled = true
                    pauseButton.isEnabled = false
                }

                PlaybackState.COMPLETED -> {
                    status.text = "Concluído"
                    playButton.text = "Repetir"
                    playButton.isEnabled = true
                    pauseButton.isEnabled = false
                }

                PlaybackState.STOPPED -> {
                    status.text = "Parado"
                    playButton.isEnabled = false
                    pauseButton.isEnabled = false
                }
            }
            status.contentDescription = status.text
        }

        engine = MidiPlaybackEngine(
            plan = playback.plan,
            onTouchEvent = { event -> dispatchChord(event.points, event.touchDurationMs) },
            onStateChanged = ::renderState,
        )
        playButton.setOnClickListener { engine?.play() }
        pauseButton.setOnClickListener { engine?.pause() }
        stopButton.setOnClickListener {
            engine?.stop()
            dismiss()
            Toast.makeText(service, "Reprodução encerrada", Toast.LENGTH_SHORT).show()
        }

        root.addView(title, rowParams())
        root.addView(details, rowParams())
        root.addView(status, rowParams())
        fun buttonParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        controls.addView(playButton, buttonParams())
        controls.addView(pauseButton, buttonParams())
        controls.addView(stopButton, buttonParams())
        root.addView(controls, rowParams())

        val params = WindowManager.LayoutParams(
            dp(310),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(90)
        }
        makeDraggable(title, root, params)
        windowManager.addView(root, params)
        activeView = root
        renderState(PlaybackState.READY)
    }

    private fun makeDraggable(
        handle: View,
        root: View,
        params: WindowManager.LayoutParams,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceAtLeast(0)
                    params.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }

                else -> false
            }
        }
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}

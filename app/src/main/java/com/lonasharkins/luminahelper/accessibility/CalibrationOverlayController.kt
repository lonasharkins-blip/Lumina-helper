package com.lonasharkins.luminahelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lonasharkins.luminahelper.calibration.ProfileCalibrator
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.ScreenPoint

internal class CalibrationOverlayController(
    private val service: AccessibilityService,
    private val onProfileSaved: (InstrumentProfile) -> Unit,
    private val onCancelled: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var activeView: View? = null

    fun prepare(profile: InstrumentProfile): Boolean {
        removeActiveView()
        return runCatching {
            showLauncher(profile)
            true
        }.getOrElse {
            removeActiveView()
            false
        }
    }

    fun dismiss() {
        removeActiveView()
    }

    private fun showLauncher(profile: InstrumentProfile) {
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(Color.argb(238, 29, 22, 43), dp(18).toFloat())
            elevation = dp(8).toFloat()
        }

        val startButton = Button(service).apply {
            text = "Mapear ${profile.keys.size} teclas"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setOnClickListener {
                runCatching { showCalibration(profile) }
                    .onFailure {
                        removeActiveView()
                        onCancelled()
                    }
            }
        }
        val cancelButton = Button(service).apply {
            text = "×"
            contentDescription = "Cancelar mapeamento"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setOnClickListener {
                removeActiveView()
                onCancelled()
            }
        }

        container.addView(
            startButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            cancelButton,
            LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val params = overlayParams(
            width = ViewGroup.LayoutParams.WRAP_CONTENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.END,
        ).apply {
            x = dp(12)
            y = dp(80)
        }
        windowManager.addView(container, params)
        activeView = container
    }

    private fun showCalibration(profile: InstrumentProfile) {
        removeActiveView()

        val root = FrameLayout(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        lateinit var saveButton: Button
        lateinit var instruction: TextView

        val touchView = CalibrationTouchView(
            service = service,
            keyCount = profile.keys.size,
            onProgressChanged = { markedCount ->
                val newInstruction = instructionText(markedCount, profile.keys.size)
                instruction.text = newInstruction
                instruction.contentDescription = newInstruction
                saveButton.isEnabled = markedCount == profile.keys.size
            },
        )
        root.addView(
            touchView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        instruction = TextView(service).apply {
            text = instructionText(0, profile.keys.size)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.argb(232, 20, 16, 31), dp(16).toFloat())
            isClickable = true
            contentDescription = text
        }
        root.addView(
            instruction,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                setMargins(dp(16), dp(24), dp(16), 0)
            },
        )

        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(Color.argb(238, 20, 16, 31), dp(16).toFloat())
            isClickable = true
        }
        val cancelButton = Button(service).apply {
            text = "Cancelar"
            isAllCaps = false
            setOnClickListener {
                removeActiveView()
                onCancelled()
            }
        }
        val undoButton = Button(service).apply {
            text = "Desfazer"
            isAllCaps = false
            setOnClickListener { touchView.undoLast() }
        }
        saveButton = Button(service).apply {
            text = "Salvar"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                val positions = touchView.positionsSnapshot()
                if (positions.size != profile.keys.size) return@setOnClickListener

                val calibrated = ProfileCalibrator.applyPositions(profile, positions)
                removeActiveView()
                onProfileSaved(calibrated)
            }
        }

        fun buttonParams() = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        )
        controls.addView(cancelButton, buttonParams())
        controls.addView(undoButton, buttonParams())
        controls.addView(saveButton, buttonParams())
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                setMargins(dp(12), 0, dp(12), dp(20))
            },
        )

        val params = overlayParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT,
            gravity = Gravity.TOP or Gravity.START,
        )
        windowManager.addView(root, params)
        activeView = root
    }

    private fun instructionText(markedCount: Int, total: Int): String = when {
        markedCount >= total -> "Confira os pontos e toque em Salvar"
        else -> "Toque no centro da tecla ${markedCount + 1} de $total"
    }

    private fun overlayParams(width: Int, height: Int, gravity: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
    }

    private fun removeActiveView() {
        activeView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        activeView = null
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}

private class CalibrationTouchView(
    service: AccessibilityService,
    private val keyCount: Int,
    private val onProgressChanged: (Int) -> Unit,
) : View(service) {
    private val positions = mutableListOf<ScreenPoint>()
    private val density = resources.displayMetrics.density
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 124, 77, 255)
        style = Paint.Style.FILL
    }
    private val markerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val markerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 16f * density
        positions.forEachIndexed { index, point ->
            val x = point.x * width
            val y = point.y * height
            canvas.drawCircle(x, y, radius, markerFill)
            canvas.drawCircle(x, y, radius, markerBorder)
            val baseline = y - ((markerText.ascent() + markerText.descent()) / 2f)
            canvas.drawText("${index + 1}", x, baseline, markerText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && positions.size < keyCount) {
            performClick()
            if (width > 0 && height > 0) {
                positions += ScreenPoint(
                    x = (event.x / width).coerceIn(0f, 1f),
                    y = (event.y / height).coerceIn(0f, 1f),
                )
                onProgressChanged(positions.size)
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (oldWidth > 0 && oldHeight > 0 && (width != oldWidth || height != oldHeight)) {
            positions.clear()
            onProgressChanged(0)
        }
    }

    fun undoLast() {
        if (positions.isEmpty()) return
        positions.removeAt(positions.lastIndex)
        onProgressChanged(positions.size)
        invalidate()
    }

    fun positionsSnapshot(): List<ScreenPoint> = positions.toList()
}

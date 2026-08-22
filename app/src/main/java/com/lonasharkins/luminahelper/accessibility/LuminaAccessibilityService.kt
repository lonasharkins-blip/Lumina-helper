package com.lonasharkins.luminahelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.lonasharkins.luminahelper.model.ScreenPoint
import java.lang.ref.WeakReference

class LuminaAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        private var activeService: WeakReference<LuminaAccessibilityService>? = null

        fun isConnected(): Boolean = activeService?.get() != null

        fun tap(point: ScreenPoint, durationMs: Long = 40L): Boolean =
            activeService?.get()?.dispatchTap(point, durationMs) ?: false

        fun tapChord(points: Collection<ScreenPoint>, durationMs: Long = 40L): Boolean =
            activeService?.get()?.dispatchChord(points, durationMs) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    private fun dispatchTap(point: ScreenPoint, durationMs: Long): Boolean =
        dispatchChord(listOf(point), durationMs)

    private fun dispatchChord(points: Collection<ScreenPoint>, durationMs: Long): Boolean {
        if (points.isEmpty()) return false

        val (screenWidth, screenHeight) = screenSize()
        val safeDuration = durationMs.coerceIn(1L, 1_000L)
        val builder = GestureDescription.Builder()

        points.distinct().forEach { point ->
            val path = Path().apply {
                moveTo(point.x * screenWidth, point.y * screenHeight)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0L, safeDuration))
        }

        return dispatchGesture(builder.build(), null, null)
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }

        @Suppress("DEPRECATION")
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }
}


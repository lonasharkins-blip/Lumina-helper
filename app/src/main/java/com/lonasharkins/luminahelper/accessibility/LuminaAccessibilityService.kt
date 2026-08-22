package com.lonasharkins.luminahelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.lonasharkins.luminahelper.model.ScreenPoint
import com.lonasharkins.luminahelper.music.KeyLayoutFactory
import com.lonasharkins.luminahelper.storage.InstrumentProfileRepository
import java.lang.ref.WeakReference
import java.util.UUID

class LuminaAccessibilityService : AccessibilityService() {
    private lateinit var profileRepository: InstrumentProfileRepository
    private lateinit var calibrationOverlay: CalibrationOverlayController

    companion object {
        @Volatile
        private var activeService: WeakReference<LuminaAccessibilityService>? = null

        fun isConnected(): Boolean = activeService?.get() != null

        fun tap(point: ScreenPoint, durationMs: Long = 40L): Boolean =
            activeService?.get()?.dispatchTap(point, durationMs) ?: false

        fun tapChord(points: Collection<ScreenPoint>, durationMs: Long = 40L): Boolean =
            activeService?.get()?.dispatchChord(points, durationMs) ?: false

        fun prepareCalibration(name: String, keyCount: Int): Boolean =
            activeService?.get()?.beginCalibration(name, keyCount) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        profileRepository = InstrumentProfileRepository(this)
        calibrationOverlay = CalibrationOverlayController(
            service = this,
            onProfileSaved = { profile ->
                profileRepository.save(profile)
                Toast.makeText(
                    this,
                    "Perfil ${profile.name} salvo com ${profile.keys.size} teclas",
                    Toast.LENGTH_LONG,
                ).show()
            },
            onCancelled = {
                Toast.makeText(this, "Mapeamento cancelado", Toast.LENGTH_SHORT).show()
            },
        )
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        if (::calibrationOverlay.isInitialized) calibrationOverlay.dismiss()
    }

    override fun onDestroy() {
        if (::calibrationOverlay.isInitialized) calibrationOverlay.dismiss()
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    private fun beginCalibration(name: String, keyCount: Int): Boolean {
        val safeName = name.trim()
        if (safeName.isEmpty() || keyCount !in 1..88 || !::calibrationOverlay.isInitialized) {
            return false
        }

        val profile = KeyLayoutFactory.centeredChromatic(
            id = UUID.randomUUID().toString(),
            name = safeName,
            keyCount = keyCount,
        )
        return calibrationOverlay.prepare(profile)
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

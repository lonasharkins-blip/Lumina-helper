package com.lonasharkins.luminahelper.accessibility

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lonasharkins.luminahelper.playback.PlaybackPlan
import com.lonasharkins.luminahelper.playback.PlaybackTouchEvent

internal enum class PlaybackState {
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    STOPPED,
}

internal class MidiPlaybackEngine(
    private val plan: PlaybackPlan,
    private val onTouchEvent: (PlaybackTouchEvent) -> Unit,
    private val onStateChanged: (PlaybackState) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var state = PlaybackState.READY
    private var nextEventIndex = 0
    private var elapsedBeforePlayMs = 0L
    private var playStartedAtMs = 0L

    private val eventRunnable = Runnable(::dispatchNextEvent)
    private val completionRunnable = Runnable(::complete)

    fun play() {
        if (state == PlaybackState.PLAYING || state == PlaybackState.STOPPED) return
        if (state == PlaybackState.COMPLETED) resetPosition()
        if (plan.events.isEmpty()) {
            complete()
            return
        }

        playStartedAtMs = SystemClock.uptimeMillis() - elapsedBeforePlayMs
        changeState(PlaybackState.PLAYING)
        scheduleNext()
    }

    fun pause() {
        if (state != PlaybackState.PLAYING) return
        elapsedBeforePlayMs = currentElapsedMs()
        removeCallbacks()
        changeState(PlaybackState.PAUSED)
    }

    fun stop() {
        removeCallbacks()
        resetPosition()
        changeState(PlaybackState.STOPPED)
    }

    fun release() {
        removeCallbacks()
        state = PlaybackState.STOPPED
    }

    private fun scheduleNext() {
        if (state != PlaybackState.PLAYING) return
        val elapsed = currentElapsedMs()
        val nextEvent = plan.events.getOrNull(nextEventIndex)
        if (nextEvent == null) {
            val remaining = (plan.durationMs - elapsed).coerceAtLeast(0L)
            if (remaining == 0L) complete() else handler.postDelayed(completionRunnable, remaining)
            return
        }

        handler.postDelayed(eventRunnable, (nextEvent.atMs - elapsed).coerceAtLeast(0L))
    }

    private fun dispatchNextEvent() {
        if (state != PlaybackState.PLAYING) return
        val event = plan.events.getOrNull(nextEventIndex) ?: return scheduleNext()
        nextEventIndex++
        onTouchEvent(event)
        scheduleNext()
    }

    private fun complete() {
        if (state == PlaybackState.STOPPED) return
        removeCallbacks()
        elapsedBeforePlayMs = plan.durationMs
        changeState(PlaybackState.COMPLETED)
    }

    private fun resetPosition() {
        nextEventIndex = 0
        elapsedBeforePlayMs = 0L
        playStartedAtMs = 0L
    }

    private fun currentElapsedMs(): Long =
        (SystemClock.uptimeMillis() - playStartedAtMs).coerceIn(0L, plan.durationMs)

    private fun removeCallbacks() {
        handler.removeCallbacks(eventRunnable)
        handler.removeCallbacks(completionRunnable)
    }

    private fun changeState(newState: PlaybackState) {
        state = newState
        onStateChanged(newState)
    }
}

package com.nichat

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object Animation {
    @JvmStatic
    fun linear(elapsedNanos: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        val durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMs)
        return (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    @JvmStatic
    fun progress(elapsedNanos: Long, durationMs: Long): Float = smoothStep(linear(elapsedNanos, durationMs))

    @JvmStatic
    fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    @JvmStatic
    fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    @JvmStatic
    fun scaleColorAlpha(color: Int, alphaMultiplier: Float): Int {
        val scaledAlpha = (((color ushr 24) and 0xFF) * alphaMultiplier.coerceIn(0f, 1f)).roundToInt()
        return (color and 0x00FFFFFF) or (scaledAlpha.coerceIn(0, 255) shl 24)
    }
}

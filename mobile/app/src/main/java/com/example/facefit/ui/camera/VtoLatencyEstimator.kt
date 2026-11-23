package com.example.facefit.ui.camera

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.annotation.TargetApi
import androidx.core.content.ContextCompat

/** Main-thread timing estimates. A slowdown enters the current window before the EMA catches up. */
internal class VtoLatencyEstimator {
    data class Snapshot(
        val inferenceNanos: Long = 0L,
        val meshIntervalNanos: Long = 0L,
        val deliveryAgeNanos: Long = 0L,
        val currentCycleNanos: Long = 0L,
        val slowed: Boolean = false,
    ) {
        val cycleNanos: Long get() = maxOf(inferenceNanos, meshIntervalNanos, deliveryAgeNanos, currentCycleNanos)
        val predictionWindowNanos: Long get() = cycleNanos + cycleNanos / 2
        private val cycles: Double get() = if (meshIntervalNanos > 0) {
            inferenceNanos.toDouble() / meshIntervalNanos
        } else 0.0
        private val protection: Double get() = if (slowed) 1.5 else 1.0
        val requiredDrops: Int get() {
            val base = if (slowed) maxOf(3, kotlin.math.round(cycles).toInt() + 1)
                else maxOf(2, kotlin.math.round(cycles).toInt())
            return (base * protection).toInt()
        }
        val graceFrames: Int get() = (maxOf(5, kotlin.math.round(2 * cycles).toInt()) * protection).toInt()
    }

    private var minimumInference = Long.MAX_VALUE
    private var snapshot = Snapshot()

    fun record(inference: Long, gap: Long, deliveryAge: Long, faceDetected: Boolean = true): Snapshot {
        val comparableInference = if (faceDetected) inference else 0L
        if (comparableInference > 0) minimumInference = minOf(minimumInference, comparableInference)
        val estimate = ema(snapshot.inferenceNanos, comparableInference, if (comparableInference > snapshot.inferenceNanos) .4 else .15)
        snapshot = Snapshot(estimate, ema(snapshot.meshIntervalNanos, gap, .2),
            ema(snapshot.deliveryAgeNanos, deliveryAge, .2), maxOf(comparableInference, gap, deliveryAge),
            minimumInference < Long.MAX_VALUE && estimate.toDouble() / minimumInference > 2)
        return snapshot
    }

    fun reset() {
        snapshot = Snapshot()
        minimumInference = Long.MAX_VALUE
    }

    private fun ema(previous: Long, sample: Long, weight: Double): Long = when {
        sample <= 0 -> previous
        previous <= 0 -> sample
        else -> previous + ((sample - previous) * weight).toLong()
    }
}

/** Main-thread state. Only consecutive completed misses can remove an active anchor. */
internal class VtoFaceLossGrace {
    enum class State { TRACKING, GRACE, LOST }

    var state = State.LOST
        private set
    private var drops = 0
    private var grace = 0
    private var lossTiming: VtoLatencyEstimator.Snapshot? = null

    fun observe(faceDetected: Boolean, timing: VtoLatencyEstimator.Snapshot): State {
        if (faceDetected) {
            reset(State.TRACKING)
        } else if (state != State.LOST) {
            val limits = lossTiming ?: timing.also { lossTiming = it }
            when (state) {
                State.TRACKING -> if (++drops >= limits.requiredDrops) state = State.GRACE
                State.GRACE -> if (++grace >= limits.graceFrames) reset()
                State.LOST -> Unit
            }
        }
        return state
    }

    fun reset(state: State = State.LOST) {
        this.state = state
        drops = 0
        grace = 0
        lossTiming = null
    }
}
/** Quality settings only. The display clock is independent of this policy. */
internal enum class VtoHardwareBudget(
    val opticalFlow: Boolean,
    val secondaryPasses: Boolean,
    private val analysisAreaFraction: Double,
) {
    FULL(true, true, 1.0),
    REDUCED(true, false, 0.5),
    CONSTRAINED(false, false, 0.25);

    fun analysisDimension(fullDimension: Int): Int =
        (fullDimension * kotlin.math.sqrt(analysisAreaFraction)).toInt().coerceAtLeast(MIN_ANALYSIS_DIMENSION)

    companion object {
        const val MIN_ANALYSIS_DIMENSION = 360
        const val DEFAULT_ANALYSIS_DIMENSION = 480
        fun inputSize(width: Int, height: Int, preferredLongEdge: Int): Pair<Int, Int> {
            require(width > 0 && height > 0)
            val preferredRatio = (preferredLongEdge.toDouble() / maxOf(width, height)).coerceAtMost(1.0)
            val minimumRatio = MIN_ANALYSIS_DIMENSION.toDouble() / minOf(width, height)
            val ratio = maxOf(preferredRatio, minimumRatio)
            return kotlin.math.ceil(width * ratio).toInt() to kotlin.math.ceil(height * ratio).toInt()
        }

        fun fromThermalStatus(status: Int) = when {
            status >= PowerManager.THERMAL_STATUS_SEVERE -> CONSTRAINED
            status == PowerManager.THERMAL_STATUS_MODERATE -> REDUCED
            else -> FULL
        }
    }
}

/** Main-thread subscription. A generation guard also rejects callbacks queued before stop. */
internal class VtoThermalMonitor(
    private val subscribe: ((Int) -> Unit) -> AutoCloseable?,
) : AutoCloseable {
    @Volatile var budget = VtoHardwareBudget.FULL
        private set
    private var subscription: AutoCloseable? = null
    private var active = false
    private var generation = 0L

    fun start() {
        if (active) return
        active = true
        val token = ++generation
        subscription = subscribe { status ->
            if (active && token == generation) budget = VtoHardwareBudget.fromThermalStatus(status)
        }
    }

    override fun close() {
        active = false
        val current = subscription
        subscription = null
        current?.close()
    }

    companion object {
        fun create(context: Context): VtoThermalMonitor = VtoThermalMonitor { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) register(context, callback) else null
        }

        @TargetApi(Build.VERSION_CODES.Q)
        private fun register(context: Context, callback: (Int) -> Unit): AutoCloseable? {
            val manager = context.getSystemService(PowerManager::class.java) ?: return null
            val listener = PowerManager.OnThermalStatusChangedListener { callback(it) }
            manager.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            callback(manager.currentThermalStatus)
            return AutoCloseable { manager.removeThermalStatusListener(listener) }
        }
    }
}

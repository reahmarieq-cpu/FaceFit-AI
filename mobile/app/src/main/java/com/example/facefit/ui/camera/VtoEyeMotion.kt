package com.example.facefit.ui.camera

import androidx.camera.core.ImageProxy
import com.example.facefit.facefit.analysis.FaceAnalyzer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bounded, non-accumulating translation search against the last measured eye patch. */
internal object VtoEyeMotion {
    class Frame(val width: Int, val height: Int, val pixels: ByteArray)
    class Anchor(val frame: Frame, val x: Int, val y: Int, val radius: Int, val capturedAt: Long)
    data class Offset(
        val anchor: Anchor,
        val x: Float,
        val y: Float,
        val capturedAt: Long,
        val predicted: Boolean = false,
        val validUntilNanos: Long = capturedAt,
        val cameraIntervalNanos: Long = 0L,
        val velocityXPerNano: Float = 0f,
        val velocityYPerNano: Float = 0f,
        val decayTimeNanos: Long = 0L,
        val accelerationXPerNanoSquared: Float = 0f,
        val accelerationYPerNanoSquared: Float = 0f,
        val hasMeasurement: Boolean = true,
        val variance: Float = if (hasMeasurement) 0f else 1f,
        val psr: Float = 0f,
    ) {
        fun varianceAt(time: Long, meshInterval: Long): Float {
            if (meshInterval <= 0L) return 1f
            val age = (time - capturedAt).coerceAtLeast(0L).toDouble() / meshInterval
            return (variance + age * age).coerceIn(0.0, 1.0).toFloat()
        }

        fun rebaseTo(next: Anchor): Offset? {
            if (anchor === next) return this
            if (anchor.frame.width != next.frame.width || anchor.frame.height != next.frame.height) return null
            return copy(
                anchor = next,
                x = x + (anchor.x - next.x).toFloat() / next.frame.width,
                y = y + (anchor.y - next.y).toFloat() / next.frame.height,
            )
        }

        fun positionAt(frameNanos: Long): Pair<Float, Float> {
            val elapsed = (frameNanos - capturedAt).coerceAtLeast(0L)
            val lead = if (decayTimeNanos > 0L) {
                val linear = minOf(elapsed, decayTimeNanos)
                val tail = elapsed - linear
                (linear - decayTimeNanos * kotlin.math.expm1(-tail.toDouble() / decayTimeNanos)).toFloat()
            } else 0f
            // Acceleration is reliable only over an observed camera interval. After that,
            // coast at the resulting velocity under the same exponential time decay.
            val cameraInterval = cameraIntervalNanos.toFloat()
            val accelerationLead = minOf(lead, cameraInterval)
            val accelerationTerm = accelerationLead * (lead - .5f * accelerationLead)
            fun displacement(velocity: Float, acceleration: Float): Float {
                // Deceleration may bring an axis to rest, but prediction alone cannot reverse it.
                val safeAcceleration = if (cameraInterval > 0f && velocity * acceleration < 0f &&
                    abs(acceleration) * cameraInterval > abs(velocity)) -velocity / cameraInterval else acceleration
                return velocity * lead + safeAcceleration * accelerationTerm
            }
            return clampX(anchor, x + displacement(velocityXPerNano, accelerationXPerNanoSquared)) to
                clampY(anchor, y + displacement(velocityYPerNano, accelerationYPerNanoSquared))
        }
    }



    private fun clampX(anchor: Anchor, x: Float): Float = x.coerceIn(
        (anchor.radius - anchor.x).toFloat() / anchor.frame.width,
        (anchor.frame.width - 1 - anchor.radius - anchor.x).toFloat() / anchor.frame.width,
    )

    private fun clampY(anchor: Anchor, y: Float): Float = y.coerceIn(
        (anchor.radius - anchor.y).toFloat() / anchor.frame.height,
        (anchor.frame.height - 1 - anchor.radius - anchor.y).toFloat() / anchor.frame.height,
    )

    /** Analyzer-thread state. Velocity comes only from measured matches. */
    class Tracker {
        private data class Velocity(val x: Float, val y: Float)
        private val recentVelocities = ArrayDeque<Velocity>()
        private var latest: Offset? = null
        private var lastCameraAt: Long? = null
        private var observedAnchor: Anchor? = null
        private var cameraInterval = 0L
        var consecutiveMisses: Int = 0
            private set

        fun update(anchor: Anchor?, frame: Frame?, capturedAt: Long, meshLatencyNanos: Long): Offset? {
            if (anchor == null || (frame != null && (frame.width != anchor.frame.width ||
                frame.height != anchor.frame.height))) {
                latest = null
                recentVelocities.clear()
                lastCameraAt = null
                observedAnchor = null
                cameraInterval = 0L
                consecutiveMisses = 0
                return null
            }
            if (observedAnchor !== anchor && consecutiveMisses > 0) {
                latest = null
                recentVelocities.clear()
                consecutiveMisses = 0
            }
            observedAnchor = anchor
            val interval = lastCameraAt?.let { capturedAt - it } ?: 0L
            lastCameraAt = capturedAt
            if (interval > 0) {
                // A single analyzer stall must not suddenly turn into a long prediction allowance.
                cameraInterval = if (cameraInterval > 0L) {
                    (cameraInterval + interval.coerceAtMost(cameraInterval * 2)) / 2
                } else interval
            }

            // A new mesh changes the origin of the offset, not the observed eye position.
            // Keep the last real observation and its timestamp across compatible anchors.
            val last = latest?.rebaseTo(anchor)
            val budget = maxOf(meshLatencyNanos + meshLatencyNanos / 2,
                meshLatencyNanos + 2 * cameraInterval, 3 * cameraInterval)
            val age = last?.let { capturedAt - it.capturedAt } ?: 0L
            val fresh = last?.takeIf { age in 0L..budget }
            val base = last ?: Offset(anchor, 0f, 0f, anchor.capturedAt, hasMeasurement = false)
            val coast = base.copy(
                validUntilNanos = base.capturedAt + budget,
                cameraIntervalNanos = cameraInterval,
                decayTimeNanos = budget,
            )
            val hint = coast.takeIf { last != null && consecutiveMisses < 2 }?.let {
                val (x, y) = it.positionAt(capturedAt)
                it.copy(x = x, y = y)
            }
            val match = frame?.let { match(anchor, it, capturedAt, hint, last, consecutiveMisses) }
            if (match != null) {
                consecutiveMisses = 0
                val (vx, vy) = if (fresh != null && age > 0) measuredVelocity(anchor, fresh, match, age)
                    else (0f to 0f).also { recentVelocities.clear() }
                // A stop or reversal invalidates the old acceleration. Keep the latest velocity.
                val accelerating = fresh != null && age > 0 &&
                    vx * fresh.velocityXPerNano + vy * fresh.velocityYPerNano > 0f
                val ax = if (accelerating) (vx - fresh.velocityXPerNano) / age else 0f
                val ay = if (accelerating) (vy - fresh.velocityYPerNano) / age else 0f
                return match.copy(
                    validUntilNanos = capturedAt + budget,
                    cameraIntervalNanos = cameraInterval,
                    decayTimeNanos = budget,
                    velocityXPerNano = vx,
                    velocityYPerNano = vy,
                    accelerationXPerNanoSquared = ax,
                    accelerationYPerNanoSquared = ay,
                ).also { latest = it }
            }
            consecutiveMisses++
            // Keep the original measured timestamp. Matching and vsync evaluate one trajectory.
            return coast.copy(predicted = true, hasMeasurement = coast.hasMeasurement && consecutiveMisses < 6)
        }

        private fun measuredVelocity(anchor: Anchor, previous: Offset, current: Offset, elapsed: Long): Pair<Float, Float> {
            val width = anchor.frame.width
            val height = anchor.frame.height
            val dx = (current.x - previous.x) * width
            val dy = (current.y - previous.y) * height
            if (hypot(dx, dy) <= anchor.radius * .025f) {
                recentVelocities.clear()
                return 0f to 0f
            }

            // Limit acceleration in tracking pixels per observed camera interval.
            val interval = cameraInterval.coerceAtLeast(1L)
            val next = Velocity(dx / elapsed, dy / elapsed)
            val prior = recentVelocities.firstOrNull()
            if (prior != null && next.x * prior.x + next.y * prior.y <= 0f) recentVelocities.clear()
            recentVelocities.addFirst(next)
            if (recentVelocities.size > 5) recentVelocities.removeLast()
            var weightedX = 0f
            var weightedY = 0f
            var weightSum = 0f
            var recency = 1f
            for (sample in recentVelocities) {
                weightedX += sample.x * recency
                weightedY += sample.y * recency
                weightSum += recency
                recency *= .61f
            }
            val targetX = weightedX / weightSum * interval
            val targetY = weightedY / weightSum * interval
            val previousX = previous.velocityXPerNano * width * interval
            val previousY = previous.velocityYPerNano * height * interval
            val aligned = targetX * previousX + targetY * previousY > 0f
            val baseX = if (aligned) previousX else 0f
            val baseY = if (aligned) previousY else 0f
            val changeX = targetX - baseX
            val changeY = targetY - baseY
            val change = hypot(changeX, changeY)
            val limit = anchor.radius * .25f * (elapsed.toFloat() / interval)
            val weight = if (change > limit) limit / change else 1f
            return (baseX + changeX * weight) / width / interval to
                (baseY + changeY * weight) / height / interval
        }
    }

    fun read(image: ImageProxy, maxDimension: Int, reusable: Frame? = null): Frame? {
        if (image.imageInfo.rotationDegrees != 0) return null
        val plane = image.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 4 || image.width <= 0 || image.height <= 0) return null
        val source = plane.buffer
        val base = source.position()
        val last = base.toLong() + (image.height - 1L) * plane.rowStride + (image.width - 1L) * 4 + 2
        if (last >= source.limit()) return null
        val scale = (maxDimension.toFloat() / maxOf(image.width, image.height)).coerceAtMost(1f)
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        val pixels = reusable?.takeIf { it.width == width && it.height == height }?.pixels
            ?: ByteArray(width * height)
        for (y in 0 until height) {
            val row = base + (y * image.height / height) * plane.rowStride
            for (x in 0 until width) {
                val position = row + (x * image.width / width) * 4
                // CameraX RGBA plane. Use green as a cheap luminance proxy.
                pixels[y * width + x] = source.get(position + 1)
            }
        }
        return Frame(width, height, pixels)
    }

    fun anchor(frame: Frame, face: FaceAnalyzer.FaceMetrics, capturedAt: Long): Anchor? {
        if (!face.isFaceDetected || face.landmarks.size <= 263) return null
        val left = face.landmarks[33]
        val right = face.landmarks[263]
        if (!left.x.isFinite() || !left.y.isFinite() || !right.x.isFinite() || !right.y.isFinite()) return null
        val x = ((left.x + right.x) * .5f * frame.width).roundToInt()
        val y = ((left.y + right.y) * .5f * frame.height).roundToInt()
        val shortSide = minOf(frame.width, frame.height)
        if (shortSide < 3) return null
        val minimumRadius = maxOf(1, shortSide / 14)
        val room = minOf(x, y, frame.width - 1 - x, frame.height - 1 - y, shortSide / 3)
        if (room < minimumRadius) return null
        val radius = (abs(right.x - left.x) * frame.width * .65f).roundToInt()
            .coerceIn(minimumRadius, room)
        return Anchor(frame, x, y, radius, capturedAt)
    }

    fun match(
        anchor: Anchor,
        current: Frame,
        capturedAt: Long,
        hint: Offset? = null,
        lastMeasured: Offset? = null,
        misses: Int = 0,
    ): Offset? {
        val reference = anchor.frame
        if (current.width != reference.width || current.height != reference.height ||
            capturedAt <= anchor.capturedAt) return null
        val radius = anchor.radius
        // Subsample the template to at most roughly 12x12 points.
        val step = maxOf(2, radius / 5)
        val range = -radius..radius step step
        val samplesPerSide = radius * 2 / step + 1
        val values = IntArray(samplesPerSide * samplesPerSide)
        var index = 0
        var sumA = 0
        var sumAA = 0
        for (y in range) for (x in range) {
            val a = reference.pixels[(anchor.y + y) * reference.width + anchor.x + x].toInt() and 255
            values[index++] = a
            sumA += a
            sumAA += a * a
        }
        val n = values.size.toDouble()
        val varianceA = sumAA - sumA.toDouble() * sumA / n
        if (varianceA <= 0.0) return null

        fun score(dx: Int, dy: Int): Double {
            val cx = anchor.x + dx
            val cy = anchor.y + dy
            if (cx - radius < 0 || cx + radius >= current.width || cy - radius < 0 || cy + radius >= current.height) return -1.0
            var i = 0
            var sumB = 0
            var sumBB = 0
            var sumAB = 0
            for (y in range) for (x in range) {
                val b = current.pixels[(cy + y) * current.width + cx + x].toInt() and 255
                sumB += b
                sumBB += b * b
                sumAB += values[i++] * b
            }
            val varianceB = sumBB - sumB.toDouble() * sumB / n
            if (varianceB <= 0.0) return -1.0
            return (sumAB - sumA.toDouble() * sumB / n) / sqrt(varianceA * varianceB)
        }

        var best = -1.0
        var bestX = 0
        var bestY = 0
        fun consider(dx: Int, dy: Int): Double {
            val value = score(dx, dy)
            if (value > best) {
                best = value
                bestX = dx
                bestY = dy
            }
            return value
        }
        val minX = radius - anchor.x
        val maxX = current.width - 1 - radius - anchor.x
        val minY = radius - anchor.y
        val maxY = current.height - 1 - radius - anchor.y
        val hintX = (hint?.let { (it.x * current.width).roundToInt() } ?: 0).coerceIn(minX, maxX)
        val hintY = (hint?.let { (it.y * current.height).roundToInt() } ?: 0).coerceIn(minY, maxY)
        val elapsed = lastMeasured?.let { (capturedAt - it.capturedAt).coerceAtLeast(0L) } ?: 0L
        val travel = lastMeasured?.let {
            hypot(it.velocityXPerNano * current.width, it.velocityYPerNano * current.height) * elapsed
        } ?: 0f
        val search = (radius * 2 + travel).roundToInt().coerceAtLeast(1)
            .coerceAtMost(maxOf(current.width, current.height))
        val coarseStep = 1
        if (lastMeasured != null) {
            consider((lastMeasured.x * current.width).roundToInt(),
                (lastMeasured.y * current.height).roundToInt())
        }
        val coarseExtent = search / coarseStep * coarseStep
        val xs = hintX - coarseExtent..hintX + coarseExtent step coarseStep
        val ys = hintY - coarseExtent..hintY + coarseExtent step coarseStep
        val coarseCount = coarseExtent * 2 / coarseStep + 1
        val coarse = DoubleArray(coarseCount * coarseCount)
        index = 0
        for (dy in ys) for (dx in xs) {
            coarse[index++] = if (dx in minX..maxX && dy in minY..maxY) consider(dx, dy) else -1.0
        }
        val coarseX = bestX
        val coarseY = bestY
        for (dy in maxOf(minY, coarseY - coarseStep)..minOf(maxY, coarseY + coarseStep)) {
            for (dx in maxOf(minX, coarseX - coarseStep)..minOf(maxX, coarseX + coarseStep)) consider(dx, dy)
        }
        // Exclude the template footprint so neighboring samples of the same peak
        // do not count as background. Welford statistics avoid cancellation.
        var count = 0
        var mean = 0.0
        var squares = 0.0
        index = 0
        for (dy in ys) for (dx in xs) {
            val value = coarse[index++]
            if (dx !in minX..maxX || dy !in minY..maxY ||
                maxOf(abs(dx - bestX), abs(dy - bestY)) <= 1) continue
            count++
            val delta = value - mean
            mean += delta / count
            squares += delta * (value - mean)
        }
        if (count <= 1 || best <= mean) return null
        val backgroundVariance = squares / (count - 1)
        val noise = sqrt(backgroundVariance) + 1e-6
        val psr = (best - mean) / noise
        // Expected maximum of independent standardized background samples.
        val threshold = 5.5
        if (!psr.isFinite() || psr <= threshold) return null
        return Offset(anchor, bestX.toFloat() / current.width, bestY.toFloat() / current.height,
            capturedAt, variance = (1.0 / psr).coerceIn(0.0, 1.0).toFloat(), psr = psr.toFloat())
    }
}
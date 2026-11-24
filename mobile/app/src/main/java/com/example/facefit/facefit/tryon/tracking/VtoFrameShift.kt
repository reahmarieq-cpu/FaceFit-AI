package com.example.facefit.facefit.tryon.tracking

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Small grayscale copy of one camera frame, used only to follow the eye region between meshes. */
internal class VtoThumbnail(
    val id: Long,
    /** Size of the camera frame this was made from. */
    val sourceWidth: Int,
    val sourceHeight: Int,
    val step: Int,
    val width: Int,
    val height: Int,
    /** Luma 0..255, row-major, width * height. */
    val pixels: IntArray,
) {
    /** Summed-area table, (width + 1) * (height + 1), built once per frame for O(1) window means. */
    internal val integral: IntArray by lazy {
        val stride = width + 1
        IntArray(stride * (height + 1)).also { table ->
            for (y in 0 until height) {
                var row = 0
                for (x in 0 until width) {
                    row += pixels[y * width + x]
                    table[(y + 1) * stride + x + 1] = table[y * stride + x + 1] + row
                }
            }
        }
    }

    internal fun windowSum(x: Int, y: Int, w: Int, h: Int): Int {
        val stride = width + 1
        val t = integral
        return t[(y + h) * stride + x + w] - t[y * stride + x + w] - t[(y + h) * stride + x] + t[y * stride + x]
    }

    companion object {
        /** Samples every [step]-th pixel of an RGBA_8888 plane (pixel stride 4). */
        fun fromRgba(id: Long, buffer: ByteBuffer, rowStride: Int, sourceWidth: Int, sourceHeight: Int, step: Int): VtoThumbnail {
            require(step > 0 && sourceWidth >= step && sourceHeight >= step && rowStride >= sourceWidth * 4)
            val w = sourceWidth / step
            val h = sourceHeight / step
            val base = buffer.position()
            val out = IntArray(w * h)
            // One bulk copy per sampled row is far cheaper than per-pixel buffer reads.
            val source = buffer.duplicate()
            val row = ByteArray(w * step * 4)
            for (y in 0 until h) {
                source.position(base + y * step * rowStride)
                source.get(row, 0, row.size)
                for (x in 0 until w) {
                    val i = x * step * 4
                    val r = row[i].toInt() and 0xFF
                    val g = row[i + 1].toInt() and 0xFF
                    val b = row[i + 2].toInt() and 0xFF
                    out[y * w + x] = (r + 2 * g + b) shr 2
                }
            }
            return VtoThumbnail(id, sourceWidth, sourceHeight, step, w, h, out)
        }
    }
}

/**
 * Follows both eyes between slow face-mesh updates.
 *
 * When a mesh arrives, a patch around each eye in that mesh's own camera frame becomes the
 * reference. Every later camera frame is matched against those same references (zero-mean SAD,
 * coarse then exact, with a sub-pixel peak), so the result is where each eye moved since the mesh's
 * frame. Two eyes give position, in-plane tilt and size (distance), not just position. Nothing
 * accumulates from frame to frame, and each new mesh replaces the references.
 *
 * Thread-safe: references are set on the tracking worker, frames are matched on the camera thread.
 */
internal class VtoFrameShiftTracker(private val searchRadius: Int = SEARCH_RADIUS) {
    /** Eye centres in the mesh frame ([from]) and now ([to]), in source-frame pixels. */
    data class Shift(
        val referenceId: Long,
        val frameId: Long,
        val firstFromX: Float, val firstFromY: Float, val firstToX: Float, val firstToY: Float,
        val secondFromX: Float, val secondFromY: Float, val secondToX: Float, val secondToY: Float,
    ) {
        /** Movement of the point between the eyes, in source pixels. */
        val dx: Float get() = (firstToX - firstFromX + secondToX - secondFromX) * .5f
        val dy: Float get() = (firstToY - firstFromY + secondToY - secondFromY) * .5f
        /** Eye distance now / eye distance in the mesh frame. */
        val scale: Float get() = hypot(secondToX - firstToX, secondToY - firstToY) /
            hypot(secondFromX - firstFromX, secondFromY - firstFromY)
        /** In-plane rotation of the eye line since the mesh frame, radians in source coordinates. */
        val roll: Float get() {
            val ax = secondFromX - firstFromX; val ay = secondFromY - firstFromY
            val bx = secondToX - firstToX; val by = secondToY - firstToY
            return atan2(ax * by - ay * bx, ax * bx + ay * by)
        }
    }

    private class Patch(
        val x0: Int, val y0: Int, val w: Int, val h: Int,
        val pixels: IntArray, val sum: Int, val contrast: Float,
        /** Eye centre in thumbnail pixels at reference time. */
        val eyeX: Float, val eyeY: Float,
    ) {
        /** Current displacement from the reference position, thumbnail px. */
        var posX = 0f
        var posY = 0f
        /** Displacement per camera frame, from the last two matches. */
        var velX = 0f
        var velY = 0f
    }

    private class Reference(
        val id: Long, val thumbWidth: Int, val thumbHeight: Int, val step: Int,
        val first: Patch, val second: Patch,
    ) {
        var lastFrameId = id
        /** Absolute eye positions (thumbnail px) per tracked frame, newest last. */
        val history = ArrayDeque<FloatArray>()
    }

    private var reference: Reference? = null

    /**
     * Eye centres normalized to the source frame. Returns false when either patch is unusable
     * (off-frame or too flat to track).
     */
    @Synchronized
    fun setReference(frame: VtoThumbnail, firstX: Float, firstY: Float, secondX: Float, secondY: Float): Boolean {
        val previous = reference
        reference = null
        if (listOf(firstX, firstY, secondX, secondY).any { !it.isFinite() }) return false
        val ax = firstX * frame.width; val ay = firstY * frame.height
        val bx = secondX * frame.width; val by = secondY * frame.height
        val span = hypot(bx - ax, by - ay)
        if (span < MIN_EYE_SPAN) return false
        // Each patch holds one eye and the lower brow, capped so a close face stays cheap.
        val halfW = (span * .32f).roundToInt().coerceIn(5, 14)
        val halfH = (span * .22f).roundToInt().coerceIn(4, 9)
        val lift = span * .06f
        val first = patch(frame, ax, ay, lift, halfW, halfH) ?: return false
        val second = patch(frame, bx, by, lift, halfW, halfH) ?: return false
        val next = Reference(frame.id, frame.width, frame.height, frame.step, first, second)
        // The mesh's frame is ~300 ms old. Start the search where the previous reference saw the
        // eyes most recently, moved by how far they went since this frame, so the first match
        // after a mesh does not have to find a long move from scratch.
        val history = previous?.history
        val then = history?.lastOrNull { it[0].toLong() <= frame.id }
        val now = history?.lastOrNull()
        // Diagnostic: the new mesh's eyes vs where tracking had them in the very same frame.
        if (previous != null && then != null && then[0].toLong() == frame.id && previous.thumbWidth == frame.width && previous.step == frame.step) {
            val ex = ((ax - then[1]) + (bx - then[3])) * .5f * frame.step
            val ey = ((ay - then[2]) + (by - then[4])) * .5f * frame.step
            val vx = (previous.first.velX + previous.second.velX) * .5f
            val vy = (previous.first.velY + previous.second.velY) * .5f
            val speed = hypot(vx, vy)
            lastHandoverErrorPx = hypot(ex, ey)
            lastHandoverAlongPx = if (speed > .5f) (ex * vx + ey * vy) / speed else Float.NaN
        } else { lastHandoverErrorPx = Float.NaN; lastHandoverAlongPx = Float.NaN }
        if (previous != null && then != null && now != null && now !== then &&
            previous.thumbWidth == frame.width && previous.step == frame.step) {
            first.posX = now[1] - then[1]; first.posY = now[2] - then[2]
            second.posX = now[3] - then[3]; second.posY = now[4] - then[4]
            first.velX = previous.first.velX; first.velY = previous.first.velY
            second.velX = previous.second.velX; second.velY = previous.second.velY
            next.lastFrameId = now[0].toLong()
        }
        reference = next
        return true
    }

    private fun patch(frame: VtoThumbnail, eyeX: Float, eyeY: Float, lift: Float, halfW: Int, halfH: Int): Patch? {
        val x0 = (eyeX - halfW).roundToInt()
        val y0 = (eyeY - lift - halfH).roundToInt()
        val w = 2 * halfW
        val h = 2 * halfH
        if (x0 < 0 || y0 < 0 || x0 + w > frame.width || y0 + h > frame.height) return null
        val pixels = IntArray(w * h)
        var sum = 0
        for (y in 0 until h) for (x in 0 until w) {
            val v = frame.pixels[(y0 + y) * frame.width + x0 + x]
            pixels[y * w + x] = v
            sum += v
        }
        val mean = sum.toFloat() / pixels.size
        val contrast = pixels.sumOf { abs(it - mean).toDouble() }.toFloat() / pixels.size
        return if (contrast < MIN_CONTRAST) null else Patch(x0, y0, w, h, pixels, sum, contrast, eyeX, eyeY)
    }

    @Synchronized
    fun clear() { reference = null }

    @Synchronized
    fun hasReference(): Boolean = reference != null

    /**
     * Where both eyes are in [frame], or null when neither can be found reliably. When only one
     * eye matches (a blink, glare), the other moves with it, keeping the last tilt and size.
     */
    @Synchronized
    fun track(frame: VtoThumbnail): Shift? {
        val ref = reference ?: return null
        if (frame.width != ref.thumbWidth || frame.height != ref.thumbHeight || frame.step != ref.step) return null
        if (frame.id == ref.id) {
            for (p in listOf(ref.first, ref.second)) { p.posX = 0f; p.posY = 0f }
            ref.lastFrameId = frame.id
            record(ref, frame.id)
            return shift(ref, frame.id)
        }
        val gap = (frame.id - ref.lastFrameId).coerceIn(0L, MAX_PREDICTED_FRAMES).toFloat()
        var a = match(frame, ref.first, gap)
        var b = match(frame, ref.second, gap)
        // One eye alone is easier to fool (a blink, a lash, a repeating pattern): trust it only
        // close to where its velocity says it should be.
        fun plausible(p: Patch, at: Pair<Float, Float>) =
            hypot(at.first - (p.posX + p.velX * gap), at.second - (p.posY + p.velY * gap)) <= SINGLE_EYE_MAX_STEP
        if (a == null && b != null && !plausible(ref.second, b)) b = null
        if (b == null && a != null && !plausible(ref.first, a)) a = null
        val saved = floatArrayOf(ref.first.posX, ref.first.posY, ref.first.velX, ref.first.velY,
            ref.second.posX, ref.second.posY, ref.second.velX, ref.second.velY)
        when {
            a != null && b != null -> { move(ref.first, a, gap); move(ref.second, b, gap) }
            a != null -> { val dx = a.first - ref.first.posX; val dy = a.second - ref.first.posY
                move(ref.first, a, gap); move(ref.second, ref.second.posX + dx to ref.second.posY + dy, gap) }
            b != null -> { val dx = b.first - ref.second.posX; val dy = b.second - ref.second.posY
                move(ref.second, b, gap); move(ref.first, ref.first.posX + dx to ref.first.posY + dy, gap) }
            else -> return null // Keep the old position; the gap grows and prediction covers it next frame.
        }
        val shift = shift(ref, frame.id)
        // Eyes cannot plausibly change distance or angle this much between two meshes.
        if (shift.scale !in MIN_SCALE..MAX_SCALE || abs(shift.roll) > MAX_ROLL) {
            ref.first.posX = saved[0]; ref.first.posY = saved[1]; ref.first.velX = saved[2]; ref.first.velY = saved[3]
            ref.second.posX = saved[4]; ref.second.posY = saved[5]; ref.second.velX = saved[6]; ref.second.velY = saved[7]
            return null
        }
        ref.lastFrameId = frame.id
        record(ref, frame.id)
        return shift
    }

    private fun move(p: Patch, to: Pair<Float, Float>, gap: Float) {
        if (gap > 0f) {
            p.velX = (to.first - p.posX) / gap
            p.velY = (to.second - p.posY) / gap
        }
        p.posX = to.first
        p.posY = to.second
    }

    private fun record(ref: Reference, frameId: Long) {
        if (ref.history.size >= HISTORY) ref.history.removeFirst()
        ref.history.addLast(floatArrayOf(frameId.toFloat(), ref.first.eyeX + ref.first.posX, ref.first.eyeY + ref.first.posY,
            ref.second.eyeX + ref.second.posX, ref.second.eyeY + ref.second.posY))
        if (midpoints.size >= MIDPOINTS) midpoints.removeFirst()
        val s = ref.step.toFloat()
        midpoints.addLast(floatArrayOf(frameId.toFloat(),
            (ref.first.eyeX + ref.first.posX + ref.second.eyeX + ref.second.posX) * .5f * s,
            (ref.first.eyeY + ref.first.posY + ref.second.eyeY + ref.second.posY) * .5f * s))
    }

    /** Recent eye midpoints across all references: (frame id, x, y) in source pixels. */
    private val midpoints = ArrayDeque<FloatArray>()

    /** Where the point between the eyes was in camera frame [frameId] (source px), if it was tracked. */
    @Synchronized
    fun eyeMidpoint(frameId: Long): FloatArray? =
        midpoints.lastOrNull { it[0].toLong() == frameId }?.let { floatArrayOf(it[1], it[2]) }

    /** Last mesh hand-over: mesh eyes minus tracked eyes in the same frame (source px; debug). */
    @Volatile var lastHandoverErrorPx = Float.NaN
        private set
    /** Same, along the direction of motion: negative means the mesh was behind the tracked eyes. */
    @Volatile var lastHandoverAlongPx = Float.NaN
        private set

    private fun shift(ref: Reference, frameId: Long): Shift {
        val s = ref.step.toFloat()
        val a = ref.first; val b = ref.second
        return Shift(ref.id, frameId,
            a.eyeX * s, a.eyeY * s, (a.eyeX + a.posX) * s, (a.eyeY + a.posY) * s,
            b.eyeX * s, b.eyeY * s, (b.eyeX + b.posX) * s, (b.eyeY + b.posY) * s)
    }

    /** New displacement of [p] in [frame] (thumbnail px), or null when the match is unreliable. */
    private fun match(frame: VtoThumbnail, p: Patch, gap: Float): Pair<Float, Float>? {
        val n = p.w * p.h
        val side = 2 * searchRadius + 1
        // Centre the window where the eye should be now: last position plus its velocity.
        val centreX = (p.posX + p.velX * gap).roundToInt()
        val centreY = (p.posY + p.velY * gap).roundToInt()
        var coarseBest = Int.MAX_VALUE
        var bestI = -1
        // Coarse pass over the whole window on every 2nd patch pixel (a quarter of the work).
        for (oy in -searchRadius..searchRadius) for (ox in -searchRadius..searchRadius) {
            val sad = sad(frame, p, p.x0 + centreX + ox, p.y0 + centreY + oy, COARSE_SUBSAMPLE)
            if (sad < coarseBest) { coarseBest = sad; bestI = (oy + searchRadius) * side + ox + searchRadius }
        }
        if (bestI < 0) return null
        val bx = bestI % side - searchRadius
        val by = bestI / side - searchRadius
        // A peak on the edge of the window may be cut off: the true match could lie outside.
        if (bx == -searchRadius || bx == searchRadius || by == -searchRadius || by == searchRadius) return null
        // Exact scores at the peak and its four neighbours give the accept test and sub-pixel fit.
        val cx = p.x0 + centreX + bx
        val cy = p.y0 + centreY + by
        val best = sad(frame, p, cx, cy, 1)
        if (best == Int.MAX_VALUE || best.toFloat() / n > maxOf(MIN_ERROR, MATCH_TOLERANCE * p.contrast)) return null
        fun subPixel(minus: Int, centre: Int, plus: Int): Float {
            if (minus == Int.MAX_VALUE || plus == Int.MAX_VALUE) return 0f
            val curvature = (minus - 2 * centre + plus).toFloat()
            return if (curvature > 0f) ((minus - plus) / (2f * curvature)).coerceIn(-.5f, .5f) else 0f
        }
        val fx = subPixel(sad(frame, p, cx - 1, cy, 1), best, sad(frame, p, cx + 1, cy, 1))
        val fy = subPixel(sad(frame, p, cx, cy - 1, 1), best, sad(frame, p, cx, cy + 1, 1))
        return (centreX + bx + fx) to (centreY + by + fy)
    }

    /** Zero-mean SAD of the patch at (wx, wy), sampling every [sub]-th row and column. */
    private fun sad(frame: VtoThumbnail, p: Patch, wx: Int, wy: Int, sub: Int): Int {
        if (wx < 0 || wy < 0 || wx + p.w > frame.width || wy + p.h > frame.height) return Int.MAX_VALUE
        // Removing the mean difference tolerates small exposure changes between frames.
        val offset = ((frame.windowSum(wx, wy, p.w, p.h) - p.sum).toFloat() / (p.w * p.h)).roundToInt()
        val px = frame.pixels
        val patch = p.pixels
        var total = 0
        var y = 0
        while (y < p.h) {
            val fr = (wy + y) * frame.width + wx
            val pr = y * p.w
            var x = 0
            while (x < p.w) { total += abs(px[fr + x] - patch[pr + x] - offset); x += sub }
            y += sub
        }
        return total
    }

    companion object {
        const val THUMBNAIL_STEP = 3
        const val SEARCH_RADIUS = 8
        const val COARSE_SUBSAMPLE = 2
        /** Eyes closer than this (thumbnail px) are too small to track. */
        const val MIN_EYE_SPAN = 12f
        /** Mean luma deviation below this is too flat to track (e.g. a covered or blown-out face). */
        const val MIN_CONTRAST = 4f
        /** Accept a match whose mean error is at most this fraction of the patch's own contrast. */
        const val MATCH_TOLERANCE = .75f
        const val MIN_ERROR = 4f
        const val MIN_SCALE = .8f
        const val MAX_SCALE = 1.25f
        const val MAX_ROLL = .5f
        /** Frames of eye positions kept to hand motion over to the next mesh's reference. */
        const val HISTORY = 24
        /** Frames of eye midpoints kept to move the face-mesh crop with the face. */
        const val MIDPOINTS = 64
        /** Velocity prediction never extrapolates further than this many camera frames. */
        const val MAX_PREDICTED_FRAMES = 4L
        /** A lone eye's match may differ from its prediction by at most this (thumbnail px). */
        const val SINGLE_EYE_MAX_STEP = 4f
    }
}

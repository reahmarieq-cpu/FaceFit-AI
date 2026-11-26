package com.facefit.kiosk.ui.scan.mobile

/** The outline used for both drawing and validating the scan guide. */
internal object FaceScanGuide {
    data class Point(val x: Float, val y: Float) {
        fun mirrored() = Point(-x, y)
    }

    private val outline = buildList<Point> {
        val top = Point(0f, -1f)
        val side = Point(1f, -.08f)
        val lowerSide = Point(1f, .36f)
        val chin = Point(0f, 1f)
        add(top)
        addAll(curve(top, Point(.75f, -1f), Point(1f, -.58f), side))
        add(lowerSide)
        addAll(curve(lowerSide, Point(.92f, .78f), Point(.48f, 1f), chin))
        addAll(toList().drop(1).dropLast(1).asReversed().map { it.mirrored() })
    }

    private fun curve(a: Point, b: Point, c: Point, d: Point): List<Point> =
        (1..24).map { step ->
            val t = step / 24f
            val u = 1f - t
            fun coordinate(start: Float, first: Float, second: Float, end: Float) =
                u * u * u * start + 3 * u * u * t * first + 3 * u * t * t * second + t * t * t * end
            Point(coordinate(a.x, b.x, c.x, d.x), coordinate(a.y, b.y, c.y, d.y))
        }

    // Allow some head movement during the burst without enlarging the visible distance guide.
    private const val CAPTURE_MARGIN = 1.18f

    /**
     * Half-width (screen px) of the outline an average face fills at the target distance.
     * [focalLengthPx] is for the upright analysis image; [analysisToScreenScale] converts its
     * pixels to screen pixels. Uses the same average face as the distance estimate, so filling
     * the outline and the chip reading 45 cm coincide for every face width.
     */
    fun halfWidthForTargetDistance(focalLengthPx: Float, analysisToScreenScale: Float): Float? {
        val cheekScreenPx = focalLengthPx * FacialFeatureMeasurement.CANONICAL_CHEEK_WIDTH_CM /
            FacialFeatureMeasurement.TARGET_DISTANCE_CM * analysisToScreenScale
        return (cheekScreenPx / 2f).takeIf { it.isFinite() && it > 0f }
    }

    /** [halfWidth] null keeps the original fixed size (camera geometry not known yet). */
    fun points(width: Float, height: Float, halfWidth: Float? = null): List<Point> {
        val resolvedHalfWidth = halfWidth?.takeIf { it.isFinite() && it > 0f }
            ?: minOf(width * .31f, height * .52f / 2.84f)
        val halfHeight = resolvedHalfWidth * 1.42f
        val centerY = height * .48f
        return outline.map { Point(width / 2 + it.x * resolvedHalfWidth, centerY + it.y * halfHeight) }
    }

    fun containsFace(width: Int, height: Int, landmarks: FloatArray, halfWidth: Float? = null): Boolean {
        if (width <= 0 || height <= 0 || landmarks.isEmpty() || landmarks.size % 2 != 0) return false
        val boundary = points(width.toFloat(), height.toFloat(), halfWidth?.times(CAPTURE_MARGIN))
        for (index in landmarks.indices step 2) {
            val x = landmarks[index]
            val y = landmarks[index + 1]
            if (!x.isFinite() || !y.isFinite()) return false
            var inside = false
            var previous = boundary.last()
            for (current in boundary) {
                if ((current.y > y) != (previous.y > y) &&
                    x < (previous.x - current.x) * (y - current.y) /
                        (previous.y - current.y) + current.x
                ) inside = !inside
                previous = current
            }
            if (!inside) return false
        }
        return true
    }
}

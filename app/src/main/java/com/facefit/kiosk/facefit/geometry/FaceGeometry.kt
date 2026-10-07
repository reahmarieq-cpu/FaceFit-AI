package com.facefit.kiosk.facefit.geometry

import kotlin.math.hypot

object FaceGeometry {
    fun distance2D(a: GeometryPoint, b: GeometryPoint): Double {
        return hypot(b.x - a.x, b.y - a.y)
    }

    fun midpoint(a: GeometryPoint, b: GeometryPoint): GeometryPoint {
        return GeometryPoint(
            x = (a.x + b.x) / 2.0,
            y = (a.y + b.y) / 2.0
        )
    }

    fun average(points: List<GeometryPoint>): GeometryPoint? {
        if (points.isEmpty()) return null

        return GeometryPoint(
            x = points.sumOf { it.x } / points.size,
            y = points.sumOf { it.y } / points.size
        )
    }

    fun ratio(value: Double, reference: Double): Double? {
        if (!value.isFinite() || !reference.isFinite() || reference <= 0.0) return null
        val ratio = value / reference
        return ratio.takeIf { it.isFinite() }
    }
}

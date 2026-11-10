package com.facefit.kiosk.facefit.mobilescan

internal fun medianValue(values: List<Float>): Float {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
}

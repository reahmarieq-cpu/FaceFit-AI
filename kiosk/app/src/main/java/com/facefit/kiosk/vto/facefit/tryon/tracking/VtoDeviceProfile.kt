package com.facefit.kiosk.vto.facefit.tryon.tracking

import android.app.ActivityManager
import android.content.Context
import android.util.Size

internal enum class VtoTier { HIGH, BALANCED, LOW }

internal data class VtoDeviceProfile(
    val tier: VtoTier,
    val previewSize: Size,
    val analysisSize: Size,
    val numThreads: Int,
    val filterBeta: Float,
    val filterMinCutoff: Float
) {
    companion object {
        private var cached: VtoDeviceProfile? = null

        @Synchronized
        fun detect(context: Context): VtoDeviceProfile {
            cached?.let { return it }
            val cores = Runtime.getRuntime().availableProcessors()
            val memoryGiB = runCatching {
                val manager = requireNotNull(context.getSystemService(ActivityManager::class.java))
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                info.totalMem / (1024L * 1024 * 1024)
            }.getOrDefault(4L)
            return select(cores, memoryGiB).also { cached = it }
        }

        private fun select(cores: Int, memoryGiB: Long): VtoDeviceProfile = when {
            cores >= 8 && memoryGiB >= 5L ->
                VtoDeviceProfile(VtoTier.HIGH, Size(1920, 1080), Size(640, 480), 4, .15f, 1.2f)
            cores >= 6 && memoryGiB >= 4L ->
                VtoDeviceProfile(VtoTier.BALANCED, Size(1280, 720), Size(480, 360), 2, .12f, 1.2f)
            else ->
                VtoDeviceProfile(VtoTier.LOW, Size(1280, 720), Size(320, 240), 2, .10f, 1.0f)
        }
    }
}

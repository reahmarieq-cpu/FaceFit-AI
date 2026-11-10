package com.facefit.kiosk.ui.results

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import java.util.concurrent.Executors
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Product photo preview, with a shape illustration when an image is unavailable. */
class FrameArtworkView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var frameShape: String = "Rectangle"
        set(value) { field = value; invalidate() }
    private data class Photo(val bitmap: Bitmap, val bounds: Rect)
    private companion object {
        val loader = Executors.newFixedThreadPool(2)
        val photos = object : LruCache<String, Photo>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Photo) = value.bitmap.byteCount
        }
    }
    private var photo: Photo? = null
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var imageAssetPath: String? = null
        set(value) {
            if (field == value) return
            field = value
            photo = value?.let { photos.get(it) }
            invalidate()
            if (value == null || photo != null) return
            val assets = context.applicationContext.assets
            loader.execute {
                val loaded = runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    assets.open(value).use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
                    }
                    val bitmap = assets.open(value).use { BitmapFactory.decodeStream(it, null, options) }
                        ?: return@runCatching null
                    // Ignore transparent export margins without changing the original file or VTO anchors.
                    var minX = bitmap.width; var minY = bitmap.height; var maxX = -1; var maxY = -1
                    if (bitmap.hasAlpha()) {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                            if ((pixels[y * bitmap.width + x] ushr 24) > 16) {
                                minX = minOf(minX, x); minY = minOf(minY, y)
                                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                            }
                        }
                    }
                    Photo(bitmap, if (maxX >= minX) Rect(minX, minY, maxX + 1, maxY + 1)
                        else Rect(0, 0, bitmap.width, bitmap.height))
                }.getOrNull()
                if (loaded != null) photos.put(value, loaded)
                post {
                    if (imageAssetPath == value) { photo = loaded; invalidate() }
                }
            }
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        photo?.let { image ->
            val inset = 3f * resources.displayMetrics.density
            val scale = minOf((width - inset * 2).coerceAtLeast(0f) / image.bounds.width(),
                (height - inset * 2).coerceAtLeast(0f) / image.bounds.height())
            val targetWidth = image.bounds.width() * scale
            val targetHeight = image.bounds.height() * scale
            val left = (width - targetWidth) / 2f
            val top = (height - targetHeight) / 2f
            canvas.drawBitmap(image.bitmap, image.bounds,
                RectF(left, top, left + targetWidth, top + targetHeight), photoPaint)
            return
        }
        val scale = minOf(width / 120f, height / 70f)
        canvas.save()
        canvas.translate((width - 120f * scale) / 2f, (height - 70f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.color = when (frameShape) {
            "Cat-eye" -> Color.rgb(190, 89, 139)
            "Aviator" -> Color.rgb(190, 133, 44)
            "Square" -> Color.rgb(45, 78, 102)
            else -> Color.rgb(61, 73, 77)
        }
        when (frameShape) {
            "Cat-eye" -> {
                val left = Path().apply {
                    moveTo(7f, 20f); lineTo(45f, 25f); quadTo(51f, 29f, 47f, 43f)
                    quadTo(42f, 53f, 24f, 48f); quadTo(13f, 46f, 7f, 20f); close()
                }
                canvas.drawPath(left, paint)
                canvas.save(); canvas.translate(120f, 0f); canvas.scale(-1f, 1f)
                canvas.drawPath(left, paint); canvas.restore()
            }
            "Aviator" -> {
                val left = Path().apply {
                    moveTo(11f, 23f); quadTo(25f, 15f, 45f, 23f)
                    quadTo(53f, 29f, 44f, 47f); quadTo(35f, 60f, 18f, 48f)
                    quadTo(7f, 39f, 11f, 23f); close()
                }
                canvas.drawPath(left, paint)
                canvas.save(); canvas.translate(120f, 0f); canvas.scale(-1f, 1f)
                canvas.drawPath(left, paint); canvas.restore()
                canvas.drawLine(47f, 23f, 73f, 23f, paint)
            }
            else -> {
                val top = if (frameShape == "Square") 19f else 25f
                canvas.drawRoundRect(9f, top, 49f, 49f, 6f, 6f, paint)
                canvas.drawRoundRect(71f, top, 111f, 49f, 6f, 6f, paint)
            }
        }
        canvas.drawPath(Path().apply { moveTo(49f, 32f); quadTo(60f, 27f, 71f, 32f) }, paint)
        canvas.drawLine(4f, 28f, 9f, 29f, paint)
        canvas.drawLine(111f, 29f, 116f, 28f, paint)
        canvas.restore()
    }
}
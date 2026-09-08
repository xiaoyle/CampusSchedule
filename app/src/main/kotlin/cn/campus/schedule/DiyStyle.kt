package cn.campus.schedule

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.max

@Serializable
data class DiyStyle(
    val enabled: Boolean = false,
    val kind: String = "photo",
    val image: String = "",
    val title: String = "",
    val motto: String = "",
    val zoom: Float = 1f,
    val x: Float = .5f,
    val y: Float = .5f,
    val blur: Float = 0f,
    val shade: Float = .45f,
    val opacity: Float = 1f,
    val accent: Long = 0xFFB7F2D5,
    val fontScale: Float = 1f
) {
    val foreground: Long get() = if (kind == "notes") 0xFF24352E else 0xFFFFFFFF
    val highlight: Long get() = if (kind == "notes") 0xFF176B52 else accent
    fun heading(compact: Boolean) = title.ifBlank { if(compact) "下一节课" else "今日课程" }
}

@Serializable data class SavedDiy(val name: String, val style: DiyStyle)

object DiyStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun prefs(c: Context) = c.getSharedPreferences("widget_diy", Context.MODE_PRIVATE)
    fun read(c: Context, compact: Boolean): DiyStyle = runCatching {
        json.decodeFromString<DiyStyle>(prefs(c).getString(if(compact) "next" else "today", "{}")!!)
    }.getOrDefault(DiyStyle())
    fun save(c: Context, compact: Boolean, style: DiyStyle) {
        check(prefs(c).edit().putString(if(compact) "next" else "today", json.encodeToString(style)).commit()) { "保存失败，请重试" }
    }
    fun themes(c: Context): List<SavedDiy> = runCatching {
        json.decodeFromString<List<SavedDiy>>(prefs(c).getString("themes", "[]")!!)
    }.getOrDefault(emptyList())
    fun saveThemes(c: Context, values: List<SavedDiy>) {
        check(prefs(c).edit().putString("themes", json.encodeToString(values)).commit()) { "保存搭配失败，请重试" }
    }
    fun imageFile(c: Context, name: String): File? = name.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }?.let { File(c.filesDir, "diy/$it") }

    // Copy a bounded, orientation-correct bitmap, never retaining a cloud URI or EXIF metadata.
    fun importImage(c: Context, uri: Uri): String {
        val bytes = c.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while(true) {
                val n = input.read(buffer); if(n < 0) break
                require(out.size() + n <= 24 * 1024 * 1024) { "图片超过 24 MB，请先压缩后再选" }
                out.write(buffer, 0, n)
            }; out.toByteArray()
        } ?: error("无法读取图片，请先保存到手机")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "这张图片无法识别，请选择 JPG、PNG 或 WebP" }
        val bitmap = if(Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val factor = minOf(1f, 1600f / max(info.size.width, info.size.height))
                decoder.setTargetSize(max(1, (info.size.width * factor).toInt()), max(1, (info.size.height * factor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while(max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("无法读取图片")
            val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            val matrix = Matrix()
            when(exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> matrix.setScale(1f, -1f)
                5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                6 -> matrix.setRotate(90f)
                7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                8 -> matrix.setRotate(270f)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if(it !== decoded) decoded.recycle() }
        }
        val name = "${UUID.randomUUID()}.jpg"
        val file = imageFile(c, name)!!
        file.parentFile!!.mkdirs()
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { bitmap.recycle() }
        return name
    }
}

/** Shared image renderer for the editor and Glance. Bounded bitmap avoids launcher binder limits. */
object DiyRenderer {
    fun render(c: Context, style: DiyStyle, width: Int, height: Int): Bitmap {
        val w = width.coerceIn(100, 560); val h = height.coerceIn(100, 560)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(if(style.kind == "notes") 0xFFF5EEDC.toInt() else 0xFF153D3B.toInt())
        val source = DiyStore.imageFile(c, style.image)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        if(source != null) {
            val scale = max(w.toFloat()/source.width, h.toFloat()/source.height) * style.zoom.coerceIn(1f, 3f)
            val sw=source.width * scale; val sh=source.height * scale
            val left=(w-sw)*style.x.coerceIn(0f,1f); val top=(h-sh)*style.y.coerceIn(0f,1f)
            canvas.drawBitmap(source, null, RectF(left, top, left+sw, top+sh), paint)
            source.recycle()
        } else {
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF173E43.toInt(), style.accent.toInt(), 0xFF285F53.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint); paint.shader=null
        }
        val blur = if(style.kind == "glass") max(.25f,style.blur) else style.blur
        if(blur > .01f) {
            // Downsample filtering gives a smooth blur on API 26+, without native dependencies.
            val bw = max(2, (w / (1f + blur*65)).toInt()); val bh=max(2,(h/(1f+blur*65)).toInt())
            val reduced=Bitmap.createScaledBitmap(result,bw,bh,true)
            canvas.drawBitmap(reduced,null,Rect(0,0,w,h),paint); reduced.recycle()
        }
        if(style.kind == "notes") {
            paint.color=Color.argb(225,250,245,229); canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint)
            paint.color=Color.argb(32,23,107,82); paint.strokeWidth=1f
            for(y in 30 until h step 32) canvas.drawLine(0f,y.toFloat(),w.toFloat(),y.toFloat(),paint)
        } else {
            val darkness=style.shade.coerceIn(0f,.85f)
            paint.shader=LinearGradient(0f,0f,0f,h.toFloat(), Color.argb((darkness*200).toInt(),0,0,0), Color.argb((40+darkness*240).toInt().coerceAtMost(245),0,0,0),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint); paint.shader=null
            if(style.kind == "glass") {
                paint.color=Color.argb(42,255,255,255)
                canvas.drawRoundRect(RectF(10f,10f,w-10f,h-10f),24f,24f,paint)
            }
        }
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val out=Canvas(output)
        val clip=Path().apply { addRoundRect(RectF(0f,0f,w.toFloat(),h.toFloat()),28f,28f,Path.Direction.CW) }
        out.clipPath(clip)
        paint.alpha=(style.opacity.coerceIn(.15f,1f)*255).toInt()
        out.drawBitmap(result,0f,0f,paint); result.recycle()
        return output
    }
}

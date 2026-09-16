package cn.campus.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import cn.campus.core.ImageImportDraft
import cn.campus.core.ImageImportKind
import cn.campus.core.ImageOcrPage
import cn.campus.core.ImageOcrToken
import cn.campus.core.ImageScheduleParser
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object ImageScheduleRecognizer {
    suspend fun recognize(context:Context,uris:List<Uri>,firstMonday:String,kind:ImageImportKind=ImageImportKind.AUTO):ImageImportDraft {
        require(uris.size in 1..20){"请选择 1 至 20 张课表图片"}
        val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val pages=uris.mapIndexed { index,uri ->
                val loaded=withContext(Dispatchers.IO){loadBitmap(context,uri)}
                try {
                    val result=recognizer.process(InputImage.fromBitmap(loaded.bitmap,loaded.rotation)).awaitResult()
                    val tokens=result.textBlocks.mapNotNull { block ->
                        val box=block.boundingBox?:return@mapNotNull null
                        val confidence=block.lines.flatMap{it.elements}.map{it.confidence}.filter{it>0f}.average().takeIf{!it.isNaN()}?.toFloat()?:1f
                        ImageOcrToken(index,block.text,box.left.toFloat(),box.top.toFloat(),box.right.toFloat(),box.bottom.toFloat(),confidence)
                    }
                    require(tokens.size>=8){"第 ${index+1} 张图片文字太少或不清晰，请换一张更清楚的截图"}
                    ImageOcrPage(index,loaded.displayWidth,loaded.displayHeight,tokens)
                } finally { loaded.bitmap.recycle() }
            }
            return withContext(Dispatchers.Default){ImageScheduleParser.parse(pages,firstMonday,kind)}
        } finally { recognizer.close() }
    }

    private data class LoadedBitmap(val bitmap:Bitmap,val rotation:Int,val displayWidth:Int,val displayHeight:Int)

    private fun loadBitmap(context:Context,uri:Uri):LoadedBitmap {
        val resolver=context.contentResolver
        resolver.openAssetFileDescriptor(uri,"r")?.use { descriptor ->
            val length=descriptor.length
            require(length<0||length<=15L*1024*1024){"单张图片不能超过 15 MB"}
        }
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}?:error("无法打开图片")
        require(bounds.outWidth>0&&bounds.outHeight>0){"图片格式不受支持，请选择 JPG、PNG 或 WebP"}
        var sample=1
        while(bounds.outWidth.toLong()*bounds.outHeight/sample/sample>24_000_000L||maxOf(bounds.outWidth,bounds.outHeight)/sample>7000)sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
        val bitmap=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:error("图片解码失败")
        val rotation=runCatching {resolver.openInputStream(uri)?.use { input ->
            when(ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)){
                ExifInterface.ORIENTATION_ROTATE_90->90
                ExifInterface.ORIENTATION_ROTATE_180->180
                ExifInterface.ORIENTATION_ROTATE_270->270
                else->0
            }
        }?:0}.getOrDefault(0)
        val displayWidth=if(rotation==90||rotation==270)bitmap.height else bitmap.width
        val displayHeight=if(rotation==90||rotation==270)bitmap.width else bitmap.height
        return LoadedBitmap(bitmap,rotation,displayWidth,displayHeight)
    }

    private suspend fun <T> Task<T>.awaitResult():T=suspendCancellableCoroutine { continuation ->
        addOnSuccessListener{value->if(continuation.isActive)continuation.resume(value)}
        addOnFailureListener{error->if(continuation.isActive)continuation.resumeWithException(error)}
        addOnCanceledListener{continuation.cancel()}
    }
}

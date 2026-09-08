package cn.campus.schedule

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import cn.campus.core.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Content-based dispatch also accepts files shared with an incorrect MIME type or suffix. */
object ScheduleFileImporter {
    fun parse(context: Context, bytes: ByteArray, monday: String): ImportResult {
        require(bytes.size <= 8 * 1024 * 1024) { "文件超过 8 MB，请导出课表后重试" }
        if (!bytes.take(1024).toByteArray().toString(Charsets.ISO_8859_1).contains("%PDF-")) {
            return WordScheduleParser().parse(bytes, monday)
        }
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = try { PDDocument.load(bytes) } catch (_: Exception) {
            throw IllegalArgumentException("PDF 无法打开或需要密码，请重新导出未加密的完整课表")
        }
        return document.use { doc ->
            require(!doc.isEncrypted) { "暂不支持加密 PDF，请导出未加密的课表" }
            require(doc.numberOfPages in 1..40) { "PDF 最多支持 40 页，请只导出课表" }
            val pages = (0 until doc.numberOfPages).map { index ->
                val page = doc.getPage(index)
                require(page.rotation == 0) { "暂不支持旋转页面，请按原始方向导出 PDF" }
                val glyphs = mutableListOf<PdfGlyph>()
                object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        require(glyphs.size < 100000) { "PDF 文字过多，请只导出课表" }
                        glyphs += PdfGlyph(text.unicode, text.xDirAdj.toDouble(), (text.yDirAdj - text.heightDir / 2).toDouble())
                    }
                }.apply { startPage = index + 1; endPage = index + 1 }.getText(doc)
                val borders = Borders(page)
                borders.processPage(page)
                PdfPage(glyphs, borders.lines)
            }
            PdfScheduleParser().parse(pages, monday)
        }
    }

    private class Borders(private val source: PDPage) : PDFGraphicsStreamEngine(source) {
        val lines = mutableListOf<PdfLine>()
        private val path = mutableListOf<PdfLine>()
        private var point = PointF()
        private var first = PointF()
        private fun edge(a: PointF, b: PointF) {
            val box = source.cropBox
            require(path.size < 20000) { "PDF 图形过于复杂，请只导出课表" }
            path += PdfLine((a.x - box.lowerLeftX).toDouble(), (box.upperRightY - a.y).toDouble(),
                (b.x - box.lowerLeftX).toDouble(), (box.upperRightY - b.y).toDouble())
        }
        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            edge(p0,p1); edge(p1,p2); edge(p2,p3); edge(p3,p0); point=p0; first=p0
        }
        override fun moveTo(x: Float,y: Float) { point=PointF(x,y); first=point }
        override fun lineTo(x: Float,y: Float) { val next=PointF(x,y); edge(point,next); point=next }
        override fun curveTo(x1: Float,y1: Float,x2: Float,y2: Float,x3: Float,y3: Float) { point=PointF(x3,y3) }
        override fun getCurrentPoint(): PointF = point
        override fun closePath() { edge(point,first); point=first }
        override fun endPath() { path.clear() }
        override fun strokePath() { require(lines.size + path.size < 20000) { "PDF 边框过多" }; lines.addAll(path); path.clear() }
        override fun fillPath(windingRule: Path.FillType) { path.clear() }
        override fun fillAndStrokePath(windingRule: Path.FillType) = strokePath()
        override fun clip(windingRule: Path.FillType) = Unit
        override fun drawImage(pdImage: PDImage) = Unit
        override fun shadingFill(shadingName: COSName) = Unit
    }
}

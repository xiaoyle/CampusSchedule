package cn.campus.schedule

import android.content.Context
import cn.campus.core.ImportResult
import cn.campus.core.Schedule
import cn.campus.core.dataJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

/** Only normalized course data is cached, never raw school HTML, exported student headers or cookies. */
object SchoolImportCache {
    const val TOKEN = "schoolImportToken"
    const val MONDAY = "firstMonday"
    @Serializable private data class Payload(val schedule: Schedule, val warnings: List<String>)
    private fun directory(context: Context) = File(context.cacheDir, "school-imports").apply { mkdirs() }
    @Synchronized fun write(context: Context, result: ImportResult): String {
        cleanup(context)
        val token = UUID.randomUUID().toString()
        File(directory(context), "$token.json").writeText(dataJson.encodeToString(Payload(result.schedule, result.warnings)))
        return token
    }
    @Synchronized fun consume(context: Context, token: String): ImportResult {
        require(token.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))) { "导入结果无效，请重新导入" }
        val file = File(directory(context), "$token.json")
        try {
            require(file.exists() && System.currentTimeMillis() - file.lastModified() < 86_400_000) { "导入结果已过期，请重新导入" }
            val payload = dataJson.decodeFromString<Payload>(file.inputStream().use { it.readLimited() }.toString(Charsets.UTF_8))
            return ImportResult(payload.schedule, payload.warnings)
        } finally { file.delete() }
    }
    @Synchronized fun cleanup(context: Context) {
        directory(context).listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() >= 86_400_000 }?.forEach { it.delete() }
    }
}

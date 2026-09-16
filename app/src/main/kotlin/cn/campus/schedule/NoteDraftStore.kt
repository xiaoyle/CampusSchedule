package cn.campus.schedule

import android.content.Context
import cn.campus.core.NoteDraft
import cn.campus.core.dataJson
import cn.campus.core.stableId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

object NoteDraftStore {
    private fun directory(context: Context) = File(context.filesDir, "note-drafts").apply { mkdirs() }
    private fun file(context: Context, noteId: String) = File(directory(context), "${stableId(noteId)}.json")

    suspend fun save(context: Context, draft: NoteDraft) = withContext(Dispatchers.IO) {
        val target = file(context, draft.note.id)
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeText(dataJson.encodeToString(draft), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
    }

    suspend fun read(context: Context, noteId: String): NoteDraft? = withContext(Dispatchers.IO) {
        runCatching { dataJson.decodeFromString<NoteDraft>(file(context, noteId).readText(Charsets.UTF_8)) }.getOrNull()
    }

    suspend fun latest(context: Context): NoteDraft? = withContext(Dispatchers.IO) {
        directory(context).listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { candidate -> runCatching { dataJson.decodeFromString<NoteDraft>(candidate.readText(Charsets.UTF_8)) }.getOrNull() }
            .maxByOrNull { it.savedAt }
    }

    suspend fun delete(context: Context, noteId: String) = withContext(Dispatchers.IO) {
        file(context, noteId).delete()
        Unit
    }
}

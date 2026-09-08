package cn.campus.schedule

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.campus.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.InputStream

fun InputStream.readLimited(limit: Int = 8 * 1024 * 1024): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = read(buffer)
    while (count >= 0) {
        require(output.size() + count <= limit) { "文件超过 8 MB，请选择教务系统导出的课表" }
        output.write(buffer, 0, count)
        count = read(buffer)
    }
    return output.toByteArray()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as ScheduleApp
    private val _data = MutableStateFlow(AppData())
    val data = _data.asStateFlow()
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val preview = MutableStateFlow<ImportResult?>(null)
    private var baseline = AppData()
    init { viewModelScope.launch { application.store.data.catch { message.value = "读取失败，请重启应用重试"; loading.value = false }.collect { _data.value = it; loading.value = false } } }
    fun importUri(uri: Uri, monday: String) = task {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readLimited() }
            ?: error("无法打开文件，请先把文件保存到手机")
        parse(bytes, monday)
    }
    private suspend fun parse(bytes: ByteArray, monday: String) {
        val result = ScheduleFileImporter.parse(application, bytes, monday)
        baseline = application.store.read()
        preview.value = result
    }
    fun confirmImport() = task {
        val incoming = preview.value?.schedule ?: return@task
        application.store.update { current ->
            require(current.schedule == baseline.schedule && current.edits == baseline.edits) { "课表已发生变化，请重新导入后核对" }
            ScheduleEngine.merge(current, incoming)
        }
        preview.value = null
        message.value = "课表已保存，可添加桌面组件"
        refreshAfterSave()
    }
    fun edit(edit: LessonEdit, onSuccess: () -> Unit = {}) = task {
        ScheduleEngine.validateEdit(edit)
        application.store.update { state -> state.copy(edits = state.edits.filterNot { it.ruleId == edit.ruleId && it.originalDate == edit.originalDate } + edit) }
        message.value = "本次课程已更新"
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun restore(lesson: Occurrence, onSuccess: () -> Unit = {}) = task {
        application.store.update { state -> state.copy(edits = state.edits.filterNot { it.ruleId == lesson.ruleId && it.originalDate == lesson.originalDate }) }
        message.value = "已恢复本次课程的学校安排"
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun alarm(enabled: Boolean) = task {
        application.store.update {it.copy(alarmEnabled=enabled)}
        if(!enabled) AlarmPlaybackService.stop(application)
    }
    fun reminder(minutes: Int) = task {
        require(minutes in listOf(-1, 5, 10, 15, 30))
        if(minutes<0) AlarmPlaybackService.stop(application)
        application.store.update { it.copy(reminderMinutes = minutes) }
        refreshAfterSave()
    }
    fun refreshReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ReminderScheduler.refresh(application) }.onFailure { RecoveryWorker.once(application) }
        }
    }
    private suspend fun refreshAfterSave() {
        AlarmPlaybackService.stop(application)
        application.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        runCatching { ReminderScheduler.refresh(application) }.onFailure {
            RecoveryWorker.once(application)
            message.value = "数据已保存；桌面与提醒刷新待重试，请重新打开应用"
        }
    }
    private fun task(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) { message.value = e.message?.take(200) ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }
}

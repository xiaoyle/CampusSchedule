package cn.campus.schedule

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.campus.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.InputStream
import java.time.Instant
import android.os.SystemClock

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
    val imageDraft = MutableStateFlow<ImageImportDraft?>(null)
    private var baseline = AppData()
    init { viewModelScope.launch { application.store.data.catch { message.value = "读取失败，请重启应用重试"; loading.value = false }.collect { _data.value = it; loading.value = false } } }
    fun importUri(uri: Uri, monday: String) = task {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readLimited() }
            ?: error("无法打开文件，请先把文件保存到手机")
        parse(bytes, monday)
    }
    fun importImages(uris:List<Uri>,monday:String,kind:ImageImportKind=ImageImportKind.AUTO)=task {
        require(runCatching{java.time.LocalDate.parse(monday).dayOfWeek==java.time.DayOfWeek.MONDAY}.getOrDefault(false)){"请先填写正确的第一教学周周一"}
        imageDraft.value=ImageScheduleRecognizer.recognize(application,uris,monday,kind)
        message.value="图片识别完成，请逐项校对"
    }
    fun replaceImageRow(row:ImageImportRow){
        imageDraft.value=imageDraft.value?.let{draft->draft.copy(rows=draft.rows.map{if(it.id==row.id)row else it})}
    }
    fun removeImageRow(id:String){
        imageDraft.value=imageDraft.value?.let{draft->draft.copy(rows=draft.rows.filterNot{it.id==id})}
    }
    fun discardImageDraft(){imageDraft.value=null}
    fun acceptImageDraft()=task {
        val draft=imageDraft.value?:return@task
        val result=draft.toImportResult()
        baseline=application.store.read()
        imageDraft.value=null
        preview.value=result
    }
    fun importSchool(token: String) = task {
        val result = SchoolImportCache.consume(application, token)
        baseline = application.store.read()
        preview.value = result
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
    fun editWithColor(edit: LessonEdit, color: Long?, onSuccess: () -> Unit = {}) = task {
        ScheduleEngine.validateEdit(edit)
        application.store.update { state -> state.copy(
            edits=state.edits.filterNot { it.ruleId==edit.ruleId && it.originalDate==edit.originalDate }+edit,
            courseColors=if(color==null) state.courseColors-edit.ruleId else state.courseColors+(edit.ruleId to color)
        ) }
        message.value="本次课程已更新"
        withContext(Dispatchers.Main) {onSuccess()}
        refreshAfterSave()
    }
    fun saveManual(lesson: ManualLesson, onSuccess: () -> Unit = {}) = task {
        ScheduleEngine.validateManualLesson(lesson)
        application.store.update {state->state.copy(manualLessons=state.manualLessons.filterNot{it.id==lesson.id}+lesson)}
        message.value=if(_data.value.manualLessons.any{it.id==lesson.id}) "自建课程已更新" else "单次课程已添加"
        withContext(Dispatchers.Main){onSuccess()}
        refreshAfterSave()
    }
    fun saveManualChange(original:ManualLesson?,draft:ManualLesson,scope:ManualLessonEditScope,index:Int,onSuccess:()->Unit={})=task {
        val changed=if(original==null)draft else ScheduleEngine.updateManualLesson(original,draft,scope,index)
        ScheduleEngine.validateManualLesson(changed)
        application.store.update{state->state.copy(manualLessons=state.manualLessons.filterNot{it.id==changed.id}+changed)}
        message.value=when{original==null&&changed.repeatCount>1->"每周课程已添加，共 ${changed.repeatCount} 次";original==null->"单次课程已添加";scope==ManualLessonEditScope.INSTANCE->"本次课程已更新";scope==ManualLessonEditScope.FUTURE->"本次及以后已更新";else->"课程系列已更新"}
        withContext(Dispatchers.Main){onSuccess()};refreshAfterSave()
    }
    fun deleteManual(lesson: ManualLesson, onSuccess: () -> Unit = {}) = task {
        application.store.update {state->state.copy(manualLessons=state.manualLessons.filterNot{it.id==lesson.id})}
        withContext(Dispatchers.Main){onSuccess()}
        refreshAfterSave()
    }
    fun deleteManualChange(lesson:ManualLesson,scope:ManualLessonEditScope,index:Int,onSuccess:()->Unit={})=task {
        val changed=ScheduleEngine.deleteManualLesson(lesson,scope,index)
        application.store.update{state->state.copy(manualLessons=state.manualLessons.filterNot{it.id==lesson.id}+listOfNotNull(changed))}
        message.value=when(scope){ManualLessonEditScope.INSTANCE->"已删除本次课程";ManualLessonEditScope.FUTURE->"已删除本次及以后课程";ManualLessonEditScope.SERIES->"已删除课程系列"}
        withContext(Dispatchers.Main){onSuccess()};refreshAfterSave()
    }
    fun saveStudyTask(studyTask: StudyTask, onSuccess: () -> Unit = {}) = task {
        StudyTaskEngine.validate(studyTask)
        val existed=_data.value.studyTasks.any { it.id==studyTask.id }
        application.store.update { state ->
            state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.filterNot { it.id==studyTask.id }+studyTask))
        }
        message.value=if(existed) "待办已更新" else "待办已添加"
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun saveStudyTaskOccurrence(studyTask: StudyTask, occurrence: TaskOccurrence, entireSeries: Boolean, onSuccess: () -> Unit = {}) = task {
        val changed = if (entireSeries || !occurrence.repeated) studyTask.copy(
            id=occurrence.taskId,
            completedAt=if(occurrence.repeated) null else occurrence.completedAt,
            instanceStates=_data.value.studyTasks.firstOrNull {it.id==occurrence.taskId}?.instanceStates.orEmpty(),
            createdAt=_data.value.studyTasks.firstOrNull {it.id==occurrence.taskId}?.createdAt ?: studyTask.createdAt
        ) else {
            val current=_data.value.studyTasks.first {it.id==occurrence.taskId}
            val state=TaskInstanceState(
                occurrenceKey=occurrence.key,
                completedAt=occurrence.completedAt,
                completedSubtaskIds=occurrence.completedSubtaskIds.toList(),
                override=TaskInstanceOverride(
                    studyTask.title,studyTask.type,studyTask.courseRuleId,studyTask.courseTitle,
                    studyTask.dueAt ?: error("本次任务需要截止时间"),studyTask.priority,studyTask.note,
                    studyTask.remindBeforeMinutes,studyTask.subtasks,studyTask.estimatedMinutes
                )
            )
            current.copy(instanceStates=current.instanceStates.filterNot {it.occurrenceKey==occurrence.key}+state)
        }
        StudyTaskEngine.validate(changed)
        application.store.update {state->state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.filterNot {it.id==occurrence.taskId}+changed))}
        message.value="待办已更新"
        withContext(Dispatchers.Main){onSuccess()}
        refreshAfterSave()
    }
    fun completeStudyTask(studyTask: StudyTask, completed: Boolean, onSuccess: () -> Unit = {}) = task {
        val changed=studyTask.copy(completedAt=if(completed) Instant.now().toString() else null)
        application.store.update { state ->
            state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.map { if(it.id==studyTask.id) changed else it }))
        }
        message.value=if(completed) "任务已完成" else "任务已恢复"
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun completeTaskOccurrence(occurrence:TaskOccurrence,completed:Boolean,onSuccess:()->Unit={})=task {
        application.store.update {state->state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.map {task->
            if(task.id!=occurrence.taskId) task
            else if(!occurrence.repeated) {
                val old=task.instanceStates.firstOrNull{it.occurrenceKey==occurrence.key}
                val changed=(old?:TaskInstanceState(occurrence.key)).copy(completedSubtaskIds=if(completed)occurrence.subtasks.map{it.id}else old?.completedSubtaskIds.orEmpty())
                task.copy(completedAt=if(completed)Instant.now().toString() else null,instanceStates=task.instanceStates.filterNot{it.occurrenceKey==occurrence.key}+changed)
            }
            else {
                val old=task.instanceStates.firstOrNull {it.occurrenceKey==occurrence.key}
                val changed=(old ?: TaskInstanceState(occurrence.key)).copy(completedAt=if(completed)Instant.now().toString() else null,completedSubtaskIds=if(completed)occurrence.subtasks.map{it.id}else old?.completedSubtaskIds.orEmpty())
                task.copy(instanceStates=task.instanceStates.filterNot {it.occurrenceKey==occurrence.key}+changed)
            }
        }))}
        message.value=if(completed)"任务已完成" else "任务已恢复"
        withContext(Dispatchers.Main){onSuccess()}
        refreshAfterSave()
    }
    fun toggleSubtask(occurrence:TaskOccurrence,subtaskId:String,completed:Boolean)=task {
        application.store.update {state->state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.map {task->
            if(task.id!=occurrence.taskId)task else {
                val old=task.instanceStates.firstOrNull {it.occurrenceKey==occurrence.key}
                val ids=(old?.completedSubtaskIds.orEmpty().toSet().let {if(completed)it+subtaskId else it-subtaskId}).toList()
                val changed=(old ?: TaskInstanceState(occurrence.key)).copy(completedSubtaskIds=ids)
                task.copy(instanceStates=task.instanceStates.filterNot {it.occurrenceKey==occurrence.key}+changed)
            }
        }))}
        refreshAfterSave()
    }
    fun deleteTaskOccurrence(occurrence:TaskOccurrence,entireSeries:Boolean,onSuccess:()->Unit={})=task {
        application.store.update {state->
            if(entireSeries||!occurrence.repeated) state.copy(studyTasks=state.studyTasks.filterNot {it.id==occurrence.taskId})
            else state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.map {task->
                if(task.id!=occurrence.taskId)task else {
                    val old=task.instanceStates.firstOrNull {it.occurrenceKey==occurrence.key}
                    val changed=(old ?: TaskInstanceState(occurrence.key)).copy(deleted=true)
                    task.copy(instanceStates=task.instanceStates.filterNot {it.occurrenceKey==occurrence.key}+changed)
                }
            }))
        }
        withContext(Dispatchers.Main){onSuccess()}
        refreshAfterSave()
    }
    fun deleteStudyTask(studyTask: StudyTask, onSuccess: () -> Unit = {}) = task {
        application.store.update { state -> state.copy(studyTasks=state.studyTasks.filterNot { it.id==studyTask.id }) }
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun clearCompletedTasks(onSuccess: () -> Unit = {}) = task {
        application.store.update { state -> state.copy(studyTasks=state.studyTasks.mapNotNull { item->
            if(item.repeatRule==null) item.takeIf {it.completedAt==null}
            else item.copy(instanceStates=item.instanceStates.map {instance->if(instance.completedAt!=null)instance.copy(completedAt=null,deleted=true) else instance})
        }) }
        message.value="已清除完成记录"
        withContext(Dispatchers.Main) { onSuccess() }
        refreshAfterSave()
    }
    fun saveNoteCategory(category:NoteCategory)=task {
        require(category.title.trim().isNotEmpty()){ "请填写栏目名称" }
        require(category.title.length<=20){ "栏目名称不能超过20字" }
        application.store.update { state ->
            state.copy(noteCategories=state.noteCategories.filterNot{it.id==category.id}+category.copy(title=category.title.trim()))
        }
        message.value="笔记栏目已保存"
    }
    fun deleteNoteCategory(category:NoteCategory)=task {
        application.store.update { state -> state.copy(
            noteCategories=state.noteCategories.filterNot{it.id==category.id},
            studyNotes=state.studyNotes.map{if(it.categoryId==category.id)it.copy(categoryId="")else it}
        ) }
        message.value="栏目已删除，笔记已移到未分类"
    }
    fun saveStudyNote(note:StudyNote,onSuccess:()->Unit={})=task {
        require(note.title.trim().isNotEmpty()){ "请填写笔记标题" }
        require(note.title.length<=80){ "笔记标题不能超过80字" }
        require(note.content.length<=20_000){ "笔记正文不能超过20000字" }
        note.appearance?.let(::validateNoteAppearance)
        application.store.update { state ->
            val safeCategory=note.categoryId.takeIf{id->state.noteCategories.any{it.id==id}}.orEmpty()
            state.copy(studyNotes=state.studyNotes.filterNot{it.id==note.id}+note.copy(categoryId=safeCategory,title=note.title.trim()))
        }
        message.value="笔记已保存"
        withContext(Dispatchers.Main){onSuccess()}
    }
    fun deleteStudyNote(note:StudyNote,onSuccess:()->Unit={})=task {
        application.store.update { state -> state.copy(studyNotes=state.studyNotes.filterNot{it.id==note.id}) }
        message.value="笔记已删除"
        withContext(Dispatchers.Main){onSuccess()}
    }
    fun saveNoteStyle(style:NoteStyle)=task {
        validateNoteAppearance(NoteAppearance(style.paper,style.accent,style.font,style.fontScale,style.lineSpacing,style.pagePaddingDp,style.patternAlpha,style.previewLines,style.paperTint))
        application.store.update { it.copy(noteStyle=style) }
        message.value="笔记外观已更新"
    }
    private fun validateNoteAppearance(value:NoteAppearance){
        require(value.fontScale in .85f..1.4f){"笔记字号设置无效"}
        require(value.lineSpacing in 1.2f..2f){"笔记行距设置无效"}
        require(value.pagePaddingDp in 12..32){"笔记页面边距设置无效"}
        require(value.patternAlpha in .08f..0.5f){"笔记纹理强度设置无效"}
        require(value.previewLines in 2..7){"笔记预览行数设置无效"}
    }
    fun saveLearningGoal(target:Int)=task {
        require(target in 1..30)
        application.store.update {it.copy(learningGoal=LearningGoal(target))}
        message.value="每周目标已保存"
        refreshAfterSave()
    }
    fun saveFocusGoal(target:Int)=task {
        require(target in 30..2100&&target%30==0){"每周专注目标须为30分钟的倍数"}
        application.store.update{it.copy(focusSettings=it.focusSettings.copy(weeklyMinutesTarget=target))}
        message.value="专注目标已保存"
    }
    fun startFocus(title:String,mode:FocusMode,minutes:Int?,sound:AmbientSound,volume:Float,courseId:String?=null,taskId:String?=null,occurrenceKey:String?=null,onSuccess:()->Unit={})=task {
        require(title.trim().isNotEmpty()){"请填写专注目标"}
        require(mode==FocusMode.STOPWATCH||minutes in 1..180){"倒计时须为1至180分钟"}
        val now=Instant.now();val active=ActiveFocusState(
            id=java.util.UUID.randomUUID().toString(),mode=mode,title=title.trim().take(60),courseRuleId=courseId,taskId=taskId,taskOccurrenceKey=occurrenceKey,
            plannedSeconds=minutes?.times(60L),startedAt=now.toString(),runStartedAt=now.toString(),runStartedElapsedMs=SystemClock.elapsedRealtime(),ambientSound=sound,volume=volume.coerceIn(0f,1f)
        );FocusEngine.validate(active)
        application.store.update{state->require(state.activeFocus==null){"已有专注正在进行"};state.copy(activeFocus=active,focusSettings=state.focusSettings.copy(lastMinutes=minutes?:state.focusSettings.lastMinutes,ambientSound=sound,ambientVolume=volume.coerceIn(0f,1f)))}
        FocusRuntime.refresh(application);withContext(Dispatchers.Main){onSuccess()}
    }
    fun pauseFocus()=task {val current=application.store.read().activeFocus?:return@task;application.store.update{it.copy(activeFocus=FocusEngine.pause(current,Instant.now(),SystemClock.elapsedRealtime()))};FocusRuntime.refresh(application)}
    fun resumeFocus()=task {val current=application.store.read().activeFocus?:return@task;application.store.update{it.copy(activeFocus=FocusEngine.resume(current,Instant.now(),SystemClock.elapsedRealtime()))};FocusRuntime.refresh(application)}
    fun finishFocus(status:FocusStatus=FocusStatus.STOPPED,onSuccess:(FocusSession)->Unit={})=task {
        val current=application.store.read().activeFocus?:return@task;FocusRuntime.complete(application,current,status)
        val session=application.store.read().focusSessions.lastOrNull{it.id==current.id}?:return@task
        withContext(Dispatchers.Main){onSuccess(session)}
    }
    fun deleteFocusSession(id:String)=task {application.store.update{state->AchievementEngine.evaluate(state.copy(focusSessions=state.focusSessions.filterNot{it.id==id})).first};message.value="专注记录已删除";StudyWidget().updateAllSafe(application)}
    fun saveCustomAchievement(definition:AchievementDefinition,onSuccess:()->Unit={})=task {
        require(!definition.builtIn){"内置成就不能修改"};require(definition.name.trim().length in 1..12){"成就名称须为1至12字"};require(definition.description.length<=40){"成就说明不能超过40字"}
        require(definition.unlockMode==AchievementUnlockMode.MANUAL||definition.metric!=null){"请选择自动解锁条件"};require(definition.target>0){"目标数值须大于0"}
        application.store.update{state->require(state.customAchievements.any{it.id==definition.id}||state.customAchievements.size<50){"最多创建50个自定义成就"};AchievementEngine.evaluate(state.copy(customAchievements=state.customAchievements.filterNot{it.id==definition.id}+definition)).first}
        message.value="成就已保存";withContext(Dispatchers.Main){onSuccess()}
    }
    fun manualUnlockAchievement(id:String)=task {application.store.update{AchievementEngine.manualUnlock(it,id)};message.value="成就已点亮"}
    fun resetAchievement(id:String)=task {application.store.update{state->state.copy(achievementProgress=state.achievementProgress.filterNot{it.achievementId==id},featuredAchievementIds=state.featuredAchievementIds-id)};message.value="成就已重置"}
    fun deleteAchievement(id:String,onSuccess:()->Unit={})=task {application.store.update{state->state.copy(customAchievements=state.customAchievements.filterNot{it.id==id},achievementProgress=state.achievementProgress.filterNot{it.achievementId==id},featuredAchievementIds=state.featuredAchievementIds-id)};withContext(Dispatchers.Main){onSuccess()};message.value="自定义成就已删除"}
    fun restoreAchievement(definition:AchievementDefinition,progress:AchievementProgress?,featured:Boolean)=task {application.store.update{state->state.copy(customAchievements=state.customAchievements.filterNot{it.id==definition.id}+definition,achievementProgress=state.achievementProgress.filterNot{it.achievementId==definition.id}+listOfNotNull(progress),featuredAchievementIds=if(featured)(state.featuredAchievementIds+definition.id).distinct().takeLast(3) else state.featuredAchievementIds)};message.value="已撤销删除"}
    fun featureAchievement(id:String,featured:Boolean)=task {application.store.update{state->val ids=if(featured)(state.featuredAchievementIds+id).distinct().takeLast(3) else state.featuredAchievementIds-id;state.copy(featuredAchievementIds=ids)};message.value="精选徽章已更新"}
    fun moveFeaturedAchievement(id:String,direction:Int)=task {application.store.update{state->val ids=state.featuredAchievementIds.toMutableList();val from=ids.indexOf(id);val to=(from+direction).coerceIn(0,ids.lastIndex);if(from>=0&&from!=to){val value=ids.removeAt(from);ids.add(to,value)};state.copy(featuredAchievementIds=ids)};message.value="精选顺序已更新"}
    fun markCelebrationSeen(id:String)=viewModelScope.launch(Dispatchers.IO){application.store.update{state->state.copy(achievementProgress=state.achievementProgress.map{if(it.achievementId==id)it.copy(celebrationSeen=true)else it})}}
    fun cancel(lesson: Occurrence, onSuccess: () -> Unit = {}) = task {
        val edit=LessonEdit(lesson.ruleId,lesson.originalDate,cancelled=true,date=lesson.date.toString(),start=lesson.start.toString(),end=lesson.end.toString())
        application.store.update {state->state.copy(edits=state.edits.filterNot{it.ruleId==edit.ruleId&&it.originalDate==edit.originalDate}+edit)}
        withContext(Dispatchers.Main){onSuccess()}
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
        application.store.update {AchievementEngine.evaluate(it).first}
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

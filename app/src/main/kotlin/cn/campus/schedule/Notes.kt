package cn.campus.schedule

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.campus.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val noteColors = listOf(0xFF176B52, 0xFF1976A3, 0xFFB46A22, 0xFF8B5BA7, 0xFFC85566)

private fun NoteStyle.appearance() = NoteAppearance(paper, accent, font, fontScale, lineSpacing, pagePaddingDp, patternAlpha, previewLines, paperTint)
private fun NoteAppearance.toStyle(compact: Boolean) = NoteStyle(paper, accent, compact, font, fontScale, lineSpacing, pagePaddingDp, patternAlpha, previewLines, paperTint)

@Composable
fun NotesWorkspace(
    data: AppData,
    busy: Boolean,
    onBack: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onSaveCategory: (NoteCategory) -> Unit,
    onDeleteCategory: (NoteCategory) -> Unit,
    onSaveNote: (StudyNote, () -> Unit) -> Unit,
    onDeleteNote: (StudyNote, () -> Unit) -> Unit,
    onSaveStyle: (NoteStyle) -> Unit,
    onPublish: (StudyNote) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf("hub") }
    var noteId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftCategory by rememberSaveable { mutableStateOf("") }
    var latestDraft by remember { mutableStateOf<NoteDraft?>(null) }

    LaunchedEffect(destination) { onFullscreenChanged(destination != "hub") }
    DisposableEffect(Unit) { onDispose { onFullscreenChanged(false) } }
    LaunchedEffect(data.studyNotes, destination) {
        if (destination == "hub") {
            latestDraft = NoteDraftStore.latest(context)?.takeIf { candidate ->
                val official = data.studyNotes.firstOrNull { it.id == candidate.note.id }
                official == null || candidate.savedAt > official.updatedAt
            }
        }
    }

    fun openEditor(id: String, category: String = "") {
        noteId = id
        draftCategory = category
        destination = "editor"
    }

    when (destination) {
        "reader" -> {
            val note = data.studyNotes.firstOrNull { it.id == noteId }
            if (note == null) LaunchedEffect(noteId) { destination = "hub" }
            else NoteReaderScreen(note, data.noteCategories.firstOrNull { it.id == note.categoryId }, data.noteStyle.appearance(), { destination = "hub" }, { destination = "editor" },{onPublish(note)})
        }
        "editor" -> {
            val base = data.studyNotes.firstOrNull { it.id == noteId } ?: StudyNote(noteId ?: UUID.randomUUID().toString(), draftCategory, "")
            NoteEditorLoader(
                base, data.noteCategories, data.noteStyle.appearance(), busy, onMessage,
                onBack = { destination = if (data.studyNotes.any { it.id == base.id }) "reader" else "hub" },
                onSave = { note -> onSaveNote(note) { scope.launch { NoteDraftStore.delete(context, note.id) }; noteId = note.id; destination = "reader" } },
                onDelete = { note -> onDeleteNote(note) { scope.launch { NoteDraftStore.delete(context, note.id) }; destination = "hub" } }
            )
        }
        "appearance" -> NoteAppearanceScreen(
            "笔记默认外观", data.noteStyle.appearance(), data.noteStyle.compact,
            onBack = { destination = "hub" },
            onSave = { appearance, compact -> onSaveStyle(appearance.toStyle(compact ?: data.noteStyle.compact)); destination = "hub" }
        )
        else -> NotesHub(
            data, busy, latestDraft, onBack,
            onOpen = { noteId = it.id; destination = "reader" },
            onNew = { openEditor(UUID.randomUUID().toString(), it) },
            onResumeDraft = { openEditor(it.note.id, it.note.categoryId) },
            onAppearance = { destination = "appearance" },
            onSaveCategory, onDeleteCategory
        )
    }
}

@Composable
private fun NotesHub(
    data: AppData,
    busy: Boolean,
    latestDraft: NoteDraft?,
    onBack: () -> Unit,
    onOpen: (StudyNote) -> Unit,
    onNew: (String) -> Unit,
    onResumeDraft: (NoteDraft) -> Unit,
    onAppearance: () -> Unit,
    onSaveCategory: (NoteCategory) -> Unit,
    onDeleteCategory: (NoteCategory) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryDialog by remember { mutableStateOf<NoteCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<NoteCategory?>(null) }
    BackHandler(onBack = onBack)
    val categories = remember(data.noteCategories) { data.noteCategories.sortedWith(compareBy<NoteCategory> { it.order }.thenBy { it.title }) }
    val notes = remember(data.studyNotes, selectedCategory) {
        data.studyNotes.asSequence().filter { selectedCategory == null || it.categoryId == selectedCategory }
            .sortedWith(compareByDescending<StudyNote> { it.pinned }.thenByDescending { it.updatedAt }).toList()
    }
    val global = data.noteStyle.appearance()

    Box(Modifier.fillMaxSize().background(notePaperColor(global))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(if (data.noteStyle.compact) 9.dp else 14.dp)
        ) {
            item(key = "title", contentType = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("灵感笔记", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("阅读、整理，再进入完整页面书写", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onAppearance) { Icon(Icons.Outlined.Palette, "笔记外观") }
                }
            }
            latestDraft?.let { draft ->
                item(key = "draft", contentType = "draft") {
                    ElevatedCard(onClick = { onResumeDraft(draft) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("继续未保存的笔记", fontWeight = FontWeight.Bold)
                                Text(draft.note.title.ifBlank { "未命名草稿" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                }
            }
            item(key = "categories", contentType = "filters") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedCategory == null, { selectedCategory = null }, { Text("全部 ${data.studyNotes.size}") })
                    categories.forEach { category ->
                        FilterChip(
                            selectedCategory == category.id, { selectedCategory = category.id }, { Text(category.title) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(Color(category.color), RoundedCornerShape(50))) },
                            trailingIcon = { Icon(Icons.Outlined.Edit, "编辑栏目", Modifier.size(16.dp).clickable { categoryDialog = category }) }
                        )
                    }
                    AssistChip(
                        onClick = { categoryDialog = NoteCategory(UUID.randomUUID().toString(), "", global.accent, categories.size) },
                        label = { Text("新建栏目") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }
                    )
                }
            }
            if (notes.isEmpty()) item(key = "empty", contentType = "empty") {
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.NoteAdd, null, tint = Color(global.accent), modifier = Modifier.size(34.dp))
                        Text(if (selectedCategory == null) "写下第一篇笔记" else "这个栏目还是空的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("内容只保存在本机，可用纯文本或 Markdown。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onNew(selectedCategory.orEmpty()) }, enabled = !busy) { Text("新建笔记") }
                    }
                }
            } else items(notes, key = { it.id }, contentType = { "note" }) { note ->
                NoteCard(note, categories.firstOrNull { it.id == note.categoryId }, data.noteStyle) { onOpen(note) }
            }
        }
        FloatingActionButton(
            onClick = { onNew(selectedCategory.orEmpty()) }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = Color(global.accent), contentColor = readableColor(Color(global.accent))
        ) { Icon(Icons.Outlined.EditNote, "新建笔记") }
    }

    categoryDialog?.let { category ->
        CategoryEditorDialog(category, busy, { categoryDialog = null }, { onSaveCategory(it); categoryDialog = null }, { deleteCategory = category; categoryDialog = null })
    }
    deleteCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteCategory = null }, title = { Text("删除“${category.title}”？") }, text = { Text("栏目中的笔记会保留，并移动到“未分类”。") },
            confirmButton = { Button(onClick = { onDeleteCategory(category); if (selectedCategory == category.id) selectedCategory = null; deleteCategory = null }, enabled = !busy) { Text("删除栏目") } },
            dismissButton = { TextButton(onClick = { deleteCategory = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun NoteCard(note: StudyNote, category: NoteCategory?, global: NoteStyle, onClick: () -> Unit) {
    val appearance = note.appearance ?: global.appearance()
    val accent = Color(category?.color ?: appearance.accent)
    val lines = if (global.compact) 2 else appearance.previewLines
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = notePaperColor(appearance))) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(5.dp).heightIn(min = if (global.compact) 88.dp else 112.dp).background(accent))
            Column(Modifier.weight(1f).padding(if (global.compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = noteTextColor(appearance))
                    if (note.pinned) Icon(Icons.Outlined.PushPin, "已置顶", tint = accent, modifier = Modifier.size(18.dp))
                }
                if (note.content.isNotBlank()) Text(notePreview(note), maxLines = lines, overflow = TextOverflow.Ellipsis, color = noteTextColor(appearance).copy(alpha = .76f), fontFamily = noteFont(appearance.font))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category?.title ?: "未分类", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(if (note.contentMode == NoteContentMode.MARKDOWN) "Markdown" else "纯文本", style = MaterialTheme.typography.labelSmall, color = noteTextColor(appearance).copy(alpha = .65f))
                    Text(noteTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = noteTextColor(appearance).copy(alpha = .65f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteReaderScreen(note: StudyNote, category: NoteCategory?, global: NoteAppearance, onBack: () -> Unit, onEdit: () -> Unit,onPublish:()->Unit) {
    BackHandler(onBack = onBack)
    val appearance = note.appearance ?: global
    var markdownBlocks by remember(note.id,note.content) { mutableStateOf<List<MarkdownBlock>?>(null) }
    LaunchedEffect(note.id,note.content,note.contentMode) {
        markdownBlocks=if(note.contentMode==NoteContentMode.MARKDOWN)withContext(Dispatchers.Default){NoteMarkdown.parse(note.content)}else emptyList()
    }
    Scaffold(
        containerColor = notePaperColor(appearance),
        topBar = {
            TopAppBar(
                title = { Text(category?.title ?: "未分类", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回笔记列表") } },
                actions = { TextButton(onClick = onPublish) { Icon(Icons.Outlined.Public, null); Spacer(Modifier.width(4.dp)); Text("发布") };TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(5.dp)); Text("编辑") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = notePaperColor(appearance), titleContentColor = noteTextColor(appearance), navigationIconContentColor = noteTextColor(appearance), actionIconContentColor = Color(appearance.accent))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).notePattern(appearance),
            contentPadding = PaddingValues(horizontal = appearance.pagePaddingDp.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "note-title") {
                Text(note.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = noteTextColor(appearance), fontFamily = noteFont(appearance.font))
                Spacer(Modifier.height(8.dp))
                Text("${if (note.pinned) "已置顶 · " else ""}${noteTime(note.updatedAt)} · ${if (note.contentMode == NoteContentMode.MARKDOWN) "Markdown" else "纯文本"}", style = MaterialTheme.typography.labelMedium, color = noteTextColor(appearance).copy(alpha = .62f))
            }
            if(note.contentMode==NoteContentMode.MARKDOWN){
                val blocks=markdownBlocks
                if(blocks==null)item(key="markdown-loading"){LinearProgressIndicator(Modifier.fillMaxWidth())}
                else items(blocks.size,key={"markdown-$it"},contentType={"markdown"}){index->MarkdownBlockRow(blocks[index],appearance)}
            } else item(key = "note-body") {
                SelectionContainer {
                    Text(note.content.ifBlank { "这篇笔记还没有正文。" }, color = noteTextColor(appearance), fontFamily = noteFont(appearance.font), fontSize = MaterialTheme.typography.bodyLarge.fontSize * appearance.fontScale, lineHeight = MaterialTheme.typography.bodyLarge.fontSize * appearance.fontScale * appearance.lineSpacing)
                }
            }
        }
    }
}

@Composable
private fun NoteEditorLoader(base: StudyNote, categories: List<NoteCategory>, global: NoteAppearance, busy: Boolean, onMessage: (String) -> Unit, onBack: () -> Unit, onSave: (StudyNote) -> Unit, onDelete: (StudyNote) -> Unit) {
    val context = LocalContext.current
    var loaded by remember(base.id) { mutableStateOf(false) }
    var recovered by remember(base.id) { mutableStateOf<StudyNote?>(null) }
    LaunchedEffect(base.id) { recovered = NoteDraftStore.read(context, base.id)?.note; loaded = true }
    if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else NoteEditorScreen(recovered ?: base, base, categories, global, busy, recovered != null, onMessage, onBack, onSave, onDelete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    initial: StudyNote,
    original: StudyNote,
    categories: List<NoteCategory>,
    global: NoteAppearance,
    busy: Boolean,
    recovered: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    onSave: (StudyNote) -> Unit,
    onDelete: (StudyNote) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var content by rememberSaveable(initial.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial.content)) }
    var categoryId by rememberSaveable(initial.id) { mutableStateOf(initial.categoryId) }
    var pinned by rememberSaveable(initial.id) { mutableStateOf(initial.pinned) }
    var mode by rememberSaveable(initial.id) { mutableStateOf(initial.contentMode) }
    var useCustom by rememberSaveable(initial.id) { mutableStateOf(initial.appearance != null) }
    var customAppearance by remember(initial.id) { mutableStateOf(initial.appearance ?: global) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val appearance = if (useCustom) customAppearance else global
    val candidate = original.copy(title = title, content = content.text, categoryId = categoryId, pinned = pinned, contentMode = mode, appearance = customAppearance.takeIf { useCustom })
    val dirty = candidate != original

    fun persistDraft() {
        if (dirty && (title.isNotBlank() || content.text.isNotBlank())) scope.launch { NoteDraftStore.save(context, NoteDraft(candidate)) }
    }
    fun requestClose() { if (dirty) discardConfirm = true else onBack() }
    fun save() {
        if (title.trim().isEmpty()) { error = "请填写笔记标题"; return }
        onSave(candidate.copy(title = title.trim(), content = content.text.trimEnd(), updatedAt = Instant.now().toString()))
    }
    fun insert(prefix: String, suffix: String = "") {
        val start = content.selection.min
        val end = content.selection.max
        val selected = content.text.substring(start, end)
        val replacement = prefix + selected + suffix
        content = content.copy(text = content.text.replaceRange(start, end, replacement), selection = TextRange(start + replacement.length - suffix.length))
    }

    LaunchedEffect(Unit) { if (recovered) onMessage("已恢复未保存的笔记内容") }
    LaunchedEffect(candidate) { if (dirty && (title.isNotBlank() || content.text.isNotBlank())) { delay(700); NoteDraftStore.save(context, NoteDraft(candidate)) } }
    DisposableEffect(owner, candidate, dirty) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) persistDraft() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = ::requestClose)

    if (appearanceOpen) {
        NoteAppearanceScreen("本篇笔记外观", appearance, null, { appearanceOpen = false }) { value, _ -> customAppearance = value; useCustom = true; appearanceOpen = false }
        return
    }

    Scaffold(
        containerColor = notePaperColor(appearance),
        topBar = {
            TopAppBar(
                title = { Text(if (original.title.isBlank()) "新建笔记" else "编辑笔记") },
                navigationIcon = { IconButton(onClick = ::requestClose) { Icon(Icons.Outlined.ArrowBack, "返回") } },
                actions = { Button(onClick = ::save, enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("保存") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = notePaperColor(appearance), titleContentColor = noteTextColor(appearance), navigationIconContentColor = noteTextColor(appearance))
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().notePattern(appearance).padding(horizontal = appearance.pagePaddingDp.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }, Modifier.weight(1f)) {
                    OutlinedTextField(
                        categories.firstOrNull { it.id == categoryId }?.title ?: "未分类", {}, readOnly = true, label = { Text("栏目") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), singleLine = true
                    )
                    ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        DropdownMenuItem({ Text("未分类") }, { categoryId = ""; categoryExpanded = false })
                        categories.forEach { category -> DropdownMenuItem({ Text(category.title) }, { categoryId = category.id; categoryExpanded = false }) }
                    }
                }
                FilterChip(pinned, { pinned = !pinned }, { Text(if (pinned) "已置顶" else "置顶") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) })
            }
            OutlinedTextField(title, { title = it.take(80); error = null }, label = { Text("标题") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(mode == NoteContentMode.PLAIN, { mode = NoteContentMode.PLAIN }, { Text("纯文本") })
                FilterChip(mode == NoteContentMode.MARKDOWN, { mode = NoteContentMode.MARKDOWN }, { Text("Markdown") })
                AssistChip({ insert(LocalDate.now(SCHOOL_ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }, { Text("日期") }, leadingIcon = { Icon(Icons.Outlined.Event, null) })
                AssistChip({ insert("• ") }, { Text("项目") })
                AssistChip({ insert(if (mode == NoteContentMode.MARKDOWN) "- [ ] " else "☐ ") }, { Text("待办") })
                AssistChip({ insert("\n---\n") }, { Text("分隔") })
                if (mode == NoteContentMode.MARKDOWN) {
                    AssistChip({ insert("**", "**") }, { Text("粗体") })
                    AssistChip({ insert("# ") }, { Text("标题") })
                    AssistChip({ insert("> ") }, { Text("引用") })
                    AssistChip({ insert("`", "`") }, { Text("代码") })
                    AssistChip({ insert("[", "](https://)") }, { Text("链接") })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { appearanceOpen = true }) { Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(5.dp)); Text("本篇外观") }
                Switch(useCustom, { useCustom = it })
                Text(if (useCustom) "单独设置" else "跟随默认", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("${content.text.length}/20000", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedTextField(
                content, { content = it.copy(text = it.text.take(20_000)); error = null },
                placeholder = { Text(if (mode == NoteContentMode.MARKDOWN) "用 Markdown 记录重点…" else "从课堂重点、复习提纲或一个想法开始…") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    color = noteTextColor(appearance), fontFamily = noteFont(appearance.font), fontSize = MaterialTheme.typography.bodyLarge.fontSize * appearance.fontScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * appearance.fontScale * appearance.lineSpacing
                ),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = Color(appearance.accent), unfocusedBorderColor = Color(appearance.accent).copy(alpha = .35f))
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (dirty) "草稿会自动保存在本机" else "内容已保存", style = MaterialTheme.typography.labelSmall, color = noteTextColor(appearance).copy(alpha = .62f), modifier = Modifier.weight(1f))
                if (original.title.isNotBlank()) TextButton(onClick = { deleteConfirm = true }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (discardConfirm) AlertDialog(
        onDismissRequest = { discardConfirm = false }, title = { Text("笔记尚未保存") }, text = { Text("草稿已经暂存在本机。你可以保存修改、放弃修改或继续编辑。") },
        confirmButton = { Button(onClick = ::save, enabled = !busy) { Text("保存修改") } },
        dismissButton = { Row { TextButton(onClick = { scope.launch { NoteDraftStore.delete(context, original.id) }; discardConfirm = false; onBack() }) { Text("放弃修改") }; TextButton(onClick = { discardConfirm = false }) { Text("继续编辑") } } }
    )
    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false }, title = { Text("删除这篇笔记？") }, text = { Text("删除后无法恢复。") },
        confirmButton = { Button(onClick = { onDelete(original) }, enabled = !busy) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteAppearanceScreen(title: String, initial: NoteAppearance, compact: Boolean?, onBack: () -> Unit, onSave: (NoteAppearance, Boolean?) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var compactDraft by remember(compact) { mutableStateOf(compact) }
    var colorText by remember(initial.accent) { mutableStateOf(hexColor(initial.accent)) }
    var discardConfirm by remember { mutableStateOf(false) }
    val colorValid = parseHexColor(colorText) != null
    val dirty = draft != initial || compactDraft != compact
    fun leave() { if (dirty) discardConfirm = true else onBack() }
    BackHandler(onBack = ::leave)
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.Outlined.ArrowBack, "返回") } }, actions = { TextButton(onClick = { if (colorValid) onSave(draft, compactDraft) }, enabled = colorValid) { Text("应用") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "preview") {
                Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Surface(color = notePaperColor(draft), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).notePattern(draft)) {
                    Column(Modifier.padding(draft.pagePaddingDp.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("把知识写成自己的路", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = noteTextColor(draft), fontFamily = noteFont(draft.font))
                        Text("清晰的结构，会让复习更轻松。\n• 标记重点\n☐ 完成今天的一小步", color = noteTextColor(draft).copy(alpha = .82f), fontFamily = noteFont(draft.font), fontSize = MaterialTheme.typography.bodyLarge.fontSize * draft.fontScale, lineHeight = MaterialTheme.typography.bodyLarge.fontSize * draft.fontScale * draft.lineSpacing)
                    }
                }
            }
            item(key = "presets") {
                Text("快捷方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "清爽讲义" to NoteAppearance(accent = 0xFF176B52),
                        "网格实验" to NoteAppearance(NotePaperStyle.GRID, 0xFF1976A3, NoteFontStyle.SYSTEM, .96f, 1.5f, 18, .34f, 4),
                        "暖纸书摘" to NoteAppearance(NotePaperStyle.WARM, 0xFFB46A22, NoteFontStyle.SERIF, 1.05f, 1.65f, 24, .22f, 5),
                        "夜读深蓝" to NoteAppearance(NotePaperStyle.DOT, 0xFF8BBEFF, NoteFontStyle.ROUNDED, 1f, 1.55f, 22, .24f, 4, 0xFF14243AL)
                    ).forEach { (label, value) -> OutlinedButton(onClick = { draft = value; colorText = hexColor(value.accent) }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
                }
            }
            item(key = "paper") {
                Text("纸张风格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(NotePaperStyle.CLEAN to "清爽", NotePaperStyle.RULED to "横线", NotePaperStyle.GRID to "方格", NotePaperStyle.DOT to "点阵", NotePaperStyle.WARM to "暖纸").forEach { (value, label) -> FilterChip(draft.paper == value, { draft = draft.copy(paper = value, paperTint = if (value == NotePaperStyle.WARM) null else draft.paperTint) }, { Text(label) }) }
                }
            }
            item(key = "type") {
                Text("字体与排版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(NoteFontStyle.SYSTEM to "无衬线", NoteFontStyle.ROUNDED to "圆体", NoteFontStyle.SERIF to "衬线").forEach { (value, label) -> FilterChip(draft.font == value, { draft = draft.copy(font = value) }, { Text(label) }) } }
                NoteSlider("正文字号 ${(draft.fontScale * 100).toInt()}%", draft.fontScale, .85f..1.4f) { draft = draft.copy(fontScale = it) }
                NoteSlider("行距 ${"%.1f".format(draft.lineSpacing)}", draft.lineSpacing, 1.2f..2f) { draft = draft.copy(lineSpacing = it) }
                NoteSlider("页面边距 ${draft.pagePaddingDp}dp", draft.pagePaddingDp.toFloat(), 12f..32f) { draft = draft.copy(pagePaddingDp = it.toInt()) }
                NoteSlider("纹理强度 ${(draft.patternAlpha * 100).toInt()}%", draft.patternAlpha, .08f..0.5f) { draft = draft.copy(patternAlpha = it) }
                NoteSlider("列表预览 ${draft.previewLines} 行", draft.previewLines.toFloat(), 2f..7f) { draft = draft.copy(previewLines = it.toInt()) }
            }
            item(key = "color") {
                Text("点缀色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { noteColors.forEach { value -> Box(Modifier.size(40.dp).background(Color(value), RoundedCornerShape(13.dp)).clickable { draft = draft.copy(accent = value); colorText = hexColor(value) }, contentAlignment = Alignment.Center) { if (draft.accent == value) Icon(Icons.Outlined.Check, null, tint = readableColor(Color(value))) } } }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(colorText, { input -> colorText = input.take(7); parseHexColor(colorText)?.let { draft = draft.copy(accent = it) } }, label = { Text("自定义颜色 #RRGGBB") }, isError = !colorValid, supportingText = { if (!colorValid) Text("颜色格式不正确，请输入 #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (Color(draft.accent).luminance() in .38f..0.62f) Text("该颜色与部分背景接近，正文会自动使用高对比颜色。", style = MaterialTheme.typography.bodySmall)
            }
            compactDraft?.let { current -> item(key = "list") { Row(Modifier.fillMaxWidth().toggleable(current) { compactDraft = it }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("紧凑笔记列表", Modifier.weight(1f)); Switch(current, { compactDraft = it }) } } }
            item(key = "reset") { TextButton(onClick = { draft = NoteAppearance(); colorText = hexColor(draft.accent); compactDraft = false.takeIf { compact != null } }) { Text("恢复默认外观") } }
        }
    }
    if (discardConfirm) AlertDialog(onDismissRequest = { discardConfirm = false }, title = { Text("外观尚未应用") }, text = { Text("要应用刚才的调整吗？") }, confirmButton = { Button(onClick = { if (colorValid) onSave(draft, compactDraft) }, enabled = colorValid) { Text("应用") } }, dismissButton = { Row { TextButton(onClick = onBack) { Text("放弃") }; TextButton(onClick = { discardConfirm = false }) { Text("继续调整") } } })
}

@Composable private fun NoteSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) { Column { Text(label, style = MaterialTheme.typography.labelMedium); Slider(value, onChange, valueRange = range) } }

@Composable
private fun MarkdownBlockRow(block:MarkdownBlock, appearance: NoteAppearance) {
    val context = LocalContext.current
    val size = MaterialTheme.typography.bodyLarge.fontSize * appearance.fontScale
    val style = when (block.kind) {
        MarkdownKind.H1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
        MarkdownKind.H2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        MarkdownKind.H3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        MarkdownKind.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyLarge.copy(fontSize = size, lineHeight = size * appearance.lineSpacing, fontFamily = noteFont(appearance.font))
    }
    val prefix = when (block.kind) { MarkdownKind.BULLET -> "• "; MarkdownKind.NUMBER -> "${block.number}. "; MarkdownKind.CHECKED -> "☑ "; MarkdownKind.UNCHECKED -> "☐ "; MarkdownKind.QUOTE -> "▍ "; else -> "" }
    val annotated = remember(block,appearance.accent){inlineMarkdown(prefix + block.text, Color(appearance.accent))}
    Surface(color = if (block.kind == MarkdownKind.CODE) Color(appearance.accent).copy(alpha = .1f) else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        ClickableText(annotated, style = style.copy(color = noteTextColor(appearance)), modifier = Modifier.fillMaxWidth().padding(if (block.kind == MarkdownKind.CODE) 12.dp else 0.dp), onClick = { offset -> annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.takeIf { it.startsWith("https://") }?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } })
    }
}

private enum class MarkdownKind { PARAGRAPH, H1, H2, H3, BULLET, NUMBER, CHECKED, UNCHECKED, QUOTE, CODE }
private data class MarkdownBlock(val kind: MarkdownKind, val text: String, val number: Int = 0)

private object NoteMarkdown {
    private val cache = object : LinkedHashMap<String, List<MarkdownBlock>>(16, .75f, true) { override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MarkdownBlock>>?) = size > 12 }
    @Synchronized fun parse(source: String): List<MarkdownBlock> {
        cache[source]?.let { return it }
        val result = mutableListOf<MarkdownBlock>(); var code = false; val codeLines = mutableListOf<String>()
        source.lines().forEach { raw ->
            if (raw.trimStart().startsWith("```")) { if (code) { result += MarkdownBlock(MarkdownKind.CODE, codeLines.joinToString("\n")); codeLines.clear() }; code = !code }
            else if (code) codeLines += raw
            else {
                val line = raw.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "[图片已忽略：$1]")
                when {
                    line.isBlank() -> result += MarkdownBlock(MarkdownKind.PARAGRAPH, " ")
                    line.startsWith("### ") -> result += MarkdownBlock(MarkdownKind.H3, line.drop(4))
                    line.startsWith("## ") -> result += MarkdownBlock(MarkdownKind.H2, line.drop(3))
                    line.startsWith("# ") -> result += MarkdownBlock(MarkdownKind.H1, line.drop(2))
                    line.startsWith("> ") -> result += MarkdownBlock(MarkdownKind.QUOTE, line.drop(2))
                    Regex("^- \\[x] ", RegexOption.IGNORE_CASE).containsMatchIn(line) -> result += MarkdownBlock(MarkdownKind.CHECKED, line.drop(6))
                    line.startsWith("- [ ] ") -> result += MarkdownBlock(MarkdownKind.UNCHECKED, line.drop(6))
                    line.startsWith("- ") || line.startsWith("* ") -> result += MarkdownBlock(MarkdownKind.BULLET, line.drop(2))
                    Regex("^\\d+\\. ").containsMatchIn(line) -> { val match = Regex("^(\\d+)\\. (.*)$").find(line)!!; result += MarkdownBlock(MarkdownKind.NUMBER, match.groupValues[2], match.groupValues[1].toInt()) }
                    else -> result += MarkdownBlock(MarkdownKind.PARAGRAPH, line)
                }
            }
        }
        if (codeLines.isNotEmpty()) result += MarkdownBlock(MarkdownKind.CODE, codeLines.joinToString("\n"))
        cache[source] = result; return result
    }
}

private fun inlineMarkdown(source: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val token = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`|\\[([^]]+)]\\((https://[^\\s)]+)\\)")
    var cursor = 0
    token.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            match.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[2]) }
            match.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = .12f))) { append(match.groupValues[3]) }
            else -> { val start = length; withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(match.groupValues[4]) }; addStringAnnotation("URL", match.groupValues[5], start, length) }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}

@Composable
private fun CategoryEditorDialog(category: NoteCategory, busy: Boolean, onDismiss: () -> Unit, onSave: (NoteCategory) -> Unit, onDelete: () -> Unit) {
    var title by remember(category.id) { mutableStateOf(category.title) }; var color by remember(category.id) { mutableLongStateOf(category.color) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (category.title.isBlank()) "新建栏目" else "编辑栏目") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(20); error = null }, label = { Text("栏目名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("栏目颜色", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { noteColors.forEach { value -> Box(Modifier.size(36.dp).background(Color(value), RoundedCornerShape(12.dp)).clickable { color = value }, contentAlignment = Alignment.Center) { if (color == value) Icon(Icons.Outlined.Check, null, tint = readableColor(Color(value))) } } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (category.title.isNotBlank()) TextButton(onClick = onDelete, enabled = !busy) { Text("删除栏目", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = { if (title.trim().isEmpty()) error = "请填写栏目名称" else onSave(category.copy(title = title.trim(), color = color)) }, enabled = !busy) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun Modifier.notePattern(appearance: NoteAppearance): Modifier = drawBehind {
    val color = Color(appearance.accent).copy(alpha = appearance.patternAlpha)
    when (appearance.paper) {
        NotePaperStyle.RULED, NotePaperStyle.WARM -> { var y = 30.dp.toPx(); while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += 30.dp.toPx() } }
        NotePaperStyle.GRID -> { val step = 28.dp.toPx(); var y = step; while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += step }; var x = step; while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += step } }
        NotePaperStyle.DOT -> { val step = 24.dp.toPx(); var y = step; while (y < size.height) { var x = step; while (x < size.width) { drawCircle(color, 1.dp.toPx(), Offset(x, y)); x += step }; y += step } }
        NotePaperStyle.CLEAN -> Unit
    }
}

@Composable private fun notePaperColor(appearance: NoteAppearance): Color = appearance.paperTint?.let(::Color) ?: when (appearance.paper) { NotePaperStyle.WARM -> if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF2B251F) else Color(0xFFFFFAEF); else -> MaterialTheme.colorScheme.background }
@Composable private fun noteTextColor(appearance: NoteAppearance): Color = appearance.paperTint?.let { readableColor(Color(it)) } ?: MaterialTheme.colorScheme.onBackground
private fun readableColor(background: Color) = if (background.luminance() > .46f) Color(0xFF12211B) else Color.White
private fun noteFont(value: NoteFontStyle) = when (value) { NoteFontStyle.SERIF -> FontFamily.Serif; NoteFontStyle.ROUNDED -> FontFamily.SansSerif; NoteFontStyle.SYSTEM -> FontFamily.Default }
private fun notePreview(note: StudyNote) = note.content.replace(Regex("[`*_>#\\[\\]()]"), "").replace(Regex("\\s+"), " ").trim()
private fun noteTime(value: String): String = runCatching { Instant.parse(value).atZone(SCHOOL_ZONE).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")) }.getOrDefault("刚刚更新")
private fun hexColor(value: Long) = "#%06X".format(value and 0xFFFFFF)
private fun parseHexColor(value: String): Long? = value.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }?.drop(1)?.toLong(16)?.let { 0xFF000000L or it }

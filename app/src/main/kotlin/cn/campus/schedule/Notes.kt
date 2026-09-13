package cn.campus.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

private val noteColors = listOf(0xFF176B52, 0xFF1976A3, 0xFFB46A22, 0xFF8B5BA7, 0xFFC85566)

@Composable
fun NotesHub(
    data: AppData,
    busy: Boolean,
    onBack: () -> Unit,
    onSaveCategory: (NoteCategory) -> Unit,
    onDeleteCategory: (NoteCategory) -> Unit,
    onSaveNote: (StudyNote) -> Unit,
    onDeleteNote: (StudyNote) -> Unit,
    onSaveStyle: (NoteStyle) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryDialog by remember { mutableStateOf<NoteCategory?>(null) }
    var editingNote by remember { mutableStateOf<StudyNote?>(null) }
    var styleDialog by remember { mutableStateOf(false) }
    var deleteCategory by remember { mutableStateOf<NoteCategory?>(null) }
    BackHandler(onBack = onBack)

    val categories = data.noteCategories.sortedWith(compareBy<NoteCategory> { it.order }.thenBy { it.title })
    val notes = data.studyNotes.asSequence()
        .filter { selectedCategory == null || it.categoryId == selectedCategory }
        .sortedWith(compareByDescending<StudyNote> { it.pinned }.thenByDescending { it.updatedAt })
        .toList()
    val style = data.noteStyle

    Box(Modifier.fillMaxSize().background(noteCanvasColor(style))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(if (style.compact) 9.dp else 14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("灵感笔记", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("按栏目整理课程重点与想法", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { styleDialog = true }) { Icon(Icons.Outlined.Palette, "笔记外观") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("全部 ${data.studyNotes.size}") })
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category.id,
                            onClick = { selectedCategory = category.id },
                            label = { Text(category.title) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(Color(category.color), RoundedCornerShape(50))) },
                            trailingIcon = { Icon(Icons.Outlined.Edit, "编辑栏目", Modifier.size(16.dp).clickable { categoryDialog = category }) }
                        )
                    }
                    AssistChip(onClick = { categoryDialog = NoteCategory(UUID.randomUUID().toString(), "", style.accent, categories.size) }, label = { Text("新建栏目") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) })
                }
            }
            if (notes.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.NoteAdd, null, tint = Color(style.accent), modifier = Modifier.size(34.dp))
                            Text(if (selectedCategory == null) "写下第一篇笔记" else "这个栏目还是空的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("可以记录课堂重点、复习提纲或临时灵感，内容只保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { editingNote = StudyNote(UUID.randomUUID().toString(), selectedCategory.orEmpty(), "") }, enabled = !busy) { Text("新建笔记") }
                        }
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note, categories.firstOrNull { it.id == note.categoryId }, style) { editingNote = note }
                }
            }
        }
        FloatingActionButton(
            onClick = { editingNote = StudyNote(UUID.randomUUID().toString(), selectedCategory.orEmpty(), "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = Color(style.accent),
            contentColor = Color.White
        ) { Icon(Icons.Outlined.EditNote, "新建笔记") }
    }

    editingNote?.let { note ->
        NoteEditorDialog(note, categories, style, busy, onDismiss = { editingNote = null }, onSave = {
            onSaveNote(it); editingNote = null
        }, onDelete = {
            onDeleteNote(it); editingNote = null
        })
    }
    categoryDialog?.let { category ->
        CategoryEditorDialog(category, busy, onDismiss = { categoryDialog = null }, onSave = { onSaveCategory(it); categoryDialog = null }, onDelete = {
            deleteCategory = category; categoryDialog = null
        })
    }
    deleteCategory?.let { category ->
        AlertDialog(onDismissRequest = { deleteCategory = null }, title = { Text("删除“${category.title}”？") }, text = { Text("栏目中的笔记会保留，并移动到“未分类”。") }, confirmButton = {
            Button(onClick = { onDeleteCategory(category); if (selectedCategory == category.id) selectedCategory = null; deleteCategory = null }, enabled = !busy) { Text("删除栏目") }
        }, dismissButton = { TextButton(onClick = { deleteCategory = null }) { Text("取消") } })
    }
    if (styleDialog) NoteStyleDialog(style, busy, onDismiss = { styleDialog = false }, onSave = { onSaveStyle(it); styleDialog = false })
}

@Composable
private fun NoteCard(note: StudyNote, category: NoteCategory?, style: NoteStyle, onClick: () -> Unit) {
    val accent = Color(category?.color ?: style.accent)
    val lines = if (style.compact) 2 else 4
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = notePaperColor(style))) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(5.dp).heightIn(min = if (style.compact) 88.dp else 112.dp).background(accent))
            Column(Modifier.weight(1f).padding(if (style.compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (note.pinned) Icon(Icons.Outlined.PushPin, "已置顶", tint = accent, modifier = Modifier.size(18.dp))
                }
                if (note.content.isNotBlank()) Text(note.content, maxLines = lines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category?.title ?: "未分类", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(noteTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorDialog(note: StudyNote, categories: List<NoteCategory>, style: NoteStyle, busy: Boolean, onDismiss: () -> Unit, onSave: (StudyNote) -> Unit, onDelete: (StudyNote) -> Unit) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(note.content) }
    var categoryId by remember(note.id) { mutableStateOf(note.categoryId) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dirty = title != note.title || content != note.content || categoryId != note.categoryId || pinned != note.pinned
    fun close() { if (dirty) discardConfirm = true else onDismiss() }
    fun save() {
        if (title.trim().isEmpty()) { error = "请填写笔记标题"; return }
        onSave(note.copy(title = title.trim(), content = content.trimEnd(), categoryId = categoryId, pinned = pinned, updatedAt = Instant.now().toString()))
    }
    AlertDialog(onDismissRequest = ::close, title = { Text(if (note.title.isBlank()) "新建笔记" else "编辑笔记") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = pinned, onClick = { pinned = !pinned }, label = { Text(if (pinned) "已置顶" else "置顶") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) })
                Spacer(Modifier.weight(1f))
                Text("${content.length}/20000", style = MaterialTheme.typography.labelSmall)
            }
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(value = categories.firstOrNull { it.id == categoryId }?.title ?: "未分类", onValueChange = {}, readOnly = true, label = { Text("栏目") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    DropdownMenuItem(text = { Text("未分类") }, onClick = { categoryId = ""; categoryExpanded = false })
                    categories.forEach { category -> DropdownMenuItem(text = { Text(category.title) }, onClick = { categoryId = category.id; categoryExpanded = false }) }
                }
            }
            OutlinedTextField(title, { title = it.take(80); error = null }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
            OutlinedTextField(
                value = content, onValueChange = { content = it.take(20_000) }, label = { Text("正文") },
                minLines = 10, maxLines = 18, modifier = Modifier.fillMaxWidth().then(if (style.paper == NotePaperStyle.GRID) Modifier.drawBehind {
                    var y = 28.dp.toPx(); while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += 28.dp.toPx() }
                } else Modifier), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(style.accent))
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (note.title.isNotBlank()) TextButton(onClick = { deleteConfirm = true }, enabled = !busy) { Text("删除笔记", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = ::save, enabled = !busy) { Text("保存") } }, dismissButton = { TextButton(onClick = ::close) { Text("返回") } })
    if (discardConfirm) AlertDialog(onDismissRequest = { discardConfirm = false }, title = { Text("笔记尚未保存") }, text = { Text("要保存刚才的修改吗？") }, confirmButton = { Button(onClick = ::save) { Text("保存修改") } }, dismissButton = { Row { TextButton(onClick = onDismiss) { Text("放弃") }; TextButton(onClick = { discardConfirm = false }) { Text("继续编辑") } } })
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("删除这篇笔记？") }, text = { Text("删除后无法恢复。") }, confirmButton = { Button(onClick = { onDelete(note) }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } })
}

@Composable
private fun CategoryEditorDialog(category: NoteCategory, busy: Boolean, onDismiss: () -> Unit, onSave: (NoteCategory) -> Unit, onDelete: () -> Unit) {
    var title by remember(category.id) { mutableStateOf(category.title) }
    var color by remember(category.id) { mutableLongStateOf(category.color) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (category.title.isBlank()) "新建栏目" else "编辑栏目") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(20); error = null }, label = { Text("栏目名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("栏目颜色", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { noteColors.forEach { value -> Box(Modifier.size(36.dp).background(Color(value), RoundedCornerShape(12.dp)).clickable { color = value }, contentAlignment = Alignment.Center) { if (color == value) Icon(Icons.Outlined.Check, null, tint = Color.White) } } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (category.title.isNotBlank()) TextButton(onClick = onDelete, enabled = !busy) { Text("删除栏目", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = { if (title.trim().isEmpty()) error = "请填写栏目名称" else onSave(category.copy(title = title.trim(), color = color)) }, enabled = !busy) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun NoteStyleDialog(style: NoteStyle, busy: Boolean, onDismiss: () -> Unit, onSave: (NoteStyle) -> Unit) {
    var draft by remember { mutableStateOf(style) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("笔记外观") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("纸张风格", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(NotePaperStyle.CLEAN to "清爽", NotePaperStyle.GRID to "方格", NotePaperStyle.WARM to "暖纸").forEach { (value, label) -> FilterChip(draft.paper == value, { draft = draft.copy(paper = value) }, { Text(label) }) } }
            Text("点缀色", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { noteColors.forEach { value -> Box(Modifier.size(36.dp).background(Color(value), RoundedCornerShape(12.dp)).clickable { draft = draft.copy(accent = value) }, contentAlignment = Alignment.Center) { if (draft.accent == value) Icon(Icons.Outlined.Check, null, tint = Color.White) } } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("紧凑列表", modifier = Modifier.weight(1f)); Switch(draft.compact, { draft = draft.copy(compact = it) }) }
            Text("外观只影响笔记，不会改变课表和桌面组件。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { Button(onClick = { onSave(draft) }, enabled = !busy) { Text("应用") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun noteCanvasColor(style: NoteStyle): Color = when (style.paper) {
    NotePaperStyle.WARM -> if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF211D19) else Color(0xFFFFF8EC)
    else -> MaterialTheme.colorScheme.background
}
@Composable private fun notePaperColor(style: NoteStyle): Color = when (style.paper) {
    NotePaperStyle.WARM -> if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF2B251F) else Color(0xFFFFFCF5)
    else -> MaterialTheme.colorScheme.surface
}
private fun noteTime(value: String): String = runCatching {
    Instant.parse(value).atZone(SCHOOL_ZONE).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
}.getOrDefault("刚刚更新")

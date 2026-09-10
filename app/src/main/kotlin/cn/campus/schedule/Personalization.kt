package cn.campus.schedule

import android.app.Application
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import kotlin.math.max

@Serializable data class ProfileData(
    val nickname: String = "",
    val greeting: String = "今天，去上课",
    val netId: String = "",
    val studentNumber: String = "",
    val dorm: String = "",
    val avatar: String = "",
    val avatarZoom: Float = 1f,
    val avatarX: Float = .5f,
    val avatarY: Float = .5f,
    val avatarShape: String = "circle",
    val avatarBorder: Long = 0xFFB7F2D5,
    val showAvatarOnWidgets: Boolean = false
)

@Serializable data class LaunchStyle(
    val enabled: Boolean = true,
    val scene: String = "core",
    val image: String = "",
    val zoom: Float = 1f,
    val x: Float = .5f,
    val y: Float = .5f,
    val blur: Float = 0f,
    val shade: Float = .3f,
    val motion: Float = .7f,
    val coreColor: Long = 0xFF071A1B,
    val glowColor: Long = 0xFF8FF4D0,
    val coreOpacity: Float = .78f,
    val glowIntensity: Float = 1f
)

@Serializable data class Personalization(
    val profile: ProfileData = ProfileData(),
    val themeMode: String = "kangle",
    val launch: LaunchStyle = LaunchStyle(),
    val profileBannerStyle: String = "warm"
)

object PersonalizationStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun prefs(context: Context) = context.getSharedPreferences("personalization", Context.MODE_PRIVATE)
    fun read(context: Context): Personalization = runCatching {
        json.decodeFromString<Personalization>(prefs(context).getString("state", "{}")!!)
    }.getOrDefault(Personalization())
    fun save(context: Context, value: Personalization) {
        check(prefs(context).edit().putString("state", json.encodeToString(value)).commit()) { "个性化设置保存失败，请重试" }
    }
    fun importImage(context: Context, uri: Uri): String = DiyStore.importImage(context, uri)
    fun clearProfile(context: Context, current: Personalization): Personalization {
        DiyStore.imageFile(context, current.profile.avatar)?.delete()
        return current.copy(profile=ProfileData())
    }
}

class PersonalizationViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as ScheduleApp
    private val _state = MutableStateFlow(PersonalizationStore.read(app))
    val state = _state.asStateFlow()
    val message = MutableStateFlow<String?>(null)

    fun save(value: Personalization, success: String? = null) {
        runCatching { PersonalizationStore.save(application, value) }
            .onSuccess {
                _state.value = value
                success?.let { message.value = it }
                refreshWidgets()
            }.onFailure { message.value = it.message ?: "保存失败，请重试" }
    }

    fun importAvatar(uri: Uri) = importImage(uri, true)
    fun importLaunchImage(uri: Uri) = importImage(uri, false)

    private fun importImage(uri: Uri, avatar: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PersonalizationStore.importImage(application, uri) }
                .onSuccess { name ->
                    val current = _state.value
                    val updated = if (avatar) current.copy(profile=current.profile.copy(avatar=name))
                    else current.copy(launch=current.launch.copy(scene="custom", image=name))
                    PersonalizationStore.save(application, updated)
                    _state.value = updated
                    message.value = if(avatar) "头像已保存" else "启动背景已保存"
                    refreshWidgets()
                }.onFailure { message.value = it.message ?: "图片读取失败" }
        }
    }

    fun clearProfile() {
        val updated = PersonalizationStore.clearProfile(application, _state.value)
        save(updated, "个人资料已清除")
    }

    private fun refreshWidgets() {
        application.scope.launch {
            runCatching { TodayWidget().updateAllSafe(application); NextWidget().updateAllSafe(application); StudyWidget().updateAllSafe(application) }
        }
    }
}

object AvatarRenderer {
    fun render(context: Context, profile: ProfileData, size: Int = 96): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val border = max(2f, size * .045f)
        val rect = RectF(border, border, size-border, size-border)
        val radius = if(profile.avatarShape == "rounded") size*.24f else size/2f
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        val source = DiyStore.imageFile(context,profile.avatar)?.takeIf {it.exists()}?.let {BitmapFactory.decodeFile(it.path)}
        if(source != null) {
            val scale=max(size.toFloat()/source.width,size.toFloat()/source.height)*profile.avatarZoom.coerceIn(1f,3f)
            val sw=source.width*scale; val sh=source.height*scale
            val left=(size-sw)*profile.avatarX.coerceIn(0f,1f); val top=(size-sh)*profile.avatarY.coerceIn(0f,1f)
            canvas.drawBitmap(source,null,RectF(left,top,left+sw,top+sh),paint); source.recycle()
        } else {
            paint.shader=LinearGradient(0f,0f,size.toFloat(),size.toFloat(),0xFF176B52.toInt(),0xFF75BDE0.toInt(),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,size.toFloat(),size.toFloat(),paint); paint.shader=null
            val initial=profile.nickname.trim().firstOrNull()?.toString() ?: "中"
            paint.color=Color.WHITE; paint.textSize=size*.45f; paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.DEFAULT_BOLD
            val baseline=size/2f-(paint.ascent()+paint.descent())/2f
            canvas.drawText(initial,size/2f,baseline,paint)
        }
        canvas.restore()
        paint.style=Paint.Style.STROKE; paint.strokeWidth=border; paint.color=profile.avatarBorder.toInt()
        canvas.drawRoundRect(rect,radius,radius,paint)
        return output
    }
}

fun seasonalThemeKey(): String = when(ZonedDateTime.now(cn.campus.core.SCHOOL_ZONE).monthValue) {
    in 3..5 -> "spring"
    in 6..8 -> "summer"
    in 9..11 -> "autumn"
    else -> "winter"
}

package cn.campus.schedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.campus.core.PhoneGuide

object WidgetSupport {
    private val receivers=listOf(NextWidgetReceiver::class.java,TodayWidgetReceiver::class.java,StudyWidgetReceiver::class.java)
    fun count(context: Context): Int? = runCatching {
        val own=AppWidgetManager.getInstance(context).installedProviders.map { it.provider }.toSet()
        receivers.count {ComponentName(context,it) in own}
    }.getOrNull()
    fun help(brand: PhoneGuide): String = when(brand) {
        PhoneGuide.HUAWEI -> "华为安卓：桌面双指捏合，进入“服务卡片”后滑到最下方寻找“窗口小工具”，或直接进入“窗口小工具”，找到“中大课表助手”。服务卡片搜索与安卓小工具列表可能分开。"
        PhoneGuide.HONOR -> "荣耀：桌面双指捏合 → 桌面卡片 → 经典小工具，找到“中大课表助手”。较早系统入口叫“窗口小工具”。"
        else -> "长按桌面空白处 → 小工具 / 窗口小工具，找到“中大课表助手”。如果有精选卡片页，请再进入全部安卓小工具列表。"
    }
    fun request(context: Context,receiver: Class<*>): String {
        val manager=AppWidgetManager.getInstance(context)
        return try {
            if(!manager.isRequestPinAppWidgetSupported) "当前桌面不支持应用内添加，请按下方指引手动添加"
            else if(manager.requestPinAppWidget(ComponentName(context,receiver),null,null)) "已向桌面发送添加请求，请完成系统确认；没有弹窗时按下方指引手动添加"
            else "桌面未接受添加请求，请按下方指引手动添加"
        } catch(_:Exception) { "无法唤起桌面添加，请按下方指引手动添加" }
    }
}

@Composable fun WidgetHelp(context: Context,tick: Int,onMessage:(String)->Unit) {
    var check by remember { mutableIntStateOf(0) }
    val count=remember(tick,check) { WidgetSupport.count(context) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(WidgetSupport.help(PhoneGuide.detect(Build.MANUFACTURER,Build.BRAND)),style=MaterialTheme.typography.bodySmall)
        Text(when(count) { 3 -> "系统已登记 3 个桌面组件；是否展示由当前桌面决定"; null -> "无法读取组件登记状态"; else -> "系统登记了 $count / 3 个组件，请重新打开应用后检查" },style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={check++;onMessage("已重新检查组件登记状态")}) {Text("检查桌面组件")}
        Text("找不到时：先打开本应用一次，确认与桌面处于同一主空间，再重启手机检查。请预留桌面空位。通过安卓兼容容器运行时，原生鸿蒙桌面可能不提供这些组件；请反馈具体型号和系统版本。",style=MaterialTheme.typography.bodySmall)
    }
}

package cn.campus.schedule

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import cn.campus.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

/** User-owned school login. No native JavaScript bridge or credential interception. */
class SchoolBrowserActivity : ComponentActivity() {
    private var web: WebView? = null
    private var host: android.widget.FrameLayout? = null
    private val windows = mutableListOf<WebView>()
    private var errorCode = "NONE"
    private var address by mutableStateOf("jwxt.sysu.edu.cn")
    private var status by mutableStateOf("登录与二次验证兼容性待真机验证；请选择“全部”周次后导入。")
    private var loading by mutableStateOf(false)
    private var importing by mutableStateOf(false)
    private var clearing by mutableStateOf(false)
    private var switching by mutableStateOf(false)
    private var switchJob: Job? = null
    private var showClear by mutableStateOf(false)
    private var importJob: Job? = null
    private var loadTimeout: Job? = null
    
    private val firstMonday get() = intent.getStringExtra(SchoolImportCache.MONDAY).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CampusTheme(PersonalizationStore.read(this).themeMode) {
            BackHandler { back() }
            Surface { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick={back()}) { Text("返回") }
                    Text("学校网页导入", style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(12.dp))
                }
                Text("登录 → 课表查询 → 选择全部 → 导入当前课表", style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(horizontal=16.dp, vertical=6.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(modifier=Modifier.fillMaxSize(), factory={context -> android.widget.FrameLayout(context).also { host=it; returnToLogin() }})
                    if(importing || clearing) Box(Modifier.fillMaxSize().clickable { })
                }
                Surface(color=MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(address, style=MaterialTheme.typography.labelSmall)
                        Text(status, style=MaterialTheme.typography.bodySmall, modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
                        Box(Modifier.fillMaxWidth().height(4.dp)) { if(loading || importing || clearing || switching) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        Button(onClick={startImport()}, enabled=!loading && !importing && !clearing && !switching, modifier=Modifier.fillMaxWidth()) { Text("导入当前课表") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick={if(web==null)returnToLogin() else web?.reload()}, enabled=!importing && !clearing) {Text("重新加载")}
                            TextButton(onClick={showClear=true}, enabled=!importing && !clearing) {Text("清除登录状态")}
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick={returnToLogin()}, enabled=!importing && !clearing) {Text("返回教务登录")}
                            TextButton(onClick={copyDiagnostics()}) {Text("复制排查信息")}
                        }
                    }
                }
            } }
            if(showClear) AlertDialog(onDismissRequest={showClear=false}, title={Text("清除学校登录状态？")}, text={Text("将退出本应用中的学校登录并清除网页缓存，已导入课表和桌面设置会保留。")}, confirmButton={TextButton(onClick={showClear=false;clearSession()}) {Text("清除登录状态")}}, dismissButton={TextButton(onClick={showClear=false}) {Text("取消")}})
        } }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled=true; domStorageEnabled=true
            allowFileAccess=false; allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true); builtInZoomControls=true; displayZoomControls=false
            useWideViewPort=true; loadWithOverviewMode=true
            setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically=true
            // Keep the device's real WebView user agent for authentication compatibility.
        }
        CookieManager.getInstance().apply {setAcceptCookie(true);setAcceptThirdPartyCookies(view,true)}
        view.webViewClient=object: WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView, request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame) return false
                return !allowNavigation(request.url.toString(),v)
            }
            @Deprecated("Required for older WebView providers")
            override fun shouldOverrideUrlLoading(v:WebView,url:String):Boolean = !allowNavigation(url,v)
            override fun onPageStarted(v:WebView,url:String?,favicon:Bitmap?) {
                if(url=="about:blank" && v in windows && windows.indexOf(v)>0) return
                if(!SchoolUrlPolicy.school(url)) {v.stopLoading();if(v===web)pageError("仅支持学校 HTTPS 网页，已停止跳转","NAVIGATION_BLOCKED");return}
                if(v!==web)return
                cancelImport();loading=true;errorCode="NONE";status="正在加载学校网页…";address=Uri.parse(url).host.orEmpty()
                loadTimeout?.cancel();loadTimeout=lifecycleScope.launch {delay(30_000);if(v===web)pageError("网页加载超时，请重新加载或返回教务登录。","LOAD_TIMEOUT")}
            }
            override fun onPageFinished(v:WebView,url:String?) {
                CookieManager.getInstance().flush()
                if(v!==web || url=="about:blank")return
                loading=false;loadTimeout?.cancel()
                if(errorCode=="NONE" && !switching)status=if(SchoolUrlPolicy.timetable(url)) "请选择全部周次后点击导入。" else "请继续完成学校登录；若页面空白，可返回教务登录或复制排查信息。"
            }
            override fun doUpdateVisitedHistory(v:WebView,url:String?,isReload:Boolean) {if(v===web){address=Uri.parse(url.orEmpty()).host ?: "学校认证窗口";if(!SchoolUrlPolicy.timetable(url)) cancelImport()}}
            override fun onReceivedError(v:WebView,request:WebResourceRequest,error:WebResourceError) {if(request.isForMainFrame && v===web) pageError("网页加载失败，请重新加载或返回教务登录。","NETWORK_${error.errorCode}")}
            override fun onReceivedHttpError(v:WebView,request:WebResourceRequest,response:WebResourceResponse) {if(request.isForMainFrame && v===web) pageError("学校网页暂时不可用，请重新登录或稍后重试。","HTTP_${response.statusCode}")}
            override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel();if(v===web)pageError("学校网页证书验证失败，已停止加载。","TLS_ERROR")}
            override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean {
                cancelImport();web=null;host?.removeAllViews()
                windows.toList().forEach {it.destroy()};windows.clear()
                pageError("网页进程已退出，请点击重新加载或返回教务登录。","RENDERER_EXIT")
                return true
            }
        }
        view.webChromeClient=object: WebChromeClient() {
            override fun onCreateWindow(v:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(v!==web || importing || clearing || !SchoolUrlPolicy.school(v.url) || windows.size>=4) return false
                cancelImport();loadTimeout?.cancel()
                val popup=WebView(this@SchoolBrowserActivity)
                configure(popup);windows+=popup;showWindow(popup)
                loading=true;status="正在打开学校认证窗口…"
                (resultMsg.obj as WebView.WebViewTransport).webView=popup
                resultMsg.sendToTarget()
                loadTimeout=lifecycleScope.launch {delay(30_000);if(web===popup && loading)pageError("认证窗口未完成加载，请返回或重新打开教务登录。","WINDOW_TIMEOUT")}
                return true
            }
            override fun onCloseWindow(window:WebView) {closeWindow(window)}
        }
        view.setDownloadListener { url,_,_,_,_ ->
            if(view===web && importing && SchoolUrlPolicy.timetable(view.url)) {
                view.evaluateJavascript("window.__campusCapture && window.__campusCapture.read(${JsonPrimitive(url)})",null)
            } else status="请点击底部“导入当前课表”，或返回使用文件导入。"
        }
    }

    private fun allowNavigation(url:String,view:WebView):Boolean {
        if(url=="about:blank" && windows.indexOf(view)>0)return true
        if(SchoolUrlPolicy.school(url)) return true
        if(view===web)pageError("此链接不是学校 HTTPS 网页，已停止跳转。","NAVIGATION_BLOCKED");return false
    }
    private fun pageError(message:String,code:String="IMPORT_ERROR") {cancelImport();loading=false;loadTimeout?.cancel();errorCode=code;status=message}
    private fun cancelImport() {importJob?.cancel();importJob=null;importing=false}
    private fun back() {stopSwitch();cancelImport();if(windows.size>1)closeWindow(web!!) else if(web?.canGoBack()==true)web?.goBack() else finish()}
    private fun showWindow(view:WebView) {
        host?.removeAllViews()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        host?.addView(view,android.widget.FrameLayout.LayoutParams(-1,-1));web=view
        address=Uri.parse(view.url.orEmpty()).host ?: "学校认证窗口"
    }
    private fun closeWindow(view:WebView) {
        if(!windows.remove(view))return
        val active=view===web
        (view.parent as? android.view.ViewGroup)?.removeView(view);view.destroy()
        if(active){cancelImport();loadTimeout?.cancel();loading=false;web=null
            windows.lastOrNull()?.let {showWindow(it)}
            status="已返回上一层学校网页。"
        }
    }
    private fun returnToLogin() {
        stopSwitch();cancelImport();loadTimeout?.cancel()
        web=null;host?.removeAllViews();windows.toList().forEach {it.stopLoading();it.destroy()};windows.clear()
        val view=WebView(this);configure(view);windows+=view;showWindow(view)
        errorCode="NONE";view.loadUrl(SchoolUrlPolicy.LOGIN)
    }
    private fun copyDiagnostics() {
        val provider=WebView.getCurrentWebViewPackage()
        val version=packageManager.getPackageInfo(packageName,0).versionName
        val report="xiaoyle 制作 · 网页登录排查\n应用：$version\nAndroid：${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\nWebView：${provider?.packageName ?: "未知"} ${provider?.versionName.orEmpty()}\n域名：$address\n状态码：$errorCode"
        getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("网页登录排查",report))
        status="已复制排查信息，不含账号或网页内容。"
    }

    private suspend fun evaluate(view:WebView,script:String):JsonElement = withTimeout(5000) {
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) {value -> if(continuation.isActive) continuation.resume(runCatching {Json.parseToJsonElement(value)}.getOrDefault(JsonNull))}
        }
    }

    private fun startImport() {
        if(importing || loading || clearing || switching) return
        val view=web ?: return
        if(!SchoolUrlPolicy.timetable(view.url)) {
            if(SchoolUrlPolicy.school(view.url) && Uri.parse(view.url).host=="jwxt.sysu.edu.cn") openCompleteTimetable(view)
            else status="请先完成学校登录，再点击导入。"
            return
        }
        importing=true;status="正在读取全部周次课表，请稍候…"
        importJob=lifecycleScope.launch {
            try {
                require(java.time.LocalDate.parse(firstMonday).dayOfWeek==java.time.DayOfWeek.MONDAY) {"第 1 周起始日期必须是周一，请返回设置页修改"}
                val tableScript=assets.open("school-table.js").bufferedReader().use {it.readText()}
                // Work from the complete table itself; never depend on a mobile download/export dialog.
                evaluate(view,"""(()=>{if(location.origin!=='https://jwxt.sysu.edu.cn'||location.hash.split('?')[0]!=='#/studentTimeTabPrint')return;const tab=Array.from(document.querySelectorAll('[role="tab"],.ant-tabs-tab')).find(e=>e.textContent.trim()==='全部');if(tab && tab.getAttribute('aria-selected')!=='true' && !tab.classList.contains('ant-tabs-tab-active'))tab.click();})()""")
                var snapshotJson:JsonElement=JsonNull
                val deadline=android.os.SystemClock.elapsedRealtime()+20_000
                var lastError="完整课表尚未加载，请稍后再点击导入。"
                while(android.os.SystemClock.elapsedRealtime()<deadline) {
                    ensureActive();require(web===view && SchoolUrlPolicy.timetable(view.url)) {"网页已变化，请重新进入课表后导入"}
                    val candidate=evaluate(view,tableScript)
                    if(candidate is JsonObject && candidate["error"]==null) {
                        delay(750)
                        if(evaluate(view,tableScript)==candidate){snapshotJson=candidate;break}
                    } else {lastError=(candidate as? JsonObject)?.get("error")?.jsonPrimitive?.content ?: lastError}
                    delay(500)
                }
                require(snapshotJson!=JsonNull) {lastError}
                val snapshot=dataJson.decodeFromJsonElement<WebTableSnapshot>(snapshotJson)
                status="正在解析完整课表…"
                val result=withContext(Dispatchers.Default) {WebScheduleParser().parse(snapshot,firstMonday)}
                ensureActive();require(SchoolUrlPolicy.timetable(view.url)) {"登录状态已变化，请重新导入"}
                require(evaluate(view,tableScript)==snapshotJson) {"课表内容已变化，请重新导入"}
                val parsed=requireNotNull(result)
                val token=withContext(Dispatchers.IO) {SchoolImportCache.write(this@SchoolBrowserActivity,parsed)}
                setResult(Activity.RESULT_OK,Intent().putExtra(SchoolImportCache.TOKEN,token));finish()
            } catch(e:TimeoutCancellationException) {status="网页响应超时，请重新加载后导入，或返回使用文件导入。"}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {status=if(e is IllegalArgumentException || e is IllegalStateException) e.message?.take(160) ?: "课表读取失败，请使用文件导入。" else "课表读取失败，请重新登录后重试，或返回使用 Word / PDF 导入。"}
            finally {
                if(web===view && SchoolUrlPolicy.timetable(view.url)) view.evaluateJavascript("if(window.__campusCapture){window.__campusCapture.stop();delete window.__campusCapture;}",null)
                importing=false
            }
        }
    }
    private fun stopSwitch() {switchJob?.cancel();switchJob=null;switching=false}
    private fun openCompleteTimetable(view:WebView) {
        if(switching)return
        switching=true;status="正在从手机版切换到完整课表，加载后自动导入…"
        view.loadUrl("https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint?code=jwxsd_xskbcx")
        switchJob=lifecycleScope.launch {
            try {
                val deadline=android.os.SystemClock.elapsedRealtime()+25_000
                while(android.os.SystemClock.elapsedRealtime()<deadline) {
                    delay(500)
                    if(web!==view)return@launch
                    if(SchoolUrlPolicy.timetable(view.url) && !loading) {
                        switching=false;startImport();return@launch
                    }
                }
                errorCode="COMPLETE_TABLE_NOT_READY"
                status="完整课表未打开。若跳到登录页请继续登录，再点导入；若已显示课表请重试。"
            } finally {switching=false}
        }
    }
    private fun clearSession() {
        stopSwitch();cancelImport();clearing=true;windows.forEach {it.stopLoading()}
        CookieManager.getInstance().removeAllCookies {
            runOnUiThread {
                WebStorage.getInstance().deleteAllData();CookieManager.getInstance().flush()
                windows.forEach {it.clearCache(true);it.clearHistory();it.clearFormData()}
                clearing=false;status="已清除登录状态，请重新登录。";returnToLogin()
            }
        }
    }
    override fun onDestroy() {
        stopSwitch();cancelImport();loadTimeout?.cancel();web=null;host?.removeAllViews();windows.toList().forEach {it.stopLoading();it.destroy()};windows.clear();host=null;super.onDestroy()
    }
}

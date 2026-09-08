package cn.campus.schedule

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.campus.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** No Javascript bridge is exposed to login pages. Export capture is restricted to the JWXT origin. */
class SchoolBrowserActivity : ComponentActivity() {
    private var web: WebView? = null
    private var status by mutableStateOf("兼容性待实测：登录后进入课表查询，选择全部周次并导出")
    private var busy by mutableStateOf(false)
    private var address by mutableStateOf("portal.sysu.edu.cn")
    private var showClear by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun school(url: String?): Boolean {
        val uri = url?.let { Uri.parse(it) } ?: return false
        val host = uri.host.orEmpty().lowercase()
        return uri.scheme == "https" && (host == "sysu.edu.cn" || host.endsWith(".sysu.edu.cn"))
    }
    private fun jwxt(url: String?) = school(url) && Uri.parse(url).host == "jwxt.sysu.edu.cn"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CampusTheme {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={ if(web?.canGoBack()==true) web?.goBack() else finish() }){Text("返回")}
                    TextButton(onClick={web?.loadUrl("https://jwxt.sysu.edu.cn/jwxt/")}){Text("本科教务")}
                    TextButton(onClick={showClear=true}){Text("清除会话")}
                }
                Text(address,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=12.dp))
                Text(status,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(modifier=Modifier.weight(1f),factory={ context ->
                    WebView(context).also { view ->
                        web=view
                        view.settings.apply {
                            javaScriptEnabled=true; domStorageEnabled=true
                            allowFileAccess=false; allowContentAccess=false
                            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportZoom(true); builtInZoomControls=true; displayZoomControls=false
                            useWideViewPort=true; loadWithOverviewMode=true
                            userAgentString="Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(view,true)
                        view.webViewClient=object:WebViewClient(){
                            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                                val url=request.url.toString()
                                if(url.startsWith("blob:") || url.startsWith("data:")) {download(url);return true}
                                if(!school(url)){status="此链接不属于学校网站，已停止跳转。可改用手机浏览器登录后导出。";return true}
                                return false
                            }
                            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?){busy=true;address=Uri.parse(url.orEmpty()).host.orEmpty()}
                            override fun onPageFinished(view:WebView,url:String?){busy=false; if(jwxt(url)) installBlobCapture(view)}
                            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame){busy=false;status="网页加载失败，请检查网络后重试；也可从电脑导出文件导入。"}}
                            override fun onReceivedSslError(view:WebView,handler:android.webkit.SslErrorHandler,error:android.net.http.SslError){handler.cancel();busy=false;status="学校页面证书验证失败，已停止加载。"}
                        }
                        view.webChromeClient=WebChromeClient()
                        view.setDownloadListener {url,_,_,_,_->download(url)}
                        view.loadUrl("https://portal.sysu.edu.cn")
                    }
                })
                TextButton(onClick={web?.reload()},modifier=Modifier.fillMaxWidth()){Text("重新加载页面")}
            }
            if(showClear) AlertDialog(onDismissRequest={showClear=false},title={Text("清除学校登录会话？")},text={Text("将退出此应用中的学校登录，不影响已导入课表。")},confirmButton={TextButton(onClick={
                CookieManager.getInstance().removeAllCookies { }
                WebStorage.getInstance().deleteAllData()
                web?.clearCache(true);web?.clearHistory();web?.loadUrl("https://portal.sysu.edu.cn")
                showClear=false
            }){Text("清除会话")}},dismissButton={TextButton(onClick={showClear=false}){Text("取消")}})
        } }
        scope.launch {
            while(isActive){
                delay(1200)
                val view=web ?: continue
                if(jwxt(view.url)) view.evaluateJavascript("window.__campusExport || null") { result ->
                    if(result!="null" && result!="\"\"" && !busy) {
                        view.evaluateJavascript("window.__campusExport=null",null)
                        runCatching { Json.parseToJsonElement(result).jsonPrimitive.content }.onSuccess { accept(it.toByteArray(Charsets.UTF_8)) }
                    }
                }
            }
        }
    }
    private fun installBlobCapture(view:WebView){
        view.evaluateJavascript("""
            (()=>{ if(window.__campusHook) return; window.__campusHook=true;
            const original=URL.createObjectURL.bind(URL);
            URL.createObjectURL=function(blob){
                const url=original(blob);
                if(blob && blob.size<=8388608 && /word|xml|octet-stream/i.test(blob.type)){
                    const reader=new FileReader();reader.onload=()=>{window.__campusExport=reader.result};reader.readAsText(blob);
                } return url;
            };
            const originalSubmit=HTMLFormElement.prototype.submit;
            HTMLFormElement.prototype.submit=function(){
                const target=new URL(this.action,location.href);
                if(target.origin===location.origin && target.pathname.endsWith('/timetable-search/stuTimeTabPrint/output')){
                    fetch(target.href,{method:'POST',body:new URLSearchParams(new FormData(this)),credentials:'same-origin'})
                    .then(r=>{if(!r.ok)throw Error();return r.blob()})
                    .then(b=>{if(b.size>8388608)throw Error();const r=new FileReader();r.onload=()=>window.__campusExport=r.result;r.readAsText(b)})
                    .catch(()=>{window.__campusExport='EXPORT_FAILED'});
                    return;
                }
                return originalSubmit.call(this);
            }; })()
        """.trimIndent(),null)
    }
    private fun download(url:String){
        if(!jwxt(web?.url)){status="请在本科教务系统的课表页面导出";return}
        when {
            url.startsWith("blob:") -> {
                val quoted=JsonPrimitive(url).toString()
                web?.evaluateJavascript("""fetch($quoted).then(r=>r.blob()).then(b=>{if(b.size>8388608)throw Error();const r=new FileReader();r.onload=()=>window.__campusExport=r.result;r.readAsText(b)}).catch(()=>{})""",null)
                status="正在获取导出文件；若没有出现预览，请改用电脑导出。"
            }
            url.startsWith("data:") -> runCatching {
                val parts=url.split(',',limit=2)
                require(parts.size==2 && url.length<12*1024*1024)
                if(parts[0].contains(";base64")) Base64.decode(parts[1],Base64.DEFAULT) else Uri.decode(parts[1]).toByteArray(Charsets.UTF_8)
            }.onSuccess{accept(it)}.onFailure{status="导出内容无法读取，请使用原始 Word 或 PDF 文件"}
            school(url) -> {
                busy=true
                // Cookies only travel to the exact school URL. Reject redirects instead of forwarding session headers.
                val cookie=CookieManager.getInstance().getCookie(url).orEmpty()
                val userAgent=web?.settings?.userAgentString.orEmpty()
                scope.launch {
                    try {
                        val bytes=withContext(Dispatchers.IO){
                            val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(java.time.Duration.ofSeconds(30)).build()
                            client.newCall(Request.Builder().url(url).header("Cookie",cookie).header("User-Agent",userAgent).build()).execute().use { response ->
                                require(response.isSuccessful){"导出未成功，请重新登录或从电脑导出"}
                                response.body?.byteStream()?.use {it.readLimited()} ?: error("导出文件为空")
                            }
                        }
                        busy=false;accept(bytes)
                    }catch(_:Exception){busy=false;status="无法下载课表，请重新登录后导出，或使用电脑文件导入。"}
                }
            }
            else -> status="不支持此下载地址，请使用学校导出的 Word 或 PDF 文件"
        }
    }
    private fun accept(bytes:ByteArray){
        if(busy)return
        busy=true
        scope.launch {
            try {
                withContext(Dispatchers.IO){
                    ScheduleFileImporter.parse(this@SchoolBrowserActivity, bytes,"2026-09-07")
                    File(cacheDir,"school-export.doc").writeBytes(bytes)
                }
                setResult(Activity.RESULT_OK);finish()
            }catch(_:Exception){status="导出内容不是可识别的课表，请选择全部周次后重试，或使用电脑导出的 Word 或 PDF 文件。";busy=false}
        }
    }
    override fun onDestroy(){scope.cancel();web?.stopLoading();web?.destroy();web=null;super.onDestroy()}
}

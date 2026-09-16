package cn.campus.schedule

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import cn.campus.core.NoteContentMode
import cn.campus.core.StudyNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable data class CommunityUser(val id:String,val username:String,val nickname:String,val role:String="USER")
@Serializable data class CommunityPost(val id:String,val title:String,val excerpt:String,val format:String,val tags:List<String> = emptyList(),val author:CommunityUser,val version:Int,val favoriteCount:Int,val commentCount:Int,val createdAt:String,val updatedAt:String,val favored:Boolean=false)
@Serializable data class CommunityComment(val id:String,val body:String,val author:CommunityUser,val createdAt:String)
@Serializable data class CommunityPostDetail(val post:CommunityPost,val body:String,val comments:List<CommunityComment> = emptyList())
@Serializable data class CommunityPage(val items:List<CommunityPost>,val nextCursor:String?=null)
@Serializable data class CommunityTokenPair(val accessToken:String,val refreshToken:String,val expiresInSeconds:Int=900,val user:CommunityUser)
@Serializable data class CommunityError(val code:String="error",val message:String="社区暂时不可用")
@Serializable data class CommunityLogin(val username:String,val password:String)
@Serializable data class CommunityRegister(val username:String,val password:String,val inviteCode:String,val email:String?=null,val nickname:String?=null)
@Serializable data class CommunityRefresh(val refreshToken:String)
@Serializable data class CommunityPostWrite(val title:String,val body:String,val format:String="PLAIN",val tags:List<String> = emptyList(),val version:Int?=null)
@Serializable data class CommunityCommentWrite(val body:String)
@Serializable data class CommunityMessage(val message:String)
@Serializable data class CommunityReportWrite(val targetType:String,val targetId:String,val reason:String)

@Entity(tableName="community_posts") data class CommunityPostCache(@PrimaryKey val id:String,val payload:String,val updatedAt:String)
@Dao interface CommunityCacheDao{
    @Query("SELECT * FROM community_posts ORDER BY updatedAt DESC LIMIT 100") suspend fun all():List<CommunityPostCache>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(items:List<CommunityPostCache>)
    @Query("DELETE FROM community_posts") suspend fun clear()
}
@Database(entities=[CommunityPostCache::class],version=1,exportSchema=false)
abstract class CommunityCacheDatabase:RoomDatabase(){abstract fun posts():CommunityCacheDao
    companion object{@Volatile private var instance:CommunityCacheDatabase?=null;fun get(c:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(c.applicationContext,CommunityCacheDatabase::class.java,"community.db").build().also{instance=it}}}
}

private class CommunityVault(private val context:Context){
    private val prefs=context.getSharedPreferences("community_auth",Context.MODE_PRIVATE);private val alias="campus-community-token";private val json=Json{ignoreUnknownKeys=true}
    fun save(pair:CommunityTokenPair){prefs.edit().putString("session",encrypt(json.encodeToString(pair))).apply()}
    fun read():CommunityTokenPair?=prefs.getString("session",null)?.let{runCatching{json.decodeFromString<CommunityTokenPair>(decrypt(it))}.getOrNull()}
    fun clear(){prefs.edit().clear().apply()}
    private fun key():SecretKey{val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (store.getKey(alias,null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generateKey()}}
    private fun encrypt(raw:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return android.util.Base64.encodeToString(c.iv+c.doFinal(raw.toByteArray()),android.util.Base64.NO_WRAP)}
    private fun decrypt(raw:String):String{val all=android.util.Base64.decode(raw,android.util.Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,all.copyOfRange(0,12)));return c.doFinal(all.copyOfRange(12,all.size)).decodeToString()}
}

private class CommunityApi(private val context:Context){
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true;explicitNulls=false};private val http=OkHttpClient();private val vault=CommunityVault(context);private val prefs=context.getSharedPreferences("community_config",Context.MODE_PRIVATE)
    var session:CommunityTokenPair?=vault.read();private set
    var baseUrl:String
        get()=prefs.getString("base_url",BuildConfig.COMMUNITY_BASE_URL).orEmpty().trimEnd('/')
        set(value){prefs.edit().putString("base_url",value.trim().trimEnd('/')).apply()}
    suspend fun posts(query:String="",mode:CommunityFeedMode=CommunityFeedMode.LATEST,authorId:String?=null):CommunityPage{val path=when(mode){CommunityFeedMode.LATEST->"/v1/posts";CommunityFeedMode.FAVORITES->"/v1/me/favorites";CommunityFeedMode.MINE->"/v1/me/posts";CommunityFeedMode.AUTHOR->"/v1/users/${authorId?:error("缺少作者编号")}/posts"};return request(path+(if(query.isBlank())"" else "?q="+java.net.URLEncoder.encode(query,"UTF-8")),auth=session!=null)}
    suspend fun detail(id:String):CommunityPostDetail=request("/v1/posts/$id",auth=session!=null)
    suspend fun login(u:String,p:String):CommunityTokenPair=request<CommunityTokenPair>("/v1/auth/login","POST",json.encodeToString(CommunityLogin(u,p)),auth=false).also{session=it;vault.save(it)}
    suspend fun register(u:String,p:String,invite:String,email:String?,nickname:String?):CommunityTokenPair=request<CommunityTokenPair>("/v1/auth/register","POST",json.encodeToString(CommunityRegister(u,p,invite,email?.takeIf{it.isNotBlank()},nickname?.takeIf{it.isNotBlank()})),auth=false).also{session=it;vault.save(it)}
    suspend fun publish(w:CommunityPostWrite):CommunityPostDetail=request("/v1/posts","POST",json.encodeToString(w))
    suspend fun update(id:String,w:CommunityPostWrite):CommunityPostDetail=request("/v1/posts/$id","PUT",json.encodeToString(w))
    suspend fun deletePost(id:String){request<CommunityMessage>("/v1/posts/$id","DELETE")}
    suspend fun comment(id:String,body:String):CommunityComment=request("/v1/posts/$id/comments","POST",json.encodeToString(CommunityCommentWrite(body)))
    suspend fun favorite(id:String,value:Boolean){request<CommunityError>("/v1/posts/$id/favorite",if(value)"PUT" else "DELETE")}
    suspend fun logoutAll(){request<CommunityMessage>("/v1/auth/logout-all","POST");logout()}
    suspend fun deleteAccount(){request<CommunityMessage>("/v1/me","DELETE");logout()}
    suspend fun report(postId:String,reason:String){request<CommunityMessage>("/v1/reports","POST",json.encodeToString(CommunityReportWrite("POST",postId,reason)))}
    fun logout(){session=null;vault.clear()}
    private suspend inline fun <reified T> request(path:String,method:String="GET",body:String?=null,auth:Boolean=true,retry:Boolean=true):T=json.decodeFromString(requestRaw(path,method,body,auth,retry))
    private suspend fun requestRaw(path:String,method:String="GET",body:String?=null,auth:Boolean=true,retry:Boolean=true):String=withContext(Dispatchers.IO){
        if(baseUrl.isBlank())error("请先配置社区测试服务器地址")
        val b=Request.Builder().url(baseUrl+path).header("Accept","application/json");if(auth)session?.accessToken?.let{b.header("Authorization","Bearer $it")}
        val media="application/json; charset=utf-8".toMediaType();when(method){"POST"->b.post((body?:"{}").toRequestBody(media));"PUT"->b.put((body?:"{}").toRequestBody(media));"DELETE"->b.delete(body?.toRequestBody(media));else->b.get()}
        http.newCall(b.build()).execute().use{response->
            if(response.code==401&&auth&&retry&&session!=null){val refreshed=runCatching{refresh()}.getOrNull();if(refreshed!=null)return@withContext requestRaw(path,method,body,auth,false)}
            val raw=response.body?.string().orEmpty();if(!response.isSuccessful){val e=runCatching{json.decodeFromString<CommunityError>(raw)}.getOrNull();error(e?.message?:"社区请求失败（${response.code}）")};raw
        }
    }
    private suspend fun refresh():CommunityTokenPair{val old=session?:error("请先登录");return request<CommunityTokenPair>("/v1/auth/refresh","POST",json.encodeToString(CommunityRefresh(old.refreshToken)),auth=false,retry=false).also{session=it;vault.save(it)}}
}

enum class CommunityFeedMode{LATEST,FAVORITES,MINE,AUTHOR}
data class CommunityUiState(val posts:List<CommunityPost> = emptyList(),val selected:CommunityPostDetail?=null,val user:CommunityUser?=null,val loading:Boolean=false,val message:String?=null,val serverUrl:String="",val query:String="",val stale:Boolean=false,val mode:CommunityFeedMode=CommunityFeedMode.LATEST,val authorId:String?=null,val authorName:String?=null)
class CommunityViewModel(app:Application):AndroidViewModel(app){
    private val api=CommunityApi(app);private val dao=CommunityCacheDatabase.get(app).posts();private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true};private val mutable=MutableStateFlow(CommunityUiState(user=api.session?.user,serverUrl=api.baseUrl));val state=mutable.asStateFlow()
    init{viewModelScope.launch{val cached=withContext(Dispatchers.IO){dao.all().mapNotNull{runCatching{json.decodeFromString<CommunityPost>(it.payload)}.getOrNull()}};if(cached.isNotEmpty())mutable.update{it.copy(posts=cached,stale=true)};if(api.baseUrl.isNotBlank())refresh()}}
    fun configure(url:String){if(url.isNotBlank()&&!url.startsWith("https://")){mutable.update{it.copy(message="服务器地址必须以 https:// 开头")};return};api.baseUrl=url;mutable.update{it.copy(serverUrl=api.baseUrl)};if(url.isNotBlank())refresh()}
    private var searchJob:Job?=null
    fun search(q:String){mutable.update{it.copy(query=q)};searchJob?.cancel();searchJob=viewModelScope.launch{delay(350);refresh()}}
    fun refresh()=launch{
        val page=api.posts(mutable.value.query,mutable.value.mode,mutable.value.authorId)
        withContext(Dispatchers.IO){
            dao.save(page.items.map{post->CommunityPostCache(post.id,json.encodeToString(post),post.updatedAt)})
        }
        mutable.update{it.copy(posts=page.items,stale=false)}
    }
    fun mode(value:CommunityFeedMode){if(value in listOf(CommunityFeedMode.FAVORITES,CommunityFeedMode.MINE)&&mutable.value.user==null){mutable.update{it.copy(message="登录后可查看收藏和自己的发布")};return};mutable.update{it.copy(mode=value,authorId=null,authorName=null)};refresh()}
    fun author(id:String,name:String){mutable.update{it.copy(selected=null,mode=CommunityFeedMode.AUTHOR,authorId=id,authorName=name,query="")};refresh()}
    fun open(id:String)=launch{val detail=api.detail(id);mutable.update{it.copy(selected=detail)}}
    fun close(){mutable.update{it.copy(selected=null)}}
    fun login(u:String,p:String)=launch{val pair=api.login(u,p);mutable.update{it.copy(user=pair.user,message="登录成功")};refresh()}
    fun register(u:String,p:String,invite:String,email:String?,nickname:String?)=launch{val pair=api.register(u,p,invite,email,nickname);mutable.update{it.copy(user=pair.user,message="注册成功")};refresh()}
    fun logout(){api.logout();mutable.update{it.copy(user=null,message="已退出登录")}}
    fun logoutAll()=launch{api.logoutAll();mutable.update{it.copy(user=null,message="已退出全部设备")}}
    fun deleteAccount()=launch{api.deleteAccount();withContext(Dispatchers.IO){dao.clear()};mutable.update{it.copy(user=null,posts=emptyList(),selected=null,message="账号已注销，公开内容已隐藏")}}
    fun publish(title:String,body:String,markdown:Boolean,tags:List<String>)=launch{val d=api.publish(CommunityPostWrite(title,body,if(markdown)"MARKDOWN" else "PLAIN",tags));mutable.update{it.copy(selected=d,message="已发布到社区")};refresh()}
    fun updatePost(title:String,body:String,markdown:Boolean,tags:List<String>,version:Int)=launch{val old=mutable.value.selected?:return@launch;val d=api.update(old.post.id,CommunityPostWrite(title,body,if(markdown)"MARKDOWN" else "PLAIN",tags,version));mutable.update{it.copy(selected=d,message="社区版本已更新")};refresh()}
    fun deletePost()=launch{val old=mutable.value.selected?:return@launch;api.deletePost(old.post.id);mutable.update{it.copy(selected=null,message="帖子已删除")};refresh()}
    fun comment(body:String)=launch{val d=mutable.value.selected?:return@launch;api.comment(d.post.id,body);mutable.update{it.copy(selected=api.detail(d.post.id),message="评论已发布")}}
    fun favorite(){launch{val d=mutable.value.selected?:return@launch;api.favorite(d.post.id,!d.post.favored);mutable.update{it.copy(selected=api.detail(d.post.id))};refresh()}}
    fun report(reason:String)=launch{val d=mutable.value.selected?:return@launch;api.report(d.post.id,reason);mutable.update{it.copy(message="举报已提交")}}
    fun consumeMessage(){mutable.update{it.copy(message=null)}}
    private fun launch(block:suspend()->Unit)=viewModelScope.launch{mutable.update{it.copy(loading=true)};runCatching{block()}.onFailure{e->mutable.update{it.copy(message=e.message?:"社区暂时不可用")}};mutable.update{it.copy(loading=false)}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CommunityScreen(publishNote:StudyNote?,onPublishConsumed:()->Unit,onBack:()->Unit,vm:CommunityViewModel=viewModel()){
    val state by vm.state.collectAsStateWithLifecycle();var account by remember{mutableStateOf(false)};var write by remember{mutableStateOf(publishNote!=null)};var config by remember{mutableStateOf(state.serverUrl.isBlank())}
    LaunchedEffect(publishNote?.id){if(publishNote!=null)write=true}
    BackHandler(enabled=state.selected!=null||account||write||config){when{write->{write=false;onPublishConsumed()};account->account=false;config->config=false;state.selected!=null->vm.close();else->onBack()}}
    if(state.selected!=null){CommunityDetail(state.selected!!,state.user,state.loading,vm::close,vm::favorite,{vm.comment(it)},vm::report,vm::updatePost,vm::deletePost,vm::author);return}
    Scaffold(topBar={TopAppBar(title={Text("课业社区")},actions={IconButton(onClick={config=true}){Icon(Icons.Outlined.Settings,"服务器设置")};TextButton(onClick={account=true}){Text(state.user?.nickname?:"登录")}})},floatingActionButton={FloatingActionButton(onClick={if(state.user==null)account=true else write=true}){Icon(Icons.Outlined.Edit,"发布笔记")}}){padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            OutlinedTextField(state.query,{vm.search(it)},label={Text("搜索标题、正文")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(state.mode==CommunityFeedMode.LATEST,{vm.mode(CommunityFeedMode.LATEST)},{Text("最新")});FilterChip(state.mode==CommunityFeedMode.FAVORITES,{vm.mode(CommunityFeedMode.FAVORITES)},{Text("收藏")});FilterChip(state.mode==CommunityFeedMode.MINE,{vm.mode(CommunityFeedMode.MINE)},{Text("我的发布")})}
            if(state.mode==CommunityFeedMode.AUTHOR)Text("作者主页 · ${state.authorName}",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=18.dp,vertical=6.dp))
            if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.stale)Text("当前显示离线缓存",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=18.dp))
            Box(Modifier.weight(1f)){
                if(state.serverUrl.isBlank())CommunityEmpty("社区服务器尚未配置","部署 CloudBase 服务后，在右上角设置 HTTPS 地址。",{config=true},"配置服务器")
                else if(state.posts.isEmpty()&&!state.loading)CommunityEmpty("还没有公开笔记","登录后可发布第一篇学习笔记。",{if(state.user==null)account=true else write=true},if(state.user==null)"登录" else "发布")
                else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(state.posts,key={it.id}){CommunityPostCard(it){vm.open(it.id)}}}
            }
        }
    }
    state.message?.let{AlertDialog(onDismissRequest=vm::consumeMessage,confirmButton={TextButton(onClick=vm::consumeMessage){Text("知道了")}},text={Text(it)})}
    if(config)CommunityConfigDialog(state.serverUrl,{config=false},{vm.configure(it);config=false})
    if(account)if(state.user==null)CommunityAccountDialog(state.loading,{account=false},vm::login,vm::register)else CommunitySessionDialog(state.user!!,state.loading,{account=false},{vm.logout();account=false},{vm.logoutAll();account=false},{vm.deleteAccount();account=false})
    if(write)CommunityWriteDialog(publishNote,state.loading,{write=false;onPublishConsumed()}){t,b,m,tags->vm.publish(t,b,m,tags);write=false;onPublishConsumed()}
}

@Composable private fun CommunityPostCard(post:CommunityPost,onClick:()->Unit){OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(post.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(post.excerpt,maxLines=3,overflow=TextOverflow.Ellipsis);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(post.author.nickname,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text("${post.favoriteCount} 收藏 · ${post.commentCount} 评论",style=MaterialTheme.typography.labelSmall)}}}}
@Composable private fun CommunityEmpty(title:String,body:String,onClick:()->Unit,action:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.Forum,null,modifier=Modifier.size(52.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body,modifier=Modifier.padding(horizontal=30.dp));Button(onClick=onClick){Text(action)}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CommunityDetail(d:CommunityPostDetail,user:CommunityUser?,busy:Boolean,onBack:()->Unit,onFavorite:()->Unit,onComment:(String)->Unit,onReport:(String)->Unit,onUpdate:(String,String,Boolean,List<String>,Int)->Unit,onDelete:()->Unit,onAuthor:(String,String)->Unit){var comment by remember{mutableStateOf("")};var report by remember{mutableStateOf(false)};var edit by remember{mutableStateOf(false)};var delete by remember{mutableStateOf(false)};val own=user?.id==d.post.author.id;Scaffold(topBar={TopAppBar(title={Text("社区笔记")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"返回")}},actions={if(own)IconButton(onClick={edit=true}){Icon(Icons.Outlined.Edit,"更新社区版本")};IconButton(onClick=onFavorite,enabled=user!=null&&!busy){Icon(if(d.post.favored)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"收藏")};IconButton(onClick={report=true},enabled=user!=null&&!busy){Icon(Icons.Outlined.Flag,"举报")}})}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text(d.post.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text("${d.post.author.nickname} · ${d.post.commentCount} 评论",color=MaterialTheme.colorScheme.primary,modifier=Modifier.clickable{onAuthor(d.post.author.id,d.post.author.nickname)});SelectionContainer{Text(d.body)};if(own)TextButton(onClick={delete=true}){Text("删除社区版本",color=MaterialTheme.colorScheme.error)}};item{HorizontalDivider();Text("评论",style=MaterialTheme.typography.titleLarge)};items(d.comments,key={it.id}){c->OutlinedCard{Column(Modifier.padding(13.dp)){Text(c.author.nickname,fontWeight=FontWeight.Bold);Text(c.body)}}};if(user!=null)item{OutlinedTextField(comment,{comment=it.take(500)},label={Text("写评论")},modifier=Modifier.fillMaxWidth());Button(onClick={onComment(comment);comment=""},enabled=comment.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth()){Text("发布评论")}}else item{Text("登录后可收藏、评论和发布。",color=MaterialTheme.colorScheme.onSurfaceVariant)}}};if(report)CommunityReportDialog({report=false}){onReport(it);report=false};if(edit)CommunityEditDialog(d,{edit=false}){t,b,m,tags->onUpdate(t,b,m,tags,d.post.version);edit=false};if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除社区版本？")},text={Text("本机原笔记不会受到影响。")},confirmButton={Button(onClick={onDelete();delete=false},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("删除")}},dismissButton={TextButton(onClick={delete=false}){Text("取消")}})}
@Composable private fun CommunityReportDialog(onDismiss:()->Unit,onReport:(String)->Unit){var reason by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("举报这篇笔记")},text={OutlinedTextField(reason,{reason=it.take(300)},label={Text("原因（2至300字）")},minLines=3)},confirmButton={Button(onClick={onReport(reason.trim())},enabled=reason.trim().length>=2){Text("提交")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})}
@Composable private fun CommunityEditDialog(d:CommunityPostDetail,onDismiss:()->Unit,onSave:(String,String,Boolean,List<String>)->Unit){var title by remember{mutableStateOf(d.post.title)};var body by remember{mutableStateOf(d.body)};var markdown by remember{mutableStateOf(d.post.format=="MARKDOWN")};var tags by remember{mutableStateOf(d.post.tags.joinToString("，"))};AlertDialog(onDismissRequest=onDismiss,title={Text("更新社区版本")},text={Column(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it.take(80)},label={Text("标题")});OutlinedTextField(body,{body=it.take(20000)},label={Text("正文")},minLines=8,maxLines=15);OutlinedTextField(tags,{tags=it.take(100)},label={Text("标签")});Row(verticalAlignment=Alignment.CenterVertically){Switch(markdown,{markdown=it});Text("Markdown")}}},confirmButton={Button(onClick={onSave(title.trim(),body,markdown,tags.split(',','，').map{it.trim()}.filter{it.isNotBlank()}.take(5))},enabled=title.isNotBlank()&&body.isNotBlank()){Text("保存更新")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})}

@Composable private fun CommunityConfigDialog(current:String,onDismiss:()->Unit,onSave:(String)->Unit){var value by remember(current){mutableStateOf(current)};AlertDialog(onDismissRequest=onDismiss,title={Text("社区测试服务器")},text={Column{Text("填写 CloudBase CloudRun 的 HTTPS 服务地址。地址只保存在本机。",style=MaterialTheme.typography.bodySmall);OutlinedTextField(value,{value=it},label={Text("https://…")},modifier=Modifier.fillMaxWidth())}},confirmButton={Button(onClick={onSave(value)}){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})}
@Composable private fun CommunityAccountDialog(busy:Boolean,onDismiss:()->Unit,onLogin:(String,String)->Unit,onRegister:(String,String,String,String?,String?)->Unit){var register by remember{mutableStateOf(false)};var user by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var invite by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var nickname by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(if(register)"邀请码注册" else "登录社区")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(user,{user=it.lowercase().filter{c->c.isLetterOrDigit()||c=='_'}.take(20)},label={Text("用户名")});OutlinedTextField(pass,{pass=it.take(72)},label={Text("密码（至少10位）")},visualTransformation=PasswordVisualTransformation());if(register){OutlinedTextField(invite,{invite=it.take(32)},label={Text("邀请码")});OutlinedTextField(nickname,{nickname=it.take(40)},label={Text("公开昵称（可选）")});OutlinedTextField(email,{email=it.take(254)},label={Text("邮箱（可选）")})};TextButton(onClick={register=!register}){Text(if(register)"已有账号，去登录" else "使用邀请码注册")}}},confirmButton={Button(onClick={if(register)onRegister(user,pass,invite,email,nickname) else onLogin(user,pass)},enabled=!busy&&user.length>=4&&pass.length>=10&&(!register||invite.length>=6)){Text(if(register)"注册" else "登录")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})}
@Composable private fun CommunitySessionDialog(user:CommunityUser,busy:Boolean,onDismiss:()->Unit,onLogout:()->Unit,onLogoutAll:()->Unit,onDelete:()->Unit){var confirmDelete by remember{mutableStateOf(false)};if(confirmDelete){AlertDialog(onDismissRequest={confirmDelete=false},title={Text("注销社区账号？")},text={Text("登录令牌会立即失效，你发布的内容将被隐藏。此操作不可撤销。")},confirmButton={Button(onClick=onDelete,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("确认注销")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("取消")}});return};AlertDialog(onDismissRequest=onDismiss,title={Text(user.nickname)},text={Column{Text("@${user.username}");TextButton(onClick=onLogout,enabled=!busy){Text("退出此设备")};TextButton(onClick=onLogoutAll,enabled=!busy){Text("退出全部设备")};TextButton(onClick={confirmDelete=true},enabled=!busy){Text("注销账号",color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})}
@Composable private fun CommunityWriteDialog(note:StudyNote?,busy:Boolean,onDismiss:()->Unit,onPublish:(String,String,Boolean,List<String>)->Unit){var title by remember(note?.id){mutableStateOf(note?.title.orEmpty())};var body by remember(note?.id){mutableStateOf(note?.content.orEmpty())};var markdown by remember(note?.id){mutableStateOf(note?.contentMode==NoteContentMode.MARKDOWN)};var tags by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(if(note==null)"发布社区笔记" else "发布笔记副本")},text={Column(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){if(note!=null)Text("只上传当前标题、正文、标签和公开昵称；本地修改不会自动同步。",style=MaterialTheme.typography.bodySmall);OutlinedTextField(title,{title=it.take(80)},label={Text("标题")},modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it.take(20000)},label={Text("正文")},minLines=8,maxLines=15,modifier=Modifier.fillMaxWidth());OutlinedTextField(tags,{tags=it.take(100)},label={Text("标签，用逗号分隔")});Row(verticalAlignment=Alignment.CenterVertically){Switch(markdown,{markdown=it});Text("Markdown")}}},confirmButton={Button(onClick={onPublish(title.trim(),body,markdown,tags.split(',','，').map{it.trim()}.filter{it.isNotBlank()}.take(5))},enabled=!busy&&title.isNotBlank()&&body.isNotBlank()){Text("确认发布")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})}

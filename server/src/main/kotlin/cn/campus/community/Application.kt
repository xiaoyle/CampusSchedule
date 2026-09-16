package cn.campus.community

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(){embeddedServer(Netty,port=System.getenv("PORT")?.toIntOrNull()?:8080,host="0.0.0.0",module=Application::module).start(wait=true)}

fun Application.module(){
    val secret=env("JWT_SECRET","local-development-secret-change-before-deploying")
    val security=Security(secret)
    val db=CommunityDatabase(env("DATABASE_URL","jdbc:mysql://127.0.0.1:3306/campus_schedule?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"),env("DATABASE_USER","campus"),env("DATABASE_PASSWORD","campus_dev_only"),security)
    db.bootstrapInvite(System.getenv("ADMIN_BOOTSTRAP_INVITE"))
    environment.monitor.subscribe(ApplicationStopped){db.close()}
    val authLimiter=RateLimiter(8,60_000);val writeLimiter=RateLimiter(20,60_000)
    install(ContentNegotiation){json(Json{ignoreUnknownKeys=true;encodeDefaults=true;explicitNulls=false})}
    install(CallLogging){filter{it.request.path().startsWith("/v1/")}}
    install(StatusPages){
        exception<ApiException>{call,e->call.respond(HttpStatusCode.fromValue(e.status),ApiError(e.code,e.message))}
        exception<Throwable>{call,e->this@module.environment.log.error("request_failed ${call.request.path()} ${e::class.simpleName}");call.respond(HttpStatusCode.InternalServerError,ApiError("internal_error","服务暂时不可用，请稍后重试"))}
    }
    install(Authentication){jwt("auth"){verifier(security.verifier());validate{JWTPrincipal(it.payload)}}}
    routing{
        get("/health"){call.respond(mapOf("status" to "ok","version" to "0.14.0"))}
        route("/v1"){
            route("/auth"){
                post("/register"){authLimiter.check(call.clientKey());val req=call.receive<RegisterRequest>();val (u,e)=db.register(req);call.respond(HttpStatusCode.Created,tokenPair(db,security,u,e))}
                post("/login"){authLimiter.check(call.clientKey());val req=call.receive<LoginRequest>();val (u,e,_)=db.login(req.username,req.password);call.respond(tokenPair(db,security,u,e))}
                post("/refresh"){authLimiter.check(call.clientKey());val req=call.receive<RefreshRequest>();val (u,e,refresh)=db.rotateRefresh(req.refreshToken);call.respond(TokenPair(security.accessToken(u,e),refresh,user=u))}
            }
            authenticate("auth",optional=true){
                get("/posts"){val viewer=call.optionalUser(db);call.respond(db.listPosts(call.request.queryParameters["cursor"],call.request.queryParameters["q"],call.request.queryParameters["limit"]?.toIntOrNull()?:20,viewer?.first?.id))}
                get("/posts/{id}"){val viewer=call.optionalUser(db);call.respond(db.postDetail(call.parameters.requireId(),viewer?.first?.id))}
                get("/users/{id}/posts"){val viewer=call.optionalUser(db);call.respond(db.listPosts(null,call.request.queryParameters["q"],20,viewer?.first?.id,authorId=call.parameters.requireId()))}
            }
            authenticate("auth"){
                get("/me"){call.respond(call.requireUser(db).first)}
                get("/me/posts"){val u=call.requireUser(db).first;call.respond(db.listPosts(null,call.request.queryParameters["q"],20,u.id,authorId=u.id))}
                get("/me/favorites"){val u=call.requireUser(db).first;call.respond(db.listPosts(null,call.request.queryParameters["q"],20,u.id,favoriteUserId=u.id))}
                post("/auth/logout-all"){val u=call.requireUser(db).first;db.revokeAll(u.id);call.respond(MessageResponse("已退出全部设备"))}
                delete("/me"){val u=call.requireUser(db).first;db.deleteAccount(u.id);call.respond(MessageResponse("账号已注销"))}
                post("/posts"){val u=call.requireUser(db).first;writeLimiter.check(u.id);call.respond(HttpStatusCode.Created,db.createPost(u.id,call.receive()))}
                put("/posts/{id}"){val u=call.requireUser(db).first;call.respond(db.updatePost(u.id,call.parameters.requireId(),call.receive()))}
                delete("/posts/{id}"){val u=call.requireUser(db).first;db.deletePost(u.id,call.parameters.requireId());call.respond(MessageResponse("帖子已删除"))}
                put("/posts/{id}/favorite"){val u=call.requireUser(db).first;db.favorite(u.id,call.parameters.requireId(),true);call.respond(MessageResponse("已收藏"))}
                delete("/posts/{id}/favorite"){val u=call.requireUser(db).first;db.favorite(u.id,call.parameters.requireId(),false);call.respond(MessageResponse("已取消收藏"))}
                post("/posts/{id}/comments"){val u=call.requireUser(db).first;writeLimiter.check("comment:${u.id}");call.respond(HttpStatusCode.Created,db.addComment(u.id,call.parameters.requireId(),call.receive<CommentWrite>().body))}
                delete("/comments/{id}"){val u=call.requireUser(db).first;db.deleteComment(u.id,call.parameters.requireId());call.respond(MessageResponse("评论已删除"))}
                post("/reports"){val u=call.requireUser(db).first;writeLimiter.check("report:${u.id}");db.report(u.id,call.receive());call.respond(HttpStatusCode.Created,MessageResponse("举报已提交"))}
                route("/admin"){
                    get("/reports"){call.requireAdmin(db);call.respond(db.reports())}
                    post("/invites"){val u=call.requireAdmin(db);db.createInvite(u.id,call.receive());call.respond(HttpStatusCode.Created,MessageResponse("邀请码已创建"))}
                    post("/moderate/{id}"){val u=call.requireAdmin(db);db.moderate(u.id,call.parameters.requireId(),call.receive());call.respond(MessageResponse("管理操作已记录"))}
                }
            }
        }
    }
}

private fun tokenPair(db:CommunityDatabase,security:Security,u:PublicUser,e:Int)=TokenPair(security.accessToken(u,e),db.saveRefresh(u.id),user=u)
private fun env(name:String,default:String)=System.getenv(name)?.takeIf{it.isNotBlank()}?:default
private fun ApplicationCall.clientKey()=request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?:request.local.remoteHost
private fun Parameters.requireId()=get("id")?.takeIf{it.length in 1..64}?:throw ApiException(400,"invalid_id","编号格式不正确")
private fun ApplicationCall.requireUser(db:CommunityDatabase):Pair<PublicUser,Int>{val jwt=principal<JWTPrincipal>()?.payload?:throw ApiException(401,"login_required","请先登录");val user=db.user(jwt.subject);if(user.second!=jwt.getClaim("epoch").asInt())throw ApiException(401,"session_invalid","登录状态已失效");return user}
private fun ApplicationCall.optionalUser(db:CommunityDatabase):Pair<PublicUser,Int>?=runCatching{requireUser(db)}.getOrNull()
private fun ApplicationCall.requireAdmin(db:CommunityDatabase)=requireUser(db).first.also{if(it.role!="ADMIN")throw ApiException(403,"admin_required","需要管理员权限")}

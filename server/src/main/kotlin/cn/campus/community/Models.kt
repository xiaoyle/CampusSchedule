package cn.campus.community

import kotlinx.serialization.Serializable

@Serializable data class ApiError(val code:String,val message:String)
@Serializable data class RegisterRequest(val username:String,val password:String,val inviteCode:String,val email:String?=null,val nickname:String?=null)
@Serializable data class LoginRequest(val username:String,val password:String)
@Serializable data class RefreshRequest(val refreshToken:String)
@Serializable data class TokenPair(val accessToken:String,val refreshToken:String,val expiresInSeconds:Int=900,val user:PublicUser)
@Serializable data class PublicUser(val id:String,val username:String,val nickname:String,val role:String="USER")
@Serializable data class PostWrite(val title:String,val body:String,val format:String="PLAIN",val tags:List<String> = emptyList(),val version:Int?=null)
@Serializable data class PostSummary(val id:String,val title:String,val excerpt:String,val format:String,val tags:List<String>,val author:PublicUser,val version:Int,val favoriteCount:Int,val commentCount:Int,val createdAt:String,val updatedAt:String,val favored:Boolean=false)
@Serializable data class PostDetail(val post:PostSummary,val body:String,val comments:List<CommentView>)
@Serializable data class CommentWrite(val body:String)
@Serializable data class CommentView(val id:String,val body:String,val author:PublicUser,val createdAt:String)
@Serializable data class PageResponse<T>(val items:List<T>,val nextCursor:String?=null)
@Serializable data class ReportRequest(val targetType:String,val targetId:String,val reason:String)
@Serializable data class InviteRequest(val code:String,val maxUses:Int,val expiresAt:String?=null)
@Serializable data class ModerateRequest(val action:String,val reason:String="")
@Serializable data class MessageResponse(val message:String)
@Serializable data class AdminReport(val id:String,val reporter:String,val targetType:String,val targetId:String,val reason:String,val status:String,val createdAt:String)

class ApiException(val status:Int,val code:String,override val message:String):RuntimeException(message)

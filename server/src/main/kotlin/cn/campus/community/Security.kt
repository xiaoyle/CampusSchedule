package cn.campus.community

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.mkammerer.argon2.Argon2Factory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Security(private val secret:String){
    init{require(secret.length>=32){"JWT_SECRET must contain at least 32 characters"}}
    private val algorithm=Algorithm.HMAC256(secret)
    private val random=SecureRandom()
    fun hashPassword(password:String):String{val chars=password.toCharArray();return Argon2Factory.create().run{try{hash(3,65536,1,chars)}finally{wipeArray(chars)}}}
    fun verifyPassword(hash:String,password:String):Boolean{val chars=password.toCharArray();return Argon2Factory.create().run{try{verify(hash,chars)}finally{wipeArray(chars)}}}
    fun accessToken(user:PublicUser,epoch:Int):String=JWT.create().withSubject(user.id).withClaim("username",user.username).withClaim("role",user.role).withClaim("epoch",epoch).withIssuedAt(Date()).withExpiresAt(Date.from(Instant.now().plusSeconds(900))).sign(algorithm)
    fun verifier()=JWT.require(algorithm).build()
    fun randomSecret():String=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)}
    fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
}

class RateLimiter(private val limit:Int,private val windowMs:Long){
    private val buckets=ConcurrentHashMap<String,ArrayDeque<Long>>()
    fun check(key:String){
        val now=System.currentTimeMillis();val q=buckets.computeIfAbsent(key){ArrayDeque()}
        synchronized(q){while(q.isNotEmpty()&&q.first<now-windowMs)q.removeFirst();if(q.size>=limit)throw ApiException(429,"rate_limited","操作过于频繁，请稍后再试");q.addLast(now)}
    }
}

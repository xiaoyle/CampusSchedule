package cn.campus.community

import kotlin.test.*

class SecurityTest{
    private val security=Security("0123456789abcdef0123456789abcdef")
    @Test fun passwordUsesArgon2AndVerifies(){val hash=security.hashPassword("correct horse battery staple");assertTrue(hash.startsWith("\$argon2"));assertTrue(security.verifyPassword(hash,"correct horse battery staple"));assertFalse(security.verifyPassword(hash,"wrong password"))}
    @Test fun accessTokenContainsShortLivedIdentity(){val token=security.verifier().verify(security.accessToken(PublicUser("id-1","student_1","同学"),3));assertEquals("id-1",token.subject);assertEquals(3,token.getClaim("epoch").asInt());assertTrue(token.expiresAt.time-token.issuedAt.time<=901_000)}
    @Test fun limiterRejectsBurst(){val limiter=RateLimiter(2,60_000);limiter.check("same");limiter.check("same");assertFailsWith<ApiException>{limiter.check("same")}}
}

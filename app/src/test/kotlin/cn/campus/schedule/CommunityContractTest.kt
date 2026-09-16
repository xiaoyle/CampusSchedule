package cn.campus.schedule

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CommunityContractTest{
    private val json=Json{ignoreUnknownKeys=true}
    @Test fun publicPostContractReadsServerPayload(){val raw="""{"id":"p1","title":"高数复习","excerpt":"矩阵","format":"MARKDOWN","tags":["数学"],"author":{"id":"u1","username":"student_1","nickname":"同学","role":"USER"},"version":2,"favoriteCount":3,"commentCount":1,"createdAt":"2026-09-15T00:00:00Z","updatedAt":"2026-09-15T00:00:00Z","favored":true}""";val post=json.decodeFromString<CommunityPost>(raw);assertEquals("p1",post.id);assertTrue(post.favored);assertEquals(2,post.version)}
    @Test fun tokenContractKeepsRefreshTokenOutOfPostCache(){val token=json.decodeFromString<CommunityTokenPair>("""{"accessToken":"a","refreshToken":"r","user":{"id":"u","username":"user_1","nickname":"用户"}}""");val cached=CommunityPostCache("p","{}","now");assertEquals("r",token.refreshToken);assertFalse(cached.payload.contains("refreshToken"))}
}

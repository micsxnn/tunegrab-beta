package com.tunegrab.app.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSearchServiceTest {

    private fun result(id: String) = ProcessedSearchResult(
        videoId = id,
        webUrl = "https://www.youtube.com/watch?v=$id",
        title = "Song $id",
        channel = "Artist",
        thumbnailUrl = null,
        description = ""
    )

    @Test
    fun `primary bem-sucedido nao chama fallback`() = runTest {
        var fallbackCalls = 0
        val service = HybridSearchService(
            primary = SearchProvider { _, _ -> listOf(result("primary")) },
            fallback = SearchProvider { _, _ -> fallbackCalls++; emptyList() }
        )

        val results = service.search("  música  ")

        assertEquals(listOf("primary"), results.map { it.videoId })
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `primary com lista vazia nao chama fallback`() = runTest {
        var fallbackCalls = 0
        val service = HybridSearchService(
            primary = SearchProvider { _, _ -> emptyList() },
            fallback = SearchProvider { _, _ -> fallbackCalls++; listOf(result("fallback")) }
        )

        val results = service.search("música")

        assertTrue(results.isEmpty())
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `falha tecnica do primary chama fallback`() = runTest {
        var fallbackCalls = 0
        val service = HybridSearchService(
            primary = SearchProvider { _, _ -> throw IllegalStateException("yt-dlp caiu") },
            fallback = SearchProvider { _, _ -> fallbackCalls++; listOf(result("fallback")) }
        )

        val results = service.search("música")

        assertEquals(listOf("fallback"), results.map { it.videoId })
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `fallback tambem falhando propaga erro legivel`() = runTest {
        val service = HybridSearchService(
            primary = SearchProvider { _, _ -> throw IllegalStateException("yt-dlp caiu") },
            fallback = SearchProvider { _, _ -> throw IllegalStateException("newpipe caiu") }
        )

        val error = runCatching { service.search("música") }.exceptionOrNull()

        assertTrue(error is SearchProviderException)
        assertTrue(error?.message.orEmpty().contains("Não foi possível buscar"))
    }

    @Test
    fun `resultado primary tem precedencia sobre fallback`() = runTest {
        val service = HybridSearchService(
            primary = SearchProvider { _, _ -> listOf(result("primary")) },
            fallback = SearchProvider { _, _ -> listOf(result("fallback")) }
        )

        val results = service.search("música")

        assertEquals(listOf("primary"), results.map { it.videoId })
    }
}

package com.tunegrab.app.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun result(id: String) = ProcessedSearchResult(
        videoId = id,
        webUrl = "https://www.youtube.com/watch?v=$id",
        title = "Song $id",
        channel = "Artist",
        thumbnailUrl = null,
        description = ""
    )

    @Test
    fun `busca publica resultados e estado final`() = runTest(dispatcher) {
        val provider = SearchProvider { _, _ -> listOf(result("one")) }
        val viewModel = SearchViewModel(provider)

        viewModel.search("música")
        advanceUntilIdle()

        assertEquals(listOf("one"), viewModel.searchResults.value.map { it.videoId })
        assertEquals(1, viewModel.uiState.value.totalResults)
        assertTrue(!viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `consulta vazia nao chama provider`() = runTest(dispatcher) {
        var calls = 0
        val provider = SearchProvider { _, _ -> calls++; emptyList() }
        val viewModel = SearchViewModel(provider)

        viewModel.search("   ")
        advanceUntilIdle()

        assertEquals(0, calls)
    }

    @Test
    fun `erro do provider aparece no estado`() = runTest(dispatcher) {
        val provider = SearchProvider { _, _ -> throw IllegalStateException("falhou") }
        val viewModel = SearchViewModel(provider)

        viewModel.search("música")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("falhou") == true)
        assertTrue(!viewModel.uiState.value.isSearching)
    }

    @Test
    fun `retry reutiliza a ultima consulta`() = runTest(dispatcher) {
        var calls = 0
        val provider = SearchProvider { _, _ ->
            calls++
            if (calls == 1) throw IllegalStateException("primeira falha")
            listOf(result("ok"))
        }
        val viewModel = SearchViewModel(provider)

        viewModel.search("música")
        advanceUntilIdle()
        viewModel.retrySearch()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(listOf("ok"), viewModel.searchResults.value.map { it.videoId })
    }

    @Test
    fun `resposta antiga nao sobrescreve consulta nova`() = runTest(dispatcher) {
        val first = CompletableDeferred<List<ProcessedSearchResult>>()
        val provider = SearchProvider { query, _ ->
            if (query == "antiga") first.await() else listOf(result("nova"))
        }
        val viewModel = SearchViewModel(provider)

        viewModel.search("antiga")
        advanceUntilIdle()
        viewModel.search("nova")
        advanceUntilIdle()
        first.complete(listOf(result("antiga")))
        advanceUntilIdle()

        assertEquals(listOf("nova"), viewModel.searchResults.value.map { it.videoId })
        assertEquals("nova", viewModel.uiState.value.query)
    }
}

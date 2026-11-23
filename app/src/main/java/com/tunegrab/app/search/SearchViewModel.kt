package com.tunegrab.app.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel da aba Buscar (v0.23.1-beta).
 *
 * - UMA busca por vez: a nova CANCELA a anterior (apertar Buscar duas vezes
 *   não deixa resposta velha disputar com resposta nova).
 * - Erro ≠ vazio: o [YouTubeSearchService] só lança quando os dois motores
 *   falham — aí a UI mostra o erro e o botão de retry. Vazio de verdade
 *   aparece como "nada encontrado", nunca como erro.
 * - Um único estado ([SearchUiState]): quem renderiza é a aba — acabou a
 *   disputa entre uiState e searchResults que o beta anterior tinha.
 */
class SearchViewModel : ViewModel() {
    private val TAG = "SearchViewModel"

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null

    init {
        YouTubeSearchService.init()
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return // a aba já valida; guarda dupla barata
        searchJob?.cancel()
        _uiState.value = SearchUiState(isSearching = true, query = trimmed)
        searchJob = viewModelScope.launch {
            try {
                val results = YouTubeSearchService.search(trimmed)
                _uiState.value = SearchUiState(isSearching = false, query = trimmed, results = results)
            } catch (e: CancellationException) {
                throw e // busca nova chegou (ou a aba morreu): não é erro
            } catch (t: Throwable) {
                Log.e(TAG, "Erro na busca", t)
                _uiState.value = SearchUiState(
                    isSearching = false,
                    query = trimmed,
                    error = t.message ?: "Erro ao buscar."
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }

    fun retrySearch() {
        _uiState.value.query.takeIf { it.isNotBlank() }?.let { search(it) }
    }
}

/**
 * Estado da UI de busca.
 */
data class SearchUiState(
    val isSearching: Boolean = false,
    val query: String = "",
    val results: List<ProcessedSearchResult> = emptyList(),
    val error: String? = null
)

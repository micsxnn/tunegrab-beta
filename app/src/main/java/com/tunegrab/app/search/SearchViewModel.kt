package com.tunegrab.app.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar a busca de músicas
 */
class SearchViewModel : ViewModel() {
    private val TAG = "SearchViewModel"

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _searchResults = MutableStateFlow<List<ProcessedSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ProcessedSearchResult>> = _searchResults

    private var currentQuery = ""

    init {
        // Inicializa o serviço de busca
        YouTubeSearchService.init()
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _uiState.value = SearchUiState()
            return
        }

        currentQuery = query.trim()
        _uiState.value = SearchUiState(isSearching = true, query = currentQuery)

        if (!YouTubeSearchService.isConfigured()) {
            Log.w(TAG, "Serviço de busca não configurado corretamente")
            _uiState.value = SearchUiState(
                isSearching = false,
                error = "Serviço de busca não está funcionando no momento."
            )
            return
        }

        viewModelScope.launch {
            try {
                val results = YouTubeSearchService.search(currentQuery)
                _searchResults.value = results
                _uiState.value = SearchUiState(
                    isSearching = false,
                    query = currentQuery,
                    results = results,
                    totalResults = results.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro na busca", e)
                _uiState.value = SearchUiState(
                    isSearching = false,
                    query = currentQuery,
                    error = "Erro ao buscar: ${e.message}"
                )
            }
        }
    }

    fun clearSearch() {
        currentQuery = ""
        _searchResults.value = emptyList()
        _uiState.value = SearchUiState()
    }

    fun retrySearch() {
        if (currentQuery.isNotEmpty()) {
            search(currentQuery)
        }
    }
}

/**
 * Estado da UI de busca
 */
data class SearchUiState(
    val isSearching: Boolean = false,
    val query: String = "",
    val results: List<ProcessedSearchResult> = emptyList(),
    val totalResults: Int = 0,
    val error: String? = null
)
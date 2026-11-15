package com.tunegrab.app.search

import android.util.Log
import com.tunegrab.app.yt.YtExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSearchResultExtractor
import org.schabi.newpipe.extractor.search.SearchQuery
import org.schabi.newpipe.extractor.ContentMetaData
import java.util.concurrent.TimeUnit

/**
 * Serviço para busca do YouTube usando NewPipeExtractor
 * - Não requer API key
 * - Funciona igual ao app já faz para downloads
 * - Usa o mesmo mecanismo de PoToken/BotGuard
 */
object YouTubeSearchService {
    private const val TAG = "YouTubeSearchService"
    private const val DEFAULT_MAX_RESULTS = 25

    private var initialized = false

    /**
     * Inicializa o serviço (usa o mesmo init do YtExtractor)
     */
    fun init() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    YtExtractor.init()
                    initialized = true
                }
            }
        }
    }

    /**
     * Busca vídeos no YouTube usando web scraping/NewPipe
     * @param query Termo de busca
     * @param maxResults Número máximo de resultados
     * @return Lista de resultados processados
     */
    suspend fun search(query: String, maxResults: Int = DEFAULT_MAX_RESULTS): List<ProcessedSearchResult> =
        withContext(Dispatchers.IO) {
        try {
            init()

            if (query.isBlank()) {
                return@withContext emptyList()
            }

            // Usa NewPipe para fazer a busca
            val service = ServiceList.YouTube
            val searchQuery = SearchQuery(query, SearchQuery.SearchResultType.VIDEO)

            // Busca os resultados
            val results = YoutubeSearchResultExtractor.getSearchResultMetaData(
                service = service,
                query = searchQuery,
                limit = maxResults.coerceAtMost(100)
            )

            // Converte para nossos modelos
            results.mapNotNull { meta ->
                try {
                    val videoId = meta.getUrl()?.let { extractVideoId(it) }
                    if (videoId != null) {
                        ProcessedSearchResult(
                            videoId = videoId,
                            title = meta.getName() ?: "",
                            channel = meta.getUploaderName() ?: "Desconhecido",
                            thumbnailUrl = meta.getThumbnail()?.url,
                            description = meta.getDescription().takeIf { !it.isNullOrBlank() } ?: ""
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao processar resultado: ${meta.getName()}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na busca do YouTube", e)
            emptyList()
        }
    }

    private fun extractVideoId(url: String): String? {
        // Extrai o ID do vídeo da URL
        val patterns = listOf(
            "v=([^&]+)",
            "youtu\.be/([^/?]+)",
            "youtube\.com/embed/([^/?]+)",
            "youtube\.com/v/([^/?]+)"
        )

        for (pattern in patterns) {
            val match = Regex(pattern).find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun isConfigured(): Boolean = initialized
}
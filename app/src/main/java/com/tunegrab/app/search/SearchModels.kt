package com.tunegrab.app.search

/**
 * Resultado de busca processado, pronto pra exibição na aba Buscar.
 */
data class ProcessedSearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    /** Duração em segundos (null quando o motor não devolveu). */
    val durationSec: Long? = null,
    val description: String = ""
)

/**
 * Erro de busca com mensagem pronta pra UI: só nasce quando TODOS os
 * motores de busca falharam (NewPipeExtractor + yt-dlp embutido).
 */
class SearchException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

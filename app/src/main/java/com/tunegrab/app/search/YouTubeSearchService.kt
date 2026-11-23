package com.tunegrab.app.search

import android.content.Context
import android.util.Log
import com.tunegrab.app.R
import com.tunegrab.app.YtCookies
import com.tunegrab.app.yt.YtDlpEngine
import com.tunegrab.app.yt.YtExtractor
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.UUID

/**
 * Serviço de busca do YouTube da aba Buscar (v0.23.1-beta).
 *
 * DOIS PLANOS, nesta ordem:
 *
 * PLANO 1 — NewPipeExtractor (a MESMA engine das extrações de download):
 * instantâneo, sem API key. É o caminho normal.
 *
 * PLANO 2 — yt-dlp EMBUTIDO (`ytsearchN` + `--flat-playlist`): o mesmo
 * motor dos downloads, mantido semanalmente contra as mudanças do YouTube.
 * Entra quando o plano 1 falha (bot-check, rotação de página, consentimento)
 * ou volta vazio sem motivo — o yt-dlp costuma achar os resultados mesmo
 * quando o extractor anônimo é travado.
 *
 * REGRA DE ERRO (o coração do conserto da v0.23.0): erro nos dois planos →
 * a exceção SOBE (a UI mostra "Erro ao buscar" + botão Tentar de novo).
 * Lista vazia nos dois → vazio de verdade ("nada encontrado"). A v0.23.0
 * engolia QUALQUER exceção e devolvia lista vazia — todo travamento do
 * YouTube virava "não achei nenhuma música", sem erro, sem chance de retry.
 */
object YouTubeSearchService {
    private const val TAG = "YouTubeSearchService"
    private const val DEFAULT_MAX_RESULTS = 25

    @Volatile private var initialized = false

    /** Contexto do app (plano yt-dlp: extração do python, cookies, strings). */
    @Volatile private var appContext: Context? = null

    /** Chamado uma vez pelo [com.tunegrab.app.TuneGrabApp]. */
    fun attach(ctx: Context) {
        appContext = ctx.applicationContext
    }

    /** Prepara o NewPipe (o MESMO init do YtExtractor — barato, sem rede). */
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

    fun isConfigured(): Boolean = initialized

    /**
     * Busca músicas/vídeos. Lança [SearchException] só quando os DOIS
     * motores falham; devolve lista (possivelmente vazia) quando a busca
     * de fato respondeu.
     */
    suspend fun search(query: String, maxResults: Int = DEFAULT_MAX_RESULTS): List<ProcessedSearchResult> =
        withContext(Dispatchers.IO) {
            init()
            if (query.isBlank()) return@withContext emptyList()
            val capped = maxResults.coerceIn(1, 50)

            var newPipeError: Throwable? = null
            val viaNewPipe = try {
                searchWithNewPipe(query, capped)
            } catch (t: Throwable) {
                Log.w(TAG, "busca NewPipe falhou — caindo no yt-dlp", t)
                newPipeError = t
                null
            }
            if (!viaNewPipe.isNullOrEmpty()) return@withContext viaNewPipe

            var dlpError: Throwable? = null
            val viaDlp = try {
                searchWithYtDlp(query, capped)
            } catch (t: Throwable) {
                Log.w(TAG, "busca yt-dlp falhou", t)
                dlpError = t
                null
            }
            if (!viaDlp.isNullOrEmpty()) return@withContext viaDlp

            // Nem o NewPipe nem o yt-dlp devolveram resultado. Regra:
            //  - qualquer ERRO nos motores → sobe a exceção real (a UI mostra
            //    "Erro ao buscar" + Tentar de novo). Vazio + erro de uma via
            //    é suspeito demais pra virar "nada encontrado" — o YouTube
            //    quase nunca devolve 0 resultados pra uma busca válida;
            //  - os DOIS responderam vazio de verdade → vazio mesmo.
            val npFailed = newPipeError != null
            val dlpFailed = dlpError != null
            when {
                npFailed && dlpFailed ->
                    throw SearchException(userMessage(), newPipeError ?: dlpError)
                npFailed ->
                    throw SearchException(userMessage(), newPipeError)
                dlpFailed ->
                    throw SearchException(userMessage(), dlpError)
                else -> emptyList()
            }
        }

    private fun userMessage(): String =
        appContext?.getString(R.string.search_error)
            ?: "Não foi possível buscar agora — confere a internet e tenta de novo."

    // ---------- PLANO 1: NewPipeExtractor ----------

    private fun searchWithNewPipe(query: String, max: Int): List<ProcessedSearchResult> {
        val service = ServiceList.YouTube
        val qh = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, qh)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val videoId = item.url?.let(::extractVideoId) ?: return@mapNotNull null
                val title = item.name.orEmpty()
                if (title.isBlank()) return@mapNotNull null
                ProcessedSearchResult(
                    videoId = videoId,
                    title = title,
                    channel = item.uploaderName?.takeIf { it.isNotBlank() } ?: "YouTube",
                    thumbnailUrl = item.thumbnails?.maxByOrNull { it.height }?.url
                        ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                    durationSec = item.duration.takeIf { it > 0 }
                )
            }
            .take(max)
    }

    // ---------- PLANO 2: yt-dlp embutido (ytsearch) ----------

    /**
     * `ytsearchN:query` com `--flat-playlist --dump-json`: UMA chamada leve
     * ao innertube que devolve um JSON por linha (id, título, canal,
     * duração, thumb) — NADA de extrair stream de vídeo aqui (rápido, e nem
     * encosta no bot-check de download). O motor já nasce pronto: o app
     * pré-aquece o yt-dlp no arranque (TuneGrabApp) e o mantém atualizado.
     */
    private fun searchWithYtDlp(query: String, max: Int): List<ProcessedSearchResult> {
        val ctx = appContext
            ?: throw SearchException(userMessage(), IllegalStateException("sem contexto"))
        YtDlpEngine.ensureReady(ctx) { /* status silencioso (warm-up já cobre) */ }

        val request = YoutubeDLRequest("ytsearch$max:$query").apply {
            addOption("--flat-playlist")
            addOption("--dump-json")
            addOption("--no-warnings")
            addOption("--socket-timeout", "15")
            // cookies do login do YouTube (se houver): menos anti-bot, igual
            // aos downloads — NO-OP sem sessão salva
            YtCookies.applyTo(this)
        }
        // forma posicional de propósito (mesma do YtDlpEngine.download): o
        // execute() tem duas sobrecargas e o trailing lambda gera ambiguidade
        val processId = UUID.randomUUID().toString()
        val response = YoutubeDL.execute(request, processId, { _, _, _ -> })

        val results = ArrayList<ProcessedSearchResult>(max)
        response.out.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("{")) return@forEach
            try {
                val obj = JSONObject(line)
                val id = obj.optString("id").orEmpty()
                val title = obj.optString("title").orEmpty()
                if (id.isBlank() || title.isBlank()) return@forEach
                val channel = obj.optString("uploader").takeIf { it.isNotBlank() && it != "null" }
                    ?: obj.optString("channel").takeIf { it.isNotBlank() && it != "null" }
                    ?: "YouTube"
                val thumb = obj.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" }
                    ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                results.add(
                    ProcessedSearchResult(
                        videoId = id,
                        title = title,
                        channel = channel,
                        thumbnailUrl = thumb,
                        durationSec = obj.optLong("duration", -1L).takeIf { it > 0 }
                    )
                )
            } catch (_: Throwable) {
                // linha quebrada não derruba a busca inteira
            }
        }
        return results.take(max)
    }

    /** ID de vídeo a partir de watch?v=, youtu.be/, /shorts/, /embed/ e /v/ */
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "[?&]v=([^&]+)",
            "youtu\\.be/([^/?]+)",
            "youtube\\.com/shorts/([^/?]+)",
            "youtube\\.com/embed/([^/?]+)",
            "youtube\\.com/v/([^/?]+)"
        )
        for (pattern in patterns) {
            Regex(pattern).find(url)?.let { return it.groupValues[1] }
        }
        return null
    }
}

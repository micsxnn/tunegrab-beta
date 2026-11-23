package com.tunegrab.app.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.tunegrab.app.R
import com.tunegrab.app.download.DownloadBus
import java.util.Locale

/**
 * Adapter da lista de resultados da busca (v0.23.1-beta).
 *
 * Cada item tem TRÊS caretas:
 *  - normal: botão de download;
 *  - SPINNER DE DOWNLOAD: quando ESTE vídeo está baixando. É um espelho do
 *    [DownloadBus] — a MESMA fonte de verdade da Central de Downloads —
 *    casado com o resultado pelo título normalizado (e com o estado vivo:
 *    rodando, pausado ou falhado). Fase indeterminada (preparando, na fila,
 *    processando) mostra a bolinha girando + o nome da fase; com progresso
 *    real mostra o percentual (e a velocidade, quando existe);
 *  - erro: o download falhou — o motivo aparece curtinho e o botão volta
 *    (dá pra tentar de novo). Concluído/cancelado volta ao botão normal.
 *
 * A correlação é só de LEITURA: nada aqui interfere no download — a
 * mecânica continua 100% no DownloadService.
 */
class SearchAdapter(
    private val onDownloadClick: (ProcessedSearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    private var results: List<ProcessedSearchResult> = emptyList()

    /** Downloads vivos casados por título normalizado (chave do espelho). */
    private var downloads: Map<String, DownloadBus.Item> = emptyMap()

    /** Toques recentes: spinner imediato enquanto o seletor de formato abre. */
    private var opening: Set<String> = emptySet()

    fun updateResults(newResults: List<ProcessedSearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    fun setOpening(ids: Set<String>) {
        opening = ids
        notifyDataSetChanged()
    }

    fun setDownloadItems(items: List<DownloadBus.Item>) {
        downloads = buildMap {
            items.forEach { item ->
                val key = normalizeTitle(item.title)
                if (key.isNotBlank()) put(key, item)
            }
        }
        notifyDataSetChanged()
    }

    fun clear() {
        results = emptyList()
        downloads = emptyMap()
        opening = emptySet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    /**
     * O download que corresponde a ESTE resultado — só quando ainda é
     * interessante mostrar: rodando, pausado ou falhado. Concluído e
     * cancelado devolvem null (o card volta ao botão de baixar de novo).
     */
    private fun busItemFor(result: ProcessedSearchResult): DownloadBus.Item? {
        val bus = downloads[normalizeTitle(result.title)] ?: return null
        return when (bus.state) {
            DownloadBus.State.RUNNING -> bus
            DownloadBus.State.PAUSED -> bus
            DownloadBus.State.FAILED -> bus
            else -> null
        }
    }

    /** Título em chave de comparação: minúsculas, sem espaços duplicados. */
    private fun normalizeTitle(title: String): String =
        title.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    inner class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ctx = view.context
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        private val title: TextView = view.findViewById(R.id.title)
        private val channel: TextView = view.findViewById(R.id.channel)
        private val statusLine: TextView = view.findViewById(R.id.statusLine)
        private val downloadBtn: View = view.findViewById(R.id.btnDownload)
        private val spinner: CircularProgressIndicator = view.findViewById(R.id.itemProgress)

        fun bind(item: ProcessedSearchResult) {
            title.text = item.title
            channel.text = item.durationSec
                ?.let { d -> "${item.channel} · ${formatDuration(d)}" }
                ?: item.channel

            thumbnail.load(item.thumbnailUrl) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }

            val isOpening = item.videoId in opening
            val bus = if (isOpening) null else busItemFor(item)

            when {
                isOpening -> {
                    // toque agora: o seletor de formato está abrindo na aba Início
                    showBusy(indeterminate = true, text = ctx.getString(R.string.status_fetching))
                    lockTap()
                }
                bus != null && bus.state == DownloadBus.State.RUNNING -> {
                    if (bus.indeterminate || bus.percent <= 0) {
                        showBusy(
                            indeterminate = true,
                            text = bus.phase.ifBlank { ctx.getString(R.string.dl_preparing) }
                        )
                    } else {
                        showBusy(
                            indeterminate = false,
                            text = if (bus.speed != null) {
                                ctx.getString(R.string.dl_running_pct_speed, bus.percent, bus.speed)
                            } else {
                                ctx.getString(R.string.dl_running_pct, bus.percent)
                            }
                        )
                    }
                    lockTap()
                }
                bus != null && bus.state == DownloadBus.State.PAUSED -> {
                    showBusy(
                        indeterminate = false,
                        text = ctx.getString(R.string.dl_running_pct, bus.percent) +
                            " · " + ctx.getString(R.string.dl_state_paused)
                    )
                }
                bus != null && bus.state == DownloadBus.State.FAILED -> {
                    showIdle()
                    statusLine.visibility = View.VISIBLE
                    statusLine.text = bus.detail
                        ?.let { ctx.getString(R.string.dl_state_failed_reason, it) }
                        ?: ctx.getString(R.string.dl_state_failed)
                    unlockTap(item)
                }
                else -> {
                    // nada baixando aqui (ou concluído/cancelado): botão normal
                    showIdle()
                    unlockTap(item)
                }
            }
        }

        private fun showIdle() {
            downloadBtn.visibility = View.VISIBLE
            spinner.visibility = View.GONE
            statusLine.visibility = View.GONE
        }

        private fun showBusy(indeterminate: Boolean, text: String) {
            downloadBtn.visibility = View.GONE
            spinner.visibility = View.VISIBLE
            if (indeterminate) {
                if (!spinner.isIndeterminate) spinner.isIndeterminate = true
            } else {
                // anel determinado parado: o número vive no texto da linha
                if (spinner.isIndeterminate) spinner.isIndeterminate = false
            }
            statusLine.visibility = View.VISIBLE
            statusLine.text = text
        }

        /** Baixando: toque não pode enfileirar o mesmo vídeo duas vezes. */
        private fun lockTap() {
            downloadBtn.setOnClickListener(null)
            itemView.setOnClickListener(null)
        }

        private fun unlockTap(item: ProcessedSearchResult) {
            val go = View.OnClickListener { onDownloadClick(item) }
            downloadBtn.setOnClickListener(go)
            itemView.setOnClickListener(go)
        }

        private fun formatDuration(sec: Long): String {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}

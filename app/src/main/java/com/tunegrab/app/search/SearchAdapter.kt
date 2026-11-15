package com.tunegrab.app.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tunegrab.app.R

/**
 * Adapter para a lista de resultados de busca
 */
class SearchAdapter(
    private val onDownloadClick: (ProcessedSearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    private var results: List<ProcessedSearchResult> = emptyList()

    fun updateResults(newResults: List<ProcessedSearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    fun clear() {
        results = emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = results[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = results.size

    inner class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        private val title: TextView = view.findViewById(R.id.title)
        private val channel: TextView = view.findViewById(R.id.channel)
        private val downloadBtn: View = view.findViewById(R.id.btnDownload)

        fun bind(item: ProcessedSearchResult) {
            title.text = item.title
            channel.text = item.channel

            // Carrega thumbnail com Coil
            thumbnail.load(item.thumbnailUrl) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }

            // Clique no botão de download
            downloadBtn.setOnClickListener { onDownloadClick(item) }

            // Clique no item inteiro
            itemView.setOnClickListener { onDownloadClick(item) }
        }
    }
}
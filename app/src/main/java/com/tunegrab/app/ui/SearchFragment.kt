package com.tunegrab.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tunegrab.app.MainActivity
import com.tunegrab.app.R
import com.tunegrab.app.databinding.FragmentSearchBinding
import com.tunegrab.app.download.DownloadBus
import com.tunegrab.app.search.ProcessedSearchResult
import com.tunegrab.app.search.SearchAdapter
import com.tunegrab.app.search.SearchUiState
import com.tunegrab.app.search.SearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Aba de busca: procura músicas/vídeos no YouTube e manda o resultado pro
 * MESMO fluxo de download do Início (seletor de formato → Central), igual à
 * aba YouTube já faz. Nenhuma mecânica de download nasce aqui.
 *
 * SPINNER DE DOWNLOAD (v0.23.1-beta): o resultado que está baixando mostra
 * a bolinha girando + o percentual/fase, espelhando o [DownloadBus] — a
 * MESMA fonte de verdade da Central de Downloads. Voltou pra esta aba com o
 * download rodando? O card da música mostra o progresso na hora.
 *
 * Consertos da v0.23.1: `text.toString()` sem parênteses e o
 * `activity.handleSharedUrl` (que não existe na MainActivity) quebravam a
 * COMPILAÇÃO da aba — a busca nunca rodou de fato no beta anterior.
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: SearchAdapter

    /** Toques recentes: spinner imediato enquanto o seletor de formato abre. */
    private val opening = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupButtons()
        observeState()
        observeDownloads()
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter { result -> onResultClick(result) }
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.tilSearch.setEndIconOnClickListener { performSearch() }
        binding.inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        // v0.23.1-beta: `text` é Editable — toString() precisa dos PARÊNTESES
        val query = binding.inputSearch.text?.toString()?.trim().orEmpty()
        if (query.length < 2) {
            Toast.makeText(requireContext(), R.string.search_min_length, Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        viewModel.search(query)
    }

    /**
     * Toque no resultado → o MESMO caminho do botão "Baixar" da aba YouTube:
     * [MainActivity.openDownloadFor] leva o link pra aba Início, que busca
     * as faixas e abre o seletor de formato. Escolheu → o download entra na
     * Central sozinho (e leva o usuário pra lá, como sempre).
     */
    private fun onResultClick(result: ProcessedSearchResult) {
        val activity = activity as? MainActivity ?: return
        if (result.videoId in opening) return
        opening.add(result.videoId)
        adapter.setOpening(opening.toSet())
        activity.openDownloadFor("https://www.youtube.com/watch?v=${result.videoId}")
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state -> updateUi(state) }
            }
        }
    }

    /** Espelho do DownloadBus: liga o spinner de download a cada resultado. */
    private fun observeDownloads() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadBus.items.collect { items -> adapter.setDownloadItems(items) }
            }
        }
    }

    private fun updateUi(state: SearchUiState) {
        adapter.updateResults(state.results)
        binding.progress.visibility = if (state.isSearching) View.VISIBLE else View.GONE
        binding.btnSearch.isEnabled = !state.isSearching
        // o botão SEMPRE volta a ser "Buscar": o listener preso no retry do
        // beta anterior fazia a próxima busca usar a consulta ANTIGA
        binding.btnSearch.text = getString(R.string.btn_search)
        binding.btnSearch.setOnClickListener { performSearch() }

        when {
            state.error != null -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = state.error
                binding.recyclerViewResults.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
            }
            state.isSearching -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = getString(R.string.status_searching)
                binding.tvEmpty.visibility = View.GONE
            }
            state.results.isEmpty() && state.query.isNotBlank() -> {
                binding.tvStatus.visibility = View.GONE
                binding.recyclerViewResults.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = getString(R.string.search_no_results)
            }
            state.results.isNotEmpty() -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = getString(R.string.search_results_count, state.results.size)
                binding.recyclerViewResults.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
            }
            else -> {
                binding.tvStatus.visibility = View.GONE
                binding.recyclerViewResults.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
            }
        }
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(InputMethodManager::class.java) ?: return
        view?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
    }

    override fun onPause() {
        super.onPause()
        // o toque já virou navegação (aba Início): o spinner "abrindo" não
        // pode ficar preso se o usuário cancelar o seletor de formatos
        if (opening.isNotEmpty()) {
            opening.clear()
            adapter.setOpening(emptySet())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SearchFragment = SearchFragment()
    }
}

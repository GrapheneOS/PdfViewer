package app.grapheneos.pdfviewer.outline

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.OutlineListFragmentBinding
import app.grapheneos.pdfviewer.viewModel.OutlineListViewModel
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.divider.MaterialDividerItemDecoration

class OutlineFragment : Fragment() {

    private lateinit var list: RecyclerView
    private lateinit var noOutlineText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var topBar: MaterialToolbar

    private lateinit var viewModel: OutlineListViewModel

    private fun onChildrenButtonClicked(child: OutlineNode, position: Int) {
        viewModel.setCurrent(child, position)
    }

    private fun onItemClick(clicked: OutlineNode) {
        parentFragmentManager.apply {
            setFragmentResult(
                RESULT_KEY,
                bundleOf(PAGE_KEY to clicked.pageNumber)
            )
            popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()

        view?.apply {
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, keyEvent ->
                if (
                    keyCode == KeyEvent.KEYCODE_BACK
                    // ACTION_UP and ACTION_DOWN will both be sent
                    && keyEvent.action == KeyEvent.ACTION_UP
                    && viewModel.hasPrevious()
                ) {
                    viewModel.goBack()
                    // consume back button event
                    true
                } else {
                    false
                }
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = OutlineListFragmentBinding.inflate(layoutInflater, container, false)

        list = binding.list
        val layoutManager = LinearLayoutManager(context)
        val dividerItemDecoration = MaterialDividerItemDecoration(list.context, layoutManager.orientation)
        list.addItemDecoration(dividerItemDecoration)
        list.layoutManager = layoutManager

        topBar = binding.dialogToolbar
        topBar.inflateMenu(R.menu.outlines)
        topBar.setOnMenuItemClickListener {
            parentFragmentManager.popBackStack()
            true
        }
        topBar.title = requireContext().getString(R.string.action_outline)
        topBar.subtitle = ""

        noOutlineText = binding.noOutlineText
        loadingBar = binding.loadingBar

        val activityViewModel = (requireActivity() as PdfViewer).viewModel
        activityViewModel.requestOutlineIfNotAvailable()
        activityViewModel.outline.observe(viewLifecycleOwner) { outlineState ->
            if (outlineState is PdfViewModel.OutlineStatus.Loaded) {
                val incomingList = outlineState.outline
                if (incomingList.isEmpty()) {
                    noOutlineText.visibility = View.VISIBLE
                    loadingBar.visibility = View.GONE
                    list.visibility = View.GONE
                } else {
                    noOutlineText.visibility = View.GONE
                    loadingBar.visibility = View.GONE
                    list.visibility = View.VISIBLE

                    val currentPage = arguments?.getInt(ARG_CURRENT_PAGE_KEY, -1) ?: -1
                    viewModel.setupDocument(incomingList, currentPage)

                    list.adapter = OutlineRecyclerViewAdapter(
                        incomingList,
                        ::onChildrenButtonClicked,
                        ::onItemClick
                    )
                }
            } else {
                noOutlineText.visibility = View.GONE
                loadingBar.visibility = View.VISIBLE
                list.visibility = View.GONE
            }
        }

        viewModel.currentChild.observe(viewLifecycleOwner) { current ->
            val outlineStatus = (requireActivity() as PdfViewer).viewModel.outline.value
            list.adapter = OutlineRecyclerViewAdapter(
                current?.children
                    ?: (outlineStatus as? PdfViewModel.OutlineStatus.Loaded)?.outline
                    ?: emptyList(),
                ::onChildrenButtonClicked,
                ::onItemClick
            )
            list.scrollToPosition(viewModel.lastRemovedPosition)
            topBar.subtitle = viewModel.getSubtitleString()
                ?: arguments?.getString(ARG_DOC_TITLE_KEY, "")
        }

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[OutlineListViewModel::class.java]
    }

    companion object {
        private const val TAG = "OutlineFragment"

        const val RESULT_KEY = TAG
        const val ARG_CURRENT_PAGE_KEY = "currentpage"
        const val ARG_DOC_TITLE_KEY = "title"
        const val PAGE_KEY = "navpage"

        @JvmStatic
        fun newInstance(currentPage: Int, title: String) = OutlineFragment().apply {
            arguments = bundleOf(
                ARG_CURRENT_PAGE_KEY to currentPage,
                ARG_DOC_TITLE_KEY to title
            )
        }
    }
}
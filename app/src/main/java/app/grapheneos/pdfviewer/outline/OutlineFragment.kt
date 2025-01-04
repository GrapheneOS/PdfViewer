package app.grapheneos.pdfviewer.outline

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.viewModels
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.OutlineFragmentBinding
import app.grapheneos.pdfviewer.viewModel.OutlineViewModel
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.appbar.MaterialToolbar

class OutlineFragment : Fragment() {

    private lateinit var noOutlineText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var topBar: MaterialToolbar
    private lateinit var listContainer: FragmentContainerView

    private val viewModel by viewModels<OutlineViewModel>()

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
                    viewModel.submitAction(OutlineViewModel.Action.Back)
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
        val binding = OutlineFragmentBinding.inflate(layoutInflater, container, false)

        listContainer = binding.outlineListContainer

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

        val docTitle = arguments?.getString(ARG_DOC_TITLE_KEY, "") ?: ""

        val activityViewModel = (requireActivity() as PdfViewer).viewModel
        activityViewModel.requestOutlineIfNotAvailable()
        activityViewModel.outline.observe(viewLifecycleOwner) { outlineState ->
            if (outlineState is PdfViewModel.OutlineStatus.Loaded) {
                val incomingList = outlineState.outline
                if (incomingList.isEmpty()) {
                    noOutlineText.visibility = View.VISIBLE
                    loadingBar.visibility = View.GONE
                    listContainer.visibility = View.GONE
                } else {
                    noOutlineText.visibility = View.GONE
                    loadingBar.visibility = View.GONE
                    listContainer.visibility = View.VISIBLE

                    val currentPage = arguments?.getInt(ARG_CURRENT_PAGE_KEY, -1) ?: -1
                    viewModel.setupDocument(incomingList, currentPage, docTitle)
                }
            } else {
                noOutlineText.visibility = View.GONE
                loadingBar.visibility = View.VISIBLE
                listContainer.visibility = View.GONE
            }
        }

        viewModel.currentAction.observe(viewLifecycleOwner) { action ->
            when (action) {
                OutlineViewModel.Action.Back -> {
                    childFragmentManager.popBackStack()
                }
                OutlineViewModel.Action.Close -> {
                    parentFragmentManager.popBackStack()
                }
                is OutlineViewModel.Action.OpenPage -> {
                    parentFragmentManager.apply {
                        setFragmentResult(RESULT_KEY, bundleOf(PAGE_KEY to action.page))
                        popBackStack()
                    }
                }
                is OutlineViewModel.Action.ViewChildren -> {
                    val parent = action.parent
                    val fragment = OutlineListFragment.makeInstance(parent.id)
                    if (childFragmentManager.findFragmentByTag(parent.id.toString()) == null) {
                        listContainer.visibility = View.VISIBLE
                        childFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                if (action.firstOpen) android.R.anim.fade_in else R.anim.slide_in_right,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.slide_out_right,
                            )
                            .replace(R.id.outline_list_container, fragment, parent.id.toString())
                            .addToBackStack(null)
                            .commit()
                    }
                }
                OutlineViewModel.Action.Idle -> {
                    return@observe
                }
            }
            viewModel.submitAction(OutlineViewModel.Action.Idle)
        }

        viewModel.currentChild.observe(viewLifecycleOwner) { _ ->
            topBar.subtitle = viewModel.getSubtitleString()
        }

        return binding.root
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

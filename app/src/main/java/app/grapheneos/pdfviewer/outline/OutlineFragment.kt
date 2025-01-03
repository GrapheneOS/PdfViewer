package app.grapheneos.pdfviewer.outline

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.OutlineListFragmentBinding
import app.grapheneos.pdfviewer.viewModel.OutlineListViewModel
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration

class OutlineFragment : DialogFragment() {

    private lateinit var list: RecyclerView
    private lateinit var noOutlineText: TextView
    private lateinit var loadingBar: ProgressBar

    private lateinit var viewModel: OutlineListViewModel

    private val childrenButtonClickListener = { child: OutlineNode, position: Int ->
        viewModel.setCurrent(child, position)
    }
    private val itemClickListener = { clicked: OutlineNode ->
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(PAGE_KEY to clicked.pageNumber)
        )
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val binding = OutlineListFragmentBinding.inflate(layoutInflater)
        dialogBuilder.setView(binding.root)
        dialogBuilder.setTitle(R.string.action_outline)

        list = binding.list
        val layoutManager = LinearLayoutManager(context)
        val dividerItemDecoration = MaterialDividerItemDecoration(list.context, layoutManager.orientation)
        list.addItemDecoration(dividerItemDecoration)
        list.layoutManager = layoutManager

        noOutlineText = binding.noOutlineText
        loadingBar = binding.loadingBar

        val activityViewModel = (requireActivity() as PdfViewer).viewModel
        activityViewModel.requestOutlineIfNotAvailable()
        activityViewModel.outline.observe(this) { outlineState ->
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
                        childrenButtonClickListener,
                        itemClickListener
                    )
                }
            } else {
                noOutlineText.visibility = View.GONE
                loadingBar.visibility = View.VISIBLE
                list.visibility = View.GONE
            }
        }

        dialogBuilder.setOnKeyListener { _, keyCode, keyEvent ->
            if (
                keyCode == KeyEvent.KEYCODE_BACK
                // ACTION_UP and ACTION_DOWN will both be sent; check to prevent double calling
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


        val dialog = dialogBuilder.create()
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        viewModel.currentChild.observe(this) { current ->
            val outlineStatus = (requireActivity() as PdfViewer).viewModel.outline.value
            list.adapter = OutlineRecyclerViewAdapter(
                    current?.children
                        ?: (outlineStatus as? PdfViewModel.OutlineStatus.Loaded)?.outline
                        ?: emptyList(),
                    childrenButtonClickListener,
                    itemClickListener
            )
            list.scrollToPosition(viewModel.lastRemovedPosition)
        }

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[OutlineListViewModel::class.java]
    }

    companion object {
        const val TAG = "OutlineFragment"

        const val RESULT_KEY = TAG
        const val ARG_CURRENT_PAGE_KEY = "currentpage"
        const val PAGE_KEY = "navpage"

        @JvmStatic
        fun newInstance(currentPage: Int) = OutlineFragment().apply {
            arguments = bundleOf(ARG_CURRENT_PAGE_KEY to currentPage)
        }
    }
}
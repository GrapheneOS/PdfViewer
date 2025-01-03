package app.grapheneos.pdfviewer.outline

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.OutlineListFragmentBinding
import app.grapheneos.pdfviewer.viewModel.OutlineListViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration

class OutlineFragment : DialogFragment() {

    private lateinit var list: RecyclerView

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

        (requireActivity() as PdfViewer).viewModel.outline.observe(this) { incomingList ->
            val outlineList = incomingList ?: emptyList()
            val currentPage = arguments?.getInt(ARG_CURRENT_PAGE_KEY, -1) ?: -1
            viewModel.onNewDocument(outlineList, currentPage)

            list.adapter = OutlineRecyclerViewAdapter(
                outlineList,
                childrenButtonClickListener,
                itemClickListener
            )
        }

        dialogBuilder.setOnKeyListener { a, keyCode, c ->
            if (
                keyCode == KeyEvent.KEYCODE_BACK
                && c.action == KeyEvent.ACTION_UP
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

        viewModel.currentChildAndPosition.observe(this) { current ->
            list.adapter = OutlineRecyclerViewAdapter(
                    current?.children
                        ?: (requireActivity() as PdfViewer).viewModel.outline.value
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
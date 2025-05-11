package app.grapheneos.pdfviewer.outline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.pdfviewer.databinding.OutlineListFragmentBinding
import app.grapheneos.pdfviewer.viewModel.OutlineViewModel
import com.google.android.material.divider.MaterialDividerItemDecoration

class OutlineListViewModel : ViewModel() {
    val outlineContents = MutableLiveData<List<OutlineNode>?>(null)

    fun needsToLoadContents() = outlineContents.value == null

    fun updateOutlineContents(contents: List<OutlineNode>?) {
        if (contents != null && needsToLoadContents()) {
            outlineContents.value = contents
        }
    }
}

class OutlineListFragment : Fragment() {
    private lateinit var list: RecyclerView

    private val viewModel by viewModels<OutlineListViewModel>()

    private val outlineViewModel by viewModels<OutlineViewModel> (
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = OutlineListFragmentBinding.inflate(layoutInflater, container, false)
        list = binding.list
        list.visibility = View.VISIBLE

        val layoutManager = LinearLayoutManager(context)
        val dividerItemDecoration = MaterialDividerItemDecoration(list.context, layoutManager.orientation)
        list.addItemDecoration(dividerItemDecoration)
        list.layoutManager = layoutManager

        val parentNodeId = arguments?.getInt(ARG_OUTLINE_ID, -2) ?: -2

        outlineViewModel.currentChild.observe(viewLifecycleOwner) { child ->
            if (viewModel.needsToLoadContents()) {
                val contents = if (child?.id == parentNodeId) {
                    child.children
                } else {
                    outlineViewModel.findNodeOrNull(parentNodeId)?.children
                }
                viewModel.updateOutlineContents(contents)
            }
        }

        viewModel.outlineContents.observe(viewLifecycleOwner) { current ->
            list.adapter = OutlineRecyclerViewAdapter(
                values = current ?: emptyList(),
                onChildrenButtonClick = {
                    outlineViewModel.submitAction(OutlineViewModel.Action.ViewChildren(it))
                },
                onItemClick = {
                    outlineViewModel.submitAction(OutlineViewModel.Action.OpenPage(it.pageNumber))
                }
            )
        }

        return binding.root
    }

    companion object {
        private const val TAG = "OutlineListFragment"
        private const val ARG_OUTLINE_ID = "outlinenodeid"

        fun makeInstance(outlineId: Int) = OutlineListFragment().apply {
            arguments = bundleOf(ARG_OUTLINE_ID to outlineId)
        }
    }
}

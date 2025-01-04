package app.grapheneos.pdfviewer.outline

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

import app.grapheneos.pdfviewer.databinding.OutlineListItemBinding

class OutlineRecyclerViewAdapter(
    private val values: List<OutlineNode>,
    private val onChildrenButtonClick: (OutlineNode) -> Unit,
    private val onItemClick: (OutlineNode) -> Unit
) : RecyclerView.Adapter<OutlineRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            OutlineListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.titleView.text = item.title
        holder.pageNumberView.text = item.pageNumber.toString()
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener { onItemClick(item) }
        if (item.children.isEmpty()) {
            holder.childrenButton.visibility = View.GONE
            holder.childrenButton.setOnClickListener(null)
        } else {
            holder.childrenButton.visibility = View.VISIBLE
            holder.childrenButton.setOnClickListener { onChildrenButtonClick(item) }
        }
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(
        binding: OutlineListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isClickable = true
        }

        val titleView: TextView = binding.itemName
        val pageNumberView: TextView = binding.pageNumber
        val childrenButton: ImageButton = binding.childrenButton


        override fun toString(): String {
            return super.toString() + " '" + pageNumberView.text + "'"
        }
    }
}

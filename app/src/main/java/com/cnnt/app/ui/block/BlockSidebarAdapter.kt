package com.cnnt.app.ui.block

import android.content.ClipData
import android.content.ClipDescription
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cnnt.app.R

class BlockSidebarAdapter(
    items: List<SidebarBlockType>,
    private val onClick: (SidebarBlockType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Category(val title: String) : Row()
        data class Item(val block: SidebarBlockType) : Row()
    }

    private val rows: List<Row> = buildList {
        var currentCategory: String? = null
        items.forEach { item ->
            if (currentCategory != item.category) {
                currentCategory = item.category
                add(Row.Category(item.category))
            }
            add(Row.Item(item))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is Row.Category -> TYPE_CATEGORY
            is Row.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CATEGORY) {
            CategoryHolder(inflater.inflate(R.layout.item_sidebar_block_category, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_sidebar_block, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Category -> (holder as CategoryHolder).bind(row.title)
            is Row.Item -> (holder as ItemHolder).bind(row.block, onClick)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class CategoryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val categoryText = view.findViewById<TextView>(R.id.blockCategoryTitle)

        fun bind(category: String) {
            categoryText.text = category
        }
    }

    private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.blockTitle)
        private val description = view.findViewById<TextView>(R.id.blockDescription)
        private val icon = view.findViewById<ImageView>(R.id.blockIcon)

        fun bind(item: SidebarBlockType, onClick: (SidebarBlockType) -> Unit) {
            title.text = item.label
            description.text = item.description
            icon.setImageResource(item.iconRes)
            itemView.tooltipText = item.description
            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val clipData = ClipData(
                    "content_block",
                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                    ClipData.Item(item.type.key)
                )
                val shadow = BlockDragShadowBuilder(view, item.label)
                view.startDragAndDrop(clipData, shadow, item, 0)
                true
            }
        }
    }

    companion object {
        private const val TYPE_CATEGORY = 1
        private const val TYPE_ITEM = 2
    }
}
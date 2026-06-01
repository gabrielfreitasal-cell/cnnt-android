package com.cnnt.app.ui.sidebar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cnnt.app.R
import com.cnnt.app.data.model.Board

class PageListAdapter(
    private val onPageClick: (Int) -> Unit
) : RecyclerView.Adapter<PageListAdapter.PageViewHolder>() {

    private val boards = mutableListOf<Board>()
    private var selectedIndex = 0

    fun submit(boardList: List<Board>, activeIndex: Int) {
        boards.clear()
        boards.addAll(boardList)
        selectedIndex = activeIndex.coerceIn(0, (boards.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val board = boards[position]
        holder.pageNumber.text = "${position + 1}"
        holder.pageName.text = board.name
        val selected = position == selectedIndex
        holder.itemView.alpha = if (selected) 1f else 0.75f
        holder.pageName.setTextColor(if (selected) Color.WHITE else 0xFFBBBBBB.toInt())
        holder.itemView.setOnClickListener { onPageClick(position) }
    }

    override fun getItemCount(): Int = boards.size

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pageNumber: TextView = itemView.findViewById(R.id.pageNumber)
        val pageName: TextView = itemView.findViewById(R.id.pageName)
    }
}

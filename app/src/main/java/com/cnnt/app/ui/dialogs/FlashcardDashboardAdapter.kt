package com.cnnt.app.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cnnt.app.R
import com.cnnt.app.data.model.Flashcard
import com.cnnt.app.flashcard.FlashcardManager

class FlashcardDashboardAdapter(
    private val flashcardManager: FlashcardManager,
    private val onEdit: (Flashcard) -> Unit,
    private val onDelete: (Flashcard) -> Unit
) : RecyclerView.Adapter<FlashcardDashboardAdapter.FlashcardViewHolder>() {

    private val items = mutableListOf<Flashcard>()

    fun submit(cards: List<Flashcard>) {
        items.clear()
        items.addAll(cards)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): Flashcard = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashcardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flashcard_dashboard, parent, false)
        return FlashcardViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlashcardViewHolder, position: Int) {
        val item = items[position]
        val due = item.nextReview <= System.currentTimeMillis()
        holder.cardTitle.text = flashcardManager.renderFront(item).take(72)
        holder.cardMeta.text = buildString {
            append(if (flashcardManager.isCloze(item)) "Cloze" else "Basic")
            append(" • ")
            append(if (due) "vencido" else "agendado")
            if (item.linkedRegionId != null) {
                append(" • canvas")
            }
        }
        holder.itemView.setOnClickListener { onEdit(item) }
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    class FlashcardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardTitle: TextView = view.findViewById(R.id.cardTitle)
        val cardMeta: TextView = view.findViewById(R.id.cardMeta)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }
}
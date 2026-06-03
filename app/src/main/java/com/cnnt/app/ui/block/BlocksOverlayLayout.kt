package com.cnnt.app.ui.block

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.cnnt.app.data.model.ContentBlock
import com.cnnt.app.data.model.LinkEdge

class BlocksOverlayLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener : BlockView.Listener

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var selectedIds = emptySet<String>()
    private var linkingSourceId: String? = null
    private var links: List<LinkEdge> = emptyList()
    private var listener: Listener? = null
    private val blockViews = linkedMapOf<String, BlockView>()

    init {
        clipChildren = false
        clipToPadding = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun updateTransform(scale: Float, tx: Float, ty: Float) {
        scaleFactor = scale
        translateX = tx
        translateY = ty
        relayoutBlocks()
    }

    fun submitBlocks(
        blocks: List<ContentBlock>,
        links: List<LinkEdge>,
        selectedIds: Set<String>,
        linkingSourceId: String?
    ) {
        this.links = links
        this.selectedIds = selectedIds
        this.linkingSourceId = linkingSourceId
        val existingIds = blockViews.keys.toSet()
        val newIds = blocks.map { it.id }.toSet()

        (existingIds - newIds).forEach { id ->
            val view = blockViews.remove(id) ?: return@forEach
            view.animateRemoval { removeView(view) }
        }

        blocks.sortedBy { it.zIndex }.forEach { block ->
            val view = blockViews.getOrPut(block.id) {
                BlockView(context).also { blockView ->
                    blockView.layoutParams = LayoutParams(1, 1)
                    blockView.alpha = 0f
                    addView(blockView)
                    blockView.animateCreation()
                }
            }
            bindBlockView(view, block)
        }
        relayoutBlocks()
    }

    fun screenBounds(): List<LinksOverlayView.ScreenBlockBounds> {
        return blockViews.mapNotNull { (_, view) ->
            val block = view.tag as? ContentBlock ?: return@mapNotNull null
            LinksOverlayView.ScreenBlockBounds(
                block = block,
                rect = RectF(view.x, view.y, view.x + view.width, view.y + view.height)
            )
        }
    }

    fun findBlockView(blockId: String): View? = blockViews[blockId]

    fun centerPointForBlock(blockId: String): PointF? {
        val view = blockViews[blockId] ?: return null
        return PointF(view.x + view.width / 2f, view.y + view.height / 2f)
    }

    private fun bindBlockView(view: BlockView, block: ContentBlock) {
        view.tag = block
        val incomingCount = links.count { it.targetBlockId == block.id }
        val outgoingCount = links.count { it.sourceBlockId == block.id }
        view.bind(
            block = block,
            isSelected = selectedIds.contains(block.id),
            isLinkingSource = linkingSourceId == block.id,
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            listener = requireNotNull(listener)
        )
    }

    private fun relayoutBlocks() {
        blockViews.values.forEach { view ->
            val block = view.tag as? ContentBlock ?: return@forEach
            val widthPx = (block.width * scaleFactor).toInt().coerceAtLeast(1)
            val heightPx = (block.height * scaleFactor).toInt().coerceAtLeast(1)
            view.layoutParams = (view.layoutParams as LayoutParams).apply {
                width = widthPx
                height = heightPx
            }
            view.x = translateX + block.posX * scaleFactor
            view.y = translateY + block.posY * scaleFactor
            view.scaleX = scaleFactor.coerceIn(0.35f, 2.2f)
            view.scaleY = scaleFactor.coerceIn(0.35f, 2.2f)
            view.requestLayout()
        }
    }
}
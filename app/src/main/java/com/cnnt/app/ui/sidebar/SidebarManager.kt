package com.cnnt.app.ui.sidebar

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cnnt.app.data.model.BlockType
import com.cnnt.app.data.model.Board
import com.cnnt.app.databinding.ActivityMainBinding
import com.cnnt.app.ui.block.BlockSidebarAdapter
import com.cnnt.app.ui.block.SidebarBlockCatalog
import com.cnnt.app.ui.block.SidebarBlockType

class SidebarManager(
    private val binding: ActivityMainBinding,
    private val activity: AppCompatActivity
) {
    var onPageSelected: ((Int) -> Unit)? = null
    var onNewPageClicked: (() -> Unit)? = null
    var onInsertBlockClicked: ((BlockType) -> Unit)? = null
    var onInsertHandwritingBlock: (() -> Unit)? = null

    private val pageAdapter = PageListAdapter { index ->
        onPageSelected?.invoke(index)
    }
    private val blockAdapter = BlockSidebarAdapter(SidebarBlockCatalog.items) { block ->
        onInsertBlockClicked?.invoke(block.type)
        closeSidebar()
    }

    init {
        setupSidebarToggle()
        setupPageList()
        setupBlockInserts()
        setupNavPlaceholders()
    }

    private fun setupSidebarToggle() {
        binding.sidebarToggle.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private fun setupPageList() {
        binding.pageList.layoutManager = LinearLayoutManager(activity)
        binding.pageList.adapter = pageAdapter
        binding.pageList.setHasFixedSize(false)
        binding.btnAddPage.setOnClickListener {
            onNewPageClicked?.invoke()
        }
        binding.contentPanelList.layoutManager = LinearLayoutManager(activity)
        binding.contentPanelList.adapter = blockAdapter
    }

    fun updatePages(boards: List<Board>, activeIndex: Int) {
        pageAdapter.submit(boards, activeIndex)
    }

    private fun setupNavPlaceholders() {
        listOf(binding.navHome, binding.navRecent, binding.navAllNotes, binding.navFavorites).forEach { view ->
            view.setOnClickListener {
                android.widget.Toast.makeText(
                    activity,
                    "Em breve: ${(view as android.widget.TextView).text}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupBlockInserts() {
        binding.contentPanelEmptyState.setOnClickListener {
            onInsertBlockClicked?.invoke(BlockType.Text)
            closeSidebar()
        }
        binding.contentPanelEmptyState.setOnLongClickListener { view ->
            triggerHapticFeedback(view)
            onInsertHandwritingBlock?.invoke()
            closeSidebar()
            true
        }
    }

    private fun triggerHapticFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // Ignore vibration failures
        }
    }

    private fun closeSidebar() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }
}

package com.cnnt.app.ui.sidebar

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cnnt.app.data.model.Board
import com.cnnt.app.data.model.SpatialObjectType
import com.cnnt.app.databinding.ActivityMainBinding

class SidebarManager(
    private val binding: ActivityMainBinding,
    private val activity: AppCompatActivity
) {
    var onPageSelected: ((Int) -> Unit)? = null
    var onNewPageClicked: (() -> Unit)? = null
    var onInsertBlockClicked: ((SpatialObjectType) -> Unit)? = null
    var onInsertHandwritingBlock: (() -> Unit)? = null

    private val pageAdapter = PageListAdapter { index ->
        onPageSelected?.invoke(index)
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
        binding.btnInsertText.setOnClickListener {
            onInsertBlockClicked?.invoke(SpatialObjectType.TEXT)
            closeSidebar()
        }
        binding.btnInsertChecklist.setOnClickListener {
            onInsertBlockClicked?.invoke(SpatialObjectType.CHECKLIST)
            closeSidebar()
        }
        binding.btnInsertImage.setOnClickListener {
            onInsertBlockClicked?.invoke(SpatialObjectType.IMAGE)
            closeSidebar()
        }
        binding.btnInsertPdf.setOnClickListener {
            onInsertBlockClicked?.invoke(SpatialObjectType.PDF)
            closeSidebar()
        }
        binding.btnInsertLink.setOnClickListener {
            onInsertBlockClicked?.invoke(SpatialObjectType.LINK)
            closeSidebar()
        }

        // Handwriting block with haptic feedback on long press
        binding.btnInsertHandwriting.setOnLongClickListener { view ->
            triggerHapticFeedback(view)
            onInsertHandwritingBlock?.invoke()
            closeSidebar()
            true
        }
        binding.btnInsertHandwriting.setOnClickListener {
            onInsertHandwritingBlock?.invoke()
            closeSidebar()
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

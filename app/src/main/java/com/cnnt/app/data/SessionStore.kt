package com.cnnt.app.data

import android.content.Context

/** Persists last open notebook/page across app restarts. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastNotebookId(): String? = prefs.getString(KEY_NOTEBOOK_ID, null)

    fun getLastBoardId(): String? = prefs.getString(KEY_BOARD_ID, null)

    fun getLastBoardIndex(): Int = prefs.getInt(KEY_BOARD_INDEX, 0)

    fun saveSession(notebookId: String, boardId: String, boardIndex: Int) {
        prefs.edit()
            .putString(KEY_NOTEBOOK_ID, notebookId)
            .putString(KEY_BOARD_ID, boardId)
            .putInt(KEY_BOARD_INDEX, boardIndex)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "cnnt_session"
        private const val KEY_NOTEBOOK_ID = "notebook_id"
        private const val KEY_BOARD_ID = "board_id"
        private const val KEY_BOARD_INDEX = "board_index"
    }
}

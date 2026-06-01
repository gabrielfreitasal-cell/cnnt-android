package com.cnnt.app

import android.app.Application
import android.util.Log
import com.cnnt.app.data.NotebookSnapshot
import com.cnnt.app.data.dao.CnntDatabase
import com.cnnt.app.data.repository.CnntRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class CnntApplication : Application() {

    val database: CnntDatabase by lazy { CnntDatabase.getDatabase(this) }
    val repository: CnntRepository by lazy { CnntRepository(database) }
    val applicationScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Global exception handler to prevent silent crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CNNT", "Uncaught exception in ${thread.name}", throwable)
            runBlocking {
                withTimeoutOrNull(2000L) {
                    NotebookSnapshot.emergencySave()
                }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: CnntApplication
            private set
    }
}

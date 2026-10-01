package com.helmet.guard

import android.app.Application
import android.util.Log
import com.helmet.guard.data.database.AppDatabase
import com.helmet.guard.data.preferences.UserPreferencesRepository
import com.helmet.guard.data.repository.HelmetRepositoryImpl
import com.helmet.guard.domain.repository.IHelmetRepository
import java.io.File

class HelmetGuardApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: UserPreferencesRepository
        private set

    lateinit var repository: IHelmetRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 全局崩溃异常捕获，保存错误现场
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashLog = File(filesDir, "last_crash.txt")
                crashLog.writeText("Thread: ${thread.name}\nException: ${throwable.stackTraceToString()}")
                Log.e("HelmetGuardCrash", "全局未捕获异常: ${throwable.message}", throwable)
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            database = AppDatabase.getInstance(this)
            preferences = UserPreferencesRepository(this)
            repository = HelmetRepositoryImpl(this, database, preferences)
        } catch (e: Throwable) {
            Log.e("HelmetGuardApp", "基础组件初始化异常: ${e.message}", e)
        }
    }
}

package com.kukifyjeff.safepatrol

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 首启导入放到这里，等下我们实现 CsvBootstrapper + Room
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@App)
            if (db.routeDao().countRoutes() == 0) {
                CsvBootstrapper.ensureConfigDirWithDefaults(this@App)
                CsvBootstrapper.importAllFromConfig(this@App, db)
            }
        }
    }
}
package com.kukifyjeff.safepatrol

import androidx.room.withTransaction

import android.content.Context
import com.kukifyjeff.safepatrol.utils.CsvReaders
import java.io.File

object CsvBootstrapper {
    private const val CONFIG_DIR = "config"

    fun ensureConfigDirWithDefaults(ctx: Context) {
        val outDir = File(ctx.getExternalFilesDir(null), CONFIG_DIR)
        if (!outDir.exists()) outDir.mkdirs()
        val assetList = ctx.assets.list(CONFIG_DIR) ?: emptyArray()
        assetList.forEach { name ->
            val out = File(outDir, name)
            if (!out.exists()) {
                ctx.assets.open("$CONFIG_DIR/$name").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    suspend fun importAllFromConfig(ctx: Context, db: AppDatabase) {
        val dir = File(ctx.getExternalFilesDir(null), CONFIG_DIR)
        val routes = CsvReaders.readRoutes(File(dir, "routes.csv"))
        val points = CsvReaders.readPoints(File(dir, "points.csv"))
        val shifts = CsvReaders.readShifts(File(dir, "shifts.csv"))
        val nfcMap = CsvReaders.readNfcMap(File(dir, "nfc_map.csv"))
        val checkItems = CsvReaders.readCheckItems(File(dir, "check_items.csv"))
        db.withTransaction {
            db.routeDao().upsertAll(routes)
            db.pointDao().upsertAll(points)
            db.shiftDao().upsertAll(shifts)
            db.nfcMapDao().upsertAll(nfcMap)
            db.checkItemDao().upsertAll(checkItems)
        }
    }
}
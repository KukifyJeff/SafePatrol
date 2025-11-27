package com.kukifyjeff.safepatrol

import androidx.room.withTransaction

import android.content.Context
import android.util.Log
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentStatusEntity
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
        val checkItems = CsvReaders.readCheckItems(File(dir, "check_items.csv"))
        val equipments = CsvReaders.readEquipments(File(dir, "equipments.csv"))
        val equipmentStatuses = equipments.map { eq ->
            EquipmentStatusEntity(
                equipmentId = eq.equipmentId,
                status = "RUNNING",
                updatedAt = 1764086400000L
            )
        }
        val employees = CsvReaders.readEmployees(File(dir, "employees.csv"))


        db.withTransaction {
            db.routeDao().upsertAll(routes)
            db.pointDao().upsertAll(points)
            db.shiftDao().upsertAll(shifts)
            db.equipmentDao().upsertAll(equipments)
            db.checkItemDao().upsertAll(checkItems)
            db.employeeDao().upsertAll(employees)
            equipmentStatuses.forEach { db.equipmentStatusDao().upsertStatus(it) }
        }
    }
}
package com.kukifyjeff.safepatrol.utils

import com.kukifyjeff.safepatrol.data.db.entities.*
import com.opencsv.CSVReader
import java.io.File
import java.io.FileReader

object CsvReaders {

    private fun readAll(file: File): List<Array<String>> {
        if (!file.exists()) return emptyList()
        CSVReader(FileReader(file)).use { r -> return r.readAll() }
    }

    fun readRoutes(file: File): List<RouteEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 2) null else RouteEntity(
                routeId = row[0].trim(),
                routeName = row[1].trim(),
                description = row.getOrNull(2)?.trim()
            )
        }

    fun readPoints(file: File): List<PointEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 5) null else PointEntity(
                equipmentId = row[0].trim(),
                name        = row[1].trim(),
                location    = row[2].trim(),
                freqHours   = row[3].trim().toInt(),
                routeId     = row[4].trim()
            )
        }

    fun readShifts(file: File): List<ShiftEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 4) null else ShiftEntity(
                shiftId   = row[0].trim(),
                name      = row[1].trim(),
                startTime = row[2].trim(),
                endTime   = row[3].trim()
            )
        }

    fun readNfcMap(file: File): List<NfcMapEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 2) null else NfcMapEntity(
                tagUid      = row[0].trim().uppercase(),
                equipmentId = row[1].trim()
            )
        }

    fun readCheckItems(file: File): List<CheckItemEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 7) null else CheckItemEntity(
                itemId      = row[1].trim(),
                equipmentId = row[0].trim(),
                itemName    = row[2].trim(),
                type        = row[3].trim().uppercase(),
                unit        = row[4].ifBlank { null },
                required    = row[5].trim().equals("YES", true),
                minValue    = row.getOrNull(6)?.ifBlank { null }?.toDoubleOrNull(),
                maxValue    = row.getOrNull(7)?.ifBlank { null }?.toDoubleOrNull()
            )
        }
}
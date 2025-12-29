package com.kukifyjeff.safepatrol.utils

import com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity
import com.kukifyjeff.safepatrol.data.db.entities.EmployeeEntity
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentStatusEntity
import com.kukifyjeff.safepatrol.data.db.entities.PointEntity
import com.kukifyjeff.safepatrol.data.db.entities.RouteEntity
import com.kukifyjeff.safepatrol.data.db.entities.ShiftEntity
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
                pointId = row[0].trim(),
                name = row[1].trim(),
                location = row[2].trim(),
                routeId = row[3].trim(),
                tagUid = row[4].trim().uppercase()
            )
        }

    fun readShifts(file: File): List<ShiftEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 4) null else ShiftEntity(
                shiftId = row[0].trim(),
                name = row[1].trim(),
                startTime = row[2].trim(),
                endTime = row[3].trim()
            )
        }


    fun readCheckItems(file: File): List<CheckItemEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 9) null else CheckItemEntity(
                equipmentId = row[0].trim().uppercase(),
                itemId = row[1].trim(),
                itemName = row[2].trim(),
                type = row[3].trim().uppercase(),
                unit = row[4].ifBlank { null },
                required = row[5].trim().equals("yes", true),
                minValue = row.getOrNull(6)?.ifBlank { null }?.toDoubleOrNull(),
                maxValue = row.getOrNull(7)?.ifBlank { null }?.toDoubleOrNull(),
                freqHours = row.getOrNull(8)?.ifBlank { null }?.toIntOrNull() ?: 8,
                requiredInStandby = row.getOrNull(9)?.trim()?.equals("yes", true) ?: false
            )
        }

    fun readEmployees(file: File): List<EmployeeEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 2) null else EmployeeEntity(
                employeeId = row[0].trim(),
                employeeName = row[1].trim(),
            )
        }

    fun readEquipments(file: File): List<EquipmentEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 4) null else EquipmentEntity(
                equipmentId = row[0].trim(),
                equipmentName = row[1].trim(),
                pointId = row[2].trim(),
                statusRequired = row[3].trim().equals("YES", true)
            )
        }

    fun readEquipmentStatuses(file: File): List<EquipmentStatusEntity> =
        readAll(file).drop(1).mapNotNull { row ->
            if (row.size < 2) null else EquipmentStatusEntity(
                equipmentId = row[0].trim(),
                status = row[1].trim(),
                updatedAt = row.getOrNull(2)?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
}
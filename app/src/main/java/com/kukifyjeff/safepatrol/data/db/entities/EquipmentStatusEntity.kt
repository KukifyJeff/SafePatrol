package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipment_status",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["equipmentId"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("equipmentId")]
)
data class EquipmentStatusEntity(
    @PrimaryKey val equipmentId: String,
    val status: String, // 运行 RUNNING / 检修 MAINTENANCE / 备用 STANDBY
    val updatedAt: Long = System.currentTimeMillis() // 更新时间
)
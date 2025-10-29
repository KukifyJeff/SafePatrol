package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipments",
    foreignKeys = [
        ForeignKey(
            entity = PointEntity::class,
            parentColumns = ["pointId"],
            childColumns = ["pointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pointId")]
)
data class EquipmentEntity(
    @PrimaryKey val equipmentId: String,
    val equipmentName: String,
    val pointId: String,
    val statusRequired: Boolean = false  // 是否需要启停状态设定（由 CSV 提前声明）
)
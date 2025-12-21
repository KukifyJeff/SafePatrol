package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_items",
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
data class CheckItemEntity(
    @PrimaryKey val itemId: String,
    val equipmentId: String,
    val itemName: String,
    val type: String,     // BOOLEAN / NUMBER / TEXT
    val unit: String?,
    val required: Boolean,
    val requiredInStandby: Boolean,
    val minValue: Double?,
    val maxValue: Double?,
    val freqHours: Int,   // 2， 4 或 8
    val adjustValue: Double?,
    val fastAdjust1: Double?,
    val fastAdjust2: Double?
)
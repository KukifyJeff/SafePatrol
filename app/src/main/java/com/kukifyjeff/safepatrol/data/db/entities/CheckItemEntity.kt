package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "check_items", indices = [Index("equipmentId")])
data class CheckItemEntity(
    @PrimaryKey val itemId: String,
    val equipmentId: String,
    val itemName: String,
    val type: String,     // BOOLEAN / NUMBER / TEXT
    val unit: String?,
    val required: Boolean,
    val minValue: Double?,
    val maxValue: Double?
)
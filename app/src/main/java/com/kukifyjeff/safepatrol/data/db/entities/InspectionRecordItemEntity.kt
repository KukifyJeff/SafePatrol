package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_record_items",
    foreignKeys = [
        ForeignKey(
            entity = InspectionRecordEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordId"), Index("itemId")]
)
data class InspectionRecordItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val equipmentId: String,
    val itemId: String,
    val slotIndex: Int,
    var value: String,   // "TRUE"/"FALSE" 或数字/文本
    var abnormal: Boolean
)
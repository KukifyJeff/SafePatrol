package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_records",
    foreignKeys = [
        ForeignKey(
            entity = InspectionSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("equipmentId")]
)
data class InspectionRecordEntity(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val sessionId: Long,
    val equipmentId: String,
    val slotIndex: Int,      // 1=第一次，2=第二次，8h=1
    val timestamp: Long
)
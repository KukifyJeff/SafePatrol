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
    indices = [Index("sessionId"), Index("pointId")]
)
data class InspectionRecordEntity(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val sessionId: Long,
    val pointId: String,   // 每条记录对应一个点位
    val slotIndex: Int,
    val timestamp: Long
)
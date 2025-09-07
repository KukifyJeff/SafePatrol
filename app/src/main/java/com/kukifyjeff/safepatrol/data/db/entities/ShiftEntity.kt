package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val shiftId: String,
    val name: String,
    val startTime: String,  // "08:00"
    val endTime: String     // "16:00"
)
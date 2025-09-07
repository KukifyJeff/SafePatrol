package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspection_sessions")
data class InspectionSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val routeId: String,
    val routeName: String,
    val operatorId: String,
    val shiftId: String,
    val startTime: Long = System.currentTimeMillis()
)
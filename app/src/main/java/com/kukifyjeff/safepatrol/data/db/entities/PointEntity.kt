package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.*

@Entity(
    tableName = "points",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["routeId"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routeId")]
)
data class PointEntity(
    @PrimaryKey val equipmentId: String,
    val name: String,
    val location: String,
    val freqHours: Int,   // 4 或 8
    val templateId: String,
    val routeId: String
)
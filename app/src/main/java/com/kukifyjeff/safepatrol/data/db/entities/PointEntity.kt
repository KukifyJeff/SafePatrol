package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    @PrimaryKey val pointId: String,
    val name: String,
    val location: String,
//    val freqHours: Int,   // 2， 4 或 8
    val routeId: String,
    val tagUid: String
)
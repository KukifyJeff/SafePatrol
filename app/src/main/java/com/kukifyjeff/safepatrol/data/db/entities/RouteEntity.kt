package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val routeName: String,
    val description: String?
)
package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.PointEntity

@Dao
interface PointDao {
    @Query("SELECT * FROM points WHERE routeId = :routeId")
    suspend fun getByRoute(routeId: String): List<PointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<PointEntity>)

    @Query("SELECT * FROM points WHERE equipmentId = :id LIMIT 1")
    suspend fun findById(id: String): PointEntity?
}
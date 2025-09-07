package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.RouteEntity

@Dao
interface RouteDao {
    @Query("SELECT COUNT(*) FROM routes") fun countRoutes(): Int
    @Query("SELECT * FROM routes") suspend fun getAll(): List<RouteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(list: List<RouteEntity>)
}
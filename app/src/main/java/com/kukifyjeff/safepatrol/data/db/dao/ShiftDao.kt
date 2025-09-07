package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.ShiftEntity

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts")
    suspend fun getAll(): List<ShiftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<ShiftEntity>)
}
package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity

@Dao
interface CheckItemDao {
    @Query("SELECT * FROM check_items WHERE equipmentId = :equipId")
    suspend fun getByEquipment(equipId: String): List<CheckItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<CheckItemEntity>)

    @Query("SELECT DISTINCT freqHours FROM check_items WHERE equipmentId = :equipId")
    suspend fun getFreqHoursByEquipment(equipId: String): List<Int>

    @Query("SELECT * FROM check_items WHERE equipmentId IN (:equipIds)")
    suspend fun getByEquipments(equipIds: List<String>): List<CheckItemEntity>
}
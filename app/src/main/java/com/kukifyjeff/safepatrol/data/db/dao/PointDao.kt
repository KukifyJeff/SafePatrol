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

    // 根据 NFC 标签 UID 查询所有设备 ID
    @Query("SELECT equipmentId FROM equipments WHERE pointId = (SELECT pointId FROM points WHERE tagUid = :tagUid)")
    suspend fun findPointIdsByTag(tagUid: String): List<String>

    // 根据 NFC 标签 UID 查询点位信息
    @Query("SELECT * FROM points WHERE tagUid = :tagUid LIMIT 1")
    suspend fun findByTagUid(tagUid: String): PointEntity?

    @Query("SELECT * FROM points WHERE pointId = :id LIMIT 1")
    suspend fun findById(id: String): PointEntity?
}
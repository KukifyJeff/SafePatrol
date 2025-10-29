package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity

@Dao
interface EquipmentDao {

    // 根据点位ID查询设备列表
    @Query("SELECT * FROM equipments WHERE pointId = :pointId")
    suspend fun getByPoint(pointId: String): List<EquipmentEntity>

    // 根据设备ID获取单个设备
    @Query("SELECT * FROM equipments WHERE equipmentId = :equipId LIMIT 1")
    suspend fun getById(equipId: String): EquipmentEntity?

    // 插入或更新设备信息
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<EquipmentEntity>)

    // 查询所有需要启停状态设置的设备
    @Query("SELECT * FROM equipments WHERE statusRequired = 1")
    suspend fun getAllStatusRequired(): List<EquipmentEntity>

    // 根据tagUid查询点位下所有设备
    @Query("""
        SELECT e.* FROM equipments e
        INNER JOIN points p ON e.pointId = p.pointId
        WHERE p.tagUid = :tagUid
    """)
    suspend fun getEquipmentsByTag(tagUid: String): List<EquipmentEntity>
}
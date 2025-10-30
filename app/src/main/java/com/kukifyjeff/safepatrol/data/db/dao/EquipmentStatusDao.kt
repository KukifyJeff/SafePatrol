package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentStatusEntity

@Dao
interface EquipmentStatusDao {

    // 插入或更新状态
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: EquipmentStatusEntity)

    // 根据设备ID查询当前状态
    @Query("SELECT * FROM equipment_status WHERE equipmentId = :equipId LIMIT 1")
    suspend fun getStatusByEquipment(equipId: String): EquipmentStatusEntity?

    // 查询某个点位下所有设备的状态
    @Query("""
        SELECT s.* FROM equipment_status s
        INNER JOIN equipments e ON s.equipmentId = e.equipmentId
        WHERE e.pointId = :pointId
    """)
    suspend fun getStatusByPoint(pointId: String): List<EquipmentStatusEntity>

    // 获取所有设备状态（可用于调试或导出）
    @Query("SELECT * FROM equipment_status")
    suspend fun getAllStatuses(): List<EquipmentStatusEntity>

    // 删除所有状态记录
    @Query("DELETE FROM equipment_status")
    suspend fun clearAll()


}
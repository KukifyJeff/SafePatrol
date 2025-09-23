package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.*
import com.kukifyjeff.safepatrol.data.db.entities.*

@Dao
interface InspectionDao {
    // Session
    @Insert suspend fun insertSession(session: InspectionSessionEntity): Long
    @Query("SELECT * FROM inspection_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): InspectionSessionEntity?

    // Record
    @Insert suspend fun insertRecord(record: InspectionRecordEntity): Long
    @Query("SELECT * FROM inspection_records WHERE sessionId = :sessionId AND equipmentId = :equipId")
    suspend fun getRecordsForPoint(sessionId: Long, equipId: String): List<InspectionRecordEntity>


    @Query("SELECT * FROM inspection_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun getSessionById(id: Long): InspectionSessionEntity?

    // Record items
    @Insert suspend fun insertItems(items: List<InspectionRecordItemEntity>)
    @Query("SELECT * FROM inspection_record_items WHERE recordId = :recordId")
    suspend fun getItemsForRecord(recordId: Long): List<InspectionRecordItemEntity>

    @Query("SELECT * FROM inspection_records WHERE sessionId = :sessionId")
    suspend fun getAllRecordsForSession(sessionId: Long): List<InspectionRecordEntity>


    // 1) 某点位在班次时间窗内，某槽位是否已点检（用于去重 & 首页打勾）
    @Query("""
    SELECT EXISTS(
      SELECT 1 FROM inspection_records
      WHERE equipmentId = :equipId
        AND slotIndex = :slotIndex
        AND timestamp BETWEEN :startMs AND :endMs
      LIMIT 1
    )
""")
    suspend fun hasRecordForPointSlotInWindow(
        equipId: String,
        slotIndex: Int,
        startMs: Long,
        endMs: Long
    ): Boolean

    // 2) 班次时间窗内的所有记录（用于导出汇总，不看 session/operator）
    @Query("""
    SELECT * FROM inspection_records
    WHERE timestamp BETWEEN :startMs AND :endMs
""")
    suspend fun getRecordsInWindow(
        startMs: Long,
        endMs: Long
    ): List<InspectionRecordEntity>

    // 3) 为导出批量取 item（避免 N 次查询）
    @Query("""
    SELECT * FROM inspection_record_items
    WHERE recordId IN (:recordIds)
""")
    suspend fun getItemsForRecordIds(recordIds: List<Long>): List<InspectionRecordItemEntity>



    @Query("""
    SELECT iri.* FROM inspection_record_items iri
    JOIN inspection_records ir ON ir.recordId = iri.recordId
    WHERE ir.sessionId = :sessionId
""")
    suspend fun getAllItemsForSession(sessionId: Long): List<InspectionRecordItemEntity>

    @Query(
        "SELECT * FROM inspection_records WHERE equipmentId = :equipId AND slotIndex = :slotIndex AND timestamp BETWEEN :startMs AND :endMs"  )
    suspend fun getRecordsForPointSlotInWindow(
            equipId: String,
            slotIndex: Int,
            startMs: Long,
            endMs: Long
        ): List<InspectionRecordEntity>

    /** 批量获取会话（用于导出时按记录还原当时操作员/班次） */
    @Query("SELECT * FROM inspection_sessions WHERE sessionId IN (:ids)")
    suspend fun getSessionsByIds(ids: List<Long>): List<InspectionSessionEntity>

    @Query("DELETE FROM inspection_record_items")
    suspend fun deleteAllRecordItems()

    @Query("DELETE FROM inspection_records")
    suspend fun deleteAllRecords()

    @Query("DELETE FROM inspection_sessions")
    suspend fun deleteAllSessions()

}
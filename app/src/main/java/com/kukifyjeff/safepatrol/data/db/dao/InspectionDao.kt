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
    suspend fun getAllRecordsForSession(sessionId: Long): List<com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity>

    @Query("""
    SELECT iri.* FROM inspection_record_items iri
    JOIN inspection_records ir ON ir.recordId = iri.recordId
    WHERE ir.sessionId = :sessionId
""")
    suspend fun getAllItemsForSession(sessionId: Long): List<com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordItemEntity>
}
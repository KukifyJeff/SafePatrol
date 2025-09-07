package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kukifyjeff.safepatrol.data.db.entities.NfcMapEntity

@Dao
interface NfcMapDao {
    @Query("SELECT equipmentId FROM nfc_map WHERE tagUid = :tagUid LIMIT 1")
    suspend fun findEquipmentIdByTag(tagUid: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<NfcMapEntity>)
}
package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_map", indices = [Index("equipmentId")])
data class NfcMapEntity(
    @PrimaryKey val tagUid: String,
    val equipmentId: String
)
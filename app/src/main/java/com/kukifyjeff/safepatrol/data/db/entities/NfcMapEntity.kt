package com.kukifyjeff.safepatrol.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nfc_map",
    indices = [Index(value = ["tagUid"]), Index(value = ["equipmentId"])]
)
data class NfcMapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tagUid: String,
    val equipmentId: String
)
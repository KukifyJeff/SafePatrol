package com.kukifyjeff.safepatrol

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kukifyjeff.safepatrol.data.db.dao.*
import com.kukifyjeff.safepatrol.data.db.entities.*

@Database(
    entities = [
        RouteEntity::class, PointEntity::class, CheckItemEntity::class,
        EquipmentEntity::class, EquipmentStatusEntity::class, ShiftEntity::class, InspectionSessionEntity::class,
        InspectionRecordEntity::class, InspectionRecordItemEntity::class,
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun pointDao(): PointDao
    abstract fun checkItemDao(): CheckItemDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun equipmentStatusDao(): EquipmentStatusDao
    abstract fun shiftDao(): ShiftDao
    abstract fun inspectionDao(): InspectionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java, "safe_patrol.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
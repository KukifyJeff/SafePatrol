package com.kukifyjeff.safepatrol

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kukifyjeff.safepatrol.data.db.dao.CheckItemDao
import com.kukifyjeff.safepatrol.data.db.dao.EmployeeDao
import com.kukifyjeff.safepatrol.data.db.dao.EquipmentDao
import com.kukifyjeff.safepatrol.data.db.dao.EquipmentStatusDao
import com.kukifyjeff.safepatrol.data.db.dao.InspectionDao
import com.kukifyjeff.safepatrol.data.db.dao.PointDao
import com.kukifyjeff.safepatrol.data.db.dao.RouteDao
import com.kukifyjeff.safepatrol.data.db.dao.ShiftDao
import com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity
import com.kukifyjeff.safepatrol.data.db.entities.EmployeeEntity
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentStatusEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordItemEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionSessionEntity
import com.kukifyjeff.safepatrol.data.db.entities.PointEntity
import com.kukifyjeff.safepatrol.data.db.entities.RouteEntity
import com.kukifyjeff.safepatrol.data.db.entities.ShiftEntity

@Database(
    entities = [
        RouteEntity::class, PointEntity::class, CheckItemEntity::class,
        EquipmentEntity::class, EquipmentStatusEntity::class, ShiftEntity::class, InspectionSessionEntity::class,
        InspectionRecordEntity::class, InspectionRecordItemEntity::class, EmployeeEntity::class,
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
    abstract fun employeeDao(): EmployeeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java, "safe_patrol.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
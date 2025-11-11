package com.kukifyjeff.safepatrol.data.db.dao

import androidx.room.*
import com.kukifyjeff.safepatrol.data.db.entities.EmployeeEntity


@Dao
interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(employees: List<EmployeeEntity>)

    @Query("SELECT * FROM employees")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE employeeId = :id LIMIT 1")
    suspend fun getById(id: String): EmployeeEntity?

    @Query("DELETE FROM employees")
    suspend fun clear()
}
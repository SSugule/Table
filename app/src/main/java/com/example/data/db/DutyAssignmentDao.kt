package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DutyAssignment
import kotlinx.coroutines.flow.Flow

@Dao
interface DutyAssignmentDao {

    @Query("SELECT * FROM duty_assignments WHERE dateString >= :startDate AND dateString <= :endDate ORDER BY dateString ASC, postId ASC, slotIndex ASC")
    fun getAssignmentsForRangeFlow(startDate: String, endDate: String): Flow<List<DutyAssignment>>

    @Query("SELECT * FROM duty_assignments WHERE dateString >= :startDate AND dateString <= :endDate ORDER BY dateString ASC, postId ASC, slotIndex ASC")
    suspend fun getAssignmentsForRange(startDate: String, endDate: String): List<DutyAssignment>

    @Query("SELECT * FROM duty_assignments WHERE dateString = :dateString")
    suspend fun getAssignmentsForDate(dateString: String): List<DutyAssignment>

    @Query("SELECT * FROM duty_assignments WHERE dateString = :dateString")
    fun getAssignmentsForDateFlow(dateString: String): Flow<List<DutyAssignment>>

    @Query("SELECT * FROM duty_assignments WHERE employeeId = :employeeId ORDER BY dateString DESC")
    suspend fun getAssignmentsForEmployee(employeeId: Long): List<DutyAssignment>

    @Query("SELECT * FROM duty_assignments ORDER BY dateString ASC")
    fun getAllAssignmentsFlow(): Flow<List<DutyAssignment>>

    @Query("SELECT * FROM duty_assignments ORDER BY dateString ASC")
    suspend fun getAllAssignments(): List<DutyAssignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(assignment: DutyAssignment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<DutyAssignment>)

    @Query("DELETE FROM duty_assignments WHERE dateString = :dateString AND postId = :postId AND slotIndex = :slotIndex")
    suspend fun removeAssignment(dateString: String, postId: String, slotIndex: Int)

    @Query("DELETE FROM duty_assignments WHERE dateString >= :startDate AND dateString <= :endDate")
    suspend fun clearRange(startDate: String, endDate: String)

    @Query("DELETE FROM duty_assignments WHERE employeeId = :employeeId")
    suspend fun deleteByEmployeeId(employeeId: Long)

    @Query("DELETE FROM duty_assignments")
    suspend fun clearAll()
}


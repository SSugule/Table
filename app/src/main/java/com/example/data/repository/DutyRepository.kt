package com.example.data.repository

import com.example.data.db.DutyAssignmentDao
import com.example.data.db.EmployeeDao
import com.example.data.model.DutyAssignment
import com.example.data.model.Employee
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DutyRepository(
    private val employeeDao: EmployeeDao,
    private val dutyAssignmentDao: DutyAssignmentDao
) {
    val allEmployeesFlow: Flow<List<Employee>> = employeeDao.getAllEmployeesFlow()
    val allAssignmentsFlow: Flow<List<DutyAssignment>> = dutyAssignmentDao.getAllAssignmentsFlow()

    fun getAssignmentsForRangeFlow(startDate: String, endDate: String): Flow<List<DutyAssignment>> {
        return dutyAssignmentDao.getAssignmentsForRangeFlow(startDate, endDate)
    }

    suspend fun getAllEmployees(): List<Employee> = withContext(Dispatchers.IO) {
        employeeDao.getAllEmployees()
    }

    suspend fun getAssignmentsForRange(startDate: String, endDate: String): List<DutyAssignment> =
        withContext(Dispatchers.IO) {
            dutyAssignmentDao.getAssignmentsForRange(startDate, endDate)
        }

    suspend fun getAssignmentsForDate(dateString: String): List<DutyAssignment> =
        withContext(Dispatchers.IO) {
            dutyAssignmentDao.getAssignmentsForDate(dateString)
        }

    suspend fun saveEmployee(employee: Employee): Long = withContext(Dispatchers.IO) {
        if (employee.id == 0L) {
            employeeDao.insert(employee)
        } else {
            employeeDao.update(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(employee: Employee) = withContext(Dispatchers.IO) {
        dutyAssignmentDao.deleteByEmployeeId(employee.id)
        employeeDao.delete(employee)
    }

    suspend fun assignEmployee(
        dateString: String,
        postId: String,
        slotIndex: Int,
        employeeId: Long,
        note: String = ""
    ) = withContext(Dispatchers.IO) {
        dutyAssignmentDao.insertOrUpdate(
            DutyAssignment(
                dateString = dateString,
                postId = postId,
                slotIndex = slotIndex,
                employeeId = employeeId,
                note = note
            )
        )
    }

    suspend fun clearAssignment(dateString: String, postId: String, slotIndex: Int) =
        withContext(Dispatchers.IO) {
            dutyAssignmentDao.removeAssignment(dateString, postId, slotIndex)
        }

    suspend fun clearRange(startDate: String, endDate: String) = withContext(Dispatchers.IO) {
        dutyAssignmentDao.clearRange(startDate, endDate)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dutyAssignmentDao.clearAll()
    }

    suspend fun insertAllAssignments(assignments: List<DutyAssignment>) = withContext(Dispatchers.IO) {
        dutyAssignmentDao.insertAll(assignments)
    }

    /**
     * При установке приложения пользователем из GitHub:
     * - График = nil
     * - Сотрудники = nil
     * Пользователь сам добавит необходимых сотрудников и сам создаст график.
     */
    suspend fun checkAndSeedInitialData() = withContext(Dispatchers.IO) {
        // Никаких стандартных сотрудников и назначений не добавляем!
    }
}


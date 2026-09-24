package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EmployeeType(val label: String, val shortLabel: String) {
    DUTY("Дежурный", "Деж."),
    ASSISTANT("Помощник", "Пом.")
}

enum class EmployeeStatus(val title: String) {
    WORKING("На рабочем дне"),
    POST_DUTY_REST("На отсыпном"),
    DAY_OFF("На выходном"),
    VACATION("В отпуске"),
    SICK_LEAVE("На больничном")
}

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String, // Фамилия + инициалы (например "Амбарцумян А.А.")
    val type: EmployeeType, // Дежурный / Помощник
    val manualStatus: EmployeeStatus = EmployeeStatus.WORKING, // Базовый статус или отпуск/больничный
    val statusUntilDate: String? = null, // например "01.08.26"
    val phone: String = "",
    val notes: String = "",
    val isActive: Boolean = true
) {
    /**
     * Отображаемый статус с датой окончания, если в отпуске или на больничном
     */
    fun getFormattedStatusText(dynamicStatus: EmployeeStatus = manualStatus): String {
        return when (dynamicStatus) {
            EmployeeStatus.VACATION -> {
                if (!statusUntilDate.isNullOrBlank()) "В отпуске\nдо $statusUntilDate" else "В отпуске"
            }
            EmployeeStatus.SICK_LEAVE -> {
                if (!statusUntilDate.isNullOrBlank()) "На больничном\nдо $statusUntilDate" else "На больничном"
            }
            EmployeeStatus.POST_DUTY_REST -> "На отсыпном"
            EmployeeStatus.DAY_OFF -> "На выходном"
            EmployeeStatus.WORKING -> "На рабочем дне"
        }
    }
}

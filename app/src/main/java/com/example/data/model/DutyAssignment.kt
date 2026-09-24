package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "duty_assignments",
    indices = [
        Index(value = ["dateString", "postId", "slotIndex"], unique = true),
        Index(value = ["employeeId", "dateString"])
    ]
)
data class DutyAssignment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String, // "YYYY-MM-DD"
    val postId: String,     // e.g. "kpp1", "kpp2", "senior_car", "vg2"
    val slotIndex: Int,     // 0, 1, 2
    val employeeId: Long,   // ID сотрудника
    val note: String = ""   // Заметка (например "утро", "обед", "ужин" или пусто)
)

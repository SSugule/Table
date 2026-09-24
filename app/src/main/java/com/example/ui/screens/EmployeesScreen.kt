package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DutyAssignment
import com.example.data.model.Employee
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import com.example.domain.DutyRulesEngine
import com.example.ui.components.EmployeeTypeBadge
import com.example.ui.components.StatusDisplayBadge
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLightBg
import com.example.ui.viewmodel.DutyViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    viewModel: DutyViewModel,
    employees: List<Employee>,
    assignments: List<DutyAssignment>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Все, 1: Дежурные, 2: Помощники

    val today = remember { LocalDate.now() }

    val filteredEmployees = remember(employees, searchQuery, selectedTab) {
        employees.filter { emp ->
            val matchRole = when (selectedTab) {
                1 -> emp.type == EmployeeType.DUTY
                2 -> emp.type == EmployeeType.ASSISTANT
                else -> true
            }
            val matchQuery = searchQuery.isBlank() || emp.fullName.contains(searchQuery, ignoreCase = true)
            matchRole && matchQuery
        }
    }

    // Подсчет нарядов каждого сотрудника
    val dutyCounts = remember(assignments) {
        assignments.groupingBy { it.employeeId }.eachCount()
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateLightBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Поиск и вкладки
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск сотрудника...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("employee_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Все (${employees.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Дежурные (${employees.count { it.type == EmployeeType.DUTY }})") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Помощники (${employees.count { it.type == EmployeeType.ASSISTANT }})") }
                        )
                    }
                }
            }

            // Список сотрудников
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredEmployees.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Сотрудники не найдены",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(filteredEmployees, key = { it.id }) { emp ->
                    val statusInfo = remember(emp, assignments) {
                        DutyRulesEngine.calculateStatusOnDate(emp, today, assignments)
                    }
                    val dutiesServed = dutyCounts[emp.id] ?: 0

                    EmployeeCard(
                        employee = emp,
                        statusText = emp.getFormattedStatusText(statusInfo.status),
                        status = statusInfo.status,
                        dutiesCount = dutiesServed,
                        onToggleRole = { viewModel.toggleEmployeeRole(emp) },
                        onEdit = { viewModel.openEditEmployeeDialog(emp) },
                        onStatusChange = { newStatus, untilDate ->
                            viewModel.updateEmployeeStatus(emp, newStatus, untilDate)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // FAB: Добавить сотрудника
        FloatingActionButton(
            onClick = { viewModel.openAddEmployeeDialog() },
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_employee_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить сотрудника")
        }
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    statusText: String,
    status: EmployeeStatus,
    dutiesCount: Int,
    onToggleRole: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (EmployeeStatus, String?) -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showCustomStatusDialog by remember { mutableStateOf<EmployeeStatus?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_item_${employee.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Аватар / Иконка и имя
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Navy800,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Navy800
                        )
                        Text(
                            text = "Нарядов отстоял: $dutiesCount",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Кнопка редактирования
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

            // Нижняя плашка: Тип (с переключателем) и Статус (с быстрым меню смены)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Переключатель роли (в один клик)
                Surface(
                    onClick = onToggleRole,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.testTag("toggle_role_${employee.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmployeeTypeBadge(type = employee.type)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Сменить роль",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Статус сотрудника (нажатие открывает меню выбора статуса)
                Box {
                    Surface(
                        onClick = { showStatusMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent
                    ) {
                        StatusDisplayBadge(status = status, formattedText = statusText)
                    }

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("На рабочем дне") },
                            onClick = {
                                onStatusChange(EmployeeStatus.WORKING, null)
                                showStatusMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("На отсыпном") },
                            onClick = {
                                onStatusChange(EmployeeStatus.POST_DUTY_REST, null)
                                showStatusMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("На выходном") },
                            onClick = {
                                onStatusChange(EmployeeStatus.DAY_OFF, null)
                                showStatusMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("В отпуске (указать дату)...") },
                            onClick = {
                                showStatusMenu = false
                                showCustomStatusDialog = EmployeeStatus.VACATION
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("На больничном (указать дату)...") },
                            onClick = {
                                showStatusMenu = false
                                showCustomStatusDialog = EmployeeStatus.SICK_LEAVE
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог ввода даты окончания отпуска или больничного
    showCustomStatusDialog?.let { targetStatus ->
        var untilDateText by remember { mutableStateOf(employee.statusUntilDate ?: "01.10.26") }

        AlertDialog(
            onDismissRequest = { showCustomStatusDialog = null },
            title = {
                Text(if (targetStatus == EmployeeStatus.VACATION) "Сотрудник в отпуске" else "Сотрудник на больничном")
            },
            text = {
                Column {
                    Text(
                        text = "Укажите дату окончания (будет отображаться в статусе):",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = untilDateText,
                        onValueChange = { untilDateText = it },
                        placeholder = { Text("например 01.08.26") },
                        label = { Text("До какой даты") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStatusChange(targetStatus, untilDateText.trim())
                        showCustomStatusDialog = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomStatusDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Диалог создания / редактирования сотрудника
 */
@Composable
fun EditEmployeeDialog(
    employee: Employee,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit,
    onDelete: (Employee) -> Unit
) {
    var fullName by remember { mutableStateOf(employee.fullName) }
    var selectedType by remember { mutableStateOf(employee.type) }
    var manualStatus by remember { mutableStateOf(employee.manualStatus) }
    var statusUntilDate by remember { mutableStateOf(employee.statusUntilDate.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (employee.id == 0L) "Новый сотрудник" else "Редактирование сотрудника")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Фамилия + инициалы") },
                    placeholder = { Text("например: Амбарцумян А.А.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_name_input")
                )

                Text(
                    text = "Тип должности:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Navy800
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedType = EmployeeType.DUTY }
                    ) {
                        RadioButton(
                            selected = selectedType == EmployeeType.DUTY,
                            onClick = { selectedType = EmployeeType.DUTY }
                        )
                        Text("Дежурный", fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedType = EmployeeType.ASSISTANT }
                    ) {
                        RadioButton(
                            selected = selectedType == EmployeeType.ASSISTANT,
                            onClick = { selectedType = EmployeeType.ASSISTANT }
                        )
                        Text("Помощник", fontSize = 14.sp)
                    }
                }

                Text(
                    text = "Базовый статус:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Navy800
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        EmployeeStatus.WORKING to "Рабочий",
                        EmployeeStatus.VACATION to "Отпуск",
                        EmployeeStatus.SICK_LEAVE to "Больничный"
                    ).forEach { (st, label) ->
                        val isSel = manualStatus == st
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) PrimaryBlue else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { manualStatus = st }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (isSel) Color.White else Navy800,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (manualStatus == EmployeeStatus.VACATION || manualStatus == EmployeeStatus.SICK_LEAVE) {
                    OutlinedTextField(
                        value = statusUntilDate,
                        onValueChange = { statusUntilDate = it },
                        label = { Text("До какой даты (например 01.08.26)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        employee.copy(
                            fullName = fullName.trim(),
                            type = selectedType,
                            manualStatus = manualStatus,
                            statusUntilDate = if (manualStatus == EmployeeStatus.VACATION || manualStatus == EmployeeStatus.SICK_LEAVE) statusUntilDate.trim() else null
                        )
                    )
                },
                modifier = Modifier.testTag("save_employee_btn")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (employee.id != 0L) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_employee_btn")
                    ) {
                        Text("Удалить")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить сотрудника?") },
            text = { Text("Сотрудник ${employee.fullName} будет удален из базы и со всех постов.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(employee)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

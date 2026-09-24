package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DutyAssignment
import com.example.data.model.DutyPost
import com.example.data.model.Employee
import com.example.data.model.SlotDef
import com.example.domain.DutyRulesEngine
import com.example.domain.IssueSeverity
import com.example.ui.components.EmployeeTypeBadge
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLightBg
import com.example.ui.viewmodel.DutyViewModel
import com.example.ui.viewmodel.ScheduleRangeType
import com.example.ui.viewmodel.ScheduleViewMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: DutyViewModel,
    selectedMonth: YearMonth,
    viewMode: ScheduleViewMode,
    assignments: List<DutyAssignment>,
    employees: List<Employee>,
    onOpenSlotPicker: (LocalDate, DutyPost, SlotDef) -> Unit
) {
    val ruLocale = Locale("ru", "RU")
    val monthTitle = selectedMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, ruLocale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() } + " ${selectedMonth.year}"

    var showCreateScheduleDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Настройки для диалога создания графика
    var selectedRangeType by remember { mutableStateOf(ScheduleRangeType.WEEK) }
    var customStartDayInput by remember { mutableStateOf("1") }
    var customEndDayInput by remember { mutableStateOf("7") }

    val daysInMonth = remember(selectedMonth) {
        val count = selectedMonth.lengthOfMonth()
        (1..count).map { selectedMonth.atDay(it) }
    }

    val empMap = remember(employees) { employees.associateBy { it.id } }
    val assignmentMap = remember(assignments) {
        assignments.associateBy { "${it.dateString}_${it.postId}_${it.slotIndex}" }
    }

    val isScheduleEmpty = assignments.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateLightBg)
    ) {
        // Месячный навигатор и панель инструментов
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.prevMonth() },
                            modifier = Modifier.size(36.dp).testTag("prev_month_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий месяц")
                        }

                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Navy800,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { viewModel.nextMonth() },
                            modifier = Modifier.size(36.dp).testTag("next_month_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий месяц")
                        }
                    }

                    // Переключатель вида (Таблица / Карточки)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setViewMode(ScheduleViewMode.TABLE) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (viewMode == ScheduleViewMode.TABLE) PrimaryBlue else Color.Transparent, RoundedCornerShape(6.dp))
                                .testTag("view_mode_table_btn")
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = "Таблица",
                                tint = if (viewMode == ScheduleViewMode.TABLE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setViewMode(ScheduleViewMode.DAY_CARDS) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (viewMode == ScheduleViewMode.DAY_CARDS) PrimaryBlue else Color.Transparent, RoundedCornerShape(6.dp))
                                .testTag("view_mode_cards_btn")
                        ) {
                            Icon(
                                Icons.Default.ViewAgenda,
                                contentDescription = "По дням",
                                tint = if (viewMode == ScheduleViewMode.DAY_CARDS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Кнопки быстрых действий: Создать график (настройка периода), Проверка, Очистить
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { showCreateScheduleDialog = true },
                        label = { Text("Создать график", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue) },
                        modifier = Modifier.testTag("autofill_btn")
                    )

                    if (!isScheduleEmpty) {
                        AssistChip(
                            onClick = { viewModel.runScheduleAudit() },
                            label = { Text("Проверка", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("audit_schedule_btn")
                        )

                        AssistChip(
                            onClick = { showClearDialog = true },
                            label = { Text("Очистить", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("clear_schedule_btn")
                        )
                    }
                }
            }
        }

        // Если график изначально пустой — показываем крупную кнопку создания с подсказкой
        if (isScheduleEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "График пока не создан",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Navy800
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Стандартно график создается на 1 неделю. Вы также можете выбрать формирование на месяц или задать любой свой диапазон дней.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showCreateScheduleDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_schedule_initial_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Создать график", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Тело экрана: Таблица или Карточки
            if (viewMode == ScheduleViewMode.TABLE) {
                ScheduleTableView(
                    days = daysInMonth,
                    posts = DutyPost.ALL_POSTS,
                    assignmentMap = assignmentMap,
                    empMap = empMap,
                    onSlotClick = onOpenSlotPicker
                )
            } else {
                ScheduleDayCardsView(
                    days = daysInMonth,
                    posts = DutyPost.ALL_POSTS,
                    assignmentMap = assignmentMap,
                    empMap = empMap,
                    onSlotClick = onOpenSlotPicker
                )
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Диалог параметров создания графика (Стандарт на неделю / Месяц / Свой диапазон)
    if (showCreateScheduleDialog) {
        AlertDialog(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showCreateScheduleDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создание графика нарядов")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Выберите период формирования (стандарт — 1 неделя):",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )

                    // Варианты: Неделя, Месяц, Свой диапазон
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScheduleRangeType.values().forEach { rangeType ->
                            val isSelected = selectedRangeType == rangeType
                            Surface(
                                onClick = { selectedRangeType = rangeType },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, if (isSelected) PrimaryBlue else Color(0xFF94A3B8), CircleShape)
                                            .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = rangeType.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryBlue else Navy800
                                    )
                                }
                            }
                        }
                    }

                    if (selectedRangeType == ScheduleRangeType.CUSTOM) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Дни месяца (${selectedMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, ruLocale)}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Navy800
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customStartDayInput,
                                onValueChange = { customStartDayInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("С какого дня") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = customEndDayInput,
                                onValueChange = { customEndDayInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("По какой день") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Соблюдается правило отдыха: Сутки -> Отсыпной -> Выходной -> Рабочий день\n" +
                                "• Старший машины: дневной наряд (можно заступать даже в отсыпной день)\n" +
                                "• Разделение дежурных и помощников по постам",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        val maxDay = selectedMonth.lengthOfMonth()
                        val (startDay, endDay) = when (selectedRangeType) {
                            ScheduleRangeType.WEEK -> Pair(1, minOf(7, maxDay))
                            ScheduleRangeType.MONTH -> Pair(1, maxDay)
                            ScheduleRangeType.CUSTOM -> {
                                val s = (customStartDayInput.toIntOrNull() ?: 1).coerceIn(1, maxDay)
                                val e = (customEndDayInput.toIntOrNull() ?: minOf(s + 6, maxDay)).coerceIn(s, maxDay)
                                Pair(s, e)
                            }
                        }
                        val startDate = selectedMonth.atDay(startDay)
                        val endDate = selectedMonth.atDay(endDay)
                        viewModel.createScheduleForRange(startDate, endDate, overwrite = true)
                        showCreateScheduleDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_schedule_btn")
                ) {
                    Text("Сформировать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showCreateScheduleDialog = false
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения очистки
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить график?") },
            text = { Text("Все назначения графика будут удалены. База сотрудников и настройки постов полностью сохранятся.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllSchedule()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}


/**
 * Табличное отображение графика — точно соответствует рукописной таблице пользователя
 */
@Composable
fun ScheduleTableView(
    days: List<LocalDate>,
    posts: List<DutyPost>,
    assignmentMap: Map<String, DutyAssignment>,
    empMap: Map<Long, Employee>,
    onSlotClick: (LocalDate, DutyPost, SlotDef) -> Unit
) {
    val hScrollState = rememberScrollState()

    // Ширины колонок: Дата (60dp), КПП1 (160dp), КПП2 (120dp), Старший машины (140dp), ВГ2 (120dp)
    val dateColWidth = 60.dp
    val kpp1ColWidth = 170.dp
    val kpp2ColWidth = 130.dp
    val carColWidth = 150.dp
    val vg2ColWidth = 130.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScrollState)
    ) {
        // Шапка таблицы
        Row(
            modifier = Modifier
                .background(Color(0xFFE2E8F0))
                .border(1.dp, Color(0xFFCBD5E1))
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(dateColWidth), contentAlignment = Alignment.Center) {
                Text("Дата", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Navy800)
            }
            Box(modifier = Modifier.width(kpp1ColWidth), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("КПП 1", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy800)
                    Text("1 деж., 2 пом.", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Box(modifier = Modifier.width(kpp2ColWidth), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("КПП 2", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy800)
                    Text("1 дежурный", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Box(modifier = Modifier.width(carColWidth), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ст. машины", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy800)
                    Text("деж. / пом.", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Box(modifier = Modifier.width(vg2ColWidth), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ВГ 2", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy800)
                    Text("1 дежурный", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Строки дней
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(days, key = { it.toString() }) { date ->
                val dateStr = DutyRulesEngine.formatDate(date)
                val isWeekend = date.dayOfWeek.value in 6..7
                val dayOfWeekStr = when (date.dayOfWeek.value) {
                    1 -> "пн"; 2 -> "вт"; 3 -> "ср"; 4 -> "чт"; 5 -> "пт"; 6 -> "сб"; else -> "вс"
                }

                val rowBg = if (isWeekend) Color(0xFFFEF2F2) else Color.White

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Колонка даты
                    Column(
                        modifier = Modifier
                            .width(dateColWidth)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isWeekend) Color(0xFFDC2626) else Navy800
                        )
                        Text(
                            text = dayOfWeekStr,
                            fontSize = 11.sp,
                            color = if (isWeekend) Color(0xFFDC2626) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Колонка КПП 1 (3 слота: дежурный + 2 помощника)
                    val kpp1 = DutyPost.POST_KPP1
                    Column(
                        modifier = Modifier
                            .width(kpp1ColWidth)
                            .padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        kpp1.slots.forEach { slot ->
                            TableCellSlot(
                                date = date,
                                post = kpp1,
                                slot = slot,
                                assignment = assignmentMap["${dateStr}_${kpp1.id}_${slot.slotIndex}"],
                                empMap = empMap,
                                onClick = { onSlotClick(date, kpp1, slot) }
                            )
                        }
                    }

                    // Колонка КПП 2 (1 слот: дежурный)
                    val kpp2 = DutyPost.POST_KPP2
                    Box(
                        modifier = Modifier
                            .width(kpp2ColWidth)
                            .padding(horizontal = 2.dp)
                    ) {
                        val slot = kpp2.slots[0]
                        TableCellSlot(
                            date = date,
                            post = kpp2,
                            slot = slot,
                            assignment = assignmentMap["${dateStr}_${kpp2.id}_${slot.slotIndex}"],
                            empMap = empMap,
                            onClick = { onSlotClick(date, kpp2, slot) }
                        )
                    }

                    // Колонка Старший машины (Утро, Обед, Вечер)
                    val car = DutyPost.POST_SENIOR_CAR
                    Box(
                        modifier = Modifier
                            .width(carColWidth)
                            .padding(horizontal = 2.dp)
                    ) {
                        SeniorCarTableCell(
                            date = date,
                            post = car,
                            assignmentMap = assignmentMap,
                            empMap = empMap,
                            onSlotClick = onSlotClick
                        )
                    }

                    // Колонка ВГ 2 (1 слот: дежурный)
                    val vg2 = DutyPost.POST_VG2
                    Box(
                        modifier = Modifier
                            .width(vg2ColWidth)
                            .padding(horizontal = 2.dp)
                    ) {
                        val slot = vg2.slots[0]
                        TableCellSlot(
                            date = date,
                            post = vg2,
                            slot = slot,
                            assignment = assignmentMap["${dateStr}_${vg2.id}_${slot.slotIndex}"],
                            empMap = empMap,
                            onClick = { onSlotClick(date, vg2, slot) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TableCellSlot(
    date: LocalDate,
    post: DutyPost,
    slot: SlotDef,
    assignment: DutyAssignment?,
    empMap: Map<Long, Employee>,
    onClick: () -> Unit
) {
    val emp = assignment?.let { empMap[it.employeeId] }
    val isAssigned = emp != null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isAssigned) Color(0xFFF1F5F9) else Color(0xFFFAFAFA),
        border = BorderStroke(0.8.dp, if (isAssigned) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("slot_${date}_${post.id}_${slot.slotIndex}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isAssigned) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = emp!!.fullName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Navy800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (assignment!!.note.isNotBlank()) {
                        Text(
                            text = assignment.note,
                            fontSize = 9.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Назначить",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = slot.title,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SeniorCarTableCell(
    date: LocalDate,
    post: DutyPost,
    assignmentMap: Map<String, DutyAssignment>,
    empMap: Map<Long, Employee>,
    onSlotClick: (LocalDate, DutyPost, SlotDef) -> Unit
) {
    val dateStr = DutyRulesEngine.formatDate(date)
    val slot0 = assignmentMap["${dateStr}_${post.id}_0"]
    val slot1 = assignmentMap["${dateStr}_${post.id}_1"]
    val slot2 = assignmentMap["${dateStr}_${post.id}_2"]

    val emp0 = slot0?.let { empMap[it.employeeId] }
    val emp1 = slot1?.let { empMap[it.employeeId] }
    val emp2 = slot2?.let { empMap[it.employeeId] }

    val allThreeSame = emp0 != null && emp0.id == emp1?.id && emp0.id == emp2?.id

    if (allThreeSame) {
        // На все 3 смены (Утро, Обед, Вечер) заступил один и тот же сотрудник
        Surface(
            onClick = { onSlotClick(date, post, post.slots[0]) },
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFEFF6FF),
            border = BorderStroke(0.8.dp, PrimaryBlue),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("slot_${date}_senior_car_all")
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = "У•О•В",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = emp0!!.fullName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("Весь день (3 смены)", fontSize = 8.sp, color = Color(0xFF2563EB))
            }
        }
    } else {
        // Раздельные смены: Утро, Обед, Вечер
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            post.slots.forEach { slot ->
                val assign = assignmentMap["${dateStr}_${post.id}_${slot.slotIndex}"]
                val emp = assign?.let { empMap[it.employeeId] }
                val prefix = when (slot.slotIndex) {
                    0 -> "У"
                    1 -> "О"
                    else -> "В"
                }
                val prefixBg = when (slot.slotIndex) {
                    0 -> Color(0xFF0284C7)
                    1 -> Color(0xFFD97706)
                    else -> Color(0xFF475569)
                }

                Surface(
                    onClick = { onSlotClick(date, post, slot) },
                    shape = RoundedCornerShape(3.dp),
                    color = if (emp != null) Color(0xFFF1F5F9) else Color(0xFFFAFAFA),
                    border = BorderStroke(0.5.dp, if (emp != null) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slot_${date}_${post.id}_${slot.slotIndex}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = prefixBg,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = prefix,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                        if (emp != null) {
                            Text(
                                text = emp.fullName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Navy800,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "+ ${slot.title}",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Отображение по дням в виде детальных карточек
 */
@Composable
fun ScheduleDayCardsView(
    days: List<LocalDate>,
    posts: List<DutyPost>,
    assignmentMap: Map<String, DutyAssignment>,
    empMap: Map<Long, Employee>,
    onSlotClick: (LocalDate, DutyPost, SlotDef) -> Unit
) {
    val ruLocale = Locale("ru", "RU")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(days, key = { it.toString() }) { date ->
            val dateStr = DutyRulesEngine.formatDate(date)
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, ruLocale)
            val isWeekend = date.dayOfWeek.value in 6..7

            // Считаем укомплектованность дня (всего слотов = 3 + 1 + 1 + 1 = 6)
            val totalSlots = posts.sumOf { it.slots.size }
            var filledSlots = 0
            posts.forEach { post ->
                post.slots.forEach { slot ->
                    if (assignmentMap["${dateStr}_${post.id}_${slot.slotIndex}"] != null) {
                        filledSlots++
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isWeekend) Color(0xFFFCA5A5) else SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Шапка дня
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ruLocale)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isWeekend) Color(0xFFDC2626) else Navy800
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "($dayOfWeek)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isWeekend) Color(0xFFDC2626) else Color(0xFF64748B)
                            )
                        }

                        // Бейдж укомплектованности
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (filledSlots == totalSlots) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "$filledSlots / $totalSlots",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filledSlots == totalSlots) Color(0xFF16A34A) else Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Посты дня
                    posts.forEach { post ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = post.shortName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.width(85.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                post.slots.forEach { slot ->
                                    val assignment = assignmentMap["${dateStr}_${post.id}_${slot.slotIndex}"]
                                    val emp = assignment?.let { empMap[it.employeeId] }

                                    Surface(
                                        onClick = { onSlotClick(date, post, slot) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (emp != null) Color(0xFFF8FAFC) else Color(0xFFFAFAFA),
                                        border = BorderStroke(1.dp, if (emp != null) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (emp != null) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = emp.fullName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Navy800
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    EmployeeTypeBadge(type = emp.type)

                                                    if (assignment?.note?.isNotBlank() == true) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "(${assignment.note})",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = "+ ${slot.title}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

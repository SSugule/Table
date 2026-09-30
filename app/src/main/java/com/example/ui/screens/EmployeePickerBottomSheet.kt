package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AvailabilityCategory
import com.example.domain.CandidateItem
import com.example.ui.components.AvailabilityBadge
import com.example.ui.components.EmployeeTypeBadge
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.viewmodel.SlotSelection
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeePickerBottomSheet(
    selection: SlotSelection,
    candidates: List<CandidateItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAssign: (employeeId: Long, note: String, selectedSlots: Set<Int>?) -> Unit,
    onClearSlot: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf(selection.currentNote) }

    val isSeniorCar = selection.post.id == "senior_car"

    // Для Старшего машины: выбор смен (утро, обед, вечер)
    var selectedSeniorCarSlots by remember(selection) {
        mutableStateOf(setOf(selection.slot.slotIndex))
    }

    val ruLocale = Locale("ru", "RU")
    val dayOfWeek = selection.date.dayOfWeek.getDisplayName(TextStyle.FULL, ruLocale)
    val monthName = selection.date.month.getDisplayName(TextStyle.FULL, ruLocale)
    val dateHeader = "${selection.date.dayOfMonth} $monthName, $dayOfWeek"

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Разделение кандидатов по категориям
    val workingCandidates = remember(candidates) {
        candidates.filter { it.category == AvailabilityCategory.AVAILABLE_WORKING }
    }
    val dayOffCandidates = remember(candidates) {
        candidates.filter { it.category == AvailabilityCategory.RESERVE_DAY_OFF }
    }
    val restDayCandidates = remember(candidates) {
        candidates.filter { it.category == AvailabilityCategory.RESERVE_REST_DAY }
    }

    // Состояния для отображения резерва / нарядов вне очереди (наказания)
    var showDayOffCandidates by remember { mutableStateOf(false) }
    var showRestDayCandidates by remember { mutableStateOf(false) }

    val visibleCandidates = remember(candidates, isSeniorCar, showDayOffCandidates, showRestDayCandidates) {
        if (isSeniorCar) {
            candidates
        } else {
            val list = mutableListOf<CandidateItem>()
            list.addAll(workingCandidates)
            if (showDayOffCandidates) {
                list.addAll(dayOffCandidates)
            }
            if (showRestDayCandidates) {
                list.addAll(restDayCandidates)
            }
            list
        }
    }

    val filteredCandidates = remember(visibleCandidates, searchQuery) {
        if (searchQuery.isBlank()) {
            visibleCandidates
        } else {
            visibleCandidates.filter { it.employee.fullName.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Заголовок слота
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selection.post.name} • ${selection.slot.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSeniorCar) {
                            "$dateHeader • Рабочий день (можно в отсыпной)"
                        } else {
                            "$dateHeader  (${selection.slot.allowedType.description})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_picker_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Панель мульти-смен для Старшего машины
            if (isSeniorCar) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Назначить на смены (1, 2 или все 3 раза):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Navy800
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "Утро",
                                1 to "Обед",
                                2 to "Вечер"
                            ).forEach { (idx, title) ->
                                val isChecked = selectedSeniorCarSlots.contains(idx)
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        selectedSeniorCarSlots = if (isChecked) {
                                            if (selectedSeniorCarSlots.size > 1) {
                                                selectedSeniorCarSlots - idx
                                            } else {
                                                selectedSeniorCarSlots // не оставляем пустым
                                            }
                                        } else {
                                            selectedSeniorCarSlots + idx
                                        }
                                    },
                                    label = { Text(title, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlue,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("chip_shift_$idx")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.clickable {
                                    selectedSeniorCarSlots = setOf(0, 1, 2)
                                }
                            ) {
                                Text(
                                    text = "Все 3 (Утро+Обед+Вечер)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Navy800,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.clickable {
                                    selectedSeniorCarSlots = setOf(selection.slot.slotIndex)
                                }
                            ) {
                                Text(
                                    text = "Только ${selection.slot.title}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Navy800,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Поле поиска и кнопка снять с поста
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по фамилии...") },
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
                        .weight(1f)
                        .testTag("candidate_search_input")
                )

                if (selection.currentEmployeeId != null) {
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onClearSlot()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("clear_slot_button")
                    ) {
                        Text("Снять")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Красное поле: кнопки отображения людей с выходного и отсыпного (наказания / вне очереди)
            if (!isSeniorCar) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reserve_red_panel")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Резерв и наряды вне очереди (наказания)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Для назначения людей при нехватке или за невыход на работу (наряды вне очереди):",
                            fontSize = 11.sp,
                            color = Color(0xFF7F1D1D)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Кнопка 1: Отобразить людей с выходного
                            OutlinedButton(
                                onClick = { showDayOffCandidates = !showDayOffCandidates },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (showDayOffCandidates) Color(0xFFD97706) else Color.White,
                                    contentColor = if (showDayOffCandidates) Color.White else Color(0xFF92400E)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFD97706)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_day_off_btn")
                            ) {
                                Text(
                                    text = if (showDayOffCandidates) "✓ Скрыть выходных" else "+ С выходного (${dayOffCandidates.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            // Кнопка 2: Добавить людей с отсыпного (наряд вне очереди)
                            OutlinedButton(
                                onClick = { showRestDayCandidates = !showRestDayCandidates },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (showRestDayCandidates) Color(0xFFDC2626) else Color.White,
                                    contentColor = if (showRestDayCandidates) Color.White else Color(0xFF991B1B)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_rest_day_btn")
                            ) {
                                Text(
                                    text = if (showRestDayCandidates) "✓ Скрыть отсыпных" else "+ С отсыпного (${restDayCandidates.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Список кандидатов
            Text(
                text = if (isSeniorCar) {
                    "Доступные сотрудники (приоритет: Дежурные):"
                } else {
                    "Доступные кандидаты (по правилам отдыха):"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredCandidates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет доступных сотрудников для данного поста",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(filteredCandidates, key = { it.employee.id }) { item ->
                    val isCurrent = item.employee.id == selection.currentEmployeeId
                    val isAvailable = item.category == AvailabilityCategory.AVAILABLE_WORKING
                    val isReserve = item.category == AvailabilityCategory.RESERVE_DAY_OFF ||
                            item.category == AvailabilityCategory.RESERVE_REST_DAY

                    CandidateCard(
                        candidate = item,
                        isSelected = isCurrent,
                        isAvailable = isAvailable,
                        isReserve = isReserve,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            val slotsToAssign = if (isSeniorCar) selectedSeniorCarSlots else null
                            onAssign(item.employee.id, noteText, slotsToAssign)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CandidateCard(
    candidate: CandidateItem,
    isSelected: Boolean,
    isAvailable: Boolean,
    isReserve: Boolean,
    onClick: () -> Unit
) {
    val isRestDayPenalty = candidate.category == AvailabilityCategory.RESERVE_REST_DAY

    val cardBg = when {
        isSelected -> Color(0xFFEFF6FF)
        isRestDayPenalty -> Color(0xFFFEF2F2)
        isAvailable -> Color.White
        isReserve -> Color(0xFFFFFBEB)
        else -> Color(0xFFF1F5F9)
    }

    val borderColor = when {
        isSelected -> PrimaryBlue
        isRestDayPenalty -> Color(0xFFF87171)
        isAvailable -> Color(0xFFCBD5E1)
        isReserve -> Color(0xFFFCD34D)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("candidate_card_${candidate.employee.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.employee.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Navy800
                    )

                    if (!candidate.employee.priorityPostId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF3E8FF),
                            border = BorderStroke(0.5.dp, Color(0xFFC084FC))
                        ) {
                            Text(
                                text = "Приоритет",
                                color = Color(0xFF7E22CE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Выбран",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmployeeTypeBadge(type = candidate.employee.type)
                    AvailabilityBadge(category = candidate.category)

                    if (candidate.totalDutiesInMonth > 0) {
                        Text(
                            text = "${candidate.totalDutiesInMonth} смен(ы)",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (candidate.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = candidate.note,
                            fontSize = 11.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Дней отдыха
            candidate.statusInfo.daysSinceLastDuty?.let { days ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Отдыхал: $days дн.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

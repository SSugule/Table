package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DutyAssignment
import com.example.data.model.Employee
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLightBg
import com.example.ui.viewmodel.DutyViewModel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ExportScreen(
    viewModel: DutyViewModel,
    selectedMonth: YearMonth,
    assignments: List<DutyAssignment>,
    employees: List<Employee>,
    isExporting: Boolean
) {
    val context = LocalContext.current
    val ruLocale = Locale("ru", "RU")

    val highlightChanges by viewModel.highlightChangesInExport.collectAsState()
    val baselineKeys by viewModel.baselineAssignmentKeys.collectAsState()

    var rangeMode by remember { mutableIntStateOf(0) } // 0: Весь месяц, 1: Конкретные даты (24-30)
    var customStartDay by remember { mutableIntStateOf(1) }
    var customEndDay by remember { mutableIntStateOf(7) }

    val monthName = selectedMonth.month.getDisplayName(TextStyle.FULL, ruLocale)

    val startDate = remember(selectedMonth, rangeMode, customStartDay) {
        if (rangeMode == 0) {
            selectedMonth.atDay(1)
        } else {
            val validStart = customStartDay.coerceIn(1, selectedMonth.lengthOfMonth())
            selectedMonth.atDay(validStart)
        }
    }

    val endDate = remember(selectedMonth, rangeMode, customEndDay) {
        if (rangeMode == 0) {
            selectedMonth.atEndOfMonth()
        } else {
            val validEnd = customEndDay.coerceIn(customStartDay, selectedMonth.lengthOfMonth())
            selectedMonth.atDay(validEnd)
        }
    }

    val inRangeAssignmentsCount = remember(assignments, startDate, endDate) {
        val startStr = startDate.toString()
        val endStr = endDate.toString()
        assignments.count { it.dateString in startStr..endStr }
    }

    // Подсчет количества измененных записей по сравнению с базовым снимком
    val changedAssignmentsCount = remember(assignments, baselineKeys) {
        if (baselineKeys.isEmpty()) 0
        else {
            assignments.count {
                val key = "${it.dateString}_${it.postId}_${it.slotIndex}_${it.employeeId}"
                !baselineKeys.contains(key)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateLightBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Карточка превью и настроек периода
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Экспорт официального графика",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Navy800
                            )
                            Text(
                                text = "Период: с ${startDate.dayOfMonth} по ${endDate.dayOfMonth} $monthName ${selectedMonth.year}г.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Диапазон выгрузки:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Navy800
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { rangeMode = 0 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (rangeMode == 0) PrimaryBlue else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Весь месяц (1-${selectedMonth.lengthOfMonth()})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (rangeMode == 0) Color.White else Navy800,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                            )
                        }

                        Surface(
                            onClick = { rangeMode = 1 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (rangeMode == 1) PrimaryBlue else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Диапазон дней",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (rangeMode == 1) Color.White else Navy800,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                            )
                        }
                    }

                    if (rangeMode == 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customStartDay.toString(),
                                onValueChange = { customStartDay = it.toIntOrNull() ?: 1 },
                                label = { Text("С какого дня") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = customEndDay.toString(),
                                onValueChange = { customEndDay = it.toIntOrNull() ?: selectedMonth.lengthOfMonth() },
                                label = { Text("По какой день") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Заполненных назначений за период: $inRangeAssignmentsCount",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        item {
            // Переключатель: отображать изменения в графике или нет
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Highlight, contentDescription = null, tint = Color(0xFFD97706))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Отобразить изменения в графике",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Navy800
                            )
                            Text(
                                text = if (changedAssignmentsCount > 0)
                                    "Обнаружено изменений: $changedAssignmentsCount (будут выделены оранжевым)"
                                else
                                    "Подсвечивать отредактированные ячейки оранжевым цветом",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Switch(
                        checked = highlightChanges,
                        onCheckedChange = { viewModel.setHighlightChangesInExport(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD97706)
                        ),
                        modifier = Modifier.testTag("toggle_highlight_changes")
                    )
                }
            }
        }

        item {
            // Секция экспорта в PDF: Скачивание + Отправка
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Экспорт в PDF (Документ А4)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Navy800
                            )
                            Text(
                                text = "Готовый документ с шапкой, таблицей и строками для подписей",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка скачать PDF в память устройства
                        OutlinedButton(
                            onClick = { viewModel.downloadScheduleAsPdf(context, startDate, endDate, highlightChanges) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_pdf_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Скачать", fontSize = 13.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                        }

                        // Кнопка отправить PDF в мессенджер
                        Button(
                            onClick = { viewModel.exportScheduleAsPdf(context, startDate, endDate, highlightChanges) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("export_pdf_button")
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Отправить", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            // Секция экспорта в PNG Картинку: Скачивание + Отправка
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF16A34A))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Экспорт в Картинку (PNG)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Navy800
                            )
                            Text(
                                text = "Четкое изображение высокого разрешения (таблица графика)",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка скачать картинку в память
                        OutlinedButton(
                            onClick = { viewModel.downloadScheduleAsImage(context, startDate, endDate, highlightChanges) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF0D9488)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_image_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0D9488))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Скачать", fontSize = 13.sp, color = Color(0xFF0D9488), fontWeight = FontWeight.SemiBold)
                        }

                        // Кнопка отправить картинку в мессенджер
                        Button(
                            onClick = { viewModel.exportScheduleAsImage(context, startDate, endDate, highlightChanges) },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("export_image_button")
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Отправить", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

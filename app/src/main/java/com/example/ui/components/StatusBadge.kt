package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import com.example.domain.AvailabilityCategory
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusDayOff
import com.example.ui.theme.StatusDayOffBg
import com.example.ui.theme.StatusRest
import com.example.ui.theme.StatusRestBg
import com.example.ui.theme.StatusSick
import com.example.ui.theme.StatusSickBg
import com.example.ui.theme.StatusVacation
import com.example.ui.theme.StatusVacationBg
import com.example.ui.theme.StatusWorking
import com.example.ui.theme.StatusWorkingBg

@Composable
fun EmployeeTypeBadge(type: EmployeeType, modifier: Modifier = Modifier) {
    val (bgColor, textColor, text) = when (type) {
        EmployeeType.DUTY -> Triple(PrimaryBlueLight, PrimaryBlue, "Дежурный")
        EmployeeType.ASSISTANT -> Triple(Color(0xFFE0F2FE), SecondaryTeal, "Помощник")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AvailabilityBadge(category: AvailabilityCategory, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (category) {
        AvailabilityCategory.AVAILABLE_WORKING -> Triple(StatusWorkingBg, StatusWorking, "На рабочем дне")
        AvailabilityCategory.RESERVE_DAY_OFF -> Triple(StatusDayOffBg, StatusDayOff, "На выходном (Резерв)")
        AvailabilityCategory.RESERVE_REST_DAY -> Triple(StatusRestBg, StatusRest, "На отсыпном (Резерв)")
        AvailabilityCategory.ALREADY_ASSIGNED_TODAY -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Занят сегодня")
        AvailabilityCategory.ON_VACATION -> Triple(StatusVacationBg, StatusVacation, "В отпуске")
        AvailabilityCategory.ON_SICK_LEAVE -> Triple(StatusSickBg, StatusSick, "На больничном")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusDisplayBadge(status: EmployeeStatus, formattedText: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        EmployeeStatus.WORKING -> Pair(StatusWorkingBg, StatusWorking)
        EmployeeStatus.POST_DUTY_REST -> Pair(StatusRestBg, StatusRest)
        EmployeeStatus.DAY_OFF -> Pair(StatusDayOffBg, StatusDayOff)
        EmployeeStatus.VACATION -> Pair(StatusVacationBg, StatusVacation)
        EmployeeStatus.SICK_LEAVE -> Pair(StatusSickBg, StatusSick)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = formattedText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

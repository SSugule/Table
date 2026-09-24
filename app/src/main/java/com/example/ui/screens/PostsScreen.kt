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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DutyPost
import com.example.data.model.SlotAllowedType
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateLightBg

@Composable
fun PostsScreen() {
    val posts = DutyPost.ALL_POSTS

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateLightBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Информационный баннер о правилах нарядов
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Правила суточных нарядов и дежурств",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Navy800
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Все наряды суточные.\n" +
                                    "• После суток: День 1 — Отсыпной, День 2 — Выходной.\n" +
                                    "• На День 3 статус переходит в «На рабочем дне» (готовность к новому наряду).\n" +
                                    "• Если доступных не хватает: сначала привлекаются те, у кого был выходной, затем те, кто на отсыпном.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Рабочие места (Посты):",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Navy800
            )
        }

        items(posts, key = { it.id }) { post ->
            val icon: ImageVector = when (post.id) {
                "kpp1" -> Icons.Default.MeetingRoom
                "kpp2" -> Icons.Default.Security
                "senior_car" -> Icons.Default.DirectionsCar
                "vg2" -> Icons.Default.Shield
                else -> Icons.Default.Badge
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = post.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Navy800
                            )
                            Text(
                                text = post.description,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Состав наряда (${post.slots.size} чел.):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        post.slots.forEach { slot ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = slot.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Navy800
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (slot.allowedType) {
                                            SlotAllowedType.DUTY_ONLY -> Color(0xFFDBEAFE)
                                            SlotAllowedType.ASSISTANT_ONLY -> Color(0xFFE0F2FE)
                                            SlotAllowedType.ANY_WITH_DUTY_PRIORITY -> Color(0xFFFEF3C7)
                                        }
                                    ) {
                                        Text(
                                            text = slot.allowedType.description,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (slot.allowedType) {
                                                SlotAllowedType.DUTY_ONLY -> PrimaryBlue
                                                SlotAllowedType.ASSISTANT_ONLY -> Color(0xFF0D9488)
                                                SlotAllowedType.ANY_WITH_DUTY_PRIORITY -> Color(0xFFB45309)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

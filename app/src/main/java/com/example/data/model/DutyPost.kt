package com.example.data.model

enum class SlotAllowedType(val description: String) {
    DUTY_ONLY("Только Дежурный"),
    ASSISTANT_ONLY("Только Помощник"),
    ANY_WITH_DUTY_PRIORITY("Дежурный или Помощник (приоритет: Дежурный)")
}

data class SlotDef(
    val slotIndex: Int,
    val title: String,
    val allowedType: SlotAllowedType
)

data class DutyPost(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val order: Int,
    val is24HourDuty: Boolean = true, // true для суточных нарядов (КПП1, КПП2, ВГ2), false для рабочего дня (Старший машины)
    val slots: List<SlotDef>
) {
    companion object {
        val POST_KPP1 = DutyPost(
            id = "kpp1",
            name = "КПП1",
            shortName = "КПП-1",
            description = "1 дежурный и 2 помощника (Суточный наряд)",
            order = 1,
            is24HourDuty = true,
            slots = listOf(
                SlotDef(0, "Дежурный КПП-1", SlotAllowedType.DUTY_ONLY),
                SlotDef(1, "Помощник 1", SlotAllowedType.ASSISTANT_ONLY),
                SlotDef(2, "Помощник 2", SlotAllowedType.ASSISTANT_ONLY)
            )
        )

        val POST_KPP2 = DutyPost(
            id = "kpp2",
            name = "КПП2",
            shortName = "КПП-2",
            description = "1 дежурный (Суточный наряд)",
            order = 2,
            is24HourDuty = true,
            slots = listOf(
                SlotDef(0, "Дежурный КПП-2", SlotAllowedType.DUTY_ONLY)
            )
        )

        val POST_SENIOR_CAR = DutyPost(
            id = "senior_car",
            name = "Старший машины",
            shortName = "Ст. машины",
            description = "Рабочий день (можно в отсыпной: Утро / Обед / Вечер)",
            order = 3,
            is24HourDuty = false,
            slots = listOf(
                SlotDef(0, "Утро", SlotAllowedType.ANY_WITH_DUTY_PRIORITY),
                SlotDef(1, "Обед", SlotAllowedType.ANY_WITH_DUTY_PRIORITY),
                SlotDef(2, "Вечер", SlotAllowedType.ANY_WITH_DUTY_PRIORITY)
            )
        )

        val POST_VG2 = DutyPost(
            id = "vg2",
            name = "ВГ2",
            shortName = "ВГ-2",
            description = "1 дежурный (Суточный наряд)",
            order = 4,
            is24HourDuty = true,
            slots = listOf(
                SlotDef(0, "Дежурный ВГ-2", SlotAllowedType.DUTY_ONLY)
            )
        )

        val ALL_POSTS = listOf(
            POST_KPP1,
            POST_KPP2,
            POST_SENIOR_CAR,
            POST_VG2
        )

        fun findById(postId: String): DutyPost? {
            return ALL_POSTS.find { it.id == postId }
        }
    }
}

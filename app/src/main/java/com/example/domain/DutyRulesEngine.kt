package com.example.domain

import com.example.data.model.DutyAssignment
import com.example.data.model.DutyPost
import com.example.data.model.Employee
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import com.example.data.model.SlotAllowedType
import com.example.data.model.SlotDef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class AvailabilityCategory(val label: String, val order: Int) {
    AVAILABLE_WORKING("На рабочем дне (Доступен)", 1),
    RESERVE_DAY_OFF("На выходном (Резерв 1)", 2),
    RESERVE_REST_DAY("На отсыпном (Резерв 2)", 3),
    ALREADY_ASSIGNED_TODAY("Уже задействован сегодня", 4),
    ON_VACATION("В отпуске", 5),
    ON_SICK_LEAVE("На больничном", 6)
}

data class EmployeeStatusInfo(
    val status: EmployeeStatus,
    val displayStatus: String,
    val daysSinceLastDuty: Int?, // Сколько дней назад заступал на СУТОЧНЫЙ наряд
    val lastDutyDate: LocalDate?
)

data class CandidateItem(
    val employee: Employee,
    val category: AvailabilityCategory,
    val statusInfo: EmployeeStatusInfo,
    val isAllowedByRole: Boolean,
    val note: String = "",
    val totalDutiesInMonth: Int = 0
)

data class ScheduleIssue(
    val date: LocalDate,
    val employeeName: String,
    val message: String,
    val severity: IssueSeverity
)

enum class IssueSeverity {
    CRITICAL, // Дважды на сутках в один день или в отпуске/на больничном
    WARNING   // Заступил на сутки без отсыпного (2 суток подряд)
}

object DutyRulesEngine {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, dateFormatter)
    }

    fun formatDate(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    /**
     * Гибкий парсер дат (поддерживает yyyy-MM-dd, dd.MM.yyyy, dd.MM.yy, d.M.yy и т.д.)
     */
    fun tryParseFlexibleDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        // yyyy-MM-dd
        try {
            return LocalDate.parse(trimmed, dateFormatter)
        } catch (_: Exception) {}
        // dd.MM.yyyy
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {}
        // dd.MM.yy
        try {
            val parts = trimmed.split(".", "/", "-")
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                var y = parts[2].toIntOrNull()
                if (d != null && m != null && y != null) {
                    if (y < 100) y += 2000
                    return LocalDate.of(y, m, d)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Проверяет, находится ли сотрудник в отпуске на указанную дату
     * (учитывает как текущий статус "В отпуске", так и вторую графу "Ближайший отпуск").
     */
    fun isEmployeeOnVacation(employee: Employee, targetDate: LocalDate): Boolean {
        // 1. Ручной текущий статус: В отпуске
        if (employee.manualStatus == EmployeeStatus.VACATION) {
            val untilDate = tryParseFlexibleDate(employee.statusUntilDate)
            if (untilDate == null || !targetDate.isAfter(untilDate)) {
                return true
            }
        }

        // 2. Ближайший запланированный отпуск (графа «Ближайший»)
        val start = tryParseFlexibleDate(employee.upcomingVacationStart)
        val end = tryParseFlexibleDate(employee.upcomingVacationEnd)
        if (start != null && end != null) {
            if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
                return true
            }
        } else if (start != null && end == null) {
            if (!targetDate.isBefore(start)) {
                return true
            }
        } else if (start == null && end != null) {
            if (!targetDate.isAfter(end)) {
                return true
            }
        }

        return false
    }

    /**
     * Проверяет, находится ли сотрудник на больничном на указанную дату
     */
    fun isEmployeeOnSickLeave(employee: Employee, targetDate: LocalDate): Boolean {
        if (employee.manualStatus == EmployeeStatus.SICK_LEAVE) {
            val untilDate = tryParseFlexibleDate(employee.statusUntilDate)
            if (untilDate == null || !targetDate.isAfter(untilDate)) {
                return true
            }
        }
        return false
    }

    /**
     * Вычисляет статус сотрудника на указанную дату.
     * ВНИМАНИЕ: Только СУТОЧНЫЕ наряды (КПП1, КПП2, ВГ2) порождают суточный цикл:
     * Сутки -> Отсыпной (1 день) -> Выходной (2 день) -> Рабочий день.
     * Пост «Старший машины» — это обычный рабочий день и отсыпной после себя не порождает.
     */
    fun calculateStatusOnDate(
        employee: Employee,
        targetDate: LocalDate,
        allAssignments: List<DutyAssignment>
    ): EmployeeStatusInfo {
        // 1. Проверка отпуска (текущий или ближайший на эту дату)
        if (isEmployeeOnVacation(employee, targetDate)) {
            val text = when {
                !employee.upcomingVacationEnd.isNullOrBlank() -> "В отпуске\nдо ${employee.upcomingVacationEnd}"
                !employee.statusUntilDate.isNullOrBlank() -> "В отпуске\nдо ${employee.statusUntilDate}"
                else -> "В отпуске"
            }
            return EmployeeStatusInfo(
                status = EmployeeStatus.VACATION,
                displayStatus = text,
                daysSinceLastDuty = null,
                lastDutyDate = null
            )
        }

        // 2. Проверка больничного
        if (isEmployeeOnSickLeave(employee, targetDate)) {
            val text = if (!employee.statusUntilDate.isNullOrBlank()) "На больничном\nдо ${employee.statusUntilDate}" else "На больничном"
            return EmployeeStatusInfo(
                status = EmployeeStatus.SICK_LEAVE,
                displayStatus = text,
                daysSinceLastDuty = null,
                lastDutyDate = null
            )
        }

        // 2. Поиск последнего СУТОЧНОГО наряда до targetDate (Старший машины не суточный!)
        val empDutiesBefore = allAssignments
            .filter { it.employeeId == employee.id }
            .filter { assignment ->
                val post = DutyPost.findById(assignment.postId)
                post == null || post.is24HourDuty
            }
            .mapNotNull {
                try {
                    parseDate(it.dateString)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it.isBefore(targetDate) }
            .sortedDescending()

        val lastDuty = empDutiesBefore.firstOrNull()

        if (lastDuty == null) {
            return EmployeeStatusInfo(
                status = EmployeeStatus.WORKING,
                displayStatus = "На рабочем дне",
                daysSinceLastDuty = null,
                lastDutyDate = null
            )
        }

        val daysBetween = ChronoUnit.DAYS.between(lastDuty, targetDate).toInt()
        return when (daysBetween) {
            1 -> EmployeeStatusInfo(
                status = EmployeeStatus.POST_DUTY_REST,
                displayStatus = "На отсыпном",
                daysSinceLastDuty = 1,
                lastDutyDate = lastDuty
            )
            2 -> EmployeeStatusInfo(
                status = EmployeeStatus.DAY_OFF,
                displayStatus = "На выходном",
                daysSinceLastDuty = 2,
                lastDutyDate = lastDuty
            )
            else -> EmployeeStatusInfo(
                status = EmployeeStatus.WORKING,
                displayStatus = "На рабочем дне",
                daysSinceLastDuty = daysBetween,
                lastDutyDate = lastDuty
            )
        }
    }

    /**
     * Формирует список кандидатов для слота на заданную дату.
     * ПРАВИЛО: «Не отображай людей в выпадающем списке которых нельзя задействовать».
     *
     * Строго исключаются:
     * - Люди в отпуске или на больничном
     * - Люди с неподходящей ролью (например, помощник для поста дежурного)
     * - Люди, уже задействованные сегодня на суточном посту
     *
     * Для суточных постов:
     * - Отображаются доступные «На рабочем дне».
     * - Если их не хватает, подключаются «На выходном».
     * - Если и их не хватает, подключаются «На отсыпном».
     *
     * Для поста «Старший машины» (рабочий день):
     * - Можно ставить людей даже в отсыпной!
     * - Можно ставить одного человека на утро, обед и вечер (один, два или все три раза).
     * - Первыми отображаются дежурные (приоритет), затем помощники.
     */
    fun getRankedCandidates(
        targetDate: LocalDate,
        post: DutyPost,
        slot: SlotDef,
        currentEmployeeId: Long?,
        employees: List<Employee>,
        allAssignments: List<DutyAssignment>
    ): List<CandidateItem> {
        val targetDateStr = formatDate(targetDate)

        // Все назначения на сегодня
        val todayAssignments = allAssignments.filter { it.dateString == targetDateStr }

        // Назначения сегодня на СУТОЧНЫЕ посты (КПП1, КПП2, ВГ2)
        val assignedTo24hToday = todayAssignments
            .filter { assignment ->
                val p = DutyPost.findById(assignment.postId)
                p == null || p.is24HourDuty
            }
            .map { it.employeeId }
            .toSet()

        // Назначения сегодня на этот же пост и слот (текущий редактируемый)
        val currentlyInThisSlot = currentEmployeeId

        // Месяц для подсчета нагрузки (для честного распределения)
        val startOfMonth = targetDate.withDayOfMonth(1)
        val endOfMonth = targetDate.withDayOfMonth(targetDate.lengthOfMonth())
        val startMonthStr = formatDate(startOfMonth)
        val endMonthStr = formatDate(endOfMonth)

        val monthDutyCounts = allAssignments
            .filter { it.dateString in startMonthStr..endMonthStr }
            .groupingBy { it.employeeId }
            .eachCount()

        val isSeniorCar = !post.is24HourDuty

        // 1. Проверяем каждого сотрудника и отсекаем тех, кого НЕЛЬЗЯ задействовать
        val candidateItems = mutableListOf<CandidateItem>()

        for (emp in employees) {
            // А. Ручной отпуск, ближайший отпуск или больничный -> НЕЛЬЗЯ задействовать!
            if (isEmployeeOnVacation(emp, targetDate) || isEmployeeOnSickLeave(emp, targetDate)) {
                continue
            }

            // Б. Соответствие должности для слота -> если не подходит, НЕЛЬЗЯ задействовать!
            val isAllowedRole = when (slot.allowedType) {
                SlotAllowedType.DUTY_ONLY -> emp.type == EmployeeType.DUTY
                SlotAllowedType.ASSISTANT_ONLY -> emp.type == EmployeeType.ASSISTANT
                SlotAllowedType.ANY_WITH_DUTY_PRIORITY -> true
            }
            if (!isAllowedRole) {
                continue
            }

            // В. Приоритет постов для дежурных:
            // «Для дежурных, в настройках сотрудника добавить кнопки приоритета по постам,
            // например Джумагазиев приоритет заступления ВГ2, на остальные посты его не отображать до тех пор, пока приоритет установлен.»
            if (emp.type == EmployeeType.DUTY && !emp.priorityPostId.isNullOrBlank()) {
                if (emp.priorityPostId != post.id) {
                    continue
                }
            }

            // Г. Если сотрудник СЕГОДНЯ уже стоит на СУТОЧНОМ наряде (КПП1, КПП2, ВГ2) ->
            // он не может заступать ни на другой суточный пост, ни старшим машины!
            if (assignedTo24hToday.contains(emp.id)) {
                // Исключение: если он уже назначен именно в ЭТОТ слот (при редактировании)
                val isInThisExactSlot = todayAssignments.any {
                    it.postId == post.id && it.slotIndex == slot.slotIndex && it.employeeId == emp.id
                }
                if (!isInThisExactSlot) {
                    continue
                }
            }

            // Д. Если текущий пост — СУТОЧНЫЙ наряд:
            if (post.is24HourDuty) {
                // Если сотрудник сегодня уже заступил старшим машины -> нельзя ставить на сутки
                val isSeniorCarToday = todayAssignments.any {
                    it.postId == "senior_car" && it.employeeId == emp.id
                }
                if (isSeniorCarToday) {
                    continue
                }
            }

            // Статус сотрудника на сегодня (на основе предыдущих суток)
            val statusInfo = calculateStatusOnDate(emp, targetDate, allAssignments)

            // Категория доступности
            val category = when {
                statusInfo.status == EmployeeStatus.WORKING -> AvailabilityCategory.AVAILABLE_WORKING
                statusInfo.status == EmployeeStatus.DAY_OFF -> AvailabilityCategory.RESERVE_DAY_OFF
                statusInfo.status == EmployeeStatus.POST_DUTY_REST -> {
                    if (isSeniorCar) {
                        // Для старшего машины: в отсыпной можно ставить напрямую!
                        AvailabilityCategory.AVAILABLE_WORKING
                    } else {
                        AvailabilityCategory.RESERVE_REST_DAY
                    }
                }
                else -> AvailabilityCategory.AVAILABLE_WORKING
            }

            val note = when {
                emp.type == EmployeeType.DUTY && emp.priorityPostId == post.id -> "Приоритет на данный пост"
                isSeniorCar && statusInfo.status == EmployeeStatus.POST_DUTY_REST -> "Отсыпной (разрешено для Ст. машины)"
                statusInfo.status == EmployeeStatus.DAY_OFF -> "Выходной после наряда"
                statusInfo.status == EmployeeStatus.POST_DUTY_REST -> "Отсыпной после суток"
                else -> ""
            }

            candidateItems.add(
                CandidateItem(
                    employee = emp,
                    category = category,
                    statusInfo = statusInfo,
                    isAllowedByRole = true,
                    note = note,
                    totalDutiesInMonth = monthDutyCounts[emp.id] ?: 0
                )
            )
        }

        // 2. Сортировка кандидатов:
        // - Приоритетный кандидат для этого поста (если установлен приоритет)
        // - Приоритет должности для «Старший машины» (дежурные первые)
        // - Порядок категории: На рабочем дне -> На выходном -> На отсыпном
        // - Равномерность нагрузки
        // - По алфавиту
        return candidateItems.sortedWith(
            compareBy<CandidateItem> {
                // Приоритет постов для дежурных: если установлен приоритет именно на этот пост, он идет ПЕРВЫМ!
                if (it.employee.type == EmployeeType.DUTY && it.employee.priorityPostId == post.id) 0 else 1
            }.thenBy {
                // Приоритет должности для "Старший машины": Дежурные первые!
                if (slot.allowedType == SlotAllowedType.ANY_WITH_DUTY_PRIORITY) {
                    if (it.employee.type == EmployeeType.DUTY) 0 else 1
                } else 0
            }.thenBy {
                // Сначала "На рабочем дне", затем резерв выходной, затем резерв отсыпной
                it.category.order
            }.thenBy {
                // Равномерное распределение нагрузки
                it.totalDutiesInMonth
            }.thenBy {
                it.employee.fullName
            }
        )
    }

    /**
     * Интеллектуальное автозаполнение графика на выбранный период с соблюдением
     * всех правил суточных нарядов и рабочих смен старшего машины.
     */
    fun autoScheduleRange(
        startDate: LocalDate,
        endDate: LocalDate,
        posts: List<DutyPost>,
        employees: List<Employee>,
        existingAssignments: List<DutyAssignment>,
        overwriteExisting: Boolean = false
    ): List<DutyAssignment> {
        val result = if (overwriteExisting) {
            val startStr = formatDate(startDate)
            val endStr = formatDate(endDate)
            existingAssignments.filterNot { it.dateString in startStr..endStr }.toMutableList()
        } else {
            existingAssignments.toMutableList()
        }

        var curDate = startDate
        while (!curDate.isAfter(endDate)) {
            val dateStr = formatDate(curDate)

            // Сначала заполняем суточные посты (КПП1, КПП2, ВГ2), затем Старшего машины
            val sortedPosts = posts.sortedByDescending { it.is24HourDuty }

            for (post in sortedPosts) {
                for (slot in post.slots) {
                    val existing = result.find {
                        it.dateString == dateStr && it.postId == post.id && it.slotIndex == slot.slotIndex
                    }
                    if (existing != null && !overwriteExisting) {
                        continue
                    }

                    val candidates = getRankedCandidates(
                        targetDate = curDate,
                        post = post,
                        slot = slot,
                        currentEmployeeId = null,
                        employees = employees,
                        allAssignments = result
                    )

                    // В авторежиме выбираем лучшего кандидата:
                    // 1. Рабочий день (с приоритетом по посту во главе)
                    // 2. Если нет — резерв 1 (выходной)
                    // 3. Если нет — резерв 2 (отсыпной)
                    val bestCandidate = if (post.is24HourDuty) {
                        candidates.firstOrNull { it.category == AvailabilityCategory.AVAILABLE_WORKING }
                            ?: candidates.firstOrNull { it.category == AvailabilityCategory.RESERVE_DAY_OFF }
                            ?: candidates.firstOrNull { it.category == AvailabilityCategory.RESERVE_REST_DAY }
                    } else {
                        candidates.firstOrNull()
                    }

                    if (bestCandidate != null) {
                        result.removeAll {
                            it.dateString == dateStr && it.postId == post.id && it.slotIndex == slot.slotIndex
                        }
                        result.add(
                            DutyAssignment(
                                dateString = dateStr,
                                postId = post.id,
                                slotIndex = slot.slotIndex,
                                employeeId = bestCandidate.employee.id
                            )
                        )
                    }
                }
            }

            curDate = curDate.plusDays(1)
        }

        return result
    }

    /**
     * Аудит графика на предмет нарушений:
     * - СУТОЧНЫЙ наряд 2 дня подряд (двое суток подряд без отдыха)
     * - Назначение сотрудника во время отпуска или больничного
     * - Одновременное назначение на два разных СУТОЧНЫХ поста в один день
     */
    fun auditSchedule(
        startDate: LocalDate,
        endDate: LocalDate,
        assignments: List<DutyAssignment>,
        employees: List<Employee>
    ): List<ScheduleIssue> {
        val issues = mutableListOf<ScheduleIssue>()
        val empMap = employees.associateBy { it.id }

        val startStr = formatDate(startDate)
        val endStr = formatDate(endDate)
        val inRangeAssignments = assignments.filter { it.dateString in startStr..endStr }

        // 1. Проверка на отпуск и больничный
        inRangeAssignments.forEach { assign ->
            val emp = empMap[assign.employeeId]
            if (emp != null) {
                val assignDate = parseDate(assign.dateString)
                if (isEmployeeOnVacation(emp, assignDate)) {
                    val vacDetail = if (!emp.upcomingVacationEnd.isNullOrBlank()) {
                        "до ${emp.upcomingVacationEnd}"
                    } else {
                        emp.getFormattedStatusText(EmployeeStatus.VACATION)
                    }
                    issues.add(
                        ScheduleIssue(
                            date = assignDate,
                            employeeName = emp.fullName,
                            message = "Назначен на наряд во время отпуска ($vacDetail)",
                            severity = IssueSeverity.CRITICAL
                        )
                    )
                } else if (isEmployeeOnSickLeave(emp, assignDate)) {
                    issues.add(
                        ScheduleIssue(
                            date = assignDate,
                            employeeName = emp.fullName,
                            message = "Назначен на наряд во время больничного (${emp.getFormattedStatusText(EmployeeStatus.SICK_LEAVE)})",
                            severity = IssueSeverity.CRITICAL
                        )
                    )
                }
            }
        }

        // 2. Проверка на дублирование СУТОЧНЫХ постов в один день
        val groupedByDate = inRangeAssignments.groupBy { it.dateString }
        groupedByDate.forEach { (dateStr, list) ->
            val date = parseDate(dateStr)
            val duties24h = list.filter {
                val post = DutyPost.findById(it.postId)
                post == null || post.is24HourDuty
            }
            val emp24hCounts = duties24h.groupingBy { it.employeeId }.eachCount()
            emp24hCounts.filter { it.value > 1 }.forEach { (empId, count) ->
                val name = empMap[empId]?.fullName ?: "Сотрудник #$empId"
                issues.add(
                    ScheduleIssue(
                        date = date,
                        employeeName = name,
                        message = "Назначен на $count суточных поста в один день!",
                        severity = IssueSeverity.CRITICAL
                    )
                )
            }
        }

        // 3. Проверка на 2 СУТОК подряд (заступил на сутки на следующий день после суток без отсыпного)
        // Старший машины — рабочий день, поэтому он не считается сутками подряд!
        val all24hDutiesByEmp = assignments
            .filter {
                val post = DutyPost.findById(it.postId)
                post == null || post.is24HourDuty
            }
            .groupBy { it.employeeId }

        all24hDutiesByEmp.forEach { (empId, list) ->
            val name = empMap[empId]?.fullName ?: "Сотрудник #$empId"
            val dates = list.mapNotNull {
                try { parseDate(it.dateString) } catch (e: Exception) { null }
            }.sorted().distinct()

            for (i in 0 until dates.size - 1) {
                val d1 = dates[i]
                val d2 = dates[i + 1]
                if (ChronoUnit.DAYS.between(d1, d2) == 1L) {
                    if (d2 in startDate..endDate) {
                        issues.add(
                            ScheduleIssue(
                                date = d2,
                                employeeName = name,
                                message = "Заступает двое суток подряд! (без отсыпного дня)",
                                severity = IssueSeverity.WARNING
                            )
                        )
                    }
                }
            }
        }

        return issues.sortedBy { it.date }
    }
}

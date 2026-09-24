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
        // 1. Ручные длительные статусы: Отпуск / Больничный
        if (employee.manualStatus == EmployeeStatus.VACATION) {
            val text = employee.getFormattedStatusText(EmployeeStatus.VACATION)
            return EmployeeStatusInfo(
                status = EmployeeStatus.VACATION,
                displayStatus = text,
                daysSinceLastDuty = null,
                lastDutyDate = null
            )
        }
        if (employee.manualStatus == EmployeeStatus.SICK_LEAVE) {
            val text = employee.getFormattedStatusText(EmployeeStatus.SICK_LEAVE)
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
            // А. Ручной отпуск или больничный -> НЕЛЬЗЯ задействовать!
            if (emp.manualStatus == EmployeeStatus.VACATION || emp.manualStatus == EmployeeStatus.SICK_LEAVE) {
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

            // В. Если сотрудник СЕГОДНЯ уже стоит на СУТОЧНОМ наряде (КПП1, КПП2, ВГ2) ->
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

            // Г. Если текущий пост — СУТОЧНЫЙ наряд:
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

        // 2. Для суточных постов: пошаговое раскрытие при нехватке сотрудников
        val filteredList = if (post.is24HourDuty) {
            val workingOnly = candidateItems.filter { it.category == AvailabilityCategory.AVAILABLE_WORKING }
            if (workingOnly.isNotEmpty()) {
                workingOnly
            } else {
                // Не хватает доступных: подключаем тех, у кого был выходной
                val workingAndDayOff = candidateItems.filter {
                    it.category == AvailabilityCategory.AVAILABLE_WORKING ||
                            it.category == AvailabilityCategory.RESERVE_DAY_OFF
                }
                if (workingAndDayOff.isNotEmpty()) {
                    workingAndDayOff
                } else {
                    // Снова не хватает: подключаем тех, у кого был отсыпной
                    candidateItems.filter {
                        it.category == AvailabilityCategory.AVAILABLE_WORKING ||
                                it.category == AvailabilityCategory.RESERVE_DAY_OFF ||
                                it.category == AvailabilityCategory.RESERVE_REST_DAY
                    }
                }
            }
        } else {
            // Для старшего машины доступны все не заблокированные сотрудники (включая отсыпных!)
            candidateItems
        }

        // 3. Сортировка кандидатов
        return filteredList.sortedWith(
            compareBy<CandidateItem> {
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

                    val bestCandidate = candidates.firstOrNull()
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
                if (emp.manualStatus == EmployeeStatus.VACATION) {
                    issues.add(
                        ScheduleIssue(
                            date = parseDate(assign.dateString),
                            employeeName = emp.fullName,
                            message = "Назначен на наряд во время отпуска (${emp.getFormattedStatusText(EmployeeStatus.VACATION)})",
                            severity = IssueSeverity.CRITICAL
                        )
                    )
                } else if (emp.manualStatus == EmployeeStatus.SICK_LEAVE) {
                    issues.add(
                        ScheduleIssue(
                            date = parseDate(assign.dateString),
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

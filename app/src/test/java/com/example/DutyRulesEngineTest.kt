package com.example

import com.example.data.model.DutyAssignment
import com.example.data.model.DutyPost
import com.example.data.model.Employee
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import com.example.domain.AvailabilityCategory
import com.example.domain.DutyRulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DutyRulesEngineTest {

    @Test
    fun test24HourDutyCycleTransitions() {
        val employee = Employee(id = 1L, fullName = "Иванов И.И.", type = EmployeeType.DUTY)
        val dutyAssignment = DutyAssignment(
            id = 1L,
            dateString = "2026-09-20",
            postId = "kpp1",
            slotIndex = 0,
            employeeId = 1L
        )
        val assignments = listOf(dutyAssignment)

        // 1 день после суток (21 сен) -> На отсыпном
        val day1Status = DutyRulesEngine.calculateStatusOnDate(
            employee = employee,
            targetDate = LocalDate.of(2026, 9, 21),
            allAssignments = assignments
        )
        assertEquals(EmployeeStatus.POST_DUTY_REST, day1Status.status)
        assertEquals(1, day1Status.daysSinceLastDuty)

        // 2 дня после суток (22 сен) -> На выходном
        val day2Status = DutyRulesEngine.calculateStatusOnDate(
            employee = employee,
            targetDate = LocalDate.of(2026, 9, 22),
            allAssignments = assignments
        )
        assertEquals(EmployeeStatus.DAY_OFF, day2Status.status)
        assertEquals(2, day2Status.daysSinceLastDuty)

        // 3 дня после суток (23 сен) -> На рабочем дне (готов к наряду)
        val day3Status = DutyRulesEngine.calculateStatusOnDate(
            employee = employee,
            targetDate = LocalDate.of(2026, 9, 23),
            allAssignments = assignments
        )
        assertEquals(EmployeeStatus.WORKING, day3Status.status)
        assertEquals(3, day3Status.daysSinceLastDuty)
    }

    @Test
    fun testSeniorCarIsNot24HourDutyAndDoesNotTriggerRestDay() {
        val employee = Employee(id = 1L, fullName = "Иванов И.И.", type = EmployeeType.DUTY)
        val seniorCarAssignment = DutyAssignment(
            id = 1L,
            dateString = "2026-09-20",
            postId = "senior_car",
            slotIndex = 0,
            employeeId = 1L
        )
        val assignments = listOf(seniorCarAssignment)

        // На следующий день после Старшего машины сотрудник остается "На рабочем дне"!
        val nextDayStatus = DutyRulesEngine.calculateStatusOnDate(
            employee = employee,
            targetDate = LocalDate.of(2026, 9, 21),
            allAssignments = assignments
        )
        assertEquals(EmployeeStatus.WORKING, nextDayStatus.status)
    }

    @Test
    fun testSeniorCarAllowsAssignmentEvenOnPostDutyRestDay() {
        val employee = Employee(id = 1L, fullName = "Иванов И.И.", type = EmployeeType.DUTY)
        // Вчера стоял на КПП-1 (сутки) -> сегодня на отсыпном
        val yesterdayKpp1 = DutyAssignment(
            id = 1L,
            dateString = "2026-09-20",
            postId = "kpp1",
            slotIndex = 0,
            employeeId = 1L
        )
        val assignments = listOf(yesterdayKpp1)

        val targetDate = LocalDate.of(2026, 9, 21)
        val seniorCarPost = DutyPost.POST_SENIOR_CAR

        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = seniorCarPost,
            slot = seniorCarPost.slots[0],
            currentEmployeeId = null,
            employees = listOf(employee),
            allAssignments = assignments
        )

        // Для Старшего машины в отсыпной день сотрудник доступен и отображается!
        assertEquals(1, candidates.size)
        assertEquals(1L, candidates[0].employee.id)
        assertTrue(candidates[0].note.contains("Отсыпной (разрешено для Ст. машины)"))
    }

    @Test
    fun testCandidateFilterExcludesUnavailableEmployees() {
        val targetDate = LocalDate.of(2026, 9, 25)

        val dutyWorker = Employee(id = 1L, fullName = "Иванов И.И.", type = EmployeeType.DUTY)
        val assistantWorker = Employee(id = 2L, fullName = "Петров П.П.", type = EmployeeType.ASSISTANT)
        val vacationWorker = Employee(
            id = 3L,
            fullName = "Сидоров С.С.",
            type = EmployeeType.DUTY,
            manualStatus = EmployeeStatus.VACATION
        )
        val sickWorker = Employee(
            id = 4L,
            fullName = "Козлов К.К.",
            type = EmployeeType.DUTY,
            manualStatus = EmployeeStatus.SICK_LEAVE
        )
        val alreadyOn24hToday = Employee(id = 5L, fullName = "Морозов М.М.", type = EmployeeType.DUTY)

        val assignments = listOf(
            DutyAssignment(id = 1L, dateString = "2026-09-25", postId = "kpp1", slotIndex = 0, employeeId = 5L)
        )

        val allEmployees = listOf(dutyWorker, assistantWorker, vacationWorker, sickWorker, alreadyOn24hToday)

        // Проверяем пост КПП-2 (Только дежурный)
        val kpp2 = DutyPost.POST_KPP2
        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = kpp2,
            slot = kpp2.slots[0],
            currentEmployeeId = null,
            employees = allEmployees,
            allAssignments = assignments
        )

        val candidateIds = candidates.map { it.employee.id }

        // Должен быть ТОЛЬКО Иванов (1L)
        assertTrue(candidateIds.contains(1L))
        // Помощник Петров не должен быть (должность не подходит)
        assertFalse(candidateIds.contains(2L))
        // Сидоров (отпуск) не должен отображаться
        assertFalse(candidateIds.contains(3L))
        // Козлов (больничный) не должен отображаться
        assertFalse(candidateIds.contains(4L))
        // Морозов (уже на КПП-1 на сутках сегодня) не должен отображаться
        assertFalse(candidateIds.contains(5L))
    }

    @Test
    fun testSeniorCarDutyOfficerPriority() {
        val dutyOfficer = Employee(id = 1L, fullName = "Колпаков А.В.", type = EmployeeType.DUTY)
        val assistant = Employee(id = 2L, fullName = "Куваев А.Д.", type = EmployeeType.ASSISTANT)
        val employees = listOf(dutyOfficer, assistant)

        val targetDate = LocalDate.of(2026, 9, 25)
        val carPost = DutyPost.POST_SENIOR_CAR

        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = carPost,
            slot = carPost.slots[0],
            currentEmployeeId = null,
            employees = employees,
            allAssignments = emptyList()
        )

        // Оба разрешены, но Дежурный идет первым
        assertEquals(2, candidates.size)
        assertEquals(EmployeeType.DUTY, candidates[0].employee.type)
        assertEquals(EmployeeType.ASSISTANT, candidates[1].employee.type)
    }

    @Test
    fun testCandidateRankingFallbackWorkingThenDayOffThenRest() {
        val empDayOff = Employee(id = 2L, fullName = "Борисов Б.", type = EmployeeType.DUTY)
        val empRest = Employee(id = 3L, fullName = "Васильев В.", type = EmployeeType.DUTY)

        val targetDate = LocalDate.of(2026, 9, 25)

        val assignments = listOf(
            DutyAssignment(id = 1L, dateString = "2026-09-24", postId = "kpp2", slotIndex = 0, employeeId = 3L), // вчера заступал -> отсыпной 25-го
            DutyAssignment(id = 2L, dateString = "2026-09-23", postId = "kpp2", slotIndex = 0, employeeId = 2L)  // 2 дня назад -> выходной 25-го
        )

        val kpp2 = DutyPost.POST_KPP2

        // Кандидаты возвращаются с корректными категориями доступности (выходной и отсыпной)
        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = kpp2,
            slot = kpp2.slots[0],
            currentEmployeeId = null,
            employees = listOf(empRest, empDayOff),
            allAssignments = assignments
        )

        val dayOffItem = candidates.find { it.employee.id == 2L }
        val restItem = candidates.find { it.employee.id == 3L }

        assertEquals(AvailabilityCategory.RESERVE_DAY_OFF, dayOffItem?.category)
        assertEquals(AvailabilityCategory.RESERVE_REST_DAY, restItem?.category)
    }

    @Test
    fun testUpcomingVacationExcludesEmployeeFromSchedule() {
        val empOnUpcomingVacation = Employee(
            id = 1L,
            fullName = "Колпаков А.В.",
            type = EmployeeType.DUTY,
            upcomingVacationStart = "2026-09-20",
            upcomingVacationEnd = "2026-09-26"
        )
        val empWorking = Employee(
            id = 2L,
            fullName = "Шахматов В.П.",
            type = EmployeeType.DUTY
        )

        // 25 сентября попадает в ближайший отпуск Колпакова
        val targetDate = LocalDate.of(2026, 9, 25)
        assertTrue(DutyRulesEngine.isEmployeeOnVacation(empOnUpcomingVacation, targetDate))
        assertFalse(DutyRulesEngine.isEmployeeOnVacation(empWorking, targetDate))

        val kpp2 = DutyPost.POST_KPP2
        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = kpp2,
            slot = kpp2.slots[0],
            currentEmployeeId = null,
            employees = listOf(empOnUpcomingVacation, empWorking),
            allAssignments = emptyList()
        )

        // Колпаков не должен отображаться среди кандидатов
        assertEquals(1, candidates.size)
        assertEquals(2L, candidates[0].employee.id)

        // А 28 сентября (после отпуска) Колпаков снова доступен
        val afterVacation = LocalDate.of(2026, 9, 28)
        assertFalse(DutyRulesEngine.isEmployeeOnVacation(empOnUpcomingVacation, afterVacation))
    }

    @Test
    fun testPostPriorityHidesEmployeeFromOtherPosts() {
        // Джумагазиев: приоритет заступления ВГ2
        val dzhumagaziev = Employee(
            id = 1L,
            fullName = "Джумагазиев Е.Т.",
            type = EmployeeType.DUTY,
            priorityPostId = "vg2"
        )
        val otherOfficer = Employee(
            id = 2L,
            fullName = "Иванов И.И.",
            type = EmployeeType.DUTY
        )

        val targetDate = LocalDate.of(2026, 9, 25)

        // 1. Пост КПП-2: Джумагазиев НЕ должен отображаться, пока установлен приоритет ВГ2!
        val kpp2Candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = DutyPost.POST_KPP2,
            slot = DutyPost.POST_KPP2.slots[0],
            currentEmployeeId = null,
            employees = listOf(dzhumagaziev, otherOfficer),
            allAssignments = emptyList()
        )
        assertEquals(1, kpp2Candidates.size)
        assertEquals(2L, kpp2Candidates[0].employee.id)

        // 2. Пост КПП-1: аналогично не отображается
        val kpp1Candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = DutyPost.POST_KPP1,
            slot = DutyPost.POST_KPP1.slots[0],
            currentEmployeeId = null,
            employees = listOf(dzhumagaziev, otherOfficer),
            allAssignments = emptyList()
        )
        assertEquals(1, kpp1Candidates.size)
        assertEquals(2L, kpp1Candidates[0].employee.id)

        // 3. Пост ВГ-2 (его приоритетный пост): отображается и идет ПЕРВЫМ!
        val vg2Candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = targetDate,
            post = DutyPost.POST_VG2,
            slot = DutyPost.POST_VG2.slots[0],
            currentEmployeeId = null,
            employees = listOf(otherOfficer, dzhumagaziev),
            allAssignments = emptyList()
        )
        assertEquals(2, vg2Candidates.size)
        assertEquals(1L, vg2Candidates[0].employee.id) // Джумагазиев первый
    }

    @Test
    fun testAuditDetectsConsecutive24HourDuties() {
        val emp = Employee(id = 1L, fullName = "Смирнов С.", type = EmployeeType.DUTY)
        val assignments = listOf(
            DutyAssignment(id = 1L, dateString = "2026-09-24", postId = "kpp2", slotIndex = 0, employeeId = 1L),
            DutyAssignment(id = 2L, dateString = "2026-09-25", postId = "kpp2", slotIndex = 0, employeeId = 1L)
        )

        val issues = DutyRulesEngine.auditSchedule(
            startDate = LocalDate.of(2026, 9, 24),
            endDate = LocalDate.of(2026, 9, 26),
            assignments = assignments,
            employees = listOf(emp)
        )

        assertTrue(issues.isNotEmpty())
        assertTrue(issues.any { it.message.contains("двое суток подряд") })
    }

    @Test
    fun testAutoScheduleSimulationForFullMonth() {
        val initialEmployees = listOf(
            Employee(id = 1L, fullName = "Колпаков", type = EmployeeType.DUTY),
            Employee(id = 2L, fullName = "Шахматов", type = EmployeeType.DUTY),
            Employee(id = 3L, fullName = "Иманов", type = EmployeeType.DUTY),
            Employee(id = 4L, fullName = "Кочановский", type = EmployeeType.DUTY),
            Employee(id = 5L, fullName = "Фадеев", type = EmployeeType.DUTY),
            Employee(id = 6L, fullName = "Крамаренко", type = EmployeeType.DUTY),
            Employee(id = 7L, fullName = "Наруков", type = EmployeeType.DUTY),
            Employee(id = 8L, fullName = "Джумагазиев", type = EmployeeType.DUTY),
            Employee(id = 9L, fullName = "Алексеенко", type = EmployeeType.DUTY),
            Employee(id = 10L, fullName = "Амбарцумян", type = EmployeeType.DUTY),
            Employee(id = 11L, fullName = "Шантин", type = EmployeeType.ASSISTANT),
            Employee(id = 12L, fullName = "Инешин", type = EmployeeType.ASSISTANT),
            Employee(id = 13L, fullName = "Куваев", type = EmployeeType.ASSISTANT),
            Employee(id = 14L, fullName = "Винокуров", type = EmployeeType.ASSISTANT),
            Employee(id = 15L, fullName = "Соломатин", type = EmployeeType.ASSISTANT),
            Employee(id = 16L, fullName = "Белан", type = EmployeeType.ASSISTANT)
        )

        val generated = DutyRulesEngine.autoScheduleRange(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30),
            posts = DutyPost.ALL_POSTS,
            employees = initialEmployees,
            existingAssignments = emptyList(),
            overwriteExisting = true
        )

        // 30 дней * 8 слотов (КПП1: 3 + КПП2: 1 + Ст.машины: 3 + ВГ2: 1 = 8) = 240
        assertEquals(240, generated.size)

        // Никто не заступает на суточный пост во время другого суточного поста в один день
        val groupedByDate = generated.groupBy { it.dateString }
        groupedByDate.forEach { (_, list) ->
            val duties24h = list.filter {
                val p = DutyPost.findById(it.postId)
                p == null || p.is24HourDuty
            }
            val empCounts24h = duties24h.groupingBy { it.employeeId }.eachCount()
            assertTrue(empCounts24h.all { it.value == 1 })
        }
    }

    @Test
    fun testAutoScheduleForWeekStandard() {
        val initialEmployees = listOf(
            Employee(id = 1L, fullName = "Колпаков", type = EmployeeType.DUTY),
            Employee(id = 2L, fullName = "Шахматов", type = EmployeeType.DUTY),
            Employee(id = 3L, fullName = "Иманов", type = EmployeeType.DUTY),
            Employee(id = 4L, fullName = "Кочановский", type = EmployeeType.DUTY),
            Employee(id = 5L, fullName = "Фадеев", type = EmployeeType.DUTY),
            Employee(id = 6L, fullName = "Шантин", type = EmployeeType.ASSISTANT),
            Employee(id = 7L, fullName = "Инешин", type = EmployeeType.ASSISTANT),
            Employee(id = 8L, fullName = "Куваев", type = EmployeeType.ASSISTANT),
            Employee(id = 9L, fullName = "Винокуров", type = EmployeeType.ASSISTANT)
        )

        val weekStart = LocalDate.of(2026, 9, 1)
        val weekEnd = LocalDate.of(2026, 9, 7) // 7 дней (стандартная неделя)

        val generated = DutyRulesEngine.autoScheduleRange(
            startDate = weekStart,
            endDate = weekEnd,
            posts = DutyPost.ALL_POSTS,
            employees = initialEmployees,
            existingAssignments = emptyList(),
            overwriteExisting = true
        )

        // 7 дней * 8 слотов = 56 назначений
        assertEquals(56, generated.size)

        // Все даты находятся строго в пределах недели
        val allDates = generated.map { it.dateString }.toSet()
        assertEquals(7, allDates.size)
        assertTrue(allDates.contains("2026-09-01"))
        assertTrue(allDates.contains("2026-09-07"))
    }
}


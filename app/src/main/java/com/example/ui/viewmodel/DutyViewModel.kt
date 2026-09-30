package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.DutyAssignment
import com.example.data.model.DutyPost
import com.example.data.model.Employee
import com.example.data.model.EmployeeStatus
import com.example.data.model.EmployeeType
import com.example.data.model.SlotDef
import com.example.data.repository.DutyRepository
import com.example.domain.CandidateItem
import com.example.domain.DutyRulesEngine
import com.example.domain.ScheduleIssue
import com.example.export.ScheduleExportManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

enum class ScheduleViewMode {
    TABLE,   // Табличный вид как на листе
    DAY_CARDS // По дням в виде детальных карточек
}

data class SlotSelection(
    val date: LocalDate,
    val post: DutyPost,
    val slot: SlotDef,
    val currentEmployeeId: Long?,
    val currentNote: String = ""
)

// Перечисление вариантов периода создания графика
enum class ScheduleRangeType(val label: String) {
    WEEK("На неделю (7 дней)"),
    MONTH("На месяц"),
    CUSTOM("Свой диапазон дней")
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class FileReady(val file: File, val mimeType: String, val title: String) : UiEvent()
    data class FileSaved(val message: String) : UiEvent()
}

class DutyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = DutyRepository(db.employeeDao(), db.dutyAssignmentDao())

    val allEmployees: StateFlow<List<Employee>> = repository.allEmployeesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAssignments: StateFlow<List<DutyAssignment>> = repository.allAssignmentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Текущий выбранный месяц
    private val _selectedMonth = MutableStateFlow(YearMonth.of(2026, 9))
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    // Режим отображения: Таблица или По дням
    private val _viewMode = MutableStateFlow(ScheduleViewMode.TABLE)
    val viewMode: StateFlow<ScheduleViewMode> = _viewMode.asStateFlow()

    // Выбранный день в календаре
    private val _selectedDay = MutableStateFlow(LocalDate.of(2026, 9, 24))
    val selectedDay: StateFlow<LocalDate> = _selectedDay.asStateFlow()

    // Снимок графика до ручных изменений (для подсветки изменений при экспорте)
    // Ключ: "yyyy-MM-dd_postId_slotIndex_employeeId"
    private val _baselineAssignmentKeys = MutableStateFlow<Set<String>>(emptySet())
    val baselineAssignmentKeys: StateFlow<Set<String>> = _baselineAssignmentKeys.asStateFlow()

    // Настройка тумблера: отображать изменения при экспорте
    private val _highlightChangesInExport = MutableStateFlow(true)
    val highlightChangesInExport: StateFlow<Boolean> = _highlightChangesInExport.asStateFlow()

    fun setHighlightChangesInExport(highlight: Boolean) {
        _highlightChangesInExport.value = highlight
    }

    // Слот, для которого открыт выбор сотрудника (BottomSheet)
    private val _activeSlotSelection = MutableStateFlow<SlotSelection?>(null)
    val activeSlotSelection: StateFlow<SlotSelection?> = _activeSlotSelection.asStateFlow()

    // Кандидаты для активного слота
    private val _activeSlotCandidates = MutableStateFlow<List<CandidateItem>>(emptyList())
    val activeSlotCandidates: StateFlow<List<CandidateItem>> = _activeSlotCandidates.asStateFlow()

    // Поиск в списке кандидатов
    private val _candidateSearchQuery = MutableStateFlow("")
    val candidateSearchQuery: StateFlow<String> = _candidateSearchQuery.asStateFlow()

    // Диалог редактирования сотрудника (null = закрыт)
    private val _editingEmployee = MutableStateFlow<Employee?>(null)
    val editingEmployee: StateFlow<Employee?> = _editingEmployee.asStateFlow()

    // Аудит графика: список предупреждений
    private val _auditIssues = MutableStateFlow<List<ScheduleIssue>?>(null)
    val auditIssues: StateFlow<List<ScheduleIssue>?> = _auditIssues.asStateFlow()

    // Флаг загрузки / экспорта
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.checkAndSeedInitialData()
        }
    }

    fun setMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
        _selectedDay.value = yearMonth.atDay(1)
    }

    fun prevMonth() {
        val prev = _selectedMonth.value.minusMonths(1)
        setMonth(prev)
    }

    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        setMonth(next)
    }

    fun setViewMode(mode: ScheduleViewMode) {
        _viewMode.value = mode
    }

    fun selectDay(date: LocalDate) {
        _selectedDay.value = date
    }

    /**
     * Открытие выбора сотрудника для конкретной позиции в конкретный день
     */
    fun openSlotPicker(date: LocalDate, post: DutyPost, slot: SlotDef) {
        val dateStr = DutyRulesEngine.formatDate(date)
        val currentAssignment = allAssignments.value.find {
            it.dateString == dateStr && it.postId == post.id && it.slotIndex == slot.slotIndex
        }

        val selection = SlotSelection(
            date = date,
            post = post,
            slot = slot,
            currentEmployeeId = currentAssignment?.employeeId,
            currentNote = currentAssignment?.note.orEmpty()
        )
        _activeSlotSelection.value = selection
        _candidateSearchQuery.value = ""

        recalculateCandidates(selection)
    }

    fun closeSlotPicker() {
        _activeSlotSelection.value = null
        _activeSlotCandidates.value = emptyList()
    }

    fun setCandidateSearchQuery(query: String) {
        _candidateSearchQuery.value = query
    }

    private fun recalculateCandidates(selection: SlotSelection) {
        val candidates = DutyRulesEngine.getRankedCandidates(
            targetDate = selection.date,
            post = selection.post,
            slot = selection.slot,
            currentEmployeeId = selection.currentEmployeeId,
            employees = allEmployees.value,
            allAssignments = allAssignments.value
        )
        _activeSlotCandidates.value = candidates
    }

    /**
     * Назначение выбранного сотрудника на активный слот (или несколько смен для Старшего машины)
     */
    fun assignEmployeeToActiveSlot(
        employeeId: Long,
        note: String = "",
        selectedSlotIndices: Set<Int>? = null
    ) {
        val selection = _activeSlotSelection.value ?: return
        val dateStr = DutyRulesEngine.formatDate(selection.date)

        viewModelScope.launch {
            if (selection.post.id == DutyPost.POST_SENIOR_CAR.id && !selectedSlotIndices.isNullOrEmpty()) {
                selectedSlotIndices.forEach { slotIdx ->
                    repository.assignEmployee(
                        dateString = dateStr,
                        postId = selection.post.id,
                        slotIndex = slotIdx,
                        employeeId = employeeId,
                        note = note
                    )
                }
            } else {
                repository.assignEmployee(
                    dateString = dateStr,
                    postId = selection.post.id,
                    slotIndex = selection.slot.slotIndex,
                    employeeId = employeeId,
                    note = note
                )
            }
            closeSlotPicker()
            _events.emit(UiEvent.ShowToast("Сотрудник назначен на пост"))
        }
    }

    /**
     * Очистка активного слота
     */
    fun clearActiveSlot() {
        val selection = _activeSlotSelection.value ?: return
        val dateStr = DutyRulesEngine.formatDate(selection.date)

        viewModelScope.launch {
            repository.clearAssignment(
                dateString = dateStr,
                postId = selection.post.id,
                slotIndex = selection.slot.slotIndex
            )
            closeSlotPicker()
            _events.emit(UiEvent.ShowToast("Слот освобожден"))
        }
    }

    /**
     * Создание графика на заданный диапазон (стандарт на неделю / на месяц / свой диапазон)
     */
    fun createScheduleForRange(startDate: LocalDate, endDate: LocalDate, overwrite: Boolean = true) {
        viewModelScope.launch {
            if (allEmployees.value.isEmpty()) {
                _events.emit(UiEvent.ShowToast("Сначала добавьте сотрудников во вкладке «Сотрудники»!"))
                return@launch
            }

            val newAssignments = DutyRulesEngine.autoScheduleRange(
                startDate = startDate,
                endDate = endDate,
                posts = DutyPost.ALL_POSTS,
                employees = allEmployees.value,
                existingAssignments = allAssignments.value,
                overwriteExisting = overwrite
            )

            if (overwrite) {
                repository.clearRange(
                    DutyRulesEngine.formatDate(startDate),
                    DutyRulesEngine.formatDate(endDate)
                )
            }
            repository.insertAllAssignments(newAssignments)

            // Фиксируем снимок сгенерированного графика как базовый
            val baseline = newAssignments.map { "${it.dateString}_${it.postId}_${it.slotIndex}_${it.employeeId}" }.toSet()
            _baselineAssignmentKeys.value = baseline

            // Переключаем выбранный месяц и день к началу периода
            _selectedMonth.value = YearMonth.of(startDate.year, startDate.month)
            _selectedDay.value = startDate

            val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
            _events.emit(UiEvent.ShowToast("График успешно создан на $days дн. с соблюдением правил отдыха!"))
        }
    }

    /**
     * Автоматическое умное заполнение выбранного месяца с соблюдением всех правил
     */
    fun autoScheduleCurrentMonth(overwrite: Boolean = false) {
        val ym = _selectedMonth.value
        createScheduleForRange(ym.atDay(1), ym.atEndOfMonth(), overwrite)
    }

    /**
     * Полная очистка всего графика
     */
    fun clearAllSchedule() {
        viewModelScope.launch {
            repository.clearAll()
            _baselineAssignmentKeys.value = emptySet()
            _events.emit(UiEvent.ShowToast("График очищен"))
        }
    }

    /**
     * Очистка графика за текущий месяц
     */
    fun clearCurrentMonth() {
        viewModelScope.launch {
            val ym = _selectedMonth.value
            repository.clearRange(
                DutyRulesEngine.formatDate(ym.atDay(1)),
                DutyRulesEngine.formatDate(ym.atEndOfMonth())
            )
            _events.emit(UiEvent.ShowToast("График за месяц очищен"))
        }
    }

    /**
     * Запуск проверки графика на нарушения
     */
    fun runScheduleAudit() {
        val ym = _selectedMonth.value
        val issues = DutyRulesEngine.auditSchedule(
            startDate = ym.atDay(1),
            endDate = ym.atEndOfMonth(),
            assignments = allAssignments.value,
            employees = allEmployees.value
        )
        _auditIssues.value = issues
    }

    fun closeAuditDialog() {
        _auditIssues.value = null
    }

    // --- Сотрудники ---

    fun openAddEmployeeDialog() {
        _editingEmployee.value = Employee(
            fullName = "",
            type = EmployeeType.DUTY,
            manualStatus = EmployeeStatus.WORKING
        )
    }

    fun openEditEmployeeDialog(employee: Employee) {
        _editingEmployee.value = employee
    }

    fun closeEmployeeDialog() {
        _editingEmployee.value = null
    }

    fun saveEmployee(employee: Employee) {
        if (employee.fullName.isBlank()) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowToast("Укажите фамилию и инициалы"))
            }
            return
        }
        viewModelScope.launch {
            repository.saveEmployee(employee)
            closeEmployeeDialog()
            _events.emit(UiEvent.ShowToast("Сотрудник сохранен"))
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
            closeEmployeeDialog()
            _events.emit(UiEvent.ShowToast("Сотрудник удален"))
        }
    }

    fun toggleEmployeeRole(employee: Employee) {
        val newType = if (employee.type == EmployeeType.DUTY) EmployeeType.ASSISTANT else EmployeeType.DUTY
        viewModelScope.launch {
            repository.saveEmployee(employee.copy(type = newType))
            _events.emit(UiEvent.ShowToast("${employee.fullName}: тип изменен на ${newType.label}"))
        }
    }

    fun updateEmployeeStatus(employee: Employee, status: EmployeeStatus, untilDate: String?) {
        viewModelScope.launch {
            repository.saveEmployee(employee.copy(manualStatus = status, statusUntilDate = untilDate))
            _events.emit(UiEvent.ShowToast("Статус сотрудника обновлен"))
        }
    }

    // --- Экспорт ---

    fun exportScheduleAsPdf(
        context: Context,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        highlightChanges: Boolean = _highlightChangesInExport.value
    ) {
        val ym = _selectedMonth.value
        val start = startDate ?: ym.atDay(1)
        val end = endDate ?: ym.atEndOfMonth()

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = ScheduleExportManager.createPdf(
                    context = context,
                    startDate = start,
                    endDate = end,
                    assignments = allAssignments.value,
                    employees = allEmployees.value,
                    posts = DutyPost.ALL_POSTS,
                    highlightChanges = highlightChanges,
                    baselineKeys = _baselineAssignmentKeys.value
                )
                _events.emit(UiEvent.FileReady(file, "application/pdf", "Отправить график (PDF)"))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(UiEvent.ShowToast("Ошибка при создании PDF: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun downloadScheduleAsPdf(
        context: Context,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        highlightChanges: Boolean = _highlightChangesInExport.value
    ) {
        val ym = _selectedMonth.value
        val start = startDate ?: ym.atDay(1)
        val end = endDate ?: ym.atEndOfMonth()

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = ScheduleExportManager.createPdf(
                    context = context,
                    startDate = start,
                    endDate = end,
                    assignments = allAssignments.value,
                    employees = allEmployees.value,
                    posts = DutyPost.ALL_POSTS,
                    highlightChanges = highlightChanges,
                    baselineKeys = _baselineAssignmentKeys.value
                )
                val success = ScheduleExportManager.saveFileToDownloads(context, file, "application/pdf")
                if (success) {
                    _events.emit(UiEvent.FileSaved("Файл PDF успешно сохранен в папку Загрузки (Downloads)"))
                } else {
                    _events.emit(UiEvent.ShowToast("Не удалось сохранить PDF в Загрузки"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(UiEvent.ShowToast("Ошибка при скачивании PDF: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportScheduleAsImage(
        context: Context,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        highlightChanges: Boolean = _highlightChangesInExport.value
    ) {
        val ym = _selectedMonth.value
        val start = startDate ?: ym.atDay(1)
        val end = endDate ?: ym.atEndOfMonth()

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = ScheduleExportManager.createImage(
                    context = context,
                    startDate = start,
                    endDate = end,
                    assignments = allAssignments.value,
                    employees = allEmployees.value,
                    posts = DutyPost.ALL_POSTS,
                    highlightChanges = highlightChanges,
                    baselineKeys = _baselineAssignmentKeys.value
                )
                _events.emit(UiEvent.FileReady(file, "image/png", "Отправить график (Изображение PNG)"))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(UiEvent.ShowToast("Ошибка при создании изображения: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun downloadScheduleAsImage(
        context: Context,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        highlightChanges: Boolean = _highlightChangesInExport.value
    ) {
        val ym = _selectedMonth.value
        val start = startDate ?: ym.atDay(1)
        val end = endDate ?: ym.atEndOfMonth()

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = ScheduleExportManager.createImage(
                    context = context,
                    startDate = start,
                    endDate = end,
                    assignments = allAssignments.value,
                    employees = allEmployees.value,
                    posts = DutyPost.ALL_POSTS,
                    highlightChanges = highlightChanges,
                    baselineKeys = _baselineAssignmentKeys.value
                )
                val success = ScheduleExportManager.saveFileToDownloads(context, file, "image/png")
                if (success) {
                    _events.emit(UiEvent.FileSaved("Изображение PNG успешно сохранено в Загрузки (Downloads)"))
                } else {
                    _events.emit(UiEvent.ShowToast("Не удалось сохранить изображение в Загрузки"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(UiEvent.ShowToast("Ошибка при скачивании изображения: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }
}


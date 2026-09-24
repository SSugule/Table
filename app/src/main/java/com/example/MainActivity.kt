package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.export.ScheduleExportManager
import com.example.ui.components.ScheduleAuditDialog
import com.example.ui.screens.EditEmployeeDialog
import com.example.ui.screens.EmployeePickerBottomSheet
import com.example.ui.screens.EmployeesScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.PostsScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Navy800
import com.example.ui.theme.PrimaryBlue
import com.example.ui.viewmodel.DutyViewModel
import com.example.ui.viewmodel.UiEvent
import com.example.update.AppUpdateManager
import com.example.update.UpdateStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DutyViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: DutyViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val assignments by viewModel.allAssignments.collectAsStateWithLifecycle()
    val employees by viewModel.allEmployees.collectAsStateWithLifecycle()
    val activeSlotSelection by viewModel.activeSlotSelection.collectAsStateWithLifecycle()
    val activeSlotCandidates by viewModel.activeSlotCandidates.collectAsStateWithLifecycle()
    val editingEmployee by viewModel.editingEmployee.collectAsStateWithLifecycle()
    val auditIssues by viewModel.auditIssues.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

    val updateStatus by AppUpdateManager.status.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Автоматическая проверка обновления на GitHub Release при старте приложения
    // Если на GitHub есть более свежий релиз, он сразу же скачивается в фоне и запускает установщик
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdateAndInstall(context, silent = true)
    }

    // Обработка событий (Toast / Share File)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.FileSaved -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is UiEvent.FileReady -> {
                    ScheduleExportManager.shareFile(
                        context = context,
                        file = event.file,
                        mimeType = event.mimeType,
                        title = event.title
                    )
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> stringResource(R.string.app_full_name)
                            1 -> "База сотрудников"
                            2 -> "Посты и работы"
                            3 -> "Экспорт и отправка"
                            else -> stringResource(R.string.app_name)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                },
                actions = {
                    // Индикатор фонового обновления
                    when (val s = updateStatus) {
                        is UpdateStatus.Downloading -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Обновление: ${(s.progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is UpdateStatus.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp).padding(end = 8.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {}
                    }

                    // Кнопка статуса / проверки обновлений
                    IconButton(
                        onClick = { showUpdateDialog = true },
                        modifier = Modifier.testTag("update_manager_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Обновление приложения",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy800,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.tab_schedule), fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        indicatorColor = Color(0xFFDBEAFE)
                    ),
                    modifier = Modifier.testTag("tab_schedule")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.tab_employees), fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        indicatorColor = Color(0xFFDBEAFE)
                    ),
                    modifier = Modifier.testTag("tab_employees")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.tab_posts), fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        indicatorColor = Color(0xFFDBEAFE)
                    ),
                    modifier = Modifier.testTag("tab_posts")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.tab_export), fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        indicatorColor = Color(0xFFDBEAFE)
                    ),
                    modifier = Modifier.testTag("tab_export")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ScheduleScreen(
                    viewModel = viewModel,
                    selectedMonth = selectedMonth,
                    viewMode = viewMode,
                    assignments = assignments,
                    employees = employees,
                    onOpenSlotPicker = { date, post, slot ->
                        viewModel.openSlotPicker(date, post, slot)
                    }
                )
                1 -> EmployeesScreen(
                    viewModel = viewModel,
                    employees = employees,
                    assignments = assignments
                )
                2 -> PostsScreen()
                3 -> ExportScreen(
                    viewModel = viewModel,
                    selectedMonth = selectedMonth,
                    assignments = assignments,
                    employees = employees,
                    isExporting = isExporting
                )
            }
        }
    }

    // Нижняя шторка (BottomSheet) выбора сотрудника для ячейки поста
    activeSlotSelection?.let { selection ->
        EmployeePickerBottomSheet(
            selection = selection,
            candidates = activeSlotCandidates,
            sheetState = sheetState,
            onDismiss = { viewModel.closeSlotPicker() },
            onAssign = { empId, note, selectedSlots ->
                viewModel.assignEmployeeToActiveSlot(empId, note, selectedSlots)
            },
            onClearSlot = {
                viewModel.clearActiveSlot()
            }
        )
    }

    // Диалог создания / редактирования сотрудника
    editingEmployee?.let { emp ->
        EditEmployeeDialog(
            employee = emp,
            onDismiss = { viewModel.closeEmployeeDialog() },
            onSave = { updatedEmp ->
                viewModel.saveEmployee(updatedEmp)
            },
            onDelete = { empToDelete ->
                viewModel.deleteEmployee(empToDelete)
            }
        )
    }

    // Диалог результатов аудита графика
    auditIssues?.let { issues ->
        ScheduleAuditDialog(
            issues = issues,
            onDismiss = { viewModel.closeAuditDialog() }
        )
    }

    // Диалог проверки и настройки авто-обновлений с GitHub Release
    if (showUpdateDialog) {
        var repoInput by remember { mutableStateOf(AppUpdateManager.getGitHubRepo(context)) }

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Авто-обновление с GitHub")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Текущая установленная версия: v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy800
                    )

                    Text(
                        text = "При выходе нового релиза на GitHub приложение автоматически скачивает APK и запускает обновление без лишних вопросов.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("GitHub репозиторий (owner/repo)") },
                        placeholder = { Text("super-souls2018/monarchy-schedule") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    when (val s = updateStatus) {
                        is UpdateStatus.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Проверка репозитория GitHub...", fontSize = 12.sp)
                            }
                        }
                        is UpdateStatus.Downloading -> {
                            Text(
                                text = "Загрузка v${s.version}: ${(s.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is UpdateStatus.UpToDate -> {
                            Text(
                                text = "У вас установлена самая актуальная версия (${s.version})",
                                fontSize = 12.sp,
                                color = Color(0xFF16A34A)
                            )
                        }
                        is UpdateStatus.Error -> {
                            Text(
                                text = "Статус: ${s.message}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppUpdateManager.setGitHubRepo(context, repoInput)
                        coroutineScope.launch {
                            AppUpdateManager.checkForUpdateAndInstall(context, silent = false)
                        }
                    }
                ) {
                    Text("Проверить и обновить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

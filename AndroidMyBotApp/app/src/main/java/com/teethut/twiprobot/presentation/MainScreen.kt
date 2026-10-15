package com.teethut.twiprobot.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import com.teethut.twiprobot.presentation.ai.RobotAiAssistantSheet
import com.teethut.twiprobot.presentation.ai.RobotAssistantViewModel
import com.teethut.twiprobot.presentation.ai.catlab.CatAiLabScreen
import com.teethut.twiprobot.presentation.ai.catlab.CatLabViewModel
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.presentation.components.ConnectionBadge
import com.teethut.twiprobot.presentation.drive.DriveScreen
import com.teethut.twiprobot.presentation.drive.DriveViewModel
import com.teethut.twiprobot.presentation.history.HistoryScreen
import com.teethut.twiprobot.presentation.history.HistoryViewModel
import com.teethut.twiprobot.presentation.settings.ConnectionDialog
import com.teethut.twiprobot.presentation.telemetry.TelemetryScreen
import com.teethut.twiprobot.presentation.telemetry.TelemetryViewModel
import com.teethut.twiprobot.presentation.tracker.TrackerScreen
import com.teethut.twiprobot.presentation.tracker.TrackerViewModel
import com.teethut.twiprobot.presentation.tuning.TuningScreen
import com.teethut.twiprobot.presentation.tuning.TuningViewModel

sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Drive : AppDestination("drive", "Drive", Icons.Default.SportsEsports)
    data object Tracker : AppDestination("tracker", "Tracker", Icons.Default.CenterFocusStrong)
    data object Scopes : AppDestination("scopes", "Scopes", Icons.AutoMirrored.Filled.ShowChart)
    data object Tuning : AppDestination("tuning", "Tuning", Icons.Default.Tune)
    data object History : AppDestination("history", "History", Icons.Default.History)

    companion object {
        val items: List<AppDestination>
            get() = listOf(Drive, Tracker, Scopes, Tuning, History)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRobotApp(
    driveViewModel: DriveViewModel? = null,
    trackerViewModel: TrackerViewModel? = null,
    telemetryViewModel: TelemetryViewModel? = null,
    tuningViewModel: TuningViewModel? = null,
    historyViewModel: HistoryViewModel? = null
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val actualDriveViewModel: DriveViewModel = driveViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DriveViewModel.createWithContext(context) as T
            }
        }
    )
    val actualTrackerViewModel: TrackerViewModel = trackerViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrackerViewModel.createWithContext(context) as T
            }
        }
    )
    val actualTelemetryViewModel: TelemetryViewModel = telemetryViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TelemetryViewModel(com.teethut.twiprobot.data.repository.RobotRepositoryImpl.instance) as T
            }
        }
    )
    val actualTuningViewModel: TuningViewModel = tuningViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TuningViewModel(
                    repository = com.teethut.twiprobot.data.repository.RobotRepositoryImpl.instance,
                    historyRepository = com.teethut.twiprobot.data.repository.PresetHistoryRepositoryImpl.getInstance(context)
                ) as T
            }
        }
    )
    val actualHistoryViewModel: HistoryViewModel = historyViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel.createWithContext(context) as T
            }
        }
    )
    val actualAssistantViewModel: RobotAssistantViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RobotAssistantViewModel(context) as T
            }
        }
    )
    val catLabViewModel: CatLabViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CatLabViewModel(context) as T
            }
        }
    )

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showAiAssistantSheet by remember { mutableStateOf(false) }
    var showCatAiLabScreen by remember { mutableStateOf(false) }
    val connectionState by actualDriveViewModel.connectionState.collectAsState()
    val trackerUiState by actualTrackerViewModel.uiState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.Drive.route
    val isCatFaceMode = (currentRoute == AppDestination.Tracker.route) && trackerUiState.isCatFaceMode

    Scaffold(
        topBar = {
            if (!isCatFaceMode) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TWIP BOT",
                                color = Color(0xFF38BDF8),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            // Unified Connection Status Pill (Wi-Fi Icon + State in ONE clean chip)
                            Surface(
                                onClick = { showConnectionDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> Color(0xFF14532D)
                                    is ConnectionState.Connecting -> Color(0xFF78350F)
                                    else -> Color(0xFF7F1D1D)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when (connectionState) {
                                        is ConnectionState.Connected -> Color(0xFF4ADE80).copy(alpha = 0.5f)
                                        is ConnectionState.Connecting -> Color(0xFFFBBF24).copy(alpha = 0.5f)
                                        else -> Color(0xFFF87171).copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (connectionState is ConnectionState.Connected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = "Connection Status",
                                        tint = when (connectionState) {
                                            is ConnectionState.Connected -> Color(0xFF4ADE80)
                                            is ConnectionState.Connecting -> Color(0xFFFBBF24)
                                            else -> Color(0xFFF87171)
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = when (connectionState) {
                                            is ConnectionState.Connected -> "ONLINE"
                                            is ConnectionState.Connecting -> "CONNECTING"
                                            is ConnectionState.Error -> "ERROR"
                                            else -> "OFFLINE"
                                        },
                                        color = when (connectionState) {
                                            is ConnectionState.Connected -> Color(0xFF4ADE80)
                                            is ConnectionState.Connecting -> Color(0xFFFBBF24)
                                            else -> Color(0xFFF87171)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }

                            // Quick 1-tap direct launcher for Live AI Cat Companion
                            FilledTonalIconButton(
                                onClick = {
                                    actualTrackerViewModel.setCatFaceMode(true)
                                    if (currentRoute != AppDestination.Tracker.route) {
                                        navController.navigate(AppDestination.Tracker.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, Color(0xFFF472B6).copy(alpha = 0.45f), CircleShape),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFFF472B6).copy(alpha = 0.15f),
                                    contentColor = Color(0xFFF472B6)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pets,
                                    contentDescription = "Live AI Cat Companion",
                                    tint = Color(0xFFF472B6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Gemma 4 Copilot launcher (Circular, clear cyan glow)
                            FilledTonalIconButton(
                                onClick = { showAiAssistantSheet = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), CircleShape),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    contentColor = Color(0xFF38BDF8)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Gemma 4 Copilot",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color(0xFF38BDF8)
                    )
                )
            }
        },
        bottomBar = {
            if (!isCatFaceMode) {
                NavigationBar(
                    containerColor = Color(0xFF0F172A),
                    tonalElevation = 6.dp
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1E293B)
                    )

                    AppDestination.items.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = itemColors
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCatFaceMode) PaddingValues(0.dp) else paddingValues)
                .background(Color(0xFF0B1220))
        ) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Drive.route
            ) {
                composable(AppDestination.Drive.route) {
                    DriveScreen(viewModel = actualDriveViewModel)
                }
                composable(AppDestination.Tracker.route) {
                    TrackerScreen(viewModel = actualTrackerViewModel)
                }
                composable(AppDestination.Scopes.route) {
                    TelemetryScreen(viewModel = actualTelemetryViewModel)
                }
                composable(AppDestination.Tuning.route) {
                    TuningScreen(viewModel = actualTuningViewModel)
                }
                composable(AppDestination.History.route) {
                    HistoryScreen(viewModel = actualHistoryViewModel)
                }
            }
        }
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            connectionState = connectionState,
            onDismiss = { showConnectionDialog = false },
            onConnect = { host, port ->
                actualDriveViewModel.connect(host, port)
            },
            onDisconnect = {
                actualDriveViewModel.disconnect()
            }
        )
    }

    if (showAiAssistantSheet) {
        RobotAiAssistantSheet(
            viewModel = actualAssistantViewModel,
            onDismissRequest = { showAiAssistantSheet = false },
            onOpenCatLab = {
                catLabViewModel.refreshModelStatus()
                showCatAiLabScreen = true
            }
        )
    }

    if (showCatAiLabScreen) {
        CatAiLabScreen(
            viewModel = catLabViewModel,
            onBack = { showCatAiLabScreen = false }
        )
    }
}

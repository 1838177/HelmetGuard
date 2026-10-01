package com.helmet.guard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.helmet.guard.presentation.countdown.AccidentCountdownOverlay
import com.helmet.guard.presentation.device.DeviceScreen
import com.helmet.guard.presentation.device.DeviceViewModel
import com.helmet.guard.presentation.emergency.EmergencyContactScreen
import com.helmet.guard.presentation.emergency.EmergencyViewModel
import com.helmet.guard.presentation.history.HistoryScreen
import com.helmet.guard.presentation.history.HistoryViewModel
import com.helmet.guard.presentation.home.HomeScreen
import com.helmet.guard.presentation.home.HomeViewModel
import com.helmet.guard.presentation.navigation.Screen
import com.helmet.guard.presentation.navigation.bottomNavScreens
import com.helmet.guard.presentation.selfcheck.SelfCheckScreen
import com.helmet.guard.presentation.selfcheck.SelfCheckViewModel
import com.helmet.guard.presentation.sensor.SensorScreen
import com.helmet.guard.presentation.sensor.SensorViewModel
import com.helmet.guard.presentation.settings.SettingsScreen
import com.helmet.guard.presentation.settings.SettingsViewModel
import com.helmet.guard.presentation.theme.HelmetGuardTheme
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.service.HelmetGuardService
import com.helmet.guard.service.SosAccessibilityService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as HelmetGuardApp
        val repo = app.repository
        val prefs = app.preferences

        // 不在 onCreate 强行调用前台服务，待权限就绪后平滑启动

        setContent {
            val themeSetting by prefs.themeMode.collectAsState(initial = 2)
            val isDark = when (themeSetting) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            HelmetGuardTheme(darkTheme = isDark) {
                MainAppContent(app)
            }
        }
    }
}

@Composable
fun MainAppContent(app: HelmetGuardApp) {
    val navController = rememberNavController()
    val repo = app.repository
    val prefs = app.preferences
    val context = androidx.compose.ui.platform.LocalContext.current

    val homeViewModel = remember { HomeViewModel(repo) }
    var showSmsPermissionDialog by remember { mutableStateOf(false) }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    // 动态权限申请
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            HelmetGuardService.startService(context)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            repo.setAccidentDetectionEnabled(true)
            HelmetGuardService.startService(context)
        } else {
            // 系统或用户拒绝/屏蔽权限，弹出引导去系统设置的手动修改弹窗
            showSmsPermissionDialog = true
        }
    }

    fun requestAutomaticAlerting(enabled: Boolean) {
        if (!enabled) {
            repo.setAccidentDetectionEnabled(false)
        } else if (hasSmsPermission()) {
            repo.setAccidentDetectionEnabled(true)
            HelmetGuardService.startService(context)
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Avoid showing the same permission sheet on every app launch.
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            HelmetGuardService.startService(context)
        }
    }

    // 全局事故状态监听
    val accidentState by repo.accidentState.collectAsState()
    val countdownLeft by repo.countdownSecondsLeft.collectAsState()
    val deviceInfo by repo.deviceInfo.collectAsState()
    val isMockMode by repo.isMockMode.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 控制底部导航栏是否在当前页面显示 (仅在 4 个主 Tab 显示)
    val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        bottomNavScreens.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                icon = {
                                    screen.icon?.let {
                                        Icon(imageVector = it, contentDescription = screen.title)
                                    }
                                },
                                label = { Text(text = screen.title) },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = StatusSafe,
                                    selectedTextColor = StatusSafe,
                                    indicatorColor = StatusSafe.copy(alpha = 0.12f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 }
                },
                exitTransition = {
                    fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 16 }
                },
                popEnterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(180)) { -it / 12 }
                },
                popExitTransition = {
                    fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { it / 16 }
                }
            ) {
                // 1. 首页
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToSensor = { navigateToTab(Screen.Sensor.route) },
                        onNavigateToHistory = { navController.navigate(Screen.History.route) },
                        onNavigateToDevice = { navigateToTab(Screen.Device.route) },
                        onToggleDetection = ::requestAutomaticAlerting
                    )
                }

                // 2. 传感器看板
                composable(Screen.Sensor.route) {
                    val sensorViewModel = SensorViewModel(repo)
                    SensorScreen(
                        viewModel = sensorViewModel,
                        onNavigateBack = { navigateToTab(Screen.Home.route) }
                    )
                }

                // 3. 设备管理
                composable(Screen.Device.route) {
                    val deviceViewModel = DeviceViewModel(repo)
                    DeviceScreen(
                        viewModel = deviceViewModel,
                        onNavigateToSelfCheck = { navController.navigate(Screen.SelfCheck.route) },
                        onNavigateBack = { navigateToTab(Screen.Home.route) }
                    )
                }

                // 4. 设置
                composable(Screen.Settings.route) {
                    val settingsViewModel = remember { SettingsViewModel(repo, prefs) }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onToggleDetection = ::requestAutomaticAlerting,
                        onNavigateToEmergency = { navController.navigate(Screen.EmergencyContacts.route) },
                        onNavigateToHistory = { navController.navigate(Screen.History.route) },
                        onNavigateToSelfCheck = { navController.navigate(Screen.SelfCheck.route) }
                    )
                }

                // 二级页面：紧急联系人
                composable(
                    route = Screen.EmergencyContacts.route,
                    enterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)) },
                    exitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(180)) },
                    popEnterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)) }
                ) {
                    val emergencyViewModel = remember { EmergencyViewModel(repo) }
                    EmergencyContactScreen(
                        viewModel = emergencyViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 二级页面：历史记录
                composable(
                    route = Screen.History.route,
                    enterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)) },
                    exitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(180)) },
                    popEnterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)) }
                ) {
                    val historyViewModel = HistoryViewModel(repo)
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 二级页面：10 项设备自检
                composable(
                    route = Screen.SelfCheck.route,
                    enterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)) },
                    exitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(180)) },
                    popEnterTransition = { slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)) }
                ) {
                    val selfCheckViewModel = SelfCheckViewModel(repo, context)
                    SelfCheckScreen(
                        viewModel = selfCheckViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // 全屏防误触倒计时报警遮罩 (最高层级)
        AccidentCountdownOverlay(
            state = accidentState,
            secondsLeft = countdownLeft,
            batteryPercent = deviceInfo.batteryPercent ?: 0,
            isMockMode = isMockMode,
            onCancelAlarm = { repo.cancelAlarm(isFromHelmet = false) }
        )

        // 短信权限手动授权引导弹窗
        if (showSmsPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showSmsPermissionDialog = false },
                title = {
                    Text(
                        text = "需要短信发送权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "开启事故自动报警后，系统在检测到强烈碰撞或摔倒时，需要向预设联系人自动发送求救短信与实时位置。\n\n手机系统当前已拦截该权限请求，请点击下方按钮前往系统设置手动开启“发送短信”权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSmsPermissionDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "前往系统设置", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSmsPermissionDialog = false },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "暂不开启", fontSize = 13.sp)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

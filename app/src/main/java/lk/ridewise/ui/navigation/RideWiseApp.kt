package lk.ridewise.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import lk.ridewise.capture.screencapture.CaptureState
import lk.ridewise.ui.RideWiseViewModel
import lk.ridewise.ui.capture.CaptureScreen
import lk.ridewise.ui.dashboard.DashboardScreen
import lk.ridewise.ui.history.HistoryScreen
import lk.ridewise.ui.review.ReviewScreen
import lk.ridewise.ui.results.ResultsScreen
import lk.ridewise.ui.settings.SettingsScreen
import lk.ridewise.live.LiveAssistantStore
import lk.ridewise.ui.live.LiveAssistantScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RideWiseApp(model: RideWiseViewModel, liveStore: LiveAssistantStore, openLive: Boolean, consumeOpenLive: () -> Unit,
    onNotificationAccess: () -> Unit, onOverlayAccess: () -> Unit, onLiveNotifications: () -> Unit,
    onLiveStart: () -> Unit, onLiveStop: () -> Unit, onLiveSources: (Boolean, Boolean) -> Unit, onLiveDemo: (Boolean) -> Unit,
    openCapture: Boolean, consumeOpenCapture: () -> Unit,
    onStartCapture: () -> Unit, onCaptureOnce: () -> Unit, onStopCapture: () -> Unit, onNotificationSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val capture by model.capture.state.collectAsStateWithLifecycle()
    val live by liveStore.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val snack = remember { SnackbarHostState() }
    fun navigate(destination: String) { nav.navigate(destination) { launchSingleTop = true } }
    fun home() { nav.navigate("dashboard") { popUpTo("dashboard") { inclusive = true }; launchSingleTop = true } }
    fun demo() { model.demo { navigate("review") } }
    fun manual() { model.manual(); navigate("review") }
    fun open(id: String) { model.openAnalysis(id); navigate("results") }
    LaunchedEffect(openCapture) { if (openCapture) { navigate("capture"); consumeOpenCapture() } }
    LaunchedEffect(openLive) { if (openLive) { navigate("live"); consumeOpenLive() } }
    LaunchedEffect(capture) {
        if (capture is CaptureState.Extracted) { model.captured((capture as CaptureState.Extracted).ride); navigate("review") }
    }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snack.showSnackbar(it); model.clearMessage() }
    }
    val primary = route in listOf("dashboard", "live", "capture", "history", "settings")
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Route, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (primary) "RideWise" else when (route) { "review" -> "Review ride"; "assumptions" -> "Edit assumptions"; else -> "Ride analysis" }, fontWeight = FontWeight.Bold)
                }
            }, navigationIcon = { if (!primary) IconButton({ if (!nav.popBackStack()) home() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (route == "dashboard") IconButton({ navigate("settings") }) { Icon(Icons.Outlined.Settings, "Vehicle settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            if (primary) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("dashboard", "Home", Icons.Outlined.Home), Triple("live", "Live", Icons.Outlined.Bolt), Triple("capture", "Capture", Icons.Outlined.CropFree),
                    Triple("history", "History", Icons.Outlined.History), Triple("settings", "Vehicle", Icons.Outlined.DirectionsCar)).forEach { (destination, label, icon) ->
                    NavigationBarItem(selected = route == destination, onClick = {
                        nav.navigate(destination) { popUpTo("dashboard") { saveState = true }; launchSingleTop = true; restoreState = true }
                    }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }, snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            NavHost(navController = nav, startDestination = "dashboard", modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                composable("dashboard") { DashboardScreen(state, { navigate("capture") }, ::manual, ::demo, { navigate("history") }, { navigate("settings") }, ::open, { navigate("live") }) }
                composable("live") { LiveAssistantScreen(live, onNotificationAccess, onOverlayAccess, onLiveNotifications, onLiveStart, onLiveStop, onLiveSources, onLiveDemo) }
                composable("capture") { CaptureScreen(capture, state.busy, ::demo, ::manual, onStartCapture, onCaptureOnce, onStopCapture, onNotificationSettings) }
                composable("review") {
                    state.draft?.let { ReviewScreen(it) { ride -> model.calculate(ride); navigate("results") } } ?: MissingDraft(::home)
                }
                composable("results") {
                    state.analysis?.let { a -> ResultsScreen(a, state.history.any { it.id == a.id }, state.busy,
                        { model.save(::home) }, ::home, { navigate("review") }, { navigate("assumptions") }, { model.delete(a.id, ::home) }) } ?: MissingDraft(::home)
                }
                composable("history") { HistoryScreen(state.history, state.busy, ::open, { model.clear() }, ::demo) }
                composable("settings") {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(32.dp))
                    else SettingsScreen(state.settings, false, state.busy) { model.saveSettings(it, false) { nav.popBackStack() } }
                }
                composable("assumptions") {
                    state.analysis?.let { a -> SettingsScreen(a.settings, true, state.busy) {
                        model.saveSettings(it, true) { nav.popBackStack() }
                    } } ?: MissingDraft(::home)
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
@Composable private fun MissingDraft(onHome: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("No ride is open. Start a demo, manual entry or a new capture.")
        Button(onHome) { Text("Return to dashboard") }
    }
}

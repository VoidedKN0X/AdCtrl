package com.vknxworks.adctrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.ui.components.CtrlBottomBar
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.TabItem
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.components.rememberNotificationAccess
import com.vknxworks.adctrl.ui.clients.ClientScreen
import com.vknxworks.adctrl.ui.clients.ClientsScreen
import com.vknxworks.adctrl.ui.filters.FiltersScreen
import com.vknxworks.adctrl.ui.overview.OverviewScreen
import com.vknxworks.adctrl.ui.queries.CheckHostScreen
import com.vknxworks.adctrl.ui.queries.QueriesScreen
import com.vknxworks.adctrl.ui.settings.ServersScreen
import com.vknxworks.adctrl.ui.settings.NotificationsScreen
import com.vknxworks.adctrl.ui.settings.SettingsScreen
import com.vknxworks.adctrl.ui.setup.SetupScreen
import com.vknxworks.adctrl.ShortcutAction
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.ui.lock.lockCovering
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.vknxworks.adctrl.ui.settings.YouScreen
import com.vknxworks.adctrl.ui.dns.DnsSettingsScreen
import com.vknxworks.adctrl.ui.dns.LogsStatsScreen

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (servers.isEmpty()) {
            // First start (or every server removed): setup fills the screen, no tabs.
            SetupScreen(onDone = {}, onCancel = null)
        } else {
            MainScaffold()
        }
    }
}

private data class TopDest(
    val route: Any,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    val icon: ImageVector,
)

/** The four tabs. Overview is the only summary screen; account and settings open from the profile picture. */
private val topDestinations = listOf(
    TopDest(OverviewRoute, R.string.tab_overview, R.string.tab_overview_hint, Icons.Outlined.Dashboard),
    TopDest(QueriesRoute, R.string.tab_queries, R.string.tab_queries_hint, Icons.Outlined.Language),
    TopDest(FiltersRoute, R.string.tab_filters, R.string.tab_filters_hint, Icons.Outlined.FilterAlt),
    TopDest(ClientsRoute, R.string.tab_clients, R.string.tab_clients_hint, Icons.Outlined.Devices),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val tabs = topDestinations.map { TabItem(stringResource(it.label), it.icon) }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts: open the right tab once AdCtrl is unlocked; the tab itself does the rest
    // (the pause menu, the search field, the blocked filter).
    val c = LocalContext.current.container
    val shortcut by c.pendingShortcut.collectAsStateWithLifecycle()
    val covered = lockCovering(c.vault)
    LaunchedEffect(shortcut, covered, backStack != null) {
        val action = shortcut ?: return@LaunchedEffect
        if (covered || backStack == null) return@LaunchedEffect
        selectTab(if (action == ShortcutAction.PAUSE) 0 else 1)
    }

    // Android 13+ drops notifications until they're allowed: with background checks on, ask once,
    // as soon as AdCtrl is unlocked. After that, Notifications in Settings shows it and offers the fix.
    val notifySettings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
    LaunchedEffect(covered, notifySettings.polling, notificationAccess.granted) {
        if (!covered && notifySettings.polling) notificationAccess.askOnce()
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets, unfolded foldables and landscape: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        tabs.forEachIndexed { i, tab ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    tabs = tabs,
                    showTabs = selectedIndex >= 0,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating Ctrl tab bar.
 * The bar shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    tabs: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(items = tabs, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = OverviewRoute) {
        topDestinations.forEach { top ->
            when (top.route) {
                OverviewRoute -> composable<OverviewRoute> { OverviewScreen() }
                QueriesRoute -> composable<QueriesRoute> { QueriesScreen() }
                FiltersRoute -> composable<FiltersRoute> { FiltersScreen() }
                ClientsRoute -> composable<ClientsRoute> { ClientsScreen() }
            }
        }
        composable<ClientRoute> {
            val r = it.toRoute<ClientRoute>()
            ClientScreen(r.key, r.persistent)
        }
        composable<CheckHostRoute> {
            val r = it.toRoute<CheckHostRoute>()
            CheckHostScreen(r.host, r.client)
        }
        composable<YouRoute> { YouScreen() }
        composable<DnsSettingsRoute> { DnsSettingsScreen() }
        composable<LogsStatsRoute> { LogsStatsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<NotificationsRoute> { NotificationsScreen() }
        composable<ServersRoute> { ServersScreen() }
        composable<AddServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(onDone = { navController.popBackStack(OverviewRoute, false) }, onCancel = navigator::back)
        }
        composable<EditServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(editId = it.toRoute<EditServerRoute>().id, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}

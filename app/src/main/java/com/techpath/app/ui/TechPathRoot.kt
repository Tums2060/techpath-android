package com.techpath.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.techpath.app.AppContainer
import com.techpath.app.data.AppSettings
import com.techpath.app.data.Events
import com.techpath.app.ui.hub.ArticleScreen
import com.techpath.app.ui.hub.HubScreen
import com.techpath.app.ui.onboarding.OnboardingScreen
import com.techpath.app.ui.opportunities.OpportunitiesScreen
import com.techpath.app.ui.roadmap.CareerPickerScreen
import com.techpath.app.ui.roadmap.RoadmapScreen
import com.techpath.app.ui.roadmap.SkillDetailScreen
import com.techpath.app.ui.settings.AboutScreen
import com.techpath.app.ui.settings.SettingsScreen

object Routes {
    const val ROADMAP = "roadmap"
    const val OPPORTUNITIES = "opportunities"
    const val RESOURCES = "resources"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CAREER_PICKER = "career_picker"
    const val SKILL = "skill/{path}/{nodeId}"
    const val ARTICLE = "article/{id}"
    fun skill(path: String, nodeId: String) = "skill/${Uri.encode(path)}/${Uri.encode(nodeId)}"
    fun article(id: String) = "article/${Uri.encode(id)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.ROADMAP, "Roadmap", Icons.Outlined.Map),
    Tab(Routes.OPPORTUNITIES, "Opportunities", Icons.Outlined.Work),
    Tab(Routes.RESOURCES, "Resources", Icons.AutoMirrored.Outlined.MenuBook),
)

@Composable
fun TechPathRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = null)
    val s = settings
    when {
        s == null -> LoadingState("Starting TechPath…")
        !s.onboarded -> OnboardingScreen(container)
        else -> MainNav(container, s)
    }
}

@Composable
private fun MainNav(container: AppContainer, settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    TabTimeTracker(container, route)

    // Each screen draws its own top bar; this scaffold only adds the bottom tabs.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.ROADMAP, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.ROADMAP) {
                RoadmapScreen(
                    container, settings,
                    onOpenSkill = { p, id -> nav.navigate(Routes.skill(p, id)) },
                    onSwitchCareer = { nav.navigate(Routes.CAREER_PICKER) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.OPPORTUNITIES) { OpportunitiesScreen(container, onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.RESOURCES) { HubScreen(container, onOpen = { nav.navigate(Routes.article(it)) }, onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.SETTINGS) { SettingsScreen(container, onBack = { nav.popBackStack() }, onAbout = { nav.navigate(Routes.ABOUT) }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.CAREER_PICKER) { CareerPickerScreen(container, onDone = { nav.popBackStack() }) }
            composable(
                Routes.SKILL,
                arguments = listOf(navArgument("path") { type = NavType.StringType }, navArgument("nodeId") { type = NavType.StringType }),
            ) { e ->
                SkillDetailScreen(
                    container,
                    path = e.arguments?.getString("path").orEmpty(),
                    nodeId = e.arguments?.getString("nodeId").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ARTICLE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                ArticleScreen(container, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Logs time spent in each bottom tab (and stops the clock when the app is backgrounded). */
@Composable
private fun TabTimeTracker(container: AppContainer, route: String?) {
    val tabRoute = route?.takeIf { r -> tabs.any { it.route == r } }
    val clock = remember { TabClock() }
    LaunchedEffect(tabRoute) { clock.switchTo(tabRoute)?.let { (tab, ms) -> container.logger.log(Events.TAB_TIME, itemId = tab, durationMs = ms) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_STOP -> clock.pause()?.let { (tab, ms) -> container.logger.log(Events.TAB_TIME, itemId = tab, durationMs = ms) }
                Lifecycle.Event.ON_START -> clock.resume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

private class TabClock {
    private var tab: String? = null
    private var since = 0L

    /** Returns the finished (tab, duration) when the tab changes. */
    fun switchTo(newTab: String?): Pair<String, Long>? {
        val done = finish()
        tab = newTab
        since = System.currentTimeMillis()
        return done
    }

    fun pause(): Pair<String, Long>? = finish().also { since = 0L }
    fun resume() { if (tab != null) since = System.currentTimeMillis() }

    private fun finish(): Pair<String, Long>? {
        val t = tab ?: return null
        if (since == 0L) return null
        val ms = System.currentTimeMillis() - since
        return if (ms >= 500) t to ms else null
    }
}

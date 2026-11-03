package com.techpath.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.techpath.R
import com.techpath.ui.hub.HubRoute
import com.techpath.ui.onboarding.OnboardingRoute
import com.techpath.ui.opportunities.OpportunitiesRoute
import com.techpath.ui.roadmap.RoadmapRoute
import com.techpath.ui.settings.SettingsRoute
import com.techpath.ui.skill.SkillRoute

object Routes {
    const val ONBOARDING = "onboarding?edit={edit}"
    const val ROADMAP = "roadmap"
    const val OPPORTUNITIES = "opportunities"
    const val HUB = "hub"
    const val SKILL = "skill/{pathId}/{nodeId}"
    const val SETTINGS = "settings"

    fun onboarding(edit: Boolean) = "onboarding?edit=$edit"
    fun skill(pathId: String, nodeId: String) = "skill/${Uri.encode(pathId)}/${Uri.encode(nodeId)}"
}

/** A bottom-bar tab. To add a tab, add one entry to [TOP_LEVEL_DESTINATIONS]. */
data class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val content: @Composable (NavController) -> Unit,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.ROADMAP, R.string.tab_roadmap, R.drawable.ic_roadmap) { nav ->
        RoadmapRoute(
            onOpenSkill = { pathId, nodeId -> nav.navigate(Routes.skill(pathId, nodeId)) },
            onChangePath = { nav.navigate(Routes.onboarding(edit = true)) },
        )
    },
    TopLevelDestination(Routes.OPPORTUNITIES, R.string.tab_opportunities, R.drawable.ic_opportunities) { OpportunitiesRoute() },
    TopLevelDestination(Routes.HUB, R.string.tab_hub, R.drawable.ic_hub) { HubRoute() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechPathNavGraph(startOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = TOP_LEVEL_DESTINATIONS.firstOrNull { it.route == backStack?.destination?.route }

    Scaffold(
        topBar = {
            if (currentTab != null) {
                TopAppBar(
                    title = { Text(stringResource(currentTab.label)) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                        }
                    },
                )
            }
        },
        bottomBar = { if (currentTab != null) BottomBar(navController, currentTab) },
        // Screens outside the tabs draw their own app bar and handle insets themselves.
        contentWindowInsets = if (currentTab != null) ScaffoldDefaults.contentWindowInsets else WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Routes.ONBOARDING else TOP_LEVEL_DESTINATIONS.first().route,
            modifier = Modifier.padding(padding),
        ) {
            TOP_LEVEL_DESTINATIONS.forEach { tab -> composable(tab.route) { tab.content(navController) } }

            composable(
                Routes.ONBOARDING,
                arguments = listOf(navArgument("edit") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val edit = entry.arguments?.getBoolean("edit") ?: false
                OnboardingRoute(
                    onDone = {
                        if (edit) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(TOP_LEVEL_DESTINATIONS.first().route) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(
                Routes.SKILL,
                arguments = listOf(navArgument("pathId") { type = NavType.StringType }, navArgument("nodeId") { type = NavType.StringType }),
            ) {
                SkillRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onChangePath = { navController.navigate(Routes.onboarding(edit = true)) },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavController, current: TopLevelDestination) {
    NavigationBar {
        TOP_LEVEL_DESTINATIONS.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(TOP_LEVEL_DESTINATIONS.first().route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

package br.com.portosoftware.chamados.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.TopLevel
import br.com.portosoftware.chamados.core.model.User
import br.com.portosoftware.chamados.ui.calls.CallListRoute
import br.com.portosoftware.chamados.ui.detail.CallDetailRoute as CallDetailScreenRoute
import br.com.portosoftware.chamados.ui.home.HomeRoute as HomeScreenRoute
import br.com.portosoftware.chamados.ui.newcall.NewCallRoute as NewCallScreenRoute
import br.com.portosoftware.chamados.ui.profile.ProfileRoute as ProfileScreenRoute
import br.com.portosoftware.chamados.ui.progress.ProgressRoute as ProgressScreenRoute
import br.com.portosoftware.chamados.ui.stock.StockRoute as StockScreenRoute

private data class TopLevelItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val route: Any,
)

private val TopLevel.item: TopLevelItem
    get() =
        when (this) {
            TopLevel.HOME -> TopLevelItem("Início", Icons.Filled.Home, Icons.Outlined.Home, HomeRoute)
            TopLevel.CALLS -> TopLevelItem("Chamados", Icons.Filled.ConfirmationNumber, Icons.Outlined.ConfirmationNumber, CallsRoute())
            TopLevel.STOCK -> TopLevelItem("Estoque", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, StockRoute)
            TopLevel.PROFILE -> TopLevelItem("Perfil", Icons.Filled.Person, Icons.Outlined.Person, ProfileRoute)
        }

private fun NavDestination?.topLevel(): TopLevel? =
    when {
        this == null -> null
        hasRoute(HomeRoute::class) -> TopLevel.HOME
        hasRoute(CallsRoute::class) -> TopLevel.CALLS
        hasRoute(StockRoute::class) -> TopLevel.STOCK
        hasRoute(ProfileRoute::class) -> TopLevel.PROFILE
        else -> null
    }

/**
 * Só a tela do topo da pilha navega. Durante a animação de saída a tela anterior ainda recebe
 * toques: um segundo "voltar" tiraria também a tela de baixo e deixaria o NavHost vazio, e um
 * segundo toque num item empilharia a mesma tela duas vezes. Compara com o topo, e não com o
 * ciclo de vida, para que um resultado que chega com o app em segundo plano ainda navegue.
 */
private fun NavHostController.isTop(entry: NavBackStackEntry) = currentBackStackEntry?.id == entry.id

private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (isTop(entry)) popBackStack()
}

private fun NavHostController.navigateFrom(
    entry: NavBackStackEntry,
    route: Any,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isTop(entry)) {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) =
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

/**
 * App autenticado: barra de navegação adaptativa (NavigationBar no celular, Rail em telas
 * largas) com destinos definidos pelo [RolePolicy]. Detalhe, Atender e Novo chamado são
 * telas empilhadas, sem a barra.
 */
@Composable
fun MainScaffold(
    user: User,
    modifier: Modifier = Modifier,
) {
    val policy = remember(user.role) { RolePolicy(user.role) }
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentTop = backStackEntry?.destination.topLevel()
    val showBar = policy.showsNavigationBar && currentTop != null
    val openProfile: (() -> Unit)? = if (policy.showsNavigationBar) null else ({ nav.navigate(ProfileRoute) { launchSingleTop = true } })

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            policy.destinations.forEach { destination ->
                val item = destination.item
                val selected = currentTop == destination
                item(
                    selected = selected,
                    onClick = { nav.navigateTopLevel(item.route) },
                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        },
        layoutType =
            if (showBar) {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
            } else {
                NavigationSuiteType.None
            },
    ) {
        NavHost(navController = nav, startDestination = policy.start.item.route) {
            composable<HomeRoute> { entry ->
                HomeScreenRoute(
                    onOpenCall = { id -> nav.navigateFrom(entry, CallDetailRoute(id)) },
                    onOpenList = { preset -> nav.navigateFrom(entry, CallsRoute(preset?.name)) },
                    onNewCall = { nav.navigateFrom(entry, NewCallRoute) },
                    onOpenProfile = openProfile,
                )
            }
            composable<CallsRoute> { entry ->
                CallListRoute(
                    onOpenCall = { id -> nav.navigateFrom(entry, CallDetailRoute(id)) },
                    onNewCall = { nav.navigateFrom(entry, NewCallRoute) },
                    onOpenProfile = openProfile,
                )
            }
            composable<StockRoute> { StockScreenRoute(onOpenProfile = openProfile) }
            composable<ProfileRoute> { entry ->
                ProfileScreenRoute(onBack = if (policy.profileHasBack) ({ nav.popFrom(entry) }) else null)
            }
            composable<CallDetailRoute>(
                // Quem não tem a aba Chamados (VIEWER) também não abre chamado por link.
                deepLinks =
                    if (TopLevel.CALLS in policy.destinations) {
                        listOf(navDeepLink<CallDetailRoute>(basePath = "chamados://calls"))
                    } else {
                        emptyList()
                    },
            ) { entry ->
                val progressSaved by entry.savedStateHandle.getStateFlow(PROGRESS_SAVED_KEY, false).collectAsStateWithLifecycle()
                CallDetailScreenRoute(
                    progressSaved = progressSaved,
                    onProgressSavedConsume = { entry.savedStateHandle[PROGRESS_SAVED_KEY] = false },
                    onBack = { nav.popFrom(entry) },
                    onAttend = { nav.navigateFrom(entry, ProgressRoute(entry.toRoute<CallDetailRoute>().id)) },
                )
            }
            composable<ProgressRoute> { entry ->
                ProgressScreenRoute(
                    onClose = { nav.popFrom(entry) },
                    onSaveSuccess = {
                        if (nav.isTop(entry)) {
                            nav.previousBackStackEntry?.savedStateHandle?.set(PROGRESS_SAVED_KEY, true)
                            nav.popBackStack()
                        }
                    },
                )
            }
            composable<NewCallRoute> { entry ->
                NewCallScreenRoute(
                    onClose = { nav.popFrom(entry) },
                    onOpenCall = { id ->
                        nav.navigateFrom(entry, CallDetailRoute(id)) { popUpTo<NewCallRoute> { inclusive = true } }
                    },
                )
            }
        }
    }
}

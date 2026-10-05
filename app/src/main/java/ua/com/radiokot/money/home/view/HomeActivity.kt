/* Copyright 2025 Oleg Koretsky

   This file is part of the 4Money,
   a budget tracking Android app.

   4Money is free software: you can redistribute it
   and/or modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation, either version 3 of the License,
   or (at your option) any later version.

   4Money is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
   See the GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with 4Money. If not, see <http://www.gnu.org/licenses/>.
*/

package ua.com.radiokot.money.home.view

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.composeunstyled.Icon
import com.composeunstyled.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ua.com.radiokot.money.MoneyAppActivity
import ua.com.radiokot.money.MoneyAppModalBottomSheetHost
import ua.com.radiokot.money.R
import ua.com.radiokot.money.privacy.view.PrivacyModeButton
import ua.com.radiokot.money.accounts.view.AccountActionSheetRoute
import ua.com.radiokot.money.accounts.view.AccountsScreenRoute
import ua.com.radiokot.money.accounts.view.ArchivedAccountsActivity
import ua.com.radiokot.money.accounts.view.EditAccountActivity
import ua.com.radiokot.money.accounts.view.EditAccountScreenRoute
import ua.com.radiokot.money.accounts.view.accountActionSheet
import ua.com.radiokot.money.accounts.view.accountsScreen
import ua.com.radiokot.money.auth.logic.UserSessionScope
import ua.com.radiokot.money.categories.view.CategoriesScreenRoute
import ua.com.radiokot.money.categories.view.CategoryActionSheetRoute
import ua.com.radiokot.money.categories.view.EditCategoryActivity
import ua.com.radiokot.money.categories.view.EditCategoryScreenRoute
import ua.com.radiokot.money.categories.view.categoriesScreen
import ua.com.radiokot.money.categories.view.categoryActionSheet
import ua.com.radiokot.money.inbox.view.InboxCardsScreenRoute
import ua.com.radiokot.money.inbox.view.InboxCardsViewModel
import ua.com.radiokot.money.inbox.view.InboxScreenViewModel
import ua.com.radiokot.money.inbox.view.InboxTabItems
import ua.com.radiokot.money.inbox.view.InboxTabRoute
import ua.com.radiokot.money.inbox.sources.view.AutoBookActivity
import ua.com.radiokot.money.inbox.view.RulesScreenRoute
import ua.com.radiokot.money.inbox.view.RulesScreenViewModel
import ua.com.radiokot.money.inbox.view.inboxCardsScreen
import ua.com.radiokot.money.inbox.view.inboxTab
import ua.com.radiokot.money.inbox.view.rulesScreen
import ua.com.radiokot.money.lock.view.SetUpPasscodeActivity
import ua.com.radiokot.money.overview.view.CategoryStatsSheetRoute
import ua.com.radiokot.money.overview.view.OverviewScreenRoute
import ua.com.radiokot.money.overview.view.categoryStatsSheet
import ua.com.radiokot.money.overview.view.overviewScreen
import ua.com.radiokot.money.preferences.view.PreferencesScreenRoute
import ua.com.radiokot.money.preferences.view.preferencesScreen
import ua.com.radiokot.money.rememberMoneyAppNavController
import ua.com.radiokot.money.routeIs
import ua.com.radiokot.money.transfers.data.TransferCounterpartyId
import ua.com.radiokot.money.transfers.history.view.ActivityScreenRoute
import ua.com.radiokot.money.transfers.history.view.activityScreen
import ua.com.radiokot.money.transfers.view.TransferCounterpartySelectionSheetRoute
import ua.com.radiokot.money.transfers.view.TransfersNavigator
import ua.com.radiokot.money.transfers.view.transferCounterpartySelectionSheet
import ua.com.radiokot.money.transfers.view.transferSheet
import ua.com.radiokot.money.uikit.ScaleIndication
import ua.com.radiokot.money.uikit.theme.MoneyTheme
import ua.com.radiokot.money.theme.view.MoneyAppTheme

class HomeActivity : MoneyAppActivity(
    requiresSession = true,
    requiresUnlocking = true,
) {

    private val viewModel: HomeViewModel by viewModel()

    /**
     * Incremented whenever the inbox tab is requested by an intent.
     */
    private val openInboxRequest = MutableStateFlow(0)

    override fun onCreateAllowed(savedInstanceState: Bundle?) {

        // Do not reopen the tab on a recreation: the intent is the same.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) {
            openInboxRequest.value++
        }

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )

        setContent {
            MoneyAppTheme {
                UserSessionScope {
                    HomeScreen(
                        viewModel = viewModel,
                        openInboxRequest = openInboxRequest.collectAsState(),
                        goToAuth = ::goToAuth,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) {
            openInboxRequest.value++
        }
    }

    companion object {
        /**
         * Open the Inbox tab, e.g. from the "payments to sort" notification.
         */
        const val EXTRA_OPEN_INBOX = "open_inbox"
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun HomeScreen(
    viewModel: HomeViewModel,
    openInboxRequest: State<Int>,
    goToAuth: () -> Unit,
) {
    val navController = rememberMoneyAppNavController()
    val transfersNavigatorFactory = koinInject<TransfersNavigator.Factory>()
    val transfersNavigator = remember(transfersNavigatorFactory, navController) {
        transfersNavigatorFactory.create(
            isIncognito = false,
            navController = navController,
        )
    }
    val context = LocalContext.current
    val inboxViewModel: InboxScreenViewModel = koinViewModel()
    val inboxCardsViewModel: InboxCardsViewModel = koinViewModel()
    val rulesViewModel: RulesScreenViewModel = koinViewModel()
    val onProceedToInboxCategorySelection = { accountId: TransferCounterpartyId.Account, isIncome: Boolean ->
        navController.navigate(
            route = TransferCounterpartySelectionSheetRoute(
                // An income category is the source of an income.
                isForSource = isIncome,
                alreadySelectedCounterpartyId = accountId,
                showAccounts = false,
                showCategories = true,
            ),
        )
    }

    LaunchedEffect(openInboxRequest.value) {
        if (openInboxRequest.value > 0) {
            navController.navigateToTab(InboxTabRoute)
        }
    }

    Column(
        modifier = Modifier
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                    .add(WindowInsets.statusBars)
            )
    ) {
        val hasNotice by viewModel.hasMoreNotice.collectAsState()
        val onProfileClicked: () -> Unit = {
            if (navController.currentDestination?.route == PreferencesScreenRoute) {
                navController.navigateUp()
            } else {
                navController.navigate(PreferencesScreenRoute) {
                    launchSingleTop = true
                }
            }
        }

        CompositionLocalProvider(
            LocalHomeProfileButton provides {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileButton(
                        hasNotice = hasNotice,
                        onClick = onProfileClicked,
                    )
                    PrivacyModeButton()
                }
            },
        ) {
        NavHost(
            navController = navController,
            startDestination = AccountsScreenRoute,
            enterTransition = {
                fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))
            },
            exitTransition = { fadeOut(tween(150)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .displayCutoutPadding()
                // The bottom bar below takes the navigation bar,
                // the inbox screens must not add it once more.
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {

            accountsScreen(
                onProceedToAccountActions = { account ->
                    navController.navigate(
                        route = AccountActionSheetRoute(
                            accountId = account.id,
                        ),
                    )
                },
                onProceedToAccountAdd = {
                    context.startActivity(
                        Intent(context, EditAccountActivity::class.java)
                            .putExtras(
                                EditAccountActivity.getBundle(
                                    route = EditAccountScreenRoute(
                                        accountToEditId = null,
                                    ),
                                )
                            )
                    )
                },
                onProceedToArchivedAccounts = {
                    context.startActivity(
                        Intent(context, ArchivedAccountsActivity::class.java)
                    )
                }
            )

            categoriesScreen(
                homeViewModel = viewModel,
                onProceedToTransfer = transfersNavigator::proceedToTransfer,
                onProceedToCategoryActions = { category, statsPeriod ->
                    navController.navigate(
                        route = CategoryActionSheetRoute(
                            category = category,
                            statsPeriod = statsPeriod,
                        )
                    )
                },
                onProceedToCategoryAdd = { isIncome ->
                    context.startActivity(
                        Intent(context, EditCategoryActivity::class.java)
                            .putExtras(
                                EditCategoryActivity.getBundle(
                                    route = EditCategoryScreenRoute(
                                        categoryToEditId = null,
                                        isIncome = isIncome,
                                    ),
                                )
                            )
                    )
                },
            )

            categoryActionSheet(
                onProceedToEdit = { category ->
                    context.startActivity(
                        Intent(context, EditCategoryActivity::class.java)
                            .putExtras(
                                EditCategoryActivity.getBundle(
                                    route = EditCategoryScreenRoute(
                                        categoryToEdit = category,
                                    ),
                                )
                            )
                    )
                    navController.navigateUp()
                },
                onProceedToFilteredActivity = { categoryCounterparty ->
                    viewModel.filterActivityByCounterparty(
                        counterparty = categoryCounterparty,
                    )
                    navController.navigate(
                        route = ActivityScreenRoute,
                        navOptions = navOptions {
                            popUpTo(CategoriesScreenRoute) {
                                inclusive = true
                            }
                        }
                    )
                },
                onDone = navController::navigateUp,
            )

            activityScreen(
                homeViewModel = viewModel,
                onProceedToEditingTransfer = transfersNavigator::proceedToTransfer,
            )

            overviewScreen(
                homeViewModel = viewModel,
                onProceedToCategoryStats = { navController.navigate(it) },
            )

            categoryStatsSheet(
                onProceedToTransfer = { category ->
                    transfersNavigator.proceedToTransfer(
                        category = category,
                        navOptions = navOptions {
                            popUpTo<CategoryStatsSheetRoute> {
                                inclusive = true
                            }
                        },
                    )
                },
                onProceedToFilteredActivity = { categoryCounterparty ->
                    viewModel.filterActivityByCounterparty(
                        counterparty = categoryCounterparty,
                    )
                    navController.navigate(
                        route = ActivityScreenRoute,
                        navOptions = navOptions {
                            popUpTo(OverviewScreenRoute) {
                                inclusive = true
                            }
                        }
                    )
                },
            )

            preferencesScreen(
                onBack = navController::navigateUp,
                onProceedToPasscodeSetup = {
                    context.startActivity(
                        Intent(context, SetUpPasscodeActivity::class.java)
                    )
                },
                onProceedToAutoBook = {
                    context.startActivity(AutoBookActivity.getIntent(context))
                },
                onSignedOut = goToAuth,
            )

            inboxTab(
                viewModel = inboxViewModel,
                onProceedToCategorySelection = onProceedToInboxCategorySelection,
                onProceedToTransfer = { route -> navController.navigate(route) },
                onProceedToRules = { navController.navigate(RulesScreenRoute) },
                onProceedToCards = { navController.navigate(InboxCardsScreenRoute) },
            )

            inboxCardsScreen(
                viewModel = inboxCardsViewModel,
                onProceedToAmountRules = { request ->
                    rulesViewModel.onAddRangeForPayeeRequested(
                        payeePattern = request.payeePattern,
                        displayPattern = request.displayPattern,
                        currencyCode = request.currencyCode,
                        isIncome = request.isIncome,
                        categoryOptions = request.categoryOptions,
                    )
                    navController.navigate(RulesScreenRoute)
                },
                onProceedToCategorySelection = onProceedToInboxCategorySelection,
                onProceedToTransfer = { route -> navController.navigate(route) },
                onProceedToRules = { navController.navigate(RulesScreenRoute) },
                onClose = navController::navigateUp,
            )

            rulesScreen(
                viewModel = rulesViewModel,
                onProceedToCategorySelection = { isIncome ->
                    navController.navigate(
                        route = TransferCounterpartySelectionSheetRoute(
                            isForSource = isIncome,
                            alreadySelectedCounterpartyId = null,
                            showAccounts = false,
                            showCategories = true,
                        ),
                    )
                },
                onClose = navController::navigateUp,
            )

            accountActionSheet(
                onDone = navController::navigateUp,
                onProceedToExpense = { sourceAccountId ->
                    transfersNavigator.proceedToTransfer(
                        accountId = sourceAccountId,
                        isIncome = false,
                        navOptions = navOptions {
                            popUpTo<AccountActionSheetRoute> {
                                inclusive = true
                            }
                        },
                    )
                },
                onProceedToIncome = { destinationAccountId ->
                    transfersNavigator.proceedToTransfer(
                        accountId = destinationAccountId,
                        isIncome = true,
                        navOptions = navOptions {
                            popUpTo<AccountActionSheetRoute> {
                                inclusive = true
                            }
                        },
                    )
                },
                onProceedToTransfer = { sourceAccountId ->
                    transfersNavigator.proceedToTransfer(
                        accountId = sourceAccountId,
                        isIncome = null,
                        navOptions = navOptions {
                            popUpTo<AccountActionSheetRoute> {
                                inclusive = true
                            }
                        },
                    )
                },
                onProceedToFilteredActivity = { accountCounterparty ->
                    viewModel.filterActivityByCounterparty(
                        counterparty = accountCounterparty,
                    )
                    navController.navigate(
                        route = ActivityScreenRoute,
                        navOptions = navOptions {
                            popUpTo(AccountsScreenRoute) {
                                inclusive = true
                            }
                        }
                    )
                },
                onProceedToEdit = { accountId ->
                    context.startActivity(
                        Intent(context, EditAccountActivity::class.java)
                            .putExtras(
                                EditAccountActivity.getBundle(
                                    route = EditAccountScreenRoute(
                                        accountToEditId = accountId,
                                    ),
                                )
                            )
                    )
                    navController.navigateUp()
                }
            )

            transferSheet(
                onProceedToTransferCounterpartySelection = {
                        alreadySelectedCounterpartyId: TransferCounterpartyId,
                        selectSource: Boolean,
                        showCategories: Boolean,
                        showAccounts: Boolean,
                    ->
                    navController.navigate(
                        route = TransferCounterpartySelectionSheetRoute(
                            isForSource = selectSource,
                            alreadySelectedCounterpartyId = alreadySelectedCounterpartyId,
                            showCategories = showCategories,
                            showAccounts = showAccounts,
                        ),
                    )
                },
                onTransferDone = navController::navigateUp,
            )

            transferCounterpartySelectionSheet(
                onSelected = { result ->
                    // The inbox screens request a selection for themselves.
                    val previousDestination = navController.previousBackStackEntry?.destination
                    when {
                        previousDestination?.routeIs<RulesScreenRoute>() == true -> {
                            navController.navigateUp()
                            rulesViewModel.onCounterpartySelected(result)
                        }

                        previousDestination?.routeIs<InboxCardsScreenRoute>() == true -> {
                            navController.navigateUp()
                            inboxCardsViewModel.onCounterpartySelected(result)
                        }

                        previousDestination?.route == InboxTabRoute -> {
                            navController.navigateUp()
                            inboxViewModel.onCounterpartySelected(result)
                        }

                        else ->
                            transfersNavigator.proceedToTransfer(result)
                    }
                },
            )
        }

        }

        CompositionLocalProvider(
            LocalIndication provides remember(::ScaleIndication),
        ) {
            BottomNavigation(
                navController = navController,
                pendingInboxCount = viewModel.pendingInboxCount.collectAsState(),
            )
        }
    }

    MoneyAppModalBottomSheetHost(
        moneyAppNavController = navController,
    )
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun BottomNavigation(
    navController: NavController,
    pendingInboxCount: State<Long>,
) = Row(
    horizontalArrangement = Arrangement.SpaceAround,
    modifier = Modifier
        .fillMaxWidth()
        .background(MoneyTheme.colors.surface)
        // Do not use safeDrawingPadding() here
        // to avoid jumping behind bottom sheets
        // with soft keyboard open.
        .displayCutoutPadding()
        .navigationBarsPadding()
        .padding(
            top = 10.dp,
            bottom = 8.dp,
        )
) {
    // Specifically track the visited route within the bottom navigation routes
    // so the current entry doesn't loose indication when a bottom sheet appears.
    // Settings sit above the tabs, no tab is current there.
    val lastVisitedBottomRoute: String? by produceState<String?>(null) {
        navController
            .currentBackStackEntryFlow
            .collect { entry ->
                val route = entry.destination.route
                when {
                    route == PreferencesScreenRoute ->
                        value = null

                    route in bottomNavigationRoutes ->
                        value = route
                }
            }
    }

    listOf(
        Triple(stringResource(R.string.home_tab_accounts), R.drawable.ic_tabler_wallet, AccountsScreenRoute),
        Triple(stringResource(R.string.home_tab_categories), R.drawable.ic_tabler_chart_donut, CategoriesScreenRoute),
        Triple(stringResource(R.string.home_tab_inbox), R.drawable.ic_tabler_inbox, InboxTabRoute),
        Triple(stringResource(R.string.home_tab_history), R.drawable.ic_tabler_list_details, ActivityScreenRoute),
        Triple(stringResource(R.string.home_tab_overview), R.drawable.ic_tabler_chart_bar, OverviewScreenRoute),
    ).forEach { (text, icon, route) ->
        BottomNavigationEntry(
            text = text,
            icon = icon,
            isCurrent = lastVisitedBottomRoute == route
                    || (LocalInspectionMode.current && route == AccountsScreenRoute),
            badgeText =
                if (route == InboxTabRoute)
                    InboxTabItems.badgeText(pendingInboxCount.value)
                else
                    null,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { navController.navigateToTab(route) },
                )
        )
    }
}

/**
 * Bottom tabs replace each other; the preferences screen, opened from the profile icon,
 * sits above a tab and is closed when switching tabs.
 */
private fun NavController.navigateToTab(route: String) {
    // Also closes whatever sits above the tab: settings, the inbox cards, rules.
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

@Composable
private fun BottomNavigationEntry(
    modifier: Modifier = Modifier,
    text: String,
    @DrawableRes icon: Int,
    isCurrent: Boolean,
    badgeText: String? = null,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier,
) {
    val colors = MoneyTheme.colors
    val pillColor by animateColorAsState(
        targetValue =
            if (isCurrent)
                colors.accentTint
            else
                Color.Transparent,
        label = "tab-pill",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (isCurrent)
                colors.accent
            else
                colors.ink3,
        label = "tab-content",
    )
    val pillScaleX by animateFloatAsState(
        targetValue =
            if (isCurrent)
                1f
            else
                0.6f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "tab-pill-scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(
                width = 56.dp,
                height = 32.dp,
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = pillScaleX
                }
                .background(
                    color = pillColor,
                    shape = RoundedCornerShape(percent = 50),
                )
        )

        Icon(
            painter = painterResource(icon),
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
        )

        if (badgeText != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .background(
                        color = colors.accent,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MoneyTheme.typography.small,
                    color = colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }

    Text(
        text = text,
        style = MoneyTheme.typography.small,
        fontWeight =
            if (isCurrent)
                FontWeight.Bold
            else
                FontWeight.Medium,
        color = contentColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
    )
}

private val bottomNavigationRoutes: Set<String> = setOf(
    AccountsScreenRoute,
    CategoriesScreenRoute,
    InboxTabRoute,
    ActivityScreenRoute,
    OverviewScreenRoute,
)

@Preview(
    apiLevel = 34,
)
@Composable
private fun BottomNavigation2Preview(

) = BottomNavigation(
    navController = rememberNavController(),
    pendingInboxCount = 3L.let(::mutableStateOf),
)

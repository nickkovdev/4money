package ua.com.radiokot.money.categories.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFilter
import com.composeunstyled.Text
import ua.com.radiokot.money.privacy.logic.PrivacyAmounts
import ua.com.radiokot.money.privacy.view.LocalPrivacyMode
import java.math.BigInteger
import ua.com.radiokot.money.colors.view.ItemLogo
import ua.com.radiokot.money.currency.view.animateAmountValueAsState
import ua.com.radiokot.money.currency.view.rememberViewAmountFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.composeunstyled.Icon
import ua.com.radiokot.money.R
import ua.com.radiokot.money.uikit.theme.MoneyShapes
import ua.com.radiokot.money.uikit.theme.MoneyTheme

@Composable
fun CategoryGrid(
    modifier: Modifier = Modifier,
    itemList: State<List<ViewCategoryListItem>>,
    onItemClicked: ((ViewCategoryListItem) -> Unit)? = null,
    onItemLongClicked: ((ViewCategoryListItem) -> Unit)? = null,
    isAddShown: Boolean,
    onAddClicked: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(6.dp),
    /**
     * Total of the shown mode in the primary currency,
     * the denominator of the shares shown in the privacy mode.
     */
    currentModeTotal: BigInteger? = null,
) {
    val gridState = rememberLazyGridState()
    val spaceBy = 6.dp
    val visibleItemList = remember {
        derivedStateOf {
            itemList
                .value
                .fastFilter(ViewCategoryListItem::isNotArchived)
        }
    }
    val archiveItemList = remember {
        derivedStateOf {
            itemList
                .value
                .fastFilter(ViewCategoryListItem::isArchived)
        }
    }
    val isArchiveExpanded = remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spaceBy),
        verticalArrangement = Arrangement.spacedBy(spaceBy, Alignment.Top),
        state = gridState,
        modifier = modifier
    ) {
        categoryItems(
            itemList = visibleItemList,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            currentModeTotal = currentModeTotal,
        )

        if (isAddShown) {
            item(
                key = "add",
            ) {
                AddItem(
                    modifier = Modifier
                        .clickable(
                            onClick = { onAddClicked?.invoke() }
                        )
                )
            }
        }

        if (archiveItemList.value.isNotEmpty()) {
            item(
                key = "archive",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                ArchiveHeader(
                    isArchiveExpanded = isArchiveExpanded,
                )
            }

            if (isArchiveExpanded.value) {
                categoryItems(
                    itemList = archiveItemList,
                    onItemClicked = onItemClicked,
                    onItemLongClicked = onItemLongClicked,
                    currentModeTotal = currentModeTotal,
                )
            }
        }
    }

    // Ensure the archive is visible when expanded.
    LaunchedEffect(isArchiveExpanded.value) {
        if (isArchiveExpanded.value) {
            var firstArchivedItemIndex = visibleItemList.value.size + 1
            if (isAddShown) {
                firstArchivedItemIndex++
            }
            gridState.animateScrollToItem(firstArchivedItemIndex)
        }
    }
}

private fun LazyGridScope.categoryItems(
    itemList: State<List<ViewCategoryListItem>>,
    onItemClicked: ((ViewCategoryListItem) -> Unit)?,
    onItemLongClicked: ((ViewCategoryListItem) -> Unit)?,
    currentModeTotal: BigInteger?,
) {
    items(
        items = itemList.value,
        key = ViewCategoryListItem::key,
    ) { item ->
        CategoryListItem(
            item = item,
            currentModeTotal = currentModeTotal,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        onItemClicked?.invoke(item)
                    },
                    onLongClick = {
                        onItemLongClicked?.invoke(item)
                    },
                )
        )
    }
}

@Composable
internal fun ArchiveHeader(
    isArchiveExpanded: MutableState<Boolean>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MoneyShapes.medium)
            .clickable(
                onClick = {
                    isArchiveExpanded.value = !isArchiveExpanded.value
                },
            )
            .padding(
                vertical = 10.dp,
                horizontal = 8.dp,
            )
            .fillMaxWidth()
    ) {
        Text(
            text = "ARCHIVED",
            style = MoneyTheme.typography.overline,
            color = MoneyTheme.colors.ink3,
            modifier = Modifier
                .weight(1f),
        )

        Icon(
            painter = painterResource(
                if (isArchiveExpanded.value)
                    R.drawable.ic_tabler_chevron_up
                else
                    R.drawable.ic_tabler_chevron_down
            ),
            contentDescription =
                if (isArchiveExpanded.value)
                    "Collapse"
                else
                    "Expand",
            tint = MoneyTheme.colors.ink3,
            modifier = Modifier
                .size(18.dp)
        )
    }
}

/**
 * A grid cell: tinted logo tile, the title under it (shrinks to fit one line),
 * then the neutral amount, muted when zero.
 */
@Composable
internal fun CategoryListItem(
    modifier: Modifier = Modifier,
    item: ViewCategoryListItem,
    currentModeTotal: BigInteger? = null,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
        .clip(MoneyShapes.medium)
        .padding(
            vertical = 4.dp,
        ),
) {
    val title = item.title
    val amount = item.amount

    ItemLogo(
        title = title,
        colorScheme = item.colorScheme,
        icon = item.icon,
        modifier = Modifier
            .size(LOGO_SIZE_DP.dp)
    )

    Text(
        text = title,
        style = MoneyTheme.typography.caption,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 10.sp,
            maxFontSize = 13.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 2.dp,
            )
    )

    if (!item.isIncognito) {
        val amountFormat = rememberViewAmountFormat()
        val animatedAmountValue = animateAmountValueAsState(
            targetAmount = amount,
        )
        val isZero = animatedAmountValue.value.signum() == 0

        val amountColor =
            if (isZero)
                MoneyTheme.colors.ink3
            else
                MoneyTheme.colors.ink2

        Text(
            text =
                if (LocalPrivacyMode.current)
                    // The share of the period total instead of the amount,
                    // no currency symbol.
                    amountFormat.privateText(
                        text = PrivacyAmounts.shareText(
                            part = item.amountInPrimaryCurrency ?: BigInteger.ZERO,
                            total = currentModeTotal,
                        ),
                        value = animatedAmountValue.value,
                        customColor = amountColor,
                    )
                else
                    amountFormat(
                        value = animatedAmountValue.value,
                        currency = amount.currency,
                        customColor = amountColor,
                    ),
            style = MoneyTheme.typography.small,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
        )
    } else {
        Text(
            text = amount.currency.symbol,
            style = MoneyTheme.typography.small,
            color = MoneyTheme.colors.ink3,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
internal fun AddItem(
    modifier: Modifier = Modifier,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
        .clip(MoneyShapes.medium)
        .padding(
            vertical = 4.dp,
        ),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(LOGO_SIZE_DP.dp)
            .background(
                color = MoneyTheme.colors.surface,
                shape = MoneyShapes.itemTile,
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_plus),
            contentDescription = null,
            tint = MoneyTheme.colors.accent,
            modifier = Modifier
                .size(24.dp)
        )
    }

    Text(
        text = "Add",
        style = MoneyTheme.typography.caption,
        fontWeight = FontWeight.Medium,
        color = MoneyTheme.colors.ink2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
    )
}

@Composable
@Preview(
    apiLevel = 34,
    widthDp = 200,
)
private fun CategoryGridPreview(
) {
    val itemList = ViewCategoryListItemPreviewParameterProvider()
        .values
        .toList()

    CategoryGrid(
        itemList = itemList.let(::mutableStateOf),
        onItemClicked = {},
        onItemLongClicked = {},
        isAddShown = true,
        onAddClicked = {},
        modifier = Modifier
            .fillMaxWidth()
    )
}

private const val LOGO_SIZE_DP = 52

package com.example.receiptreminder.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.ui.theme.SafeGreenBg
import com.example.receiptreminder.ui.theme.SafeGreenBgDark
import com.example.receiptreminder.ui.theme.UrgentOrange
import com.example.receiptreminder.ui.theme.UrgentOrangeBg
import com.example.receiptreminder.ui.theme.UrgentOrangeBgDark
import com.example.receiptreminder.ui.theme.UrgentOrangeDark
import com.example.receiptreminder.ui.theme.WarningBrown
import com.example.receiptreminder.ui.theme.WarningBrownBg
import com.example.receiptreminder.ui.theme.WarningBrownBgDark
import com.example.receiptreminder.ui.theme.WarningBrownDark

@Composable
fun ShoppingListScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToScan: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShoppingListViewModel(container.shoppingListRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    ShoppingListContent(
        state = state,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToScan = onNavigateToScan,
        onPurchased = viewModel::onPurchased,
        onStillHave = viewModel::onStillHave,
        onDoNotBuy = viewModel::onDoNotBuy,
        onRetry = viewModel::retry,
        onConsumeActionError = viewModel::consumeActionError,
        onOpenAdd = viewModel::openAddForm,
        onDismissAdd = viewModel::dismissAddForm,
        onNameChange = viewModel::onNameChange,
        onDateChange = viewModel::onDateChange,
        onQuantityChange = viewModel::onQuantityChange,
        onSubmitAdd = viewModel::submitAddForm
    )
}

@Composable
private fun warningColor(): Color =
    if (isSystemInDarkTheme()) WarningBrownDark else WarningBrown

@Composable
private fun warningBgColor(): Color =
    if (isSystemInDarkTheme()) WarningBrownBgDark else WarningBrownBg

@Composable
private fun urgentAccentColor(): Color =
    if (isSystemInDarkTheme()) UrgentOrangeDark else UrgentOrange

@Composable
private fun urgentAccentBgColor(): Color =
    if (isSystemInDarkTheme()) UrgentOrangeBgDark else UrgentOrangeBg

@Composable
private fun safeAccentBgColor(): Color =
    if (isSystemInDarkTheme()) SafeGreenBgDark else SafeGreenBg

private fun categoryIconForName(name: String): ImageVector = when {
    listOf("牛乳", "お茶", "水", "コーヒー", "ジュース", "豆乳", "麦茶", "コーラ", "ビール", "酒", "飲料").any { name.contains(it) } ->
        Icons.Filled.LocalDrink
    listOf("洗剤", "漂白", "掃除", "スポンジ", "柔軟剤", "ハンドソープ", "クリーナー", "消臭", "除菌").any { name.contains(it) } ->
        Icons.Filled.CleaningServices
    listOf("ティッシュ", "トイレット", "ペーパ", "ラップ", "ゴミ袋", "ホイル", "袋", "フィルター", "シート").any { name.contains(it) } ->
        Icons.Filled.Layers
    listOf("米", "パン", "卵", "納豆", "豆腐", "肉", "野菜", "チーズ", "ヨーグルト", "ハム", "ウインナー", "麺", "パスタ", "うどん").any { name.contains(it) } ->
        Icons.Filled.Restaurant
    listOf("醤油", "しょうゆ", "みりん", "油", "塩", "砂糖", "マヨ", "味噌", "みそ", "ソース", "ケチャップ", "酢", "だし", "ドレッシング", "ポン酢").any { name.contains(it) } ->
        Icons.Filled.SoupKitchen
    listOf("シャンプー", "石鹸", "歯", "ボディ", "化粧", "リンス", "コンディショナー", "洗顔", "入浴", "綿棒", "マスク").any { name.contains(it) } ->
        Icons.Filled.Spa
    else -> Icons.Filled.ShoppingBag
}

@Composable
fun ShoppingListContent(
    state: ShoppingListUiState,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToScan: () -> Unit,
    onPurchased: (String) -> Unit,
    onStillHave: (String) -> Unit,
    onDoNotBuy: (String) -> Unit,
    onRetry: () -> Unit,
    onConsumeActionError: () -> Unit,
    onOpenAdd: () -> Unit,
    onDismissAdd: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onSubmitAdd: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailedMessage = stringResource(R.string.list_action_failed)
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            onConsumeActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onNavigateToScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 1.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.list_scan_receipt),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.list_title),
                    style = MaterialTheme.typography.headlineLarge
                )
                OutlinedButton(
                    onClick = onOpenAdd,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.list_add_manual),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!state.isEmpty && !state.isLoading && !state.loadFailed) {
                SummaryBannerRow(
                    urgentCount = state.urgent.size,
                    safeCount = state.safe.size
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.isLoading -> CenterMessage {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.list_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.loadFailed -> CenterMessage {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.list_error_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.list_error_retry))
                    }
                }

                state.isEmpty -> CenterMessage {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCartCheckout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.list_empty_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.list_empty_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.urgent.isNotEmpty()) {
                        item(key = "header-urgent") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = warningColor(),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.list_section_urgent, state.urgent.size),
                                    color = warningColor(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        items(state.urgent, key = { "u-" + it.productId }) { row ->
                            ShoppingItemCard(
                                row = row,
                                urgent = true,
                                onItemClick = { onNavigateToDetail(row.productId) },
                                onPurchased = { onPurchased(row.productId) },
                                onStillHave = { onStillHave(row.productId) },
                                onDoNotBuy = { onDoNotBuy(row.productId) }
                            )
                        }
                    }
                    if (state.safe.isNotEmpty()) {
                        item(key = "header-safe") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.list_section_safe),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        items(state.safe, key = { "s-" + it.productId }) { row ->
                            ShoppingItemCard(
                                row = row,
                                urgent = false,
                                onItemClick = { onNavigateToDetail(row.productId) },
                                onPurchased = { onPurchased(row.productId) },
                                onStillHave = { onStillHave(row.productId) },
                                onDoNotBuy = { onDoNotBuy(row.productId) }
                            )
                        }
                    }
                    item(key = "footer") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    state.addForm?.let { form ->
        AddPurchaseDialog(
            form = form,
            onDismiss = onDismissAdd,
            onNameChange = onNameChange,
            onDateChange = onDateChange,
            onQuantityChange = onQuantityChange,
            onSubmit = onSubmitAdd
        )
    }
}

@Composable
private fun SummaryBannerRow(
    urgentCount: Int,
    safeCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Urgent stat block
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = warningBgColor(),
            border = BorderStroke(1.dp, warningColor().copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = warningColor().copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = warningColor(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.list_section_urgent, urgentCount),
                        color = warningColor(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${urgentCount}品目",
                        color = warningColor(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Safe stat block
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = safeAccentBgColor(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.list_section_safe),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${safeCount}品目",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun remainingText(days: Int): String = when {
    days < 0 -> stringResource(R.string.list_remaining_overdue, -days)
    days == 0 -> stringResource(R.string.list_remaining_today)
    else -> stringResource(R.string.list_remaining_days, days)
}

@Composable
fun ShoppingItemCard(
    row: ShoppingRow,
    urgent: Boolean,
    onItemClick: () -> Unit,
    onPurchased: () -> Unit,
    onStillHave: () -> Unit,
    onDoNotBuy: () -> Unit
) {
    val isOverdueOrToday = row.remainingDays <= 0
    val accentColor = when {
        isOverdueOrToday -> urgentAccentColor()
        urgent -> warningColor()
        else -> MaterialTheme.colorScheme.primary
    }
    val badgeBgColor = when {
        isOverdueOrToday -> urgentAccentBgColor()
        urgent -> warningBgColor()
        else -> safeAccentBgColor()
    }
    val stockProgress = if (row.remainingDays <= 0) {
        0.05f
    } else {
        ((row.remainingDays.coerceIn(0, 14)) / 14f).coerceIn(0.08f, 1f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (urgent) 2.dp else 1.dp),
        border = BorderStroke(
            width = if (urgent) 1.5.dp else 1.dp,
            color = if (urgent) accentColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = badgeBgColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryIconForName(row.name),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.name,
                                fontWeight = if (urgent) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (row.isEstimated) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.list_estimated),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { stockProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (row.remainingDays <= 0) Icons.Filled.WarningAmber else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = remainingText(row.remainingDays),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPurchased,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.list_action_purchased),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onStillHave,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.list_action_still_have),
                        fontSize = 13.sp
                    )
                }
                TextButton(
                    onClick = onDoNotBuy,
                    modifier = Modifier.height(42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.list_action_do_not_buy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPurchaseDialog(
    form: AddFormState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val nameError = form.nameError?.let {
        when (it) {
            NameError.EMPTY -> stringResource(R.string.list_add_error_name_empty)
            NameError.TOO_LONG ->
                stringResource(R.string.list_add_error_name_too_long, ShoppingListViewModel.MAX_NAME_LENGTH)
        }
    }
    val dateError = form.dateError?.let {
        when (it) {
            DateError.INVALID -> stringResource(R.string.list_add_error_date_invalid)
            DateError.FUTURE -> stringResource(R.string.list_add_error_date_future)
        }
    }
    val quantityError = form.quantityError?.let {
        when (it) {
            QuantityError.INVALID -> stringResource(R.string.list_add_error_quantity_invalid)
            QuantityError.OUT_OF_RANGE ->
                stringResource(R.string.list_add_error_quantity_range, ShoppingListViewModel.MAX_QUANTITY)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.list_add_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.date,
                    onValueChange = onDateChange,
                    label = { Text(stringResource(R.string.list_add_date)) },
                    singleLine = true,
                    isError = dateError != null,
                    supportingText = dateError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.quantity,
                    onValueChange = onQuantityChange,
                    label = { Text(stringResource(R.string.list_add_quantity)) },
                    singleLine = true,
                    isError = quantityError != null,
                    supportingText = quantityError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.isSaving) {
                Text(stringResource(R.string.list_add_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.list_add_cancel))
            }
        }
    )
}

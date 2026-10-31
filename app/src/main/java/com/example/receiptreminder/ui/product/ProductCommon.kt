package com.example.receiptreminder.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiptreminder.R
import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.ui.theme.ErrorRed
import com.example.receiptreminder.ui.theme.WarningBrown
import com.example.receiptreminder.ui.theme.WarningBrownDark

/** ダークテーマでも読める警告色 */
@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) WarningBrownDark else WarningBrown

@Composable
fun dangerColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else ErrorRed

@Composable
fun productStatusText(product: Product): String = when (product.status) {
    StockStatus.OUT_OF_STOCK_OR_OVERDUE -> stringResource(R.string.product_status_overdue, -product.remainingDays)
    StockStatus.RUNNING_OUT -> stringResource(R.string.product_status_running_out, product.remainingDays)
    StockStatus.SUFFICIENT -> stringResource(R.string.product_status_sufficient, product.remainingDays)
    StockStatus.NOT_TRACKED -> stringResource(R.string.product_status_untracked)
}

@Composable
fun productStatusColor(product: Product): Color = when (product.status) {
    StockStatus.OUT_OF_STOCK_OR_OVERDUE -> dangerColor()
    StockStatus.RUNNING_OUT -> warningColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 商品名からカテゴリに応じたアイコンを返す */
private fun categoryIconForName(name: String): ImageVector = when {
    listOf("牛乳", "お茶", "水", "飲料", "コーヒー", "ジュース", "豆乳", "麦茶", "緑茶").any { name.contains(it) } ->
        Icons.Filled.LocalDrink
    listOf("洗剤", "掃除", "漂白", "柔軟剤", "スポンジ", "クリーナー").any { name.contains(it) } ->
        Icons.Filled.CleaningServices
    listOf("ティッシュ", "トイレット", "紙", "ラップ", "袋", "ホイル", "ペーパー").any { name.contains(it) } ->
        Icons.Filled.Layers
    listOf("米", "卵", "たまご", "パン", "豆腐", "納豆", "麺", "うどん", "そば").any { name.contains(it) } ->
        Icons.Filled.Restaurant
    listOf("醤油", "しょうゆ", "油", "塩", "砂糖", "味噌", "みそ", "マヨ", "ケチャップ", "酢", "だし").any { name.contains(it) } ->
        Icons.Filled.Kitchen
    listOf("シャンプー", "石鹸", "歯", "リンス", "ソープ", "入浴", "化粧").any { name.contains(it) } ->
        Icons.Filled.Spa
    else -> Icons.Filled.Inventory2
}

/** 商品カテゴリと在庫状態に応じた丸型アイコンバッジ */
@Composable
fun ProductCategoryAvatar(
    name: String,
    status: StockStatus,
    size: Dp = 44.dp
) {
    val tint = when (status) {
        StockStatus.OUT_OF_STOCK_OR_OVERDUE -> dangerColor()
        StockStatus.RUNNING_OUT -> warningColor()
        StockStatus.SUFFICIENT -> MaterialTheme.colorScheme.primary
        StockStatus.NOT_TRACKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when (status) {
        StockStatus.OUT_OF_STOCK_OR_OVERDUE -> dangerColor().copy(alpha = 0.14f)
        StockStatus.RUNNING_OUT -> warningColor().copy(alpha = 0.15f)
        StockStatus.SUFFICIENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        StockStatus.NOT_TRACKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, tint.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIconForName(name),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/** 残り日数の割合を視覚化するプログレスバー */
@Composable
fun StockLevelBar(product: Product) {
    val ratio = (product.remainingDays.toFloat() / product.cycleDays.coerceAtLeast(1.0).toFloat())
        .coerceIn(0.06f, 1f)
    val barColor = when (product.status) {
        StockStatus.OUT_OF_STOCK_OR_OVERDUE -> dangerColor()
        StockStatus.RUNNING_OUT -> warningColor()
        StockStatus.SUFFICIENT -> MaterialTheme.colorScheme.primary
        StockStatus.NOT_TRACKED -> MaterialTheme.colorScheme.outlineVariant
    }
    LinearProgressIndicator(
        progress = { ratio },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        strokeCap = StrokeCap.Round
    )
}

/** 「推定」「予測対象外」などの小さなラベル */
@Composable
fun SmallBadge(text: String) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        content()
    }
}

@Composable
fun MessageBlock(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CardColumn(modifier = Modifier.padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun BadgeRow(vararg badges: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach { SmallBadge(it) }
    }
}

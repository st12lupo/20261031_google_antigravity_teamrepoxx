package com.example.receiptreminder.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

/** 「推定」「予測対象外」などの小さなラベル */
@Composable
fun SmallBadge(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun BadgeRow(vararg badges: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach { SmallBadge(it) }
    }
}

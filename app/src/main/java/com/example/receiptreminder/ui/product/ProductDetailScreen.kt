package com.example.receiptreminder.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource
import java.time.LocalDate

@Composable
fun ProductDetailScreen(
    productId: String?,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ProductDetailViewModel = viewModel(
        key = "product-detail-$productId",
        factory = viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    productId = productId,
                    productRepository = container.productRepository,
                    shoppingListRepository = container.shoppingListRepository
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    val messageText = message?.let { productMessageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.product_back),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            when (val content = state.content) {
                ProductDetailContent.Loading -> item {
                    Text(
                        stringResource(R.string.product_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProductDetailContent.NotFound -> item {
                    MessageBlock(text = stringResource(R.string.product_not_found))
                }
                ProductDetailContent.Error -> item {
                    MessageBlock(
                        text = stringResource(R.string.product_detail_error),
                        actionLabel = stringResource(R.string.product_retry),
                        onAction = viewModel::retry
                    )
                }
                is ProductDetailContent.Loaded -> loadedItems(
                    product = content.product,
                    purchases = content.purchases,
                    onPurchased = viewModel::markPurchased,
                    onSnooze = viewModel::snooze,
                    onStop = viewModel::stopTracking,
                    onAdd = viewModel::startAddPurchase,
                    onEdit = viewModel::startEditPurchase,
                    onDelete = viewModel::requestDelete,
                    onTrackedChange = viewModel::setTracked
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    state.editor?.let { editor ->
        PurchaseEditorDialog(
            editor = editor,
            onDateChange = { viewModel.updateEditor(dateText = it) },
            onQuantityChange = { viewModel.updateEditor(quantityText = it) },
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor
        )
    }
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.product_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.product_delete_body,
                        formatFullDate(target.purchasedOn),
                        stringResource(R.string.product_quantity_value, target.quantity)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.product_delete_confirm), color = dangerColor())
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.product_editor_cancel))
                }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loadedItems(
    product: Product,
    purchases: List<PurchaseRecord>,
    onPurchased: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTrackedChange: (Boolean) -> Unit
) {
    item {
        CardColumn {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ProductCategoryAvatar(
                        name = product.name,
                        status = product.status,
                        size = 56.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusColor = productStatusColor(product)
                            val chipShape = RoundedCornerShape(10.dp)
                            Text(
                                text = productStatusText(product),
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.12f), chipShape)
                                    .border(1.dp, statusColor.copy(alpha = 0.28f), chipShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            if (!product.isTracked) {
                                SmallBadge(stringResource(R.string.product_badge_untracked))
                            }
                        }
                    }
                }
                if (product.isTracked) {
                    StockLevelBar(product = product)
                }
            }
        }
    }

    if (product.isTracked) {
        item {
            CardColumn {
                DetailRow(
                    icon = Icons.Filled.Event,
                    label = stringResource(R.string.product_row_out_date),
                    value = formatShortDate(product.predictedOutDate),
                    showDivider = true
                )
                DetailRow(
                    icon = Icons.Filled.Autorenew,
                    label = stringResource(R.string.product_row_cycle),
                    value = stringResource(R.string.product_cycle_value, formatCycleDays(product.cycleDays)),
                    badge = if (product.isEstimated) stringResource(R.string.product_badge_estimated) else null,
                    showDivider = true
                )
                DetailRow(
                    icon = Icons.Filled.NotificationsActive,
                    label = stringResource(R.string.product_row_became_low),
                    value = product.becameLowDate?.let { formatShortDate(it) }
                        ?: stringResource(R.string.product_row_none),
                    showDivider = false
                )
            }
        }
    }

    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPurchased,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.product_action_purchased))
            }
            OutlinedButton(
                onClick = onSnooze,
                enabled = product.isTracked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.product_action_snooze))
            }
        }
    }

    item {
        Text(
            text = stringResource(R.string.product_history_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    item {
        CardColumn {
            if (purchases.isEmpty()) {
                Text(
                    text = stringResource(R.string.product_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            purchases.forEachIndexed { index, record ->
                HistoryRow(
                    record = record,
                    showDivider = index < purchases.lastIndex,
                    onEdit = { onEdit(record.id) },
                    onDelete = { onDelete(record.id) }
                )
            }
        }
    }
    item {
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.product_add_purchase), fontSize = 14.sp)
        }
    }

    item {
        Text(
            text = stringResource(R.string.product_settings_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    item {
        CardColumn {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.product_tracked_toggle), fontSize = 15.sp)
                Checkbox(checked = product.isTracked, onCheckedChange = onTrackedChange)
            }
            if (product.isTracked) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                TextButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.product_action_stop),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.product_action_stop_hint),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    showDivider: Boolean,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) SmallBadge(badge)
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
    if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun HistoryRow(
    record: PurchaseRecord,
    showDivider: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceIcon = when (record.source) {
        PurchaseSource.RECEIPT -> Icons.AutoMirrored.Filled.ReceiptLong
        PurchaseSource.MANUAL -> Icons.Filled.EditNote
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sourceIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatFullDate(record.purchasedOn),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = sourceLabel(record),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            val pillShape = RoundedCornerShape(12.dp)
            Text(
                text = stringResource(R.string.product_quantity_value, record.quantity),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), pillShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), pillShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.product_edit_purchase))
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = dangerColor(),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.product_delete_purchase), color = dangerColor())
            }
        }
    }
    if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun PurchaseEditorDialog(
    editor: PurchaseEditorState,
    onDateChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.editingId == null) R.string.product_editor_title_add
                    else R.string.product_editor_title_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.dateText,
                    onValueChange = onDateChange,
                    label = { Text(stringResource(R.string.product_editor_date)) },
                    singleLine = true,
                    isError = editor.dateError != null,
                    supportingText = editor.dateError?.let { error ->
                        {
                            Text(
                                stringResource(
                                    if (error == DateInputError.FUTURE) R.string.product_editor_error_date_future
                                    else R.string.product_editor_error_date_invalid
                                )
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = editor.quantityText,
                    onValueChange = onQuantityChange,
                    label = { Text(stringResource(R.string.product_editor_quantity)) },
                    singleLine = true,
                    isError = editor.quantityError,
                    supportingText = if (editor.quantityError) {
                        { Text(stringResource(R.string.product_editor_error_quantity)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.product_editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.product_editor_cancel)) }
        }
    )
}

@Composable
private fun sourceLabel(record: PurchaseRecord): String = when (record.source) {
    PurchaseSource.MANUAL -> stringResource(R.string.product_source_manual)
    PurchaseSource.RECEIPT -> record.storeName?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.product_source_receipt_store, it) }
        ?: stringResource(R.string.product_source_receipt)
}

@Composable
private fun formatShortDate(date: LocalDate): String =
    stringResource(R.string.product_date_short, date.monthValue, date.dayOfMonth)

@Composable
private fun formatFullDate(date: LocalDate): String =
    stringResource(R.string.product_date_full, date.year, date.monthValue, date.dayOfMonth)

@Composable
private fun productMessageText(message: ProductMessage): String = stringResource(
    when (message) {
        ProductMessage.PURCHASED -> R.string.product_msg_purchased
        ProductMessage.SNOOZED -> R.string.product_msg_snoozed
        ProductMessage.STOPPED -> R.string.product_msg_stopped
        ProductMessage.TRACKED_ON -> R.string.product_msg_tracked_on
        ProductMessage.TRACKED_OFF -> R.string.product_msg_tracked_off
        ProductMessage.ADDED -> R.string.product_msg_added
        ProductMessage.UPDATED -> R.string.product_msg_updated
        ProductMessage.DELETED -> R.string.product_msg_deleted
        ProductMessage.FAILED -> R.string.product_msg_failed
    }
)

package com.example.receiptreminder.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.ui.theme.WarningBrown
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onNavigateToScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.list_scan_receipt),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.list_title),
                    style = MaterialTheme.typography.headlineLarge
                )
                OutlinedButton(
                    onClick = onOpenAdd,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(stringResource(R.string.list_add_manual), fontSize = 14.sp)
                }
            }

            when {
                state.isLoading -> CenterMessage {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.list_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.loadFailed -> CenterMessage {
                    Text(
                        stringResource(R.string.list_error_title),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(R.string.list_error_retry))
                    }
                }

                state.isEmpty -> CenterMessage {
                    Text(
                        stringResource(R.string.list_empty_title),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.list_empty_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.urgent.isNotEmpty()) {
                        item(key = "header-urgent") {
                            Text(
                                text = stringResource(R.string.list_section_urgent, state.urgent.size),
                                color = warningColor(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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
                            Text(
                                text = stringResource(R.string.list_section_safe),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
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
                    Text(
                        text = row.name,
                        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.isEstimated) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.list_estimated),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = remainingText(row.remainingDays),
                    color = if (urgent) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
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
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.list_action_purchased))
                }
                OutlinedButton(
                    onClick = onStillHave,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(stringResource(R.string.list_action_still_have))
                }
                TextButton(
                    onClick = onDoNotBuy,
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        stringResource(R.string.list_action_do_not_buy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
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

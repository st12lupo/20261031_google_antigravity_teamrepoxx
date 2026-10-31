package com.example.receiptreminder.ui.store

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.ui.theme.ErrorRed

@Composable
fun StoresScreen(
    onNavigateToExplainer: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: StoresViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StoresViewModel(
                    storeRepository = container.storeRepository,
                    notificationSettingsRepository = container.notificationSettingsRepository,
                    geofenceManager = container.geofenceManager
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    StoresContent(
        state = state,
        onNavigateToExplainer = onNavigateToExplainer,
        canStartRegistration = viewModel::canStartRegistration,
        onRegister = viewModel::registerStore,
        onUpdate = viewModel::updateStore,
        onToggle = viewModel::setNotifyEnabled,
        onDelete = viewModel::deleteStore,
        onSetTimeRange = viewModel::setTimeRange,
        onClearAll = viewModel::clearAllData,
        onDismissError = viewModel::dismissError
    )
}

/** 開いているダイアログ。対象の店舗は ID で持つ */
private enum class DialogKind { None, Explain, Name, Edit, Delete, TimeRange, ClearAll }

@Composable
private fun StoresContent(
    state: StoresUiState,
    onNavigateToExplainer: () -> Unit,
    canStartRegistration: () -> Boolean,
    onRegister: (String) -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSetTimeRange: (String, String) -> Unit,
    onClearAll: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(DialogKind.None) }
    var targetStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var permissionStatus by remember { mutableStateOf(StorePermissionPlanner.read(context)) }

    fun refreshPermissions() {
        permissionStatus = StorePermissionPlanner.read(context)
    }

    // 設定アプリから戻ったときに、権限の状態を読み直す
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = StorePermissionPlanner.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 段階 2: バックグラウンド位置情報。拒否されても前景位置情報があれば登録へ進み、
    // 通知が使えないことは画面で説明する
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
        if (permissionStatus.foregroundLocation) dialog = DialogKind.Name
    }
    // 段階 1: 前景位置情報(Android 13 以上では通知権限も同時に)
    val firstLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
        val status = permissionStatus
        when {
            !status.foregroundLocation -> Unit
            StorePermissionPlanner.needsBackgroundRequest(status, Build.VERSION.SDK_INT) ->
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> dialog = DialogKind.Name
        }
    }

    fun startRegistration() {
        if (!canStartRegistration()) return
        refreshPermissions()
        dialog = if (StorePermissionPlanner.shouldExplain(permissionStatus, permissionAsked)) {
            DialogKind.Explain
        } else {
            DialogKind.Name
        }
    }

    val targetStore = state.stores.firstOrNull { it.id == targetStoreId }

    // 対象の店舗が消えたら(削除など)、変更や削除のダイアログを閉じる
    LaunchedEffect(dialog, targetStore) {
        if ((dialog == DialogKind.Edit || dialog == DialogKind.Delete) && targetStore == null) {
            dialog = DialogKind.None
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.store_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = stringResource(R.string.store_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            state.error?.let { error ->
                item {
                    ErrorCard(message = errorMessage(error), onDismiss = onDismissError)
                }
            }

            item {
                Button(
                    onClick = ::startRegistration,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.store_register_button),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val showDenied = !permissionStatus.storeNotificationsAvailable &&
                (permissionAsked || state.stores.isNotEmpty())
            if (showDenied) {
                item {
                    PermissionDeniedCard(
                        status = permissionStatus,
                        onOpenSettings = { openAppSettings(context) },
                        onLearnMore = onNavigateToExplainer
                    )
                }
            }

            when {
                !state.isLoaded -> item { LoadingRow() }
                state.isEmpty -> item { EmptyStoresCard() }
                else -> {
                    items(state.stores, key = { it.id }) { store ->
                        StoreCard(
                            store = store,
                            enabled = !state.isBusy,
                            onToggle = { onToggle(store.id, it) },
                            onEdit = {
                                targetStoreId = store.id
                                dialog = DialogKind.Edit
                            },
                            onDelete = {
                                targetStoreId = store.id
                                dialog = DialogKind.Delete
                            }
                        )
                    }
                    item {
                        Text(
                            text = stringResource(
                                R.string.store_count_summary,
                                state.stores.size,
                                StoreLimits.MAX_STORES
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.store_settings_section),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.store_time_range_label), fontSize = 15.sp)
                        TextButton(
                            onClick = { dialog = DialogKind.TimeRange },
                            enabled = !state.isBusy
                        ) {
                            Text(
                                text = state.timeRange.displayText,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 15.sp
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    TextButton(
                        onClick = { dialog = DialogKind.ClearAll },
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.store_clear_all),
                            color = dangerColor(),
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    when (dialog) {
        DialogKind.None -> Unit

        DialogKind.Explain -> PermissionExplainDialog(
            onDismiss = { dialog = DialogKind.None },
            onProceed = {
                permissionAsked = true
                val status = StorePermissionPlanner.read(context)
                val request = StorePermissionPlanner.firstRequest(status, Build.VERSION.SDK_INT)
                dialog = DialogKind.None
                when {
                    request.isNotEmpty() -> firstLauncher.launch(request.toTypedArray())
                    StorePermissionPlanner.needsBackgroundRequest(status, Build.VERSION.SDK_INT) ->
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else -> dialog = DialogKind.Name
                }
            }
        )

        DialogKind.Name -> NameDialog(
            onDismiss = { dialog = DialogKind.None },
            onConfirm = { name ->
                dialog = DialogKind.None
                onRegister(name)
            }
        )

        DialogKind.Edit -> if (targetStore != null) {
            EditStoreDialog(
                store = targetStore,
                onDismiss = { dialog = DialogKind.None },
                onConfirm = { name, radius ->
                    dialog = DialogKind.None
                    onUpdate(targetStore.id, name, radius)
                }
            )
        }

        DialogKind.Delete -> if (targetStore != null) {
            ConfirmDialog(
                title = stringResource(R.string.store_delete_dialog_title),
                body = stringResource(R.string.store_delete_dialog_body, targetStore.name),
                confirmLabel = stringResource(R.string.store_action_delete),
                onDismiss = { dialog = DialogKind.None },
                onConfirm = {
                    dialog = DialogKind.None
                    onDelete(targetStore.id)
                }
            )
        }

        DialogKind.ClearAll -> ConfirmDialog(
            title = stringResource(R.string.store_clear_dialog_title),
            body = stringResource(R.string.store_clear_dialog_body),
            confirmLabel = stringResource(R.string.store_action_delete),
            onDismiss = { dialog = DialogKind.None },
            onConfirm = {
                dialog = DialogKind.None
                onClearAll()
            }
        )

        DialogKind.TimeRange -> TimeRangeDialog(
            current = state.timeRange,
            onDismiss = { dialog = DialogKind.None },
            onConfirm = { start, end ->
                dialog = DialogKind.None
                onSetTimeRange(start, end)
            }
        )
    }
}

private val DangerOnDark = Color(0xFFFF8F84)

/** ErrorRed は暗い背景では読みにくいので、ダークテーマでは明るい赤にする */
@Composable
private fun dangerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DangerOnDark else ErrorRed

@StringRes
private fun inputErrorRes(error: InputError): Int = when (error) {
    InputError.NameEmpty -> R.string.store_input_error_name_empty
    InputError.NameTooLong -> R.string.store_input_error_name_too_long
    InputError.RadiusNotNumber -> R.string.store_input_error_radius_not_number
    InputError.RadiusOutOfRange -> R.string.store_input_error_radius_range
    InputError.TimeFormat -> R.string.store_input_error_time_format
    InputError.TimeOrder -> R.string.store_input_error_time_order
}

@Composable
private fun inputErrorText(error: InputError): String = when (error) {
    InputError.NameTooLong ->
        stringResource(inputErrorRes(error), StoreLimits.MAX_NAME_LENGTH)
    InputError.RadiusOutOfRange ->
        stringResource(
            inputErrorRes(error),
            StoreLimits.MIN_RADIUS_METERS,
            StoreLimits.MAX_RADIUS_METERS
        )
    else -> stringResource(inputErrorRes(error))
}

@Composable
private fun errorMessage(error: StoresError): String = when (error) {
    StoresError.StoreLimit -> stringResource(R.string.store_error_limit, StoreLimits.MAX_STORES)
    StoresError.LocationUnavailable -> stringResource(R.string.store_error_location)
    StoresError.GeofenceRegisterFailed -> stringResource(R.string.store_error_geofence_register)
    StoresError.GeofenceUnregisterFailed -> stringResource(R.string.store_error_geofence_unregister)
    StoresError.SaveFailed -> stringResource(R.string.store_error_save)
    StoresError.DeleteFailed -> stringResource(R.string.store_error_delete)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // 設定画面を開けない端末でも落ちないようにする
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.store_loading), fontSize = 14.sp)
    }
}

@Composable
private fun EmptyStoresCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.store_empty_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = stringResource(R.string.store_empty_body),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, dangerColor())
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Text(text = message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.store_action_close), fontSize = 14.sp)
                }
            }
        }
    }
}

/** 権限が足りず、店舗の通知が使えないことの説明(N-5) */
@Composable
private fun PermissionDeniedCard(
    status: StorePermissionStatus,
    onOpenSettings: () -> Unit,
    onLearnMore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.store_denied_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(text = stringResource(R.string.store_denied_body), fontSize = 14.sp)
            if (!status.foregroundLocation) {
                Text(stringResource(R.string.store_denied_foreground), fontSize = 14.sp)
            }
            if (!status.backgroundLocation) {
                Text(stringResource(R.string.store_denied_background), fontSize = 14.sp)
            }
            if (!status.notification) {
                Text(stringResource(R.string.store_denied_notification), fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.store_denied_open_settings), fontSize = 14.sp)
                }
                TextButton(onClick = onLearnMore) {
                    Text(stringResource(R.string.store_denied_learn_more), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun StoreCard(
    store: Store,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            // 通知オフの店舗は背景色を変えて区別する(テーマの色なのでダークテーマでも読める)
            containerColor = if (store.notifyEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = store.notifyEnabled,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onToggle
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = store.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!store.notifyEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.store_notify_off_label),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Checkbox(
                    checked = store.notifyEnabled,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.store_radius_label, store.radiusMeters),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEdit,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.store_edit),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.store_delete),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionExplainDialog(onDismiss: () -> Unit, onProceed: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_permission_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.store_permission_dialog_body), fontSize = 15.sp)
                Text(stringResource(R.string.store_permission_point_1), fontSize = 14.sp)
                Text(stringResource(R.string.store_permission_point_2), fontSize = 14.sp)
                Text(stringResource(R.string.store_permission_point_3), fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text(stringResource(R.string.store_permission_proceed), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_permission_not_now))
            }
        }
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val error = StoreInputValidator.validateName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_register_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.store_register_dialog_body,
                        StoreLimits.DEFAULT_RADIUS_METERS
                    ),
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.store_name_label)) },
                    singleLine = true,
                    isError = submitted && error != null,
                    supportingText = {
                        if (submitted && error != null) Text(inputErrorText(error))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (error == null) onConfirm(name.trim())
            }) {
                Text(stringResource(R.string.store_action_register), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.store_action_cancel)) }
        }
    )
}

@Composable
private fun EditStoreDialog(
    store: Store,
    onDismiss: () -> Unit,
    onConfirm: (name: String, radius: String) -> Unit
) {
    var name by rememberSaveable(store.id) { mutableStateOf(store.name) }
    var radius by rememberSaveable(store.id) { mutableStateOf(store.radiusMeters.toString()) }
    var submitted by rememberSaveable(store.id) { mutableStateOf(false) }
    val nameError = StoreInputValidator.validateName(name)
    val radiusError = StoreInputValidator.validateRadius(radius)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_edit_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.store_name_label)) },
                    singleLine = true,
                    isError = submitted && nameError != null,
                    supportingText = {
                        if (submitted && nameError != null) Text(inputErrorText(nameError))
                    }
                )
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it },
                    label = {
                        Text(
                            stringResource(
                                R.string.store_radius_input_label,
                                StoreLimits.MIN_RADIUS_METERS,
                                StoreLimits.MAX_RADIUS_METERS
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = submitted && radiusError != null,
                    supportingText = {
                        if (submitted && radiusError != null) Text(inputErrorText(radiusError))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (nameError == null && radiusError == null) onConfirm(name.trim(), radius.trim())
            }) {
                Text(stringResource(R.string.store_action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.store_action_cancel)) }
        }
    )
}

@Composable
private fun TimeRangeDialog(
    current: NotificationTimeRange,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String) -> Unit
) {
    var start by rememberSaveable {
        mutableStateOf("%d:%02d".format(current.startHour, current.startMinute))
    }
    var end by rememberSaveable {
        mutableStateOf("%d:%02d".format(current.endHour, current.endMinute))
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val error = StoreInputValidator.validateTimeRange(start, end)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_time_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.store_time_dialog_body), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text(stringResource(R.string.store_time_start_label)) },
                        singleLine = true,
                        isError = submitted && error != null,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text(stringResource(R.string.store_time_end_label)) },
                        singleLine = true,
                        isError = submitted && error != null,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (submitted && error != null) {
                    Text(
                        text = inputErrorText(error),
                        fontSize = 13.sp,
                        color = dangerColor()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (error == null) onConfirm(start.trim(), end.trim())
            }) {
                Text(stringResource(R.string.store_action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.store_action_cancel)) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, fontSize = 15.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = dangerColor(), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.store_action_cancel)) }
        }
    )
}

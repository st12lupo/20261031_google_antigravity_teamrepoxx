package com.example.receiptreminder.ui.review

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.ui.theme.WarningBrownBg
import com.example.receiptreminder.ui.theme.WarningBrownBgDark
import com.example.receiptreminder.ui.theme.WarningBrownDark
import com.example.receiptreminder.ui.theme.WarningBrownText
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private const val MAX_RECEIPT_PAGES = 10

@Composable
fun ReceiptReviewScreen(
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ReceiptReviewViewModel = viewModel(
        factory = ReceiptReviewViewModel.Factory(
            scanner = container.receiptScanner,
            service = container.receiptReviewService
        )
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.finished) {
        when (state.finished) {
            ReviewResult.Saved -> onSave()
            ReviewResult.Cancelled -> onCancel()
            null -> Unit
        }
    }
    BackHandler(enabled = !state.isSaving && state.finished == null) { viewModel.cancel() }

    val activity = LocalActivity.current
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages
        if (result.resultCode == Activity.RESULT_OK && pages != null) {
            viewModel.onImagesCaptured(pages.map { it.imageUri.toString() })
        }
    }
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_RECEIPT_PAGES)
    ) { uris ->
        viewModel.onImagesPicked(uris.map { it.toString() })
    }

    when (state.phase) {
        ReviewPhase.ChooseSource -> ChooseSourceContent(
            onCapture = {
                if (activity == null) {
                    viewModel.onCaptureUnavailable()
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(false)
                        .setPageLimit(MAX_RECEIPT_PAGES)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options)
                        .getStartScanIntent(activity)
                        .addOnSuccessListener { sender ->
                            scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                        .addOnFailureListener { viewModel.onCaptureUnavailable() }
                }
            },
            onPick = {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onManual = viewModel::startManualInput,
            onCancel = viewModel::cancel
        )
        ReviewPhase.Scanning -> ScanningContent(onCancel = viewModel::cancel)
        ReviewPhase.ScanFailed -> ScanFailedContent(
            onRetake = viewModel::chooseSourceAgain,
            onManual = viewModel::startManualInput,
            onCancel = viewModel::cancel
        )
        ReviewPhase.Editing -> EditingContent(state, viewModel)
    }
}

@Composable
private fun ChooseSourceContent(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    onManual: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.review_source_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.review_source_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_source_camera), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onPick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_source_gallery))
            }
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_manual_input))
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_cancel))
            }
        }
    }
}

@Composable
private fun ScanningContent(onCancel: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(96.dp),
                    strokeWidth = 3.dp
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.review_scanning),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.review_cancel))
            }
        }
    }
}

@Composable
private fun ScanFailedContent(onRetake: () -> Unit, onManual: () -> Unit, onCancel: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.review_scan_failed_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.review_scan_failed_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_retake), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_manual_input))
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_cancel))
            }
        }
    }
}

@Composable
private fun fieldErrorText(error: FieldError?, show: Boolean): String? {
    if (!show || error == null) return null
    return stringResource(
        when (error) {
            FieldError.Required -> R.string.review_error_required
            FieldError.InvalidDate -> R.string.review_error_date
            FieldError.InvalidNumber -> R.string.review_error_number
            FieldError.InvalidQuantity -> R.string.review_error_quantity
        }
    )
}

@Composable
private fun EditingContent(state: ReceiptReviewUiState, vm: ReceiptReviewViewModel) {
    val form = state.form
    val validation = state.validation
    val show = state.showErrors
    val dark = isSystemInDarkTheme()
    val warnBg = if (dark) WarningBrownBgDark else WarningBrownBg
    val warnText = if (dark) WarningBrownDark else WarningBrownText

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.saveFailed) {
                    Text(
                        text = stringResource(R.string.review_save_failed),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (show && validation.noItems) {
                    Text(
                        text = stringResource(R.string.review_error_no_items),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.hasCapturedImages || state.hasPickedImages) {
                    Text(
                        text = stringResource(
                            if (state.hasCapturedImages) R.string.review_image_deleted_notice
                            else R.string.review_image_kept_notice
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = vm::cancel,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.review_cancel)) }
                    Button(
                        onClick = vm::save,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (state.isSaving) R.string.review_saving else R.string.review_save
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
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
                        text = stringResource(R.string.review_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = stringResource(R.string.review_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            if (state.isDuplicate) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = warnBg)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = warnText,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.review_duplicate_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = warnText
                                )
                                Text(
                                    text = stringResource(R.string.review_duplicate_body),
                                    fontSize = 14.sp,
                                    color = warnText
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val storeError = fieldErrorText(validation.store, show)
                        OutlinedTextField(
                            value = form.storeName,
                            onValueChange = vm::onStoreNameChange,
                            label = { Text(stringResource(R.string.review_field_store)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            isError = storeError != null,
                            supportingText = storeError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val dateError = fieldErrorText(validation.date, show)
                            OutlinedTextField(
                                value = form.date,
                                onValueChange = vm::onDateChange,
                                label = { Text(stringResource(R.string.review_field_date)) },
                                placeholder = { Text(stringResource(R.string.review_field_date_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                isError = dateError != null,
                                supportingText = dateError?.let { { Text(it) } },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            val totalError = fieldErrorText(validation.total, show)
                            OutlinedTextField(
                                value = form.total,
                                onValueChange = vm::onTotalChange,
                                label = { Text(stringResource(R.string.review_field_total)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                isError = totalError != null,
                                supportingText = totalError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.review_items_count, form.items.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (form.items.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    ) {
                        form.items.forEachIndexed { index, item ->
                            ReceiptItemRow(
                                index = index + 1,
                                item = item,
                                errors = if (show) validation.items[item.id] else null,
                                onNameChange = { vm.onItemNameChange(item.id, it) },
                                onQuantityChange = { vm.onItemQuantityChange(item.id, it) },
                                onPriceChange = { vm.onItemPriceChange(item.id, it) },
                                onRemove = { vm.removeItem(item.id) }
                            )
                            if (index < form.items.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = vm::addItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.review_add_item), fontSize = 14.sp)
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun ReceiptItemRow(
    index: Int,
    item: ItemForm,
    errors: ItemErrors?,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (item.rawOcrText.isNotBlank()) {
            Text(
                text = item.rawOcrText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val nameError = fieldErrorText(errors?.name, errors != null)
            OutlinedTextField(
                value = item.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.review_field_item_name)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            val quantityError = fieldErrorText(errors?.quantity, errors != null)
            OutlinedTextField(
                value = item.quantity,
                onValueChange = onQuantityChange,
                label = { Text(stringResource(R.string.review_field_quantity)) },
                isError = quantityError != null,
                supportingText = quantityError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            val priceError = fieldErrorText(errors?.price, errors != null)
            OutlinedTextField(
                value = item.price,
                onValueChange = onPriceChange,
                label = { Text(stringResource(R.string.review_field_price)) },
                isError = priceError != null,
                supportingText = priceError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.review_item_delete)
                )
            }
        }
        val notice = item.mergedNotice
        val status = when {
            !item.isTracked -> stringResource(R.string.review_item_untracked)
            notice != null -> stringResource(R.string.review_item_tracked_with_notice, notice)
            else -> stringResource(R.string.review_item_tracked)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (item.isTracked) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (item.isTracked) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.RemoveCircleOutline
                },
                contentDescription = null,
                tint = if (item.isTracked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = status,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (item.isTracked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

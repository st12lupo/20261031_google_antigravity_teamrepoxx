package com.example.receiptreminder.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.StockStatus

@Composable
fun ProductListScreen(
    onNavigateToDetail: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ProductListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProductListViewModel(container.productRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    ProductListContent(
        state = state,
        onRetry = viewModel::retry,
        onNavigateToDetail = onNavigateToDetail
    )
}

@Composable
internal fun ProductListContent(
    state: ProductListUiState,
    onRetry: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.product_list_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            when (state) {
                ProductListUiState.Loading -> Text(
                    text = stringResource(R.string.product_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProductListUiState.Error -> MessageBlock(
                    text = stringResource(R.string.product_list_error),
                    actionLabel = stringResource(R.string.product_retry),
                    onAction = onRetry
                )
                is ProductListUiState.Success -> if (state.products.isEmpty()) {
                    ProductEmptyState()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item(key = "summary_overview") {
                            ProductSummaryOverviewRow(products = state.products)
                        }
                        items(state.products, key = { it.id }) { product ->
                            ProductRow(product) { onNavigateToDetail(product.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSummaryOverviewRow(products: List<Product>) {
    val totalCount = products.size
    val lowOrOverdueCount = products.count {
        it.status == StockStatus.RUNNING_OUT || it.status == StockStatus.OUT_OF_STOCK_OR_OVERDUE
    }
    val sufficientCount = products.count { it.status == StockStatus.SUFFICIENT }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Inventory2,
            count = totalCount,
            label = stringResource(R.string.product_list_title),
            accentColor = MaterialTheme.colorScheme.primary
        )
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.WarningAmber,
            count = lowOrOverdueCount,
            label = "要確認",
            accentColor = if (lowOrOverdueCount > 0) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant
        )
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CheckCircleOutline,
            count = sufficientCount,
            label = "余裕あり",
            accentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    count: Int,
    label: String,
    accentColor: Color
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProductEmptyState() {
    CardColumn(modifier = Modifier.padding(vertical = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(R.string.product_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.product_list_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    val statusIcon = when (product.status) {
        StockStatus.OUT_OF_STOCK_OR_OVERDUE -> Icons.Filled.ErrorOutline
        StockStatus.RUNNING_OUT -> Icons.Filled.WarningAmber
        StockStatus.SUFFICIENT -> Icons.Filled.CheckCircleOutline
        StockStatus.NOT_TRACKED -> Icons.Filled.RemoveCircleOutline
    }
    val statusColor = productStatusColor(product)

    CardColumn(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductCategoryAvatar(
                name = product.name,
                status = product.status,
                size = 44.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!product.isTracked) {
                        SmallBadge(stringResource(R.string.product_badge_untracked))
                    } else if (product.isEstimated) {
                        SmallBadge(stringResource(R.string.product_badge_estimated))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (product.isTracked) {
                            productStatusText(product)
                        } else {
                            stringResource(R.string.product_list_item_untracked)
                        },
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = if (product.status == StockStatus.OUT_OF_STOCK_OR_OVERDUE ||
                            product.status == StockStatus.RUNNING_OUT
                        ) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (product.isTracked) {
                    Spacer(modifier = Modifier.height(1.dp))
                    StockLevelBar(product = product)
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

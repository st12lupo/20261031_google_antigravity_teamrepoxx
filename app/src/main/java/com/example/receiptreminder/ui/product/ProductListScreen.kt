package com.example.receiptreminder.ui.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receiptreminder.R
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.domain.model.Product

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
                    Column(
                        modifier = Modifier.padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.product_list_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.product_list_empty_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun ProductRow(product: Product, onClick: () -> Unit) {
    CardColumn(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (product.isTracked) {
                        productStatusText(product)
                    } else {
                        stringResource(R.string.product_list_item_untracked)
                    },
                    color = productStatusColor(product),
                    fontSize = 13.sp
                )
            }
            if (!product.isTracked) {
                SmallBadge(stringResource(R.string.product_badge_untracked))
            } else if (product.isEstimated) {
                SmallBadge(stringResource(R.string.product_badge_estimated))
            }
        }
    }
}

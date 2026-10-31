package com.example.receiptreminder.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiptreminder.ui.theme.PrimaryGreen
import com.example.receiptreminder.ui.theme.PrimaryGreenLight
import com.example.receiptreminder.ui.theme.WarningBrown

@Composable
fun ShoppingListScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToScan: () -> Unit
) {
    Scaffold(
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
                        containerColor = Color(0xFF1B2623),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "レシートを撮影",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "買い物リスト",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    OutlinedButton(
                        onClick = { /* 手動追加ダイアログなど */ },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("手動で追加", fontSize = 14.sp)
                    }
                }
            }

            item {
                Text(
                    text = "なくなりそう 3 点",
                    color = WarningBrown,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // なくなりそうアイテム1
            item {
                UrgentShoppingItem(
                    name = "牛乳",
                    statusText = "1 日過ぎています",
                    onItemClick = { onNavigateToDetail("milk") },
                    onPurchased = { },
                    onStillHave = { },
                    onDoNotBuy = { }
                )
            }

            // なくなりそうアイテム2
            item {
                UrgentShoppingItem(
                    name = "食器用洗剤",
                    statusText = "あと 2 日",
                    onItemClick = { onNavigateToDetail("dish-soap") },
                    onPurchased = { },
                    onStillHave = { },
                    onDoNotBuy = { }
                )
            }

            // なくなりそうアイテム3
            item {
                UrgentShoppingItem(
                    name = "トイレットペーパー",
                    statusText = "あと 4 日",
                    onItemClick = { onNavigateToDetail("toilet-paper") },
                    onPurchased = { },
                    onStillHave = { },
                    onDoNotBuy = { }
                )
            }

            item {
                Text(
                    text = "まだ大丈夫",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // まだ大丈夫リスト
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    SafeShoppingItemRow(
                        name = "シャンプー",
                        remainingText = "あと 18 日",
                        isEstimated = false,
                        showDivider = true,
                        onClick = { onNavigateToDetail("shampoo") }
                    )
                    SafeShoppingItemRow(
                        name = "米",
                        remainingText = "あと 26 日",
                        isEstimated = true,
                        showDivider = false,
                        onClick = { onNavigateToDetail("rice") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun UrgentShoppingItem(
    name: String,
    statusText: String,
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
                Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = statusText, color = WarningBrown, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPurchased,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("購入済み", color = Color.White)
                }
                OutlinedButton(
                    onClick = onStillHave,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("まだある")
                }
                TextButton(
                    onClick = onDoNotBuy,
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("もう買わない", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SafeShoppingItemRow(
    name: String,
    remainingText: String,
    isEstimated: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, fontSize = 15.sp)
            if (isEstimated) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = PrimaryGreenLight,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "推定",
                        color = PrimaryGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(text = remainingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
    if (showDivider) {
        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

package com.example.receiptreminder.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.receiptreminder.ui.theme.WarningBrown

@Composable
fun ProductDetailScreen(
    productId: String?,
    onBack: () -> Unit
) {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("リストに戻る", color = PrimaryGreen, fontSize = 14.sp)
                    }
                    Text(
                        text = "食器用洗剤",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "なくなりそう・あと 2 日",
                        color = WarningBrown,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ステータス概要
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    DetailRow(label = "切れる日", value = "11 月 2 日", showDivider = true)
                    DetailRow(label = "周期", value = "30 日 / 個", showDivider = true)
                    DetailRow(label = "なくなりそうになった日", value = "10 月 27 日", showDivider = false)
                }
            }

            // アクションボタン
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("購入済み", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("まだある")
                    }
                }
            }

            // 購入履歴
            item {
                Text(
                    text = "購入履歴",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    HistoryRow(date = "10 月 3 日", source = "レシート・みどりマート 中央店", quantity = "1 個", showDivider = true)
                    HistoryRow(date = "9 月 2 日", source = "手動で追加", quantity = "1 個", showDivider = true)
                    HistoryRow(date = "8 月 3 日", source = "レシート・みどりマート 中央店", quantity = "1 個", showDivider = true)
                    HistoryRow(date = "7 月 5 日", source = "レシート・ドラッグひかり 駅前店", quantity = "1 個", showDivider = false)
                }
            }

            item {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("購入を追加", fontSize = 14.sp)
                }
            }

            // 予測の設定
            item {
                Text(
                    text = "予測の設定",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("予測の対象にする", fontSize = 15.sp)
                        Checkbox(
                            checked = true,
                            onCheckedChange = { },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    TextButton(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("もう買わない", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("次にレシートで購入すると自動で戻ります", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    if (showDivider) {
        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

@Composable
private fun HistoryRow(date: String, source: String, quantity: String, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = date, fontSize = 15.sp)
            Text(text = source, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(text = quantity, fontSize = 15.sp)
    }
    if (showDivider) {
        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

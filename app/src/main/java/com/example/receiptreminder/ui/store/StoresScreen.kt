package com.example.receiptreminder.ui.store

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
import com.example.receiptreminder.ui.theme.ErrorRed
import com.example.receiptreminder.ui.theme.PrimaryGreen

@Composable
fun StoresScreen(
    onNavigateToExplainer: () -> Unit
) {
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
                        text = "店舗",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "登録した店舗の近くで、なくなりそうなものをお知らせします。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Button(
                    onClick = onNavigateToExplainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(
                        text = "現在地を店舗として登録",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 店舗アイテム1
            item {
                StoreCard(
                    storeName = "みどりマート 中央店",
                    radiusText = "通知する範囲 半径 200m",
                    isEnabled = true,
                    isOffLabelVisible = false
                )
            }

            // 店舗アイテム2
            item {
                StoreCard(
                    storeName = "ドラッグひかり 駅前店",
                    radiusText = "通知する範囲 半径 300m",
                    isEnabled = true,
                    isOffLabelVisible = false
                )
            }

            // 店舗アイテム3 (通知オフ)
            item {
                StoreCard(
                    storeName = "まるやまスーパー",
                    radiusText = "通知する範囲 半径 200m",
                    isEnabled = false,
                    isOffLabelVisible = true
                )
            }

            // 設定
            item {
                Text(
                    text = "設定",
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("通知する時間帯", fontSize = 15.sp)
                        TextButton(onClick = { }) {
                            Text("8:00〜21:00", color = PrimaryGreen, fontSize = 15.sp)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    TextButton(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "履歴と店舗をすべて削除",
                            color = ErrorRed,
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
}

@Composable
private fun StoreCard(
    storeName: String,
    radiusText: String,
    isEnabled: Boolean,
    isOffLabelVisible: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else Color(0xFFE8ECE9)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = storeName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (isOffLabelVisible) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "通知オフ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Checkbox(
                    checked = isEnabled,
                    onCheckedChange = { },
                    colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = radiusText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { }, modifier = Modifier.height(36.dp)) {
                        Text("変更", color = PrimaryGreen, fontSize = 14.sp)
                    }
                    TextButton(onClick = { }, modifier = Modifier.height(36.dp)) {
                        Text("削除", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

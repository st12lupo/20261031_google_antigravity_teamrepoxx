package com.example.receiptreminder.ui.review

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiptreminder.ui.theme.PrimaryGreen
import com.example.receiptreminder.ui.theme.WarningBrownBg
import com.example.receiptreminder.ui.theme.WarningBrownText

@Composable
fun ReceiptReviewScreen(
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "保存するとレシートの画像は削除されます。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("取り消す")
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("保存する", fontWeight = FontWeight.Bold, color = Color.White)
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
                        text = "レシートの確認",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "読み取り結果を確認して、違うところを直してください。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // 重複警告バナー
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningBrownBg)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "同じレシートが登録済みかもしれません",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = WarningBrownText
                        )
                        Text(
                            text = "店名、日付、合計金額が一致しています。別のレシートならそのまま保存できます。",
                            fontSize = 14.sp,
                            color = WarningBrownText
                        )
                    }
                }
            }

            // 店名・日付・合計金額入力欄
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = "みどりマート 中央店",
                            onValueChange = { },
                            label = { Text("店名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = "2026/10/31",
                                onValueChange = { },
                                label = { Text("日付") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = "¥1,544",
                                onValueChange = { },
                                label = { Text("合計金額") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "商品 4 点",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 商品一覧
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    ReceiptItemRow(
                        rawOcr = "ｼｮｯｷｾﾝｻﾞｲ ｷｭｷｭｯﾄ",
                        normalizedName = "食器用洗剤",
                        quantityAndPrice = "1 個　¥198",
                        statusText = "予測の対象",
                        isTarget = true,
                        showDivider = true
                    )
                    ReceiptItemRow(
                        rawOcr = "ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R",
                        normalizedName = "トイレットペーパー",
                        quantityAndPrice = "1 個　¥458",
                        statusText = "予測の対象・2 日前の「購入済み」と 1 件にまとめます",
                        isTarget = true,
                        showDivider = true
                    )
                    ReceiptItemRow(
                        rawOcr = "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238",
                        normalizedName = "牛乳",
                        quantityAndPrice = "2 個　¥476",
                        statusText = "予測の対象",
                        isTarget = true,
                        showDivider = true
                    )
                    ReceiptItemRow(
                        rawOcr = "ﾄﾘﾑﾈﾆｸ",
                        normalizedName = "鶏むね肉",
                        quantityAndPrice = "1 個　¥412",
                        statusText = "予測の対象外",
                        isTarget = false,
                        showDivider = false
                    )
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
                    Text("商品を追加", fontSize = 14.sp)
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ReceiptItemRow(
    rawOcr: String,
    normalizedName: String,
    quantityAndPrice: String,
    statusText: String,
    isTarget: Boolean,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = rawOcr,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = normalizedName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = quantityAndPrice, fontSize = 14.sp)
        }
        Text(
            text = statusText,
            fontSize = 12.sp,
            color = if (isTarget) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showDivider) {
        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

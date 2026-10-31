package receipt.scan

import kotlin.math.abs

/** 文字認識で得た 1 行と、画像上の位置 */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * 同じ高さにある行を左から順に連結し、上から順の行にする。
 * 商品名と金額の間が空いていると ML Kit は別のブロックとして返すため、位置から対応づける。
 */
fun joinLinesByRow(lines: List<OcrLine>): List<String> {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()
        if (row != null && abs(line.centerY - row.first().centerY) <= minOf(line.height, row.first().height) / 2) {
            row.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
}

package com.monesy.kaoyan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 课表图片识别：ML Kit 中文文字识别（离线模型，打包在 APK 内，不联网）
 * → 重建行列网格 → 交给 TimetableParser 与导入预览页（与 Excel 同一条链路）。
 *
 * 支持两类主流课表截图：
 * - 表格式（每节一行，节次列是"第X节"或数字）
 * - 卡片式（彩色课程卡片跨节次，节次列只有数字，如 kbpro 等小程序课表）
 *
 * 重建流程：文本行 → 合并为"文本块"（同一张卡片的课名/教室/标签）→
 * x 聚类成列 → 定位星期表头行 → 用节次数字列做"行锚"把卡片映射到节次行
 * （跨节卡片落到最近锚，3、4 号锚同属第 3-4 节）→ 输出与 Excel 同构的网格。
 */
object OcrReader {

    suspend fun readGrid(context: Context, uri: Uri): List<List<String>> {
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .getOrElse { throw IllegalArgumentException("无法读取所选图片") }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val text = try {
            recognizer.process(image).awaitTask()
        } finally {
            recognizer.close()
        }
        val lines = text.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() && it.boundingBox != null }
        if (lines.size < 5) {
            throw IllegalArgumentException("图片里识别到的文字太少，请使用清晰、完整的课表截图")
        }
        return toGrid(lines)
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { r ->
                if (r != null) cont.resume(r) else cont.resumeWithException(IllegalStateException("识别结果为空"))
            }
            addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    // ---------- 网格重建 ----------

    private data class Block(
        val lines: List<Text.Line>,
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int,
    ) {
        fun centerX() = (left + right) / 2f
        fun centerY() = (top + bottom) / 2f
        fun text(): String =
            lines.sortedBy { it.boundingBox!!.top }.joinToString("\n") { it.text.replace("\n", " ").trim() }
    }

    private fun Block.mergedWith(l: Text.Line): Block {
        val b = l.boundingBox!!
        return Block(lines + l, minOf(left, b.left), maxOf(right, b.right), minOf(top, b.top), maxOf(bottom, b.bottom))
    }

    private val DAY_RE = Regex("(?:星期|周)([一二三四五六日天])")

    private fun toGrid(lines: List<Text.Line>): List<List<String>> {
        val heights = lines.map { it.boundingBox!!.height() }.sorted()
        val medH = heights[heights.size / 2].coerceAtLeast(1)

        // 1) 文本行 → 文本块：水平范围重叠 且 垂直间隙 < 行高中位数×0.6（卡片内行距紧凑，卡片间空隙大）
        val blocks = mutableListOf<Block>()
        for (l in lines.sortedBy { it.boundingBox!!.top }) {
            val b = l.boundingBox!!
            var best = -1
            var bestGap = Int.MAX_VALUE
            for ((i, cur) in blocks.withIndex()) {
                val overlap = b.left < cur.right - medH * 0.2f && b.right > cur.left + medH * 0.2f
                if (!overlap) continue
                val gap = b.top - cur.bottom
                if (gap < -medH || gap > medH * 0.6f) continue
                if (kotlin.math.abs(gap) < bestGap) { best = i; bestGap = kotlin.math.abs(gap) }
            }
            if (best >= 0) blocks[best] = blocks[best].mergedWith(l)
            else blocks.add(Block(listOf(l), b.left, b.right, b.top, b.bottom))
        }

        // 2) 列聚类：块中心 x 一维聚类（间距 > 行高中位数即断开）
        val colCenters = mutableListOf<Int>()
        run {
            var cluster = mutableListOf<Int>()
            var last = Int.MIN_VALUE
            for (x in blocks.map { it.centerX() }.sorted()) {
                if (cluster.isNotEmpty() && x - last > medH) {
                    colCenters.add(cluster.average().toInt())
                    cluster = mutableListOf()
                }
                cluster.add(x.toInt()); last = x.toInt()
            }
            if (cluster.isNotEmpty()) colCenters.add(cluster.average().toInt())
        }
        if (colCenters.size < 4) {
            throw IllegalArgumentException("没认出课表的列结构（一般应有节次列 + 7 天），建议用表格线清晰的截图")
        }
        fun colOf(b: Block): Int {
            var best = 0
            var bestD = Float.MAX_VALUE
            colCenters.forEachIndexed { i, c ->
                val d = kotlin.math.abs(b.centerX() - c)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }

        // 3) 按块中心 y 聚类行带，找含 ≥3 个星期词的行带 = 表头
        val sortedBlocks = blocks.sortedBy { it.centerY() }
        val bands = mutableListOf<MutableList<Block>>()
        var bandY = Float.NaN
        for (b in sortedBlocks) {
            val cy = b.centerY()
            if (bandY.isNaN() || kotlin.math.abs(cy - bandY) > medH * 1.2f) {
                bands.add(mutableListOf(b)); bandY = cy
            } else {
                val band = bands.last()
                bandY = (bandY * band.size + cy) / (band.size + 1)
                band.add(b)
            }
        }
        // 表头格：含"星期X/周X"，或整块就是单字星期（WakeUp 等课表用"一 二 三 …"做表头）
        fun isDayText(t: String): Boolean {
            val s = t.trim()
            if (DAY_RE.containsMatchIn(s)) return true
            val ch = s.firstOrNull() ?: return false
            return s.length <= 2 && ch in "一二三四五六日天"
        }
        val headerBand = bands.firstOrNull { band ->
            band.count { isDayText(it.text()) } >= 4
        }
        if (headerBand == null) {
            // 退回纯行带网格：交给 TimetableParser 报"没认出表头"，预览页兜底
            return bands.map { band ->
                val cells = MutableList(colCenters.size) { StringBuilder() }
                for (b in band.sortedBy { it.left }) {
                    val c = colOf(b)
                    if (cells[c].isNotEmpty()) cells[c].append('\n')
                    cells[c].append(b.text())
                }
                cells.map { it.toString() }
            }
        }
        val headerCells = MutableList(colCenters.size) { "" }
        for (b in headerBand) headerCells[colOf(b)] = b.text()

        // 4) 节次锚：最左列中"首行是 1..12 数字"的块（kbpro 是纯数字；WakeUp 是"数字+上下课时间"竖排块）
        val leftCol = 0
        val anchors = sortedBlocks
            .filter { colOf(it) == leftCol }
            .mapNotNull { b ->
                val firstLine = b.text().lineSequence().firstOrNull()?.trim()
                val n = firstLine?.toIntOrNull()
                if (n != null && n in 1..12) Triple(n, b.centerY(), b) else null
            }
            .sortedBy { it.second }

        val grid = mutableListOf<List<String>>()
        grid.add(headerCells)
        if (anchors.size < 3) {
            // 无节次锚（表格式截图）：其余行带直接作为网格行
            for (band in bands) {
                if (band === headerBand) continue
                val cells = MutableList(colCenters.size) { StringBuilder() }
                for (b in band.sortedBy { it.left }) {
                    val c = colOf(b)
                    if (cells[c].isNotEmpty()) cells[c].append('\n')
                    cells[c].append(b.text())
                }
                grid.add(cells.map { it.toString() })
            }
            return grid
        }

        // 5) 卡片式：内容块映射到最近的节次锚（3、4 号锚同属第 3-4 节 slot）
        val anchorGaps = anchors.zipWithNext { a, b -> b.second - a.second }.sorted()
        val medGap = (anchorGaps.getOrNull(anchorGaps.size / 2) ?: 100f).coerceAtLeast(1f)
        data class SlotRow(val slot: Int, val anchorY: Float, val cells: MutableList<MutableList<Block>>)
        val slotRows = mutableListOf<SlotRow>()

        for (anchor in anchors) {
            val slot = (anchor.first - 1) / 2
            if (slotRows.none { it.slot == slot }) {
                slotRows.add(SlotRow(slot, anchor.second, MutableList(colCenters.size) { mutableListOf<Block>() }))
            }
        }
        slotRows.sortBy { it.anchorY }

        val anchorBlocks = anchors.map { it.third }.toSet()
        for (b in sortedBlocks) {
            if (b in headerBand || b in anchorBlocks) continue
            // 找最近的锚
            var bestSlot = -1
            var bestDist = Float.MAX_VALUE
            for (row in slotRows) {
                val d = kotlin.math.abs(b.centerY() - row.anchorY)
                if (d < bestDist) { bestDist = d; bestSlot = row.slot }
            }
            if (bestSlot < 0 || bestDist > medGap * 0.85f) continue // 离所有节次行都太远：页眉/导航等噪音，丢弃
            val row = slotRows.first { it.slot == bestSlot }
            row.cells[colOf(b)].add(b)
        }

        for (row in slotRows) {
            val cells = MutableList(colCenters.size) { "" }
            // 节次列放该 slot 对应的锚数字（如 3-4 节 → "3\n4"），供 TimetableParser 定位节次
            val nums = anchors.filter { (it.first - 1) / 2 == row.slot }.joinToString("\n") { it.first.toString() }
            cells[leftCol] = nums
            for (c in 1 until colCenters.size) {
                cells[c] = row.cells[c].sortedBy { it.top }.joinToString("\n") { it.text() }
            }
            grid.add(cells)
        }
        return grid
    }
}

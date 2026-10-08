package com.monesy.kaoyan

import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板课表导入：在浏览器打开教务系统的课表页面 → 全选复制 → 回到这里粘贴。
 *
 * 浏览器复制网页表格时，剪贴板里同时带有 text/html（完整 <table> 结构，单元格文本无损）
 * 与 text/plain（视觉排版文本）两种格式。优先解析 HTML 表格（含 rowspan/colspan 展开，
 * 教务系统的跨节课程与合并表头都能正确落位）；HTML 不可用时退回纯文本按制表符/多空格切列。
 *
 * 这是一条完全离线、文本无损的导入路径——比截图 OCR 可靠得多，适用于所有能打开课表网页的学校。
 */
object ClipboardImporter {

    /**
     * 剪贴板 → 课程解析（编排三种路径）：
     * 1) HTML 表格（浏览器复制表格首选）→ 网格 → 通用表头解析
     * 2) 纯文本但属"线性课表格式"（jsxsd 等教务系统）→ 专用解析
     * 3) 纯文本其它形态 → 按制表符/多空格分列 → 通用解析
     */
    fun readCourses(context: Context): TimetableParser.Result {
        val cm = context.getSystemService(ClipboardManager::class.java)
            ?: throw IllegalArgumentException("无法访问剪贴板")
        val clip = cm.primaryClip ?: throw IllegalArgumentException(EMPTY_HINT)
        if (clip.itemCount == 0) throw IllegalArgumentException(EMPTY_HINT)

        val html = runCatching { clip.getItemAt(0).coerceToHtmlText(context) }.getOrNull()
        if (!html.isNullOrBlank() && html.contains("<table", ignoreCase = true)) {
            val grid = htmlTablesToGrid(html)
            if (grid.isNotEmpty()) {
                val r = TimetableParser.parse(grid)
                if (r.courses.isNotEmpty()) return r
            }
        }

        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        // 误复制网址的精准提示：用户复制了地址栏而不是表格内容
        if (URL_LIKE.matches(text.trim())) {
            throw IllegalArgumentException(
                "你复制的看起来是网址，不是课表内容。\n\n" +
                    "正确操作：在浏览器打开课表页面后，用手指长按课表表格里的文字 → 选择「全选」→ 点「复制」。\n" +
                    "复制的是屏幕上那张课表表格本身，不是网页地址。"
            )
        }
        if (TimetableParser.looksLinear(text)) {
            val r = TimetableParser.parseLinear(text.split('\n'))
            if (r.courses.isNotEmpty()) return r
        }
        val grid = plainTextToGrid(text)
        if (grid.isNotEmpty()) return TimetableParser.parse(grid)

        throw IllegalArgumentException(
            "剪贴板里没有识别到课表表格。请在浏览器打开教务系统的课表页面（显示完整表格的那一页），" +
                "从表格左上角开始全选并复制，再回来点「剪贴板」"
        )
    }

    private const val EMPTY_HINT = "剪贴板是空的：请先在浏览器打开教务系统的课表页面，全选复制表格内容"

    private val URL_LIKE = Regex("^\\s*(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)

    /** 从剪贴板解析课表网格；解析不出抛可读异常。 */
    fun readGrid(context: Context): List<List<String>> {
        val cm = context.getSystemService(ClipboardManager::class.java)
            ?: throw IllegalArgumentException("无法访问剪贴板")
        val clip = cm.primaryClip ?: throw IllegalArgumentException(
            "剪贴板是空的：请先在浏览器打开教务系统的课表页面，全选复制表格内容"
        )
        if (clip.itemCount == 0) {
            throw IllegalArgumentException("剪贴板是空的：请先在浏览器打开教务系统的课表页面，全选复制表格内容")
        }

        // 1) HTML 表格（首选：无损）
        val html = runCatching { clip.getItemAt(0).coerceToHtmlText(context) }.getOrNull()
        if (!html.isNullOrBlank() && html.contains("<table", ignoreCase = true)) {
            val grid = htmlTablesToGrid(html)
            if (grid.isNotEmpty()) return grid
        }

        // 2) 纯文本兜底：制表符 / 多空格分列
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        val grid = plainTextToGrid(text)
        if (grid.isNotEmpty()) return grid

        throw IllegalArgumentException(
            "剪贴板里没有识别到课表表格。请在浏览器打开教务系统的课表页面（显示完整表格的那一页），" +
                "从表格左上角开始全选并复制，再回来点「剪贴板」"
        )
    }

    // ---------- HTML 表格 ----------

    private val TR_RE = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CELL_RE = Regex(
        "<(t[dh])[^>]*?(?:rowspan=\"?(\\d+)\"?)?[^>]*?(?:colspan=\"?(\\d+)\"?)?[^>]*>(.*?)</\\1>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val TABLE_RE = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** 解析页面里所有表格，返回包含最多"星期"字样的那张（教务页外层布局表格会被跳过）。 */
    private fun htmlTablesToGrid(html: String): List<List<String>> {
        var best: List<List<String>> = emptyList()
        var bestScore = 0
        TABLE_RE.findAll(html).forEach { t ->
            val grid = expandHtmlTable(t.groupValues[1])
            val score = grid.sumOf { row -> row.count { DAY_HINT.containsMatchIn(it) } } + grid.size
            if (score > bestScore && grid.size >= 3) { best = grid; bestScore = score }
        }
        return best
    }

    private val DAY_HINT = Regex("星期|周一|周二|周三|周四|周五|周六|周日")

    /** 标准表格展开：按 rowspan/colspan 占位填充，产出对齐的二维网格。 */
    private fun expandHtmlTable(tableInner: String): List<List<String>> {
        val grid = mutableListOf<MutableList<String?>>()
        fun cellAt(r: Int, c: Int): String? {
            while (grid.size <= r) grid.add(mutableListOf())
            val row = grid[r]
            while (row.size <= c) row.add(null)
            return row[c]
        }
        fun firstFree(r: Int, start: Int): Int {
            var c = start
            while (cellAt(r, c) != null) c++
            return c
        }

        TR_RE.findAll(tableInner).forEachIndexed { r, tr ->
            var col = 0
            CELL_RE.findAll(tr.groupValues[1]).forEach { m ->
                val rowspan = (m.groupValues[2].toIntOrNull() ?: 1).coerceIn(1, 30)
                val colspan = (m.groupValues[3].toIntOrNull() ?: 1).coerceIn(1, 15)
                val text = m.groupValues[4]
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace(Regex("[ \t]+"), " ")
                    .trim()
                col = firstFree(r, col)
                for (dr in 0 until rowspan) {
                    for (dc in 0 until colspan) {
                        while (grid.size <= r + dr) grid.add(mutableListOf())
                        val row = grid[r + dr]
                        while (row.size <= col + dc) row.add(null)
                        row[col + dc] = if (dr == 0 && dc == 0) text else (grid[r][col] ?: text)
                    }
                }
                col += colspan
            }
        }
        return grid.map { row -> row.map { it ?: "" } }
    }

    // ---------- 纯文本兜底 ----------

    private fun plainTextToGrid(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val rows = text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
        if (rows.size < 3) return emptyList()
        return rows.map { line ->
            if (line.contains('\t')) {
                line.split('\t').map { it.trim() }
            } else {
                // 视觉排版文本：2 个以上连续空格视为列分隔
                line.split(Regex(" {2,}")).map { it.trim() }
            }
        }
    }
}

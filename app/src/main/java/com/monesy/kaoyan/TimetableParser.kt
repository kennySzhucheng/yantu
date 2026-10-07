package com.monesy.kaoyan

/**
 * 课表网格解析器：把 Excel 二维文本网格（行=节次 / 列=星期）解析成课程条目。
 * 宽松解析 + 预览确认兜底：识别不出的内容宁可漏掉，由用户在预览页补。
 */
object TimetableParser {

    data class Course(
        var day: Int,           // 1=周一 … 7=周日
        var slot: Int,          // 0=第1-2节 … 5=第11-12节
        var name: String,
        var room: String,
        var weeks: String,      // 规范表达式，如 "1-4,6-15"
        var checked: Boolean = true,
    )

    data class Result(val courses: MutableList<Course>, val error: String? = null)

    private val DAY_RE = Regex("(?:星期|周)([一二三四五六日天])")
    private val CN = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
    )

    private data class RawCourse(val name: String, val room: String, val weeks: String)

    fun parse(grid: List<List<String>>): Result {
        // 1. 找表头行：一行里有 >=3 个"星期X/周X"（截图课表的表头可能只有单字"一二三…"）
        var headerRow = -1
        var dayToCol = mapOf<Int, Int>()
        for ((r, row) in grid.withIndex()) {
            val m = mutableMapOf<Int, Int>()
            row.forEachIndexed { c, cell ->
                val d = dayFromCell(cell)
                if (d > 0 && d !in m) m[d] = c
            }
            if (m.size >= 3) { headerRow = r; dayToCol = m; break }
        }
        if (headerRow < 0) {
            return Result(mutableListOf(), "没认出课表表头（含“星期一/周一”的行），请确认是教务系统导出的课程表；也可以手动添加课程")
        }

        val courses = mutableListOf<Course>()
        val slotCol = (dayToCol.values.minOrNull() ?: 1) - 1
        for (r in headerRow + 1 until grid.size) {
            val row = grid[r]
            val slotText = if (slotCol >= 0) row.getOrNull(slotCol) ?: "" else ""
            if (slotText.startsWith("备注")) continue
            val slot = slotIndexOf(slotText) ?: continue
            for ((day, col) in dayToCol) {
                val cell = row.getOrNull(col) ?: continue
                if (cell.isBlank()) continue
                parseCell(cell).forEach { rc ->
                    courses.add(Course(day, slot, rc.name, rc.room, rc.weeks))
                }
            }
        }
        if (courses.isEmpty()) {
            return Result(courses, "表格里没有解析出课程（可能是空表或格式特殊），可以用“手动添加”补充")
        }
        return Result(courses)
    }

    private fun dayIndex(ch: Char): Int = when (ch) {
        '一' -> 1; '二' -> 2; '三' -> 3; '四' -> 4
        '五' -> 5; '六' -> 6; '日', '天' -> 7; else -> 0
    }

    /** 表头格 → 星期：优先"星期X/周X"；否则整格是单字星期（截图课表"一 二 三…"）也算 */
    private fun dayFromCell(cell: String): Int {
        DAY_RE.find(cell)?.let { return dayIndex(it.groupValues[1].firstOrNull() ?: ' ') }
        val t = cell.trim()
        val ch = t.firstOrNull() ?: return 0
        return if (t.length <= 2 && ch in "一二三四五六日天") dayIndex(ch) else 0
    }

    /** "第一二节 / 01、02小节 / 第9-10节 / 第十一十二节 / 纯数字3（截图课表的单节号）" → slot 0..5 */
    fun slotIndexOf(text: String): Int? {
        val t = text.trim()
        // 截图课表的节次列常为纯数字单节号：1..12 → (n-1)/2
        t.toIntOrNull()?.let { return if (it in 1..12) (it - 1) / 2 else null }
        if (!t.contains("节")) return null
        val nums = mutableListOf<Int>()
        var i = 0
        while (i < text.length && nums.size < 2) {
            val ch = text[i]
            when {
                ch.isDigit() -> {
                    var j = i
                    while (j < text.length && text[j].isDigit()) j++
                    nums.add(text.substring(i, j).toIntOrNull() ?: 0)
                    i = j
                }
                ch == '十' -> {
                    var v = 10
                    if (i + 1 < text.length && text[i + 1] in CN && text[i + 1] != '十') { v += CN.getValue(text[i + 1]); i++ }
                    nums.add(v); i++
                }
                ch in CN -> {
                    var v = CN.getValue(ch)
                    if (i + 1 < text.length && text[i + 1] == '十') {
                        v *= 10
                        if (i + 2 < text.length && text[i + 2] in CN && text[i + 2] != '十') { v += CN.getValue(text[i + 2]); i++ }
                        i++
                    }
                    nums.add(v); i++
                }
                else -> i++
            }
        }
        val n = nums.firstOrNull() ?: return null
        if (n < 1 || n > 12) return null
        return (n - 1) / 2
    }

    // ---------- 单元格解析 ----------

    private val WEEK_RANGE = Regex("\\d+(?:\\s*[-–~]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-–~]\\s*\\d+)?)*")
    private val CODE = Regex("[A-Z][A-Z0-9]{6,}")

    private fun parseCell(text: String): List<RawCourse> {
        val out = mutableListOf<RawCourse>()
        var t = text.replace("\r", "").replace('\u00a0', ' ')
        // 东林等格式：下一门课的「课名[教师]」被挤在上一条教室后面，按边界切行
        t = Regex(" +(?=[^\\s;\\[\\]]+\\[[^\\]]+\\])").replace(t, "\n")
        val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var pendingName: String? = null
        for (line in lines) {
            val nameMatch = Regex("^(.*?)\\[[^\\]]+\\]\\s*$").find(line)
            if (nameMatch != null && line.firstOrNull()?.isDigit() != true) {
                pendingName = nameMatch.groupValues[1].trim()
                continue
            }
            val pending = pendingName
            if (pending != null && line.any { it.isDigit() }) {
                // 详情行："1-4,6-15周;丹青楼401" / "第8周;成栋楼634"
                val semi = line.indexOfFirst { it == ';' || it == '；' }
                val weeksPart = if (semi >= 0) line.substring(0, semi) else line
                val rest = if (semi >= 0) line.substring(semi + 1) else ""
                val weeks = normalizeWeeks(weeksPart)
                if (weeks.isNotBlank()) {
                    out.add(RawCourse(pending, cleanRoom(rest), weeks))
                    pendingName = null
                    continue
                }
            }
            // 单行通用格式："高等数学@教1-101 (1-16周)" / "数学分析 1-16周 丹青楼301"
            out.addAll(parseGenericLine(line))
            pendingName = null
        }
        if (out.isEmpty()) out.addAll(parseCardLike(t))
        return out
    }

    private val ROOM_HINT = Regex("(楼|室|馆|场|区|中心|教|号楼|栋|厅)|\\d")

    /** 有明确周次文字的才算"表格内容"，卡片式截图（课名+教室+标签、无周次）交给 parseCardLike */
    private val WEEK_TEXT = Regex("\\d+\\s*[-–~]\\s*\\d+\\s*周|第\\s*\\d+\\s*周|单周|双周|\\d+周")

    /**
     * 卡片式课表兜底（截图课表常见）：格子是"课名（可换行）/ @教室（可断行）/ [标签]"，
     * 没有周次信息 → 课名取文字行（@ 前），教室取 @ 后或含楼室馆等线索的行并续接断行，
     * 周次默认全学期（1-25），由用户在预览页修改。
     */
    private fun parseCardLike(text: String): List<RawCourse> {
        if (WEEK_TEXT.containsMatchIn(text)) return emptyList()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val name = StringBuilder()
        var room = ""
        for (raw in lines) {
            val line = raw.removePrefix("周]")
            val at = line.indexOf('@')
            when {
                line.startsWith("[") || line.startsWith("（") || line.startsWith("(") -> {}
                at >= 0 -> {
                    // "@教1-101" 可能被拆成 "@东 / 10A- / 303"：@ 前归课名、@ 后起教室并续接断行
                    val before = line.substringBefore('@').trim()
                    if (before.isNotBlank() && before.firstOrNull()?.isDigit() != true) name.append(before)
                    if (room.isEmpty()) room = line.substringAfter('@').trim()
                }
                name.isEmpty() && line.firstOrNull()?.isDigit() != true && !line.contains("/") -> name.append(line)
                room.isEmpty() && ROOM_HINT.containsMatchIn(line) -> room = line
                room.isEmpty() && line.firstOrNull()?.isDigit() != true && !line.contains("/") -> name.append(line)
                room.isNotEmpty() && (line.all { it.isDigit() } || (line.firstOrNull()?.isDigit() == true && line.length <= 6)) -> room += line
                else -> if (room.isEmpty()) room = line
            }
        }
        val n = name.toString().replace("\n", "").trim()
        if (n.length < 2 || n.any { it.isDigit() } || n.contains("节") || n.contains("星期")) return emptyList()
        return listOf(RawCourse(n, cleanRoom(room.trim().trimStart('@')), "1-25"))
    }

    private fun parseGenericLine(line: String): List<RawCourse> {
        val weeks = normalizeWeeks(line)
        if (weeks.isBlank()) return emptyList()
        val atIdx = line.indexOf('@')
        var name: String
        var room: String
        if (atIdx > 0) {
            name = line.substring(0, atIdx)
            room = line.substring(atIdx + 1)
        } else {
            val m = WEEK_RANGE.find(line)
            if (m == null) return emptyList()
            name = line.substring(0, m.range.first)
            room = line.substring(m.range.last + 1)
        }
        name = name.replace(Regex("\\[[^\\]]*\\]"), "").trim().trim(';', '；', ',', '，')
        room = room.trim().trim(';', '；', ':', '：', ',', '，')
        if (room.isNotBlank()) room = cleanRoom(room)
        // 排除误把节次行当课程
        if (name.isBlank() || name.contains("节")) return emptyList()
        return listOf(RawCourse(name, room, weeks))
    }

    /** 周次表达式规范化："第1-16周" → "1-16"；单/双周展开为显式列表 */
    fun normalizeWeeks(part: String): String {
        var p = part.replace("第", "").replace("周", "")
        val m = WEEK_RANGE.find(p) ?: return if (part.contains("单周") || part.contains("双周")) {
            expandParity("", part.contains("单周"))
        } else ""
        val expr = m.value.replace(Regex("\\s*[,，、]\\s*"), ",").replace(Regex("\\s*[-–~]\\s*"), "-").trim(',')
        return when {
            part.contains("单周") -> expandParity(expr, odd = true)
            part.contains("双周") -> expandParity(expr, odd = false)
            else -> expr
        }
    }

    private fun expandParity(expr: String, odd: Boolean): String {
        val base = if (expr.isBlank()) "1-25" else expr
        val out = mutableListOf<Int>()
        for (seg in base.split(',')) {
            if (seg.contains('-')) {
                val a = seg.substringBefore('-').toIntOrNull() ?: continue
                val b = seg.substringAfter('-').toIntOrNull() ?: continue
                if (a > b) continue
                for (w in a..b) if ((w % 2 == 1) == odd) out.add(w)
            } else seg.toIntOrNull()?.let { if ((it % 2 == 1) == odd) out.add(it) }
        }
        return out.joinToString(",")
    }

    /** 教室清洗：剥离课程代码及其后的班级/人数等尾注 */
    private fun cleanRoom(rest: String): String {
        var r = rest.trim()
        if (r.isEmpty()) return ""
        if (Regex("^[A-Z][A-Z0-9]{6,}").containsMatchIn(r)) return ""
        r = Regex("\\s+[A-Z][A-Z0-9]{6,}\\b.*$").replace(r, "")
        return r.trim().trimEnd(';', '；', ' ')
    }
}

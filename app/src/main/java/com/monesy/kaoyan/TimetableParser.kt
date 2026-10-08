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

    /** 解析不出的单元格原文（保留给用户在预览页补录，不静默丢弃） */
    data class UnparsedCell(val day: Int, val slot: Int, val text: String)

    data class Result(
        val courses: MutableList<Course>,
        val error: String? = null,
        val unparsed: MutableList<UnparsedCell> = mutableListOf(),
    )

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
        val unparsed = mutableListOf<UnparsedCell>()
        val slotCol = (dayToCol.values.minOrNull() ?: 1) - 1
        for (r in headerRow + 1 until grid.size) {
            val row = grid[r]
            val slotText = if (slotCol >= 0) row.getOrNull(slotCol) ?: "" else ""
            if (slotText.startsWith("备注")) continue
            val slot = slotIndexOf(slotText) ?: continue
            for ((day, col) in dayToCol) {
                val cell = row.getOrNull(col) ?: continue
                if (cell.isBlank()) continue
                val parsed = parseCell(cell)
                if (parsed.isEmpty()) {
                    if (meaningfulCell(cell)) unparsed.add(UnparsedCell(day, slot, cell.trim()))
                } else parsed.forEach { rc ->
                    courses.add(Course(day, slot, rc.name, rc.room, rc.weeks))
                }
            }
        }
        if (courses.isEmpty()) {
            return Result(courses, "表格里没有解析出课程（可能是空表或格式特殊），可以用“手动添加”补充", unparsed)
        }
        return Result(courses, unparsed = unparsed)
    }

    /** 值得收集为"未识别内容"的文本：去掉 [标签]/(标签) 后仍有 ≥2 个汉字 */
    private fun meaningfulCell(text: String): Boolean {
        val t = text.replace(Regex("[\\[（(][^\\]）)\\n]*[\\]）)]"), " ").trim()
        val cn = t.count { it in '\u4e00'..'\u9fff' }
        return t.length in 2..80 && cn >= 2
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
        // 取首个非空行解析（OCR 网格的节次列可能是"1\n2"这类多行拼接）
        val t = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        // 截图课表的节次列常为纯数字单节号：1..12 → (n-1)/2
        t.toIntOrNull()?.let { return if (it in 1..12) (it - 1) / 2 else null }
        if (!t.contains("节")) return null
        val nums = mutableListOf<Int>()
        var i = 0
        while (i < t.length && nums.size < 2) {
            val ch = t[i]
            when {
                ch.isDigit() -> {
                    var j = i
                    while (j < t.length && t[j].isDigit()) j++
                    nums.add(t.substring(i, j).toIntOrNull() ?: 0)
                    i = j
                }
                ch == '十' -> {
                    var v = 10
                    if (i + 1 < t.length && t[i + 1] in CN && t[i + 1] != '十') { v += CN.getValue(t[i + 1]); i++ }
                    nums.add(v); i++
                }
                ch in CN -> {
                    var v = CN.getValue(ch)
                    if (i + 1 < t.length && t[i + 1] == '十') {
                        v *= 10
                        if (i + 2 < t.length && t[i + 2] in CN && t[i + 2] != '十') { v += CN.getValue(t[i + 2]); i++ }
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
        // 网页课表常见排版：下一门课的「课名[教师]」被挤在上一条教室后面，按边界切行
        t = Regex(" +(?=[^\\s;\\[\\]]+\\[[^\\]]+\\])").replace(t, "\n")
        // 带括号课程代码的教务格式优先："教师 课名(3YJ1042A.01)(4-11,教学主楼D405)"
        if (t.contains('(')) parseBracketed(t).let { if (it.isNotEmpty()) return it }
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
                // 详情行："1-4,6-15周;教学楼401" / "第8周;实验楼634"
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
            // 单行通用格式："高等数学@教1-101 (1-16周)" / "数学分析 1-16周 教学楼301"
            out.addAll(parseGenericLine(line))
            pendingName = null
        }
        // 竖排多行格式（网页课表复制，如 jsxsd 教务）："课名\n教师\n1-16周\n教室"
        if (out.isEmpty()) parseVertical(lines)?.let { out.add(it) }
        if (out.isEmpty()) out.addAll(parseCardLike(t))
        return out
    }

    /**
     * 竖排多行单元格："课名 /（教师）/ 1-16周 / 教室" 各占一行（剪贴板复制的网页课表常见形态）。
     * 周次行之前的第一个非时间、非纯数字行视为课名；周次行内周次之后或其后首个含楼室线索的行视为教室。
     */
    private fun parseVertical(lines: List<String>): RawCourse? {
        if (lines.size < 2) return null
        // 周次行：含"周"字（或纯数字表达式）且非时间行——否则教室行里的数字（"教学楼302"）会被误判
        val weekIdx = lines.indexOfFirst {
            !it.contains(':') && (it.contains('周') || it.matches(Regex("[\\[\\]\\d,\\-–~，、\\s]+"))) &&
                normalizeWeeks(it).isNotBlank()
        }
        if (weekIdx < 0) return null
        val name = lines.take(weekIdx).firstOrNull { s ->
            s.length >= 2 && !s.contains(':') && !s.all { it.isDigit() } &&
                !s.contains('节') && !DAY_RE.containsMatchIn(s)
        } ?: return null
        if (name.length > 30) return null
        val weekLine = lines[weekIdx]
        val m = WEEK_RANGE.find(weekLine) ?: return null
        var room = weekLine.substring(m.range.last + 1).trim(' ', ',', '，', ';', '；', '-', '(', ')')
        if (room.isBlank()) {
            room = lines.drop(weekIdx + 1)
                .firstOrNull { ROOM_HINT.containsMatchIn(it) && !it.all { c -> c.isDigit() } && !it.contains('周') } ?: ""
        }
        return RawCourse(name, cleanRoom(room), normalizeWeeks(weekLine))
    }

    private val ROOM_HINT = Regex("(楼|室|馆|场|区|中心|教|号楼|栋|厅)|\\d")

    private val TEACHER_PREFIX = Regex("^([\u4e00-\u9fa5]{2,4})([,，][\u4e00-\u9fa5]{2,4})*\\s+")
    // 课程代码兼容数字开头（如 "3YJ1512A.01"）与字母开头（如 "TS1003A.01"）两类
    private val COURSE_CODE = Regex("[A-Za-z0-9]{4,}\\.[0-9]+")
    private val PAREN_GROUP = Regex("\\(([^()]*)\\)")

    /**
     * 一类教务格式（特征：尾括号是“周次,教室”、其前是课程代码括号）：
     * "王青峡 材料成型传输原理(3YJ1042A.01)(4-11,教学主楼D405)" → 课名 / 教室 / 4-11。
     * OCR 截图会把括号内容拆行，逐行解析失败后合并整格再试一次。
     */
    private fun parseBracketed(text: String): List<RawCourse> {
        val out = mutableListOf<RawCourse>()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        fun parseLine(line0: String): RawCourse? {
            val line = line0.replace('（', '(').replace('）', ')')
            val groups = PAREN_GROUP.findAll(line).toList()
            if (groups.size < 2) return null
            val last = groups.last().groupValues[1].trim()
            val wm = WEEK_RANGE.find(last) ?: return null
            val weeks = normalizeWeeks(wm.value)
            if (weeks.isBlank()) return null
            if (!COURSE_CODE.matches(groups[groups.size - 2].groupValues[1].trim())) return null
            val room = last.substring(wm.range.last + 1).trim(' ', ',', '，', ';', '；')
            val head = TEACHER_PREFIX
                .replace(line.substring(0, groups[groups.size - 2].range.first).trim(), "")
                .trim(' ', ',', '，', ';', '；')
            if (head.length < 2 || head.any { it.isDigit() } || head.contains("节")) return null
            return RawCourse(head, cleanRoom(room), weeks)
        }
        for (raw in lines) parseLine(raw)?.let { out.add(it) }
        if (out.isEmpty() && lines.size > 1) parseLine(lines.joinToString(" "))?.let { out.add(it) }
        return out
    }

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

    // ---------- 线性课表格式（网页课表复制：jsxsd 等教务系统） ----------

    private val BLOCK_SECTION = Regex("^第[一二三四五六七八九十]+[一二三四五六七八九十]?节$")
    private val WEEKS_DAY = Regex("\\[([^\\]]*周)[^\\]]*\\]\\s*(星期[一二三四五六日天])")
    private val ROOM_LIKE = Regex("(楼|馆|室|厅|场|中心)|^[A-Za-z]?\\d{2,4}$")

    /** 线性格式特征：含"[周次] 星期X"或"教师："标记 */
    fun looksLinear(text: String): Boolean =
        WEEKS_DAY.containsMatchIn(text) || text.contains("教师：") || text.contains("教师:")

    /**
     * 解析"整表线性复制"的课表文本（每行一个字段，表格列结构在复制时丢失）。
     * - 完整条目（自带"[周次] 星期X"）→ 精确还原：课名 / 星期 / 节次 / 周次 / 教室
     * - 简略条目（仅"课名 + 教室"）→ 无法确定星期，输出为「未定位」（day=0），在预览页手动选星期；
     *   同名课程若已由完整条目定位则跳过（重复展示），同名简略只保留一条
     * - "备注"行之后的内容（课程设计等）不属于课表，忽略
     */
    fun parseLinear(lines0: List<String>): Result {
        val lines = lines0.map { it.trim() }.filter { it.isNotEmpty() }
        val courses = mutableListOf<Course>()
        val located = mutableSetOf<String>()
        fun norm(s: String) = s.replace(Regex("[\\s　]"), "")
        var curSlot = -1
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("备注")) break
            if (BLOCK_SECTION.matches(line)) {
                slotIndexOf(line)?.let { curSlot = it }
                i++
                continue
            }
            if (line.startsWith("(") || line.startsWith("（")) { i++; continue } // (01、02小节)
            if (line == "节次" || dayFromCell(line) > 0) { i++; continue }        // 表头

            // 完整条目：课名 / 教师：X / 01~02节 / [周次] 星期X / 教室
            val next = lines.getOrNull(i + 1) ?: ""
            if (next.startsWith("教师：") || next.startsWith("教师:")) {
                var j = i + 2
                while (j < lines.size && j < i + 6 && !WEEKS_DAY.containsMatchIn(lines[j])) j++
                val wl = lines.getOrNull(j) ?: ""
                val m = WEEKS_DAY.find(wl)
                if (m != null) {
                    val weeks = normalizeWeeks(m.groupValues[1])
                    val day = dayIndex(m.groupValues[2].firstOrNull() ?: ' ')
                    val courseSlot = lines.getOrNull(i + 2)?.let { slotIndexOf(it) } ?: curSlot
                    val room = cleanRoom(lines.getOrNull(j + 1) ?: "")
                    if (day > 0 && weeks.isNotBlank() && courseSlot != null) {
                        courses.add(Course(day, courseSlot, line, room, weeks))
                        located.add(norm(line))
                        i = j + 1
                        continue
                    }
                }
            }

            // 简略条目：课名 + 教室（无时间信息）
            val roomLine = lines.getOrNull(i + 1) ?: ""
            if (curSlot >= 0 && norm(line) !in located &&
                ROOM_LIKE.containsMatchIn(roomLine) && !roomLine.contains("周") &&
                roomLine.length <= 16 && dayFromCell(line) == 0 && !line.startsWith("教师")
            ) {
                courses.add(Course(0, curSlot, line, cleanRoom(roomLine), "1-25"))
                located.add(norm(line)) // 同名简略只保留一条
                i += 2
                continue
            }
            i++
        }
        val unlocated = courses.count { it.day == 0 }
        val err = when {
            courses.isEmpty() -> "没解析出课程：请确认复制时包含了完整课表表格（含“教师：”“[周次] 星期一”这类内容）"
            unlocated > 0 -> "提示：有 $unlocated 门次课程缺少时间信息（已标记「未定位」），请在下方逐条选择星期；用「剪贴板」或教务导出 Excel 可获得更完整的时间信息"
            else -> null
        }
        return Result(courses, err)
    }
}

package com.monesy.kaoyan

import android.content.Context
import android.net.Uri
import jxl.Workbook
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 读取 Excel 为二维字符串网格。
 * - .xls（BIFF8/OLE2）→ jxl 库
 * - .xlsx（zip+XML）→ 自研解析（零依赖，处理共享字符串/内联字符串/共享公式）
 */
object ExcelReader {

    fun readGrid(context: Context, uri: Uri): List<List<String>> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取所选文件")
        if (bytes.size < 4) throw IllegalArgumentException("文件为空或格式不正确")
        return when {
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> readXlsx(bytes)   // PK zip
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() -> readXls(bytes)    // OLE2
            else -> throw IllegalArgumentException("不是 Excel 文件（.xls / .xlsx），请用教务系统导出的课表")
        }
    }

    // ---------- .xls ----------

    private fun readXls(bytes: ByteArray): List<List<String>> {
        val wb = Workbook.getWorkbook(ByteArrayInputStream(bytes))
        try {
            val sheet = wb.getSheet(0)
            return (0 until sheet.rows).map { r ->
                (0 until sheet.columns).map { c ->
                    runCatching { sheet.getCell(c, r).contents ?: "" }.getOrDefault("")
                }
            }
        } finally {
            wb.close()
        }
    }

    // ---------- .xlsx ----------

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        var sharedStrings: List<String> = emptyList()
        var sheetXml: ByteArray? = null
        var fallbackSheet: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(zip)
                    name == "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") && fallbackSheet == null ->
                        fallbackSheet = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val xml = sheetXml ?: fallbackSheet ?: throw IllegalArgumentException("xlsx 里没有找到工作表")
        return parseSheet(xml, sharedStrings)
    }

    private fun newParser(input: java.io.InputStream): XmlPullParser {
        val p = android.util.Xml.newPullParser()
        p.setInput(input, "UTF-8")
        return p
    }

    private fun parseSharedStrings(input: java.io.InputStream): List<String> {
        val out = mutableListOf<String>()
        val p = newParser(input)
        var event = p.eventType
        val sb = StringBuilder()
        var inSi = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> if (inSi) sb.append(p.nextText().orEmpty())
                }
                XmlPullParser.END_TAG -> if (p.name == "si") { out.add(sb.toString()); inSi = false }
            }
            event = p.next()
        }
        return out
    }

    private data class CellRec(
        val row: Int,
        val col: Int,
        val type: String,
        val value: String?,
        val inline: String?,
        /** 本格公式体（普通公式或共享公式主格才有） */
        val formula: String?,
        /** 共享公式组 id */
        val si: Int?,
    ) {
        fun ref(): String = colName(col) + (row + 1)
    }

    /**
     * 两遍解析：第一遍收集全部单元格与共享公式组基准（si → 基准格 + 公式体），
     * 第二遍落网格——有缓存值直接用；没有缓存值的公式格（教务系统导出的表很常见）
     * 按共享公式平移引用后取被引用格的值。只支持"取引用格内容"级别的公式，
     * 复杂公式解析不出就为空，由导入预览页兜底。
     */
    private fun parseSheet(xmlBytes: ByteArray, shared: List<String>): List<List<String>> {
        val recs = mutableListOf<CellRec>()
        val sharedBases = mutableMapOf<Int, Pair<String, String>>() // si -> (基准ref, 公式体)

        val p = newParser(ByteArrayInputStream(xmlBytes))
        var event = p.eventType
        var curRow = -1
        var curRef = ""
        var curType = ""
        var cellValue: String? = null
        var inlineText: StringBuilder? = null
        var formula: String? = null
        var si: Int? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> curRow = p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1) ?: (curRow + 1)
                    "c" -> {
                        curRef = p.getAttributeValue(null, "r") ?: ""
                        curType = p.getAttributeValue(null, "t") ?: ""
                        cellValue = null; inlineText = null; formula = null; si = null
                    }
                    "v" -> cellValue = p.nextText()
                    "is" -> inlineText = StringBuilder()
                    "t" -> if (inlineText != null) inlineText!!.append(p.nextText().orEmpty())
                    "f" -> {
                        si = p.getAttributeValue(null, "si")?.toIntOrNull()
                        val body = if (p.isEmptyElementTag) "" else p.nextText().orEmpty()
                        if (p.getAttributeValue(null, "t") == "shared" && si != null) {
                            val baseRef = p.getAttributeValue(null, "ref")?.substringBefore(":") ?: curRef
                            if (body.isNotBlank()) {
                                // 主格：记录组基准；其余同组格随后按基准平移
                                sharedBases[si] = baseRef to body
                                if (baseRef == curRef) formula = body
                            }
                        } else if (body.isNotBlank()) {
                            formula = body
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "c" -> {
                        if (curRow >= 0 && curRef.isNotEmpty()) {
                            recs.add(
                                CellRec(
                                    row = curRow, col = colIndex(curRef), type = curType,
                                    value = cellValue, inline = inlineText?.toString(),
                                    formula = formula, si = si,
                                )
                            )
                        }
                        cellValue = null; inlineText = null; formula = null; si = null
                    }
                }
            }
            event = p.next()
        }

        val byRef = recs.associateBy { it.ref() }
        val cache = HashMap<String, String>()

        fun valueOfRec(rec: CellRec?, depth: Int): String {
            if (rec == null || depth > 6) return ""
            cache[rec.ref()]?.let { return it }
            val direct = when (rec.type) {
                "s" -> rec.value?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                "inlineStr" -> rec.inline ?: ""
                else -> rec.value ?: ""
            }
            if (direct.isNotEmpty()) { cache[rec.ref()] = direct; return direct }
            // 公式求值：从属格（只有 si）按组基准平移公式引用
            var f = rec.formula
            if (f.isNullOrBlank() && rec.si != null) {
                sharedBases[rec.si]?.let { (base, body) -> f = shiftFormula(body, base, rec.ref()) }
            }
            val ff = f ?: return ""
            // 剥掉字符串字面量再找第一个单元格引用
            val target = REF_RE.find(STR_LIT.replace(ff, "").replace("$", ""))?.value ?: return ""
            return valueOfRec(byRef[target.uppercase()], depth + 1)
        }

        val grid = mutableListOf<MutableList<String>>()
        fun put(row: Int, col: Int, value: String) {
            while (grid.size <= row) grid.add(mutableListOf())
            val r = grid[row]
            while (r.size <= col) r.add("")
            r[col] = value
        }
        for (rec in recs) put(rec.row, rec.col, valueOfRec(rec, 0))
        return grid
    }

    private val REF_RE = Regex("[A-Z]{1,3}\\d+")
    private val STR_LIT = Regex("\"[^\"]*\"")

    /** 共享公式从属格：把组基准公式里的相对引用按 (基准格 → 本格) 偏移平移，绝对引用不动 */
    private fun shiftFormula(formula: String, baseRef: String, atRef: String): String {
        val dCol = colIndex(atRef) - colIndex(baseRef)
        val dRow = rowNum(atRef) - rowNum(baseRef)
        if (dCol == 0 && dRow == 0) return formula
        return Regex("(\\$?)([A-Z]{1,3})(\\$?)(\\d+)").replace(formula) { m ->
            val absCol = m.groupValues[1] == "$"
            val absRow = m.groupValues[3] == "$"
            if (absCol && absRow) return@replace m.value
            val col = if (absCol) m.groupValues[2] else colName(colIndex(m.groupValues[2]) + dCol)
            val row = if (absRow) m.groupValues[4] else (m.groupValues[4].toInt() + dRow).toString()
            "${m.groupValues[1]}$col${m.groupValues[3]}$row"
        }
    }

    /** "B12" → 行 12（1 基） */
    private fun rowNum(ref: String): Int {
        var i = ref.length
        while (i > 0 && ref[i - 1].isDigit()) i--
        return ref.substring(i).toIntOrNull() ?: 0
    }

    /** 列序号 → 列字母：0 → "A" */
    private fun colName(idx: Int): String {
        if (idx < 0) return "A"
        var n = idx + 1
        val sb = StringBuilder()
        while (n > 0) {
            sb.insert(0, ('A' + (n - 1) % 26))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** "B12" → 列 1（0 基） */
    private fun colIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch.isLetter()) idx = idx * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return idx - 1
    }
}

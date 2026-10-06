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
 * - .xlsx（zip+XML）→ 自研解析（零依赖，处理共享字符串/内联字符串）
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

    private fun parseSheet(xmlBytes: ByteArray, shared: List<String>): List<List<String>> {
        val grid = mutableListOf<MutableList<String>>()
        fun put(row: Int, col: Int, value: String) {
            while (grid.size <= row) grid.add(mutableListOf())
            val r = grid[row]
            while (r.size <= col) r.add("")
            r[col] = value
        }

        val p = newParser(ByteArrayInputStream(xmlBytes))
        var event = p.eventType
        var curRow = -1
        var curCol = -1
        var cellType = ""
        var cellValue: String? = null
        var inlineText: StringBuilder? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> curRow = p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1) ?: (curRow + 1)
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r") ?: ""
                        curCol = colIndex(ref)
                        cellType = p.getAttributeValue(null, "t") ?: ""
                        cellValue = null
                        inlineText = null
                    }
                    "v" -> cellValue = p.nextText()
                    "is" -> inlineText = StringBuilder()
                    "t" -> if (inlineText != null) inlineText!!.append(p.nextText().orEmpty())
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "c" -> {
                        val raw = when (cellType) {
                            "s" -> cellValue?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "inlineStr" -> inlineText?.toString() ?: ""
                            else -> cellValue ?: ""
                        }
                        if (curRow >= 0 && curCol >= 0) put(curRow, curCol, raw)
                        cellValue = null
                        inlineText = null
                    }
                }
            }
            event = p.next()
        }
        return grid
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

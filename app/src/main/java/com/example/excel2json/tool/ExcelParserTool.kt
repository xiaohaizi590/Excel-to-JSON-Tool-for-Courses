package com.example.excel2json.tool

import java.io.InputStream
import android.util.Log
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory

object ExcelParserTool {
    private const val TAG = "Excel2Json"

    // ---- 周数行推断参数 ----
    private const val MIN_WEEK = 1              // 学期最小周次
    private const val MAX_WEEK = 30             // 学期最大周次
    private const val MAX_WEEK_SPAN = 30        // 单个区间的最大跨度，超过视为异常写法
    private const val MIN_WEEK_LINE_SCORE = 5   // 判定某行为周数行的最低得分
    private val WEEK_NUMBER_REGEX = Regex("(\\d+)\\s*[-~～至]\\s*(\\d+)|(\\d+)")
    private val WEEK_SPEC_REGEX = Regex("^[\\d,，、\\-~～\\s]+$")
    private val SECTION_BRACKET_REGEX = Regex("[（(\\[【][^）)\\]】]*节[^）)\\]】]*[）)\\]】]")
    // 地点/班级类关键字：含这些词的行不可能是周数行
    private val NON_WEEK_KEYWORDS = listOf("楼", "室", "号", "区", "教", "班", "课")

    /**
     * 解析二维课表 Excel
     * 逻辑：
     * 1. 找到"星期"行 → 确定星期一到星期日的列索引
     * 2. 找到"节次"列 → 确定各节次对应的行索引
     * 3. 遍历行列交叉点，提取每个单元格内的课程
     */
    fun parse(inputStream: InputStream): CourseTableImportModel {
        val courses = mutableListOf<ImportCourseJsonModel>()

        WorkbookFactory.create(inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            Log.i(TAG, "开始解析Excel，sheet名: ${sheet.sheetName}, 行数: ${sheet.lastRowNum + 1}")

            // ---------- 第一步：定位星期行 ----------
            val weekRowIndex = findWeekRow(sheet) ?: throw IllegalArgumentException("未找到包含'星期'的表头行")
            val weekRow = sheet.getRow(weekRowIndex)
            val weekColumnMap = mapWeekColumns(weekRow)
            Log.i(TAG, "星期行: 第${weekRowIndex + 1}行, 映射列: $weekColumnMap")

            // ---------- 第二步：定位节次列（在星期行之后查找） ----------
            val periodColumnIndex = findPeriodColumn(sheet, weekRowIndex + 1) ?: throw IllegalArgumentException("未找到包含'节'的列")
            val periodRowMap = mapPeriodRows(sheet, periodColumnIndex, weekRowIndex + 1)
            Log.i(TAG, "节次列: 第${periodColumnIndex + 1}列, 映射行: $periodRowMap")

            // ---------- 第三步：遍历矩阵提取课程 ----------
            for ((dayOfWeek, colIndex) in weekColumnMap) {
                for ((periodName, rowIndex) in periodRowMap) {
                    val cell = sheet.getRow(rowIndex)?.getCell(colIndex)
                    val cellContent = getCellContent(cell)
                    if (cellContent.isBlank()) continue

                    // 一个单元格内可能有多门课（用 ----- 分隔）
                    val courseBlocks = cellContent.split("---------------------")
                    for (block in courseBlocks) {
                        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isEmpty()) continue

                        val course = parseCourseBlock(lines, dayOfWeek, periodName, rowIndex + 1, colIndex + 1)
                        if (course != null) {
                            courses.add(course)
                            Log.d(TAG, "解析到课程: ${course.name}, 周${course.day} 第${course.startSection}-${course.endSection}节")
                        }
                    }
                }
            }
        }

        Log.i(TAG, "解析完成，共 ${courses.size} 门课程")
        courses.forEachIndexed { i, c ->
            Log.i(TAG, "  [${i + 1}] ${c.name} | 教师:${c.teacher} | 周${c.day} | 第${c.startSection}-${c.endSection}节 | 地点:${c.position} | 周数:${c.weeks}")
        }
        return CourseTableImportModel(courses, null, null)
    }

    // ======================= 定位函数 =======================

    /**
     * 查找包含"星期"的行（如"星期一 星期二 ..."）
     */
    private fun findWeekRow(sheet: Sheet): Int? {
        for (i in 0..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            for (cell in row) {
                val content = getCellContent(cell)
                if (content.contains("星期一") || content.contains("周二") || content.contains("星期")) {
                    return i
                }
            }
        }
        return null
    }

    /**
     * 映射星期列：遍历星期行，找出"星期一"到"星期日"对应的列索引
     * 返回 Map<星期数字(1-7), 列索引>
     */
    private fun mapWeekColumns(weekRow: Row): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        val weekKeywords = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

        for (cell in weekRow) {
            val content = getCellContent(cell)
            for ((index, keyword) in weekKeywords.withIndex()) {
                if (content.contains(keyword)) {
                    map[index + 1] = cell.columnIndex
                    break
                }
            }
        }
        return map
    }

    /**
     * 查找包含"节"的列（A列通常有"第一二节"、"第三四节"等）
     */
    private fun findPeriodColumn(sheet: Sheet, startRow: Int): Int? {
        for (colIdx in 0..10) {
            for (rowIdx in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                val cell = row.getCell(colIdx) ?: continue
                val content = getCellContent(cell)
                if (content.contains("节") && (content.contains("第") || content.contains("一二") || content.contains("三四"))) {
                    return colIdx
                }
            }
        }
        return null
    }

    /**
     * 映射节次行：在节次列中，找出"第一二节"、"第三四节"等对应的行索引
     * 返回 Map<节次名称, 行索引>
     */
    private fun mapPeriodRows(sheet: Sheet, periodColumnIndex: Int, startRow: Int): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in startRow..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val cell = row.getCell(periodColumnIndex) ?: continue
            val content = getCellContent(cell)
            Log.d(TAG, "第${i}行节次列内容: '$content'")
            if (content.contains(Regex("第[一二三四五六七八九十]+节"))) {
                map[content.trim()] = i
            }
        }
        return map
    }

    // ======================= 课程块解析 =======================

    /**
     * 解析一个课程块（行数不固定，可能多出"班级"等行）
     * 常见内容：课程名 / (班级) / 教师(职称) / 周数([周])[节次] / 地点
     * 周数行位置不固定，以"周"、"节"为锚点定位（相邻两行内匹配），例如：
     *   1-8([周])[03-04节]
     *   1-2,4,6,8,12,14,16([周])[01-02节]
     */
    private fun parseCourseBlock(
        lines: List<String>,
        day: Int,
        periodName: String,
        rowNum: Int,
        colNum: Int
    ): ImportCourseJsonModel? {
        if (lines.isEmpty()) return null

        // 提取课程名（第一行）
        val name = lines.getOrNull(0)?.trim() ?: return null
        if (name.isEmpty()) return null

        // 定位并提取周数行（位置不固定）
        val weekLineIndex = findWeekLineIndex(lines)
        val weeks = if (weekLineIndex >= 0) parseWeeksFromLine(lines[weekLineIndex]) else emptyList()
        if (weeks.isEmpty()) {
            throw IllegalArgumentException("未识别到周数：请在周数行加入\"周\"字符，例如 1-8([周])[01-02节]")
        }

        // 提取教师（周数行的上一行，找不到周数行时退回第二行）
        val teacherLine = if (weekLineIndex > 0) lines[weekLineIndex - 1] else lines.getOrNull(1) ?: ""
        val teacher = teacherLine.replace(Regex("[（(].*[）)]"), "").trim() // 去掉职称/班级括号

        // 提取地点（周数行之后的行，可能多个地点）
        val positions = mutableListOf<String>()
        val startIndex = if (weekLineIndex >= 0) weekLineIndex + 1 else 3
        for (i in startIndex until lines.size) {
            val line = lines[i].trim()
            if (line.isNotEmpty() && !line.contains("周") && !line.contains("节")) {
                positions.add(line)
            }
        }
        val position = positions.joinToString(";")

        // 解析节次名称获取 start/end
        val (startSection, endSection) = parsePeriodName(periodName)

        return ImportCourseJsonModel(
            name = name,
            teacher = teacher,
            position = position,
            day = day,
            startSection = startSection,
            endSection = endSection,
            weeks = weeks
        )
    }

    /**
     * 定位周数行：对块内每一行打分，取可信度最高的行
     * 打分依据：是否含"周"、"节"（锚点）、是否含节次括号、是否"单/双"周、
     *          去括号后是否只由数字和分隔符组成、周次是否在合理范围且升序、是否紧邻含"节"的行
     * 返回行索引，最高分达不到阈值则返回 -1
     */
    private fun findWeekLineIndex(lines: List<String>): Int {
        var bestIndex = -1
        var bestScore = 0
        for (i in lines.indices) {
            val score = scoreWeekLine(lines, i)
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        if (bestIndex >= 0 && bestScore >= MIN_WEEK_LINE_SCORE) {
            Log.d(TAG, "周数行推断: 第${bestIndex}行 '${lines[bestIndex]}' (得分 $bestScore)")
            return bestIndex
        }
        Log.w(TAG, "未能推断出周数行: $lines")
        return -1
    }

    /**
     * 计算某一行作为周数行的可信度得分，0 表示不可能
     */
    private fun scoreWeekLine(lines: List<String>, index: Int): Int {
        val line = lines[index].trim()
        if (line.isEmpty()) return 0

        // 含英文字母（3B311）或地点/班级类关键字（7号楼B316、计科教学班）→ 直接排除
        if (line.any { it in 'A'..'Z' || it in 'a'..'z' }) return 0
        if (NON_WEEK_KEYWORDS.any { line.contains(it) }) return 0

        // 去除括号与节次描述后必须能解析出周次
        val cleaned = cleanWeekLine(line)
        val weeks = extractWeekNumbers(cleaned)
        if (weeks.isEmpty()) return 0
        // 周次必须落在一学期的合理范围（1-30 周）
        if (weeks.any { it !in MIN_WEEK..MAX_WEEK }) return 0

        var score = 0
        if (line.contains("周")) score += 4                                    // 显式"周"锚点
        if (line.contains("节")) score += 3                                    // 显式"节"锚点
        if (SECTION_BRACKET_REGEX.containsMatchIn(line)) score += 2             // 形如 [03-04节]
        if (line.contains("单") || line.contains("双")) score += 2              // 单/双周
        if (WEEK_SPEC_REGEX.matches(cleaned)) score += 4                        // 纯周次写法，如 "17"
        if (index > 0 && lines[index - 1].contains("节")) score += 1            // 紧邻节次行
        if (index + 1 < lines.size && lines[index + 1].contains("节")) score += 1
        if (weeks == weeks.sorted()) score += 1                                 // 周次递增，符合写法习惯
        return score
    }

    /**
     * 去掉括号/中括号内容（如 ([周])、([])、(单)、[03-04节]）、"第x-x节"描述和"周"字符
     */
    private fun cleanWeekLine(line: String): String = line
        .replace(Regex("[（(][^）)]*[）)]"), "")
        .replace(Regex("[\\[【][^\\]】]*[\\]】]"), "")
        .replace(Regex("第\\s*[\\d一二三四五六七八九十]+\\s*[-~～至]?\\s*[\\d一二三四五六七八九十]*\\s*节"), "")
        .replace("周", "")
        .trim()

    /**
     * 从 "1-2,4,6,8,12,14,16" 这类文本中提取周次
     * 支持区间（-, ~, ～, 至）与枚举，自动纠正倒序区间
     */
    private fun extractWeekNumbers(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Int>()
        WEEK_NUMBER_REGEX.findAll(text).forEach { match ->
            val rangeStart = match.groupValues[1]
            if (rangeStart.isNotEmpty()) {
                val a = rangeStart.toInt()
                val b = match.groupValues[2].toInt()
                val from = minOf(a, b)
                val to = maxOf(a, b)
                if (to - from <= MAX_WEEK_SPAN) result.addAll(from..to) // 过滤异常大区间
            } else {
                result.add(match.groupValues[3].toInt())
            }
        }
        return result.distinct()
    }

    /**
     * 从 "1-2,4,6,8,12,14,16([周])[01-02节]" 中提取周数列表
     * 支持："1-8([周])[03-04节]" → [1..8]，"17([])[01-02节]" → [17]
     */
    private fun parseWeeksFromLine(line: String): List<Int> =
        extractWeekNumbers(cleanWeekLine(line)).sorted()

    /**
     * 解析节次名称："第一二节" → (1, 2), "第三四节" → (3, 4)
     */
    private fun parsePeriodName(periodName: String): Pair<Int, Int> {
        val numMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8,
            "九" to 9, "十" to 10, "十一" to 11, "十二" to 12
        )

        val cleaned = periodName.replace("第", "").replace("节", "")
        return when {
            cleaned.contains("一二") -> 1 to 2
            cleaned.contains("三四") -> 3 to 4
            cleaned.contains("五六") -> 5 to 6
            cleaned.contains("七八") -> 7 to 8
            cleaned.contains("九十") -> 9 to 10
            cleaned.contains("十一十二") -> 11 to 12
            else -> {
                val chars = cleaned.toList().mapNotNull { numMap[it.toString()] }
                if (chars.size >= 2) chars[0] to chars[1]
                else if (chars.size == 1) chars[0] to chars[0]
                else 1 to 2
            }
        }
    }

    // ======================= 辅助工具 =======================

    private fun getCellContent(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
            CellType.FORMULA -> {
                try { cell.stringCellValue.trim() } catch (_: Exception) { "" }
            }
            else -> ""
        }
    }
}

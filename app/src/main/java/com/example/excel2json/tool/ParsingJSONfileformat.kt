package com.example.excel2json.tool

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 导入原课程表导出的json文件，对json进行解析，将格式进行保存（弹出个是否记录json格式的窗口），
 * 后面导入execl转出json文件的时候进行格式填充和比对
 */
class ParsingJSONfileformat {

    /**
     * 表示一个被检测到的JSON格式模板
     */
    data class JsonFormat(
        /** 根对象中课程数组的键名（如 "courses"、"data"、"items"） */
        val rootArrayKey: String,
        /** 根对象中除课程数组外的其他字段名 */
        val rootOtherKeys: List<String>,
        /** 课程对象中所有字段名（按顺序） */
        val courseFieldNames: List<String>,
        /** 标准字段 -> 目标字段名的映射（仅包含能匹配上的字段） */
        val fieldMapping: Map<String, String>
    )

    companion object {
        private const val TAG = "Excel2Json"
        private const val PREFS_NAME = "json_format_prefs"
        private const val FORMAT_KEY = "saved_json_format"

        // 本工具使用的标准字段
        private val STANDARD_FIELDS = listOf("name", "teacher", "position", "day", "startSection", "endSection", "weeks")

        /**
         * 解析JSON字符串并检测其格式结构
         *
         * @param jsonString 用户导入的参考JSON
         * @return 检测到的格式信息，解析失败返回null
         */
        fun detectFormat(jsonString: String): JsonFormat? {
            return try {
                val root = JSONObject(jsonString)

                // 在根对象中查找课程数组
                val (arrayKey, courseArray) = findCourseArray(root) ?: run {
                    Log.w(TAG, "未在JSON中找到课程对象数组")
                    return null
                }

                // 提取课程对象的字段名
                val courseFields = mutableListOf<String>()
                if (courseArray.length() > 0) {
                    val firstCourse = courseArray.getJSONObject(0)
                    val keys = firstCourse.keys()
                    while (keys.hasNext()) {
                        courseFields.add(keys.next())
                    }
                }

                // 提取根对象其他字段
                val otherKeys = mutableListOf<String>()
                val rootKeys = root.keys()
                while (rootKeys.hasNext()) {
                    val key = rootKeys.next()
                    if (key != arrayKey) {
                        otherKeys.add(key)
                    }
                }

                // 智能匹配标准字段到目标字段
                val mapping = mutableMapOf<String, String>()
                for (standardField in STANDARD_FIELDS) {
                    val matched = matchField(standardField, courseFields)
                    if (matched != null) {
                        mapping[standardField] = matched
                    }
                }

                Log.i(TAG, "检测到格式: arrayKey=$arrayKey, 字段映射=$mapping")
                JsonFormat(
                    rootArrayKey = arrayKey,
                    rootOtherKeys = otherKeys,
                    courseFieldNames = courseFields,
                    fieldMapping = mapping
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析JSON格式失败: ${e.message}", e)
                null
            }
        }

        /**
         * 在JSON根对象中查找课程数组
         * 优先匹配常见键名，否则返回第一个元素为对象的数组
         */
        private fun findCourseArray(root: JSONObject): Pair<String, JSONArray>? {
            // 优先查找常见键名
            val preferredKeys = listOf("courses", "courseList", "course_list", "data", "items", "list", "rows", "results")
            for (key in preferredKeys) {
                val value = root.opt(key)
                if (value is JSONArray && value.length() > 0 && value.opt(0) is JSONObject) {
                    return key to value
                }
            }

            // 遍历查找第一个包含对象的数组
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = root.opt(key)
                if (value is JSONArray && value.length() > 0 && value.opt(0) is JSONObject) {
                    return key to value
                }
            }
            return null
        }

        /**
         * 智能匹配标准字段名到目标JSON中的字段名
         * 支持直接匹配、忽略大小写匹配、语义匹配
         */
        private fun matchField(standardField: String, availableFields: List<String>): String? {
            // 1. 精确匹配
            if (standardField in availableFields) return standardField

            // 2. 忽略大小写匹配
            val lowerField = standardField.lowercase()
            for (field in availableFields) {
                if (field.lowercase() == lowerField) return field
            }

            // 3. 包含关系匹配
            for (field in availableFields) {
                val lower = field.lowercase()
                if (lower.contains(lowerField) || lowerField.contains(lower)) return field
            }

            // 4. 语义匹配（常见变体）
            for (field in availableFields) {
                val lower = field.lowercase()
                val matched = when (standardField) {
                    "name" -> lower.contains("course") || lower.contains("class") ||
                            lower.contains("subject") || lower.contains("lesson") ||
                            lower.contains("课程") || lower.contains("名称")
                    "teacher" -> lower.contains("teacher") || lower.contains("instructor") ||
                            lower.contains("tutor") || lower.contains("professor") ||
                            lower.contains("教师") || lower.contains("老师")
                    "position" -> lower.contains("position") || lower.contains("location") ||
                            lower.contains("room") || lower.contains("classroom") ||
                            lower.contains("place") || lower.contains("地点") || lower.contains("教室")
                    "day" -> lower.contains("day") || lower.contains("weekday") ||
                            lower.contains("星期") || lower.contains("周")
                    "startSection" -> (lower.contains("start") || lower.contains("begin")) &&
                            (lower.contains("section") || lower.contains("period") || lower.contains("节"))
                    "endSection" -> (lower.contains("end") || lower.contains("stop")) &&
                            (lower.contains("section") || lower.contains("period") || lower.contains("节"))
                    "weeks" -> lower.contains("week") || lower.contains("周数") ||
                            lower.contains("周次")
                    else -> false
                }
                if (matched) return field
            }

            // 针对 startSection/endSection 的二次匹配
            if (standardField == "startSection") {
                for (field in availableFields) {
                    val lower = field.lowercase()
                    if ((lower.contains("start") || lower.contains("begin")) && !lower.contains("end")) return field
                }
            }
            if (standardField == "endSection") {
                for (field in availableFields) {
                    val lower = field.lowercase()
                    if ((lower.contains("end") || lower.contains("stop")) && !lower.contains("start") && !lower.contains("begin")) return field
                }
            }

            return null
        }

        // ======================= 持久化 =======================

        /**
         * 保存格式模板到 SharedPreferences
         */
        fun saveFormat(context: Context, format: JsonFormat) {
            val json = JSONObject().apply {
                put("rootArrayKey", format.rootArrayKey)
                put("rootOtherKeys", JSONArray(format.rootOtherKeys))
                put("courseFieldNames", JSONArray(format.courseFieldNames))
                put("fieldMapping", JSONObject(format.fieldMapping))
            }.toString()

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(FORMAT_KEY, json)
                .apply()
            Log.i(TAG, "JSON格式模板已保存: $json")
        }

        /**
         * 加载已保存的格式模板
         */
        fun loadFormat(context: Context): JsonFormat? {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(FORMAT_KEY, null) ?: return null
            return try {
                val obj = JSONObject(json)
                val mapping = mutableMapOf<String, String>()
                val mappingObj = obj.optJSONObject("fieldMapping") ?: JSONObject()
                val mappingKeys = mappingObj.keys()
                while (mappingKeys.hasNext()) {
                    val key = mappingKeys.next()
                    mapping[key] = mappingObj.getString(key)
                }

                val rootOtherKeys = mutableListOf<String>()
                val rootOtherArray = obj.optJSONArray("rootOtherKeys")
                if (rootOtherArray != null) {
                    for (i in 0 until rootOtherArray.length()) {
                        rootOtherKeys.add(rootOtherArray.getString(i))
                    }
                }

                val courseFieldNames = mutableListOf<String>()
                val courseFieldArray = obj.optJSONArray("courseFieldNames")
                if (courseFieldArray != null) {
                    for (i in 0 until courseFieldArray.length()) {
                        courseFieldNames.add(courseFieldArray.getString(i))
                    }
                }

                JsonFormat(
                    rootArrayKey = obj.getString("rootArrayKey"),
                    rootOtherKeys = rootOtherKeys,
                    courseFieldNames = courseFieldNames,
                    fieldMapping = mapping
                )
            } catch (e: Exception) {
                Log.e(TAG, "加载格式模板失败", e)
                null
            }
        }

        /**
         * 清除已保存的格式模板
         */
        fun clearFormat(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(FORMAT_KEY)
                .apply()
            Log.i(TAG, "JSON格式模板已清除")
        }

        /**
         * 检查是否已保存格式模板
         */
        fun hasSavedFormat(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .contains(FORMAT_KEY)
        }

        // ======================= 格式应用 =======================

        /**
         * 根据保存的格式模板，将标准数据重新格式化为目标格式的JSON字符串
         *
         * @param standardJson 标准的 CourseTableImportModel 格式JSON
         * @param format       保存的格式模板
         * @return 按目标格式重组后的JSON字符串
         */
        fun applyFormat(standardJson: String, format: JsonFormat): String {
            return try {
                val standardRoot = JSONObject(standardJson)
                val standardCourses = standardRoot.optJSONArray("courses") ?: JSONArray()

                val targetRoot = JSONObject()
                val targetCourses = JSONArray()

                // 将标准课程字段名映射为目标字段名
                for (i in 0 until standardCourses.length()) {
                    val standardCourse = standardCourses.getJSONObject(i)
                    val targetCourse = JSONObject()

                    for (targetField in format.courseFieldNames) {
                        // 反向查找：目标字段对应的标准字段是什么
                        val sourceField = format.fieldMapping.entries
                            .firstOrNull { it.value == targetField }
                            ?.key

                        if (sourceField != null && standardCourse.has(sourceField)) {
                            targetCourse.put(targetField, standardCourse.get(sourceField))
                        } else {
                            // 无映射的字段尝试直接取值，否则填null
                            targetCourse.put(targetField, if (standardCourse.has(targetField)) standardCourse.get(targetField) else JSONObject.NULL)
                        }
                    }

                    targetCourses.put(targetCourse)
                }

                targetRoot.put(format.rootArrayKey, targetCourses)

                // 复制根对象的其他字段
                for (key in format.rootOtherKeys) {
                    if (standardRoot.has(key)) {
                        targetRoot.put(key, standardRoot.get(key))
                    } else {
                        targetRoot.put(key, JSONObject.NULL)
                    }
                }

                targetRoot.toString(2)
            } catch (e: Exception) {
                Log.e(TAG, "应用格式模板失败", e)
                standardJson // 应用失败时返回原始JSON
            }
        }
    }
}

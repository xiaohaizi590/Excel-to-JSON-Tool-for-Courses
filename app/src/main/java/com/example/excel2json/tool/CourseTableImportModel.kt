package com.example.excel2json.tool

import com.squareup.moshi.JsonClass

//封装课表
@JsonClass(generateAdapter = true)
data class CourseTableImportModel(
    val courses: List<ImportCourseJsonModel>,
    val semester: String? = null,
    val note: String? = null
)
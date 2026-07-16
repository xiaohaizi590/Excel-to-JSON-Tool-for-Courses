package com.example.excel2json.tool

import com.squareup.moshi.JsonClass

//对解析数据进行重组封装

@JsonClass(generateAdapter = true)
data class ImportCourseJsonModel(
    val name: String,
    val teacher: String,
    val position: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>
)//封装课程


package com.example.excel2json.tool

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

//打包机，json的序列化与反序列化 相当于操作类

object JsonHelper{
    private const val TAG = "Excel2Json"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    //将 CourseTableImportModel 转为格式化的 JSON
    fun toPrettyJson(data:CourseTableImportModel):String{
        Log.i(TAG, "开始序列化课表，共 ${data.courses.size} 门课程")
        val adapter=moshi.adapter(CourseTableImportModel::class.java)//要翻译的目标类型
        // Moshi 默认不缩进，但可以通过 indent 方法实现
        val json = adapter.indent("  ").toJson(data)
        Log.d(TAG, "序列化完成，JSON长度: ${json.length} 字符")
        return json
    }
}
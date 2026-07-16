package com.example.excel2json.tool

//导出json
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class ExportJsonWrapper(
    private val activity: ComponentActivity,
    private val onExportResult: (Boolean, String) -> Unit
) {
    //选择保存路径和写入文件是两个动作
    //写个临时缓存变量，保存内容，等待写入
    private var jsonContent: String = ""
    //写入文件有几个步骤
    //创建新文件
    private val launcher: ActivityResultLauncher<String> = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->//声明了一个参数
        if (uri != null) {
            Log.i(TAG, "用户选择了保存路径: $uri")
            val success = writeToFile(activity, uri, jsonContent)
            jsonContent = ""
            if (success) {
                Log.i(TAG, "JSON文件写入成功")
                onExportResult(true, "导出成功")
            } else {
                Log.e(TAG, "JSON文件写入失败")
                onExportResult(false, "导出失败")
            }
        } else {
            Log.w(TAG, "用户取消了导出")
            jsonContent = ""
            onExportResult(false, "取消导出")
        }
    }

    //导出json的启动器,带json内容，文件名
    fun exportJson(json: String, defaultFileName: String = "course_table.json") {
        Log.i(TAG, "开始导出JSON，文件名: $defaultFileName，内容长度: ${json.length} 字符")
        jsonContent = json
        launcher.launch(defaultFileName)//参数作为文件名
    }

    //写入文件的方法
    private fun writeToFile(context: Context, uri: Uri, content: String): Boolean {
        return try {
            //contentResolver是一个安卓系统的核心类。  ?表示前面不通过时，后面不进行执行操作 带use具备自回收功能
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                //将内容放入缓存区,BufferedWriter是一个类,OutputStreamWriter是一个方法
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.write(content)
                }
            }
            Log.d(TAG, "文件写入完成")
            true
        } catch (e: Exception) {
            //捕获所有异常，因为用户只需要知道没有成功就可以了
            Log.e(TAG, "写入文件时发生异常", e)
            false
        }
    }

    companion object {
        private const val TAG = "Excel2Json"
    }
}
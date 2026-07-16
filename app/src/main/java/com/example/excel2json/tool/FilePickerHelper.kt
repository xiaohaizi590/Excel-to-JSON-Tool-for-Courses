package com.example.excel2json.tool

//文件选择
import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

class FilePickerHelper(
    //传入两个参数
    private val activity: ComponentActivity,
    private val onFileSelected: (Uri?) -> Unit//onFileSelected：这是一个回调函数（Lambda）。外部调用者会传入一段代码
) {
    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            Log.d(TAG, "用户选择的文件URI: $uri")
            onFileSelected(uri)
        }

    fun pickExcelFile() {//传入文件的类型
        Log.i(TAG, "打开文件选择器")
        val mimeTypes = arrayOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
        launcher.launch(mimeTypes)
    }

    companion object {
        private const val TAG = "Excel2Json"
    }
}
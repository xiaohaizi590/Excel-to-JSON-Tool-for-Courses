package com.example.excel2json.tool

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log

object ClipboardHelper {
    private const val TAG = "Excel2Json"

    fun copyToClipboard(context: Context, text: String, label: String = "课程表JSON"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Log.i(TAG, "复制到剪贴板成功，内容长度: ${text.length} 字符")
            true
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
            false
        }
    }
}
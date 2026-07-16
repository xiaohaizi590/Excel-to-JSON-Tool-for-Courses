package com.example.excel2json

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.excel2json.tool.CourseTableImportModel
import com.example.excel2json.tool.ExcelParserTool
import com.example.excel2json.tool.ExportJsonWrapper
import com.example.excel2json.tool.FilePickerHelper
import com.example.excel2json.tool.ImportCourseJsonModel
import com.example.excel2json.tool.ClipboardHelper
import com.example.excel2json.tool.JsonHelper
import com.example.excel2json.tool.ParsingJSONfileformat
import com.example.excel2json.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var parsedModel by mutableStateOf<CourseTableImportModel?>(null)
    private var statusText by mutableStateOf("请选择一个Excel课表文件")

    // JSON格式参考相关状态
    private var savedFormat by mutableStateOf<ParsingJSONfileformat.JsonFormat?>(null)
    private var detectedFormat by mutableStateOf<ParsingJSONfileformat.JsonFormat?>(null)
    private var showSaveFormatDialog by mutableStateOf(false)
    private var useFormatExport by mutableStateOf(false)
    private var importedJsonContent by mutableStateOf<String?>(null)

    private lateinit var filePicker: FilePickerHelper
    private lateinit var exportHelper: ExportJsonWrapper

    // JSON文件选择器
    private val jsonFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            statusText = "未选择JSON文件"
            return@registerForActivityResult
        }
        statusText = "正在解析JSON格式..."
        lifecycleScope.launch {
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                }
                if (jsonContent != null) {
                    importedJsonContent = jsonContent
                    val format = ParsingJSONfileformat.detectFormat(jsonContent)
                    if (format != null) {
                        detectedFormat = format
                        showSaveFormatDialog = true
                        statusText = "已检测到JSON格式，是否保存？"
                    } else {
                        statusText = "无法识别JSON中的课程数据格式"
                        Toast.makeText(this@MainActivity, "JSON格式识别失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    statusText = "读取JSON文件失败"
                }
            } catch (e: Exception) {
                statusText = "JSON解析失败: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 加载已保存的格式
        savedFormat = ParsingJSONfileformat.loadFormat(this)
        useFormatExport = savedFormat != null

        filePicker = FilePickerHelper(this) { uri ->
            if (uri == null) {
                statusText = "未选择文件"
                return@FilePickerHelper
            }
            statusText = "正在解析..."
            lifecycleScope.launch {
                try {
                    val model = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            ExcelParserTool.parse(stream)
                        }
                    }
                    if (model != null) {
                        parsedModel = model
                        statusText = "解析完成，共 ${model.courses.size} 门课程"
                    } else {
                        statusText = "解析失败: 文件读取异常"
                    }
                } catch (e: Exception) {
                    statusText = "解析失败: ${e.message}"
                    e.printStackTrace()
                }
            }
        }

        exportHelper = ExportJsonWrapper(this) { success, msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            statusText = msg
        }

        setContent {
            MyApplicationTheme {
                MainScreen(
                    statusText = statusText,
                    courseCount = parsedModel?.courses?.size ?: 0,
                    courses = parsedModel?.courses ?: emptyList(),
                    hasSavedFormat = savedFormat != null,
                    useFormatExport = useFormatExport,
                    showSaveFormatDialog = showSaveFormatDialog,
                    detectedFormat = detectedFormat,
                    onPickFile = { filePicker.pickExcelFile() },
                    onImportJsonRef = { jsonFilePicker.launch(arrayOf("application/json", "*/*")) },
                    onExportJson = {
                        val model = parsedModel
                        if (model != null) {
                            val json = JsonHelper.toPrettyJson(model)
                            if (useFormatExport && savedFormat != null) {
                                val formatted = ParsingJSONfileformat.applyFormat(json, savedFormat!!)
                                exportHelper.exportJson(formatted, "course_table_custom.json")
                            } else {
                                exportHelper.exportJson(json)
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "请先解析课表", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyJson = {
                        val model = parsedModel
                        if (model != null) {
                            val json = JsonHelper.toPrettyJson(model)
                            val finalJson = if (useFormatExport && savedFormat != null) {
                                ParsingJSONfileformat.applyFormat(json, savedFormat!!)
                            } else {
                                json
                            }
                            val success = ClipboardHelper.copyToClipboard(this@MainActivity, finalJson)
                            Toast.makeText(this@MainActivity, if (success) "复制成功" else "复制失败", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "请先解析课表", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFormatToggle = { checked ->
                        useFormatExport = checked
                    },
                    onDismissSaveDialog = {
                        showSaveFormatDialog = false
                        detectedFormat = null
                    },
                    onConfirmSaveFormat = {
                        val format = detectedFormat
                        if (format != null) {
                            ParsingJSONfileformat.saveFormat(this@MainActivity, format)
                            savedFormat = format
                            useFormatExport = true
                            statusText = "JSON格式模板已保存"
                            Toast.makeText(this@MainActivity, "格式模板已保存", Toast.LENGTH_SHORT).show()
                        }
                        showSaveFormatDialog = false
                        detectedFormat = null
                    },
                    onClearFormat = {
                        ParsingJSONfileformat.clearFormat(this@MainActivity)
                        savedFormat = null
                        useFormatExport = false
                        statusText = "已清除保存的格式模板"
                        Toast.makeText(this@MainActivity, "格式模板已清除", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        parsedModel = null
        statusText = "请选择一个Excel课表文件"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    statusText: String,
    courseCount: Int,
    courses: List<ImportCourseJsonModel>,
    hasSavedFormat: Boolean,
    useFormatExport: Boolean,
    showSaveFormatDialog: Boolean,
    detectedFormat: ParsingJSONfileformat.JsonFormat?,
    onPickFile: () -> Unit,
    onImportJsonRef: () -> Unit,
    onExportJson: () -> Unit,
    onCopyJson: () -> Unit,
    onFormatToggle: (Boolean) -> Unit,
    onDismissSaveDialog: () -> Unit,
    onConfirmSaveFormat: () -> Unit,
    onClearFormat: () -> Unit
) {
    // 保存格式对话框
    if (showSaveFormatDialog && detectedFormat != null) {
        AlertDialog(
            onDismissRequest = onDismissSaveDialog,
            title = { Text("检测到JSON格式") },
            text = {
                Column {
                    Text("检测到以下课程数据格式：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "根数组键: ${detectedFormat!!.rootArrayKey}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "课程字段: ${detectedFormat!!.courseFieldNames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (detectedFormat!!.rootOtherKeys.isNotEmpty()) {
                        Text(
                            text = "其他字段: ${detectedFormat!!.rootOtherKeys.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (detectedFormat!!.fieldMapping.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("字段映射：", style = MaterialTheme.typography.bodySmall)
                        detectedFormat!!.fieldMapping.forEach { (standard, target) ->
                            Text(
                                text = "  $standard → $target",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("是否保存此格式模板？保存后导出JSON时可选择按此格式输出。")
                }
            },
            confirmButton = {
                Button(onClick = onConfirmSaveFormat) {
                    Text("保存格式")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSaveDialog) {
                    Text("不保存")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("课表解析工具") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 第一行：选择Excel + 导入JSON格式
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("选择Excel文件")
                }

                OutlinedButton(
                    onClick = onImportJsonRef,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入JSON格式参考")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二行：导出 + 复制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportJson,
                    modifier = Modifier.weight(1f),
                    enabled = courses.isNotEmpty()
                ) {
                    Text(if (useFormatExport) "按格式导出JSON" else "导出JSON")
                }

                OutlinedButton(
                    onClick = onCopyJson,
                    modifier = Modifier.weight(1f),
                    enabled = courses.isNotEmpty()
                ) {
                    Text("复制到剪贴板")
                }
            }

            // 格式开关
            if (hasSavedFormat) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useFormatExport,
                        onCheckedChange = onFormatToggle
                    )
                    Text(
                        text = "应用保存的格式模板导出",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearFormat) {
                        Text("清除格式", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 状态信息
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (courseCount > 0) {
                Text(
                    text = "共 $courseCount 门课程",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            // 课程列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(courses, key = { "${it.name}_${it.day}_${it.startSection}_${it.endSection}_${it.weeks.joinToString()}" }) { course ->
                    CourseCard(course)
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: ImportCourseJsonModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "教师: ${course.teacher}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "时间: 周${course.day} 第${course.startSection}-${course.endSection}节",
                style = MaterialTheme.typography.bodySmall
            )
            if (course.position.isNotBlank()) {
                Text(
                    text = "地点: ${course.position}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "周数: ${course.weeks.joinToString(",")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

package com.example.excel2json

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.excel2json.tool.CourseTableImportModel
import com.example.excel2json.tool.ExcelParserTool
import com.example.excel2json.tool.ExportJsonWrapper
import com.example.excel2json.tool.FilePickerHelper
import com.example.excel2json.tool.ImportCourseJsonModel
import com.example.excel2json.tool.ClipboardHelper
import com.example.excel2json.tool.JsonHelper
import com.example.excel2json.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var parsedModel by mutableStateOf<CourseTableImportModel?>(null)
    private var statusText by mutableStateOf("请选择一个Excel课表文件")

    private lateinit var filePicker: FilePickerHelper
    private lateinit var exportHelper: ExportJsonWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    onPickFile = { filePicker.pickExcelFile() },
                    onExportJson = {
                        val model = parsedModel
                        if (model != null) {
                            val json = JsonHelper.toPrettyJson(model)
                            exportHelper.exportJson(json)
                        } else {
                            Toast.makeText(this@MainActivity, "请先解析课表", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyJson = {
                        val model = parsedModel
                        if (model != null) {
                            val json = JsonHelper.toPrettyJson(model)
                            val success = ClipboardHelper.copyToClipboard(this@MainActivity, json)
                            Toast.makeText(this@MainActivity, if (success) "复制成功" else "复制失败", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "请先解析课表", Toast.LENGTH_SHORT).show()
                        }
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
    onPickFile: () -> Unit,
    onExportJson: () -> Unit,
    onCopyJson: () -> Unit
) {
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
            // 操作按钮
            Button(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择Excel文件")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExportJson,
                modifier = Modifier.fillMaxWidth(),
                enabled = courses.isNotEmpty()
            ) {
                Text("导出JSON")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCopyJson,
                modifier = Modifier.fillMaxWidth(),
                enabled = courses.isNotEmpty()
            ) {
                Text("复制到剪贴板")
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

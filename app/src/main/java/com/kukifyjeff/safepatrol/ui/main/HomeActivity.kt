@file:Suppress("DEPRECATION")

package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.ui.review.PointRecordActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.databinding.ActivityHomeBinding
import com.kukifyjeff.safepatrol.export.ExportUtil.exportFromLastTimeXlsx
import com.kukifyjeff.safepatrol.utils.ShiftUtils
import com.kukifyjeff.safepatrol.utils.SlotUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class HomeActivity : BaseActivity() {

    // 存储最后导出时间戳
    private var lastExportTs: Long = 0L

    private lateinit var binding: ActivityHomeBinding
    private var sessionId: Long = 0L
    private val db by lazy { AppDatabase.get(this) }


    private lateinit var routeId: String
    private lateinit var routeName: String
    private lateinit var operatorId: String
    private lateinit var exportPassword: String

    private lateinit var adapter: PointStatusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContextCompat.getColor(this, R.color.blue_primary).also { window.statusBarColor = it }

        exportPassword = com.kukifyjeff.safepatrol.config.ConfigLoader
            .getExportPassword(this, default = "G7v#pK9!sX2qLm4@")

        routeId = intent.getStringExtra("routeId") ?: ""
        routeName = intent.getStringExtra("routeName") ?: ""
        operatorId = intent.getStringExtra("operatorId") ?: ""
        val operatorName = intent.getStringExtra("operatorName") ?: ""

        val shift = ShiftUtils.resolveCurrentShift()
        val shiftValue = ShiftUtils.currentShiftValue()
        Log.d("FuckShift", shiftValue)
        val today =
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        // 读取上次导出时间
        val prefs = getSharedPreferences("SafePatrolPrefs", MODE_PRIVATE)
        lastExportTs = prefs.getLong("lastExportTs", 0L)
        val lastExportStr = if (lastExportTs > 0) java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(java.util.Date(lastExportTs)) else "无"
        binding.tvHeader.text = getString(
            R.string.homepage_header,
            routeName,
            operatorName,
            today,
            shiftValue,
            shift.name,
            shift.rangeText,
            lastExportStr
        )
        // RecyclerView 基本设置
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        adapter = PointStatusAdapter(
            data = emptyList(),
            onClickPoint = { pointUi ->
                // 可以先简单处理，例如弹个Toast
//                Toast.makeText(this, "点击了点位：${pointUi.name}", Toast.LENGTH_SHORT).show()
            },
            onViewRecordsClick = { pointUi ->
                val intent = Intent(this, PointRecordActivity::class.java).apply {
                    putExtra("pointId", pointUi.equipmentId)
                    putExtra("pointName", pointUi.name)
                    putExtra("freqHours", pointUi.freqHours)
                    putExtra("shiftName", shift.name + shift.rangeText)
                }
                startActivity(intent)
            }
        )
        binding.rvPoints.adapter = adapter

        // 创建（或更新）本次巡检 session（按当前路线路线/工号/班次）
        lifecycleScope.launch {
            // 为简单起见，每次进入页面都新建一个 session；后续可按需复用
            sessionId = db.inspectionDao().insertSession(
                com.kukifyjeff.safepatrol.data.db.entities.InspectionSessionEntity(
                    routeId = routeId,
                    routeName = routeName,
                    operatorId = operatorId,
                    shiftId = shift.id
                )
            )

            // 初次加载
            refreshPointStatuses()
        }

        // 按钮
        binding.btnClearData.setOnClickListener {
            // 第一次确认
            AlertDialog.Builder(this)
                .setTitle("确认删除？")
                .setMessage("此操作将清除所有巡检会话、记录与检查项，且不可恢复。是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续") { _, _ ->
                    // 第二次确认：输入“删除”
                    val input = EditText(this).apply {
                        hint = "请输入：删除"
                    }
                    AlertDialog.Builder(this)
                        .setTitle("二次确认")
                        .setMessage("为避免误操作，请输入“删除”两个字确认。")
                        .setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确认") { _, _ ->
                            val text = input.text?.toString()?.trim()
                            if (text == "删除") {
                                lifecycleScope.launch {
                                    try {
                                        val newSessionId = withContext(Dispatchers.IO) {
                                            val dao = db.inspectionDao()
                                            // 顺序很重要：先删子表再删父表
                                            dao.deleteAllRecordItems()
                                            dao.deleteAllRecords()
                                            dao.deleteAllSessions()

                                            // 清空后立即创建一个新的 session，避免后续依赖 sessionId 的逻辑崩溃
                                            val shiftNow = ShiftUtils.resolveCurrentShift()
                                            dao.insertSession(
                                                com.kukifyjeff.safepatrol.data.db.entities.InspectionSessionEntity(
                                                    routeId = routeId,
                                                    routeName = routeName,
                                                    operatorId = operatorId,
                                                    shiftId = shiftNow.id
                                                )
                                            )
                                        }
                                        // 删除应用私有 Documents 文件夹下的所有文件
                                        val docsDir =
                                            getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                                        if (docsDir != null && docsDir.exists() && docsDir.isDirectory) {
                                            docsDir.listFiles()?.forEach { file ->
                                                try {
                                                    file.deleteRecursively()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                        // 更新当前持有的 sessionId
                                        sessionId = newSessionId

                                        Toast.makeText(
                                            this@HomeActivity,
                                            "已清除所有点检记录，并已创建新的会话",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // 刷新首页状态
                                        refreshPointStatuses()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@HomeActivity,
                                            "删除失败：" + (e.message ?: "未知错误"),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(this, "输入不正确，已取消", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
                .show()
        }
        binding.btnScan.setOnClickListener {
            lifecycleScope.launch {
                val dao = db.inspectionDao()

                val latestTs = withContext(Dispatchers.IO) {
                    dao.getLatestRecordTimestamp() ?: 0L
                }

                val now = System.currentTimeMillis()

                if (!developerMode && now < latestTs) {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("系统时间异常")
                        .setMessage(
                            "检测到当前设备系统时间(${hhmm(now)}) 早于最近一次点检记录时间(${
                                hhmm(
                                    latestTs
                                )
                            }).\n\n" +
                                    "若是因为修改了系统时间，可选择删除未来时间的点检记录，此操作会被记录。"
                        )
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除冲突点检记录") { _, _ ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    // 删除 timestamp > now 的记录
                                    dao.deleteRecordsAfter(now)
                                    dao.deleteRecordItemsAfter(now)

                                    // 写入一条系统记录日志
                                    val systemRecordId = dao.insertRecord(
                                        com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity(
                                            sessionId = sessionId,
                                            pointId = "-1",
                                            slotIndex = 1,
                                            timestamp = now
                                        )
                                    )

                                    dao.insertItem(
                                        com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordItemEntity(
                                            recordId = systemRecordId,
                                            equipmentId = "-1",
                                            itemId = "-1",
                                            slotIndex = 1,
                                            value = "用户删除了冲突记录",
                                            abnormal = false
                                        )
                                    )
                                }

                                Toast.makeText(
                                    this@HomeActivity,
                                    "已删除冲突记录，并记录日志。",
                                    Toast.LENGTH_LONG
                                ).show()

                                val it = Intent(
                                    this@HomeActivity,
                                    com.kukifyjeff.safepatrol.ui.inspection.NfcReaderActivity::class.java
                                ).putExtra("sessionId", sessionId)
                                startActivity(it)
                            }
                        }
                        .show()
                    return@launch
                }

                val it = Intent(
                    this@HomeActivity,
                    com.kukifyjeff.safepatrol.ui.inspection.NfcReaderActivity::class.java
                ).putExtra("sessionId", sessionId)
                startActivity(it)
            }
        }
        binding.btnExport.setOnClickListener {
            val progressLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                gravity = Gravity.CENTER
                addView(ProgressBar(context).apply { isIndeterminate = true })
                val tv = TextView(context).apply {
                    text = "正在导出中，请稍后"
                    setPadding(0, 30, 0, 0)
                    textSize = 16f
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                addView(tv)
            }
            val progressDialog = AlertDialog.Builder(this)
                .setView(progressLayout)
                .setCancelable(false)
                .create()
            progressDialog.setCanceledOnTouchOutside(false)
            // 禁止窗口触摸
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            progressDialog.show()

            lifecycleScope.launch {
                try {
                    val modifyPwd = exportPassword

                    // 计算导出起止时间戳
                    val startTs =
                        if (lastExportTs > 0) lastExportTs else 1766592000000L // 从最后导出时间开始或从第一条记录
                    val endTs = System.currentTimeMillis()

                    val path = exportFromLastTimeXlsx(
                        context = this@HomeActivity,
                        db = db,
                        startTs = startTs,
                        endTs = endTs,
                        modifyPassword = modifyPwd
                    )

                    val file = java.io.File(path)
                    // === Copy file to system Downloads folder ===
                    val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                    if (!downloads.exists()) downloads.mkdirs()

                    val destFile = java.io.File(downloads, file.name)
                    try {
                        file.copyTo(destFile, overwrite = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@HomeActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )

                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    // 关闭加载对话框并恢复窗口交互
                    progressDialog.dismiss()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

                    startActivity(Intent.createChooser(share, "分享导出文件"))

                    // 弹出导出确认对话框
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("确认导出")
                        .setMessage("是否确认已经完成导出？确认后系统将记录导出时间，以后将无法重新导出本次时间段。")

                        .setPositiveButton("是") { dialog, _ ->
                            // 更新导出时间
                            lastExportTs = endTs
                            // 保存到 SharedPreferences
                            getSharedPreferences("SafePatrolPrefs", MODE_PRIVATE)
                                .edit {
                                    putLong("lastExportTs", endTs)
                                }

                            // 刷新 tvHeader 上的最后导出时间显示
                            val shift = ShiftUtils.resolveCurrentShift()
                            val shiftValue = ShiftUtils.currentShiftValue()
                            Log.d("FuckShift", shiftValue)
                            val today =
                                java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    .format(java.util.Date())
                            val lastExportStr =
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(java.util.Date(lastExportTs))
                            binding.tvHeader.text = getString(
                                R.string.homepage_header,
                                routeName,
                                operatorName,
                                today,
                                shiftValue,
                                shift.name,
                                shift.rangeText,
                                lastExportStr
                            )
                            dialog.dismiss()
                            Toast.makeText(this@HomeActivity, "已记录导出时间", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .setNegativeButton("否") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } catch (t: Throwable) {
                    // 关闭加载对话框并恢复窗口交互
                    try {
                        progressDialog.dismiss()
                    } catch (_: Throwable) {
                    }
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    Toast.makeText(this@HomeActivity, "导出失败：${t.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 返回时刷新一次（比如提交完成返回）
        lifecycleScope.launch { refreshPointStatuses() }
    }

    private suspend fun refreshPointStatuses() {
        val points = withContext(Dispatchers.IO) {
            db.pointDao().getByRoute(routeId)
        }

        // 获取当前班次时间窗
        val window = ShiftUtils.currentShiftWindowMillis()

        val uiList = withContext(Dispatchers.IO) {
            points.map { point ->
                // 获取该点下的所有设备
                val equipments = db.equipmentDao().getByPoint(point.pointId)

                // 获取该点下所有检查项
                val allCheckItems = equipments.flatMap { eq ->
                    db.checkItemDao().getByEquipment(eq.equipmentId)
                }

                // 获取频率最高（间隔最短）的频次
                val highestFreq =
                    if (allCheckItems.isNotEmpty()) allCheckItems.minOf { it.freqHours } else 8
                val expectedSlotCount = when (highestFreq) {
                    2 -> 4
                    4 -> 2
                    8 -> 1
                    else -> 1
                }
                android.util.Log.d(
                    "FuckHomeActivity",
                    "→ Point=${point.pointId}, highestFreq=$highestFreq, expectedSlotCount=$expectedSlotCount"
                )

                // 只保留最高频率的检查项
                val targetCheckItems = allCheckItems.filter { it.freqHours == highestFreq }

                // 构建槽位状态表
                val slotStatusMap = mutableMapOf<Int, Long>()

                // 查询当前点位所有最高频率检查项的所有记录（在当前班次窗口中）
                for (checkItem in targetCheckItems) {
                    // 先获取在时间窗内的记录（inspection_records）
                    val records = db.inspectionDao().getInspectionRecordsForCheckItemInWindow(
                        checkItemId = checkItem.itemId,
                        startMs = window.startMs,
                        endMs = window.endMs
                    )

                    if (records.isEmpty()) continue

                    // 建立 recordId -> timestamp 映射
                    val recordTimestampMap = records.associate { it.recordId to it.timestamp }
                    val recordIds = recordTimestampMap.keys.toList()

                    // 批量查询这些记录对应的 item（inspection_record_items）
                    val items = db.inspectionDao().getItemsForRecordIds(recordIds)

                    // 过滤出属于当前检查项的 item 并按 record 的时间计算 slot
                    for (item in items) {
                        // 忽略系统日志条目
                        if (item.itemId == "-1" || item.equipmentId == "-1") {
                            android.util.Log.d(
                                "FuckHomeActivity",
                                "⏭ Skipped system item for point=${point.pointId}"
                            )
                            continue
                        }

                        if (item.itemId != checkItem.itemId) continue

                        val recTs = recordTimestampMap[item.recordId] ?: continue
                        val slotIdx = SlotUtils.getSlotIndex(highestFreq, recTs)
                        slotStatusMap[slotIdx] = recTs
                        android.util.Log.d(
                            "FuckHomeActivity",
                            "✅ Point=${point.pointId}, CheckItem=${checkItem.itemId}, Slot=$slotIdx (recordId=${item.recordId})"
                        )
                    }
                }

                // 输出每个槽位状态（勾选 or 未检）
                val slots = (1..expectedSlotCount).map { slotIdx ->
                    val ts = slotStatusMap[slotIdx]
                    val isChecked = ts != null
                    android.util.Log.d(
                        "FuckHomeActivity",
                        "Point ${point.pointId} slot $slotIdx -> ${if (isChecked) "✅ checked" else "⬜ unchecked"}"
                    )
                    val slotChinese = slotIndexToChinese(slotIdx)
                    val title = if (isChecked) {
                        val timeStr = hhmm(ts)
                        getString(R.string.point_slot_status_done, slotChinese, timeStr)
                    } else {
                        getString(R.string.point_slot_status_pending, slotChinese)
                    }
                    SlotStatus(title, isChecked, null)
                }

                // 更新 UI：点位名称
                PointStatusUi(
                    equipmentId = point.pointId,
                    name = point.name,
                    location = point.location,
                    freqHours = highestFreq,
                    slots = slots
                )
            }
        }

        withContext(Dispatchers.Main) {
            adapter.submitList(uiList)
        }
    }

    // 将时间戳格式化为 HH:mm
    private fun hhmm(ts: Long): String = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(java.util.Date(ts))

}

// 槽位数字转中文：1->一, 2->二, 3->三, 4->四
private fun slotIndexToChinese(idx: Int): String {
    return when (idx) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        else -> idx.toString()
    }
}
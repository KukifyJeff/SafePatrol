@file:Suppress("DEPRECATION")

package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.databinding.ActivityHomeBinding
import com.kukifyjeff.safepatrol.ui.inspection.InspectionActivity
import com.kukifyjeff.safepatrol.utils.ShiftUtils
import com.kukifyjeff.safepatrol.utils.SlotUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeActivity : BaseActivity() {

    companion object {
        // 开关：true 允许点击列表 item 进入模拟点检；false 只能通过 NFC 进入点检
        const val ALLOW_SIMULATED_INSPECTION = false
    }

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

        val shift = resolveCurrentShift()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        binding.tvHeader.text = getString(R.string.homepage_header, routeName, operatorName, shift.name, shift.rangeText, today )
        // RecyclerView 基本设置
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        adapter = PointStatusAdapter(emptyList()) { point ->
            if (ALLOW_SIMULATED_INSPECTION) {
                val it = Intent(this, InspectionActivity::class.java)
                    .putExtra("equipmentId", point.equipmentId)
                    .putExtra("equipmentName", point.name)
                    .putExtra("freqHours", point.freqHours)
                    .putExtra("sessionId", sessionId)
                startActivity(it)
            } else {
//                Toast.makeText(this, "请通过 NFC 标签进行点检", Toast.LENGTH_SHORT).show()
            }
        }
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
                                            val shiftNow = resolveCurrentShift()
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
                                        val docsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                                        if (docsDir != null && docsDir.exists() && docsDir.isDirectory) {
                                            docsDir.listFiles()?.forEach { file ->
                                                try {
                                                    file.deleteRecursively()
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        // 更新当前持有的 sessionId
                                        sessionId = newSessionId

                                        Toast.makeText(this@HomeActivity, "已清除所有点检记录，并已创建新的会话", Toast.LENGTH_LONG).show()
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
            val it = Intent(this, com.kukifyjeff.safepatrol.ui.inspection.NfcReaderActivity::class.java)
                .putExtra("sessionId", sessionId)
            startActivity(it)
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
                    // 打开无需密码，修改需密码（你可改为从配置读）
                    val modifyPwd = exportPassword

                    // 默认导出当前月，若需导出其他月份可改为 UI 选择年/月
                    val cal = java.util.Calendar.getInstance()
                    val year = cal.get(java.util.Calendar.YEAR)
                    val month = cal.get(java.util.Calendar.MONTH) + 1 // Calendar.MONTH 从 0 开始

                    val path = com.kukifyjeff.safepatrol.export.ExportUtil.exportMonthlyXlsx(
                        context = this@HomeActivity,
                        db = db,
                        year = year,
                        month = month,
                        modifyPassword = modifyPwd
                    )

                    val file = java.io.File(path)
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
                } catch (t: Throwable) {
                    // 关闭加载对话框并恢复窗口交互
                    try { progressDialog.dismiss() } catch (_: Throwable) {}
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    Toast.makeText(this@HomeActivity, "导出失败：${t.message}", Toast.LENGTH_LONG).show()
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
                val highestFreq = if (allCheckItems.isNotEmpty()) allCheckItems.minOf { it.freqHours } else 8
                val expectedSlotCount = when (highestFreq) {
                    2 -> 4
                    4 -> 2
                    8 -> 1
                    else -> 1
                }
                android.util.Log.d("FuckHomeActivity", "→ Point=${point.pointId}, highestFreq=$highestFreq, expectedSlotCount=$expectedSlotCount")

                // 只保留最高频率的检查项
                val targetCheckItems = allCheckItems.filter { it.freqHours == highestFreq }

                // 构建槽位状态表
                val slotStatusMap = mutableMapOf<Int, Long>()

                // 查询当前点位所有最高频率检查项的所有记录（在当前班次窗口中）
                for (checkItem in targetCheckItems) {
                    val allRecords = db.inspectionDao().getInspectionRecordsForCheckItemInWindow(
                        checkItemId = checkItem.itemId,
                        startMs = window.startMs,
                        endMs = window.endMs
                    )
                    for (record in allRecords) {
                        val slotIdx = SlotUtils.getSlotIndex(highestFreq, record.timestamp)
                        slotStatusMap[slotIdx] = record.timestamp
                        android.util.Log.d("FuckHomeActivity", "✅ Point=${point.pointId}, CheckItem=${checkItem.itemId}, Slot=$slotIdx")
                    }
                }

                // 输出每个槽位状态（勾选 or 未检）
                val slots = (1..expectedSlotCount).map { slotIdx ->
                    val ts = slotStatusMap[slotIdx]
                    val isChecked = ts != null
                    android.util.Log.d("FuckHomeActivity", "Point ${point.pointId} slot $slotIdx -> ${if (isChecked) "✅ checked" else "⬜ unchecked"}")
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

    /**
     * 班次规则（使用手机本地时间）：
     * 08:30 - 16:30  白班
     * 16:30 - 次日00:30 中班（跨天）
     * 00:30 - 08:30  夜班
     */
    private fun resolveCurrentShift(): ShiftInfo {
        val now = LocalTime.now()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val t0830 = LocalTime.of(8, 30)
        val t1630 = LocalTime.of(16, 30)
        val t0030 = LocalTime.of(0, 30)

        return when {
            // 白班：08:30 <= now < 16:30
            !now.isBefore(t0830) && now.isBefore(t1630) ->
                ShiftInfo(id = "S1", name = "白班", rangeText = "${t0830.format(fmt)}-${t1630.format(fmt)}")

            // 中班：16:30 - 24:00 or 00:00 - 00:30
            (!now.isBefore(t1630)) || now.isBefore(t0030) ->
                ShiftInfo(id = "S2", name = "中班", rangeText = "${t1630.format(fmt)}-次日${t0030.format(fmt)}")

            // 夜班：00:30 <= now < 08:30
            else -> ShiftInfo(id = "S3", name = "夜班", rangeText = "${t0030.format(fmt)}-${t0830.format(fmt)}")
        }
    }

    data class ShiftInfo(
        val id: String,
        val name: String,
        val rangeText: String
    )
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
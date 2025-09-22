@file:Suppress("DEPRECATION")

package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.databinding.ActivityHomeBinding
import com.kukifyjeff.safepatrol.ui.inspection.InspectionActivity
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withContext
import com.kukifyjeff.safepatrol.utils.ShiftUtils
import kotlinx.coroutines.Dispatchers
import android.widget.EditText
import android.widget.ProgressBar
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.kukifyjeff.safepatrol.R
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class HomeActivity : AppCompatActivity() {

    companion object {
        // 开关：true 允许点击列表 item 进入模拟点检；false 只能通过 NFC 进入点检
        const val ALLOW_SIMULATED_INSPECTION = true
    }

    private lateinit var binding: ActivityHomeBinding
    private var sessionId: Long = 0L
    private val db by lazy { AppDatabase.get(this) }


    private lateinit var routeId: String
    private lateinit var routeName: String
    private lateinit var operatorId: String
    private lateinit var exportPassword: String

    private lateinit var adapter: PointStatusAdapter

    private fun currentShiftId(): String {
        val now = LocalTime.now()
        val t0830 = LocalTime.of(8, 30)
        val t1630 = LocalTime.of(16, 30)
        val t0030 = LocalTime.of(0, 30)
        return when {
            !now.isBefore(t0830) && now.isBefore(t1630) -> "白班"
            !now.isBefore(t1630) || now.isBefore(t0030) -> "中班"
            else -> "夜班"
        }
    }

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

        val shift = resolveCurrentShift()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        binding.tvHeader.text = "路线：$routeName    工号：$operatorId\n班次：${shift.name}(${shift.rangeText})    日期：$today"

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

    // 构建 UI 列表（每个点位）
    val uiList = withContext(Dispatchers.IO) {
        points.map { p: com.kukifyjeff.safepatrol.data.db.entities.PointEntity ->
            val freq = p.freqHours
            // 8h -> 1 个槽；4h -> 2 个槽；2h -> 4 个槽；其它默认 1 个槽
            val nSlots = when (freq) {
                2 -> 4
                4 -> 2
                8 -> 1
                else -> 1
            }
            val slotTitles = arrayOf("第一次", "第二次", "第三次", "第四次")

            val slots: List<SlotStatus> = (1..nSlots).map { slotIdx ->
                val recs = db.inspectionDao().getRecordsForPointSlotInWindow(
                    equipId = p.equipmentId,
                    slotIndex = slotIdx,
                    startMs = window.startMs,
                    endMs = window.endMs
                )
                val latest = recs.maxByOrNull { it.timestamp }
                val title = if (nSlots == 1) "本班" else slotTitles[slotIdx - 1]
                SlotStatus(title, latest != null, latest?.let { hhmm(it.timestamp) })
            }
            PointStatusUi(
                equipmentId = p.equipmentId,
                name = p.name,
                location = p.location,
                freqHours = freq,
                slots = slots
            )
        }
    }

    // 更新 RecyclerView/ListView
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
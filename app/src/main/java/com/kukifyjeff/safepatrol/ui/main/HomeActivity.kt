package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.core.content.FileProvider
import java.io.File

class HomeActivity : AppCompatActivity() {

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

        exportPassword = com.kukifyjeff.safepatrol.config.ConfigLoader
            .getExportPassword(this, default = "G7v#pK9!sX2qLm4@")


        routeId = intent.getStringExtra("routeId") ?: ""
        routeName = intent.getStringExtra("routeName") ?: ""
        operatorId = intent.getStringExtra("operatorId") ?: ""

        val shift = resolveCurrentShift()
        binding.tvHeader.text = "路线：$routeName    工号：$operatorId    班次：${shift.name}(${shift.rangeText})"

        // RecyclerView 基本设置
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        adapter = PointStatusAdapter(emptyList()) { point ->
            // 点击点位 → 进入点检表单页
            val it = Intent(this, InspectionActivity::class.java)
                .putExtra("equipmentId", point.equipmentId)
                .putExtra("equipmentName", point.name)
                .putExtra("freqHours", point.freqHours)
                .putExtra("sessionId", sessionId)
            startActivity(it)
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

        // 按钮（后续接入真实逻辑）
        binding.btnScan.setOnClickListener {
            val it = Intent(this, com.kukifyjeff.safepatrol.ui.inspection.NfcReaderActivity::class.java)
                .putExtra("sessionId", sessionId)
            startActivity(it)
        }

        binding.btnExport.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // 打开无需密码，修改需密码（你可改为从配置读）
                    val modifyPwd = exportPassword

                    val path = com.kukifyjeff.safepatrol.export.ExportUtil
                        .exportSessionXlsx(this@HomeActivity, db, sessionId, modifyPwd)

                    val file = java.io.File(path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@HomeActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )

                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(share, "分享导出文件"))
                } catch (t: Throwable) {
                    android.widget.Toast.makeText(this@HomeActivity, "导出失败：${t.message}", android.widget.Toast.LENGTH_LONG).show()
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
        val points = db.pointDao().getByRoute(routeId)
        val uiList = points.map { p ->
            val recs = db.inspectionDao().getRecordsForPoint(sessionId, p.equipmentId)

            fun latestForSlot(slot: Int) =
                recs.filter { it.slotIndex == slot }
                    .maxByOrNull { it.timestamp }

            val slots = if (p.freqHours == 4) {
                val first = latestForSlot(1)
                val second = latestForSlot(2)
                listOf(
                    SlotStatus("第一次", first != null, first?.let { hhmm(it.timestamp) }),
                    SlotStatus("第二次", second != null, second?.let { hhmm(it.timestamp) })
                )
            } else {
                val only = recs.maxByOrNull { it.timestamp }
                listOf(
                    SlotStatus("本班", only != null, only?.let { hhmm(it.timestamp) })
                )
            }

            PointStatusUi(
                equipmentId = p.equipmentId,
                name = p.name,
                location = p.location,
                freqHours = p.freqHours,
                slots = slots
            )
        }
        adapter.submitList(uiList)
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
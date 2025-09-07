package com.kukifyjeff.safepatrol.ui.inspection

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordItemEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.*
import java.util.Date
import java.util.Locale
import android.util.Log

class InspectionActivity : AppCompatActivity() {

    private val db by lazy { AppDatabase.get(this) }

    private lateinit var equipmentId: String
    private lateinit var equipmentName: String
    private var freqHours: Int = 8
    private var sessionId: Long = 0L

    private lateinit var rv: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: FormAdapter
    private val rows = mutableListOf<FormRow>()

    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspection)

        equipmentId = intent.getStringExtra("equipmentId") ?: ""
        equipmentName = intent.getStringExtra("equipmentName") ?: equipmentId
        freqHours = intent.getIntExtra("freqHours", 8)
        sessionId = intent.getLongExtra("sessionId", 0L)

        findViewById<TextView>(R.id.tvHeader).text = "点位：$equipmentName（$equipmentId）"

        rv = findViewById(R.id.rvForm)
        rv.layoutManager = LinearLayoutManager(this)

        // 显示上次提交时间
        lifecycleScope.launch {
            val lastText = withContext(Dispatchers.IO) {
                val recs = db.inspectionDao().getRecordsForPoint(sessionId, equipmentId)
                recs.lastOrNull()?.let { "上次提交：" + hhmm.format(Date(it.timestamp)) } ?: "上次提交：-"
            }
            findViewById<TextView>(R.id.tvLast).text = lastText
        }

        // 加载检查项并渲染为工业化表单
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { db.checkItemDao().getByEquipment(equipmentId) }
            rows.clear()
            rows.addAll(items.map { it.toRow() })
            adapter = FormAdapter(rows)
            rv.adapter = adapter
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSubmit).setOnClickListener { onSubmit() }
    }

    private fun CheckItemEntity.toRow(): FormRow {
        return when (type.uppercase()) {
            "BOOLEAN" -> FormRow.Bool(this, null)   // 默认未选择，避免误触
            "NUMBER"  -> FormRow.Number(this, null)
            else      -> FormRow.Text(this, "")
        }
    }

    private fun onSubmit() {
        val values = mutableListOf<InspectionRecordItemEntity>()
        val abnormalList = mutableListOf<String>()
        val missingList = mutableListOf<String>()

        rows.forEach { row ->
            when (row) {
                is FormRow.Bool -> {
                    if (row.ok == null && row.item.required) {
                        missingList.add(row.item.itemName)
                    }
                    val abnormal = (row.ok == false)  // 不合格为异常
                    if (abnormal) abnormalList.add(row.item.itemName)
                    values.add(
                        InspectionRecordItemEntity(
                            recordId = 0,
                            itemId = row.item.itemId,
                            value = when (row.ok) { true -> "TRUE"; false -> "FALSE"; null -> "" },
                            abnormal = abnormal
                        )
                    )
                }
                is FormRow.Number -> {
                    val v = row.value
                    if (v == null) {
                        if (row.item.required) missingList.add(row.item.itemName)
                        values.add(InspectionRecordItemEntity(recordId = 0, itemId = row.item.itemId, value = "", abnormal = row.item.required))
                    } else {
                        val low = row.item.minValue?.let { v < it } ?: false
                        val high = row.item.maxValue?.let { v > it } ?: false
                        val ab = low || high
                        if (ab) abnormalList.add("${row.item.itemName}=$v")
                        values.add(InspectionRecordItemEntity(recordId = 0, itemId = row.item.itemId, value = v.toString(), abnormal = ab))
                    }
                }
                is FormRow.Text -> {
                    values.add(
                        InspectionRecordItemEntity(
                            recordId = 0,
                            itemId = row.item.itemId,
                            value = row.text.trim(),
                            abnormal = false
                        )
                    )
                }
            }
        }

        if (missingList.isNotEmpty()) {
            Toast.makeText(this, "必填未填：${missingList.joinToString()}", Toast.LENGTH_LONG).show()
            return
        }

        if (abnormalList.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("异常确认")
                .setMessage("发现异常项：\n${abnormalList.joinToString("\n")}\n\n确认提交？")
                .setPositiveButton("确认") { _, _ -> save(values) }
                .setNegativeButton("取消", null)
                .show()
        } else {
            save(values)
        }
    }

    private fun save(items: List<InspectionRecordItemEntity>) {
        lifecycleScope.launch {
            val recId = withContext(Dispatchers.IO) {
                val slot = decideSlotIndex(sessionId, equipmentId, freqHours)
                val recordId = db.inspectionDao().insertRecord(
                    InspectionRecordEntity(
                        sessionId = sessionId,
                        equipmentId = equipmentId,
                        slotIndex = slot,
                        timestamp = System.currentTimeMillis()
                    )
                )
                db.inspectionDao().insertItems(items.map { it.copy(recordId = recordId) })
                recordId
            }
            Toast.makeText(this@InspectionActivity, "已提交（记录ID：$recId）", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private suspend fun decideSlotIndex(sessionId: Long, equipId: String, freqHours: Int): Int {
        if (freqHours != 4) return 1 // 8h 点位固定一个槽位

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        // 定义三个固定边界时间（当天）
        val today = now.toLocalDate()
        val boundaries = listOf(
            ZonedDateTime.of(today, LocalTime.of(0, 30), zone),  // 00:30
            ZonedDateTime.of(today, LocalTime.of(8, 30), zone),  // 08:30
            ZonedDateTime.of(today, LocalTime.of(16, 30), zone)  // 16:30
        )

        // 同时考虑“昨天”的 16:30（覆盖 00:00-00:30 的跨天中班）
        val yesterdayBoundary = ZonedDateTime.of(today.minusDays(1), LocalTime.of(16, 30), zone)

        // 取 <= now 的所有候选中的最大者作为“当前班次起点”
        val candidates = (boundaries + yesterdayBoundary).filter { !it.isAfter(now) }
        val shiftStart = candidates.maxByOrNull { it.toInstant().toEpochMilli() }
            ?: ZonedDateTime.of(today, LocalTime.of(0, 30), zone)

        val elapsedMinutes = Duration.between(shiftStart, now).toMinutes()

        // Debug 日志，方便你验证
        Log.d("InspectionSlot", "now=${now.toLocalTime()} start=${shiftStart.toLocalTime()} elapsedMin=$elapsedMinutes")

        return if (elapsedMinutes < 240) 1 else 2
    }
}
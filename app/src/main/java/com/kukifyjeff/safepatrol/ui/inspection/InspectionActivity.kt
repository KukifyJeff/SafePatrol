package com.kukifyjeff.safepatrol.ui.inspection

import android.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

class InspectionActivity : AppCompatActivity() {

    private val db by lazy { AppDatabase.get(this) }

    private lateinit var equipmentId: String
    private lateinit var equipmentName: String
    private var freqHours: Int = 8
    private var sessionId: Long = 0L

    private var currentSlotIdx: Int = 1

    private lateinit var rv: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: FormAdapter
    private val rows = mutableListOf<FormRow>()

    // 已确认的数值项集合（类级别，供 onSubmit/save 等使用）
    private val confirmedNumberItemIds = mutableSetOf<String>()

    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 计算当前时间在班次窗口中的槽位：8h->1; 4h->2; 2h->4
    private fun resolveCurrentSlotIndex(freqHours: Int, startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val nSlots = when (freqHours) {
            2 -> 4
            4 -> 2
            8 -> 1
            else -> 1
        }
        if (nSlots == 1) return 1
        val total = (endMs - startMs).coerceAtLeast(1L)
        val slotLen = (total / nSlots).coerceAtLeast(1L)
        val offset = (nowMs - startMs).coerceIn(0L, total - 1)
        val idx = (offset / slotLen).toInt() + 1
        return idx.coerceIn(1, nSlots)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspection)
        ContextCompat.getColor(this, R.color.blue_primary).also { window.statusBarColor = it }
        equipmentId = intent.getStringExtra("equipmentId") ?: ""
        equipmentName = intent.getStringExtra("equipmentName") ?: equipmentId
        freqHours = intent.getIntExtra("freqHours", 8)
        sessionId = intent.getLongExtra("sessionId", 0L)

        // ===== 同一班次同一槽位防重复点检 =====
        val window = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
        val nSlotsForFreq = when (freqHours) { 2 -> 4; 4 -> 2; 8 -> 1; else -> 1 }
        val passedSlot = intent.getIntExtra("slotIndex", 0)
        currentSlotIdx = if (passedSlot in 1..nSlotsForFreq) {
            passedSlot
        } else {
            resolveCurrentSlotIndex(freqHours, window.startMs, window.endMs)
        }

        lifecycleScope.launch {
            val existed = withContext(Dispatchers.IO) {
                val window = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
                db.inspectionDao().hasRecordForPointSlotInWindow(
                    equipId = equipmentId,
                    slotIndex = currentSlotIdx,
                    startMs = window.startMs,
                    endMs = window.endMs
                )
            }
            if (existed) {
                Toast.makeText(
                    this@InspectionActivity,
                    "本时段已点检过该点位，无需重复。请在下一时段再进行。",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }
        }

        findViewById<TextView>(R.id.tvHeader).text = "点位：$equipmentName（$equipmentId）"

        rv = findViewById(R.id.rvForm)


        rv.layoutManager = LinearLayoutManager(this)

        // 显示上次提交时间
        lifecycleScope.launch {
            val lastText = withContext(Dispatchers.IO) {
                val window = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
                val nSlots = when (freqHours) { 2 -> 4; 4 -> 2; 8 -> 1; else -> 1 }
                val all = mutableListOf<com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity>()
                for (idx in 1..nSlots) {
                    all += db.inspectionDao().getRecordsForPointSlotInWindow(
                        equipId = equipmentId,
                        slotIndex = idx,
                        startMs = window.startMs,
                        endMs = window.endMs
                    )
                }
                val latest = all.maxByOrNull { it.timestamp }
                latest?.let { "上次提交：" + hhmm.format(Date(it.timestamp)) } ?: "上次提交：-"
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
            // 为每个已附加的子项设置行内确认按钮行为（对没有直接修改 Adapter 的项目进行增强）
            rv.addOnChildAttachStateChangeListener(object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: android.view.View) {
                    try {
                        val pos = rv.getChildAdapterPosition(view)
                        if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
                        val row = rows.getOrNull(pos) ?: return
                        if (row !is FormRow.Number) return

                        val et = view.findViewById<android.widget.EditText>(R.id.etValue)
                        val btn = view.findViewById<android.widget.Button>(R.id.btnConfirmNumber)
                        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
                        if (et == null || btn == null || tvStatus == null) return

                        // 初始按钮样式：灰色（不可确认）或可点击
                        val green = "#2E7D32".toColorInt()
                        val red = "#C62828".toColorInt()

                        fun setBtnGray() {
                            btn.isEnabled = !(et.text.isNullOrBlank())
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf("#EEEEEE".toColorInt())
                            btn.setTextColor(Color.BLACK)
                            tvStatus.text = if (confirmedNumberItemIds.contains(row.item.itemId)) "已确认" else "状态：--"
                        }

                        fun markConfirmed(isNormal: Boolean) {
                            confirmedNumberItemIds.add(row.item.itemId)
                            btn.isEnabled = false
                            btn.setTextColor(Color.WHITE)
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isNormal) green else red)
                            tvStatus.text = if (isNormal) "已确认（正常）" else "已确认（异常）"
                        }

                        // 绑定按钮点击：校验数值是否正常，标记颜色
                        btn.setOnClickListener {
                            val text = et.text?.toString()?.trim()
                            if (text.isNullOrEmpty()) {
                                Toast.makeText(this@InspectionActivity, "请先输入数值再确认", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            val v = try { text.toDouble() } catch (e: Exception) { null }
                            if (v == null) {
                                Toast.makeText(this@InspectionActivity, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            val low = row.item.minValue?.let { v < it } ?: false
                            val high = row.item.maxValue?.let { v > it } ?: false
                            val normal = !(low || high)
                            markConfirmed(normal)
                        }

                        // 编辑框变动时，取消已确认状态并恢复灰色/可点击
                        val existingTag = et.getTag(R.id.etValue)
                        if (existingTag is android.text.TextWatcher) {
                            et.removeTextChangedListener(existingTag)
                        }
                        val watcher = object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                // 用户手动修改时，取消确认
                                if (confirmedNumberItemIds.remove(row.item.itemId)) {
                                    // 恢复按钮可点并置灰
                                    btn.isEnabled = !(et.text.isNullOrBlank())
                                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf("#EEEEEE".toColorInt())
                                    tvStatus.text = "状态：--"
                                } else {
                                    // 保持按钮启用/禁用状态
                                    btn.isEnabled = !(et.text.isNullOrBlank())
                                }
                            }
                        }
                        et.addTextChangedListener(watcher)
                        et.setTag(R.id.etValue, watcher)

                        // 最后根据当前值与已确认集合设置初始样式
                        if (confirmedNumberItemIds.contains(row.item.itemId)) {
                            // 如果已确认，显示绿色或红色需根据是否异常重新判定
                            val currentText = et.text?.toString()?.trim()
                            val vcur = try { currentText?.toDouble() } catch (e: Exception) { null }
                            val low = vcur?.let { row.item.minValue?.let { vv -> it < vv } } ?: false
                            val high = vcur?.let { row.item.maxValue?.let { vv -> it > vv } } ?: false
                            val normal = !(low || high)
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(if (normal) green else red)
                            btn.isEnabled = false
                            tvStatus.text = if (normal) "已确认（正常）" else "已确认（异常）"
                        } else {
                            setBtnGray()
                        }

                    } catch (t: Throwable) {
                        // 忽略单行绑定中的异常，避免整个页面崩溃
                        Log.w("InspectionActivity", "bind confirm button failed: ${t.message}")
                    }
                }

                override fun onChildViewDetachedFromWindow(view: android.view.View) {
                    // 清理 TextWatcher（避免内存泄漏）
                    try {
                        val et = view.findViewById<android.widget.EditText>(R.id.etValue)
                        val tag = et?.getTag(R.id.etValue)
                        if (tag is android.text.TextWatcher) {
                            et.removeTextChangedListener(tag)
                            et.setTag(R.id.etValue, null)
                        }
                    } catch (_: Throwable) {}
                }
            })
            // 当表单加载完成后，禁用提交按钮直到必要的数值项被确认
            val submitBtn = findViewById<Button>(R.id.btnSubmit)
            // 若存在数值项，提交前需要确认：初始不禁用，让 onSubmit 做二次校验。
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
        // 校验：所有已输入的数值项必须先被确认
        val unconfirmed = rows.filterIsInstance<FormRow.Number>().filter { it.value != null && !confirmedNumberItemIds.contains(it.item.itemId) }
        if (unconfirmed.isNotEmpty()) {
            Toast.makeText(
                this,
                "${unconfirmed.joinToString("、") { it.item.itemName }}项数值未确认",
                Toast.LENGTH_LONG
            ).show()
            return
        }

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
                val recordId = db.inspectionDao().insertRecord(
                    InspectionRecordEntity(
                        sessionId = sessionId,
                        equipmentId = equipmentId,
                        slotIndex = currentSlotIdx,
                        timestamp = System.currentTimeMillis()
                    )
                )
                db.inspectionDao().insertItems(items.map { it.copy(recordId = recordId) })
                recordId
            }
            Toast.makeText(this@InspectionActivity, "已提交", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
            confirmedNumberItemIds.clear()
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
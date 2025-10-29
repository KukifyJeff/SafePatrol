package com.kukifyjeff.safepatrol.ui.inspection

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt


@Suppress("DEPRECATION")
class InspectionActivity : AppCompatActivity() {

    private val db by lazy { AppDatabase.get(this) }

    private var freqHours: Int = 8
    private var sessionId: Long = 0L
    private lateinit var pointId: String

    private var currentSlotIdx: Int = 1

    private lateinit var rv: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: FormAdapter
    private val rows = mutableListOf<FormRow>()

    // 已确认的数值项集合（类级别，供 onSubmit/save 等使用）
    private val confirmedNumberItemIds = mutableSetOf<String>()

    // 保存 Boolean 异常项的备注内容，key 为 itemId
    private val remarkMap = mutableMapOf<String, String>()

    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var runningEquipments = arrayListOf<String>()


    // 调试开关：true 使用 minValue 作为默认值
    private val useMinValueAsDefault: Boolean = true

    // 计算当前时间在班次窗口中的槽位：8h->1; 4h->2; 2h->4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspection)
        ContextCompat.getColor(this, R.color.blue_primary).also { window.statusBarColor = it }
        runningEquipments = intent.getStringArrayListExtra("runningEquipments") ?: arrayListOf()
        Log.d("FuckInspectionActivity", "Fucking runningEquipments.size=${runningEquipments.size}  内容=${runningEquipments.joinToString()}")


        pointId = intent.getStringExtra("pointId").toString()
        val pointName = intent.getStringExtra("pointName")
        freqHours = intent.getIntExtra("freqHours", 8)
        sessionId = intent.getLongExtra("sessionId", 0L)

        // ===== 同一班次同一槽位防重复点检 =====
        val nSlotsForFreq = when (freqHours) { 2 -> 4; 4 -> 2; 8 -> 1; else -> 1 }
        val passedSlot = intent.getIntExtra("slotIndex", 0)
        currentSlotIdx = if (passedSlot in 1..nSlotsForFreq) {
            passedSlot
        } else {
            com.kukifyjeff.safepatrol.utils.SlotUtils.getSlotIndex(freqHours, System.currentTimeMillis())
        }

        lifecycleScope.launch {
            val existed = withContext(Dispatchers.IO) {
                val window = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
                val checkSlot = com.kukifyjeff.safepatrol.utils.SlotUtils.getSlotIndex(freqHours, System.currentTimeMillis())
                db.inspectionDao().hasRecordForPointSlotInWindow(
                    equipId = pointId,
                    slotIndex = checkSlot,
                    startMs = window.startMs,
                    endMs = window.endMs
                )
            }
            if (existed) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@InspectionActivity,
                        "该点位本时段已点检，无需重复。",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                return@launch
            }
        }

        findViewById<TextView>(R.id.tvHeader).text = getString(R.string.inspection_header,  pointName, pointId)

        rv = findViewById(R.id.rvForm)

        rv.layoutManager = LinearLayoutManager(this)

        // 显示上次提交时间
        lifecycleScope.launch {
            val lastText = withContext(Dispatchers.IO) {
                val window = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
                val nSlots = when (freqHours) { 2 -> 4; 4 -> 2; 8 -> 1; else -> 1 }
                val all = mutableListOf<InspectionRecordEntity>()
                for (idx in 1..nSlots) {
                    all += db.inspectionDao().getRecordsForPointSlotInWindow(
                        pointId = pointId,
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

        // 加载检查项并渲染为工业化表单（多设备支持）
        lifecycleScope.launch {
            val rowsAll = mutableListOf<FormRow>()
            withContext(Dispatchers.IO) {
                val activeFreqs = com.kukifyjeff.safepatrol.utils.SlotUtils.getActiveFrequenciesForCurrentSlot(freqHours)
                Log.d("FuckInspectionActivity", "当前活动频率: $activeFreqs")
                for (equipId in runningEquipments) {
                    Log.d("FuckInspectionActivity", "开始处理设备: $equipId")
                    // 获取设备名称
                    val equipEntity = db.equipmentDao().getById(equipId)
                    Log.d("FuckInspectionActivity", "设备 $equipId 查找结果=${equipEntity != null}")
                    val equipName = equipEntity?.equipmentName ?: equipId
                    // 不再添加设备表头，而是将设备名前置到每个检查项
                    val items = db.checkItemDao().getByEquipment(equipId)
                    Log.d("FuckInspectionActivity", "设备 $equipId 检查项数量=${items.size}")
                    val filteredItems = items.filter { (it.freqHours ?: 8) in activeFreqs }
                    Log.d("FuckInspectionActivity", "设备 $equipId 筛选后数量=${filteredItems.size}")
                    rowsAll.addAll(filteredItems.map {
                        it.copy(itemName = "$equipName - ${it.itemName}（${it.freqHours}h/次）").toRow()
                    })
                }
            }
            withContext(Dispatchers.Main) {
                rows.clear()
                rows.addAll(rowsAll)
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
                            et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            // 自动填充默认值为 minValue（如果开关开启且 EditText 为空）
                            if (useMinValueAsDefault && row.item.minValue != null && et.text.isNullOrBlank()) {
                                et.setText(row.item.minValue.toString())
                            }
                            val btn = view.findViewById<Button>(R.id.btnConfirmNumber)
                            val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
                            if (et == null || btn == null || tvStatus == null) return

                            // ====== 自适应快捷按钮调节 ======
                            val min = row.item.minValue ?: 0.0
                            val max = row.item.maxValue ?: 100.0
                            val range = max - min

                            // 左右微调按钮步长
                            val smallStep = if (range < 1) 0.01 else 1.0
                            view.findViewById<Button>(R.id.btnMinus)?.setOnClickListener {
                                val current = et.text.toString().toDoubleOrNull() ?: min
                                val newVal = current - smallStep
                                et.setText(String.format("%.3f", newVal))
                            }
                            view.findViewById<Button>(R.id.btnPlus)?.setOnClickListener {
                                val current = et.text.toString().toDoubleOrNull() ?: min
                                val newVal = current + smallStep
                                et.setText(String.format("%.3f", newVal))
                            }

                            // 中间三颗快捷按钮
                            val quickSteps = when {
                                range >= 40 -> listOf(-5.0, 5.0, 10.0)
                                range >= 10 -> listOf(-2.0, 2.0, 5.0)
                                range >= 1 -> listOf(-0.5, 0.5, 1.0)
                                range >= 0.1 -> listOf(-0.05, 0.05, 0.1)
                                else -> listOf(-0.01, 0.01, 0.02)
                            }
                            val btnQuick1 = view.findViewById<Button>(R.id.btnQuick1)
                            val btnQuick5 = view.findViewById<Button>(R.id.btnQuick5)
                            val btnQuick10 = view.findViewById<Button>(R.id.btnQuick10)
                            // 动态更新按钮文字
                            btnQuick1?.text = if (quickSteps[0] >= 0) "+${quickSteps[0]}" else quickSteps[0].toString()
                            btnQuick5?.text = if (quickSteps[1] >= 0) "+${quickSteps[1]}" else quickSteps[1].toString()
                            btnQuick10?.text = if (quickSteps[2] >= 0) "+${quickSteps[2]}" else quickSteps[2].toString()
                            btnQuick1?.setOnClickListener {
                                val current = et.text.toString().toDoubleOrNull() ?: min
                                val newVal = current + quickSteps[0]
                                et.setText(String.format("%.3f", newVal))
                            }
                            btnQuick5?.setOnClickListener {
                                val current = et.text.toString().toDoubleOrNull() ?: min
                                val newVal = current + quickSteps[1]
                                et.setText(String.format("%.3f", newVal))
                            }
                            btnQuick10?.setOnClickListener {
                                val current = et.text.toString().toDoubleOrNull() ?: min
                                val newVal = current + quickSteps[2]
                                et.setText(String.format("%.3f", newVal))
                            }

                            // 更新 tvRange 显示范围
                            view.findViewById<TextView>(R.id.tvRange)?.text = "范围：$min ~ $max"

                            val green = "#2E7D32".toColorInt()
                            val red = "#C62828".toColorInt()
                            // 初始按钮样式：灰色（不可确认）或可点击
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
                                val v = try { text.toDouble() } catch (_: Exception) { null }
                                if (v == null) {
                                    Toast.makeText(this@InspectionActivity, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }

                                // 更新当前行的值，防止 onSubmit 判断 value == null
                                row.value = v

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
                                val vcur = try { currentText?.toDouble() } catch (_: Exception) { null }
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
                // 若存在数值项，提交前需要确认：初始不禁用，让 onSubmit 做二次校验。
            }
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
                            equipmentId = row.item.equipmentId,
                            itemId = row.item.itemId,
                            value = when (row.ok) { true -> "TRUE"; false -> "FALSE"; null -> "" },
                            abnormal = abnormal,
                            slotIndex = currentSlotIdx,
                        )
                    )
                }
                is FormRow.Number -> {
                    val v = row.value
                    if (v == null) {
                        if (row.item.required) missingList.add(row.item.itemName)
                        values.add(InspectionRecordItemEntity(recordId = 0, equipmentId = row.item.equipmentId, itemId = row.item.itemId, value = "", abnormal = row.item.required, slotIndex = currentSlotIdx))
                    } else {
                        val low = row.item.minValue?.let { v < it } ?: false
                        val high = row.item.maxValue?.let { v > it } ?: false
                        val ab = low || high
                        if (ab) abnormalList.add("${row.item.itemName}=$v")
                        values.add(InspectionRecordItemEntity(recordId = 0, equipmentId = row.item.equipmentId, itemId = row.item.itemId, value = v.toString(), abnormal = ab, slotIndex = currentSlotIdx))
                    }
                }
                is FormRow.Text -> {
                    values.add(
                        InspectionRecordItemEntity(
                            recordId = 0,
                            equipmentId = row.item.equipmentId,
                            itemId = row.item.itemId,
                            value = row.text.trim(),
                            abnormal = false,
                            slotIndex = currentSlotIdx
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
            withContext(Dispatchers.IO) {
                val recordId = db.inspectionDao().insertRecord(
                    InspectionRecordEntity(
                        sessionId = sessionId,
                        pointId = pointId,
                        slotIndex = currentSlotIdx,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // make a mutable copy of incoming items and assign recordId
                val finalItems = items.map { it.copy(recordId = recordId) }.toMutableList()

                // read maintenance / standby lists from intent and add corresponding records
                val maintenanceEquipments = intent.getStringArrayListExtra("maintenanceEquipments") ?: arrayListOf<String>()
                val standbyEquipments = intent.getStringArrayListExtra("standbyEquipments") ?: arrayListOf<String>()
                Log.d("FuckInspectionActivity", "Maintenance Equipments: $maintenanceEquipments, Standby Equipments: $standbyEquipments")
                for (equipId in maintenanceEquipments) {
                    finalItems.add(
                        InspectionRecordItemEntity(
                            recordId = recordId,
                            equipmentId = equipId,
                            itemId = "",
                            value = "维修",
                            abnormal = false,
                            slotIndex = currentSlotIdx
                        )
                    )
                }
                for (equipId in standbyEquipments) {
                    finalItems.add(
                        InspectionRecordItemEntity(
                            recordId = recordId,
                            equipmentId = equipId,
                            itemId = "",
                            value = "备用",
                            abnormal = false,
                            slotIndex = currentSlotIdx
                        )
                    )
                }

                // finally insert all items
                db.inspectionDao().insertItems(finalItems)
                recordId
            }
            Toast.makeText(this@InspectionActivity, "已提交", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
            confirmedNumberItemIds.clear()
        }
    }


}
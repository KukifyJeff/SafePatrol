package com.kukifyjeff.safepatrol.ui.review

import com.kukifyjeff.safepatrol.utils.ShiftUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toColorInt
import androidx.core.view.setPadding
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PointRecordActivity : BaseActivity() {

    private lateinit var db: AppDatabase

    // UI
    private lateinit var tvHeader: TextView
    private lateinit var tvShift: TextView
    private lateinit var tvFreqInfo: TextView
    private lateinit var tblContainer: LinearLayout
    private lateinit var btnFinish: Button

    // Data
    private var pointId: String = ""
    private var pointName: String = ""
    private var shift: String = ""
    private var freqHours: Int = 1
    private var sessionStartTs: Long = 0L
    private var sessionEndTs: Long = 0L

    private var isModified = false

    private val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    // Data structures for display
    data class RecordDetail(
        val record: InspectionRecordEntity,
        val items: MutableList<InspectionRecordItemEntity>
    )

    private var recordDetails: List<RecordDetail> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_record)

        // Initialize Views immediately after setContentView
        tvHeader = findViewById(R.id.tvHeader)
        tvShift = findViewById(R.id.tvShift)
        tvFreqInfo = findViewById(R.id.tvFreqInfo)
        tblContainer = findViewById(R.id.tblContainer)
        btnFinish = findViewById(R.id.btnFinish)

        db = AppDatabase.get(this)

        pointId = intent.getStringExtra("pointId") ?: ""
        pointName = intent.getStringExtra("pointName") ?: ""
        shift = intent.getStringExtra("shiftName") ?: ""
        freqHours = intent.getIntExtra("freqHours", 0)

        val shiftInfo = ShiftUtils.resolveCurrentShift()
        tvShift.text = "当前班次：${shiftInfo.name}"

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now()

        val (startDateTime, endDateTime) = when (shiftInfo.id) {
            "S1" -> Pair(LocalDateTime.of(today, LocalTime.of(8, 30)), LocalDateTime.of(today, LocalTime.of(16, 30)))
            "S2" -> {
                // 中班：16:30 - 00:30, handle cross-day
                if (now.toLocalTime().isBefore(LocalTime.of(0, 30))) {
                    // Between 00:00 - 00:30, belongs to previous day middle shift
                    Pair(
                        LocalDateTime.of(today.minusDays(1), LocalTime.of(16, 30)),
                        LocalDateTime.of(today, LocalTime.of(0, 30))
                    )
                } else {
                    Pair(
                        LocalDateTime.of(today, LocalTime.of(16, 30)),
                        LocalDateTime.of(today.plusDays(1), LocalTime.of(0, 30))
                    )
                }
            }
            else -> Pair(LocalDateTime.of(today, LocalTime.of(0, 30)), LocalDateTime.of(today, LocalTime.of(8, 30)))
        }

        sessionStartTs = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
        sessionEndTs = endDateTime.atZone(zoneId).toInstant().toEpochMilli()

        Log.d(
            "FuckPointRecordAct",
            "Resolved shift window: ${shiftInfo.name}, start=${df.format(Date(sessionStartTs))}, end=${df.format(Date(sessionEndTs))}"
        )

        tvHeader.text = "$pointId - $pointName"
        tvFreqInfo.text = "最短频率：$freqHours h/次"

        btnFinish.setOnClickListener { handleFinish() }
        updateFinishButton()

        loadRecords()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleFinish()
            }
        })
    }

    private fun loadRecords() {
        CoroutineScope(Dispatchers.IO).launch {
            // Debug logging for sessionStartTs and sessionEndTs
            Log.d("FuckPointRecordAct", "sessionStartTs=$sessionStartTs (${df.format(Date(sessionStartTs))}), sessionEndTs=$sessionEndTs (${df.format(Date(sessionEndTs))}), pointId=$pointId")

            // Load records in time window for this pointId and special "-1" pointId for deleted conflicts
            val rawRecords = db.inspectionDao().getRecordsInWindow(sessionStartTs, sessionEndTs)
            Log.d("FuckPointRecordAct", "Fetched ${rawRecords.size} records in window: ${df.format(Date(sessionStartTs))} - ${df.format(Date(sessionEndTs))}")

            val records = rawRecords.filter { it.pointId == pointId || it.pointId == "-1" }
            Log.d("FuckPointRecordAct", "After filtering by pointId=$pointId: ${records.size} records")

            // Fetch equipments for the current pointId in IO
            val equipments = db.equipmentDao().getByPoint(pointId)

            // For each equipment, fetch its CheckItems using equipmentId; build a map
            val equipmentIdToCheckItems = equipments.associate { equipment ->
                equipment.equipmentId to db.checkItemDao().getByEquipment(equipment.equipmentId)
            }
            // Build a global map of itemId to CheckItem for all equipments
            val allCheckItems = equipmentIdToCheckItems.values.flatten()
            val itemIdToCheckItem = allCheckItems.associateBy { it.itemId }

            val itemsMap = db.inspectionDao().getItemsForRecordIds(records.map { it.recordId })
                .groupBy { it.recordId }

            // Only keep InspectionRecordItemEntitys whose itemId is in current equipments' CheckItems
            recordDetails = records.map { record ->
                RecordDetail(
                    record = record,
                    items = itemsMap[record.recordId]
                        .orEmpty()
                        .filter { itemIdToCheckItem.containsKey(it.itemId) }
                        .map { it.copy() }
                        .toMutableList()
                )
            }.sortedBy { it.record.timestamp }

            withContext(Dispatchers.Main) {
                buildTables(equipments, itemIdToCheckItem)
            }
        }
    }

    private fun buildTables(
        equipments: List<com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity>,
        itemIdToCheckItem: Map<String, com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity>
    ) {
        tblContainer.removeAllViews()

        // Debug logging: number of equipments and number of recordDetails
        Log.d("FuckPointRecordAct", "Starting buildTables: equipments.size=${equipments.size}, recordDetails.size=${recordDetails.size}")

        if (recordDetails.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "无记录"
                gravity = Gravity.CENTER
                setPadding(20)
            }
            tblContainer.addView(tvEmpty)
            return
        }

        if (equipments.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "无设备"
                gravity = Gravity.CENTER
                setPadding(20)
            }
            tblContainer.addView(tvEmpty)
            return
        }

        for (equipment in equipments) {
            // For each equipment, filter recordDetails where any item matches equipmentId or record.pointId == "-1"
            val details = recordDetails.filter { detail ->
                detail.items.any { it.equipmentId == equipment.equipmentId } || detail.record.pointId == "-1"
            }
            // Debug logging: current equipment and filtered recordDetails count
            Log.d(
                "FuckPointRecordAct",
                "Equipment: ${equipment.equipmentName ?: equipment.equipmentId}, filtered recordDetails.size=${details.size}"
            )
            if (details.isEmpty()) continue

            val label = TextView(this).apply {
                text = "设备: ${equipment.equipmentName ?: equipment.equipmentId}"
                textSize = 18f
                setPadding(10, 20, 10, 10)
            }
            tblContainer.addView(label)

            // Group all itemIds for this equipment
            val equipmentItemIds = itemIdToCheckItem.values.filter { it.equipmentId == equipment.equipmentId }.map { it.itemId }.distinct()
            // For completeness, add any itemIds present in details but not in CheckItems (for deleted check items)
            val extraItemIds = details.flatMap { it.items }
                .filter { it.equipmentId == equipment.equipmentId }
                .map { it.itemId }
                .distinct()
                .filter { it !in equipmentItemIds }
            val allItemIds = (equipmentItemIds + extraItemIds).distinct()

            // For this equipment, collect all slot times for the shift at the finest granularity (smallest freqHours among its items)
            // But for merged cells, we will merge by each item's own freqHours
            // We'll build the slot headers as the union of all possible slots (use 1h as min slot, or min freqHours)
            val allCheckItems = allItemIds.mapNotNull { itemIdToCheckItem[it] }
            val minFreqHours = allCheckItems.map { it.freqHours }.minOrNull() ?: freqHours
            val shiftDurationHours = ((sessionEndTs - sessionStartTs) / (1000 * 3600.0))
            val maxSlotCount = (shiftDurationHours / minFreqHours).toInt()
            val slotIndexToTimestamp = (1..maxSlotCount).map { idx ->
                sessionStartTs + (idx - 1) * minFreqHours * 3600_000L
            }
            val slotCount = maxSlotCount

            // Build header row with slots (timestamp + 第N次)
            val table = TableLayout(this).apply {
                setStretchAllColumns(true)
                setShrinkAllColumns(true)
            }

            val headerRow = TableRow(this)
            val headerParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)

            val titleHeader = TextView(this).apply {
                text = "检查项"
                gravity = Gravity.CENTER
                setPadding(10)
                setBackgroundColor("#DDDDDD".toColorInt())
            }
            headerRow.addView(titleHeader, headerParams)

            // Build slot headers using slotIndexToTimestamp
            for (i in 0 until slotCount) {
                val ts = slotIndexToTimestamp[i]
                val slotHeader = TextView(this).apply {
                    text = "${df.format(Date(ts))}\n第${i + 1}次"
                    gravity = Gravity.CENTER
                    setPadding(10)
                    setBackgroundColor("#DDDDDD".toColorInt())
                }
                headerRow.addView(slotHeader, headerParams)
            }
            table.addView(headerRow)

            for (itemId in allItemIds) {
                val row = TableRow(this)
                val checkItem = itemIdToCheckItem[itemId]
                val itemFreqHours = checkItem?.freqHours ?: freqHours
                val itemName = TextView(this).apply {
                    text = when {
                        checkItem != null -> "${checkItem.itemName} (${checkItem.freqHours}h)"
                        itemId.isNotEmpty() -> "检查项 ID: $itemId"
                        else -> "未知检查项"
                    }
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(10)
                }
                row.addView(itemName, headerParams)

                // For this item, calculate how many slots (merged cells) it should have
                val itemSlotCount = (shiftDurationHours / itemFreqHours).toInt()
                // For each merged cell, determine which slots it covers
                // For each merged cell:
                var slotIdx = 1
                var mergedCellStart = 1
                while (mergedCellStart <= slotCount) {
                    // The slot in shift for this merged cell: start at mergedCellStart, cover 'mergeSize' slots if itemFreqHours > minFreqHours
                    val mergeSize = (itemFreqHours / minFreqHours).coerceAtLeast(1)
                    // Find the slotIndex for this item (1-based)
                    val itemSlotIndex = ((mergedCellStart - 1) / mergeSize) + 1
                    // Find the InspectionRecordItemEntity for this slot
                    val itemEntity = details.flatMap { it.items }
                        .firstOrNull { it.itemId == itemId && it.equipmentId == equipment.equipmentId && it.slotIndex == itemSlotIndex }

                    val cell = TextView(this).apply {
                        gravity = Gravity.CENTER
                        setPadding(10)
                        setBackgroundResource(android.R.drawable.editbox_background_normal)
                        isClickable = true
                        isFocusable = true
                    }

                    if (itemEntity != null) {
                        cell.text = itemEntity.value ?: "未检"
                        val valueNum = itemEntity.value?.toDoubleOrNull()
                        val isAbnormal = valueNum?.let {
                            val minVal = checkItem?.minValue ?: Double.MIN_VALUE
                            val maxVal = checkItem?.maxValue ?: Double.MAX_VALUE
                            it < minVal || it > maxVal
                        } ?: false
                        if (isAbnormal) cell.setTextColor(android.graphics.Color.RED)

                        cell.setOnClickListener {
                            val detail = recordDetails.firstOrNull { it.items.contains(itemEntity) }
                            if (detail != null) showEditDialog(detail, itemEntity, cell, itemIdToCheckItem)
                        }
                    } else {
                        cell.text = "未检"
                        cell.setTextColor("#999999".toColorInt())
                        cell.isClickable = false
                    }
                    // Set merged cell width by setting span in TableRow.LayoutParams
                    val lp = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, mergeSize.toFloat())
                    row.addView(cell, lp)
                    mergedCellStart += mergeSize
                }
                table.addView(row)
            }

            tblContainer.addView(table)
        }
    }


    private fun showEditDialog(
        detail: RecordDetail,
        itemEntity: InspectionRecordItemEntity?,
        cell: TextView,
        itemIdToCheckItem: Map<String, com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity>
    ) {
        if (itemEntity == null) return

        val checkItem = itemIdToCheckItem[itemEntity.itemId]
        // If checkItem is boolean type (TRUE/FALSE), show radio dialog
        val isBooleanType = checkItem != null &&
                checkItem.type.equals("boolean", ignoreCase = true)
        if (isBooleanType) {
            // Use radio buttons for "正常" (TRUE) and "异常" (FALSE) with unique IDs
            val radioGroup = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
            }
            val trueId = View.generateViewId()
            val falseId = View.generateViewId()
            val rbTrue = RadioButton(this).apply { text = "正常"; id = trueId }
            val rbFalse = RadioButton(this).apply { text = "异常"; id = falseId }
            radioGroup.addView(rbTrue)
            radioGroup.addView(rbFalse)
            // Set checked based on current value
            when (itemEntity.value?.uppercase(Locale.getDefault())) {
                "TRUE" -> radioGroup.check(trueId)
                "FALSE" -> radioGroup.check(falseId)
                else -> radioGroup.clearCheck()
            }
            AlertDialog.Builder(this)
                .setTitle("编辑值")
                .setView(radioGroup)
                .setPositiveButton("确定") { _, _ ->
                    val selected = radioGroup.checkedRadioButtonId
                    val newValue = when (selected) {
                        trueId -> "TRUE"
                        falseId -> "FALSE"
                        else -> null
                    }
                    if (newValue != null && newValue != itemEntity.value) {
                        itemEntity.value = newValue
                        cell.text = newValue
                        cell.setTextColor(android.graphics.Color.BLACK)
                        markAsModified()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // Default: EditText dialog for non-boolean types
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(itemEntity.value ?: "")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("编辑值")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newValue = input.text.toString()
                if (newValue != itemEntity.value) {
                    itemEntity.value = newValue
                    cell.text = newValue.ifBlank { "未检" }
                    val valueNum = newValue.toDoubleOrNull()
                    // Use CheckItemEntity for min/max
                    val isAbnormal = valueNum?.let {
                        val minVal = checkItem?.minValue ?: Double.MIN_VALUE
                        val maxVal = checkItem?.maxValue ?: Double.MAX_VALUE
                        it < minVal || it > maxVal
                    } ?: false
                    cell.setTextColor(if (isAbnormal) android.graphics.Color.RED else android.graphics.Color.BLACK)
                    markAsModified()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleFinish() {
        if (!isModified) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("保存修改")
            .setMessage("您已对点检记录进行了修改，是否确认保存？")
            .setPositiveButton("保存") { _, _ -> saveChanges() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveChanges() {
        CoroutineScope(Dispatchers.IO).launch {
            recordDetails.forEach { detail ->
                detail.items.forEach { itemEntity ->
                    db.inspectionDao().updateRecordItem(itemEntity)
                }
            }
            withContext(Dispatchers.Main) {
                isModified = false
                updateFinishButton()
                finish()
            }
        }
    }

    private fun markAsModified() {
        if (!isModified) {
            isModified = true
            updateFinishButton()
        }
    }

    private fun updateFinishButton() {
        if (!isModified) {
            btnFinish.isEnabled = true
            btnFinish.setBackgroundColor("#9E9E9E".toColorInt())
            btnFinish.text = "返回"
        } else {
            btnFinish.isEnabled = true
            btnFinish.setBackgroundColor(getColor(R.color.blue_primary))
            btnFinish.text = "保存修改"
        }
    }
}
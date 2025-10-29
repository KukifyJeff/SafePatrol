package com.kukifyjeff.safepatrol.ui.inspection

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.ColorStateList
import android.util.TypedValue
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity
import kotlin.properties.Delegates

class EquipmentStatusActivity : AppCompatActivity() {

    private lateinit var llEquipmentList: LinearLayout
    private lateinit var btnConfirm: Button
    private var sessionId: Long = 0L
    private var freqHours: Int = 8

    // 用于存储用户选择的设备状态
    private val selectedStatuses = mutableMapOf<String, String>()

    private var allEquipments: List<EquipmentEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equipment_status)

        llEquipmentList = findViewById(R.id.llEquipmentList)
        btnConfirm = findViewById(R.id.btnConfirm)

        val pointId = intent.getStringExtra("pointId") ?: return
        val pointName = intent.getStringExtra("pointName") ?: "未知点位"
        freqHours = intent.getIntExtra("freqHours", 8)
        sessionId = intent.getLongExtra("sessionId", 0L)

        findViewById<TextView>(R.id.tvPointName).text = "点位：$pointName ($pointId)"

        lifecycleScope.launch {
            loadEquipments(pointId, pointName)
        }

        btnConfirm.setOnClickListener {
            lifecycleScope.launch {
                saveStatuses()
            }
        }
    }

    private suspend fun loadEquipments(pointId: String, pointName: String) {
        val db = AppDatabase.Companion.get(this)
        val equipments = withContext(Dispatchers.IO) {
            db.equipmentDao().getByPoint(pointId)
        }

        allEquipments = equipments

        if (equipments.isEmpty()) {
            Toast.makeText(this, "该点位下没有需要设置状态的设备", Toast.LENGTH_LONG).show()
            return
        }

        withContext(Dispatchers.Main) {
            val blueColor = ContextCompat.getColor(this@EquipmentStatusActivity, R.color.blue_primary)
            val colorRun = ColorStateList.valueOf(android.graphics.Color.parseColor("#E53935"))
            val colorMaint = ColorStateList.valueOf(android.graphics.Color.parseColor("#43A047"))
            val colorStandby = ColorStateList.valueOf(android.graphics.Color.parseColor("#9E9E9E"))
            val colorBlue = ColorStateList.valueOf(blueColor)

            equipments.filter { it.statusRequired }.forEach { eq ->
                val itemView = layoutInflater.inflate(R.layout.item_equipment_status, llEquipmentList, false)

                val tvName = itemView.findViewById<TextView>(R.id.tvEquipmentName)
                val btnRun = itemView.findViewById<Button>(R.id.btnRunning)
                val btnMaint = itemView.findViewById<Button>(R.id.btnMaintenance)
                val btnStandby = itemView.findViewById<Button>(R.id.btnStandby)
                val tvStatus = itemView.findViewById<TextView>(R.id.tvCurrentStatus)

                tvName.text = eq.equipmentName

                // Show equipment ID below name
                val tvEquipId = TextView(this@EquipmentStatusActivity).apply {
                    text = "设备ID：${eq.equipmentId}"
                    setPadding(5, 0, 0, 10)
                }
                // Insert the tvEquipId below tvName in the itemView layout
                val parent = tvName.parent as LinearLayout
                val index = parent.indexOfChild(tvName)
                parent.addView(tvEquipId, index + 1)

                // Set all buttons tint to blue initially
                btnRun.setBackgroundTintList(colorBlue)
                btnMaint.setBackgroundTintList(colorBlue)
                btnStandby.setBackgroundTintList(colorBlue)

                // Buttons enabled
                btnRun.isEnabled = true
                btnMaint.isEnabled = true
                btnStandby.isEnabled = true

                val updateStatus = { status: String ->
                    selectedStatuses[eq.equipmentId] = status
                    tvStatus.text = "当前状态：$status"
                    btnConfirm.isEnabled = selectedStatuses.size == equipments.count { it.statusRequired }

                    // Update button tint colors dynamically
                    when (status) {
                        "运行" -> {
                            btnRun.setBackgroundTintList(colorRun)
                            btnMaint.setBackgroundTintList(colorBlue)
                            btnStandby.setBackgroundTintList(colorBlue)
                        }
                        "检修" -> {
                            btnRun.setBackgroundTintList(colorBlue)
                            btnMaint.setBackgroundTintList(colorMaint)
                            btnStandby.setBackgroundTintList(colorBlue)
                        }
                        "备用" -> {
                            btnRun.setBackgroundTintList(colorBlue)
                            btnMaint.setBackgroundTintList(colorBlue)
                            btnStandby.setBackgroundTintList(colorStandby)
                        }
                    }
                }

                btnRun.setOnClickListener { updateStatus("运行") }
                btnMaint.setOnClickListener { updateStatus("检修") }
                btnStandby.setOnClickListener { updateStatus("备用") }

                llEquipmentList.addView(itemView)
            }
        }
    }

    private suspend fun saveStatuses() {
        val db = AppDatabase.Companion.get(this)
        val runningEquipments = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            selectedStatuses.forEach { (equipId, status) ->
                val mappedStatus = when (status) {
                    "运行" -> "RUNNING"
                    "备用" -> "STANDBY"
                    "检修" -> "MAINTENANCE"
                    else -> status
                }
                db.equipmentStatusDao().upsertStatus(
                    EquipmentStatusEntity(
                        equipmentId = equipId,
                        status = mappedStatus
                    )
                )
                if (mappedStatus == "RUNNING") {
                    runningEquipments.add(equipId)
                }
            }

            // ✅ 自动加入不需要输入状态的设备
            allEquipments.filter { !it.statusRequired }.forEach { eq ->
                if (!runningEquipments.contains(eq.equipmentId)) {
                    runningEquipments.add(eq.equipmentId)
                }
            }
        }

        // ✅ 保存成功后跳转
        withContext(Dispatchers.Main) {
            Toast.makeText(this@EquipmentStatusActivity, "设备状态已保存", Toast.LENGTH_SHORT).show()

            val pointName = intent.getStringExtra("pointName") ?: return@withContext
            val pointId = intent.getStringExtra("pointId") ?: return@withContext

            val intent = Intent(this@EquipmentStatusActivity, InspectionActivity::class.java).apply {
                putExtra("pointId", pointId)
                putExtra("sessionId", sessionId)
                putExtra("pointName", pointName)
                putExtra("freqHours", freqHours)
                putStringArrayListExtra("runningEquipments", ArrayList(runningEquipments))
                putStringArrayListExtra("maintenanceEquipments", ArrayList(selectedStatuses.filter { it.value == "检修" }.keys))
                putStringArrayListExtra("standbyEquipments", ArrayList(selectedStatuses.filter { it.value == "备用" }.keys))
            }
            startActivity(intent)
            finish()
        }
    }
}
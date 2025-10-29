package com.kukifyjeff.safepatrol.ui.inspection

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import android.util.Log
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity
import com.kukifyjeff.safepatrol.data.db.entities.PointEntity
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Objects.toString

class NfcReaderActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val db by lazy { AppDatabase.get(this) }

    // 从 HomeActivity 传入，用于写入检查记录
    private var sessionId: Long = 0L

    private fun appendDebugLine(line: String) {
        try {
            val f = File(cacheDir, "nfc_debug.log")
            FileWriter(f, true).use { fw ->
                fw.appendLine("${java.time.Instant.now()}: $line")
            }
        } catch (e: IOException) {
            Log.w("NfcReaderActivity", "write debug failed: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reader)
        ContextCompat.getColor(this, R.color.blue_primary).also {
            @Suppress("DEPRECATION")
            window.statusBarColor = it
        }

        sessionId = intent.getLongExtra("sessionId", 0L)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "当前设备不支持或未开启 NFC", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 开启 ReaderMode（A/B/F/V 基本覆盖常见工业标签）
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }


    override fun onTagDiscovered(tag: Tag?) {
        if (tag?.id == null) return
        val uidHex = tag.id.toHexString()

        Log.d("NfcReaderActivity", "tag discovered: $uidHex")
        appendDebugLine("tag discovered: $uidHex")

        lifecycleScope.launch {
            try {
                // 查找所有对应的点位
                val allPoints: List<PointEntity> = withContext(Dispatchers.IO) {
                    val rev = uidHex.chunked(2).reversed().joinToString("")
                    val pointsNormal = db.pointDao().findAllByTagUid(uidHex) +
                            db.pointDao().findAllByTagUid(uidHex.lowercase()) +
                            db.pointDao().findAllByTagUid(uidHex.uppercase())
                    val pointsReversed = db.pointDao().findAllByTagUid(rev) +
                            db.pointDao().findAllByTagUid(rev.lowercase()) +
                            db.pointDao().findAllByTagUid(rev.uppercase())
                    (pointsNormal + pointsReversed).distinctBy { it.pointId }
                }

                if (allPoints.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "无效标签，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 获取当前会话以确认 routeId
                val session = withContext(Dispatchers.IO) {
                    db.inspectionDao().getSessionById(sessionId)
                }

                val currentRouteId = session?.routeId?.trim()?.lowercase()

                val matchedPoint = if (currentRouteId != null) {
                    val matched = allPoints.filter { it.routeId.trim().lowercase() == currentRouteId }
                    if (matched.isEmpty()) null else {
                        if (matched.size > 1) {
                            Log.w("NfcReaderActivity", "Multiple points matched route $currentRouteId for tag $uidHex, using first one")
                        }
                        matched.first()
                    }
                } else {
                    allPoints.firstOrNull()
                }

                if (matchedPoint == null) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "当前标签不属于本路线，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }


                // 查询该点位下的设备
                val equipments: List<EquipmentEntity> = withContext(Dispatchers.IO) {
                    db.equipmentDao().getByPoint(matchedPoint.pointId)
                }
                Log.d("FuckNfcReaderActivity", "Fucking equipments are =${equipments.joinToString()}")


                if (equipments.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "该标签下未配置设备，请联系管理员", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val matchesInRoute = if (currentRouteId != null) {
                    if (matchedPoint.routeId.trim().lowercase() == currentRouteId) {
                        equipments
                    } else {
                        emptyList()
                    }
                } else equipments

                val eqs = db.equipmentDao().getByPoint(matchedPoint.pointId)

                // 获取该点下所有检查项
                val allCheckItems = eqs.flatMap { eq ->
                    db.checkItemDao().getByEquipment(eq.equipmentId)
                }

                // 获取频率最高（间隔最短）的频次
                val freq = if (allCheckItems.isNotEmpty()) allCheckItems.minOf { it.freqHours } else 8
                Log.d("FuckNfcReaderActivity", "Fucking session id is $sessionId")

                if (matchesInRoute.isNotEmpty()) {
                    runOnUiThread {
                        val requiresStatusEquipments = equipments.filter { it.statusRequired }
                        if (requiresStatusEquipments.isEmpty()) {
                            // 所有设备都不需要调整状态，直接跳转到 InspectionActivity
                            val runningEquipments = equipments.map { it.equipmentId } // 默认全部视为运行中
                            Log.d("FuckRunningEquips", "Fucking Equipments are ${toString(runningEquipments)}")
                            val intent = Intent(this@NfcReaderActivity, InspectionActivity::class.java).apply {
                                putExtra("pointId", matchedPoint.pointId)
                                putExtra("sessionId", sessionId)
                                putExtra("pointName", matchedPoint.name)
                                putExtra("freqHours", freq)
                                putStringArrayListExtra("runningEquipments", ArrayList(runningEquipments))
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // 存在需要设定状态的设备，跳转到状态选择界面
                            val intent = Intent(this@NfcReaderActivity, EquipmentStatusActivity::class.java).apply {
                                putExtra("pointName", matchedPoint.name)
                                putExtra("pointId", matchedPoint.pointId)
                                putExtra("sessionId", sessionId)
                                putExtra("freqHours", freq)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                    return@launch
                } else {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "当前标签不属于本路线，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NfcReaderActivity, e.message ?: "NFC 解析失败", Toast.LENGTH_LONG).show()
                }
                appendDebugLine("Exception: ${e.message ?: "NFC 解析失败"}")
                finish()
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }.uppercase(Locale.getDefault())}
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
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
                // 查找对应的点位
                val point = withContext(Dispatchers.IO) {
                    db.pointDao().findByTagUid(uidHex)
                        ?: db.pointDao().findByTagUid(uidHex.lowercase())
                        ?: db.pointDao().findByTagUid(uidHex.uppercase())
                        ?: run {
                            val rev = uidHex.chunked(2).reversed().joinToString("")
                            db.pointDao().findByTagUid(rev)
                                ?: db.pointDao().findByTagUid(rev.lowercase())
                                ?: db.pointDao().findByTagUid(rev.uppercase())
                        }
                }

                if (point == null) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "无效标签，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 查询该点位下的设备
                val equipments: List<EquipmentEntity> = withContext(Dispatchers.IO) {
                    db.equipmentDao().getByPoint(point.pointId)
                }

                if (equipments.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "该标签下未配置设备，请联系管理员", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 获取当前会话以确认 routeId
                val session = withContext(Dispatchers.IO) {
                    db.inspectionDao().getSessionById(sessionId)
                }

                val currentRouteId = session?.routeId?.trim()?.lowercase()

                // 当前点位本身已经绑定 routeId，因此直接比较 point.routeId
                val matchesInRoute = if (currentRouteId != null) {
                    if (point.routeId.trim().lowercase() == currentRouteId) {
                        equipments
                    } else {
                        emptyList()
                    }
                } else equipments

                val eqs = db.equipmentDao().getByPoint(point.pointId)

                // 获取该点下所有检查项
                val allCheckItems = eqs.flatMap { eq ->
                    db.checkItemDao().getByEquipment(eq.equipmentId)
                }

                // 获取频率最高（间隔最短）的频次
                val freq = if (allCheckItems.isNotEmpty()) allCheckItems.minOf { it.freqHours } else 8
                Log.d("FuckNfcReaderActivity", "Fucking session id is $sessionId")

                if (matchesInRoute.isNotEmpty()) {
                    runOnUiThread {
                        val intent = Intent(this@NfcReaderActivity, EquipmentStatusActivity::class.java)
                            .putExtra("pointName", point.name)
                            .putExtra("pointId", point.pointId)
                            .putExtra("sessionId", sessionId)
                            .putExtra("freqHours", freq)
                        startActivity(intent)
                        finish()
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
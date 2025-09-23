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
                // 取出所有匹配该 tag 的 equipmentId 列表
                val equipIds = withContext(Dispatchers.IO) {
                    val results = mutableListOf<String>()
                    suspend fun addAllFor(key: String?) {
                        if (key == null) return
                        try {
                            val list = db.nfcMapDao().findEquipmentIdsByTag(key)
                            if (!list.isNullOrEmpty()) results += list
                        } catch (_: Exception) {}
                    }

                    addAllFor(uidHex)
                    addAllFor(uidHex.lowercase())
                    addAllFor(uidHex.uppercase())
                    // 尝试字节序反转
                    val rev = uidHex.chunked(2).reversed().joinToString("")
                    addAllFor(rev)
                    addAllFor(rev.lowercase())
                    addAllFor(rev.uppercase())

                    results.distinct()
                }

                Log.d("NfcReaderActivity", "equipIds for $uidHex = ${equipIds.joinToString()}")
                appendDebugLine("equipIds for $uidHex = ${equipIds.joinToString()}")

                if (equipIds.isNullOrEmpty()) {
                    // 标签未在映射表中
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "无效标签，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 批量查点位
                val points = withContext(Dispatchers.IO) {
                    equipIds.mapNotNull { id -> db.pointDao().findById(id) }
                }

                Log.d("NfcReaderActivity", "points mapped: ${points.map { it.equipmentId + ":" + (it.routeId ?: "") + ":" + (it.name ?: "") }}")
                appendDebugLine("points mapped: ${points.map { it.equipmentId + ":" + (it.routeId ?: "") + ":" + (it.name ?: "") }}")

                if (points.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "标签绑定的点位不存在，请联系管理员", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 获取当前会话以确认 routeId
                val session = withContext(Dispatchers.IO) {
                    db.inspectionDao().getSessionById(sessionId)
                }

                Log.d("NfcReaderActivity", "sessionId=$sessionId routeId=${session?.routeId}")
                appendDebugLine("sessionId=$sessionId routeId=${session?.routeId}")

                val currentRouteId = session?.routeId

                // 优先在 points 中匹配当前 routeId（忽略大小写和空格）
                val matchesInRoute = if (currentRouteId != null) {
                    val cur = currentRouteId.trim().lowercase()
                    points.filter { it.routeId?.trim()?.lowercase() == cur }
                } else emptyList()

                Log.d("NfcReaderActivity", "matchesInRoute: ${matchesInRoute.map { it.equipmentId }}")
                appendDebugLine("matchesInRoute: ${matchesInRoute.map { it.equipmentId }}")

                when {
                    // 如果在当前路线找到唯一匹配，直接使用
                    matchesInRoute.size == 1 -> {
                        val point = matchesInRoute.first()
                        runOnUiThread {
                            appendDebugLine("selected point ${point.equipmentId} route=${point.routeId}")
                            val it = Intent(this@NfcReaderActivity, InspectionActivity::class.java)
                                .putExtra("equipmentId", point.equipmentId)
                                .putExtra("equipmentName", point.name)
                                .putExtra("freqHours", point.freqHours)
                                .putExtra("sessionId", sessionId)
                            startActivity(it)
                            finish()
                        }
                        return@launch
                    }

                    // 如果在当前路线有多个匹配，要求用户选择
                    matchesInRoute.size > 1 -> {
                        val names = matchesInRoute.map { it.name }.toTypedArray()
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this@NfcReaderActivity)
                                .setTitle("请选择点位")
                                .setItems(names) { _, which ->
                                    val chosen = matchesInRoute[which]
                                    appendDebugLine("selected point ${chosen.equipmentId} route=${chosen.routeId}")
                                    val it = Intent(this@NfcReaderActivity, InspectionActivity::class.java)
                                        .putExtra("equipmentId", chosen.equipmentId)
                                        .putExtra("equipmentName", chosen.name)
                                        .putExtra("freqHours", chosen.freqHours)
                                        .putExtra("sessionId", sessionId)
                                    startActivity(it)
                                    finish()
                                }
                                .setCancelable(true)
                                .show()
                        }
                        return@launch
                    }

                    // 如果没有匹配到当前 route，但只映射到单一点位，则使用该点位（向后兼容）
                    points.size == 1 -> {
                        val point = points.first()
                        runOnUiThread {
                            appendDebugLine("selected point ${point.equipmentId} route=${point.routeId}")
                            val it = Intent(this@NfcReaderActivity, InspectionActivity::class.java)
                                .putExtra("equipmentId", point.equipmentId)
                                .putExtra("equipmentName", point.name)
                                .putExtra("freqHours", point.freqHours)
                                .putExtra("sessionId", sessionId)
                            startActivity(it)
                            finish()
                        }
                        return@launch
                    }

                    // 其他情况：存在多个映射但未匹配当前路线
                    else -> {
                        Log.d("NfcReaderActivity", "no matching route, equipIds: ${equipIds.joinToString()}, points: ${points.map { it.equipmentId }}")
                        appendDebugLine("no matching route, equipIds: ${equipIds.joinToString()}, points: ${points.map { it.equipmentId }}")
                        runOnUiThread {
                            Toast.makeText(this@NfcReaderActivity, "当前标签不属于本路线，请重新扫描", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
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
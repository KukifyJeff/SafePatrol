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

class NfcReaderActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val db by lazy { AppDatabase.get(this) }

    // 从 HomeActivity 传入，用于写入检查记录
    private var sessionId: Long = 0L

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

        lifecycleScope.launch {
            try {
                val equipmentId = withContext(Dispatchers.IO) {
                    db.nfcMapDao().findEquipmentIdByTag(uidHex)
                }
                if (equipmentId == null) {
                    // 标签未在映射表中
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "无效标签，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val point = withContext(Dispatchers.IO) {
                    db.pointDao().findById(equipmentId)
                }
                if (point == null) {
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "标签绑定的点位不存在，请联系管理员", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 获取当前会话以确认 routeId
                val session = withContext(Dispatchers.IO) {
                    db.inspectionDao().getSessionById(sessionId)
                }

                if (session != null && session.routeId != null && session.routeId != point.routeId) {
                    // 标签属于其他路线
                    runOnUiThread {
                        Toast.makeText(this@NfcReaderActivity, "当前标签不属于本路线，请重新扫描", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 一切正常，进入点检页面
                val it = Intent(this@NfcReaderActivity, InspectionActivity::class.java)
                    .putExtra("equipmentId", point.equipmentId)
                    .putExtra("equipmentName", point.name)
                    .putExtra("freqHours", point.freqHours)
                    .putExtra("sessionId", sessionId)

                startActivity(it)
                finish()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NfcReaderActivity, e.message ?: "NFC 解析失败", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }.uppercase(Locale.getDefault())
}
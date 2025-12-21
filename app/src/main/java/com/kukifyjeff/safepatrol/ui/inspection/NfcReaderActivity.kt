package com.kukifyjeff.safepatrol.ui.inspection

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.data.db.entities.EquipmentEntity
import com.kukifyjeff.safepatrol.data.db.entities.PointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Locale
import java.util.Objects.toString

@Suppress("DEPRECATION")
class NfcReaderActivity : BaseActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private val db by lazy { AppDatabase.get(this) }

    // 从 HomeActivity 传入，用于写入检查记录
    private var sessionId: Long = 0L

    private lateinit var nfcPendingIntent: PendingIntent
    private lateinit var nfcIntentFilters: Array<IntentFilter>

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

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        val techFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        nfcIntentFilters = arrayOf(techFilter)

        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "当前设备不支持或未开启 NFC", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(
            this,
            nfcPendingIntent,
            nfcIntentFilters,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            handleTag(tag)
        }
    }

    private fun handleTag(tag: Tag) {
        val uidHex = tag.id.toHexString()
        Log.d("NfcReaderActivity", "tag discovered: $uidHex")
        appendDebugLine("tag discovered: $uidHex")
        lifecycleScope.launch {
            try {
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
                        Toast.makeText(
                            this@NfcReaderActivity,
                            "无效标签，请重新扫描",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val session = withContext(Dispatchers.IO) {
                    db.inspectionDao().getSessionById(sessionId)
                }

                val currentRouteId = session?.routeId?.trim()?.lowercase()

                val matchedPoint = if (currentRouteId != null) {
                    val matched =
                        allPoints.filter { it.routeId.trim().lowercase() == currentRouteId }
                    if (matched.isEmpty()) null else matched.first()
                } else {
                    allPoints.firstOrNull()
                }

                if (matchedPoint == null) {
                    runOnUiThread {
                        Toast.makeText(
                            this@NfcReaderActivity,
                            "当前标签不属于本路线，请重新扫描",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val equipments: List<EquipmentEntity> = withContext(Dispatchers.IO) {
                    db.equipmentDao().getByPoint(matchedPoint.pointId)
                }

                if (equipments.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@NfcReaderActivity,
                            "该标签下未配置设备，请联系管理员",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val matchesInRoute = if (currentRouteId != null) {
                    if (matchedPoint.routeId.trim().lowercase() == currentRouteId) {
                        equipments
                    } else emptyList()
                } else equipments

                val eqs = db.equipmentDao().getByPoint(matchedPoint.pointId)

                val allCheckItems = eqs.flatMap { eq ->
                    db.checkItemDao().getByEquipment(eq.equipmentId)
                }

                val freq =
                    if (allCheckItems.isNotEmpty()) allCheckItems.minOf { it.freqHours } else 8

                if (matchesInRoute.isNotEmpty()) {
                    runOnUiThread {
                        val requiresStatusEquipments = equipments.filter { it.statusRequired }
                        if (requiresStatusEquipments.isEmpty()) {
                            val runningEquipments = equipments.map { it.equipmentId }
                            val intent = Intent(
                                this@NfcReaderActivity,
                                InspectionActivity::class.java
                            ).apply {
                                putExtra("pointId", matchedPoint.pointId)
                                putExtra("sessionId", sessionId)
                                putExtra("pointName", matchedPoint.name)
                                putExtra("freqHours", freq)
                                putStringArrayListExtra(
                                    "runningEquipments",
                                    ArrayList(runningEquipments)
                                )
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            val intent = Intent(
                                this@NfcReaderActivity,
                                EquipmentStatusActivity::class.java
                            ).apply {
                                putExtra("pointName", matchedPoint.name)
                                putExtra("pointId", matchedPoint.pointId)
                                putExtra("sessionId", sessionId)
                                putExtra("freqHours", freq)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@NfcReaderActivity,
                            "当前标签不属于本路线，请重新扫描",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@NfcReaderActivity,
                        e.message ?: "NFC 解析失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
                appendDebugLine("Exception: ${e.message ?: "NFC 解析失败"}")
                finish()
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }.uppercase(Locale.getDefault())
}
package com.kukifyjeff.safepatrol

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.system.exitProcess
import androidx.core.content.edit
import com.kukifyjeff.safepatrol.ui.inspection.FormRow

open class BaseActivity : AppCompatActivity() {

    companion object {
        @JvmStatic
        var developerMode: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
//        checkForTimeTampering()
    }

//    private fun checkForTimeTampering() {
//        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
//        val lastTime = prefs.getLong(KEY_LAST_TIME, -1L)
//        val currentTime = System.currentTimeMillis()
//        val diff = currentTime - lastTime
//
//        val toleranceBackward = 5 * 60 * 1000   // 5分钟
//        val lastTimeInstant = java.time.Instant.ofEpochMilli(lastTime)
//        val currentTimeInstant = java.time.Instant.ofEpochMilli(currentTime)
//        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//            .withZone(java.time.ZoneId.systemDefault())
//
//
//        if (lastTime > 0 && (diff < -toleranceBackward) && (!developerMode)) {
//
//            AlertDialog.Builder(this)
//                .setTitle("系统时间异常")
//                .setMessage("检测到设备时间可能被修改。\n上次记录时间：${formatter.format(lastTimeInstant)}\n当前系统时间：${formatter.format(currentTimeInstant)}\n请校准系统时间后重启应用。")
//                .setCancelable(false)
//                .setPositiveButton("退出") { _, _ ->
//                    finishAffinity()
//                    exitProcess(0)
//                }
//                .show()
//        } else {
//            prefs.edit { putLong(KEY_LAST_TIME, currentTime) }
//        }
//    }
}
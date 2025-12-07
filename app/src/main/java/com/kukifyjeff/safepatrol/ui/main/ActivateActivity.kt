package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.ui.route.RouteSelectActivity

class ActivateActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "app_prefs"
        private const val KEY_ACTIVATED = "activated"
        private const val SECRET_KEY = "G2X69-CX8Y7-JE8FY" // 替换为真实密钥
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activate)

        val etKey = findViewById<EditText>(R.id.etKey)
        val btnActivate = findViewById<Button>(R.id.btnActivate)

        btnActivate.setOnClickListener {
            val inputKey = etKey.text.toString().trim()
            if (validateKey(inputKey)) {
                val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                prefs.edit { putBoolean(KEY_ACTIVATED, true) }

                // 激活成功，进入首页
                startActivity(Intent(this, RouteSelectActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "密钥无效，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateKey(key: String): Boolean {
        // 简单校验，可改成 Hash 或服务器验证
        return key == SECRET_KEY
    }
}
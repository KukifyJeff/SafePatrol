package com.kukifyjeff.safepatrol.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.ui.admin.DBUpdateActivity

import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.EditText
import androidx.core.content.edit
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.enableEdgeToEdge

class AdminActivity : AppCompatActivity() {

    private lateinit var itemDbUpdate: android.view.View
    private lateinit var itemIconTheme: android.view.View
    private lateinit var itemAppName: android.view.View
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        prefs = getSharedPreferences("admin_settings", MODE_PRIVATE)

        bindViews()
        setupItems()
        setupActions()
    }

    private fun bindViews() {
        itemDbUpdate = findViewById(R.id.itemDbUpdate)
        itemIconTheme = findViewById(R.id.itemDebugExport)   // reuse for now
        itemAppName = findViewById(R.id.itemAbout)           // reuse for now
    }

    private fun setupItems() {
        // 数据库更新
        setItem(
            itemDbUpdate,
            "数据库更新",
            "导入、校验并替换数据库"
        )

        // 图标换色（占位）
        setItem(
            itemIconTheme,
            "图标主题",
            "切换应用图标颜色（开发中）"
        )

        // App名称修改（占位）
        setItem(
            itemAppName,
            "应用名称",
            "修改应用显示名称（开发中）"
        )
    }

    private fun setupActions() {
        // 跳转数据库更新
        itemDbUpdate.setOnClickListener {
            startActivity(Intent(this, DBUpdateActivity::class.java))
        }

        // 图标换色
        itemIconTheme.setOnClickListener {
        }

        // App名称修改
        itemAppName.setOnClickListener {

        }
    }

    private fun setItem(view: android.view.View, title: String, subtitle: String) {
        view.findViewById<TextView>(R.id.tvTitle).text = title
        view.findViewById<TextView>(R.id.tvSubtitle).text = subtitle
    }
}

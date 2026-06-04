package com.kukifyjeff.safepatrol.ui.route

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.CsvBootstrapper
import com.kukifyjeff.safepatrol.data.db.entities.EmployeeEntity
import com.kukifyjeff.safepatrol.databinding.ActivityRouteSelectBinding
import com.kukifyjeff.safepatrol.ui.main.ConfirmSystemTimeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class RouteSelectActivity : BaseActivity() {

    private lateinit var binding: ActivityRouteSelectBinding
    private val db by lazy { AppDatabase.get(this) }
    private var routeIds: List<String> = emptyList()
    private var employees: List<EmployeeEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activation check
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val activated = prefs.getBoolean("activated", false)
        if (!activated) {
            startActivity(
                Intent(
                    this,
                    com.kukifyjeff.safepatrol.ui.main.ActivateActivity::class.java
                )
            )
            finish()
            return
        }
        binding = ActivityRouteSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val statusColor = "#CD9DB9EF".toColorInt() // AARRGGBB
        statusColor.also { window.statusBarColor = it }
        loadOrImportRoutes()

        var headerClickCount = 0
        binding.imgHeader.setOnClickListener {
            headerClickCount++
            if (headerClickCount >= 5) {
                developerMode = true
                Toast.makeText(this, "已进入开发者模式", Toast.LENGTH_SHORT).show()
                headerClickCount = 0
            }
        }

        binding.btnStart.setOnClickListener {
            val operatorId = binding.etOperatorId.text.toString().trim()
            val idx = binding.routeSpinner.selectedItemPosition
            if (operatorId.isEmpty() || idx < 0) {
                Toast.makeText(this, "请输入工号并选择路线", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val employee = employees.find { it.employeeId.equals(operatorId, ignoreCase = true) }
            if (employee == null) {
                Toast.makeText(this, "无效工号，请检查后重新输入", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (employee.isAdmin) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("管理员入口")
                    .setMessage("请选择要进入的界面")
                    .setPositiveButton("点检界面") { _, _ ->
                        val routeId = routeIds.getOrNull(idx) ?: ""
                        val routeName = if (routeIds.isEmpty()) "（无路线）" else binding.routeSpinner.selectedItem.toString()
                        startActivity(
                            Intent(this, ConfirmSystemTimeActivity::class.java)
                                .putExtra("routeId", routeId)
                                .putExtra("routeName", routeName)
                                .putExtra("operatorId", operatorId)
                                .putExtra("operatorName", employee.employeeName)
                        )
                        finish()
                    }
                    .setNegativeButton("管理员界面") { _, _ ->
                        startActivity(
                            Intent(this, com.kukifyjeff.safepatrol.ui.admin.AdminActivity::class.java)
                                .putExtra("operatorId", operatorId)
                                .putExtra("operatorName", employee.employeeName)
                        )
                        finish()
                    }
                    .setCancelable(true)
                    .show()
                return@setOnClickListener
            }

            val routeId = routeIds[idx]
            val routeName = binding.routeSpinner.selectedItem.toString()
            startActivity(
                Intent(this, ConfirmSystemTimeActivity::class.java)
                    .putExtra("routeId", routeId)
                    .putExtra("routeName", routeName)
                    .putExtra("operatorId", operatorId)
                    .putExtra("operatorName", employee.employeeName)
            )
            finish()
        }
    }

    private fun loadOrImportRoutes() {
        lifecycleScope.launch {
            var routes = db.routeDao().getAll()

            // 如果数据库为空，尝试从 assets/config 再导入一次
            if (routes.isEmpty()) {
                withContext(Dispatchers.IO) {
                    // 把 assets/config 拷到 files/config（若不存在）
                    CsvBootstrapper.ensureConfigDirWithDefaults(this@RouteSelectActivity)
                    // 重新导入到数据库（注意：这是挂起函数）
                    CsvBootstrapper.importAllFromConfig(this@RouteSelectActivity, db)
                }
                routes = db.routeDao().getAll()
            }

            if (routes.isEmpty()) {
                Toast.makeText(
                    this@RouteSelectActivity,
                    "未找到巡检路线（当前允许管理员模式登录）",
                    Toast.LENGTH_LONG
                ).show()

                routeIds = emptyList()
                binding.routeSpinner.adapter = ArrayAdapter(
                    this@RouteSelectActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("（无路线）")
                )

                employees = db.employeeDao().getAll()
                return@launch
            }

            routeIds = routes.map { it.routeId }
            employees = db.employeeDao().getAll()
            binding.routeSpinner.adapter = ArrayAdapter(
                this@RouteSelectActivity,
                android.R.layout.simple_spinner_dropdown_item,
                routes.map { it.routeName }
            )
        }
    }
}
package com.kukifyjeff.safepatrol.ui.route

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.CsvBootstrapper
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.databinding.ActivityRouteSelectBinding
import com.kukifyjeff.safepatrol.ui.main.HomeActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouteSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteSelectBinding
    private val db by lazy { AppDatabase.get(this) }
    private var routeIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val statusColor = Color.parseColor("#CD9DB9EF") // AARRGGBB
        statusColor.also { window.statusBarColor = it }
        loadOrImportRoutes()

        binding.btnStart.setOnClickListener {
            val operatorId = binding.etOperatorId.text.toString().trim()
            val idx = binding.routeSpinner.selectedItemPosition
            if (operatorId.isEmpty() || idx < 0) {
                Toast.makeText(this, "请输入工号并选择路线", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val routeId = routeIds[idx]
            val routeName = binding.routeSpinner.selectedItem.toString()
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .putExtra("routeId", routeId)
                    .putExtra("routeName", routeName)
                    .putExtra("operatorId", operatorId)
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
                // 还为空：提示排查 assets 目录和文件名
                Toast.makeText(this@RouteSelectActivity,
                    "未找到巡检路线，请确认 assets/config/routes.csv 是否存在且有数据（含表头）",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            routeIds = routes.map { it.routeId }
            binding.routeSpinner.adapter = ArrayAdapter(
                this@RouteSelectActivity,
                android.R.layout.simple_spinner_dropdown_item,
                routes.map { it.routeName }
            )
        }
    }
}
package com.kukifyjeff.safepatrol.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.kukifyjeff.safepatrol.BaseActivity
import com.kukifyjeff.safepatrol.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfirmSystemTimeActivity : BaseActivity() {

    private lateinit var tvTime: TextView
    private lateinit var btnConfirm: Button
    private val handler = Handler(Looper.getMainLooper())
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            tvTime.text = sdf.format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_system_time)

        val routeId = intent.getStringExtra("routeId") ?: ""
        val routeName = intent.getStringExtra("routeName") ?: ""
        val operatorId = intent.getStringExtra("operatorId") ?: ""
        val operatorName = intent.getStringExtra("operatorName") ?: ""

        tvTime = findViewById(R.id.tvTime)
        btnConfirm = findViewById(R.id.btnConfirm)

        // Start updating time every second
        handler.post(updateTimeRunnable)

        btnConfirm.setOnClickListener {
            val intentRoute = Intent(this, HomeActivity::class.java)
                .putExtra("routeId", routeId)
                .putExtra("routeName", routeName)
                .putExtra("operatorId", operatorId)
                .putExtra("operatorName", operatorName)
            startActivity(intentRoute)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
    }
}
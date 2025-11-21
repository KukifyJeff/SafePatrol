package com.kukifyjeff.safepatrol

import android.content.Intent
import android.os.Bundle
import com.kukifyjeff.safepatrol.ui.route.RouteSelectActivity

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RouteSelectActivity::class.java))
        finish()
    }
}
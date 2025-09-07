package com.kukifyjeff.safepatrol.config

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset

object ConfigLoader {

    private var cachedPassword: String? = null

    fun getExportPassword(context: Context, default: String = "G7v#pK9!sX2qLm4@"): String {
        // 先读缓存，避免多次 I/O
        cachedPassword?.let { return it }
        return try {
            val json = context.assets.open("config.json")
                .use { it.readBytes().toString(Charset.forName("UTF-8")) }
            val obj = JSONObject(json)
            val pwd = obj.optString("exportPassword", default)
            cachedPassword = pwd
            pwd
        } catch (_: Throwable) {
            // 文件不存在或格式问题 → 使用默认
            default
        }
    }
}
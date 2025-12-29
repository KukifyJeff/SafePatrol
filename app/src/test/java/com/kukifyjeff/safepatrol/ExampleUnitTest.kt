package com.kukifyjeff.safepatrol

import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExampleUnitTest {

    @Test
    fun printTimestamp() {
        // 指定日期 2025-12-15 00:00:00
        val localDateTime = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
        // 转换为东八区
        val zoneId = ZoneId.of("Asia/Shanghai")
        val timestamp = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
        println("Timestamp for 2025-12-25 00:00:00 UTC+8: $timestamp")
    }
}
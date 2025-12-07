package com.kukifyjeff.safepatrol.utils

import java.time.LocalTime
import java.time.ZonedDateTime

object ShiftUtils {
    data class Window(val startMs: Long, val endMs: Long)

    /** 计算当前时间的班次（白，中，夜） */
    fun currentShiftWindowMillis(now: ZonedDateTime = ZonedDateTime.now()): Window {
        val zone = now.zone
        val today = now.toLocalDate()
        val t0030 = ZonedDateTime.of(today, LocalTime.of(0, 30), zone)
        val t0830 = ZonedDateTime.of(today, LocalTime.of(8, 30), zone)
        val t1630 = ZonedDateTime.of(today, LocalTime.of(16, 30), zone)

        val start = when {
            !now.isBefore(t0830) && now.isBefore(t1630) -> t0830 // 白
            (!now.isBefore(t1630)) || now.isBefore(t0030) ->
                if (!now.isBefore(t1630)) t1630 else t1630.minusDays(1) // 中（跨天）
            else -> t0030 // 夜
        }
        val end = start.plusHours(8)
        return Window(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
    }

}
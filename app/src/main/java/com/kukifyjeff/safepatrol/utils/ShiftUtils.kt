package com.kukifyjeff.safepatrol.utils

import java.time.LocalTime
import java.time.ZonedDateTime

object ShiftUtils {
    data class Window(val startMs: Long, val endMs: Long)

    // 班次边界：08:30 白班起；16:30 中班起；00:30 夜班起（跨天）
    fun currentShiftWindowMillis(now: ZonedDateTime = ZonedDateTime.now()): Window {
        val zone = now.zone
        val today = now.toLocalDate()

        val t0030 = ZonedDateTime.of(today, LocalTime.of(0,30), zone)
        val t0830 = ZonedDateTime.of(today, LocalTime.of(8,30), zone)
        val t1630 = ZonedDateTime.of(today, LocalTime.of(16,30), zone)

        val start = when {
            !now.isBefore(t0830) && now.isBefore(t1630) -> t0830 // 白班
            (!now.isBefore(t1630)) || now.isBefore(t0030) ->  // 中班（跨天）
                if (!now.isBefore(t1630)) t1630 else t1630.minusDays(1)
            else -> t0030 // 夜班
        }
        val end = start.plusHours(8) // 一个班次 8h
        return Window(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
    }

    /** 频率=4h 时的槽位：前4h→1；后4h→2；其它频率→1 */
    fun slotIndexNow(freqHours: Int, now: ZonedDateTime = ZonedDateTime.now()): Int {
        if (freqHours != 4) return 1
        val w = currentShiftWindowMillis(now)
        return if (now.toInstant().toEpochMilli() < w.startMs + 4 * 60 * 60 * 1000L) 1 else 2
    }
}
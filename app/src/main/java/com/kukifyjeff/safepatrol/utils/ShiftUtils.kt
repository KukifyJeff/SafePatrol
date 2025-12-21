package com.kukifyjeff.safepatrol.utils

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ShiftUtils {
    data class Window(val startMs: Long, val endMs: Long)

    data class ShiftInfo(
        val id: String,
        val name: String,
        val rangeText: String
    )

    private val shiftValues = listOf("甲", "乙", "丙", "丁", "戊")
    private val initialDate = LocalDate.of(2025, 12, 15)
    private val initialShiftMap = mapOf(
        "白班" to "甲",
        "中班" to "丙",
        "夜班" to "戊"
    )

    /** 解析当前时间所属班次（白 / 中 / 夜） */
    fun resolveCurrentShift(now: LocalTime = LocalTime.now()): ShiftInfo {
        val t0830 = LocalTime.of(8, 30)
        val t1630 = LocalTime.of(16, 30)
        val t0030 = LocalTime.of(0, 30)

        return when {
            !now.isBefore(t0830) && now.isBefore(t1630) ->
                ShiftInfo("S1", "白班", "08:30-16:30")
            (!now.isBefore(t1630)) || now.isBefore(t0030) ->
                ShiftInfo("S2", "中班", "16:30-次日00:30")
            else ->
                ShiftInfo("S3", "夜班", "00:30-08:30")
        }
    }

    /** 当前班次时间窗口（毫秒） */
    fun currentShiftWindowMillis(now: ZonedDateTime = ZonedDateTime.now()): Window {
        val zone = now.zone
        val today = now.toLocalDate()
        val t0030 = ZonedDateTime.of(today, LocalTime.of(0, 30), zone)
        val t0830 = ZonedDateTime.of(today, LocalTime.of(8, 30), zone)
        val t1630 = ZonedDateTime.of(today, LocalTime.of(16, 30), zone)

        val start = when {
            !now.isBefore(t0830) && now.isBefore(t1630) -> t0830
            (!now.isBefore(t1630)) || now.isBefore(t0030) ->
                if (!now.isBefore(t1630)) t1630 else t1630.minusDays(1)
            else -> t0030
        }
        val end = start.plusHours(8)
        return Window(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
    }

    /** 计算某天某班次的当班值（甲乙丙丁戊） */
    fun getValueOnShift(date: LocalDate, shiftName: String): String {
        val daysDiff = ChronoUnit.DAYS.between(initialDate, date).toInt()
        val baseValue = initialShiftMap[shiftName] ?: error("班次不存在")
        val startIndex = shiftValues.indexOf(baseValue)
        val valueIndex = (startIndex + daysDiff) % shiftValues.size
        return shiftValues[valueIndex]
    }

    /** 获取当前班次的当班值 */
    fun currentShiftValue(now: ZonedDateTime = ZonedDateTime.now()): String {
        val shift = resolveCurrentShift(now.toLocalTime())
        val date = now.toLocalDate()
        return getValueOnShift(date, shift.name)
    }
}
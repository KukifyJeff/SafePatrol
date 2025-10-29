package com.kukifyjeff.safepatrol.utils

import java.util.*

object SlotUtils {

    // 班次时间段（固定）
    private val SHIFT_WINDOWS = listOf(
        Shift("白班", 8, 30, 16, 30),
        Shift("中班", 16, 30, 0, 30),
        Shift("夜班", 0, 30, 8, 30)
    )

    data class Shift(
        val name: String,
        val startHour: Int,
        val startMin: Int,
        val endHour: Int,
        val endMin: Int
    )

    /**
     * 返回给定频率的总槽位数
     */
    fun getTotalSlots(freqHours: Int): Int = when (freqHours) {
        2 -> 4
        4 -> 2
        8 -> 1
        else -> 1
    }

    /**
     * 返回当前时间在对应频率下属于第几个槽位（1起始）
     */
    fun getSlotIndex(freqHours: Int, timestamp: Long = System.currentTimeMillis()): Int {
        val now = Calendar.getInstance().apply { timeInMillis = timestamp }

        // 找出当前属于哪个班次
        val currentShift = getCurrentShift(now)
        val start = getShiftStartTime(currentShift, now)
        val end = getShiftEndTime(currentShift, now)

        val totalSlots = getTotalSlots(freqHours)
        if (totalSlots == 1) return 1

        val slotLenMs = (end.timeInMillis - start.timeInMillis) / totalSlots
        val offsetMs = (timestamp - start.timeInMillis).coerceIn(
            0,
            (end.timeInMillis - start.timeInMillis - 1)
        )
        val slotIndex = (offsetMs / slotLenMs).toInt() + 1

        return slotIndex.coerceIn(1, totalSlots)
    }

    /**
     * 返回当前班次名称
     */
    fun getCurrentShiftName(timestamp: Long = System.currentTimeMillis()): String {
        val now = Calendar.getInstance().apply { timeInMillis = timestamp }
        return getCurrentShift(now).name
    }

    /**
     * 返回当前时间下应显示的频率列表（单位小时）
     * 例如当前在第3个2h槽位时，返回 [2, 4]
     */
    fun getActiveFrequenciesForCurrentSlot(timestamp: Long = System.currentTimeMillis()): List<Int> {
        val slot2h = getSlotIndex(2, timestamp)
        val active = mutableListOf(2)
        if (slot2h == 1 || slot2h == 3) active.add(4)
        if (slot2h == 1) active.add(8)
        return active
    }

    // ===== 内部函数 =====

    private fun getCurrentShift(now: Calendar): Shift {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        return when {
            (hour > 8 || (hour == 8 && minute >= 30)) && (hour < 16 || (hour == 16 && minute < 30)) -> SHIFT_WINDOWS[0]
            (hour > 16 || (hour == 16 && minute >= 30)) || (hour < 0 || (hour == 0 && minute < 30)) -> SHIFT_WINDOWS[1]
            else -> SHIFT_WINDOWS[2]
        }
    }

    private fun getShiftStartTime(shift: Shift, now: Calendar): Calendar {
        val cal = now.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, shift.startHour)
        cal.set(Calendar.MINUTE, shift.startMin)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (shift.name == "夜班" && now.get(Calendar.HOUR_OF_DAY) < 8) {
            // 夜班属于前一天的 0:30 - 8:30
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal
    }

    private fun getShiftEndTime(shift: Shift, now: Calendar): Calendar {
        val cal = now.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, shift.endHour)
        cal.set(Calendar.MINUTE, shift.endMin)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // 结束时间如果跨天，向后推一天
        if (shift.endHour < shift.startHour) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal
    }
}
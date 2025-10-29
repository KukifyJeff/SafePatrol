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
//    fun getSlotIndex(freqHours: Int, timestamp: Long = System.currentTimeMillis()): Int {
//        val now = Calendar.getInstance().apply { timeInMillis = timestamp }
//
//        // 找出当前属于哪个班次
//        val currentShift = getCurrentShift(now)
//        val start = getShiftStartTime(currentShift, now)
//        val end = getShiftEndTime(currentShift, now)
//
//        val totalSlots = getTotalSlots(freqHours)
//        if (totalSlots == 1) return 1
//
//        val slotLenMs = (end.timeInMillis - start.timeInMillis) / totalSlots
//        val offsetMs = (timestamp - start.timeInMillis).coerceIn(
//            0,
//            (end.timeInMillis - start.timeInMillis - 1)
//        )
//        val slotIndex = (offsetMs / slotLenMs).toInt() + 1
//
//        return slotIndex.coerceIn(1, totalSlots)
//    }

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
    fun getActiveFrequenciesForCurrentSlot(freqHours: Int, currentTime: Long = System.currentTimeMillis()): List<Int> {
        val slotIndex = getSlotIndex(freqHours, currentTime)
        return when (freqHours) {
            // 最高频率为2h的情况：4个槽位
            2 -> when (slotIndex) {
                1 -> listOf(2, 4, 8)
                2 -> listOf(2)
                3 -> listOf(2, 4)
                4 -> listOf(2)
                else -> listOf(2)
            }

            // 最高频率为4h的情况：2个槽位
            4 -> when (slotIndex) {
                1 -> listOf(4, 8)
                2 -> listOf(4)
                else -> listOf(4)
            }

            // 最高频率为8h的情况：仅1个槽位，始终显示8
            8 -> listOf(8)

            else -> listOf(8)
        }
    }

    /**
     * 根据频率计算当前槽位 index。
     * 2h → 4 槽, 4h → 2 槽, 8h → 1 槽。
     */
    fun getSlotIndex(freqHours: Int, currentTime: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (freqHours) {
            2 -> (hour / 2) % 4 + 1   // 每2小时一个槽，共4个
            4 -> (hour / 4) % 2 + 1   // 每4小时一个槽，共2个
            else -> 1                 // 8小时或其他情况，仅1个槽
        }
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
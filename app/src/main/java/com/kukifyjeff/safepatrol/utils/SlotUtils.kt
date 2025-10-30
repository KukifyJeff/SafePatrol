package com.kukifyjeff.safepatrol.utils

import java.util.*

object SlotUtils {


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

}
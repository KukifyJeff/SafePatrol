package com.kukifyjeff.safepatrol.utils

import java.util.Calendar

object SlotUtils {


    /**
     * 返回当前时间下应显示的频率列表（单位小时）
     * 例如当前在第3个2h槽位时，返回 [2, 4]
     */
    fun getActiveFrequenciesForCurrentSlot(
        freqHours: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<Int> {
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
     * freqHours：当前点位/设备 的最高检测频率，currentTime是该点位的时间。
     */
    fun getSlotIndex(freqHours: Int, currentTime: Long): Int {
        // determine shift start and end based on defined boundaries
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }

        // today at 00:30, 08:30, 16:30
        val today = Calendar.getInstance().apply { timeInMillis = currentTime }
        val t00_30 = Calendar.getInstance().apply {
            timeInMillis = currentTime; set(Calendar.HOUR_OF_DAY, 0); set(
            Calendar.MINUTE,
            30
        ); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val t08_30 = Calendar.getInstance().apply {
            timeInMillis = currentTime; set(Calendar.HOUR_OF_DAY, 8); set(
            Calendar.MINUTE,
            30
        ); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val t16_30 = Calendar.getInstance().apply {
            timeInMillis = currentTime; set(Calendar.HOUR_OF_DAY, 16); set(
            Calendar.MINUTE,
            30
        ); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        // tomorrow 00:30 and yesterday 16:30 for comparisons
        val tTomorrow00_30 = Calendar.getInstance()
            .apply { timeInMillis = t00_30.timeInMillis; add(Calendar.DAY_OF_MONTH, 1) }
        val tYesterday16_30 = Calendar.getInstance()
            .apply { timeInMillis = t16_30.timeInMillis; add(Calendar.DAY_OF_MONTH, -1) }

        val cur = currentTime

        val shiftStartMs: Long
        val shiftEndMs: Long

        when {
            // between 08:30 (inclusive) and 16:30 (exclusive) => day shift
            cur >= t08_30.timeInMillis && cur < t16_30.timeInMillis -> {
                shiftStartMs = t08_30.timeInMillis
                shiftEndMs = t16_30.timeInMillis
            }
            // between 16:30 (inclusive) and next day 00:30 (exclusive) => mid shift
            cur >= t16_30.timeInMillis && cur < tTomorrow00_30.timeInMillis -> {
                shiftStartMs = t16_30.timeInMillis
                shiftEndMs = tTomorrow00_30.timeInMillis
            }
            // between 00:30 (inclusive) and 08:30 (exclusive) => night shift (today)
            cur >= t00_30.timeInMillis && cur < t08_30.timeInMillis -> {
                shiftStartMs = t00_30.timeInMillis
                shiftEndMs = t08_30.timeInMillis
            }
            // times between 00:00 and 00:30 belong to previous day's mid shift
            else -> {
                // previous day's 16:30 to today 00:30
                val prev16_30 = Calendar.getInstance()
                    .apply { timeInMillis = t16_30.timeInMillis; add(Calendar.DAY_OF_MONTH, -1) }
                val prev00_30 = Calendar.getInstance().apply { timeInMillis = t00_30.timeInMillis }
                shiftStartMs = prev16_30.timeInMillis
                shiftEndMs = prev00_30.timeInMillis
            }
        }

        val nSlots = when (freqHours) {
            2 -> 4
            4 -> 2
            else -> 1
        }

        val slotLenMs = (shiftEndMs - shiftStartMs).toDouble() / nSlots.toDouble()
        val offset = (currentTime - shiftStartMs).coerceAtLeast(0L).toDouble()
        var idx = (offset / slotLenMs).toInt() + 1
        if (idx < 1) idx = 1
        if (idx > nSlots) idx = nSlots

        return idx
    }

}
package com.kukifyjeff.safepatrol.ui.main

data class SlotStatus(
    val name: String,           // "第一次" / "第二次" / "本班"
    val done: Boolean,          // 是否完成
    val timeText: String? = null // 完成时间，例如 "09:12"
)

data class PointStatusUi(
    val equipmentId: String,
    val name: String,
    val location: String,
    val freqHours: Int,         //2， 4 或 8
    val slots: List<SlotStatus>
)
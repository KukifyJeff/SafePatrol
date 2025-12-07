package com.kukifyjeff.safepatrol.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kukifyjeff.safepatrol.R

class PointStatusAdapter(
    private var data: List<PointStatusUi>,
    private val onClickPoint: (PointStatusUi) -> Unit // 点击卡片可进入点检
) : RecyclerView.Adapter<PointStatusAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSub: TextView = view.findViewById(R.id.tvSub)
        val slotContainer: LinearLayout = view.findViewById(R.id.slotContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.item_point_status, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        val freqLabel = when (item.freqHours) {
            2 -> "2h/次"
            4 -> "4h/次"
            8 -> "8h/次"
            else -> "${item.freqHours}h/次"
        }

        holder.tvTitle.text = holder.itemView.context.getString(
            R.string.point_status_title, item.equipmentId, item.name, freqLabel
        )
        holder.tvSub.text =
            holder.itemView.context.getString(R.string.point_status_sub, item.location)

        // 清空容器，重新添加槽位行
        holder.slotContainer.removeAllViews()
        item.slots.forEach { slot ->
            holder.slotContainer.addView(makeSlotRow(holder.slotContainer, slot))
        }

        holder.itemView.setOnClickListener { onClickPoint(item) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newData: List<PointStatusUi>) {
        data = newData
        notifyDataSetChanged()
    }

    private fun makeSlotRow(parent: ViewGroup, slot: SlotStatus): View {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp4(ctx) }
        }

        val tvName = TextView(ctx).apply {
            text = slot.name
            textSize = 14f
        }
//        val tvStatus = TextView(ctx).apply {
//            text =
//                if (slot.done) ctx.getString(R.string.point_slot_status_done, slot.timeText ?: "")
//                else ctx.getString(R.string.point_slot_status_pending)
//            textSize = 14f
//        }

        row.addView(tvName)
//        row.addView(tvStatus)
        return row
    }

    private fun dp4(ctx: android.content.Context): Int {
        val scale = ctx.resources.displayMetrics.density
        return (4 * scale + 0.5f).toInt()
    }
}
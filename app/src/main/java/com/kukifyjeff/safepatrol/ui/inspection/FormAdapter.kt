package com.kukifyjeff.safepatrol.ui.inspection

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.kukifyjeff.safepatrol.data.db.entities.CheckItemEntity
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import android.content.res.ColorStateList
import com.kukifyjeff.safepatrol.R

sealed class FormRow(val item: CheckItemEntity) {
    class Bool(item: CheckItemEntity, var ok: Boolean? = null) : FormRow(item)
    class Number(item: CheckItemEntity, var value: Double? = null) : FormRow(item)
    class Text(item: CheckItemEntity, var text: String = "") : FormRow(item)
}

class FormAdapter(private val rows: List<FormRow>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val T_BOOL = 1
        private const val T_NUM = 2
        private const val T_TEXT = 3
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is FormRow.Bool -> T_BOOL
        is FormRow.Number -> T_NUM
        is FormRow.Text -> T_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_BOOL -> BoolVH(inf.inflate(R.layout.item_form_boolean_industrial, parent, false))
            T_NUM  -> NumVH(inf.inflate(R.layout.item_form_number_industrial, parent, false))
            else   -> TextVH(inf.inflate(R.layout.item_form_text_industrial, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is FormRow.Bool -> (holder as BoolVH).bind(row)
            is FormRow.Number -> (holder as NumVH).bind(row)
            is FormRow.Text -> (holder as TextVH).bind(row)
        }
    }

    class BoolVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvLabel: TextView = v.findViewById(R.id.tvLabel)
        private val tvHint: TextView = v.findViewById(R.id.tvHint)
        private val btnOk: Button = v.findViewById(R.id.btnOk)
        private val btnNg: Button = v.findViewById(R.id.btnNg)

        fun bind(row: FormRow.Bool) {
            tvLabel.text = itemView.context.getString(R.string.form_bool_label, row.item.itemName)
            fun render() {
                when (row.ok) {
                    true -> {
                        btnOk.setBackgroundColor("#2E7D32".toColorInt()) // 深绿
                        btnOk.setTextColor(Color.WHITE)
                        btnNg.setBackgroundColor("#EEEEEE".toColorInt())
                        btnNg.setTextColor(Color.BLACK)
                        tvHint.text = itemView.context.getString(R.string.status_current_normal)
                        tvHint.setTextColor("#2E7D32".toColorInt())
                    }
                    false -> {
                        btnNg.setBackgroundColor("#C62828".toColorInt()) // 深红
                        btnNg.setTextColor(Color.WHITE)
                        btnOk.setBackgroundColor("#EEEEEE".toColorInt())
                        btnOk.setTextColor(Color.BLACK)
                        tvHint.text = itemView.context.getString(R.string.status_current_abnormal)
                        tvHint.setTextColor("#C62828".toColorInt())
                    }
                    null -> {
                        btnOk.setBackgroundColor("#EEEEEE".toColorInt())
                        btnOk.setTextColor(Color.BLACK)
                        btnNg.setBackgroundColor("#EEEEEE".toColorInt())
                        btnNg.setTextColor(Color.BLACK)
                        tvHint.text = itemView.context.getString(R.string.status_current_unselected)
                        tvHint.setTextColor("#666666".toColorInt())
                    }
                }
            }
            render()
            btnOk.setOnClickListener { row.ok = true; render() }
            btnNg.setOnClickListener { row.ok = false; render() }
        }
    }

    class NumVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvLabel: TextView = v.findViewById(R.id.tvLabel)
        private val tvRange: TextView = v.findViewById(R.id.tvRange)
        private val et: EditText = v.findViewById(R.id.etValue)
//        private val btnMinus: Button = v.findViewById(R.id.btnMinus)
//        private val btnPlus: Button = v.findViewById(R.id.btnPlus)
        private val btnQuick1: Button = v.findViewById(R.id.btnQuick1)
        private val btnQuick5: Button = v.findViewById(R.id.btnQuick5)
        private val btnQuick10: Button = v.findViewById(R.id.btnQuick10)
        private val tvStatus: TextView = v.findViewById(R.id.tvStatus)

        fun bind(row: FormRow.Number) {
            val unit = row.item.unit ?: ""
            tvLabel.text = itemView.context.getString(R.string.form_number_label, row.item.itemName, unit)
            val min = row.item.minValue?.toString() ?: "-"
            val max = row.item.maxValue?.toString() ?: "-"
            tvRange.text = itemView.context.getString(R.string.range_format, min, max)

            fun setValue(newVal: Double?) {
                row.value = newVal
                et.setText(newVal?.toString() ?: "")
                et.setSelection(et.text.length)
                renderStatus(row)
            }

            fun readValue(): Double? = et.text.toString().trim().toDoubleOrNull()

            fun delta(d: Double) {
                val cur = readValue() ?: 0.0
                setValue(cur + d)
            }

//            btnMinus.setOnClickListener { delta(-1.0) }
//            btnPlus.setOnClickListener { delta(+1.0) }
            btnQuick1.setOnClickListener { delta(+1.0) }
            btnQuick5.setOnClickListener { delta(+5.0) }
            btnQuick10.setOnClickListener { delta(+10.0) }

            et.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { renderStatus(row) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // 初始
            et.setText(row.value?.toString() ?: "")
            renderStatus(row)
        }

        private fun renderStatus(row: FormRow.Number) {
            val v = et.text.toString().trim().toDoubleOrNull()
            val has = v != null
            val min = row.item.minValue
            val max = row.item.maxValue

            // Themed colors (auto-adapt to dark/light)
            val cOnSurfaceVar = MaterialColors.getColor(et, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val cError = MaterialColors.getColor(et, com.google.android.material.R.attr.colorError)
            val cPrimary = MaterialColors.getColor(et, com.google.android.material.R.attr.colorPrimary)
            val cOutline = MaterialColors.getColor(et, com.google.android.material.R.attr.colorOutline)

            fun tintEditText(color: Int?) {
                // Prefer tinting instead of hard background colors for better M3 compatibility
                et.backgroundTintList = color?.let { ColorStateList.valueOf(it) }
                // Fallback outline color for some styles
                et.highlightColor = color ?: cOutline
            }

            when {
                !has -> {
                    tvStatus.text = itemView.context.getString(R.string.status_unentered)
                    tvStatus.setTextColor(cOnSurfaceVar)
                    tintEditText(null) // reset to default tint
                }
                (min != null && v < min) -> {
                    tvStatus.text = itemView.context.getString(R.string.status_abnormal_low)
                    tvStatus.setTextColor(cError)
                    tintEditText(cError)
                }
                (max != null && v > max) -> {
                    tvStatus.text = itemView.context.getString(R.string.status_abnormal_high)
                    tvStatus.setTextColor(cError)
                    tintEditText(cError)
                }
                else -> {
                    tvStatus.text = itemView.context.getString(R.string.status_normal)
                    tvStatus.setTextColor(cPrimary)
                    tintEditText(cPrimary)
                }
            }
        }
    }

    class TextVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvLabel: TextView = v.findViewById(R.id.tvLabel)
        private val et: EditText = v.findViewById(R.id.etValue)
        fun bind(row: FormRow.Text) {
            tvLabel.text = itemView.context.getString(R.string.form_item_label, row.item.itemName)
            et.setText(row.text)
            et.setOnFocusChangeListener { _, _ -> row.text = et.text.toString().trim() }
        }
    }
}
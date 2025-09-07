package com.kukifyjeff.safepatrol.export

import android.content.Context
import android.os.Environment
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.data.db.entities.InspectionRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.poifs.crypt.HashAlgorithm
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date

/**
 * 导出工具（XLSX 版）
 *
 * - 直接导出为 .xlsx（不再生成 CSV 或 ZIP）
 * - 打开无需密码；尝试修改/变更结构时需要输入“修改密码”
 * - 支持只读推荐（Open as Read-Only Recommended）
 * - 生成三张工作表：4h_第一次、4h_第二次、8h
 * - 每行代表一个【检查项】（而不是记录），列为：
 *   A routeId
 *   B routeName
 *   C operatorId
 *   D shiftId
 *   E equipmentId
 *   F equipmentName
 *   G recordTimeLocal
 *   H timezone
 *   I checkInfo（检查项信息，目前填 itemId）
 *   J result（输入的数值 + （正常/异常））
 */
object ExportUtil {

    /**
     * 导出当前 session 的数据为 XLSX。
     * @param modifyPassword 打开不需要密码；**修改/保存结构**需要此密码（留空则不设置修改密码）。
     * @param readOnlyRecommended 是否启用“只读推荐”。
     * @return 生成的 .xlsx 绝对路径
     */
    suspend fun exportSessionXlsx(
        context: Context,
        db: AppDatabase,
        sessionId: Long,
        modifyPassword: String,
        readOnlyRecommended: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        // === 读取基础数据 ===
        val session = db.inspectionDao().getSessionById(sessionId)
            ?: throw IllegalStateException("未找到本次巡检会话")
        val tz = TimeZone.getDefault().id
        val routeId = session.routeId
        val routeName = session.routeName
        val shiftId = session.shiftId

        val points = db.pointDao().getByRoute(routeId)
        val pointMap = points.associateBy { it.equipmentId }

        val allRecords = db.inspectionDao().getAllRecordsForSession(sessionId)
        val allItems = db.inspectionDao().getAllItemsForSession(sessionId)
        val itemsByRecord = allItems.groupBy { it.recordId }

        // === 写 Excel ===
        val wb = XSSFWorkbook()
        val header = arrayOf(
            "routeId",        // A
            "routeName",      // B
            "operatorId",     // C
            "shiftId",        // D
            "equipmentId",    // E
            "equipmentName",  // F
            "recordTimeLocal",// G
            "timezone",       // H
            "checkInfo",      // I (当前填 itemId)
            "result"          // J (输入数值 + (正常/异常))
        )

        fun createSheet(name: String) = wb.createSheet(name).apply { createFreezePane(0, 1) }
        val s4h1 = createSheet("4h_第一次")
        val s4h2 = createSheet("4h_第二次")
        val s8h  = createSheet("8h")

        val headStyle = wb.createCellStyle().apply {
            val font = wb.createFont().apply { bold = true }
            setFont(font); wrapText = true
        }

        fun putHeader(sheet: org.apache.poi.ss.usermodel.Sheet) {
            val r = sheet.createRow(0)
            header.forEachIndexed { i, title ->
                r.createCell(i).apply {
                    setCellValue(title)
                    cellStyle = headStyle
                }
            }
        }
        putHeader(s4h1); putHeader(s4h2); putHeader(s8h)

        val hh = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 一行 = 一个检查项
        fun appendItemRow(
            sheet: org.apache.poi.ss.usermodel.Sheet,
            rec: InspectionRecordEntity,
            itemId: String,
            value: String?,
            abnormal: Boolean
        ) {
            val p = pointMap[rec.equipmentId]
            val eqName = p?.name ?: ""
            val row = sheet.createRow(sheet.lastRowNum + 1)

            val resultText = if (value.isNullOrBlank()) {
                if (abnormal) "(异常)" else "(正常)"
            } else {
                "$value(${if (abnormal) "异常" else "正常"})"
            }

            val cells = arrayOf(
                routeId,                           // A
                routeName,                         // B
                session.operatorId,                // C
                shiftId,                           // D
                rec.equipmentId,                   // E
                eqName,                            // F
                hh.format(Date(rec.timestamp)),    // G
                tz,                                // H
                itemId.toString(),                 // I
                resultText                         // J
            )
            cells.forEachIndexed { idx, v -> row.createCell(idx).setCellValue(v) }
        }

        // 遍历记录 -> 每条记录下的每个检查项都写一行
        allRecords.forEach { rec ->
            val items = itemsByRecord[rec.recordId].orEmpty()
            val freq = pointMap[rec.equipmentId]?.freqHours ?: 8
            val targetSheet = when {
                freq == 4 && rec.slotIndex == 1 -> s4h1
                freq == 4 && rec.slotIndex == 2 -> s4h2
                else -> s8h
            }
            items.forEach { itm ->
                appendItemRow(
                    sheet = targetSheet,
                    rec = rec,
                    itemId = itm.itemId,
                    value = itm.value,
                    abnormal = itm.abnormal
                )
            }
        }

        // 自适应列宽（设置上限防止过大 & 性能问题）
        arrayOf(s4h1, s4h2, s8h).forEach { sh ->
            for (i in header.indices) {
                try { sh.autoSizeColumn(i) } catch (_: Throwable) { }
                val w = sh.getColumnWidth(i).coerceAtMost(80 * 256) // ~80 字符宽
                sh.setColumnWidth(i, w)
            }
        }

        // 只读推荐 + 修改密码（打开无需密码）
        if (readOnlyRecommended) {
            val ctWb = wb.ctWorkbook
            val fs = if (ctWb.isSetFileSharing) ctWb.fileSharing else ctWb.addNewFileSharing()
            fs.setReadOnlyRecommended(true)
        }
        if (modifyPassword.isNotEmpty()) {
            wb.lockStructure()
            wb.setWorkbookPassword(modifyPassword, HashAlgorithm.sha512)
        }

        // === 保存 ===
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val day = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeRoute = routeName.replace(Regex("[\\/:*?\"<>|]"), "_")
        val safeShift = shiftId.replace(Regex("[\\/:*?\"<>|]"), "_")
        val xlsx = File(outDir, "SafePatrol_${day}_${safeRoute}_${safeShift}.xlsx")

        xlsx.outputStream().use { wb.write(it) }
        wb.close()

        xlsx.absolutePath
    }
}
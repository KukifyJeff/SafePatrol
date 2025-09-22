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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 导出工具（按月导出，单 Sheet，按时间顺序导出所有点位的点检记录）
 *
 * - 导出目标为 .xlsx（单个工作表："Monthly"），按记录时间排序。
 * - 每条记录下的每个检查项占一行；若记录没有检查项也会输出一行（item 列为空）。
 * - 文件保存在应用的 Documents 私有目录（外部私有目录），文件名格式：SafePatrol_Monthly-YYYY-MM.xlsx
 * - 支持只读推荐与修改密码（打开无需密码；修改结构需要密码）。
 */
object ExportUtil {

    /**
     * 导出指定公历年-月的所有点检记录（按时间顺序），返回生成文件的绝对路径。
     * @param year 公历年（例如 2025）
     * @param month 公历月（1..12）
     */
    suspend fun exportMonthlyXlsx(
        context: Context,
        db: AppDatabase,
        year: Int,
        month: Int,
        modifyPassword: String = "",
        readOnlyRecommended: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        // 计算当月起止毫秒（含当月第一毫秒，不含下月第一毫秒）
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val startMs = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val endMsExclusive = cal.timeInMillis
        val endMs = endMsExclusive - 1L

        // 查询当月内所有记录（按时间顺序）
        val allRecordsInWindow = db.inspectionDao().getRecordsInWindow(startMs, endMs)
            .sortedBy { it.timestamp }

        // 如果没有记录可以抛出或返回空文件，我们选择生成空表并返回路径
        // 取出所有 recordIds 并批量查询 items
        val recordIds = allRecordsInWindow.map { it.recordId }
        val itemsByRecord = if (recordIds.isEmpty()) emptyMap()
        else db.inspectionDao().getItemsForRecordIds(recordIds).groupBy { it.recordId }

        // 预取相关 session 与 point 信息，方便在导出时填充
        val sessionIds = allRecordsInWindow.map { it.sessionId }.distinct()
        val sessionsMap = if (sessionIds.isEmpty()) emptyMap()
        else db.inspectionDao().getSessionsByIds(sessionIds).associateBy { it.sessionId }

        // 如果 pointDao 没有提供 getAll()，我们按 session 对应的 routeId 批量拉取点位集合并合并
        val routeIds = sessionsMap.values.map { it.routeId }.distinct()
        val points = routeIds.flatMap { rid -> db.pointDao().getByRoute(rid) }
        val pointMap = points.associateBy { it.equipmentId }

        // 创建 Excel（单表）
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Monthly")
        sheet.createFreezePane(0, 1)

        val header = arrayOf(
            "recordTimestamp",
            "sessionId",
            "routeId",
            "routeName",
            "operatorId",
            "shiftId",
            "equipmentId",
            "equipmentName",
            "slotIndex",
            "itemId",
            "itemValue",
            "abnormal"
        )

        val headStyle = wb.createCellStyle().apply {
            val f = wb.createFont().apply { bold = true }
            setFont(f); wrapText = true
        }

        // 写 header
        val hr = sheet.createRow(0)
        header.forEachIndexed { i, t -> hr.createCell(i).apply { setCellValue(t); cellStyle = headStyle } }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val tzId = TimeZone.getDefault().id

        // helper：把一个 record 与对应 items 写入表格（若 items 为空也写一行）
        fun appendRecordRows(startRowIdx: Int, rec: InspectionRecordEntity): Int {
            val items = itemsByRecord[rec.recordId].orEmpty()
            val p = pointMap[rec.equipmentId]
            val eqName = p?.name ?: ""
            val session = sessionsMap[rec.sessionId]
            val routeId = session?.routeId ?: ""
            val routeName = session?.routeName ?: ""
            val operatorIdOut = session?.operatorId ?: ""
            val shiftIdOut = session?.shiftId ?: ""

            if (items.isEmpty()) {
                val r = sheet.createRow(startRowIdx)
                val cells = arrayOf(
                    sdf.format(Date(rec.timestamp)),
                    rec.sessionId.toString(),
                    routeId,
                    routeName,
                    operatorIdOut,
                    shiftIdOut,
                    rec.equipmentId,
                    eqName,
                    rec.slotIndex.toString(),
                    "",
                    "",
                    ""
                )
                cells.forEachIndexed { idx, v -> r.createCell(idx).setCellValue(v) }
                return startRowIdx + 1
            }

            var next = startRowIdx
            for (itm in items) {
                val r = sheet.createRow(next)
                val cells = arrayOf(
                    sdf.format(Date(rec.timestamp)),
                    rec.sessionId.toString(),
                    routeId,
                    routeName,
                    operatorIdOut,
                    shiftIdOut,
                    rec.equipmentId,
                    eqName,
                    rec.slotIndex.toString(),
                    itm.itemId,
                    itm.value,
                    if (itm.abnormal) "1" else "0"
                )
                cells.forEachIndexed { idx, v -> r.createCell(idx).setCellValue(v) }
                next++
            }
            return next
        }

        // 写数据行（按时间顺序）
        var rowIdx = 1
        for (rec in allRecordsInWindow) {
            rowIdx = appendRecordRows(rowIdx, rec)
        }

        // 自适应列宽（上限保护）
        for (i in header.indices) {
            try { sheet.autoSizeColumn(i) } catch (_: Throwable) {}
            val w = sheet.getColumnWidth(i).coerceAtMost(80 * 256)
            sheet.setColumnWidth(i, w)
        }

        // 只读推荐 + 修改密码
        if (readOnlyRecommended) {
            val ctWb = wb.ctWorkbook
            val fs = if (ctWb.isSetFileSharing) ctWb.fileSharing else ctWb.addNewFileSharing()
            fs.readOnlyRecommended = true
        }
        if (modifyPassword.isNotEmpty()) {
            wb.lockStructure()
            wb.setWorkbookPassword(modifyPassword, HashAlgorithm.sha512)
        }

        // 保存到 App 私有目录（Documents）
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val filename = String.format(Locale.getDefault(), "SafePatrol_Monthly-%04d-%02d.xlsx", year, month)
        val xlsx = File(outDir, filename)
        xlsx.outputStream().use { wb.write(it) }
        wb.close()
        xlsx.absolutePath
    }
}
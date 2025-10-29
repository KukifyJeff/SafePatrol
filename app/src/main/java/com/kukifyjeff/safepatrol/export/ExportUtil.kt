package com.kukifyjeff.safepatrol.export

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import com.kukifyjeff.safepatrol.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

        // 查询当月内所有实际记录（用于按 recordId 批量加载 items）
        val actualRecords = db.inspectionDao().getRecordsInWindow(startMs, endMs).sortedBy { it.timestamp }
        val recordIds = actualRecords.map { it.recordId }
        val itemsByRecord = if (recordIds.isEmpty()) emptyMap()
        else db.inspectionDao().getItemsForRecordIds(recordIds).groupBy { it.recordId }

        // 预取 session 信息（按 sessionId）
        val sessionIds = actualRecords.map { it.sessionId }.distinct()
        val sessionsMap = if (sessionIds.isEmpty()) emptyMap()
        else db.inspectionDao().getSessionsByIds(sessionIds).associateBy { it.sessionId }

        // 选择当前 route（优先取 sessionsMap 中第一个 session 的 route）
        val currentRouteId: String? = sessionsMap.values.firstOrNull()?.routeId
        val currentRouteName: String = sessionsMap.values.firstOrNull()?.routeName ?: ""

        // 如果 pointDao 没有提供 getAll()，我们按 session 对应的 routeId 批量拉取点位集合并合并
        val points = if (currentRouteId != null) {
            db.pointDao().getByRoute(currentRouteId)
        } else {
            val routeIds = sessionsMap.values.map { it.routeId }.distinct()
            routeIds.flatMap { rid -> db.pointDao().getByRoute(rid) }
        }
        val pointMap = points.associateBy { it.pointId }

        // 预加载每个点位的检查项名称映射 (itemId -> itemName)
        val itemNameByEquip = mutableMapOf<String, Map<String, String>>()
        for (p in points) {
            val cis = db.checkItemDao().getByEquipment(p.pointId)
            val m = cis.associateBy({ it.itemId }, { it.itemName })
            itemNameByEquip[p.pointId] = m
        }

        // 预加载设备名称缓存 (pointId -> equipmentName)
        val equipmentNameCache = mutableMapOf<String, String>()

        // 创建 Excel（单表）
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Monthly")
        sheet.createFreezePane(0, 1)

        val header = arrayOf(
            "日期",
            "时间",
            "路线名",
            "点检员",
            "班次",
            "点位id",
            "点位名",
            "设备名",
            "点检项",
            "点检频率",
            "点检频次",
            "检测值",
            "是否正常"
        )

        val headStyle = wb.createCellStyle().apply {
            val f = wb.createFont().apply { bold = true }
            setFont(f); wrapText = true
        }

        // 写 header
        val hr = sheet.createRow(0)
        header.forEachIndexed { i, t -> hr.createCell(i).apply { setCellValue(t); cellStyle = headStyle } }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun shiftIdToName(shiftId: String?): String {
            val s = shiftId?.trim()?.lowercase() ?: ""
            val key = when {
                s.startsWith("s") -> s.substring(1)
                else -> s
            }
            return when (key) {
                "1", "01", "s1" -> "白班"
                "2", "02", "s2" -> "中班"
                "3", "03", "s3" -> "夜班"
                else -> ""
            }
        }

        fun shiftNameFromWindowStart(startMs: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = startMs }
            return when (c.get(Calendar.HOUR_OF_DAY)) {
                8 -> "白班"
                16 -> "中班"
                0 -> "夜班"
                else -> ""
            }
        }

        @SuppressLint("DefaultLocale")
        fun formatDateTimeForRecord(ts: Long, windowStart: Long, windowEnd: Long): Pair<String, String> {
            val cTs = Calendar.getInstance().apply { timeInMillis = ts }
            // determine if window spans midnight (start day != end day)
            val cs = Calendar.getInstance().apply { timeInMillis = windowStart }
            val ce = Calendar.getInstance().apply { timeInMillis = windowEnd }
            val spansMidnight = cs.get(Calendar.DAY_OF_YEAR) != ce.get(Calendar.DAY_OF_YEAR) || cs.get(Calendar.YEAR) != ce.get(Calendar.YEAR)

            if (spansMidnight && cTs.get(Calendar.HOUR_OF_DAY) == 0 && cTs.get(Calendar.MINUTE) < 30) {
                // treat as 24:MM on previous day
                val dateStr = sdfDate.format(Date(windowStart))
                val timeStr = String.format("24:%02d", cTs.get(Calendar.MINUTE))
                return Pair(dateStr, timeStr)
            }
            return Pair(sdfDate.format(Date(ts)), sdfTime.format(Date(ts)))
        }

        val windows = mutableListOf<Pair<Long, Long>>()
        val cal2 = Calendar.getInstance().apply { timeInMillis = startMs }
        val currentWindow = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
        // iterate days from start to end, but stop when window start > currentWindow.endMs
        while (cal2.timeInMillis <= endMs) {
            val y = cal2.get(Calendar.YEAR)
            val m = cal2.get(Calendar.MONTH)
            val d = cal2.get(Calendar.DAY_OF_MONTH)

            fun msOf(hour: Int, minute: Int, dayOffset: Int = 0): Long {
                val c = Calendar.getInstance().apply { clear(); set(y, m, d + dayOffset, hour, minute) }
                return c.timeInMillis
            }

            // 夜班：00:30 - 08:30 (same day)
            val s0 = msOf(0, 30)
            val e0 = msOf(8, 30)
            if (e0 >= startMs && s0 <= endMs && s0 <= currentWindow.startMs) windows.add(Pair(s0.coerceAtLeast(startMs), e0.coerceAtMost(endMs)))

            // 白班：08:30 - 16:30
            val s1 = msOf(8, 30)
            val e1 = msOf(16, 30)
            if (e1 >= startMs && s1 <= endMs && s1 <= currentWindow.startMs) windows.add(Pair(s1.coerceAtLeast(startMs), e1.coerceAtMost(endMs)))

            // 中班：16:30 - 次日00:30
            val s2 = msOf(16, 30)
            val e2 = msOf(0, 30, 1)
            if (e2 >= startMs && s2 <= endMs && s2 <= currentWindow.startMs) windows.add(Pair(s2.coerceAtLeast(startMs), e2.coerceAtMost(endMs)))

            cal2.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 为每个窗口、每个点位、每个槽位输出行：若有记录则写出记录对应的 items，否则写出一行未检
        var rowIdx = 1

        // 预加载每个设备的检查频次（从 CheckItemEntity 而非 PointEntity）
        val freqByEquip = mutableMapOf<String, Int>()
        for (p in points) {
            val freqs = db.checkItemDao().getFreqHoursByEquipment(p.pointId)
            val freq = if (freqs.isNotEmpty()) freqs.maxOrNull() ?: 8 else 8
            freqByEquip[p.pointId] = freq
        }

        for ((wStart, wEnd) in windows) {
            for (p in points) {
                val freq = freqByEquip[p.pointId] ?: 8
                val nSlots = when (freq) { 2 -> 4; 4 -> 2; 8 -> 1; else -> 1 }
                for (slotIdx in 1..nSlots) {
                    // 查找该点位在该窗口、该槽位的记录（选择最新一条）
                    val recs = db.inspectionDao().getRecordsForPointSlotInWindow(
                        pointId = p.pointId,
                        slotIndex = slotIdx,
                        startMs = wStart,
                        endMs = wEnd
                    )
                    val latest = recs.maxByOrNull { it.timestamp }
                    if (latest != null) {
                        // 有记录，写出对应 items 行（若没有 items 也写占位）
                        val items = itemsByRecord[latest.recordId].orEmpty()
                        val shiftName = sessionsMap[latest.sessionId]?.shiftId?.let { shiftIdToName(it) } ?: shiftNameFromWindowStart(wStart)

                        // 获取设备名缓存或查询
                        val equipName = equipmentNameCache.getOrPut(latest.pointId) {
                            db.equipmentDao().getById(latest.pointId)?.equipmentName ?: ""
                        }

                        if (items.isEmpty()) {
                            val (dateStr, timeStr) = formatDateTimeForRecord(latest.timestamp, wStart, wEnd)
                            val r = sheet.createRow(rowIdx)
                            val cells = arrayOf(
                                dateStr,
                                timeStr,
                                sessionsMap[latest.sessionId]?.routeName ?: currentRouteName,
                                sessionsMap[latest.sessionId]?.operatorId ?: "",
                                shiftName,
                                latest.pointId,
                                pointMap[latest.pointId]?.name ?: "",
                                equipName,
                                "",
                                freq.toString(),
                                latest.slotIndex.toString(),
                                "未检",
                                ""
                            )
                            cells.forEachIndexed { idx, v -> r.createCell(idx).setCellValue(v) }
                            rowIdx++
                        } else {
                            for (itm in items) {
                                val (dateStr, timeStr) = formatDateTimeForRecord(latest.timestamp, wStart, wEnd)
                                val r = sheet.createRow(rowIdx)
                                val itemLabel =
                                    itemNameByEquip[latest.pointId]?.get(itm.itemId) ?: itm.itemId
                                val freqHours = db.checkItemDao().getByEquipment(latest.pointId)
                                    .firstOrNull { it.itemId == itm.itemId }?.freqHours ?: 8
                                val cells = arrayOf(
                                    dateStr,
                                    timeStr,
                                    sessionsMap[latest.sessionId]?.routeName ?: currentRouteName,
                                    sessionsMap[latest.sessionId]?.operatorId ?: "",
                                    shiftName,
                                    latest.pointId,
                                    pointMap[latest.pointId]?.name ?: "",
                                    equipName,
                                    itemLabel,
                                    freqHours.toString(),
                                    latest.slotIndex.toString(),
                                    itm.value,
                                    if (itm.abnormal) "异常" else "正常"
                                )
                                cells.forEachIndexed { idx, v -> r.createCell(idx).setCellValue(v) }
                                rowIdx++
                            }
                        }
                    } else {
                        // 未检：写一条占位行，itemValue 标为 未检，设备名留空
                        val dateOnly = sdfDate.format(Date(wStart))
                        val r = sheet.createRow(rowIdx)
                        val freqVal = freqByEquip[p.pointId] ?: 8
                        val cells = arrayOf(
                            dateOnly,
                            "",
                            currentRouteName, // routeName unknown when no session
                            "",
                            shiftNameFromWindowStart(wStart),
                            p.pointId,
                            p.name,
                            "",
                            "",
                            freqVal.toString(),
                            slotIdx.toString(),
                            "未检",
                            ""
                        )
                        cells.forEachIndexed { idx, v -> r.createCell(idx).setCellValue(v) }
                        rowIdx++
                    }
                }
            }
        }

        // 自适应列宽（上限保护）
        for (i in header.indices) {
            try { sheet.autoSizeColumn(i) } catch (_: Throwable) {}
            val w = sheet.getColumnWidth(i).coerceAtMost(80 * 256)
            sheet.setColumnWidth(i, w)
        }

        // 只读推荐
        if (readOnlyRecommended) {
            val ctWb = wb.ctWorkbook
            val fs = if (ctWb.isSetFileSharing) ctWb.fileSharing else ctWb.addNewFileSharing()
            fs.readOnlyRecommended = true
        }

        val curWindow = com.kukifyjeff.safepatrol.utils.ShiftUtils.currentShiftWindowMillis()
        val fileDate = sdfDate.format(Date(curWindow.startMs))
        val ym = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        val shiftForFile = shiftNameFromWindowStart(curWindow.startMs)
        val safeRoute = if (currentRouteName.isNotBlank()) currentRouteName.replace(Regex("[/:*?\"<>|]"), "_") else ""
        val filename = if (safeRoute.isNotBlank()) "SafePatrol_${safeRoute}_${ym}@${fileDate}_${shiftForFile}.xlsx" else "SafePatrol_${ym}@${fileDate}_${shiftForFile}.xlsx"

        // 保存到 App 私有目录（Documents），并根据是否加密进行处理
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val xlsx = File(outDir, filename)

        if (modifyPassword.isNotEmpty()) {
            // 先将工作簿写入内存
            val bos = java.io.ByteArrayOutputStream()
            wb.write(bos)
            wb.close()

            val pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(java.io.ByteArrayInputStream(bos.toByteArray()))
            val info = org.apache.poi.poifs.crypt.EncryptionInfo(org.apache.poi.poifs.crypt.EncryptionMode.standard)
            val encryptor = info.encryptor
            encryptor.confirmPassword(modifyPassword)

            // 使用 POIFSFileSystem 来生成加密 Excel
            val fs = org.apache.poi.poifs.filesystem.POIFSFileSystem()
            encryptor.getDataStream(fs).use { ds ->
                pkg.save(ds)
            }
            pkg.close()

            // 将加密后的 POIFS 写入文件
            xlsx.outputStream().use { fos ->
                fs.writeFilesystem(fos)
            }
            fs.close()
        } else {
            xlsx.outputStream().use { wb.write(it) }
            wb.close()
        }
        xlsx.absolutePath
    }
}
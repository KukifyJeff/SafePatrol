
package com.kukifyjeff.safepatrol.export

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import com.kukifyjeff.safepatrol.AppDatabase
import com.kukifyjeff.safepatrol.export.ExportUtil.exportFromLastTimeXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 最小 SXSSF 测试：验证 SXSSFWorkbook 是否会触发 FontRenderContext 错误
 * - 仅创建一个 sheet，写入一行一列，不设置 wrapText，不 autoSizeColumn
 * - 文件保存到 context.filesDir/test.xlsx
 * - 捕获异常并打印到 Logcat
 */

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
     * 导出指定时间范围内的所有点检记录（按时间顺序），返回生成文件的绝对路径。
     * @param startTs 导出起始毫秒（含）
     * @param endTs 导出终止毫秒（含）
     * @param modifyPassword 可选的结构修改密码
     * @param readOnlyRecommended 是否推荐只读
     */
    suspend fun exportFromLastTimeXlsx(
        context: Context,
        db: AppDatabase,
        startTs: Long,
        endTs: Long,
        modifyPassword: String = "",
    ): String = withContext(Dispatchers.IO) {
        // 查询时间窗口内所有实际记录（用于按 recordId 批量加载 items）
        val readOnlyRecommended = true
        val actualRecords =
            db.inspectionDao().getRecordsInWindow(startTs, endTs).sortedBy { it.timestamp }

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

        // 预加载每个点位的检查项名称映射 (itemId -> itemName)
        val itemNameByEquip = mutableMapOf<String, Map<String, String>>()
        for (p in points) {
            val cis = db.checkItemDao().getByEquipment(p.pointId)
            val m = cis.associateBy({ it.itemId }, { it.itemName })
            itemNameByEquip[p.pointId] = m
        }

        // 优化设备名逻辑：改为从 InspectionRecordItemEntity 的 equipmentId 获取
        val equipmentNameCache = mutableMapOf<String, String>()
        suspend fun getEquipmentName(equipmentId: String?): String {
            if (equipmentId.isNullOrBlank()) return ""
            return equipmentNameCache.getOrPut(equipmentId) {
                db.equipmentDao().getById(equipmentId)?.equipmentName ?: ""
            }
        }

        // 创建 Excel（单表）
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Monthly")
        sheet.createFreezePane(0, 1)

        val header = arrayOf(
            "日期",
            "时间",
            "路线名",
            "点检员",
            "值",
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
        header.forEachIndexed { i, t ->
            hr.createCell(i).apply { setCellValue(t); cellStyle = headStyle }
        }

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
        fun formatDateTimeForRecord(
            ts: Long,
            windowStart: Long,
            windowEnd: Long
        ): Pair<String, String> {
            val cTs = Calendar.getInstance().apply { timeInMillis = ts }
            // determine if window spans midnight (start day != end day)
            val cs = Calendar.getInstance().apply { timeInMillis = windowStart }
            val ce = Calendar.getInstance().apply { timeInMillis = windowEnd }
            val spansMidnight =
                cs.get(Calendar.DAY_OF_YEAR) != ce.get(Calendar.DAY_OF_YEAR) || cs.get(Calendar.YEAR) != ce.get(
                    Calendar.YEAR
                )

            if (spansMidnight && cTs.get(Calendar.HOUR_OF_DAY) == 0 && cTs.get(Calendar.MINUTE) < 30) {
                // treat as 24:MM on previous day
                val dateStr = sdfDate.format(Date(windowStart))
                val timeStr = String.format("24:%02d", cTs.get(Calendar.MINUTE))
                return Pair(dateStr, timeStr)
            }
            return Pair(sdfDate.format(Date(ts)), sdfTime.format(Date(ts)))
        }

        // 计算窗口：与月导出一致，按班次分割（夜班/白班/中班）
        data class ShiftWindow(
            val start: Long,
            val end: Long,
            val shiftName: String
        )

        val windows = mutableListOf<ShiftWindow>()
        val cal2 = Calendar.getInstance().apply { timeInMillis = startTs }
        val sdfFull = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        while (cal2.timeInMillis <= endTs) {
            val y = cal2.get(Calendar.YEAR)
            val m = cal2.get(Calendar.MONTH)
            val d = cal2.get(Calendar.DAY_OF_MONTH)

            fun msOf(hour: Int, minute: Int, dayOffset: Int = 0): Long {
                val c =
                    Calendar.getInstance().apply { clear(); set(y, m, d + dayOffset, hour, minute) }
                return c.timeInMillis
            }

            // 夜班：00:30 - 08:30 (same day)
            val s0 = msOf(0, 30)
            val e0 = msOf(8, 30)
            if (e0 >= startTs && s0 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s0.coerceAtLeast(startTs),
                        end = e0.coerceAtMost(endTs),
                        shiftName = "夜班"
                    )
                )
            }

            // 白班：08:30 - 16:30
            val s1 = msOf(8, 30)
            val e1 = msOf(16, 30)
            if (e1 >= startTs && s1 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s1.coerceAtLeast(startTs),
                        end = e1.coerceAtMost(endTs),
                        shiftName = "白班"
                    )
                )
            }

            // 中班：16:30 - 次日00:30
            val s2 = msOf(16, 30)
            val e2 = msOf(0, 30, 1)
            if (e2 >= startTs && s2 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s2.coerceAtLeast(startTs),
                        end = e2.coerceAtMost(endTs),
                        shiftName = "中班"
                    )
                )
            }

            cal2.add(Calendar.DAY_OF_MONTH, 1)
        }

        // ========= 基于最高频次 + 全槽位遍历 =========
        var rowIdx = 1

        // 1️⃣ 计算每个点位的最高频次
        val maxFreqByPoint = mutableMapOf<String, Int>()
        for (p in points) {
            // 按 point -> equipments -> checkitems 的关系计算该点的最高频次（即最短间隔）
            val equipments = db.equipmentDao().getByPoint(p.pointId)
            val allCheckItems =
                equipments.flatMap { eq -> db.checkItemDao().getByEquipment(eq.equipmentId) }
            val freq = if (allCheckItems.isNotEmpty()) allCheckItems.minOfOrNull { it.freqHours }
                ?: 8 else 8
            maxFreqByPoint[p.pointId] = freq
        }

        // 2️⃣ 获取窗口内所有记录
        val records = db.inspectionDao().getRecordsInWindow(startTs, endTs)
        val itemsByRecord = db.inspectionDao()
            .getItemsForRecordIds(records.map { it.recordId })
            .groupBy { it.recordId }

        // 3️⃣ 遍历所有时间段（window）、点位及槽位
        for (window in windows) {
            val windowStart = window.start
            val shiftName = window.shiftName
            Log.d("FuckExport", "Processing export window: date=${sdfDate.format(Date(windowStart))}, shift=$shiftName")
            val windowEnd = window.end

            // --- Export system logs that fall inside this window, before points ---
            val systemLogsInWindow = records.filter {
                it.pointId == "-1" && it.timestamp in windowStart..windowEnd
            }
            for (rec in systemLogsInWindow) {
                val items = itemsByRecord[rec.recordId].orEmpty()
                val (dateStr, timeStr) = formatDateTimeForRecord(
                    rec.timestamp,
                    windowStart,
                    windowEnd
                )
                val shiftNameRec = shiftName
                // Compute shiftValue using ShiftUtils.getValueOnShift
                val date = Calendar.getInstance().apply { timeInMillis = rec.timestamp }
                val localDate = java.time.LocalDate.of(
                    date.get(Calendar.YEAR),
                    date.get(Calendar.MONTH) + 1,
                    date.get(Calendar.DAY_OF_MONTH)
                )
                val shiftValue = com.kukifyjeff.safepatrol.utils.ShiftUtils.getValueOnShift(localDate, shiftName)

                if (items.isEmpty()) {
                    val r = sheet.createRow(rowIdx++)
                    val cells = arrayOf(
                        dateStr, timeStr,
                        sessionsMap[rec.sessionId]?.routeName ?: currentRouteName,
                        sessionsMap[rec.sessionId]?.operatorId ?: "",
                        shiftValue,
                        shiftName,
                        "", "", "",
                        "用户删除了冲突记录",
                        "0", "0",
                        "用户删除了冲突记录",
                        ""
                    )
                    cells.forEachIndexed { i, v -> r.createCell(i).setCellValue(v) }
                } else {
                    for (itm in items) {
                        val r = sheet.createRow(rowIdx++)
                        val cells = arrayOf(
                            dateStr, timeStr,
                            sessionsMap[rec.sessionId]?.routeName ?: currentRouteName,
                            sessionsMap[rec.sessionId]?.operatorId ?: "",
                            shiftValue,
                            shiftName,
                            "", "", "",
                            "用户删除了冲突记录",
                            "0", "0",
                            "用户删除了冲突记录",
                            ""
                        )
                        cells.forEachIndexed { i, v -> r.createCell(i).setCellValue(v) }
                    }
                }
            }

            for (p in points) {
                val freq = maxFreqByPoint[p.pointId] ?: 8
                val nSlots = when (freq) {
                    2 -> 4; 4 -> 2; 8 -> 1; else -> 1
                }
                for (slotIdx in 1..nSlots) {
                    // 查询该时间窗内该点位槽位的记录
                    val recsInWindow = db.inspectionDao()
                        .getRecordsForPointSlotInWindow(p.pointId, slotIdx, windowStart, windowEnd)
                    // 🚫 系统日志不参与任何点位/槽位的记录选择
                    val rec = recsInWindow.maxByOrNull { it.timestamp }

                    if (rec != null) {
                        val items = itemsByRecord[rec.recordId].orEmpty()
                        for (itm in items) {
                            val itemLabel =
                                db.checkItemDao().getItemNameById(itm.itemId) ?: itm.itemId
                            val freqHours = db.checkItemDao().getById(itm.itemId)?.freqHours ?: 8
                            val equipName = getEquipmentName(itm.equipmentId)
                            val (dateStr, timeStr) = formatDateTimeForRecord(
                                rec.timestamp,
                                windowStart,
                                windowEnd
                            )
                            val shiftNameRec =
                                sessionsMap[rec.sessionId]?.shiftId?.let { shiftIdToName(it) } ?: shiftName

                            // Compute shiftValue using ShiftUtils.getValueOnShift
                            val date = Calendar.getInstance().apply { timeInMillis = rec.timestamp }
                            val localDate = java.time.LocalDate.of(
                                date.get(Calendar.YEAR),
                                date.get(Calendar.MONTH) + 1,
                                date.get(Calendar.DAY_OF_MONTH)
                            )
                            val shiftValue = com.kukifyjeff.safepatrol.utils.ShiftUtils.getValueOnShift(localDate, shiftName)

                            val slotIdxForItem = when (freqHours) {
                                2 -> {
                                    val slotLen = ((windowEnd - windowStart) / 4.0)
                                    var idx = ((rec.timestamp - windowStart) / slotLen + 1).toInt()
                                    idx.coerceIn(1, 4)
                                }

                                4 -> {
                                    val slotLen = ((windowEnd - windowStart) / 2.0)
                                    var idx = ((rec.timestamp - windowStart) / slotLen + 1).toInt()
                                    idx.coerceIn(1, 2)
                                }

                                8 -> 1
                                else -> 1
                            }

                            val r = sheet.createRow(rowIdx++)
                            val cells = arrayOf(
                                dateStr,
                                timeStr,
                                sessionsMap[rec.sessionId]?.routeName ?: currentRouteName,
                                sessionsMap[rec.sessionId]?.operatorId ?: "",
                                shiftValue,
                                shiftName,
                                p.pointId,
                                p.name,
                                equipName,
                                itemLabel,
                                freqHours.toString(),
                                slotIdxForItem.toString(),
                                itm.value,
                                if (itm.abnormal) "异常" else "正常"
                            )
                            cells.forEachIndexed { i, v -> r.createCell(i).setCellValue(v) }
                        }
                    } else {
                        // 没有记录，输出未检行，包含日期和班次
                        val (dateStr, _) = formatDateTimeForRecord(
                            windowStart,
                            windowStart,
                            windowEnd
                        )
                        val shiftNameCell = shiftName
                        // 计算未检行的班次对应值
                        val dateForShift = Calendar.getInstance().apply { timeInMillis = windowStart }
                        val localDateForShift = java.time.LocalDate.of(
                            dateForShift.get(Calendar.YEAR),
                            dateForShift.get(Calendar.MONTH) + 1,
                            dateForShift.get(Calendar.DAY_OF_MONTH)
                        )
                        val shiftValue = com.kukifyjeff.safepatrol.utils.ShiftUtils.getValueOnShift(localDateForShift, shiftName)
                        val r = sheet.createRow(rowIdx++)
                        val cells = arrayOf(
                            dateStr, // 日期
                            "",      // 时间
                            currentRouteName,
                            "",      // 点检员
                            shiftValue,
                            shiftName, // 班次
                            p.pointId,
                            p.name,
                            "",      // 设备名
                            "",      // 点检项
                            freq.toString(),
                            slotIdx.toString(),
                            "未检",
                            ""       // 是否正常
                        )
                        cells.forEachIndexed { i, v -> r.createCell(i).setCellValue(v) }
                    }
                }
            }
        }

        // 固定列宽为 20（20*256）
        val fixedWidth = 20 * 256
        for (i in header.indices) {
            sheet.setColumnWidth(i, fixedWidth)
        }


        // 文件名格式：SafePatrol_路线_YYYYMMDD-HHMMSS至YYYYMMDD-HHMMSS.xlsx
        val sdfFile = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val safeRoute = if (currentRouteName.isNotBlank()) currentRouteName.replace(
            Regex("[/:*?\"<>|]"),
            "_"
        ) else ""
        val startStr = sdfFile.format(Date(startTs))
        val endStr = sdfFile.format(Date(endTs))
        val filename = if (safeRoute.isNotBlank())
            "点检结果_${safeRoute}_${startStr}至${endStr}.xlsx"
        else
            "点检结果_${startStr}至${endStr}.xlsx"

        // 保存到 App 私有目录（Documents），并根据是否加密进行处理
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val xlsx = File(outDir, filename)

        if (modifyPassword.isNotEmpty()) {
            // 先将工作簿写入内存
            val bos = java.io.ByteArrayOutputStream()
            wb.write(bos)
            wb.close() // 立即关闭释放内存

            val pkg =
                org.apache.poi.openxml4j.opc.OPCPackage.open(java.io.ByteArrayInputStream(bos.toByteArray()))
            val info =
                org.apache.poi.poifs.crypt.EncryptionInfo(org.apache.poi.poifs.crypt.EncryptionMode.standard)
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
            wb.close() // 立即关闭释放内存
        }
        // 不保留任何 Sheet、Row、Cell 或 Style 对象的引用
        // 仅返回路径，不返回wb对象
        xlsx.absolutePath
    }
}
    /**
     * 增量导出：从上次导出时间点开始，每3个班次生成一个XLSX文件，打包为ZIP，返回ZIP绝对路径。
     * 复用 exportFromLastTimeXlsx 的窗口计算和导出逻辑。
     */
    suspend fun exportIncrementalZipFromLastTime(
        context: Context,
        db: AppDatabase,
        startTs: Long,
        endTs: Long,
        modifyPassword: String = "",
    ): String = withContext(Dispatchers.IO) {
        // 1. 计算所有班次窗口（和 exportFromLastTimeXlsx 相同）
        data class ShiftWindow(
            val start: Long,
            val end: Long,
            val shiftName: String
        )
        val windows = mutableListOf<ShiftWindow>()
        val cal2 = Calendar.getInstance().apply { timeInMillis = startTs }
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        while (cal2.timeInMillis <= endTs) {
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
            if (e0 >= startTs && s0 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s0.coerceAtLeast(startTs),
                        end = e0.coerceAtMost(endTs) - 1,
                        shiftName = "夜班"
                    )
                )
            }
            // 白班：08:30 - 16:30
            val s1 = msOf(8, 30)
            val e1 = msOf(16, 30)
            if (e1 >= startTs && s1 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s1.coerceAtLeast(startTs),
                        end = e1.coerceAtMost(endTs) - 1,
                        shiftName = "白班"
                    )
                )
            }
            // 中班：16:30 - 次日00:30
            val s2 = msOf(16, 30)
            val e2 = msOf(0, 30, 1)
            if (e2 >= startTs && s2 <= endTs) {
                windows.add(
                    ShiftWindow(
                        start = s2.coerceAtLeast(startTs),
                        end = e2.coerceAtMost(endTs) - 1,
                        shiftName = "中班"
                    )
                )
            }
            cal2.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 2. 每3个班次为一组，生成xlsx文件
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val tempDir = File(outDir, "temp_export_zip_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val xlsxFiles = mutableListOf<File>()
        // 先获取当前路线名
        // 选择当前 route（优先取第一个窗口内记录的 session 的 route）
        val actualRecords =
            db.inspectionDao().getRecordsInWindow(startTs, endTs).sortedBy { it.timestamp }
        // 预取 session 信息（按 sessionId）
        val sessionIds = actualRecords.map { it.sessionId }.distinct()
        val sessionsMap = if (sessionIds.isEmpty()) emptyMap()
            else db.inspectionDao().getSessionsByIds(sessionIds).associateBy { it.sessionId }
        val currentRouteName: String = sessionsMap.values.firstOrNull()?.routeName ?: ""
        val safeRoute = if (currentRouteName.isNotBlank()) currentRouteName.replace(
            Regex("[/:*?\"<>|]"),
            "_"
        ) else ""
        val sdfFile = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        var i = 0
        while (i < windows.size) {
            val group = windows.subList(i, minOf(i + 3, windows.size))
            val groupStart = group.first().start
            val groupEnd = group.last().end
            // 日志输出每个班次
            for (w in group) {
                Log.d("FuckExport", "窗口组: date=${sdfDate.format(Date(w.start))}, shift=${w.shiftName}")
            }
            val startStr = sdfFile.format(Date(groupStart))
            val endStr = sdfFile.format(Date(groupEnd))
            val xlsxFile = File(tempDir, if (safeRoute.isNotBlank())
                "点检结果_${safeRoute}_${startStr}至${endStr}.xlsx"
            else
                "点检结果_${startStr}至${endStr}.xlsx")
            // 调用单窗口导出逻辑，exportFromLastTimeXlsx保证wb已关闭且不返回Workbook对象
            val xlsxPath = exportFromLastTimeXlsx(
                context = context,
                db = db,
                startTs = groupStart,
                endTs = groupEnd,
                modifyPassword = modifyPassword
            )
            // 拷贝到临时目录（因为 exportFromLastTimeXlsx 直接写到 outDir）
            val srcFile = File(xlsxPath)
            srcFile.copyTo(xlsxFile, overwrite = true)
            xlsxFiles.add(xlsxFile)
            // 立即释放本地变量引用
            // srcFile 不再使用，确保无 Workbook 对象保留
            Log.d("FuckExport", "生成XLSX: ${xlsxFile.absolutePath}")
            i += 3
        }

        // 3. 打包为zip
        // zip 文件名同样使用 safeRoute, startStr, endStr（取整个导出范围）
        val zipStartStr = sdfFile.format(Date(windows.firstOrNull()?.start ?: startTs))
        val zipEndStr = sdfFile.format(Date(windows.lastOrNull()?.end ?: endTs))
        val zipFile = File(outDir, if (safeRoute.isNotBlank())
            "点检结果_${safeRoute}_${zipStartStr}至${zipEndStr}.zip"
        else
            "点检结果_${zipStartStr}至${zipEndStr}.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            for (file in xlsxFiles) {
                val entry = java.util.zip.ZipEntry(file.name)
                zos.putNextEntry(entry)
                file.inputStream().use { ins ->
                    ins.copyTo(zos)
                }
                zos.closeEntry()
            }
        }

        // 4. 清理临时XLSX
        for (f in xlsxFiles) {
            try { f.delete() } catch (_: Throwable) {}
        }
        try { tempDir.delete() } catch (_: Throwable) {}

        zipFile.absolutePath
    }
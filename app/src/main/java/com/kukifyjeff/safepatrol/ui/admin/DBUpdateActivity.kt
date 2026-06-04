package com.kukifyjeff.safepatrol.ui.admin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kukifyjeff.safepatrol.R
import com.kukifyjeff.safepatrol.AppDatabase
import kotlin.system.exitProcess
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DBUpdateActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null
    private var tempDbFile: File? = null
    private var lastBackupFile: File? = null

    private lateinit var tvFilePath: TextView
    private lateinit var svStatus: ScrollView
    private lateinit var tvStatus: TextView

    private val PICK_DB_FILE = 1001
    private val DB_NAME = "safe_patrol.db"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_db_update)

        tvFilePath = findViewById(R.id.tvFilePath)
        svStatus = findViewById(R.id.svStatus)
        tvStatus = findViewById(R.id.tvStatus)

        val btnSelectDb = findViewById<Button>(R.id.btnSelectDb)
        val btnValidate = findViewById<Button>(R.id.btnValidate)
        val btnImport = findViewById<Button>(R.id.btnImport)

        btnSelectDb.setOnClickListener {
            pickDatabaseFile()
        }

        btnValidate.setOnClickListener {
            val file = tempDbFile
            if (file == null) {
                toast("请先选择数据库文件")
                return@setOnClickListener
            }
            val ok = validateDatabase(file)
            appendStatus(if (ok) "✅ 校验通过" else "❌ 校验失败")
        }

        btnImport.setOnClickListener {
            val file = tempDbFile
            if (file == null) {
                toast("请先选择数据库文件")
                return@setOnClickListener
            }

            // 1️⃣ 校验
            if (!validateDatabase(file)) {
                appendStatus("❌ 导入失败：数据库校验未通过")
                return@setOnClickListener
            }

            try {
                appendStatus("🔄 正在关闭数据库连接...")
                AppDatabase.destroyInstance()

                appendStatus("💾 创建备份...")
                createBackup()

                appendStatus("📦 替换数据库文件...")
                replaceDatabaseSafe(file)

                appendStatus("♻️ 正在重启应用以应用新数据库...")

                toast("导入成功，正在重启应用")

                restartApp()

            } catch (e: Exception) {
                appendStatus("❌ 替换失败: ${e.message}")
                rollbackDatabase()
            }
        }
    }

    private fun pickDatabaseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_DB_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_DB_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri

            val name = getFileName(uri)
            tvFilePath.text = name ?: "已选择文件"

            val tempFile = File(cacheDir, "import.db")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempDbFile = tempFile
            appendStatus("📦 文件已加载")
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    private fun appendStatus(msg: String) {
        runOnUiThread {
            tvStatus.append("\n$msg")

            svStatus.post {
                svStatus.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun getSchema(db: android.database.sqlite.SQLiteDatabase): Map<String, List<String>> {
        val tables = mutableListOf<String>()

        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        val result = mutableMapOf<String, List<String>>()

        for (table in tables) {
            val cols = mutableListOf<String>()

            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    cols.add(cursor.getString(1))
                }
            }

            result[table] = cols
        }

        return result
    }

    // =========================
    // 🔥 Stage 4: Backup System
    // =========================

    private fun createBackup() {
        val dbFile = getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            appendStatus("⚠️ 无旧数据库，跳过备份")
            return
        }

        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backup = File(dbFile.parentFile, "backup_${time}.db")

        dbFile.copyTo(backup, overwrite = true)
        lastBackupFile = backup

        appendStatus("💾 已备份数据库: ${backup.name}")
    }

    private fun rollbackDatabase() {
        val backup = lastBackupFile
        if (backup == null || !backup.exists()) {
            appendStatus("❌ 无可用备份，无法回滚")
            return
        }

        val dbFile = getDatabasePath(DB_NAME)
        backup.copyTo(dbFile, overwrite = true)

        appendStatus("🔄 已回滚到备份: ${backup.name}")
        toast("已回滚数据库")
    }

    // =========================
    // 🔥 Safe Replace (Stage 4)
    // =========================

    private fun replaceDatabaseSafe(newDb: File) {
        val dbFile = getDatabasePath(DB_NAME)

        val parent = dbFile.parentFile
        if (parent?.exists() == false) parent.mkdirs()

        val tempTarget = File(parent, "temp_$DB_NAME")

        // 先写入临时文件
        newDb.copyTo(tempTarget, overwrite = true)

        // 删除旧库
        if (dbFile.exists()) {
            dbFile.delete()
        }

        // 原子替换
        val renamed = tempTarget.renameTo(dbFile)
        if (!renamed) {
            throw RuntimeException("数据库替换失败（rename失败）")
        }
    }

    // =========================
    // Validation (unchanged core logic)
    // =========================

    private fun validateDatabase(file: File): Boolean {
        return try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            appendStatus("📊 === SCHEMA DIFF START ===")

            // import database schema (uploaded file)
            val importSchema = getSchema(db)

            // current app database schema
            val appDbFile = getDatabasePath(DB_NAME)
            val appDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                appDbFile.path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            val appSchema = getSchema(appDb)
            appDb.close()

            val allTables = (importSchema.keys + appSchema.keys).toSet()

            for (table in allTables) {

                val importCols = importSchema[table]
                val appCols = appSchema[table]

                if (importCols == null) {
                    appendStatus("❌ 多余表(上传存在App不存在): $table")
                    continue
                }

                if (appCols == null) {
                    appendStatus("⚠️ 新增表(App不存在): $table")
                    continue
                }

                val missing = appCols - importCols.toSet()
                val extra = importCols - appCols.toSet()

                if (missing.isEmpty() && extra.isEmpty()) {
                    appendStatus("✅ $table 表 表头未变化")
                } else {
                    appendStatus("⚠️ $table 表 表头有变化")

                    if (missing.isNotEmpty()) {
                        appendStatus("   - 上传数据库缺失字段: ${missing.joinToString()}")
                    }

                    if (extra.isNotEmpty()) {
                        appendStatus("   - 上传数据库新增字段: ${extra.joinToString()}")
                    }
                }
            }

            appendStatus("📊 === SCHEMA DIFF END ===")

            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
                null
            ).use { cursor ->
                val list = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    list.add(cursor.getString(0))
                }
                list
            }

            for (table in tables) {
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table)
                )
                if (cursor.count == 0) {
                    cursor.close()
                    db.close()
                    return false
                }
                cursor.close()
            }

            val integrityCursor = db.rawQuery("PRAGMA integrity_check", null)
            if (integrityCursor.moveToFirst()) {
                val result = integrityCursor.getString(0)
                if (result != "ok") {
                    integrityCursor.close()
                    db.close()
                    return false
                }
            }
            integrityCursor.close()

            val checks = listOf(
                "SELECT COUNT(*) FROM points p LEFT JOIN routes r ON p.routeId = r.routeId WHERE r.routeId IS NULL",
                "SELECT COUNT(*) FROM equipments e LEFT JOIN points p ON e.pointId = p.pointId WHERE p.pointId IS NULL",
                "SELECT COUNT(*) FROM check_items c LEFT JOIN equipments e ON c.equipmentId = e.equipmentId WHERE e.equipmentId IS NULL"
            )

            for (sql in checks) {
                val cursor = db.rawQuery(sql, null)
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    if (count > 0) {
                        cursor.close()
                        db.close()
                        return false
                    }
                }
                cursor.close()
            }

            db.close()
            true
        } catch (e: Exception) {
            appendStatus("❌ validateDatabase exception: ${e.message}")
            e.printStackTrace()
            false
        }
    }
private fun restartApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    intent?.addFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
    )
    startActivity(intent)
    finish()
    exitProcess(0)
}
}
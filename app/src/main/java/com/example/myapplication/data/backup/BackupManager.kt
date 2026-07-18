package com.example.myapplication.data.backup

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.store.FileStore
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份管理：把全部用户数据（配置/对话/记忆/Skills/子代理/工作区文件）打成 zip，
 * 通过 SAF 导出到用户选择的位置，或从 zip 导入恢复（导入前自动备份当前数据）。
 */
class BackupManager(
    private val appContext: Context,
    private val store: FileStore
) {
    /** 参与备份的顶层条目（相对 filesDir） */
    private val entries = listOf(
        "config.json", "agents.json",
        "conversations", "memory", "skills", "workspace"
    )

    private val root: File get() = store.configFile.parentFile!!

    /** 导出到 SAF Uri。返回写入的文件数。 */
    fun exportTo(uri: Uri): Int {
        var count = 0
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                for (name in entries) {
                    val f = File(root, name)
                    if (!f.exists()) continue
                    if (f.isFile) {
                        addFile(zip, f, name)
                        count++
                    } else {
                        f.walkTopDown().filter { it.isFile }.forEach { file ->
                            val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                            addFile(zip, file, rel)
                            count++
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("无法打开导出位置")
        return count
    }

    /**
     * 从 SAF Uri 导入。先把当前数据备份到 filesDir/backups/backup-<时间戳>.zip，再覆盖。
     * 返回导入的文件数。
     */
    fun importFrom(uri: Uri): Int {
        // 1. 自动备份当前数据
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backupFile = File(store.backupsDir, "backup-$stamp.zip")
        ZipOutputStream(backupFile.outputStream().buffered()).use { zip ->
            for (name in entries) {
                val f = File(root, name)
                if (!f.exists()) continue
                if (f.isFile) addFile(zip, f, name)
                else f.walkTopDown().filter { it.isFile }.forEach { file ->
                    addFile(zip, file, file.relativeTo(root).path.replace(File.separatorChar, '/'))
                }
            }
        }

        // 2. 清空现有数据目录
        for (name in entries) File(root, name).deleteRecursively()

        // 3. 解压（带 Zip Slip 防护）
        var count = 0
        val rootCanonical = root.canonicalFile
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(root, entry.name).canonicalFile
                        if (outFile.path.startsWith(rootCanonical.path + File.separator)) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { it.write(zip.readBytes()) }
                            count++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("无法读取导入文件")
        store.init() // 重建目录结构
        return count
    }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "agent-backup-$stamp.zip"
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

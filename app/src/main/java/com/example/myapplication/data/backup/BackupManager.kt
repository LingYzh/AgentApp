package com.example.myapplication.data.backup

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.store.FileStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SAF adapter; portable archive and staged restore are also testable without an Android device. */
class BackupManager(private val appContext: Context, private val store: FileStore) {
    private val archive = FullBackupArchive(store)

    data class ImportResult(val fileCount: Int, val missingAvatars: Int)

    fun exportTo(uri: Uri): Int =
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { archive.export(it) }
            ?: throw IllegalStateException("无法打开导出位置")

    fun importFrom(uri: Uri): ImportResult =
        appContext.contentResolver.openInputStream(uri)?.use {
            val result = archive.restore(it)
            ImportResult(result.fileCount, result.missingAvatars)
        } ?: throw IllegalStateException("无法读取导入文件")

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "agent-backup-$stamp.zip"
    }
}

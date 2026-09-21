package com.example.myapplication.data.backup

import com.example.myapplication.data.store.FileStore
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 导出工作区中用户选定的文件。
 *
 * 所有输入路径都经过 FileStore.workspaceFile() 校验，并以 workspace 根目录为
 * ZIP 相对路径，避免把用户输入直接作为 ZIP entry 名称造成越界路径。
 */
object SelectedFilesExport {

    /** 将选定的工作区文件写入输出流；输出流由 ZIP 写入器关闭。 */
    fun writeWorkspaceZip(
        store: FileStore,
        paths: Set<String>,
        output: OutputStream
    ) {
        require(paths.isNotEmpty()) { "未选择工作区文件" }

        val root = store.workspaceDir.canonicalFile
        val rootPrefix = root.path + File.separator
        val files = paths.map { path ->
            val file = store.workspaceFile(path)
            require(file.isFile) { "工作区文件不存在或不是文件: $path" }
            require(file.path.startsWith(rootPrefix)) { "工作区文件路径越界: $path" }
            val relativePath = file.path
                .removePrefix(rootPrefix)
                .replace(File.separatorChar, '/')
            require(relativePath.isNotBlank()) {
                "工作区文件路径无效: $path"
            }
            relativePath to file
        }
            // Different spellings can resolve to the same canonical file. Export it once.
            .distinctBy { it.first }
            .sortedBy { it.first }

        require(files.isNotEmpty()) { "未找到可导出的工作区文件" }

        ZipOutputStream(output).use { zip ->
            files.forEach { (relativePath, file) ->
                zip.putNextEntry(ZipEntry(relativePath))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

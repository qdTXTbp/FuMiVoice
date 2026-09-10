package com.fumi.voice.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log

/**
 * 通过 SAF（Storage Access Framework）读取用户选中的目录。
 *
 * 走 SAF 而不是直接读 /sdcard，是为了绕开 Android 11+ 的分区存储限制——
 * 应用无需申请任何存储权限，只能访问用户明确授权的那棵目录树。
 */
object DocumentTreeScanner {

    private const val TAG = "DocumentTreeScanner"

    /**
     * 递归扫描目录树，返回扩展名匹配的文件。
     *
     * @param extensions 小写扩展名（不含点），例如 listOf("sf2")
     */
    fun scan(context: Context, treeUri: Uri, extensions: List<String>, maxDepth: Int = 3): List<Uri> {
        val results = mutableListOf<Uri>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return results
        val wanted = extensions.map { it.lowercase() }

        fun scanDir(documentId: String, level: Int) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        val mime = cursor.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (level < maxDepth) scanDir(childId, level + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in wanted) {
                                results.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                            }
                        }
                    }
                }
            }.onFailure { Log.e(TAG, "扫描目录失败: $childrenUri", it) }
        }

        scanDir(rootId, 0)
        return results
    }

    /** 查询 SAF 文档的显示名。 */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}

package com.camlauncher.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object StorageHelper {
    /** Creates a file in the user-selected SAF directory */
    fun createSafFile(context: Context, treeUriStr: String, mimeType: String, displayName: String): Uri? {
        return try {
            val treeUri = Uri.parse(treeUriStr)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            DocumentsContract.createDocument(context.contentResolver, docUri, mimeType, displayName)
        } catch (e: Exception) {
            println("StorageHelper: Error creating SAF file: ${e.message}")
            null
        }
    }

    /** Safely deletes a file whether it's SAF or MediaStore */
    fun deleteMedia(context: Context, uri: Uri) {
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Resolves the display name of a SAF directory tree */
    fun getFolderName(context: Context, treeUriStr: String): String {
        return try {
            val treeUri = Uri.parse(treeUriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            
            context.contentResolver.query(documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            } ?: "Selected Folder"
        } catch (e: Exception) {
            "Custom Folder"
        }
    }
}

package com.otakeeesen.byebyemoneylist.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.serialization.json.Json
import java.io.IOException

class SyncFolderRepository(
    private val context: Context,
    internal val prefs: PreferencesManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isFolderSet(): Boolean = prefs.getSharedFolderUri() != null

    fun getFolderUri(): Uri? = prefs.getSharedFolderUri()?.let { Uri.parse(it) }

    fun getFolderDisplayName(): String? {
        val uri = getFolderUri() ?: return null
        val docFile = DocumentFile.fromTreeUri(context, uri) ?: return null
        return docFile.name ?: uri.lastPathSegment
    }

    fun clearFolder() {
        prefs.setSharedFolderUri(null)
    }

    fun persistFolderUri(uri: Uri) {
        prefs.setSharedFolderUri(uri.toString())
    }

    fun listSyncFiles(): List<SyncFileMeta> {
        val uri = getFolderUri() ?: return emptyList()
        return try {
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
            rootDir.listFiles()
                .filter { it.name?.endsWith(".bbl.json") == true }
                .mapNotNull { file ->
                    val name = file.name ?: return@mapNotNull null
                    SyncFileMeta(name, file.lastModified())
                }
        } catch (e: SecurityException) {
            prefs.setSharedFolderUri(null)
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun readFile(fileName: String): SyncListDto? {
        val uri = getFolderUri() ?: return null
        return try {
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return null
            val file = rootDir.findFile(fileName) ?: return null
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                json.decodeFromString<SyncListDto>(content)
            }
        } catch (e: SecurityException) {
            prefs.setSharedFolderUri(null)
            null
        } catch (e: Exception) {
            null
        }
    }

    fun writeFile(syncId: String, dto: SyncListDto): Boolean {
        val uri = getFolderUri() ?: return false
        val fileName = "$syncId.bbl.json"
        return try {
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return false
            val content = json.encodeToString(SyncListDto.serializer(), dto)
            val existingFile = rootDir.findFile(fileName)
            val targetUri = if (existingFile != null) {
                existingFile.uri
            } else {
                val created = rootDir.createFile("application/json", fileName)
                created?.uri ?: return false
            }
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: SecurityException) {
            prefs.setSharedFolderUri(null)
            false
        } catch (e: Exception) {
            false
        }
    }

    fun deleteFile(syncId: String): Boolean {
        val uri = getFolderUri() ?: return false
        return try {
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return false
            val file = rootDir.findFile("$syncId.bbl.json")
            file?.delete() ?: true
        } catch (e: SecurityException) {
            prefs.setSharedFolderUri(null)
            false
        } catch (e: Exception) {
            false
        }
    }
}

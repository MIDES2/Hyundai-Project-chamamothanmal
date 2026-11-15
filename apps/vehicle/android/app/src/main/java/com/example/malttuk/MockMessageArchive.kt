package com.example.malttuk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

internal class MockMessageArchive(context: Context) {

    data class Entry(
        val messageId: String,
        val title: String,
        val completedAt: Long,
        val permanent: Boolean = false,
        val reaction: String? = null,
        val deleted: Boolean = false
    ) {
        fun expiresAt(): Long = completedAt + RETENTION_MILLIS

        fun isExpired(now: Long): Boolean =
            !permanent && now >= expiresAt()
    }

    private val appContext = context.applicationContext
    private val directory =
        File(appContext.noBackupFilesDir, "mock-message-archive")

    private val mainHandler = Handler(Looper.getMainLooper())

    fun saveCompleted(
        messageId: String,
        title: String,
        rawResourceId: Int,
        callback: (Result<Unit>) -> Unit
    ) {
        runIo(
            action = {
                ensureDirectory()
                val existing = readEntry(messageId)

                if (existing?.deleted == true) {
                    throw IOException("삭제한 메시지는 다시 보관할 수 없습니다")
                }

                if (existing != null) {
                    if (!audioFile(messageId).isFile) {
                        throw IOException("보관 음성 파일을 찾을 수 없습니다")
                    }
                } else {
                    val audio = AtomicFile(audioFile(messageId))
                    val output = audio.startWrite()

                    try {
                        appContext.resources.openRawResource(rawResourceId).use {
                                input -> input.copyTo(output)
                        }
                        audio.finishWrite(output)
                    } catch (error: Exception) {
                        audio.failWrite(output)
                        throw error
                    }

                    writeEntry(
                        Entry(
                            messageId = messageId,
                            title = title,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }

                Unit
            },
            callback = callback
        )
    }

    fun openAudio(
        messageId: String,
        callback: (Result<ParcelFileDescriptor>) -> Unit
    ) {
        runIo(
            action = {
                requireActiveEntry(messageId)

                val file = audioFile(messageId)
                if (!file.isFile) {
                    throw IOException("보관 음성 파일을 찾을 수 없습니다")
                }

                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            },
            callback = callback
        )
    }

    fun setReaction(
        messageId: String,
        reaction: String,
        callback: (Result<Unit>) -> Unit
    ) {
        runIo(
            action = {
                val entry = requireActiveEntry(messageId)
                writeEntry(entry.copy(reaction = reaction))
            },
            callback = callback
        )
    }

    fun keepPermanently(
        messageId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        runIo(
            action = {
                val entry = requireActiveEntry(messageId)
                writeEntry(entry.copy(permanent = true))
            },
            callback = callback
        )
    }

    fun delete(
        messageId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        runIo(
            action = {
                val entry = readEntry(messageId)
                if (entry != null) {
                    removeEntry(entry)
                }
                Unit
            },
            callback = callback
        )
    }

    fun listActive(callback: (Result<List<Entry>>) -> Unit) {
        runIo(
            action = {
                ensureDirectory()

                val files = directory.listFiles()
                    ?: throw IOException("보관함 목록을 읽을 수 없습니다")

                val ids = files.mapNotNull { file ->
                    when {
                        file.name.endsWith(".json") ->
                            file.name.removeSuffix(".json")

                        file.name.endsWith(".json.bak") ->
                            file.name.removeSuffix(".json.bak")

                        else -> null
                    }
                }.distinct()

                val now = System.currentTimeMillis()
                val entries = mutableListOf<Entry>()

                for (id in ids) {
                    val entry = readEntry(id) ?: continue

                    if (entry.deleted || entry.isExpired(now)) {
                        removeEntry(entry)
                    } else {
                        if (!audioFile(id).isFile) {
                            throw IOException("보관 음성 파일이 없습니다")
                        }
                        entries.add(entry)
                    }
                }

                entries.sortedByDescending { it.completedAt }
            },
            callback = callback
        )
    }

    private fun requireActiveEntry(messageId: String): Entry {
        val entry = readEntry(messageId)
            ?: throw IOException("보관된 메시지가 없습니다")

        if (entry.deleted || entry.isExpired(System.currentTimeMillis())) {
            removeEntry(entry)
            throw IOException("삭제되었거나 보관 기간이 지난 메시지입니다")
        }

        return entry
    }

    private fun readEntry(messageId: String): Entry? {
        val file = metadataFile(messageId)

        if (!file.exists() && !File(file.path + ".bak").exists()) {
            return null
        }

        val json = JSONObject(
            AtomicFile(file).readFully().toString(Charsets.UTF_8)
        )

        return Entry(
            messageId = messageId,
            title = json.getString("title"),
            completedAt = json.getLong("completedAt"),
            permanent = json.optBoolean("permanent", false),
            reaction = if (json.isNull("reaction")) {
                null
            } else {
                json.getString("reaction")
            },
            deleted = json.optBoolean("deleted", false)
        )
    }

    private fun writeEntry(entry: Entry) {
        ensureDirectory()

        val json = JSONObject().apply {
            put("title", entry.title)
            put("completedAt", entry.completedAt)
            put("permanent", entry.permanent)
            put("reaction", entry.reaction ?: JSONObject.NULL)
            put("deleted", entry.deleted)
        }

        val file = AtomicFile(metadataFile(entry.messageId))
        val output = file.startWrite()

        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun removeEntry(entry: Entry) {
        // 삭제 상태를 먼저 저장하여 중간에 실패해도 다시 노출하지 않는다.
        writeEntry(entry.copy(deleted = true))
        deleteAtomicFile(audioFile(entry.messageId))
        deleteAtomicFile(metadataFile(entry.messageId))
    }

    private fun deleteAtomicFile(file: File) {
        AtomicFile(file).delete()

        val remaining = listOf(
            file,
            File(file.path + ".bak"),
            File(file.path + ".new")
        ).any { it.exists() }

        if (remaining) {
            throw IOException("보관 파일을 삭제하지 못했습니다")
        }
    }

    private fun metadataFile(messageId: String): File {
        require(messageId.matches(Regex("[A-Za-z0-9-]+")))
        return File(directory, "$messageId.json")
    }

    private fun audioFile(messageId: String): File {
        require(messageId.matches(Regex("[A-Za-z0-9-]+")))
        return File(directory, "$messageId.wav")
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("보관함 폴더를 만들 수 없습니다")
        }
    }

    private fun <T> runIo(
        action: () -> T,
        callback: (Result<T>) -> Unit
    ) {
        ioExecutor.execute {
            val result = runCatching(action)
            mainHandler.post { callback(result) }
        }
    }

    companion object {
        private const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val ioExecutor = Executors.newSingleThreadExecutor()
    }
}

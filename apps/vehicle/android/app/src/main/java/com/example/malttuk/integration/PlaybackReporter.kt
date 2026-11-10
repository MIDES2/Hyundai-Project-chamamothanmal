package com.example.malttuk.integration

import java.util.ArrayDeque
import java.util.UUID

/** Use on one serial worker. Retries retain the exact event identity and expected version. */
class PlaybackReporter(initialVersion: Long, initialStatus: String,
    private val send: (Report) -> Long) {
    data class Report(val eventId: String, val status: String, val expectedVersion: Long,
                      val source: String, val reason: String?)
    private data class Pending(val status: String, val source: String, val reason: String?, var report: Report? = null)
    private val queue = ArrayDeque<Pending>()
    var version = initialVersion; private set
    var status = initialStatus; private set
    @Volatile var blocked = false; private set
    fun record(status: String, source: String, reason: String? = null) {
        queue.addLast(Pending(status, source, reason)); flush()
    }
    fun flush() {
        while(queue.isNotEmpty()) {
            val next = queue.first
            if(next.report == null && next.status == status) { queue.removeFirst(); continue }
            check(status !in setOf("COMPLETED", "SKIPPED")) { "Playback is terminal" }
            val report = next.report ?: Report(UUID.randomUUID().toString(), next.status, version, next.source, next.reason).also { next.report = it }
            try {
                val acceptedVersion = send(report)
                check(acceptedVersion == report.expectedVersion + 1) { "Unexpected playback version" }
                version = acceptedVersion; status = report.status; queue.removeFirst(); blocked = false
            } catch(error: Exception) { blocked = true; throw error }
        }
    }
}

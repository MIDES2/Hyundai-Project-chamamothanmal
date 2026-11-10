package com.example.malttuk.integration

import org.junit.Assert.*
import org.junit.Test

class PlaybackReporterTest {
    @Test fun lostAcknowledgementRetriesSameEventBeforeNextEvent() {
        val seen = mutableListOf<PlaybackReporter.Report>(); var dropAck = true
        val reporter = PlaybackReporter(0, "PENDING") { report ->
            seen += report
            if (dropAck) { dropAck = false; throw java.io.IOException("lost response") }
            report.expectedVersion + 1
        }
        try { reporter.record("PLAYING", "MOCK", "USER_ACTION"); fail("should fail") } catch (_: java.io.IOException) { }
        assertTrue(reporter.blocked)
        reporter.record("COMPLETED", "MOCK")
        assertEquals(seen[0], seen[1])
        assertEquals(0L, seen[1].expectedVersion)
        assertEquals("COMPLETED", seen[2].status)
        assertEquals(1L, seen[2].expectedVersion)
        assertEquals(2L, reporter.version)
        assertFalse(reporter.blocked)
    }
    @Test fun sourceIsPreservedAndRepeatedHoldDoesNotCreateInvalidTransition() {
        val seen = mutableListOf<PlaybackReporter.Report>()
        val reporter = PlaybackReporter(0, "PENDING") { seen += it; it.expectedVersion + 1 }
        reporter.record("HELD", "MOCK", "DOOR_OPEN")
        reporter.record("HELD", "MOCK", "DOOR_OPEN")
        assertEquals(1, seen.size); assertEquals("MOCK", seen.single().source)
        assertEquals("DOOR_OPEN", seen.single().reason)
    }
    @Test fun terminalDeliveryCannotBeStartedAgain() {
        var calls = 0
        val reporter = PlaybackReporter(2, "COMPLETED") { calls++; it.expectedVersion + 1 }
        try { reporter.record("PLAYING", "SDK"); fail("terminal must block") } catch (_: IllegalStateException) { }
        assertEquals(0, calls)
    }
    @Test fun versionConflictIsNotSilentlySkipped() {
        var first: PlaybackReporter.Report? = null
        val reporter = PlaybackReporter(3, "HELD") {
            if (first == null) first = it else assertEquals(first, it)
            throw IllegalStateException("conflict")
        }
        repeat(2) { try { if (it == 0) reporter.record("PLAYING", "SDK") else reporter.flush() } catch (_: IllegalStateException) { } }
        assertEquals(3L, reporter.version); assertTrue(reporter.blocked)
    }
}

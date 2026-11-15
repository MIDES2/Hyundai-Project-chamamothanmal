package com.example.malttuk.integration

import org.junit.Assert.*
import org.junit.Test

class ListeningPolicyTest {
    @Test fun eitherPartyCanRequireConfirmationAndUnknownModesNeverAutoplay() {
        assertFalse(ListeningPolicy.requiresConfirmation("TOGETHER",false))
        assertTrue(ListeningPolicy.requiresConfirmation("TOGETHER",true))
        assertTrue(ListeningPolicy.requiresConfirmation("PRIVATE",false))
        assertTrue(ListeningPolicy.requiresConfirmation("PRIVATE",true))
        assertTrue(ListeningPolicy.requiresConfirmation(null,false))
        assertTrue(ListeningPolicy.requiresConfirmation("UNKNOWN",false))
    }
}

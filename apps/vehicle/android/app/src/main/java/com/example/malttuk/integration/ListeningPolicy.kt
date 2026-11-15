package com.example.malttuk.integration

/** A sender or driver confirmation requirement can never be overridden by automatic mode. */
object ListeningPolicy {
    fun requiresConfirmation(senderMode: String?, driverRequiresConfirmation: Boolean): Boolean =
        senderMode != "TOGETHER" || driverRequiresConfirmation
}

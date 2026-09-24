package com.example.myapplication.agent

import kotlinx.serialization.json.JsonObject
import java.io.File

/** One model run's device access. Android implementation lives behind this testable boundary. */
interface DeviceToolSession {
    val enabled: Boolean
    suspend fun execute(name: String, args: JsonObject, attach: ((File) -> String?)?): String
    fun close()
}

/** A conversation owns the screen until its last participating run finishes. */
class DeviceLease {
    private var owner: String? = null
    private val holders = mutableSetOf<String>()

    @Synchronized
    fun acquire(conversationId: String, holder: String): Boolean {
        if (owner != null && owner != conversationId) return false
        owner = conversationId
        holders += holder
        return true
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        if (holders.isEmpty()) owner = null
    }
}

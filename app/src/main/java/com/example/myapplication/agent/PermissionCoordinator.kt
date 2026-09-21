package com.example.myapplication.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class PermissionRequestKind { COMMAND, PLAN, FILE_DELETE }

enum class PermissionDecision { DENY, ALLOW_ONCE, ALLOW_ALWAYS, FEEDBACK, ACCEPT_AUTO, ACCEPT_EDIT }

data class PermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val kind: PermissionRequestKind,
    val command: String = "",
    val workingDirectory: String = "",
    val planText: String = "",
    val planPath: String = "",
    val canAlwaysAllow: Boolean = false,
    val filePath: String = ""
)

data class PermissionResolution(val decision: PermissionDecision, val feedback: String)

/** UI-independent rendezvous for permission dialogs. Cancellation always removes its row. */
class PermissionCoordinator {
    private val lock = Any()
    private val waiting = linkedMapOf<String, CompletableDeferred<PermissionResolution>>()
    private val _pending = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val pending: StateFlow<List<PermissionRequest>> = _pending.asStateFlow()

    suspend fun request(request: PermissionRequest): PermissionResolution {
        com.example.myapplication.diagnostics.RuntimeDiagnostics.event("approval_requested",
            "conversation" to request.conversationId, "kind" to request.kind, "requestId" to request.id)
        val deferred = CompletableDeferred<PermissionResolution>()
        synchronized(lock) {
            waiting[request.id] = deferred
            _pending.value = _pending.value + request
        }
        try {
            return deferred.await()
        } catch (e: CancellationException) {
            throw e
        } finally {
            synchronized(lock) {
                waiting.remove(request.id)
                _pending.value = _pending.value.filterNot { it.id == request.id }
            }
        }
    }

    fun resolve(id: String, decision: PermissionDecision, feedback: String = ""): Boolean {
        return synchronized(lock) {
            val target = waiting.remove(id) ?: return@synchronized false
            com.example.myapplication.diagnostics.RuntimeDiagnostics.event("approval_resolved",
                "requestId" to id, "decision" to decision)
            _pending.value = _pending.value.filterNot { it.id == id }
            target.complete(PermissionResolution(decision, feedback))
        }
    }
}

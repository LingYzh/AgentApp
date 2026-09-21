package com.example.myapplication

import com.example.myapplication.provider.Sse
import com.example.myapplication.provider.withCancellableResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class NetworkCancellationTest {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Test
    fun `completed SSE response does not wait for cancellation watcher`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val serving = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    val body = "data: hello\n\ndata: [DONE]\n\n"
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body").toByteArray())
                        flush()
                    }
                }
            }
            val deltas = mutableListOf<String>()
            withTimeout(5000) {
                Sse.post(client, Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()) {
                    deltas += it
                }
            }
            serving.join()
            assertEquals(listOf("hello"), deltas)
        }
    }

    @Test
    fun `cancel interrupts waiting for response headers`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val connected = async(Dispatchers.IO) { server.accept() }
            val job = launch(Dispatchers.IO) {
                withCancellableResponse(client, request) { error("No response expected") }
            }
            try {
                withTimeout(5000) { connected.await() }.use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    withTimeout(3000) { job.cancelAndJoin() }
                    assertTrue(job.isCancelled)
                    assertEquals(-1, input.read())
                }
            } finally {
                job.cancelAndJoin()
                connected.cancel()
            }
        }
    }

    @Test
    fun `cancel interrupts stalled SSE body after receiving a delta`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val received = CompletableDeferred<String>()
            val connected = async(Dispatchers.IO) { server.accept() }
            val job = launch(Dispatchers.IO) {
                Sse.post(client, request) { received.complete(it) }
            }
            try {
                withTimeout(5000) { connected.await() }.use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                            "Connection: close\r\n\r\ndata: partial\n\n").toByteArray())
                        flush()
                    }
                    assertEquals("partial", withTimeout(5000) { received.await() })
                    withTimeout(3000) { job.cancelAndJoin() }
                    assertTrue(job.isCancelled)
                    assertEquals(-1, input.read())
                }
            } finally {
                job.cancelAndJoin()
                connected.cancel()
            }
        }
    }
}

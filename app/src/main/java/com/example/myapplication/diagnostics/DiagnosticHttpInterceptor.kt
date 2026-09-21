package com.example.myapplication.diagnostics

import okhttp3.Interceptor
import okhttp3.Response

/** Never log URL/query, headers or payloads: compatible gateways may put credentials there. */
class DiagnosticHttpInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val started = System.nanoTime()
        return try {
            val response = chain.proceed(chain.request())
            RuntimeDiagnostics.event("http_response", "status" to response.code,
                "elapsedMs" to (System.nanoTime() - started) / 1_000_000)
            response
        } catch (error: Exception) {
            RuntimeDiagnostics.event("http_failure", "errorClass" to error.javaClass.simpleName,
                "elapsedMs" to (System.nanoTime() - started) / 1_000_000)
            throw error
        }
    }
}

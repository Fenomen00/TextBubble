package com.example.network

/**
 * State of API key and connection test.
 */
sealed interface TestResult {
    data object Idle : TestResult

    data object Testing : TestResult

    data class Success(
        val latencyMs: Long,
        val message: String,
        val previewOutput: String? = null
    ) : TestResult

    data class Error(
        val message: String,
        val statusCode: Int? = null
    ) : TestResult
}

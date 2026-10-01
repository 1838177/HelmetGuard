package com.helmet.guard.core.dispatcher

sealed class DispatchResult {
    data class Success(val info: String) : DispatchResult()
    data class FallbackRequired(val reason: String, val messageBody: String, val phone: String) : DispatchResult()
    data class Failure(val error: String) : DispatchResult()
}

/**
 * 紧急消息分发抽象契约
 */
interface EmergencyDispatcher {
    suspend fun dispatch(phone: String, messageBody: String): DispatchResult
}

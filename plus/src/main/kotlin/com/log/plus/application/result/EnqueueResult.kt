package com.log.plus.application.result

sealed interface EnqueueResult {
    data class Success(val eventId: String) : EnqueueResult
    data object QueueFull : EnqueueResult
}

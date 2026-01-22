package com.log.plus.application.command

data class SaveLogEventCommand(
    val eventId: String? = null,
    val service: String,
    val level: String,
    val message: String,
    val timestamp: String? = null,
    val traceId: String? = null,
    val tags: List<String>? = null
)

package com.log.plus.domain.entity

import java.time.Instant
import java.util.UUID

data class LogEvent (
    val eventId: String = UUID.randomUUID().toString(),
    val service: String,
    val level: String,
    val message: String,
    val timestamp: String = Instant.now().toString(),
    val traceId: String? = null,
    val tags: List<String>? = null
)
package com.log.plus.presentation.dto

data class CreateEventRequest (
    val eventId: String? = null,
    val service: String,
    val level: String,
    val message: String,
    val timestamp: String? = null,
    val traceId: String? = null,
    val tags: List<String>? = null
)
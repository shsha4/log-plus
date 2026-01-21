package com.log.plus.presentation.dto

import com.log.plus.domain.entity.LogEvent

data class EventResponse (
    val eventId: String,
    val service: String,
    val level: String,
    val message: String,
    val timestamp: String,
    val traceId: String?,
    val tags: List<String>?
) {
    
    companion object {
        fun from(event: LogEvent) = EventResponse(
            eventId = event.eventId, 
            service = event.service,
            level = event.level, 
            message = event.message, 
            timestamp = event.timestamp, 
            traceId = event.traceId, 
            tags = event.tags
        )
    }
}
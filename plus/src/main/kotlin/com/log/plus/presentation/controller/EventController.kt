package com.log.plus.presentation.controller

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

import com.log.plus.domain.repository.LogEventRepository
import com.log.plus.domain.entity.LogEvent
import com.log.plus.presentation.dto.CreateEventRequest
import com.log.plus.presentation.dto.EventResponse

import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/events")
class EventController (
    private val logEventRepository: LogEventRepository
) {

    @PostMapping
    suspend fun create(@RequestBody request: CreateEventRequest): EventResponse {
        val event = LogEvent(
            eventId = request.eventId ?: UUID.randomUUID().toString(),
            service = request.service,
            level = request.level,
            message = request.message,
            timestamp = request.timestamp ?: Instant.now().toString(),
            traceId = request.traceId,
            tags = request.tags
        )

        return EventResponse.from(logEventRepository.save(event))
    }

    @GetMapping
    suspend fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) service: String?,
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "0") page: Int
    ): List<EventResponse> {
        val events = logEventRepository.search(q, service, level, from, to, size, page)
        
        return events.map { EventResponse.from(it) }
    }
}
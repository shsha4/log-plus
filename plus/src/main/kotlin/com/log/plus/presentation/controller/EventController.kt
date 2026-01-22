package com.log.plus.presentation.controller

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

import com.log.plus.application.command.SaveLogEventCommand
import com.log.plus.application.query.SearchLogEventQuery
import com.log.plus.application.service.LogEventService
import com.log.plus.presentation.dto.CreateEventRequest
import com.log.plus.presentation.dto.EventResponse

@RestController
@RequestMapping("/events")
class EventController(
    private val logEventService: LogEventService
) {

    @PostMapping
    suspend fun create(@RequestBody request: CreateEventRequest): EventResponse {
        val command = SaveLogEventCommand(
            eventId = request.eventId,
            service = request.service,
            level = request.level,
            message = request.message,
            timestamp = request.timestamp,
            traceId = request.traceId,
            tags = request.tags
        )
        return EventResponse.from(logEventService.save(command))
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
        val query = SearchLogEventQuery(
            q = q,
            service = service,
            level = level,
            from = from,
            to = to,
            size = size,
            page = page
        )
        return logEventService.search(query).map { EventResponse.from(it) }
    }
}

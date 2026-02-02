package com.log.plus.presentation.controller

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus

import com.log.plus.application.command.SaveLogEventCommand
import com.log.plus.application.query.SearchLogEventQuery
import com.log.plus.application.service.LogEventService
import com.log.plus.application.service.IngestService
import com.log.plus.application.result.EnqueueResult
import com.log.plus.presentation.dto.CreateEventRequest
import com.log.plus.presentation.dto.EventResponse

@RestController
@RequestMapping("/events")
class EventController(
    private val logEventService: LogEventService,
    private val ingestService: IngestService
) {

    @PostMapping
    suspend fun create(@RequestBody request: CreateEventRequest): ResponseEntity<EventResponse> {
        val command = SaveLogEventCommand(
            eventId = request.eventId,
            service = request.service,
            level = request.level,
            message = request.message,
            timestamp = request.timestamp,
            traceId = request.traceId,
            tags = request.tags
        )

        return when (val result = ingestService.enqueue(command)) {
            is EnqueueResult.Success -> {
                val response = EventResponse(
                    eventId = result.eventId,
                    service = request.service,
                    level = request.level,
                    message = request.message,
                    timestamp = request.timestamp ?: "",
                    traceId = request.traceId,
                    tags = request.tags
                )
                ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
            }
            is EnqueueResult.QueueFull -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
            }
        }
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

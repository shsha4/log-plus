package com.log.plus.application.service

import com.log.plus.application.command.SaveLogEventCommand
import com.log.plus.application.query.SearchLogEventQuery
import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class LogEventService(
    private val logEventRepository: LogEventRepository
) {
    suspend fun save(command: SaveLogEventCommand): LogEvent {
        val event = LogEvent(
            eventId = command.eventId ?: UUID.randomUUID().toString(),
            service = command.service,
            level = command.level,
            message = command.message,
            timestamp = command.timestamp ?: Instant.now().toString(),
            traceId = command.traceId,
            tags = command.tags
        )
        return logEventRepository.save(event)
    }

    suspend fun search(query: SearchLogEventQuery): List<LogEvent> {
        return logEventRepository.search(
            q = query.q,
            service = query.service,
            level = query.level,
            from = query.from,
            to = query.to,
            size = query.size,
            page = query.page
        )
    }
}

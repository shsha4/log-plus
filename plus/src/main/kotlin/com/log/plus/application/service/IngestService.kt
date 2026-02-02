package com.log.plus.application.service

import com.log.plus.application.command.SaveLogEventCommand
import com.log.plus.application.result.EnqueueResult
import com.log.plus.domain.entity.LogEvent
import com.log.plus.infrastructure.metrics.IngestMetrics
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class IngestService(
    private val eventQueue: Channel<LogEvent>,
    private val metrics: IngestMetrics,
    @Value("\${ingest.backpressure.strategy:reject}") private val backpressureStrategy: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun enqueue(command: SaveLogEventCommand): EnqueueResult {
        metrics.incrementIngested()

        val event = LogEvent(
            eventId = command.eventId ?: UUID.randomUUID().toString(),
            service = command.service,
            level = command.level,
            message = command.message,
            timestamp = command.timestamp ?: Instant.now().toString(),
            traceId = command.traceId,
            tags = command.tags
        )

        val sendResult = eventQueue.trySend(event)

        return if (sendResult.isSuccess) {
            metrics.incrementEnqueued()
            EnqueueResult.Success(event.eventId)
        } else {
            handleBackpressure(event)
        }
    }

    private fun handleBackpressure(event: LogEvent): EnqueueResult {
        return when (backpressureStrategy) {
            "reject" -> {
                metrics.incrementRejected()
                logger.warn("Queue full - rejecting event ${event.eventId}")
                EnqueueResult.QueueFull
            }
            "drop" -> {
                metrics.incrementDropped()
                logger.warn("Queue full - dropping event ${event.eventId}")
                EnqueueResult.Success(event.eventId) 
            }
            else -> {
                metrics.incrementRejected()
                EnqueueResult.QueueFull
            }
        }
    }
}
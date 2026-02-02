package com.log.plus.application.service

import com.log.plus.application.command.SaveLogEventCommand
import com.log.plus.application.result.EnqueueResult
import com.log.plus.domain.entity.LogEvent
import com.log.plus.infrastructure.metrics.IngestMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IngestServiceTest {

    private lateinit var eventQueue: Channel<LogEvent>
    private lateinit var metrics: IngestMetrics
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var ingestService: IngestService

    @BeforeEach
    fun setup() {
        eventQueue = Channel(capacity = 3)  // 작은 큐로 테스트
        meterRegistry = SimpleMeterRegistry()
        metrics = IngestMetrics(meterRegistry)
    }

    @Test
    fun `enqueue should succeed when queue has space`() = runTest {
        // Given
        ingestService = IngestService(eventQueue, metrics, "reject")
        val command = SaveLogEventCommand(
            eventId = "test-id-1",
            service = "test-service",
            level = "INFO",
            message = "Test message",
            timestamp = "2024-01-01T00:00:00Z",
            traceId = null,
            tags = null
        )

        // When
        val result = ingestService.enqueue(command)

        // Then
        assertTrue(result is EnqueueResult.Success)
        assertEquals("test-id-1", (result as EnqueueResult.Success).eventId)

        // 메트릭 확인
        assertEquals(1.0, meterRegistry.counter("ingested_total").count())
        assertEquals(1.0, meterRegistry.counter("enqueued_total").count())
        assertEquals(0.0, meterRegistry.counter("rejected_total").count())
        assertEquals(0.0, meterRegistry.counter("dropped_total").count())
    }

    @Test
    fun `enqueue should reject when queue is full with reject strategy`() = runTest {
        // Given
        ingestService = IngestService(eventQueue, metrics, "reject")
        val command = SaveLogEventCommand(
            service = "test-service",
            level = "INFO",
            message = "Test message"
        )

        // 큐를 가득 채움 (capacity=3)
        repeat(3) {
            ingestService.enqueue(command)
        }

        // When - 큐가 가득 찬 상태에서 추가 시도
        val result = ingestService.enqueue(command)

        // Then
        assertTrue(result is EnqueueResult.QueueFull)

        // 메트릭 확인
        assertEquals(4.0, meterRegistry.counter("ingested_total").count())  // 4번 시도
        assertEquals(3.0, meterRegistry.counter("enqueued_total").count())  // 3개만 성공
        assertEquals(1.0, meterRegistry.counter("rejected_total").count())  // 1개 거절
        assertEquals(0.0, meterRegistry.counter("dropped_total").count())
    }

    @Test
    fun `enqueue should drop silently when queue is full with drop strategy`() = runTest {
        // Given
        ingestService = IngestService(eventQueue, metrics, "drop")
        val command = SaveLogEventCommand(
            service = "test-service",
            level = "INFO",
            message = "Test message"
        )

        // 큐를 가득 채움
        repeat(3) {
            ingestService.enqueue(command)
        }

        // When - 큐가 가득 찬 상태에서 추가 시도
        val result = ingestService.enqueue(command)

        // Then
        assertTrue(result is EnqueueResult.Success)  // drop 전략은 Success 반환 (거짓말)

        // 메트릭 확인
        assertEquals(4.0, meterRegistry.counter("ingested_total").count())
        assertEquals(3.0, meterRegistry.counter("enqueued_total").count())  // 실제로는 3개만
        assertEquals(0.0, meterRegistry.counter("rejected_total").count())
        assertEquals(1.0, meterRegistry.counter("dropped_total").count())  // 1개 드롭
    }

    @Test
    fun `enqueue should generate eventId when not provided`() = runTest {
        // Given
        ingestService = IngestService(eventQueue, metrics, "reject")
        val command = SaveLogEventCommand(
            eventId = null,  // eventId 없음
            service = "test-service",
            level = "INFO",
            message = "Test message"
        )

        // When
        val result = ingestService.enqueue(command)

        // Then
        assertTrue(result is EnqueueResult.Success)
        assertNotNull((result as EnqueueResult.Success).eventId)
        assertTrue(result.eventId.isNotEmpty())
    }

    @Test
    fun `enqueue should generate timestamp when not provided`() = runTest {
        // Given
        ingestService = IngestService(eventQueue, metrics, "reject")
        val command = SaveLogEventCommand(
            service = "test-service",
            level = "INFO",
            message = "Test message",
            timestamp = null  // timestamp 없음
        )

        // When
        val result = ingestService.enqueue(command)

        // Then
        assertTrue(result is EnqueueResult.Success)

        // 큐에서 이벤트를 꺼내서 timestamp 확인
        val event = eventQueue.receive()
        assertNotNull(event.timestamp)
        assertTrue(event.timestamp.isNotEmpty())
    }
}

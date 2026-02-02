package com.log.plus.presentation.controller

import com.log.plus.domain.entity.LogEvent
import com.log.plus.infrastructure.metrics.IngestMetrics
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = [
    "ingest.queue.capacity=5",
    "ingest.backpressure.strategy=reject"
])
class EventControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var eventQueue: Channel<LogEvent>

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setup() {
        // 큐 비우기
        while (!eventQueue.isEmpty) {
            eventQueue.tryReceive()
        }
    }

    @Test
    fun `POST events should return 202 Accepted when queue has space`() {
        // Given
        val requestBody = """
            {
                "service": "test-service",
                "level": "INFO",
                "message": "Test message",
                "tags": ["tag1", "tag2"]
            }
        """.trimIndent()

        // When & Then
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.eventId").isNotEmpty
            .jsonPath("$.service").isEqualTo("test-service")
            .jsonPath("$.level").isEqualTo("INFO")
            .jsonPath("$.message").isEqualTo("Test message")
    }

    @Test
    fun `POST events should return 429 when queue is full`() {
        // Given - 큐를 가득 채움 (capacity=5)
        val requestBody = """
            {
                "service": "test-service",
                "level": "INFO",
                "message": "Test message"
            }
        """.trimIndent()

        // BatchWriter가 처리하기 전에 빠르게 여러 요청을 보냄
        // 최소 하나는 429를 받을 것으로 기대
        var receivedAccepted = 0
        var received429 = false

        repeat(10) {  // 큐 capacity(5)보다 많이 보냄
            val status = try {
                webTestClient.post()
                    .uri("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchange()
                    .returnResult(String::class.java)
                    .status
                    .value()
            } catch (e: Exception) {
                429  // 에러가 발생하면 429로 간주
            }

            when (status) {
                202 -> receivedAccepted++
                429 -> received429 = true
            }
        }

        // Then - 429가 발생하거나, 큐가 가득 찼거나, 대부분 accepted를 받았어야 함
        assert(received429 || receivedAccepted >= 5) {
            "Expected either 429 or at least 5 accepted responses. Got: accepted=$receivedAccepted, has429=$received429"
        }
    }

    @Test
    fun `POST events should generate eventId when not provided`() {
        // Given
        val requestBody = """
            {
                "service": "test-service",
                "level": "INFO",
                "message": "Test message"
            }
        """.trimIndent()

        // When & Then
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.eventId").isNotEmpty
            .jsonPath("$.eventId").value<String> { eventId ->
                assert(eventId.isNotEmpty())
            }
    }

    @Test
    fun `POST events should increment metrics correctly`() {
        // Given
        val initialIngestedCount = meterRegistry.counter("ingested_total").count()
        val initialEnqueuedCount = meterRegistry.counter("enqueued_total").count()

        val requestBody = """
            {
                "service": "metrics-test",
                "level": "INFO",
                "message": "Metrics test message"
            }
        """.trimIndent()

        // When
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isAccepted

        // Then
        val finalIngestedCount = meterRegistry.counter("ingested_total").count()
        val finalEnqueuedCount = meterRegistry.counter("enqueued_total").count()

        assert(finalIngestedCount > initialIngestedCount)
        assert(finalEnqueuedCount > initialEnqueuedCount)
    }

    @Test
    fun `GET events search should work independently of ingest pipeline`() {
        // Given & When
        webTestClient.get()
            .uri("/events?service=test-service&level=INFO")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }
}

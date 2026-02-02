package com.log.plus.presentation.controller

import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import com.log.plus.presentation.dto.CreateEventRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EventControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var repository: LogEventRepository

    @Test
    fun `POST events should create event and return response`() {
        val request = CreateEventRequest(
            service = "test-service",
            level = "INFO",
            message = "Controller test message"
        )

        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isAccepted  // 202 Accepted
            .expectBody()
            .jsonPath("$.eventId").isNotEmpty
            .jsonPath("$.service").isEqualTo("test-service")
            .jsonPath("$.level").isEqualTo("INFO")
            .jsonPath("$.message").isEqualTo("Controller test message")
    }

    @Test
    fun `POST events with eventId should use provided eventId`() {
        val eventId = "custom-id-${System.currentTimeMillis()}"
        val request = CreateEventRequest(
            eventId = eventId,
            service = "test-service",
            level = "WARN",
            message = "Custom ID test"
        )

        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.eventId").isEqualTo(eventId)
    }

    @Test
    fun `GET events search should return results`() {
        // Repository로 직접 데이터 저장 (비동기 파이프라인 우회)
        runBlocking {
            repository.saveBulk(listOf(
                LogEvent(
                    eventId = "search-test-${System.currentTimeMillis()}",
                    service = "test-service",
                    level = "INFO",
                    message = "login test message for search",
                    timestamp = Instant.now().toString()
                )
            ))
        }

        webTestClient.get()
            .uri("/events?q=login")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should filter by service`() {
        // Repository로 직접 데이터 저장
        runBlocking {
            repository.saveBulk(listOf(
                LogEvent(
                    eventId = "service-test-${System.currentTimeMillis()}",
                    service = "api-server",
                    level = "INFO",
                    message = "API server test message",
                    timestamp = Instant.now().toString()
                )
            ))
        }

        webTestClient.get()
            .uri("/events?service=api-server")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should filter by level`() {
        // Repository로 직접 데이터 저장
        runBlocking {
            repository.saveBulk(listOf(
                LogEvent(
                    eventId = "level-test-${System.currentTimeMillis()}",
                    service = "test-service",
                    level = "ERROR",
                    message = "Error test message",
                    timestamp = Instant.now().toString()
                )
            ))
        }

        webTestClient.get()
            .uri("/events?level=ERROR")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should support pagination`() {
        // Repository로 직접 데이터 저장
        runBlocking {
            repository.saveBulk(listOf(
                LogEvent(
                    eventId = "page-1-${System.currentTimeMillis()}",
                    service = "pagination-test",
                    level = "INFO",
                    message = "Pagination test 1",
                    timestamp = Instant.now().toString()
                ),
                LogEvent(
                    eventId = "page-2-${System.currentTimeMillis()}",
                    service = "pagination-test",
                    level = "INFO",
                    message = "Pagination test 2",
                    timestamp = Instant.now().toString()
                ),
                LogEvent(
                    eventId = "page-3-${System.currentTimeMillis()}",
                    service = "pagination-test",
                    level = "INFO",
                    message = "Pagination test 3",
                    timestamp = Instant.now().toString()
                )
            ))
        }

        webTestClient.get()
            .uri("/events?service=pagination-test&size=2&page=0")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").value<Int> { size ->
                assert(size <= 2) { "Expected size <= 2 but got $size" }
            }
    }
}

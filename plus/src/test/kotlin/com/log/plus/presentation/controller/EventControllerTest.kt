package com.log.plus.presentation.controller

import com.log.plus.presentation.dto.CreateEventRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EventControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

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
            .expectStatus().isOk
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
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.eventId").isEqualTo(eventId)
    }

    @Test
    fun `GET events search should return results`() {
        // First create an event to search for
        val createRequest = CreateEventRequest(
            service = "test-service",
            level = "INFO",
            message = "login test message for search"
        )
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isOk

        webTestClient.get()
            .uri("/events?q=login")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should filter by service`() {
        // First create an event with specific service
        val createRequest = CreateEventRequest(
            service = "api-server",
            level = "INFO",
            message = "API server test message"
        )
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isOk

        webTestClient.get()
            .uri("/events?service=api-server")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should filter by level`() {
        // First create an ERROR event
        val createRequest = CreateEventRequest(
            service = "test-service",
            level = "ERROR",
            message = "Error test message"
        )
        webTestClient.post()
            .uri("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isOk

        webTestClient.get()
            .uri("/events?level=ERROR")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET events search should support pagination`() {
        // First create some events
        repeat(3) {
            webTestClient.post()
                .uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CreateEventRequest(
                    service = "pagination-test",
                    level = "INFO",
                    message = "Pagination test $it"
                ))
                .exchange()
                .expectStatus().isOk
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

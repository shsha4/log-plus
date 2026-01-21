package com.log.plus.infrastructure.elasticsearch

import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class LogEventRepositoryImplTest {

    @Autowired
    private lateinit var repository: LogEventRepository

    @Test
    fun `save should store event and return it`() = runTest {
        val event = LogEvent(
            eventId = "test-save-${System.currentTimeMillis()}",
            service = "test-service",
            level = "INFO",
            message = "Test message",
            timestamp = Instant.now().toString(),
            traceId = "trace-test",
            tags = listOf("test")
        )

        val saved = repository.save(event)

        assertEquals(event.eventId, saved.eventId)
        assertEquals(event.service, saved.service)
        assertEquals(event.message, saved.message)
    }

    @Test
    fun `search should find events by message`() = runTest {
        // First save an event with "login" in message
        val loginEvent = LogEvent(
            eventId = "test-login-${System.currentTimeMillis()}",
            service = "test-service",
            level = "INFO",
            message = "User login successful",
            timestamp = Instant.now().toString(),
            traceId = "trace-login",
            tags = listOf("test")
        )
        repository.save(loginEvent)

        val results = repository.search(
            q = "login",
            service = null,
            level = null,
            from = null,
            to = null,
            size = 10,
            page = 0
        )

        assertTrue(results.isNotEmpty(), "Results should not be empty after saving a login event")
        assertTrue(results.any { it.message.contains("login", ignoreCase = true) })
    }

    @Test
    fun `search should filter by service`() = runTest {
        // First save an event with specific service
        val serviceEvent = LogEvent(
            eventId = "test-api-${System.currentTimeMillis()}",
            service = "api-server",
            level = "INFO",
            message = "API server test message",
            timestamp = Instant.now().toString(),
            traceId = "trace-api",
            tags = listOf("test")
        )
        repository.save(serviceEvent)

        val results = repository.search(
            q = null,
            service = "api-server",
            level = null,
            from = null,
            to = null,
            size = 10,
            page = 0
        )

        assertTrue(results.isNotEmpty(), "Results should not be empty after saving an api-server event")
        assertTrue(results.all { it.service == "api-server" })
    }

    @Test
    fun `search should filter by level`() = runTest {
        // First save an ERROR level event
        val errorEvent = LogEvent(
            eventId = "test-error-${System.currentTimeMillis()}",
            service = "test-service",
            level = "ERROR",
            message = "Test error message",
            timestamp = Instant.now().toString(),
            traceId = "trace-error",
            tags = listOf("test")
        )
        repository.save(errorEvent)

        val results = repository.search(
            q = null,
            service = null,
            level = "ERROR",
            from = null,
            to = null,
            size = 10,
            page = 0
        )

        assertTrue(results.isNotEmpty(), "Results should not be empty after saving an ERROR event")
        assertTrue(results.all { it.level == "ERROR" })
    }

    @Test
    fun `search should return empty list when no match`() = runTest {
        val results = repository.search(
            q = "nonexistent-query-string-12345",
            service = null,
            level = null,
            from = null,
            to = null,
            size = 10,
            page = 0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search should respect pagination`() = runTest {
        val page0 = repository.search(
            q = null,
            service = "api-server",
            level = null,
            from = null,
            to = null,
            size = 2,
            page = 0
        )

        assertTrue(page0.size <= 2)
    }
}

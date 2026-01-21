package com.log.plus.domain.entity

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LogEventTest {

    @Test
    fun `should create LogEvent with all fields`() {
        val timestamp = Instant.now().toString()
        val event = LogEvent(
            eventId = "event-001",
            service = "my-service",
            level = "INFO",
            message = "Test message",
            timestamp = timestamp,
            traceId = "trace-001",
            tags = listOf("tag1", "tag2")
        )

        assertEquals("event-001", event.eventId)
        assertEquals("my-service", event.service)
        assertEquals("INFO", event.level)
        assertEquals("Test message", event.message)
        assertEquals(timestamp, event.timestamp)
        assertEquals("trace-001", event.traceId)
        assertEquals(listOf("tag1", "tag2"), event.tags)
    }

    @Test
    fun `should generate UUID when eventId not provided`() {
        val event = LogEvent(
            service = "my-service",
            level = "INFO",
            message = "Test message"
        )

        assertNotNull(event.eventId)
        assertEquals(36, event.eventId.length)
    }

    @Test
    fun `should allow null optional fields`() {
        val event = LogEvent(
            service = "my-service",
            level = "INFO",
            message = "Test message"
        )

        assertNull(event.traceId)
        assertNull(event.tags)
    }
}

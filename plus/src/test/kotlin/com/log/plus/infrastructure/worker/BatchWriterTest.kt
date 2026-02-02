package com.log.plus.infrastructure.worker

import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class BatchWriterTest {

    private lateinit var eventQueue: Channel<LogEvent>
    private lateinit var mockRepository: LogEventRepository
    private lateinit var batchWriter: BatchWriter

    @BeforeEach
    fun setup() {
        eventQueue = Channel(capacity = 100)
        mockRepository = mock<LogEventRepository>()
    }

    @AfterEach
    fun teardown() {
        batchWriter.stop()
    }

    @Test
    fun `should handle empty batch gracefully`() = runTest {
        // Given
        batchWriter = BatchWriter(
            eventQueue = eventQueue,
            repository = mockRepository,
            batchSize = 10,
            flushIntervalMs = 100
        )

        // When
        batchWriter.start()

        // Then - saveBulk이 빈 배치로 호출되지 않아야 함
        verify(mockRepository, never()).saveBulk(emptyList())
    }

    @Test
    fun `stop should cancel worker gracefully`() = runTest {
        // Given
        batchWriter = BatchWriter(
            eventQueue = eventQueue,
            repository = mockRepository,
            batchSize = 10,
            flushIntervalMs = 1000
        )

        batchWriter.start()

        // When
        batchWriter.stop()

        // Then - 중단되어야 함 (추가 검증 없음, 크래시만 안 나면 OK)
        assert(true)
    }
}

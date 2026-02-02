package com.log.plus.infrastructure.worker

import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@Component
class BatchWriter(
    private val eventQueue: Channel<LogEvent>,
    private val repository: LogEventRepository,
    @Value("\${ingest.batch.size:200}") private val batchSize: Int,
    @Value("\${ingest.batch.flush-interval-ms:500}") private val flushIntervalMs: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workerJob: Job? = null

    @PostConstruct
    fun start() {
        logger.info("Starting BatchWriter (batchSize=$batchSize, flushInterval=${flushIntervalMs}ms)")
        workerJob = scope.launch {
            processBatches()
        }
    }

    @PreDestroy
    fun stop() {
        logger.info("Stopping BatchWriter")
        workerJob?.cancel()
        scope.cancel()
    }

    private suspend fun processBatches() {
        while (scope.isActive) {
            try {
                val batch = collectBatch()
                if (batch.isNotEmpty()) {
                    flushBatch(batch)
                }
            } catch (e: Exception) {
                logger.error("Error processing batch", e)
                delay(1000)  // 에러 발생 시 잠시 대기
            }
        }
    }

    private suspend fun collectBatch(): List<LogEvent> {
        val batch = mutableListOf<LogEvent>()
        val deadline = System.currentTimeMillis() + flushIntervalMs

        while (batch.size < batchSize && System.currentTimeMillis() < deadline) {
            // 타임아웃 계산
            val remainingTime = deadline - System.currentTimeMillis()
            if (remainingTime <= 0) break

            // 타임아웃 내에서 이벤트 수신 시도
            try {
                val event = withTimeoutOrNull(remainingTime) {
                    eventQueue.receive()
                }
                if (event != null) {
                    batch.add(event)
                } else {
                    break  // 타임아웃
                }
            } catch (e: Exception) {
                break
            }
        }

        return batch
    }

    private suspend fun flushBatch(batch: List<LogEvent>) {
        try {
            val count = repository.saveBulk(batch)
            logger.info("Flushed ${batch.size} events to Elasticsearch (saved: $count)")
        } catch (e: Exception) {
            logger.error("Failed to flush batch of ${batch.size} events", e)
            // 실패한 이벤트 처리 전략 (재시도, DLQ 등) 추가 가능
        }
    }
}

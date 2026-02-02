package com.log.plus.config

import com.log.plus.domain.entity.LogEvent
import kotlinx.coroutines.channels.Channel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IngestPipelineConfig {

    @Value("\${ingest.queue.capacity:10000}")
    private val queueCapacity: Int = 10000

    @Bean
    fun eventQueue(): Channel<LogEvent> {
        return Channel(capacity = queueCapacity)
    }
}

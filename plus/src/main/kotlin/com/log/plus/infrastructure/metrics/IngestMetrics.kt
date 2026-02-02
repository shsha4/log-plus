package com.log.plus.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class IngestMetrics(
    private val meterRegistry: MeterRegistry
) {
    private val ingestedCounter: Counter = Counter.builder("ingested_total")
        .description("Total number of events received via POST")
        .register(meterRegistry)

    private val enqueuedCounter: Counter = Counter.builder("enqueued_total")
        .description("Total number of events successfully enqueued")
        .register(meterRegistry)

    private val droppedCounter: Counter = Counter.builder("dropped_total")
        .description("Total number of events dropped due to queue full")
        .register(meterRegistry)

    private val rejectedCounter: Counter = Counter.builder("rejected_total")
        .description("Total number of requests rejected with 429")
        .register(meterRegistry)

    fun incrementIngested() = ingestedCounter.increment()
    fun incrementEnqueued() = enqueuedCounter.increment()
    fun incrementDropped() = droppedCounter.increment()
    fun incrementRejected() = rejectedCounter.increment()
}

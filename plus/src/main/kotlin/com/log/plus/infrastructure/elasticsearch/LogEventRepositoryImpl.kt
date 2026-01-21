package com.log.plus.infrastructure.elasticsearch

import org.springframework.stereotype.Repository
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import com.log.plus.domain.entity.LogEvent
import com.log.plus.domain.repository.LogEventRepository
import kotlin.collections.Map
import kotlin.collections.mapOf
import kotlin.collections.listOf

@Repository
class LogEventRepositoryImpl (
    private val esWebClient: WebClient
): LogEventRepository {
    private val indexName = "log-events"

    override suspend fun save(event: LogEvent): LogEvent {
        esWebClient.put()
            .uri("/$indexName/_doc/${event.eventId}?refresh=wait_for")
            .bodyValue(event)
            .retrieve()
            .awaitBody<Map<String, Any>>()

        return event
    }

    override suspend fun search(q: String?,
                                service: String?,
                                level: String?,
                                from: String?,
                                to: String?,
                                size: Int,
                                page: Int): List<LogEvent> {
        val mustClauses = mutableListOf<Map<String, Any>>()

        q?.let {
            mustClauses.add(mapOf("match" to mapOf("message" to it)))
        }

        service?.let {
            mustClauses.add(mapOf("term" to mapOf("service" to it)))
        }

        level?.let {
            mustClauses.add(mapOf("term" to mapOf("level" to it)))
        }

        if (from != null || to != null) {
            val rangeMap = mutableMapOf<String, Any>()
            from?.let { rangeMap["gte"] = it }
            to?.let { rangeMap["lte"] = it }
            mustClauses.add(mapOf("range" to mapOf("timestamp" to rangeMap)))
        }

        val query = if (mustClauses.isEmpty()) {
            // 조건이 없으면 match_all 로 전체 조회
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            // 조건이 하나라도 있다면 bool 쿼리
            mapOf("bool" to mapOf("must" to mustClauses))
        }

        val body = mapOf(
            "query" to query,
            "size" to size,
            "from" to page * size,
            "sort" to listOf(mapOf("timestamp" to "desc"))
        )

        val response = esWebClient.post()
                .uri("/$indexName/_search")
                .bodyValue(body)
                .retrieve()
                .awaitBody<Map<String, Any>>()

        val hits = (response["hits"] as Map<*, *>)["hits"] as List<Map<*, *>>
        return hits.map { hit ->
            val source = hit["_source"] as Map<*, *>
            LogEvent(
                eventId = source["eventId"] as String,
                service = source["service"] as String,
                level = source["level"] as String,
                message = source["message"] as String,
                timestamp = source["timestamp"] as String,
                traceId = source["traceId"] as? String,
                tags = (source["tags"] as? List<*>)?.map { it.toString() }
            )
        }
    }
}
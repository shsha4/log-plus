package com.log.plus.domain.repository

import com.log.plus.domain.entity.LogEvent

interface LogEventRepository {
    suspend fun save(event: LogEvent): LogEvent
    suspend fun search(
        q: String?,
        service: String?,
        level: String?,
        from: String?,
        to: String?,
        size: Int,
        page: Int
    ): List<LogEvent>
}
package com.log.plus.application.query

data class SearchLogEventQuery(
    val q: String? = null,
    val service: String? = null,
    val level: String? = null,
    val from: String? = null,
    val to: String? = null,
    val size: Int = 20,
    val page: Int = 0
)

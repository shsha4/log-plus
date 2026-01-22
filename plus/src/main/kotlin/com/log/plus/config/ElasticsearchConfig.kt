package com.log.plus.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders

@Configuration
class ElasticsearchConfig(
    @Value("\${elasticsearch.host}") private val host: String,
    @Value("\${elasticsearch.username}") private val username: String,
    @Value("\${elasticsearch.password}") private val password: String
) {

    @Bean
    fun esWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(host)
            .defaultHeaders {
                it.setBasicAuth(username, password)
                it.accept = listOf(MediaType.APPLICATION_JSON)
                it.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }
            .build()
    }
}

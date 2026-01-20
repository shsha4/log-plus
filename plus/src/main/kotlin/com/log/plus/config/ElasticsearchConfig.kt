import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.core.io.ClassPathResource
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders
import co.elastic.clients.elasticsearch.ElasticsearchClient
import io.netty.handler.ssl.SslContextBuilder
import reactor.netty.http.client.HttpClient
import kotlin.collections.listOf

@Configuration
class ElasticsearchConfig(
    @Value("\${elasticsearch.host}") private val host: String,
    @Value("\${elasticsearch.port}") private val port: Int,
    @Value("\${elasticsearch.username}") private val username: String,
    @Value("\${elasticsearch.password}") private val password: String,
    @Value("\${elasticsearch.ssl.ca-path}") private val caPath: String
) {

    @Bean
    fun esWebClient(): WebClient {
        val sslContext = ClassPathResource(caPath).inputStream.use { input ->
            SslContextBuilder.forClient().trustManager(input).build()
        }

        val httpClient = HttpClient.create().secure() { spec -> spec.sslContext(sslContext) }

        return WebClient.builder()
            .baseUrl(host)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeaders {
                it.setBasicAuth(username, password)
                it.accept = listOf(MediaType.APPLICATION_JSON)
                it.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }
            .build()
    }
}
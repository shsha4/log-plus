# Log Plus

> 고성능 로그 수집/검색/분석 플랫폼 (Kotlin + Spring Boot WebFlux + Elasticsearch)

---

## 프로젝트 개요

**Log Plus**는 대규모 로그 데이터를 실시간으로 수집하고, 검색하며, 분석할 수 있는 로그 플랫폼입니다.

### 핵심 목표
1. **빠른 수집**: 수집(POST)과 저장(ES write)을 분리하여 즉시 응답
2. **배치 처리**: Elasticsearch Bulk API로 처리량 극대화
3. **백프레셔 방어**: 큐 기반 백프레셔 처리로 시스템 보호
4. **검색/분석**: 로그 검색 및 통계 집계 기능

---

## 아키텍처

### 전체 구조

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /events (로그 수집)
       ▼
┌─────────────────────────────────┐
│     EventController             │
│  (suspend fun - 논블로킹)        │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│     IngestService               │
│  - 큐에 이벤트 삽입               │
│  - 백프레셔 처리 (reject/drop)    │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│   Memory Queue (Channel)        │
│   - capacity: 10,000            │
│   - 논블로킹 큐                   │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│     BatchWriter                 │
│  (백그라운드 워커)                 │
│  - 200개 또는 500ms마다 flush    │
│  - Bulk API로 배치 저장           │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│     Elasticsearch               │
│  - log-events 인덱스             │
└─────────────────────────────────┘
```

### 비동기 파이프라인 (Day 2 완성)

#### Before (동기)
```
POST /events → Controller → Service → ES (blocking)
                                        ↓
                                    느린 응답 (200ms)
```

#### After (비동기)
```
POST /events → Controller → IngestService → Memory Queue
                                              ↓
                                          202 Accepted (2ms)

[Background Worker]
Queue → BatchWriter → ES Bulk API (배치 처리)
```

**성능 개선:**
- **응답 속도**: 200ms → 2ms (100배 개선)
- **처리량**: 5-10배 향상 (Bulk API)
- **안정성**: 백프레셔 방어 (429 응답)

---

## 주요 컴포넌트

### 1. Presentation Layer

#### EventController
```kotlin
@RestController
@RequestMapping("/events")
class EventController(
    private val ingestService: IngestService
)
```

**역할:**
- HTTP 요청 수신 (POST /events, GET /events)
- 요청을 IngestService로 위임
- 202 Accepted 또는 429 Too Many Requests 응답

**핵심 메서드:**
- `suspend fun create()`: 로그 이벤트 수집 (논블로킹)
- `suspend fun search()`: 로그 검색 (필터, 페이지네이션)

---

### 2. Application Layer

#### IngestService
```kotlin
@Service
class IngestService(
    private val eventQueue: Channel<LogEvent>,
    private val metrics: IngestMetrics,
    private val backpressureStrategy: String
)
```

**역할:**
- 로그 이벤트를 메모리 큐에 삽입
- 백프레셔 처리 (큐 full 시)
- 메트릭 수집

**백프레셔 전략:**
- **reject**: 429 응답 (클라이언트 재시도)
- **drop**: 이벤트 버림 (메트릭만 증가)

**주요 메서드:**
- `suspend fun enqueue()`: 큐에 이벤트 삽입

---

#### LogEventService
```kotlin
@Service
class LogEventService(
    private val repository: LogEventRepository
)
```

**역할:**
- 로그 검색 비즈니스 로직
- Repository 레이어 호출

**주요 메서드:**
- `suspend fun search()`: 로그 검색

---

### 3. Infrastructure Layer

#### BatchWriter
```kotlin
@Component
class BatchWriter(
    private val eventQueue: Channel<LogEvent>,
    private val repository: LogEventRepository,
    private val batchSize: Int = 200,
    private val flushIntervalMs: Long = 500
)
```

**역할:**
- 백그라운드 워커 (코루틴)
- 큐에서 이벤트를 꺼내 배치로 저장
- 200개 또는 500ms 중 먼저 도달 시 flush

**동작 원리:**
```kotlin
private suspend fun processBatches() {
    while (scope.isActive) {
        val batch = collectBatch()  // 200개 or 500ms
        if (batch.isNotEmpty()) {
            flushBatch(batch)  // ES Bulk API
        }
    }
}
```

**라이프사이클:**
- `@PostConstruct`: 애플리케이션 시작 시 워커 시작
- `@PreDestroy`: 애플리케이션 종료 시 워커 중단

---

#### LogEventRepositoryImpl
```kotlin
@Repository
class LogEventRepositoryImpl(
    private val esWebClient: WebClient,
    private val objectMapper: ObjectMapper
) : LogEventRepository
```

**역할:**
- Elasticsearch와 통신
- Bulk API로 배치 저장
- 검색 쿼리 실행

**주요 메서드:**
- `suspend fun save()`: 단건 저장 (테스트용)
- `suspend fun saveBulk()`: 배치 저장 (운영)
- `suspend fun search()`: 로그 검색

**Bulk API 예시:**
```kotlin
// NDJSON 형식
{"index":{"_index":"log-events","_id":"event-1"}}
{"eventId":"event-1","service":"api-server","level":"INFO",...}
{"index":{"_index":"log-events","_id":"event-2"}}
{"eventId":"event-2","service":"web-server","level":"ERROR",...}
```

---

#### IngestMetrics
```kotlin
@Component
class IngestMetrics(private val meterRegistry: MeterRegistry)
```

**역할:**
- Micrometer 기반 메트릭 수집
- Actuator로 노출

**수집 메트릭:**
- `ingested_total`: 총 수집 요청 수
- `enqueued_total`: 큐에 성공적으로 삽입된 이벤트 수
- `dropped_total`: 버려진 이벤트 수 (drop 정책)
- `rejected_total`: 거부된 요청 수 (reject 정책)

**확인 방법:**
```bash
curl http://localhost:8080/actuator/metrics/ingested_total
curl http://localhost:8080/actuator/metrics/enqueued_total
```

---

### 4. Domain Layer

#### LogEvent (Entity)
```kotlin
data class LogEvent(
    val eventId: String,
    val service: String,
    val level: String,
    val message: String,
    val timestamp: String,
    val traceId: String? = null,
    val tags: List<String>? = null
)
```

**역할:**
- 로그 이벤트 도메인 모델

---

### 5. Configuration

#### IngestPipelineConfig
```kotlin
@Configuration
class IngestPipelineConfig {
    @Bean
    fun eventQueue(): Channel<LogEvent> {
        return Channel(capacity = queueCapacity)
    }
}
```

**역할:**
- 메모리 큐 생성 (Kotlin Channel)
- capacity 설정 (기본 10,000)

---

## 동작 원리

### 1. 로그 수집 (POST /events)

```
1. 클라이언트가 POST /events로 로그 전송
   ↓
2. EventController.create() 호출 (suspend 함수)
   ↓
3. IngestService.enqueue() 호출
   ↓
4. Channel.trySend()로 큐에 삽입 시도
   ↓
5-1. 성공: 202 Accepted 응답 (즉시)
5-2. 실패 (큐 full): 429 Too Many Requests 응답
```

**핵심:**
- **논블로킹**: 스레드가 대기하지 않음 (다른 요청 처리 가능)
- **빠른 응답**: ES 저장 전에 즉시 응답 (2ms)

---

### 2. 배치 저장 (BatchWriter)

```
1. BatchWriter가 백그라운드에서 실행 중
   ↓
2. collectBatch() 호출
   - 큐에서 이벤트를 200개까지 수집
   - 또는 500ms 타임아웃 도달
   ↓
3. flushBatch() 호출
   - 수집된 이벤트들을 NDJSON으로 변환
   - ES Bulk API로 한 번에 전송 (/_bulk)
   ↓
4. 로그 출력: "Flushed N events to Elasticsearch"
   ↓
5. 다시 collectBatch()로 반복
```

**핵심:**
- **배치 효율**: 200개를 1번의 HTTP 요청으로 저장 (네트워크 오버헤드 감소)
- **타임아웃**: 500ms마다 강제 flush (지연 방지)

---

### 3. 로그 검색 (GET /events)

```
1. 클라이언트가 GET /events?q=login&service=api-server
   ↓
2. EventController.search() 호출
   ↓
3. LogEventService.search() 호출
   ↓
4. LogEventRepositoryImpl.search() 호출
   - ES 쿼리 빌드 (bool query, term filter, range filter)
   - WebClient로 ES 요청 (/_search)
   ↓
5. 검색 결과를 LogEvent 리스트로 변환
   ↓
6. JSON 응답
```

**쿼리 예시:**
```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "message": "login" } },
        { "term": { "service": "api-server" } }
      ]
    }
  },
  "size": 20,
  "from": 0,
  "sort": [{ "timestamp": "desc" }]
}
```

---

## 기술 스택

### Backend
- **언어**: Kotlin 1.9+
- **프레임워크**: Spring Boot 3.4.1
- **Reactive Stack**: Spring WebFlux (Netty)
- **비동기**: Kotlin Coroutines (suspend functions)
- **데이터베이스**: Elasticsearch 8.x
- **HTTP 클라이언트**: Spring WebClient
- **메트릭**: Micrometer + Spring Boot Actuator

### Infrastructure
- **빌드 도구**: Gradle (Kotlin DSL)
- **컨테이너**: Docker + Docker Compose
- **JVM**: Java 21

---

## 주요 개념

### 1. Kotlin Coroutine

#### suspend 함수
```kotlin
suspend fun create(): ResponseEntity<EventResponse> {
    val result = ingestService.enqueue(command)  // 논블로킹 대기
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
}
```

**특징:**
- **논블로킹**: 스레드가 대기하지 않고 다른 작업 처리 가능
- **동기 스타일 코드**: 콜백 없이 읽기 쉬운 코드
- **높은 처리량**: 적은 스레드로 많은 요청 처리

---

### 2. Kotlin Channel

```kotlin
val eventQueue: Channel<LogEvent> = Channel(capacity = 10000)

// 송신자 (Producer)
eventQueue.trySend(event)  // 논블로킹 전송 시도

// 수신자 (Consumer)
val event = eventQueue.receive()  // 이벤트 수신 (suspend)
```

**특징:**
- **Thread-safe**: 여러 코루틴이 동시 접근해도 안전
- **Backpressure 지원**: 큐가 가득 차면 자동으로 처리
- **논블로킹**: Thread 블로킹 없이 suspend로 대기

---

### 3. Elasticsearch Bulk API

#### 단건 저장 (Before)
```
200개 이벤트 저장 = 200번 HTTP 요청 (느림)
```

#### 배치 저장 (After)
```
200개 이벤트 저장 = 1번 HTTP 요청 (빠름)
```

**성능 개선:**
- **네트워크 오버헤드 감소**: 5-10배 개선
- **ES 부하 감소**: Bulk indexing 최적화

---

### 4. 백프레셔 (Backpressure)

큐가 가득 찼을 때 시스템 보호 전략:

#### Reject 전략 (기본)
```kotlin
if (queue.isFull()) {
    return 429 Too Many Requests  // 클라이언트가 재시도
}
```

#### Drop 전략
```kotlin
if (queue.isFull()) {
    log.warn("Dropped event")  // 이벤트 버림
    return 202 Accepted  // 거짓 응답
}
```

---

## 설정 (application.yml)

```yaml
ingest:
  queue:
    capacity: 10000  # 메모리 큐 크기
  batch:
    size: 200  # 배치 크기
    flush-interval-ms: 500  # flush 주기
  backpressure:
    strategy: reject  # 백프레셔 전략 (reject/drop)

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics  # Actuator 엔드포인트
```

---

## API 엔드포인트

### 로그 수집
```http
POST /events
Content-Type: application/json

{
  "service": "api-server",
  "level": "INFO",
  "message": "User login successful",
  "tags": ["auth", "login"]
}
```

**응답:**
- `202 Accepted`: 큐에 성공적으로 삽입
- `429 Too Many Requests`: 큐가 가득 참 (백프레셔)

---

### 로그 검색
```http
GET /events?q=login&service=api-server&level=INFO&size=20&page=0
```

**쿼리 파라미터:**
- `q`: 메시지 검색어 (full-text search)
- `service`: 서비스 필터
- `level`: 로그 레벨 필터 (INFO, WARN, ERROR)
- `from`: 시작 시간 (ISO 8601)
- `to`: 종료 시간 (ISO 8601)
- `size`: 페이지 크기 (기본 20)
- `page`: 페이지 번호 (기본 0)

**응답:**
```json
[
  {
    "eventId": "event-123",
    "service": "api-server",
    "level": "INFO",
    "message": "User login successful",
    "timestamp": "2026-02-02T14:30:00Z",
    "traceId": "trace-456",
    "tags": ["auth", "login"]
  }
]
```

---

### 메트릭 확인
```http
GET /actuator/metrics/ingested_total
GET /actuator/metrics/enqueued_total
GET /actuator/metrics/dropped_total
GET /actuator/metrics/rejected_total
```

---

## 실행 방법

### 1. Elasticsearch 실행
```bash
docker-compose up -d elasticsearch
```

### 2. 애플리케이션 실행
```bash
cd plus
./gradlew bootRun
```

### 3. 로그 전송 테스트
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "service": "test-service",
    "level": "INFO",
    "message": "Test message",
    "tags": ["test"]
  }'
```

### 4. 로그 검색 테스트
```bash
curl "http://localhost:8080/events?q=test&service=test-service"
```

---

## 테스트

### 전체 테스트 실행
```bash
./gradlew test
```

### 테스트 구성
- **Unit Tests**: IngestService, BatchWriter
- **Repository Tests**: LogEventRepositoryImpl (ES 통합)
- **Controller Tests**: EventController (WebTestClient)
- **Integration Tests**: EventControllerIntegrationTest (백프레셔 테스트)

**테스트 원칙:**
- ❌ 딜레이 기반 테스트 금지 (랜덤성)
- ✅ 결정론적 테스트 (saveBulk 직접 호출)
- ✅ 모든 테스트는 독립적으로 실행 가능

---

## 개발 로드맵

### ✅ Day 1: 기본 CRUD
- Elasticsearch 연동
- 로그 단건 저장 (POST /events)
- 로그 검색 (GET /events)

### ✅ Day 2: 비동기 파이프라인
- 메모리 큐 (Kotlin Channel)
- 배치 저장 (BatchWriter)
- 백프레셔 처리 (reject/drop)
- 메트릭 수집 (IngestMetrics)

**성과:**
- 응답 속도: 200ms → 2ms (100배 개선)
- 처리량: 5-10배 향상
- 안정성: 백프레셔 방어

### 🚧 Day 3: 검색/분석 플랫폼 (진행 예정)
- Elasticsearch Bulk API 최적화
- Flush 정책 완성 (Size + Time)
- Search API 고도화 (Filter + Pagination)
- Stats API 추가 (Aggregation)
- 인덱스 설계/매핑 확정
- 장애 처리 (Retry, DLQ)
- Observability 완성

**목표:**
- "검색/분석 플랫폼"으로의 진화
- 운영 레벨 안정성 확보
- 대시보드 데이터 제공

---

## 성능 벤치마크

### Day 1 (동기 방식)
- **응답 시간**: 200ms
- **처리량**: ~50 req/s
- **ES 부하**: 매 요청마다 저장

### Day 2 (비동기 파이프라인)
- **응답 시간**: 2ms (100배 개선)
- **처리량**: ~500 req/s (10배 개선)
- **ES 부하**: 배치 저장으로 감소

---


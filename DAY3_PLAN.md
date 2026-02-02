# Day 3: 검색/분석 플랫폼으로 진화

> "검색/분석 플랫폼"의 진짜 핵심 기능: Aggregation + 시간기반 분석 + 운영성 강화

---

## 목표

Day 2가 "큐 + 배치 + 백프레셔"까지라면, **Day 3는 운영 레벨로 확 올라가는 날**.

코드가 아니라 **"진짜 로그 플랫폼"으로 만드는 작업**.

---

## 핵심 태스크 (우선순위 순)

### 1) 진짜 Bulk Indexing 적용 (Day 2의 완성)

**현재 상태:**
- Day 2는 "배치 저장 구조"를 만들었음
- 하지만 ES Bulk API 최적화가 완벽하지 않을 수 있음

**Day 3 작업:**
- 단건 insert 반복 제거
- BulkRequest로 한번에 write
- NDJSON 형식 최적화

**✅ 완료 기준:**
- 200건 저장이 "1번 요청"으로 들어감
- 처리량이 눈에 띄게 증가
- 로그에서 "Flushed 200 events" 확인

**예상 코드 위치:**
- `LogEventRepositoryImpl.saveBulk()` 최적화
- Bulk API 응답 처리 개선

---

### 2) Flush 정책 완성: Size + Time 둘 다

**현재 상태:**
- Day 2는 size-only일 가능성이 높음 (200개 도달 시만 flush)

**Day 3 작업:**
- `batchSize=200` 도달 시 flush
- `flushInterval=500ms` 지나면 flush
- 두 조건 중 **먼저 도달하는 것**으로 flush

**✅ 완료 기준:**
- 트래픽이 적어도 500ms마다 이벤트가 늦지 않고 들어감
- 트래픽이 많으면 200개마다 즉시 flush

**예상 코드 위치:**
- `BatchWriter.collectBatch()` 로직 개선
- `withTimeoutOrNull()`을 사용한 타임아웃 처리

---

### 3) Search API 고도화: Filter + Pagination

**현재 상태:**
- 기본 검색만 가능 (q 파라미터)

**Day 3 작업:**
검색 플랫폼은 CRUD가 아니라 **"검색 UX"**가 핵심.

추가할 기능:
- `service` term filter
- `level` term filter
- `from~to` timestamp range filter
- pagination (page/size)
- 정렬 (timestamp desc)

**✅ 완료 기준:**
- 로그가 많아도 검색이 안 깨지고 페이지 이동 가능
- 필터 조합이 정확히 동작

**API 예시:**
```http
GET /events?q=login&service=api-server&level=ERROR&from=2026-02-01T00:00:00Z&to=2026-02-03T00:00:00Z&size=20&page=1
```

**예상 코드 위치:**
- `LogEventRepositoryImpl.search()` 쿼리 빌드 개선
- Bool query에 must, filter, range 추가

---

### 4) Stats API 추가 (Kibana 영역의 핵심)

**🌟 Day 3의 꽃**

**현재 상태:**
- 검색만 가능, 통계/집계 없음

**Day 3 작업:**
대시보드에 필요한 통계 데이터를 제공하는 API 추가.

**endpoint 예시:**
```http
GET /events/stats?service=api-server&from=2026-02-01T00:00:00Z&to=2026-02-03T00:00:00Z
```

**추가할 aggregation:**
1. **level별 count** (terms aggregation)
   - INFO: 1000건, WARN: 200건, ERROR: 50건
2. **시간별 이벤트 수** (date_histogram)
   - 시간대별 로그 발생량 그래프용
3. **top N error messages** (terms on keyword)
   - 가장 많이 발생한 에러 메시지 top 10

**✅ 완료 기준:**
- "대시보드에 바로 올릴 수 있는 데이터"가 나온다
- JSON 응답이 Kibana 스타일 통계

**응답 예시:**
```json
{
  "totalEvents": 1250,
  "levelStats": {
    "INFO": 1000,
    "WARN": 200,
    "ERROR": 50
  },
  "timeline": [
    { "timestamp": "2026-02-02T00:00:00Z", "count": 100 },
    { "timestamp": "2026-02-02T01:00:00Z", "count": 150 }
  ],
  "topErrors": [
    { "message": "Connection timeout", "count": 20 },
    { "message": "Database error", "count": 15 }
  ]
}
```

**예상 코드 위치:**
- 신규 `LogEventRepositoryImpl.getStats()` 메서드
- 신규 `EventController.stats()` 엔드포인트
- ES Aggregation API 활용

---

### 5) 인덱스 설계/매핑 확정

**현재 상태:**
- Dynamic mapping (ES가 자동으로 타입 추론)

**Day 3 작업:**
운영 레벨 감성 들어가는 부분.

**확정할 매핑:**
```json
{
  "mappings": {
    "properties": {
      "eventId": { "type": "keyword" },
      "service": { "type": "keyword" },
      "level": { "type": "keyword" },
      "message": {
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "timestamp": { "type": "date" },
      "traceId": { "type": "keyword" },
      "tags": { "type": "keyword" }
    }
  }
}
```

**핵심:**
- `message`: text (full-text 검색) + keyword 서브필드 (집계)
- `timestamp`: date 타입 (range query 최적화)
- `tags`: keyword 배열 (필터, 집계)
- `service/level`: keyword (term filter, aggregation)

**✅ 완료 기준:**
- full-text 검색 + 필터 + 집계가 다 정확히 동작
- 인덱스 템플릿 또는 초기 매핑 스크립트 작성

**예상 코드 위치:**
- `resources/elasticsearch/` 디렉토리에 매핑 JSON
- 애플리케이션 시작 시 인덱스 자동 생성 (선택적)

---

### 6) 장애 처리 기본값 넣기

**현재 상태:**
- 배치 저장 실패 시 로그만 출력

**Day 3 작업:**
운영에서는 무조건 필요.

**추가할 기능:**
1. **Bulk 실패 item 로깅**
   - ES Bulk API 응답에서 실패한 item 파싱
   - 실패 이유와 eventId 로깅
2. **Retry 1회**
   - 실패한 배치를 1회 재시도
3. **실패 누적되면 DLQ (Dead Letter Queue)**
   - 파일 또는 메모리 DLQ에 저장
   - 나중에 수동 처리 또는 재시도

**✅ 완료 기준:**
- ES가 잠깐 죽어도 시스템이 바로 터지지 않는다
- 실패한 이벤트를 추적할 수 있다

**예상 코드 위치:**
- `BatchWriter.flushBatch()` 에러 처리 개선
- 신규 `DeadLetterQueue` 클래스 (파일 또는 메모리)

---

### 7) Observability 최소 완성

**현재 상태:**
- 기본 메트릭만 존재 (ingested, enqueued, dropped, rejected)

**Day 3 작업:**
"운영 감성" 마무리.

**필수 메트릭 추가:**
1. **queue size gauge**: 현재 큐에 몇 개 쌓여있는지
2. **bulk flush count**: 총 flush 횟수
3. **bulk 실패 count**: 실패한 배치 수
4. **ingest rejected(429)**: 이미 있음 (확인)

**로그 개선:**
1. **bulk flush 시 took ms 출력**
   - ES 응답에서 `took` 값 추출
   - "Flushed 200 events to Elasticsearch (took: 123ms)"

**✅ 완료 기준:**
- `actuator/metrics` 보면 병목이 보인다
- 큐가 쌓이는지, flush가 느린지 즉시 파악 가능

**예상 코드 위치:**
- `IngestMetrics`에 gauge, counter 추가
- `BatchWriter.flushBatch()` 로그 개선

---

## 완료 기준 요약

### Goal 1: Bulk API 최적화 ✅
- [ ] 200건이 1번 요청으로 저장됨
- [ ] 처리량 눈에 띄게 증가

### Goal 2: Flush 정책 완성 ✅
- [ ] 200개 도달 시 flush
- [ ] 500ms 타임아웃 시 flush
- [ ] 트래픽 적어도 늦지 않음

### Goal 3: Search API 고도화 ✅
- [ ] service, level, from~to 필터 동작
- [ ] pagination 동작
- [ ] 로그 많아도 검색 안 깨짐

### Goal 4: Stats API 추가 ✅
- [ ] level별 count 집계
- [ ] 시간별 이벤트 수 집계
- [ ] top N error messages 집계
- [ ] 대시보드용 JSON 응답

### Goal 5: 인덱스 설계 확정 ✅
- [ ] message: text + keyword
- [ ] timestamp: date
- [ ] 나머지: keyword
- [ ] full-text + 필터 + 집계 모두 동작

### Goal 6: 장애 처리 ✅
- [ ] Bulk 실패 item 로깅
- [ ] Retry 1회
- [ ] DLQ 구현 (파일/메모리)
- [ ] ES 잠깐 죽어도 시스템 안 터짐

### Goal 7: Observability ✅
- [ ] queue size gauge 추가
- [ ] bulk flush count 추가
- [ ] bulk 실패 count 추가
- [ ] flush took ms 로그 출력
- [ ] actuator/metrics로 병목 파악 가능

---

## 브랜치 전략

```bash
# Day 3 브랜치 생성
git checkout -b feature/search-and-stats

# 작업 완료 후
git add .
git commit -m "feat: Day 3 - Search/Stats API and Observability"
git push origin feature/search-and-stats
```

---

## 예상 파일 변경

### 신규 파일
- `plus/src/main/kotlin/com/log/plus/presentation/dto/StatsResponse.kt`
- `plus/src/main/kotlin/com/log/plus/domain/vo/EventStats.kt`
- `plus/src/main/kotlin/com/log/plus/infrastructure/dlq/DeadLetterQueue.kt`
- `plus/src/main/resources/elasticsearch/log-events-mapping.json`
- `plus/src/test/kotlin/com/log/plus/presentation/controller/StatsControllerTest.kt`

### 수정 파일
- `plus/src/main/kotlin/com/log/plus/presentation/controller/EventController.kt` (stats 엔드포인트 추가)
- `plus/src/main/kotlin/com/log/plus/domain/repository/LogEventRepository.kt` (getStats 메서드 추가)
- `plus/src/main/kotlin/com/log/plus/infrastructure/elasticsearch/LogEventRepositoryImpl.kt` (search 개선, getStats 구현)
- `plus/src/main/kotlin/com/log/plus/infrastructure/worker/BatchWriter.kt` (에러 처리, 로그 개선)
- `plus/src/main/kotlin/com/log/plus/infrastructure/metrics/IngestMetrics.kt` (gauge, counter 추가)
- `plus/src/main/resources/application.yml` (DLQ 설정 추가)

---

## Trade-offs & Decisions

### 1. Stats API 위치
- **선택**: `/events/stats` (RESTful)
- **이유**: 같은 리소스 `/events`의 집계 데이터

### 2. DLQ 구현
- **선택**: 메모리 기반 (파일 선택적)
- **이유**: Day 3 범위 내 구현, 추후 Kafka/SQS로 확장 가능

### 3. Aggregation 범위
- **선택**: level, timeline, top errors
- **이유**: 대시보드 필수 데이터 우선

### 4. Retry 전략
- **선택**: 1회 재시도
- **이유**: 무한 재시도는 위험, exponential backoff는 Day 4 이후

---

## Day 3 이후 로드맵 (참고)

### Day 4: 분산/확장성
- Kafka 연동 (메모리 큐 → Kafka)
- 멀티 인스턴스 배포
- Consumer Group 기반 분산 처리
- 인덱스 샤딩 전략

### Day 5: 고급 분석
- ML 기반 이상 탐지
- 로그 패턴 분석
- 알림 시스템 (Slack, Email)
- Grafana 대시보드 연동

---

## 참고 자료

### Elasticsearch Aggregation API
- [Terms Aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html)
- [Date Histogram Aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html)
- [Composite Aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-composite-aggregation.html)

### Bulk API 최적화
- [Elasticsearch Bulk API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html)
- [Bulk Indexing Best Practices](https://www.elastic.co/guide/en/elasticsearch/reference/current/tune-for-indexing-speed.html)

---

## 시작 전 체크리스트

- [ ] Day 2 코드 모두 커밋됨
- [ ] 모든 테스트 통과 (29 tests)
- [ ] Elasticsearch 실행 중
- [ ] 애플리케이션 정상 실행 확인
- [ ] README.md 검토 완료
- [ ] DAY3_PLAN.md 이해 완료

---

**준비됐으면 Day 3 시작! 🚀**

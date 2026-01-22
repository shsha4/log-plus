```bash
# 인덱스 삭제 (재생성 필요시)
curl -X DELETE "http://localhost:9200/log-events" -u elastic:changeme

# 인덱스 생성
curl -X PUT "http://localhost:9200/log-events" \
  -u elastic:changeme \
  -H "Content-Type: application/json" \
  -d '{
    "mappings": {
      "properties": {
        "eventId": { "type": "keyword" },
        "service": { "type": "keyword" },
        "level": { "type": "keyword" },
        "message": {
          "type": "text",
          "fields": { "keyword": { "type": "keyword" } }
        },
        "timestamp": { "type": "date" },
        "traceId": { "type": "keyword" },
        "tags": { "type": "keyword" }
      }
    }
  }'
```

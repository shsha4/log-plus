```json
curl -X PUT "https://localhost:9200/log-events" \
  -u elastic:changeme \
  --cacert src/main/resources/ca.crt \
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
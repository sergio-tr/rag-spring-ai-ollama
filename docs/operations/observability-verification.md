# Observability verification checklist

Manual verification steps and traceability for metrics, traces, and logs. Re-run after infrastructure or metric name changes.

## References

- Gap notes: [observability-gap-audit.md](observability-gap-audit.md)
- Operator guide: [grafana-observability-guide.md](grafana-observability-guide.md)
- Observability stack: [`observability/README.md`](../../observability/README.md)
- Compose observability: [`docker/compose.obs.yml`](../../docker/compose.obs.yml)
- Backend infra profile: [`rag-service/src/main/resources/application-infra.properties`](../../rag-service/src/main/resources/application-infra.properties)

## Implemented artifacts (traceability)

| Area | Deliverable |
| ------ | ------------- |
| Retrieval | `rag_retrieval_documents_total{operation,bucket}` in `TracedContextRetriever` (bucketed counts; no raw document count as label value) |
| Classifier | `ClassifierInferenceMetricsDecorator`: `rag_classifier_calls_total` with `status` ∈ `success`, `null_result` only |
| HTTP tracing | `RagQueryConfiguration` + `ClassifierServiceClient` using shared `RestTemplate` for W3C propagation |
| Logs | `application-infra.properties`: logging patterns include `trace_id` / `span_id` (MDC) |
| Compose | [`observability/README.md`](../../observability/README.md#start-with-compose) — Start with Compose; backend `SPRING_PROFILES_ACTIVE: docker,infra` in `compose.obs.yml` |
| Dashboards | `observability/grafana/provisioning/dashboards/json/rag-overview.json` (classifier + retrieval panels) |
| Guide | [grafana-observability-guide.md](grafana-observability-guide.md) |
| This checklist | Sections below |

## Manual checklist (reproducible)

Complete in order; mark **OK** when satisfied.

1. **Stack + observability**  
   - From repo: `cd docker` and run `docker compose` with `docker-compose.yml` + `compose.obs.yml` and env files as in [`observability/README.md`](../../observability/README.md).  
   - Confirm containers: `otel-collector`, `jaeger`, `prometheus`, `grafana` (and optional Loki/Promtail if using logs stack).  
   - Confirm backend logs or env show **`docker,infra`** and OTLP endpoints toward the collector.

2. **Telemetry validation**  
   - Follow **Telemetry validation checklist** in [`observability/README.md`](../../observability/README.md#telemetry-validation-checklist) (Jaeger + Grafana RAG Overview).  
   - **OK** if: `rag.query.classify` and `rag.documents.search` appear in a trace for a typical RAG request; RAG Overview panels show movement for query rate/duration, classifier, and retrieval buckets after traffic.

3. **Prometheus series**  
   - Query: `rag_retrieval_documents_total`, `rag_classifier_calls_total` with bounded labels (`operation`, `bucket`, `status`).  
   - **OK** if: no unexpected high-cardinality labels (e.g. raw query strings, project UUIDs as metric tags).

4. **Logs ↔ trace**  
   - After a request, confirm log lines include `trace_id=` / `span_id=` (infra profile).  
   - If Loki is enabled, locate the same trace id in Grafana Explore (see [`grafana-observability-guide.md`](grafana-observability-guide.md)).

5. **Tests**  
   - `cd rag-service && ./mvnw test` passes (includes `TracedContextRetrieverTest`, configuration tests).

## Example PromQL (for reports or screenshots)

```text
sum by (operation, bucket) (rate(rag_retrieval_documents_total{job="backend"}[5m]))
sum by (status) (rate(rag_classifier_calls_total{job="backend"}[5m]))
histogram_quantile(0.95, sum by (le) (rate(rag_query_generate_seconds_bucket{job="backend"}[5m])))
```

## Sample evidence to attach

- One **Jaeger** screenshot or trace ID string showing `rag-backend` + `classifier-service` + `rag.documents.search`.  
- One **Grafana** screenshot of RAG Overview with non-empty classifier and retrieval panels.  
- Optional: one **Loki** query line proving filter by `trace_id`.

**Version note:** record Git commit SHA and image tags used when capturing evidence.

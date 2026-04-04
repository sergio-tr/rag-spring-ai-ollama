# Operator guide — Grafana, Jaeger, and Loki (observability stack)

This guide is for **operators** who need to move from **metrics → traces → logs** when debugging the RAG pipeline. It assumes the stack was started with observability as described in [`observability/README.md`](../../observability/README.md) and [`docker/README.md`](../../docker/README.md).

## Prerequisites

- `observability/.env` created (e.g. `./docker/scripts/create-env-observability.sh`).
- Stack up with `compose.obs.yml` and the same env files as in the observability README (backend `SPRING_PROFILES_ACTIVE=docker,infra`).
- URLs use ports from `.env` (defaults below use typical values).

## 1. Open Grafana and the RAG dashboard

1. Open Grafana: `http://localhost:${GRAFANA_PORT:-3000}` (admin password from `GRAFANA_ADMIN_PASSWORD`).
2. Navigate to **Dashboards** → **RAG Overview** (provisioned JSON: `observability/grafana/provisioning/dashboards/json/rag-overview.json`).
3. Interpret:
   - **RAG query duration (p50/p95/p99):** end-to-end generation latency from Micrometer histogram `rag_query_generate_seconds`.
   - **RAG query rate:** request rate from the same histogram’s `_count` series.
   - **Classifier calls:** `rag_classifier_calls_total` with `status` in `success` or `null_result` (low cardinality; no per-model UUID label).
   - **Retrieval document count:** `rag_retrieval_documents_total` summed by `bucket` (`0`, `1_4`, `5_19`, `20_plus`) and `operation` — shows how many documents fell into each band after retrieval, not raw per-query IDs.

If a panel shows **No data**, confirm Prometheus is scraping `backend:.../actuator/prometheus`, that you issued at least one RAG request, and that the time range includes that traffic.

## 2. From metrics to traces (Jaeger)

1. Open Jaeger UI: `http://localhost:${JAEGER_UI_PORT:-16686}` (or use the Jaeger datasource inside Grafana if configured).
2. Select service **`rag-backend`**, choose a recent time range, **Find Traces**.
3. Open a trace that matches your test window. You should see a coherent chain including:
   - `rag.query.classify` (downstream HTTP to classifier when used)
   - `rag.documents.search` (retrieval)
   - `rag.query.generate` (generation), depending on the code path
4. Classifier spans appear under service **`classifier-service`** when the pipeline calls the classifier; propagation uses W3C trace context on the HTTP client (`RestTemplate` from `RestTemplateBuilder` in configuration).

**Tip:** Copy a **trace ID** from Jaeger for log correlation (section 4).

## 3. Prometheus (optional deep dive)

1. Open Prometheus: `http://localhost:${PROMETHEUS_PORT:-9090}`.
2. Example queries:
   - `sum by (status) (rate(rag_classifier_calls_total{job="backend"}[5m]))`
   - `sum by (operation, bucket) (rate(rag_retrieval_documents_total{job="backend"}[5m]))`
   - `histogram_quantile(0.95, sum by (le) (rate(rag_query_generate_seconds_bucket{job="backend"}[5m])))`

Use **Graph** vs **Table** to verify labels and cardinality stay bounded (no unbounded IDs in label values).

## 4. Logs and Loki (trace correlation)

1. With `compose.logs.yml` (or full stack including Loki/Promtail), open Grafana → **Explore** → **Loki**.
2. Backend logs (profile **`infra`**) include MDC-style fields in the pattern: `trace_id=...` and `span_id=...` from `application-infra.properties` logging patterns.
3. In Explore, filter with LogQL, e.g. `{job="backend"}` plus a line filter on a **trace_id** value you copied from Jaeger.

If logs do not show `trace_id`, confirm `SPRING_PROFILES_ACTIVE` includes **`infra`** and that Micrometer tracing is active so `traceId`/`spanId` populate the logging MDC.

## 5. Troubleshooting

| Symptom | What to check |
|--------|----------------|
| No traces in Jaeger | `otel-collector` and `jaeger` up; `OTEL_EXPORTER_OTLP_ENDPOINT` points to collector; backend `MANAGEMENT_OTLP_TRACING_ENDPOINT` ends with `/v1/traces`. |
| Classifier not in trace | Classifier container up; network from `backend` to `classifier-service`; look for `rag.query.classify` and client HTTP spans. |
| Grafana panels empty | Prometheus targets **UP**; job label `backend`; time range; traffic generated. |
| High cardinality warnings | Do not add raw query text, project UUIDs, or per-request IDs as metric labels; use bounded `bucket`/`operation`/`status` only. |

## Related

- Full conventions and checklist: [`observability/README.md`](../../observability/README.md) (including **Telemetry validation checklist**).
- Architecture context: [integration-flows.md](../architecture/integration-flows.md), [deployment-model.md](../architecture/deployment-model.md).
- Verification checklist: [observability-verification.md](observability-verification.md).

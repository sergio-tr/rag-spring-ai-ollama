# Operations runbook (minimal)

Quick reference for development, demo, and thesis validation. For compose ports and env vars, see [README.md](README.md).

## View a distributed trace

1. Start the stack with OTLP enabled (e.g. `docker/compose.obs.yml` and backend profile `infra`, or `docker/compose.rag-dev-obs.yml`).
2. Open Jaeger UI (default host port `16686`).
3. Select service `rag-backend` or `classifier-service` and search by trace ID from logs (`trace_id` in structured logs).

## Classifier unavailable

- Check Spring Actuator readiness: `GET /actuator/health` includes `classifier` when enabled.
- Verify `RAG_CLASSIFIER_SERVICE_URL` / `rag.classifier.service.url` points to the Python service.
- Classifier logs: ensure `OTEL_EXPORTER_OTLP_ENDPOINT` matches the collector if tracing export is on.

## Readiness vs liveness

- **Readiness** (`/actuator/health` group `readiness`): DB, Ollama, classifier (configurable). Use before routing traffic.
- **Liveness**: process up; rely on default health when probes are enabled (`management.endpoint.health.probes.enabled` in profile `infra`).

## Logs and correlation

- With profile `infra`, console/file patterns include `trace_id` and `span_id` aligned with Micrometer tracing (`application-infra.properties`).
- Async jobs log `async_task_start` with `taskId` and `taskType`; account jobs log `account_export_job_start` / `account_deletion_job_start` with `taskId` and `userId` (UUID only).

## Rollback observability

- Disable OTLP export: `management.otlp.tracing.export.enabled=false`, `management.otlp.metrics.export.enabled=false` (defaults in base `application.properties`).

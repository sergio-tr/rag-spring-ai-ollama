# Observability: design vs repository (notes)

**References:** [grafana-observability-guide.md](grafana-observability-guide.md), [observability/README.md](../../observability/README.md), [observability-verification.md](observability-verification.md).

| Design area | Expected (README / operator guide) | Repository |
|-------------|-----------------------------------|------------|
| Retrieval metrics | Histogram/counter for chunk or document counts after retrieval; low cardinality | `TracedContextRetriever`: `rag_retrieval_documents_total{operation,bucket}` |
| Classifier Micrometer | `rag_classifier_calls_total` with bounded `status` | `ClassifierInferenceMetricsDecorator` |
| Trace propagation RAG → classifier | W3C context on HTTP to classifier | `ClassifierServiceClient` uses shared `RestTemplate` for propagation |
| Logs ↔ trace (Loki) | `trace_id` / `span_id` in logs when infra profile active | `application-infra.properties` logging pattern |
| Compose observability | `compose.obs.yml` + `docker,infra` + `.env` consistent | Documented; manual steps in [observability-verification.md](observability-verification.md) |
| Grafana RAG overview | Panels match Micrometer names | `observability/grafana/provisioning/dashboards/json/rag-overview.json` |
| Operator guide | Metrics → trace → logs path | [grafana-observability-guide.md](grafana-observability-guide.md) |

**Hexagonal rule:** Instrumentation in **infrastructure** and **configuration** (beans), not REST controllers.

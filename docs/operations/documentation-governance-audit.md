# Documentation governance audit

**Purpose:** Record how this repository applies [documentation governance strategy](../development/documentation-governance-strategy.md) and [documentation guidelines](../development/documentation-guidelines.md).

## Canonical hub

| Area | Canonical location | Notes |
| ------ | ------------------- | -------- |
| Documentation entry | [docs/README.md](../README.md) | Tables for workflows, links to `docs/operations/`, [testing overview](../testing/README.md), ADRs. |
| Operational deploy / VM | [runbook-docker-vm.md](runbook-docker-vm.md), [deploy-workflow-audit.md](deploy-workflow-audit.md), [docker/README.md](../../docker/README.md) | **Single story** for SSH + Compose; avoid parallel narratives. |
| Observability stack | [observability/README.md](../../observability/README.md) | Metrics, traces, Compose overlays; **not** duplicate VM deploy (see [Production / VM](../../observability/README.md#production--vm-where-to-read-what) there). |

## Rules

- **One operator path:** deploy procedures live under **`docs/operations/`**; observability mechanics stay under **`observability/`**.

## Duplicate-risk topics (resolved)

| Topic | Avoid duplicating in | Use instead |
| ------- | ---------------------- | ------------- |
| VM deploy steps | `observability/README.md` body | [runbook-docker-vm.md](runbook-docker-vm.md) |
| Grafana/Jaeger walkthrough | `docker/README.md` long-form | [grafana-observability-guide.md](grafana-observability-guide.md) |

## Related

- [release-readiness-checklist.md](release-readiness-checklist.md)

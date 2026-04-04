# Operations documentation

Conceptual and procedural documentation for **deploy**, **VM**, **CI/CD gates**, and **observability** from an operator perspective. Executable commands and env layout remain in [docker/README.md](../../docker/README.md), [docker/scripts/README.md](../../docker/scripts/README.md), and [rag-service/README.md](../../rag-service/README.md) where appropriate.

## Documents

- [Deploy workflow audit](deploy-workflow-audit.md) — `deploy.yml` gates, secrets, limitations.
- [Runbook — Docker on Linux VM](runbook-docker-vm.md) — SSH, Compose, env files, verification.
- [Azure / VM parameterization](azure-vm-parameterization.md) — DNS, networking without fixed IPs.
- [Release readiness checklist](release-readiness-checklist.md) — CI/CD and deploy criteria.
- [Documentation governance audit](documentation-governance-audit.md) — hub alignment, duplicate-risk topics.
- [Grafana / Jaeger / Loki operator guide](grafana-observability-guide.md) — metrics → traces → logs.
- [Observability gap notes](observability-gap-audit.md) — design vs repository.
- [Observability verification](observability-verification.md) — checklist and traceability.
- [Environments](environments.md) — dev vs Docker vs VM notes for Spring and Ollama URLs.
- [Release notes template](release-notes-template.md) — tag and GitHub Release text.

## Related

- Hub: [../README.md](../README.md)
- Observability stack (Compose, dashboards): [../../observability/README.md](../../observability/README.md)

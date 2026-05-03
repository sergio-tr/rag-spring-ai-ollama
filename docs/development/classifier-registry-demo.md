# Classifier registry and activation (demo)

## Prerequisites

- Stack running with **PostgreSQL**, **rag-service**, and **classifier-service** reachable from the BFF (see [`docker/README.md`](../docker/README.md) and [`classifier-service/README.md`](../classifier-service/README.md)).
- Webapp logged in; at least one **project** exists (use as “active project” in the UI).
- Classifier train returns JSON with **`modelId`** (inference tag for `POST /classify`) — see [classifier-service README](../../classifier-service/README.md) and [DATA_MODEL.md](../architecture/DATA_MODEL.md) (`classifier_model`).

## Happy path (manual)

1. **Train** — Lab → Classifier: upload training `.xlsx`, run train (async). Wait until the job completes successfully.
2. **Registry** — The **Registered models** card lists a row whose **Inference tag** equals the `modelId` from the train response (stored in `classifier_model.artifact_path`).
3. **Evaluate (optional)** — Run evaluate against that tag; metrics (`accuracy`, `f1`) may update the same row when the tag matches.
4. **Activate** — Choose the active project, click **Activate for project**, confirm. The backend merges `classifierModelId` into that project’s RAG JSON (does not replace other keys).
5. **Verify** — Inspect project config (Settings) or run classify/chat and confirm the pipeline resolves the new tag (see [`0001-lab-promotion-modes.md`](../adr/0001-lab-promotion-modes.md): activation is explicit, not silent promotion).

## API (same contract as UI)

| Method | Path | Purpose |
| -------- | ------ | --------- |
| `GET` | `{product}/lab/classifier/models` | List registered models for the current user |
| `POST` | `{product}/lab/classifier/models/{modelId}/activate` | Body: `{ "projectId": "<uuid>" }` — sets active row and merges `classifierModelId` |

## Logs (observability)

- `ClassifierModelRegistryService`: structured `log.info` on register (`Registered classifier_model …`), eval enrich, and activate (`Activated classifier model row …` with `projectId`, `userId`, `inferenceTag`).

## Automated checks

- Backend: `cd rag-service && ./mvnw verify` (includes registry service and controller tests).
- Optional Playwright (requires classifier configured in the fullstack env): set `E2E_CLASSIFIER_REGISTRY=1` and run fullstack E2E — asserts the **Registered models** section heading is visible (see [`webapp/e2e/research/classifier-lab.spec.ts`](../../webapp/e2e/research/classifier-lab.spec.ts)).

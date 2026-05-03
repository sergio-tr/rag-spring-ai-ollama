# Knowledge system model

**Purpose:** Canonical **target** model for how **workspace documents** become **retrievable knowledge**: artefacts, indices, snapshots, reindex, and materialization strategies. Physical tables stay defined in [DATA_MODEL.md](DATA_MODEL.md) and Flyway ([ADR 0011](../adr/0011-knowledge-system-artifacts-snapshots-and-materialization.md)).

**Related:** [target-architecture.md](target-architecture.md), [configuration-resolution-model.md](configuration-resolution-model.md), [rag-runtime-architecture.md](rag-runtime-architecture.md).

## WorkspaceDocument

A **WorkspaceDocument** is a document **owned by a project** in the product sense (PDF or other supported types ingested into the corpus). It is the **root aggregate** for downstream artefacts. Naming aligns with product language; ER table names remain as in [DATA_MODEL.md](DATA_MODEL.md).

## Artefact chain

| Stage | Artefact (conceptual) | Role |
| ------- | ------------------------ | ------ |
| Parse | Parse output | Normalized text / structure from source file. |
| Metadata | Metadata record | Extracted or computed fields (e.g. meeting attributes) for filtering and tools. |
| Chunk | Chunk set | Text segments for embedding and retrieval. |
| Index | Index entries | Vector index + any auxiliary indexes for structured search. |

## Index snapshots

An **index snapshot** is a **point-in-time** view of the index state for a corpus scope (e.g. project) usable for **reproducible retrieval** in lab runs and for rollback reasoning after bulk reindex. Representation may be logical (version id + embedding model id) rather than a full copy.

## Reindex events

A **reindex event** records that (re)building indices or embeddings was required because of document changes, **embedding model** changes, chunking policy changes, or **compatibility-breaking** config updates flagged by `ReindexImpactAnalyzer`.

## Materialization strategies

| Strategy | When to use (architectural intent) |
| ---------- | ----------------------------------- |
| **Document-level** | Whole-document embedding or retrieval when chunks are not required or for coarse routing. |
| **Chunk-level** | Default fine-grained RAG when passages must align to citations. |
| **Hybrid** | Combines document-level signals (e.g. summary) with chunk retrieval for answer quality. |
| **Structured search** | Querying **metadata / structured fields** (SQL or dedicated search) complementary to vector retrieval; not a substitute for the conceptual chain above. |

**Structured search vs vector retrieval:** structured search answers **field-bound** questions (counts, filters, boolean metadata); vector retrieval answers **semantic** similarity over text chunks. The runtime may combine both inside `RetrievalPipeline`.

## Relationship to other subsystems

- **Workspace / Product** owns upload and lifecycle triggers.
- **Runtime Configuration** may change chunking, embedding model, or tool visibility → triggers `ReindexImpactAnalyzer`.
- **RAG Runtime Engine** consumes read-only knowledge views during `RetrievalPipeline`.

## Alignment with the repository

### Backend product development

- **Canonical write path:** `KnowledgePipelineOrchestrator` (with `KnowledgeIndexingService` as stage helper) is the only production path for `document_artifact` inserts and corpus `vector_store` writes; `KnowledgeSnapshotService` owns snapshot rows, membership on activation, and vector deletes by `indexSnapshotId`.
- **Orchestrated read path:** product RAG reads `vector_store` / `document_artifact` for query execution through `SnapshotCorpusAssembler` (full-corpus workflow) and `AdvancedRetrievalPipeline` (dense RAG workflows), constrained to ACTIVE `knowledge_index_snapshot` ids from `KnowledgeRuntimeSnapshotSelector` (`indexSnapshotId ∈ orderedSnapshotIds` when ids are non-empty). Hybrid retrieval uses PostgreSQL full-text search on `vector_store.content` (generated `tsvector` + GIN index; see migration `V29__vector_store_fts.sql`).
- **REST:** `${rag.api.product-base-path}/projects/{projectId}/knowledge/*` — ingest, `POST …/rebuild/preview`, `POST …/rebuild/execute`, legacy `POST …/reindex` (thin alias to execute-with-defaults), list/detail snapshots with `corpusScope` and optional `conversationId` per scope rules ([rag-service README](../../rag-service/README.md)).
- **Persistence:** Tables and JSONB rules are summarized in [DATA_MODEL.md — Section 6.2](DATA_MODEL.md#dm-s6-2); `reindex_event` rows are created/updated through `ReindexService` with mandatory `resolved_config_snapshot_id` (V27). Every `knowledge_index_snapshot` row carries `resolved_config_snapshot_id` + `resolved_config_hash` (V28). Corpus ingestion persists a default `resolved_config_snapshot` row (without `knowledgeBuildProjection`) so first-time indexing satisfies linkage.

### Configuration integration

- **Single projection path:** `KnowledgeBuildProjection` is built only via `KnowledgeBuildProjectionMapper` from `ResolvedRuntimeConfig` (`ConfigResolverService.preview` / `resolve`) or from persisted `resolved_config_snapshot.payload_jsonb.knowledgeBuildProjection` on pin-by-id execute (no `ResolvedRuntimeConfig` deserialized from the DB for that path).
- **Single reindex decision:** `KnowledgeConfigurationIntegrationService.computeReindexDecision` is shared by preview and execute; `ReindexService` does not re-derive `ReindexImpact` from raw JSON.
- **Domain:** `com.uniovi.rag.domain.knowledge` holds records and enums (no JPA); entity mapping uses `WorkspaceDocumentMapper` and `KnowledgeIndexSnapshotMapper` / `ReindexEventMapper` at the infrastructure boundary.

### Still out of scope (later phases)

- **Structured search execution** over stored projections (METADATA JSONB is persisted in 3.1; query execution is not).
- Broader **automated reindex** from arbitrary non-knowledge callers (use `KnowledgeConfigurationIntegrationService` / product knowledge routes where config-aware behaviour is required).

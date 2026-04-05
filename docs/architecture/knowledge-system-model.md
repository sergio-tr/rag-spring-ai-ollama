# Knowledge system model

**Purpose:** Canonical **target** model for how **workspace documents** become **retrievable knowledge**: artefacts, indices, snapshots, reindex, and materialization strategies. Physical tables stay defined in [DATA_MODEL.md](DATA_MODEL.md) and Flyway ([ADR 0011](../adr/0011-knowledge-system-artifacts-snapshots-and-materialization.md)).

**Related:** [target-architecture.md](target-architecture.md), [configuration-resolution-model.md](configuration-resolution-model.md), [rag-runtime-architecture.md](rag-runtime-architecture.md).

## WorkspaceDocument

A **WorkspaceDocument** is a document **owned by a project** in the product sense (PDF or other supported types ingested into the corpus). It is the **root aggregate** for downstream artefacts. Naming aligns with product language; ER table names remain as in [DATA_MODEL.md](DATA_MODEL.md).

## Artefact chain

| Stage | Artefact (conceptual) | Role |
|-------|------------------------|------|
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
|----------|-----------------------------------|
| **Document-level** | Whole-document embedding or retrieval when chunks are not required or for coarse routing. |
| **Chunk-level** | Default fine-grained RAG when passages must align to citations. |
| **Hybrid** | Combines document-level signals (e.g. summary) with chunk retrieval for answer quality. |
| **Structured search** | Querying **metadata / structured fields** (SQL or dedicated search) complementary to vector retrieval; not a substitute for the conceptual chain above. |

**Structured search vs vector retrieval:** structured search answers **field-bound** questions (counts, filters, boolean metadata); vector retrieval answers **semantic** similarity over text chunks. The runtime may combine both inside `RetrievalPipeline`.

## Relationship to other subsystems

- **Workspace / Product** owns upload and lifecycle triggers.
- **Runtime Configuration** may change chunking, embedding model, or tool visibility → triggers `ReindexImpactAnalyzer`.
- **RAG Runtime Engine** consumes read-only knowledge views during `RetrievalPipeline`.

## Alignment with the repository (current state)

### What already exists

- Document ingestion (`ProjectDocumentIngestionService`, extractors), chunk and vector storage per [DATA_MODEL.md](DATA_MODEL.md).
- Metadata-heavy tools and retrievers (`MetadataMinuteDocumentService`, metadata retrievers, meeting-minutes adapters).
- pgvector-backed retrieval paths.

### What is partial

- **Index snapshot** as an explicit versioned concept for lab reproducibility may be partial or implicit.
- **Reindex events** may not be logged as first-class audit records.
- **Materialization strategy** may be implicit in code paths rather than explicitly selected per project/preset.

### What is still missing

- Uniform API for **structured search** vs **vector** retrieval with clear contracts to `RetrievalPipeline`.
- Automated **reindex** orchestration tied to config changes per target model.
- Explicit **hybrid** materialization path documented in code and operator runbooks (module READMEs).

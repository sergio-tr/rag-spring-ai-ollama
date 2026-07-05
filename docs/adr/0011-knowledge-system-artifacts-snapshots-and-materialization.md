# ADR 0011 - Knowledge system artefacts, snapshots, and materialization

## Status

Accepted

## Context

RAG quality depends on **ingestion**, **chunking**, **embedding**, and **retrieval** contracts. Without frozen concepts, operators and researchers cannot reason about **reindex**, **reproducibility**, or **structured** vs **vector** search.

## Decision

1. The **knowledge system** follows [knowledge-system-model.md](../architecture/knowledge-system-model.md): **`WorkspaceDocument`**, **parse / metadata / chunk / index** artefacts, **index snapshots**, **reindex events**, and **materialization strategies** (document-level, chunk-level, hybrid, structured search).
2. Physical schema remains authoritative in [DATA_MODEL.md](../architecture/DATA_MODEL.md); this ADR freezes **semantic** roles, not every column name.
3. **`ReindexImpactAnalyzer`** output must drive explicit **reindex** planning when config or knowledge definitions change ([ADR 0007](0007-capability-groups-and-compatibility-rules.md)).

## Consequences

- Ingestion and retrieval changes must state which **artefacts** and **strategies** are affected.
- Lab reproducibility should reference **snapshots** where vector state matters.
- Structured search features must not silently replace vector retrieval without documentation.

## References

- [knowledge-system-model.md](../architecture/knowledge-system-model.md)
- [DATA_MODEL.md](../architecture/DATA_MODEL.md)
- [0007-capability-groups-and-compatibility-rules.md](0007-capability-groups-and-compatibility-rules.md)

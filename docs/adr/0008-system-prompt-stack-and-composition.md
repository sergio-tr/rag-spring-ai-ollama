# ADR 0008 - System prompt stack and composition

## Status

Accepted

## Context

Effective LLM behaviour depends on **system** and **user** inputs. Without a frozen composition model, prompts drift between UI, database, and ad-hoc code, breaking reproducibility between Product and Lab.

## Decision

1. At the architectural level, each call uses **`effective system prompt` + `user query`**.
2. The **`effective system prompt`** is composed of **four** layers: **base**, **account-level**, **project-level**, **workflow/preset** ([configuration-resolution-model.md](../architecture/configuration-resolution-model.md)).
3. The **effective system prompt** is part of **resolved runtime configuration** semantics, not a UX-only concern; **`SystemPromptComposer`** is the conceptual composer.
4. **Phase-I** studies may treat prompt layers as **experimental variables**; **later RAG studies** may fix a **stabilized** system prompt as a **baseline** while varying retrieval, judges, or routing - without changing the four-layer architecture.

## Consequences

- Implementation must centralize prompt assembly accordingly; regression tests for prompts should target the composer seam.
- Documentation for operators must distinguish **target** prompt resolution from **editor** UX.

## References

- [configuration-resolution-model.md](../architecture/configuration-resolution-model.md)
- [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md)

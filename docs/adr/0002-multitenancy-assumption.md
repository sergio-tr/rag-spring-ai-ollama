# ADR 0002 — Multi-tenancy and data isolation assumption

## Status

Accepted

## Context

The platform serves multiple **users** with **projects**, **documents**, and **configurations** stored in a **single** PostgreSQL database. Stakeholders may ask for “multi-tenant SaaS” isolation (separate databases, strict row-level security per tenant, or org-wide boundaries). The implemented model must be described clearly to avoid overstating guarantees.

## Decision

1. **Isolation model**  
   Logical isolation is enforced by **application rules** and **foreign keys**: a **user** owns **projects** (`projects.owner_id` → `users.id`); project-scoped entities reference `project_id` and/or `user_id` as defined in migrations. There is **no** separate database or schema per external tenant in the current design.

2. **Not in scope (current product)**  
   **Strong multi-tenancy** (e.g. dedicated deployment per customer, mandatory row-level security policies for arbitrary tenants, or cross-tenant admin without explicit support tooling) is **out of scope** unless added by a future ADR and implementation.

3. **ADMIN vs USER**  
   Role **ADMIN** can manage global policies (e.g. allowlist) via `/api/admin/**`. That is **role-based** access, not a second tenant dimension in the data model.

4. **Configuration resolution**  
   Effective RAG parameters are computed from system → user → project layers (see [DATA_MODEL.md §6](../architecture/DATA_MODEL.md)); this remains **per authenticated user and owned project**, consistent with the isolation above.

## Consequences

- Security reviews focus on **authorization** (users access only their projects unless ADMIN) and **correct FK usage**, not on DB-level tenant IDs beyond `user_id` / `owner_id`.
- Scaling or compliance needs for **hard** multi-tenancy require a new ADR and likely schema or deployment changes.
- Documentation and thesis scope should not claim isolated “tenants” beyond this model without updating this ADR.

## References

- [DATA_MODEL.md](../architecture/DATA_MODEL.md)
- [conceptual-model.md](../domain/conceptual-model.md)
- [conceptual-model.md](../domain/conceptual-model.md) (identity and project containers)
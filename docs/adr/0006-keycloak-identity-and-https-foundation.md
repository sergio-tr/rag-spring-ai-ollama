# ADR 0006 - Keycloak identity and HTTPS foundation

## Status

Accepted

## Context

The platform requires **serious security** for a multi-user product and defensible operations. JWT-based integration with Spring exists today; stakeholders closed **Keycloak** as the identity provider and **HTTPS** as the transport foundation for target deployments.

## Decision

1. **Keycloak** is the chosen **external identity provider** for Identity & Access in the target architecture ([target-architecture.md](../architecture/target-architecture.md)).
2. **HTTPS** is mandatory for **target** deployments; **TLS termination** (reverse proxy vs application) is a structural choice documented here at ADR level - concrete compose/nginx layout lives in module READMEs as implementation evolves.
3. Promotion of this ADR does **not** by itself complete integration work; it **freezes** the direction of travel.

## Consequences

- Docker, Spring Security, and webapp auth flows will converge on Keycloak-compatible flows over time.
- Non-HTTPS dev shortcuts may remain **local-only** but must be clearly separated from target deployment narrative.
- Operational runbooks under `docker/` and `operations/` gain Keycloak/HTTPS as first-class concerns in later roadmap blocks.

## References

- [target-architecture.md](../architecture/target-architecture.md)
- [integration-flows.md](../architecture/integration-flows.md)
- [deployment-model.md](../architecture/deployment-model.md)

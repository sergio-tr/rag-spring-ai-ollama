# Maven and JaCoCo inventory (rag-service)

## Maven layout

There is **exactly one** [`pom.xml`](../../rag-service/pom.xml) in this repository (`rag-service/pom.xml`).

## Surefire

[`maven-surefire-plugin`](../../rag-service/pom.xml) configures:

- `argLine`: disables CDS (`-Xshare:off`), merges JaCoCo `@{argLine}`, and attaches **Mockito** as a Java agent (inline mock maker compatibility).
- There is **no** `<excludes>` list for test classes in the current POM.

## JaCoCo plugin

| Setting | Value |
|---------|--------|
| Plugin | `jacoco-maven-plugin` **0.8.12** |
| `prepare-agent` | default execution |
| `report` | bound to **`test`** phase → XML for Sonar |
| `check` | bound to **`verify`** phase |

### Bundle rule (global gate)

| Element | Counter | Limit |
|---------|-----------|--------|
| `BUNDLE` | `LINE` / `COVEREDRATIO` | **minimum 0.80** |

### `<excludes>` (verbatim patterns)

Production bytecode matching these **Ant-style** paths is excluded from the JaCoCo **bundle** ratio:

1. `com/uniovi/Application.class`
2. `com/uniovi/rag/domain/entity/**`
3. `com/uniovi/rag/service/document/**`
4. `com/uniovi/rag/application/service/knowledge/**`
5. `com/uniovi/rag/service/retriever/**`
6. `com/uniovi/rag/service/evaluation/**`
7. `com/uniovi/rag/tool/**`
8. `com/uniovi/rag/service/analyser/**`
9. `com/uniovi/rag/service/extraction/**`
10. `com/uniovi/rag/service/ranker/**`
11. `com/uniovi/rag/application/service/evaluation/**`
12. `com/uniovi/rag/infrastructure/persistence/traceregressionsuitedefinition/**`

### Failsafe

No `maven-failsafe-plugin` is configured for separate IT execution; integration-style tests run on the **Surefire** classpath in this project.

## Parity

When you change `<excludes>`, update **`sonar.coverage.exclusions`** in [`sonar-project.properties`](../../sonar-project.properties) for the same **Java** intent — see [coverage-strategy.md](coverage-strategy.md).

## Master exclude narrative

Human-readable rationale, wave history, and residual roadmap: [../coverage/jacoco-coverage-target-ledger.md](../coverage/jacoco-coverage-target-ledger.md).

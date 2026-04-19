# Coverage strategy: global bundle vs new code

This document separates **two different numbers** teams often confuse.

## Concepts

| Concept | Meaning | Enforcement |
|---------|---------|-------------|
| **Global Java coverage (bundle)** | JaCoCo **LINE** ratio on bytecode **after** `<excludes>` in [`rag-service/pom.xml`](../../rag-service/pom.xml) | **`jacoco:check`** at end of `./mvnw verify` — **minimum 0.80** |
| **New / changed code (leak period)** | Sonar **New Code** coverage and issues on PRs / quality gate | **Sonar Cloud Quality Gate** (see [sonar-baseline-record.md](sonar-baseline-record.md)) |
| **Classifier** | pytest-cov aggregate with `fail_under = 80` | [`.coveragerc`](../../classifier-service/.coveragerc) |
| **Webapp** | Vitest thresholds on **included** globs | [`webapp/vitest.config.ts`](../../webapp/vitest.config.ts) |

## Excludes: risk, not “free coverage”

JaCoCo `<excludes>` remove lines from the **denominator** of the **bundle** ratio. They **do not** eliminate product risk in excluded packages — they keep a **single** aggregate gate workable while tests are added incrementally.

**Parity:** every intentional Java exclude in the POM should have a matching **intent** in `sonar.coverage.exclusions` ([`sonar-project.properties`](../../sonar-project.properties)). The [Maven inventory](maven-jacoco-inventory.md) lists POM patterns; Sonar adds a few extra file-level patterns (e.g. TS/Python noise) — see the matrix in [README.md](README.md).

## Operational rule (FD-coverage-dual)

- **Do not** merge if **`./mvnw clean verify`** fails **`jacoco:check`**, unless the team documents a **time-boxed exception** in the exclusion matrix with a remediation date.
- **Do not** merge if **Sonar Quality Gate** fails on the PR, unless the failure is **waived** by policy (security / release management) and recorded.

## Related

- Ledger and roadmap: [../coverage/jacoco-coverage-target-ledger.md](../coverage/jacoco-coverage-target-ledger.md)
- Commands: [../testing/baseline-runbook.md](../testing/baseline-runbook.md)

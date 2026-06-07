# Final evaluation synthesis (closing package)

**Wave references:** [`wave-pilot.md`](wave-pilot.md) (W-PILOT-001), [`wave-comparative.md`](wave-comparative.md) (W-COMP-001).  
**Design reference:** [`experimental-design-matrix.md`](experimental-design-matrix.md).  
**Inventory snapshot:** [`inventory-repository-state.md`](inventory-repository-state.md).

---

## 1. Executive summary *(fill after comparative runs)*

| Item | Status |
| --- | --- |
| RQ1 / H1 conclusion | `TBD` — requires Lab exports for rows A2 and A1. |
| RQ2 / H2 conclusion | `TBD` — requires Lab exports for rows A3 and A4. |
| Latency budget `L` (ms p95) | `TBD` — set from micro-benchmark or Lab timing policy. |

---

## 2. Results table *(placeholder structure)*

| Row | Benchmark | Config / k | Primary DV | p95 latency (if measured) |
| --- | --- | --- | --- | --- |
| A2 | `LLM_JUDGE_QA` | — | `TBD` | `TBD` |
| A1 | `RAG_PRESET_END_TO_END` | snapshot `TBD` | `TBD` | `TBD` |
| A3 | `EMBEDDING_RETRIEVAL` | `k1` | `TBD` | `TBD` |
| A4 | `EMBEDDING_RETRIEVAL` | `k2` | `TBD` | `TBD` |

---

## 3. Threats to validity

| Threat | Mitigation used | Residual risk |
| --- | --- | --- |
| **Dataset bias** (minutes-only, language) | Manifest + domain notes in pilot sheet | External validity limited — state clearly in external write-ups. |
| **Non-reproducibility** (Ollama, temperature) | Freeze model tags; `N≥3` if stochastic | Residual variance — report spread. |
| **Concurrent engineering change** | SHA freeze + wave ids | Low if protocol followed; high if hotfixes unlogged. |
| **Metric mismatch** | Lab for quality; micro-benchmark for latency only | Low if tables never mix roles. |
| **Lab vs product drift** | ADR 0009 + explicit parity checks *(inventory “to verify”)* | Medium until parity rows executed. |

---

## 4. Metric limitations (normative statements)

1. **Python micro-benchmarks** use **estimated** tokens (`estimated: true` in schema) — fine for **rough** cost/latency narratives; not for tokenizer-accurate billing claims.
2. **Gatling** measures **load** — not interchangeable with single-host micro-benchmark samples.
3. **JaCoCo / coverage** (`mvn verify`) prove **tested code quality**, not **RAG answer quality**.

---

## 5. Artefact index *(update paths when exports exist)*

| Artefact | Wave | Location | `sha256` |
| --- | --- | --- | --- |
| Lab export (A1) | W-COMP-001 | `TBD` | `TBD` |
| Lab export (A2) | W-COMP-001 | `TBD` | `TBD` |
| Lab export (A3–A4) | W-COMP-001 | `TBD` | `TBD` |
| Maven verify log | W-PILOT-001 / W-COMP-001 | local `rag-service/target/` | N/A |
| Micro-benchmark JSON | optional | `TBD` | validate vs `benchmark-report-v1` |

---

## 6. Failure conditions (formal review)

Per plan — declare failure and revise before strong claims if:

- More than **50%** of comparative rows lack manifest + SHA.
- Any “better than baseline” claim lacks **B-SUB-X** row.
- Answer-quality claims cite only micro-benchmark token lines.
- Documented exports contradict narrative without errata.

---

## 7. Official documentation pointers

- Spring Boot: https://docs.spring.io/spring-boot/documentation.html  
- Spring AI reference: https://docs.spring.io/spring-ai/reference/  
- Ollama API / models: https://github.com/ollama/ollama/blob/main/docs/api.md  
- SonarCloud quality gates: https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/defining-quality-gates/

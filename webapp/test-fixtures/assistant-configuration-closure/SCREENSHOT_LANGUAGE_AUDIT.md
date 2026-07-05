# Screenshot Language Audit

Recorded: 2026-06-29  
Locale: `/en/` | Theme: light | Viewport: 1440×900

## 1. Screenshots reviewed

| Screenshot | Status | Notes |
|------------|--------|-------|
| `01_assistant_configuration_overview.png` | Accepted | Assistant configuration compact summary; technical details collapsed |
| `02_assistant_instructions.png` | Accepted | Three editable instruction layers; English labels |
| `03_model_configuration.png` | Accepted | "Configured model provider" product label; no raw enum |
| `04_retrieval_settings.png` | Accepted | Retrieval parameters visible; embedding warning is user-facing, not internal ID |
| `05_conversation_memory_and_clarification.png` | Accepted | Conversation memory + Clarification sections |
| `06_evaluation_setup.png` | Accepted | Evaluation Lab overview; no preset codes |
| `07_single_model_evaluation.png` | Accepted | Single-model LLM evaluation page |
| `08_evaluation_results_exports.png` | Accepted | Results/export region; no live campaign data fabricated |
| `09_admin_model_catalog.png` | Accepted | "Configured model catalog" heading |
| `10_source_documents_evidence.png` | Accepted | Source documents in chat configuration editor |
| `appendix_advanced_technical_details.png` | Accepted (appendix only) | Section title exactly "Advanced technical details"; expanded by design |

## 2. Forbidden visible terms check

| Forbidden term | Found? | Screenshot | Action |
|----------------|--------|------------|--------|
| Demo_Best | No | - | - |
| Demo_Worst | No | - | - |
| Demo_NaiveFullCorpus | No | - | - |
| P0 / P1 / P15 | No | - | - |
| preset code | No | - | - |
| experimental preset | No | - | - |
| snapshot | No | - | - |
| profile hash | No | - | - |
| prompt bundle hash | No | - | - |
| runtime override | No | - | - |
| resolved config | No | - | - |
| deterministic tool | No | - | - |
| function calling takes precedence | No | - | - |
| internal route | No | - | - |
| OpenAI-compatible (primary label) | No | Uses "Configured model provider" / "Configured API catalog" | - |

## 3. Product terminology check

| Required product term | Present where expected? | Notes |
|-----------------------|-------------------------|-------|
| Assistant configuration | Yes | Screenshot 01 |
| Assistant instructions | Yes | Screenshot 02 |
| Model configuration | Yes | Screenshots 01, 03 |
| Embedding model | Yes | Screenshot 03 |
| Retrieval settings | Yes | Screenshot 04 |
| Conversation memory | Yes | Screenshot 05 |
| Clarification | Yes | Screenshot 05 |
| Evaluation / Evaluation Lab | Yes | Screenshots 06–08 |
| Configured model catalog | Yes | Screenshot 09 |
| Source documents | Yes | Screenshot 10 |
| Advanced technical details | Yes | Appendix only |

## 4. Screenshots accepted for thesis

All 11 PNG files in `thesis-screenshots/` are **accepted** for thesis use (10 principal + 1 appendix).

## 5. Screenshots rejected or blocked

None.

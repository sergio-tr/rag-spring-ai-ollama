-- Thesis experimental presets: DB parity with ExperimentalPresetCanonicalCatalog (Chat rag_preset.values + UI labels).
-- Fixes prior mislabeled rows from V44 (UUID order vs P11–P14 semantic codes) and exposes corpus-grounded P0/P1 naming.
-- Safe for environments that already applied V51/V52; idempotent additive JSON merges where applicable.

-- Align runtime JSON keys introduced after V51 (corpusGroundedDirectWorkflow) for presets P3–P14 (P0–P2 handled in V52).
UPDATE rag_preset
SET values = values || '{"corpusGroundedDirectWorkflow": false}'::jsonb
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000013'::uuid,
    'cafe0001-0001-4001-8001-000000000014'::uuid,
    'cafe0001-0001-4001-8001-000000000015'::uuid,
    'cafe0001-0001-4001-8001-000000000016'::uuid,
    'cafe0001-0001-4001-8001-000000000017'::uuid,
    'cafe0001-0001-4001-8001-000000000018'::uuid,
    'cafe0001-0001-4001-8001-000000000019'::uuid,
    'cafe0001-0001-4001-8001-000000000020'::uuid,
    'cafe0001-0001-4001-8001-000000000021'::uuid,
    'cafe0001-0001-4001-8001-000000000022'::uuid,
    'cafe0001-0001-4001-8001-000000000023'::uuid,
    'cafe0001-0001-4001-8001-000000000024'::uuid
);

-- Display names and descriptions (stable UUIDs; Chat catalog reads these alongside canonical runtime JSON).
UPDATE rag_preset SET name = 'TFG_P0_CORPUS_GROUNDED_DIRECT',
  description = 'P0: corpus-grounded direct LLM (snapshot chunks, budgeted; no dense retrieval).',
  tags = '["system","experimental","tfg","p0","corpus"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000010'::uuid;

UPDATE rag_preset SET name = 'TFG_P1_NAIVE_FULL_CORPUS',
  description = 'P1: naive full-corpus baseline (budgeted snapshot chunks; no dense retrieval).',
  tags = '["system","experimental","tfg","p1","corpus"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000011'::uuid;

UPDATE rag_preset SET name = 'TFG_P2_DOCUMENT_LEVEL_DENSE_RETRIEVAL',
  description = 'P2: document-level dense retrieval.',
  tags = '["system","experimental","tfg","p2"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000012'::uuid;

UPDATE rag_preset SET name = 'TFG_P3_CHUNK_LEVEL_DENSE_RETRIEVAL',
  description = 'P3: chunk-level dense retrieval.',
  tags = '["system","experimental","tfg","p3"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000013'::uuid;

UPDATE rag_preset SET name = 'TFG_P4_CHUNK_METADATA_RETRIEVAL',
  description = 'P4: chunk-level retrieval + metadata.',
  tags = '["system","experimental","tfg","p4"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000014'::uuid;

UPDATE rag_preset SET name = 'TFG_P5_QUERY_UNDERSTANDING',
  description = 'P5: retrieval + expansion + NER.',
  tags = '["system","experimental","tfg","p5"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000015'::uuid;

UPDATE rag_preset SET name = 'TFG_P6_STRUCTURED_REWRITE',
  description = 'P6: reasoning-enabled retrieval stack.',
  tags = '["system","experimental","tfg","p6"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000016'::uuid;

UPDATE rag_preset SET name = 'TFG_P7_DETERMINISTIC_TOOLS',
  description = 'P7: retrieval + deterministic tools.',
  tags = '["system","experimental","tfg","p7"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000017'::uuid;

UPDATE rag_preset SET name = 'TFG_P8_ADVANCED_RETRIEVAL',
  description = 'P8: hybrid retrieval + ranker + post-retrieval.',
  tags = '["system","experimental","tfg","p8"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000018'::uuid;

UPDATE rag_preset SET name = 'TFG_P9_FUNCTION_CALLING',
  description = 'P9: hybrid stack + function calling.',
  tags = '["system","experimental","tfg","p9"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000019'::uuid;

UPDATE rag_preset SET name = 'TFG_P10_ADVISORS',
  description = 'P10: advisors + hybrid retrieval.',
  tags = '["system","experimental","tfg","p10"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000020'::uuid;

-- UUID ...021 is canonical P13 (clarification); V44 incorrectly labeled as P11.
UPDATE rag_preset SET name = 'TFG_P13_CLARIFICATION_MULTITURN',
  description = 'P13: clarification loop (multi-turn only; not selectable for single-turn Lab benchmark).',
  tags = '["system","experimental","tfg","p13","multi_turn","future"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000021'::uuid;

-- UUID ...022 is canonical P14 (memory); V44 incorrectly labeled as P12.
UPDATE rag_preset SET name = 'TFG_P14_MEMORY_MULTITURN',
  description = 'P14: conversational memory (multi-turn only; not selectable for single-turn Lab benchmark).',
  tags = '["system","experimental","tfg","p14","multi_turn","future"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000022'::uuid;

-- UUID ...023 is canonical P11 (adaptive routing); V44 incorrectly labeled as P13.
UPDATE rag_preset SET name = 'TFG_P11_ADAPTIVE_ROUTING',
  description = 'P11: adaptive routing + hybrid retrieval.',
  tags = '["system","experimental","tfg","p11"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000023'::uuid;

-- UUID ...024 is canonical P12 (judge); V44 incorrectly labeled as P14.
UPDATE rag_preset SET name = 'TFG_P12_JUDGE_ENHANCED',
  description = 'P12: judge-enhanced hybrid retrieval flow.',
  tags = '["system","experimental","tfg","p12"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000024'::uuid;

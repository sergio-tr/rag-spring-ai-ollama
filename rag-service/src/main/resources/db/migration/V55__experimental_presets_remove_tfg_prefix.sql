-- Remove the obsolete TFG prefix from experimental preset display names without editing applied migrations.

UPDATE rag_preset SET name = 'P0_CORPUS_GROUNDED_DIRECT',
  tags = '["system","experimental","p0","corpus"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000010'::uuid;

UPDATE rag_preset SET name = 'P1_NAIVE_FULL_CORPUS',
  tags = '["system","experimental","p1","corpus"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000011'::uuid;

UPDATE rag_preset SET name = 'P2_DOCUMENT_LEVEL_DENSE_RETRIEVAL',
  tags = '["system","experimental","p2"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000012'::uuid;

UPDATE rag_preset SET name = 'P3_CHUNK_LEVEL_DENSE_RETRIEVAL',
  tags = '["system","experimental","p3"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000013'::uuid;

UPDATE rag_preset SET name = 'P4_CHUNK_METADATA_RETRIEVAL',
  tags = '["system","experimental","p4"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000014'::uuid;

UPDATE rag_preset SET name = 'P5_QUERY_UNDERSTANDING',
  tags = '["system","experimental","p5"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000015'::uuid;

UPDATE rag_preset SET name = 'P6_STRUCTURED_REWRITE',
  tags = '["system","experimental","p6"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000016'::uuid;

UPDATE rag_preset SET name = 'P7_DETERMINISTIC_TOOLS',
  tags = '["system","experimental","p7"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000017'::uuid;

UPDATE rag_preset SET name = 'P8_ADVANCED_RETRIEVAL',
  tags = '["system","experimental","p8"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000018'::uuid;

UPDATE rag_preset SET name = 'P9_FUNCTION_CALLING',
  tags = '["system","experimental","p9"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000019'::uuid;

UPDATE rag_preset SET name = 'P10_ADVISORS',
  tags = '["system","experimental","p10"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000020'::uuid;

UPDATE rag_preset SET name = 'P13_CLARIFICATION_MULTITURN',
  tags = '["system","experimental","p13","multi_turn","future"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000021'::uuid;

UPDATE rag_preset SET name = 'P14_MEMORY_MULTITURN',
  tags = '["system","experimental","p14","multi_turn","future"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000022'::uuid;

UPDATE rag_preset SET name = 'P11_ADAPTIVE_ROUTING',
  tags = '["system","experimental","p11"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000023'::uuid;

UPDATE rag_preset SET name = 'P12_JUDGE_ENHANCED',
  tags = '["system","experimental","p12"]'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000024'::uuid;

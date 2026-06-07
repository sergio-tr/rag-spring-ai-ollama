-- TFG experimental RAG presets P0–P14.
-- These are SYSTEM presets meant to be selectable in Chat as "experimental catalog" entries,
-- but they are not returned by GET /presets (product presets) and are surfaced through GET /lab/experimental-presets.

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000010'::uuid,
       NULL,
       'TFG_P0_DIRECT_LLM',
       'P0: direct LLM baseline (no retrieval).',
       '["system","experimental","tfg","p0"]'::jsonb,
       '{"useRetrieval":false,"useAdvisor":false,"naiveFullCorpusInPromptEnabled":false,"topK":5,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P0_DIRECT_LLM' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000011'::uuid,
       NULL,
       'TFG_P1_FULL_CORPUS_PROMPT',
       'P1: naive full corpus prompt baseline (capped).',
       '["system","experimental","tfg","p1"]'::jsonb,
       '{"expansionEnabled":false,"nerEnabled":false,"toolsEnabled":false,"metadataEnabled":false,"reasoningEnabled":false,"rankerEnabled":false,"postRetrievalEnabled":false,"functionCallingEnabled":false,"useRetrieval":true,"useAdvisor":false,"naiveFullCorpusInPromptEnabled":true,"naiveFullCorpusMaxChars":32000,"topK":3,"similarityThreshold":0.9,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P1_FULL_CORPUS_PROMPT' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000012'::uuid,
       NULL,
       'TFG_P2_DOCUMENT_LEVEL_DENSE_RETRIEVAL',
       'P2: document-level dense retrieval.',
       '["system","experimental","tfg","p2"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"naiveFullCorpusInPromptEnabled":false,"topK":8,"similarityThreshold":0.72,"materializationStrategy":"DOCUMENT_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P2_DOCUMENT_LEVEL_DENSE_RETRIEVAL' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000013'::uuid,
       NULL,
       'TFG_P3_CHUNK_LEVEL_DENSE_RETRIEVAL',
       'P3: chunk-level dense retrieval.',
       '["system","experimental","tfg","p3"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P3_CHUNK_LEVEL_DENSE_RETRIEVAL' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000014'::uuid,
       NULL,
       'TFG_P4_CHUNK_METADATA_RETRIEVAL',
       'P4: chunk-level dense retrieval + metadata + deterministic tools.',
       '["system","experimental","tfg","p4"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"metadataEnabled":true,"toolsEnabled":true,"functionCallingEnabled":false,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P4_CHUNK_METADATA_RETRIEVAL' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000015'::uuid,
       NULL,
       'TFG_P5_QUERY_UNDERSTANDING',
       'P5: retrieval + expansion + NER.',
       '["system","experimental","tfg","p5"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"expansionEnabled":true,"nerEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P5_QUERY_UNDERSTANDING' AND is_system = TRUE);

-- P6 and P8 intentionally include advanced flags; runtime support is evaluated via /lab/experimental-presets and Chat disables them when unsupported.
INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000016'::uuid,
       NULL,
       'TFG_P6_STRUCTURED_REWRITE',
       'P6: structured rewrite (reasoning) — may be NOT_SUPPORTED depending on runtime capabilities.',
       '["system","experimental","tfg","p6"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"reasoningEnabled":true,"reasoningStrategy":"COT","naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P6_STRUCTURED_REWRITE' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000017'::uuid,
       NULL,
       'TFG_P7_DETERMINISTIC_TOOLS',
       'P7: retrieval + deterministic tool route.',
       '["system","experimental","tfg","p7"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"toolsEnabled":true,"functionCallingEnabled":false,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P7_DETERMINISTIC_TOOLS' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000018'::uuid,
       NULL,
       'TFG_P8_ADVANCED_RETRIEVAL',
       'P8: hybrid retrieval + ranker + post-retrieval — may be NOT_SUPPORTED depending on runtime capabilities.',
       '["system","experimental","tfg","p8"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"rankerEnabled":true,"postRetrievalEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":12,"similarityThreshold":0.6,"materializationStrategy":"HYBRID"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P8_ADVANCED_RETRIEVAL' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000019'::uuid,
       NULL,
       'TFG_P9_FUNCTION_CALLING',
       'P9: function calling route (if enabled).',
       '["system","experimental","tfg","p9"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"functionCallingEnabled":true,"toolsEnabled":false,"naiveFullCorpusInPromptEnabled":false,"topK":12,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P9_FUNCTION_CALLING' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000020'::uuid,
       NULL,
       'TFG_P10_ADVISORS',
       'P10: advisor phase + retrieval.',
       '["system","experimental","tfg","p10"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":true,"naiveFullCorpusInPromptEnabled":false,"topK":12,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P10_ADVISORS' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000021'::uuid,
       NULL,
       'TFG_P11_CLARIFICATION_LOOP',
       'P11: clarification loop (multi-turn).',
       '["system","experimental","tfg","p11"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"clarificationEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P11_CLARIFICATION_LOOP' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000022'::uuid,
       NULL,
       'TFG_P12_MEMORY_ENABLED_CONVERSATIONAL_FLOW',
       'P12: bounded conversational memory (multi-turn).',
       '["system","experimental","tfg","p12"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"memoryEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":10,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P12_MEMORY_ENABLED_CONVERSATIONAL_FLOW' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000023'::uuid,
       NULL,
       'TFG_P13_ADAPTIVE_ROUTING',
       'P13: adaptive routing stage.',
       '["system","experimental","tfg","p13"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"adaptiveRoutingEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":12,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P13_ADAPTIVE_ROUTING' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000024'::uuid,
       NULL,
       'TFG_P14_JUDGE_ENHANCED_FLOW',
       'P14: judge-enhanced flow (post-answer judge).',
       '["system","experimental","tfg","p14"]'::jsonb,
       '{"useRetrieval":true,"useAdvisor":false,"judgeEnabled":true,"naiveFullCorpusInPromptEnabled":false,"topK":12,"similarityThreshold":0.7,"materializationStrategy":"CHUNK_LEVEL"}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'TFG_P14_JUDGE_ENHANCED_FLOW' AND is_system = TRUE);


-- Canonical TFG experimental presets P0–P14 (accumulative).
-- This migration aligns the persisted rag_preset.values (Chat execution) with the canonical preset ladder
-- used by Lab (ExperimentalPresetCanonicalCatalog).
--
-- IMPORTANT: ids are stable and match V44. We update values deterministically in-place.

UPDATE rag_preset
SET values = '{
  "useRetrieval": false,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": false,
  "expansionEnabled": false,
  "nerEnabled": false,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 5,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000010'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": false,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": true,
  "naiveFullCorpusMaxChars": 32000,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": false,
  "expansionEnabled": false,
  "nerEnabled": false,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 3,
  "similarityThreshold": 0.9
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000011'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "DOCUMENT_LEVEL",
  "metadataEnabled": false,
  "expansionEnabled": false,
  "nerEnabled": false,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 8,
  "similarityThreshold": 0.72
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000012'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": false,
  "expansionEnabled": false,
  "nerEnabled": false,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 10,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000013'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": true,
  "expansionEnabled": false,
  "nerEnabled": false,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 10,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000014'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 10,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000015'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": false,
  "functionCallingEnabled": false,
  "reasoningEnabled": true,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 10,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000016'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "CHUNK_LEVEL",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": false,
  "reasoningEnabled": true,
  "rankerEnabled": false,
  "postRetrievalEnabled": false,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 10,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000017'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": false,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000018'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000019'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": false,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000020'::uuid;

-- P11: adaptive routing (single-turn executable)
UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": true,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000023'::uuid;

-- P12: judge-enhanced flow (single-turn executable)
UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": true,
  "judgeEnabled": true,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000024'::uuid;

-- P13: clarification loop (multi-turn)
UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": true,
  "judgeEnabled": true,
  "clarificationEnabled": true,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000021'::uuid;

-- P14: memory-enabled conversational flow (multi-turn)
UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "adaptiveRoutingEnabled": true,
  "judgeEnabled": true,
  "clarificationEnabled": true,
  "memoryEnabled": true,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000022'::uuid;


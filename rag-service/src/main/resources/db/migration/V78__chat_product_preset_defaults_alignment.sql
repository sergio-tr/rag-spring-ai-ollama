-- Phase 2.6: align Chat-visible rag_preset.values with ChatProductPresetAlignment.
-- Lab protocol ladder (ExperimentalPresetCanonicalCatalog) is intentionally unchanged.

-- P4–P6: metadata/query intelligence without default tools (Deterministic Tools starts at P7).
UPDATE rag_preset
SET values = jsonb_set(values, '{toolsEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000014'::uuid,
    'cafe0001-0001-4001-8001-000000000015'::uuid,
    'cafe0001-0001-4001-8001-000000000016'::uuid
);

-- P4–P9 and P15: extended reasoning deferred to advanced presets (P10+).
UPDATE rag_preset
SET values = jsonb_set(values, '{reasoningEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000014'::uuid,
    'cafe0001-0001-4001-8001-000000000015'::uuid,
    'cafe0001-0001-4001-8001-000000000016'::uuid,
    'cafe0001-0001-4001-8001-000000000017'::uuid,
    'cafe0001-0001-4001-8001-000000000018'::uuid,
    'cafe0001-0001-4001-8001-000000000019'::uuid,
    'cafe0001-0001-4001-8001-000000000025'::uuid
);

-- P10–P14: advanced presets enable extended reasoning.
UPDATE rag_preset
SET values = jsonb_set(values, '{reasoningEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000020'::uuid,
    'cafe0001-0001-4001-8001-000000000023'::uuid,
    'cafe0001-0001-4001-8001-000000000024'::uuid,
    'cafe0001-0001-4001-8001-000000000021'::uuid,
    'cafe0001-0001-4001-8001-000000000022'::uuid
);

-- P10–P14: advisor pack disables function calling (catalog P9/P10 fork parity).
UPDATE rag_preset
SET values = jsonb_set(values, '{functionCallingEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000020'::uuid,
    'cafe0001-0001-4001-8001-000000000023'::uuid,
    'cafe0001-0001-4001-8001-000000000024'::uuid,
    'cafe0001-0001-4001-8001-000000000021'::uuid,
    'cafe0001-0001-4001-8001-000000000022'::uuid
);

-- Product similarity threshold (0.1) for chat-visible presets; P0/P1 and Demo_NaiveFullCorpus keep lab baselines.
UPDATE rag_preset
SET values = jsonb_set(values, '{similarityThreshold}', '0.1'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000001'::uuid,
    'cafe0001-0001-4001-8001-000000000012'::uuid,
    'cafe0001-0001-4001-8001-000000000013'::uuid,
    'cafe0001-0001-4001-8001-000000000014'::uuid,
    'cafe0001-0001-4001-8001-000000000015'::uuid,
    'cafe0001-0001-4001-8001-000000000016'::uuid,
    'cafe0001-0001-4001-8001-000000000017'::uuid,
    'cafe0001-0001-4001-8001-000000000018'::uuid,
    'cafe0001-0001-4001-8001-000000000019'::uuid,
    'cafe0001-0001-4001-8001-000000000020'::uuid,
    'cafe0001-0001-4001-8001-000000000023'::uuid,
    'cafe0001-0001-4001-8001-000000000024'::uuid,
    'cafe0001-0001-4001-8001-000000000021'::uuid,
    'cafe0001-0001-4001-8001-000000000022'::uuid,
    'cafe0001-0001-4001-8001-000000000025'::uuid
);

-- P7: ensure deterministic tool routing flag is present for Chat (catalog delta).
UPDATE rag_preset
SET values = jsonb_set(values, '{deterministicToolRoutingEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000017'::uuid
  AND (values->>'deterministicToolRoutingEnabled') IS NULL;

-- Demo_Best: production bundle aligned with V72 latency choices and product ladder.
UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": true,
  "naiveFullCorpusInPromptEnabled": false,
  "corpusGroundedDirectWorkflow": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "deterministicToolRoutingEnabled": true,
  "functionCallingEnabled": true,
  "functionCallingBackendProposalEnabled": true,
  "functionCallingNativeProviderEnabled": false,
  "postRetrievalEnabled": true,
  "clarificationEnabled": true,
  "reasoningEnabled": false,
  "rankerEnabled": false,
  "judgeEnabled": false,
  "memoryEnabled": false,
  "adaptiveRoutingEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.1,
  "reasoningStrategy": "SIMPLE",
  "naiveFullCorpusMaxChars": 24000
}'::jsonb,
    description = 'Production assistant: hybrid retrieval, metadata context, query intelligence, deterministic tools, function calling, advisor, and clarification. Ranker, reasoning, judge, and memory off for interactive latency.',
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

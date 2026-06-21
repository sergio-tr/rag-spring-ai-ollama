-- P15: integrated single-turn preset (P9 capabilities + adaptive route composition).

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000025'::uuid,
       NULL,
       'P15_INTEGRATED_SINGLE_TURN',
       'P15: hybrid retrieval, backend function calling, and adaptive route composition.',
       '["system","experimental","p15"]'::jsonb,
       '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "corpusGroundedDirectWorkflow": false,
  "materializationStrategy": "HYBRID",
  "metadataEnabled": true,
  "expansionEnabled": true,
  "nerEnabled": true,
  "toolsEnabled": true,
  "functionCallingEnabled": true,
  "functionCallingBackendProposalEnabled": true,
  "functionCallingNativeProviderEnabled": false,
  "reasoningEnabled": true,
  "rankerEnabled": true,
  "postRetrievalEnabled": true,
  "deterministicToolRoutingEnabled": false,
  "adaptiveRoutingEnabled": true,
  "judgeEnabled": false,
  "clarificationEnabled": false,
  "memoryEnabled": false,
  "topK": 12,
  "similarityThreshold": 0.6
}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE id = 'cafe0001-0001-4001-8001-000000000025'::uuid);

-- Align persisted P0 rag_preset.values with direct-LLM baseline (ExperimentalPresetCanonicalCatalog).

UPDATE rag_preset
SET values = '{
  "useRetrieval": false,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "corpusGroundedDirectWorkflow": false,
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
  "topK": 5,
  "similarityThreshold": 0.7
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000010'::uuid;

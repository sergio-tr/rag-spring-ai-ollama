-- Align persisted experimental rag_preset.values with corpus-grounded P0/P1 semantics (see ExperimentalPresetCanonicalCatalog).

UPDATE rag_preset
SET values = '{
  "useRetrieval": false,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": true,
  "corpusGroundedDirectWorkflow": true,
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

UPDATE rag_preset
SET values = '{
  "useRetrieval": false,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": true,
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
  "topK": 3,
  "similarityThreshold": 0.9
}'::jsonb
WHERE id = 'cafe0001-0001-4001-8001-000000000011'::uuid;

UPDATE rag_preset
SET values = '{
  "useRetrieval": true,
  "useAdvisor": false,
  "naiveFullCorpusInPromptEnabled": false,
  "corpusGroundedDirectWorkflow": false,
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

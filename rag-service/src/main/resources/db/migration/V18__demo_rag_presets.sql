-- System RAG presets for demo / product: reproducible "worst", "naive full corpus", and "best" configurations.

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000001'::uuid,
       NULL,
       'Demo_Worst',
       'Plain LLM only: no retrieval, tools, NER, expansion, or advisor. Baseline showing weak answers on actas without RAG.',
       '["demo","system","baseline"]'::jsonb,
       '{"expansionEnabled":false,"nerEnabled":false,"toolsEnabled":false,"metadataEnabled":false,"reasoningEnabled":false,"rankerEnabled":false,"postRetrievalEnabled":false,"functionCallingEnabled":false,"useRetrieval":false,"useAdvisor":false,"topK":5,"similarityThreshold":0.7,"reasoningStrategy":"SIMPLE","naiveFullCorpusInPromptEnabled":false,"naiveFullCorpusMaxChars":24000}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'Demo_Worst' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000002'::uuid,
       NULL,
       'Demo_NaiveFullCorpus',
       'Concatenated chunk dump into the prompt (capped): no semantic retrieval, tools, or advisor. Contrasts with segmented RAG.',
       '["demo","system","naive"]'::jsonb,
       '{"expansionEnabled":false,"nerEnabled":false,"toolsEnabled":false,"metadataEnabled":false,"reasoningEnabled":false,"rankerEnabled":false,"postRetrievalEnabled":false,"functionCallingEnabled":false,"useRetrieval":true,"useAdvisor":false,"topK":3,"similarityThreshold":0.9,"reasoningStrategy":"SIMPLE","naiveFullCorpusInPromptEnabled":true,"naiveFullCorpusMaxChars":32000}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'Demo_NaiveFullCorpus' AND is_system = TRUE);

INSERT INTO rag_preset (id, owner_id, name, description, tags, values, is_system, created_at, updated_at)
SELECT 'cafe0001-0001-4001-8001-000000000003'::uuid,
       NULL,
       'Demo_Best',
       'Full pipeline: retrieval, advisor, tools, function-calling, metadata guard path, expansion, NER, post-retrieval, and COT reasoning (ranker off for latency).',
       '["demo","system","optimal"]'::jsonb,
       '{"expansionEnabled":true,"nerEnabled":true,"toolsEnabled":true,"metadataEnabled":true,"reasoningEnabled":true,"rankerEnabled":false,"postRetrievalEnabled":true,"functionCallingEnabled":true,"useRetrieval":true,"useAdvisor":true,"topK":12,"similarityThreshold":0.55,"reasoningStrategy":"COT","naiveFullCorpusInPromptEnabled":false,"naiveFullCorpusMaxChars":24000}'::jsonb,
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM rag_preset WHERE name = 'Demo_Best' AND is_system = TRUE);

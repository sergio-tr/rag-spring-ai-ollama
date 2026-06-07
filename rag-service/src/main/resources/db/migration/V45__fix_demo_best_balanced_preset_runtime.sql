-- Make the default Chat preset (Demo_Best / "RAG balanced") executable in the current runtime.
-- The runtime does not implement advanced workflow flags (reasoning/ranker/post-retrieval), so they must be OFF.

UPDATE rag_preset
SET values = jsonb_set(
        jsonb_set(
                jsonb_set(values, '{reasoningEnabled}', 'false'::jsonb, true),
                '{postRetrievalEnabled}', 'false'::jsonb, true),
        '{rankerEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;


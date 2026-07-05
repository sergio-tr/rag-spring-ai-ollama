-- Demo_NaiveFullCorpus must behave as naive full corpus (no retrieval workflow).
-- V18 seeded useRetrieval=true which routed through retrieval instead of FullCorpusWorkflow.

UPDATE rag_preset
SET values = jsonb_set(values, '{useRetrieval}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE name = 'Demo_NaiveFullCorpus'
  AND is_system = TRUE
  AND (values->>'useRetrieval')::boolean IS DISTINCT FROM false;

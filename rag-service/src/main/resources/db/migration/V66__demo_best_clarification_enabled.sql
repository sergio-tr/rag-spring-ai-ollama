-- Demo_Best: enable clarification for ambiguous president / missing-date queries (Phase H Q18).

UPDATE rag_preset
SET values = jsonb_set(values, '{clarificationEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

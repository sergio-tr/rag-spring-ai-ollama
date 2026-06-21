-- Demo_Best: enable deterministic tool routing alongside function calling.
-- Structured GET_FIELD queries bypass FC via orchestrator precedence; FC remains for other query types.

UPDATE rag_preset
SET values = jsonb_set(values, '{deterministicToolRoutingEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

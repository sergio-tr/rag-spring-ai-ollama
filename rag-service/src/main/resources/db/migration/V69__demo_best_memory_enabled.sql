-- Demo_Best: enable conversation memory for FD-MEM-01/02 functional defense cases.

UPDATE rag_preset
SET values = jsonb_set(values, '{memoryEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

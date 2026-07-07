-- Demo_Best: disable memory for interactive demo latency (Option B).
-- P14 (cafe0001-0001-4001-8001-000000000022) retains conversation memory for FD-MEM cases.

UPDATE rag_preset
SET values = jsonb_set(
        jsonb_set(
                jsonb_set(values, '{memoryEnabled}', 'false'::jsonb, true),
                '{judgeEnabled}', 'false'::jsonb, true),
        '{reasoningEnabled}', 'false'::jsonb, true),
    description = 'Production demo: hybrid retrieval, advisor, tools, and clarification. Memory and judge disabled for interactive latency.',
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

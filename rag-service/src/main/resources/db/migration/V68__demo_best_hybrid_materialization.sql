-- Demo_Best: bind HYBRID index for metadata/cross-document queries; restore post-retrieval + ranker
-- (V45 disabled them for an older runtime; P8+ ladder requires them for factual acta benchmarks).

UPDATE rag_preset
SET values = jsonb_set(
        jsonb_set(
                jsonb_set(values, '{materializationStrategy}', '"HYBRID"'::jsonb, true),
                '{postRetrievalEnabled}', 'true'::jsonb, true),
        '{rankerEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000003'::uuid;

-- Product preset semantics — P5 query expansion only; P6 adds NER (structured rewriter lane).
-- Lab ladder (ExperimentalPresetCanonicalCatalog) unchanged; Chat persisted values aligned with ChatProductPresetAlignment.

UPDATE rag_preset
SET values = jsonb_set(values, '{nerEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000015'::uuid;

UPDATE rag_preset
SET values = jsonb_set(values, '{nerEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000016'::uuid;

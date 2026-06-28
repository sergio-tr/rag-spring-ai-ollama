-- Align P4+ experimental presets with canonical catalog: metadata lane requires toolsEnabled.
-- Fixes REQUIRES_CAPABILITY 422 when creating conversations with P4–P15 presets.

UPDATE rag_preset
SET values = jsonb_set(values, '{toolsEnabled}', 'true'::jsonb, true),
    updated_at = NOW()
WHERE id IN (
    'cafe0001-0001-4001-8001-000000000014'::uuid,
    'cafe0001-0001-4001-8001-000000000015'::uuid,
    'cafe0001-0001-4001-8001-000000000016'::uuid,
    'cafe0001-0001-4001-8001-000000000017'::uuid,
    'cafe0001-0001-4001-8001-000000000018'::uuid,
    'cafe0001-0001-4001-8001-000000000019'::uuid,
    'cafe0001-0001-4001-8001-000000000020'::uuid,
    'cafe0001-0001-4001-8001-000000000023'::uuid,
    'cafe0001-0001-4001-8001-000000000024'::uuid,
    'cafe0001-0001-4001-8001-000000000021'::uuid,
    'cafe0001-0001-4001-8001-000000000022'::uuid,
    'cafe0001-0001-4001-8001-000000000025'::uuid
)
AND (values->>'metadataEnabled')::boolean IS TRUE;

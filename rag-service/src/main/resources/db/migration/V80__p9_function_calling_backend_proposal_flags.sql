UPDATE rag_preset
SET values = jsonb_set(
        jsonb_set(values, '{functionCallingBackendProposalEnabled}', 'true'::jsonb, true),
        '{functionCallingNativeProviderEnabled}', 'false'::jsonb, true),
    updated_at = NOW()
WHERE id = 'cafe0001-0001-4001-8001-000000000019'::uuid
  AND (values->>'functionCallingBackendProposalEnabled') IS NULL;

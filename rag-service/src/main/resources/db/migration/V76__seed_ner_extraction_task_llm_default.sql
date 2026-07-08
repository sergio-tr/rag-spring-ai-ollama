-- Backfill NER extraction role in system taskLlmOverrides when missing (A1-Fix-4).

UPDATE default_system_configuration dsc
SET values = jsonb_set(
        COALESCE(dsc.values, '{}'::jsonb),
        '{taskLlmOverrides,ner_extraction}',
        '{"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 1024, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false}'::jsonb,
        true
    ),
    updated_at = NOW()
WHERE NOT (
    COALESCE(dsc.values->'taskLlmOverrides', '{}'::jsonb) ? 'ner_extraction'
);

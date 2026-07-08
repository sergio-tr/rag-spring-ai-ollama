-- Seed system per-role task LLM defaults into default_system_configuration when missing.
-- Idempotent: only fills absent taskLlmOverrides entries per role.

UPDATE default_system_configuration dsc
SET values = jsonb_set(
        COALESCE(dsc.values, '{}'::jsonb),
        '{taskLlmOverrides}',
        COALESCE(dsc.values->'taskLlmOverrides', '{}'::jsonb)
            || '{
                "final_answer": {"enabled": true, "inheritModel": true, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.1, "topP": 1.0, "maxTokens": 1024, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "query_rewrite": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 256, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "query_expansion": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.1, "topP": 1.0, "maxTokens": 384, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "memory_condense": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "runtime_judge": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "runtime_judge_retry": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "factual_verifier": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "llm_ranker": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 384, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "metadata_reasoning": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "evaluation_judge": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.0, "topP": 1.0, "maxTokens": 768, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "llm_baseline_evaluation": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.1, "topP": 1.0, "maxTokens": 1024, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false}
            }'::jsonb,
        true
    ),
    updated_at = NOW()
WHERE NOT (
    dsc.values ? 'taskLlmOverrides'
    AND (dsc.values->'taskLlmOverrides') ? 'final_answer'
    AND (dsc.values->'taskLlmOverrides') ? 'query_rewrite'
    AND (dsc.values->'taskLlmOverrides') ? 'query_expansion'
    AND (dsc.values->'taskLlmOverrides') ? 'memory_condense'
    AND (dsc.values->'taskLlmOverrides') ? 'runtime_judge'
    AND (dsc.values->'taskLlmOverrides') ? 'runtime_judge_retry'
    AND (dsc.values->'taskLlmOverrides') ? 'factual_verifier'
    AND (dsc.values->'taskLlmOverrides') ? 'llm_ranker'
    AND (dsc.values->'taskLlmOverrides') ? 'metadata_reasoning'
    AND (dsc.values->'taskLlmOverrides') ? 'evaluation_judge'
    AND (dsc.values->'taskLlmOverrides') ? 'llm_baseline_evaluation'
);

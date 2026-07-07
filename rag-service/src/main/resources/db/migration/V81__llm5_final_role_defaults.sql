-- LLM-5: apply final per-role task LLM defaults (Stage A / reduced B recommendations).
-- Overwrites system taskLlmOverrides for all visible roles; leaves llm_baseline_evaluation unchanged.

UPDATE default_system_configuration dsc
SET values = jsonb_set(
        COALESCE(dsc.values, '{}'::jsonb),
        '{taskLlmOverrides}',
        COALESCE(dsc.values->'taskLlmOverrides', '{}'::jsonb)
            || '{
                "final_answer": {"enabled": true, "inheritModel": true, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.1, "topP": 0.9, "seed": 42, "maxTokens": 1024, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "query_rewrite": {"enabled": true, "inheritModel": false, "model": "qwen3.5:27b", "inheritParameters": false, "temperature": 0.1, "topP": 0.85, "seed": 42, "maxTokens": 384, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "query_expansion": {"enabled": true, "inheritModel": false, "model": "qwen3.5:27b", "inheritParameters": false, "temperature": 0.2, "topP": 0.85, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "memory_condense": {"enabled": true, "inheritModel": false, "model": "ministral-3:14b", "inheritParameters": false, "temperature": 0.1, "topP": 0.9, "seed": 42, "maxTokens": 768, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "text", "think": false},
                "runtime_judge": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "runtime_judge_retry": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "factual_verifier": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "llm_ranker": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "metadata_reasoning": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "ner_extraction": {"enabled": true, "inheritModel": false, "model": "qwen3.5:9b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 1024, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false},
                "evaluation_judge": {"enabled": true, "inheritModel": false, "model": "gemma4:12b", "inheritParameters": false, "temperature": 0.0, "topP": 0.9, "seed": 42, "maxTokens": 512, "presencePenalty": 0.0, "frequencyPenalty": 0.0, "responseFormat": "json_object", "think": false}
            }'::jsonb,
        true
    ),
    updated_at = NOW();

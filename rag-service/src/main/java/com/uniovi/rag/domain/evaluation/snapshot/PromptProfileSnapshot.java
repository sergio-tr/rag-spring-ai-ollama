package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Versioned lab-baseline prompt stack (no production prompt strings without {@link #profileVersion()}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromptProfileSnapshot(
        String profileVersion,
        String baseSystem,
        String projectSystem,
        String chatSystem,
        @JsonProperty("retrieval_qu")
        @JsonAlias("retrievalQuestionTemplate")
        String retrievalQuestionTemplate,
        String answerFormatting,
        String effectiveSystemPrompt,
        String effectiveSystemPromptSha256) {}

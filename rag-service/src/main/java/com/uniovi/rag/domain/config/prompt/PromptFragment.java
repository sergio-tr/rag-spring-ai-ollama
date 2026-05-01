package com.uniovi.rag.domain.config.prompt;

/**
 * One layer in the assembled prompt. Text may be empty when the layer is inactive.
 */
public record PromptFragment(PromptFragmentRole role, String sourceLabel, String text) {
}

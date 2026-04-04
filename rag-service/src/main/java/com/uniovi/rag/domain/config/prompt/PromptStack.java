package com.uniovi.rag.domain.config.prompt;

import java.util.List;

public record PromptStack(List<PromptFragment> fragments) {

    public static PromptStack empty() {
        return new PromptStack(List.of());
    }
}

package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.prompt.PromptStack;

import java.util.List;

public record PromptStackDto(List<PromptFragmentDto> fragments) {

    public static PromptStackDto fromDomain(PromptStack s) {
        List<PromptFragmentDto> list =
                s.fragments().stream()
                        .map(f -> new PromptFragmentDto(f.role().name(), f.sourceLabel(), f.text()))
                        .toList();
        return new PromptStackDto(list);
    }
}

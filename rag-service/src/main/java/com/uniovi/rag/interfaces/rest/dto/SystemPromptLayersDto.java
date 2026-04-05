package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;

public record SystemPromptLayersDto(String base, String account, String project, String presetWorkflow) {

    public static SystemPromptLayersDto fromDomain(SystemPromptLayers l) {
        if (l == null) {
            return new SystemPromptLayersDto("", "", "", "");
        }
        return new SystemPromptLayersDto(l.base(), l.account(), l.project(), l.presetWorkflow());
    }
}

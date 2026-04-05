package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sole production composer of the four effective system prompt layers (ADR 0008).
 */
@Component
public class SystemPromptComposer {

    public String compose(SystemPromptLayers layers) {
        if (layers == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addIfNonBlank(parts, layers.base());
        addIfNonBlank(parts, layers.account());
        addIfNonBlank(parts, layers.project());
        addIfNonBlank(parts, layers.presetWorkflow());
        return String.join("\n\n", parts);
    }

    private static void addIfNonBlank(List<String> parts, String s) {
        if (s == null) {
            return;
        }
        String t = s.trim();
        if (!t.isEmpty()) {
            parts.add(t);
        }
    }
}

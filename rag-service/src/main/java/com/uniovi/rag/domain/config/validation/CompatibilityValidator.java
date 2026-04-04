package com.uniovi.rag.domain.config.validation;

import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates semantic consistency of resolved configuration (capabilities vs models).
 */
public final class CompatibilityValidator {

    private CompatibilityValidator() {
    }

    public static CompatibilityResult validate(RagConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (config.topK() < 1 || config.topK() > 2000) {
            errors.add("topK must be between 1 and 2000");
        }

        if (config.metadataEnabled() && !config.toolsEnabled()) {
            warnings.add("metadataEnabled is on but toolsEnabled is off; metadata tooling may be limited");
        }

        String classifier = config.classifierModelId();
        if (config.metadataEnabled() && (classifier == null || classifier.isBlank())) {
            warnings.add("metadataEnabled without a classifier model id may degrade metadata features");
            suggestions.add("Set classifierModelId or disable metadata");
        }

        if (config.expansionEnabled() && !config.useRetrieval()) {
            warnings.add("expansionEnabled with useRetrieval false is an unusual combination");
        }

        CompatibilitySeverity severity =
                errors.isEmpty()
                        ? (warnings.isEmpty() ? CompatibilitySeverity.OK : CompatibilitySeverity.WARNING)
                        : CompatibilitySeverity.ERROR;

        boolean valid = errors.isEmpty();
        return new CompatibilityResult(valid, severity, List.copyOf(errors), List.copyOf(warnings), List.copyOf(suggestions));
    }
}

package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.interfaces.rest.dto.DisabledRuntimeFeatureDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.mapper.RuntimeConfigRestMapper;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Preset-controlled Chat runtime feature locking (Phase 2.5).
 *
 * <p>Boolean runtime toggles visible in Chat must match the selected preset's base effective config.
 * Optional add-on toggling is deferred; users cannot disable base features or enable non-base features from Chat.
 */
public final class PresetBaseFeatureSupport {

    public static final String PRESET_BASE_FEATURE_LOCKED = "PRESET_BASE_FEATURE_LOCKED";
    public static final String PRESET_FEATURE_TOGGLE_DEFERRED = "PRESET_FEATURE_TOGGLE_DEFERRED";
    public static final String PROJECT_FEATURE_UNAVAILABLE = "PROJECT_FEATURE_UNAVAILABLE";

    public static final String MSG_PRESET_BASE_FEATURE_LOCKED =
            "This feature is required by the selected preset and cannot be disabled from Chat.";
    public static final String MSG_PRESET_FEATURE_TOGGLE_DEFERRED =
            "Optional preset add-ons cannot be changed from Chat. Use Settings → Presets for custom combinations.";
    public static final String MSG_PROJECT_FEATURE_UNAVAILABLE =
            "This feature is unavailable for the active project index profile.";

    /** Chat-configurable boolean runtime keys (aligned with {@code RuntimeConfigCapabilitiesService}). */
    private static final Set<String> PRESET_CONTROLLED_BOOLEAN_KEYS =
            Set.of(
                    "useRetrieval",
                    "naiveFullCorpusInPromptEnabled",
                    "expansionEnabled",
                    "nerEnabled",
                    "toolsEnabled",
                    "functionCallingEnabled",
                    "useAdvisor",
                    "reasoningEnabled",
                    "rankerEnabled",
                    "postRetrievalEnabled",
                    "adaptiveRoutingEnabled",
                    "judgeEnabled",
                    "clarificationEnabled",
                    "memoryEnabled");

    private PresetBaseFeatureSupport() {}

    public static boolean isPresetControlledBooleanKey(String key) {
        return key != null && PRESET_CONTROLLED_BOOLEAN_KEYS.contains(key);
    }

    /** Keys that are {@code true} in the preset base effective config (locked on in Chat). */
    public static Set<String> baseFeatures(Map<String, Object> presetBaseEffectiveConfig) {
        if (presetBaseEffectiveConfig == null || presetBaseEffectiveConfig.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String key : PRESET_CONTROLLED_BOOLEAN_KEYS) {
            if (coerceBool(presetBaseEffectiveConfig.get(key))) {
                out.add(key);
            }
        }
        return Set.copyOf(out);
    }

    public static Optional<DisabledRuntimeFeatureDto> presetLockDisable(
            String featureKey, Map<String, Object> presetBaseEffectiveConfig) {
        if (!isPresetControlledBooleanKey(featureKey) || presetBaseEffectiveConfig == null) {
            return Optional.empty();
        }
        boolean presetValue = coerceBool(presetBaseEffectiveConfig.get(featureKey));
        if (presetValue) {
            return Optional.of(
                    new DisabledRuntimeFeatureDto(
                            featureKey, PRESET_BASE_FEATURE_LOCKED, MSG_PRESET_BASE_FEATURE_LOCKED));
        }
        return Optional.of(
                new DisabledRuntimeFeatureDto(
                        featureKey, PRESET_FEATURE_TOGGLE_DEFERRED, MSG_PRESET_FEATURE_TOGGLE_DEFERRED));
    }

    /**
     * Validates a merged conversation configuration snapshot against preset base semantics and project gates.
     *
     * @param presetBaseEffectiveConfig resolved config for the preset without conversation overrides
     * @param mergedSnapshot persisted/custom snapshot after merge (may be empty for preset-only mode)
     * @param configPatch raw patch keys from the request (only these keys are checked when non-empty)
     * @param indexCompatibility active index compatibility for project-profile gates
     */
    public static List<RuntimeConfigValidationIssueDto> validateRuntimeOverrideChange(
            Map<String, Object> presetBaseEffectiveConfig,
            Map<String, Object> mergedSnapshot,
            Map<String, Object> configPatch,
            RuntimeIndexCompatibilityDto indexCompatibility) {
        List<RuntimeConfigValidationIssueDto> issues = new ArrayList<>();
        if (presetBaseEffectiveConfig == null) {
            presetBaseEffectiveConfig = Map.of();
        }
        Map<String, Object> snapshot = mergedSnapshot != null ? mergedSnapshot : Map.of();
        Map<String, Object> patch = configPatch != null ? configPatch : Map.of();

        Iterable<String> keysToCheck =
                patch.isEmpty()
                        ? snapshot.keySet()
                        : patch.keySet();

        for (String key : keysToCheck) {
            if (!isPresetControlledBooleanKey(key)) {
                continue;
            }
            if (!patch.isEmpty() && !patch.containsKey(key)) {
                continue;
            }
            boolean presetValue = coerceBool(presetBaseEffectiveConfig.get(key));
            boolean attemptedValue = coerceBool(snapshot.get(key));

            if (presetValue && !attemptedValue) {
                issues.add(issue(PRESET_BASE_FEATURE_LOCKED, key, MSG_PRESET_BASE_FEATURE_LOCKED));
                continue;
            }
            if (!presetValue && attemptedValue) {
                Optional<DisabledRuntimeFeatureDto> projectGate =
                        MaterializationFeatureGateService.materializationDisable(key, indexCompatibility);
                if (projectGate.isPresent()) {
                    issues.add(
                            issue(
                                    PROJECT_FEATURE_UNAVAILABLE,
                                    key,
                                    projectGate.get().reason() != null
                                            ? projectGate.get().reason()
                                            : MSG_PROJECT_FEATURE_UNAVAILABLE));
                } else {
                    issues.add(issue(PRESET_FEATURE_TOGGLE_DEFERRED, key, MSG_PRESET_FEATURE_TOGGLE_DEFERRED));
                }
            } else if (attemptedValue) {
                Optional<DisabledRuntimeFeatureDto> projectGate =
                        MaterializationFeatureGateService.materializationDisable(key, indexCompatibility);
                if (projectGate.isPresent()) {
                    issues.add(
                            issue(
                                    PROJECT_FEATURE_UNAVAILABLE,
                                    key,
                                    projectGate.get().reason() != null
                                            ? projectGate.get().reason()
                                            : MSG_PROJECT_FEATURE_UNAVAILABLE));
                }
            }
        }
        return List.copyOf(issues);
    }

    public static void throwIfInvalid(
            Map<String, Object> presetBaseEffectiveConfig,
            Map<String, Object> mergedSnapshot,
            Map<String, Object> configPatch,
            RuntimeIndexCompatibilityDto indexCompatibility) {
        List<RuntimeConfigValidationIssueDto> issues =
                validateRuntimeOverrideChange(
                        presetBaseEffectiveConfig, mergedSnapshot, configPatch, indexCompatibility);
        if (issues.isEmpty()) {
            return;
        }
        RuntimeConfigValidationIssueDto first = issues.getFirst();
        throw new RuntimeConfigurationInvalidException(
                first.code() != null && !first.code().isBlank() ? first.code() : "PRESET_FEATURE_INVALID",
                first.message() != null && !first.message().isBlank() ? first.message() : "Preset feature change is not allowed.",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                RuntimeConfigRestMapper.fromValidationIssueDtos(issues));
    }

    private static RuntimeConfigValidationIssueDto issue(String code, String field, String message) {
        return new RuntimeConfigValidationIssueDto(code, field, message, "ERROR");
    }

    private static boolean coerceBool(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof String s && "true".equalsIgnoreCase(s.trim()));
    }
}

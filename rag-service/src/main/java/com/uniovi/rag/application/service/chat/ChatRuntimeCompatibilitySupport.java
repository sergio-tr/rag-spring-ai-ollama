package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.interfaces.rest.dto.ChatPresetSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.DisabledRuntimeFeatureDto;
import com.uniovi.rag.interfaces.rest.dto.PresetCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.mapper.RuntimeConfigRestMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class ChatRuntimeCompatibilitySupport {

    private static final String ERROR = "ERROR";
    private static final Set<String> INDEX_BOUND_RUNTIME_OVERRIDE_KEYS =
            Set.of(
                    "embeddingModel",
                    "metadataEnabled",
                    "materializationStrategy",
                    "chunkMaxChars",
                    "chunkOverlap",
                    "metadataProfile",
                    "indexProfile");

    private ChatRuntimeCompatibilitySupport() {}

    public static boolean isIndexBoundRuntimeOverrideKey(String key) {
        return key != null && INDEX_BOUND_RUNTIME_OVERRIDE_KEYS.contains(key);
    }

    public static List<RuntimeConfigValidationIssueDto> findIndexBoundOverrideIssues(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<RuntimeConfigValidationIssueDto> out = new ArrayList<>();
        for (String key : raw.keySet()) {
            if (isIndexBoundRuntimeOverrideKey(key)) {
                out.add(
                        new RuntimeConfigValidationIssueDto(
                                "INDEX_BOUND_RUNTIME_OVERRIDE",
                                key,
                                key + " is index-bound and cannot be changed from Chat. Create or reindex a project with a compatible profile.",
                                ERROR));
            }
        }
        return out;
    }

    public static Map<String, Object> copyWithoutNonRuntimeOverrideKeys(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            if (key == null
                    || ConversationRuntimeModelKeys.isModelSelectionKey(key)
                    || isIndexBoundRuntimeOverrideKey(key)) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    public static List<RuntimeConfigValidationIssueDto> blockingIssues(RuntimeConfigValidateResponse vr) {
        if (vr == null) {
            return List.of(
                    new RuntimeConfigValidationIssueDto(
                            "RUNTIME_STATE_UNAVAILABLE",
                            null,
                            "Runtime configuration could not be validated.",
                            ERROR));
        }
        List<RuntimeConfigValidationIssueDto> out = new ArrayList<>();
        if (vr.errors() != null) {
            out.addAll(vr.errors());
        }
        if (vr.requiresReindex() && out.isEmpty()) {
            out.add(
                    new RuntimeConfigValidationIssueDto(
                            "REINDEX_REQUIRED",
                            "indexCompatibility",
                            "The active index snapshot does not satisfy the selected preset requirements.",
                            ERROR));
        }
        if ((!vr.valid() || !vr.supported()) && out.isEmpty()) {
            out.add(
                    new RuntimeConfigValidationIssueDto(
                            "UNSUPPORTED_RUNTIME_CONFIGURATION",
                            null,
                            "Unsupported runtime configuration.",
                            ERROR));
        }
        return List.copyOf(out);
    }

    public static RuntimeCompatibilityDto runtimeCompatibility(RuntimeConfigValidateResponse vr) {
        return new RuntimeCompatibilityDto(
                vr != null && vr.valid(),
                vr != null && vr.supported(),
                vr != null ? vr.selectedWorkflow() : null,
                blockingIssues(vr),
                vr != null && vr.warnings() != null ? List.copyOf(vr.warnings()) : List.of());
    }

    public static void throwIfInvalid(RuntimeConfigValidateResponse vr) {
        List<RuntimeConfigValidationIssueDto> issues = blockingIssues(vr);
        if (issues.isEmpty()) {
            return;
        }
        RuntimeConfigValidationIssueDto first = issues.getFirst();
        throw new RuntimeConfigurationInvalidException(
                first.code() != null && !first.code().isBlank() ? first.code() : "RUNTIME_CONFIGURATION_INVALID",
                first.message() != null && !first.message().isBlank() ? first.message() : "Runtime configuration is invalid.",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                RuntimeConfigRestMapper.fromValidationIssueDtos(issues));
    }

    public static void throwIfIndexBoundOverride(Map<String, Object> raw) {
        List<RuntimeConfigValidationIssueDto> issues = findIndexBoundOverrideIssues(raw);
        if (issues.isEmpty()) {
            return;
        }
        throw new RuntimeConfigurationInvalidException(
                "INDEX_BOUND_RUNTIME_OVERRIDE",
                issues.getFirst().message(),
                HttpStatus.BAD_REQUEST.value(),
                RuntimeConfigRestMapper.fromValidationIssueDtos(issues));
    }

    public static PresetCompatibilityDto presetCompatibility(
            ChatPresetSummaryDto preset,
            RuntimeIndexCompatibilityDto indexCompatibility,
            List<RuntimeConfigValidationIssueDto> blockingIssues) {
        boolean indexOk = indexCompatibility == null || indexCompatibility.compatibleWithPreset();
        DisabledReason disabled =
                firstPresetDisabledReason(preset, indexCompatibility, indexOk, firstIssue(blockingIssues));
        return new PresetCompatibilityDto(
                disabled == null,
                disabled != null ? disabled.code() : null,
                disabled != null ? disabled.reason() : null,
                indexCompatibility != null ? indexCompatibility.presetIndexRequirements() : null,
                indexOk);
    }

    public static List<DisabledRuntimeFeatureDto> disabledRuntimeFeatures(
            List<RuntimeConfigCapabilityDto> capabilities,
            Map<String, Object> effectiveConfig) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        List<DisabledRuntimeFeatureDto> out = new ArrayList<>();
        for (RuntimeConfigCapabilityDto c : capabilities) {
            if (c == null || !c.visibleInChat()) {
                continue;
            }
            DisabledRuntimeFeatureDto disabled = disabledRuntimeFeature(c, effectiveConfig);
            if (disabled != null) {
                out.add(disabled);
            }
        }
        return List.copyOf(out);
    }

    private static DisabledRuntimeFeatureDto disabledRuntimeFeature(
            RuntimeConfigCapabilityDto c,
            Map<String, Object> effectiveConfig) {
        DisabledRuntimeFeatureDto ownState = disabledByCapabilityState(c);
        if (ownState != null) {
            return ownState;
        }
        DisabledRuntimeFeatureDto missingRequirement = firstMissingRequirement(c, effectiveConfig);
        if (missingRequirement != null) {
            return missingRequirement;
        }
        return firstActiveExclusion(c, effectiveConfig);
    }

    private static DisabledReason firstPresetDisabledReason(
            ChatPresetSummaryDto preset,
            RuntimeIndexCompatibilityDto indexCompatibility,
            boolean indexOk,
            RuntimeConfigValidationIssueDto firstIssue) {
        if (preset != null && !preset.chatSelectable()) {
            String code = preset.supportStatus() != null ? preset.supportStatus() : "PRESET_NOT_SELECTABLE";
            String reason =
                    preset.reasonIfUnsupported() != null && !preset.reasonIfUnsupported().isBlank()
                            ? preset.reasonIfUnsupported()
                            : code;
            return new DisabledReason(code, reason);
        }
        if (!indexOk && indexCompatibility != null) {
            String code =
                    indexCompatibility.compatibilityStatus() != null
                            ? indexCompatibility.compatibilityStatus()
                            : "INDEX_INCOMPATIBLE";
            return new DisabledReason(code, "Create or reindex the project with a compatible index profile.");
        }
        return firstIssue != null ? new DisabledReason(firstIssue.code(), firstIssue.message()) : null;
    }

    private static RuntimeConfigValidationIssueDto firstIssue(List<RuntimeConfigValidationIssueDto> issues) {
        return issues != null && !issues.isEmpty() ? issues.getFirst() : null;
    }

    private static DisabledRuntimeFeatureDto disabledByCapabilityState(RuntimeConfigCapabilityDto c) {
        if (!c.configurableInChat()) {
            return new DisabledRuntimeFeatureDto(
                    c.key(),
                    "NOT_CONFIGURABLE_IN_CHAT",
                    c.reasonIfDisabled() != null ? c.reasonIfDisabled() : "Not configurable in Chat.");
        }
        if (!c.implemented() || !c.engineWired()) {
            return new DisabledRuntimeFeatureDto(
                    c.key(),
                    "NOT_IMPLEMENTED",
                    c.reasonIfNotImplemented() != null ? c.reasonIfNotImplemented() : "Not implemented.");
        }
        return null;
    }

    private static DisabledRuntimeFeatureDto firstMissingRequirement(
            RuntimeConfigCapabilityDto c,
            Map<String, Object> effectiveConfig) {
        for (String req : safeList(c.requires())) {
            if (!coerceBool(value(effectiveConfig, req))) {
                return new DisabledRuntimeFeatureDto(c.key(), "REQUIRES_" + req, "Requires " + req + "=true.");
            }
        }
        return null;
    }

    private static DisabledRuntimeFeatureDto firstActiveExclusion(
            RuntimeConfigCapabilityDto c,
            Map<String, Object> effectiveConfig) {
        for (String ex : safeList(c.excludes())) {
            if (coerceBool(value(effectiveConfig, ex))) {
                return new DisabledRuntimeFeatureDto(
                        c.key(), "EXCLUDES_" + ex, "Cannot be enabled with " + ex + "=true.");
            }
        }
        return null;
    }

    private static Object value(Map<String, Object> values, String key) {
        return values != null ? values.get(key) : null;
    }

    private static List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private static boolean coerceBool(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof String s && "true".equalsIgnoreCase(s.trim()));
    }

    private record DisabledReason(String code, String reason) {}
}

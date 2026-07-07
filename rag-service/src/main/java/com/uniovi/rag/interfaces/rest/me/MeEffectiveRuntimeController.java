package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeEffectiveRuntimeResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Me", description = "Effective runtime configuration for chat preview")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/llm")
public class MeEffectiveRuntimeController {

    private final RuntimeConfigValidationService runtimeConfigValidationService;
    private final TaskModelSettingsService taskModelSettingsService;
    private final ProjectAccessService projectAccessService;
    private final ChatPresetDefaults chatPresetDefaults;

    public MeEffectiveRuntimeController(
            RuntimeConfigValidationService runtimeConfigValidationService,
            TaskModelSettingsService taskModelSettingsService,
            ProjectAccessService projectAccessService,
            ChatPresetDefaults chatPresetDefaults) {
        this.runtimeConfigValidationService = runtimeConfigValidationService;
        this.taskModelSettingsService = taskModelSettingsService;
        this.projectAccessService = projectAccessService;
        this.chatPresetDefaults = chatPresetDefaults;
    }

    @GetMapping("/effective-runtime")
    public MeEffectiveRuntimeResponseDto effectiveRuntime(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam UUID projectId,
            @RequestParam UUID conversationId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ConversationEntity conversation =
                projectAccessService.requireConversationForUser(principal.userId(), conversationId);
        UUID conversationProjectId =
                conversation.getProject() != null ? conversation.getProject().getId() : null;
        if (conversationProjectId == null || !conversationProjectId.equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversation project mismatch");
        }

        UUID presetId = conversation.getPreset() != null ? conversation.getPreset().getId() : null;
        UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(presetId);
        RagPresetEntity presetEntity = conversation.getPreset();
        String presetName = presetEntity != null ? presetEntity.getName() : null;
        String presetSource = presetId != null ? "PERSISTED" : "DEFAULT";
        Map<String, Object> runtimeOverride =
                conversation.getRuntimeOverride() != null
                        ? new LinkedHashMap<>(conversation.getRuntimeOverride())
                        : Map.of();

        RuntimeConfigValidateResponse validation =
                runtimeConfigValidationService.validate(
                        principal.userId(),
                        new RuntimeConfigValidateRequest(
                                conversationId,
                                presetId != null ? presetId.toString() : null,
                                null,
                                runtimeOverride));

        Map<String, Object> effective =
                validation.effectiveConfig() != null
                        ? Map.copyOf(validation.effectiveConfig())
                        : Map.of();

        Map<String, Object> taskSettings =
                taskModelSettingsService.getEffectiveForUser(principal.userId(), projectId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles =
                taskSettings.get("roles") instanceof List<?> list
                        ? (List<Map<String, Object>>) list
                        : List.of();

        String classifierModelId =
                effective.get("classifierModelId") instanceof String s && !s.isBlank()
                        ? s.trim()
                        : null;
        String snapshotEmbedding = null;
        String materializationStrategy = null;
        if (validation.indexCompatibility() != null
                && validation.indexCompatibility().activeIndexProfile() != null) {
            Map<String, Object> profile = validation.indexCompatibility().activeIndexProfile();
            Object modelId = profile.get("embeddingModelId");
            if (modelId instanceof String s && !s.isBlank()) {
                snapshotEmbedding = s.trim();
            }
            Object mat = profile.get("materializationStrategy");
            if (mat instanceof String s && !s.isBlank()) {
                materializationStrategy = s.trim();
            }
        }

        Integer retrievalTopK = readInt(effective.get("topK"));
        Double retrievalSimilarityThreshold = readDouble(effective.get("similarityThreshold"));

        return new MeEffectiveRuntimeResponseDto(
                projectId.toString(),
                conversationId.toString(),
                effective,
                List.copyOf(roles),
                classifierModelId,
                snapshotEmbedding,
                presetId != null ? presetId.toString() : null,
                effectivePresetId != null ? effectivePresetId.toString() : null,
                presetName,
                presetSource,
                retrievalTopK,
                retrievalSimilarityThreshold,
                materializationStrategy);
    }

    private static Integer readInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static Double readDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}

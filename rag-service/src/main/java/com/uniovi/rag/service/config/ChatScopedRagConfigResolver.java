package com.uniovi.rag.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves {@link RagConfig} for chat-scoped requests using the same cascade as {@link com.uniovi.rag.service.query.ProcessQueryService}
 * (alignment for SSE sources and main turn when config v2 is enabled).
 */
@Service
public class ChatScopedRagConfigResolver {

    private final ConfigResolver configResolver;
    private final boolean configV2Enabled;
    private final RuntimeConfigResolutionService runtimeConfigResolutionService;
    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final ChatPresetDefaults chatPresetDefaults;

    public ChatScopedRagConfigResolver(
            ConfigResolver configResolver,
            @Autowired(required = false) RuntimeConfigResolutionService runtimeConfigResolutionService,
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper,
            @Value("${rag.config.v2.enabled:false}") boolean configV2Enabled,
            ChatPresetDefaults chatPresetDefaults) {
        this.configResolver = configResolver;
        this.runtimeConfigResolutionService = runtimeConfigResolutionService;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.configV2Enabled = configV2Enabled;
        this.chatPresetDefaults = chatPresetDefaults;
    }

    /**
     * Same resolution path as the main query pipeline for a {@link RagExecutionContext} overlay (including legacy null overlay).
     */
    public RagConfig resolveForExecutionContext(RagExecutionContext overlay) {
        JsonNode chatRuntime = null;
        if (overlay != null && overlay.conversationId() != null && !overlay.conversationId().isBlank()) {
            try {
                UUID convId = UUID.fromString(overlay.conversationId().trim());
                chatRuntime = buildRuntimeOverrideForChat(convId);
            } catch (IllegalArgumentException ignored) {
                // leave chatRuntime null
            }
        }
        UUID uid = parseUuid(overlay != null ? overlay.userId() : null);
        UUID pid = parseUuid(overlay != null ? overlay.projectId() : null);
        if (configV2Enabled && runtimeConfigResolutionService != null) {
            return runtimeConfigResolutionService.resolve(uid, pid, chatRuntime).toRagConfig();
        }
        return configResolver.resolve(uid, pid, chatRuntime);
    }

    /**
     * Same semantics as the main chat turn for SSE sources: optional conversation runtime override + v2 resolution.
     */
    public RagConfig resolveForChat(UUID userId, UUID projectId, UUID conversationId) {
        JsonNode chatRuntime = buildRuntimeOverrideForChat(conversationId);
        if (configV2Enabled && runtimeConfigResolutionService != null) {
            return runtimeConfigResolutionService.resolve(userId, projectId, chatRuntime).toRagConfig();
        }
        return configResolver.resolve(userId, projectId, chatRuntime);
    }

    /**
     * Merged conversation configuration (conversation config + preset + runtime override), same map as used for
     * v2 {@link #resolveForChat(UUID, UUID, UUID)} terminal JSON.
     */
    public JsonNode mergedConversationConfigAsJson(UUID conversationId) {
        return buildRuntimeOverrideForChat(conversationId);
    }

    private JsonNode buildRuntimeOverrideForChat(UUID conversationId) {
        if (conversationId == null) {
            return null;
        }
        return conversationRepository
                .findByIdWithConfigAndPreset(conversationId)
                .map(this::mergeConversationConfigLayers)
                .filter(m -> !m.isEmpty())
                .map(m -> objectMapper.convertValue(m, JsonNode.class))
                .orElse(null);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<String, Object> mergeConversationConfigLayers(ConversationEntity c) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (c.getConfig() != null && c.getConfig().getValues() != null) {
            merged.putAll(c.getConfig().getValues());
        }
        if (c.getPreset() != null && c.getPreset().getValues() != null) {
            merged.putAll(c.getPreset().getValues());
        } else {
            chatPresetDefaults
                    .loadDeterministicDefaultPreset()
                    .filter(p -> p.getValues() != null && !p.getValues().isEmpty())
                    .ifPresent(p -> merged.putAll(p.getValues()));
        }
        merged.putAll(c.getRuntimeOverride());
        return merged;
    }
}

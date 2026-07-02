package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRuntimePresetLockedRetrievalTest {

    @Test
    void mergedConversationConfig_includesLockedPresetTopK() throws Exception {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        RuntimeConfigResolutionService runtime = mock(RuntimeConfigResolutionService.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        UUID cid = UUID.randomUUID();

        RagConfigurationEntity cfgEnt = mock(RagConfigurationEntity.class);
        when(cfgEnt.getValues()).thenReturn(Map.of());
        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getValues())
                .thenReturn(
                        Map.of(
                                "topK",
                                5,
                                "similarityThreshold",
                                0.9,
                                "retrievalParameterPolicy",
                                "PRESET_LOCKED"));
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getConfig()).thenReturn(cfgEnt);
        when(conv.getPreset()).thenReturn(preset);
        when(conv.getRuntimeOverride()).thenReturn(Map.of());
        when(repo.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

        RagConfig core = sampleRagConfig();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        core);
        when(runtime.resolve(any(), any(), any(JsonNode.class))).thenReturn(resolved);

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, runtime, repo, om, true, emptyChatPresetDefaults());

        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        cut.resolveForChat(uid, pid, cid);

        ArgumentCaptor<JsonNode> terminalCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(runtime).resolve(eq(uid), eq(pid), terminalCaptor.capture());
        JsonNode terminal = terminalCaptor.getValue();
        assertEquals(5, terminal.get("topK").asInt());
        assertEquals(0.9, terminal.get("similarityThreshold").asDouble());
    }

    private static ChatPresetDefaults emptyChatPresetDefaults() {
        ChatPresetDefaults d = mock(ChatPresetDefaults.class);
        when(d.loadDeterministicDefaultPreset()).thenReturn(Optional.empty());
        return d;
    }

    private static RagConfig sampleRagConfig() {
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        features.setUseRetrieval(true);
        return RagConfig.fromFeatureConfiguration(features, 8, 0.25, "lm", "em", null, "NONE");
    }
}

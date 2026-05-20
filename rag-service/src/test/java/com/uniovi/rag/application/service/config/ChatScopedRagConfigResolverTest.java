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
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatScopedRagConfigResolverTest {

    @Test
    void resolveForExecutionContext_nullOverlay_usesDefaultResolver() {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        RagConfig expected = sampleRagConfig();
        when(configResolver.resolve(isNull(), isNull(), isNull())).thenReturn(expected);

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, null, repo, om, false, emptyChatPresetDefaults());

        assertSame(expected, cut.resolveForExecutionContext(null));
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void resolveForExecutionContext_invalidConversationIdString_skipsRepoLookup() {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        RagConfig expected = sampleRagConfig();
        when(configResolver.resolve(isNull(), isNull(), isNull())).thenReturn(expected);

        RagExecutionContext ctx =
                new RagExecutionContext("not-a-uuid", null, null, expected, List.of(), "t");

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, null, repo, om, false, emptyChatPresetDefaults());

        assertSame(expected, cut.resolveForExecutionContext(ctx));
    }

    @Test
    void resolveForExecutionContext_validConversation_buildsRuntimeJson() throws Exception {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        RagConfig base = sampleRagConfig();

        UUID convId = UUID.randomUUID();
        RagConfigurationEntity cfgEnt = mock(RagConfigurationEntity.class);
        when(cfgEnt.getValues()).thenReturn(Map.of("topK", 3));
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getConfig()).thenReturn(cfgEnt);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of());

        when(repo.findByIdWithConfigAndPreset(convId)).thenReturn(Optional.of(conv));

        when(configResolver.resolve(isNull(), isNull(), any(JsonNode.class))).thenReturn(base);

        RagExecutionContext ctx =
                new RagExecutionContext(
                        convId.toString(), null, null, base, List.of(), "trace");

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, null, repo, om, false, emptyChatPresetDefaults());

        assertSame(base, cut.resolveForExecutionContext(ctx));
    }

    @Test
    void resolveForChat_configV2_usesRuntimeService() {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        RuntimeConfigResolutionService runtime = mock(RuntimeConfigResolutionService.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();

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
                        null);

        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(repo.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.empty());
        when(runtime.resolve(eq(uid), eq(pid), isNull())).thenReturn(resolved);

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, runtime, repo, om, true, emptyChatPresetDefaults());

        RagConfig out = cut.resolveForChat(uid, pid, cid);
        assertSame(core, out);
    }

    @Test
    void mergedConversationConfigAsJson_noConversation_returnsNull() {
        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        mock(ConfigResolver.class),
                        null,
                        mock(ConversationRepository.class),
                        new ObjectMapper(),
                        false,
                        emptyChatPresetDefaults());

        assertNull(cut.mergedConversationConfigAsJson(null));
    }

    @Test
    void mergedConversationConfigAsJson_returnsMergedTree() {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        UUID cid = UUID.randomUUID();
        RagConfigurationEntity cfgEnt = mock(RagConfigurationEntity.class);
        when(cfgEnt.getValues()).thenReturn(Map.of("useAdvisor", true));
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getConfig()).thenReturn(cfgEnt);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of());
        when(repo.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, null, repo, om, false, emptyChatPresetDefaults());

        JsonNode node = cut.mergedConversationConfigAsJson(cid);
        assertNotNull(node);
        assertNotNull(node.get("useAdvisor"));
    }

    @Test
    void mergedConversationConfigAsJson_includesPersistedLlmAndClassifierAfterRuntimeOverride() throws Exception {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        UUID cid = UUID.randomUUID();
        RagConfigurationEntity cfgEnt = mock(RagConfigurationEntity.class);
        when(cfgEnt.getValues()).thenReturn(Map.of());
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getConfig()).thenReturn(cfgEnt);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of("llmModel", "from-json"));
        when(conv.getLlmModel()).thenReturn("from-column");
        when(conv.getClassifierModelId()).thenReturn("cls-tag");
        when(repo.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(
                        configResolver, null, repo, om, false, emptyChatPresetDefaults());

        JsonNode node = cut.mergedConversationConfigAsJson(cid);
        assertNotNull(node);
        assertNotNull(node.get("llmModel"));
        assertNotNull(node.get("classifierModelId"));
        // Conversation columns win after runtime JSON merge.
        assertNotNull(node);
        assertEquals("from-column", node.get("llmModel").asText());
        assertEquals("cls-tag", node.get("classifierModelId").asText());
    }

    @Test
    void mergedConversationConfigAsJson_mergesDeterministicDemoWorstWhenPresetNull() {
        ConfigResolver configResolver = mock(ConfigResolver.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        ObjectMapper om = new ObjectMapper();
        UUID cid = UUID.randomUUID();
        RagConfigurationEntity cfgEnt = mock(RagConfigurationEntity.class);
        when(cfgEnt.getValues()).thenReturn(Map.of());
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getConfig()).thenReturn(cfgEnt);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of());
        when(repo.findByIdWithConfigAndPreset(cid)).thenReturn(Optional.of(conv));

        RagPresetEntity demoWorst = mock(RagPresetEntity.class);
        when(demoWorst.getValues()).thenReturn(Map.of("useRetrieval", false, "toolsEnabled", false));

        ChatPresetDefaults defaults = mock(ChatPresetDefaults.class);
        when(defaults.loadDeterministicDefaultPreset()).thenReturn(Optional.of(demoWorst));

        ChatScopedRagConfigResolver cut =
                new ChatScopedRagConfigResolver(configResolver, null, repo, om, false, defaults);

        JsonNode node = cut.mergedConversationConfigAsJson(cid);
        assertNotNull(node);
        assertNotNull(node.get("useRetrieval"));
        assertNotNull(node.get("toolsEnabled"));
    }

    private static ChatPresetDefaults emptyChatPresetDefaults() {
        ChatPresetDefaults d = mock(ChatPresetDefaults.class);
        when(d.loadDeterministicDefaultPreset()).thenReturn(Optional.empty());
        return d;
    }

    private static RagConfig sampleRagConfig() {
        RagFeatureConfiguration features = mock(RagFeatureConfiguration.class);
        when(features.isExpansionEnabled()).thenReturn(false);
        when(features.isNerEnabled()).thenReturn(false);
        when(features.isToolsEnabled()).thenReturn(false);
        when(features.isMetadataEnabled()).thenReturn(false);
        when(features.isReasoningEnabled()).thenReturn(false);
        when(features.isRankerEnabled()).thenReturn(false);
        when(features.isPostRetrievalEnabled()).thenReturn(false);
        when(features.isFunctionCallingEnabled()).thenReturn(false);
        when(features.isUseRetrieval()).thenReturn(true);
        when(features.isUseAdvisor()).thenReturn(false);
        when(features.isClarificationEnabled()).thenReturn(false);
        when(features.isMemoryEnabled()).thenReturn(false);
        when(features.isAdaptiveRoutingEnabled()).thenReturn(false);
        when(features.isJudgeEnabled()).thenReturn(false);
        return RagConfig.fromFeatureConfiguration(features, 10, 0.7, "lm", "em", null, "NONE");
    }
}

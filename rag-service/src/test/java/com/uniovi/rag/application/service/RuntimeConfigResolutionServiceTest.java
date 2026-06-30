package com.uniovi.rag.application.service;

import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import com.uniovi.rag.infrastructure.config.PromptBundleFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeConfigResolutionServiceTest {

    @Mock
    private ConfigResolverService configResolverService;

    @Mock
    private ObjectProvider<RuntimeObservability> runtimeObservability;

    @InjectMocks
    private RuntimeConfigResolutionService service;

    @Test
    void resolve_delegatesToConfigResolverService() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        when(configResolverService.resolve(any(RuntimeConfigResolutionInput.class))).thenReturn(resolved);

        assertSame(resolved, service.resolve(uid, pid, null));

        verify(configResolverService)
                .resolve(eq(RuntimeConfigResolutionInput.forResolve(uid, pid, null)));
    }

    @Test
    void resolveForOrchestratedExecute_delegates() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        when(configResolverService.resolve(any(RuntimeConfigResolutionInput.class))).thenReturn(resolved);

        assertSame(resolved, service.resolveForOrchestratedExecute(uid, pid, null, "corr"));

        verify(configResolverService)
                .resolve(
                        eq(RuntimeConfigResolutionInput.forOrchestratedResolve(uid, pid, null, "corr")));
    }

    @Test
    void preview_withProfileTypes_delegates() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
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
        RagConfig cfg = RagConfig.fromFeatureConfiguration(features, 10, 0.7, "lm", "em", null, "NONE");
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        when(configResolverService.preview(any(RuntimeConfigResolutionInput.class))).thenReturn(resolved);

        assertSame(
                resolved,
                service.preview(uid, pid, null, Set.of(ConfigProfileType.INDEX), caps));

        verify(configResolverService).preview(any(RuntimeConfigResolutionInput.class));
    }

    @Test
    void previewRuntimeInput_passThrough() {
        RuntimeConfigResolutionInput input = RuntimeConfigResolutionInput.forResolve(UUID.randomUUID(), null, null);
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        when(configResolverService.preview(input)).thenReturn(resolved);

        assertSame(resolved, service.preview(input));
    }

    @Test
    void frozenPromptBundle_availableForConfigTraceability() {
        PromptBundleFingerprint.Result bundle = PromptBundleFingerprint.computeFrozen();
        assertThat(bundle.bundleHashSha256()).isNotBlank();
        assertThat(bundle.includedGroups()).isNotEmpty();
    }
}

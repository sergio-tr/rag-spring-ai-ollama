package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaModelProvisioningServiceTest {

    @Mock
    private OllamaApiClient ollamaApiClient;

    private RagHealthProperties healthProperties;
    private RagOllamaProperties ollamaProperties;

    @BeforeEach
    void initProps() {
        healthProperties = new RagHealthProperties();
        ollamaProperties = new RagOllamaProperties();
    }

    private OllamaModelProvisioningService newService() {
        return new OllamaModelProvisioningService(
                healthProperties,
                ollamaProperties,
                ollamaApiClient,
                "chat-m",
                "embed-m"
        );
    }

    private void invokeInit(OllamaModelProvisioningService svc) throws Exception {
        Method m = OllamaModelProvisioningService.class.getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(svc);
    }

    @Test
    void init_whenOllamaDisabled_setsReady() throws Exception {
        healthProperties.setOllamaEnabled(false);
        OllamaModelProvisioningService svc = newService();
        invokeInit(svc);
        assertEquals(OllamaModelProvisioningService.State.READY, svc.getState());
    }

    @Test
    void init_whenAutoPullOff_setsReady() throws Exception {
        ollamaProperties.setAutoPullEnabled(false);
        OllamaModelProvisioningService svc = newService();
        invokeInit(svc);
        assertEquals(OllamaModelProvisioningService.State.READY, svc.getState());
    }

    @Test
    void ensureConfiguredModels_allPresent_setsReady() throws Exception {
        OllamaModelProvisioningService svc = newService();
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("chat-m", "embed-m"));
        svc.ensureConfiguredModelsAtStartup();
        assertEquals(OllamaModelProvisioningService.State.READY, svc.getState());
        verify(ollamaApiClient, never()).pullModel(anyString(), anyLong());
    }

    @Test
    void ensureConfiguredModels_pullsMissing() throws Exception {
        OllamaModelProvisioningService svc = newService();
        // Mutable set: production code mutates "installed" after each pull (same reference as listModelNames()).
        when(ollamaApiClient.listModelNames()).thenReturn(new HashSet<>());
        lenient().doAnswer(inv -> null).when(ollamaApiClient).pullModel(anyString(), anyLong());
        svc.ensureConfiguredModelsAtStartup();
        assertEquals(OllamaModelProvisioningService.State.READY, svc.getState());
        verify(ollamaApiClient, times(2)).pullModel(anyString(), eq(ollamaProperties.getPullReadTimeoutMs()));
    }

    @Test
    void ensureConfiguredModels_onFailure_setsFailed() throws Exception {
        OllamaModelProvisioningService svc = newService();
        when(ollamaApiClient.listModelNames()).thenThrow(new IOException("net"));
        svc.ensureConfiguredModelsAtStartup();
        assertEquals(OllamaModelProvisioningService.State.FAILED, svc.getState());
        assertNotNull(svc.getLastError());
    }

    @Test
    void ensureModelPresent_whenDisabled_noop() throws Exception {
        healthProperties.setOllamaEnabled(false);
        OllamaModelProvisioningService svc = newService();
        svc.ensureModelPresent("any");
        verifyNoInteractions(ollamaApiClient);
    }

    @Test
    void ensureModelPresent_blank_noop() throws Exception {
        OllamaModelProvisioningService svc = newService();
        svc.ensureModelPresent("  ");
        verifyNoInteractions(ollamaApiClient);
    }

    @Test
    void ensureModelPresent_alreadyInstalled_noPull() throws Exception {
        OllamaModelProvisioningService svc = newService();
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("x"));
        svc.ensureModelPresent("x");
        verify(ollamaApiClient, never()).pullModel(anyString(), anyLong());
    }

    @Test
    void ensureModelPresent_pullsWhenMissing() throws Exception {
        OllamaModelProvisioningService svc = newService();
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of());
        doNothing().when(ollamaApiClient).pullModel(eq("new-m"), anyLong());
        svc.ensureModelPresent("new-m");
        verify(ollamaApiClient).pullModel("new-m", ollamaProperties.getPullReadTimeoutMs());
    }

    @Test
    void isReadyForApiTraffic_reflectsState() throws Exception {
        healthProperties.setOllamaEnabled(false);
        OllamaModelProvisioningService svc = newService();
        invokeInit(svc);
        assertTrue(svc.isReadyForApiTraffic());
    }
}

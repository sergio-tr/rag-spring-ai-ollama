package com.uniovi.rag.infrastructure.llm.ollama;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaProvisioningGateFilterTest {

    private OllamaModelProvisioningService provisioningService;
    private OllamaProvisioningGateFilter filter;

    @BeforeEach
    void setUp() {
        provisioningService = mock(OllamaModelProvisioningService.class);
        filter =
                new OllamaProvisioningGateFilter(
                        provisioningService, new ObjectMapper(), new RagApiPathProperties());
    }

    @Test
    void shouldNotFilter_nonApiPath() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void shouldNotFilter_whenReady_allowsNonOllamaEndpoints() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path("/projects"));
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void shouldNotFilter_whenNotReady_allowsNonOllamaEndpoints() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path("/projects"));
        assertTrue(filter.shouldNotFilter(req));
        verify(provisioningService, never()).isReadyForApiTraffic();
    }

    @Test
    void shouldNotFilter_whenNotReady_blocksChatMessageExecution() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(false);
        MockHttpServletRequest req =
                new MockHttpServletRequest("POST", path("/conversations/00000000-0000-0000-0000-000000000000/messages"));
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void shouldNotFilter_whenNotReady_blocksRuntimeTraceReplay() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(false);
        MockHttpServletRequest req =
                new MockHttpServletRequest("POST", path("/runtime-traces/abc/replay"));
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void shouldNotFilter_whenNotReady_blocksRuntimeTraceReplayComparison() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(false);
        MockHttpServletRequest req =
                new MockHttpServletRequest("POST", path("/runtime-traces/abc/replay-comparison"));
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void shouldNotFilter_whenNotReady_allowsRuntimeTraceRead() {
        when(provisioningService.isReadyForApiTraffic()).thenReturn(false);
        MockHttpServletRequest req =
                new MockHttpServletRequest("GET", path("/runtime-traces/abc"));
        assertTrue(filter.shouldNotFilter(req));
        verify(provisioningService, never()).isReadyForApiTraffic();
    }

    @Test
    void doFilterInternal_pending_returns503Json() throws Exception {
        when(provisioningService.getState()).thenReturn(OllamaModelProvisioningService.State.PENDING);
        when(provisioningService.getLastError()).thenReturn(null);

        MockHttpServletRequest req =
                new MockHttpServletRequest(
                        "POST",
                        path("/conversations/00000000-0000-0000-0000-000000000000/messages"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, res.getStatus());
        assertTrue(res.getContentAsString().contains("OLLAMA_PROVISIONING"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_failed_usesLastErrorDetail() throws Exception {
        when(provisioningService.getState()).thenReturn(OllamaModelProvisioningService.State.FAILED);
        when(provisioningService.getLastError()).thenReturn("boom");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(503, res.getStatus());
        assertTrue(res.getContentAsString().contains("boom"));
    }
}

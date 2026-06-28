package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.LabCampaignService;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LabCampaignController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LabCampaignController.class)
class LabCampaignControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LabCampaignService labCampaignService;
    @MockitoBean private BenchmarkRunOrchestrator benchmarkRunOrchestrator;

    private UUID userId;

    @BeforeEach
    void auth() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summary_returns200() throws Exception {
        UUID cid = UUID.randomUUID();
        when(labCampaignService.summary(eq(userId), eq(cid))).thenReturn(Map.of("campaignId", cid));
        mockMvc.perform(get(path("/lab/campaigns/" + cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(cid.toString()));
    }

    @Test
    void exportSummaryCsv_returns200() throws Exception {
        UUID cid = UUID.randomUUID();
        when(labCampaignService.exportCampaignSummaryCsv(eq(userId), eq(cid)))
                .thenReturn("campaignId,campaign_type,comparison_axis\n" + cid + ",LLM,LLM_MODEL\n");
        mockMvc.perform(get(path("/lab/campaigns/" + cid + "/export/summary.csv")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("comparison_axis")));
    }

    @Test
    void exportItemsJson_returns200() throws Exception {
        UUID cid = UUID.randomUUID();
        when(labCampaignService.exportCampaignMvpItemsJson(eq(userId), eq(cid)))
                .thenReturn(Map.of("campaignId", cid, "items", List.of()));
        mockMvc.perform(get(path("/lab/campaigns/" + cid + "/export/mvp/items.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(cid.toString()))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void exportMvpItemsCsv_returns200() throws Exception {
        UUID cid = UUID.randomUUID();
        when(labCampaignService.exportCampaignItemsCsv(eq(userId), eq(cid)))
                .thenReturn("campaignId,presetKey\n" + cid + ",P0\n");
        mockMvc.perform(get(path("/lab/campaigns/" + cid + "/export/mvp/items.csv")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("presetKey")));
    }
}


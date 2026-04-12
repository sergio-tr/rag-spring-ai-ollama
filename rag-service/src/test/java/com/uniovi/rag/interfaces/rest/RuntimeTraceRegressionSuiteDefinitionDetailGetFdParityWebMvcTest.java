package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    RuntimeTraceRegressionSuiteDefinitionController.class,
    RegressionSuiteDefinitionMutationJacksonConfiguration.class,
    RuntimeTraceRegressionSuiteDefinitionDetailGetFdParityWebMvcTest.Fd4OnlyJsonConverters.class
})
class RuntimeTraceRegressionSuiteDefinitionDetailGetFdParityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunExportService runExportService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportService runImportService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

    private static ObjectMapper fd4ObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    private UUID userId;
    private UUID definitionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void t11_detailBodyEqualsFromSnapshotUnderFd4Mapper() throws Exception {
        UUID tid = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snapshot =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "suite",
                        null,
                        1,
                        Instant.parse("2024-03-01T12:00:00Z"),
                        Instant.parse("2024-03-02T12:00:00Z"),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(tid))));
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId))).thenReturn(Optional.of(snapshot));

        byte[] body =
                mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions/{id}", definitionId))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        ObjectMapper fd4 = fd4ObjectMapper();
        assertThat(fd4.readValue(body, RuntimeTraceRegressionSuiteDefinitionDetailDto.class))
                .isEqualTo(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snapshot));
    }

    @TestConfiguration
    static class Fd4OnlyJsonConverters implements WebMvcConfigurer {

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.clear();
            converters.add(new MappingJackson2HttpMessageConverter(fd4ObjectMapper()));
        }
    }
}

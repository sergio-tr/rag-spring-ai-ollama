package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.P39ImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionImportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionImportController.class, RuntimeTraceRegressionSuiteDefinitionImportControllerWebMvcTest.ImportTestConfig.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionImportControllerWebMvcTest {

    private static final String IMPORT_PATH = "/api/test/runtime-trace-regression-suite-definitions/import";

    @TestConfiguration
    static class ImportTestConfig {

        @Bean
        RuntimeTraceRegressionSuiteDefinitionImportService runtimeTraceRegressionSuiteDefinitionImportService(
                RuntimeTraceRegressionSuiteDefinitionService definitionService) {
            return new RuntimeTraceRegressionSuiteDefinitionImportService(definitionService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    private UUID userId;
    private UUID definitionIdPath;
    private ObjectMapper fd4;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionIdPath = UUID.randomUUID();
        fd4 = P39ImportZipTestUtil.fd4ObjectMapper();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private byte[] validDefinitionJson() throws Exception {
        RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionIdPath,
                        "import-name",
                        "desc",
                        1,
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-02T00:00:00Z"),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                        List.of(UUID.randomUUID()))));
        return fd4.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));
    }

    private byte[] validZip() throws Exception {
        return P39ImportZipTestUtil.buildConvergedP38Zip(
                fd4, Instant.parse("2024-06-01T00:00:00Z"), userId, definitionIdPath, validDefinitionJson());
    }

    @Test
    void t1_created201_locationEmptyBody_createOnce() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(definitionService.create(eq(userId), any(CreateDefinitionCommand.class))).thenReturn(createdId);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(validZip()))
                .andExpect(status().isCreated())
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getContentAsByteArray().length).isZero();
                            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                                    .isEqualTo("/api/test/runtime-trace-regression-suite-definitions/" + createdId);
                        });
        verify(definitionService, times(1)).create(eq(userId), any(CreateDefinitionCommand.class));
    }

    @Test
    void t2_queryString_neverCreate() throws Exception {
        mockMvc.perform(
                        post(IMPORT_PATH)
                                .queryParam("x", "1")
                                .contentType("application/zip")
                                .content(validZip()))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t3_contentTypeJson_neverCreate() throws Exception {
        mockMvc.perform(post(IMPORT_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t3b_contentTypeZipWithCharset_neverCreate() throws Exception {
        mockMvc.perform(
                        post(IMPORT_PATH)
                                .header(HttpHeaders.CONTENT_TYPE, "application/zip; charset=UTF-8")
                                .content(validZip()))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t3c_malformedContentType_neverCreate() throws Exception {
        mockMvc.perform(post(IMPORT_PATH).header(HttpHeaders.CONTENT_TYPE, "@@@").content(validZip()))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t4_emptyBody_neverCreate() throws Exception {
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(new byte[0]))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t5_garbageZip_neverCreate() throws Exception {
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(new byte[] {0x50, 0x4b, 0x03}))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t6_firstEntryNotManifest_neverCreate() throws Exception {
        byte[] man = "{}".getBytes();
        byte[] def = validDefinitionJson();
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("wrong.json", man, "definition.json", def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t7_secondEntryNotDefinition_neverCreate() throws Exception {
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] manBytes = fd4.writeValueAsBytes(man);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("manifest.json", manBytes, "wrong.json", validDefinitionJson());
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t8_wrongExportKind_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "OTHER");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] manBytes = fd4.writeValueAsBytes(man);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(manBytes, def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t9_truncatedTrue_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", true);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t9_truncatedAbsent_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t9_truncatedNotBoolean_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", "yes");
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t10_schemaVersionStringOne_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", "1");
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t10_schemaVersionMissing_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t10_schemaVersionIntegralNotOne_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 2);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t11_createIllegalState_conflict() throws Exception {
        when(definitionService.create(eq(userId), any(CreateDefinitionCommand.class)))
                .thenThrow(
                        new IllegalStateException(
                                "A regression suite definition with this name already exists for the user",
                                new RuntimeException()));
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(validZip()))
                .andExpect(status().isConflict());
        verify(definitionService, times(1)).create(eq(userId), any(CreateDefinitionCommand.class));
    }

    @Test
    void t12_zipSizeBytesMismatch_neverCreate() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 1L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t13_definitionBeforeManifest_neverCreate() throws Exception {
        byte[] man = fd4.writeValueAsBytes(
                new com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportManifest(
                        1,
                        "REGRESSION_SUITE_DEFINITION",
                        Instant.now(),
                        userId.toString(),
                        "SAVED_DEFINITION_BY_ID",
                        java.util.Map.of("definitionId", definitionIdPath.toString()),
                        definitionIdPath.toString(),
                        0L,
                        false));
        byte[] def = validDefinitionJson();
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("definition.json", def, "manifest.json", man);
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t14_thirdZipEntry_neverCreate() throws Exception {
        byte[] zip =
                P39ImportZipTestUtil.buildConvergedP38ZipWithThirdRootEntry(
                        fd4,
                        Instant.parse("2024-07-01T00:00:00Z"),
                        userId,
                        definitionIdPath,
                        validDefinitionJson(),
                        new byte[] {9});
        mockMvc.perform(post(IMPORT_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(definitionService, never()).create(any(), any());
    }
}

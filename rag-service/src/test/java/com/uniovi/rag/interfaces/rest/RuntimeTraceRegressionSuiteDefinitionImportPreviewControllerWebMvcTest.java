package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.P39ImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class,
    RuntimeTraceRegressionSuiteDefinitionImportPreviewControllerWebMvcTest.PreviewTestConfig.class
})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionImportPreviewControllerWebMvcTest {

    private static final String PREVIEW_PATH = "/api/test/runtime-trace-regression-suite-definitions/import/preview";

    @TestConfiguration
    static class PreviewTestConfig {

        @Bean
        RuntimeTraceRegressionSuiteDefinitionImportPreviewService runtimeTraceRegressionSuiteDefinitionImportPreviewService() {
            return new RuntimeTraceRegressionSuiteDefinitionImportPreviewService();
        }
    }

    @Autowired
    private MockMvc mockMvc;

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
    void t1_validZip_200_jsonShape() throws Exception {
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(validZip()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).contains("application/json"))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.definition").isMap())
                .andExpect(jsonPath("$.definition.name").value("import-name"));
    }

    @Test
    void t2_queryString_400_emptyBody() throws Exception {
        mockMvc.perform(
                        post(PREVIEW_PATH)
                                .queryParam("x", "1")
                                .contentType("application/zip")
                                .content(validZip()))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t3_contentTypeJson_400_emptyBody() throws Exception {
        mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t17_contentTypeZipWithCharset_400_emptyBody() throws Exception {
        mockMvc.perform(
                        post(PREVIEW_PATH)
                                .header(HttpHeaders.CONTENT_TYPE, "application/zip;charset=UTF-8")
                                .content(validZip()))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t4_emptyBody_400() throws Exception {
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t5_garbageZip_400() throws Exception {
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(new byte[] {0x50, 0x4b, 0x03}))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t6_firstEntryNotManifest_400() throws Exception {
        byte[] man = "{}".getBytes();
        byte[] def = validDefinitionJson();
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("wrong.json", man, "definition.json", def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t7_secondEntryNotDefinition_400() throws Exception {
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] manBytes = fd4.writeValueAsBytes(man);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("manifest.json", manBytes, "wrong.json", validDefinitionJson());
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t8_wrongExportKind_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "OTHER");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t9_truncatedTrue_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", true);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t9_truncatedAbsent_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t9_truncatedNotBoolean_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", "yes");
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t10_schemaVersionStringOne_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", "1");
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t10_schemaVersionMissing_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t10_schemaVersionIntegralNotOne_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 2);
        man.put("truncated", false);
        man.put("zipSizeBytes", 999999L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t11_zipSizeBytesMismatch_400() throws Exception {
        byte[] def = validDefinitionJson();
        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        man.put("zipSizeBytes", 1L);
        byte[] zip = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t12_definitionBeforeManifest_400() throws Exception {
        byte[] man =
                fd4.writeValueAsBytes(
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
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t14_thirdZipEntry_400() throws Exception {
        byte[] zip =
                P39ImportZipTestUtil.buildConvergedP38ZipWithThirdRootEntry(
                        fd4,
                        Instant.parse("2024-07-01T00:00:00Z"),
                        userId,
                        definitionIdPath,
                        validDefinitionJson(),
                        new byte[] {9});
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    @Test
    void t19_firstEntryDeflated_400() throws Exception {
        byte[] manifest = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] def = validDefinitionJson();
        byte[] zip = writeManifestDeflatedThenDefinitionStored(manifest, def);
        mockMvc.perform(post(PREVIEW_PATH).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isZero());
    }

    private static byte[] writeManifestDeflatedThenDefinitionStored(byte[] manifest, byte[] definition)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(manifest.length + definition.length + 256);
        try (ZipOutputStream zos =
                        new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            ZipEntry e1 = new ZipEntry("manifest.json");
            zos.putNextEntry(e1);
            zos.write(manifest);
            zos.closeEntry();

            ZipEntry e2 = new ZipEntry("definition.json");
            e2.setMethod(ZipEntry.STORED);
            e2.setSize(definition.length);
            e2.setCompressedSize(definition.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(definition);
            e2.setCrc(crc.getValue());
            zos.putNextEntry(e2);
            zos.write(definition);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

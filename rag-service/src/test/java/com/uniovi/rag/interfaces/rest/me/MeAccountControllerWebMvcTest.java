package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.async.AsyncTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MeAccountController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeAccountController.class)
class MeAccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private AccountExportArtifactRepository accountExportArtifactRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
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
    void export_returnsAccepted() throws Exception {
        UUID job = UUID.randomUUID();
        when(asyncTaskService.submitAccountExport(eq(userId))).thenReturn(job);

        mockMvc.perform(post("/api/v5/me/account/export"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.toString()))
                .andExpect(jsonPath("$.pollPath").value("/api/v5/me/account/jobs/" + job));
    }

    @Test
    void accountJob_returnsDto() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.getAccountJobStatus(eq(taskId), eq(userId)))
                .thenReturn(
                        new AsyncTaskStatusDto(
                                taskId,
                                "ACCOUNT_EXPORT",
                                "SUCCEEDED",
                                null,
                                Map.of("exportArtifactId", "a1"),
                                null,
                                true,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:01Z"),
                                Instant.parse("2025-01-01T00:00:02Z"),
                                Instant.parse("2025-01-01T00:00:03Z")));

        mockMvc.perform(get("/api/v5/me/account/jobs/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("ACCOUNT_EXPORT"));
    }

    @Test
    void download_returnsZip() throws Exception {
        UUID exportId = UUID.randomUUID();
        Path tmp = Files.createTempFile("me-export-", ".zip");
        Files.writeString(tmp, "zip", StandardCharsets.UTF_8);

        UserEntity u = mock(UserEntity.class);
        AccountExportArtifactEntity a = AccountExportArtifactEntity.newArtifact();
        a.setId(exportId);
        a.setUser(u);
        a.setStorageUri(tmp.toAbsolutePath().toString());
        a.setSha256("ab".repeat(32));
        a.setByteSize(3);
        a.setStatus(AccountExportArtifactStatus.READY);
        a.setCreatedAt(Instant.now());
        a.setExpiresAt(Instant.now().plusSeconds(3600));

        when(accountExportArtifactRepository.findByIdAndUser_Id(eq(exportId), eq(userId)))
                .thenReturn(Optional.of(a));

        mockMvc.perform(get("/api/v5/me/account/export/{id}/download", exportId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void deletion_returnsAccepted_whenEmailMatches() throws Exception {
        UUID job = UUID.randomUUID();
        UserEntity u = mock(UserEntity.class);
        when(u.getEmail()).thenReturn("u@test");
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(u));
        when(asyncTaskService.submitAccountDeletion(eq(userId))).thenReturn(job);

        mockMvc.perform(
                        post("/api/v5/me/account/deletion")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":\"DELETE_ACCOUNT_AND_ALL_DATA\",\"email\":\"u@test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.toString()));
    }
}

package com.uniovi.rag.application.service.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountExportApplicationServiceTest {

    @Mock
    private AccountExportSnapshotLoader snapshotLoader;

    @Mock
    private AccountExportArtifactRegistrar artifactRegistrar;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void runExport_writesZipWithManifestV2_andDelegatesCompletion(@TempDir Path exportRoot) throws Exception {
        RagAccountProperties props = new RagAccountProperties();
        props.setExportStorageDir(exportRoot.toString());
        props.setExportTtlHours(1);

        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        AccountExportSnapshotLoader.ExportSnapshot snap =
                new AccountExportSnapshotLoader.ExportSnapshot(
                        user,
                        Map.of("email", "a@b.c"),
                        Map.of(),
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(Map.of("id", "m1", "content", "hello")),
                        List.of(),
                        List.of(),
                        Map.of("runs", List.of()),
                        List.of(),
                        AccountExportExclusions.build());
        when(snapshotLoader.load(userId)).thenReturn(snap);

        AccountExportApplicationService svc =
                new AccountExportApplicationService(snapshotLoader, artifactRegistrar, props, new ObjectMapper());

        svc.runExport(task, mutation);

        verify(mutation).appendProgressLine(eq(taskId), ArgumentMatchers.contains("Collecting"));
        verify(mutation).appendProgressLine(eq(taskId), ArgumentMatchers.contains("ZIP"));

        ArgumentCaptor<AccountExportCompletion> cap = ArgumentCaptor.forClass(AccountExportCompletion.class);
        verify(artifactRegistrar).saveAndCompleteTask(cap.capture());
        AccountExportCompletion c = cap.getValue();
        assertThat(c.taskId()).isEqualTo(taskId);
        assertThat(c.user()).isSameAs(user);
        assertThat(Files.exists(c.zipPath())).isTrue();
        assertThat(c.sha256()).hasSize(64);
        assertThat(c.byteSize()).isGreaterThan(0L);

        try (ZipFile zip = new ZipFile(c.zipPath().toFile())) {
            assertThat(zip.getEntry("manifest.json")).isNotNull();
            assertThat(zip.getEntry("messages.json")).isNotNull();
            assertThat(zip.getEntry("exclusions.json")).isNotNull();
            JsonNode manifest =
                    new ObjectMapper()
                            .readTree(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes());
            assertThat(manifest.get("schemaVersion").asInt()).isEqualTo(2);
            assertThat(manifest.get("entries").isArray()).isTrue();
            assertThat(manifest.get("entries").size()).isGreaterThan(0);
        }
    }
}

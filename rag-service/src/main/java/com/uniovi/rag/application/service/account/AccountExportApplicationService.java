package com.uniovi.rag.application.service.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Orchestrates account export: load snapshot (transactional), write ZIP (filesystem), register artifact (transactional).
 */
@Service
public class AccountExportApplicationService {

    private final AccountExportSnapshotLoader snapshotLoader;
    private final AccountExportArtifactRegistrar artifactRegistrar;
    private final RagAccountProperties accountProperties;
    private final ObjectMapper objectMapper;

    public AccountExportApplicationService(
            AccountExportSnapshotLoader snapshotLoader,
            AccountExportArtifactRegistrar artifactRegistrar,
            RagAccountProperties accountProperties,
            ObjectMapper objectMapper) {
        this.snapshotLoader = snapshotLoader;
        this.artifactRegistrar = artifactRegistrar;
        this.accountProperties = accountProperties;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void runExport(AsyncTaskEntity task, AsyncTaskMutationService mutation)
            throws IOException, NoSuchAlgorithmException {
        UUID taskId = task.getId();
        UUID userId = task.getUser().getId();
        mutation.appendProgressLine(taskId, "Collecting account data…");

        AccountExportSnapshotLoader.ExportSnapshot snap = snapshotLoader.load(userId);
        mutation.appendProgressLine(taskId, "Writing ZIP…");

        Path baseDir = accountProperties.getExportStorageDir().resolve(userId.toString());
        Files.createDirectories(baseDir);
        UUID artifactId = UUID.randomUUID();
        Path zipPath = baseDir.resolve(artifactId + ".zip");

        writeZip(
                zipPath,
                snap.manifest(),
                snap.profile(),
                snap.preferences(),
                snap.personalization(),
                snap.projects(),
                snap.conversations(),
                snap.documents());

        long byteSize = Files.size(zipPath);
        String sha256 = sha256Hex(zipPath);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds((long) accountProperties.getExportTtlHours() * 3600);

        artifactRegistrar.saveAndCompleteTask(
                new AccountExportCompletion(
                        task, taskId, artifactId, snap.user(), zipPath, sha256, byteSize, now, expiresAt, mutation));
    }

    private void writeZip(
            Path zipPath,
            Map<String, Object> manifest,
            Map<String, Object> profile,
            Map<String, Object> prefs,
            Map<String, Object> pers,
            java.util.List<Map<String, Object>> projects,
            java.util.List<Map<String, Object>> conversations,
            java.util.List<Map<String, Object>> documents)
            throws IOException {
        try (ZipOutputStream zos =
                new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)), StandardCharsets.UTF_8)) {
            putJson(zos, "manifest.json", manifest);
            putJson(zos, "profile.json", profile);
            putJson(zos, "preferences.json", prefs);
            putJson(zos, "personalization.json", pers);
            putJson(zos, "projects.json", Map.of("items", projects));
            putJson(zos, "conversations.json", Map.of("items", conversations));
            putJson(zos, "documents.json", Map.of("items", documents));
        }
    }

    private void putJson(ZipOutputStream zos, String name, Object value) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        zos.write(bytes);
        zos.closeEntry();
    }

    private static String sha256Hex(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(file), md)) {
            dis.transferTo(OutputStream.nullOutputStream());
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

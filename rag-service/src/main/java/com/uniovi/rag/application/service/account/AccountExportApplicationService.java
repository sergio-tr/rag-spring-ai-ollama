package com.uniovi.rag.application.service.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.configuration.RagAccountProperties;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/**
 * Orchestrates account export: load snapshot (transactional), write ZIP (filesystem), register artifact (transactional).
 */
@Service
public class AccountExportApplicationService {

    public static final int MANIFEST_SCHEMA_VERSION = 2;

    private static final String EXPORT_JSON_ITEMS_KEY = "items";

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

        writeZip(zipPath, userId, snap);

        long byteSize = Files.size(zipPath);
        String sha256 = sha256Hex(zipPath);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds((long) accountProperties.getExportTtlHours() * 3600);

        artifactRegistrar.saveAndCompleteTask(
                new AccountExportCompletion(
                        task, taskId, artifactId, snap.user(), zipPath, sha256, byteSize, now, expiresAt, mutation));
    }

    private void writeZip(Path zipPath, UUID userId, AccountExportSnapshotLoader.ExportSnapshot snap)
            throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("profile.json", objectMapper.writeValueAsBytes(snap.profile()));
        payloads.put("preferences.json", objectMapper.writeValueAsBytes(snap.preferences()));
        payloads.put("personalization.json", objectMapper.writeValueAsBytes(snap.personalization()));
        payloads.put("projects.json", objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.projects())));
        payloads.put("conversations.json", objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.conversations())));
        payloads.put("messages.json", objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.messages())));
        payloads.put("documents.json", objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.documents())));
        payloads.put(
                "rag-config-summary.json",
                objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.ragConfigSummary())));
        payloads.put("lab-evaluation-summary.json", objectMapper.writeValueAsBytes(snap.labEvaluationSummary()));
        payloads.put(
                "classifier-models.json",
                objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.classifierModels())));
        payloads.put("exclusions.json", objectMapper.writeValueAsBytes(Map.of(EXPORT_JSON_ITEMS_KEY, snap.exclusions())));

        List<Map<String, Object>> manifestEntries = new ArrayList<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, byte[]> entry : payloads.entrySet()) {
            byte[] bytes = entry.getValue();
            md.reset();
            String fileSha = hex(md.digest(bytes));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", entry.getKey());
            row.put("sha256", fileSha);
            row.put("byteSize", bytes.length);
            manifestEntries.add(row);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", MANIFEST_SCHEMA_VERSION);
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("userId", userId.toString());
        manifest.put("entries", manifestEntries);
        payloads.put("manifest.json", objectMapper.writeValueAsBytes(manifest));

        Set<String> writeOrder = new LinkedHashSet<>(payloads.keySet());
        writeOrder.remove("manifest.json");
        writeOrder.add("manifest.json");

        try (ZipOutputStream zos =
                new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)), StandardCharsets.UTF_8)) {
            for (String name : writeOrder) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(payloads.get(name));
                zos.closeEntry();
            }
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(file), md)) {
            dis.transferTo(OutputStream.nullOutputStream());
        }
        return hex(md.digest());
    }
}

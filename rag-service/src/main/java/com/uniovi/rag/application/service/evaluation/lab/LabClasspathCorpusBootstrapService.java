package com.uniovi.rag.application.service.evaluation.lab;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Explicit Lab bootstrap: load classpath resources as PROJECT_SHARED project documents before RAG benchmark.
 */
@Service
public class LabClasspathCorpusBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(LabClasspathCorpusBootstrapService.class);

    /** Wait for async-shaped rows left INGESTING (defensive; bootstrap ingest is synchronous). */
    private static final int POLL_MS = 200;

    private static final long POLL_TIMEOUT_MS = 180_000L;

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ProjectAccessService projectAccessService;
    private final ResourcePatternResolver resourceResolver;

    @Autowired
    public LabClasspathCorpusBootstrapService(
            KnowledgeIngestionService knowledgeIngestionService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            EvaluationRunRepository evaluationRunRepository,
            ProjectAccessService projectAccessService) {
        this(
                knowledgeIngestionService,
                knowledgeDocumentRepository,
                evaluationRunRepository,
                projectAccessService,
                new PathMatchingResourcePatternResolver());
    }

    LabClasspathCorpusBootstrapService(
            KnowledgeIngestionService knowledgeIngestionService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            EvaluationRunRepository evaluationRunRepository,
            ProjectAccessService projectAccessService,
            ResourcePatternResolver resourceResolver) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.projectAccessService = projectAccessService;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Loads classpath corpus for the run's project when {@code aggregates_json.corpusBootstrapPolicy.enabled=true}.
     */
    public LabCorpusBootstrapResult bootstrap(UUID userId, EvaluationRunEntity run) {
        if (run == null) {
            return disabledBootstrapSummary();
        }
        UUID projectId =
                run.getProject() != null && run.getProject().getId() != null
                        ? run.getProject().getId()
                        : evaluationRunRepository.findEffectiveProjectIdByRunId(run.getId()).orElse(null);
        return bootstrap(userId, run.getId(), projectId, run.getAggregatesJson());
    }

    /**
     * Classpath bootstrap without JPA run graph (safe for async workers).
     */
    public LabCorpusBootstrapResult bootstrap(
            UUID userId, UUID runId, UUID projectIdHint, Map<String, Object> aggregatesJson) {
        Instant startedAt = Instant.now();
        List<String> errors = new ArrayList<>();
        List<UUID> docIds = new ArrayList<>();

        Map<String, Object> policy = readPolicy(aggregatesJson);
        if (policy == null || !Boolean.TRUE.equals(policy.get("enabled"))) {
            return new LabCorpusBootstrapResult(
                    false,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    startedAt,
                    Instant.now());
        }

        UUID projectId = projectIdHint;
        if (projectId == null && runId != null) {
            projectId = evaluationRunRepository.findEffectiveProjectIdByRunId(runId).orElse(null);
        }
        if (projectId == null) {
            throw new IllegalStateException(
                    LabCorpusBootstrapErrors.REQUIRES_PROJECT
                            + ": Classpath corpus bootstrap requires evaluation corpus index scope.");
        }
        String corpusScopeName = String.valueOf(policy.getOrDefault("corpusScope", CorpusScope.PROJECT_SHARED.name()));
        if (!CorpusScope.PROJECT_SHARED.name().equalsIgnoreCase(corpusScopeName)) {
            throw new IllegalStateException(
                    LabCorpusBootstrapErrors.UNSUPPORTED_CORPUS_SCOPE
                            + ": Only PROJECT_SHARED is supported for Lab classpath bootstrap; scope="
                            + corpusScopeName);
        }

        String location = String.valueOf(policy.getOrDefault("classpathDocsLocation", "classpath*:docs/**/*"));
        boolean skipExisting = policy.get("skipExisting") == null || Boolean.TRUE.equals(policy.get("skipExisting"));
        boolean failOnDocumentError =
                policy.get("failOnDocumentError") == null || Boolean.TRUE.equals(policy.get("failOnDocumentError"));

        String pattern = normalizeClasspathPattern(location);
        projectAccessService.requireOwnedProject(userId, projectId);

        List<Resource> rawFiles;
        try {
            rawFiles = listReadableFileResources(pattern);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "LAB_CLASSPATH_SCAN_FAILED: Could not scan classpath for corpus files: " + e.getMessage(), e);
        }

        List<Resource> files = filterCorpusResources(rawFiles);
        if (files.isEmpty()) {
            if (!rawFiles.isEmpty()) {
                throw new IllegalStateException(
                        LabCorpusBootstrapErrors.formatOnlyNonCorpusResourcesMatched(pattern, rawFiles.size()));
            }
            throw new IllegalStateException(LabCorpusBootstrapErrors.formatNoDocumentsMatched(pattern));
        }

        int discovered = files.size();
        int created = 0;
        int reused = 0;
        int failed = 0;
        int skipped = 0;

        files.sort(Comparator.comparing(r -> r.getFilename() != null ? r.getFilename() : ""));

        for (Resource resource : files) {
            String filename = resource.getFilename();
            if (filename == null || filename.isBlank()) {
                continue;
            }
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                failed++;
                errors.add(filename + ": " + e.getMessage());
                log.warn("Classpath corpus bootstrap I/O failure for {}: {}", filename, e.getMessage());
                if (failOnDocumentError) {
                    throw new IllegalStateException(
                            "LAB_CORPUS_BOOTSTRAP_IO: " + filename + ": " + e.getMessage(), e);
                }
                continue;
            }
            if (bytes.length == 0) {
                skipped++;
                log.info("Classpath corpus bootstrap skipped empty file: {}", filename);
                continue;
            }

            String sha = sha256Hex(bytes);
            String contentType = guessContentType(filename);

            Optional<KnowledgeDocumentEntity> existing =
                    knowledgeDocumentRepository.findFirstByProject_IdAndFileNameAndCorpusScopeAndConversationIsNull(
                            projectId, filename, CorpusScope.PROJECT_SHARED);

            try {
                if (existing.isPresent()) {
                    KnowledgeDocumentEntity row = existing.get();
                    if (row.getStatus() == ProjectDocumentStatus.INGESTING) {
                        waitUntilTerminal(projectId, row.getId());
                        row = knowledgeDocumentRepository.findById(row.getId()).orElseThrow();
                    }
                    if (skipExisting
                            && row.getStatus() == ProjectDocumentStatus.READY
                            && row.getStorageUri() != null
                            && !row.getStorageUri().isBlank()) {
                        String checksum = row.getContentChecksum();
                        if (checksum == null || checksum.equalsIgnoreCase(sha)) {
                            reused++;
                            docIds.add(row.getId());
                            continue;
                        }
                    }

                    var dto =
                            knowledgeIngestionService.reingestProjectSharedDocumentSynchronouslyFromBytes(
                                    userId, projectId, row.getId(), bytes, filename, contentType);
                    docIds.add(dto.id());
                    created++;
                    continue;
                }

                var dto =
                        knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                                userId, projectId, bytes, filename, contentType);
                docIds.add(dto.id());
                created++;

            } catch (RuntimeException | IOException ex) {
                failed++;
                errors.add(filename + ": " + ex.getMessage());
                log.warn("Classpath corpus bootstrap failed for {}: {}", filename, ex.getMessage());
                if (failOnDocumentError) {
                    throw new IllegalStateException(
                            "LAB_CORPUS_BOOTSTRAP_DOCUMENT_FAILED: " + filename + ": " + ex.getMessage(), ex);
                }
            }
        }

        validatePostConditions(projectId, docIds, failOnDocumentError, errors);

        int ready = countReadyWithStorage(projectId, docIds);
        Instant completedAt = Instant.now();

        log.info(
                "Lab classpath corpus bootstrap finished: classpathDocsLocation={} corpusScope={} bootstrapDocumentsFound={} "
                        + "bootstrapDocumentsCreated={} bootstrapDocumentsReused={} bootstrapDocumentsSkipped={} bootstrapDocumentsFailed={} "
                        + "bootstrapDocumentsReady={}",
                pattern,
                corpusScopeName,
                discovered,
                created,
                reused,
                skipped,
                failed,
                ready);

        return new LabCorpusBootstrapResult(
                true,
                pattern,
                CorpusScope.PROJECT_SHARED.name(),
                discovered,
                created,
                reused,
                ready,
                failed,
                skipped,
                docIds,
                errors.isEmpty() ? List.of() : List.copyOf(errors),
                startedAt,
                completedAt);
    }

    private void validatePostConditions(UUID projectId, List<UUID> docIds, boolean failOnError, List<String> errors) {
        if (docIds.isEmpty()) {
            throw new IllegalStateException(
                    LabCorpusBootstrapErrors.NO_DOCUMENTS
                            + ": No project documents were created or reused. "
                            + "All classpath files may have been empty, skipped, or failed ingestion. "
                            + "Review bootstrapFailOnDocumentError and server logs.");
        }
        int ready = countReadyWithStorage(projectId, docIds);
        if (ready == 0) {
            throw new IllegalStateException(
                    LabCorpusBootstrapErrors.NO_READY_DOCUMENTS
                            + ": No READY documents with storage after classpath bootstrap. "
                            + "Verify ingestion writes storage_uri for PROJECT_SHARED uploads.");
        }
        for (UUID id : docIds) {
            KnowledgeDocumentEntity d = knowledgeDocumentRepository.findByIdAndProject_Id(id, projectId).orElse(null);
            if (d == null) {
                continue;
            }
            if (d.getStatus() == ProjectDocumentStatus.READY) {
                if (d.getStorageUri() == null || d.getStorageUri().isBlank()) {
                    if (failOnError) {
                        throw new IllegalStateException(
                                LabCorpusBootstrapErrors.MISSING_STORAGE_URI
                                        + ": READY document "
                                        + d.getFileName()
                                        + " has no storage_uri.");
                    }
                    errors.add(d.getFileName() + ": missing storage_uri on READY");
                }
            }
        }
    }

    private int countReadyWithStorage(UUID projectId, List<UUID> docIds) {
        int n = 0;
        for (UUID id : docIds) {
            KnowledgeDocumentEntity d = knowledgeDocumentRepository.findByIdAndProject_Id(id, projectId).orElse(null);
            if (d != null
                    && d.getStatus() == ProjectDocumentStatus.READY
                    && d.getStorageUri() != null
                    && !d.getStorageUri().isBlank()) {
                n++;
            }
        }
        return n;
    }

    private void waitUntilTerminal(UUID projectId, UUID documentId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            KnowledgeDocumentEntity d =
                    knowledgeDocumentRepository.findByIdAndProject_Id(documentId, projectId).orElse(null);
            if (d == null) {
                return;
            }
            if (d.getStatus() != ProjectDocumentStatus.INGESTING) {
                return;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("LAB_CORPUS_BOOTSTRAP_INGEST_TIMEOUT: documentId=" + documentId);
    }

    private static LabCorpusBootstrapResult disabledBootstrapSummary() {
        Instant now = Instant.now();
        return new LabCorpusBootstrapResult(
                false, null, null, 0, 0, 0, 0, 0, 0, List.of(), List.of(), now, now);
    }

    private static Map<String, Object> readPolicy(Map<String, Object> aggregatesJson) {
        if (aggregatesJson == null || aggregatesJson.isEmpty()) {
            return null;
        }
        Object o = aggregatesJson.get("corpusBootstrapPolicy");
        return o instanceof Map<?, ?> m ? castStringKeyed(m) : null;
    }

    private static Map<String, Object> readPolicy(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null) {
            return null;
        }
        return readPolicy(run.getAggregatesJson());
    }

    private static Map<String, Object> castStringKeyed(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static String normalizeClasspathPattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return "classpath*:docs/**/*";
        }
        String t = raw.trim();
        if (t.startsWith("classpath:/") && !t.startsWith("classpath*:")) {
            t = "classpath*:" + t.substring("classpath:".length());
        } else if (t.startsWith("classpath:") && !t.startsWith("classpath*:")) {
            t = "classpath*:" + t.substring("classpath:".length());
        }
        return t;
    }

    private List<Resource> listReadableFileResources(String pattern) throws IOException {
        Resource[] res = resourceResolver.getResources(pattern);
        List<Resource> out = new ArrayList<>();
        for (Resource r : res) {
            if (r == null || !r.exists() || !r.isReadable()) {
                continue;
            }
            String fn = r.getFilename();
            if (fn == null || fn.isBlank() || fn.endsWith("/")) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    /**
     * Keeps thesis corpus files (PDF actas, text, markdown, HTML) and drops noise such as {@code .gitkeep}.
     */
    static List<Resource> filterCorpusResources(List<Resource> raw) {
        List<Resource> out = new ArrayList<>();
        for (Resource r : raw) {
            String fn = r.getFilename();
            if (isSupportedCorpusFilename(fn)) {
                out.add(r);
            }
        }
        return out;
    }

    static boolean isSupportedCorpusFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gitkeep")) {
            return false;
        }
        // Template / documentation filenames under docs/ should not pollute the benchmark corpus.
        if (lower.equals("readme.md") || lower.equals("readme.txt")) {
            return false;
        }
        return lower.endsWith(".pdf")
                || lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".html")
                || lower.endsWith(".htm");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String guessContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "text/plain";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        return "application/octet-stream";
    }
}

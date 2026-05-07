package com.uniovi.rag.service.admin;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminModelsService {

    private static final long PULL_TIMEOUT_MS = 10L * 60 * 1000;
    private static final long EMBEDDING_PROBE_TIMEOUT_MS = 10_000;

    private final AllowedModelRepository allowedModelRepository;
    private final OllamaApiClient ollamaApiClient;

    public AdminModelsService(AllowedModelRepository allowedModelRepository, OllamaApiClient ollamaApiClient) {
        this.allowedModelRepository = allowedModelRepository;
        this.ollamaApiClient = ollamaApiClient;
    }

    @Transactional(readOnly = true)
    public List<AdminModelEntryDto> list() {
        return allowedModelRepository.findAll().stream()
                .map(AdminModelsService::toDto)
                .sorted(Comparator.comparing(AdminModelEntryDto::modelId).thenComparing(e -> e.modelType().name()))
                .toList();
    }

    @Transactional
    public AdminModelCheckResponse check(AdminModelCheckRequest req) {
        Instant now = Instant.now();
        String requested = req.modelId().trim();
        AllowedModelType type = req.modelType();

        Set<String> installed = tagsOrThrow();
        List<String> matches = findMatches(requested, installed);
        boolean exists = !matches.isEmpty();
        boolean canPull = true;
        boolean pulled = false;
        String pullSummary = null;

        if (!exists && req.pullIfMissing()) {
            try {
                ollamaApiClient.pullModel(requested, PULL_TIMEOUT_MS);
                pulled = true;
                pullSummary = "pull ok";
            } catch (IOException e) {
                throw new AdminModelCheckException("MODEL_PULL_FAILED", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AdminModelCheckException("MODEL_PULL_FAILED", "Interrupted while pulling model");
            }
            installed = tagsOrThrow();
            matches = findMatches(requested, installed);
            exists = !matches.isEmpty();
        }

        boolean embeddingProbeOk = true;
        if (type == AllowedModelType.EMBEDDING && exists) {
            String chosen = pickBestId(requested, matches);
            try {
                embeddingProbeOk = ollamaApiClient.probeEmbedding(chosen, "ping", EMBEDDING_PROBE_TIMEOUT_MS);
            } catch (Exception e) {
                embeddingProbeOk = false;
            }
            if (!embeddingProbeOk) {
                throw new AdminModelCheckException("MODEL_TYPE_MISMATCH", "Model did not pass embedding probe");
            }
        }

        if (!exists) {
            return new AdminModelCheckResponse(
                    requested,
                    type,
                    false,
                    canPull,
                    pulled,
                    false,
                    matches,
                    now,
                    "MODEL_NOT_FOUND",
                    "Model not installed locally",
                    pullSummary);
        }

        return new AdminModelCheckResponse(
                pickBestId(requested, matches),
                type,
                true,
                canPull,
                pulled,
                type != AllowedModelType.EMBEDDING || embeddingProbeOk,
                matches,
                now,
                null,
                null,
                pullSummary);
    }

    @Transactional
    public AdminModelEntryDto upsert(AdminModelUpsertRequest req) {
        String requestedId = req.modelId().trim();
        AdminModelCheckResponse checked = check(new AdminModelCheckRequest(requestedId, req.modelType(), req.pullIfMissing()));
        if (!checked.existsLocal() && req.enabled()) {
            throw new AdminModelCheckException("MODEL_NOT_FOUND", "Cannot enable a model that is not installed");
        }

        String modelId = checked.existsLocal() ? checked.modelId() : requestedId;
        Optional<AllowedModelEntity> existing = allowedModelRepository.findByNameAndType(modelId, req.modelType());
        AllowedModelEntity e = existing.orElseGet(() -> AllowedModelEntity.newRow(modelId, req.modelType(), false, null));
        e.setName(modelId);
        e.setType(req.modelType());
        e.setInAllowlist(req.enabled());
        e.setDisplayName(req.displayName() != null && !req.displayName().isBlank() ? req.displayName().trim() : null);
        e.setTags(req.tags() != null ? List.copyOf(req.tags()) : List.of());
        e.setAvailable(checked.existsLocal());
        e.setLastCheckedAt(checked.checkedAt());
        if (checked.existsLocal()) {
            e.setLastPullStatus(checked.pulled() ? "PULLED" : "OK");
            e.setLastPullError(null);
            if (e.getInstalledAt() == null) {
                e.setInstalledAt(Instant.now());
            }
        } else {
            e.setLastPullStatus("MISSING");
            e.setLastPullError(checked.errorMessage());
        }
        return toDto(allowedModelRepository.save(e));
    }

    private Set<String> tagsOrThrow() {
        try {
            return ollamaApiClient.listModelNames();
        } catch (IOException e) {
            throw new AdminModelCheckException("OLLAMA_UNAVAILABLE", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdminModelCheckException("OLLAMA_UNAVAILABLE", "Interrupted while calling /api/tags");
        } catch (Exception e) {
            throw new AdminModelCheckException("OLLAMA_UNAVAILABLE", e.getMessage());
        }
    }

    private static List<String> findMatches(String requested, Set<String> installed) {
        if (requested == null || requested.isBlank() || installed == null || installed.isEmpty()) {
            return List.of();
        }
        String r = requested.trim();
        String rLower = r.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : installed) {
            if (name == null) continue;
            String n = name.trim();
            if (n.equals(r) || n.equalsIgnoreCase(r)) {
                out.add(n);
            } else if (!r.contains(":") && n.toLowerCase(Locale.ROOT).equals(rLower + ":latest")) {
                out.add(n);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static String pickBestId(String requested, List<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return requested != null ? requested.trim() : "";
        }
        // prefer exact
        for (String m : matches) {
            if (m != null && requested != null && m.equals(requested.trim())) {
                return m;
            }
        }
        return matches.getFirst();
    }

    private static AdminModelEntryDto toDto(AllowedModelEntity e) {
        return new AdminModelEntryDto(
                e.getId(),
                e.getName(),
                e.getDisplayName(),
                e.getType(),
                e.isInAllowlist(),
                e.isAvailable(),
                e.getLastCheckedAt(),
                e.getLastPullStatus(),
                e.getLastPullError(),
                e.getInstalledAt(),
                e.getTags());
    }
}


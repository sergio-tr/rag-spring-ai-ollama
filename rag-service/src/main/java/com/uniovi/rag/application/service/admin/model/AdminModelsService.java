package com.uniovi.rag.application.service.admin.model;

import com.uniovi.rag.application.service.model.OllamaInstalledModelMatcher;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaEmbeddingProbeResult;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelDeleteResponse;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpdateRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModelsService {

    private static final long PULL_TIMEOUT_MS = 10L * 60 * 1000;
    private static final long EMBEDDING_PROBE_TIMEOUT_MS = 10_000;

    private final AllowedModelRepository allowedModelRepository;
    private final OllamaApiClient ollamaApiClient;
    private final AllowedModelReferenceGuard referenceGuard;

    public AdminModelsService(
            AllowedModelRepository allowedModelRepository,
            OllamaApiClient ollamaApiClient,
            AllowedModelReferenceGuard referenceGuard) {
        this.allowedModelRepository = allowedModelRepository;
        this.ollamaApiClient = ollamaApiClient;
        this.referenceGuard = referenceGuard;
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
        List<String> matches = OllamaInstalledModelMatcher.findMatchingInstalledNames(requested, installed);
        boolean exists = !matches.isEmpty();
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
            matches = OllamaInstalledModelMatcher.findMatchingInstalledNames(requested, installed);
            exists = !matches.isEmpty();
        }

        if (!exists) {
            return new AdminModelCheckResponse(
                    requested,
                    type,
                    false,
                    true,
                    pulled,
                    false,
                    matches,
                    now,
                    "MODEL_NOT_FOUND",
                    "Model not installed locally",
                    null,
                    pullSummary);
        }

        String resolvedId = OllamaInstalledModelMatcher.pickBestInstalledName(requested, matches);
        if (type == AllowedModelType.EMBEDDING) {
            return checkEmbedding(resolvedId, type, matches, now, pulled, pullSummary);
        }
        return new AdminModelCheckResponse(
                resolvedId,
                type,
                true,
                true,
                pulled,
                true,
                matches,
                now,
                null,
                null,
                null,
                pullSummary);
    }

    @Transactional
    public AdminModelCheckResponse reprobe(UUID id) {
        AllowedModelEntity row = findOrThrow(id);
        return check(new AdminModelCheckRequest(row.getName(), row.getType(), false));
    }

    @Transactional
    public AdminModelEntryDto upsert(AdminModelUpsertRequest req) {
        String requestedId = req.modelId().trim();
        AdminModelCheckResponse checked = check(new AdminModelCheckRequest(requestedId, req.modelType(), req.pullIfMissing()));
        if (!checked.existsLocal() && req.enabled()) {
            throw new AdminModelCheckException("MODEL_NOT_FOUND", "Cannot enable a model that is not installed");
        }
        if (req.enabled()
                && req.modelType() == AllowedModelType.EMBEDDING
                && checked.existsLocal()
                && !checked.embeddingProbeOk()) {
            throw new AdminModelCheckException(
                    checked.errorCode() != null ? checked.errorCode() : "MODEL_EMBEDDING_PROBE_FAILED",
                    checked.errorMessage() != null ? checked.errorMessage() : "Embedding probe failed");
        }

        String modelId = checked.existsLocal() ? checked.modelId() : requestedId;
        Optional<AllowedModelEntity> existing = allowedModelRepository.findByNameAndType(modelId, req.modelType());
        AllowedModelEntity e = existing.orElseGet(() -> AllowedModelEntity.newRow(modelId, req.modelType(), false, null));
        applyCheckMetadata(e, req.displayName(), req.modelType(), req.enabled(), req.tags(), checked);
        return toDto(allowedModelRepository.save(e));
    }

    @Transactional
    public AdminModelEntryDto update(UUID id, AdminModelUpdateRequest req) {
        AllowedModelEntity e = findOrThrow(id);
        if (req.modelType() != null && req.modelType() != e.getType()) {
            allowedModelRepository
                    .findByNameAndType(e.getName(), req.modelType())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Another entry already uses this name and type");
                    });
            e.setType(req.modelType());
        }
        if (req.displayName() != null) {
            e.setDisplayName(req.displayName().isBlank() ? null : req.displayName().trim());
        }
        if (req.tags() != null) {
            e.setTags(List.copyOf(req.tags()));
        }
        if (req.enabled() != null) {
            if (req.enabled() && !e.isAvailable()) {
                AdminModelCheckResponse checked = check(new AdminModelCheckRequest(e.getName(), e.getType(), false));
                if (!checked.existsLocal()) {
                    throw new AdminModelCheckException("MODEL_NOT_FOUND", "Cannot enable a model that is not installed");
                }
                if (e.getType() == AllowedModelType.EMBEDDING && !checked.embeddingProbeOk()) {
                    throw new AdminModelCheckException(
                            checked.errorCode() != null ? checked.errorCode() : "MODEL_EMBEDDING_PROBE_FAILED",
                            checked.errorMessage() != null ? checked.errorMessage() : "Embedding probe failed");
                }
                applyCheckMetadata(e, e.getDisplayName(), e.getType(), true, e.getTags(), checked);
            } else {
                e.setInAllowlist(req.enabled());
            }
        }
        return toDto(allowedModelRepository.save(e));
    }

    @Transactional
    public AdminModelDeleteResponse delete(UUID id) {
        AllowedModelEntity e = findOrThrow(id);
        if (referenceGuard.isReferenced(e.getName())) {
            e.setInAllowlist(false);
            e.setAvailable(false);
            e.setLastPullStatus("DISABLED");
            e.setLastPullError("Disabled because historical runs or project profiles reference this model");
            allowedModelRepository.save(e);
            return new AdminModelDeleteResponse(
                    e.getId(),
                    e.getName(),
                    e.getType(),
                    "DISABLED",
                    "Model disabled because evaluation history references it");
        }
        allowedModelRepository.delete(e);
        return new AdminModelDeleteResponse(
                id, e.getName(), e.getType(), "DELETED", "Model removed from allowlist");
    }

    private AdminModelCheckResponse checkEmbedding(
            String resolvedId,
            AllowedModelType type,
            List<String> matches,
            Instant now,
            boolean pulled,
            String pullSummary) {
        try {
            OllamaEmbeddingProbeResult probe =
                    ollamaApiClient.probeEmbeddingDetailed(resolvedId, "ping", EMBEDDING_PROBE_TIMEOUT_MS);
            if (probe.ok()) {
                return new AdminModelCheckResponse(
                        resolvedId,
                        type,
                        true,
                        true,
                        pulled,
                        true,
                        matches,
                        now,
                        null,
                        null,
                        null,
                        pullSummary);
            }
            return new AdminModelCheckResponse(
                    resolvedId,
                    type,
                    true,
                    true,
                    pulled,
                    false,
                    matches,
                    now,
                    "MODEL_EMBEDDING_PROBE_FAILED",
                    probe.userMessage(),
                    probe.technicalDetail(),
                    pullSummary);
        } catch (Exception ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new AdminModelCheckResponse(
                    resolvedId,
                    type,
                    true,
                    true,
                    pulled,
                    false,
                    matches,
                    now,
                    "MODEL_EMBEDDING_PROBE_FAILED",
                    "Embedding probe failed",
                    detail,
                    pullSummary);
        }
    }

    private void applyCheckMetadata(
            AllowedModelEntity e,
            String displayName,
            AllowedModelType type,
            boolean enabled,
            List<String> tags,
            AdminModelCheckResponse checked) {
        e.setName(checked.modelId());
        e.setType(type);
        e.setInAllowlist(enabled);
        e.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : null);
        e.setTags(tags != null ? List.copyOf(tags) : List.of());
        e.setAvailable(checked.existsLocal() && (type != AllowedModelType.EMBEDDING || checked.embeddingProbeOk()));
        e.setLastCheckedAt(checked.checkedAt());
        if (checked.existsLocal()) {
            e.setLastPullStatus(checked.pulled() ? "PULLED" : "OK");
            if (type == AllowedModelType.EMBEDDING && !checked.embeddingProbeOk()) {
                e.setLastPullStatus("PROBE_FAILED");
                e.setLastPullError(checked.technicalDetail() != null ? checked.technicalDetail() : checked.errorMessage());
            } else {
                e.setLastPullError(null);
            }
            if (e.getInstalledAt() == null) {
                e.setInstalledAt(Instant.now());
            }
        } else {
            e.setLastPullStatus("MISSING");
            e.setLastPullError(checked.errorMessage());
        }
    }

    private AllowedModelEntity findOrThrow(UUID id) {
        return allowedModelRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allowlist entry not found"));
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

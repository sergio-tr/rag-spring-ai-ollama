package com.uniovi.rag.application.service.model;

import com.uniovi.rag.interfaces.rest.dto.AllowlistModelEntryDto;
import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.interfaces.rest.dto.SelectableModelDto;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Combines {@code allowed_model} rows with live Ollama {@code /api/tags} for the settings UI.
 */
@Service
public class ModelsCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ModelsCatalogService.class);

    private final AllowedModelRepository allowedModelRepository;
    private final OllamaApiClient ollamaApiClient;
    private final EmbeddingModelStoreCompatibility embeddingModelStoreCompatibility;

    public ModelsCatalogService(
            AllowedModelRepository allowedModelRepository,
            OllamaApiClient ollamaApiClient,
            EmbeddingModelStoreCompatibility embeddingModelStoreCompatibility) {
        this.allowedModelRepository = allowedModelRepository;
        this.ollamaApiClient = ollamaApiClient;
        this.embeddingModelStoreCompatibility = embeddingModelStoreCompatibility;
    }

    /**
     * When Ollama listing is interrupted, the interrupt flag is restored and an empty installed set is returned
     * so the UI can still load the allowlist without failing the request.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("java:S2142")
    public ModelsCatalogResponseDto buildCatalog() {
        Set<String> installed;
        boolean reachable;
        try {
            installed = ollamaApiClient.listModelNames();
            reachable = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ollama tags unavailable (interrupted): {}", e.getMessage());
            installed = Collections.emptySet();
            reachable = false;
        } catch (Exception e) {
            log.warn("Ollama tags unavailable: {}", e.getMessage());
            installed = Collections.emptySet();
            reachable = false;
        }

        List<AllowedModelEntity> rows = allowedModelRepository.findAll();
        List<AllowlistModelEntryDto> entries = new ArrayList<>(rows.size());
        for (AllowedModelEntity row : rows) {
            String name = row.getName();
            boolean isInstalled = OllamaInstalledModelMatcher.matchesInstalledName(name, installed);
            entries.add(new AllowlistModelEntryDto(
                    name, row.getType(), row.isInAllowlist(), isInstalled));
        }
        List<String> installedSorted = new ArrayList<>(installed);
        Collections.sort(installedSorted);
        return new ModelsCatalogResponseDto(reachable, installedSorted, entries);
    }

    @Transactional(readOnly = true)
    public List<SelectableModelDto> listSelectableByType(String rawType) {
        AllowedModelType type;
        try {
            type = AllowedModelType.valueOf(rawType != null ? rawType.trim().toUpperCase(Locale.ROOT) : "");
        } catch (Exception e) {
            return List.of();
        }
        Set<String> installed;
        try {
            installed = ollamaApiClient.listModelNames();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            installed = Set.of();
        } catch (Exception e) {
            installed = Set.of();
        }
        Instant now = Instant.now();
        List<AllowedModelEntity> rows = allowedModelRepository.findAll();
        List<SelectableModelDto> out = new ArrayList<>();
        for (AllowedModelEntity row : rows) {
            if (row.getType() != type) continue;
            if (!row.isInAllowlist()) continue;
            boolean available = OllamaInstalledModelMatcher.matchesInstalledName(row.getName(), installed);
            if (!available) continue;
            if (type == AllowedModelType.EMBEDDING
                    && !embeddingModelStoreCompatibility.isSelectableForLabEmbeddingBenchmark(row.getName())) {
                continue;
            }
            out.add(new SelectableModelDto(
                    row.getName(),
                    row.getDisplayName(),
                    row.getType(),
                    row.getTags() != null ? row.getTags() : List.of(),
                    true,
                    row.getLastCheckedAt() != null ? row.getLastCheckedAt() : now));
        }
        out.sort((a, b) -> a.modelId().compareTo(b.modelId()));
        return out;
    }
}

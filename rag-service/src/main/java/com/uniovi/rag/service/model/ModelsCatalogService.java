package com.uniovi.rag.service.model;

import com.uniovi.rag.interfaces.rest.dto.AllowlistModelEntryDto;
import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Combines {@code allowed_model} rows with live Ollama {@code /api/tags} for the settings UI.
 */
@Service
public class ModelsCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ModelsCatalogService.class);

    private final AllowedModelRepository allowedModelRepository;
    private final OllamaApiClient ollamaApiClient;

    public ModelsCatalogService(
            AllowedModelRepository allowedModelRepository, OllamaApiClient ollamaApiClient) {
        this.allowedModelRepository = allowedModelRepository;
        this.ollamaApiClient = ollamaApiClient;
    }

    @Transactional(readOnly = true)
    public ModelsCatalogResponseDto buildCatalog() {
        Set<String> installed;
        boolean reachable;
        try {
            installed = ollamaApiClient.listModelNames();
            reachable = true;
        } catch (Exception e) {
            log.warn("Ollama tags unavailable: {}", e.getMessage());
            installed = Collections.emptySet();
            reachable = false;
        }

        List<AllowedModelEntity> rows = allowedModelRepository.findAll();
        List<AllowlistModelEntryDto> entries = new ArrayList<>(rows.size());
        for (AllowedModelEntity row : rows) {
            String name = row.getName();
            boolean isInstalled = name != null && installed.contains(name);
            entries.add(new AllowlistModelEntryDto(
                    name, row.getType(), row.isInAllowlist(), isInstalled));
        }
        List<String> installedSorted = new ArrayList<>(installed);
        Collections.sort(installedSorted);
        return new ModelsCatalogResponseDto(reachable, installedSorted, entries);
    }
}

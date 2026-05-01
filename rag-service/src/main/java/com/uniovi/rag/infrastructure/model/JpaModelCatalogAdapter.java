package com.uniovi.rag.infrastructure.model;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads allowed LLM names from {@code allowed_model} (adapter for {@link ModelCatalogPort}).
 */
@Service
public class JpaModelCatalogAdapter implements ModelCatalogPort {

    private final AllowedModelRepository allowedModelRepository;

    public JpaModelCatalogAdapter(AllowedModelRepository allowedModelRepository) {
        this.allowedModelRepository = allowedModelRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> allowedLlmNamesInGovernance() {
        List<AllowedModelEntity> rows = allowedModelRepository.findAll();
        Set<String> out = new HashSet<>();
        for (AllowedModelEntity row : rows) {
            if (row.getType() == AllowedModelType.LLM && row.isInAllowlist() && row.getName() != null) {
                out.add(row.getName());
            }
        }
        return out;
    }
}

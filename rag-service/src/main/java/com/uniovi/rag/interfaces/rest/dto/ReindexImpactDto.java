package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.indexing.ReindexImpact;

import java.util.List;

public record ReindexImpactDto(String level, List<String> reasons) {

    public static ReindexImpactDto fromDomain(ReindexImpact i) {
        if (i == null) {
            return new ReindexImpactDto("NO_REINDEX", List.of());
        }
        return new ReindexImpactDto(i.level().name(), i.reasons());
    }
}

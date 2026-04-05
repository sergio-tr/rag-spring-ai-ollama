package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.indexing.ReindexPreview;

import java.util.List;

public record ReindexPreviewDto(boolean requiresReindex, List<String> reasons) {

    public static ReindexPreviewDto fromDomain(ReindexPreview p) {
        return new ReindexPreviewDto(p.requiresReindex(), p.reasons());
    }
}

package com.uniovi.rag.interfaces.rest.dto.evaluation;

import java.util.List;

/** Multipart upload response: updated corpus summary plus per-file results. */
public record EvaluationCorpusDocumentsUploadResponseDto(
        EvaluationCorpusSummaryDto corpus, List<EvaluationCorpusDocumentUploadItemDto> uploads) {}

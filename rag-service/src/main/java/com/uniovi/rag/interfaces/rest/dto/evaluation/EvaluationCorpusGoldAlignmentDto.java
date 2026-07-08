package com.uniovi.rag.interfaces.rest.dto.evaluation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Result of aligning an evaluation corpus with the packaged reference workbook gold ids. */
public record EvaluationCorpusGoldAlignmentDto(
        UUID corpusId,
        int chunkRegistryRows,
        int corpusDocumentRows,
        int embeddingQueryRows,
        boolean workbookAligned,
        List<String> alignmentViolations,
        List<Map<String, Object>> indexedChunks,
        String referenceBundleSha256) {}

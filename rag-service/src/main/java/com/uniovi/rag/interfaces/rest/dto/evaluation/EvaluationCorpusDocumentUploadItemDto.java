package com.uniovi.rag.interfaces.rest.dto.evaluation;

import java.util.UUID;

/** Per-file outcome for Lab evaluation corpus multipart upload. */
public record EvaluationCorpusDocumentUploadItemDto(
        UUID documentId, String fileName, String status, String error) {}

package com.uniovi.rag.interfaces.rest.dto.evaluation;

import java.util.List;

public record LabRagKnowledgeBaseDocumentsUploadResponseDto(
        LabRagKnowledgeBaseDto knowledgeBase, List<EvaluationCorpusDocumentUploadItemDto> uploads) {}

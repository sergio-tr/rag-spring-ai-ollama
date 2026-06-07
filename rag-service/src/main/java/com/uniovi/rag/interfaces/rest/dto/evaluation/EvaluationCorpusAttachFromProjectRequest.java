package com.uniovi.rag.interfaces.rest.dto.evaluation;

import java.util.List;
import java.util.UUID;

public record EvaluationCorpusAttachFromProjectRequest(UUID projectId, List<UUID> documentIds) {}

package com.uniovi.rag.interfaces.rest.dto;

import java.util.UUID;

public record LabJobAcceptedDto(UUID jobId, String status, String pollPath, String streamPath) {}

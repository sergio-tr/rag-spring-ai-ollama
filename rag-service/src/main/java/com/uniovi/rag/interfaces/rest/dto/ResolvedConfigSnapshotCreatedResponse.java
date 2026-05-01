package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ResolvedConfigSnapshotCreatedResponse(UUID id, String configHash, Instant createdAt) {}

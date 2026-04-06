package com.uniovi.rag.domain.knowledge;

import java.util.List;

/** Ordered chunks before embedding (§8 matrix when CHUNK is produced). */
public record ChunkArtifactPayload(int schemaVersion, int chunkCount, List<String> chunks) {}

package com.uniovi.rag.domain.knowledge;

import java.util.List;

/** Index binding: vectors and/or empty refs for STRUCTURED_SEARCH (§8). */
public record IndexArtifactPayload(
        int schemaVersion,
        int vectorChunkCount,
        String indexSignatureHash,
        List<String> vectorRefs) {}

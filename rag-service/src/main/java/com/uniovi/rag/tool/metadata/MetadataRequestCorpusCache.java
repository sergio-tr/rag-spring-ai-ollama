package com.uniovi.rag.tool.metadata;

import java.util.List;
import org.springframework.ai.document.Document;

/** Per-request cache of in-scope vector_store rows for metadata-tool corpus scans. */
public final class MetadataRequestCorpusCache {

    private static final ThreadLocal<List<Document>> CHUNKS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOAD_ATTEMPTED = new ThreadLocal<>();

    private MetadataRequestCorpusCache() {}

    public static List<Document> chunks() {
        return CHUNKS.get();
    }

    public static void setChunks(List<Document> chunks) {
        CHUNKS.set(chunks != null ? List.copyOf(chunks) : List.of());
        LOAD_ATTEMPTED.set(Boolean.TRUE);
    }

    public static boolean loadAttempted() {
        return Boolean.TRUE.equals(LOAD_ATTEMPTED.get());
    }

    public static void clear() {
        CHUNKS.remove();
        LOAD_ATTEMPTED.remove();
    }
}

package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.application.service.runtime.observability.RagToolTimingTelemetry;
import com.uniovi.rag.application.service.runtime.retrieval.MetadataCorpusChunkLoader;
import java.util.List;
import org.springframework.ai.document.Document;

/** Request-scoped metadata corpus gateway (single JDBC load per turn). */
public final class MetadataCorpusAccess {

    private static volatile MetadataCorpusChunkLoader loader;

    private MetadataCorpusAccess() {}

    public static void registerLoader(MetadataCorpusChunkLoader chunkLoader) {
        loader = chunkLoader;
    }

    /**
     * Returns cached in-scope corpus chunks, loading once per request when a loader is registered.
     */
    public static List<Document> getOrLoadCorpusChunks() {
        List<Document> cached = MetadataRequestCorpusCache.chunks();
        if (cached != null) {
            return cached;
        }
        if (MetadataRequestCorpusCache.loadAttempted() || loader == null) {
            return List.of();
        }
        long start = System.nanoTime();
        List<Document> loaded = loader.loadScopedCorpusChunks();
        MetadataRequestCorpusCache.setChunks(loaded);
        RagToolTimingTelemetry.logTool(
                "metadata-corpus",
                "corpus_jdbc_load",
                RagToolTimingTelemetry.elapsedMs(start),
                loaded.isEmpty() ? "EMPTY" : "OK",
                "chunks=" + loaded.size());
        return loaded;
    }
}

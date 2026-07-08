package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.retrieval.MetadataCorpusChunkLoader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MetadataCorpusAccessTest {

    @BeforeEach
    void setUp() {
        MetadataRequestCorpusCache.clear();
    }

    @AfterEach
    void tearDown() {
        MetadataRequestCorpusCache.clear();
        MetadataCorpusAccess.registerLoader(null);
    }

    @Test
    void getOrLoadCorpusChunks_loadsOncePerRequest() {
        MetadataCorpusChunkLoader loader = mock(MetadataCorpusChunkLoader.class);
        Document doc = new Document("acta body", Map.of("date_iso", "2026-02-25"));
        when(loader.loadScopedCorpusChunks()).thenReturn(List.of(doc));
        MetadataCorpusAccess.registerLoader(loader);

        List<Document> first = MetadataCorpusAccess.getOrLoadCorpusChunks();
        List<Document> second = MetadataCorpusAccess.getOrLoadCorpusChunks();

        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        verify(loader, times(1)).loadScopedCorpusChunks();
    }

    @Test
    void getOrLoadCorpusChunks_returnsEmptyWhenLoaderUnset() {
        assertThat(MetadataCorpusAccess.getOrLoadCorpusChunks()).isEmpty();
    }
}

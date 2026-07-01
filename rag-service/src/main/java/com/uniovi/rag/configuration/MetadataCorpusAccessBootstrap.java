package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.runtime.retrieval.MetadataCorpusChunkLoader;
import com.uniovi.rag.tool.metadata.MetadataCorpusAccess;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MetadataCorpusAccessBootstrap {

    private final MetadataCorpusChunkLoader chunkLoader;

    public MetadataCorpusAccessBootstrap(MetadataCorpusChunkLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
    }

    @PostConstruct
    void register() {
        MetadataCorpusAccess.registerLoader(chunkLoader);
    }
}

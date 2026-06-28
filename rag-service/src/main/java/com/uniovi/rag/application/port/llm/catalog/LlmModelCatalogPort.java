package com.uniovi.rag.application.port.llm.catalog;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogDefaults;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import java.util.List;
import java.util.Optional;

/** Read-only central catalog of LLM models (properties-backed in G.1–G.2). */
public interface LlmModelCatalogPort {

    Optional<LlmCatalogEntry> find(LlmProvider provider, String modelName, LlmModelCapability capability);

    List<LlmCatalogEntry> listConfigured(LlmCatalogQuery query);

    void assertUsable(
            LlmProvider provider,
            String modelName,
            LlmModelCapability capability,
            LlmModelUsageContext usageContext);

    LlmCatalogDefaults resolveSystemDefaults(LlmProvider provider);
}

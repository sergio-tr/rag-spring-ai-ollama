package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import java.util.List;
import org.json.JSONObject;
import org.springframework.ai.document.Document;

/**
 * Decorator that blocks vector retrieval when the active index profile is incompatible with the effective
 * embedding provider/model. Never routes to Ollama when the stack is OpenAI-compatible.
 */
public class ProviderAwareContextRetriever implements ContextRetriever {

    private final ContextRetriever delegate;
    private final EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService;

    public ProviderAwareContextRetriever(
            ContextRetriever delegate, EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService) {
        this.delegate = delegate;
        this.embeddingIndexCompatibilityService = embeddingIndexCompatibilityService;
    }

    @Override
    public List<Document> retrieve(String query) {
        embeddingIndexCompatibilityService.assertToolVectorRetrievalCompatible();
        return delegate.retrieve(query);
    }

    @Override
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        embeddingIndexCompatibilityService.assertToolVectorRetrievalCompatible();
        return delegate.retrieveWithMetadataFilters(query, nerEntities);
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        return delegate.createContext(documents, query, entities);
    }

    @Override
    public int getTopK() {
        return delegate.getTopK();
    }

    @Override
    public void setTopK(int topK) {
        delegate.setTopK(topK);
    }

    @Override
    public double getSimilarityThreshold() {
        return delegate.getSimilarityThreshold();
    }

    @Override
    public void setSimilarityThreshold(double similarityThreshold) {
        delegate.setSimilarityThreshold(similarityThreshold);
    }

    @Override
    public void restoreDefaultSettings() {
        delegate.restoreDefaultSettings();
    }
}

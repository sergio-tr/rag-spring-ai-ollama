package com.uniovi.rag.services.retriever;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.Loggable;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ContextRetriever with configurable caching capabilities.
 * Extends AbstractContextRetriever to maintain total compatibility.
 * 
 * Features:
 * - Configurable cache by flags
 * - Original methods preserved
 * - Instant activation/deactivation
 * - Integrated cache metrics
 */
@Component
public class CachedContextRetriever extends AbstractContextRetriever implements Loggable {

    private final RagFeatureConfiguration featureConfig;

    public CachedContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, 
                                 RagFeatureConfiguration featureConfig, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
        this.featureConfig = featureConfig;
    }

    /**
     * Main retrieval method with configurable cache.
     * If cache is disabled, uses the original method.
     */
    @Override
    public List<Document> retrieve(String query) {
        if (featureConfig.isCacheDocumentsEnabled()) {
            return retrieveWithCache(query);
        }
        return retrieveOriginal(query);
    }

    /**
     * Retrieval with cache enabled.
     * Cache key includes query, topK and similarityThreshold for maximum precision.
     */
    @Cacheable(value = "documentRetrieval", 
               key = "#query.hashCode() + '_' + #topK + '_' + #similarityThreshold",
               condition = "@ragFeatureConfiguration.isCacheDocumentsEnabled()")
    public List<Document> retrieveWithCache(String query) {
        log().debug("Cache MISS - Retrieving documents for query: {}", query);
        return retrieveOriginal(query);
    }

    /**
     * Original retrieval method (without cache).
     * Preserved for compatibility and comparison.
     */
    private List<Document> retrieveOriginal(String query) {
        log().debug("Retrieving documents for query: {} (topK: {}, threshold: {})", 
                   query, topK, similarityThreshold);
        
        return vectorStore.similaritySearch(
            org.springframework.ai.vectorstore.SearchRequest
                .query(query)
                .withTopK(topK)
                .withSimilarityThreshold(similarityThreshold)
        );
    }

    /**
     * Retrieval with metadata filters (future functionality).
     * Prepared for integration with NER entities.
     */
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        if (featureConfig.isCacheDocumentsEnabled()) {
            return retrieveWithMetadataFiltersCached(query, nerEntities);
        }
        return retrieveWithMetadataFiltersOriginal(query, nerEntities);
    }

    @Cacheable(value = "documentRetrievalWithMetadata", 
               key = "#query.hashCode() + '_' + #nerEntities.hashCode() + '_' + #topK",
               condition = "@ragFeatureConfiguration.isCacheDocumentsEnabled()")
    public List<Document> retrieveWithMetadataFiltersCached(String query, JSONObject nerEntities) {
        log().debug("Cache MISS - Retrieving documents with metadata filters for query: {}", query);
        return retrieveWithMetadataFiltersOriginal(query, nerEntities);
    }

    private List<Document> retrieveWithMetadataFiltersOriginal(String query, JSONObject nerEntities) {
        // For now, use normal retrieval
        // In the future, implement metadata filters based on NER
        log().debug("Retrieving documents with metadata filters for query: {} (NER: {})", 
                   query, nerEntities != null ? nerEntities.toString() : "null");
        
        return retrieveOriginal(query);
    }

    /**
     * Document content filtering with configurable cache.
     * If cache is disabled, uses the original method.
     */
    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {
        if (featureConfig.isCacheDocumentsEnabled()) {
            return filterDocumentContentWithCache(doc, query, entities);
        }
        return filterDocumentContentOriginal(doc, query, entities);
    }

    @Cacheable(value = "documentContentFiltering", 
               key = "#doc.id + '_' + #query.hashCode() + '_' + (#entities != null ? #entities.hashCode() : 'null')",
               condition = "@ragFeatureConfiguration.isCacheDocumentsEnabled()")
    public String filterDocumentContentWithCache(Document doc, String query, JSONObject entities) {
        log().debug("Cache MISS - Filtering document content for doc: {}", doc.getId());
        return filterDocumentContentOriginal(doc, query, entities);
    }

    private String filterDocumentContentOriginal(Document doc, String query, JSONObject entities) {
        // Basic implementation - return full content
        // In the future, implement intelligent filtering with LLM
        log().debug("Filtering document content for doc: {} (query: {})", doc.getId(), query);
        return doc.getContent();
    }

    /**
     * Context creation with configurable cache.
     * If cache is disabled, uses the original method.
     */
    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        if (featureConfig.isCacheDocumentsEnabled()) {
            return createContextWithCache(documents, query, entities);
        }
        return createContextOriginal(documents, query, entities);
    }

    @Cacheable(value = "contextCreation", 
               key = "#documents.hashCode() + '_' + #query.hashCode() + '_' + (#entities != null ? #entities.hashCode() : 'null')",
               condition = "@ragFeatureConfiguration.isCacheDocumentsEnabled()")
    public String createContextWithCache(List<Document> documents, String query, JSONObject entities) {
        log().debug("Cache MISS - Creating context for query: {} with {} documents", query, documents.size());
        return createContextOriginal(documents, query, entities);
    }

    private String createContextOriginal(List<Document> documents, String query, JSONObject entities) {
        if (documents.isEmpty()) {
            return "";
        }

        log().debug("Creating context for query: {} with {} documents", query, documents.size());
        
        return documents.stream()
                .map(doc -> filterDocumentContentOriginal(doc, query, entities))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Method to get cache statistics.
     * Useful for monitoring and debugging.
     */
    public String getCacheStats() {
        if (!featureConfig.isCacheDocumentsEnabled()) {
            return "Cache is disabled";
        }
        
        // In the future, integrate with Caffeine metrics
        return "Cache is enabled - Stats not yet implemented";
    }

    /**
     * Method to manually clear cache.
     * Useful for testing and debugging.
     */
    public void clearCache() {
        if (featureConfig.isCacheDocumentsEnabled()) {
            log().info("Cache clear requested - Implementation pending");
            // In the future, implement cache clearing
        }
    }
}

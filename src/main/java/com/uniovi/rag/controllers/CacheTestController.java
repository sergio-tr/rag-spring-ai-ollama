package com.uniovi.rag.controllers;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.services.retriever.CachedContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for testing the document cache system.
 * Allows enabling/disabling cache and measuring its impact.
 */
@RestController
@RequestMapping("/api/cache-test")
public class CacheTestController {

    @Autowired
    private ContextRetriever contextRetriever;

    @Autowired
    private RagFeatureConfiguration featureConfig;

    /**
     * Gets the current system configuration.
     */
    @GetMapping("/config")
    public Map<String, Boolean> getConfiguration() {
        return featureConfig.getConfiguration();
    }

    /**
     * Enables/disables document cache.
     */
    @PostMapping("/toggle-documents-cache")
    public Map<String, Object> toggleDocumentsCache(@RequestParam boolean enabled) {
        featureConfig.setCacheDocumentsEnabled(enabled);
        
        return Map.of(
            "message", "Document cache " + (enabled ? "enabled" : "disabled"),
            "cache_documents_enabled", enabled,
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Enables/disables general cache.
     */
    @PostMapping("/toggle-cache")
    public Map<String, Object> toggleCache(@RequestParam boolean enabled) {
        featureConfig.setCacheEnabled(enabled);
        
        return Map.of(
            "message", "General cache " + (enabled ? "enabled" : "disabled"),
            "cache_enabled", enabled,
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Document retrieval test with time measurement.
     */
    @GetMapping("/test-retrieval")
    public Map<String, Object> testRetrieval(@RequestParam String query) {
        long startTime = System.currentTimeMillis();
        
        List<Document> documents = contextRetriever.retrieve(query);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        return Map.of(
            "query", query,
            "documents_found", documents.size(),
            "duration_ms", duration,
            "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Multiple retrieval test to measure cache hit ratio.
     */
    @GetMapping("/test-cache-performance")
    public Map<String, Object> testCachePerformance(@RequestParam String query, @RequestParam int iterations) {
        long totalTime = 0;
        long firstCallTime = 0;
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            contextRetriever.retrieve(query);
            long endTime = System.currentTimeMillis();
            
            long duration = endTime - startTime;
            totalTime += duration;
            
            if (i == 0) {
                firstCallTime = duration;
            }
        }
        
        long averageTime = totalTime / iterations;
        long timeSaved = firstCallTime - averageTime;
        double improvementPercentage = featureConfig.isCacheDocumentsEnabled() ? 
            ((double) timeSaved / firstCallTime) * 100 : 0;
        
        return Map.of(
            "query", query,
            "iterations", iterations,
            "first_call_ms", firstCallTime,
            "average_time_ms", averageTime,
            "total_time_ms", totalTime,
            "time_saved_ms", timeSaved,
            "improvement_percentage", improvementPercentage,
            "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Gets cache statistics (if available).
     */
    @GetMapping("/cache-stats")
    public Map<String, Object> getCacheStats() {
        String stats = "Cache not available";
        
        if (contextRetriever instanceof CachedContextRetriever) {
            CachedContextRetriever cachedRetriever = (CachedContextRetriever) contextRetriever;
            stats = cachedRetriever.getCacheStats();
        }
        
        return Map.of(
            "cache_stats", stats,
            "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
            "retriever_type", contextRetriever.getClass().getSimpleName(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Manually clears the cache.
     */
    @PostMapping("/clear-cache")
    public Map<String, Object> clearCache() {
        if (contextRetriever instanceof CachedContextRetriever) {
            CachedContextRetriever cachedRetriever = (CachedContextRetriever) contextRetriever;
            cachedRetriever.clearCache();
            
            return Map.of(
                "message", "Cache cleared successfully",
                "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
                "timestamp", System.currentTimeMillis()
            );
        }
        
        return Map.of(
            "message", "Cache not available - using BasicContextRetriever",
            "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Comparison test between retriever with cache and without cache.
     */
    @GetMapping("/compare-retrievers")
    public Map<String, Object> compareRetrievers(@RequestParam String query) {
        // First call (without cache or cache miss)
        long startTime1 = System.currentTimeMillis();
        List<Document> documents1 = contextRetriever.retrieve(query);
        long endTime1 = System.currentTimeMillis();
        long duration1 = endTime1 - startTime1;
        
        // Second call (with cache hit if enabled)
        long startTime2 = System.currentTimeMillis();
        List<Document> documents2 = contextRetriever.retrieve(query);
        long endTime2 = System.currentTimeMillis();
        long duration2 = endTime2 - startTime2;
        
        long timeDifference = duration1 - duration2;
        double improvementPercentage = duration1 > 0 ? ((double) timeDifference / duration1) * 100 : 0;
        
        return Map.of(
            "query", query,
            "first_call_ms", duration1,
            "second_call_ms", duration2,
            "time_difference_ms", timeDifference,
            "improvement_percentage", improvementPercentage,
            "documents_consistent", documents1.size() == documents2.size(),
            "cache_enabled", featureConfig.isCacheDocumentsEnabled(),
            "timestamp", System.currentTimeMillis()
        );
    }
}

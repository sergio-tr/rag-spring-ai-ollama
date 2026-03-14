package com.uniovi.rag.tool;

import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.model.QueryType;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects relevant tools by embedding the query and tool descriptions, then returning top-K QueryTypes by similarity.
 */
public class ToolRagService {

    private final EmbeddingModel embeddingModel;
    private final List<QueryType> orderedTypes;
    private final List<float[]> toolEmbeddings;

    public ToolRagService(EmbeddingModel embeddingModel, int topK) {
        this.embeddingModel = embeddingModel;
        Map<QueryType, ToolDescriptor.Descriptor> all = ToolDescriptor.getAll();
        orderedTypes = new ArrayList<>(all.keySet());
        List<String> texts = orderedTypes.stream()
                .map(t -> ToolDescriptor.getName(t) + " " + ToolDescriptor.getDescription(t))
                .collect(Collectors.toList());
        this.toolEmbeddings = embeddingModel.embed(texts);
    }

    public List<QueryType> findTopQueryTypes(String query, int k) {
        if (query == null || query.trim().isEmpty()) {
            return orderedTypes.subList(0, Math.min(k, orderedTypes.size()));
        }
        float[] queryEmbedding = embeddingModel.embed(query.trim());
        int limit = Math.min(k, orderedTypes.size());
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < orderedTypes.size(); i++) indices.add(i);
        indices.sort(Comparator.comparingDouble(i -> -cosineSimilarity(queryEmbedding, toolEmbeddings.get(i))));
        return indices.stream().limit(limit).map(orderedTypes::get).toList();
    }

    public QueryType findBestQueryType(String query) {
        List<QueryType> top = findTopQueryTypes(query, 1);
        return top.isEmpty() ? null : top.get(0);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}

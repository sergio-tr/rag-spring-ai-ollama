package com.uniovi.rag.service.retriever;

import org.json.JSONObject;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ContextRetriever {

    List<Document> retrieve(String query);

    int getTopK();
    void setTopK(int topK);

    double getSimilarityThreshold();
    void setSimilarityThreshold(double similarityThreshold);
    void restoreDefaultSettings();

    List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities);

    String createContext(List<Document> documents, String query, JSONObject entities);
}

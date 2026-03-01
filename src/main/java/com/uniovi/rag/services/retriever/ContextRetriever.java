package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
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

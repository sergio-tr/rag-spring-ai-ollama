package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ContextRetriever {

    List<Document> retrieve(String query);

    void setTopK(int topK);

    void setSimilarityThreshold(double similarityThreshold);

    void restoreDefaultSettings();

    String createContext(List<Document> documents, String query, JSONObject entities);

}

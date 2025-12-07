package com.uniovi.rag.services.retriever;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractContextRetriever implements ContextRetriever {

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;
    protected int topK;
    protected double similarityThreshold;

    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public AbstractContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.defaultTopK = topK;
        this.defaultSimilarityThreshold = similarityThreshold;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<Document> retrieve(String query) {
        SearchRequest req = SearchRequest.
                query(query).
                withTopK(topK).
                withSimilarityThreshold(similarityThreshold);
        return vectorStore.similaritySearch(req);
    }
    
    /**
     * Retrieves documents with metadata filters when NER entities are available.
     * This optimizes retrieval by filtering at the database level before vector search.
     * 
     * @param query The search query
     * @param nerEntities NER entities extracted from the query (can be null)
     * @return List of documents matching the query and metadata filters
     */
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        if (nerEntities == null || nerEntities.isEmpty()) {
            // No NER entities, use standard retrieval
            return retrieve(query);
        }
        
        // Build metadata filters from NER entities
        // Note: Spring AI PgVectorStore may support withFilterExpression, but we'll filter post-retrieval
        // if the API doesn't support it directly
        SearchRequest req = SearchRequest.
                query(query).
                withTopK(topK * 2). // Retrieve more documents to account for post-filtering
                withSimilarityThreshold(similarityThreshold);
        
        List<Document> retrievedDocs = vectorStore.similaritySearch(req);
        
        // Post-filter by metadata if NER entities are present
        return filterDocumentsByMetadata(retrievedDocs, nerEntities);
    }
    
    /**
     * Filters documents by metadata matching NER entities.
     * This is a post-retrieval filter when database-level filtering isn't available.
     */
    private List<Document> filterDocumentsByMetadata(List<Document> documents, JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return documents;
        }
        
        return documents.stream()
                .filter(doc -> matchesDocumentMetadata(doc, ner))
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Checks if a document's metadata matches NER entities.
     */
    private boolean matchesDocumentMetadata(Document doc, JSONObject ner) {
        if (doc == null || ner == null || ner.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return true; // No metadata to filter on
        }
        
        // Check date matching
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = (String) metadata.get("date");
            if (docDate != null && !docDate.trim().isEmpty()) {
                JSONArray nerDates = ner.getJSONArray("date");
                boolean dateMatches = false;
                for (int i = 0; i < nerDates.length(); i++) {
                    String nerDate = nerDates.getString(i);
                    if (docDate.contains(nerDate) || nerDate.contains(docDate)) {
                        dateMatches = true;
                        break;
                    }
                }
                if (!dateMatches) {
                    return false;
                }
            }
        }
        
        // Check person matching
        if (ner.has("person") && !ner.getJSONArray("person").isEmpty()) {
            String docPresident = (String) metadata.get("president");
            String docSecretary = (String) metadata.get("secretary");
            JSONArray nerPersons = ner.getJSONArray("person");
            
            boolean personMatches = false;
            for (int i = 0; i < nerPersons.length(); i++) {
                String nerPerson = nerPersons.getString(i).toLowerCase();
                if ((docPresident != null && docPresident.toLowerCase().contains(nerPerson)) ||
                    (docSecretary != null && docSecretary.toLowerCase().contains(nerPerson))) {
                    personMatches = true;
                    break;
                }
            }
            if (!personMatches) {
                return false;
            }
        }
        
        return true; // Passed all filters
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        if (documents.isEmpty()) {
            return "";
        }

        return documents.stream()
                .filter(doc -> doc != null && doc.getContent() != null && !doc.getContent().trim().isEmpty())
                .map(doc -> filterDocumentContent(doc, query, entities))
                .filter(content -> content != null && !content.trim().isEmpty())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void setTopK(int topK) {
        if (topK > 0) {
            this.topK = topK;
        }
    }

    @Override
    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold > 0 && similarityThreshold <= 1) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    @Override
    public void restoreDefaultSettings() {
        this.topK = defaultTopK;
        this.similarityThreshold = defaultSimilarityThreshold;
    }

    public abstract String filterDocumentContent(Document doc, String query, JSONObject entities);

}

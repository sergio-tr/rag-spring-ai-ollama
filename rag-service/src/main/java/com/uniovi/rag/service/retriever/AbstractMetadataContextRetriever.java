package com.uniovi.rag.service.retriever;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public abstract class AbstractMetadataContextRetriever extends AbstractContextRetriever {

    public AbstractMetadataContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }
    
    /**
     * Retrieves documents with metadata filters when NER entities are available.
     * This optimizes retrieval by filtering at the database level before vector search.
     * 
     * @param query The search query
     * @param nerEntities NER entities extracted from the query (can be null)
     * @return List of documents matching the query and metadata filters
     */
    @Override
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        if (nerEntities == null || nerEntities.isEmpty()) {
            // No NER entities, use standard retrieval (which already groups chunks)
            return retrieve(query);
        }
        
        // Build metadata filters from NER entities
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK() * 2) // Retrieve more documents to account for post-filtering
                .similarityThreshold(effectiveSimilarityThreshold())
                .build();

        List<Document> retrievedDocs = vectorStore.similaritySearch(req);
        retrievedDocs = applyProjectAndDocumentFilter(retrievedDocs);
        
        // Group and combine chunks first (before filtering)
        List<Document> groupedDocs = groupAndCombineChunks(retrievedDocs);
        
        // Post-filter by metadata if NER entities are present
        List<Document> filtered = filterDocumentsByMetadata(groupedDocs, nerEntities);
        
        // If filtering removed all documents, try less aggressive filtering
        if (filtered.isEmpty() && !groupedDocs.isEmpty()) {
            log().info("Metadata filtering removed all documents, trying less aggressive filtering");
            filtered = filterDocumentsByMetadataLenient(groupedDocs, nerEntities);
            
            // If still empty, return unfiltered documents
            if (filtered.isEmpty()) {
                log().info("Lenient filtering also removed all documents, returning unfiltered documents (limit: {})", topK);
                return groupedDocs.stream().limit(topK).toList();
            }
        }
        
        return filtered;
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
                .toList();
    }
    
    /**
     * Less aggressive filtering that only filters by date if it's a strict requirement.
     * Used as fallback when strict filtering removes all documents.
     */
    private List<Document> filterDocumentsByMetadataLenient(List<Document> documents, JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return documents;
        }
        
        return documents.stream()
                .filter(doc -> matchesDocumentMetadataLenient(doc, ner))
                .toList();
    }
    
    /**
     * Lenient version of metadata matching that only filters by date if it's clearly wrong.
     * More permissive to avoid filtering out all documents.
     */
    protected boolean matchesDocumentMetadataLenient(Document doc, JSONObject ner) {
        if (doc == null || ner == null || ner.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return true; // No metadata to filter on
        }
        
        // Only filter by date if there's a clear mismatch (different year)
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = getMetadataDate(metadata);
            if (docDate != null && !docDate.trim().isEmpty()) {
                JSONArray nerDates = ner.getJSONArray("date");
                boolean hasYearMatch = false;
                
                // Extract years and check if any match
                String docYear = metadata.get("year") instanceof Number number
                        ? String.valueOf(number.intValue())
                        : extractYearFromDate(docDate);
                for (int i = 0; i < nerDates.length(); i++) {
                    String nerDate = nerDates.getString(i);
                    String nerYear = extractYearFromDate(nerDate);
                    
                    if (docYear != null && nerYear != null && docYear.equals(nerYear)) {
                        hasYearMatch = true;
                        // If years match, check if dates are similar
                        if (normalizedDateMatches(docDate, nerDate)) {
                            return true; // Exact or close match
                        }
                    }
                }
                
                // If no year match at all, filter out
                if (!hasYearMatch) {
                    log().info("Document filtered out by year mismatch: docDate={}, nerDates={}", docDate, nerDates);
                    return false;
                }
                
            }
        }
        
        // For other filters (person, place), use same logic as strict matching
        // Check person matching with normalization
        if (ner.has("person") && !ner.getJSONArray("person").isEmpty()) {
            String docPresident = (String) metadata.get("president");
            String docSecretary = (String) metadata.get("secretary");
            JSONArray nerPersons = ner.getJSONArray("person");
            
            boolean personMatches = false;
            for (int i = 0; i < nerPersons.length(); i++) {
                String nerPerson = nerPersons.getString(i);
                if ((docPresident != null && normalizedNameMatches(docPresident, nerPerson)) ||
                    (docSecretary != null && normalizedNameMatches(docSecretary, nerPerson))) {
                    personMatches = true;
                    break;
                }
            }
            if (!personMatches) {
                return false;
            }
        }
        
        // Check place more leniently (contains)
        if (ner.has("place") && !ner.getJSONArray("place").isEmpty()) {
            String docPlace = (String) metadata.get("place");
            if (docPlace != null && !docPlace.trim().isEmpty()) {
                JSONArray nerPlaces = ner.getJSONArray("place");
                boolean placeMatches = false;
                for (int i = 0; i < nerPlaces.length(); i++) {
                    String nerPlace = nerPlaces.getString(i);
                    if (docPlace.toLowerCase().contains(nerPlace.toLowerCase())) {
                        placeMatches = true;
                        break;
                    }
                }
                if (!placeMatches) {
                    return false;
                }
            }
        }
        
        return true; // Passed all filters
    }
    
    /**
     * Checks if a document's metadata matches NER entities.
     */
    protected boolean matchesDocumentMetadata(Document doc, JSONObject ner) {
        if (doc == null || ner == null || ner.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return true; // No metadata to filter on
        }
        
        // Check date matching with normalization
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = getMetadataDate(metadata);
            if (docDate != null && !docDate.trim().isEmpty()) {
                JSONArray nerDates = ner.getJSONArray("date");
                boolean dateMatches = false;
                for (int i = 0; i < nerDates.length(); i++) {
                    String nerDate = nerDates.getString(i);
                    if (normalizedDateMatches(docDate, nerDate)) {
                        dateMatches = true;
                        break;
                    }
                }
                if (!dateMatches) {
                    log().info("Document filtered out by date mismatch: docDate={}, nerDates={}", docDate, nerDates);
                    return false;
                }
            } else {
                // If document has no date but NER requires date, be more lenient
                // Don't filter out - let other filters decide
                log().info("Document has no date metadata, skipping date filter");
            }
        }
        
        // Check person matching with normalization
        if (ner.has("person") && !ner.getJSONArray("person").isEmpty()) {
            String docPresident = (String) metadata.get("president");
            String docSecretary = (String) metadata.get("secretary");
            JSONArray nerPersons = ner.getJSONArray("person");
            
            boolean personMatches = false;
            for (int i = 0; i < nerPersons.length(); i++) {
                String nerPerson = nerPersons.getString(i);
                if ((docPresident != null && normalizedNameMatches(docPresident, nerPerson)) ||
                    (docSecretary != null && normalizedNameMatches(docSecretary, nerPerson))) {
                    personMatches = true;
                    break;
                }
            }
            if (!personMatches) {
                return false;
            }
        }
        
        // Check place matching
        if (ner.has("place") && !ner.getJSONArray("place").isEmpty()) {
            String docPlace = (String) metadata.get("place");
            if (docPlace != null && !docPlace.trim().isEmpty()) {
                JSONArray nerPlaces = ner.getJSONArray("place");
                boolean placeMatches = false;
                for (int i = 0; i < nerPlaces.length(); i++) {
                    String nerPlace = nerPlaces.getString(i);
                    if (docPlace.toLowerCase().contains(nerPlace.toLowerCase())) {
                        placeMatches = true;
                        break;
                    }
                }
                if (!placeMatches) {
                    log().info("Document filtered out by place mismatch: docPlace={}, nerPlaces={}", docPlace, nerPlaces);
                    return false;
                }
            }
        }
        
        return true; // Passed all filters
    }

    /**
     * Gets the preferred date from metadata, prioritizing date_iso (ISO format) over date (Spanish format).
     * Always returns ISO format if date_iso exists, otherwise returns date field (may need parsing).
     */
    private String getMetadataDate(Map<String, Object> metadata) {
        if (metadata == null) return null;
        
        // PRIORITY 1: Use date_iso if available (already in ISO format)
        Object dateIso = metadata.get("date_iso");
        if (dateIso instanceof String s && !s.trim().isEmpty()) {
            // Validate it's actually ISO format
            try {
                LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                return s;
            } catch (DateTimeParseException e) {
                log().warn("date_iso field '{}' is not valid ISO format, will try date field", s);
            }
        }
        
        // PRIORITY 2: Use date field (may be in Spanish format, needs parsing)
        Object date = metadata.get("date");
        if (date instanceof String s && !s.trim().isEmpty()) {
            // Try to parse and normalize to ISO format
            LocalDate parsed = parseDateToLocalDate(s);
            if (parsed != null) {
                String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                log().debug("Normalized date field to ISO: {} -> {}", s, normalized);
                return normalized;
            }
            // If parsing fails, check if already in ISO format
            try {
                LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                return s;
            } catch (DateTimeParseException ignored) {
                // Not ISO format and can't parse, return as-is
                log().warn("Could not parse date '{}' from metadata, returning as-is", s);
                return s;
            }
        }
        
        return null;
    }

    /**
     * Checks if two dates match precisely using LocalDate parsing.
     */
    protected boolean datesMatchPrecisely(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        LocalDate parsed1 = parseDateToLocalDate(date1);
        LocalDate parsed2 = parseDateToLocalDate(date2);
        
        if (parsed1 != null && parsed2 != null) {
            return parsed1.equals(parsed2);
        }
        
        return false;
    }
    
    /**
     * Simple heuristic to check if two date strings are similar.
     * Used as fallback when precise parsing fails.
     */
    protected boolean datesAreSimilar(String date1, String date2) {
        if (date1 == null || date2 == null) return false;
        
        // Extract year from both dates
        String year1 = extractYear(date1);
        String year2 = extractYear(date2);
        
        if (year1 != null && year2 != null && year1.equals(year2)) {
            // Same year, check if month/day are similar
            String monthDay1 = extractMonthDay(date1);
            String monthDay2 = extractMonthDay(date2);
            return monthDay1 != null && monthDay2 != null && 
                   (monthDay1.contains(monthDay2) || monthDay2.contains(monthDay1));
        }
        
        return false;
    }
    
    private String extractYear(String date) {
        // Try to extract 4-digit year
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{4})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private String extractMonthDay(String date) {
        // Extract month name or number
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?i)(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|january|february|march|april|may|june|july|august|september|october|november|december)");
        java.util.regex.Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        // Try to extract day number
        pattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b");
        matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

}

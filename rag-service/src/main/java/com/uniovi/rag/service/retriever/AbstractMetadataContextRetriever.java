package com.uniovi.rag.service.retriever;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

public abstract class AbstractMetadataContextRetriever extends AbstractContextRetriever {

    private static final int MONTH_NAME_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.CANON_EQ;
    private static final Pattern MONTH_NAMES_ES =
            Pattern.compile(
                    "(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)",
                    MONTH_NAME_FLAGS);
    private static final Pattern MONTH_NAMES_EN =
            Pattern.compile(
                    "(january|february|march|april|may|june|july|august|september|october|november|december)",
                    MONTH_NAME_FLAGS);

    protected AbstractMetadataContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    protected AbstractMetadataContextRetriever(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            int topK,
            double similarityThreshold,
            boolean knowledgeChatOverlayEnabled) {
        super(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
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
            return true;
        }
        return lenientDateNerPasses(metadata, ner)
                && lenientPersonNerPasses(metadata, ner)
                && lenientPlaceNerPasses(metadata, ner);
    }

    private boolean lenientDateNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("date") || ner.getJSONArray("date").isEmpty()) {
            return true;
        }
        String docDate = getMetadataDate(metadata);
        if (docDate == null || docDate.trim().isEmpty()) {
            return true;
        }
        JSONArray nerDates = ner.getJSONArray("date");
        boolean hasYearMatch = false;
        String docYear =
                metadata.get("year") instanceof Number number
                        ? String.valueOf(number.intValue())
                        : extractYearFromDate(docDate);
        for (int i = 0; i < nerDates.length(); i++) {
            String nerDate = nerDates.getString(i);
            String nerYear = extractYearFromDate(nerDate);
            if (docYear != null && nerYear != null && docYear.equals(nerYear)) {
                hasYearMatch = true;
                if (normalizedDateMatches(docDate, nerDate)) {
                    return true;
                }
            }
        }
        if (!hasYearMatch) {
            log().info("Document filtered out by year mismatch: docDate={}, nerDates={}", docDate, nerDates);
            return false;
        }
        return true;
    }

    private boolean lenientPersonNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("person") || ner.getJSONArray("person").isEmpty()) {
            return true;
        }
        String docPresident = (String) metadata.get("president");
        String docSecretary = (String) metadata.get("secretary");
        JSONArray nerPersons = ner.getJSONArray("person");
        for (int i = 0; i < nerPersons.length(); i++) {
            String nerPerson = nerPersons.getString(i);
            if ((docPresident != null && normalizedNameMatches(docPresident, nerPerson))
                    || (docSecretary != null && normalizedNameMatches(docSecretary, nerPerson))) {
                return true;
            }
        }
        return false;
    }

    private boolean lenientPlaceNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("place") || ner.getJSONArray("place").isEmpty()) {
            return true;
        }
        String docPlace = (String) metadata.get("place");
        if (docPlace == null || docPlace.trim().isEmpty()) {
            return true;
        }
        JSONArray nerPlaces = ner.getJSONArray("place");
        for (int i = 0; i < nerPlaces.length(); i++) {
            String nerPlace = nerPlaces.getString(i);
            if (docPlace.toLowerCase().contains(nerPlace.toLowerCase())) {
                return true;
            }
        }
        return false;
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
            return true;
        }
        return strictDateNerPasses(metadata, ner)
                && strictPersonNerPasses(metadata, ner)
                && strictPlaceNerPasses(metadata, ner);
    }

    private boolean strictDateNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("date") || ner.getJSONArray("date").isEmpty()) {
            return true;
        }
        String docDate = getMetadataDate(metadata);
        if (docDate == null || docDate.trim().isEmpty()) {
            log().info("Document has no date metadata, skipping date filter");
            return true;
        }
        JSONArray nerDates = ner.getJSONArray("date");
        for (int i = 0; i < nerDates.length(); i++) {
            if (normalizedDateMatches(docDate, nerDates.getString(i))) {
                return true;
            }
        }
        log().info("Document filtered out by date mismatch: docDate={}, nerDates={}", docDate, nerDates);
        return false;
    }

    private boolean strictPersonNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("person") || ner.getJSONArray("person").isEmpty()) {
            return true;
        }
        String docPresident = (String) metadata.get("president");
        String docSecretary = (String) metadata.get("secretary");
        JSONArray nerPersons = ner.getJSONArray("person");
        for (int i = 0; i < nerPersons.length(); i++) {
            String nerPerson = nerPersons.getString(i);
            if ((docPresident != null && normalizedNameMatches(docPresident, nerPerson))
                    || (docSecretary != null && normalizedNameMatches(docSecretary, nerPerson))) {
                return true;
            }
        }
        return false;
    }

    private boolean strictPlaceNerPasses(Map<String, Object> metadata, JSONObject ner) {
        if (!ner.has("place") || ner.getJSONArray("place").isEmpty()) {
            return true;
        }
        String docPlace = (String) metadata.get("place");
        if (docPlace == null || docPlace.trim().isEmpty()) {
            return true;
        }
        JSONArray nerPlaces = ner.getJSONArray("place");
        for (int i = 0; i < nerPlaces.length(); i++) {
            String nerPlace = nerPlaces.getString(i);
            if (docPlace.toLowerCase().contains(nerPlace.toLowerCase())) {
                return true;
            }
        }
        log().info("Document filtered out by place mismatch: docPlace={}, nerPlaces={}", docPlace, nerPlaces);
        return false;
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
        Pattern pattern = Pattern.compile("\\b(\\d{4})\\b");
        Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private String extractMonthDay(String date) {
        Matcher m = MONTH_NAMES_ES.matcher(date);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        m = MONTH_NAMES_EN.matcher(date);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        Matcher day = Pattern.compile("\\b(\\d{1,2})\\b").matcher(date);
        if (day.find()) {
            return day.group(1);
        }
        return null;
    }

}

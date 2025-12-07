package com.uniovi.rag.services.retriever;

import com.uniovi.rag.model.Loggable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractContextRetriever implements ContextRetriever, Loggable {

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
    /**
     * Retrieves documents with metadata filters when NER entities are available.
     * This optimizes retrieval by filtering at the database level before vector search.
     * 
     * MEJORA: Agregado fallback si el filtrado elimina todos los documentos.
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
        List<Document> filtered = filterDocumentsByMetadata(retrievedDocs, nerEntities);
        
        // If filtering removed all documents, try less aggressive filtering
        if (filtered.isEmpty() && !retrievedDocs.isEmpty()) {
            log().debug("Metadata filtering removed all documents, trying less aggressive filtering");
            filtered = filterDocumentsByMetadataLenient(retrievedDocs, nerEntities);
            
            // If still empty, return unfiltered documents
            if (filtered.isEmpty()) {
                log().debug("Lenient filtering also removed all documents, returning unfiltered documents (limit: {})", topK);
                return retrievedDocs.stream().limit(topK).collect(Collectors.toList());
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
                .collect(java.util.stream.Collectors.toList());
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
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Lenient version of metadata matching that only filters by date if it's clearly wrong.
     * More permissive to avoid filtering out all documents.
     */
    private boolean matchesDocumentMetadataLenient(Document doc, JSONObject ner) {
        if (doc == null || ner == null || ner.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return true; // No metadata to filter on
        }
        
        // Only filter by date if there's a clear mismatch (different year)
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = (String) metadata.get("date");
            if (docDate != null && !docDate.trim().isEmpty()) {
                JSONArray nerDates = ner.getJSONArray("date");
                boolean hasYearMatch = false;
                
                // Extract years and check if any match
                String docYear = extractYearFromDate(docDate);
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
                    log().debug("Document filtered out by year mismatch: docDate={}, nerDates={}", docDate, nerDates);
                    return false;
                }
                
                // If year matches but date doesn't, be lenient and keep it
                // This helps with cases like "25 feb" vs "24 feb" in same year
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
        
        return true; // Passed all filters
    }
    
    /**
     * Extracts year from a date string.
     */
    private String extractYearFromDate(String date) {
        if (date == null) {
            return null;
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{4})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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
        
        // Check date matching with normalization
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = (String) metadata.get("date");
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
                    log().debug("Document filtered out by date mismatch: docDate={}, nerDates={}", docDate, nerDates);
                    return false;
                }
            } else {
                // If document has no date but NER requires date, be more lenient
                // Don't filter out - let other filters decide
                log().debug("Document has no date metadata, skipping date filter");
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
        
        return true; // Passed all filters
    }
    
    /**
     * Normalizes and matches dates for better comparison using LocalDate parsing.
     * Handles different date formats (e.g., "25 de agosto de 2026" vs "25/08/2026").
     */
    private boolean normalizedDateMatches(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        // Try to parse both dates to LocalDate for precise comparison
        LocalDate parsed1 = parseDateToLocalDate(date1);
        LocalDate parsed2 = parseDateToLocalDate(date2);
        
        if (parsed1 != null && parsed2 != null) {
            // Both dates parsed successfully, compare directly
            return parsed1.equals(parsed2);
        }
        
        // Fallback to string-based matching if parsing fails
        String normalized1 = normalizeDate(date1);
        String normalized2 = normalizeDate(date2);
        
        // More strict matching: require significant overlap (not just substring)
        if (normalized1.equals(normalized2)) {
            return true;
        }
        
        // Extract key components (day, month, year) for comparison
        String[] components1 = extractDateComponents(date1);
        String[] components2 = extractDateComponents(date2);
        
        if (components1 != null && components2 != null) {
            // Compare year, month, and day
            return components1[0].equals(components2[0]) && // year
                   components1[1].equals(components2[1]) && // month
                   components1[2].equals(components2[2]);   // day
        }
        
        // Last resort: flexible matching (but less reliable)
        return normalized1.contains(normalized2) || normalized2.contains(normalized1);
    }
    
    /**
     * Parses a date string to LocalDate using multiple formatters.
     */
    private LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
        );
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        
        return null;
    }
    
    /**
     * Extracts date components (year, month, day) from a date string.
     * Returns array [year, month, day] or null if extraction fails.
     */
    private String[] extractDateComponents(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        
        // Try parsing first
        LocalDate parsed = parseDateToLocalDate(dateStr);
        if (parsed != null) {
            return new String[]{
                String.valueOf(parsed.getYear()),
                String.valueOf(parsed.getMonthValue()),
                String.valueOf(parsed.getDayOfMonth())
            };
        }
        
        // Fallback: extract using regex
        String lower = dateStr.toLowerCase();
        
        // Extract year (4 digits)
        java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(\\d{4})\\b");
        java.util.regex.Matcher yearMatcher = yearPattern.matcher(dateStr);
        String year = yearMatcher.find() ? yearMatcher.group(1) : null;
        
        // Extract month (name or number)
        String[] monthNames = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                              "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        String month = null;
        for (int i = 0; i < monthNames.length; i++) {
            if (lower.contains(monthNames[i])) {
                month = String.valueOf(i + 1);
                break;
            }
        }
        
        // Extract day (1-2 digits)
        java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b");
        java.util.regex.Matcher dayMatcher = dayPattern.matcher(dateStr);
        String day = null;
        while (dayMatcher.find()) {
            String candidate = dayMatcher.group(1);
            int dayNum = Integer.parseInt(candidate);
            if (dayNum >= 1 && dayNum <= 31) {
                day = candidate;
                break;
            }
        }
        
        if (year != null && month != null && day != null) {
            return new String[]{year, month, day};
        }
        
        return null;
    }
    
    /**
     * Normalizes a date string for comparison.
     * Removes extra spaces, converts to lowercase, and handles common date formats.
     */
    private String normalizeDate(String date) {
        if (date == null) {
            return "";
        }
        
        return date.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")  // Multiple spaces to one
                .replaceAll("de\\s+", " ")  // "de" to space (for Spanish dates)
                .replaceAll("/", "-")  // Slash to dash
                .replaceAll("\\.", "-");  // Dot to dash
    }
    
    /**
     * Normalizes and matches names for better comparison.
     * Handles variations in name formats (e.g., "Juan Pérez" vs "Juan Pérez Gutiérrez").
     */
    private boolean normalizedNameMatches(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        
        // Normalize names: lowercase, no accents, no extra spaces
        String normalized1 = normalizeName(name1);
        String normalized2 = normalizeName(name2);
        
        // Smarter partial matching (contains or is contained)
        return normalized1.contains(normalized2) || 
               normalized2.contains(normalized1) ||
               normalized1.equals(normalized2);
    }
    
    /**
     * Normalizes a name string for comparison.
     * Removes accents, converts to lowercase, and removes extra spaces.
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        
        return name.toLowerCase()
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u")
                .replaceAll("ñ", "n")
                .trim()
                .replaceAll("\\s+", " ");  // Multiple spaces to one
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

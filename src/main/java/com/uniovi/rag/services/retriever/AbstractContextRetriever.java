package com.uniovi.rag.services.retriever;

import com.uniovi.rag.model.Loggable;
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
import java.util.stream.Collectors;

public abstract class AbstractContextRetriever implements ContextRetriever, Loggable {

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;
    protected int topK;
    protected double similarityThreshold;

    protected final int defaultTopK;
    protected final double defaultSimilarityThreshold;
    protected static final int DEFAULT_MAX_PROMPT_CHARS = 6000;

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
    
    /**
     * Gets the current topK value.
     */
    public int getTopK() {
        return topK;
    }
    
    /**
     * Gets the current similarity threshold value.
     */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public abstract String filterDocumentContent(Document doc, String query, JSONObject entities);
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        throw new UnsupportedOperationException("The retriver does not support metadata filters");
    }
    
    /**
     * Trunca el contenido antes de enviarlo a un prompt LLM para evitar desbordar
     * el contexto. Conserva cabecera y pie para mantener señal relevante.
     */
    protected String truncateForPrompt(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }

        int head = (int) (maxChars * 0.65); // mantener más cabecera
        int tail = maxChars - head;
        String truncated = trimmed.substring(0, head) + "\n...\n" + trimmed.substring(trimmed.length() - tail);
        log().debug("Prompt content truncated from {} to {} characters", trimmed.length(), truncated.length());
        return truncated;
    }
    
    /**
     * Extracts year from a date string.
     */
    protected String extractYearFromDate(String date) {
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
     * Normalizes and matches dates for better comparison using LocalDate parsing.
     * Handles different date formats (e.g., "25 de agosto de 2026" vs "25/08/2026").
     */
    protected boolean normalizedDateMatches(String date1, String date2) {
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
    protected LocalDate parseDateToLocalDate(String dateStr) {
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
    protected String[] extractDateComponents(String dateStr) {
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
    protected String normalizeDate(String date) {
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
    protected boolean normalizedNameMatches(String name1, String name2) {
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
    protected String normalizeName(String name) {
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

}

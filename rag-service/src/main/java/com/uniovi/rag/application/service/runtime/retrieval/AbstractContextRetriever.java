package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
import com.uniovi.rag.util.DateParsingSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

public abstract class AbstractContextRetriever implements ContextRetriever, Loggable {

    private static final int NAME_REPLACE_FLAGS = Pattern.CANON_EQ | Pattern.UNICODE_CASE;
    private static final Pattern ACCENT_A_CLASS = Pattern.compile("[áàäâ]", NAME_REPLACE_FLAGS);
    private static final Pattern ACCENT_E_CLASS = Pattern.compile("[éèëê]", NAME_REPLACE_FLAGS);
    private static final Pattern ACCENT_I_CLASS = Pattern.compile("[íìïî]", NAME_REPLACE_FLAGS);
    private static final Pattern ACCENT_O_CLASS = Pattern.compile("[óòöô]", NAME_REPLACE_FLAGS);
    private static final Pattern ACCENT_U_CLASS = Pattern.compile("[úùüû]", NAME_REPLACE_FLAGS);
    private static final Pattern ACCENT_N_CLASS = Pattern.compile("ñ", NAME_REPLACE_FLAGS);
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;
    protected int topK;
    protected double similarityThreshold;

    protected final int defaultTopK;
    protected final double defaultSimilarityThreshold;
    /** When true, {@code CHAT_LOCAL} chunks match by {@code conversationId} (chat overlay). */
    protected final boolean knowledgeChatOverlayEnabled;
    protected static final int DEFAULT_MAX_PROMPT_CHARS = 6000;
    /** Metadata key for ISO date (vector store / chunk metadata). */
    private static final String META_DATE_ISO = "date_iso";

    protected AbstractContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        this(vectorStore, chatClient, topK, similarityThreshold, false);
    }

    protected AbstractContextRetriever(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            int topK,
            double similarityThreshold,
            boolean knowledgeChatOverlayEnabled) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.defaultTopK = topK;
        this.defaultSimilarityThreshold = similarityThreshold;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.knowledgeChatOverlayEnabled = knowledgeChatOverlayEnabled;
    }

    @Override
    public List<Document> retrieve(String query) {
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK())
                .similarityThreshold(effectiveSimilarityThreshold())
                .build();
        List<Document> docs = vectorStore.similaritySearch(req);
        docs = applyProjectAndDocumentFilter(docs);
        // Group and combine chunks by document_id to ensure complete content
        return groupAndCombineChunks(docs);
    }

    protected int effectiveTopK() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null && ctx.resolvedConfig().topK() > 0) {
            return ctx.resolvedConfig().topK();
        }
        return topK;
    }

    protected double effectiveSimilarityThreshold() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null && ctx.resolvedConfig().similarityThreshold() > 0) {
            return ctx.resolvedConfig().similarityThreshold();
        }
        return similarityThreshold;
    }

    /**
     * When {@link RagExecutionContext} scopes by project or document ids, drop chunks that do not match.
     * Legacy chunks without {@code projectId} metadata remain visible (backward compatible).
     */
    protected List<Document> applyProjectAndDocumentFilter(List<Document> docs) {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || !ctx.restrictsByProject()) {
            return docs;
        }
        String pid = ctx.projectId();
        List<Document> byProject = docs.stream()
                .filter(d -> passesProjectMetadata(d, pid, ctx))
                .toList();
        if (ctx.documentFilterIsAll()) {
            return byProject;
        }
        Set<String> allowed = new HashSet<>(ctx.documentFilter());
        return byProject.stream()
                .filter(d -> passesDocumentAllowlist(d, allowed))
                .toList();
    }

    private boolean passesProjectMetadata(Document d, String projectId, RagExecutionContext ctx) {
        Map<String, Object> meta = d.getMetadata();
        Object cs = meta.get("corpusScope");
            if ("CHAT_LOCAL".equalsIgnoreCase(String.valueOf(cs))) {
                if (!knowledgeChatOverlayEnabled || ctx.conversationId() == null) {
                    return false;
                }
            Object conv = meta.get("conversationId");
            return ctx.conversationId().equals(String.valueOf(conv));
        }
        Object p = meta.get("projectId");
        if (p == null) {
            // Project-scoped chat must not be contaminated by legacy chunks with no project metadata.
            return false;
        }
        return projectId.equals(String.valueOf(p));
    }

    private static boolean passesDocumentAllowlist(Document d, Set<String> allowed) {
        Object id = d.getMetadata().get("document_id");
        if (id == null) {
            id = d.getMetadata().get("documentId");
        }
        if (id == null) {
            id = d.getMetadata().get("projectDocumentId");
        }
        if (id == null) {
            return false;
        }
        return allowed.contains(String.valueOf(id));
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        if (documents.isEmpty()) {
            return "";
        }

        return documents.stream()
                .filter(Objects::nonNull)
                .filter(doc -> {
                    String text = doc.getText();
                    return text != null && !text.trim().isEmpty();
                })
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

    /**
     * Builds content with optional metadata prefix (Acta: date. Presidente: X. Temas: Y. Contenido: )
     * when the document has date_iso/date, president or topics in metadata. Otherwise returns content unchanged.
     */
    protected String buildContentWithOptionalMetadataPrefix(Document doc, String content) {
        if (doc == null) {
            return content != null ? content : "";
        }
        Map<String, Object> meta = doc.getMetadata();
        if (meta.isEmpty()) {
            return content != null ? content : "";
        }
        String date = null;
        if (meta.containsKey(META_DATE_ISO)) {
            date = String.valueOf(meta.get(META_DATE_ISO));
        } else if (meta.containsKey("date")) {
            date = String.valueOf(meta.get("date"));
        }
        String president = meta.containsKey("president") ? String.valueOf(meta.get("president")) : null;
        Object topicsObj = meta.get("topics");
        String topicsStr = null;
        if (topicsObj instanceof List<?> list) {
            topicsStr = list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        } else if (topicsObj != null) {
            topicsStr = topicsObj.toString();
        }
        if (date == null && president == null && (topicsStr == null || topicsStr.isBlank())) {
            return content != null ? content : "";
        }
        StringBuilder prefix = new StringBuilder("Acta: ");
        if (date != null) prefix.append(date).append(". ");
        if (president != null) prefix.append("Presidente: ").append(president).append(". ");
        if (topicsStr != null && !topicsStr.isBlank()) prefix.append("Temas: ").append(topicsStr).append(". ");
        prefix.append("Contenido: ");
        return prefix + (content != null ? content : "");
    }

    public abstract String filterDocumentContent(Document doc, String query, JSONObject entities);
    /**
     * Retrieves documents and then filters by NER metadata (e.g. date).
     * When ner=true, orchestrated retrieval uses this so date filters from NER are respected.
     */
    @Override
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        List<Document> docs = retrieve(query);
        if (nerEntities == null || !nerEntities.has("date")) {
            return docs;
        }
        try {
            JSONArray arr = nerEntities.getJSONArray("date");
            List<LocalDate> requestedDates = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                LocalDate d = parseDateToLocalDate(arr.optString(i, "").trim());
                if (d != null) requestedDates.add(d);
            }
            if (requestedDates.isEmpty()) return docs;
            return docs.stream()
                    .filter(doc -> {
                        String docDate = getDocumentDateFromMetadata(doc);
                        if (docDate == null) return true;
                        LocalDate docLocal = parseDateToLocalDate(docDate);
                        return docLocal != null && requestedDates.contains(docLocal);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log().warn("Error applying NER metadata filters, returning unfiltered docs: {}", e.getMessage());
            return docs;
        }
    }

    private String getDocumentDateFromMetadata(Document doc) {
        if (doc == null) return null;
        Map<String, Object> m = doc.getMetadata();
        Object dateIso = m.get(META_DATE_ISO);
        if (dateIso != null && !dateIso.toString().trim().isEmpty()) return dateIso.toString().trim();
        Object date = m.get("date");
        return date != null ? date.toString().trim() : null;
    }
    
    /**
     * Truncates content before sending it to an LLM prompt to avoid overflowing
     * context. Keeps head and tail to preserve relevant signal.
     */
    protected String truncateForPrompt(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }

        int head = (int) (maxChars * 0.65); // keep more from the head
        int tail = maxChars - head;
        String truncated = trimmed.substring(0, head) + "\n...\n" + trimmed.substring(trimmed.length() - tail);
        log().info("Prompt content truncated from {} to {} characters", trimmed.length(), truncated.length());
        return truncated;
    }
    
    /**
     * Extracts year from a date string.
     */
    protected String extractYearFromDate(String date) {
        if (date == null) {
            return null;
        }
        
        Pattern pattern = Pattern.compile("\\b(\\d{4})\\b");
        Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Normalizes and matches dates for better comparison using LocalDate parsing.
     * Handles different date formats (e.g., "25 de agosto de 2026" vs "25/08/2026" vs "2026-08-25").
     * Prioritizes ISO format parsing for better accuracy.
     */
    protected boolean normalizedDateMatches(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        // Try ISO format first (most reliable)
        LocalDate parsed1 = null;
        LocalDate parsed2 = null;
        
        try {
            parsed1 = LocalDate.parse(date1.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO format, try flexible parsing
            parsed1 = parseDateToLocalDate(date1);
        }
        
        try {
            parsed2 = LocalDate.parse(date2.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO format, try flexible parsing
            parsed2 = parseDateToLocalDate(date2);
        }
        
        if (parsed1 != null && parsed2 != null) {
            // Both dates parsed successfully, compare directly
            boolean matches = parsed1.equals(parsed2);
            if (!matches && log().isDebugEnabled()) {
                log().debug(
                        "Date comparison: {} ({}) vs {} ({}) - no match",
                        date1,
                        parsed1.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        date2,
                        parsed2.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            return matches;
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
        
        if (components1.length >= 3 && components2.length >= 3) {
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
     * Enhanced to match parseDateFlexible for consistency across the system.
     * Always tries ISO format first for better performance.
     */
    protected LocalDate parseDateToLocalDate(String dateStr) {
        return DateParsingSupport.parseDateToLocalDate(dateStr);
    }
    
    /**
     * Extracts date components (year, month, day) from a date string.
     * Returns a three-element array [year, month, day], or an empty array if extraction fails.
     */
    protected String[] extractDateComponents(String dateStr) {
        if (dateStr == null) {
            return new String[0];
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
        Pattern yearPattern = Pattern.compile("\\b(\\d{4})\\b");
        Matcher yearMatcher = yearPattern.matcher(dateStr);
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
        Pattern dayPattern = Pattern.compile("\\b(\\d{1,2})\\b");
        Matcher dayMatcher = dayPattern.matcher(dateStr);
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
        
        return new String[0];
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
                .replace("/", "-")  // Slash to dash
                .replace(".", "-");  // Dot to dash
    }
    
    /**
     * Normalizes and matches names for better comparison.
     * Handles variations in name formats (e.g. short vs full name with middle/surname).
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
        
        String n = name.toLowerCase();
        n = ACCENT_A_CLASS.matcher(n).replaceAll("a");
        n = ACCENT_E_CLASS.matcher(n).replaceAll("e");
        n = ACCENT_I_CLASS.matcher(n).replaceAll("i");
        n = ACCENT_O_CLASS.matcher(n).replaceAll("o");
        n = ACCENT_U_CLASS.matcher(n).replaceAll("u");
        n = ACCENT_N_CLASS.matcher(n).replaceAll("n");
        return MULTI_SPACE.matcher(n.trim()).replaceAll(" ");
    }
    
    /**
     * Groups and combines chunks by document_id, merging content from all chunks.
     * This ensures that when a document is split into multiple chunks, all content is preserved.
     * The resulting Document contains the combined content and metadata from the chunk with most complete metadata.
     * 
     * This is the central place where chunk grouping happens - all retrievers use this method.
     */
    protected List<Document> groupAndCombineChunks(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return docs;
        }
        
        // Group documents by document_id
        Map<String, List<Document>> documentsById = docs.stream()
                .collect(Collectors.groupingBy(this::getDocumentId));
        
        // Combine chunks for each document
        List<Document> combinedDocuments = new ArrayList<>();
        for (Map.Entry<String, List<Document>> entry : documentsById.entrySet()) {
            String documentId = entry.getKey();
            List<Document> chunks = entry.getValue();
            
            if (chunks.size() == 1) {
                // Single chunk, use as-is
                combinedDocuments.add(chunks.get(0));
            } else {
                // Multiple chunks, combine content
                Document combined = combineChunks(chunks);
                if (combined != null) {
                    combinedDocuments.add(combined);
                    String combinedText = combined.getText();
                    log().info("Combined {} chunks for document_id: {} (total content length: {})",
                              chunks.size(), documentId,
                              combinedText != null ? combinedText.length() : 0);
                }
            }
        }
        
        log().info("Grouped and combined {} documents from {} chunks", 
                  combinedDocuments.size(), docs.size());
        return combinedDocuments;
    }
    
    /**
     * Combines multiple chunks of the same document into a single Document.
     * Merges content in order (by chunk_index) and uses metadata from the chunk with most complete metadata.
     */
    protected Document combineChunks(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        
        if (chunks.size() == 1) {
            return chunks.get(0);
        }
        
        // Sort chunks by chunk_index if available
        List<Document> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((d1, d2) -> {
            Integer idx1 = getChunkIndex(d1);
            Integer idx2 = getChunkIndex(d2);
            if (idx1 == null && idx2 == null) return 0;
            if (idx1 == null) return 1;
            if (idx2 == null) return -1;
            return idx1.compareTo(idx2);
        });
        
        // Combine content from all chunks
        StringBuilder combinedContent = new StringBuilder();
        for (Document chunk : sortedChunks) {
            String content = chunk.getText();
            if (content != null && !content.trim().isEmpty()) {
                if (!combinedContent.isEmpty()) {
                    combinedContent.append("\n\n");
                }
                combinedContent.append(content.trim());
            }
        }
        // Defensive: do not allow combined per-document content to grow without bound, otherwise
        // legacy prompt assembly can exceed LLM context window and fail with 400.
        String combinedText =
                RuntimePromptBudgeter.truncate(
                        "combined_document",
                        combinedContent.toString(),
                        12_000,
                        "default_combined_document_max_chars")
                        .textUsed();
        
        // Select chunk with most complete metadata
        Document bestMetadataChunk = sortedChunks.stream()
                .max((d1, d2) -> Integer.compare(
                    countMetadataFields(d1), 
                    countMetadataFields(d2)
                ))
                .orElse(sortedChunks.get(0));
        
        // Create new Document with combined content and best metadata
        Map<String, Object> combinedMetadata = new HashMap<>(bestMetadataChunk.getMetadata());
        // Remove chunk-specific metadata
        combinedMetadata.remove("chunk_index");
        combinedMetadata.remove("total_chunks");
        
        return new Document(combinedText, combinedMetadata);
    }
    
    /**
     * Gets the chunk index from document metadata.
     */
    private Integer getChunkIndex(Document doc) {
        if (doc == null) {
            return null;
        }
        Object chunkIndex = doc.getMetadata().get("chunk_index");
        if (chunkIndex instanceof Number) {
            return ((Number) chunkIndex).intValue();
        }
        return null;
    }
    
    /**
     * Extracts the document_id from a document's metadata.
     * Falls back to the document's id if document_id is not present.
     */
    private String getDocumentId(Document doc) {
        if (doc == null) {
            return null;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        
        // Try to get document_id first (new documents)
        Object docId = metadata.get("document_id");
        if (docId != null) {
            return docId.toString();
        }
        
        // Fallback: try to get id from metadata (should be the same as document_id)
        Object id = metadata.get("id");
        if (id != null) {
            return id.toString();
        }

        return doc.getId();
    }

    private static boolean isNonEmptyMetadataFieldValue(Object value) {
        if (value instanceof String s) {
            return !s.trim().isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
    
    /**
     * Counts non-null, non-empty metadata fields in a document.
     * Used to select the chunk with most complete metadata when combining.
     */
    private int countMetadataFields(Document doc) {
        if (doc == null) {
            return 0;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        
        int count = 0;
        for (Object value : metadata.values()) {
            if (value != null && isNonEmptyMetadataFieldValue(value)) {
                count++;
            }
        }
        return count;
    }

}

package com.uniovi.rag.services.document;

import com.uniovi.rag.model.DocumentAlreadyExistsException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataDocumentService<T> extends AbstractDocumentService<T> {

    protected final int chunkMaxChars;

    public AbstractMetadataDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate, int chunkMaxChars) {
        super(vectorStore, chatClient, jdbcTemplate);
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
    }

    protected abstract T extractModel(String fullText, String filename);

    protected abstract Map<String, Object> extractMetadata(T model);

    /**
     * Processes a document with improved validation and error handling.
     * Validates content, extracts metadata, and stores the document in the vector store.
     * 
     * @param file The document file to process
     * @throws IllegalArgumentException if content or metadata is invalid
     */
    @Override
    public void processDocument(MultipartFile file) {
        String filename = file != null ? file.getOriginalFilename() : "unknown";
        
        try {
            log().info("Processing document: {}", filename);
            
            // Step 1: Extract content
            String content = extractContent(file);
            
            if (content == null || content.trim().isEmpty()) {
                log().error("Extracted content is null or empty for file: {}", filename);
                throw new IllegalArgumentException("Extracted content is null or empty for file: " + filename);
            }
            
            // Step 2: Validate content length (minimum threshold)
            if (content.trim().length() < 20) {
                log().warn("Content very short for file: {} (length: {}). May result in incomplete extraction.", 
                          filename, content.length());
            }
            
            log().info("Content extracted for file: {} (length: {})", filename, content.length());
            
            // Step 3: Extract model
            T model = extractModel(content, filename);
            
            if (model == null) {
                log().error("Model extraction returned null for file: {}", filename);
                throw new IllegalArgumentException("Model extraction returned null for file: " + filename);
            }
            
            // Step 4: Extract metadata
            Map<String, Object> metadata = extractMetadata(model);
            
            if (metadata == null || metadata.isEmpty()) {
                log().error("Metadata extraction returned null or empty for file: {}", filename);
                throw new IllegalArgumentException("Metadata extraction returned null or empty for file: " + filename);
            }
            
            // Step 4b: Never insert duplicates — if document_id already exists, do not add
            Object docIdObj = metadata.get("document_id");
            String documentId = docIdObj != null ? docIdObj.toString() : null;
            if (documentId != null && !documentId.isBlank() && hasDocumentWithId(documentId)) {
                log().info("Document already exists (document_id={}), skipping insert to avoid duplicate", documentId);
                throw new DocumentAlreadyExistsException(documentId);
            }

            // Step 5: Validate critical metadata fields
            validateMetadata(metadata, filename);
            
            // Step 6: Split content into chunks for embedding (embedding models have lower context limits)
            List<String> chunks = splitContentIntoChunks(content, chunkMaxChars);
            
            log().info("Content split into {} chunks for embedding (original length: {})", 
                      chunks.size(), content.length());
            
            // Step 7: Create multiple documents (one per chunk) with same metadata
            // Prepend short date/president prefix to chunk text so embedding reflects metadata (better similarity for date/person queries)
            String metadataPrefix = buildChunkMetadataPrefix(metadata);
            List<Document> documents = new java.util.ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> chunkMetadata = new java.util.HashMap<>(metadata);
                chunkMetadata.put("chunk_index", i);
                chunkMetadata.put("total_chunks", chunks.size());
                String chunkText = chunks.get(i);
                String contentForEmbedding = metadataPrefix.isEmpty() ? chunkText : (metadataPrefix + chunkText);
                documents.add(new Document(contentForEmbedding, chunkMetadata));
            }
            
            add(documents);
            
            log().info("Successfully processed document: {} with {} chunks and {} metadata fields", 
                      filename, documents.size(), metadata.size());
            
            log().info("Successfully processed document: {} with {} metadata fields", filename, metadata.size());
        } catch (IllegalArgumentException e) {
            log().error("Validation error processing document {}: {}", filename, e.getMessage());
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log().error("Unexpected error processing document: {}", filename, e);
            throw new RuntimeException("Failed to process document: " + filename, e);
        }
    }
    
    /**
     * Validates that critical metadata fields are present and non-empty.
     * This helps prevent storing documents with incomplete metadata.
     * 
     * @param metadata The metadata to validate
     * @param filename The filename for logging purposes
     * @throws IllegalArgumentException if critical fields are missing or invalid
     */
    protected void validateMetadata(Map<String, Object> metadata, String filename) {
        if (metadata == null) {
            log().error("Metadata is null for file: {}", filename);
            throw new IllegalArgumentException("Metadata cannot be null for file: " + filename);
        }
        
        // Validate critical fields
        if (!metadata.containsKey("document_id") || metadata.get("document_id") == null) {
            log().error("Metadata missing document_id for file: {}. This will cause issues with chunk grouping.", filename);
            throw new IllegalArgumentException("Metadata missing document_id for file: " + filename);
        }
        
        if (!metadata.containsKey("filename") || metadata.get("filename") == null) {
            log().error("Metadata missing filename for file: {}", filename);
            throw new IllegalArgumentException("Metadata missing filename for file: " + filename);
        }
        
        // Validate types of complex fields
        validateMetadataTypes(metadata, filename);
        
        // Log metadata summary for debugging
        if (log().isDebugEnabled()) {
            log().info("Metadata validation for file: {} - Fields: {}", filename, metadata.keySet());
        }
    }
    
    /**
     * Validates types of complex metadata fields.
     */
    private void validateMetadataTypes(Map<String, Object> metadata, String filename) {
        // Validate attendees is List
        if (metadata.containsKey("attendees")) {
            Object attendees = metadata.get("attendees");
            if (attendees != null && !(attendees instanceof List)) {
                log().warn("Invalid type for 'attendees' in file {}: {}, expected List. Will be normalized during extraction.", 
                          filename, attendees.getClass().getName());
            }
        }
        
        // Validate decisions is List
        if (metadata.containsKey("decisions")) {
            Object decisions = metadata.get("decisions");
            if (decisions != null && !(decisions instanceof List)) {
                log().warn("Invalid type for 'decisions' in file {}: {}, expected List. Will be normalized during extraction.", 
                          filename, decisions.getClass().getName());
            }
        }
        
        // Validate topics is List
        if (metadata.containsKey("topics")) {
            Object topics = metadata.get("topics");
            if (topics != null && !(topics instanceof List)) {
                log().warn("Invalid type for 'topics' in file {}: {}, expected List. Will be normalized during extraction.", 
                          filename, topics.getClass().getName());
            }
        }
        
        // Validate mentionedEntities is List
        if (metadata.containsKey("mentionedEntities")) {
            Object mentionedEntities = metadata.get("mentionedEntities");
            if (mentionedEntities != null && !(mentionedEntities instanceof List)) {
                log().warn("Invalid type for 'mentionedEntities' in file {}: {}, expected List. Will be normalized during extraction.", 
                          filename, mentionedEntities.getClass().getName());
            }
        }
        
        // Validate agenda is Map
        if (metadata.containsKey("agenda")) {
            Object agenda = metadata.get("agenda");
            if (agenda != null && !(agenda instanceof Map)) {
                log().warn("Invalid type for 'agenda' in file {}: {}, expected Map. Will be normalized during extraction.", 
                          filename, agenda.getClass().getName());
            }
        }
        
        // Validate numberOfAttendees is Number
        if (metadata.containsKey("numberOfAttendees")) {
            Object numberOfAttendees = metadata.get("numberOfAttendees");
            if (numberOfAttendees != null && !(numberOfAttendees instanceof Number)) {
                log().warn("Invalid type for 'numberOfAttendees' in file {}: {}, expected Number. Will be normalized during extraction.", 
                          filename, numberOfAttendees.getClass().getName());
            }
        }
    }
    
    /**
     * Splits content into chunks that fit within embedding model context limits.
     * Each chunk will be stored as a separate document with the same metadata.
     * This preserves all content while respecting embedding model limits.
     * 
     * @param content The full content to split
     * @param maxCharsPerChunk Maximum characters per chunk (conservative limit for embedding models)
     * @return List of content chunks
     */
    protected List<String> splitContentIntoChunks(String content, int maxCharsPerChunk) {
        if (content == null || content.trim().isEmpty()) {
            return List.of("");
        }
        
        String trimmed = content.trim();
        
        // If content fits in one chunk, return as single chunk
        if (trimmed.length() <= maxCharsPerChunk) {
            return List.of(trimmed);
        }
        
        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        
        while (start < trimmed.length()) {
            int end = Math.min(start + maxCharsPerChunk, trimmed.length());
            
            // Try to break at word boundary to avoid splitting words
            if (end < trimmed.length()) {
                // Look for last space, newline, or punctuation before the limit
                int lastBreak = end;
                for (int i = end - 1; i > start + (maxCharsPerChunk * 2 / 3); i--) {
                    char c = trimmed.charAt(i);
                    if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                        lastBreak = i + 1;
                        break;
                    }
                }
                end = lastBreak;
            }
            
            String chunk = trimmed.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            start = end;
        }
        
        log().info("Split content into {} chunks (max {} chars per chunk, total {} chars)", 
                  chunks.size(), maxCharsPerChunk, trimmed.length());
        
        return chunks;
    }

    /**
     * Builds a short prefix from metadata (date, president) to prepend to chunk content for embedding.
     * Improves similarity search for queries like "president on 25 feb 2026". Kept short to not exceed embedding limit.
     */
    protected String buildChunkMetadataPrefix(Map<String, Object> metadata) {
        if (metadata == null) return "";
        Object dateObj = metadata.get("date_iso") != null ? metadata.get("date_iso") : metadata.get("date");
        Object presidentObj = metadata.get("president");
        String dateStr = dateObj != null ? dateObj.toString().trim() : "";
        String presidentStr = presidentObj != null ? presidentObj.toString().trim() : "";
        if (dateStr.isEmpty() && presidentStr.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (!dateStr.isEmpty()) sb.append("Acta ").append(dateStr).append(". ");
        if (!presidentStr.isEmpty()) sb.append("Presidente: ").append(presidentStr).append(". ");
        return sb.toString();
    }
}
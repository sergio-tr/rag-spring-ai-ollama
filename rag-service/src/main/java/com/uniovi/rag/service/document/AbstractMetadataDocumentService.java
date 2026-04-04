package com.uniovi.rag.service.document;

import com.uniovi.rag.domain.exception.DocumentAlreadyExistsException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataDocumentService<T> extends AbstractDocumentService {

    private static final String METADATA_KEY_DOCUMENT_ID = "document_id";

    private static int safeFilenameLength(String filename) {
        return filename != null ? filename.length() : 0;
    }

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
            log().info("Processing document (filename length: {})", safeFilenameLength(filename));
            
            // Step 1: Extract content
            String content = extractContent(file);
            
            if (content == null || content.trim().isEmpty()) {
                log().error("Extracted content is null or empty (filename length: {})", safeFilenameLength(filename));
                throw new IllegalArgumentException("Extracted content is null or empty for file: " + filename);
            }
            
            // Step 2: Validate content length (minimum threshold)
            if (content.trim().length() < 20) {
                log().warn("Content very short (filename length: {}, content length: {}). May result in incomplete extraction.",
                          safeFilenameLength(filename), content.length());
            }
            
            log().info("Content extracted (filename length: {}, content length: {})", safeFilenameLength(filename), content.length());
            
            // Step 3: Extract model
            T model = extractModel(content, filename);
            
            if (model == null) {
                log().error("Model extraction returned null (filename length: {})", safeFilenameLength(filename));
                throw new IllegalArgumentException("Model extraction returned null for file: " + filename);
            }
            
            // Step 4: Extract metadata
            Map<String, Object> metadata = extractMetadata(model);
            
            if (metadata == null || metadata.isEmpty()) {
                log().error("Metadata extraction returned null or empty (filename length: {})", safeFilenameLength(filename));
                throw new IllegalArgumentException("Metadata extraction returned null or empty for file: " + filename);
            }
            
            // Step 4b: Never insert duplicates — if document_id already exists, do not add
            Object docIdObj = metadata.get(METADATA_KEY_DOCUMENT_ID);
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
            
            log().info("Successfully processed document (filename length: {}) with {} chunks and {} metadata fields",
                      safeFilenameLength(filename), documents.size(), metadata.size());

            log().info("Successfully processed document (filename length: {}) with {} metadata fields", safeFilenameLength(filename), metadata.size());
        } catch (IllegalArgumentException e) {
            log().error("Validation error processing document (filename length: {}): {}", safeFilenameLength(filename), e.getMessage());
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log().error("Unexpected error processing document (filename length: {})", safeFilenameLength(filename), e);
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
            log().error("Metadata is null (filename length: {})", safeFilenameLength(filename));
            throw new IllegalArgumentException("Metadata cannot be null for file: " + filename);
        }
        
        // Validate critical fields
        if (!metadata.containsKey("document_id") || metadata.get("document_id") == null) {
            log().error("Metadata missing document_id (filename length: {}). This will cause issues with chunk grouping.", safeFilenameLength(filename));
            throw new IllegalArgumentException("Metadata missing document_id for file: " + filename);
        }
        
        if (!metadata.containsKey("filename") || metadata.get("filename") == null) {
            log().error("Metadata missing filename field (upload filename length: {})", safeFilenameLength(filename));
            throw new IllegalArgumentException("Metadata missing filename for file: " + filename);
        }
        
        // Validate types of complex fields
        validateMetadataTypes(metadata, filename);
        
        if (log().isDebugEnabled()) {
            log().debug("Metadata validation: {} entries", metadata.size());
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
                log().warn("Invalid type for 'attendees' in file (name length {}): {}, expected List. Will be normalized during extraction.",
                          filename != null ? filename.length() : 0, attendees.getClass().getName());
            }
        }
        
        // Validate decisions is List
        if (metadata.containsKey("decisions")) {
            Object decisions = metadata.get("decisions");
            if (decisions != null && !(decisions instanceof List)) {
                log().warn("Invalid type for 'decisions': expected List, got {}. Will be normalized during extraction.",
                          decisions.getClass().getSimpleName());
            }
        }
        
        // Validate topics is List
        if (metadata.containsKey("topics")) {
            Object topics = metadata.get("topics");
            if (topics != null && !(topics instanceof List)) {
                log().warn("Invalid type for 'topics': expected List, got {}. Will be normalized during extraction.",
                          topics.getClass().getSimpleName());
            }
        }
        
        // Validate mentionedEntities is List
        if (metadata.containsKey("mentionedEntities")) {
            Object mentionedEntities = metadata.get("mentionedEntities");
            if (mentionedEntities != null && !(mentionedEntities instanceof List)) {
                log().warn("Invalid type for 'mentionedEntities': expected List, got {}. Will be normalized during extraction.",
                          mentionedEntities.getClass().getSimpleName());
            }
        }
        
        // Validate agenda is Map
        if (metadata.containsKey("agenda")) {
            Object agenda = metadata.get("agenda");
            if (agenda != null && !(agenda instanceof Map)) {
                log().warn("Invalid type for 'agenda': expected Map, got {}. Will be normalized during extraction.",
                          agenda.getClass().getSimpleName());
            }
        }
        
        // Validate numberOfAttendees is Number
        if (metadata.containsKey("numberOfAttendees")) {
            Object numberOfAttendees = metadata.get("numberOfAttendees");
            if (numberOfAttendees != null && !(numberOfAttendees instanceof Number)) {
                log().warn("Invalid type for 'numberOfAttendees': expected Number, got {}. Will be normalized during extraction.",
                          numberOfAttendees.getClass().getSimpleName());
            }
        }
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
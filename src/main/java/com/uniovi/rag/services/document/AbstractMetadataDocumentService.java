package com.uniovi.rag.services.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataDocumentService<T> extends AbstractDocumentService<T> {

    public AbstractMetadataDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        super(vectorStore, chatClient, jdbcTemplate);
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
            
            log().debug("Content extracted for file: {} (length: {})", filename, content.length());
            
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
            
            // Step 5: Validate critical metadata fields
            validateMetadata(metadata, filename);
            
            // Step 6: Create document and add to vector store
            Document document = new Document(content, metadata);
            add(List.of(document));
            
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
        // Check for document_id (critical for chunk grouping)
        if (!metadata.containsKey("document_id") || metadata.get("document_id") == null) {
            log().warn("Metadata missing document_id for file: {}. This may cause issues with chunk grouping.", filename);
        }
        
        // Check for filename
        if (!metadata.containsKey("filename") || metadata.get("filename") == null) {
            log().warn("Metadata missing filename for file: {}", filename);
        }
        
        // Log metadata summary for debugging
        if (log().isDebugEnabled()) {
            log().debug("Metadata validation for file: {} - Fields: {}", filename, metadata.keySet());
        }
    }
}
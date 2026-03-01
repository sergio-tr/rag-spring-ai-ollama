package com.uniovi.rag.services.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class SimpleDocumentService<T> extends AbstractDocumentService<T> {

    private final int chunkMaxChars;

    public SimpleDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate, int chunkMaxChars) {
        super(vectorStore, chatClient, jdbcTemplate);
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
    }

    public void processDocument(MultipartFile file) {
        log().info("SIMPLE: Processing file" + file.getName());
        String content = extractContent(file);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Document content does not exist");
        }
        
        // Split content into chunks for embedding (embedding models have lower context limits)
        List<String> chunks = splitContentIntoChunks(content, chunkMaxChars);
        
        log().info("Content split into {} chunks for embedding (original length: {})", 
                  chunks.size(), content.length());
        
        // Generate a unique document_id based on filename and content hash for chunk grouping
        String documentId = generateDocumentId(file.getOriginalFilename(), content);

        // If the same document (same name+content) already exists, remove it and re-insert to avoid duplicates
        if (hasDocumentWithId(documentId)) {
            log().info("Document already exists (document_id={}), removing previous chunks and re-inserting", documentId);
            deleteDocumentByDocumentId(documentId);
        }
        
        // Create multiple documents (one per chunk) with basic metadata for grouping
        List<Document> documents = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("document_id", documentId);
            metadata.put("filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", chunks.size());
            documents.add(new Document(chunks.get(i), metadata));
        }
        
        vectorStore.add(documents);
    }
    
    /**
     * Splits content into chunks that fit within embedding model context limits.
     * Each chunk will be stored as a separate document.
     * This preserves all content while respecting embedding model limits.
     * 
     * @param content The full content to split
     * @param maxCharsPerChunk Maximum characters per chunk (conservative limit for embedding models)
     * @return List of content chunks
     */
    private List<String> splitContentIntoChunks(String content, int maxCharsPerChunk) {
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
     * Generates a unique document ID based on filename and content hash.
     * This allows chunks from the same document to be grouped together.
     */
    private String generateDocumentId(String filename, String content) {
        String base = (filename != null ? filename : "unknown") + "_" + 
                     (content != null ? String.valueOf(content.hashCode()) : "0");
        // Use a simple hash to create a consistent ID
        return String.valueOf(Math.abs(base.hashCode()));
    }
}

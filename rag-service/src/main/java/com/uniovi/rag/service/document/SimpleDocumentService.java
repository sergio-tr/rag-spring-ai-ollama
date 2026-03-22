package com.uniovi.rag.service.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class SimpleDocumentService<T> extends AbstractDocumentService<T> {

    private final int chunkMaxChars;

    public SimpleDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate,
                                 @Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
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

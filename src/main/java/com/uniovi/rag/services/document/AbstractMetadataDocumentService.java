package com.uniovi.rag.services.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataDocumentService<T> extends AbstractDocumentService<T> {

    public AbstractMetadataDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        super(vectorStore, chatClient);
    }

    protected abstract T extractModel(String fullText, String filename);

    protected abstract Map<String, Object> extractMetadata(T model);

    @Override
    public void processDocument(MultipartFile file) {
        String filename = file != null ? file.getOriginalFilename() : "unknown";
        
        try {
            String content = extractContent(file);
            
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("Extracted content is null or empty for file: " + filename);
            }
            
            T model = extractModel(content, filename);
            Map<String, Object> metadata = extractMetadata(model);
            
            if (metadata == null || metadata.isEmpty()) {
                throw new IllegalArgumentException("Metadata extraction returned null or empty for file: " + filename);
            }
            
            add(List.of(new Document(content, metadata)));
        } catch (Exception e) {
            System.err.println("Error in processDocument for file: " + filename);
            e.printStackTrace();
            throw e; // Re-lanzar para que el controller lo maneje
        }
    }
}
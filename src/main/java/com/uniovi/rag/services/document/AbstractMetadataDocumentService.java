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
        String content = extractContent(file);
        T model = extractModel(content, file.getOriginalFilename());
        Map<String, Object> metadata = extractMetadata(model);
        add(List.of(new Document(content, metadata)));
    }
}
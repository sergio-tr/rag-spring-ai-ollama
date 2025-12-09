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

    public SimpleDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        super(vectorStore, chatClient, jdbcTemplate);
    }

    public void processDocument(MultipartFile file) {
        log().info("SIMPLE: Processing file" + file.getName());
        String content = extractContent(file);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Document content does not exist");
        }
        Document document = new Document(content);
        vectorStore.add(List.of(document));
    }
}

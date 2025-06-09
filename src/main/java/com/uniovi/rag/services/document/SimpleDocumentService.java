package com.uniovi.rag.services.document;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class SimpleDocumentService extends AbstractDocumentService {

    public SimpleDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        super(vectorStore, chatClient);
    }

    public void processDocument(MultipartFile file) {
        System.out.println("SIMPLE: Processing file" + file.getName());
        String content = extractContent(file);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Document content does not exist");
        }
        Document document = new Document(content);
        vectorStore.add(List.of(document));
    }
}

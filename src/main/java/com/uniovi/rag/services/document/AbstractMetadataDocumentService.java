package com.uniovi.rag.services.document;

import com.uniovi.rag.model.Minute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataDocumentService extends AbstractDocumentService {

    public AbstractMetadataDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        super(vectorStore, chatClient);
    }

    protected abstract Minute extractMinute(String fullText, String filename);

    protected abstract Map<String, Object> extractMetadata(Minute minute);

    @Override
    public void processDocument(MultipartFile file) {
        String content = extractContent(file);
        Minute minute = extractMinute(content, file.getOriginalFilename());
        Map<String, Object> metadata = extractMetadata(minute);
        add(List.of(new Document(content, metadata)));
    }
}
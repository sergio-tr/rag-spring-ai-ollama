package com.uniovi.rag.services.query;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SimpleQueryService extends AbstractQueryService{

    public SimpleQueryService(PgVectorStore vectorStore, OllamaChatModel chatModel) {
        super(vectorStore, chatModel);
    }
    public String generateResponse(String question) {
        SearchRequest req = SearchRequest
                .query(question)
                .withTopK(5);
        List<Document> relevantDocs = vectorStore.similaritySearch(req);

        String context = relevantDocs.stream()
                .map(Document::getContent)
                .reduce("", (a, b) -> a + "\n\n" + b);

        Message prompt = promptTemplate.createMessage(Map.of("query", question, "documents", context));
        UserMessage userMessage = new UserMessage(prompt.getContent());
        System.out.println("Question: " + prompt.getContent());
        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        System.out.println("System prompt: " + systemPrompt);

        return chatModel.call(systemMessage, userMessage);
    }
}
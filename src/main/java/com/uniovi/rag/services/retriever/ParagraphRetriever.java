package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ParagraphRetriever extends FilteredContextRetriever {

    public ParagraphRetriever(PgVectorStore vectorStore, OllamaChatModel model, int topK) {
        super(vectorStore, model, topK);
    }

    @Override
    protected String filterContentByQuestion(Document doc, String query, String search) {

        String[] paragraphs = doc.getContent().split("\n");
        String lowerCaseSearch = search.toLowerCase();

        String context = Arrays.stream(paragraphs)
                .map(String::toLowerCase)
                .filter(paragraph -> paragraph.contains(lowerCaseSearch))
                .collect(Collectors.joining("\n"));

        System.out.println("------------------------");
        System.out.println("PARAGRAPH RETRIEVER: ");
        System.out.println(context);

        return context;
    }
}

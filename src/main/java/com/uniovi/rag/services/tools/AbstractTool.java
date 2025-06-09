package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

public abstract class AbstractTool implements Tool {

    public static final String PROMPT_KEYWORDS_EXTRACTOR = """
            Act as a keyword extractor for search queries over homeowners’ meeting minutes.
            
            The minutes follow a structure with fields like:
            - date, place, startTime, endTime
            - president, secretary, attendees
            - agenda (topics), decisions, mentioned_entities, section, number_of_attendees
            
            Your task:
            1. Read the following user question:
            "%s"
            
            2. Extract only the **most relevant and concrete terms** related to the fields above or to entities/actions that could appear in the minutes.
            
            3. Return a single line with the keywords in English, space-separated. Do not include explanations or formatting.
            
            If nothing useful can be extracted, return: [EMPTY]
            """;

    protected final ChatClient chatClient;
    protected final ContextRetriever retriever;

    public AbstractTool(ChatClient chatClient, ContextRetriever retriever) {
        this.chatClient = chatClient;
        this.retriever = retriever;
    }

    protected List<Document> retrieveAllDocuments(String query) {
        retriever.setTopK(1000);
        retriever.setSimilarityThreshold(0);
        return retriever.retrieve(query);
    }

    protected List<Document> retrieveDocuments(String query) {
        retriever.restoreDefaultSettings();
        return retriever.retrieve(query);
    }

    protected List<Document> retrieveDocumentsWithTopK(String query, int topK) {
        retriever.restoreDefaultSettings();
        retriever.setTopK(topK);
        return retriever.retrieve(query);
    }

    protected String extractKeywordsFromQuery(String query) {
        String prompt = PROMPT_KEYWORDS_EXTRACTOR.formatted(query);

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        log().info("[TOOL] Keywords extracted: {}", result);

        return result;
    }


}

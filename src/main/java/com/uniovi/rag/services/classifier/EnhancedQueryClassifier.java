package com.uniovi.rag.services.classifier;

import org.springframework.ai.chat.client.ChatClient;

public class EnhancedQueryClassifier implements QueryClassifier {

    private final QueryClassifier baseClassifier;
    private final ChatClient chatClient;

    public EnhancedQueryClassifier(QueryClassifier baseClassifier, ChatClient chatClient) {
        this.baseClassifier = baseClassifier;
        this.chatClient = chatClient;
    }

    @Override
    public String classifyWithText(String query) {
        return getRefinedType(query);
    }

    @Override
    public QueryType classify(String query) {
        try {
            return QueryType.valueOf(classifyWithText(query));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getRefinedType(String query) {
        String initialType = baseClassifier.classifyWithText(query);
        String refinedType = validateWithLLM(query, initialType);

        System.out.println("[CLASSIFIER] Initial type: " + initialType);
        System.out.println("[CLASSIFIER] Refined type: " + refinedType);

        return refinedType;
    }

    private String validateWithLLM(String query, String initialType) {
        String prompt = """
            You are an expert system for classifying questions asked about meeting minutes. Below are the possible question types in this system, along with their definitions:
            
            Possible question types:
            1. COUNT_DOCUMENTS → How many documents meet a certain condition?
            2. COUNT_AND_EXPLAIN → How many documents address a topic, and what was said in them?
            3. EXTRACT_ENTITIES → Extract people, entities, roles, attendees...
            4. FIND_PARAGRAPH → Locate literal excerpts about a topic.
            5. GET_FIELD → Retrieve a literal value directly (date, location, chairperson…).
            6. BOOLEAN_QUERY → Confirm whether something occurred (was mentioned, approved…).
            7. COMPARE → Compare values between different minutes (attendees, duration, mentions…).
            8. SUMMARIZE_TOPIC → Summarize what was said about a specific topic.
            9. SUMMARIZE_MEETING → Summarize an entire meeting.
            10. DECISION_EXTRACTION → Extract the decisions that were agreed upon.
            11. FILTER_AND_LIST → Apply multiple filters and list the results.
            12. GET_DURATION → Retrieve the duration of a meeting.
            
            Your task is to validate whether the initial classification is correct or should be corrected.
            
            Question:
            "%s"
            
            Proposed classification:
            %s
            
            Return only one of the following valid identifiers (without explanations or quotation marks):
            COUNT_DOCUMENTS, COUNT_AND_EXPLAIN, EXTRACT_ENTITIES, FIND_PARAGRAPH, GET_FIELD,
            BOOLEAN_QUERY, COMPARE, SUMMARIZE_TOPIC, SUMMARIZE_MEETING,
            DECISION_EXTRACTION, FILTER_AND_LIST, GET_DURATION.
            """.formatted(query, initialType);


        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toUpperCase();
    }
}

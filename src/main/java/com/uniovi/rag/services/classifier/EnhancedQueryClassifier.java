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
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        try {
            String typeText = classifyWithText(query);
            
            if (typeText == null || typeText.trim().isEmpty() || typeText.equals("UNKNOWN")) {
                // Fallback: try base classifier directly first
                try {
                    QueryType baseType = baseClassifier.classify(query);
                    if (baseType != null) {
                        log().info("[CLASSIFIER] Enhanced failed, using base classifier result: {}", baseType);
                        return baseType;
                    }
                } catch (Exception e) {
                    log().debug("[CLASSIFIER] Base classifier failed: {}", e.getMessage());
                }
                
                // If base classifier also returns null, try LLM fallback
                log().info("[CLASSIFIER] Both enhanced and base classifier returned null, trying LLM fallback");
                QueryType llmType = classifyWithLLMFallback(query);
                if (llmType != null) {
                    log().info("[CLASSIFIER] LLM fallback succeeded: {}", llmType);
                    return llmType;
                }
                
                log().info("[CLASSIFIER] All classification methods failed, returning null");
                return null;
            }
            
            return QueryType.valueOf(typeText);
        } catch (IllegalArgumentException e) {
            log().info("[CLASSIFIER] Invalid QueryType, trying base classifier and LLM fallback");
            try {
                QueryType baseType = baseClassifier.classify(query);
                if (baseType != null) {
                    log().info("[CLASSIFIER] Using base classifier result: {}", baseType);
                    return baseType;
                }
            } catch (Exception ex) {
                log().debug("[CLASSIFIER] Base classifier failed: {}", ex.getMessage());
            }
            
            // Try LLM fallback
            QueryType llmType = classifyWithLLMFallback(query);
            if (llmType != null) {
                log().info("[CLASSIFIER] LLM fallback succeeded: {}", llmType);
                return llmType;
            }
            
            return null;
        } catch (Exception e) {
            log().info("[CLASSIFIER] Error in enhanced classifier: {}, trying base classifier and LLM fallback", e.getMessage());
            try {
                QueryType baseType = baseClassifier.classify(query);
                if (baseType != null) {
                    return baseType;
                }
            } catch (Exception ex) {
                log().debug("[CLASSIFIER] Base classifier failed: {}", ex.getMessage());
            }
            
            // Try LLM fallback
            QueryType llmType = classifyWithLLMFallback(query);
            if (llmType != null) {
                log().info("[CLASSIFIER] LLM fallback succeeded: {}", llmType);
                return llmType;
            }
            
            return null;
        }
    }

    /** P10: Override refined type when it would route to a worse tool (e.g. COMPARE→BOOLEAN_QUERY, GET_FIELD→EXTRACT_ENTITIES). */
    private static boolean queryClearlyCompares(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return q.contains("compara") || q.contains("comparar") || q.contains("comparación") || q.contains("comparison")
                || (q.contains("más") && (q.contains("febrero") || q.contains("agosto") || q.contains("february") || q.contains("august")));
    }
    private static boolean queryClearlyGetField(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return q.contains("fecha del acta") || q.contains("date of the acta") || (q.contains("fecha") && q.contains("donde") && (q.contains("presidente") || q.contains("secretaria")));
    }
    private static boolean queryClearlyFindParagraph(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return q.contains("secciones que contiene") || q.contains("orden del día") || q.contains("qué contiene el orden");
    }

    private String getRefinedType(String query) {
        String initialType = baseClassifier.classifyWithText(query);
        String refinedType = validateWithLLM(query, initialType);

        log().info("[CLASSIFIER] Initial type: " + initialType);
        log().info("[CLASSIFIER] Refined type: " + refinedType);

        String overridden = applyClassifierOverrides(query, initialType, refinedType);
        if (overridden != null) {
            log().info("[CLASSIFIER] P10 override: keeping {}", overridden);
            return overridden;
        }
        return refinedType;
    }

    /**
     * P10: Applies rule-based overrides to avoid suboptimal refinement. Returns the type to use, or null if no override.
     * Package-private for regression tests.
     */
    static String applyClassifierOverrides(String query, String initialType, String refinedType) {
        if (queryClearlyCompares(query) && "COMPARE".equals(initialType) && !"COMPARE".equals(refinedType)) {
            return "COMPARE";
        }
        if (queryClearlyGetField(query) && "GET_FIELD".equals(initialType) && "EXTRACT_ENTITIES".equals(refinedType)) {
            return "GET_FIELD";
        }
        if (queryClearlyFindParagraph(query) && "FIND_PARAGRAPH".equals(initialType) && "SUMMARIZE_TOPIC".equals(refinedType)) {
            return "FIND_PARAGRAPH";
        }
        return null;
    }

    /**
     * Validates classification with LLM, with fallback to base classifier if LLM fails.
     * Uses English for internal processing, but preserves original language in query.
     */
    private String validateWithLLM(String query, String initialType) {
        if (query == null || query.trim().isEmpty()) {
            return initialType != null ? initialType : "UNKNOWN";
        }
        
        String prompt = String.format("""
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
            
            Question (may be in any language):
            "%s"
            
            Proposed classification:
            %s
            
            Return ONLY one of the following valid identifiers (without explanations or quotation marks):
            COUNT_DOCUMENTS, COUNT_AND_EXPLAIN, EXTRACT_ENTITIES, FIND_PARAGRAPH, GET_FIELD,
            BOOLEAN_QUERY, COMPARE, SUMMARIZE_TOPIC, SUMMARIZE_MEETING,
            DECISION_EXTRACTION, FILTER_AND_LIST, GET_DURATION.
            """, query, initialType != null ? initialType : "UNKNOWN");

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().info("[CLASSIFIER] Empty LLM response, using base classifier result: " + initialType);
                return initialType != null ? initialType : "UNKNOWN";
            }
            
            String refinedType = response.strip().toUpperCase();
            
            try {
                QueryType.valueOf(refinedType);
                return refinedType;
            } catch (IllegalArgumentException e) {
                log().info("[CLASSIFIER] Invalid QueryType from LLM: " + refinedType + ", using base classifier result: " + initialType);
                return initialType != null ? initialType : "UNKNOWN";
            }
        } catch (Exception e) {
            log().info("[CLASSIFIER] Error validating with LLM, using base classifier result: " + initialType + ", error: " + e.getMessage());
            return initialType != null ? initialType : "UNKNOWN";
        }
    }

    /**
     * Classifies query using LLM when base classifier returns null.
     * Uses a detailed prompt with all available tools and their descriptions.
     * Returns null if classification is not possible (better not to classify than to classify incorrectly).
     */
    private QueryType classifyWithLLMFallback(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        String prompt = String.format("""
            You are an expert system for classifying questions asked about meeting minutes.
            
            Your task is to classify the following question into one of the available query types.
            If the question does not clearly fit any of the types, respond with "UNKNOWN".
            It is better to return "UNKNOWN" than to incorrectly classify the question.
            
            Question (may be in any language):
            "%s"
            
            Available query types with detailed descriptions and examples:
            
            1. COUNT_DOCUMENTS
               Description: Questions asking how many documents/meetings meet a certain condition or criteria.
               Examples:
               - "How many meetings were held in 2025?"
               - "¿Cuántas actas hay sobre el ascensor?"
               - "How many meetings discussed the budget?"
               - "¿Cuántas reuniones se celebraron en febrero?"
            
            2. COUNT_AND_EXPLAIN
               Description: Questions asking both the count AND what was said about a topic across multiple documents.
               Examples:
               - "How many meetings discussed the elevator and what was said?"
               - "¿En cuántas actas se menciona el presupuesto y qué se dijo?"
               - "How many times was the budget mentioned and what were the details?"
            
            3. EXTRACT_ENTITIES
               Description: Questions asking to extract specific entities like people, roles, attendees, organizations.
               Examples:
               - "Who attended the meeting on February 24?"
               - "¿Quién fue el presidente de la reunión?"
               - "List all attendees"
               - "¿Qué personas se mencionaron en la reunión?"
            
            4. FIND_PARAGRAPH
               Description: Questions asking to locate specific paragraphs or excerpts from the documents.
               Examples:
               - "Find the paragraph about the elevator"
               - "¿Dónde se habla del presupuesto?"
               - "Show me the section about maintenance"
            
            5. GET_FIELD
               Description: Questions asking for a specific field value directly (date, place, president, secretary, etc.).
               Examples:
               - "What was the date of the meeting?"
               - "¿Dónde se celebró la reunión?"
               - "Who was the president?"
               - "¿Cuál fue el lugar de la reunión del 24 de febrero?"
            
            6. BOOLEAN_QUERY
               Description: Questions asking for a yes/no confirmation about whether something occurred, was mentioned, or was approved.
               Examples:
               - "Was the elevator mentioned in 2025?"
               - "¿Se aprobó el presupuesto?"
               - "Did they discuss maintenance?"
               - "¿Se mencionó el ascensor en alguna reunión?"
            
            7. COMPARE
               Description: Questions asking to compare values between different meetings or periods.
               Examples:
               - "Compare the number of attendees between 2025 and 2026"
               - "¿Cuál fue la diferencia de asistentes entre febrero y marzo?"
               - "Compare meeting durations"
               - "¿Se mencionó más el ascensor en 2025 o en 2026?"
            
            8. SUMMARIZE_TOPIC
               Description: Questions asking to summarize what was said about a specific topic across meetings.
               Examples:
               - "Summarize what was discussed about the elevator"
               - "¿Qué se dijo sobre el presupuesto?"
               - "Summarize the discussions about maintenance"
            
            9. SUMMARIZE_MEETING
               Description: Questions asking to summarize an entire meeting or specific meeting.
               Examples:
               - "Summarize the meeting on February 24"
               - "¿Qué pasó en la reunión del 24 de febrero?"
               - "Give me a summary of the meeting"
               - "Resume la reunión"
            
            10. DECISION_EXTRACTION
                Description: Questions asking specifically for decisions, agreements, or resolutions made in meetings.
                Examples:
                - "What decisions were made?"
                - "¿Qué se acordó en la reunión?"
                - "List all decisions from the meeting"
                - "¿Cuáles fueron las decisiones tomadas?"
            
            11. FILTER_AND_LIST
                Description: Questions asking to apply multiple filters and list the results (e.g., list meetings by date, topic, etc.).
                Examples:
                - "List all meetings in February"
                - "¿Qué reuniones hubo sobre el ascensor?"
                - "Show meetings with more than 10 attendees"
            
            12. GET_DURATION
                Description: Questions asking specifically for the duration or length of a meeting.
                Examples:
                - "How long was the meeting on February 24?"
                - "¿Cuánto duró la reunión?"
                - "What was the duration of the meeting?"
            
            Instructions:
            - Analyze the question carefully
            - Determine which query type best matches the question's intent
            - If the question is ambiguous or doesn't clearly fit any type, respond with "UNKNOWN"
            - Consider semantic meaning, not just exact words
            - Respond with ONLY the query type identifier (e.g., "COUNT_DOCUMENTS", "GET_FIELD", etc.) or "UNKNOWN"
            - Do not include any explanation or additional text
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().info("[CLASSIFIER] Empty LLM fallback response, returning null");
                return null;
            }
            
            String typeText = response.strip().toUpperCase().trim();
            
            // Remove any extra text that might be in the response
            // Extract just the QueryType identifier
            for (QueryType type : QueryType.values()) {
                if (typeText.contains(type.name())) {
                    log().info("[CLASSIFIER] LLM fallback classified as: {}", type);
                    return type;
                }
            }
            
            // Check for UNKNOWN
            if (typeText.contains("UNKNOWN")) {
                log().info("[CLASSIFIER] LLM fallback returned UNKNOWN, returning null");
                return null;
            }
            
            // Try to parse directly
            try {
                QueryType parsedType = QueryType.valueOf(typeText);
                log().info("[CLASSIFIER] LLM fallback classified as: {}", parsedType);
                return parsedType;
            } catch (IllegalArgumentException e) {
                log().warn("[CLASSIFIER] LLM fallback returned invalid QueryType: '{}', returning null", typeText);
                return null;
            }
        } catch (Exception e) {
            log().warn("[CLASSIFIER] Error in LLM fallback classification: {}, returning null", e.getMessage());
            return null;
        }
    }
}

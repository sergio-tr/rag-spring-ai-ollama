package com.uniovi.rag.services.expand;

import org.springframework.ai.chat.client.ChatClient;

public class MinuteDocumentStructureExpander extends AbstractQueryExpander {

    private static final String DOCUMENT_STRUCTURE_PROMPT = """
        You are a query enhancement system for meeting minutes retrieval.
        
        Your task is to rephrase the user's question to make it clearer, more structured, and more relevant 
        within the context of homeowners' association meeting minutes.
        
        These meeting minutes follow a formal structure with sections such as:
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions.
        - Questions and Requests: open interventions at the end of the session.
        
        IMPORTANT REQUIREMENTS:
        1. Maintain the EXACT SAME LANGUAGE as the original question (if Spanish, keep Spanish; if English, keep English, etc.)
        2. Keep the original meaning and intent of the question
        3. Use terminology from the meeting minutes structure when appropriate
        4. Do not translate or change the language
        5. Do not answer the question - only rephrase it
        
        Rephrase the question taking this structure into account, using specific terms from the sections mentioned above,
        while also keeping the user's wording and orienting the generated query to facilitate the precise location 
        of information within the minutes later on.
        
        If the question cannot be rephrased because it is already well-formulated, simply return the original question unchanged.
        
        Original Question: "%s"
        
        Rephrased question (in the same language):
        """;


    public MinuteDocumentStructureExpander(ChatClient client) {
        super(client);
    }

    @Override
    public String expand(String query) {
        if (query == null || query.trim().isEmpty()) {
            log().warn("Empty query provided to expander, returning original");
            return query != null ? query : "";
        }
        
        String prompt = String.format(DOCUMENT_STRUCTURE_PROMPT, query);

        try {
            String result = client.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in expander, returning original query");
                return query;
            }
            
            String trimmed = result.trim();
            
            if (!isValidExpansion(query, trimmed)) {
                log().warn("Expansion failed quality validation, returning original query");
                return query;
            }
            
            log().info("-----------------------------------------------------------------------------");
            log().info("EXPANDER: Pregunta original: {}", query);
            log().info("EXPANDER: Pregunta reformulada: {}", trimmed);

            return trimmed;
        } catch (Exception e) {
            log().error("Error expanding query, returning original", e);
            return query; // Return original query on error
        }
    }
    
    /**
     * Validates the quality of the expansion.
     */
    private boolean isValidExpansion(String original, String expanded) {
        if (expanded == null || expanded.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = expanded.trim();
        
        // Validation 1: Same language
        boolean originalHasSpanish = original.matches(".*[áéíóúñ¿¡].*");
        boolean resultHasSpanish = trimmed.matches(".*[áéíóúñ¿¡].*");
        
        if (originalHasSpanish && !resultHasSpanish) {
            log().warn("Expansion changed language from Spanish to another");
            return false;
        }
        
        // Validation 2: Reasonable length (no more than 3x nor less than 1/3)
        if (trimmed.length() > original.length() * 3 || trimmed.length() < original.length() / 3) {
            log().warn("Expansion result length is very different from original (original: {}, expanded: {})", 
                      original.length(), trimmed.length());
            return false;
        }
        
        // Validation 3: Should not be identical (if identical, there's no useful expansion)
        if (trimmed.equalsIgnoreCase(original)) {
            // This is fine, it means the query was already well-formulated
            return true;
        }
        
        // Validation 4: Must contain at least some words from the original (basic similarity)
        String[] originalWords = original.toLowerCase().split("\\s+");
        String expandedLower = trimmed.toLowerCase();
        int matchingWords = 0;
        for (String word : originalWords) {
            if (word.length() > 3 && expandedLower.contains(word)) {  // Only words with more than 3 characters
                matchingWords++;
            }
        }
        
        // At least 30% of words must be present
        if (originalWords.length > 0 && matchingWords < originalWords.length * 0.3) {
            log().warn("Expansion has too few matching words with original ({} out of {})", 
                      matchingWords, originalWords.length);
            return false;
        }
        
        return true;
    }
}

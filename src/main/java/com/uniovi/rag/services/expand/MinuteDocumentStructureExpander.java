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
            
            // If the original query has Spanish characters and the result doesn't, it might have changed language
            boolean originalHasSpanish = query.matches(".*[áéíóúñ¿¡].*");
            boolean resultHasSpanish = trimmed.matches(".*[áéíóúñ¿¡].*");
            
            if (originalHasSpanish && !resultHasSpanish) {
                log().warn("Expansion may have changed language from Spanish to another, returning original query");
                return query;
            }
            
            if (trimmed.length() > query.length() * 3 || trimmed.length() < query.length() / 3) {
                log().warn("Expansion result length is very different from original, returning original query");
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
}

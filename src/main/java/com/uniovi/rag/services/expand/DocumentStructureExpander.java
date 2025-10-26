package com.uniovi.rag.services.expand;

import org.springframework.ai.chat.client.ChatClient;

public class DocumentStructureExpander extends AbstractQueryExpander {

    private static final String DOCUMENT_STRUCTURE_PROMPT = """
        Your task is to rephrase the <Question> to make it clearer, more structured, and more relevant within the context of homeowners’ association meeting minutes.
        
        These meeting minutes follow a formal structure with sections such as:
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions.
        - Questions and Requests: open interventions at the end of the session.
        
        Rephrase the question taking this structure into account, using specific terms from the sections mentioned above,
        while also keeping the user’s wording and orienting the generated query to facilitate the precise location of information within the minutes later on.
        
        Do not answer the question. Return ONLY ONE SINGLE NEW QUESTION OR PHRASE that asks exactly the same thing,
        but using the terminology and sections previously described to identify parts of the minutes.
        If the question cannot be rephrased because it is already well-formulated, simply return the original question.
        
        Original <Question>: "%s"
        Rephrased question:
        """;


    public DocumentStructureExpander(ChatClient client) {
        super(client);
    }

    @Override
    public String expand(String query) {
        String prompt = String.format(DOCUMENT_STRUCTURE_PROMPT, query);

        String result = client.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        log().info("-----------------------------------------------------------------------------");
        log().info("EXPANDER: Pregunta original: {}", query);
        log().info("EXPANDER: Pregunta reformulada: {}", result);

        return result;
    }
}

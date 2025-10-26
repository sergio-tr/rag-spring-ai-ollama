package com.uniovi.rag.services.retriever;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.PgVectorStore;

public class DocumentContextRetriever extends FilteredContextRetriever {

    private final static String PROMPT_TEMPLATE = """
        Your task is to filter the following content by removing only the information that is irrelevant
        to the given question, **without modifying or summarizing** anything that may be useful to answer it.
        
        The content of homeowners’ association meeting minutes follows a structure like this:
        - MINUTES OF THE HOMEOWNERS’ ASSOCIATION MEETING
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions
        - Questions and Requests: open interventions at the end of the session
        - Meeting end time
        
        In addition to the question, key entities related to what should be searched for in the content of one or more meeting minutes have been identified. 
        These entities may include dates, locations, participants, topics, or important actions. You should use them to help decide what content to keep.
        
        Content: "%s"
        Question: "%s"
        
        Return only the filtered content (without adding headers, notes, comments, or explanations). 
        If there is nothing relevant, return an empty string ('').
        """;

    private static final String NER_PROMPT_TEMPLATE = """
        Your task is to filter the following content by removing only the information that is irrelevant
        to the given question and to the key entities extracted for this question, **without modifying or summarizing** 
        anything that may be useful to answer it.
        
        The content of homeowners’ association meeting minutes follows a structure like this:
        - MINUTES OF THE HOMEOWNERS’ ASSOCIATION MEETING
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions
        - Questions and Requests: open interventions at the end of the session
        - Meeting end time
        
        In addition to the question, key entities related to what should be searched for in the content of one or more meeting minutes have been identified. 
        These entities may include dates, locations, participants, topics, or important actions. You should use them to help decide what content to keep.
        
        Content: "%s"
        Question: "%s"
        Key entities: "%s"
        
        Return only the filtered content (without adding headers, notes, comments, or explanations). 
        If there is nothing relevant, return an empty string ('').
        """;



    public DocumentContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
        setPromptTemplate(PROMPT_TEMPLATE);
        setNerPromptTemplate(NER_PROMPT_TEMPLATE);
    }

}

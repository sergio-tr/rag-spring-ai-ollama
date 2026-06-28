package com.uniovi.rag.application.service.knowledge.document;

/**
 * One indexed slice of an acta with a stable section label for retrieval expansion.
 */
public record ActaSectionChunk(String text, String sectionType, int sectionPart) {

    public static final String SECTION_HEADER = "header";
    public static final String SECTION_PARTICIPANTS = "participants";
    public static final String SECTION_AGENDA = "agenda";
    public static final String SECTION_AGREEMENTS = "agreements";
    public static final String SECTION_CLOSING = "closing";
    public static final String SECTION_BODY = "body";

    public ActaSectionChunk {
        text = text != null ? text : "";
        sectionType = sectionType != null && !sectionType.isBlank() ? sectionType : SECTION_BODY;
    }
}

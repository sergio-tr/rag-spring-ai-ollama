package com.uniovi.rag.domain.exception;

/**
 * Thrown when attempting to add a document that already exists in the knowledge base (same document_id).
 */
public class DocumentAlreadyExistsException extends RuntimeException {

    private final String documentId;

    public DocumentAlreadyExistsException(String documentId) {
        super("Document already exists in knowledge base: " + documentId);
        this.documentId = documentId;
    }

    public String getDocumentId() {
        return documentId;
    }
}

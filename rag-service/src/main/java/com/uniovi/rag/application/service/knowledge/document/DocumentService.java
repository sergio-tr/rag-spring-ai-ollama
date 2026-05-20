package com.uniovi.rag.application.service.knowledge.document;

import com.uniovi.rag.infrastructure.observability.Loggable;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends Loggable {

    void processDocument(MultipartFile file);

    void add(List<Document> documents);
    
    /**
     * Clears all documents from the vector store and documents table.
     * This is useful when switching between different configurations that process documents differently.
     */
    void clearDatabase();
    
    /**
     * Checks if the database has any documents.
     * @return true if there are documents in the database, false otherwise
     */
    boolean hasDocuments();
    
    /**
     * Deletes all chunks of a document by document_id.
     * Since documents are now split into chunks, this method deletes all chunks
     * that belong to the same document_id.
     * 
     * @param documentId The document_id to delete (all chunks with this document_id will be removed)
     * @return The number of chunks deleted
     */
    int deleteDocumentByDocumentId(String documentId);

    /**
     * Checks whether any chunk with the given document_id exists in the store.
     * Used to avoid duplicate documents: if present, caller should delete then re-insert.
     *
     * @param documentId The document_id to look for (e.g. from filename+hash or metadata)
     * @return true if at least one chunk has this document_id
     */
    boolean hasDocumentWithId(String documentId);
}

package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.model.Minute;

/**
 * Repository for minute documents in the knowledge base.
 * Ensures no duplicate documents are inserted (by document id).
 */
public interface MinuteDocumentRepository {

    /**
     * Adds a minute to the knowledge base. If a document with the same id already exists,
     * does not insert and returns ALREADY_EXISTS.
     *
     * @param minute the minute to add (must have a non-blank id)
     * @return ADDED if inserted, ALREADY_EXISTS if a document with this id already exists
     */
    AddResult addMinute(Minute minute);

    /**
     * Deletes all chunks and document entries for the given document id.
     *
     * @param id document id
     * @return number of chunks deleted
     */
    int deleteById(String id);

    /**
     * Checks whether any document with the given id exists in the knowledge base.
     *
     * @param id document id
     * @return true if at least one chunk has this document_id
     */
    boolean hasDocumentWithId(String id);
}

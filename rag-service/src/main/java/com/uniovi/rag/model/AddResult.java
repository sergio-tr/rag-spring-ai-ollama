package com.uniovi.rag.model;

/**
 * Result of attempting to add a minute document to the knowledge base.
 */
public enum AddResult {
    /** Document was added successfully. */
    ADDED,
    /** A document with the same id already exists; nothing was inserted. */
    ALREADY_EXISTS
}

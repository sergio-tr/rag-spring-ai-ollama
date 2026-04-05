package com.uniovi.rag.application.model;

/**
 * Wrapper for a candidate response text (and optional source) to be ranked.
 */
public record CandidateResponse(String text, String source) {

    public static CandidateResponse of(String text) {
        return new CandidateResponse(text, null);
    }

    public static CandidateResponse of(String text, String source) {
        return new CandidateResponse(text, source);
    }
}

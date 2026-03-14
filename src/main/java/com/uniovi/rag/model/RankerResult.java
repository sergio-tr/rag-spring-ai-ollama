package com.uniovi.rag.model;

import java.util.List;

/**
 * Result of selecting the best response from a list of candidates.
 */
public record RankerResult(String chosenText, int chosenIndex, List<Double> scoresPerCandidate) {

    public static RankerResult of(String chosenText, int chosenIndex) {
        return new RankerResult(chosenText, chosenIndex, null);
    }

    public static RankerResult of(String chosenText, int chosenIndex, List<Double> scores) {
        return new RankerResult(chosenText, chosenIndex, scores);
    }
}

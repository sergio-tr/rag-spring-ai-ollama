package com.uniovi.rag.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cluster of similar explanations
 */
public class ExplanationCluster {
    private final List<Explanation> explanations = new ArrayList<>();

    public ExplanationCluster(Explanation initialExplanation) {
        explanations.add(initialExplanation);
    }

    public void addExplanation(Explanation explanation) {
        explanations.add(explanation);
    }

    public Explanation getRepresentativeExplanation() {
        // Return the explanation with highest relevance score
        return explanations.stream()
                .max((a, b) -> Double.compare(a.getRelevanceScore(), b.getRelevanceScore()))
                .orElse(explanations.get(0));
    }

    public String getRepresentativeContent() {
        return getRepresentativeExplanation().getContent();
    }
}


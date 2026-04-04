package com.uniovi.rag.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cluster of similar decisions
 */
public class DecisionCluster {
    private final List<Decision> decisions = new ArrayList<>();

    public DecisionCluster(Decision initialDecision) {
        decisions.add(initialDecision);
    }

    public void addDecision(Decision decision) {
        decisions.add(decision);
    }

    public Decision getRepresentativeDecision() {
        // Return the longest decision text as representative
        return decisions.stream()
                .max((a, b) -> Integer.compare(
                        a.getDecisionText() != null ? a.getDecisionText().length() : 0,
                        b.getDecisionText() != null ? b.getDecisionText().length() : 0))
                .orElse(decisions.get(0));
    }

    public String getRepresentativeContent() {
        return getRepresentativeDecision().getDecisionText();
    }
}


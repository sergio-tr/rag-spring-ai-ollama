package com.uniovi.rag.service.extraction;

import com.uniovi.rag.model.Cluster;

import java.util.List;
import java.util.function.Function;

/**
 * Extracts structured data and fragments from document/minute content.
 * Replaces static utility methods with a single injectable service.
 */
public interface DocumentContentExtractor {

    String extractDate(String content);

    String extractRelevantFragment(String content, String query);

    String extractTime(String content, String type);

    int extractAttendeeCount(String content);

    int calculateDuration(String content);

    String extractLiteralField(String field, String content);

    List<String> extractAttendees(String content);

    String extractAgenda(String content);

    int countProposals(String content);

    int countAgendaItems(String content);

    int countQuestions(String content);

    boolean containsAnyKeyword(String text, String[] keywords);

    <T> List<Cluster<T>> clusterItems(List<T> items,
                                        Function<T, String> contentExtractor,
                                        Function<T, String> typeExtractor,
                                        double similarityThreshold);
}

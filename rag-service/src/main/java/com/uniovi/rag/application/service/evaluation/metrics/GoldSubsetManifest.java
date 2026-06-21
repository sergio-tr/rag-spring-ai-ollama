package com.uniovi.rag.application.service.evaluation.metrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;

/** Versioned benchmark subset manifest for targeted evaluation runs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldSubsetManifest(
        String manifestVersion,
        String manifestId,
        String datasetId,
        String labelledDatasetSha256,
        String answerabilityRulesVersion,
        List<Entry> entries) {

    public GoldSubsetManifest {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String datasetQuestionId,
            String question,
            String queryTypeExpected,
            String answerability,
            String expectedAnswer,
            String errorCategory,
            String inclusionReason) {}

    public List<String> questionIds() {
        return entries.stream().map(Entry::datasetQuestionId).filter(Objects::nonNull).toList();
    }
}

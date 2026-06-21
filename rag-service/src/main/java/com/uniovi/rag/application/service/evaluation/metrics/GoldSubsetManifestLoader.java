package com.uniovi.rag.application.service.evaluation.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Loads and validates classpath gold subset manifests against workbook rows. */
public final class GoldSubsetManifestLoader {

    public static final String GOLD_SUBSET_V1 = "gold-subset-v1";
    public static final String CLASSPATH_TEMPLATE = "evaluation/%s.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoldSubsetManifestLoader() {}

    public static GoldSubsetManifest load(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "goldSubsetManifestId is required");
        }
        String id = manifestId.trim();
        String location = String.format(CLASSPATH_TEMPLATE, id);
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown gold subset manifest: " + id);
        }
        try (InputStream in = resource.getInputStream()) {
            GoldSubsetManifest manifest = MAPPER.readValue(in, GoldSubsetManifest.class);
            if (manifest.entries() == null || manifest.entries().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gold subset manifest has no entries");
            }
            return manifest;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to load gold subset manifest: " + id);
        }
    }

    public static void validateAgainstWorkbook(GoldSubsetManifest manifest, List<RagPresetQuestion> workbookQuestions) {
        if (manifest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gold subset manifest is required");
        }
        Map<String, RagPresetQuestion> byId =
                workbookQuestions.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(RagPresetQuestion::id, q -> q, (a, b) -> a, LinkedHashMap::new));
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            RagPresetQuestion question = byId.get(entry.datasetQuestionId());
            if (question == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Gold subset entry not found in workbook: " + entry.datasetQuestionId());
            }
            if (entry.question() != null
                    && !entry.question().isBlank()
                    && !entry.question().trim().equals(question.question().trim())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Gold subset question text mismatch for " + entry.datasetQuestionId());
            }
            String expectedType = question.queryType().map(Enum::name).orElse("");
            if (entry.queryTypeExpected() != null
                    && !entry.queryTypeExpected().isBlank()
                    && !entry.queryTypeExpected().trim().equals(expectedType)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Gold subset query type mismatch for " + entry.datasetQuestionId());
            }
        }
    }

    public static GoldSubsetManifest buildFromWorkbook(
            String manifestId,
            String manifestVersion,
            String datasetId,
            String labelledDatasetSha256,
            List<GoldSubsetSpec> specs,
            List<RagPresetQuestion> workbookQuestions) {
        Map<String, RagPresetQuestion> byId =
                workbookQuestions.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(RagPresetQuestion::id, q -> q, (a, b) -> a, LinkedHashMap::new));
        List<GoldSubsetManifest.Entry> entries = new ArrayList<>();
        for (GoldSubsetSpec spec : specs) {
            RagPresetQuestion question = byId.get(spec.datasetQuestionId());
            if (question == null) {
                throw new IllegalArgumentException("Unknown workbook question: " + spec.datasetQuestionId());
            }
            AnswerabilityLabelResult label = AnswerabilityLabelingService.label(question);
            entries.add(
                    new GoldSubsetManifest.Entry(
                            question.id(),
                            question.question(),
                            question.queryType().map(Enum::name).orElse(""),
                            label.label().name(),
                            question.expectedAnswer(),
                            spec.errorCategory(),
                            spec.inclusionReason()));
        }
        return new GoldSubsetManifest(
                manifestVersion,
                manifestId,
                datasetId,
                labelledDatasetSha256,
                AnswerabilityLabelingService.rulesVersion(),
                List.copyOf(entries));
    }

    public record GoldSubsetSpec(String datasetQuestionId, String errorCategory, String inclusionReason) {}

    public static Set<String> requiredCategories() {
        return Set.of(
                "negative-evidence",
                "count-comparison",
                "date-duration",
                "boolean",
                "entity-topic-lookup",
                "summarization-explanation");
    }

    public static void assertCategoryCoverage(GoldSubsetManifest manifest) {
        Set<String> present =
                manifest.entries().stream()
                        .map(GoldSubsetManifest.Entry::errorCategory)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .collect(Collectors.toSet());
        for (String required : requiredCategories()) {
            if (!present.contains(required)) {
                throw new IllegalStateException("Gold subset missing category: " + required);
            }
        }
    }
}

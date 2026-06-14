package com.uniovi.rag.application.service.evaluation.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoldSubsetManifestLoaderTest {

    private static final List<GoldSubsetManifestLoader.GoldSubsetSpec> SPECS =
            List.of(
                    spec("RAG-003", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-004", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-005", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-010", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-019", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-030", "negative-evidence", "negative_evidence_absence"),
                    spec("RAG-001", "count-comparison", "count_comparison_accuracy"),
                    spec("RAG-002", "count-comparison", "count_comparison_accuracy"),
                    spec("RAG-008", "count-comparison", "count_comparison_accuracy"),
                    spec("RAG-029", "date-duration", "date_duration_accuracy"),
                    spec("RAG-059", "date-duration", "date_duration_accuracy"),
                    spec("RAG-032", "boolean", "boolean_accuracy"),
                    spec("RAG-034", "boolean", "boolean_accuracy"),
                    spec("RAG-012", "entity-topic-lookup", "entity_topic_lookup"),
                    spec("RAG-016", "entity-topic-lookup", "entity_topic_lookup"),
                    spec("RAG-058", "entity-topic-lookup", "entity_topic_lookup"),
                    spec("RAG-009", "summarization-explanation", "summarization_quality"),
                    spec("RAG-046", "summarization-explanation", "summarization_quality"));

    @Test
    void loadsClasspathManifestAndValidatesWorkbook() {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        List<RagPresetQuestion> questions = loader.getSnapshot().workbook().ragPresetQuestionsEnriched();

        GoldSubsetManifest manifest = GoldSubsetManifestLoader.load(GoldSubsetManifestLoader.GOLD_SUBSET_V1);
        GoldSubsetManifestLoader.validateAgainstWorkbook(manifest, questions);
        GoldSubsetManifestLoader.assertCategoryCoverage(manifest);

        assertThat(manifest.entries()).hasSize(18);
    }

    @Test
    void buildManifestFromWorkbook_matchesSpecs() {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        List<RagPresetQuestion> questions = loader.getSnapshot().workbook().ragPresetQuestionsEnriched();
        String sha = loader.getSnapshot().sha256Hex().orElse("");

        GoldSubsetManifest manifest =
                GoldSubsetManifestLoader.buildFromWorkbook(
                        GoldSubsetManifestLoader.GOLD_SUBSET_V1,
                        "1",
                        "00000000-0000-7000-8000-000000000001",
                        sha,
                        SPECS,
                        questions);

        GoldSubsetManifestLoader.assertCategoryCoverage(manifest);
        assertThat(manifest.entries()).hasSize(18);
        assertThat(manifest.answerabilityRulesVersion()).isEqualTo(AnswerabilityLabelingService.rulesVersion());
    }

    @Test
    void writeManifestJsonToTarget() throws Exception {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        List<RagPresetQuestion> questions = loader.getSnapshot().workbook().ragPresetQuestionsEnriched();
        String sha = loader.getSnapshot().sha256Hex().orElse("");
        GoldSubsetManifest manifest =
                GoldSubsetManifestLoader.buildFromWorkbook(
                        GoldSubsetManifestLoader.GOLD_SUBSET_V1,
                        "1",
                        "00000000-0000-7000-8000-000000000001",
                        sha,
                        SPECS,
                        questions);
        Path out = Path.of("target/generated-gold-subset-v1.json");
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), manifest);
        assertThat(out).exists();
    }

    private static GoldSubsetManifestLoader.GoldSubsetSpec spec(
            String id, String category, String reason) {
        return new GoldSubsetManifestLoader.GoldSubsetSpec(id, category, reason);
    }
}

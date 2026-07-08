package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceBundleClasspathResourceTest {

    @Test
    void classpathResource_exists_andParsesWithoutErrors() throws Exception {
        ClassPathResource r = new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);
        assertThat(r.exists()).isTrue();

        try (InputStream in = r.getInputStream()) {
            EvaluationWorkbookParser parser = new EvaluationWorkbookParser();
            var parsed = parser.parse(in, ExperimentalDatasetType.REFERENCE_BUNDLE);
            assertThat(parsed.validationReport().hasErrors()).isFalse();
            assertThat(parsed.workbook().sheetNamesPresent())
                    .contains(
                            WorkbookSheetNames.README,
                            WorkbookSheetNames.CORPUS_DOCUMENTS,
                            WorkbookSheetNames.CHUNK_REGISTRY,
                            WorkbookSheetNames.LLM_READER_QUESTIONS,
                            WorkbookSheetNames.LLM_ROLE_EVAL_CASES,
                            WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES,
                            WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                            WorkbookSheetNames.LLM_CANDIDATES,
                            WorkbookSheetNames.EMBEDDING_CANDIDATES,
                            WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                            WorkbookSheetNames.METRIC_SPEC,
                            WorkbookSheetNames.RESULT_SCHEMA,
                            WorkbookSheetNames.SUMMARY_COUNTS);

            // Canonical dataset row-count expectations (guards against accidental demo bundle regressions).
            assertThat(parsed.workbook().llmReaderQuestions()).hasSize(36);
            assertThat(parsed.workbook().llmRoleEvalCases()).hasSize(64);
            assertThat(parsed.workbook().embeddingRetrievalQueries()).hasSize(60);
            assertThat(parsed.workbook().ragPresetQuestionsEnriched()).hasSize(60);
            assertThat(parsed.workbook().chunkRegistry()).hasSize(30);
            assertThat(parsed.workbook().corpusDocuments()).hasSize(5);
            assertThat(parsed.workbook().ragPresetCatalog()).hasSize(15);

            // RAG questions must carry dataset dimensions (no blank query_type/difficulty in the canonical bundle).
            assertThat(parsed.workbook().ragPresetQuestionsEnriched())
                    .allSatisfy(q -> {
                        assertThat(q.queryType()).isPresent();
                        assertThat(q.difficulty()).isPresent();
                    });

            // Guardrail: ensure stable question ids are the canonical RAG-001..RAG-060 (no demo ids).
            assertThat(parsed.workbook().ragPresetQuestionsEnriched())
                    .hasSize(60)
                    .allSatisfy(q -> assertThat(q.id()).startsWith("RAG-"));
        }
    }

    @Test
    void classpathResource_doesNotContainDemoStrings() throws Exception {
        ClassPathResource r = new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);
        assertThat(r.exists()).isTrue();
        byte[] bytes;
        try (InputStream in = r.getInputStream()) {
            bytes = in.readAllBytes();
        }
        // XLSX is a zip; sharedStrings.xml typically embeds plaintext. A raw byte scan is sufficient as a guardrail.
        String haystack = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(haystack).doesNotContain("RAG_Q1");
        assertThat(haystack).doesNotContain("What does the sample acta contain?");
        assertThat(haystack).doesNotContain("Evidence: Acta sample 1");
    }
}


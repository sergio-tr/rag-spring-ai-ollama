package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

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
                            WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES,
                            WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                            WorkbookSheetNames.LLM_CANDIDATES,
                            WorkbookSheetNames.EMBEDDING_CANDIDATES,
                            WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                            WorkbookSheetNames.METRIC_SPEC,
                            WorkbookSheetNames.RESULT_SCHEMA,
                            WorkbookSheetNames.SUMMARY_COUNTS);
        }
    }
}


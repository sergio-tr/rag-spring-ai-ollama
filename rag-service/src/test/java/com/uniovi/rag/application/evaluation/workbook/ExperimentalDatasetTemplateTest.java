package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentalDatasetTemplateTest {

    @Test
    void eachTemplateKind_producesNonEmptyXlsx() throws Exception {
        for (ExperimentalDatasetType t :
                new ExperimentalDatasetType[] {
                    ExperimentalDatasetType.LLM_MODEL_BASELINE,
                    ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE,
                    ExperimentalDatasetType.RAG_PRESET_BENCHMARK,
                    ExperimentalDatasetType.CLASSIFIER_DATASET
                }) {
            byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(t);
            assertThat(bytes).isNotEmpty();
            assertThat(new String(bytes, 0, Math.min(bytes.length, 4))).startsWith("PK");
        }
    }
}

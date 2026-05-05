package com.uniovi.rag.application.evaluation.workbook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReferenceBundleLoaderTest {

    @Test
    void loadsClasspathBundle_resourcePresent_andValidated() {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        ReferenceBundleSnapshot snap = loader.getSnapshot();

        assertThat(snap.classpathResourcePresent()).isTrue();
        assertThat(snap.validationReport().hasErrors())
                .as("REFERENCE_BUNDLE validation should pass for shipped workbook")
                .isFalse();
        assertThat(snap.workbook().llmReaderQuestions()).isNotEmpty();
        assertThat(snap.counts().llmReaderQuestions()).isGreaterThan(0);
        assertThat(snap.countsByDatasetKind()).containsKeys("embeddingRetrievalQueries", "ragPresetQuestions", "presets");
        assertThat(snap.sha256Hex()).isPresent();
        assertThat(snap.byteSize()).isGreaterThan(0);
    }

    @Test
    void snapshot_isCached() {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        ReferenceBundleSnapshot a = loader.getSnapshot();
        ReferenceBundleSnapshot b = loader.getSnapshot();
        assertThat(a).isSameAs(b);
    }
}

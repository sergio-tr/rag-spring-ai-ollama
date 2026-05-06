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
        assertThat(snap.counts().llmReaderQuestions()).isEqualTo(36);
        assertThat(snap.counts().embeddingRetrievalQueries()).isEqualTo(60);
        assertThat(snap.counts().ragPresetQuestions()).isEqualTo(60);
        assertThat(snap.counts().chunkRegistryEntries()).isEqualTo(30);
        assertThat(snap.counts().corpusDocuments()).isEqualTo(5);
        assertThat(snap.counts().presets()).isEqualTo(15);
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

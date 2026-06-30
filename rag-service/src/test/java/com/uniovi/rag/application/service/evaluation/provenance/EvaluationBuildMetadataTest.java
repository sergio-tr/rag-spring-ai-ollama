package com.uniovi.rag.application.service.evaluation.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvaluationBuildMetadataTest {

    @Test
    void explicitConstructor_preservesValues() {
        EvaluationBuildMetadata metadata = EvaluationBuildMetadata.of("sha1", "build-9", "staging");
        assertThat(metadata.gitSha()).isEqualTo("sha1");
        assertThat(metadata.buildId()).isEqualTo("build-9");
        assertThat(metadata.environmentLabel()).isEqualTo("staging");
        assertThat(metadata.asMap())
                .containsEntry(EvaluationProvenanceKeys.GIT_SHA, "sha1")
                .containsEntry(EvaluationProvenanceKeys.BUILD_ID, "build-9")
                .containsEntry(EvaluationProvenanceKeys.ENVIRONMENT_LABEL, "staging");
    }

    @Test
    void blankValuesBecomeUnknown() {
        EvaluationBuildMetadata metadata = EvaluationBuildMetadata.of("  ", null, "");
        assertThat(metadata.gitSha()).isEqualTo(EvaluationBuildMetadata.UNKNOWN);
        assertThat(metadata.buildId()).isEqualTo(EvaluationBuildMetadata.UNKNOWN);
        assertThat(metadata.environmentLabel()).isEqualTo(EvaluationBuildMetadata.UNKNOWN);
    }

    @Test
    void resolveGitSha_prefersConfiguredValue() {
        assertThat(EvaluationBuildMetadataProvider.resolveGitSha("from-config")).isEqualTo("from-config");
    }

    @Test
    void resolveGitSha_fallsBackToUnknown() {
        assertThat(EvaluationBuildMetadataProvider.resolveGitSha("")).isEqualTo(EvaluationBuildMetadata.UNKNOWN);
    }
}

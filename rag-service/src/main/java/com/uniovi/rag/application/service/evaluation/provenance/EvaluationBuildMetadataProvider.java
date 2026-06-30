package com.uniovi.rag.application.service.evaluation.provenance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves {@link EvaluationBuildMetadata} from env, properties, and optional {@code git.properties}. */
@Component
public class EvaluationBuildMetadataProvider {

    private final EvaluationBuildMetadata metadata;

    public EvaluationBuildMetadataProvider(
            @Value("${rag.evaluation.provenance.git-sha:}") String configuredGitSha,
            @Value("${rag.evaluation.provenance.build-id:}") String configuredBuildId,
            @Value("${rag.evaluation.provenance.environment-label:}") String configuredEnvironmentLabel) {
        this.metadata =
                EvaluationBuildMetadata.of(
                        resolveGitSha(configuredGitSha),
                        resolveBuildId(configuredBuildId),
                        resolveEnvironmentLabel(configuredEnvironmentLabel));
    }

    public EvaluationBuildMetadata metadata() {
        return metadata;
    }

    static String resolveGitSha(String configured) {
        return firstNonBlank(
                configured,
                System.getenv("GIT_SHA"),
                System.getenv("BUILD_GIT_SHA"),
                System.getenv("COMMIT_SHA"),
                System.getenv("RAG_EVALUATION_GIT_SHA"),
                EvaluationGitPropertiesReader.readProperty("git.commit.id.abbrev").orElse(null),
                EvaluationGitPropertiesReader.readProperty("git.commit.id").orElse(null),
                EvaluationBuildMetadata.UNKNOWN);
    }

    static String resolveBuildId(String configured) {
        return firstNonBlank(
                configured,
                System.getenv("BUILD_ID"),
                System.getenv("BUILD_NUMBER"),
                System.getenv("RAG_EVALUATION_BUILD_ID"),
                EvaluationGitPropertiesReader.readProperty("git.build.version").orElse(null),
                EvaluationBuildMetadata.UNKNOWN);
    }

    static String resolveEnvironmentLabel(String configured) {
        return firstNonBlank(
                configured,
                System.getenv("RAG_ENVIRONMENT"),
                System.getenv("ENVIRONMENT"),
                System.getenv("SPRING_PROFILES_ACTIVE"),
                EvaluationBuildMetadata.UNKNOWN);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return EvaluationBuildMetadata.UNKNOWN;
    }
}

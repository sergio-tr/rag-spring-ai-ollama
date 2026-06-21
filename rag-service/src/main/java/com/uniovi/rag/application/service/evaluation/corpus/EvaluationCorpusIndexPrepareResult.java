package com.uniovi.rag.application.service.evaluation.corpus;

import java.util.UUID;

/** Outcome of automatic or manual evaluation-corpus index preparation. */
public record EvaluationCorpusIndexPrepareResult(
        IndexBuildStatus status,
        UUID knowledgeIndexSnapshotId,
        UUID resolvedConfigSnapshotId,
        String resolvedConfigHash,
        String indexProfileHash,
        String reasonCode,
        String reasonMessage) {

    public enum IndexBuildStatus {
        REUSED,
        BUILT,
        FAILED
    }

    public static EvaluationCorpusIndexPrepareResult reused(
            UUID snapshotId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash,
            String indexProfileHash) {
        return new EvaluationCorpusIndexPrepareResult(
                IndexBuildStatus.REUSED,
                snapshotId,
                resolvedConfigSnapshotId,
                resolvedConfigHash,
                indexProfileHash,
                null,
                null);
    }

    public static EvaluationCorpusIndexPrepareResult built(
            UUID snapshotId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash,
            String indexProfileHash) {
        return new EvaluationCorpusIndexPrepareResult(
                IndexBuildStatus.BUILT,
                snapshotId,
                resolvedConfigSnapshotId,
                resolvedConfigHash,
                indexProfileHash,
                null,
                null);
    }

    public static EvaluationCorpusIndexPrepareResult failed(String reasonCode, String reasonMessage) {
        return new EvaluationCorpusIndexPrepareResult(
                IndexBuildStatus.FAILED, null, null, null, null, reasonCode, reasonMessage);
    }

    public boolean succeeded() {
        return status == IndexBuildStatus.REUSED || status == IndexBuildStatus.BUILT;
    }
}

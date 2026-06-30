package com.uniovi.rag.application.service.evaluation.corpus;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Encodes workbook gold document/chunk ids in evaluation-corpus filenames for indexing. */
public final class EvaluationGoldCorpusFilenameSupport {

    public static final String SUFFIX = ".eval-gold.txt";

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^(.+?)__(.+?)\\.eval-gold\\.txt$", Pattern.CASE_INSENSITIVE);

    private EvaluationGoldCorpusFilenameSupport() {}

    public record Parsed(String evaluationDocumentId, String evaluationChunkId) {}

    public static boolean isEvaluationGoldFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        return filename.trim().toLowerCase(Locale.ROOT).endsWith(SUFFIX);
    }

    public static String buildFilename(String evaluationDocumentId, String evaluationChunkId) {
        if (evaluationDocumentId == null || evaluationDocumentId.isBlank()) {
            throw new IllegalArgumentException("evaluationDocumentId required");
        }
        if (evaluationChunkId == null || evaluationChunkId.isBlank()) {
            throw new IllegalArgumentException("evaluationChunkId required");
        }
        return evaluationDocumentId.trim() + "__" + evaluationChunkId.trim() + SUFFIX;
    }

    public static Optional<Parsed> parse(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        var m = FILENAME_PATTERN.matcher(filename.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String docId = m.group(1).trim();
        String chunkId = m.group(2).trim();
        if (docId.isEmpty() || chunkId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(docId, chunkId));
    }
}

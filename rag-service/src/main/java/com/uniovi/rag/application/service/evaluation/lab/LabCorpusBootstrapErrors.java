package com.uniovi.rag.application.service.evaluation.lab;

/** Stable machine codes for Lab classpath corpus bootstrap failures. */
public final class LabCorpusBootstrapErrors {

    public static final String REQUIRES_PROJECT = "LAB_CORPUS_BOOTSTRAP_REQUIRES_PROJECT";
    public static final String UNSUPPORTED_BENCHMARK_KIND = "LAB_CORPUS_BOOTSTRAP_UNSUPPORTED_BENCHMARK_KIND";
    public static final String UNSUPPORTED_CORPUS_SCOPE = "LAB_CORPUS_BOOTSTRAP_UNSUPPORTED_CORPUS_SCOPE";
    public static final String NO_DOCUMENTS = "LAB_CORPUS_BOOTSTRAP_NO_DOCUMENTS";
    public static final String NO_READY_DOCUMENTS = "LAB_CORPUS_BOOTSTRAP_NO_READY_DOCUMENTS";
    public static final String MISSING_STORAGE_URI = "LAB_CORPUS_BOOTSTRAP_MISSING_STORAGE_URI";

    private LabCorpusBootstrapErrors() {}

    /**
     * User-oriented message when the classpath scan finds no acceptable corpus files (PDF, text, markdown, HTML).
     */
    public static String formatNoDocumentsMatched(String pattern) {
        return NO_DOCUMENTS
                + ": No matching corpus files were found. Add PDF actas or text documents under rag-service/src/main/resources/docs/"
                + " (they are packaged into the JAR and visible to classpath*:docs/**), or set classpathDocsLocation on the benchmark request."
                + " Pattern used: "
                + pattern;
    }

    /**
     * When resources matched the glob but none looked like ingestible corpus filenames (after filtering noise files).
     */
    public static String formatOnlyNonCorpusResourcesMatched(String pattern, int rawResourceCount) {
        return NO_DOCUMENTS
                + ": The classpath scan matched "
                + rawResourceCount
                + " resource(s), but none had a supported corpus extension (.pdf, .txt, .md, .html, .htm). "
                + "Pattern used: "
                + pattern;
    }

    /** Extracts the {@code LAB_CORPUS_BOOTSTRAP_*} prefix from {@link IllegalStateException#getMessage()} payloads. */
    public static String reasonCodeFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return "LAB_CORPUS_BOOTSTRAP_UNKNOWN";
        }
        int colon = message.indexOf(':');
        String head = colon > 0 ? message.substring(0, colon).trim() : message.trim();
        return head.startsWith("LAB_CORPUS_BOOTSTRAP_") ? head : "LAB_CORPUS_BOOTSTRAP_UNKNOWN";
    }
}


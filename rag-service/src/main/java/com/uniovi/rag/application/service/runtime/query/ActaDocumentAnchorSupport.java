package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves Spanish acta document number anchors (with or without {@code .pdf}) for retrieval targeting. */
public final class ActaDocumentAnchorSupport {

    private static final int UNICODE_CANON = Pattern.UNICODE_CASE | Pattern.CANON_EQ;

    private static final Pattern ACTA_NUMBER_WITH_PDF =
            Pattern.compile("(?i)\\bacta\\s*(\\d+)\\.pdf\\b", UNICODE_CANON);
    private static final Pattern ACTA_NUMBER_GENERIC =
            Pattern.compile(
                    "(?i)\\b(?:el\\s+|la\\s+)?acta\\s+(?:n[uú]m(?:ero)?\\.?|n[°ºo]\\.?|#)?\\s*(\\d+)\\b",
                    UNICODE_CANON);
    private static final Pattern NORMALIZE_FILENAME =
            Pattern.compile("(?i)ACTA\\s*(\\d+)(?:\\.pdf)?", UNICODE_CANON);

    private ActaDocumentAnchorSupport() {}

    public static Optional<Integer> resolveActaNumber(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.trim();
        Integer fromPdf = firstGroupInt(ACTA_NUMBER_WITH_PDF.matcher(q));
        if (fromPdf != null) {
            return Optional.of(fromPdf);
        }
        Integer fromGeneric = firstGroupInt(ACTA_NUMBER_GENERIC.matcher(q));
        return fromGeneric != null ? Optional.of(fromGeneric) : Optional.empty();
    }

    public static String canonicalFilename(int actaNumber) {
        return "ACTA " + actaNumber + ".pdf";
    }

    public static boolean hasExplicitActaDocumentReference(String normalizedText) {
        return resolveActaNumber(normalizedText).isPresent();
    }

    public static boolean candidateMatchesActaNumber(RetrievalCandidate candidate, int actaNumber) {
        if (candidate == null || actaNumber <= 0) {
            return false;
        }
        String expected = canonicalFilename(actaNumber).toLowerCase(Locale.ROOT);
        String filename = filename(candidate);
        if (!filename.isBlank() && normalizeFilename(filename).equalsIgnoreCase(expected)) {
            return true;
        }
        String content = candidate.content() != null ? candidate.content() : "";
        return content.toLowerCase(Locale.ROOT).contains(expected.replace(".pdf", ""));
    }

    /**
     * When an acta number anchor is present, keep only candidates from that document when any match exists;
     * otherwise preserve the input ordering.
     */
    public static List<RetrievalCandidate> preferActaAnchored(List<RetrievalCandidate> candidates, int actaNumber) {
        if (candidates == null || candidates.isEmpty() || actaNumber <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        List<RetrievalCandidate> exact = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            if (candidateMatchesActaNumber(c, actaNumber)) {
                exact.add(c);
            }
        }
        if (!exact.isEmpty()) {
            return List.copyOf(exact);
        }
        return candidates;
    }

    public static Set<String> extractActaFilenamesFromText(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Matcher pdfMatcher = ACTA_NUMBER_WITH_PDF.matcher(text);
        while (pdfMatcher.find()) {
            out.add(normalizeFilename(pdfMatcher.group(0)));
        }
        Matcher genericMatcher = ACTA_NUMBER_GENERIC.matcher(text);
        while (genericMatcher.find()) {
            try {
                int n = Integer.parseInt(genericMatcher.group(1));
                if (n > 0) {
                    out.add(canonicalFilename(n));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed capture
            }
        }
        return Set.copyOf(out);
    }

    private static String filename(RetrievalCandidate candidate) {
        if (candidate == null || candidate.metadata() == null) {
            return "";
        }
        Object raw = candidate.metadata().get("filename");
        return raw != null ? String.valueOf(raw).trim() : "";
    }

    private static String normalizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher matcher = NORMALIZE_FILENAME.matcher(raw.trim());
        if (matcher.find()) {
            return canonicalFilename(Integer.parseInt(matcher.group(1)));
        }
        return raw.trim();
    }

    private static Integer firstGroupInt(Matcher matcher) {
        if (matcher.find()) {
            try {
                int n = Integer.parseInt(matcher.group(1));
                return n > 0 ? n : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

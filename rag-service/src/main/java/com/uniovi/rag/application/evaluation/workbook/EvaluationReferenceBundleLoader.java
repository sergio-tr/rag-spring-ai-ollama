package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ReadmeEntry;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Loads the packaged thesis/reference workbook from the classpath, parses it with {@link EvaluationWorkbookParser},
 * and exposes a cached {@link ReferenceBundleSnapshot} (validation report + counts).
 */
@Component
public class EvaluationReferenceBundleLoader {

    /** Classpath location of the internal reference bundle (Phase 0 canonical artifact). */
    public static final String CLASSPATH_LOCATION = "evaluation/rag_experiment_datasets_and_protocols.xlsx";

    private static final Pattern PROTOCOL_VERSION_ITEM = Pattern.compile(".*protocol.*version.*", Pattern.CASE_INSENSITIVE);

    private final EvaluationWorkbookParser parser;
    private volatile ReferenceBundleSnapshot cache;

    public EvaluationReferenceBundleLoader(EvaluationWorkbookParser parser) {
        this.parser = parser;
    }

    /**
     * Returns a lazily cached snapshot. Safe for concurrent calls after first load.
     */
    public ReferenceBundleSnapshot getSnapshot() {
        ReferenceBundleSnapshot s = cache;
        if (s != null) {
            return s;
        }
        synchronized (this) {
            if (cache != null) {
                return cache;
            }
            cache = loadFresh();
            return cache;
        }
    }

    private ReferenceBundleSnapshot loadFresh() {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_LOCATION);
        if (!resource.exists()) {
            return ReferenceBundleSnapshot.classpathMissing();
        }
        try (InputStream in = resource.getInputStream()) {
            WorkbookParseResult result = parser.parse(in, ExperimentalDatasetType.REFERENCE_BUNDLE);
            EvaluationWorkbook wb = result.workbook();
            ReferenceBundleCounts counts = ReferenceBundleCounts.fromWorkbook(wb);
            Optional<String> protocolVersion = extractProtocolVersion(wb);
            return new ReferenceBundleSnapshot(true, wb, result.validationReport(), counts, protocolVersion);
        } catch (IOException e) {
            return ReferenceBundleSnapshot.loadFailedIo(e.getMessage());
        }
    }

    /**
     * Looks for a README row whose Item mentions protocol version, or an Item exactly {@code Protocol version} /
     * {@code Version}; Decision holds the value.
     */
    static Optional<String> extractProtocolVersion(EvaluationWorkbook wb) {
        for (ReadmeEntry e : wb.readme()) {
            String item = e.item() != null ? e.item().trim() : "";
            if (item.isEmpty()) {
                continue;
            }
            boolean candidate =
                    PROTOCOL_VERSION_ITEM.matcher(item).matches()
                            || item.equalsIgnoreCase("Protocol version")
                            || item.equalsIgnoreCase("Version");
            if (!candidate) {
                continue;
            }
            String decision = e.decision() != null ? e.decision().trim() : "";
            if (!decision.isEmpty()) {
                return Optional.of(decision);
            }
        }
        return Optional.empty();
    }
}

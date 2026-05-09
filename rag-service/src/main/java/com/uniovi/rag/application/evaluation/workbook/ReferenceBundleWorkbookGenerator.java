package com.uniovi.rag.application.evaluation.workbook;

import java.io.IOException;

/**
 * Deprecated utility that used to generate a minimal demo reference workbook at build time.
 *
 * <p>The canonical reference bundle is shipped as a classpath resource at
 * {@link EvaluationReferenceBundleLoader#CLASSPATH_LOCATION}. The Lab must never run on a 1-row demo bundle.
 */
public final class ReferenceBundleWorkbookGenerator {

    public static void main(String[] args) throws IOException {
        throw new UnsupportedOperationException(
                "Reference bundle XLSX generation was removed. "
                        + "Ship the canonical bundle as a classpath resource at "
                        + EvaluationReferenceBundleLoader.CLASSPATH_LOCATION
                        + " and validate it via /lab/status.");
    }
}


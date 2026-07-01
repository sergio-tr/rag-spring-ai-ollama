package com.uniovi.rag.tool.metadata;

/** Runtime gate for full-corpus metadata scan on small acta corpora. */
public final class MetadataCorpusScanSettings {

    private static volatile int fullScanMaxDocuments = 30;

    private MetadataCorpusScanSettings() {}

    public static int fullScanMaxDocuments() {
        return fullScanMaxDocuments;
    }

    public static void setFullScanMaxDocuments(int max) {
        fullScanMaxDocuments = max > 0 ? max : 30;
    }
}

package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GDPR-style account export storage and TTL (see {@code /me/account/export}).
 */
@ConfigurationProperties(prefix = "rag.account")
@Validated
public class RagAccountProperties {

    /**
     * Directory where export ZIP files are written (per-user subfolders). Bound from {@code rag.account.export-storage-dir}.
     */
    private String exportStorageDir = Paths.get(System.getProperty("java.io.tmpdir"), "rag-account-export").toString();

    /** Hours until a READY export artifact expires and may be purged. */
    private int exportTtlHours = 24;

    public Path getExportStorageDir() {
        return Paths.get(exportStorageDir);
    }

    public void setExportStorageDir(String exportStorageDir) {
        this.exportStorageDir = exportStorageDir;
    }

    public int getExportTtlHours() {
        return exportTtlHours;
    }

    public void setExportTtlHours(int exportTtlHours) {
        this.exportTtlHours = exportTtlHours;
    }
}

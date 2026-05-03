package com.uniovi.rag.infrastructure.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipIoGuardsTest {

    @Test
    void readStoredEntryBytes_honoursDeclaredLengthAndBudget() throws IOException {
        byte[] m = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] r = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] zip = zipStoredTwo("manifest.json", m, "run.json", r);

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipExpansionBudget budget = ZipExpansionBudget.forUploadedZip(64 * 1024);
            ZipEntry e1 = zin.getNextEntry();
            byte[] out1 = ZipIoGuards.readStoredEntryBytes(zin, e1, 4096, budget);
            assertThat(out1).isEqualTo(m);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            byte[] out2 = ZipIoGuards.readStoredEntryBytes(zin, e2, 4096, budget);
            assertThat(out2).isEqualTo(r);
        }
    }

    @Test
    void readStoredEntryBytes_rejectsEntryLargerThanPerEntryCap() throws IOException {
        byte[] m = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] zip = zipStoredTwo("manifest.json", m, "run.json", new byte[] {1, 2, 3});

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipExpansionBudget budget = ZipExpansionBudget.forUploadedZip(64 * 1024);
            ZipEntry e1 = zin.getNextEntry();
            ZipIoGuards.readStoredEntryBytes(zin, e1, 4096, budget);
            zin.closeEntry();

            ZipEntry e2 = zin.getNextEntry();
            assertThatThrownBy(() -> ZipIoGuards.readStoredEntryBytes(zin, e2, 2, budget))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("too large");
        }
    }

    private static byte[] zipStoredTwo(String n1, byte[] c1, String n2, byte[] c2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(c1.length + c2.length + 256);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos), StandardCharsets.UTF_8)) {
            putStored(zos, n1, c1);
            putStored(zos, n2, c2);
        }
        return baos.toByteArray();
    }

    private static void putStored(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

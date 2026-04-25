package com.uniovi.rag.application.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises nested port records so JaCoCo counts them when port interfaces are included in the bundle.
 */
class ApplicationPortRecordsCoverageTest {

    @Test
    void binaryStoragePort_storedObject_holdsFields() {
        BinaryStoragePort.StoredObject o = new BinaryStoragePort.StoredObject("a/b", "deadbeef");
        assertEquals("a/b", o.relativeUri());
        assertEquals("deadbeef", o.sha256Hex());
    }

    @Test
    void evaluationDatasetStorePort_storedDataset_holdsFields() {
        EvaluationDatasetStorePort.StoredDataset d =
                new EvaluationDatasetStorePort.StoredDataset("u/1", "abc", 99L);
        assertEquals("u/1", d.storageUri());
        assertEquals("abc", d.sha256Hex());
        assertEquals(99L, d.byteSize());
    }
}

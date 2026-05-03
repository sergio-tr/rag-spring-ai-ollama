package com.uniovi.rag.infrastructure.persistence.jpa;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
class JpaEmbeddableAndKeyTest {

    @Test
    void ragPresetProfileRefId_equalsHashCodeAndRejectsWrongType() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        RagPresetProfileRefId x = new RagPresetProfileRefId(a, b);
        RagPresetProfileRefId y = new RagPresetProfileRefId(a, b);
        assertEquals(x, y);
        assertEquals(x.hashCode(), y.hashCode());
        assertNotEquals(x, new RagPresetProfileRefId(a, UUID.randomUUID()));
        assertNotEquals(x, "other");
        assertNotEquals(x, null);
    }

    @Test
    void knowledgeSnapshotDocumentPk_settersEqualsAndHashCode() {
        UUID s = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        KnowledgeSnapshotDocumentPk pk = new KnowledgeSnapshotDocumentPk(s, d);
        assertEquals(s, pk.getSnapshotId());
        assertEquals(d, pk.getDocumentId());
        UUID s2 = UUID.randomUUID();
        pk.setSnapshotId(s2);
        assertEquals(s2, pk.getSnapshotId());
        KnowledgeSnapshotDocumentPk other = new KnowledgeSnapshotDocumentPk(s2, d);
        assertEquals(pk, other);
        assertEquals(pk.hashCode(), other.hashCode());
        assertNotEquals(pk, new KnowledgeSnapshotDocumentPk(UUID.randomUUID(), d));
        assertNotEquals(pk, new Object());
    }
}

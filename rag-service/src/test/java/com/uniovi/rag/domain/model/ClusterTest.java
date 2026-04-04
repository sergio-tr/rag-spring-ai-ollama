package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClusterTest {

    @Test
    void initialItemAndAddItem() {
        Cluster<String> c = new Cluster<>("first");
        assertEquals(1, c.getSize());
        assertEquals("first", c.getRepresentativeItem());
        c.addItem("second");
        assertEquals(2, c.getSize());
        assertEquals("first", c.getRepresentativeItem());
        assertEquals(List.of("first", "second"), c.getItems());
    }

    @Test
    void clusterToString_containsRepresentative() {
        Cluster<String> c = new Cluster<>("rep");
        assertTrue(c.toString().contains("Cluster") && c.toString().contains("rep"));
    }
}

package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityClusterTest {

    @Test
    void representativeIsLongestName() {
        Entity e1 = new Entity("Ana", EntityType.PERSON, EntityRole.ATTENDEE);
        Entity e2 = new Entity("Juan Carlos García López", EntityType.PERSON, EntityRole.PRESIDENT);
        EntityCluster c = new EntityCluster(e1);
        c.addEntity(e2);
        assertEquals(e2, c.getRepresentativeEntity());
    }

    @Test
    void singleEntity() {
        Entity e = new Entity("Solo", EntityType.ORGANIZATION, EntityRole.UNKNOWN);
        EntityCluster c = new EntityCluster(e);
        assertEquals(e, c.getRepresentativeEntity());
    }
}

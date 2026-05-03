package com.uniovi.rag.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

/**
 * Test-only factory: same package as {@link ProjectEntity} to access its constructor.
 */
public final class ProjectEntityTestFactory {

    private ProjectEntityTestFactory() {
    }

    public static ProjectEntity project(UUID id, String name, String description) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setName(name);
        p.setDescription(description);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}

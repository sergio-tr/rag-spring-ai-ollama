package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.RagConfigurationLevel;

import java.time.Instant;
import java.util.Map;

/**
 * Constructs {@link RagConfigurationEntity} rows for user-default and project-scoped configuration.
 */
public final class RagConfigurationEntityFactory {

    private RagConfigurationEntityFactory() {
    }

    public static RagConfigurationEntity newUserDefault(
            UserEntity user, Map<String, Object> values, Instant now) {
        RagConfigurationEntity e = new RagConfigurationEntity();
        e.setUser(user);
        e.setProject(null);
        e.setLevel(RagConfigurationLevel.USER_DEFAULT);
        e.setName("user-default");
        e.setValues(values);
        e.setActive(true);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    public static RagConfigurationEntity newProjectScoped(
            UserEntity user, ProjectEntity project, Map<String, Object> values, Instant now) {
        RagConfigurationEntity e = new RagConfigurationEntity();
        e.setUser(user);
        e.setProject(project);
        e.setLevel(RagConfigurationLevel.PROJECT);
        e.setName("project");
        e.setValues(values);
        e.setActive(true);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}

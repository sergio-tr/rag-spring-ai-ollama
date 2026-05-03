package com.uniovi.rag.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Map;

public final class DefaultSystemConfigurationEntityFactory {

    private DefaultSystemConfigurationEntityFactory() {
    }

    public static DefaultSystemConfigurationEntity emptyRow() {
        DefaultSystemConfigurationEntity e = new DefaultSystemConfigurationEntity();
        e.setValues(Map.of());
        e.setUpdatedAt(Instant.now());
        return e;
    }
}

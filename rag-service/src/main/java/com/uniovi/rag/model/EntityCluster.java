package com.uniovi.rag.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cluster of similar entities
 */
public class EntityCluster {
    private final List<Entity> entities = new ArrayList<>();

    public EntityCluster(Entity initialEntity) {
        entities.add(initialEntity);
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public Entity getRepresentativeEntity() {
        // Longest name as representative
        return entities.stream()
                .max((a, b) -> Integer.compare(
                        a.getName() != null ? a.getName().length() : 0,
                        b.getName() != null ? b.getName().length() : 0))
                .orElse(entities.get(0));
    }
}


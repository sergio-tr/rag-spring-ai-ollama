package com.uniovi.rag.model;

/**
 * Represents an entity with enhanced metadata.
 */
public class Entity {
    private final String name;
    private final EntityType type;
    private final EntityRole role;

    public Entity(String name, EntityType type, EntityRole role) {
        this.name = name;
        this.type = type;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public EntityType getType() {
        return type;
    }

    public EntityRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return String.format("Entity[%s, type=%s, role=%s]", name, type, role);
    }
}

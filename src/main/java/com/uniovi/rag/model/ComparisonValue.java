package com.uniovi.rag.model;

/**
 * Represents a comparison value with its type.
 */
public class ComparisonValue {
    private final Object value;
    private final ComparisonType type;

    public ComparisonValue(Object value, ComparisonType type) {
        this.value = value;
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public ComparisonType getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", value.toString(), type.name());
    }
}

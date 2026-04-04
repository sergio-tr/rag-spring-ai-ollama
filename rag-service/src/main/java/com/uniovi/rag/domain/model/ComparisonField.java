package com.uniovi.rag.domain.model;

/**
 * Represents a comparison field with its type.
 */
public class ComparisonField {
    private final String fieldName;
    private final ComparisonType type;

    public ComparisonField(String fieldName, ComparisonType type) {
        this.fieldName = fieldName;
        this.type = type;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ComparisonType getType() {
        return type;
    }
}

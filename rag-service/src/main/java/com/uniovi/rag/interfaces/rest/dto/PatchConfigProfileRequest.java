package com.uniovi.rag.interfaces.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Partial update for draft {@code config_profile} rows.
 */
public record PatchConfigProfileRequest(String label, Map<String, Object> payload) {

    public List<String> nonNullFieldNames() {
        List<String> names = new ArrayList<>();
        if (label != null) {
            names.add("label");
        }
        if (payload != null) {
            names.add("payload");
        }
        return names;
    }
}

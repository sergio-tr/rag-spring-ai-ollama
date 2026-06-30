package com.uniovi.rag.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Normalizes NER {@code date} metadata whether stored as a JSON array or a single string.
 */
public final class NerDateFieldSupport {

    private static final Logger log = LoggerFactory.getLogger(NerDateFieldSupport.class);

    private NerDateFieldSupport() {
    }

    public static List<String> readDateStrings(JSONObject json, String key) {
        if (json == null || key == null || !json.has(key) || json.isNull(key)) {
            return List.of();
        }
        return coerceDateValues(json.get(key));
    }

    public static List<String> readDateStrings(JSONObject json) {
        return readDateStrings(json, "date");
    }

    public static List<String> coerceDateValues(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return List.of();
        }
        if (raw instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed);
        }
        if (raw instanceof JSONArray arr) {
            return readJSONArray(arr);
        }
        if (raw instanceof JSONObject) {
            log.warn("NER date field has unsupported shape: JSONObject (expected String or JSONArray)");
            return List.of();
        }
        log.warn("NER date field has unsupported shape: {}", raw.getClass().getName());
        return List.of();
    }

    private static List<String> readJSONArray(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            Object element = arr.opt(i);
            if (element == null || element == JSONObject.NULL) {
                continue;
            }
            if (element instanceof String s) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            } else if (element instanceof Number number) {
                out.add(number.toString());
            } else {
                log.warn("NER date array element has unsupported shape: {}", element.getClass().getName());
            }
        }
        return Collections.unmodifiableList(out);
    }
}

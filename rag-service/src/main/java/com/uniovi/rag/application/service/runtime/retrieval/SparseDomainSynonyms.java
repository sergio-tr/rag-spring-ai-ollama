package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Controlled domain synonym map for sparse lexical expansion. */
@Component
public class SparseDomainSynonyms {

    private static final TypeReference<Map<String, List<String>>> MAP_TYPE = new TypeReference<>() {};

    private final Map<String, List<String>> headToSynonyms;

    public SparseDomainSynonyms() {
        this(loadDefaultMap());
    }

    SparseDomainSynonyms(Map<String, List<String>> headToSynonyms) {
        this.headToSynonyms = normalizeMap(headToSynonyms);
    }

    public List<String> expansionsForHead(String head) {
        if (head == null || head.isBlank()) {
            return List.of();
        }
        String key = SpanishRetrievalTextSupport.foldAccents(head.toLowerCase(Locale.ROOT).trim());
        List<String> syns = headToSynonyms.get(key);
        return syns == null ? List.of() : List.copyOf(syns);
    }

    public List<String> expandWhenHeadPresent(Set<String> presentHeads) {
        if (presentHeads == null || presentHeads.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String head : presentHeads) {
            for (String syn : expansionsForHead(head)) {
                if (syn != null && !syn.isBlank()) {
                    out.add(syn.trim());
                }
            }
        }
        return List.copyOf(out);
    }

    public Set<String> knownHeads() {
        return Set.copyOf(headToSynonyms.keySet());
    }

    private static Map<String, List<String>> loadDefaultMap() {
        try (InputStream in =
                SparseDomainSynonyms.class.getResourceAsStream("/retrieval/sparse-domain-synonyms.json")) {
            if (in == null) {
                return Map.of();
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<String>> raw = mapper.readValue(in, MAP_TYPE);
            return raw != null ? raw : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, List<String>> normalizeMap(Map<String, List<String>> raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            String key = SpanishRetrievalTextSupport.foldAccents(e.getKey().toLowerCase(Locale.ROOT).trim());
            List<String> syns = new ArrayList<>();
            if (e.getValue() != null) {
                for (String s : e.getValue()) {
                    if (s != null && !s.isBlank()) {
                        syns.add(s.trim());
                    }
                }
            }
            out.put(key, List.copyOf(syns));
        }
        return Map.copyOf(out);
    }
}

package com.uniovi.rag.service.analyser;

import org.json.JSONObject;

/**
 * Query analyser that performs no extraction. Returns null (or empty JSONObject)
 * so that NER-dependent paths treat the query as having no entities.
 * Used when rag.analyser.impl=no-op to compare impact of the analyser independently of the NER feature flag.
 */
public class NoOpQueryAnalyser implements QueryAnalyser {

    @Override
    public JSONObject analyse(String query) {
        return null;
    }
}

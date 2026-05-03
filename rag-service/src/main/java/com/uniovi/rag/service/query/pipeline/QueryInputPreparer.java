package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagEffectiveFeatures;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-generation pipeline: optional expansion, optional NER, optional classification, rule overrides.
 */
public final class QueryInputPreparer {

    private static final Logger log = LoggerFactory.getLogger(QueryInputPreparer.class);

    private final RagFeatureConfiguration featureConfig;
    private final QueryExpander expander;
    private final QueryAnalyser analyser;
    private final QueryClassifier classifier;

    public QueryInputPreparer(
            RagFeatureConfiguration featureConfig,
            QueryExpander expander,
            QueryAnalyser analyser,
            QueryClassifier classifier) {
        this.featureConfig = featureConfig;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
    }

    public PreparedQuery prepare(String query) {
        String expanded = RagEffectiveFeatures.expansionEnabled(featureConfig) ? expander.expand(query) : query;
        JSONObject ner = RagEffectiveFeatures.nerEnabled(featureConfig) ? analyser.analyse(expanded) : null;
        QueryType queryType = RagEffectiveFeatures.toolsEnabled(featureConfig) ? classifier.classify(expanded) : null;
        if (RagEffectiveFeatures.toolsEnabled(featureConfig)) {
            queryType = ClassifierOverrides.apply(expanded, queryType);
        }
        if (RagEffectiveFeatures.toolsEnabled(featureConfig) && queryType == null) {
            log.warn(
                    "[CLASSIFIER] tools_enabled=true but QueryType is null after classification "
                            + "(classifier URL empty, HTTP failure, unknown label, or empty response). "
                            + "Deterministic adapter is skipped; function-calling may still run; LLM+RAG fallback applies when those yield nothing. query_preview={}",
                    query == null ? "" : (query.length() > 120 ? query.substring(0, 120) + "..." : query));
        }
        return new PreparedQuery(expanded, ner, queryType);
    }
}
